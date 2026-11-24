package cat.gbdsmixxx

import android.content.Context
import java.nio.channels.FileChannel

/** Proveïdor de música de consoles per a OSTixxx Music: les emissores que abans feia `Biblioteca` i la importació de ROM de consola. */
object ProveidorConsolesImpl : ProveidorConsoles {
    init { ModeCarrega.afegeixCarpeta = { c, r, inf, p -> RomsConsoles.afegeixCarpeta(c, r, inf, p) }; I18n.afegeix(TEXTOS_CARREGA) }
    override val extensions = setOf("gb", "gbc", "spc", "gbs", "nes", "sfc", "smc", "nsf", "vgm", "vgz", "cue", "iso")

    override fun emissores(ctx: Context, b: Biblioteca, l: MutableList<Emissora>) {
        val env = b.env
        fun titol(sec: String, codi: String, per: String) = b.titol(sec, codi, per)
        fun catDe(sec: String, codi: String) = b.catDe(sec, codi)
        if (!DriversExtra.nomes) {
        // Game Boy: Cristall, Groc i Trading Card Game (motors portats de pret; les adreces es troben per contingut de la ROM)
        val gbJson: org.json.JSONObject? = try { org.json.JSONObject(env.ctx.assets.open("gb_noms.json").bufferedReader().readText()) } catch (e: Exception) { null }
        val ctxP = env.ctx
        for (codi in RomsConsoles.gbDisponibles(env.ctx)) {
            val j = gbJson ?: continue
            // Les cançons surten de l'índex (`RadioIndex`, «PGB_<codi>», amb el segell del fitxer): la ROM no es llegeix per fer la biblioteca, només en reproduir
            // (i no es reté a l'àlbum: es demana cada vegada a la memòria cau `RomsConsoles.carregaGb`).
            val segell = RomsMusica.segellFitxer(RomsConsoles.fitxerGb(env.ctx, codi))
            val ent = RadioIndex.get(env.ctx, "PGB_$codi", segell) ?: run {
                val rom0 = RomsConsoles.carregaGb(env.ctx, codi) ?: return@run null
                val ids0: List<Int> = when (codi) {
                    "CRY" -> if (rom0.taulaCr < 0) emptyList() else (1 until minOf(j.getJSONArray("CRY").length(), rom0.nCancons)).toList()
                    "TCG" -> RomTcg(rom0.d).let { rt -> if (!rt.esTcg) emptyList() else rt.cancons.map { it.first } }
                    "TCG2" -> RomTcg2(rom0.d).cancons.map { it.first }
                    else -> if (rom0.ones0.isNotEmpty()) listOf(0) else emptyList()
                }
                RadioIndex.Entrada(segell, ids0, emptyMap()).also { RadioIndex.put(env.ctx, "PGB_$codi", it) }
            } ?: continue
            if (ent.ids.isEmpty()) continue
            fun rom(): RomGb = RomsConsoles.carregaGb(ctxP, codi) ?: throw IllegalStateException("ROM no trobada")
            if (codi == "CRY") {
                val noms = j.getJSONArray("CRY")
                l.add(Emissora("CRISTALL", ent.ids, { titol("CRY", noms.optString(it, ""), noms.optString(it, "#$it").replace('_', ' ')) }, { noms.optString(it, "#$it") }, { catDe("CRY", noms.optString(it, "")) }) { SinteCristall(rom(), it, RadioMotor.muts, RadioMotor.solo) }.also { it.iconaNom = "Pokémon Crystal"; it.vista = "Johto" })
            } else if (codi == "TCG") {
                val noms = j.getJSONArray("TCG")
                l.add(Emissora("TCG", ent.ids, { titol("TCG", noms.optString(it, ""), noms.optString(it, "#$it").replace('_', ' ')) }, { noms.optString(it, "#$it") }, { catDe("TCG", noms.optString(it, "")) }) {
                    val rt = RomsConsoles.tcg(ctxP); SinteTcg(rt, rt.cancons.toMap().getValue(it), it, RadioMotor.muts, RadioMotor.solo)
                }.also { it.iconaNom = "Pokémon TCG"; it.vista = "TCG Island" })
            } else if (codi == "TCG2") {
                val noms = j.optJSONArray("TCG2") ?: continue
                l.add(Emissora("TCG2", ent.ids, { titol("TCG2", noms.optString(it, ""), noms.optString(it, "#$it").replace('_', ' ')) }, { noms.optString(it, "#$it") }, { catDe("TCG2", noms.optString(it, "")) }) {
                    val rt = RomsConsoles.tcg2(ctxP); SinteTcg2(rt, rt.cancons.toMap().getValue(it), it, RadioMotor.muts, RadioMotor.solo)
                }.also { it.iconaNom = "Pokémon TCG 2"; it.vista = "Pokémon TCG 2" })
            } else {
                class Can(val banc: Int, val off: Int, val nch: Int, val nom: String, val nota: String, val cod: String)
                val cans = ArrayList<Can>()
                val clauJ = if (codi == "YEL") "YEL" else "RB"           // Red i Blue: mateix motor i mateixa estructura que Yellow, amb 3 bancs d'àudio ($02, $08 i $1F)
                val bancs = j.getJSONArray(clauJ)
                for (bk in 0 until bancs.length()) {
                    val bo = bancs.getJSONObject(bk); val banc = bo.getInt("banc")
                    var off = banc * 0x4000 + 3 * (1 + bo.getInt("sfx"))
                    val cs = bo.getJSONArray("cancons")
                    for (k in 0 until cs.length()) {
                        val c = cs.getJSONObject(k)
                        cans.add(Can(banc, off, c.getInt("c"), c.getString("n").replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " "), c.optString("nota"), c.getString("n")))
                        off += 3 * c.getInt("c")
                    }
                }
                l.add(Emissora(when (codi) { "RED" -> "POKEMON_RED"; "BLU" -> "POKEMON_BLUE"; else -> "GROC" }, cans.indices.toList(), { titol(clauJ, cans[it].cod, cans[it].nom) }, { cans[it].nota.ifEmpty { cans[it].nom } }, { catDe(clauJ, cans[it].cod) }) {
                    val c = cans[it]; SinteGroc(rom(), c.banc, c.off, c.nch, RadioMotor.muts, RadioMotor.solo)
                }.also { it.iconaNom = when (codi) { "RED" -> "Pokémon Red"; "BLU" -> "Pokémon Blue"; else -> "Pokémon Yellow" }; it.vista = "Kanto" })
            }
        }
        }
        if (ModeCarrega.antic) emissoresAntic(ctx, b, l) else emissoresIndex(ctx, b, l)
        if (!DriversExtra.nomes) {
        // Fitxers .nsf (NES): un àlbum per fitxer; la CPU 6502 corre el seu init i el seu play
        for ((nomF, n) in RomsConsoles.nsfLlista(env.ctx)) {
            l.add(Emissora("NSF_$nomF", (1..maxOf(1, n.nCancons)).toList(), { t("Cançó") + " " + it }, { "#$it" }, { 4 }) { SinteNsf(n, it, RadioMotor.muts, RadioMotor.solo) }
                .also { e -> e.vista = n.titol.ifEmpty { "NSF" }; e.altra = true; e.iconaNom = e.vista })
        }
        // Fitxers .vgm: un àlbum per joc (etiqueta GD3), una cançó per fitxer
        for ((joc, llistaJoc) in RomsConsoles.vgmLlista(env.ctx).groupBy { it.second }) {
            l.add(Emissora("VGM_$joc", llistaJoc.indices.toList(), { llistaJoc[it].third }, { llistaJoc[it].third }, { 4 }) {
                val dades = RomsConsoles.carregaVgm(env.ctx, llistaJoc[it].first) ?: throw IllegalStateException("VGM no trobat")
                SinteVgm(VgmArxiu(dades), dades, RadioMotor.muts, RadioMotor.solo)
            }.also { e -> e.vista = joc; e.altra = true; e.iconaNom = joc })
        }
        // Fitxers .gbs: un àlbum per fitxer; la CPU de Game Boy corre el seu init i el seu play
        for ((nomF, g) in RomsConsoles.gbsLlista(env.ctx)) {
            val drv = DriverGbs(g); val img = g.imatge
            l.add(Emissora("GBS_$nomF", drv.ids.toList(), { t("Cançó") + " " + it }, { "#$it" }, { 4 }) { SinteGbCpu(img, drv, it, RadioMotor.muts, RadioMotor.solo) }
                .also { e -> e.vista = g.titol.ifEmpty { "GBS" }; e.altra = true; e.iconaNom = e.vista })
        }
        // PlayStation: un àlbum per disc (temes XA i pistes de CD àudio); la imatge es llegeix d'on és
        for ((clau, titol) in Ps1.llista(env.ctx)) {
            val disc = Ps1.carrega(env.ctx, clau) ?: continue
            l.add(Emissora("PS1_$clau", disc.pistes.indices.toList(), { disc.pistes[it].nom }, { "#" + (it + 1) }, { 4 }) { Ps1.crea(disc.pistes[it], RadioMotor.muts) }
                .also { e -> e.vista = titol; e.altra = true; e.iconaNom = titol })
        }
        // SNES: fitxers .spc; un àlbum per cada títol de joc de les etiquetes
        for ((joc, llistaJoc) in RomsConsoles.spcLlista(env.ctx).groupBy { it.second }) {
            l.add(Emissora("SNES_$joc", llistaJoc.indices.toList(), { llistaJoc[it].third }, { llistaJoc[it].third }, { 4 }) {
                val dades = RomsConsoles.carregaSpc(env.ctx, llistaJoc[it].first) ?: throw IllegalStateException("SPC no trobat")
                SinteSpc(dades, RadioMotor.muts, RadioMotor.solo)
            }.also { e -> e.vista = joc; e.altra = true; e.iconaNom = joc })
        }
        }
    }

    /** Lliga l'àlbum a la seva clau estable (`Biblioteca.clausEstables` i `Emissora.estable`): totes les dades de l'usuari hi van (P1). */
    private fun lliga(b: Biblioteca, x: Emissora, est: String) { b.clausEstables[x.nom] = est; x.estable = est }

    /**
     * P2: al mode antic, la mateixa clau estable que al mode nou («CRC:tipus:clau:rev»), si es coneix el CRC de la ROM i el driver és el registrat amb aquest nom d'àlbum
     * (`RegistreDrivers.carrega(id)` ha de ser el mateix objecte). Si no, l'àlbum es queda amb el nom (com abans). Si dues ROM fan servir el mateix driver, la primera.
     */
    private fun lligaAntic(b: Biblioteca, x: Emissora, sis: SistemaDriver, drv: Any, nomDrv: String) {
        val crc = x.crc ?: return
        if (b.clausEstables.containsKey(x.nom)) return
        val ref = try { RegistreDrivers.perClauAlbum(sis.prefix + nomDrv) } catch (e: Throwable) { null } ?: return
        if (RegistreDrivers.carrega(ref.id) !== drv) return
        lliga(b, x, FacanaDrivers.clauDades(crc, ref.id))
    }

    /** MODE ANTIC (temporal, Ajustos): com abans de l'índex; llegeix i reté les ROM copiades dins l'app. Es treurà quan el mode nou estigui provat. */
    private fun emissoresAntic(ctx: Context, b: Biblioteca, l: MutableList<Emissora>) {
        val env = b.env
        // Jocs de Game Boy amb driver de so executable (la CPU de la consola corre el codi del joc): Link's Awakening...
        val gbsDrv = RomsConsoles.gbDrivers(env.ctx); val driversPerRom = gbsDrv.groupBy { it.second }.mapValues { it.value.size }      // més d'un driver per ROM: els àlbums es diuen «Joc - Driver»
        for ((drv, bytes) in gbsDrv) {
            val titolDrv0 = if (drv is DriverGbAuto) drv.titol else if (drv is DriverGbPoke) drv.titol else when (drv.nom) { "Zelda" -> "Zelda: Link's Awakening"; "Kirby" -> "Kirby's Dream Land"; "Metroid2" -> "Metroid II: Return of Samus"; "MarioLand" -> "Super Mario Land"; "Tetris" -> "Tetris"; "MarioLand2" -> "Super Mario Land 2"; "WarioLand" -> "Wario Land"; "MegaMan2" -> "Mega Man II"; "FFA" -> "Final Fantasy Adventure"; "DonkeyKong94" -> "Donkey Kong '94"; "MarioTennis" -> "Mario Tennis"; "MoleMania" -> "Mole Mania"; "PocketLove" -> "Pocket Love"; "PokePinball" -> "Pokémon Pinball"; "PokePuzzle" -> "Pokémon Puzzle Challenge"; "OracleAges" -> "The Legend of Zelda: Oracle of Ages"; "OracleSeasons" -> "The Legend of Zelda: Oracle of Seasons"; "CarrotCrazy" -> "Looney Tunes: Carrot Crazy"; "BattleTanx" -> "BattleTanx"; "Robopon" -> "Robopon"; "MegaMan1GB" -> "Mega Man: Dr. Wily's Revenge"; "MegaMan3GB" -> "Mega Man III"; "MegaMan4GB" -> "Mega Man IV"; "MegaMan5GB" -> "Mega Man V"; "BionicCommando" -> "Bionic Commando"; "AlfredChicken" -> "Alfred Chicken"; "LionKingGB" -> "The Lion King"; "RType" -> "R-Type"; "RType2" -> "R-Type II"; "AladdinGB" -> "Disney's Aladdin"; "StarWarsGB" -> "Star Wars"; "EarthwormJim" -> "Earthworm Jim"; "Speedball2" -> "Speedball 2: Brutal Deluxe"; "BlasterMaster" -> "Blaster Master: Enemy Below"; "KidIcarusOMM" -> "Kid Icarus: Of Myths and Monsters"; "JungleBook" -> "The Jungle Book"; "PocketKing" -> "Pocket King"; "EarthwormJim2" -> "Earthworm Jim 2"; else -> drv.nom }
            val titolDrv = if ((driversPerRom[bytes] ?: 1) > 1) titolDrv0 + " - " + drv.nom else titolDrv0
            l.add(Emissora("GB_${drv.nom}", drv.ids.toList(), { (when (drv) { is DriverMarioTennis -> drv.NOMS; is R2gDriver -> drv.NOMS; is R2gDosBancs -> drv.NOMS; is DriverJungleBook -> drv.NOMS; is DriverEarthwormJim2 -> drv.NOMS; is DriverGbAuto -> drv.noms; is DriverGbPoke -> drv.noms; else -> null })?.getOrNull(it - drv.ids.first) ?: (t("Cançó") + " " + it) }, { "#$it" }, { 4 }) { SinteGbCpu(bytes, drv, it, RadioMotor.muts, RadioMotor.solo) }
                .also { e -> e.vista = titolDrv; e.altra = true; e.iconaNom = titolDrv0; e.crc = try { crcDe(bytes) } catch (x: Throwable) { null }; e.editor = try { EditorsGb.de(bytes) } catch (x: Throwable) { null } })
            lligaAntic(b, l.last(), SistemaDriver.GB, drv, drv.nom)
        }
        // ROMs de NES amb driver executable (la CPU 6502 corre el codi de so del joc): Super Mario Bros...
        for ((drv, nr) in RomsConsoles.nesDrivers(env.ctx)) {
            val titolDrv = if (drv is DriverNesAuto) drv.titol else when (drv.nom) { "SMB" -> "Super Mario Bros."; "Zelda1" -> "The Legend of Zelda"; "Metroid" -> "Metroid"; "Contra" -> "Contra"; "MegaMan3" -> "Mega Man 3"; "MightyFinalFight" -> "Mighty Final Fight"; "FF1" -> "Final Fantasy"; "MegaMan1" -> "Mega Man"; "MegaMan4" -> "Mega Man 4"; "MegaMan5" -> "Mega Man 5"; "MegaMan6" -> "Mega Man 6"; "Milon" -> "Milon's Secret Castle"; "Zelda2" -> "The Legend of Zelda II: The Adventure of Link"; "Crystalis" -> "Crystalis"; "SMB2" -> "Super Mario Bros. 2"; "SMB3" -> "Super Mario Bros. 3"; "TecmoSB" -> "Tecmo Super Bowl"; "Mother" -> "EarthBound Beginnings"; "MarioBros" -> "Mario Bros."; "TetrisNES" -> "Tetris"; "Elite" -> "Elite"; "Transformers" -> "Transformers: Mystery of Convoy"; "DrMario" -> "Dr. Mario"; "DonkeyKong" -> "Donkey Kong"; "BalloonFight" -> "Balloon Fight"; "DragonWarrior" -> "Dragon Warrior"; "DonaldLand" -> "Donald Land"; "MegaMan2Nes" -> "Mega Man 2"; "SuperC" -> "Super C"; "CaptainAmerica" -> "Captain America and The Avengers"; "FelixCat" -> "Felix the Cat"; "IceClimber" -> "Ice Climber"; "Excitebike" -> "Excitebike"; "NutsMilk" -> "Nuts & Milk"; "SonSon" -> "Son Son"; "AdventureIsland" -> "Adventure Island"; "TecmoWorldCup" -> "Tecmo World Cup Soccer"; "BugsBunny" -> "The Bugs Bunny Crazy Castle"; "LittleMermaid" -> "The Little Mermaid"; "DoubleDragon2" -> "Double Dragon II: The Revenge"; "KunioSoccer" -> "Kunio-kun no Nekketsu Soccer League"; "NinjaGaiden" -> "Ninja Gaiden"; "LegacyWizard" -> "Legacy of the Wizard"; "BubbleBobble2" -> "Bubble Bobble Part 2"; else -> drv.nom }
            l.add(Emissora("NES_${drv.nom}", drv.cancons.indices.map { it + 1 }, { drv.cancons.getOrElse(it - 1) { t("Cançó") + " " + it } }, { "#$it" }, { 4 }) { SinteNesRom(nr, drv, it, RadioMotor.muts, RadioMotor.solo) }
                .also { e -> e.vista = titolDrv; e.altra = true; e.iconaNom = titolDrv; e.crc = RomsConsoles.crcFitxer(env.ctx, "musica_nes_${drv.nom}.nes") })
            lligaAntic(b, l.last(), SistemaDriver.NES, drv, drv.nom)
        }
        // ROMs de SNES amb driver executable (el programa de so de la ROM s'executa a l'SPC700 d'SnesSpc): un àlbum per joc
        for ((drv, sr) in RomsConsoles.snesDrivers(env.ctx)) {
            val titolDrv = TITOLS_SNES[drv.nom] ?: drv.nom
            l.add(Emissora("SNES_${drv.nom}", drv.cancons.indices.map { it + 1 }, { drv.cancons.getOrElse(it - 1) { t("Cançó") + " " + it } }, { "#$it" }, { 4 }) { SinteSnesRom(sr, drv, it, RadioMotor.muts, RadioMotor.solo) }
                .also { e -> e.vista = titolDrv; e.altra = true; e.iconaNom = titolDrv; e.crc = RomsConsoles.crcFitxer(env.ctx, "musica_snes_${drv.nom}.sfc") })
            lligaAntic(b, l.last(), SistemaDriver.SNES, drv, drv.nom)
        }
    }

    /** Títol d'un driver de GB. */
    private fun titolGb(drv: GbDriver): String = if (drv is DriverGbAuto) drv.titol else if (drv is DriverGbPoke) drv.titol else when (drv.nom) { "Zelda" -> "Zelda: Link's Awakening"; "Kirby" -> "Kirby's Dream Land"; "Metroid2" -> "Metroid II: Return of Samus"; "MarioLand" -> "Super Mario Land"; "Tetris" -> "Tetris"; "MarioLand2" -> "Super Mario Land 2"; "WarioLand" -> "Wario Land"; "MegaMan2" -> "Mega Man II"; "FFA" -> "Final Fantasy Adventure"; "DonkeyKong94" -> "Donkey Kong '94"; "MarioTennis" -> "Mario Tennis"; "MoleMania" -> "Mole Mania"; "PocketLove" -> "Pocket Love"; "PokePinball" -> "Pokémon Pinball"; "PokePuzzle" -> "Pokémon Puzzle Challenge"; "OracleAges" -> "The Legend of Zelda: Oracle of Ages"; "OracleSeasons" -> "The Legend of Zelda: Oracle of Seasons"; "CarrotCrazy" -> "Looney Tunes: Carrot Crazy"; "BattleTanx" -> "BattleTanx"; "Robopon" -> "Robopon"; "MegaMan1GB" -> "Mega Man: Dr. Wily's Revenge"; "MegaMan3GB" -> "Mega Man III"; "MegaMan4GB" -> "Mega Man IV"; "MegaMan5GB" -> "Mega Man V"; "BionicCommando" -> "Bionic Commando"; "AlfredChicken" -> "Alfred Chicken"; "LionKingGB" -> "The Lion King"; "RType" -> "R-Type"; "RType2" -> "R-Type II"; "AladdinGB" -> "Disney's Aladdin"; "StarWarsGB" -> "Star Wars"; "EarthwormJim" -> "Earthworm Jim"; "Speedball2" -> "Speedball 2: Brutal Deluxe"; "BlasterMaster" -> "Blaster Master: Enemy Below"; "KidIcarusOMM" -> "Kid Icarus: Of Myths and Monsters"; "JungleBook" -> "The Jungle Book"; "PocketKing" -> "Pocket King"; "EarthwormJim2" -> "Earthworm Jim 2"; else -> drv.nom }
    /** Títol d'un driver de NES. */
    private fun titolNes(drv: NesDriver): String = if (drv is DriverNesAuto) drv.titol else when (drv.nom) { "SMB" -> "Super Mario Bros."; "Zelda1" -> "The Legend of Zelda"; "Metroid" -> "Metroid"; "Contra" -> "Contra"; "MegaMan3" -> "Mega Man 3"; "MightyFinalFight" -> "Mighty Final Fight"; "FF1" -> "Final Fantasy"; "MegaMan1" -> "Mega Man"; "MegaMan4" -> "Mega Man 4"; "MegaMan5" -> "Mega Man 5"; "MegaMan6" -> "Mega Man 6"; "Milon" -> "Milon's Secret Castle"; "Zelda2" -> "The Legend of Zelda II: The Adventure of Link"; "Crystalis" -> "Crystalis"; "SMB2" -> "Super Mario Bros. 2"; "SMB3" -> "Super Mario Bros. 3"; "TecmoSB" -> "Tecmo Super Bowl"; "Mother" -> "EarthBound Beginnings"; "MarioBros" -> "Mario Bros."; "TetrisNES" -> "Tetris"; "Elite" -> "Elite"; "Transformers" -> "Transformers: Mystery of Convoy"; "DrMario" -> "Dr. Mario"; "DonkeyKong" -> "Donkey Kong"; "BalloonFight" -> "Balloon Fight"; "DragonWarrior" -> "Dragon Warrior"; "DonaldLand" -> "Donald Land"; "MegaMan2Nes" -> "Mega Man 2"; "SuperC" -> "Super C"; "CaptainAmerica" -> "Captain America and The Avengers"; "FelixCat" -> "Felix the Cat"; "IceClimber" -> "Ice Climber"; "Excitebike" -> "Excitebike"; "NutsMilk" -> "Nuts & Milk"; "SonSon" -> "Son Son"; "AdventureIsland" -> "Adventure Island"; "TecmoWorldCup" -> "Tecmo World Cup Soccer"; "BugsBunny" -> "The Bugs Bunny Crazy Castle"; "LittleMermaid" -> "The Little Mermaid"; "DoubleDragon2" -> "Double Dragon II: The Revenge"; "KunioSoccer" -> "Kunio-kun no Nekketsu Soccer League"; "NinjaGaiden" -> "Ninja Gaiden"; "LegacyWizard" -> "Legacy of the Wizard"; "BubbleBobble2" -> "Bubble Bobble Part 2"; else -> drv.nom }
    private fun etiqGb(drv: GbDriver): (Int) -> String = { (when (drv) { is DriverMarioTennis -> drv.NOMS; is R2gDriver -> drv.NOMS; is R2gDosBancs -> drv.NOMS; is DriverJungleBook -> drv.NOMS; is DriverEarthwormJim2 -> drv.NOMS; is DriverGbAuto -> drv.noms; is DriverGbPoke -> drv.noms; else -> null })?.getOrNull(it - drv.ids.first) ?: (t("Cançó") + " " + it) }

    /**
     * Bloc de càrrega (2026-11-16): àlbums de GB/NES/SNES a partir de l'índex binari (`IndexRoms`), SENSE llegir cap ROM (només les que tenen drivers nous per avaluar).
     * Cada àlbum guarda una `RomRef` (no els bytes): la ROM es llegeix en tocar una cançó, a través de la memòria cau limitada per bytes. Si la ROM ja no hi és, l'àlbum
     * surt de la biblioteca. Les dades de l'usuari es lliguen a «CRC:tipus:clau» (`Biblioteca.clausEstables`).
     */
    private fun emissoresIndex(ctx: Context, b: Biblioteca, l: MutableList<Emissora>) {
        val app = ctx.applicationContext
        val (conj, entrades) = RomsConsoles.preparaIndex(app)
        // catàleg definitiu: les ROM que tornen a ser a l'índex ja no «falten»
        CatalegConsoles.inicia(app)
        try { val fl = CatalegConsoles.falten(app); if (fl.poda(RomsConsoles.index(app)) > 0) fl.desa() } catch (x: Throwable) { }
        // les internes primer (les que ja hi eren abans de l'índex conserven el nom d'àlbum de sempre); després pel CRC
        val ordre = entrades.sortedWith(compareBy<EntradaRom>({ if (it.interna) 0 else 1 }, { it.crc }))
        val usats = HashSet<String>()
        fun nomUnic(base: String, crc: Long): String { if (usats.add(base)) return base; val n = base + "~%08X".format(crc); usats.add(n); return n }
        for (e in ordre) {
            val ref = e.ref() ?: continue
            val ids = RomsConsoles.driversDe(app, conj, e)
            if (ids.isEmpty()) continue                      // sense driver: a l'índex, però sense àlbum (sortirà sol quan n'hi hagi)
            // catàleg definitiu (l'autor, 2026-11-22): nom del joc i pistes (descartades amagades; si és definitiu, ordre i noms) per a l'àlbum del driver catalogat
            val cat = try { CatalegDefinitiu.troba(e.crc, e.mida) } catch (x: Throwable) { null }
            fun pistes(id: String, ids0: List<Int>, orig: (Int) -> String): CatalegDefinitiu.Pistes =
                if (cat != null && cat.driver == id) CatalegDefinitiu.aplicaPistes(cat, ids0, orig) { k -> t(k) } else CatalegDefinitiu.Pistes(ids0, orig)
            fun nomJoc(t0: String) = cat?.nom?.ifBlank { null } ?: t0
            // [drivers 2026-11-16] tots els drivers que reconeixen la ROM són visibles (també a NES); nom «Joc - Custom/Auto/Exp» (EtiquetaDriver) si n'hi ha més d'un;
            // les dades de l'usuari van a «CRC:tipus:clau:rev» (FacanaDrivers.clauDades: una rev nova = sense marca)
            val refs = ids.mapNotNull { conj.ref(it) }.sortedBy { it.ordre }
            for (r in refs) {
                val id = r.id
                when (e.consola) {
                    "GB" -> {
                        val drv = conj.gb(id) ?: continue
                        val titol0 = nomJoc(titolGb(drv))
                        val nom = nomUnic("GB_${drv.nom}", e.crc)
                        val ps = pistes(id, drv.ids.toList(), etiqGb(drv))
                        l.add(Emissora(nom, ps.ids, ps.etiqueta, { "#$it" }, { 4 }) { id -> val rom = RomsConsoles.obre(app, ref)
                            // si hi ha la partitura d'aquest joc (gravada per un analitzador) i la ROM és la seva, només es sintetitza amb l'APU (sense CPU ni driver)
                            PartituresApp.canco(app, e.crc, e.mida.toInt(), rom, r.id, id)?.let { SintePartGb(rom, it, RadioMotor.muts, RadioMotor.solo) }
                                ?: SinteGbCpu(rom, drv, id, RadioMotor.muts, RadioMotor.solo) }
                            .also { x -> x.vista = EtiquetaDriver.nom(titol0, refs, r, EtiquetaDriver.mostra) { k -> t(k) } + candidat(id); x.altra = true; x.iconaNom = titol0; x.crc = e.crc; x.mida = e.mida; x.editor = e.editor })
                        lliga(b, l.last(), FacanaDrivers.clauDades(e.crc, id))
                    }
                    "NES" -> {
                        val drv = conj.nes(id) ?: continue
                        val titol = nomJoc(titolNes(drv))
                        val nom = nomUnic("NES_${drv.nom}", e.crc)
                        val ps = pistes(id, drv.cancons.indices.map { it + 1 }) { drv.cancons.getOrElse(it - 1) { t("Cançó") + " " + it } }
                        l.add(Emissora(nom, ps.ids, ps.etiqueta, { "#$it" }, { 4 }) { id -> val nr = RomsConsoles.obreNes(app, ref)
                            // si hi ha la partitura d'aquest joc (gravada per un analitzador) i la ROM és la seva, només es sintetitza amb l'APU (sense CPU ni driver)
                            PartituresApp.canco(app, e.crc, e.mida.toInt(), nr.d, r.id, id)?.let { SinteNesPart(nr, it, RadioMotor.muts, RadioMotor.solo) }
                                ?: SinteNesRom(nr, drv, id, RadioMotor.muts, RadioMotor.solo) }
                            .also { x -> x.vista = EtiquetaDriver.nom(titol, refs, r, EtiquetaDriver.mostra) { k -> t(k) } + candidat(id); x.altra = true; x.iconaNom = titol; x.crc = e.crc; x.mida = e.mida })
                        lliga(b, l.last(), FacanaDrivers.clauDades(e.crc, id))
                    }
                    "SNES" -> {
                        // un àlbum per driver (Super Mario All-Stars en porta tres jocs)
                        val drv = conj.snes(id) ?: continue
                        val titol = nomJoc(TITOLS_SNES[drv.nom] ?: drv.nom)
                        val nom = nomUnic("SNES_${drv.nom}", e.crc)
                        val ps = pistes(id, drv.cancons.indices.map { it + 1 }) { drv.cancons.getOrElse(it - 1) { t("Cançó") + " " + it } }
                        l.add(Emissora(nom, ps.ids, ps.etiqueta, { "#$it" }, { 4 }) { SinteSnesRom(RomsConsoles.obreSnes(app, ref), drv, it, RadioMotor.muts, RadioMotor.solo) }
                            .also { x -> x.vista = EtiquetaDriver.nom(titol, refs, r, EtiquetaDriver.mostra) { k -> t(k) }; x.altra = true; x.iconaNom = titol; x.crc = e.crc; x.mida = e.mida })
                        lliga(b, l.last(), FacanaDrivers.clauDades(e.crc, id))
                    }
                }
            }
        }
    }

    /** Sufix dels àlbums amb un driver candidat de «Prova compatibilitat» (sense validar). */
    private fun candidat(id: String): String = if (RegistreDrivers.esCandidat(id)) " · " + t("Auto (sense validar)") else ""

    override fun importa(ctx: Context, uri: android.net.Uri, ch: FileChannel, h: ByteArray) = RomsConsoles.importa(ctx, uri, ch, h)
    override fun buidaCaches() = RomsConsoles.buidaCaches()

    /** P3 (l'autor, 2026-11-22): les marques «provat» d'abans (`confirmatsAbans`) passen a ✓ dels àlbums d'aquests drivers, una sola vegada per (CRC, id). */
    override fun despresBiblioteca(b: Biblioteca, du: MagatzemUsuari) {
        val conf = RegistreDrivers.confirmatsAbans().map { it.id }.toSet()
        if (conf.isEmpty()) { catalegLazy(b, du); return }
        val l = HashMap(b.clausEstables).mapNotNull { (nom, est) -> if (est.substringAfter(':') in conf) est to (b.perNom(nom)?.vista ?: nom) else null }
        val n = du.migraConfirmats(l)
        if (n > 0) App.traca?.invoke("marques: $n àlbums marcats ✓ des dels «provat» d'abans")
        catalegLazy(b, du)
    }
    /** Catàleg definitiu: els àlbums que funcionen (✓ o «provat» d'abans) i encara no hi són s'hi afegeixen (sense validar). */
    private fun catalegLazy(b: Biblioteca, du: MagatzemUsuari) {
        try { CatalegConsoles.inicia(b.env.ctx); val n = CatalegConsoles.despresBiblioteca(b.env.ctx, b, du); if (n > 0) App.traca?.invoke("catàleg: $n jocs nous") }
        catch (e: Throwable) { App.traca?.invoke("catàleg: error " + e.javaClass.simpleName) }
    }
}
