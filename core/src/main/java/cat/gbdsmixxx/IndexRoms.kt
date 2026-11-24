package cat.gbdsmixxx

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.CRC32

/** D'on surt una ROM: copiada dins l'app (`INTERNA`) o llegida d'on és al mòbil (`REFERENCIA`, com un reproductor de MP3). */
object OrigenRom { const val INTERNA = 0; const val REFERENCIA = 1 }

/** La ROM ja no és on era (fitxer esborrat, targeta treta) o ha canviat de contingut. */
class RomPerduda(val crc: Long, missatge: String) : IOException(missatge)

/**
 * Referència a una ROM de consola (GB/GBC, NES, SNES): on és, la mida i la data que tenia en registrar-la i el CRC32 del fitxer sencer (la identitat de la ROM).
 * No guarda mai els bytes: `obre()` la llegeix sota demanda a través de `MemoriaRoms` (LRU limitat per bytes). Contracte documentat a INTERFICIE_ROM.md.
 */
class RomRef(val origen: Int, val ruta: String, val mida: Long, val data: Long, val crc: Long) {
    val fitxer: File get() = File(ruta)
    fun existeix(): Boolean = fitxer.isFile
    /** Bytes de la ROM (de la memòria cau o del disc). Llença `RomPerduda` si el fitxer no hi és o ja no és la mateixa ROM. */
    fun obre(): ByteArray = MemoriaRoms.bytes(this)
    override fun toString() = (if (origen == OrigenRom.INTERNA) "interna:" else "ref:") + ruta
}

/** Bytes de les ROM obertes: un sol LRU per a tota l'app, limitat per bytes (per defecte 48 MB). Res no hi queda retingut fora d'aquí. */
object MemoriaRoms {
    val lru = LruBytes<String, Any>(48L shl 20) { v -> when (v) { is ByteArray -> v.size.toLong(); is AmbMida -> v.mida; else -> 1024L } }
    /** Objecte desat a la memòria cau amb la seva mida (p. ex. una ROM ja convertida). */
    class AmbMida(val valor: Any, val mida: Long)
    private val panys = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private fun pany(k: String): Any = panys.getOrPut(k) { Any() }

    /** Llegeix el fitxer sencer en un sol array de la mida exacta (sense duplicar-lo mentre creix). */
    fun llegeixFitxer(f: File): ByteArray = FileInputStream(f).channel.use { ch ->
        val n = ch.size(); if (n > Int.MAX_VALUE - 64) throw IOException("massa gran")
        val b = java.nio.ByteBuffer.allocate(n.toInt()); while (b.hasRemaining()) if (ch.read(b) < 0) break
        if (b.hasRemaining()) throw IOException("fitxer tallat")
        b.array()
    }

    fun bytes(r: RomRef): ByteArray {
        val k = "raw:" + r.ruta
        (lru.get(k) as? ByteArray)?.let { return it }
        synchronized(pany(k)) {
            (lru.get(k) as? ByteArray)?.let { return it }
            val f = r.fitxer
            if (!f.isFile) throw RomPerduda(r.crc, "no hi és: " + r.ruta)
            val b = try { llegeixFitxer(f) } catch (e: IOException) { throw RomPerduda(r.crc, "no es pot llegir: " + r.ruta) }
            // si el fitxer ha canviat des que es va registrar (mida o data), es comprova que encara sigui la mateixa ROM
            if ((b.size.toLong() != r.mida || f.lastModified() != r.data) && crc32(b) != r.crc) throw RomPerduda(r.crc, "ja no és la mateixa ROM: " + r.ruta)
            lru.put(k, b)
            return b
        }
    }

    /** Un objecte fet a partir de la ROM (p. ex. `RomSnes`) a la mateixa memòria cau, amb la seva mida; `fes` rep els bytes llegits del disc (no queden a la memòria cau a part). */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> objecte(tipus: String, r: RomRef, mida: (T) -> Long, fes: (ByteArray) -> T): T {
        val k = tipus + ":" + r.ruta
        ((lru.get(k) as? AmbMida)?.valor as? T)?.let { return it }
        synchronized(pany(k)) {
            ((lru.get(k) as? AmbMida)?.valor as? T)?.let { return it }
            val raw = (lru.get("raw:" + r.ruta) as? ByteArray) ?: run {
                val f = r.fitxer
                if (!f.isFile) throw RomPerduda(r.crc, "no hi és: " + r.ruta)
                val b = try { llegeixFitxer(f) } catch (e: IOException) { throw RomPerduda(r.crc, "no es pot llegir: " + r.ruta) }
                if ((b.size.toLong() != r.mida || f.lastModified() != r.data) && crc32(b) != r.crc) throw RomPerduda(r.crc, "ja no és la mateixa ROM: " + r.ruta)
                b
            }
            val v = fes(raw)
            lru.put(k, AmbMida(v, mida(v)))
            return v
        }
    }

    fun oblida(ruta: String) { lru.removeIf { it.endsWith(":" + ruta) } }
    fun buida() = lru.clear()

    fun crc32(b: ByteArray): Long { val c = CRC32(); c.update(b); return c.value }
    /** CRC32 d'un fitxer en flux (trossos de 64 KB), sense carregar-lo sencer. */
    fun crc32(f: File): Long {
        val c = CRC32(); val buf = ByteArray(65536)
        FileInputStream(f).use { i -> while (true) { val n = i.read(buf); if (n < 0) break; c.update(buf, 0, n) } }
        return c.value
    }
}

/** On és una ROM registrada: origen (`OrigenRom`), camí absolut, mida i data que tenia en registrar-la. */
class FontRom(val origen: Int, val ruta: String, val mida: Long, val data: Long)

/**
 * Una ROM de consola de l'índex, identificada pel CRC32 del fitxer sencer. `drivers` = identificadors de driver («tipus:clau:rev») reconeguts per signatura;
 * `avaluats` = els drivers per signatura que ja s'hi han provat (només es tornen a provar els nous o amb «Refer biblioteca»). Una ROM sense cap driver també hi és.
 */
class EntradaRom(val crc: Long, val consola: String, val mida: Long, val titol: String, val editor: String?, val fonts: MutableList<FontRom>,
                 var drivers: List<String>, var avaluats: Set<String>) {
    fun ref(): RomRef? = fonts.firstOrNull()?.let { RomRef(it.origen, it.ruta, it.mida, it.data, crc) }
    val interna: Boolean get() = fonts.any { it.origen == OrigenRom.INTERNA }
}

/**
 * Índex binari persistent de la biblioteca de ROM de consoles (`files/musica/index_roms.bin`, `Durable`, magic «OXIR», versió 1). No es refà mai del tot:
 * s'hi afegeixen, es treuen i s'actualitzen entrades segons l'ús (importar, revisar una carpeta, una ROM que ja no hi és). La biblioteca es construeix des d'aquí sense llegir cap ROM.
 * També hi ha les carpetes afegides per l'usuari (per a «Revisa les carpetes») i els fitxers d'altres formats que ja s'han vist en una carpeta (per no tornar-los a importar).
 * Kotlin pur: es prova a JVM.
 */
class IndexRoms(val f: File) {
    val entrades = LinkedHashMap<Long, EntradaRom>()
    val carpetes = LinkedHashSet<String>()
    /** Fitxers d'altres formats (GBA, DS, NSF...) ja importats des d'una carpeta: camí -> «mida:data». */
    val vistos = HashMap<String, String>()
    /** Les ROM copiades dins l'app abans de l'índex (`musica_gbd_*.gb`, `musica_nes_*.nes`, `musica_snes_*.sfc`) ja s'hi han registrat. */
    var internesRegistrades = false
    @Volatile var brut = false; private set

    @Synchronized fun carrega(): Boolean {
        entrades.clear(); carpetes.clear(); vistos.clear(); internesRegistrades = false; brut = false
        return Durable.llegeixDades(f, MAGIC, VERSIO) { _, d -> llegeixDe(d) } ?: false
    }

    private fun llegeixDe(d: DataInputStream): Boolean {
        val e2 = LinkedHashMap<Long, EntradaRom>(); val c2 = LinkedHashSet<String>(); val v2 = HashMap<String, String>()
        val intern = d.readBoolean()
        repeat(d.readInt()) { c2.add(d.readUTF()) }
        repeat(d.readInt()) { v2[d.readUTF()] = d.readUTF() }
        repeat(d.readInt()) {
            val crc = d.readLong(); val cons = d.readUTF(); val mida = d.readLong(); val titol = d.readUTF(); val ed = d.readUTF().ifEmpty { null }
            val fonts = ArrayList<FontRom>(); repeat(d.readInt()) { fonts.add(FontRom(d.readByte().toInt(), d.readUTF(), d.readLong(), d.readLong())) }
            val drv = List(d.readInt()) { d.readUTF() }
            val av = HashSet<String>(); repeat(d.readInt()) { av.add(d.readUTF()) }
            e2[crc] = EntradaRom(crc, cons, mida, titol, ed, fonts, drv, av)
        }
        entrades.putAll(e2); carpetes.addAll(c2); vistos.putAll(v2); internesRegistrades = intern
        return true
    }

    @Synchronized fun desa() {
        Durable.escriuDades(f, MAGIC, VERSIO) { d ->
            d.writeBoolean(internesRegistrades)
            d.writeInt(carpetes.size); for (c in carpetes) d.writeUTF(c)
            d.writeInt(vistos.size); for ((k, v) in vistos) { d.writeUTF(k); d.writeUTF(v) }
            d.writeInt(entrades.size)
            for (e in entrades.values) {
                d.writeLong(e.crc); d.writeUTF(e.consola); d.writeLong(e.mida); d.writeUTF(e.titol.take(200)); d.writeUTF(e.editor ?: "")
                d.writeInt(e.fonts.size); for (x in e.fonts) { d.writeByte(x.origen); d.writeUTF(x.ruta); d.writeLong(x.mida); d.writeLong(x.data) }
                d.writeInt(e.drivers.size); for (x in e.drivers) d.writeUTF(x)
                d.writeInt(e.avaluats.size); for (x in e.avaluats) d.writeUTF(x)
            }
        }
        brut = false
    }
    /** Desa només si hi ha hagut canvis. */
    @Synchronized fun desaSiCal() { if (brut) desa() }
    @Synchronized fun marcaBrut() { brut = true }

    @Synchronized fun perRuta(ruta: String): EntradaRom? = entrades.values.firstOrNull { e -> e.fonts.any { it.ruta == ruta } }
    @Synchronized fun te(crc: Long) = entrades.containsKey(crc)
    /** Afegeix una ROM nova; si el CRC ja hi és (interna o no), NO s'afegeix cap duplicat i retorna false. */
    @Synchronized fun afegeix(e: EntradaRom): Boolean { if (entrades.containsKey(e.crc)) return false; entrades[e.crc] = e; brut = true; return true }
    @Synchronized fun treu(crc: Long): EntradaRom? = entrades.remove(crc)?.also { brut = true }
    @Synchronized fun llista(): List<EntradaRom> = entrades.values.toList()

    companion object {
        val MAGIC = Durable.magic("OXIR")
        const val VERSIO = 1

        /** Font d'un fitxer tal com és ara. */
        fun font(origen: Int, f: File) = FontRom(origen, f.absolutePath, f.length(), f.lastModified())

        /** Consola d'una ROM pels primers 0x200 bytes i la mida (null si no és GB/GBC, NES ni SNES). Mateixos criteris que la importació d'abans. */
        fun consola(h: ByteArray, mida: Long, ext: String): String? {
            if (h.size >= 16 && h[0] == 'N'.code.toByte() && h[1] == 'E'.code.toByte() && h[2] == 'S'.code.toByte() && h[3] == 0x1A.toByte() && mida < 2_000_000L) return "NES"
            if (h.size >= 0x150 && h[0x104] == 0xCE.toByte() && h[0x105] == 0xED.toByte() && h[0x106] == 0x66.toByte() && mida <= 8_000_000L) return "GB"
            if ((ext == "sfc" || ext == "smc") && mida in 0x40000L..0x800000L) return "SNES"
            return null
        }
        /** Títol de la capçalera (GB: 0x134, 16 caràcters; NES no en té; SNES: el de la capçalera interna si se sap). */
        fun titolGb(h: ByteArray): String = if (h.size < 0x144) "" else String(h, 0x134, 16, Charsets.ISO_8859_1).takeWhile { it.code in 32..126 }.trim()
    }
}
