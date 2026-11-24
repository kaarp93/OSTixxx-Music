package cat.gbdsmixxx

/*
 * Lot N, fitxer 4: jocs de repos propis (da65/ca65/asm6). Les verificacions s'han fet assemblant el banc de so (o el joc) amb ca65/ld65 dins d'una ROM sintètica quan no s'ha pogut muntar la ROM sencera.
 */

/**
 * Blaster Master (NES, MMC1, 128 KB, versió USA). De MetaFight/blaster-master-disasm-public (`disasm/prg_bank_05.ca65.asm`, amb l'adreça de cada instrucció): el motor de so és tot el banc de 16 KB $05 (visible a $8000) i té una taula
 * d'entrades al final del banc: `$BFE5` (`jmp L_8000`, inicia el motor), `$BFE8` (`jmp L_8013`, ordre: A = identificador de so) i `$BFF1` (`jmp L_8077`, passada per fotograma). `L_84B0` ($84B0) és la taula de punters
 * de so (85 entrades, ids 0 a $54, música i efectes junts; el desassemblatge no els dona nom; l'entrada 9 apunta a `L_855A`). Es reconeix per la taula d'entrades (`jmp $8000; jmp $8013; jmp 0000; jmp 0000; jmp $8077`) al
 * final del banc 5 (PRG $17FE5). Verificat assemblant només el banc 5 del desassemblatge amb ca65/ld65 dins d'una ROM sintètica i fent-lo sonar amb `SinteNesRom` (no s'ha pogut muntar la ROM sencera).
 */
internal object ExpNesBlasterMaster : NesDriver {
    override val nom = "BlasterMasterN"
    override val cancons = (0 until 85).map { "So \$" + hN(it) + " · taula \$" + hN(0x84B0 + it * 2, 4) }
    private val cau = CercaN(sigN("4C 00 80 4C 13 80 4C 00 00 4C 00 00 4C 77 80"))
    override fun reconeix(rom: NesRom) = rom.mapper == 1 && rom.prgBancs == 8 && cau.adr(rom) == 0x17FE5
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaBanc(5); cpu.sp = 0xFD; cpu.crida(0xBFE5)
        cpu.sp = 0xFD; cpu.ldaN((id - 1).coerceIn(0, 84)); cpu.crida(0xBFE8)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaBanc(5); cpu.sp = 0xFD; cpu.crida(0xBFF1) }
}

/**
 * StarTropics (NES, MMC6 = MMC3, 256 KB de PRG, versió USA). De jogalleciez/StarTropics-decomp (`docs/ARCHITECTURE.md`, `docs/DATA.md`, `src/bank31.asm`, `include/symbols.inc`): mode de PRG 1 ($8000 banc 30 fix, $A000 = R7, $C000 = R6, $E000 banc 31).
 * `RunSoundDriver` (banc 30, $81BF) fa, a cada NMI: R7 = 8 (música, a $A000), R6 = 9 (efectes i més música, a $C000), si `SoundSet` ($7167) té el bit 7 crida `J_Sound_LoadSfxSet` ($FEF6) i després `J_Sound_Update` ($FEF0, `Sound_Update` $F6A4);
 * `J_Sound_Init` ($FEF3) reinicia el driver. Cançó: `MusicRequest` ($A0) = $80 + número ($01 a $23; `SongTable` a $A000 del banc 8). Efectes: `SoundSet` tria 1 de 6 jocs de 32 efectes; `SfxRequest1` a `SfxRequest4` ($A2-$A5)
 * són màscares de bits (el bit més alt activa l'efecte 0-7, 8-15, 16-23, 24-31). Pistes: les 35 cançons i els 6 x 32 efectes. Es reconeix per l'inici de `Sound_Update` (`ldx $7168; bmi; lda $A0; beq; bpl; cmp #$81`) al PRG $3F6A4.
 * Reproducció des del desassemblatge (el motor és al ROM; les dades dels bancs 8 i 9 no es poden provar sense la ROM): s'ha assemblat el codi del repo amb una `baserom` de zeros només per comprovar l'adreça i la signatura. **No comprovat amb cap ROM.**
 * La ROM original té la capçalera iNES «brossa» (`DiskDude!` als bytes 7-15): `NesRom.mapper` l'ha d'ignorar (vegeu l'informe).
 */
internal object ExpNesStarTropics : NesDriver {
    override val nom = "StarTropicsN"
    override val cancons = (1..0x23).map { "Música \$" + hN(it) + " · MusicRequest \$" + hN(0x80 + it) + " (SongTable \$" + hN(0xA000 + it * 2, 4) + ")" } +
        (0 until 6).flatMap { s -> (0 until 32).map { e -> "Efecte $e · joc $s · \$" + hN(0xA2 + e / 8) + " bit " + (e % 8) } }
    private val cau = CercaN(sigN("AE 68 71 30 F8 A5 A0 F0 F1 10 C0 C9 81 B0 CF A9 00 85 A0 85 A1"))
    override fun reconeix(rom: NesRom) = (rom.mapper == 4 || rom.mapper == 0x44) && rom.prgBancs == 16 && cau.adr(rom) == 0x3F6A4
    private fun bancs(m: NesRomMem) { m.wb(0x8000, 0x67); m.wb(0x8001, 8); m.wb(0x8000, 0x66); m.wb(0x8001, 9) }
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        bancs(m); cpu.sp = 0xFD; cpu.crida(0xFEF3)
        val i = (id - 1).coerceIn(0, 35 + 192 - 1)
        val set = if (i < 35) 0 else (i - 35) / 32
        m.wb(0x7167, set); cpu.sp = 0xFD; cpu.crida(0xFEF6)
        if (i < 35) m.ram[0xA0] = 0x80 + i + 1
        else { val e = (i - 35) % 32; m.ram[0xA2 + e / 8] = 1 shl (e % 8) }
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { bancs(m); cpu.sp = 0xFD; cpu.crida(0xFEF0) }
}

/**
 * Lupin III: Pandora no Isan (NES, MMC3, 128 KB). De PavelSha/Lupin-Sansei---Pandora-no-Isan (adreces als comentaris; `docs/ru/audio_tracks.md` descriu cada pista): el motor és al banc de 16 KB $02 (bancs de 8 KB $04 a $8000 i $05 a $A000;
 * `tbl_sound_row_mini` a $8000, 4 bytes per pista: fila de so, canal APU i punter; el gestor de so és a `loc_AD80_activate_sound_manager` $AD80). Al banc fix: `sub_C402_clear_sound_parts` ($C402, reinicia), `sub_C420_add_sound_effect`
 * ($C420, A = número de pista; una pista és un sol canal) i `sub_FFF0_update_sounds` ($FFF0, a cada fotograma). Una música són diverses pistes cridades juntes (una per canal: quadrada 1, quadrada 2, triangle i soroll):
 * s'ofereixen els grups documentats (títol 0-3, nivell 1 4/5/$39/$48, nivell 2 6/7/$3A/$4B, nivell 3 8/9/$3B/$49, conducció $1B/$1C/$43/$4C, nivell 4 $1D/$1E/$44/$4D, aigua $1F/$20/$45/$4A, cap $22/$23/$46/$4E,
 * motxilles $24/$25/$47/$4F, tresor $55-$57, Clarissa $5B-$5E) i després les 97 pistes soles ($00 a $60). Es reconeix per l'inici de la taula de pistes al PRG $8000. **No comprovat amb cap ROM:** el repo no porta els fitxers de
 * so (`assets/audio_track*.bin`), així que no s'ha pogut assemblar.
 */
internal object ExpNesLupin3 : NesDriver {
    override val nom = "LupinIIIN"
    private val GRUPS = listOf(
        "Títol (menú)" to intArrayOf(0, 1, 2, 3), "Nivell 1" to intArrayOf(4, 5, 0x39, 0x48), "Nivell 2" to intArrayOf(6, 7, 0x3A, 0x4B), "Nivell 3" to intArrayOf(8, 9, 0x3B, 0x49),
        "Nivell de conducció" to intArrayOf(0x1B, 0x1C, 0x43, 0x4C), "Nivell 4" to intArrayOf(0x1D, 0x1E, 0x44, 0x4D), "Sota l'aigua" to intArrayOf(0x1F, 0x20, 0x45, 0x4A),
        "Sala del cap" to intArrayOf(0x22, 0x23, 0x46, 0x4E), "Sala de les motxilles / personatge no jugable" to intArrayOf(0x24, 0x25, 0x47, 0x4F),
        "Tresor (abans del final)" to intArrayOf(0x55, 0x56, 0x57), "Rescat de la Clarisse" to intArrayOf(0x5B, 0x5C, 0x5D, 0x5E),
    )
    private val NOMS = mapOf(
        0x0B to "tret de pistola", 0x0C to "cop d'espasa", 0x0D to "porta", 0x0E to "pausa", 0x0F to "explosió", 0x10 to "bala contra enemic blindat", 0x11 to "radio", 0x12 to "el protagonista rep mal", 0x13 to "enemic rep mal",
        0x14 to "maleta blanca trencada", 0x15 to "canvi de personatge", 0x16 to "motor de la motxilla de reacció", 0x17 to "tret de bazuca", 0x18 to "objecte de paret destruïda", 0x19 to "enemic tallat", 0x1A to "ulleres d'infrarojos",
        0x21 to "bala contra enemic", 0x28 to "globus", 0x29 to "projectil que cau", 0x2C to "bloqueig del cap 1", 0x2D to "llançament del cap 1", 0x30 to "caiguda enorme", 0x31 to "cops de puny", 0x32 to "voltereta d'enemic",
        0x35 to "papallones", 0x36 to "capbussó", 0x37 to "barril", 0x3D to "tret de projectil", 0x3F to "bomba d'ocell", 0x40 to "salt del cotxe", 0x41 to "aterratge del cotxe", 0x50 to "lletres del missatge",
        0x51 to "tret (menú)", 0x52 to "tret (menú, soroll)", 0x5F to "obrir el cofre", 0x60 to "vol del Fènix",
    )
    override val cancons = GRUPS.map { (n, t) -> n + " · pistes " + t.joinToString("+") { "\$" + hN(it) } } + (0..0x60).map { (NOMS[it] ?: "Pista") + " · \$" + hN(it) + " · taula \$" + hN(0x8000 + it * 4, 4) }
    private val cau = CercaN(sigN("54 00 84 81 69 01 B1 81 7E 02 D4 81 93 03 F7 81"))
    override fun reconeix(rom: NesRom) = rom.mapper == 4 && rom.prgBancs == 8 && cau.adr(rom) == 0x8000
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaMmc3(4, 5); cpu.sp = 0xFD; cpu.crida(0xC402)
        val i = (id - 1).coerceIn(0, GRUPS.size + 0x60)
        val tr = if (i < GRUPS.size) GRUPS[i].second else intArrayOf(i - GRUPS.size)
        for (t in tr) { m.triaMmc3(4, 5); cpu.sp = 0xFD; cpu.ldaN(t); cpu.crida(0xC420) }
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaMmc3(4, 5); cpu.sp = 0xFD; cpu.crida(0xFFF0) }
}

val EXP_NES_N4: List<NesDriver> = listOf(ExpNesBlasterMaster, ExpNesStarTropics, ExpNesLupin3)
