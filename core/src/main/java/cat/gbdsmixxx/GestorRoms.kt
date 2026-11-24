package cat.gbdsmixxx

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Qui reconeix les ROM (el bloc de DRIVERS el substitueix pel registre real; vegeu INTERFICIE_ROM.md). Els identificadors són «tipus:clau:rev».
 *  - `perCrc`: drivers que es reconeixen només pel CRC (sense llegir la ROM);
 *  - `perSignatura`: drivers que necessiten els bytes (`reconeix`), que s'avaluen UNA vegada per ROM i es desen a l'índex.
 */
interface Reconeixedor {
    fun perCrc(consola: String, crc: Long): List<String>
    fun perSignatura(consola: String): List<String>
    fun reconeix(consola: String, id: String, bytes: ByteArray): Boolean
}

/**
 * Operacions sobre l'índex de ROM (`IndexRoms`) sense Android (es proven a JVM): registrar un fitxer (per referència o còpia interna), revisar les carpetes i les
 * fonts (fitxers que ja no hi són), avaluar els drivers nous i dir quins drivers té cada ROM. Res no esborra fitxers de l'usuari.
 */
class GestorRoms(val idx: IndexRoms, val rec: Reconeixedor, val dirIntern: File) {
    sealed class Resultat {
        class Nova(val e: EntradaRom) : Resultat()
        class Duplicada(val crc: Long) : Resultat()
        object NoEsRom : Resultat()
        class Error(val missatge: String) : Resultat()
    }

    /** Extensions de ROM de consola que es registren per referència. */
    val extensions = setOf("gb", "gbc", "nes", "sfc", "smc")

    /** Consola d'un fitxer (null si no és GB/GBC, NES ni SNES), llegint només la capçalera. */
    fun consolaDe(f: File): String? = try {
        RandomAccessFile(f, "r").use { r ->
            val h = ByteArray(0x200); r.seek(0); val n = r.read(h).coerceAtLeast(0)
            if (n < 16) null else IndexRoms.consola(h, r.length(), f.name.substringAfterLast('.', "").lowercase()) ?: (if (capcaleraSnes(r)) "SNES" else null)
        }
    } catch (e: Throwable) { null }

    /** Registra un fitxer de ROM que ja és al disc (per referència o una còpia interna ja feta). Calcula el CRC (llegint-lo sencer una sola vegada, que cal per avaluar els drivers). */
    fun registra(f: File, origen: Int): Resultat {
        val cons = consolaDe(f) ?: return Resultat.NoEsRom
        val bytes = try { MemoriaRoms.llegeixFitxer(f) } catch (e: Throwable) { return Resultat.Error(e.javaClass.simpleName) }
        return registraBytes(bytes, cons, IndexRoms.font(origen, f))
    }

    /** Registra una ROM de la qual ja es tenen els bytes (`font` diu on és). Si el CRC ja és a l'índex, no s'afegeix cap duplicat. */
    fun registraBytes(bytes: ByteArray, cons: String, font: FontRom): Resultat {
        val crc = MemoriaRoms.crc32(bytes)
        if (idx.te(crc)) return Resultat.Duplicada(crc)
        val h = if (bytes.size >= 0x200) bytes.copyOf(0x200) else bytes
        val titol = if (cons == "GB") IndexRoms.titolGb(h) else ""
        val e = EntradaRom(crc, cons, bytes.size.toLong(), titol, editor?.invoke(cons, bytes), arrayListOf(font), emptyList(), emptySet())
        avalua(e, bytes)
        idx.afegeix(e)
        return Resultat.Nova(e)
    }

    /** Empresa de la capçalera (GB: `EditorsGb.de`); la posa `:consoles`. */
    var editor: ((String, ByteArray) -> String?)? = null

    /** Avalua a `e` els drivers per signatura que encara no s'hi han provat (amb els bytes de la ROM). */
    private fun avalua(e: EntradaRom, bytes: ByteArray) {
        val tots = rec.perSignatura(e.consola)
        val nous = tots.filter { it !in e.avaluats }
        if (nous.isEmpty()) return
        val bons = nous.filter { id -> try { rec.reconeix(e.consola, id, bytes) } catch (x: Throwable) { false } }
        e.drivers = (e.drivers.filter { it in tots } + bons).distinct()
        e.avaluats = e.avaluats.filter { it in tots }.toSet() + nous
        idx.marcaBrut()
    }

    /** Hi ha drivers per signatura nous per a aquesta ROM? (llavors cal llegir-la una vegada) */
    fun calAvaluar(e: EntradaRom): Boolean = rec.perSignatura(e.consola).any { it !in e.avaluats }

    /**
     * Avalua els drivers nous de totes les ROM que en tenen de pendents (llegint cada ROM una sola vegada, una darrere l'altra). Les que ja no hi són es treuen de l'índex.
     * Retorna els CRC de les ROM tretes.
     */
    fun avaluaPendents(potTreure: Boolean = true): List<Long> {
        val tretes = ArrayList<Long>()
        for (e in idx.llista()) {
            if (!calAvaluar(e)) continue
            val r = e.ref() ?: continue
            // si no es pot llegir: es treu (si `potTreure`) o es deixa pendent per a la propera vegada (p. ex. sense permís de lectura: no és que no hi sigui)
            val b = try { MemoriaRoms.llegeixFitxer(r.fitxer).also { if (MemoriaRoms.crc32(it) != e.crc) throw RomPerduda(e.crc, "canviada") } } catch (x: Throwable) { if (potTreure) { idx.treu(e.crc); tretes.add(e.crc) }; continue }
            avalua(e, b)
        }
        return tretes
    }

    /** Identificadors dels drivers d'una ROM: els de CRC (sense llegir res) i els de signatura ja avaluats que encara existeixen. */
    fun driversDe(e: EntradaRom): List<String> {
        val sig = rec.perSignatura(e.consola).toSet()
        return (rec.perCrc(e.consola, e.crc) + e.drivers.filter { it in sig }).distinct()
    }

    /** Una ROM ja no hi és (en tocar-la o revisar-la): es treu de l'índex (les dades de l'usuari queden guardades pel CRC). */
    fun perduda(crc: Long): Boolean = idx.treu(crc) != null

    /** Per a «Refer biblioteca»: tots els drivers per signatura es tornaran a avaluar. */
    fun oblidaAvaluacions() { for (e in idx.llista()) { e.avaluats = emptySet(); e.drivers = emptyList() }; idx.marcaBrut() }

    class Revisio(val afegides: List<EntradaRom>, val tretes: List<Long>, val duplicades: Int, val altres: List<File>)

    /**
     * Revisa: (1) les fonts de cada ROM (si ja no hi és o ha canviat de contingut, es treu); (2) les carpetes afegides (les ROM noves s'hi registren per referència; els
     * fitxers d'altres formats nous es retornen a `altres` perquè s'importin com sempre). Només ho fa quan l'usuari ho demana.
     */
    fun revisa(altresExt: Set<String>): Revisio {
        val tretes = ArrayList<Long>()
        for (e in idx.llista()) {
            val fnt = e.fonts.firstOrNull()
            if (fnt == null) { idx.treu(e.crc); tretes.add(e.crc); continue }
            val f = File(fnt.ruta)
            if (!f.isFile) { idx.treu(e.crc); tretes.add(e.crc); continue }
            if (f.length() != fnt.mida || f.lastModified() != fnt.data) {
                val c = try { MemoriaRoms.crc32(f) } catch (x: Throwable) { -1L }
                if (c != e.crc) { idx.treu(e.crc); tretes.add(e.crc); MemoriaRoms.oblida(fnt.ruta); continue }
                e.fonts[0] = IndexRoms.font(fnt.origen, f); idx.marcaBrut()
            }
        }
        val afegides = ArrayList<EntradaRom>(); var dup = 0; val altres = ArrayList<File>()
        for (c in idx.carpetes.toList()) {
            val dir = File(c)
            if (!dir.isDirectory) continue
            for (f in dir.walkTopDown().onEnter { !it.name.startsWith(".") }.filter { it.isFile }) {
                val ext = f.name.substringAfterLast('.', "").lowercase()
                if (ext in extensions) {
                    if (idx.perRuta(f.absolutePath) != null) continue
                    when (val r = registra(f, OrigenRom.REFERENCIA)) { is Resultat.Nova -> afegides.add(r.e); is Resultat.Duplicada -> dup++; else -> {} }
                } else if (ext in altresExt) {
                    val segell = "${f.length()}:${f.lastModified()}"
                    if (idx.vistos[f.absolutePath] == segell) continue
                    altres.add(f)
                }
            }
        }
        return Revisio(afegides, tretes, dup, altres)
    }

    /** Marca un fitxer d'un altre format com a ja importat (perquè la revisió no el torni a importar). */
    fun marcaVist(f: File) { idx.vistos[f.absolutePath] = "${f.length()}:${f.lastModified()}"; idx.marcaBrut() }

    /** Copia una ROM dins l'app (`roms/<CRC>.<ext>`) de manera atòmica (tmp + fsync + rename) i en retorna la font interna. Si ja hi és, no la torna a escriure. */
    fun copiaInterna(bytes: ByteArray, ext: String): FontRom {
        val crc = MemoriaRoms.crc32(bytes)
        val d = File(dirIntern, "roms"); d.mkdirs()
        val f = File(d, "%08X.%s".format(crc, ext.ifEmpty { "rom" }))
        if (!(f.isFile && f.length() == bytes.size.toLong() && MemoriaRoms.crc32(f) == crc)) {
            val t = File(f.path + ".tmp")
            FileOutputStream(t).use { o -> o.write(bytes); o.flush(); o.fd.sync() }
            try { Files.move(t.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (e: java.nio.file.AtomicMoveNotSupportedException) { Files.move(t.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
        return IndexRoms.font(OrigenRom.INTERNA, f)
    }

    companion object {
        /** Una ROM de SNES es coneix per la capçalera interna ($7FC0 o $FFC0, amb o sense els 512 bytes de les còpies .smc): complement i suma coherents i títol de text. */
        fun capcaleraSnes(r: RandomAccessFile): Boolean {
            val mida = r.length()
            if (mida !in 0x40000L..0x800000L || (mida % 0x8000L != 0L && mida % 0x8000L != 512L)) return false
            val extra = if (mida % 1024L == 512L) 512L else 0L
            for (base in longArrayOf(0x7FC0L, 0xFFC0L)) {
                val o = base + extra
                if (o + 0x30 > mida) continue
                val a = ByteArray(0x30); r.seek(o); r.readFully(a)
                val compl = (a[0x1C].toInt() and 255) or ((a[0x1D].toInt() and 255) shl 8)
                val suma = (a[0x1E].toInt() and 255) or ((a[0x1F].toInt() and 255) shl 8)
                if ((compl xor suma) == 0xFFFF && (0 until 12).all { a[it].toInt() in 0x20..0x7E }) return true
            }
            return false
        }
    }
}
