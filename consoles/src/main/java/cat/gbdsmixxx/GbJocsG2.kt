package cat.gbdsmixxx

/*
 * Drivers de Game Boy Color (lot G2). Mateixa idea que `GbDriver.kt`: s'executa el codi de so original del joc amb `GbCpu`; les adreces surten dels desassemblatges
 * (comentaris amb adreça, docs/ o l'estructura de les seccions). Cap d'aquests s'ha pogut provar amb una ROM. En tots, `NOMS[id - ids.first]` és el nom de la cançó.
 */

private fun g2B(rom: ByteArray, off: Int, vararg v: Int): Boolean {
    if (off < 0 || off + v.size > rom.size) return false
    for (i in v.indices) if (v[i] >= 0 && (rom[off + i].toInt() and 255) != v[i]) return false
    return true
}

/** La capçalera ($134) comença per `t`. */
private fun g2Titol(rom: ByteArray, t: String): Boolean {
    if (rom.size < 0x150 || t.length > 16) return false
    for (i in t.indices) if ((rom[0x134 + i].toInt() and 255) != t[i].code) return false
    return true
}

/**
 * Pokemon Pinball. De pret/pokepinball: cada banc de música ($0F, $10, $11, $12 i $13) porta una còpia completa del motor, amb el mateix disseny: `Func_x000` (inici, $4000),
 * `PlaySong_BankX` ($4090: de = número de cançó dins del banc; fa ell mateix l'inici) i `Func_x180` ($4180, un cop per fotograma: `UpdateSFX` de home/audio.asm, que el joc crida des
 * del vblank). El joc tria el banc a `wCurrentSongBank`. Les cançons són les de `song_constants.asm`: banc $0F 1-7, $10 1-5, $11 1-4, $12 1-4, $13 1-2; aquí l'`id` va de l'1 al 22 seguit.
 * Cartutx MBC5 d'1 MB, títol POKEPINBALL. **No comprovat amb cap ROM.**
 */
object DriverPokePinball : GbDriver {
    override val nom = "PokePinball"
    private val BANCS = intArrayOf(0x0F, 0x10, 0x11, 0x12, 0x13)
    private val N = intArrayOf(7, 5, 4, 4, 2)
    val NOMS = listOf("Red Field", "Catch Em Red", "Hurry Up Red", "Pokedex", "Gastly Graveyard", "Haunter Graveyard", "Gengar Graveyard",
        "Blue Field", "Catch Em Blue", "Hurry Up Blue", "Hi Score", "Game Over",
        "Whack Diglett", "Whack Dugtrio", "Seel Stage", "Title Screen",
        "Mewtwo Stage", "Options", "Field Select", "Meowth Stage",
        "End Credits", "Name Entry")
    override val ids = 1..22
    override val mbc5: Boolean? get() = true
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size != 0x100000 || !g2Titol(rom, "POKEPINBALL")) return false
        for (b in BANCS) {
            val o = b * 0x4000
            if (!g2B(rom, o, 0xE5, 0xD5, 0xC5, 0xF5, 0xCD, 0x1B, 0x4B)) return false      // Func_x000: push hl,de,bc,af / call $4B1B
            if (!g2B(rom, o + 0x90, 0xD5, 0xCD, 0x00, 0x40, 0xD1, 0xCD, 0x1B, 0x4B)) return false   // PlaySong_BankX
            if (!g2B(rom, o + 0x180, 0xFA, 0x00, 0xDD, 0xA7, 0xC8)) return false           // Func_x180: ld a,[wdd00] / and a / ret z
        }
        return true
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        var k = id - 1
        var i = 0
        while (i < N.size - 1 && k >= N[i]) { k -= N[i]; i++ }
        m.banc = BANCS[i]
        cpu.d = 0; cpu.e = k + 1
        cpu.crida(0x4090)                              // PlaySong_BankX (inclou l'inici del motor)
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.sp = 0xDFFF; cpu.crida(0x4180) }     // Func_x180 (UpdateSFX)
}

/**
 * Pokemon Puzzle Challenge. De pret/pokepuzzle (comentaris `(20:4xxx)` a src/audio/engine.asm): el motor és al banc $20; `Func_81383` ($5383) inicia el so, `Func_813ba` ($53BA, l = número
 * de cançó) en demana una i `Func_814c5` ($54C5) és l'actualització que el joc crida des de `Func_13eb` a cada fotograma. Les rutines de home que fa servir el motor (`Func_13c0`,
 * `Func_13d7`, `Func_1412`) canvien de banc amb `hROMBank` ($FFA3) i escriuen a $2100, i en tornar restauren el banc que hi havia a `hROMBank`: cal tenir-hi el $20. Cançons 1-32
 * (`music_constants.asm`; les altres entrades de la taula són «None»). Cartutx MBC5, títol POKEMONPC. **No comprovat amb cap ROM.** Hi ha una crida a $21:4012 a `Func_13eb` que no és al
 * desassemblatge (banc $21 només té trossos): no s'executa.
 */
object DriverPokePuzzle : GbDriver {
    override val nom = "PokePuzzle"
    val NOMS = listOf("Unused Song 1", "Goldenrod Gym", "Azalea And Blackthorn Gym", "Unused Song2", "Violet And Olivine Gym", "Main Menu", "Travelling To The Gyms", "Mahogany Gym", "Gym Victory", "Ecruteak And Cianwood Gym", "Elite Four", "Lance", "Game Over", "Intro", "Title Screen", "Here Comes The Elite Four", "Cleffa And Igglybuffs Theme", "Elekid And Magbys Theme", "Elite Four Danger", "Here Comes Lance", "Round Cleared", "Gym Battle Danger", "Secret Trainer", "Secret Trainer Victory", "Main Menu Copy", "Credits", "Lance Danger", "Unused Song3", "Line Clear Victory", "Panel De Pon1", "Panel De Pon2", "Panel De Pon3")
    override val ids = 1..32
    override val mbc5: Boolean? get() = true
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x90000 || !g2Titol(rom, "POKEMONPC")) return false
        return g2B(rom, 0x813BA, 0xC5, 0xD5, 0xFA, 0x02, 0xC6, 0xE6, 0x01, 0xF5) &&   // Func_813ba: push bc,de / ld a,[wc602] / and 1 / push af
            g2B(rom, 0x814C5, 0x21, 0xF0, 0xC5, 0xCB, 0x7E, 0xCA, 0x20, 0x55) &&       // Func_814c5: ld hl,wc5f0 / bit 7,[hl] / jp z,$5520
            g2B(rom, 0x81383, 0xC5, 0xE5, 0x21)                                          // Func_81383
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.wb(0xFFA3, 0x20); m.banc = 0x20            // hROMBank
        cpu.crida(0x5383)                            // Func_81383: neteja l'estat i activa l'APU
        cpu.l = id; cpu.crida(0x53BA)                // Func_813ba: cançó l
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.sp = 0xDFFF; m.wb(0xFFA3, 0x20); m.banc = 0x20; cpu.crida(0x54C5) }     // Func_814c5 (update sound)
}

/**
 * The Legend of Zelda: Oracle of Ages / Oracle of Seasons. De Drenn1/oracles-disasm: `code/audio.s` és un objecte propi a `.BANK $39 SLOT 1 .ORG 0` (el banc $39, des de $4000), amb quatre salts
 * `jp` seguits: `b39_initSound` ($4000, A = banc de les dades: $39), `b39_updateSound` ($4003), `b39_playSound` ($4006, A = cançó) i `b39_stopSound` ($4009). El joc crida `updateSound` des de
 * la interrupció del temporitzador (`timerInterrupt` de code/bank0.s: TAC = 4096 Hz i TMA = $BB a velocitat normal, o sigui 4096/69 = 59,4 Hz). El motor llegeix les dades amb una rutina copiada a
 * `wMusicReadFunction` ($C000) que canvia de banc amb $2000 i torna al banc de `hSoundDataBaseBank` ($FFD9; `hSoundDataBaseBank2` és $FFD8). Cançons $01-$4B (`soundPointers.s`).
 * Es distingeixen per la capçalera (ZELDA NAYRU / ZELDA DIN, de code/bank0.s); MBC5 d'1 MB. **No comprovat amb cap ROM.**
 */
abstract class G2Oracle(override val nom: String, private val titol: String) : GbDriver {
    override val ids = 1..0x4B
    override val fps = 4096.0 / 69.0
    override val mbc5: Boolean? get() = true
    /**
     * NR52 ($FF26): el motor apaga el canal 3 (`ld a,0 / ldh (NR30),a`) i espera en bucle que el bit 2 s'apagui (`ldh a,(NR52) / and 4 / jr nz`). L'emulació de la CPU torna el valor escrit
     * (el bit 2 no s'apaga mai): el bucle no acabava, el fotograma arribava al límit d'instruccions, el canal 3 no arrencava mai i la percussió es desincronitzava (mesurat amb les ROM d'Ages i Seasons).
     * Com al xip: amb el DAC del canal 3 apagat (NR30 bit 7 = 0), el bit 2 de NR52 és 0. Només Oracle.
     */
    override fun preparaRam(cpu: GbCpu, m: GbMem) {
        super<GbDriver>.preparaRam(cpu, m)
        m.alLlegir = { a -> if (a == 0xFF26) { val v = (m.io[0x26].toInt() and 255) or 0x70; if (m.rb(0xFF1A) and 0x80 == 0) v and 4.inv() else v } else -1 }
    }
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size != 0x100000 || !g2Titol(rom, titol)) return false
        val o = 0x39 * 0x4000
        for (k in 0..3) if (!g2B(rom, o + 3 * k, 0xC3, -1, -1)) return false
        val t = (rom[o + 1].toInt() and 255) or ((rom[o + 2].toInt() and 255) shl 8)
        if (t < 0x4000 || t >= 0x8000) return false
        return g2B(rom, o + t - 0x4000, 0xE0, 0xD9, 0xCD)                         // initSound: ldh (hSoundDataBaseBank),a / call stopSound
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.wb(0xFFD8, 0x39); m.wb(0xFFD9, 0x39)       // hSoundDataBaseBank2 i hSoundDataBaseBank
        m.banc = 0x39
        cpu.a = 0x39; cpu.crida(0x4000)              // b39_initSound
        m.banc = 0x39
        cpu.a = id; cpu.crida(0x4006)                // b39_playSound
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.sp = 0xDFFF; m.banc = 0x39; cpu.crida(0x4003) }     // b39_updateSound
}

object DriverOracleAges : G2Oracle("OracleAges", "ZELDA NAYRU") {
    val NOMS = listOf("Titlescreen", "Minigame", "Overworld", "Overworld Past", "Crescent", "Essence", "Ambi Palace", "Nayru", "Gameover", "Lynna City", "Lynna Village", "Zora Village", "Essence Room", "Indoors", "Fairy Fountain", "Get Essence", "File Select", "Maku Path", "Spirits Grave", "Wing Dungeon", "Moonlit Grotto", "Skull Dungeon", "Crown Dungeon", "Mermaids Cave", "Jabu Jabus Belly", "Ancient Tomb", "Final Dungeon", "Onox Castle", "Room Of Rites", "Maku Tree", "Sadness", "Triumphant", "Disaster", "Underwater", "Pirates", "Symmetry Present", "Symmetry Past", "Tokay House", "Rosa Date", "Black Tower", "Credits1", "Credits2", "Maple Theme", "Maple Game", "Miniboss", "Boss", "Ladx Sideview", "Fairy Forest", "Crazy Dance", "Final Boss", "Twinrova", "Ganon", "Ralph", "Cave", "So 55", "Zelda Saved", "Great Moblin", "So 58", "So 59", "Syrup", "So 61", "Goron Cave", "Intro1", "Intro2", "So 65", "So 66", "So 67", "So 68", "So 69", "Black Tower Entrance", "So 71", "So 72", "So 73", "Precredits", "So 75")
}

object DriverOracleSeasons : G2Oracle("OracleSeasons", "ZELDA DIN") {
    val NOMS = listOf("Titlescreen", "Minigame", "Overworld", "Temple Remains", "Tarm Ruins", "Essence", "Unused1", "Carnival", "Gameover", "Horon Village", "Hide And Seek", "Sunken City", "Essence Room", "Indoors", "Fairy Fountain", "Get Essence", "File Select", "Heros Cave", "Gnarled Root Dungeon", "Snakes Remains", "Poison Moths Lair", "Dancing Dragon Dungeon", "Unicorns Cave", "Ancient Ruins", "Explorers Crypt", "Sword And Shield Maze", "Final Dungeon", "Onox Castle", "Room Of Rites", "Maku Tree", "Sadness", "Triumphant", "Disaster", "Subrosian Dance", "Pirates", "So 36", "Unused2", "Subrosian Shop", "Rosa Date", "Subrosia", "Credits1", "Credits2", "Maple Theme", "Maple Game", "Miniboss", "Boss", "Ladx Sideview", "So 48", "Crazy Dance", "Final Boss", "Twinrova", "Ganon", "Samasa Desert", "Cave", "So 55", "Zelda Saved", "Great Moblin", "So 58", "So 59", "Syrup", "Song Of Storms", "Goron Cave", "Intro1", "Intro2", "So 65", "So 66", "So 67", "So 68", "So 69", "Black Tower Entrance", "So 71", "So 72", "So 73", "Precredits", "So 75")
}

/**
 * Looney Tunes: Carrot Crazy. De huderlem/carrotcrazy (main.asm): tot el so és al banc 2 (`SECTION "ROM Bank $02", ROMX a $4000`), que comença amb la taula `MusicCommandTable` (37 `dw` = 74 bytes) i després
 * una fila de salts `jp` de 3 bytes: `TickMusicEngine` $404A, `PauseMusic` $404D, `ResumeMusic` $4050, `StopMusic` $4053, `PlaySoundEffect` $4056, `InitSound` $4059, `FadeInMusic` $405C, `FadeOutMusic` $405F, i
 * els `PlaySong_*` des de $4062 (IntroScene, Studio, Title, Copyright, GameOver, Unused2, SpaceStation, TazZoo, Boss, TreasureIsland, Menu, Unused3-5, CrazyTown, FuddForest), de 3 en 3 fins a $408F, i dues
 * ranures mortes ($4092 i $4095). `TickMusicEngineHome` s'executa a cada fotograma del bucle principal. Cada cançó és un carregador (no hi ha número): `id` 1-16 és el `PlaySong_*` de l'ordre anterior.
 * Cartutx MBC5, títol BUGS BUNNY, només GBC. **No comprovat amb cap ROM.**
 */
object DriverCarrotCrazy : GbDriver {
    override val nom = "CarrotCrazy"
    val NOMS = listOf("Intro Scene", "Studio", "Title", "Copyright", "Game Over", "Unused 2", "Space Station", "Taz Zoo", "Boss", "Treasure Island", "Menu", "Unused 3", "Unused 4", "Unused 5", "Crazy Town", "Fudd Forest")
    override val ids = 1..16
    override val mbc5: Boolean? get() = true
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x10000 || !g2Titol(rom, "BUGS BUNNY")) return false
        if ((rom[0x147].toInt() and 255) != 0x19 || (rom[0x143].toInt() and 255) != 0xC0) return false
        var a = 0x804A
        while (a <= 0x8095) {
            if (!g2B(rom, a, 0xC3, -1, -1)) return false
            val t = (rom[a + 1].toInt() and 255) or ((rom[a + 2].toInt() and 255) shl 8)
            if (t < 0x4000 || t >= 0x8000) return false
            a += 3
        }
        return true
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.banc = 2; cpu.crida(0x4059)                // InitSound
        m.banc = 2; cpu.crida(0x4062 + 3 * (id - 1)) // PlaySong_*
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.sp = 0xDFFF; m.banc = 2; cpu.crida(0x404A) }       // TickMusicEngine
}

/**
 * BattleTanx (GBC). De TheGh0stShip/BattleTanx-GBC, `docs/research/AUDIO_RESEARCH.md` (amb totes les adreces; el desassemblatge dona la ROM idèntica): el driver són quatre rutines a ROM0 i el codi
 * del banc $06. `Audio_InitFar` ($2EB8) l'inicia (crida `Audio_Init` $7CAE del banc $06), `Audio_QueueSound` ($2F73, e = id) posa un so a la cua i `Audio_Update` ($2F21) s'executa un cop per fotograma
 * (buida la cua, i fa avançar efectes i música). Els ids $80-$89 són les 10 músiques (`MusicPointers`, tots al banc $06); els títols no estan assignats. Cartutx MBC5 d'1 MB, títol BATTLETANX,
 * només GBC. **No comprovat amb cap ROM.**
 */
object DriverBattleTanx : GbDriver {
    override val nom = "BattleTanx"
    val NOMS = (0..9).map { "So " + (128 + it) }
    override val ids = 0x80..0x89
    override val mbc5: Boolean? get() = true
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size != 0x100000 || !g2Titol(rom, "BATTLETANX")) return false
        return g2B(rom, 0x2EB8, 0xFA, 0xFF, 0x7F, 0xF5, 0x3E, 0x06, 0xEA, 0x00, 0x20, 0xCD, 0xAE, 0x7C) &&     // Audio_InitFar
            g2B(rom, 0x2F73, 0xF5, 0xC5, 0xD5, 0xE5, 0x7B, 0xA7, 0xC8) &&                                          // Audio_QueueSound
            g2B(rom, 0x2F21, 0xF5, 0xC5, 0xD5, 0xE5, 0xFA)                                                          // Audio_Update
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.banc = 1; cpu.crida(0x2EB8)                // Audio_InitFar
        cpu.e = id; cpu.crida(0x2F73)                // Audio_QueueSound
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.sp = 0xDFFF; cpu.crida(0x2F21) }                  // Audio_Update
}

/**
 * Robopon Sun / Star. De PikalaxALT/robopon (comentaris `(1c:4xxx)` a audio/engine.asm): el motor és al banc $1C (ROM $70000) amb la taula de salts `SoundOff` $4000, `UpdateSound` $4003,
 * `StartSong` $4006 (A = cançó), `StartSFX` $4009...; `SoundOff__` ($4088) inicia el so. El joc crida `UpdateSound` des de la interrupció del temporitzador (TAC = 4096 Hz, TMA = -68: 60,24 Hz, com
 * Kirby). Cartutx HuC3 (tipus $FE): el banc es tria escrivint a $2000, el que `GbMem` ja fa amb `mbc5` forçat. Cançons 1-28 (`song_constants.asm`; en té 29 amb la 0). Títol ROBOPON SUN o ROBOPON STAR.
 * **No comprovat amb cap ROM.**
 */
object DriverRobopon : GbDriver {
    override val nom = "Robopon"
    val NOMS = listOf("Title", "Credits", "Town1", "Wild Battle", "Victory", "Trainer Battle", "Town2", "Cave", "Tower", "So 10", "Shop", "Center", "Maze Minigame", "Jump Minigame", "Card Minigame", "Punch Minigame", "Town3", "Teil Town", "Prince Teil", "World Map", "Gym Leader Battle", "Heal", "Level Up", "Encounter Beauty", "Encounter Unknown", "Encounter Evil", "So 27", "So 28")
    override val ids = 1..28
    override val fps = 4096.0 / 68.0
    override val mbc5: Boolean? get() = true
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x80000 || !g2Titol(rom, "ROBOPON S") || (rom[0x147].toInt() and 255) != 0xFE) return false
        return g2B(rom, 0x70000, 0xC3, 0x88, 0x40, 0xC3, -1, -1, 0xC3, 0x21, 0x40, 0xC3, 0x38, 0x40)
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.banc = 0x1C; cpu.crida(0x4000)             // SoundOff
        m.banc = 0x1C; cpu.a = id; cpu.crida(0x4006) // StartSong
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.sp = 0xDFFF; m.banc = 0x1C; cpu.crida(0x4003) }   // UpdateSound
}

val DRIVERS_GB_G2: List<GbDriver> = listOf(DriverPokePinball, DriverPokePuzzle, DriverOracleAges, DriverOracleSeasons, DriverCarrotCrazy, DriverBattleTanx, DriverRobopon)
