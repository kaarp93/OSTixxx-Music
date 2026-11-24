package cat.gbdsmixxx

/**
 * Mole Mania (Game Boy, MBC1 de 512 KB, títol de capçalera «MOGURANYA»). De froggestspirit/mmania (les etiquetes `Logged_0x1C002` etc. porten l'adreça de fitxer, o sigui
 * banc * $4000 + adreça): hi ha tres còpies del mateix motor de so als bancs $07, $0B i $1A, cadascuna amb les seves cançons. A cada banc: $4000 és un `jr` a l'inici del so
 * (`Logged_0x1C071`: engega l'APU i la posa a zero), $4002 és l'actualització que el joc crida a cada vblank (`Logged_0x0463` copia la petició de $D244 a $DC00 i fa
 * `call $4002` al banc de la taula $048A, indexada per `$D243`) i la cançó es demana escrivint-ne el número (1..n) a $DC00; la taula de cançons és a $50FB de cada banc
 * i acaba a l'inici de la taula d'efectes (bancs $07: $510D, 9 cançons; $0B: $5129, 23 cançons; $1A: $5101, 3 cançons). Els números d'aquí són correlatius: 1-9 banc $07,
 * 10-32 banc $0B, 33-35 banc $1A. **Escrit del desassemblatge; no comprovat amb cap ROM** (el desassemblatge no porta les dades de so, que són `INCBIN`; la mida de les
 * taules surt de les adreces de les rutines).
 */
object DriverMoleMania : GbDriver {
    override val nom = "MoleMania"
    override val ids = 1..35
    private val BANCS = intArrayOf(0x07, 0x0B, 0x1A)
    private val NCANS = intArrayOf(9, 23, 3)
    private fun b(rom: ByteArray, o: Int) = rom[o].toInt() and 255
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x6C000 || String(rom, 0x134, 9, Charsets.US_ASCII) != "MOGURANYA") return false
        if (b(rom, 0x1C000) != 0x18 || b(rom, 0x1C001) != 0x6F) return false          // jr Logged_0x1C071
        for (bk in BANCS) { val o = bk * 0x4000 + 2; if (b(rom, o) != 0xCD || b(rom, o + 1) != 0xA8 || b(rom, o + 2) != 0x40) return false }   // call $40A8
        return true
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        var n = id - 1; var k = 0
        while (k < 2 && n >= NCANS[k]) { n -= NCANS[k]; k++ }
        cpu.sp = 0xDFFE
        m.banc = 0x07; cpu.crida(0x4000)             // Logged_0x1C000: inici del so
        m.banc = BANCS[k]                            // el motor d'aquest banc queda triat (el so no canvia mai de banc)
        m.wb(0xDC00, n + 1)                          // petició de cançó (dins del banc)
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.crida(0x4002) }                    // Logged_0x1C002 / 0x2C002 / 0x68002
}

/**
 * Pocket Love (Game Boy, MBC1 de 512 KB, «POCKET LOVE»). De RussianManSMWC/Pocket-Love-Disassembly (les etiquetes porten banc i adreça): el motor és a la ROM0 i les dades
 * de so al banc 1. `InitSound_00_0B2D` (posa `SoundDataBank` a 1 i inicialitza el motor), `ChangeMusic_00_0B12` (A = cançó 1-13; $0B12 demana la cançó i canvia de banc sola) i
 * `SoundEngine_00_0482`, que l'interrupció de vblank crida a cada fotograma amb el banc de so triat. Cançons 1 (MainTheme) a $0D, segons `MusicAndSFXChannelConfigurations_01_4000`
 * (la 0 és silenci). Es reconeix per la capçalera i per la primera fila de la taula del banc 1 ($4000: 4 zeros i `01 02 03 04`). **Escrit del desassemblatge; no comprovat amb cap ROM.**
 */
object DriverPocketLove : GbDriver {
    override val nom = "PocketLove"
    override val ids = 1..13
    private val SIG = intArrayOf(0, 0, 0, 0, 1, 2, 3, 4, 0x0D, 0x0E, 0x0F, 0x10)
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x8000 || String(rom, 0x134, 11, Charsets.US_ASCII) != "POCKET LOVE") return false
        for (k in SIG.indices) if ((rom[0x4000 + k].toInt() and 255) != SIG[k]) return false
        return true
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFE
        cpu.crida(0x0B2D)                            // InitSound_00_0B2D
        cpu.a = id; cpu.crida(0x0B12)                // ChangeMusic_00_0B12
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { m.banc = 1; cpu.crida(0x0482) }       // SoundEngine_00_0482
}

val DRIVERS_GB_G1: List<GbDriver> = listOf(DriverMoleMania, DriverPocketLove)
