package cat.gbdsmixxx

/** Compara bytes de la ROM a desplaçaments de fitxer fixos (finestres hexadecimals del codi del so, tretes del desassemblatge assemblat). */
fun r2gFix(rom: ByteArray, sig: Array<Pair<Int, String>>): Boolean {
    for ((off, hex) in sig) {
        if (off + hex.length / 2 > rom.size) return false
        for (k in 0 until hex.length / 2) if ((rom[off + k].toInt() and 255) != hex.substring(2 * k, 2 * k + 2).toInt(16)) return false
    }
    return true
}

/**
 * Kid Icarus: Of Myths and Monsters (Game Boy, MBC1 de 128 KB). De ordinary-hacker/kid-icarus-omm (mgbdis; la ROM és `game.gb` i només les imatges venen de `baserom.gb`, així que assemblat amb les
 * imatges a zero el codi i les dades de so són els de la ROM). `Audio_Init` $0C29 i `Audio_PlaySFX` $0C55 (A = entrada de la taula `Constant_SoundTable`, banc 5 $47B8, 79 entrades de 4 bytes [canal, ?, punter]: cada cançó són 3-4 entrades consecutives,
 * una per canal, que el joc demana amb `Call_000_0c4c` i següents; aquí s'agrupen per canal creixent) són a la ROM0; cada fotograma, la interrupció de vblank (`Jump_000_01e4`) tria el banc 5 i crida `$4000`. El desassemblatge no dona noms de cançó. **Verificat amb la ROM assemblada.**
 */
object DriverKidIcarusOMM : GbDriver {
    override val nom = "KidIcarusOMM"
    override val ids = 0..36        // 0-18 efectes d'una sola pista; 19-36 grups de pistes consecutives (música)
    private val INICIS = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71, 74, 76, 77, 78, 79)
    private val SIG = arrayOf(3113 to "3E80E026AFE025EA97DD3E77", 3157 to "C5D5E5EA9EDD6FFAFF7FF53E", 81920 to "C30F40C30A76C3B777C3D877", 81984 to "DDEA9ADDF0E647F0FDB0A728")
    override fun reconeix(rom: ByteArray): Boolean = rom.size == 0x20000 && r2gFix(rom, SIG)
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.banc = 1; cpu.crida(0x0C29)                // Audio_Init
        val k = (id - ids.first).coerceIn(0, INICIS.size - 2)
        for (e in INICIS[k] until INICIS[k + 1]) { m.banc = 1; cpu.a = e; cpu.crida(0x0C55) }    // Audio_PlaySFX, una pista per entrada
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.sp = 0xDFFF; m.banc = 5; cpu.crida(0x4000) }
}

/**
 * The Jungle Book (Game Boy, MBC1 de 128 KB). De not-chciken/jungle-book-gb-disassembly (`jb.sym` i comentaris `; $xxxx`; assemblat amb els gràfics buits el banc 7 és el del joc): el motor és al banc 7, amb
 * `InitSound` a $4118 (`LoadSound0` $4000 hi salta) i `HandleSound` a $4003, que la interrupció del temporitzador crida (4096/66 = 62,06 Hz, `StartTimer` $14C5). La cançó es demana escrivint-ne el número
 * (0 a $B) a `CurrentSong` ($C500; el bit 6 fa primer un fos de sortida de l'anterior). **Verificat amb la ROM assemblada (gràfics buits).**
 */
object DriverJungleBook : GbDriver {
    override val nom = "JungleBook"
    override val ids = 0..11
    override val fps = 4096.0 / 66.0
    val NOMS = listOf("The Bare Necessities", "Colonel Hathi's March", "Trust in Me", "So 3", "So 4", "Transition level", "I Wanna Be Like You (no melody)", "Game over", "I Wanna Be Like You", "Boss", "Outro", "Boss found")
    private val SIG = arrayOf(114688 to "C31841CDD463FA06C5A72812", 114691 to "CDD463FA06C5A72812FABEC5", 114968 to "3E00E0263EFFEA00C53CEA04", 5290 to "FAFF7FF53E07C7CD0340F1C7")
    override fun reconeix(rom: ByteArray): Boolean = rom.size == 0x20000 && r2gFix(rom, SIG)
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.banc = 7; cpu.crida(0x4118)                // InitSound
        m.wb(0xC502, 12); m.wb(0xC503, 0xC0)         // FadeOutCounterResetVal i TrackEnable: el joc els fixa en iniciar la partida
        m.wb(0xC500, id)                             // CurrentSong
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.sp = 0xDFFF; m.banc = 7; cpu.crida(0x4003) }       // HandleSound
}

/**
 * Pocket King (Game Boy Color, MBC5), motor de so desassemblat per turboboy215 (PK-GBC-Audio-Disasm; les etiquetes i adreces són les del joc, el banc del motor és el $0E i les dades de pista al $0F, que
 * la rutina `GetSeqByte` de la ROM0 del joc tria). `AudioOff` $4000 (silenci, inici), `LoadSong` $407A (el número de so es passa a `SFXNum` $DEF2; les músiques són de $43 a $5F), `PlayAudio` $4165 cada
 * fotograma; abans d'`AudioOff` el joc fixa `NR50Val` ($DFA2) a $77 i després `NewWave` ($DEF7) a $FF. El banc del motor es busca pel contingut. **Verificat amb la ROM de prova del desassemblat (bancs 1 i 2; la
 * ROM original els té al $0E i $0F, la rutina de ROM0 resta la diferència; no comprovat amb la ROM original).**
 */
object DriverPocketKing : R2gDriver("PocketKing", PKSIG, 0x4000, 0, 0, intArrayOf(0x165), 0x43..0x5F, listOf()) {
    override val mbc5: Boolean? get() = true
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.banc = banc(m); m.wb(0xDFA2, 0x77); cpu.crida(0x4000)       // NR50Val; AudioOff
        m.banc = banc(m); m.wb(0xDEF7, 0xFF)                          // NewWave
        m.wb(0xDEF2, id); cpu.crida(0x407A)                           // SFXNum; LoadSong
    }
}
private val PKSIG: Array<Pair<Int, String>> = arrayOf(0 to "AFE0243E80E0263E08E010EA", 0x40 to "E018EA97DFEA98DF3E80E019", 0x7A to "FAF2DEFE06CA3641C5D587C6", 0x165 to "AF21F1DE7787C6F06F3E4BCE")

/**
 * Earthworm Jim 2 (Game Boy Color, MBC5), motor de Mark Cooksey desassemblat per turboboy215: tres bancs de so amb una còpia del motor cada un (les cançons repartides 4 + 9 + 5; els bancs reals es troben
 * pel contingut). Com als altres Cooksey: inici +$0, petició de cançó +$6 (A = número dins del banc, de l'1), fotograma +$C i +$F. Es canvia de banc i es reinicia abans de cada cançó. **Verificat amb la
 * ROM de prova del desassemblat (no amb la ROM original).**
 */
object DriverEarthwormJim2 : GbDriver {
    override val nom = "EarthwormJim2"
    override val ids = 1..18
    val NOMS = listOf("Title", "Graveyard", "Sweetshop", "Subway", "Scifi", "Desolation", "Laboratory", "Happiness", "Bedroom", "Boss1", "Boss2", "Boss3", "Boss4", "Garden", "Lvl 100 Comp", "Game 100 Comp", "Game Over", "City")
    override val mbc5: Boolean? get() = true
    private val SIGS = arrayOf(arrayOf(0 to "C3E340C37B48C32B41C32B40", 300 to "260029545D29291911C97519", 8948 to "E648E840E644E945E941E443", 11068 to "B4C0B4C5B4C0B4BCB4C0B4B9", 13036 to "35D435D235D439D43BD43BD4"),
        arrayOf(0 to "C3E340C37B48C32B41C32B40", 300 to "260029545D29291911A87719", 9152 to "44E445E244E448E448E441E2", 11300 to "54E3016459E3036459E30362", 13536 to "30123212341239123B123C12"),
        arrayOf(0 to "C3E340C37B48C32B41C32B40", 300 to "260029545D29291911546819", 8052 to "E221E223E22FE22DE22BE22A", 9224 to "A114AD14A8149D149D14A914", 10508 to "616A00000001246DC0BB6100"))
    private val NUM = intArrayOf(4, 9, 5)
    override fun reconeix(rom: ByteArray): Boolean = rom.size >= 0xC000 && SIGS.all { r2gBanc(rom, it) > 0 }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        var n = (id - ids.first).coerceIn(0, 17); var k = 0
        while (k < 2 && n >= NUM[k]) { n -= NUM[k]; k++ }
        cpu.sp = 0xDFFF
        m.banc = r2gBanc(m, SIGS[k]); cpu.crida(0x4000)
        m.banc = r2gBanc(m, SIGS[k]); cpu.a = n + 1; cpu.crida(0x4006)
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.sp = 0xDFFF; cpu.crida(0x400C); cpu.crida(0x400F) }      // el banc queda el de la cançó
}

val DRIVERS_GB_R2GB: List<GbDriver> = listOf(DriverKidIcarusOMM, DriverJungleBook, DriverPocketKing, DriverEarthwormJim2)
