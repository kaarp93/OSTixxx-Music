package cat.ostixxx.music

import android.content.Context
import cat.gbdsmixxx.Biblioteca
import cat.gbdsmixxx.Emissora
import cat.gbdsmixxx.FiltreCancons
import cat.gbdsmixxx.RadioDades
import cat.gbdsmixxx.cancons

/**
 * Llistes automàtiques (l'autor, 2026-11-16: «compilacions»): no són botons ni filtres, són llistes de cançons amb nom propi que surten soles combinant les dades de libretro de cada joc:
 * saga, empresa, gènere, consola (8 Bit / 16 Bit...) i dècada. Un algoritme genera totes les combinacions i es queda només amb les que tenen prou jocs i no repeteixen el mateix conjunt:
 *  - Sagues: una per saga (2 jocs o més);
 *  - Empreses: l'empresa, l'empresa × consola i l'empresa × dècada (3 jocs o més);
 *  - Gèneres: el gènere, el gènere × consola, el gènere × dècada i «Inicis dels …» (els primers jocs del gènere per any).
 * Les empreses es normalitzen (Square + Squaresoft + Enix = Square Enix) amb una taula d'equivalències curta (`EMPRESES`), que es pot ampliar.
 */
object AutoLlistes {
    /** Equivalències d'empresa: si el nom comença o és igual a una clau (sense majúscules), és aquesta empresa. Només les que són segures. */
    private val EMPRESES = listOf(
        "square enix" to "Square Enix", "squaresoft" to "Square Enix", "square" to "Square Enix", "enix" to "Square Enix",
        "nintendo" to "Nintendo", "konami" to "Konami", "capcom" to "Capcom", "sega" to "Sega", "namco" to "Namco", "taito" to "Taito",
        "hudson" to "Hudson Soft", "tecmo" to "Tecmo", "koei" to "Koei", "rare" to "Rare", "hal " to "HAL Laboratory", "bandai" to "Bandai", "atlus" to "Atlus", "natsume" to "Natsume")
    private val SUFIXOS = Regex("[,.]?\\s*(co\\.?,?\\s*)?(ltd\\.?|inc\\.?|corp\\.?|corporation|limited)$", RegexOption.IGNORE_CASE)
    fun empresa(brut: String): String {
        val b = brut.trim().replace(SUFIXOS, "").trim(); if (b.isEmpty()) return ""
        val l = b.lowercase()
        for ((k, v) in EMPRESES) if (l == k.trim() || l.startsWith(k)) return v
        return b
    }
    private fun genere(brut: String): String { val g = brut.substringBefore(" / ").trim(); return if (g.startsWith("Role-playing")) "RPG" else g }
    private fun consola(e: Emissora): String = when (consolaDe(e)) { "GB", "GBC", "NES" -> "8 Bit"; "SNES" -> "16 Bit"; "GBA" -> "Game Boy Advance"; "DS" -> "Nintendo DS"; else -> "" }
    private fun decada(any: Int): String = if (any < 1980) "" else when { any < 1990 -> "Anys 80"; any < 2000 -> "Anys 90"; any < 2010 -> "Anys 2000"; else -> "Anys 2010" }

    /** Paraules que no serveixen per deduir una saga pel títol (el primer mot del títol quan no hi ha dada de libretro). */
    private val GENERIQUES = setOf("super", "the", "game", "new", "mega", "pro", "world", "battle", "grand", "great", "little", "big", "first")
    private fun sagaPerTitol(titol: String): String {
        val w = titol.split(Regex("[^\\p{L}0-9]+")).firstOrNull { it.isNotEmpty() } ?: return ""
        return if (w.length < 5 || w.lowercase() in GENERIQUES || w.all { it.isDigit() }) "" else w.replaceFirstChar { it.uppercase() }
    }
    private class Joc(val e: Emissora, val emp: String, val saga: String, val sagaTitol: Boolean, val gen: String, val cons: String, val dec: String, val any: Int)
    private class Cand(val grup: String, val nom: String, val jocs: List<Joc>, val especificitat: Int)

    // ---- Persistència: les llistes es desen (en binari, `Durable`: atòmic, amb `.bak`, CRC i versió; magic «OXAL», v1) a `files/libretro/llistes_auto.bin` i només es tornen
    // a generar si canvia la signatura (jocs, dades de libretro o versió de l'app). Les últimes aplicades es guarden aquí i a `Biblioteca.heretades`: una biblioteca
    // nova (refeta per un import, un driver...) les té de seguida, encara que la signatura no hagi canviat (abans es quedava sense llistes fins que es reiniciava l'app).
    @Volatile private var sigAplicada = ""
    @Volatile private var ultimes: List<Biblioteca.LlistaAuto> = emptyList()
    @Volatile private var treballant = false
    private val MAGIC = cat.gbdsmixxx.Durable.magic("OXAL")
    private fun fitxer(ctx: Context) = java.io.File(java.io.File(ctx.filesDir, "libretro").also { it.mkdirs() }, "llistes_auto.bin")
    /** Signatura del que determina les llistes: els jocs del mòbil, les dades de libretro baixades (índexs binaris) i la versió de l'app. Va per la clau de dades (P1): les llistes desades amb noms d'àlbum es refan una vegada. */
    fun signatura(ctx: Context, b: Biblioteca): String {
        val noms = b.reals().filter { it.ids.isNotEmpty() }.map { it.clauDades }.sorted().joinToString(",").hashCode()
        return "$noms/" + Libretro.signaturaDades(ctx) + "/" + cat.gbdsmixxx.Informe.build
    }
    /** Format (dins `Durable`): UTF(signatura), int n i, per llista, UTF(secció), UTF(nom), int k i k UTF(clau). */
    private fun llegeix(ctx: Context, sig: String): List<Biblioteca.LlistaAuto>? = cat.gbdsmixxx.Durable.llegeixDades(fitxer(ctx), MAGIC, 1) { _, d ->
        if (d.readUTF() != sig) null else {
            val n = d.readInt(); val out = ArrayList<Biblioteca.LlistaAuto>(n)
            repeat(n) { val g = d.readUTF(); val nom = d.readUTF(); val k = d.readInt(); out.add(Biblioteca.LlistaAuto(g, nom, List(k) { d.readUTF() })) }
            out
        }
    }
    private fun desa(ctx: Context, sig: String, l: List<Biblioteca.LlistaAuto>) {
        try {
            cat.gbdsmixxx.Durable.escriuDades(fitxer(ctx), MAGIC, 1) { d ->
                d.writeUTF(sig); d.writeInt(l.size)
                for (x in l) { d.writeUTF(x.grup); d.writeUTF(x.nom); d.writeInt(x.claus.size); for (c in x.claus) d.writeUTF(c) }
            }
            java.io.File(java.io.File(ctx.filesDir, "libretro"), "llistes_auto.txt").delete()          // el format antic en text (es regeneren soles)
        } catch (e: Throwable) { }
    }
    /**
     * Carrega les llistes desades si són vàlides i, si no, les genera (tot en un fil apart) i les desa; `aplica` es crida al fil principal. Si la signatura no ha canviat,
     * `aplica` es crida igualment (al moment) amb les últimes llistes: la biblioteca nova se les queda.
     */
    fun carregaOGenera(ctx: Context, b: Biblioteca, forca: Boolean = false, aplica: (List<Biblioteca.LlistaAuto>) -> Unit) {
        if (treballant) return
        val sig = signatura(ctx, b)
        if (!forca && sig == sigAplicada) { if (b.autoLlistes !== ultimes) aplica(ultimes); return }
        treballant = true
        Thread {
            val r = try { llegeix(ctx, sig)?.takeIf { !forca } ?: genera(ctx, b).also { desa(ctx, sig, it) } } catch (e: Throwable) { emptyList() }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                sigAplicada = sig; ultimes = r; Biblioteca.heretades = r; treballant = false; aplica(r)
                // si mentrestant ha arribat una biblioteca nova (la crida d'aleshores s'ha descartat per `treballant`), es torna a mirar amb la d'ara: si no, es quedava amb les llistes de la vella
                val nb = cat.gbdsmixxx.App.bib
                if (nb != null && nb !== b) try { carregaOGenera(ctx, nb, false, aplica) } catch (e: Throwable) { }
            }
        }.start()
    }

    fun genera(ctx: Context, b: Biblioteca): List<Biblioteca.LlistaAuto> {
        // tots els jocs del mòbil: amb les dades de libretro (si s'han baixat) i, si no, amb el que diu la pròpia ROM (consola, empresa de la capçalera, primer mot del títol)
        val jocs = b.reals().filter { it.ids.isNotEmpty() }.map { e ->
            val i = Libretro.info(ctx, e)
            val sagaLib = i?.saga?.trim() ?: ""
            val saga = sagaLib.ifEmpty { sagaPerTitol(titolAlbum(e)) }
            Joc(e, empresa((i?.desenvolupador ?: "").ifBlank { e.editor ?: "" }), saga, sagaLib.isEmpty() && saga.isNotEmpty(), genere(i?.genere ?: ""), consola(e), decada(i?.any ?: 0), i?.any ?: 0)
        }
        val c = ArrayList<Cand>()
        fun grupa(grup: String, esp: Int, min: Int, clau: (Joc) -> String?, nom: (String) -> String = { it }) {
            for ((k, v) in jocs.groupBy { clau(it) ?: "" }) if (k.isNotEmpty() && v.size >= min) c.add(Cand(grup, nom(k), v, esp))
        }
        for ((k, v) in jocs.groupBy { it.saga }) if (k.isNotEmpty() && v.size >= (if (v.all { it.sagaTitol }) 3 else 2)) c.add(Cand("Sagues", k, v, 1))
        grupa("Consoles", 1, 3, { consolaDe(it.e) }, { nomConsola(it) })
        grupa("Empreses", 1, 3, { it.emp })
        grupa("Empreses", 2, 3, { if (it.emp.isEmpty() || it.cons.isEmpty()) null else it.emp + "|" + it.cons }, { it.replace("|", " ") })
        grupa("Empreses", 2, 3, { if (it.emp.isEmpty() || it.dec.isEmpty()) null else it.emp + "|" + it.dec }, { it.replace("|", " ") })
        grupa("Gèneres", 1, 4, { it.gen })
        grupa("Gèneres", 2, 4, { if (it.gen.isEmpty() || it.cons.isEmpty()) null else it.gen + "|" + it.cons }, { it.replace("|", " ") })
        grupa("Gèneres", 2, 4, { if (it.gen.isEmpty() || it.dec.isEmpty()) null else it.gen + "|" + it.dec }, { it.replace("|", " ") })
        // «Inicis dels …»: els primers jocs del gènere per any (només amb prou jocs amb any conegut)
        for ((g, v) in jocs.filter { it.gen.isNotEmpty() && it.any > 0 }.groupBy { it.gen }) if (v.size >= 12) c.add(Cand("Gèneres", "Inicis dels " + g, v.sortedBy { it.any }.take(8), 3))
        // sense repetir el mateix conjunt de jocs: es queda el més específic (nom amb més dimensions)
        val vistos = HashMap<Set<String>, Cand>()
        for (x in c.sortedByDescending { it.especificitat }) vistos.putIfAbsent(x.jocs.map { it.e.nom }.toSet(), x)
        val out = ArrayList<Biblioteca.LlistaAuto>()
        for (grup in listOf("Sagues", "Empreses", "Gèneres", "Consoles")) {
            for (x in vistos.values.filter { it.grup == grup }.sortedWith(compareByDescending<Cand> { it.jocs.size }.thenBy { it.nom }).take(14)) {
                val perJoc = maxOf(4, 400 / x.jocs.size)
                val claus = ArrayList<String>()
                for (j in x.jocs.sortedBy { it.e.nom }) for (id in cancons(b, j.e).filter { !FiltreCancons.esSfx(j.e.nom, it) }.take(perJoc)) claus.add(RadioDades.clau(j.e.clauDades, id))
                if (claus.size >= 4) out.add(Biblioteca.LlistaAuto(grup, x.nom, claus))
            }
        }
        return out
    }
}
