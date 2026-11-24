package cat.gbdsmixxx

import java.io.File

/**
 * Formats binaris de les dades petites de l'app (Kotlin pur, es proven a JVM). Tots van per `Durable` (atòmic, `.bak`, CRC32, versió):
 *  - `radio_dades.bin` (magic «OXRD», v1): llistes, noms canviats, ocultes, anàlisi (BPM, tonalitat, bucle), combinacions de canals, tipus de canal, temps màxim i voltes;
 *  - `radio_index.bin` (magic «OXRI», v1): l'índex de cançons dels jocs de GBA i DS;
 *  - taules de files de text (magic «OXTB», v1): `altres_gba.bin`, `vgm/index.bin`, `spc/index.bin` (abans eren TXT).
 */
object DadesBin {
    // ---- radio_dades ----
    class Radio(
        val noms: Map<String, String>, val ocults: Set<String>, val llistes: List<Pair<String, List<String>>>,
        /** bpm, tònica, major (0/1), confiança, bucle, beats */
        val analisi: Map<String, IntArray>, val combos: Map<String, IntArray>, val tipus: Map<String, IntArray>, val tempsMax: Int, val bucles: Int)

    val MAGIC_RADIO = Durable.magic("OXRD")
    private fun escriuRadioA(d: java.io.DataOutputStream, r: Radio) {
        fun mapa(m: Map<String, IntArray>) { d.writeInt(m.size); for ((k, a) in m) { Durable.escriuText(d, k); d.writeInt(a.size); for (x in a) d.writeInt(x) } }
        d.writeInt(r.noms.size); for ((k, v) in r.noms) { Durable.escriuText(d, k); Durable.escriuText(d, v) }
        d.writeInt(r.ocults.size); for (k in r.ocults) Durable.escriuText(d, k)
        d.writeInt(r.llistes.size); for ((n, cs) in r.llistes) { Durable.escriuText(d, n); d.writeInt(cs.size); for (c in cs) Durable.escriuText(d, c) }
        mapa(r.analisi); mapa(r.combos); mapa(r.tipus)
        d.writeInt(r.tempsMax); d.writeInt(r.bucles)
    }
    private fun llegeixRadioDe(d: java.io.DataInputStream): Radio {
        fun mapa(): Map<String, IntArray> { val m = LinkedHashMap<String, IntArray>(); repeat(d.readInt()) { val k = Durable.llegeixText(d); val n = d.readInt(); require(n in 0..1024); m[k] = IntArray(n) { d.readInt() } }; return m }
        val noms = LinkedHashMap<String, String>(); repeat(d.readInt()) { noms[Durable.llegeixText(d)] = Durable.llegeixText(d) }
        val ocults = LinkedHashSet<String>(); repeat(d.readInt()) { ocults.add(Durable.llegeixText(d)) }
        val llistes = ArrayList<Pair<String, List<String>>>(); repeat(d.readInt()) { val n = Durable.llegeixText(d); val k = d.readInt(); llistes.add(n to List(k) { Durable.llegeixText(d) }) }
        val an = mapa(); val co = mapa(); val ti = mapa()
        return Radio(noms, ocults, llistes, an, co, ti, d.readInt(), d.readInt())
    }
    fun escriuRadio(f: File, r: Radio) = Durable.escriuDades(f, MAGIC_RADIO, 1) { d -> escriuRadioA(d, r) }
    fun llegeixRadio(f: File): Radio? = Durable.llegeixDades(f, MAGIC_RADIO, 1) { _, d -> llegeixRadioDe(d) }
    /** Secció «RDIO» de la còpia `.ostx` (mateixa càrrega que `radio_dades.bin` v1). */
    fun radioBytes(r: Radio): ByteArray { val bo = java.io.ByteArrayOutputStream(); java.io.DataOutputStream(bo).use { escriuRadioA(it, r) }; return bo.toByteArray() }
    fun radioDeBytes(b: ByteArray): Radio = llegeixRadioDe(java.io.DataInputStream(java.io.ByteArrayInputStream(b)))
    /** Quantes dades porta una ràdio (per al resum de la còpia). */
    fun compta(r: Radio): Int = r.noms.size + r.ocults.size + r.llistes.size + r.analisi.size + r.combos.size + r.tipus.size

    /**
     * Fusiona la ràdio importada `imp` amb la d'aquí (`local`). Sense `substitueix`: noms, anàlisi, combinacions i tipus que aquí no hi són s'afegeixen (si ja hi són, es
     * queda la d'aquí); ocultes, unió; una llista amb el mateix nom rep les cançons que li falten (al final, sense duplicar) i una de nova s'afegeix; temps màxim i voltes,
     * els d'aquí si no són 0. Amb `substitueix`, el resultat és la importada. Retorna (ràdio resultant, nombre de canvis). Res no s'esborra en fusionar.
     */
    fun fusionaRadio(local: Radio, imp: Radio, substitueix: Boolean): Pair<Radio, Int> {
        if (substitueix) return imp to compta(imp)
        var n = 0
        fun <V> uneix(a: Map<String, V>, b: Map<String, V>): Map<String, V> { val o = LinkedHashMap(a); for ((k, v) in b) if (!o.containsKey(k)) { o[k] = v; n++ }; return o }
        val noms = uneix(local.noms, imp.noms)
        val ocults = LinkedHashSet(local.ocults); for (k in imp.ocults) if (ocults.add(k)) n++
        val llistes = local.llistes.map { it.first to it.second.toMutableList() }.toMutableList()
        for ((nom, cs) in imp.llistes) {
            val ara = llistes.firstOrNull { it.first == nom }
            if (ara == null) { llistes.add(nom to cs.toMutableList()); n++ }
            else for (c in cs) if (c !in ara.second) { ara.second.add(c); n++ }
        }
        val r = Radio(noms, ocults, llistes, uneix(local.analisi, imp.analisi), uneix(local.combos, imp.combos), uneix(local.tipus, imp.tipus),
            if (local.tempsMax != 0) local.tempsMax else imp.tempsMax, if (local.bucles != 0) local.bucles else imp.bucles)
        return r to n
    }

    // ---- radio_index ----
    class EntradaIndex(val segell: String, val ids: List<Int>, val noms: Map<Int, String>)
    val MAGIC_INDEX = Durable.magic("OXRI")
    fun escriuIndex(f: File, m: Map<String, EntradaIndex>) = Durable.escriuDades(f, MAGIC_INDEX, 1) { d ->
        d.writeInt(m.size)
        for ((k, e) in m) {
            Durable.escriuText(d, k); Durable.escriuText(d, e.segell)
            d.writeInt(e.ids.size); for (x in e.ids) d.writeInt(x)
            d.writeInt(e.noms.size); for ((id, n) in e.noms) { d.writeInt(id); Durable.escriuText(d, n) }
        }
    }
    fun llegeixIndex(f: File): Map<String, EntradaIndex>? = Durable.llegeixDades(f, MAGIC_INDEX, 1) { _, d ->
        val m = LinkedHashMap<String, EntradaIndex>()
        repeat(d.readInt()) {
            val k = Durable.llegeixText(d); val s = Durable.llegeixText(d)
            val ids = List(d.readInt()) { d.readInt() }
            val noms = HashMap<Int, String>(); repeat(d.readInt()) { noms[d.readInt()] = Durable.llegeixText(d) }
            m[k] = EntradaIndex(s, ids, noms)
        }
        m
    }

    // ---- taules de files (abans TXT separats per tabuladors) ----
    val MAGIC_TAULA = Durable.magic("OXTB")
    fun escriuTaula(f: File, files: List<List<String>>) = Durable.escriuDades(f, MAGIC_TAULA, 1) { d ->
        d.writeInt(files.size); for (r in files) { d.writeInt(r.size); for (c in r) Durable.escriuText(d, c) }
    }
    fun llegeixTaula(f: File): List<List<String>>? = Durable.llegeixDades(f, MAGIC_TAULA, 1) { _, d -> List(d.readInt()) { List(d.readInt()) { Durable.llegeixText(d) } } }

    /**
     * Taula `bin` amb migració des del TXT antic `txt` (files separades per salts de línia i columnes per tabuladors): si el binari no hi és i el TXT sí, es passa
     * al binari, es comprova llegint-lo i el TXT es reanomena a `.migrat` (no s'esborra). Retorna les files (buit si no n'hi ha cap).
     */
    fun taulaAmbMigracio(bin: File, txt: File): List<List<String>> {
        llegeixTaula(bin)?.let { return it }
        if (!txt.exists()) return emptyList()
        val files = try { txt.readLines().filter { it.isNotEmpty() }.map { it.split('\t') } } catch (e: Throwable) { return emptyList() }
        try {
            escriuTaula(bin, files)
            if (llegeixTaula(bin) == files) txt.renameTo(File(txt.path + ".migrat"))
        } catch (e: Throwable) { }
        return files
    }

    // ---- P1 (l'autor, 2026-11-22): claus «àlbum|id» de radio_dades, del nom de l'àlbum a la clau estable ----
    /**
     * Passa a la clau estable (`estable`: nom de l'àlbum -> «CRC:tipus:clau:rev») les claus «àlbum|id» que encara porten el nom d'un àlbum que ara en té, DINS de les mateixes
     * col·leccions (les llistes no canvien d'objecte). Res no s'esborra: si la clau estable ja té un valor (més nou), el del nom es queda on és; un nom que no és a `estable`
     * (ROM absent) no es toca i es migrarà quan torni. A les ocultes, una clau duplicada es fusiona. Idempotent: una segona crida no canvia res. Retorna quantes claus ha canviat.
     */
    fun migraClausRadio(estable: Map<String, String>, noms: MutableMap<String, String>, ocults: MutableSet<String>, llistes: List<MutableList<String>>, mapes: List<MutableMap<String, *>>): Int {
        fun nova(k: String): String? {
            val i = k.lastIndexOf('|'); if (i <= 0) return null
            val e = estable[k.substring(0, i)] ?: return null
            return if (e == k.substring(0, i)) null else e + k.substring(i)
        }
        var n = 0
        @Suppress("UNCHECKED_CAST")
        fun <V> mou(m: MutableMap<String, V>) {
            for (k in m.keys.toList()) { val nk = nova(k) ?: continue; if (m.containsKey(nk)) continue; val v = m[k] ?: continue; m[nk] = v; m.remove(k); n++ }
        }
        mou(noms)
        for (m in mapes) mou(m as MutableMap<String, Any?>)
        for (k in ocults.toList()) { val nk = nova(k) ?: continue; ocults.add(nk); ocults.remove(k); n++ }
        for (l in llistes) for (j in l.indices) { val nk = nova(l[j]) ?: continue; l[j] = nk; n++ }
        return n
    }
}
