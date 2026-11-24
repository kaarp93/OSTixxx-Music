package cat.gbdsmixxx

/**
 * Un joc de Game Boy el driver de so del qual s'executa tal com és (CPU `GbCpu`): com s'inicia el so, com es tria una cançó i què fa el joc a cada fotograma per
 * cridar el so. Cada joc amb desassemblatge públic en té un (les adreces i la seqüència surten del seu codi); es reconeix pel contingut de la ROM.
 */
interface GbDriver {
    val nom: String
    /** Joc que s'ha de poder reconèixer pel títol i pel codi del so. */
    fun reconeix(rom: ByteArray): Boolean
    /** Prepara el so (rutina d'init del joc) i demana la cançó `id`. */
    fun inicia(cpu: GbCpu, m: GbMem, id: Int)
    /** Un fotograma (1/59,7275 s): el mateix que el joc fa a cada interrupció de vblank per al so. `n` és el compte de fotogrames. */
    fun frame(cpu: GbCpu, m: GbMem, n: Long)
    /** Números de cançó que val la pena provar. */
    val ids: IntRange
    /** Fotogrames de so per segon: 59,7275 si el joc crida el so al vblank; si fa servir el temporitzador, el seu ritme. */
    val fps: Double get() = 59.7275
    /** El ritme actual de fotogrames, per als jocs que el canvien mentre sona (el temporitzador depèn de la cançó); per defecte, `fps`. */
    fun fpsActual(m: GbMem): Double = fps
    /** El joc fa servir MBC5 (banc de 9 bits); null = es llegeix de la capçalera de la ROM. */
    val mbc5: Boolean? get() = null
    /** Estat de RAM que deixa l'arrencada del joc, abans d'`inicia` (`EstatInicialGb`). Per defecte, el de `EstatInicialGb.PER_DRIVER` (buit: no canvia res). */
    fun preparaRam(cpu: GbCpu, m: GbMem) { EstatInicialGb.PER_DRIVER["GB_$nom"]?.let { EstatInicialGb.aplica(m, it) } }
}

/**
 * The Legend of Zelda: Link's Awakening (Game Boy i DX). De zladx/LADX-Disassembly: el so és al banc $1F (`SoundSystemInit` a $4000, `PlaySfx` a $4006) i la música a
 * les dues còpies del motor, bancs $1B i $1E (`PlayMusicTrack_1B/1E` a $4006). La cançó es demana escrivint-ne el número a `wMusicTrackToPlay` ($D368), i el joc
 * crida l'àudio a cada fotograma amb `PlayAudioStep` (bank0): `PlaySfx`; si `hWaveSfx` ($FFF3) és 0, segons `wMusicTrackTiming` ($C10B) una o dues vegades
 * (o una cada dos fotogrames si val 2, segons `hFrameCounter` $FFE7) el parell de motors de música.
 */
object DriverZelda : GbDriver {
    override val nom = "Zelda"
    override val ids = 1..96                  // les 96 cançons dels GBS (el número és el de «wMusicTrackToPlay»)
    private fun jp(rom: ByteArray, banc: Int, off: Int, lo: Int, hi: Int): Boolean {
        val o = banc * 0x4000 + off
        return o + 3 <= rom.size && (rom[o].toInt() and 255) == 0xC3 && (rom[o + 1].toInt() and 255) == lo && (rom[o + 2].toInt() and 255) == hi
    }
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x80000 || rom.size < 0x150) return false
        val t = String(rom, 0x134, 5, Charsets.US_ASCII)
        if (t != "ZELDA" && !(rom.size >= 0x100000 && String(rom, 0x134, 4, Charsets.US_ASCII) == "ZELD")) return false
        return jp(rom, 0x1F, 0, 0x09, 0x40) && jp(rom, 0x1F, 6, 0x1E, 0x40) && jp(rom, 0x1B, 6, 0x1E, 0x40) && jp(rom, 0x1E, 6, 0x1E, 0x40)
    }
    private fun crida(cpu: GbCpu, m: GbMem, banc: Int, adr: Int) { m.banc = banc; cpu.crida(adr) }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        crida(cpu, m, 0x1F, 0x4000)                  // SoundSystemInit
        m.wb(0xD368, id)                             // wMusicTrackToPlay
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) {
        m.wb(0xFFE7, (n and 255).toInt())            // hFrameCounter
        crida(cpu, m, 0x1F, 0x4006)                  // PlaySfx
        if (m.rb(0xFFF3) != 0) return               // hWaveSfx
        fun pas() { crida(cpu, m, 0x1B, 0x4006); crida(cpu, m, 0x1E, 0x4006) }
        val t = m.rb(0xC10B)                         // wMusicTrackTiming
        if (t == 0) pas() else if (t != 2) { pas(); pas() } else if (m.rb(0xFFE7) and 1 == 0) pas()
    }
}

/**
 * Kirby's Dream Land (Game Boy). De huderlem/kirbydreamland: tot el so és al banc 5; `InitSoundEngine` (es troba per contingut al banc 5), `PlaySE` crida la rutina de
 * $4C9E amb l'identificador de so a A (taula de 36 sons: 0 a 35) i `HandleTimer` ($4E0B) s'executa a cada interrupció del temporitzador: TMA = 188 i TAC =
 * 4096 Hz, és a dir 4096/68 = 60,24 fotogrames per segon. **No comprovat amb cap ROM** (no en tinc aquí).
 */
object DriverKirby : GbDriver {
    override val nom = "Kirby"
    /** 0 a 54: músiques (`PlaySong` → $4DC5, taula de 55 cançons a $79F3); 100 a 135: efectes de so (`PlaySE` → $4C9E, 36 sons). */
    override val ids = 0..135
    override val fps = 4096.0 / 68.0
    private val SIG_TIMER = intArrayOf(0x06, 0x07, 0x26, 0xDE, 0x3E, 0x52, 0x80, 0x6F, 0x7E, 0xA7, 0x28)
    private val SIG_INIT = intArrayOf(0x3E, 0x80, 0xE0, 0x26, 0x3E, 0x77, 0xE0, 0x24, 0x3E, 0xFF, 0xE0, 0x25, 0x3E, 0xFF, 0xEA, 0x01, 0xDE)
    private fun cerca(rom: ByteArray, des: Int, fins: Int, sig: IntArray): Int {
        var i = des
        while (i + sig.size <= minOf(fins, rom.size)) { var ok = true; for (k in sig.indices) if ((rom[i + k].toInt() and 255) != sig[k]) { ok = false; break }; if (ok) return i; i++ }
        return -1
    }
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x18000 || String(rom, 0x134, 11, Charsets.US_ASCII) != "KIRBY DREAM") return false
        return cerca(rom, 0x14E0B, 0x14E0B + SIG_TIMER.size, SIG_TIMER) == 0x14E0B && cerca(rom, 0x14000, 0x18000, SIG_INIT) >= 0
    }
    /** `$21BB` (ROM0) escriu a la RAM el mini-programa `LD [DE],A; INC DE; RET` que fa servir el descompressor `$20DA`; `$4B30` (banc 5) el fa servir per generar a `$DC00` una taula de consulta que el motor llegeix. */
    private val SIG_THUNK = intArrayOf(0x21, 0x99, 0xD0, 0xAF, 0x22, 0x22, 0x3E, 0x12)
    private val SIG_TAULA = intArrayOf(0x21, 0x3A, 0x4B, 0x11, 0x00, 0xDC, 0xCD, 0xDA, 0x20, 0xC9)
    private fun hi(rom: ByteArray, off: Int, sig: IntArray) = off + sig.size <= rom.size && sig.indices.all { (rom[off + it].toInt() and 255) == sig[it] }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        // Taula que el joc genera en arrencar (sense ella, amb la WRAM a zero, les notes de P1/P2 no s'apaguen: comprovat contra PyBoy, 270 de 270 fotogrames iguals a 8 cançons amb ella i 1477 de 2160 sense)
        if (hi(m.rom, 0x21BB, SIG_THUNK) && hi(m.rom, 0x14B30, SIG_TAULA)) { cpu.crida(0x21BB); m.banc = 5; cpu.crida(0x4B30) }
        val ad = 0x4000 + (cerca(m.rom, 0x14000, 0x18000, SIG_INIT) - 0x14000)
        m.banc = 5; cpu.crida(ad)                    // InitSoundEngine
        m.banc = 5
        if (id < 100) { cpu.a = id; cpu.crida(0x4DC5) }          // PlaySong -> rutina de música del banc 5
        else { cpu.a = id - 100; cpu.crida(0x4C9E) }             // PlaySE -> rutina d'efectes del banc 5
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { m.banc = 5; cpu.crida(0x4E0B) }     // HandleTimer
}

/**
 * Metroid II: Return of Samus. De metroidret/M2RoS: el so és al banc 4, amb tres salts a l'inici ($4000 `handleAudio`, $4003 `silenceAudio`, $4006 `initializeAudio`).
 * La cançó es demana escrivint-ne el número a `songRequest` ($CEDC) i el joc crida `handleAudio` un cop per fotograma. Les cançons són de l'1 al $20 (a la llista del
 * desassemblatge, algunes repetides: «clone»). **No comprovat amb cap ROM.**
 */
object DriverMetroid2 : GbDriver {
    override val nom = "Metroid2"
    override val ids = 1..0x20
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x14000 || String(rom, 0x134, 8, Charsets.US_ASCII) != "METROID2") return false
        for (k in 0..2) { val o = 0x10000 + 3 * k; if ((rom[o].toInt() and 255) != 0xC3 || (rom[o + 2].toInt() and 255) !in 0x40..0x7F) return false }
        return true
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.banc = 4; cpu.crida(0x4006)               // initializeAudio
        m.wb(0xCEDC, id)                             // songRequest
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { m.banc = 4; cpu.crida(0x4000) }       // handleAudio
}

/**
 * Super Mario Land. De kaspermeerts/supermarioland: el so és al banc 3. `InitSound` ($7FF3) i, a cada interrupció del temporitzador, `Call_7FF0` ($7FF0); el
 * temporitzador va a 16384 Hz amb TMA = 0, o sigui 64 fotogrames per segon. La música es demana escrivint-ne el número a $DFE8 (1 a 19, a la taula de cançons
 * del banc 3). **No comprovat amb cap ROM.**
 */
object DriverMarioLand : GbDriver {
    override val nom = "MarioLand"
    override val ids = 1..19
    override val fps = 16384.0 / 256.0
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x10000 || String(rom, 0x134, 10, Charsets.US_ASCII) != "SUPER MARI") return false
        fun b(o: Int) = rom[o].toInt() and 255
        return b(0xFFF0) == 0xC3 && b(0xFFF3) == 0xC3
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.banc = 3; cpu.crida(0x7FF3)               // InitSound
        m.wb(0xDFE8, id)                             // petició de música
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { m.banc = 3; cpu.crida(0x7FF0) }       // Call_7FF0 (interrupció del temporitzador)
}

/**
 * Tetris (Game Boy). De osnr/tetris (desassemblatge amb totes les adreces): és el mateix motor de so que Super Mario Land (mateix equip), sense banc: `jp` a $7FF0
 * (actualització, el joc la crida a cada pas del bucle principal, `call $7FF0` a $02CA) i a $7FF3 (inici). La música es demana escrivint-ne el número a $DFE8
 * (el joc hi escriu 1, 2, 3, 4, 8, 9, $10 i $11, i $FF per aturar-la). **No comprovat amb cap ROM.**
 */
object DriverTetris : GbDriver {
    override val nom = "Tetris"
    override val ids = 1..0x11
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x8000 || String(rom, 0x134, 6, Charsets.US_ASCII) != "TETRIS") return false
        return (rom[0x7FF0].toInt() and 255) == 0xC3 && (rom[0x7FF3].toInt() and 255) == 0xC3
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        cpu.crida(0x7FF3)                            // inici del so
        m.wb(0xDFE8, id)                             // petició de música
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.crida(0x7FF0) }
}

/**
 * Super Mario Land 2: 6 Golden Coins. De froggestspirit/marioland2: el so és al banc 4 (`$4000` inicia, `_UpdateSound` a `$4030`) i guarda l'estat a la RAM del
 * cartutx ($A400 i següents). El joc crida `UpdateSound` (carregar 4 al registre de banc $2100 i cridar `_UpdateSound`) només si `sSoundDisabled` ($A2CE) és 0, i demana una cançó
 * escrivint-ne el número a $A468. **No comprovat amb cap ROM.**
 */
object DriverMarioLand2 : GbDriver {
    override val nom = "MarioLand2"
    override val ids = 1..0x20
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x14000 || String(rom, 0x134, 10, Charsets.US_ASCII) != "MARIOLAND2") return false
        fun b(o: Int) = rom[o].toInt() and 255
        return b(0x10000) == 0x21 && b(0x10001) == 0x00 && b(0x10002) == 0xA4 && b(0x10030) == 0xFA && b(0x10031) == 0x5E && b(0x10032) == 0xA4
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.banc = 4; cpu.crida(0x4000)               // inici del so (neteja la RAM del so i activa l'APU)
        m.wb(0xA468, id)                             // petició de cançó
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { if (m.rb(0xA2CE) != 0) return; m.banc = 4; cpu.crida(0x4030) }
}

/**
 * Super Mario Land 3: Wario Land. De Kak2X/wl: el so és al banc 4, amb els mateixos tres salts d'entrada que Metroid II (`Sound_DoStub` $4000, `Sound_StopAllStub` $4003,
 * `Sound_InitStub` $4006) i, tot seguit a $4009, `Sound_Init` (que es reconeix pels seus bytes). Es crida `Sound_Do` a cada fotograma i es demana la música escrivint-ne
 * el número a `sBGMSet` ($A61C; l'estat del so és a la RAM del cartutx). **No comprovat amb cap ROM.**
 */
object DriverWarioLand : GbDriver {
    override val nom = "WarioLand"
    override val ids = 1..0x20
    private val SIG = intArrayOf(0x3E, 0x80, 0xE0, 0x26, 0x3E, 0x77, 0xE0, 0x24, 0x3E, 0xFF, 0xE0, 0x25, 0x21)
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x14000 || String(rom, 0x134, 8, Charsets.US_ASCII) == "METROID2") return false
        for (k in 0..2) { val o = 0x10000 + 3 * k; if ((rom[o].toInt() and 255) != 0xC3 || (rom[o + 2].toInt() and 255) != 0x40) return false }
        for (k in SIG.indices) if ((rom[0x10009 + k].toInt() and 255) != SIG[k]) return false
        return String(rom, 0x134, 4, Charsets.US_ASCII).uppercase().let { it.startsWith("WARI") || it.startsWith("SUPE") }
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.banc = 4; cpu.crida(0x4006)               // Sound_InitStub
        m.wb(0xA61C, id)                             // sBGMSet
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { m.banc = 4; cpu.crida(0x4000) }       // Sound_DoStub
}

/**
 * Mega Man II (Rockman World 2). De Kak2X/rw2: el driver de so és al banc 7 i comença a $4000 (`Sound_Do`, una funció lineal); el joc la crida des de la interrupció del
 * temporitzador (TAC = 4096 Hz; el ritme és 4096/(256-TMA) i el canvia la cançó: per defecte TMA = $D0, 85,33 Hz). La cançó es demana a `hBGMSet` ($FF98; $FF = cap);
 * `hSFXSet` ($FF99) ha de valdre $FF perquè no soni cap efecte. Cançons de l'1 (títol) al $12 (final). **No comprovat amb cap ROM.**
 */
object DriverMegaMan2 : GbDriver {
    override val nom = "MegaMan2"
    override val ids = 1..0x12
    override val fps = 4096.0 / 48.0
    private val SIG = intArrayOf(0xF0, 0x98, 0xFE, 0xFF, 0xCA)
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x20000) return false
        for (k in SIG.indices) if ((rom[0x1C000 + k].toInt() and 255) != SIG[k]) return false
        return (rom[0x1C007].toInt() and 255) == 0x47 && (rom[0x1C008].toInt() and 255) == 0xF0 && (rom[0x1C009].toInt() and 255) == 0x9A && (rom[0x1C00A].toInt() and 255) == 0xB8
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.wb(0xFF99, 0xFF)                           // hSFXSet: cap efecte
        m.wb(0xFF9A, 0xFF)                           // hBGMCur: cap cançó en marxa
        m.wb(0xFF98, id)                             // hBGMSet
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { m.banc = 7; cpu.crida(0x4000) }
    override fun fpsActual(m: GbMem): Double = 4096.0 / (256 - (m.io[0x06].toInt() and 255)).coerceAtLeast(1)

}

/**
 * Final Fantasy Adventure (Seiken Densetsu). De daid/FFA-Disassembly (amb totes les adreces): el motor de so és al banc $0F, `runSoundEngine` a $4000 (el joc la crida a
 * cada pas del bucle principal) i `initSoundEngine` a $4003. La cançó es demana escrivint-ne el número (1 a 30; 0 = aturar) a `hCurrentMusic` ($FF90); el motor la
 * compara amb `hPlayingMusic` ($FF93) i la inicia. Es reconeix per la seqüència de bytes d'entrada del banc $0F. **No comprovat amb cap ROM.**
 */
object DriverFfa : GbDriver {
    override val nom = "FFA"
    override val ids = 1..30
    private val SIG = intArrayOf(0x18, 0x04, 0x00, 0x18, 0x43, 0x00, 0xF5, 0xC5, 0xD5, 0xE5, 0xF0, 0x91, 0xB7, 0x28, 0x0F)
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x40000) return false
        for (k in SIG.indices) if ((rom[0x3C000 + k].toInt() and 255) != SIG[k]) return false
        return true
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.banc = 0x0F; cpu.crida(0x4003)            // initSoundEngine
        m.wb(0xFF90, id)                             // hCurrentMusic
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { m.banc = 0x0F; cpu.crida(0x4000) }       // runSoundEngine
}

/**
 * Donkey Kong (Game Boy, 1994). De CelestialAmber/DKGBDisasm: la interrupció del temporitzador (TMA = $BB, TAC = 4096 Hz: 4096/69 = 59,36 Hz) crida `Call_02_4003` (efectes) i
 * `Call_03_4003` (música) als bancs 2 i 3. Només es fa servir la de música: `InitSound` és a $4000 del banc 3 i `Call_03_4003` a $4003. Al banc 3, el motor llegeix la
 * petició de cançó a $C836 (1 a $28; $80 = aturar) i la cançó que sona a $C837. **No comprovat amb cap ROM.**
 */
object DriverDonkeyKong94 : GbDriver {
    override val nom = "DonkeyKong94"
    override val ids = 1..0x28
    override val fps = 4096.0 / 69.0
    private val SIG = intArrayOf(0xCD, 0xA9, 0x45, 0xCD, 0x39, 0x77, 0xC3, 0x1E, 0x48)
    override fun reconeix(rom: ByteArray): Boolean {
        if (rom.size < 0x10000 || (rom[0xC000].toInt() and 255) != 0xC3) return false
        for (k in SIG.indices) if ((rom[0xC003 + k].toInt() and 255) != SIG[k]) return false
        return true
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.banc = 3; cpu.crida(0x4000)               // InitSound
        m.wb(0xC836, id)                             // petició de música
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { m.banc = 3; cpu.crida(0x4003) }       // Call_03_4003
}

/**
 * Bits 0-3 de NR52 ($FF26: canal 1-4 actiu) com al xip, per als motors que els llegeixen (Pan Docs, «NR52»): un canal s'engega amb el trigger (bit 7 de NRx4) si el seu DAC és encès
 * (NRx2 & $F8 != 0; canal 3: NR30 bit 7), s'apaga si s'apaga el DAC, si s'acaba la longitud (NRx4 bit 6, a 256 Hz) o si s'apaga l'APU (NR52 bit 7 = 0). La CPU per defecte torna el valor
 * escrit (bits 0-3 = 0): el motor creu que el canal és apagat. La longitud es compta per fotogrames (`avanca`, a cada fotograma del driver; resolució d'un fotograma).
 * Es fa servir només als drivers que ho demanen (`instala` a `preparaRam`): cap altre joc canvia.
 */
internal class EstatCanalsGb(private val fps: Double) {
    private val on = BooleanArray(5); private val lenEn = BooleanArray(5); private val len = IntArray(5); private val dacOn = BooleanArray(5)
    private var acc = 0.0
    fun registre(a: Int, v: Int) {
        when (a) {
            0xFF11, 0xFF16, 0xFF20 -> len[if (a == 0xFF11) 1 else if (a == 0xFF16) 2 else 4] = 64 - (v and 63)
            0xFF1B -> len[3] = 256 - v
            0xFF12, 0xFF17, 0xFF21 -> { val c = if (a == 0xFF12) 1 else if (a == 0xFF17) 2 else 4; dacOn[c] = v and 0xF8 != 0; if (!dacOn[c]) on[c] = false }
            0xFF1A -> { dacOn[3] = v and 0x80 != 0; if (!dacOn[3]) on[3] = false }
            0xFF14, 0xFF19, 0xFF1E, 0xFF23 -> {
                val c = when (a) { 0xFF14 -> 1; 0xFF19 -> 2; 0xFF1E -> 3; else -> 4 }
                lenEn[c] = v and 0x40 != 0
                if (v and 0x80 != 0) { if (len[c] == 0) len[c] = if (c == 3) 256 else 64; on[c] = dacOn[c] }
            }
            0xFF26 -> if (v and 0x80 == 0) { on.fill(false); lenEn.fill(false); len.fill(0); dacOn.fill(false) }
        }
    }
    /** Un fotograma del driver: la longitud baixa a 256 Hz. */
    fun avanca() { acc += 256.0 / fps; while (acc >= 1.0) { acc -= 1.0; for (c in 1..4) if (lenEn[c] && len[c] > 0) { len[c]--; if (len[c] == 0) on[c] = false } } }
    fun nr52(escrit: Int): Int { var r = (escrit and 0x80) or 0x70; for (c in 1..4) if (on[c]) r = r or (1 shl (c - 1)); return r }
    companion object {
        /** Engega el seguiment a `m` (després que el sintetitzador hi hagi posat `alRegistre`) i fa que les lectures de NR52 el facin servir. */
        fun instala(m: GbMem, fps: Double) {
            val e = EstatCanalsGb(fps); m.extra = e
            val prev = m.alRegistre; m.alRegistre = { a, v -> e.registre(a, v); prev?.invoke(a, v) }
            m.alLlegir = { a -> if (a == 0xFF26) e.nr52(m.io[0x26].toInt() and 255) else -1 }
        }
        fun avanca(m: GbMem) { (m.extra as? EstatCanalsGb)?.avanca() }
    }
}

/** Tots els jocs amb driver executable. */
/**
 * Mario Tennis (Game Boy Color, MBC5 de 2 MB, «CGBTENNIS»). De la documentació del desassemblatge (docs/sound_engine.md; cada instrucció hi porta l'adreça): tot el motor és al banc 0
 * i fa servir el banc de WRAM 7 (`wram_bank` = $FF70, que `GbMem` ja admet) i els bancs de ROM $78-$7F per als scripts. `InitAudioEngine` $3078 (posa els 6 canals en repòs),
 * `PlaySound` $3297 (A = número de cançó, $01-$32 són les músiques) i `UpdateSoundEngine` $2F1A (el que la VBlank crida a cada fotograma). **No comprovat amb cap ROM.**
 */
object DriverMarioTennis : GbDriver {
    override val nom = "MarioTennis"
    override val ids = 1..0x32
    val NOMS = listOf("Intro", "Title screen", "Menu", "Status screen", "Dictionary", "Exhibition match", "Exhibition match link", "Mario minigame", "Win", "Lose", "Exp award", "Exp distribution", "Stat distribution", "Tiebreak", "Set point", "Game point", "Court star", "Court castle", "Court tropic", "Court warehouse", "Two on one", "Court jungle", "So 23", "So 24", "Target minigames", "Academy building", "Academy outdoors", "Dorm room", "Academy rooms", "Tennis machine", "Wall practice", "Practice match", "Junior ranking", "Senior ranking", "Varsity ranking", "Drill match", "Drill practice", "Island open", "Island open semifinal", "Island open final", "Dream match", "Island open lose", "Island open win", "Credits", "The end", "So 46", "So 47", "So 48", "So 49", "So 50")
    override val mbc5: Boolean? get() = true
    override fun reconeix(rom: ByteArray): Boolean = rom.size == 0x200000 && String(rom, 0x134, 9, Charsets.US_ASCII) == "CGBTENNIS"
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xCFFF                                // la pila a WRAM0: a $D000-$DFFF el motor canvia de banc ($FF70) i la perdria
        cpu.crida(0x3078)
        cpu.a = id; cpu.crida(0x3297)
    }
    /** El motor llegeix NR52 bit 2 a cada nota del canal 3 (`.waveTrigger`, $395D): si el canal ja sona, no el torna a disparar (nota lligada). Vegeu `EstatCanalsGb`. */
    override fun preparaRam(cpu: GbCpu, m: GbMem) { super.preparaRam(cpu, m); EstatCanalsGb.instala(m, fps) }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { EstatCanalsGb.avanca(m); cpu.sp = 0xCFFF; cpu.crida(0x2F1A) }
}

internal val DRIVERS_GB_CUSTOM: List<GbDriver> = listOf(DriverZelda, DriverKirby, DriverMetroid2, DriverMarioLand, DriverTetris, DriverMarioLand2, DriverWarioLand, DriverMegaMan2, DriverFfa, DriverDonkeyKong94, DriverMarioTennis) + DRIVERS_GB_G1 + DRIVERS_GB_G2 + DRIVERS_GB_R2GA + DRIVERS_GB_R2GB

/** Tots els drivers (fets a mà, experimentals i automàtics), construïts. Compatibilitat amb el codi d'abans: el codi nou fa servir `RegistreDrivers` (a demanda, per CRC). */
val DRIVERS_GB: List<GbDriver> get() = RegistreDrivers.llistaGb()
