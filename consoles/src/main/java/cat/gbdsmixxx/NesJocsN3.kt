package cat.gbdsmixxx

/**
 * Zelda II: The Adventure of Link (NES, MMC1, 128 KB de PRG). De FiendsOfTheElements/z2disassembly (porta l'adreça de cada rutina als comentaris): el motor de so és al
 * banc 6 de PRG (`$9000`, `Bank6Code2`); la NMI del joc (`bank7_related_to_sound`) el crida amb un acumulador que es salta una part dels fotogrames (`$07AB += $D8`: només
 * s'executa quan hi ha acarrejament, o sigui 216 de cada 256 fotogrames). Es reconeix pels bytes d'entrada del motor (`lda $EA; beq; lda #0; sta $4015; beq; lda #$FF; sta $4017;
 * jsr $920B`). La música es demana a `$EB` com a màscara de bits (un bit per cançó: $01 a $80); quina cançó és depèn del «món» (`$0707`: 0 = taula 1, 1-2 = taula 2, 3-4 =
 * taula 3, la resta = taula 4). El desassemblatge no dona noms a les cançons: es diuen «Món X so N». Verificat amb una ROM assemblada del propi desassemblatge (amb CHR
 * buit).
 */
object DriverZelda2 : NesDriver {
    override val nom = "Zelda2"
    // (món, bit) de cada cançó; només les que el motor té definides
    internal val COMBOS: List<Pair<Int, Int>> = listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4, 1 to 0, 1 to 1, 1 to 2, 1 to 4, 3 to 0, 3 to 1, 3 to 3, 3 to 6, 5 to 0, 5 to 1, 5 to 2, 5 to 3, 5 to 5, 5 to 6)
    override val cancons = COMBOS.mapIndexed { i, _ -> "So ${i + 1}" }
    private val SIG = intArrayOf(0xA5, 0xEA, 0xF0, 0x07, 0xA9, 0x00, 0x8D, 0x15, 0x40, 0xF0, 0x25, 0xA9, 0xFF, 0x8D, 0x17, 0x40, 0x20, -1, -1)   // el JSR final canvia entre USA ($920B) i Europa Rev 2 ($925A)
    override fun reconeix(rom: NesRom) = rom.mapper == 1 && rom.prgBancs == 8 && rom.cerca(SIG) == 6 * 0x4000 + 0x1000
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        val (mon, bit) = COMBOS[(id - 1).coerceIn(0, COMBOS.size - 1)]
        m.triaBanc(6); m.ram[0xEA] = 0; m.ram[0x707] = mon; m.ram[0xEB] = 1 shl bit
    }
    /** La ROM europea (PAL, 50 fotogrames/s) acaba el motor amb `JSR $925A` en lloc de `$920B`; és la que porta la porta de tempo. */
    private fun esPal(rom: NesRom) = rom.cerca(intArrayOf(0xA5, 0xEA, 0xF0, 0x07, 0xA9, 0x00, 0x8D, 0x15, 0x40, 0xF0, 0x25, 0xA9, 0xFF, 0x8D, 0x17, 0x40, 0x20, 0x5A, 0x92)) >= 0
    /**
     * El motor ($9000) es crida a cada fotograma de 60 Hz. A la ROM europea (PAL) s'hi aplica la porta de tempo de la ROM (`$07AB += $D8`, només es crida amb carry: 216 de cada 256
     * fotogrames, 50,7 ticks/s, o sigui el ritme d'una consola PAL); a la nord-americana (NTSC, 60 ticks/s) no. Abans no s'aplicava a cap i la europea sonava massa ràpida
     * (l'autor, 2026-11-15: «zelda2 ara va massa ràpid»); amb la porta a totes dues (primera versió) la nord-americana sonava un 16% més lenta (2026-11-13).
     */
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) {
        if (esPal(rom)) { val s = (m.ram[0x7AB] and 255) + 0xD8; m.ram[0x7AB] = s and 255; if (s <= 255) return }
        m.triaBanc(6); cpu.sp = 0xFD; cpu.crida(0x9000)
    }
}


/**
 * Crystalis (NES, MMC3, 256 KB). De crystalisdisassembly (desassemblatge sense etiquetes, però cada instrucció porta els seus bytes i el seu desplaçament, i es reassembla amb
 * ca65): el motor de so és al banc de 16 KB $0C (bancs de 8 KB $18 a $8000 i $19 a $A000; les cançons són al banc $19; els efectes, al banc de 8 KB $0C, que el motor posa a $A000 amb els
 * registres de l'MMC3). El joc l'inicia amb `JSR $8020` (després de canviar al banc $0C), demana un so posant-ne l'identificador a `$0110` i cridant `$8048`, i fa una passada
 * per fotograma amb `JSR $8186` (després de posar els bancs $18/$19 a R6/R7). Els identificadors de música són $01 a $1F ($00 és silenci) (taula de punters a `$8C0C`, 2 bytes per cançó; $02, $05
 * i $10 són buits); del $20 en amunt són efectes de so de dos canals, que no s'inclouen.
 * Es reconeix per la seqüència d'init del motor (al PRG $30020). El desassemblatge no dona noms: «So xx». Verificat amb una ROM assemblada del desassemblatge
 * (sense CHR).
 */
object DriverCrystalis : NesDriver {
    override val nom = "Crystalis"
    internal val IDS = intArrayOf(0x01, 0x03, 0x04, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F)
    override val cancons = IDS.map { "So %02X".format(it) }
    private val SIG = intArrayOf(0xA9, 0x01, 0x85, 0xF0, 0xA9, 0xFF, 0x8D, 0x11, 0x01, 0x8D, 0x12, 0x01, 0xA9, 0x00, 0x8D, 0x14, 0x01, 0x8D, 0x15, 0x01, 0x8D, 0x16, 0x01, 0x8D, 0x17, 0x01, 0x8D, 0x18, 0x01, 0x8D, 0x19, 0x01, 0x85, 0xFB, 0x8D, 0x15, 0x40, 0x85, 0xF0, 0x60)
    override fun reconeix(rom: NesRom) = rom.mapper == 4 && rom.prgBancs == 16 && rom.cerca(SIG) == 0x30020
    private fun bancs(m: NesRomMem) { m.wb(0x8000, 6); m.wb(0x8001, 0x18); m.wb(0x8000, 7); m.wb(0x8001, 0x19) }
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        bancs(m); cpu.sp = 0xFD; cpu.crida(0x8020)
        m.ram[0x110] = IDS[(id - 1).coerceIn(0, IDS.size - 1)]
        bancs(m); cpu.sp = 0xFD; cpu.crida(0x8048)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { bancs(m); cpu.sp = 0xFD; cpu.crida(0x8186) }
}


/**
 * Super Mario Bros. 2 (USA/Europa, NES, MMC3, 128 KB). De Xkeeper0/smb2 (porta els comentaris de les cues a `ram.asm`): el motor de so és als bancs de 8 KB 4 i 5
 * (parell `PRGBank_4_5`; `src/prg-4-5.asm`), que la rutina `DoSoundProcessing` del banc fix posa a $8000/$A000 abans de cridar `StartProcessingSoundQueue` ($8000, la primera
 * rutina del banc 4). Es reconeix per la seva seqüència d'entrada (`lda #$FF; sta $4017; lda $0100; cmp #$41; bne; lda #$0C; sta $4015; jmp`), que ha de ser al PRG $8000.
 * La música es demana a la cua `MusicQueue1` ($0600: $01 món, $02 selecció de personatge, $04 interior, $08 cap, $10 invencible, $20 subespai, $40 Wart, $80 títol) o a
 * `MusicQueue2` ($0603: $01 fanfàrria d'entrada/bolet, $02 cap vençut, $04 final i crèdits, $08 mort, $10 game over, $20 mini fanfàrria; el $80 atura). **No comprovat amb cap
 * ROM** (el desassemblatge es munta amb asm6f, que no es pot fer servir aquí).
 */
object DriverSmb2 : NesDriver {
    override val nom = "SMB2"
    override val cancons = listOf("Món", "Selecció de personatge", "Interior", "Cap", "Invencible", "Subespai", "Wart", "Títol", "Fanfàrria d'entrada", "Cap vençut", "Final i crèdits", "Mort", "Game over", "Mini fanfàrria")
    private val SIG = intArrayOf(0xA9, 0xFF, 0x8D, 0x17, 0x40, 0xAD, 0x00, 0x01, 0xC9, 0x41, 0xD0, -1, 0xA9, 0x0C, 0x8D, 0x15, 0x40, 0x4C)
    override fun reconeix(rom: NesRom) = rom.mapper == 4 && rom.prgBancs == 8 && rom.cerca(SIG) == 0x8000
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        if (id <= 8) m.ram[0x600] = 1 shl (id - 1) else m.ram[0x603] = 1 shl (id - 9)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaMmc3(4, 5); cpu.sp = 0xFD; cpu.crida(0x8000) }
}

/**
 * Super Mario Bros. 3 (NES, MMC3, 256 KB). De captainsouthbird/smb3 (nesasm; les adreces de RAM surten de `smb3.asm`): a cada NMI el joc posa la pàgina 28 a $A000 i la 29 a
 * $C000 (mode de PRG 1 de l'MMC3: R6 a $C000) i crida `Sound_Engine_Begin` ($A000, primera rutina de la pàgina 28), que crida `Sound_PlayMusic` i la resta del motor al banc
 * fix. Es reconeix per la seva seqüència d'entrada (`lda #$FF; sta $4017; lda Sound_QPause; bne; lda SndCur_Pause; bne; lda Sound_IsPaused; bne; jmp`), que ha de ser al PRG
 * $38000. La música es demana a `Sound_QMusic1` ($04F4: $01 mort, $02 game over, $04 victòria, $08 victòria del món, $10 caiguda de Bowser, $20 pantalla superada) o a
 * `Sound_QMusic2` ($04F5: $01-$08 mapa dels mons 1 a 8, $09 cel, $0A invencibilitat, $0B xiulet, $0C caixa de música, $0D sala del rei, $0E joc de bonus, $0F final; $10 a $C0
 * els temes de nivell). **No comprovat amb cap ROM** (el desassemblatge es munta amb nesasm, que no es pot fer servir aquí).
 */
object DriverSmb3 : NesDriver {
    override val nom = "SMB3"
    private val Q1 = listOf("Mort" to 0x01, "Game over" to 0x02, "Victòria" to 0x04, "Victòria del món" to 0x08, "Caiguda de Bowser" to 0x10, "Pantalla superada" to 0x20)
    private val Q2 = listOf(
        "Mapa del món 1" to 0x01, "Mapa del món 2" to 0x02, "Mapa del món 3" to 0x03, "Mapa del món 4" to 0x04, "Mapa del món 5" to 0x05, "Mapa del món 6" to 0x06, "Mapa del món 7" to 0x07,
        "Mapa del món 8" to 0x08, "Cel / zona de salt" to 0x09, "Invencibilitat" to 0x0A, "Xiulet" to 0x0B, "Caixa de música" to 0x0C, "Sala del rei" to 0x0D, "Joc de bonus" to 0x0E, "Final" to 0x0F,
        "Superfície" to 0x10, "Subterrani" to 0x20, "Aigua" to 0x30, "Fortalesa" to 0x40, "Cap" to 0x50, "Vaixell volador" to 0x60, "Batalla dels germans Martell" to 0x70, "Casa de Toad" to 0x80,
        "Superfície 2" to 0x90, "Interruptor P" to 0xA0, "Bowser" to 0xB0, "Carta del món 8" to 0xC0)
    override val cancons = Q1.map { it.first } + Q2.map { it.first }
    private val SIG = intArrayOf(0xA9, 0xFF, 0x8D, 0x17, 0x40, 0xAD, 0xF7, 0x04, 0xD0, -1, 0xAD, 0xE7, 0x04, 0xD0, -1, 0xAD, 0xED, 0x04, 0xD0, -1, 0x4C)
    override fun reconeix(rom: NesRom) = rom.mapper == 4 && rom.prgBancs == 16 && rom.cerca(SIG) == 28 * 0x2000
    private fun bancs(m: NesRomMem) { m.wb(0x8000, 0x46); m.wb(0x8001, 29); m.wb(0x8000, 0x47); m.wb(0x8001, 28) }
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        if (id <= Q1.size) m.ram[0x4F4] = Q1[id - 1].second else m.ram[0x4F5] = Q2[(id - 1 - Q1.size).coerceIn(0, Q2.size - 1)].second
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { bancs(m); cpu.sp = 0xFD; cpu.crida(0xA000) }
}

/**
 * Tecmo Super Bowl (NES, MMC3, 256 KB). De bruddog/Tecmo_Super_Bowl_NES_Disassembly (etiquetat i comentat a fons; els valors surten de `constants_variables/`): el motor és al banc
 * de 8 KB 27 (`SOUND_ENGINE_START`, $8000), que la NMI posa a $8000 abans de cridar-lo a cada fotograma; ell mateix tria el banc de dades de la cançó a $A000 (28 per als sons
 * $3D en amunt, 29 per als altres). Es reconeix per la seva seqüència d'entrada (`lda SOFT_APU_STATUS; bne; lda #$0F; sta $4015; sta SOFT_APU_STATUS; lda SOUND_ID_TO_PLAY;
 * cmp #STOP_SONG_SOUND_ID; bne`), que ha de ser al PRG $36000. La cançó es demana a `SOUND_ID_TO_PLAY` ($0700) amb l'identificador de `sound_ids.asm` ($20 en amunt són cançons;
 * 1 atura, 2 fa un fade-out; els identificadors inferiors són efectes). **No comprovat amb cap ROM** (el desassemblatge es munta amb asm6f, que no es pot fer servir aquí).
 */
object DriverTecmoSb : NesDriver {
    override val nom = "TecmoSB"
    private val CANCONS = listOf(
        "Pilotada inicial" to 32, "Touchdown (carrera)" to 33, "Safety" to 34, "Touchback" to 35, "Intercepció" to 36, "Canvi de bàndol" to 37, "Primer intent (jugador 1)" to 38, "Primer intent (jugador 2)" to 39,
        "Gol de camp" to 40, "Gol de camp fallit" to 41, "Lesió" to 43, "Final de quart" to 44, "Mitja part" to 45, "Cadenes" to 46, "Estadístiques" to 47, "Campió de conferència" to 48,
        "Campió de divisió" to 49, "Classificació" to 50, "Tornada de lesió" to 52, "Touchdown (passada)" to 53, "Sack" to 54, "Fumble" to 55, "Intro alternativa" to 57, "Intro (part 1)" to 58,
        "Intro (part 2)" to 59, "Pro Bowl (jugador 1)" to 60, "Pro Bowl (jugador 2)" to 61, "Playoff (jugador 1)" to 62, "Playoff (jugador 2)" to 63, "Temporada (jugador 1)" to 64,
        "Temporada (jugador 2)" to 65, "Pretemporada (jugador 1)" to 66, "Pretemporada (jugador 2)" to 67, "Guanyador de la Super Bowl" to 68, "Crèdits de la Super Bowl" to 69)
    override val cancons = CANCONS.map { it.first }
    private val SIG = intArrayOf(0xAD, 0xF4, 0x07, 0xD0, 0x08, 0xA9, 0x0F, 0x8D, 0x15, 0x40, 0x8D, 0xF4, 0x07, 0xAD, 0x00, 0x07, 0xC9, 0x01, 0xD0)
    override fun reconeix(rom: NesRom) = rom.mapper == 4 && rom.prgBancs == 16 && rom.cerca(SIG) == 27 * 0x2000
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) { m.ram[0x700] = CANCONS[(id - 1).coerceIn(0, CANCONS.size - 1)].second }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.wb(0x8000, 6); m.wb(0x8001, 27); cpu.sp = 0xFD; cpu.crida(0x8000) }
}

/**
 * EarthBound Beginnings / Mother (NES, MMC3, 256 KB; versió en anglès). De Nathan-R-Og/mother (`src/global/prg/music.asm` i `linker.cfg`): el motor i les cançons són al segment
 * `MUSIC`, de 16 KB, que el linker posa a $8000 amb el banc de 8 KB $1C (R6 = $1C, R7 = $1D). `Music_Init` és a $8006 (`jmp B28_0216`) i `Music_Tick` a $8000 (`jmp B28_0277`, a
 * cada fotograma). Es reconeix per la seqüència de `B28_0277` (`lda #$C0; sta $4017; jsr TickRNG; 5 jsr; lda #0; ldx #6; sta sram_mode,x`), que ha de ser al banc $1C (la versió
 * japonesa hi afegeix una crida i no es reconeix). La cançó es demana a `soundqueue_track` ($07F5) amb el número de pista (el prefix dels fitxers de `tracks/`; el 6 sona com el 7 si
 * el grup no té un sol personatge, i aquí el posem a 1 a les dues còpies de `pc_count` de l'SRAM). Els noms són els dels fitxers del desassemblatge. **No comprovat amb cap ROM**
 * (el desassemblatge necessita la ROM per extreure'n les dades).
 */
object DriverMother : NesDriver {
    override val nom = "Mother"
    private val PISTES = listOf(
        "Ocarina" to 1, "Flippant (batalla)" to 2, "Dangerous" to 3, "Hippie" to 4, "You Win" to 5, "Pollyanna" to 6, "Be Friends" to 7, "Yucca Desert" to 8, "Magicant" to 9, "Snowman" to 10,
        "Mt. Itoi" to 11, "Factory" to 12, "Ghastly Site" to 13, "Twinkle Elementary" to 14, "Humoresque of a Little Dog" to 15, "Poltergeist" to 16, "Underground" to 17, "Home" to 18,
        "Approaching Mt. Itoi" to 19, "Paradise Line" to 20, "Fall in Love" to 21, "Mother Earth" to 22, "Roving Tank" to 23, "Monkey Cave" to 24, "Maria's Song" to 25, "Wisdom of the World" to 26,
        "Tombstone" to 27, "Game Over" to 28, "Big Victory" to 29, "Airplane Ride" to 30, "Level Up" to 31, "Recovery" to 32, "Fanfare" to 33, "Live House" to 34, "All That I Needed Was You" to 35,
        "Melodia 1" to 36, "Melodia 2" to 37, "Melodia 3" to 38, "Melodia 4" to 39, "Melodia 5" to 40, "Melodia 6" to 41, "Melodia 7" to 42, "Melodia 8" to 43, "Giegue (batalla)" to 44,
        "Zoo" to 46, "Phone" to 47, "Young Town" to 48, "Cave of the Tail" to 49, "Path to Giegue" to 50)
    override val cancons = PISTES.map { it.first }
    private val SIG = intArrayOf(0xA9, 0xC0, 0x8D, 0x17, 0x40, 0x20, -1, -1, 0x20, -1, -1, 0x20, -1, -1, 0x20, -1, -1, 0x20, -1, -1, 0x20, -1, -1)
    override fun reconeix(rom: NesRom): Boolean { val o = rom.cerca(SIG); return rom.mapper == 4 && o >= 0 && o / 0x2000 == 0x1C }
    private fun bancs(m: NesRomMem) { m.wb(0x8000, 6); m.wb(0x8001, 0x1C); m.wb(0x8000, 7); m.wb(0x8001, 0x1D) }
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        bancs(m); m.sram[0x707] = 1; m.sram[0xD07] = 1
        cpu.sp = 0xFD; cpu.crida(0x8006)
        m.ram[0x7F5] = PISTES[(id - 1).coerceIn(0, PISTES.size - 1)].second
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { bancs(m); cpu.sp = 0xFD; cpu.crida(0x8000) }
}

/**
 * Castlevania (NES, UNROM 128 KB, USA). Del desassemblat Castlevania-Labelled-Disassembly (cvclean.asm): el motor de so és al banc 0 de PRG (`$8000`). `_func_0187` (`$8187`, A = codi de so)
 * posa la taula de 3 bytes per codi (punter a `$8185`) i engega el so; `UpdateMusic` (`$838A`) s'ha de cridar a cada fotograma (el joc ho fa a l'NMI amb el banc 0 posat) i
 * `InitializeMusic` (`$C0F7`) escriu `$4015` = `$0F` i `$4017` = `$C0`. Una cançó són tres codis seguits (un per canal: `$80`, `$04`, `$08` al primer byte de l'entrada) i es demana amb
 * el primer: els que fa servir el joc són `$27`, `$2A`... `$51` de tres en tres (15 cançons; `EachStageMusicList` usa `$2A, $2D, $30, $33, $36, $39, $3C`). Es reconeix per les
 * seqüències d'`_func_0187` a `$8187` i d'`UpdateMusic` a `$838A` del banc 0. Els noms de cançó no són al desassemblat: «So N». **Comprovat amb la ROM USA que ho reconeix; el so, no.**
 */
object DriverCastlevania : NesDriver {
    override val nom = "Castlevania"
    private val CODIS = intArrayOf(0x27, 0x2A, 0x2D, 0x30, 0x33, 0x36, 0x39, 0x3C, 0x3F, 0x42, 0x45, 0x48, 0x4B, 0x4E, 0x51)
    override val cancons = CODIS.map { "So \$%02X".format(it) }
    private val SIG_INIT = intArrayOf(0x85, 0xE5, 0xD0, 0x05, 0x85, 0xC2, 0x85, 0xD2, 0x60, 0xC9, 0x1A, 0xD0, 0x04, 0xA9, 0x01, 0x85, 0xEE)
    private val SIG_UPD = intArrayOf(0xA5, 0xE2, 0xF0, 0x21, 0xC6, 0xE3, 0xD0, 0x1D, 0xA9, 0x28, 0x85, 0xE3)
    override fun reconeix(rom: NesRom) = rom.mapper == 2 && rom.prgBancs == 8 && rom.cerca(SIG_INIT) == 0x187 && rom.cerca(SIG_UPD) == 0x38A
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaBanc(7); cpu.sp = 0xFD; cpu.crida(0xC0F7)                                          // InitializeMusic (banc fix)
        m.triaBanc(0); cpu.sp = 0xFD; cpu.a = CODIS[(id - 1).coerceIn(0, CODIS.size - 1)]; cpu.crida(0x8187)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaBanc(0); cpu.sp = 0xFD; cpu.crida(0x838A) }
}

/**
 * Gradius (NES, CNROM, 32 KB de PRG fix, USA). Sense desassemblat públic: trobat desassemblant la ROM. El motor de so és a `$EC00`-`$EFFF`; l'NMI (`$806A`) crida `$ED02` a cada
 * fotograma. `$EC1E` engega un so: A = codi, bits 0-5 = número de so, bits 6-7 = nombre d'entrades seguides menys 1 (una per canal); la taula de 3 bytes per so és a `$EFCD`
 * (registre base del canal APU `$00`/`$04`/`$08`/`$0C`, adreça de les dades). Una cançó és un grup d'entrades seguides amb la mateixa capçalera `$Dx` (tempo): de 3 canals (`$90`, `$93`, `$96`, `$A5`, `$AF`, `$B2`)
 * o de 2 (pols 2 + triangle: `$59`... `$63`, les cançons dels nivells; el pols 1 queda lliure pels efectes). Els altres grups (`$28`, `$2C`, `$37`...) comencen amb `$2x` i són efectes o jingles. Es reconeix per les seqüències de `$EC1E` i `$ED02`. Amb la ROM USA al harness: `$93`-`$A5` fan bucle (80 s sense parar), `$90`, `$AF` i `$B2` són jingles de 1 a 9 s. Les cançons es diuen «So $xx».
 */
object DriverGradius : NesDriver {
    override val nom = "Gradius"
    private val CODIS = intArrayOf(0x90, 0x93, 0x96, 0x59, 0x5B, 0x5D, 0x5F, 0x61, 0x63, 0xA5, 0xAF, 0xB2)
    override val cancons = CODIS.map { "So \$%02X".format(it) }
    private val SIG_PLAY = intArrayOf(0x85, 0xDF, 0x8A, 0x48, 0x98, 0x48, 0xA5, 0xDF, 0x2A, 0x2A, 0x2A, 0x29, 0x03, 0x85, 0xE0)
    private val SIG_UPD = intArrayOf(0xA2, 0xB0, 0xA0, 0x00, 0x86, 0xF8, 0x84, 0xF9)
    override fun reconeix(rom: NesRom) = rom.mapper == 3 && rom.prgBancs == 2 && rom.cerca(SIG_PLAY) == 0x6C1E && rom.cerca(SIG_UPD) == 0x6D02
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) { cpu.sp = 0xFD; cpu.a = CODIS[(id - 1).coerceIn(0, CODIS.size - 1)]; cpu.crida(0xEC1E) }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(0xED02) }
}

/**
 * Akumajou Densetsu (Castlevania III, Japó; NES, mapper VRC6 24, 256 KB). Sense desassemblat públic: trobat desassemblant la ROM. El motor de so és al banc de 16 KB 12 (`$8000`);
 * el banc fix té els embolcalls: `$E211` (inicia: banc 12 i `$87AE`), `$E249` (A = codi de so: banc 12 i `$8850`) i `$E238` (cada fotograma: banc 12 i `$89E2`). Els codis `$50`-`$6E` són
 * cançons senceres (taula de punters a `$8F5E`; cada una porta el banc de les dades, el nombre de canals i, per canal, índex i adreça); `$5B`, `$5C` i `$6C` repeteixen `$58`, `$56` i `$5F`.
 * Fa servir el xip VRC6 (`NesApu.escriuVrc6`: dos pols i una dent de serra, escala aproximada). Els noms no són al joc: «So $xx».
 */
object DriverAkumajou : NesDriver {
    override val nom = "Akumajou"
    private val CODIS = (0x50..0x6E).filter { it != 0x5B && it != 0x5C && it != 0x6C }.toIntArray()
    override val cancons = CODIS.map { "So \$%02X".format(it) }
    private val SIG_PLAY = intArrayOf(0x48, 0xA9, 0xFF, 0x85, 0x26, 0xA5, 0x23, 0x85, 0x25, 0xA9, 0x0C, 0x20, 0xD0, 0xE2, 0x68, 0x20, 0x50, 0x88)
    private val SIG_UPD = intArrayOf(0xA9, 0xFF, 0x85, 0x26, 0xA9, 0x0C, 0x20, 0xD0, 0xE2, 0x20, 0xE2, 0x89, 0xA9, 0x00, 0x85, 0x26, 0x60)
    override fun reconeix(rom: NesRom) = rom.mapper == 24 && rom.prgBancs == 16 && rom.cerca(SIG_PLAY) == 0x3E249 && rom.cerca(SIG_UPD) == 0x3E238
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.wb(0xC000, 0x1E)                                   // banc de 8 KB de $C000 (les mostres del DMC): el joc el deixa a $1E; sense això les primeres percussions es llegeixen del banc 0
        cpu.sp = 0xFD; cpu.crida(0xE211)
        cpu.sp = 0xFD; cpu.a = CODIS[(id - 1).coerceIn(0, CODIS.size - 1)]; cpu.crida(0xE249)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(0xE238) }
}

val DRIVERS_NES_N3: List<NesDriver> = listOf(DriverAkumajou, DriverGradius, DriverCastlevania, DriverZelda2, DriverCrystalis, DriverSmb2, DriverSmb3, DriverTecmoSb, DriverMother)
