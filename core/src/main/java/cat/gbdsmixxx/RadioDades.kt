package cat.gbdsmixxx

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Dades de l'usuari de la ràdio (l'autor, 2026-11-08), desades a `musica/radio_dades.bin` (binari, abans JSON): llistes de reproducció, noms de cançó canviats, cançons
 * ocultes («Esborra de reproducció») i temps màxim per cançó. Una cançó s'identifica amb la clau «emissora|número»; l'emissora és `Emissora.clauDades` (P1, l'autor
 * 2026-11-22): «CRC:tipus:clau:rev» per a les ROM de consola amb driver i el nom per a la resta (DIAMANT, TCG, GBA_<codi>, DS_<codi>...). Les dades d'abans, amb el nom
 * de l'àlbum de consola, es migren soles en publicar cada biblioteca (`adopta`).
 */
object RadioDades {
    class Llista(var nom: String, val cancons: MutableList<String>)

    val noms = HashMap<String, String>()
    val ocults = HashSet<String>()
    val llistes = ArrayList<Llista>()
    /** BPM i tonalitat d'una cançó que s'ha posat en un deck i s'ha reproduït (tonica -1 = tonalitat desconeguda; confiança en %). */
    /** `bucle`: 1 = la cançó fa bucle, 0 = no en fa (acaba sola: efectes, fanfàrries...), 2 = analitzada però no s'ha pogut saber, -1 = encara no analitzada. */
    class Analisi(val bpm: Int, val tonica: Int, val major: Boolean, val confianca: Int, val bucle: Int = -1, val beats: Int = 0)
    val analisi = java.util.concurrent.ConcurrentHashMap<String, Analisi>()
    /** Combinacions de canals desades per cançó (4 per cançó): màscara de bits dels canals silenciats, o -1 si és buida. */
    val combos = java.util.concurrent.ConcurrentHashMap<String, IntArray>()
    /** Tipus de cada canal editat a mà per cançó (pestanya Cançons): 16 valors; -1 = el detectat sol; 0 bateria, 1 baix, 2 acords, 3 melodia, 4 agut. */
    val tipus = java.util.concurrent.ConcurrentHashMap<String, IntArray>()
    /** Segons que sona cada cançó abans de passar a la següent (els bucles són il·limitats); 0 = sense límit. */
    @Volatile var tempsMax = 0
    /** Voltes que fa cada cançó abans de passar a la següent (amb fade out); 0 = sense límit. Es compta a part del temps màxim: el que arribi primer. */
    @Volatile var bucles = 0

    private var fitxer: File? = null
    private var carregat = false

    fun clau(emissora: String, id: Int) = "$emissora|$id"
    fun emissoraDe(clau: String) = clau.substringBeforeLast('|')
    fun idDe(clau: String) = clau.substringAfterLast('|').toIntOrNull() ?: -1

    /**
     * Carrega `musica/radio_dades.bin` (binari atòmic amb `.bak`, `DadesBin`). Si no hi és però hi ha el `radio_dades.json` d'abans, el migra: el passa al binari,
     * el torna a llegir per comprovar-lo i només llavors reanomena el JSON a `.migrat` (no s'esborra).
     */
    @Synchronized fun carrega(ctx: Context) {
        if (carregat) return
        carregat = true
        val dir = File(ctx.filesDir, "musica"); dir.mkdirs()
        val f = File(dir, "radio_dades.bin"); fitxer = f
        val r = DadesBin.llegeixRadio(f)
        if (r != null) { aplica(r); return }
        val json = File(dir, "radio_dades.json")
        if (!json.exists()) return
        try {
            val j = JSONObject(json.readText())
            j.optJSONObject("noms")?.let { o -> for (k in o.keys()) noms[k] = o.getString(k) }
            j.optJSONArray("ocults")?.let { a -> for (i in 0 until a.length()) ocults.add(a.getString(i)) }
            j.optJSONArray("llistes")?.let { a ->
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i); val c = o.getJSONArray("cancons")
                    llistes.add(Llista(o.getString("nom"), MutableList(c.length()) { c.getString(it) }))
                }
            }
            j.optJSONObject("analisi")?.let { o -> for (k in o.keys()) { val a = o.getJSONArray(k); analisi[k] = Analisi(a.getInt(0), a.getInt(1), a.getInt(2) != 0, a.getInt(3), a.optInt(4, -1), a.optInt(5, 0)) } }
            j.optJSONObject("combos")?.let { o -> for (k in o.keys()) { val a = o.getJSONArray(k); combos[k] = IntArray(4) { i -> a.optInt(i, -1) } } }
            j.optJSONObject("tipus")?.let { o -> for (k in o.keys()) { val a = o.getJSONArray(k); tipus[k] = IntArray(16) { i -> a.optInt(i, -1) } } }
            tempsMax = j.optInt("tempsMax", 0); bucles = j.optInt("bucles", 0)
        } catch (e: Exception) { return }          // JSON tallat (l'error d'abans): no es migra res i el JSON es queda on és
        try {
            val inst = instantania()
            DadesBin.escriuRadio(f, inst)
            val prova = DadesBin.llegeixRadio(f)
            if (prova != null && prova.noms == inst.noms && prova.llistes == inst.llistes && prova.analisi.size == inst.analisi.size && prova.ocults == inst.ocults)
                json.renameTo(File(dir, "radio_dades.json.migrat"))
        } catch (e: Throwable) { }
    }

    private fun aplica(r: DadesBin.Radio) {
        noms.putAll(r.noms); ocults.addAll(r.ocults)
        for ((n, cs) in r.llistes) llistes.add(Llista(n, cs.toMutableList()))
        for ((k, a) in r.analisi) if (a.size >= 6) analisi[k] = Analisi(a[0], a[1], a[2] != 0, a[3], a[4], a[5])
        for ((k, a) in r.combos) combos[k] = IntArray(4) { a.getOrElse(it) { -1 } }
        for ((k, a) in r.tipus) tipus[k] = IntArray(16) { a.getOrElse(it) { -1 } }
        tempsMax = r.tempsMax; bucles = r.bucles
    }

    /** Còpia de totes les dades (es fa al fil que desa; l'escriptura va en un altre fil). */
    private fun instantania() = DadesBin.Radio(HashMap(noms), HashSet(ocults), llistes.map { it.nom to it.cancons.toList() },
        analisi.mapValues { (_, a) -> intArrayOf(a.bpm, a.tonica, if (a.major) 1 else 0, a.confianca, a.bucle, a.beats) }, combos.mapValues { it.value.copyOf() }, tipus.mapValues { it.value.copyOf() }, tempsMax, bucles)

    /** Secció «RDIO» de la còpia `.ostx`. Cal cridar-la al fil principal (la UI hi canvia les col·leccions). */
    @Synchronized fun exportaBytes(): ByteArray = DadesBin.radioBytes(instantania())
    /** Quantes dades hi ha (per al resum de la còpia). */
    fun mida(): Int = try { DadesBin.compta(instantania()) } catch (e: Throwable) { -1 }

    /**
     * Aplica una secció «RDIO» (`DadesBin.fusionaRadio`: fusió sense esborrar res, o `substitueix`). Al fil principal. Abans d'escriure es copia el fitxer a
     * `radio_dades.bin.abans_ostx`; després es desa com sempre (atòmic, amb `.bak`). Retorna quants canvis; llença si la secció no s'entén (no toca res).
     */
    fun importaBytes(b: ByteArray, substitueix: Boolean): Int {
        val imp = DadesBin.radioDeBytes(b)
        val (r, n) = synchronized(this) { DadesBin.fusionaRadio(instantania(), imp, substitueix) }
        if (n == 0 && !substitueix) return 0
        fitxer?.let { f -> if (f.exists()) try { f.copyTo(File(f.path + ".abans_ostx"), true) } catch (e: Throwable) { } }
        synchronized(this) { noms.clear(); ocults.clear(); llistes.clear(); analisi.clear(); combos.clear(); tipus.clear(); aplica(r) }
        desa()
        return n
    }

    /** Treu de tot (noms, ocultes i llistes) les cançons de les emissores els noms de les quals comencen per algun dels prefixos. */
    fun treuEmissores(prefixos: List<String>) {
        fun es(k: String) = prefixos.any { emissoraDe(k).startsWith(it) }
        noms.keys.removeAll { es(it) }
        ocults.removeAll { es(it) }
        analisi.keys.removeAll { es(it) }
        combos.keys.removeAll { es(it) }
        tipus.keys.removeAll { es(it) }
        for (l in llistes) l.cancons.removeAll { es(it) }
    }

    /**
     * P1: passa a la clau estable les claus que encara porten el nom d'un àlbum de consola (`noms`: nom -> clau estable, `Biblioteca.clausEstables`). Al fil principal (és on
     * la UI toca `noms`, `ocults` i `llistes`). Abans del primer canvi es fa una còpia del fitxer tal com era (`radio_dades.bin.abans_p1`, una sola vegada); després es desa
     * com sempre (atòmic, amb `.bak`). Idempotent: si no hi ha res a canviar, no escriu res. Retorna quantes claus ha canviat.
     */
    fun adopta(noms: Map<String, String>): Int {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) { principal.post { adopta(noms) }; return 0 }
        val f = synchronized(this) { if (!carregat) null else fitxer } ?: return 0
        val n = synchronized(this) {
            val prova = DadesBin.migraClausRadio(noms, HashMap(this.noms), HashSet(ocults), llistes.map { it.cancons.toMutableList() }, listOf(HashMap(analisi), HashMap(combos), HashMap(tipus)))
            if (prova == 0) return 0
            val copia = File(f.path + ".abans_p1")
            if (f.exists() && !copia.exists()) try { f.copyTo(copia) } catch (e: Throwable) { return 0 }          // sense còpia no es migra (es tornarà a provar)
            DadesBin.migraClausRadio(noms, this.noms, ocults, llistes.map { it.cancons }, listOf(analisi, combos, tipus))
        }
        desa()
        return n
    }

    fun gravaAnalisi(clau: String, a: Analisi) { val vell = analisi[clau]; analisi[clau] = if (a.bucle < 0 && vell != null && vell.bucle >= 0) Analisi(a.bpm, a.tonica, a.major, a.confianca, vell.bucle, vell.beats) else if (a.beats <= 0 && vell != null && vell.beats > 0) Analisi(a.bpm, a.tonica, a.major, a.confianca, a.bucle, vell.beats) else a; desa() }

    private val principal = android.os.Handler(android.os.Looper.getMainLooper())
    private val escriptor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "radio-dades").also { it.isDaemon = true } }
    @Volatile private var pendent: DadesBin.Radio? = null
    /** Desa (atòmic, amb `.bak`): la còpia de les dades es fa ara i l'escriptura en un fil apart; si se'n demanen moltes seguides, només s'escriu l'última. */
    @Synchronized fun desa() {
        // la còpia es fa al fil principal, que és on la UI canvia `noms`, `ocults` i `llistes` (col·leccions no concurrents): `gravaAnalisi` es crida des del fil d'àudio
        // del mesclador i, des d'allà, la còpia podia petar amb una ConcurrentModificationException (empassada: el canvi no es desava) i copiava totes les llistes dins del fil d'àudio
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) { principal.post { desa() }; return }
        val f = fitxer ?: return
        pendent = try { instantania() } catch (e: Throwable) { return }
        escriptor.execute {
            val r = synchronized(this) { pendent.also { pendent = null } } ?: return@execute
            try { DadesBin.escriuRadio(f, r) } catch (e: Throwable) { }
        }
    }
}
