package cat.ostixxx.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Emissora
import cat.gbdsmixxx.Portades
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dades i portades dels jocs des de libretro (l'autor, 2026-11-15): `libretro-database` (gènere, saga i desenvolupador per CRC32 de la ROM, sense clau d'API; CC BY-SA 4.0)
 * i `libretro-thumbnails` (portades pel nom del joc). Només es baixa si l'autor ho activa a Ajustos; les dades es desen a `files/libretro/` i després va sense connexió.
 * El CRC és el del fitxer sencer (capçalera inclosa), que és el que fa servir libretro (comprovat amb ROM de NES, SNES i GB).
 */
object Libretro {
    class Info(val nom: String, val genere: String, val saga: String, val desenvolupador: String, val sistema: String, val any: Int = 0)

    private const val BASE = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat"
    // Portades: el GitHub de libretro (libretro-thumbnails/<sistema amb _>/master/Named_Boxarts); la web thumbnails.libretro.com no funciona
    private const val ART = "https://raw.githubusercontent.com/libretro-thumbnails"
    private val SISTEMES = mapOf("GB" to "Nintendo - Game Boy", "GBC" to "Nintendo - Game Boy Color", "NES" to "Nintendo - Nintendo Entertainment System",
        "SNES" to "Nintendo - Super Nintendo Entertainment System", "GBA" to "Nintendo - Game Boy Advance", "DS" to "Nintendo - Nintendo DS")
    private val TIPUS = listOf("genre", "franchise", "developer", "releaseyear")

    var versio by mutableIntStateOf(0)
    var estat by mutableStateOf("")
    var treballant by mutableStateOf(false)
    fun dadesActives() = App.env.int("libretro_dades", 0) == 1
    fun artActiu() = App.env.int("libretro_art", 0) == 1
    fun posaDades(v: Boolean) = App.env.putInt("libretro_dades", if (v) 1 else 0)
    fun posaArt(v: Boolean) = App.env.putInt("libretro_art", if (v) 1 else 0)

    private fun dir(ctx: Context) = File(ctx.filesDir, "libretro").also { it.mkdirs() }
    private fun fitxer(ctx: Context, tipus: String, sis: String) = File(dir(ctx), "${tipus}__${sis.replace(' ', '_')}.dat")

    /** Consoles on es busca un àlbum: GB i GBC es miren els dos (un joc de GB pot ser a l'un o a l'altre). */
    private fun consoles(c: String) = if (c == "GB" || c == "GBC") listOf("GB", "GBC") else listOf(c)

    // ---- Índex binari per consola (bloc de càrrega, 2026-11-16): en baixar les dades, els .dat de text es converteixen UNA vegada a `idx_<consola>.bin`
    // (`Durable`, magic «OXLB», v1: CRC -> info i serial -> info) i els .dat s'esborren. Després només es llegeix el binari, sota demanda i mai al fil principal
    // (sense `readText` ni expressions regulars a l'arrencada). Si encara hi ha .dat d'abans (baixats amb una versió anterior), es converteixen la primera vegada que calen.
    private class Taula(val perCrc: Map<Long, Info>, val perSerial: Map<String, Info>)
    private val MAGIC_IDX = cat.gbdsmixxx.Durable.magic("OXLB")
    private fun fitxerIdx(ctx: Context, cons: String) = File(dir(ctx), "idx_$cons.bin")
    private val index = java.util.concurrent.ConcurrentHashMap<String, Taula>()          // consola -> taula

    private fun escriuInfo(d: java.io.DataOutputStream, i: Info) { for (x in listOf(i.nom, i.genere, i.saga, i.desenvolupador, i.sistema)) cat.gbdsmixxx.Durable.escriuText(d, x); d.writeInt(i.any) }
    private fun llegeixInfo(d: java.io.DataInputStream): Info { val c = List(5) { cat.gbdsmixxx.Durable.llegeixText(d) }; return Info(c[0], c[1], c[2], c[3], c[4], d.readInt()) }
    private fun llegeixIdx(ctx: Context, cons: String): Taula? = cat.gbdsmixxx.Durable.llegeixDades(fitxerIdx(ctx, cons), MAGIC_IDX, 1) { _, d ->
        val pc = HashMap<Long, Info>(); repeat(d.readInt()) { pc[d.readLong()] = llegeixInfo(d) }
        val ps = HashMap<String, Info>(); repeat(d.readInt()) { ps[cat.gbdsmixxx.Durable.llegeixText(d)] = llegeixInfo(d) }
        Taula(pc, ps)
    }
    /** Fitxers .dat de text que pertanyen a una consola. */
    private fun dats(ctx: Context, cons: String): List<File> {
        val sis = SISTEMES[cons] ?: return emptyList()
        return TIPUS.map { fitxer(ctx, it, sis) } + when (cons) { "GBA" -> listOf(File(dir(ctx), "serial__GBA.dat")); "DS" -> listOf(File(dir(ctx), "nointro__DS.dat"), File(dir(ctx), "dat__DS.dat")); else -> emptyList() }
    }

    private val RE = Regex("comment \"([^\"]*)\"\\s+(?:genre|franchise|developer|releaseyear) \"([^\"]*)\"\\s+rom \\( crc ([0-9A-Fa-f]+)")
    private val GBA_SERIAL = Regex("comment \"([^\"]*)\"\\s+serial \"AGB-([A-Z0-9]{4})-[A-Z]+\"\\s+rom \\(\\s+crc ([0-9A-Fa-f]+)")
    private class Ds(val nom: String, val genere: String, val dev: String, val any: Int = 0)
    private fun valorDe(l: String, camp: String): String? = if (l.startsWith("\t$camp \"")) l.substring(camp.length + 2).trim().removeSurrounding("\"") else null

    /**
     * Converteix els .dat de text d'una consola a l'índex binari (fusionant amb el que ja hi havia: un .dat que no s'ha pogut baixar no esborra les dades d'abans),
     * comprova l'índex llegint-lo i només llavors esborra els .dat. Es crida en un fil apart.
     */
    @Synchronized private fun converteix(ctx: Context, cons: String) {
        val fs = dats(ctx, cons).filter { it.exists() }
        if (fs.isEmpty()) return
        val sis = SISTEMES[cons] ?: return
        val vell = llegeixIdx(ctx, cons)
        // CRC -> info (GB, GBC, NES, SNES, GBA)
        val nom = HashMap<Long, String>(); val camp = TIPUS.associateWith { HashMap<Long, String>() }; val tipusNous = HashSet<String>()
        for (t in TIPUS) { val f = fitxer(ctx, t, sis); if (!f.exists()) continue; tipusNous.add(t)
            for (m in RE.findAll(f.readText())) { val crc = m.groupValues[3].toLong(16); nom.putIfAbsent(crc, m.groupValues[1]); camp.getValue(t)[crc] = m.groupValues[2] } }
        val perCrc = HashMap<Long, Info>(vell?.perCrc ?: emptyMap())
        for ((c, n) in nom) {
            val o = perCrc[c]
            fun v(t: String, ant: String?) = if (t in tipusNous) camp.getValue(t)[c] ?: "" else ant ?: ""
            perCrc[c] = Info(n, v("genre", o?.genere), v("franchise", o?.saga), v("developer", o?.desenvolupador), sis, if ("releaseyear" in tipusNous) camp.getValue("releaseyear")[c]?.toIntOrNull() ?: 0 else o?.any ?: 0)
        }
        val perSerial = HashMap<String, Info>(vell?.perSerial ?: emptyMap())
        if (cons == "GBA") {
            val f = File(dir(ctx), "serial__GBA.dat")
            if (f.exists()) for (m in GBA_SERIAL.findAll(f.readText())) {
                val b = perCrc[m.groupValues[3].toLong(16)]
                perSerial[m.groupValues[2]] = Info(m.groupValues[1], b?.genere ?: "", b?.saga ?: "", b?.desenvolupador ?: "", sis, b?.any ?: 0)
            }
        }
        if (cons == "DS") {
            val d = HashMap<String, Ds>()
            File(dir(ctx), "dat__DS.dat").takeIf { it.exists() }?.let { f -> var nm = ""; var ser = ""; var g = ""; var dv = ""; var an = 0
                f.forEachLine { l -> if (l == "game (") { nm = ""; ser = ""; g = ""; dv = ""; an = 0 } else if (l == ")") { if (ser.isNotEmpty() && nm.isNotEmpty()) d.putIfAbsent(ser, Ds(nm, g, dv, an)); ser = "" }
                    else { if (nm.isEmpty()) valorDe(l, "name")?.let { nm = it }; if (ser.isEmpty()) valorDe(l, "serial")?.let { ser = it }; valorDe(l, "genre")?.let { g = it }; valorDe(l, "developer")?.let { dv = it }; if (l.startsWith("\treleaseyear ")) an = l.substring(13).trim().toIntOrNull() ?: 0 } } }
            val n = HashMap<String, MutableList<Pair<String, String>>>()
            File(dir(ctx), "nointro__DS.dat").takeIf { it.exists() }?.let { f -> var nm = ""; var reg = ""
                f.forEachLine { l -> valorDe(l, "name")?.let { nm = it }; valorDe(l, "region")?.let { reg = it }; valorDe(l, "serial")?.let { n.getOrPut(it) { ArrayList() }.add(nm to reg) } } }
            for (codi in d.keys + n.keys) {
                val x = d[codi]
                val regio = when (codi.lastOrNull()) { 'E' -> "USA"; 'P' -> "Europe"; 'J' -> "Japan"; 'K' -> "Korea"; 'C' -> "China"; 'D' -> "Germany"; 'F' -> "France"; 'I' -> "Italy"; 'S' -> "Spain"; 'H' -> "Netherlands"; 'U' -> "Australia"; else -> "" }
                val nomArt = n[codi]?.let { l -> (l.firstOrNull { regio.isNotEmpty() && it.second.contains(regio) } ?: l.first()).first } ?: x?.nom ?: continue
                perSerial[codi] = Info(nomArt, x?.genere ?: "", "", x?.dev ?: "", sis, x?.any ?: 0)
            }
        }
        cat.gbdsmixxx.Durable.escriuDades(fitxerIdx(ctx, cons), MAGIC_IDX, 1) { d ->
            d.writeInt(perCrc.size); for ((c, i) in perCrc) { d.writeLong(c); escriuInfo(d, i) }
            d.writeInt(perSerial.size); for ((k, i) in perSerial) { cat.gbdsmixxx.Durable.escriuText(d, k); escriuInfo(d, i) }
        }
        val prova = llegeixIdx(ctx, cons)
        if (prova != null && prova.perCrc.size == perCrc.size && prova.perSerial.size == perSerial.size) for (f in fs) f.delete()          // el .dat de text ja no cal
        index.remove(cons)
    }

    /** Una sola lectura per consola: `getOrPut` no és atòmic i, amb molts àlbums alhora (`infoUi`), cada fil llegia i desava a memòria la seva còpia de l'índex. */
    private fun carrega(ctx: Context, cons: String): Taula = index[cons] ?: synchronized(this) {
        index[cons] ?: run {
            if (dats(ctx, cons).any { it.exists() }) try { converteix(ctx, cons) } catch (x: Throwable) { }
            (llegeixIdx(ctx, cons) ?: Taula(emptyMap(), emptyMap())).also { index[cons] = it }
        }
    }

    /** Dades d'un àlbum (gènere, saga, desenvolupador), o null si no hi ha CRC o el joc no és a la base de dades. No es crida mai al fil principal (`infoUi`). */
    fun info(ctx: Context, e: Emissora): Info? {
        // GBA i DS: no es busquen pel CRC del fitxer (no és a dins de la ROM) sinó pel codi de joc de 4 lletres de la capçalera, que libretro té com a «serial»
        e.serial?.let { sr -> val c = consolaDe(e); if (c == "GBA" || c == "DS") carrega(ctx, c).perSerial[sr]?.let { return it } }
        val crc = e.crc ?: return null
        for (c in consoles(consolaDe(e))) carrega(ctx, c).perCrc[crc]?.let { return it }
        return null
    }

    /** Signatura de les dades baixades (per a les llistes automàtiques): mida i data dels índexs binaris. */
    fun signaturaDades(ctx: Context): Long = (dir(ctx).listFiles { f -> f.name.startsWith("idx_") && f.name.endsWith(".bin") } ?: emptyArray()).sumOf { it.lastModified() / 1000 + it.length() } +
        (dir(ctx).listFiles { f -> f.name.endsWith(".dat") } ?: emptyArray()).sumOf { it.lastModified() / 1000 + it.length() }

    // ---- Per a la UI: mai llegeix fitxers al fil principal; la primera vegada ho fa en un fil apart i avisa amb `versio` ----
    /** Canvia quan una `infoUi` s'acaba de carregar (la pantalla d'àlbum es redibuixa); no afecta les llistes automàtiques. */
    var infoVersio by mutableIntStateOf(0)
    private val cacheInfo = java.util.concurrent.ConcurrentHashMap<String, List<Info?>>()
    private val carregantInfo = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    // Revisió UI (2026-11-22): un sol fil de fons per a totes les files (abans, un `Thread` nou per àlbum: en lliscar la Biblioteca se'n podien obrir centenars,
    // i cada un podia llegir l'índex binari de la mateixa consola alhora); es fa servir el context de l'aplicació (no l'Activity).
    private val execInfo = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "libretro-info").also { it.isDaemon = true; it.priority = Thread.MIN_PRIORITY } }
    /** Poca memòria (`MusicApp.onTrimMemory`): les fitxes es tornen a llegir del disc quan calguin. */
    fun buidaMemoria() { cacheInfo.clear() }
    fun infoUi(ctx: Context, e: Emissora): Info? {
        cacheInfo[e.clauDades]?.let { return it[0] }
        // només qui encara no té la dada queda pendent de `infoVersio` (abans cada fila el llegia sempre i es refeia a cada dada nova de qualsevol àlbum)
        @Suppress("UNUSED_VARIABLE") val v = infoVersio
        if (carregantInfo.add(e.clauDades)) {
            val app = ctx.applicationContext
            execInfo.execute { try { cacheInfo[e.clauDades] = listOf(info(app, e)) } catch (x: Throwable) { cacheInfo[e.clauDades] = listOf(null) }; carregantInfo.remove(e.clauDades); principal.post { infoVersio++ } }
        }
        return null
    }
    /** Un sol fil per a totes les `infoUi` (abans un fil nou per àlbum: una pantalla amb centenars d'àlbums en creava centenars). */
    private val principal = android.os.Handler(android.os.Looper.getMainLooper())
    private fun baixa(url: String, desti: File): Boolean = try {
        val c = URL(url).openConnection() as HttpURLConnection; c.connectTimeout = 15000; c.readTimeout = 30000
        if (c.responseCode != 200) false else { val tmp = File(desti.path + ".tmp"); c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }; tmp.renameTo(desti) }
    } catch (x: Throwable) { false }

    private fun nomArxiu(n: String) = n.map { if (it in "&*/:`<>?\\|") '_' else it }.joinToString("")
    private fun enc(s: String) = android.net.Uri.encode(s)

    /** Baixa (en un fil apart) les dades i/o les portades dels àlbums indicats, segons el que l'autor tingui activat. */
    fun actualitza(ctx: Context, albums: List<Emissora>) {
        if (treballant) return
        val dades = dadesActives(); val art = artActiu()
        if (!dades && !art) { estat = t2("Activa les dades o les portades"); return }
        treballant = true; estat = t2("Baixant...")
        Thread {
            try {
                if (dades) {
                    val cons = albums.flatMap { consoles(consolaDe(it)) }.toSet().filter { it in SISTEMES }
                    var n = 0
                    for (c in cons) for (tp in TIPUS) { estat = t2("Dades") + " " + nomConsola(c) + " · " + tp; if (baixa("$BASE/$tp/${enc(SISTEMES.getValue(c))}.dat", fitxer(ctx, tp, SISTEMES.getValue(c)))) n++ }
                    if ("GBA" in cons && baixa("$BASE/serial/${enc(SISTEMES.getValue("GBA"))}.dat", File(dir(ctx), "serial__GBA.dat"))) n++
                    if ("DS" in cons) { if (baixa("$BASE/no-intro/${enc(SISTEMES.getValue("DS"))}.dat", File(dir(ctx), "nointro__DS.dat"))) n++
                        if (baixa("https://raw.githubusercontent.com/libretro/libretro-database/master/dat/${enc(SISTEMES.getValue("DS"))}.dat", File(dir(ctx), "dat__DS.dat"))) n++ }
                    for (c in cons) { estat = t2("Dades") + " " + nomConsola(c); try { converteix(ctx, c) } catch (x: Throwable) { } }
                    index.clear(); cacheInfo.clear(); infoVersio++          // les files tornen a demanar la dada nova
                }
                if (art) {
                    val dirArt = File(dir(ctx), "art").also { it.mkdirs() }
                    var fets = 0; var sense = 0; val llista = albums.filter { !File(dirArt, cat.gbdsmixxx.Portades.nomArt(it) + ".jpg").exists() }
                    for ((k, e) in llista.withIndex()) {
                        val inf = info(ctx, e); if (inf == null) { sense++; continue }
                        estat = t2("Portades") + " " + (k + 1) + "/" + llista.size
                        val tmp = File(dirArt, cat.gbdsmixxx.Portades.nomArt(e) + ".png.tmp")
                        if (baixa("$ART/${enc(inf.sistema.replace(' ', '_'))}/master/Named_Boxarts/${enc(nomArxiu(inf.nom))}.png", tmp)) {
                            try { val o = BitmapFactory.Options().apply { inSampleSize = 2 }; val bm = BitmapFactory.decodeFile(tmp.path, o)
                                if (bm != null) { File(dirArt, cat.gbdsmixxx.Portades.nomArt(e) + ".jpg").outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 88, it) }; fets++ } } catch (x: Throwable) {}
                        } else sense++
                        tmp.delete()
                    }
                    Portades.netejaCache()
                    estat = t2("Fet") + ": " + fets + " " + t2("portades") + (if (sense > 0) ", " + sense + " " + t2("sense portada") else "")
                } else estat = t2("Fet")
            } catch (x: Throwable) { estat = t2("Error de xarxa") }
            treballant = false; versio++
        }.start()
    }

    /** Esborra les portades baixades. */
    fun esborraArt(ctx: Context) { File(dir(ctx), "art").deleteRecursively(); Portades.netejaCache(); versio++; estat = "" }

    private fun t2(s: String) = cat.gbdsmixxx.t(s)
}
