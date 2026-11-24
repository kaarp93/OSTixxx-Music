package cat.gbdsmixxx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Una pista al catàleg. `tipus` = la mateixa etiqueta que `Marques` (0 sense, 1 Cançó, 2 Sfx, 3 Descarta, 4 Jingle); `titol` = el nom que li ha posat l'usuari (buit = cap:
 * es fa servir el del driver o, si l'entrada és definitiva, «Cançó N / Jingle N / SFX N»); `ordre` = posició final dins del seu grup (1, 2...; 0 = l'original).
 */
class TitolCataleg(val tipus: Int, val titol: String, val ordre: Int = 0) {
    override fun equals(other: Any?) = other is TitolCataleg && other.tipus == tipus && other.titol == titol && other.ordre == ordre
    override fun hashCode() = (tipus * 31 + titol.hashCode()) * 31 + ordre
    override fun toString() = "$tipus:$titol:$ordre"
    companion object { const val SENSE = 0; const val CANCO = 1; const val SFX = 2; const val DESCARTA = 3; const val JINGLE = 4 }
}

/**
 * Un joc del catàleg definitiu. Clau = (`crc`, `mida`): CRC32 del fitxer sencer (el de `crcDe`, libretro i `CrcDrivers`) i mida en bytes.
 * `consola` = «GB», «NES» o «SNES» (GB inclou GBC). `titols` = id de pista -> títol. `driver` = id «tipus:clau:rev» (`DriverRef.id`) o «propi:<àlbum>» per als motors
 * propis (Groc, Cristall, TCG...). `definitiu` = l'usuari l'ha validat amb el botó explícit «Valida del tot» / «Confirma el driver» (`posaDefinitiu`); false = catalogat però sense validar. `data` = ms de l'última modificació.
 */
class EntradaCataleg(val crc: Long, val mida: Long, val nom: String, val consola: String, val titols: Map<Int, TitolCataleg>, val driver: String, val definitiu: Boolean, val data: Long) {
    override fun equals(other: Any?) = other is EntradaCataleg && other.crc == crc && other.mida == mida && other.nom == nom && other.consola == consola &&
        other.titols == titols && other.driver == driver && other.definitiu == definitiu && other.data == data
    override fun hashCode() = (crc * 31 + mida).hashCode()
    override fun toString() = "%08X/%d %s".format(crc, mida, nom)
}

/**
 * Catàleg definitiu de jocs (l'autor, 2026-11-22): metadades «per sempre» de cada ROM que funciona (nom del joc, consola, títol i tipus de cada pista, driver).
 * El motor continua sent codi; això només en són les dades. Fitxer binari propi `files/cataleg.bin` (`Durable`: magic «OXCT», versió, CRC, atòmic, `.bak`).
 * Es carrega sota demanda (la primera crida); les escriptures van a un fil propi (només l'última foto). Cap mètode no s'ha de cridar al fil principal la primera vegada.
 * Exportar escriu una còpia `.ostx` (contenidor `Ostx`, secció «CATL»); importar fusiona sense esborrar res (en conflicte guanya la `data` més recent). Kotlin pur: es prova a JVM.
 */
object CatalegDefinitiu {
    val MAGIC = Durable.magic("OXCT")
    /** v2 (2026-11-22): cada pista porta també `ordre`. La v1 es llegeix (ordre 0). */
    const val VERSIO = 2
    const val NOM_FITXER = "cataleg.bin"

    @Volatile private var fitxer: File? = null
    private val m = LinkedHashMap<String, EntradaCataleg>()
    private var carregat = false
    private val fil = Executors.newSingleThreadExecutor { r -> Thread(r, "cataleg").also { it.isDaemon = true } }
    private var pendent = false

    /** On és el fitxer (l'app hi passa `files/cataleg.bin`). Canviar de fitxer buida la memòria (proves). */
    fun inicia(f: File) {
        if (fitxer?.path == f.path) return
        espera()                                   // fora del pany: la tasca d'escriptura també el fa servir
        synchronized(m) { fitxer = f; carregat = false; m.clear() }
    }

    private fun clau(crc: Long, mida: Long): String = "%08X:%d".format(crc and 0xFFFFFFFFL, mida)

    private fun carrega() {
        synchronized(m) {
            if (carregat) return
            m.clear()
            val f = fitxer
            if (f != null) Durable.llegeixDades(f, MAGIC, VERSIO) { v, d -> llegeixLlista(v, d) }?.forEach { posa(it) }
            carregat = true
        }
    }

    private fun posa(e: EntradaCataleg) { m[clau(e.crc, e.mida)] = e }
    private fun cerca(crc: Long, mida: Long): EntradaCataleg? = m[clau(crc, mida)]

    fun troba(crc: Long, mida: Long): EntradaCataleg? { carrega(); synchronized(m) { return cerca(crc, mida) } }

    /** Afegeix o substitueix l'entrada d'aquesta ROM (en memòria ara; al disc en el fil del catàleg). */
    fun desa(e: EntradaCataleg) { carrega(); synchronized(m) { posa(e) }; programa() }

    /**
     * Posa o treu l'estat «definitiu» (l'autor, 2026-11-22: només el botó explícit «Valida del tot» / «Confirma el driver» el posa a true; les ✓ antigues i
     * `confirmatAbans` no). Si la ROM no hi era, es crea una entrada mínima (sense nom ni títols, que s'ompliran en catalogar-la). La `data` passa a ara (guanya en una
     * fusió). Retorna true (l'entrada existia o s'ha creat).
     */
    fun posaDefinitiu(crc: Long, mida: Long, definitiu: Boolean): Boolean {
        carrega()
        synchronized(m) {
            val e = cerca(crc, mida)
            if (e != null && e.definitiu == definitiu) return true
            posa(if (e == null) EntradaCataleg(crc and 0xFFFFFFFFL, mida, "", "", emptyMap(), "", definitiu, System.currentTimeMillis())
                 else EntradaCataleg(e.crc, e.mida, e.nom, e.consola, e.titols, e.driver, definitiu, maxOf(System.currentTimeMillis(), e.data + 1)))
        }
        programa()
        return true
    }
    fun esDefinitiu(crc: Long, mida: Long): Boolean = troba(crc, mida)?.definitiu == true

    /** Ids de les pistes descartades (✗) d'aquesta ROM: no tornen a sortir (ni en refer la biblioteca, ni en importar un `.ostx`, ni amb un driver regenerat). */
    fun pistesDescartades(crc: Long, mida: Long): Set<Int> = troba(crc, mida)?.titols?.filterValues { it.tipus == TitolCataleg.DESCARTA }?.keys ?: emptySet()

    /** Pistes d'un àlbum segons el catàleg: `ids` (ordre en què surten) i `etiqueta`. */
    class Pistes(val ids: List<Int>, val etiqueta: (Int) -> String)

    /**
     * Aplica el catàleg a les pistes d'un àlbum en construir la biblioteca (sense canviar cap id: les claus de pista continuen sent CRC + id).
     * Sempre: les descartades s'amaguen i el nom de l'usuari (`titol`) substitueix el del driver. Si l'entrada és `definitiu`: ordre Cançons → Jingles → Sfx (i al final
     * les sense etiqueta), dins de cada grup per `ordre` (0 = la posició original) i, a les pistes sense nom, «Cançó N / Jingle N / SFX N» amb la numeració de cada grup.
     * `tr` tradueix «Cançó», «Jingle» i «SFX» (el `t()` de l'app). Sense entrada, retorna les pistes tal qual.
     */
    fun aplicaPistes(e: EntradaCataleg?, ids: List<Int>, original: (Int) -> String, tr: (String) -> String = { it }): Pistes {
        if (e == null || e.titols.isEmpty()) return Pistes(ids, original)
        val vis = ids.filter { e.titols[it]?.tipus != TitolCataleg.DESCARTA }
        if (!e.definitiu) return Pistes(vis) { id -> e.titols[id]?.titol?.ifEmpty { null } ?: original(id) }
        fun grup(id: Int) = when (e.titols[id]?.tipus) { TitolCataleg.CANCO -> 0; TitolCataleg.JINGLE -> 1; TitolCataleg.SFX -> 2; else -> 3 }
        val pos = vis.withIndex().associate { it.value to it.index }
        val ord = vis.sortedWith(compareBy<Int>({ grup(it) }, { val o = e.titols[it]?.ordre ?: 0; if (o > 0) o - 1 else pos.getValue(it) }, { pos.getValue(it) }))
        val noms = HashMap<Int, String>(); val n = IntArray(4)
        for (id in ord) {
            val g = grup(id); n[g]++
            val propi = e.titols[id]?.titol?.ifEmpty { null }
            noms[id] = propi ?: when (g) { 0 -> tr("Cançó") + " " + n[g]; 1 -> tr("Jingle") + " " + n[g]; 2 -> tr("SFX") + " " + n[g]; else -> original(id) }
        }
        return Pistes(ord) { id -> noms[id] ?: original(id) }
    }

    fun totes(): List<EntradaCataleg> { carrega(); synchronized(m) { return m.values.toList() } }
    fun nombre(): Int { carrega(); synchronized(m) { return m.size } }

    private fun programa() {
        synchronized(m) { if (pendent) return; pendent = true }
        fil.execute {
            val foto = synchronized(m) { pendent = false; m.values.toList() }
            val f = fitxer ?: return@execute
            try { Durable.escriu(f, MAGIC, VERSIO, serialitza(foto)) } catch (e: Throwable) { }
        }
    }

    /** Espera que el disc tingui l'última versió (proves; abans d'exportar). */
    fun espera(ms: Long = 10000) { val l = java.util.concurrent.CountDownLatch(1); fil.execute { l.countDown() }; l.await(ms, TimeUnit.MILLISECONDS) }

    /** Escriu una còpia `.ostx` que només porta el catàleg (secció «CATL»). La còpia de seguretat sencera la fa `CopiaSeguretat` (Music) amb `seccio()`. */
    fun exporta(out: java.io.OutputStream) { Ostx.escriu(out, listOf(seccio())) }

    /** La secció «CATL» del `.ostx` (mateix format que la càrrega de `cataleg.bin`). */
    fun seccio(): Ostx.Seccio = Ostx.Seccio(Ostx.CATALEG, VERSIO, serialitza(totes()))

    /**
     * Fusiona una còpia `.ostx` (només en llegeix la secció «CATL»): les entrades noves s'afegeixen; si la ROM ja hi és, guanya la de `data` més recent (amb la mateixa
     * data, la d'aquí). No s'esborra res. Retorna quantes entrades s'han afegit o canviat. Llença `ErrorOstx` si el fitxer no és un OSTX vàlid, és d'una versió més
     * nova o no porta un catàleg sencer; aleshores no es canvia res.
     */
    fun importa(inp: java.io.InputStream): Int {
        val l = Ostx.llegeix(inp)
        val sec = l.seccions.firstOrNull { it.tipus == Ostx.CATALEG } ?: throw ErrorOstx(if (l.trencades > 0) ErrorOstx.MALMES else ErrorOstx.SENSE_CATALEG)
        return importaSeccio(sec, false)
    }

    /** Fusiona (o, amb `substitueix`, reemplaça tot el catàleg per) la secció «CATL». Retorna quantes entrades s'han afegit o canviat. */
    fun importaSeccio(sec: Ostx.Seccio, substitueix: Boolean): Int {
        if (sec.versio < 1 || sec.versio > VERSIO) throw ErrorOstx(ErrorOstx.MES_NOU)
        val llista = try { llegeixLlista(sec.versio, DataInputStream(ByteArrayInputStream(sec.dades))) } catch (e: Throwable) { throw ErrorOstx(ErrorOstx.MALMES) }
        carrega()
        var canvis = 0
        synchronized(m) {
            if (substitueix) { canvis = llista.size; m.clear() }
            for (e in llista) {
                val ara = cerca(e.crc, e.mida)
                if (substitueix || ara == null || e.data > ara.data) { posa(e); if (!substitueix) canvis++ }
            }
        }
        if (substitueix || canvis > 0) programa()
        return canvis
    }

    // ---- format de la càrrega (v2; la v1 no porta l'`ordre`) ----
    // int n; per entrada: long crc, long mida, text nom, text consola, text driver, bool definitiu, long data, int nTitols; per títol: int id, byte tipus, text títol, int ordre.
    // Els textos amb `Durable.escriuText` (int longitud + UTF-8).

    private fun serialitza(l: List<EntradaCataleg>): ByteArray {
        val bo = ByteArrayOutputStream()
        DataOutputStream(bo).use { d ->
            d.writeInt(l.size)
            for (e in l) {
                d.writeLong(e.crc and 0xFFFFFFFFL); d.writeLong(e.mida)
                Durable.escriuText(d, e.nom); Durable.escriuText(d, e.consola); Durable.escriuText(d, e.driver)
                d.writeBoolean(e.definitiu); d.writeLong(e.data)
                d.writeInt(e.titols.size)
                for ((id, t) in e.titols.toSortedMap()) { d.writeInt(id); d.writeByte(t.tipus); Durable.escriuText(d, t.titol); d.writeInt(t.ordre) }
            }
        }
        return bo.toByteArray()
    }

    private fun llegeixLlista(v: Int, d: DataInputStream): List<EntradaCataleg> {
        val n = d.readInt(); require(n in 0..1_000_000)
        val out = ArrayList<EntradaCataleg>(minOf(n, 4096))
        repeat(n) {
            val crc = d.readLong(); val mida = d.readLong()
            val nom = Durable.llegeixText(d); val consola = Durable.llegeixText(d); val driver = Durable.llegeixText(d)
            val def = d.readBoolean(); val data = d.readLong()
            val nt = d.readInt(); require(nt in 0..100_000)
            val tt = LinkedHashMap<Int, TitolCataleg>()
            repeat(nt) { val id = d.readInt(); val tp = d.readByte().toInt(); val tx = Durable.llegeixText(d); tt[id] = TitolCataleg(tp, tx, if (v >= 2) d.readInt() else 0) }
            out.add(EntradaCataleg(crc, mida, nom, consola, tt, driver, def, data))
        }
        return out
    }
}
