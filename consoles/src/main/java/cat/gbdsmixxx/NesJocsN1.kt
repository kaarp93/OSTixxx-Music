package cat.gbdsmixxx

/*
 * Drivers de NES del lot N1 (Balloon Fight, Donkey Kong, Mario Bros., Dr. Mario, Dragon Warrior, Tetris, Punch-Out!!, Donald Land, Elite, Transformers...).
 * Cada un executa el codi de so original del joc amb la CPU 6502 mínima; vegeu NesRom.kt per al patró.
 */

/** Adreça (dins la CPU) d'una seqüència de bytes de la PRG, suposant que la PRG acaba a $FFFF; -1 si no hi és. */
internal fun n1AdrFixa(rom: NesRom, sig: IntArray): Int {
    val o = rom.cerca(sig)
    return if (o < 0) -1 else 0x10000 - rom.prg.size + o
}

/**
 * Mario Bros. (NES, NROM de 16 KB). De RussianManSMWC/Mario-Bros.-Dis (ca65): `SoundEngine` ($F8A7, la NMI el crida a cada fotograma) es troba per la seva seqüència de
 * bytes (set NOP, `lda #$C0; sta $4017; jsr`). Les peces de música són els 8 jingles de `Sound_Jingle` ($FD, un bit per jingle; el motor el posa a 0 després de llegir-lo).
 * Verificat assemblant el desassemblatge amb cc65 (sense gràfics) i fent-lo sonar amb `SinteNesRom`.
 */
object DriverMarioBros : NesDriver {
    override val nom = "MarioBros"
    override val cancons = listOf("Inici de la partida", "Inici de la fase", "Perfecte", "Pausa", "Reaparició del jugador", "Recompte de monedes", "Game over", "Pantalla de títol")
    private val SIG = intArrayOf(0xEA, 0xEA, 0xEA, 0xEA, 0xEA, 0xEA, 0xEA, 0xA9, 0xC0, 0x8D, 0x17, 0x40, 0x20, -1, -1, 0xA9, 0x00, 0x85, 0xFF, 0x85, 0xFE, 0x85, 0xFD)
    override fun reconeix(rom: NesRom) = rom.mapper == 0 && rom.prgBancs == 1 && rom.cerca(SIG) >= 0
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) { m.ram[0xFD] = 1 shl (id - 1).coerceIn(0, 7) }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(n1AdrFixa(rom, SIG)) }
}

/**
 * Tetris (NES, MMC1 de 32 KB). De CelestialAmber/TetrisNESDisasm: `updateAudio` ($E216, el joc el crida a cada NMI) es troba per la seva seqüència de bytes
 * (`lda #$C0; sta $4017; lda musicStagingNoiseHi; cmp #5; beq`) i el reinici del so (`LE1D8`, $E1D8: `$4015` = $0F i la llavor del soroll) pel seu. La cançó es demana a
 * `musicTrack` ($06F5; 1-10 = els 10 elements de `musicDataTable`; el motor el posa a 0 en llegir-lo). Es reconeix per `musicDataTableIndex`. Verificat assemblant el
 * desassemblatge amb cc65 (sense gràfics) i fent-lo sonar amb `SinteNesRom`.
 */
object DriverTetrisNes : NesDriver {
    override val nom = "TetrisNES"
    override val cancons = listOf("Títol", "Objectiu B assolit", "Música 1", "Música 2", "Música 3", "Música 1 (variant)", "Música 2 (variant)", "Música 3 (variant)", "Felicitacions", "Finals")
    private val SIG = intArrayOf(0xA9, 0xC0, 0x8D, 0x17, 0x40, 0xAD, 0x8D, 0x06, 0xC9, 0x05, 0xF0)
    private val SIG_INI = intArrayOf(0xA9, 0x0F, 0x8D, 0x15, 0x40, 0xA9, 0x55, 0x85, -1, 0x20, -1, -1, 0x60)
    private val SIG_IDX = intArrayOf(0x00, 0x0A, 0x14, 0x1E, 0x28, 0x32, 0x3C, 0x46, 0x50, 0x5A)
    override fun reconeix(rom: NesRom) = rom.mapper == 1 && rom.prgBancs == 2 && rom.cerca(SIG) >= 0 && rom.cerca(SIG_INI) >= 0 && rom.cerca(SIG_IDX) >= 0
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaBanc(0); cpu.sp = 0xFD; cpu.crida(n1AdrFixa(rom, SIG_INI))
        m.ram[0x6F5] = (id).coerceIn(1, 10)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaBanc(0); cpu.sp = 0xFD; cpu.crida(n1AdrFixa(rom, SIG)) }
}

/**
 * Elite (NES, MMC1 de 128 KB). De markmoxon/elite-source-code-nes: el so és al banc 6 (que es veu a $8000), amb una taula de 7 JMP al principi (`ChooseMusicS`,
 * `MakeSoundsS`, `StopSoundsS`, `EnableSoundS`, `StartEffectOn...`; a l'NTSC a $800C). Es reconeix i es troba per aquests 7 JMP seguits de l'inici de `ChooseMusic`
 * (`tay; jsr StopSoundsS; lda #0; clc; dey; bmi; adc #9; bne; tax`). `ChooseMusic` (A = tonada) inicia la música i activa el so, i `MakeSounds` (que la NMI crida a
 * cada fotograma) la fa avançar. Tonades: 0 «Elite Theme», 1 «The Blue Danube», 2 «Game Theme», 3 «Assassin's Touch», 4 «Assassin's Touch» seguida de «Game Theme».
 * Verificat amb la ROM que el propi repositori inclou (`5-compiled-rom-images`) i fent-la sonar amb `SinteNesRom`.
 */
object DriverElite : NesDriver {
    override val nom = "Elite"
    override val cancons = listOf("Elite Theme", "The Blue Danube", "Game Theme", "Assassin's Touch", "Assassin's Touch + Game Theme")
    private val SIG = intArrayOf(0x4C, -1, -1, 0x4C, -1, -1, 0x4C, -1, -1, 0x4C, -1, -1, 0x4C, -1, -1, 0x4C, -1, -1, 0x4C, -1, -1,
        0xA8, 0x20, -1, -1, 0xA9, 0x00, 0x18, 0x88, 0x30, 0x04, 0x69, 0x09, 0xD0, 0xF9, 0xAA)
    override fun reconeix(rom: NesRom) = rom.mapper == 1 && rom.prgBancs == 8 && rom.cerca(SIG) >= 0
    private fun adr(rom: NesRom) = 0x8000 + (rom.cerca(SIG) % 0x4000)
    private fun banc(rom: NesRom) = rom.cerca(SIG) / 0x4000
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaBanc(banc(rom)); cpu.sp = 0xFD; cpu.a = (id - 1).coerceIn(0, 4); cpu.crida(adr(rom))
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaBanc(banc(rom)); cpu.sp = 0xFD; cpu.crida(adr(rom) + 3) }
}

/**
 * Transformers: Mystery of Convoy (NES, CNROM de 32 KB; mapper 3, que `NesRomMem` tracta com NROM). De Fixatron/TFRom-Recreation (ca65, comentat amb les adreces
 * originals): `audio_rtn` ($D4AD, la NMI el crida a cada fotograma; recorre les 8 pistes de RAM de $0300) i les rutines `disable_audio_channels` ($D48E: marca totes les
 * pistes com a desactivades) i `load_audio_ram` ($D833: A = índex de la taula `sample_jmp_tbl`, X = pista de RAM 0-7) es troben per les seves seqüències de bytes. Cada
 * cançó és un conjunt de parells (índex, pista) tal com els carreguen `play_title_theme`, `play_stage_music`, etc. Verificat assemblant el desassemblatge amb cc65 (amb els
 * seus gràfics) i fent-lo sonar amb `SinteNesRom`.
 */
object DriverTransformers : NesDriver {
    override val nom = "Transformers"
    override val cancons = listOf("Títol", "Fase", "Cap de fase", "Sala lateral", "Sala del guardià", "Sala de teletransport", "Game over", "Pantalla final", "Tema dels Transformers")
    private val PISTES = listOf(
        intArrayOf(0x07, 0, 0x08, 1, 0x09, 2, 0x0A, 4),
        intArrayOf(0x00, 6, 0x01, 7),
        intArrayOf(0x0D, 6, 0x0E, 7),
        intArrayOf(0x0F, 6, 0x10, 7),
        intArrayOf(0x15, 6, 0x16, 7),
        intArrayOf(0x17, 6),
        intArrayOf(0x11, 0, 0x12, 1, 0x13, 2, 0x14, 4),
        intArrayOf(0x1B, 0, 0x1C, 1, 0x1D, 2, 0x1E, 4),
        intArrayOf(0x1F, 0, 0x20, 1, 0x21, 2, 0x22, 4))
    private val SIG_RTN = intArrayOf(0xA9, 0x00, 0x8D, 0x80, 0x03, 0x8D, 0x81, 0x03, 0x8D, 0x82, 0x03, 0x8D, 0x83, 0x03, 0xAA, 0xBD, 0x01, 0x03, 0x8D, 0x84, 0x03)
    private val SIG_LOAD = intArrayOf(0x85, 0x12, 0x0A, 0x18, 0x65, 0x12, 0xA8, 0xB9, -1, -1, 0x85, 0x12, 0xB9, -1, -1, 0x85, 0x13, 0xB9, -1, -1, 0x85, 0x14, 0x8A, 0x0A, 0x0A, 0x0A, 0x0A, 0x18, 0x69, 0x00, 0x85, 0x15, 0xA9, 0x00, 0x69, 0x03, 0x85, 0x16)
    private val SIG_OFF = intArrayOf(0x8A, 0x48, 0x98, 0x48, 0xA2, 0x00, 0x8E, 0x15, 0x40, 0x8E, -1, -1, 0xA9, 0xFF, 0x9D, -1, -1, 0x8A, 0x18, 0x69, 0x10, 0xAA, 0xC9, 0x80, 0xD0, 0xF2, 0x68, 0xA8, 0x68, 0xAA, 0x60)
    override fun reconeix(rom: NesRom) = rom.mapper == 3 && rom.prgBancs == 2 && rom.cerca(SIG_RTN) >= 0 && rom.cerca(SIG_LOAD) >= 0 && rom.cerca(SIG_OFF) >= 0
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        cpu.sp = 0xFD; cpu.crida(n1AdrFixa(rom, SIG_OFF))
        val pp = PISTES[(id - 1).coerceIn(0, PISTES.size - 1)]
        var i = 0
        while (i < pp.size) { cpu.sp = 0xFD; cpu.a = pp[i]; cpu.x = pp[i + 1]; cpu.crida(n1AdrFixa(rom, SIG_LOAD)); i += 2 }
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(n1AdrFixa(rom, SIG_RTN)) }
}

/**
 * Dr. Mario (NES, MMC1 de 32 KB). De Nostaljipi/dr-mario-disassembly (asm6f; l'`audioUpdate` és a $D3D0 a la ROM original): `audioUpdate` (la NMI el crida a cada
 * fotograma) es troba per la seva seqüència de bytes (`lda #$C0; sta $4017; jsr randomizeNoisePeriod; lda flag_pause_forAudio ($068D); cmp #5; beq; lda #0; sta
 * $0682; sta $068B; lda sfx_toPlay_sq0_sq1 ($06F4); cmp #3; beq; jmp`). La música es demana a `music_toPlay` ($06F5, que el motor posa a 0 en llegir-lo) amb les
 * constants `mus_*`: 1 cinemàtica, 2 chill, 3 silenci, 4 fever, 5 fracàs, 6 títol, 7 opcions, 8 final, 9/10/11 victòria (fever/chill/final), 12 explosió de la
 * cinemàtica (se salta el silenci). Verificat assemblant el motor de so del desassemblatge (convertit d'asm6f a ca65, sense la resta del joc ni les mostres DMC) amb la
 * configuració sense optimitzar de la revisió A i fent-lo sonar amb `SinteNesRom`. No comprovat amb la ROM comercial.
 */
object DriverDrMario : NesDriver {
    override val nom = "DrMario"
    override val cancons = listOf("Cinemàtica", "Chill", "Fever", "Fracàs", "Títol", "Opcions", "Final", "Victòria (Fever)", "Victòria (Chill)", "Victòria final", "Cinemàtica (explosió)")
    private val MUS = intArrayOf(1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12)
    private val SIG = intArrayOf(0xA9, 0xC0, 0x8D, 0x17, 0x40, 0x20, -1, -1, 0xAD, 0x8D, 0x06, 0xC9, 0x05, 0xF0, -1, 0xA9, 0x00, 0x8D, 0x82, 0x06, 0x8D, 0x8B, 0x06, 0xAD, 0xF4, 0x06, 0xC9, 0x03, 0xF0, 0x03, 0x4C)
    override fun reconeix(rom: NesRom) = rom.mapper == 1 && rom.prgBancs == 2 && rom.cerca(SIG) >= 0
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) { m.ram[0x6F5] = MUS[(id - 1).coerceIn(0, MUS.size - 1)] }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(n1AdrFixa(rom, SIG)) }
}

/**
 * Donkey Kong (NES, NROM de 16 KB). De RussianManSMWC/Donkey-Kong-NES-Disassembly (asm6): `SoundEngine` ($FA48 en totes les versions, la NMI el crida a cada
 * fotograma) es troba per la seva seqüència de bytes (`lda #$C0; sta $4017; jsr HandleAllSound; ldx #0; stx $FF; stx $FE; stx $FD`). La música de fons es demana a
 * `Sound_Music` ($FC, que es manté): $02 fase 25 m, $10 fase 100 m, $20 «es fa tard», $40 martell; les melodies curtes a `Sound_Fanfare` ($FD, un bit per melodia i el
 * motor el posa a 0): $01 inici de la partida, $02 fase completada, $04 Kong vençut, $08 inici de fase, $10 el Kong cau, $20 punts, $40 pausa, $80 pantalla de títol.
 * Verificat assemblant la secció de so del desassemblatge (convertida d'asm6 a ca65, sense la resta del joc) i fent-la sonar amb `SinteNesRom`. No comprovat amb la ROM
 * comercial.
 */
object DriverDonkeyKong : NesDriver {
    override val nom = "DonkeyKong"
    override val cancons = listOf("Fase 25 m", "Fase 100 m", "Es fa tard", "Martell", "Inici de la partida", "Fase completada", "Kong vençut", "Inici de fase", "El Kong cau", "Punts", "Pausa", "Pantalla de títol")
    private val SIG = intArrayOf(0xA9, 0xC0, 0x8D, 0x17, 0x40, 0x20, -1, -1, 0xA2, 0x00, 0x86, 0xFF, 0x86, 0xFE, 0x86, 0xFD, 0xAD, 0xF0, 0x06)
    override fun reconeix(rom: NesRom) = rom.mapper == 0 && rom.prgBancs == 1 && rom.cerca(SIG) >= 0
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        val i = (id - 1).coerceIn(0, 11)
        if (i < 4) m.ram[0xFC] = intArrayOf(0x02, 0x10, 0x20, 0x40)[i] else m.ram[0xFD] = 1 shl (i - 4)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(n1AdrFixa(rom, SIG)) }
}

/**
 * Balloon Fight (NES, NROM de 16 KB). De LuigiBlood/balloonfight_dis (bass; l'adreça de cada rutina és al nom de l'etiqueta): `lfff7_audiomain` ($FFF7, que la NMI
 * crida a cada fotograma) salta a `lf758` ($F758), que es troba per la seva seqüència de bytes (`lda #$C0; sta $4017; jsr x5; lda $F1; sta $07E9; lda #0; sta $F0-$F3;
 * rts`). La música i les melodies es demanen a `$F2` (un bit per peça; el motor el posa a 0): $01 game over, $02 fase superada, $04 pausa, $08 inici de fase, $10 fase
 * de bonus perfecta, $20 balloon trip / fase de bonus, $40 peix, $80 reaparició. Verificat assemblant el desassemblatge (convertit de bass a ca65; totes les etiquetes
 * cauen a l'adreça que porta el seu nom) i fent-lo sonar amb `SinteNesRom`. No comprovat amb la ROM comercial.
 */
object DriverBalloonFight : NesDriver {
    override val nom = "BalloonFight"
    override val cancons = listOf("Game over", "Fase superada", "Pausa", "Inici de fase", "Fase de bonus perfecta", "Balloon Trip / fase de bonus", "Peix", "Reaparició")
    private val SIG = intArrayOf(0xA9, 0xC0, 0x8D, 0x17, 0x40, 0x20, -1, -1, 0x20, -1, -1, 0x20, -1, -1, 0x20, -1, -1, 0x20, -1, -1, 0xA5, 0xF1, 0x8D, 0xE9, 0x07, 0xA9, 0x00, 0x85, 0xF0, 0x85, 0xF1, 0x85, 0xF2, 0x85, 0xF3, 0x60)
    override fun reconeix(rom: NesRom) = rom.mapper == 0 && rom.prgBancs == 1 && rom.cerca(SIG) >= 0
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) { m.ram[0xF2] = 1 shl (id - 1).coerceIn(0, 7) }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(n1AdrFixa(rom, SIG)) }
}

/**
 * Dragon Warrior (NES, MMC1 de 64 KB). De nmikstas/dragon-warrior-disassembly (Ophis; cada etiqueta porta la seva adreça): el motor de so és al banc 1 (a $8000):
 * `UpdateSound` ($8028, que la NMI crida a cada fotograma amb el banc 1 triat), `InitMusicSFX` ($81A0, A = número de música 1-$1A o d'efecte $80+) i `ClearSoundRegs`
 * ($8178; salta el seu `jsr WaitForNMI` inicial, que és al banc fix, i en fa la resta: silencia i activa els canals i posa el tempo a $FF). Les tres es troben per les
 * seves seqüències de bytes. Les cançons són les `MSC_*` (1 intro ... $1A pujada de nivell); els efectes no s'inclouen. Verificat assemblant el banc 1 del desassemblatge
 * (convertit d'Ophis a ca65; 15 etiquetes de dades no coincideixen amb el seu nom per errors del propi desassemblatge, cap abans del motor) i fent-lo sonar amb
 * `SinteNesRom`. No comprovat amb la ROM comercial.
 */
object DriverDragonWarrior : NesDriver {
    override val nom = "DragonWarrior"
    override val cancons = listOf("Intro", "Sala del tron", "Castell de Tantagel 2", "Poble", "Exterior", "Masmorra 1", "Masmorra 2", "Masmorra 3", "Masmorra 4", "Masmorra 5", "Masmorra 6", "Masmorra 7", "Masmorra 8",
        "Inici del combat", "Cap final", "Final", "Arpa de plata", "Flauta de la fada", "Pont de l'arc de Sant Martí", "Mort", "Posada", "Amor de la princesa Gwaelin", "Maleït", "Combat normal", "Victòria", "Pujada de nivell")
    private val SIG_UPD = intArrayOf(0x48, 0x8A, 0x48, 0x98, 0x48, 0xA2, 0x06, 0xA0, 0x04, 0xA5, 0xFA, 0xF0, 0x2A, 0xA5, 0xFD, 0x48, 0xA9, 0x00, 0x85, 0xFD, 0x20, -1, -1, 0xAA, 0x68, 0x85, 0xFD, 0x8A, 0xD0, 0x19, 0xA9, 0x05, 0x8D, 0x15, 0x40)
    private val SIG_INI = intArrayOf(0xA2, 0xFF, 0x8E, 0x05, 0x60, 0xAA, 0x30, 0x3A, 0x0A, 0x85, 0xFE, 0x0A, 0x65, 0xFE, 0x69, 0x04, 0xA8, 0xA2, 0x04, 0xB9, -1, -1, 0xD0, 0x0B)
    private val SIG_CLR = intArrayOf(0x20, -1, -1, 0xA9, 0x00, 0x8D, 0x10, 0x40, 0x8D, 0x17, 0x40, 0x8D, 0x15, 0x40, 0x85, 0xF4, 0x85, 0xF6, 0x85, 0xF8, 0x85, 0xFA, 0xA9, 0x0F, 0x8D, 0x15, 0x40, 0xA9, 0xFF, 0x85, 0xFB, 0xA9, 0x08, 0x8D, 0x01, 0x40, 0x8D, 0x05, 0x40, 0x60)
    private fun adr(rom: NesRom, sig: IntArray, salt: Int = 0) = 0x8000 + (rom.cerca(sig) % 0x4000) + salt
    override fun reconeix(rom: NesRom): Boolean {
        if (rom.mapper != 1 || rom.prgBancs != 4) return false
        val a = rom.cerca(SIG_UPD); val b = rom.cerca(SIG_INI); val c = rom.cerca(SIG_CLR)
        return a >= 0 && b >= 0 && c >= 0 && a / 0x4000 == 1 && b / 0x4000 == 1 && c / 0x4000 == 1
    }
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaBanc(1)
        cpu.sp = 0xFD; cpu.crida(adr(rom, SIG_CLR, 3))
        cpu.sp = 0xFD; cpu.a = (id).coerceIn(1, 0x1A); cpu.crida(adr(rom, SIG_INI))
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaBanc(1); cpu.sp = 0xFD; cpu.crida(adr(rom, SIG_UPD)) }
}

/**
 * Donald Land (NES, MMC1 de 128 KB). De brunovalads/donald-land (bass; cada etiqueta porta la seva adreça): el motor de so és al banc 4 (a $8000), que comença amb tres
 * JMP: $8000 inicia el so, $8003 demana un so (A = número; el $F0 en guarda l'últim) i $8006 el fa avançar (la NMI el crida a cada fotograma). Es reconeix i es troba
 * per aquests tres JMP i els bytes que segueixen. De la taula de sons ($89FE, 2 bytes per número) els 1 a 31 són silenci i efectes (capçalera amb el bit 7 posat) i els
 * 32 a 52 són música (capçalera $0F, quatre canals); aquí només hi ha la música i, com que el desassemblatge no la nomena, es diu «So N». Verificat assemblant el banc 4
 * del desassemblatge (convertit de bass a ca65; totes les etiquetes cauen a l'adreça que porta el seu nom) i fent-lo sonar amb `SinteNesRom`. No comprovat amb la ROM
 * comercial.
 */
object DriverDonaldLand : NesDriver {
    override val nom = "DonaldLand"
    override val cancons = (32..52).map { "So $it" }
    private val SIG = intArrayOf(0x4C, 0x11, 0x80, 0x4C, 0x1C, 0x81, 0x4C, 0xB4, 0x81, 0xC6, 0xD6, 0xE6, 0xF6, 0x06, 0x06, 0x06, 0x06, 0xA9, 0x0F, 0x8D, 0x15, 0x40, 0xA9, 0x08, 0x8D, 0x01, 0x40, 0x8D, 0x05, 0x40)
    override fun reconeix(rom: NesRom): Boolean { val o = rom.cerca(SIG); return rom.mapper == 1 && rom.prgBancs == 8 && o >= 0 && o % 0x4000 == 0 }
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaBanc(rom.cerca(SIG) / 0x4000)
        cpu.sp = 0xFD; cpu.crida(0x8000)
        cpu.sp = 0xFD; cpu.a = (31 + id).coerceIn(32, 52); cpu.crida(0x8003)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaBanc(rom.cerca(SIG) / 0x4000); cpu.sp = 0xFD; cpu.crida(0x8006) }
}

val DRIVERS_NES_N1: List<NesDriver> = listOf(DriverMarioBros, DriverTetrisNes, DriverElite, DriverTransformers, DriverDrMario, DriverDonkeyKong, DriverBalloonFight, DriverDragonWarrior, DriverDonaldLand)
