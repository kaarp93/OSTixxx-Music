package cat.gbdsmixxx

/*
 * Lot N, fitxer 2: jocs de cyneprepou4uk/NES-Games-Disassembly de motor d'una sola pàgina (NROM de 16 KB). Verificats assemblant el desassemblatge amb ca65/ld65.
 */

private fun flagsN(base: Int, n: Int, prefix: String) = (0 until n).map { NP("$prefix \$${hN(base + it, 4)}", NR(base + it)) }

/**
 * Pac-Man (NES, NROM de 16 KB). `sub_EE18` ($EE18, arrenca el motor: punters, $4015/$4017 i neteja) i `sub_EE5C_update_sound_engine` ($EE5C, a cada fotograma des de la NMI). Els efectes i les
 * músiques es demanen posant una bandera diferent de 0 a `ram_sfx` ($0600-$060F); el desassemblatge només dona nom a `plr_ready` ($0600-$0601, música d'inici), `death` ($0603),
 * `eat_pellet` ($0604), `eat_fruit` ($0606) i `eat_ghost` ($0607). Hi ha una pista per bandera i els grups que el joc posa junts. Verificat amb ROM assemblada.
 */
internal val ExpNesPacMan = ExpNesRam(
    "PacManN",
    listOf(NP("Preparats (música d'inici) · \$0600+\$0601", NR(0x600), NR(0x601)), NP("Pellets · \$0604+\$0605", NR(0x604), NR(0x605)), NP("So \$060D+\$060E (grup, \$E663)", NR(0x60D), NR(0x60E))) +
        (0 until 16).map { NP((mapOf(0 to "Preparats", 3 to "Mort", 4 to "Menjar pellet", 6 to "Menjar fruita", 7 to "Menjar fantasma")[it] ?: "So") + " · \$06${hN(it)}", NR(0x600 + it)) },
    { r -> r.mapper == 0 && r.prgBancs == 1 && r.cerca(sigN("A9 00 85 F8 85 F9 85 FA 85 FB 85 FD 85 FF A9 F8 85 FE")) == 0x2E5C },
    { cpu, _, _ -> cpu.crida(0xEE18) },
    { cpu, _, _, _ -> cpu.crida(0xEE5C) },
)

/**
 * Mappy (NES, NROM de 16 KB). `sub_F4BF` ($F4BF, neteja les ranures i copia `tbl_F89E` a $06E2) i `sub_F4F1_update_sound_engine` ($F4F1, a cada fotograma; `ram_0023_flag` ha de ser 0). 25 banderes
 * a `ram_sounds` ($0600-$0618): el desassemblatge no els dona nom (`ram_sfx_0600`...). Una pista per bandera, i els grups que el joc posa junts (trios/parells de `STA` consecutius: $0602-$0604 a $D6CF,
 * $060B-$060D a $DB62, $060E-$0610 a $CD6D, $0611-$0612 a $C98F, $0613-$0614 a $C95B, $0615-$0617 a $CAC5, $0601+$0618 a $CE33). Verificat amb ROM assemblada.
 */
internal val ExpNesMappy = ExpNesRam(
    "MappyN",
    listOf(NP("Grup \$0602+\$0603+\$0604 (\$D6CF)", NR(0x602), NR(0x603), NR(0x604)), NP("Grup \$060B+\$060C+\$060D (\$DB62)", NR(0x60B), NR(0x60C), NR(0x60D)), NP("Grup \$060E+\$060F+\$0610 (\$CD6D)", NR(0x60E), NR(0x60F), NR(0x610)),
        NP("Grup \$0611+\$0612 (\$C98F)", NR(0x611), NR(0x612)), NP("Grup \$0613+\$0614 (\$C95B)", NR(0x613), NR(0x614)), NP("Grup \$0615+\$0616+\$0617 (\$CAC5)", NR(0x615), NR(0x616), NR(0x617)), NP("Grup \$0601+\$0618 (\$CE33)", NR(0x601), NR(0x618))) +
        flagsN(0x600, 25, "So"),
    { r -> r.mapper == 0 && r.prgBancs == 1 && r.cerca(sigN("A5 23 D0 06 A9 19 85 F5 10 0E A9 01 85 F5 A9 10 8D 04 40")) == 0x34F1 },
    { cpu, m, _ -> m.ram[0x23] = 0; cpu.crida(0xF4BF) },
    { cpu, m, _, _ -> m.ram[0x23] = 0; cpu.crida(0xF4F1) },
)

/**
 * Tennis (NES, NROM de 16 KB). `sub_FFF7` ($FFF7, `jmp loc_F8E0`) és la passada de so per fotograma (la NMI hi crida; al final posa a 0 les peticions `ram_00FD`, `ram_00FE` i `ram_00FF`). Les músiques es demanen
 * posant un bit a `ram_00FD` (el bit més alt actiu tria l'entrada de `tbl_FDFA_music` ($FDFA, 8 entrades: $FE02, $FE07, $FE0C, $FE11 (dues vegades), $FE16, $FE1B, $FE20); el joc posa el bit 7 a $C0F4). Els
 * efectes són bits de `ram_00FF` i `ram_00FE`. No hi ha noms. Les 8 pistes de música més els 16 bits d'efecte. Verificat amb ROM assemblada.
 */
internal val ExpNesTennis = ExpNesRam(
    "TennisN",
    (0 until 8).map { NP("Música bit $it · \$FD · entrada \$FE" + hN(listOf(0x02, 0x07, 0x0C, 0x11, 0x11, 0x16, 0x1B, 0x20)[it]), NR(0xFD, 1 shl it)) } +
        (0 until 8).map { NP("Efecte \$FF bit $it", NR(0xFF, 1 shl it)) } + (0 until 8).map { NP("Efecte \$FE bit $it", NR(0xFE, 1 shl it)) },
    { r -> r.mapper == 0 && r.prgBancs == 1 && r.cerca(sigN("A6 FA D0 3D AE F8 07 D0 97 A5 FF 4A B0 88")) == 0x3A39 },
    { _, m, _ -> m.ram[0xFA] = 0; m.ram[0x7F8] = 0 },
    { cpu, m, _, _ -> m.ram[0xFA] = 0; cpu.crida(0xFFF7) },
)

/**
 * Yie Ar Kung-Fu (NES, NROM de 16 KB). `sub_CE45` ($CE45) demana un so amb A = canal (bits 6-7: 0 a 3 = $4000/$4004/$4008/$400C) + identificador (bits 0-5; 0 = silenci del canal) i `sub_CEF9` ($CEF9) és la passada per
 * fotograma (`ram_0067` ha de ser 0). `tbl_CB42_sound_and_music_data` ($CB42) té 23 punters (ids 1 a $17). Les pistes són les combinacions que crida el joc (A en cada canal: $0D+$4E tema principal,
 * $0F+$50, $14+$55, $11+$52, $8B+$CC, $82+$C3, $84+$C5...) i, després, cada identificador sol al canal 0 i al canal 1. Verificat amb ROM assemblada.
 */
private val YIE_COMBOS = listOf(
    "tema principal" to intArrayOf(0x0D, 0x4E), "so \$0F+\$50" to intArrayOf(0x0F, 0x50), "so \$14+\$55" to intArrayOf(0x14, 0x55), "so \$11+\$52" to intArrayOf(0x11, 0x52), "so \$81" to intArrayOf(0x81), "so \$8B+\$CC" to intArrayOf(0x8B, 0xCC),
    "so \$8A" to intArrayOf(0x8A), "so \$89" to intArrayOf(0x89), "so \$87" to intArrayOf(0x87), "so \$93" to intArrayOf(0x93), "so \$86" to intArrayOf(0x86), "so \$82+\$C3" to intArrayOf(0x82, 0xC3), "so \$84+\$C5" to intArrayOf(0x84, 0xC5),
    "so \$97 (\$C0C8)" to intArrayOf(0x97),
)

internal object ExpNesYieAr : NesDriver {
    override val nom = "YieArKungFuN"
    private val PIS: List<Pair<String, IntArray>> = YIE_COMBOS.map { (n, v) -> ("Combinació " + n + " · CE45 A=" + v.joinToString("+") { "\$" + hN(it) }) to v } +
        (1..23).map { ("Id \$" + hN(it) + " canal 0 · A=\$" + hN(it)) to intArrayOf(it) } + (1..23).map { ("Id \$" + hN(it) + " canal 1 · A=\$" + hN(0x40 + it)) to intArrayOf(0x40 + it) }
    override val cancons = PIS.map { it.first }
    private val cau = CercaN(sigN("86 49 84 38 85 27 4A 4A 4A 4A 29 0C 85 16 4A 4A"))
    override fun reconeix(rom: NesRom) = rom.mapper == 0 && rom.prgBancs == 1 && cau.adr(rom) == 0xE45
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.ram[0x67] = 0
        for (a in PIS[(id - 1).coerceIn(0, PIS.size - 1)].second) { cpu.sp = 0xFD; cpu.ldaN(a); cpu.x = 0; cpu.y = 0; cpu.crida(0xCE45) }
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.ram[0x67] = 0; cpu.sp = 0xFD; cpu.crida(0xCEF9) }
}

val EXP_NES_N2: List<NesDriver> = listOf(ExpNesPacMan, ExpNesMappy, ExpNesTennis, ExpNesYieAr)
