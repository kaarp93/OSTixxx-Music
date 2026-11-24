package cat.gbdsmixxx

/** Totes les cançons que es poden reproduir (jocs de GBA i DS importats, i llistes de l'usuari): emissores, noms, tipus i icones. Sense interfície. */
class Biblioteca(val env: Env) {
    init { RadioFreq.taux = env.int("radio_freq", 32768).takeIf { it in RadioFreq.OPCIONS } ?: 32768 }
    private val sdat = RadioMotor.carrega(env.ctx)

    // Noms de llocs i de temes de mirada de Diamant (assets/radio_noms.json) i noms de cançons de GBA (assets/gba_noms.json)
    private val noms: Pair<Map<String, String>, Map<String, String>> = try {
        val j = org.json.JSONObject(env.ctx.assets.open("radio_noms.json").bufferedReader().readText())
        fun mapa(o: org.json.JSONObject) = o.keys().asSequence().associateWith { o.getString(it) }
        Pair(mapa(j.getJSONObject("lloc")), mapa(j.getJSONObject("mirada")))
    } catch (e: Exception) { Pair(emptyMap(), emptyMap()) }
    private val gbaNoms: Map<String, Map<Int, String>> = try {
        val j = org.json.JSONObject(env.ctx.assets.open("gba_noms.json").bufferedReader().readText())
        j.keys().asSequence().associateWith { k -> val o = j.getJSONObject(k); o.keys().asSequence().associate { it.toInt() to o.getString(it) } }
    } catch (e: Exception) { emptyMap() }

    // Noms de lloc de HeartGold/SoulSilver (assets/hgss_noms.json, del decomp): SEQ_GS_* -> {d: lloc de dia, n: lloc de nit}
    private val hgNoms: Map<String, String> = try {
        val j = org.json.JSONObject(env.ctx.assets.open("hgss_noms.json").bufferedReader().readText())
        j.keys().asSequence().associateWith { k -> val o = j.getJSONObject(k); o.optString("d", o.optString("n")) }
    } catch (e: Exception) { emptyMap() }

    // Títols de les cançons en català, anglès i castellà (assets/radio_titols.json: {secció: {codi: [ca, en, es]}}, escrits per l'autor)
    private val titols: org.json.JSONObject? = try { org.json.JSONObject(env.ctx.assets.open("radio_titols.json").bufferedReader().readText()) } catch (e: Exception) { null }
    fun titol(sec: String, codi: String, per: String): String {
        val a = titols?.optJSONObject(sec)?.optJSONArray(codi) ?: return per
        val v = a.optString(when (I18n.actual) { "ca" -> 0; "es" -> 2; else -> 1 }, "")
        return if (v.isEmpty()) per else v
    }

    // Tipus de cançó (0 combat, 1 rutes/coves/masmorres, 2 entrenadors, 3 ciutats, 4 altres): assets/radio_cat.json, de eines/dades/radio_cat.py
    private val cats: org.json.JSONObject? = try { org.json.JSONObject(env.ctx.assets.open("radio_cat.json").bufferedReader().readText()) } catch (e: Exception) { null }
    fun catDe(sec: String, codi: String): Int {
        val v = cats?.optJSONObject(sec)?.optInt(codi, -1) ?: -1
        return if (v >= 0) v else catGenerica(codi)
    }
    /** Tipus per al codi de pret quan la cançó no és a radio_cat.json (Platí, Black, White, Black 2, White 2...). */
    private fun catGenerica(c0: String): Int {
        val c = c0.uppercase().removePrefix("P_")
        return when {
            Regex("^(VS|BA_|FIGHT|BATTLE|WIN)").containsMatchIn(c) -> 0
            c.startsWith("EYE") -> 2
            Regex("^(ROAD|R_|D_|DUNGEON|CAVE|N_CASTLE|GEAR_STATION|SAFARI)").containsMatchIn(c) -> 1
            Regex("^(TOWN|CITY|C_|T_|POKECEN|POKESEN|GYM|LABO)").containsMatchIn(c) -> 3
            else -> 4
        }
    }

    private fun codiDiamant(seq: Int) = (sdat?.nom(0, seq) ?: "?").removePrefix("SEQ_")
    private fun etiquetaDiamant(seq: Int): String {
        val c = codiDiamant(seq)
        val dn = if (c.endsWith("_D")) " " + t("(dia)") else if (c.endsWith("_N")) " " + t("(nit)") else ""
        noms.first[c]?.let { return it + dn }
        noms.second[c]?.let { return t("Mirada") + ": " + it }
        val num = c.filter { it.isDigit() }
        return when {
            c.startsWith("TOWN") -> t("Poble") + " " + num.trimStart('0') + dn
            c.startsWith("CITY") -> t("Ciutat") + " " + num.trimStart('0') + dn
            c.startsWith("ROAD") -> t("Ruta") + " " + c.removePrefix("ROAD_").removeSuffix("_D").removeSuffix("_N") + dn
            c.startsWith("BA_") -> t("Combat") + " " + c.removePrefix("BA_")
            c.startsWith("WIN") -> t("Victòria") + " " + c.removePrefix("WIN")
            c.startsWith("FANFA") -> t("Fanfàrria") + " " + num
            c.startsWith("CO_") -> t("Concurs") + " " + c.removePrefix("CO_")
            c.startsWith("D_") -> t("Zona") + " " + c.removePrefix("D_")
            else -> c
        }
    }

    /** Nom d'àlbum -> clau estable de les dades de l'usuari («CRC:tipus:clau» per a les ROM de consola; la posa el proveïdor de consoles). Els que no hi són fan servir el nom. */
    val clausEstables = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Nom de l'àlbum que té ara aquesta clau estable (null si cap). */
    fun nomDeEstable(est: String): String? = clausEstables.entries.firstOrNull { it.value == est }?.key

    var emissores: List<Emissora> = ArrayList<Emissora>().also { l ->
        if (sdat != null && sdat.musica.isNotEmpty())
            l.add(Emissora("DIAMANT", sdat.musica, { titol("DP", codiDiamant(it), etiquetaDiamant(it)) }, { codiDiamant(it) }, { catDe("DP", codiDiamant(it)) }) { Sinte(sdat, it, RadioMotor.muts, RadioMotor.solo) }.also { it.iconaNom = "Pokémon Diamond"; it.sdatVeus = { bk -> sdat to bk }; it.creaMix = { id, vs -> Sinte(sdat, id, RadioMotor.muts, RadioMotor.solo, veus = Array(16) { k -> vs.getOrNull(k)?.let { v -> v.joc.sdatVeus?.invoke(v.canco)?.let { p -> VeuDs(p.first, p.second, v.prog, v.oct) } } }) } })
        // Altres jocs de DS importats (Platí, HeartGold...): mateix format que Diamant; el nom surt de l'arxiu (SYMB) o és «#número» si no en porta.
        // Diamant/Perla ja importats amb l'importador de gràfics no es repeteixen.
        for ((codi, nomJoc) in RomsMusica.sdatDisponibles(env.ctx)) {
            if (sdat != null && (codi.startsWith("ADA") || codi.startsWith("APA"))) continue
            // Les cançons surten de l'índex (RadioIndex); l'SDAT només es carrega en reproduir (o si l'índex falta o el fitxer ha canviat)
            val segell = RomsMusica.segellFitxer(RomsMusica.fitxerSdat(env.ctx, codi))
            val ent = RadioIndex.get(env.ctx, "DSK_$codi", segell) ?: run {
                val sd0 = RomsMusica.carregaSdat(env.ctx, codi) ?: return@run null
                if (sd0.musica.isEmpty()) return@run null
                RadioIndex.Entrada(segell, sd0.musica, sd0.musica.associateWith { sd0.nom(0, it) ?: "" }).also { RadioIndex.put(env.ctx, "DSK_$codi", it) }
            } ?: continue
            if (ent.ids.isEmpty()) continue
            // l'SDAT es demana cada vegada a la memòria cau limitada per bytes (`RomsMusica`): l'àlbum no el reté
            val ctxA = env.ctx
            fun sd(): Sdat = RomsMusica.carregaSdat(ctxA, codi) ?: throw IllegalStateException("SDAT no trobat")
            fun brut(i: Int): String? = ent.noms[i]?.ifEmpty { null }
            fun codiSeq(i: Int) = brut(i)?.removePrefix("SEQ_")?.removePrefix("GS_")?.removePrefix("BGM_") ?: "#$i"
            val hg = codi.startsWith("IPK") || codi.startsWith("IPG")
            fun etiq(i: Int) = if (hg) titol("HGSS", codiSeq(i), brut(i)?.let { hgNoms[it] } ?: codiSeq(i)) else (if (codi.startsWith("ADA") || codi.startsWith("APA")) titol("DP", codiSeq(i), codiSeq(i)) else if (codi.startsWith("IRB") || codi.startsWith("IRA")) titol("BW", codiSeq(i), codiSeq(i)) else if (codi.startsWith("IRE") || codi.startsWith("IRD")) titol("B2W2", codiSeq(i), codiSeq(i)) else codiSeq(i))
            l.add(Emissora(nomJoc.uppercase(), ent.ids, { etiq(it) }, { codiSeq(it) }, { catDe(if (hg) "HGSS" else "DP", codiSeq(it)) }) { Sinte(sd(), it, RadioMotor.muts, RadioMotor.solo) }.also { it.iconaCodi = codi; it.serial = codi; it.iconaNom = "Pokémon $nomJoc"; it.sdatVeus = { bk -> sd() to bk }; it.creaMix = { id, vs -> Sinte(sd(), id, RadioMotor.muts, RadioMotor.solo, veus = Array(16) { k -> vs.getOrNull(k)?.let { v -> v.joc.sdatVeus?.invoke(v.canco)?.let { p -> VeuDs(p.first, p.second, v.prog, v.oct) } } }) } })
        }
        for (codi in RomsMusica.disponibles(env.ctx)) {
            val nm = gbaNoms[codi] ?: emptyMap()
            val segell = RomsMusica.segellFitxer(RomsMusica.fitxer(env.ctx, codi))
            val ent = RadioIndex.get(env.ctx, "GBK_$codi", segell) ?: run {
                val rom0 = RomsMusica.carrega(env.ctx, codi) ?: return@run null
                if (rom0.taula < 0) return@run null
                // Només música: amb els noms de pret, les MUS_*; si no n'hi ha, les cançons de 2 pistes o més (els efectes en tenen una)
                val ids0 = (0 until rom0.nEntrades).filter { i -> rom0.nPistes(i) > 0 && (nm[i]?.startsWith("MUS_") ?: (rom0.nPistes(i) >= 2)) && nm[i] != "MUS_DUMMY" }
                RadioIndex.Entrada(segell, ids0, emptyMap()).also { RadioIndex.put(env.ctx, "GBK_$codi", it) }
            } ?: continue
            val ctxG = env.ctx
            fun rom(): RomGba = RomsMusica.carrega(ctxG, codi) ?: throw IllegalStateException("ROM no trobada")
            l.add(Emissora((RomsMusica.GBA[codi] ?: codi).uppercase(), ent.ids, { titol(if (codi == "BPEE") "BPEE" else "BPGE", nm[it] ?: "", nm[it]?.removePrefix("MUS_") ?: "#$it") }, { nm[it] ?: "#$it" }, { catDe(if (codi == "BPEE") "BPEE" else "BPGE", nm[it] ?: "") }) { SinteGba(rom(), it, RadioMotor.muts, RadioMotor.solo) }.also { it.serial = codi; it.iconaNom = "Pokémon " + (RomsMusica.GBA[codi] ?: codi); it.romVeus = { rom() }; it.creaMix = { id, vs -> SinteGba(rom(), id, RadioMotor.muts, RadioMotor.solo, veus = Array(16) { k -> vs.getOrNull(k)?.let { v -> v.joc.romVeus?.invoke()?.let { r -> VeuGba(r, v.canco, v.prog, v.oct) } } }) } })
        }
        // Música de consoles (Game Boy, NES, SNES...): només si l'app registra el proveïdor (OSTixxx Music)
        Consoles.proveidor?.emissores(env.ctx, this, l)
        // Altres ràdios: jocs de DS desconeguts amb arxiu SDAT (SdatAuto); el títol és el de la bàner i les cançons es diuen pel nom de l'SDAT o pel número
        // Un joc pot portar diversos SDAT (musica_<codi>.sdat, musica_<codi>_2.sdat...): surten junts, en una sola llista. Les cançons del principal
        // conserven el seu número (així no es perden llistes ni noms); les dels altres porten el número de l'SDAT multiplicat per 10000.
        val grups = RomsMusica.altresDisponibles(env.ctx).groupBy { it.first.replace(Regex("_\\d+$"), "") }
        for ((base, membres) in grups) {
            val segell = membres.joinToString("|") { RomsMusica.segellFitxer(RomsMusica.fitxerSdat(env.ctx, it.first)) }
            fun carregaGrup(): List<Sdat> = membres.mapNotNull { (c, _) -> RomsMusica.carregaSdat(env.ctx, c) }.also { ll -> for (x in ll) x.companys = ll.filter { it !== x } }
            val ent = RadioIndex.get(env.ctx, "DSU_$base", segell) ?: run {
                val sds0 = carregaGrup()
                if (sds0.isEmpty()) return@run null
                val ids0 = ArrayList<Int>(); val noms0 = HashMap<Int, String>()
                for ((k, sd) in sds0.withIndex()) {
                    for (i in (try { sd.musicaGen } catch (e: Exception) { emptyList() })) { ids0.add(k * 10000 + i); noms0[k * 10000 + i] = sd.nom(0, i) ?: "" }
                    for (i in sd.musicaStrm) { ids0.add(k * 10000 + 5000 + i); noms0[k * 10000 + 5000 + i] = sd.nom(7, i) ?: "" }      // els streams (àudio gravat) van a partir de 5000
                }
                if (ids0.isEmpty()) return@run null
                RadioIndex.Entrada(segell, ids0, noms0).also { RadioIndex.put(env.ctx, "DSU_$base", it) }
            } ?: continue
            val titolJoc = membres.first().second
            fun sds(): List<Sdat> = carregaGrup()          // cada vegada de la memòria cau per bytes (no es reté a l'àlbum)
            fun brut(id: Int): String? = ent.noms[id]?.ifEmpty { null }
            fun etiq(id: Int): String {
                val k = id / 10000; val i = id % 10000; val b = brut(id)
                val nom = if (i >= 5000) (b?.removePrefix("STRM_")?.replace('_', ' ')?.ifBlank { null } ?: ("STRM " + (i - 4999)))
                    else b?.removePrefix("SEQ_")?.replace('_', ' ')?.ifBlank { null } ?: (t("Cançó") + " " + (i + 1))
                return if (k == 0) nom else "$nom (${k + 1})"
            }
            l.add(Emissora("DS_$base", ent.ids, { etiq(it) }, { id -> brut(id) ?: "#$id" }, { 4 }) {
                if (it % 10000 >= 5000) SinteStrm(sds()[it / 10000], it % 10000 - 5000, RadioMotor.muts, RadioMotor.solo) else Sinte(sds()[it / 10000], it % 10000, RadioMotor.muts, RadioMotor.solo)
            }.also { em ->
                em.vista = titolJoc; em.altra = true; em.iconaCodi = membres.first().first; em.serial = membres.first().first; em.iconaNom = titolJoc
                // Instruments d'una altra cançó (d'aquest joc o d'un altre de DS): no amb els streams, que són àudio gravat
                em.sdatVeus = { bk -> if (bk % 10000 >= 5000) null else sds()[bk / 10000] to bk % 10000 }
                em.creaMix = { id, vs ->
                    if (id % 10000 >= 5000) em.crea(id)
                    else Sinte(sds()[id / 10000], id % 10000, RadioMotor.muts, RadioMotor.solo, veus = Array(16) { k -> vs.getOrNull(k)?.let { v -> v.joc.sdatVeus?.invoke(v.canco)?.let { p -> VeuDs(p.first, p.second, v.prog, v.oct) } } })
                }
            })
        }
        // Altres ràdios, GBA: ROMs amb M4A llegides d'on són al mòbil (no se'n desa cap còpia)
        for ((codi, titolFitxer, uri) in RomsMusica.altresGba(env.ctx)) {
            // el nom és el del fitxer de la ROM (es torna a llegir a cada biblioteca); si no es pot saber, el que es va desar en importar
            val titolJoc = RomsMusica.nomDelFitxer(env.ctx, uri) ?: titolFitxer
            val segell = RomsMusica.segellUri(env.ctx, uri)
            val ent = RadioIndex.get(env.ctx, "GBU_$codi", segell) ?: run {
                val rom0 = RomsMusica.carregaAltreGba(env.ctx, codi) ?: return@run null
                if (rom0.taula < 0) return@run null
                val ids0 = (0 until rom0.nEntrades).filter { rom0.nPistes(it) >= 2 }
                if (ids0.isEmpty()) return@run null
                RadioIndex.Entrada(segell, ids0, emptyMap()).also { RadioIndex.put(env.ctx, "GBU_$codi", it) }
            } ?: continue
            val ctxU = env.ctx
            fun rom(): RomGba = RomsMusica.carregaAltreGba(ctxU, codi) ?: throw IllegalStateException("ROM no trobada")
            l.add(Emissora("GBA_$codi", ent.ids, { t("Cançó") + " " + (it + 1) }, { "#$it" }, { 4 }) { SinteGba(rom(), it, RadioMotor.muts, RadioMotor.solo) }.also { it.serial = codi; it.vista = titolJoc; it.altra = true; it.iconaNom = titolJoc; it.romVeus = { rom() }; it.creaMix = { id, vs -> SinteGba(rom(), id, RadioMotor.muts, RadioMotor.solo, veus = Array(16) { k -> vs.getOrNull(k)?.let { v -> v.joc.romVeus?.invoke()?.let { r -> VeuGba(r, v.canco, v.prog, v.oct) } } }) } })
        }
    }.let { l -> if (Consoles.nomesExperimentals) l.filter { it.nom.startsWith("GB_") || it.nom.startsWith("NES_") || it.nom.startsWith("SNES_") } else l }
    private val NOMS_JOC = mapOf("HEARTGOLD" to "HeartGold", "SOULSILVER" to "SoulSilver", "LEAFGREEN" to "LeafGreen", "FIRERED" to "FireRed", "BLACK" to "Black", "WHITE" to "White", "BLACK_2" to "Black 2", "WHITE_2" to "White 2")
    private val REGIONS = mapOf("DIAMANT" to "Sinnoh", "DIAMANT_PERLA" to "Sinnoh", "PERLA" to "Sinnoh", "PLATI" to "Sinnoh (Platí)", "HEARTGOLD" to "Johto Remakes", "SOULSILVER" to "Johto Remakes",
        "EMERALD" to "Hoenn", "RUBY" to "Hoenn", "SAPPHIRE" to "Hoenn", "LEAFGREEN" to "Kanto Remakes", "FIREREED" to "Kanto Remakes", "FIRERED" to "Kanto Remakes",
        "BLACK" to "Unova", "WHITE" to "Unova", "BLACK_2" to "Unova 2", "WHITE_2" to "Unova 2")
    init {
        for (e in emissores) if (!e.altra) e.vista = REGIONS[e.nom.uppercase().replace(' ', '_')] ?: e.nom
        // Si dues emissores tenen la mateixa regió (p. ex. Emerald i Robí), s'hi afegeix el joc
        for (e in emissores) if (!e.altra && emissores.count { it.vista == e.vista } > 1) e.vista = e.vista + " (" + (NOMS_JOC[e.nom.replace(' ', '_')] ?: e.nom.lowercase().replaceFirstChar { it.uppercase() }) + ")"
    }
    // ---- Llistes de reproducció, noms canviats i cançons ocultes (RadioDades) ----
    fun reals() = emissores.filter { !it.esLlista }
    /** Àlbum real pel nom en O(1) (els àlbums reals no canvien en tota la vida de la biblioteca; `refaLlistes` només refà les llistes). Si n'hi ha dos amb el mateix nom, el primer (com `firstOrNull`). */
    private val realsPerNom: Map<String, Emissora> by lazy { HashMap<String, Emissora>().also { m -> for (e in reals()) m.putIfAbsent(e.nom, e) } }
    /** Àlbum real per la clau de dades (`Emissora.clauDades`: la clau estable dels de consola). */
    private val realsPerClau: Map<String, Emissora> by lazy { HashMap<String, Emissora>().also { m -> for (e in reals()) m.putIfAbsent(e.clauDades, e) } }
    /** Àlbum real per la clau de dades (P1: les claus `àlbum|id` de RadioDades, comptes i llistes) o, si no, pel nom (dades d'abans encara no migrades, preferències). */
    fun perNom(nom: String): Emissora? = realsPerClau[nom] ?: realsPerNom[nom]
    private fun etiqClau(clau: String): String {
        val e = perNom(RadioDades.emissoraDe(clau))
        val nom = RadioDades.noms[clau] ?: e?.etiqueta?.invoke(RadioDades.idDe(clau)) ?: clau
        return if (e != null) (e.iconaNom ?: e.vista) + " - " + nom else nom          // a les llistes, el nom del joc davant de cada cançó (si no, centenars de «Cançó 1»)
    }
    /** Torna a crear les emissores de les llistes de l'usuari (al final de la llista de jocs). */
    fun refaLlistes() {
        emissores = reals() + RadioDades.llistes.mapIndexed { k, l ->
            Emissora("LLISTA_$k", l.cancons.indices.toList(), { i -> etiqClau(l.cancons[i]) }, { i -> l.cancons[i] }, { 4 }) { i ->
                val c = l.cancons[i]
                val e = perNom(RadioDades.emissoraDe(c)) ?: throw IllegalStateException("cançó de la llista no trobada")
                e.crea(RadioDades.idDe(c))
            }.also { it.vista = l.nom; it.esLlista = true; it.llistaIdx = k }
        } + llistaTop() + autoLlistes.mapIndexed { k, a -> llistaAuto("LLISTA_AUTO_$k", a.nom, a.claus).also { it.autoGrup = a.grup } }
    }
    /** Llista automàtica generada (Music: sagues, empreses, gèneres i combinacions): nom, secció de l'Inici i claus `àlbum|id` de les seves cançons. */
    class LlistaAuto(val grup: String, val nom: String, val claus: List<String>)
    /** Les llistes automàtiques d'ara: una biblioteca nova les hereta (`heretades`) i no es queda buida fins que es tornen a generar. */
    @Volatile var autoLlistes: List<LlistaAuto> = heretades
    companion object { @Volatile var heretades: List<LlistaAuto> = emptyList() }
    private fun llistaAuto(id: String, nom: String, ks: List<String>): Emissora =
        Emissora(id, ks.indices.toList(), { i -> etiqClau(ks[i]) }, { i -> ks[i] }, { 4 }) { i ->
            val c = ks[i]; (perNom(RadioDades.emissoraDe(c)) ?: throw IllegalStateException("cançó no trobada")).crea(RadioDades.idDe(c))
        }.also { it.vista = nom; it.esLlista = true; it.llistaIdx = -1; it.autoClaus = ks }
    /** Llista automàtica «Les 50 més reproduïdes» (buida mentre no hi ha reproduccions: aleshores no surt). */
    private fun llistaTop(): List<Emissora> {
        val ks = Comptes.top(50).filter { c -> perNom(RadioDades.emissoraDe(c)) != null && !FiltreCancons.esSfx(RadioDades.emissoraDe(c), RadioDades.idDe(c)) }
        if (ks.isEmpty()) return emptyList()
        return listOf(llistaAuto("LLISTA_TOP50", "Les 50 més reproduïdes", ks))
    }
    init { RadioIndex.desaSiCal(env.ctx); RadioDades.carrega(env.ctx); refaLlistes() }
    /** Clau de la cançó `id` de l'emissora `e` (en una llista, la de la cançó original). */
    fun clauDe(e: Emissora, id: Int): String {
        e.autoClaus?.let { return it.getOrNull(id) ?: "" }
        return if (e.esLlista) RadioDades.llistes.getOrNull(e.llistaIdx)?.cancons?.getOrNull(id) ?: "" else RadioDades.clau(e.clauDades, id)
    }
    fun etiq(e: Emissora, id: Int): String = if (e.esLlista) e.etiqueta(id) else RadioDades.noms[RadioDades.clau(e.clauDades, id)] ?: e.etiqueta(id)
    fun ocult(e: Emissora, id: Int) = RadioDades.ocults.contains(clauDe(e, id))

}
