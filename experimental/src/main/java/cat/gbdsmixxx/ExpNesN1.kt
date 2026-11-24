package cat.gbdsmixxx

/*
 * Lot N, fitxer 1: jocs de cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris de cada instrucció). Verificats assemblant el desassemblatge amb ca65/ld65 (script mkrom).
 */

/**
 * Battle City (NES, NROM de 16 KB). `sub_EA7E_sound_driver` ($EA7E, la NMI la crida a cada fotograma) recull 28 ranures d'efecte a `ram_sounds` ($0300-$031B; posar-hi 1 demana l'efecte).
 * `sub_EA51_clear_sound_engine_data` ($EA51) reinicia el motor. El joc no té música de fons: les «pistes» són els 28 efectes (jingles d'inici de fase, game over, rècord...) amb noms de `bank_ram.inc`.
 * Es reconeix per l'entrada de `sub_EA7E` al PRG $2A7E. Verificat amb ROM assemblada.
 */
object ExpNesBattleCity : NesDriver {
    override val nom = "BattleCityN"
    private val NOMS = listOf("pausa", "inici de fase 1", "inici de fase 2", "inici de fase 3", "vida extra 1", "vida extra 2", "bonus agafat", "explosió jugador", "(lliure)", "bonus apareix", "explosió enemic", "explosió quarter general",
        "bala contra maó", "bala contra paret", "bala contra tanc", "tret", "moviment sobre gel", "moviment jugador", "moviment enemic", "puntuació 1", "puntuació 2", "rècord 1", "rècord 2", "rècord 3", "game over 1", "game over 2", "game over 3", "bonus 1000")
    private val GRUPS = listOf(intArrayOf(1, 2, 3) to "inici de fase (1+2+3)", intArrayOf(4, 5) to "vida extra (1+2)", intArrayOf(21, 22, 23) to "rècord (1+2+3)", intArrayOf(24, 25, 26) to "game over (1+2+3)", intArrayOf(19, 20) to "puntuació (1+2)")
    override val cancons = GRUPS.map { g -> g.second + " · RAM " + g.first.joinToString("+") { "\$03" + hN(it) } } + NOMS.indices.map { "${NOMS[it]} · RAM \$03${hN(it)}" }
    private val SIG = sigN("A5 6D D0 06 A9 1C 85 F5 10 04 A9 01 85 F5 A9 00 A2 03 95 F9")
    private val cau = CercaN(SIG)
    override fun reconeix(rom: NesRom) = rom.mapper == 0 && rom.prgBancs == 1 && cau.adr(rom) == 0x2A7E
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        cpu.sp = 0xFD; cpu.crida(0xEA51)
        m.ram[0x6D] = 0
        val i = (id - 1).coerceIn(0, GRUPS.size + 27)
        if (i < GRUPS.size) for (k in GRUPS[i].first) m.ram[0x300 + k] = 1 else m.ram[0x300 + i - GRUPS.size] = 1
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; m.ram[0x6D] = 0; cpu.crida(0xEA7E) }
}

val EXP_NES_N1: List<NesDriver> = listOf(ExpNesBattleCity)
