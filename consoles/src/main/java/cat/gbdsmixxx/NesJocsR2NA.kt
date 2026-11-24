package cat.gbdsmixxx

/*
 * Drivers de NES de la ronda 2 (lot R2N, part A). Els ids de cançó són la posició a la llista (a partir d'1).
 */

/** Equivalent a LDA #v: posa l'acumulador i també les banderes N i Z (algun codi del joc hi depèn en entrar). */
internal fun Cpu6502.ldaR2N(v: Int) { a = v and 255; fz = a == 0; fn = a and 0x80 != 0 }

/** Cerca la signatura a la PRG una sola vegada per ROM (els drivers són objectes compartits i frame() es crida a cada fotograma). */
internal class CercaR2N(private val sig: IntArray) {
    private val cau = java.util.WeakHashMap<NesRom, Int>()
    fun adr(rom: NesRom): Int = synchronized(cau) { cau.getOrPut(rom) { rom.cerca(sig) } }
}

/**
 * Mega Man 2 (NES, MMC1, 256 KB). De plasticsmoke/megaman2-disassembly-ca65 (porta l'adreça i el comentari de cada rutina; `ENGINE.md` descriu el motor): el motor de so
 * és tot el banc de 16 KB `$0C` (visible a $8000): `$8000` = passada per fotograma, `$8003` = ordre amb A = identificador (`$00-$17` música, `$21-$42` efectes). El joc
 * (al banc fix) hi crida les dues coses des de la NMI. Es reconeix per l'entrada del banc (`jmp; cmp #$FC; bne; jmp; cmp #$FD; bne; jmp; cmp #$FE`) a l'inici del banc $0C
 * d'una ROM MMC1 de 16 bancs. Només s'ofereixen les 24 músiques. Verificat assemblant el desassemblatge amb ca65/ld65 i fent-lo sonar amb `SinteNesRom`.
 */
object DriverMegaMan2Nes : NesDriver {
    override val nom = "MegaMan2Nes"
    override val cancons = listOf(
        "Flash Man", "Wood Man", "Crash Man", "Heat Man", "Air Man", "Metal Man", "Quick Man", "Bubble Man", "Wily 1-2", "Wily 3-5", "Intro de fase", "Cap",
        "Selecció de fase", "Títol", "Obertura", "Game over", "Contrasenya", "Mapa de Wily", "Cap derrotat (escena)", "Epíleg", "Crèdits", "Fase completada", "Wily derrotat", "Arma obtinguda",
    )
    private val SIG = intArrayOf(0x4C, -1, -1, 0xC9, 0xFC, 0xD0, 0x03, 0x4C, -1, -1, 0xC9, 0xFD, 0xD0, 0x03, 0x4C, -1, -1, 0xC9, 0xFE, 0xD0, 0x0B)
    private val cau = CercaR2N(SIG)
    override fun reconeix(rom: NesRom): Boolean { val o = cau.adr(rom); return rom.mapper == 1 && rom.prgBancs == 16 && o == 0xC * 0x4000 }
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaBanc(0x0C); cpu.sp = 0xFD; cpu.a = (id - 1).coerceIn(0, 0x17); cpu.y = 0
        cpu.crida(0x8003)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaBanc(0x0C); cpu.sp = 0xFD; cpu.crida(0x8000) }
}

/**
 * Super C (NES, MMC3, 128 KB). De vermiceli/nes-super-c (amb `docs/Sound Documentation.md`, que dona la taula de codis de so) i del desassemblatge de cyneprepou4uk (ROM idèntica
 * quan s'assembla): el motor de so és als bancs de PRG de 8 KB $0C ($8000) i $0D ($A000). El joc (`play_sound`) hi crida `init_sound_code_vars` ($8039, A = codi de so) i, a
 * cada fotograma, `handle_sound_slots` ($813C). Codis de música: $28 a $36 (el $2E repeteix el $29). Es reconeix per l'entrada d'`init_sound_code_vars`
 * (`sta $0161; cmp #$37; bcc; jmp; cmp #$28; bcc; jsr`) al PRG $18039. Verificat assemblant el desassemblatge de cyneprepou4uk amb ca65/ld65 i fent-lo sonar amb `SinteNesRom`.
 */
object DriverSuperC : NesDriver {
    override val nom = "SuperC"
    private val CODIS = intArrayOf(0x28, 0x2B, 0x2A, 0x29, 0x2C, 0x2D, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36)
    override val cancons = listOf("Thunder Landing (zona 1)", "In A Tight Squeeze (zona 2)", "Jungle Juncture (zona 3)", "No Escape (zones 4 i 7)", "M-3 (zona 5)", "Hotter Than Hell (zona 6)", "Deathbed (zona 8)", "Great Heli (caps)", "Cap", "Cap 3", "Fi de nivell", "Fi del nivell final", "Game over", "Free World (final)")
    private val SIG = intArrayOf(0x8D, 0x61, 0x01, 0xC9, 0x37, 0x90, 0x03, 0x4C, -1, -1, 0xC9, 0x28, 0x90, 0x03, 0x20)
    private val cau = CercaR2N(SIG)
    override fun reconeix(rom: NesRom): Boolean = rom.mapper == 4 && rom.prgBancs == 8 && cau.adr(rom) == 12 * 0x2000 + 0x39
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaMmc3(12, 13); cpu.sp = 0xFD; cpu.a = CODIS[(id - 1).coerceIn(0, CODIS.size - 1)]
        cpu.crida(0x8039)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaMmc3(12, 13); cpu.sp = 0xFD; cpu.crida(0x813C) }
}

/**
 * Captain America and The Avengers (NES, MMC3, 128 KB). De cyneprepou4uk/NES-Games-Disassembly (cada instrucció porta la direcció i els bytes als comentaris, i les constants
 * `con_music_*` donen els codis): el motor de so és al banc de PRG de 8 KB $07 (visible a $8000): `$8003` = reproduir (A = codi), `$8006` = passada per fotograma (el joc la fa
 * amb el banc $07 a $8000 a cada NMI), `$8000` = aturar. Codis de música: $1C a $26, $28 i $2A. Es reconeix per les tres entrades `jmp` i la taula d'adreces que les segueix, al
 * PRG $E000. Verificat assemblant el desassemblatge amb ca65/ld65 i fent-lo sonar amb `SinteNesRom`.
 */
object DriverCaptainAmerica : NesDriver {
    override val nom = "CaptainAmerica"
    private val CODIS = intArrayOf(0x1C, 0x1D, 0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x28, 0x2A)
    override val cancons = listOf("Escena després del títol", "Crèdits", "Game over", "Jugador mort", "Mapa", "Captain America", "Hawkeye", "Alerta vermella", "Diàleg 1", "Diàleg 2", "Cap", "Menú de pausa", "Emboscada")
    private val SIG = intArrayOf(0x4C, 0x11, 0x80, 0x4C, 0x21, 0x81, 0x4C, 0xFD, 0x81, 0xB7, 0xBD, 0xC3, 0xC9, 0x06, 0x06, 0x06, 0x06)
    private val cau = CercaR2N(SIG)
    override fun reconeix(rom: NesRom): Boolean = rom.mapper == 4 && rom.prgBancs == 8 && cau.adr(rom) == 7 * 0x2000
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaMmc3(7, 8); cpu.sp = 0xFD; cpu.crida(0x8000)
        cpu.sp = 0xFD; cpu.ldaR2N(CODIS[(id - 1).coerceIn(0, CODIS.size - 1)])
        cpu.crida(0x8003)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaMmc3(7, 8); cpu.sp = 0xFD; cpu.crida(0x8006) }
}

/**
 * Double Dragon II: The Revenge (NES, MMC3, 128 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris, constants `con_music_*`): el motor de so és al banc de 16 KB $05
 * (bancs de PRG de 8 KB $0A a $8000 i $0B a $A000). El so es demana posant-ne el codi a `ram_sound_id` ($07FF) i cridant `$8000`; `$8003` és la passada per fotograma (el joc
 * l'executa a cada NMI). Codis de música: $01 a $14 (el $0B no s'usa). Es reconeix per les dues entrades `jmp` al PRG $14000. Verificat assemblant el desassemblatge amb ca65/ld65
 * i fent-lo sonar amb `SinteNesRom`.
 */
object DriverDoubleDragon2 : NesDriver {
    override val nom = "DoubleDragon2"
    private val CODIS = intArrayOf(0x01, 0x03, 0x02, 0x0F, 0x10, 0x04, 0x07, 0x06, 0x05, 0x0C, 0x11, 0x08, 0x09, 0x0D, 0x0A, 0x0E, 0x12, 0x13, 0x14)
    override val cancons = listOf("Logo i menú", "Missió 1", "Missió 2", "Missió 3", "Missió 4", "Missió 5", "Missió 6", "Missió 7", "Missió 8", "Missió 9 (part 1)", "Missió 9 (part 2)", "Cap normal", "Cap del tractor", "Cap clònic", "Missió completada", "Text de la missió", "Game over", "Escena final (part 1)", "Crèdits")
    private val SIG = intArrayOf(0x4C, 0x26, 0x80, 0x4C, 0x96, 0x81, 0x00, 0x00, 0x00, 0x00)
    private val cau = CercaR2N(SIG)
    override fun reconeix(rom: NesRom): Boolean = rom.mapper == 4 && rom.prgBancs == 8 && cau.adr(rom) == 10 * 0x2000
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaMmc3(10, 11); cpu.sp = 0xFD
        m.ram[0x7F0] = 0x8D; m.ram[0x7F2] = 0x40; m.ram[0x7F3] = 0x60       // codi automodificable `sta $40xx; rts` ($07F0), que el joc deixa a la RAM en arrencar
        m.ram[0x7FF] = CODIS[(id - 1).coerceIn(0, CODIS.size - 1)]
        cpu.crida(0x8000)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaMmc3(10, 11); cpu.sp = 0xFD; cpu.crida(0x8003) }
}

/**
 * Felix the Cat (NES, MMC3, 128 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris, constants `con_music_*`): el motor de so és al banc de 16 KB $00 (bancs de 8 KB
 * $00 i $01). `play_sound` ($85DE, A = codi; $20-$2F són músiques i es deixen a `ram_music` $043A) i `update_sound_engine` ($8618, a cada fotograma). Codis de música: $21 a $2F.
 * Es reconeix per l'inici de `play_sound` al PRG $5DE. Verificat assemblant el desassemblatge amb ca65/ld65 i fent-lo sonar amb `SinteNesRom`.
 */
object DriverFelixCat : NesDriver {
    override val nom = "FelixCat"
    private val CODIS = intArrayOf(0x21, 0x22, 0x2C, 0x28, 0x25, 0x24, 0x29, 0x26, 0x23, 0x2F, 0x27, 0x2E, 0x2D, 0x2A)
    override val cancons = listOf("Títol / món de Felix", "Ronda 1", "Ronda 2", "Ronda 3", "Ronda 4", "Ronda 5", "Ronda 6", "Ronda 7", "Ronda 8", "Cap", "Trucada del professor", "Ronda completada", "Game over", "Pantalla final")
    private val SIG = intArrayOf(0xC9, 0x80, 0xB0, 0x30, 0xC9, 0x30, 0xB0, 0x08, 0xC9, 0x20, 0x90, 0x08, 0x8D, 0x3A, 0x04, 0x60)
    private val cau = CercaR2N(SIG)
    override fun reconeix(rom: NesRom): Boolean = rom.mapper == 4 && rom.prgBancs == 8 && cau.adr(rom) == 0x5DE
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaMmc3(0, 1); cpu.sp = 0xFD; cpu.crida(0x8571)
        cpu.sp = 0xFD; cpu.ldaR2N(CODIS[(id - 1).coerceIn(0, CODIS.size - 1)])
        cpu.crida(0x85DE)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaMmc3(0, 1); cpu.sp = 0xFD; cpu.crida(0x8618) }
}

/**
 * Ice Climber (NES, NROM de 16 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris, constants `con_music_*`): `sub_F91E_update_sound_engine` ($F91E) es crida a cada
 * fotograma (la NMI) i recull les peticions de la zero page: la música es demana posant un bit a `ram_music_1` ($FB): $01 pujada de la muntanya, $02 música de fons, $04 logo
 * (des del principi, sense bucle), $08 logo (des del mig, en bucle), $10 ocell agafat. Es reconeix per la seqüència d'entrada de la rutina. Verificat assemblant el desassemblatge amb ca65/ld65
 * i fent-lo sonar amb `SinteNesRom`.
 */
object DriverIceClimber : NesDriver {
    override val nom = "IceClimber"
    override val cancons = listOf("Pujada de la muntanya", "Música de fons", "Logo (sense bucle)", "Logo (en bucle)", "Ocell agafat")
    private val SIG = intArrayOf(0xA9, 0xFF, 0x8D, 0x17, 0x40, 0x20, 0xEB, 0xF9, 0x20, 0xC4, 0xFA, 0x20, 0xA1, 0xFB, 0x20, 0xFC, 0xFB, 0xA9, 0x00, 0x85, 0xFF, 0x85, 0xFE, 0x85, 0xFC, 0x85, 0xFB)
    override fun reconeix(rom: NesRom) = rom.mapper == 0 && rom.prgBancs == 1 && rom.cerca(SIG) == 0x391E
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) { m.ram[0xFB] = 1 shl (id - 1).coerceIn(0, 4) }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(0xF91E) }
}

/**
 * Excitebike (NES, NROM de 16 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris): `sub_F844` ($F844) és la passada de so per fotograma i recull `ram_00FB`
 * ($FB), on cada bit demana una música (la taula `tbl_FDD3_index` en té 8: els bits 1, 3 i 4 comparteixen dades). El desassemblatge no dona noms a les músiques. Es reconeix per la
 * seqüència d'entrada de `sub_F844`. Verificat assemblant el desassemblatge amb ca65/ld65 i fent-lo sonar amb `SinteNesRom`.
 */
object DriverExcitebike : NesDriver {
    override val nom = "Excitebike"
    private val BITS = intArrayOf(0, 1, 2, 5, 6, 7)
    override val cancons = BITS.map { "So ${it + 1}" }
    private val SIG = intArrayOf(0xA9, 0xC0, 0x8D, 0x17, 0x40, 0x20, 0xD4, 0xF8, 0x20, 0x74, 0xFA, 0x20, 0x3D, 0xFC, 0x20, 0x8D, 0xFC, 0xA9, 0x00, 0x85, 0xFF, 0x85, 0xFE, 0x85, 0xFD, 0x85, 0xFB)
    override fun reconeix(rom: NesRom) = rom.mapper == 0 && rom.prgBancs == 1 && rom.cerca(SIG) == 0x3844
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) { m.ram[0xFB] = 1 shl BITS[(id - 1).coerceIn(0, BITS.size - 1)] }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(0xF844) }
}

/**
 * Nuts & Milk (NES, NROM de 16 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces i noms de rutina als comentaris): la NMI (`$C17D`) crida a cada fotograma `sub_F1A7`,
 * `sub_F1F2`, `sub_F23D` i `sub_F2A4` (un motor per canal). La música es demana cridant la rutina `sub_Fxxx_play_music___*` corresponent (després de `sub_F2E4`, que reinicia el so):
 * títol $F046, anunci de ronda de bonus $F004, ronda de bonus $F039, bonus completat $F064, ronda completada $F144, ronda nova $F163, mort $F182 i la música de fons de les rondes
 * $F01B (dues versions segons la paritat de `ram_current_round`, $20; només porta la línia del triangle). Es reconeix per l'entrada de `sub_F1A7` al PRG $31A7. Verificat assemblant el
 * desassemblatge amb ca65/ld65 i fent-lo sonar amb `SinteNesRom`.
 */
object DriverNutsMilk : NesDriver {
    override val nom = "NutsMilk"
    override val cancons = listOf("Títol", "Ronda nova", "Ronda completada", "Mort", "Anunci de la ronda de bonus", "Ronda de bonus", "Bonus completat", "Ronda (parell)", "Ronda (senar)")
    private val ENTRADES = intArrayOf(0xF046, 0xF163, 0xF144, 0xF182, 0xF004, 0xF039, 0xF064, 0xF01B, 0xF01B)
    private val SIG = intArrayOf(0xA5, 0x34, 0xD0, 0x01, 0x60, 0xA2, 0x05, 0xA5, 0x3E, 0xF0, 0x02, 0xA2, 0x09, 0xE6, 0x35, 0xE4, 0x35, 0xF0, 0x01, 0x60)
    override fun reconeix(rom: NesRom) = rom.mapper == 0 && rom.prgBancs == 1 && rom.cerca(SIG) == 0x31A7
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        val i = (id - 1).coerceIn(0, ENTRADES.size - 1)
        cpu.sp = 0xFD; cpu.crida(0xF2E4)
        m.ram[0x20] = if (i == 8) 1 else 0
        cpu.sp = 0xFD; cpu.crida(ENTRADES[i])
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) {
        for (a in intArrayOf(0xF1A7, 0xF1F2, 0xF23D, 0xF2A4)) { cpu.sp = 0xFD; cpu.crida(a) }
    }
}

/**
 * Son Son (NES, NROM de 32 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris): la NMI crida `sub_D664_update_sound_engine` ($D664) a cada fotograma; una música
 * es posa en marxa activant els seus bits amb `sub_D5ED_sound_engine_set_bit` ($D5ED, A = pàgina * 8 + bit) després de `sub_D560_disable_music` ($D560). Cada música són 2 o 3 bits
 * (els grups són els que crida el joc; només s'ofereixen els 7 que sonen amb les tres veus). El desassemblatge no dona noms. Es reconeix per l'entrada de `sub_D664` al PRG $5664. Verificat assemblant el
 * desassemblatge amb ca65/ld65 i fent-lo sonar amb `SinteNesRom`.
 */
object DriverSonSon : NesDriver {
    override val nom = "SonSon"
    internal val GRUPS = listOf(intArrayOf(0x04, 0x05, 0x06), intArrayOf(0x0C, 0x1E, 0x1F), intArrayOf(0x14, 0x15, 0x16), intArrayOf(0x08, 0x09), intArrayOf(0x18, 0x19), intArrayOf(0x0A, 0x1A, 0x1B), intArrayOf(0x0B, 0x1C, 0x1D))
    override val cancons = GRUPS.indices.map { "So ${it + 1}" }
    private val SIG = intArrayOf(0xAD, 0x01, 0x07, 0x85, 0xF8, 0xAD, 0x02, 0x07, 0x85, 0xF9, 0xAD, 0x03, 0x07, 0x85, 0xFA, 0xAD, 0x04, 0x07, 0x85, 0xFB)
    override fun reconeix(rom: NesRom) = rom.mapper == 0 && rom.prgBancs == 2 && rom.cerca(SIG) == 0x5664
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        cpu.sp = 0xFD; cpu.crida(0xD560)
        for (b in GRUPS[(id - 1).coerceIn(0, GRUPS.size - 1)]) { cpu.sp = 0xFD; cpu.ldaR2N(b); cpu.crida(0xD5ED) }
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(0xD664) }
}

/**
 * Adventure Island (NES, mapper 3 CNROM de 32 KB: la PRG és fixa, com una NROM). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris, constants `con_music_*`): la NMI
 * crida `sub_C681` (efectes) i `sub_C8BE` (música) a cada fotograma; la música es demana posant l'identificador a `ram_music_id` ($AB, 1 a $0E). Es reconeix per l'entrada de
 * `sub_C8BE` al PRG $48BE. Verificat assemblant el desassemblatge amb ca65/ld65 i fent-lo sonar amb `SinteNesRom`.
 */
object DriverAdventureIsland : NesDriver {
    override val nom = "AdventureIsland"
    override val cancons = listOf("Títol", "Bosc (dia)", "Bosc (nit)", "Platja", "Cova", "Muntanya", "Bonus", "Albergínia", "Mort", "Game over", "Escena final", "Cap", "Cap derrotat", "Fase completada")
    private val SIG = intArrayOf(0xA5, 0xAC, 0xD0, 0xD7, 0xA5, 0xAB, 0xF0, 0xF7, 0x30, 0x4B, 0xA5, 0xAB, 0xC9, 0x0F)
    override fun reconeix(rom: NesRom) = rom.mapper == 3 && rom.prgBancs == 2 && rom.cerca(SIG) == 0x48BE
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) { m.ram[0xAB] = (id).coerceIn(1, 14) }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) {
        cpu.sp = 0xFD; cpu.crida(0xC681)
        cpu.sp = 0xFD; cpu.crida(0xC8BE)
    }
}

/**
 * Tecmo World Cup Soccer (NES, MMC3, 64 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris, constants `con_music_*`): el motor de so és als bancs de PRG de 8 KB $00
 * ($8000) i $01 ($A000); la NMI hi crida `sub_0x000010_update_sound_engine` ($8000) a cada fotograma. Els sons es demanen a la cua `ram_sound_queue` ($0701...) amb el
 * comptador `ram_sound_cnt` ($0700): músiques $10 a $17. Es reconeix per l'entrada del motor al PRG $0. Verificat assemblant el desassemblatge amb ca65/ld65 i fent-lo sonar amb `SinteNesRom`.
 */
object DriverTecmoWorldCup : NesDriver {
    override val nom = "TecmoWorldCup"
    override val cancons = listOf("Camp de joc", "Logo", "Gol", "Mitja part", "Número de partit", "Crèdits", "Selecció d'equips", "Penals")
    private val SIG = intArrayOf(0xA5, 0xFC, 0xF0, 0x01, 0x60, 0xAA, 0xAD, 0x00, 0x07, 0xF0, 0x1E, 0xBC, 0x01, 0x07, 0xF0, 0x0E, 0xC0, 0x20)
    private val cau = CercaR2N(SIG)
    override fun reconeix(rom: NesRom): Boolean = rom.mapper == 4 && rom.prgBancs == 4 && cau.adr(rom) == 0
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaMmc3(0, 1); m.ram[0x701] = 0x10 + (id - 1).coerceIn(0, 7); m.ram[0x700] = 1
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaMmc3(0, 1); cpu.sp = 0xFD; cpu.crida(0x8000) }
}

/**
 * The Bugs Bunny Crazy Castle (NES, MMC1, 64 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris, constants `con_music_*`): el motor és al banc fix; `sub_F7A2_play_sound`
 * ($F7A2, A = identificador; canvia ell sol al banc 2, on hi ha les dades) i `sub_F8D1_update_sound_engine` ($F8D1, a cada NMI). En arrencar, el joc posa a `ram_003D` el punter a
 * la taula de músiques (`tbl_0x009E10_music_data`, $9E10 del banc 2), `ram_bsw_stack_index` ($0D) a $FF i crida `sub_F78E` (neteja $0700-$07D7). Les entrades de la taula van per identificador - 1; només sonen amb les tres veus els codis
 * $01 títol, $02 fase de maons, $03 (compartit amb el $04 a $06), $07 i $0E (els altres són efectes d'un sol canal). Es reconeix per l'entrada de `sub_F8D1`. Verificat assemblant el desassemblatge amb ca65/ld65 i fent-lo sonar amb `SinteNesRom`.
 */
object DriverBugsBunny : NesDriver {
    override val nom = "BugsBunny"
    private val CODIS = intArrayOf(0x01, 0x02, 0x03, 0x0E, 0x07)
    override val cancons = listOf("Títol", "Fase de maons", "So 3", "So 4", "So 5")
    private val SIG = intArrayOf(0xA5, 0xF0, 0x29, 0xFD, 0x85, 0xF0, 0x29, 0x0C, 0xF0, 0x07, 0xA5, 0xF0, 0x09, 0x80, 0x85, 0xF0, 0x60, 0x20)
    override fun reconeix(rom: NesRom) = rom.mapper == 1 && rom.prgBancs == 4 && rom.cerca(SIG) == 0xF8D1
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.ram[0x0D] = 0xFF; m.ram[0x3D] = 0x10; m.ram[0x3E] = 0x9E
        m.triaBanc(2); cpu.sp = 0xFD; cpu.crida(0xF78E)
        cpu.sp = 0xFD; cpu.ldaR2N(CODIS[(id - 1).coerceIn(0, CODIS.size - 1)]); cpu.crida(0xF7A2)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(0xF8D1) }
}

/**
 * The Little Mermaid (NES, UNROM de 128 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris, constants `con_music_*`): el motor de so és al banc de 16 KB $05
 * (visible a $8000): `$8003` (`prepare_new_sfx`, A = identificador: músiques $00 a $0C) i `$8000` (`update_sound_engine`, a cada NMI). Es reconeix per les dues entrades `jmp`
 * (`4C 6C 80 4C FE 80`) al començament del banc 5. Verificat assemblant el desassemblatge amb ca65/ld65 i fent-lo sonar amb `SinteNesRom`.
 */
object DriverLittleMermaid : NesDriver {
    override val nom = "LittleMermaid"
    override val cancons = listOf("Títol", "Escena 1", "Fase 1", "Fase 2", "Fase 3", "Fase 4", "Fase 5", "Fase completada", "Cap", "Fase 6 (cap final)", "Escena 2", "Game over", "Presentació de la fase")
    private val SIG = intArrayOf(0x4C, 0x6C, 0x80, 0x4C, 0xFE, 0x80, 0xA9, 0x00, 0x85, 0xC2, 0xA0, 0x08)
    private val cau = CercaR2N(SIG)
    override fun reconeix(rom: NesRom): Boolean = rom.mapper == 2 && rom.prgBancs == 8 && cau.adr(rom) == 5 * 0x4000
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaBanc(5); cpu.sp = 0xFD; cpu.ldaR2N((id - 1).coerceIn(0, 12)); cpu.crida(0x8003)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaBanc(5); cpu.sp = 0xFD; cpu.crida(0x8000) }
}

/**
 * Kunio-kun no Nekketsu Soccer League (NES, MMC3, 128 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris): motor de so igual que el de Double Dragon II (mateixa
 * companyia), als bancs de PRG de 8 KB $00 ($8000) i $01 ($A000): `$8000` = ordre (A = identificador) i `$8003` = passada per fotograma; el motor fa servir el codi automodificable
 * `sta $40xx; rts` de la RAM ($07E1) que el joc hi deixa en arrencar. El desassemblatge no dona noms als identificadors: només els $01 a $0E són músiques (la resta, efectes). Es reconeix per l'entrada del banc 0 al PRG $0.
 * Verificat assemblant el desassemblatge amb ca65/ld65 i fent-lo sonar amb `SinteNesRom`.
 */
object DriverKunioSoccer : NesDriver {
    override val nom = "KunioSoccer"
    internal val CODIS = (1..0x0E).toList().toIntArray()
    override val cancons = CODIS.map { "So %02X".format(it) }
    private val SIG = intArrayOf(0x4C, 0x06, 0x80, 0x4C, 0x83, 0x81, 0x8D, 0xE6, 0x07, 0x8A, 0x48, 0x98, 0x48)
    private val cau = CercaR2N(SIG)
    override fun reconeix(rom: NesRom): Boolean = rom.mapper == 4 && rom.prgBancs == 8 && cau.adr(rom) == 0
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaMmc3(0, 1); cpu.sp = 0xFD
        m.ram[0x7E1] = 0x8D; m.ram[0x7E3] = 0x40; m.ram[0x7E4] = 0x60
        cpu.ldaR2N(CODIS[(id - 1).coerceIn(0, CODIS.size - 1)]); cpu.crida(0x8000)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaMmc3(0, 1); cpu.sp = 0xFD; cpu.crida(0x8003) }
}

/**
 * Ninja Gaiden (NES, MMC1, 128 KB). De 0xe1f/NinjaGaidenNES (`docs/game-notes.md` i `prg/bank03.asm`, amb les adreces): el so és al banc de 16 KB $03 (visible a $8000); la NMI hi
 * crida `play_sounds` ($8000) a cada fotograma; una cançó es demana posant-ne el número a una de les sis ranures `sound` ($0600 a $0605). La taula `sound_list` és a $877D. Es
 * reconeix per l'entrada de `play_sounds` (`ldx #5; ldy $0600,x; beq; cpy #$25; bne; lda #$40; sta $06C7`) al començament del banc 3. Els números de les músiques són els dels
 * fitxers de `audio/music` ($03 a $4E); només les de fase tenen nom. Verificat assemblant el banc 3 del desassemblatge amb ca65/ld65 dins d'una ROM sintètica i fent-lo sonar amb
 * `SinteNesRom` (la ROM sencera no es pot assemblar sense l'original).
 */
object DriverNinjaGaiden : NesDriver {
    override val nom = "NinjaGaiden"
    internal val CODIS = intArrayOf(0x03, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x26, 0x27, 0x28, 0x2B, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34, 0x43, 0x47, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E)
    override val cancons = listOf(
        "Acte I fase 1", "Acte II fase 1", "Acte II fase 2", "Acte I fase 2", "Acte V fase 1", "Acte III fase 2", "Acte IV fase 2", "So 26", "So 27", "So 28", "Acte VI fase 4",
        "Acte III fase 1", "So 2E", "So 2F", "Acte IV fase 3", "Acte V fase 3", "So 32", "So 33", "So 34", "So 43", "So 47", "So 4A", "So 4B", "So 4C", "So 4D", "So 4E",
    )
    private val SIG = intArrayOf(0xA2, 0x05, 0xBC, 0x00, 0x06, 0xF0, -1, 0xC0, 0x25, 0xD0, -1, 0xA9, 0x40, 0x8D, 0xC7, 0x06, 0x8D, 0xC8, 0x06)
    private val cau = CercaR2N(SIG)
    override fun reconeix(rom: NesRom): Boolean = rom.mapper == 1 && rom.prgBancs == 8 && cau.adr(rom) == 3 * 0x4000
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) { m.triaBanc(3); m.ram[0x600] = CODIS[(id - 1).coerceIn(0, CODIS.size - 1)] }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaBanc(3); cpu.sp = 0xFD; cpu.crida(0x8000) }
}

/**
 * Legacy of the Wizard (NES, MMC3, 128 KB). De seufet/Legacy-of-the-Wizard-Disassembly (cc65, sense dades externes; porta l'adreça de cada instrucció als comentaris): al banc fix
 * (`$C000-$FFFF`) `SOUND_MAIN` ($F89A, la NMI la crida a cada fotograma i canvia sola els bancs de PRG de so) i `LOAD_MUSIC_DATA` ($FC08), que carrega la música de `MUSIC_ID`
 * ($8E; $00 a $09 als bancs `a-b`, $0A a $0E als `c-d`): $00 entrada de mazmorra, $01 Xemn, $02 Mayna, $03 Lyll, $04 Pochi, $05 combat del drac, $06 posada/casa, $07 botiga,
 * $08 mort, $09 pantalla d'inici, $0A crèdits, $0B cap de la corona, $0C món exterior, $0D pantalla d'objectes, $0E cofre del tresor. Es reconeix per l'entrada de les dues rutines
 * al PRG $1F89A i $1FC08. Verificat assemblant el desassemblatge amb ca65/ld65 (surt idèntic a la ROM: MD5 DC2075DE...) i fent-lo sonar amb `SinteNesRom`.
 */
object DriverLegacyWizard : NesDriver {
    override val nom = "LegacyWizard"
    override val cancons = listOf("Entrada de mazmorra", "Xemn", "Mayna", "Lyll", "Pochi", "Combat del drac", "Posada / casa", "Botiga", "Mort", "Pantalla d'inici", "Crèdits", "Cap de la corona", "Món exterior", "Pantalla d'objectes", "Cofre del tresor")
    private val SIG = intArrayOf(0x20, 0x74, 0xFD, 0xA9, 0x40, 0x85, 0x02, 0x20, 0x60, 0xFA, 0xA5, 0x8D, 0xF0, 0x25)
    private val SIG2 = intArrayOf(0xA2, 0x0A, 0xA5, 0x8E, 0xC9, 0x0A, 0x90, 0x02, 0xA2, 0x0C, 0x86, 0x34, 0xE8, 0x86, 0x35, 0x20, 0x87, 0xFD)
    override fun reconeix(rom: NesRom) = rom.mapper == 4 && rom.prgBancs == 8 && rom.cerca(SIG) == 0x1F89A && rom.cerca(SIG2) == 0x1FC08
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.ram[0x8E] = (id - 1).coerceIn(0, 14); cpu.sp = 0xFD; cpu.crida(0xFC08)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(0xF89A) }
}

/**
 * Bubble Bobble Part 2 / Bubble Bobble 2 (NES, MMC3, 128 KB, versió USA). De Bobschneeder45/Bubble-Bobble-2-NES-Disassembly (cc65; el banc `bank_0E` porta el motor de so i els ids de
 * `defines/music_ids.inc`): al banc fix de 8 KB $0E ($C000) hi ha `AudioInit` ($C006) i `AudioUpdate` ($C039, la NMI la crida a cada fotograma amb el banc $0D a $A000, on hi ha
 * `AdditionalTracks`). La música es demana posant-ne l'identificador a `musicTrigger` ($E0; el bit 7 vol dir «sonant»). Ids: 1 títol, 2 final, 3 Giant Tower, 4 Boomtown, 5 Shining
 * Lake, 6 Fire Castle, 7 cap, 8 joc de bonus, 9 intro, $A victòria, $B cap final, $C (sense nom), $D avís de l'esquelet, $E esquelet, $F game over. Es reconeix per l'entrada
 * d'`AudioUpdate` al PRG $1C039. Verificat assemblant el desassemblatge amb ca65/ld65 i fent-lo sonar amb `SinteNesRom`.
 */
object DriverBubbleBobble2 : NesDriver {
    override val nom = "BubbleBobble2"
    override val cancons = listOf("Títol", "Final", "Giant Tower", "Boomtown", "Shining Lake", "Fire Castle", "Cap", "Joc de bonus", "Intro", "Victòria", "Cap final", "So 0C", "Avís de l'esquelet", "Esquelet", "Game over")
    private val SIG = intArrayOf(0x20, 0x6D, 0xC7, 0xA5, 0xE1, 0x30, 0x06, 0x20, 0x35, 0xC7, 0x4C, 0x64, 0xC0)
    private val cau = CercaR2N(SIG)
    override fun reconeix(rom: NesRom): Boolean = rom.mapper == 4 && rom.prgBancs == 8 && cau.adr(rom) == 0x1C039
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaMmc3(0x0C, 0x0D); cpu.sp = 0xFD; cpu.crida(0xC006)
        m.ram[0xE0] = (id).coerceIn(1, 15)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaMmc3(0x0C, 0x0D); cpu.sp = 0xFD; cpu.crida(0xC039) }
}

val DRIVERS_NES_R2NA: List<NesDriver> = listOf(DriverMegaMan2Nes, DriverSuperC, DriverCaptainAmerica, DriverFelixCat, DriverIceClimber, DriverExcitebike, DriverNutsMilk, DriverSonSon, DriverAdventureIsland, DriverTecmoWorldCup, DriverBugsBunny, DriverLittleMermaid, DriverDoubleDragon2, DriverNinjaGaiden, DriverLegacyWizard, DriverBubbleBobble2)
