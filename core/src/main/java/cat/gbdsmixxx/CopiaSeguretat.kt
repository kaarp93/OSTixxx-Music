package cat.gbdsmixxx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Peces pures (sense Android, provades a JVM) de la còpia de seguretat `.ostx`: la secció de l'índex de la biblioteca (`IDXR`), la de preferències (`PREF`) i la llista
 * de ROM que falten. La resta de seccions les donen `CatalegDefinitiu.seccio`, `MagatzemUsuari.exportaBytes`, `DadesBin.radioBytes` i el paquet de drivers importats.
 */
object SeccioIndex {
    const val VERSIO = 1

    fun escriuEntrada(d: DataOutputStream, e: EntradaRom) {
        d.writeLong(e.crc); Durable.escriuText(d, e.consola); d.writeLong(e.mida); Durable.escriuText(d, e.titol); Durable.escriuText(d, e.editor ?: "")
        d.writeInt(e.fonts.size); for (x in e.fonts) { d.writeByte(x.origen); Durable.escriuText(d, x.ruta); d.writeLong(x.mida); d.writeLong(x.data) }
        d.writeInt(e.drivers.size); for (x in e.drivers) Durable.escriuText(d, x)
        d.writeInt(e.avaluats.size); for (x in e.avaluats) Durable.escriuText(d, x)
    }
    fun llegeixEntrada(d: DataInputStream): EntradaRom {
        val crc = d.readLong(); val cons = Durable.llegeixText(d); val mida = d.readLong(); val titol = Durable.llegeixText(d); val ed = Durable.llegeixText(d).ifEmpty { null }
        val nf = d.readInt(); require(nf in 0..10000)
        val fonts = ArrayList<FontRom>(); repeat(nf) { fonts.add(FontRom(d.readByte().toInt(), Durable.llegeixText(d), d.readLong(), d.readLong())) }
        val nd = d.readInt(); require(nd in 0..100000); val drv = List(nd) { Durable.llegeixText(d) }
        val na = d.readInt(); require(na in 0..100000); val av = HashSet<String>(); repeat(na) { av.add(Durable.llegeixText(d)) }
        return EntradaRom(crc, cons, mida, titol, ed, fonts, drv, av)
    }

    /**
     * Secció «IDXR»: les carpetes afegides i, per cada ROM, CRC, consola, mida, títol, editor, d'on ve (origen i ruta, mida i data del fitxer) i els drivers (ids
     * «tipus:clau:rev»). També hi van les que ara falten (`falten`), perquè una còpia feta en un mòbil sense alguna ROM no la perdi. Cap byte de cap ROM.
     */
    fun exporta(idx: IndexRoms, falten: RomsQueFalten?): ByteArray {
        val bo = ByteArrayOutputStream()
        synchronized(idx) {
            val totes = LinkedHashMap<Long, EntradaRom>()
            for (e in falten?.llista() ?: emptyList()) totes[e.crc] = e
            for (e in idx.llista()) totes[e.crc] = e
            DataOutputStream(bo).use { d ->
                d.writeInt(idx.carpetes.size); for (c in idx.carpetes) Durable.escriuText(d, c)
                d.writeInt(totes.size); for (e in totes.values) escriuEntrada(d, e)
            }
        }
        return bo.toByteArray()
    }

    class Dades(val carpetes: List<String>, val entrades: List<EntradaRom>)
    fun llegeix(b: ByteArray): Dades {
        val d = DataInputStream(ByteArrayInputStream(b))
        val nc = d.readInt(); require(nc in 0..100000); val c = List(nc) { Durable.llegeixText(d) }
        val ne = d.readInt(); require(ne in 0..1_000_000); val e = List(ne) { llegeixEntrada(d) }
        return Dades(c, e)
    }

    /** Resultat d'importar l'índex: ROM afegides que ja són al mòbil (àlbums restaurats), ROM que falten (amb nom), les que ja hi eren i carpetes noves. */
    class Resum(val restaurades: List<EntradaRom>, val falten: List<EntradaRom>, val jaHiEren: Int, val carpetesNoves: Int)

    /** Una font és bona en aquest mòbil si el fitxer hi és amb la mateixa mida (si la data ha canviat, `RomRef.obre` en torna a comprovar el CRC). */
    val existeixPerDefecte: (FontRom) -> Boolean = { f -> val x = File(f.ruta); x.isFile && x.length() == f.mida }

    /**
     * Fusiona l'índex importat (sempre fusió: mai treu cap ROM d'aquí). Mateix CRC ja registrat = no s'afegeix. Les ROM que tenen algun fitxer al mòbil s'afegeixen
     * només amb aquests fitxers; les altres van a `falten` (sense àlbum, amb les dades de l'usuari guardades) fins que «Revisa les carpetes» o una importació les torni a
     * trobar. Les carpetes s'afegeixen. No desa: qui crida desa `idx` i `falten`.
     */
    fun importa(b: ByteArray, idx: IndexRoms, falten: RomsQueFalten, existeix: (FontRom) -> Boolean = existeixPerDefecte): Resum {
        val dd = llegeix(b)
        val rest = ArrayList<EntradaRom>(); val falt = ArrayList<EntradaRom>(); var ja = 0; var nc = 0
        synchronized(idx) {
            for (c in dd.carpetes) if (idx.carpetes.add(c)) { nc++; idx.marcaBrut() }
            for (e in dd.entrades) {
                if (idx.te(e.crc)) { ja++; continue }
                val bones = e.fonts.filter { try { existeix(it) } catch (x: Throwable) { false } }
                if (bones.isNotEmpty()) { idx.afegeix(EntradaRom(e.crc, e.consola, e.mida, e.titol, e.editor, ArrayList(bones), e.drivers, e.avaluats)); rest.add(e) }
                else { falten.afegeix(e); falt.add(e) }
            }
        }
        return Resum(rest, falt, ja, nc)
    }
}

/**
 * ROM que la biblioteca coneix (d'una còpia `.ostx`) però que no són al mòbil (`files/musica/roms_falten.bin`, `Durable`, magic «OXRF», v1). No fan àlbum; les seves
 * dades de l'usuari queden guardades pel CRC. `poda` les treu quan la ROM torna a ser a l'índex (revisant carpetes o important-la). Kotlin pur.
 */
class RomsQueFalten(val f: File) {
    private val m = LinkedHashMap<Long, EntradaRom>()
    private var carregat = false
    @Synchronized private fun carrega() {
        if (carregat) return
        carregat = true
        Durable.llegeixDades(f, MAGIC, VERSIO) { _, d -> List(d.readInt()) { SeccioIndex.llegeixEntrada(d) } }?.forEach { m[it.crc] = it }
    }
    @Synchronized fun llista(): List<EntradaRom> { carrega(); return m.values.toList() }
    @Synchronized fun afegeix(e: EntradaRom) { carrega(); m[e.crc] = e }
    @Synchronized fun te(crc: Long): Boolean { carrega(); return m.containsKey(crc) }
    /** Treu les que ja són a l'índex; retorna quantes. No desa. */
    @Synchronized fun poda(idx: IndexRoms): Int { carrega(); val k = m.keys.filter { idx.te(it) }; for (x in k) m.remove(x); return k.size }
    @Synchronized fun desa() {
        carrega()
        if (m.isEmpty() && !f.exists()) return
        Durable.escriuDades(f, MAGIC, VERSIO) { d -> d.writeInt(m.size); for (e in m.values) SeccioIndex.escriuEntrada(d, e) }
    }
    companion object { val MAGIC = Durable.magic("OXRF"); const val VERSIO = 1 }
}

/**
 * Secció «PREF»: les preferències (`SharedPreferences "gbdsmixxx"`) que són de l'usuari i serveixen en un altre mòbil (tema, visualitzador, concert, efectes, freqüència,
 * triangle sostingut, tipus de driver, libretro...). No hi van les dades d'usuari antigues (ja migrades a «DUSR»), ni la carpeta dels reports (un permís SAF que no
 * serveix en un altre mòbil), ni el mode antic (xarxa de seguretat d'aquest mòbil).
 */
object SeccioPrefs {
    const val VERSIO = 1
    private val PREFIXOS_FORA = listOf("marca_", "marcamot_", "marcatit_", "pm_", "pt_", "pe_", "pn_", "po_")
    private val CLAUS_FORA = setOf("comptes", "marques_llista", "reports_dir", "mode_antic")
    fun exportable(k: String) = k !in CLAUS_FORA && PREFIXOS_FORA.none { k.startsWith(it) }

    fun exporta(prefs: Map<String, *>): ByteArray {
        val l = prefs.entries.filter { exportable(it.key) && it.value != null }
        val bo = ByteArrayOutputStream()
        DataOutputStream(bo).use { d ->
            val bons = l.filter { val v = it.value; v is Int || v is Long || v is Float || v is Boolean || v is String || v is Set<*> }
            d.writeInt(bons.size)
            for ((k, v) in bons) {
                Durable.escriuText(d, k)
                when (v) {
                    is Int -> { d.writeByte(1); d.writeInt(v) }
                    is Long -> { d.writeByte(2); d.writeLong(v) }
                    is Float -> { d.writeByte(3); d.writeFloat(v) }
                    is Boolean -> { d.writeByte(4); d.writeBoolean(v) }
                    is String -> { d.writeByte(5); Durable.escriuText(d, v) }
                    is Set<*> -> { val s = v.filterIsInstance<String>(); d.writeByte(6); d.writeInt(s.size); for (x in s) Durable.escriuText(d, x) }
                }
            }
        }
        return bo.toByteArray()
    }

    fun llegeix(b: ByteArray): Map<String, Any> {
        val d = DataInputStream(ByteArrayInputStream(b))
        val n = d.readInt(); require(n in 0..1_000_000)
        val out = LinkedHashMap<String, Any>()
        repeat(n) {
            val k = Durable.llegeixText(d)
            val v: Any = when (d.readByte().toInt()) {
                1 -> d.readInt(); 2 -> d.readLong(); 3 -> d.readFloat(); 4 -> d.readBoolean(); 5 -> Durable.llegeixText(d)
                6 -> { val m = d.readInt(); require(m in 0..100000); LinkedHashSet<String>().also { s -> repeat(m) { s.add(Durable.llegeixText(d)) } } }
                else -> throw IllegalArgumentException("tipus")
            }
            if (exportable(k)) out[k] = v
        }
        return out
    }

    /** Què cal escriure: fusió = només les claus que aquí no hi són; `substitueix` = totes les importades (les d'aquí que no hi són es queden). */
    fun aEscriure(local: Map<String, *>, imp: Map<String, Any>, substitueix: Boolean): Map<String, Any> =
        if (substitueix) imp.filter { local[it.key] != it.value } else imp.filter { !local.containsKey(it.key) }
}
