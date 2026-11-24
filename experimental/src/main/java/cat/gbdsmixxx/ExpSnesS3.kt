package cat.gbdsmixxx

/*
 * Lot S, part 3 (experimental): Plok i Spider-Man & X-Men: Arcade's Revenge. Les dades de so són a una taula de punters («unsorted data»: Plok `Ptrs` a $81:F91D, SXAR Bloc 1 `Ptrs` a $01:B7BC, 4 bytes per entrada). El punter és
 * (banc relatiu * $8000) + (adreça and $7FFF), amb el banc relatiu respecte al banc de la taula (macro `PLOK_UnsortedDataTableEntry` / `SXAR_UnsortedDataTableEntry`): la posició del fitxer és el punter
 * més $8000 (banc 1). DEDUÏT: aquesta conversió, que els blocs de motor/mostres/música són fluxos IPL i que no cal cap ordre (no portat: el joc els puja amb el seu propi flux d'ordres de la
 * funció CODE_87E85C a Plok). Una pista per cada música de la taula. No comprovat amb cap ROM.
 */

internal fun expPunter(rom: RomSnes?, banc: Int, adr: Int, i: Int): Int {
    if (rom == null) return 0
    return rom.u24(banc, adr + 4 * i)
}

private fun expTaulaBlocs(spc: SnesSpc, rom: RomSnes, ofit: Int): Int {
    val d = rom.d
    fun u16(p: Int) = (d[p].toInt() and 255) or ((d[p + 1].toInt() and 255) shl 8)
    var p = ofit; var n = 0
    while (p >= 0 && p + 4 <= d.size && n++ < 512) {
        val len = u16(p); val ad = u16(p + 2); p += 4
        if (len == 0) return ad
        if (p + len > d.size) return -1
        for (k in 0 until len) spc.ram[(ad + k) and 0xFFFF] = d[p + k]
        p += len
    }
    return -1
}

internal fun expTaulaInicia(spc: SnesSpc, rom: RomSnes, banc: Int, adr: Int, base: Int, motor: IntArray, pista: Int) {
    var entrada = -1
    for (i in motor + intArrayOf(pista)) {
        val e = expTaulaBlocs(spc, rom, rom.u24(banc, adr + 4 * i) + base)
        if (entrada < 0 && e >= 0) entrada = e
    }
    spc.arrenca(if (entrada >= 0) entrada else 0x0200)
}

object DriverPlok : SnesDriver {
    override val nom = "Plok"
    private val PISTES = listOf(
        Pair(0, "SPCEngine (Plok)"),
        Pair(2, "SPCSampleBank (Plok)"),
        Pair(3, "TitleScreen (Plok)"),
        Pair(4, "Map (Plok)"),
        Pair(5, "CottonIsland (Plok)"),
        Pair(6, "Akrillic (Plok)"),
        Pair(7, "Beach (Plok)"),
        Pair(8, "PloksHouse (Plok)"),
        Pair(9, "CreepyCrag (Plok)"),
        Pair(10, "Cavern (Plok)"),
        Pair(11, "Boss (Plok)"),
        Pair(12, "Fleapit (Plok)"),
        Pair(13, "BonusLevel (Plok)"),
        Pair(14, "DiggingForAmulet (Plok)"),
        Pair(15, "LegacyIsland (Plok)"),
        Pair(16, "BoxerPlok (Plok)"),
        Pair(17, "ArsonistPlok (Plok)"),
        Pair(18, "CowboyPlok (Plok)"),
        Pair(19, "HunterPlok (Plok)"),
        Pair(20, "RocketeerPlok (Plok)"))
    override val cancons: List<String> = PISTES.map { (i, n) ->
        "$n · taula \$81:F91D[$i]"
    }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "PLOK")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expTaulaInicia(spc, rom, 0x81, 0xF91D, 0x8000, intArrayOf(0, 2), PISTES[id.coerceIn(1, PISTES.size) - 1].first)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}
object DriverSpiderManXMen : SnesDriver {
    override val nom = "SpiderXMen"
    private val PISTES = listOf(
        Pair(95, "SPCEngine"),
        Pair(96, "SPCSampleBank"),
        Pair(97, "CopyOfBriefing"),
        Pair(98, "SpiderManLevel"),
        Pair(99, "StormLevel"),
        Pair(100, "CyclopsLevel"),
        Pair(101, "TitleScreen"),
        Pair(102, "WolverineLevel"),
        Pair(103, "GambitLevel"),
        Pair(104, "HighScore"),
        Pair(105, "Briefing"),
        Pair(106, "Ending"),
        Pair(107, "FinalLevel"),
        Pair(108, "GameOver"))
    override val cancons: List<String> = PISTES.map { (i, n) ->
        "$n · taula \$01:B7BC[$i]"
    }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "X-MEN")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expTaulaInicia(spc, rom, 0x01, 0xB7BC, 0x8000, intArrayOf(95, 96), PISTES[id.coerceIn(1, PISTES.size) - 1].first)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

val EXP_SNES_S3: List<SnesDriver> = listOf(DriverPlok, DriverSpiderManXMen)
val EXP_COMPAT_S3: List<JocCompat> = listOf(
    JocCompat("Plok", "SNES", "SNES_Plok"),
    JocCompat("Spider-Man & X-Men: Arcade's Revenge", "SNES", "SNES_SpiderXMen"))
