package cat.gbdsmixxx

/**
 * Port del motor M4A de GBA (pokeemerald `src/m4a.c`, `src/m4a_1.s`, `src/m4a_tables.c`) per sintetitzar en directe la música des de la ROM.
 * Cada funció diu de quina rutina ve. Configuració del joc (m4aSoundInit, igual a Emerald i FireRed): 5 canals DirectSound, volum mestre 12, 13379 Hz.
 * Els 4 canals de Game Boy (2 quadrats, ona, soroll) s'emulen amb el seu hardware (envolupant, escombrada, longitud, LFSR).
 * Fet servir: Emerald i LeafGreen/FireRed (les mateixes constants); Robí/Safir no s'ha pogut comprovar amb el seu decomp.
 */
internal object M4aT {
    val FREQ = longArrayOf(2147483648L, 2275179671L, 2410468894L, 2553802834L, 2705659852L, 2866546760L, 3037000500L, 3217589947L, 3408917802L, 3611622603L, 3826380858L, 4053909305L)
    val CGBF = intArrayOf(-2004, -1891, -1785, -1685, -1591, -1501, -1417, -1337, -1262, -1192, -1125, -1062)
    val NOISE = intArrayOf(
        0xD7, 0xD6, 0xD5, 0xD4, 0xC7, 0xC6, 0xC5, 0xC4, 0xB7, 0xB6, 0xB5, 0xB4, 0xA7, 0xA6, 0xA5, 0xA4, 0x97, 0x96, 0x95, 0x94,
        0x87, 0x86, 0x85, 0x84, 0x77, 0x76, 0x75, 0x74, 0x67, 0x66, 0x65, 0x64, 0x57, 0x56, 0x55, 0x54, 0x47, 0x46, 0x45, 0x44,
        0x37, 0x36, 0x35, 0x34, 0x27, 0x26, 0x25, 0x24, 0x17, 0x16, 0x15, 0x14, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01, 0x00)
    val CGB3VOL = intArrayOf(0x00, 0x00, 0x60, 0x60, 0x60, 0x60, 0x40, 0x40, 0x40, 0x40, 0x80, 0x80, 0x80, 0x80, 0x20, 0x20)
    val CLOCK = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 28, 30, 32, 36, 40, 42, 44, 48, 52, 54, 56, 60, 64, 66, 68, 72, 76, 78, 80, 84, 88, 90, 92, 96)
    val DELTA = intArrayOf(0, 1, 4, 9, 16, 25, 36, 49, -64, -49, -36, -25, -16, -9, -4, -1)

    fun s8(x: Int) = x.toByte().toInt()

    /** MidiKeyToFreq: relació respecte a la tecla 60 (la freqüència de la mostra és la de la tecla 60). */
    fun ratioDs(keyIn: Int, fineIn: Int): Double {
        var key = keyIn; var fine = fineIn
        if (key > 178) { key = 178; fine = 255 }
        fun v(k: Int): Long { val s = ((14 - k / 12) shl 4) or (k % 12); return FREQ[s and 15] ushr (s ushr 4) }
        val v1 = v(key); val v2 = v(key + 1)
        return (v1 + ((v2 - v1) * fine) / 256).toDouble() / 4194304.0
    }

    /** MidiKeyToFreq (m4a.c) exacte en enters: `umul3232H32(wav->freq, val1 + umul3232H32(val2 - val1, fine << 24))`. `wavFreq` és el camp cru (Hz·1024). */
    fun freqDs(wavFreq: Long, keyIn: Int, fineIn: Int): Long {
        var key = keyIn and 255; var fine = fineIn and 255
        if (key > 178) { key = 178; fine = 255 }
        fun v(k: Int): Long { val s = ((14 - k / 12) shl 4) or (k % 12); return FREQ[s and 15] ushr (s ushr 4) }
        val v1 = v(key); val v2 = v(key + 1)
        val x = (v1 + (((v2 - v1) * fine) ushr 8)) and 0xFFFFFFFFL
        return (wavFreq * x) ushr 32          // el producte (< 2^64) cap en els 64 bits baixos; `ushr 32` és la part alta sense signe
    }

    /** MidiKeyToCgbFreq (sense soroll). */
    fun cgbFreq(keyIn: Int, fineIn: Int): Int {
        var key = keyIn; var fine = fineIn
        if (key <= 35) { fine = 0; key = 0 } else { key -= 36; if (key > 130) { key = 130; fine = 255 } }
        fun v(k: Int): Int { val s = ((k / 12) shl 4) or (k % 12); return CGBF[s and 15] shr (s shr 4) }
        val v1 = v(key); val v2 = v(key + 1)
        return v1 + ((fine * (v2 - v1)) shr 8) + 2048
    }

    fun noiseFreq(keyIn: Int): Int {
        var key = keyIn
        if (key <= 20) key = 0 else { key -= 21; if (key > 59) key = 59 }
        return NOISE[key]
    }
}

/**
 * Saturació de sortida dels motors de GBA i DS: `(tanh(x) * 32767.0).toInt()` **idèntic bit a bit** al càlcul directe, però sense cridar `tanh`
 * gairebé mai (era un terç de la CPU del motor de GBA). Es fa amb una taula de tanh i interpolació d'Hermite cúbica (error < 2e-13, és a dir
 * < 1e-8 en unitats de la sortida); si el resultat cau a menys de 1e-6 d'un enter (on l'arrodoniment podria canviar), es fa el càlcul de debò.
 */
/** 1/32768 (2^-15): multiplicar-hi dona exactament el mateix que dividir per 32768 (és una potència de 2) i és més barat. */
internal const val INV15 = 1f / 32768f

object Satura {
    private const val N = 512                                  // punts per unitat
    private const val MAX = 8                                  // per sobre de |x| = 8 (o NaN) es calcula de debò
    /** Per a cada interval [i/N, (i+1)/N): els 4 coeficients del polinomi d'Hermite (valors i derivades de tanh als extrems), ja multiplicats per 32767. */
    private val C = DoubleArray(4 * N * MAX).also { c ->
        for (i in 0 until N * MAX) {
            val p0 = Math.tanh(i.toDouble() / N); val p1 = Math.tanh((i + 1).toDouble() / N)
            val m0 = (1.0 - p0 * p0) / N; val m1 = (1.0 - p1 * p1) / N
            c[4 * i] = p0 * 32767.0; c[4 * i + 1] = m0 * 32767.0
            c[4 * i + 2] = (3 * (p1 - p0) - 2 * m0 - m1) * 32767.0; c[4 * i + 3] = (2 * (p0 - p1) + m0 + m1) * 32767.0
        }
    }
    private const val EPS = 1e-6

    fun q(x: Double): Int {
        val ax = Math.abs(x)
        if (ax < 3.0e-5) return 0                              // |tanh(x)| ≤ |x|: |tanh(x) · 32767| < 0,99, que dona 0 (també x = 0)
        if (!(ax < MAX.toDouble())) return (Math.tanh(x) * 32767.0).toInt()
        val u = ax * N; val i = u.toInt(); val t = u - i; val o = 4 * i
        val y = C[o] + t * (C[o + 1] + t * (C[o + 2] + t * C[o + 3]))
        val lo = y - EPS; val k = lo.toInt()
        if (lo < 0.0 || (y + EPS).toInt() != k) return (Math.tanh(x) * 32767.0).toInt()
        return if (x < 0.0) -k else k
    }
}

class M4aTone {
    var type = 0; var key = 0; var length = 0; var panSweep = 0; var wav = 0; var attack = 0; var decay = 0; var sustain = 0; var release = 0; var tab = 0
}

class M4aChan(val num: Int) {
    var status = 0; var type = 0; var rVol = 0; var lVol = 0
    var attack = 0; var decay = 0; var sustain = 0; var release = 0
    var key = 0; var envVol = 0; var envR = 0; var envL = 0; var echoVol = 0; var echoLen = 0
    var gate = 0; var midiKey = 0; var vel = 0; var prio = 0; var rhythmPan = 0
    var track: M4aTrack? = null; var trackIdx = 0
    var wavO: OnaG? = null; var hz = 0.0; var pos = 0.0
    var ph = 0L; var lfo = 0L; var thr = 0L; var flt = 0      // instruments sintètics del Golden Sun
    var pas = 0.0; var pasSint = 0L                          // avanç per mostra (es calcula un cop per fotograma: hz només canvia al fotograma)
    var cur = 0; var cnt = 0; var fw = 0                      // SoundMainRAM: mostra actual, mostres que queden i fracció de 23 bits
    var freq = 0L; var count0 = 0                             // SoundChannel.frequency (MidiKeyToFreq) i `count` que posa ply_note (track->unk_3C: inici de la mostra)
    // Game Boy
    var length = 0; var sweep = 0; var wavRaw = 0; var wavOff = -1; var fReg = 0
    var envGoal = 0; var susGoal = 0; var envCounter = 0; var n4 = 0; var pan = 0; var panMask = 0; var modify = 0; var curWave = -2
}

class M4aTrack(val num: Int) {
    var flags = 0; var wait = 0; var patLevel = 0; val patStack = IntArray(3); var repN = 0
    var gate = 0; var key = 0; var vel = 0; var running = 0
    var keyM = 0; var pitM = 0; var keyShift = 0; var tune = 0; var bend = 0; var bendRange = 0
    var volX = 0; var vol = 0; var pan = 0; var modM = 0; var mod = 0; var modT = 0
    var lfoSpeed = 0; var lfoSpeedC = 0; var lfoDelay = 0; var lfoDelayC = 0
    var prio = 0; var echoVol = 0; var echoLen = 0; var volML = 0; var volMR = 0; var timer = 0
    val tone = M4aTone(); var pc = -1
    var voice = 0           // número d'instrument actual de la pista (comanda VOICE)
    var unk3C = 0           // ply_xcmd_0D: posició inicial de la mostra (es copia a SoundChannel.count a ply_note; Clear64byte el posa a 0)
    val chans = ArrayList<M4aChan>()
    fun clear64() {
        flags = 0; wait = 0; patLevel = 0; repN = 0; gate = 0; key = 0; vel = 0; running = 0; keyM = 0; pitM = 0; keyShift = 0; tune = 0; bend = 0; bendRange = 0
        volX = 0; vol = 0; pan = 0; modM = 0; mod = 0; modT = 0; lfoSpeed = 0; lfoSpeedC = 0; lfoDelay = 0; lfoDelayC = 0; prio = 0; echoVol = 0; echoLen = 0
        volML = 0; volMR = 0; timer = 0; unk3C = 0
    }
}

/** Hardware de so de Game Boy (canals 1-4) amb els registres que escriu CgbSound(). */
/** Condensador de sortida de la consola (filtre de pas alt): treu el continu; 0,999958 per cicle de 4,19 MHz = 0,99464 per mostra de 32768 Hz. */
class DcBlock(taux: Int = 32768) { private var cap = 0.0
    private val r = Math.pow(0.99464, 32768.0 / taux)      // el coeficient és per mostra: es canvia amb la freqüència perquè el condensador sigui el mateix
    fun f(x: Double): Double { val o = x - cap; cap = x - o * r; return o } }

class M4aGb(private val gba: Boolean = true, private val taux: Int = 32768) {
    private val DUTY = intArrayOf(0b00000001, 0b10000001, 0b10000111, 0b01111110)   // patrons de 8 passos (12,5%, 25%, 50%, 75%)
    private val ENV = intArrayOf(1, 2, 4)
    // Passos dels oscil·ladors per mostra: només es tornen a dividir quan canvia la freqüència (el mateix càlcul, el mateix resultat)
    private val pasF = IntArray(5) { -1 }; private val pasT = IntArray(5); private val pasV = DoubleArray(5)
    private var sorollP = -1; private var sorollT = 0; private var sorollV = 0.0
    val nr2 = IntArray(5); val nr3 = IntArray(5); var nr51 = 0; var nr30 = 0
    /** NR50 ($FF24): volum general esquerre (bits 6-4) i dret (bits 2-0); el valor v escala la sortida per (v+1)/8 (Pan Docs, «FF24 — NR50»). */
    var nr50 = 0x77
    /** NR52 bit 7: APU encesa. Apagada, els registres NR10-NR51 són a zero i no s'hi pot escriure (Pan Docs, «FF26 — NR52»). */
    var encesa = true; private set
    val on = BooleanArray(5); val vol = IntArray(5); val pace = IntArray(5); val up = BooleanArray(5); val etimer = IntArray(5)
    val lenEn = BooleanArray(5); val len = IntArray(5); val freq = IntArray(5); val duty = IntArray(5); val phase = DoubleArray(5)
    var swPace = 0; var swDown = true; var swShift = 0; var swTimer = 0; var swShadow = 0; var swOn = false
    val wave = IntArray(32); var wavePhase = 0.0
    /** Buffer del canal 3: l'última mostra llegida. En fer trigger la posició torna a 0 però el buffer no es refà: fins al primer pas sona la mostra d'abans i la primera que es llegeix és la 1 (Pan Docs, «Wave channel (CH3)»). */
    private var waveBuf = 0; private var waveIni = false
    var lfsr = 0x7FFF; var noiseAcc = 0.0
    private var cnt = 0

    fun setDuty(ch: Int, v: Int) { duty[ch] = (v shr 6) and 3 }
    /** Escriptura a NRx2 des de la CPU: si el DAC queda apagat (bits 3-7 a 0), el canal també s'apaga (Pan Docs, «DACs» i «FF26 — NR52»). */
    fun nrx2(ch: Int, v: Int) { nr2[ch] = v; if (v and 0xF8 == 0) on[ch] = false }
    /** Escriptura a NR30 des de la CPU: el bit 7 és el DAC del canal 3; apagat, el canal s'apaga. */
    fun nr30w(v: Int) { nr30 = v; if (v and 0x80 == 0) on[3] = false }
    /**
     * NR52 ($FF26) bit 7. Apagar: NR10-NR51 a zero i canals apagats (la RAM d'ones es manté). Encendre: el seqüenciador torna a començar pel pas 0,
     * els quadrats tornen al primer pas del cicle i el buffer del canal 3 és 0 (gbdev wiki, «Gameboy sound hardware», «Power Control»).
     */
    fun nr52(v: Int) {
        val ara = v and 0x80 != 0
        if (!ara && encesa) {
            for (ch in 1..4) { nr2[ch] = 0; nr3[ch] = 0; on[ch] = false; vol[ch] = 0; lenEn[ch] = false; freq[ch] = 0; duty[ch] = 0; pace[ch] = 0; up[ch] = false }
            // La longitud (NRx1) no es toca: és el comportament de la DMG (Pan Docs, nota de «NR52»); la CGB també la posaria a 0.
            nr10(0); nr30 = 0; nr50 = 0; nr51 = 0; swOn = false
        } else if (ara && !encesa) { cnt = 448; acc = 0.0; phase[1] = 0.0; phase[2] = 0.0; waveBuf = 0 }
        encesa = ara
    }
    /** Nota MIDI exacta dels 4 canals (P1, P2, WV, NO) a `out[off..off+3]`, de l'estat de l'APU: -1 si no sona o és soroll. */
    fun notesMidi(out: FloatArray, off: Int) {
        for (k in 0 until 4) {
            val ch = k + 1; var n = -1f
            if (ch <= 3 && on[ch] && !(lenEn[ch] && len[ch] <= 0)) {
                val sona = if (ch == 3) ((nr2[3] shr 5) and 3) != 0 else vol[ch] > 0
                val x = 2048 - freq[ch]
                if (sona && x > 0) { val hz = (if (ch == 3) 65536.0 else 131072.0) / x; n = (69.0 + 12.0 * Math.log(hz / 440.0) / Math.log(2.0)).toFloat() }
            }
            if (off + k < out.size) out[off + k] = n
        }
    }
    fun nr43(v: Int) { nr3[4] = v and 255 }
    fun setWave(nib: IntArray) { for (i in 0 until 32) wave[i] = nib[i] and 15 }
    fun nr10(v: Int) { swPace = (v shr 4) and 7; swDown = v and 8 != 0; swShift = v and 7 }
    fun nr1x(ch: Int, v: Int) { duty[ch] = (v shr 6) and 3; len[ch] = 64 - (v and 63) }
    fun nr31(v: Int) { len[3] = 256 - (v and 255) }
    fun nr41(v: Int) { len[4] = 64 - (v and 63) }
    fun nrx3(ch: Int, v: Int) { freq[ch] = (freq[ch] and 0x700) or (v and 255) }
    fun nrx4(ch: Int, v: Int) {
        freq[ch] = (freq[ch] and 0xFF) or ((v and 7) shl 8); lenEn[ch] = v and 0x40 != 0
        if (v and 0x80 != 0) trigger(ch)
    }
    private fun sweepCalc(): Int { val d = swShadow shr swShift; return if (swDown) swShadow - d else swShadow + d }
    private fun trigger(ch: Int) {
        when (ch) {
            1, 2 -> {
                on[ch] = nr2[ch] and 0xF8 != 0; vol[ch] = nr2[ch] shr 4; pace[ch] = nr2[ch] and 7; up[ch] = nr2[ch] and 8 != 0
                etimer[ch] = if (pace[ch] == 0) 8 else pace[ch]; if (len[ch] == 0) len[ch] = 64
                phase[ch] = Math.floor(phase[ch] * 8.0) / 8.0          // el pas del cicle no es reinicia, només el temporitzador (Pan Docs, «Pulse channels»)
                if (ch == 1) {
                    swShadow = freq[1]; swTimer = if (swPace == 0) 8 else swPace; swOn = swPace != 0 || swShift != 0
                    if (swShift != 0 && sweepCalc() > 2047) on[1] = false
                }
            }
            3 -> { on[3] = nr30 and 0x80 != 0; if (len[3] == 0) len[3] = 256; wavePhase = 0.0; waveIni = true }
            else -> {
                on[4] = nr2[4] and 0xF8 != 0; vol[4] = nr2[4] shr 4; pace[4] = nr2[4] and 7; up[4] = nr2[4] and 8 != 0
                etimer[4] = if (pace[4] == 0) 8 else pace[4]; if (len[4] == 0) len[4] = 64; lfsr = 0x7FFF
            }
        }
    }
    fun oscOff(ch: Int) {
        when (ch) { 3 -> { nr30 = 0; on[3] = false }; else -> { nr2[ch] = 8; on[ch] = false; vol[ch] = 0 } }
    }
    fun loadWave(rom: RomGba, off: Int) {
        if (off < 0 || off + 16 > rom.mida) return
        for (i in 0 until 16) { val b = rom.u8(off + i); wave[2 * i] = b shr 4; wave[2 * i + 1] = b and 15 }
    }

    /** El seqüenciador de fotogrames compta a 32768 Hz «virtuals» (una mostra a 32768 Hz de sortida); a una altra freqüència s'avança amb un acumulador. */
    private var acc = 0.0
    private val accPas = 32768.0 / taux
    private fun seq() { acc += accPas; while (acc >= 1.0) { acc -= 1.0; pasSeq() } }
    private fun pasSeq() {
        cnt++
        if (cnt % 128 == 0) { // 256 Hz: longitud
            for (ch in 1..4) if (lenEn[ch] && len[ch] > 0) { len[ch]--; if (len[ch] == 0) on[ch] = false }
        }
        if (cnt % 256 == 128) { // 128 Hz: escombrada del canal 1 (passos 2 i 6 del seqüenciador de 512 Hz; gbdev wiki, «Frame Sequencer»)
            if (swTimer > 0) swTimer--
            if (swTimer == 0) {
                swTimer = if (swPace == 0) 8 else swPace
                if (swOn && swPace != 0) {
                    val nf = sweepCalc()
                    if (nf > 2047) on[1] = false
                    else if (swShift != 0) { swShadow = nf; freq[1] = nf; if (sweepCalc() > 2047) on[1] = false }
                }
            }
        }
        if (cnt % 512 == 448) { // 64 Hz: envolupant (pas 7)
            for (ch in ENV) if (on[ch] && pace[ch] != 0) {
                etimer[ch]--
                if (etimer[ch] <= 0) { etimer[ch] = pace[ch]; if (up[ch]) { if (vol[ch] < 15) vol[ch]++ } else if (vol[ch] > 0) vol[ch]-- }
            }
        }
    }

    /**
     * Sortida analògica d'un canal (-1..1) com la consola: cada canal dona un valor digital 0..15 que el seu DAC (només si està encès) converteix
     * en analògic (digital/7,5 - 1); un canal apagat amb el DAC encès dona el valor de 0 digital (un desplaçament de continu que després treu el
     * condensador de sortida, vegeu DcBlock). Els quadrats i l'ona es mitjanen 4 cops per mostra per no fer alias.
     */
    fun out(ch: Int, taux: Int): Double {
        val dac = if (ch == 3) nr30 and 0x80 != 0 else (nr2[ch] and 0xF8) != 0
        if (!dac) return 0.0
        return digital(ch, taux) / 7.5 - 1.0
    }

    /**
     * Valor digital del canal (0..15, mitjana de l'interval de la mostra): el que suma la GBA al seu mesclador digital (no hi ha DAC analògic per canal;
     * mGBA `GBAudioSamplePSG` amb `GB_AUDIO_GBA`: sense desplaçament de continu). 0 si el canal no sona o té el DAC apagat.
     */
    fun digital(ch: Int, taux: Int): Double {
        val dac = if (ch == 3) nr30 and 0x80 != 0 else (nr2[ch] and 0xF8) != 0
        if (!dac) return 0.0
        var dig = 0.0
        if (on[ch]) when (ch) {
            1, 2 -> {
                val patro = DUTY[duty[ch] and 3]
                if (pasF[ch] != freq[ch] || pasT[ch] != taux) { pasF[ch] = freq[ch]; pasT[ch] = taux; pasV[ch] = 131072.0 / (2048 - freq[ch]) / (taux * 4) }
                val pas = pasV[ch]
                var s = 0
                var ph = phase[ch]
                for (k in 0 until 4) { ph += pas; if (ph >= 1.0) ph -= Math.floor(ph); if ((patro shr (ph * 8).toInt().coerceIn(0, 7)) and 1 != 0) s++ }
                phase[ch] = ph
                dig = vol[ch] * s / 4.0
            }
            3 -> {
                val v = nr2[3]
                if (pasF[3] != freq[3] || pasT[3] != taux) { pasF[3] = freq[3]; pasT[3] = taux; pasV[3] = 65536.0 / (2048 - freq[3]) / (taux * 4) }
                val pas = pasV[3]
                var s = 0.0; var wp = wavePhase
                for (k in 0 until 4) {
                    wp += pas; if (wp >= 1.0) { wp -= Math.floor(wp); waveIni = false }
                    val idx = (wp * 32).toInt().coerceIn(0, 31)
                    if (waveIni && idx == 0) { } else { waveIni = false; waveBuf = wave[idx] }
                    val n = waveBuf
                    // 75 % (GBA, NR32 bit 7): mGBA `sample += sample << 1; sample >>= 2` (trunca)
                    s += if (gba && v and 0x80 != 0) ((n * 3) shr 2).toDouble() else when ((v shr 5) and 3) { 0 -> 0.0; 1 -> n.toDouble(); 2 -> (n shr 1).toDouble(); else -> (n shr 2).toDouble() }
                }
                wavePhase = wp
                dig = s / 4.0
            }
            else -> {
                // El soroll avança a 262144/(r·2^s) Hz, molt per sobre de la mostra de sortida: es fa la mitjana dels bits de cada interval
                val p = nr3[4]
                if (sorollP != p || sorollT != taux) { val s = p shr 4; val r = p and 7; sorollP = p; sorollT = taux; sorollV = 262144.0 / ((if (r == 0) 0.5 else r.toDouble()) * (1 shl s)) / taux }
                if (p shr 4 < 14) noiseAcc += sorollV              // desplaçament 14 o 15: el LFSR no rep cap rellotge (Pan Docs, «FF22 — NR43»)
                val n = minOf(noiseAcc.toInt(), 128); noiseAcc -= noiseAcc.toInt()
                var suma = 0
                if (n == 0) suma = if (lfsr and 1 == 0) 1 else 0
                else { val r = lfsrPassos(lfsr, n, p and 8 != 0); lfsr = r shr 8; suma = r and 255 }          // n passos del LFSR a blocs (idèntic al bucle d'un a un: provat amb tots els estats, n = 0..128 i els dos modes; eines/opt/gb/LfsrProva.kt)
                dig = vol[4] * suma.toDouble() / (if (n == 0) 1 else n)
            }
        }
        return dig
    }
    fun tick() = seq()
    /** Nivell actual del canal `ch` (1 a 4), de 0 a 1: el volum de l'envolupant (el del nivell d'ona al canal 3) si sona i el DAC és actiu; 0 si no. */
    fun nivell(ch: Int): Float {
        if (!on[ch]) return 0f
        return when (ch) {
            3 -> if (nr30 and 0x80 == 0) 0f else when ((nr2[3] shr 5) and 3) { 1 -> 1f; 2 -> 0.5f; 3 -> 0.25f; else -> 0f }
            else -> if ((nr2[ch] and 0xF8) == 0) 0f else vol[ch] / 15f
        }
    }
}

/** Funsies: instruments per a una pista: ROM i cançó de la qual s'agafa el grup de veus, i número d'instrument fix (-1 = el que demani la pista). */
class VeuGba(val rom: RomGba, val canco: Int, val prog: Int, val oct: Int = 0)

/** Motor de GBA: `fidel` = so original 100 % (mesclador de la consola, amb el seu gra); desactivat (per defecte), el mesclador propi de la 1.0 (sense gra, però pot tenir errors de so). Es llegeix en començar cada cançó. */
object GbaSo { @JvmField var fidel = false; const val CLAU_ENV = "gba_so_fidel" }

class SinteGba(private val rom: RomGba, song: Int, private val muts: BooleanArray, solo0: Int = -1, maxCh: Int = rom.maxChans, private val masterVol: Int = rom.volMestre, bancDe: Int = -1, romVeus: RomGba? = null, veus: Array<VeuGba?>? = null) : Sintetitzador {
    override val taux = RadioFreq.taux
    @Volatile override var solo = solo0

    private val h = rom.capcalera(song)
    /** ROM d'on surten els instruments (grup de veus, taules i mostres): la mateixa de la cançó o, si es demana, la d'un altre joc. */
    private val rv: RomGba = romVeus ?: rom
    private val vg = rv.pt(rv.u32((if (bancDe >= 0) rv.capcalera(bancDe) else h) + 4))      // grup de veus: el de la cançó o, si es demana, el d'una altra cançó (d'aquest joc o d'un altre)
    // Funsies: instruments propis de cada pista (ROM i grup de veus d'una altra cançó, i número d'instrument fix; -1 = el que digui la pista)
    private val rvT = Array(16) { veus?.getOrNull(it)?.rom ?: rv }
    private val vgT = IntArray(16) { k -> veus?.getOrNull(k)?.let { v -> v.rom.pt(v.rom.u32(v.rom.capcalera(v.canco) + 4)) } ?: vg }
    private val progT = IntArray(16) { veus?.getOrNull(it)?.prog ?: -1 }
    override val volPistes = FloatArray(16) { 1f }
    private val octT = IntArray(16) { veus?.getOrNull(it)?.oct ?: 0 }       // Funsies: octaves amunt/avall de cada pista
    private val prioSong = rom.u8(h + 2)
    private val reverb = rom.u8(h + 3).let { if (it and 0x80 != 0) it and 0x7F else rom.reverbMode }   // MPlayStart: només si porta el bit 0x80; si no, la del mode del joc (m4aSoundMode)
    private val resolucio = rom.resolucio                    // SOUNDBIAS bits 14-15 (m4aSoundMode, DA_BIT)
    private val tracks = ArrayList<M4aTrack>()
    private val ds = Array(maxCh) { M4aChan(0) }              // maxChans (5 a Pokémon)
    private val cgb = Array(4) { M4aChan(it + 1).also { c -> c.panMask = 0x11 shl it; c.type = it + 1 } }
    private val gb = M4aGb(true, taux)                         // el seqüenciador de fotogrames (512 Hz) compta a la freqüència de sortida real
    private val hpL = DcBlock(taux); private val hpR = DcBlock(taux)
    private var tempoI = 150; private var tempoC = 0; private var c15 = 0
    private val memAcc = IntArray(256)
    private val tg = FloatArray(16) { 1f }
    private val gbOwner = IntArray(5)

    init {
        val n = minOf(rom.nPistes(song), rom.maxPistes(song), 16)   // MPlayStart: min(pistes de la cançó, pistes del reproductor)
        for (t in 0 until n) { val tr = M4aTrack(t); tr.flags = 0x80 or 0x40; tr.pc = rom.pt(rom.u32(h + 8 + 4 * t)); tracks.add(tr) }
    }
    override fun pistaActiva(i: Int) = i < tracks.size
    private var pistaBucle = -1; private var voltesN = 0
    override val voltes: Int get() = voltesN
    private val rellotge = RellotgeBatec(24)
    private var colector: DoubleArray? = null
    private val acum = Array(16) { AcumPerfil() }
    private val usosN = IntArray(128)
    override val usos: IntArray? get() = usosN
    override val perfils: Array<PerfilPista?>? get() = Array(16) { acum[it].perfil() }
    /** Nivell de cada pista: el volum dels seus canals que sonen (envolupant dreta + esquerra dels de mostra; el volum dels de la Game Boy). */
    override val nivells: FloatArray? get() = FloatArray(tracks.size.coerceAtMost(16)) { i ->
        var m = 0f
        for (c in ArrayList(tracks[i].chans)) { if (c.status and 0xC7 == 0) continue; m = maxOf(m, (c.envR + c.envL) / 260f, c.envVol / 15f * 0.6f) }
        m.coerceIn(0f, 1f)
    }
    override fun analitza(maxSegons: Int): DoubleArray? {
        val h = DoubleArray(12); colector = h
        val max = (maxSegons * 59.7275).toInt(); var f = 0
        while (f < max && voltesN < 1 && !acabada) { frame(false); f++ }
        colector = null
        return h
    }
    override val batec: Double get() = rellotge.posicio(tempoC / 150.0)
    override val batecMax: Double get() = rellotge.maxim()
    /** Oscil·loscopi per pista (1 a 16): la contribució de cada pista a la mescla, a ~8 kHz. */
    override val oscil = OscDades(List(16) { (it + 1).toString() })
    private val oscT = FloatArray(16); private val oscV = FloatArray(16); private var oscN = 0
    /** Nota MIDI exacta de cada pista (clau del canal + transposició + pitch bend); -1 = callada, sense to (soroll) o percussió de to fix. */
    override fun notesMidi(out: FloatArray): Boolean {
        for (k in 0 until minOf(16, out.size)) out[k] = -1f
        for (t in tracks) {
            val i = t.num.coerceIn(0, 15)
            cadaCanal(t.chans) { c ->
                if (c.status and 0xC7 == 0 || c.type and 0x08 != 0) return@cadaCanal
                val cg = c.type and 7
                if (cg == 4) return@cadaCanal
                if (cg != 0) { val g = FloatArray(4); gb.notesMidi(g, 0); if (g[cg - 1] >= 0f && i < out.size && out[i] < 0f) out[i] = g[cg - 1]; return@cadaCanal }
                if (i < out.size && out[i] < 0f) out[i] = (c.key + t.keyM).coerceAtLeast(0) + t.pitM / 256f
            }
        }
        return true
    }
    /** m4a: 24 rellotges per negra; cada fotograma (59,7275 Hz) suma `tempoI` i cada 150 és un rellotge. */
    override val bpm: Double get() = tempoI * 59.7275 * 60.0 / (150.0 * 24.0)
    override val acabada: Boolean get() = tracks.all { it.flags and 0x80 == 0 } && ds.all { it.status and 0xC7 == 0 }

    // ---------------- Reproductor (MPlayMain) ----------------
    private fun s8(o: Int) = rom.s8(o)
    private fun rd(t: M4aTrack): Int = rom.u8(t.pc++)

    /** Recorre els canals d'una pista de l'últim al primer, i així se'n pot treure el que es visita (abans es feia una còpia, `ArrayList(t.chans)`, a cada tic);
     *  el que es fa a cada canal no depèn dels altres, i l'ordre no canvia res. */
    private inline fun cadaCanal(l: ArrayList<M4aChan>, f: (M4aChan) -> Unit) { var j = l.size - 1; while (j >= 0) { if (j < l.size) f(l[j]); j-- } }
    private fun clearChain(c: M4aChan) { c.track?.chans?.remove(c); c.track = null }

    private fun clearModM(t: M4aTrack) { t.lfoSpeedC = 0; t.modM = 0; t.flags = t.flags or (if (t.modT == 0) 0x0C else 0x03) }

    private fun fine(t: M4aTrack) {
        cadaCanal(t.chans) { c -> if (c.status and 0xC7 != 0) c.status = c.status or 0x40; clearChain(c) }
        t.flags = 0
    }

    private fun goTo(t: M4aTrack) { val d = rom.pt(rom.u32(t.pc)); if (d < 0) fine(t) else t.pc = d }

    private fun trkVolPitSet(t: M4aTrack) {
        if (t.flags and 1 != 0) {
            var x = (t.vol * t.volX) ushr 5
            if (t.modT == 1) x = (x * (t.modM + 128)) ushr 7
            var y = 2 * t.pan
            if (t.modT == 2) y += t.modM
            if (y < -128) y = -128 else if (y > 127) y = 127
            t.volMR = (((y + 128) * x) ushr 8) and 255
            t.volML = (((127 - y) * x) ushr 8) and 255
        }
        if (t.flags and 4 != 0) {
            val bend = t.bend * t.bendRange
            var x = (t.tune + bend) * 4 + (t.keyShift shl 8)
            if (t.modT == 0) x += 16 * t.modM
            t.keyM = M4aT.s8(x shr 8); t.pitM = x and 0xFF
        }
        t.flags = t.flags and 0x05.inv()
    }

    private fun chnVolSet(c: M4aChan, t: M4aTrack) {
        val rp = M4aT.s8(c.rhythmPan)
        c.rVol = minOf(255, (t.volMR * ((0x80 + rp) * c.vel)) shr 14)
        c.lVol = minOf(255, (t.volML * ((0x7F - rp) * c.vel)) shr 14)
    }

    private fun tick() {
        for (t in tracks) {
            if (t.flags and 0x80 == 0) continue
            cadaCanal(t.chans) { c ->
                if (c.status and 0xC7 == 0) clearChain(c)
                else if (c.gate != 0) { c.gate--; if (c.gate == 0) c.status = c.status or 0x40 }
            }
            if (t.flags and 0x40 != 0) { t.clear64(); t.flags = 0x80; if (octT[t.num.coerceIn(0, 15)] != 0) { t.keyShift = 12 * octT[t.num.coerceIn(0, 15)]; t.flags = t.flags or 0x0C }; t.bendRange = 2; t.volX = 0x40; t.lfoSpeed = 0x16; t.tone.type = 1 }
            var guard = 0
            while (true) {
                if (++guard > 5000) { fine(t); break }
                if (t.wait == 0) {
                    if (t.pc < 0 || t.pc >= rom.mida) { fine(t); break }
                    rellotge.visita(t.num, t.pc)
                    var c = rom.u8(t.pc)
                    if (c >= 0x80) { t.pc++; if (c >= 0xBD) t.running = c } else c = t.running
                    if (c >= 0xCF) plyNote(c - 0xCF, t)
                    else if (c > 0xB0) { cmd(c - 0xB1, t); if (t.flags == 0) break }
                    else t.wait = M4aT.CLOCK[c - 0x80]
                    continue
                }
                t.wait--
                if (t.lfoSpeed != 0 && t.mod != 0) {
                    if (t.lfoDelayC != 0) t.lfoDelayC--
                    else {
                        // MPlayMain (_081DD95A): la suma es desa en un byte però es fa servir SENSE truncar (`adds r1, r0, 0` després de `strb`): si
                        // passa de 255 i el byte baix cau a 0x40..0xBF, el triangle surt de 0x80 - suma (no de 0x80 - byte). Es replica tal qual.
                        val suma = t.lfoSpeedC + t.lfoSpeed
                        t.lfoSpeedC = suma and 255
                        val x = if (((suma - 0x40) and 0x80) == 0) 0x80 - suma else M4aT.s8(suma)
                        val m = M4aT.s8((t.mod * x) shr 6)
                        if (m != t.modM) { t.modM = m; t.flags = t.flags or (if (t.modT == 0) 0x0C else 0x03) }
                    }
                }
                break
            }
        }
    }

    private fun cmd(i: Int, t: M4aTrack) {
        when (i) {
            0 -> fine(t)
            1 -> {      // GOTO cap enrere = bucle de la cançó; es compta a la primera pista que en fa un
                val d = rom.pt(rom.u32(t.pc))
                if (d in 0 until t.pc) { if (pistaBucle < 0) pistaBucle = t.num; if (pistaBucle == t.num) voltesN++; rellotge.salt(t.num, d) }
                goTo(t)
            }
            2 -> if (t.patLevel < 3) { t.patStack[t.patLevel] = t.pc + 4; t.patLevel++; goTo(t) } else fine(t)
            3 -> if (t.patLevel > 0) { t.patLevel--; t.pc = t.patStack[t.patLevel] }
            4 -> {   // ply_rept
                if (rom.u8(t.pc) == 0) { t.pc++; goTo(t) }
                else {
                    t.repN++; val cnt = rd(t)
                    if (t.repN >= cnt) { t.repN = 0; t.pc += 4 } else goTo(t)
                }
            }
            8 -> memacc(t)
            9 -> t.prio = rd(t)
            10 -> tempoI = rd(t) * 2
            11 -> { t.keyShift = s8(t.pc++) + 12 * octT[t.num.coerceIn(0, 15)]; t.flags = t.flags or 0x0C }
            12 -> {
                val nv = rd(t); val rv = rvT[t.num]; t.voice = nv
                val o = vgT[t.num] + 12 * (if (progT[t.num] >= 0) progT[t.num] else nv)
                if (o < 0 || o + 12 > rv.mida) return
                val to = t.tone
                to.type = rv.u8(o); to.key = rv.u8(o + 1); to.length = rv.u8(o + 2); to.panSweep = rv.u8(o + 3)
                to.wav = rv.u32(o + 4); to.tab = rv.u32(o + 8)
                to.attack = rv.u8(o + 8); to.decay = rv.u8(o + 9); to.sustain = rv.u8(o + 10); to.release = rv.u8(o + 11)
            }
            13 -> { t.vol = rd(t); t.flags = t.flags or 0x03 }
            14 -> { t.pan = M4aT.s8(rd(t) - 64); t.flags = t.flags or 0x03 }          // ply_pan: `subs r3, C_V; strb` (s8)
            15 -> { t.bend = M4aT.s8(rd(t) - 64); t.flags = t.flags or 0x0C }
            16 -> { t.bendRange = rd(t); t.flags = t.flags or 0x0C }
            17 -> { t.lfoSpeed = rd(t); if (t.lfoSpeed == 0) clearModM(t) }
            18 -> t.lfoDelay = rd(t)
            19 -> { t.mod = rd(t); if (t.mod == 0) clearModM(t) }
            20 -> { val v = rd(t); if (t.modT != v) { t.modT = v; t.flags = t.flags or 0x0F } }
            23 -> { t.tune = M4aT.s8(rd(t) - 64); t.flags = t.flags or 0x0C }
            27 -> t.pc += 2
            28 -> xcmd(t)
            29 -> endTie(t)
            else -> fine(t)
        }
    }

    private fun memacc(t: M4aTrack) {
        val op = rd(t); val a = rd(t); val d = rd(t)
        val v = memAcc[a]; val w = memAcc[d]
        fun cond(b: Boolean) { if (b) goTo(t) else t.pc += 4 }
        when (op) {
            0 -> memAcc[a] = d
            1 -> memAcc[a] = (v + d) and 255
            2 -> memAcc[a] = (v - d) and 255
            3 -> memAcc[a] = w
            4 -> memAcc[a] = (v + w) and 255
            5 -> memAcc[a] = (v - w) and 255
            6 -> cond(v == d); 7 -> cond(v != d); 8 -> cond(v > d); 9 -> cond(v >= d); 10 -> cond(v <= d); 11 -> cond(v < d)
            12 -> cond(v == w); 13 -> cond(v != w); 14 -> cond(v > w); 15 -> cond(v >= w); 16 -> cond(v <= w); 17 -> cond(v < w)
        }
    }

    private fun xcmd(t: M4aTrack) {
        val n = rd(t)
        when (n) {
            1 -> { t.tone.wav = rom.u32(t.pc); t.pc += 4 }
            2 -> t.tone.type = rd(t)
            4 -> t.tone.attack = rd(t)
            5 -> t.tone.decay = rd(t)
            6 -> t.tone.sustain = rd(t)
            7 -> t.tone.release = rd(t)
            8 -> t.echoVol = rd(t)
            9 -> t.echoLen = rd(t)
            10 -> t.tone.length = rd(t)
            11 -> t.tone.panSweep = rd(t)
            12 -> { val len = rom.u16(t.pc); if (t.timer < len) { t.timer++; t.pc -= 2; t.wait = 1 } else { t.timer = 0; t.pc += 2 } }
            13 -> { t.unk3C = rom.u32(t.pc); t.pc += 4 }        // ply_xcmd_0D
            else -> fine(t)
        }
    }

    private fun endTie(t: M4aTrack) {
        val k: Int
        if (rom.u8(t.pc) < 0x80) { t.key = rom.u8(t.pc); t.pc++; k = t.key } else k = t.key
        // ply_endtie recorre la cadena des del cap, i ply_note hi posa cada canal nou AL CAP: el primer que es troba és el més nou (l'últim de `chans`)
        for (j in t.chans.indices.reversed()) {
            val c = t.chans[j]
            if (c.status and (0x80 or 0x03) != 0 && c.status and 0x40 == 0 && c.midiKey == k) { c.status = c.status or 0x40; break }
        }
    }

    // ---------------- ply_note ----------------
    private fun plyNote(len: Int, t: M4aTrack) {
        t.gate = M4aT.CLOCK[len]
        if (rom.u8(t.pc) < 0x80) {
            t.key = rom.u8(t.pc++)
            if (rom.u8(t.pc) < 0x80) {
                t.vel = rom.u8(t.pc++)
                if (rom.u8(t.pc) < 0x80) { t.gate += rom.u8(t.pc++) }
            }
        }
        if (t.gate > 255) t.gate = t.gate and 255
        val rv = rvT[t.num]           // instruments d'aquesta pista
        val tone = t.tone
        var rhythmPan = 0
        var key = t.key
        var sub = M4aTone(); var subOff = -1
        val ty = tone.type
        if (ty and 0xC0 != 0) {
            val idx = if (ty and 0x40 != 0) { val tab = rv.pt(tone.tab); if (tab < 0) return; rv.u8(tab + t.key) } else t.key
            val g = rv.pt(tone.wav); if (g < 0) return
            subOff = g + 12 * idx
            if (subOff + 12 > rv.mida) return
            if (rv.u8(subOff) and 0xC0 != 0) return
            if (ty and 0x80 != 0) {
                val ps = rv.u8(subOff + 3)
                if (ps and 0x80 != 0) rhythmPan = M4aT.s8((ps - 0xC0) * 2)
                key = rv.u8(subOff + 1)
            }
            sub.type = rv.u8(subOff); sub.length = rv.u8(subOff + 2); sub.panSweep = rv.u8(subOff + 3)
            sub.wav = rv.u32(subOff + 4)
            sub.attack = rv.u8(subOff + 8); sub.decay = rv.u8(subOff + 9); sub.sustain = rv.u8(subOff + 10); sub.release = rv.u8(subOff + 11)
        } else {
            sub = tone
        }
        val pri = minOf(255, t.prio + prioSong)
        val cgbType = sub.type and 7
        // Anàlisi de tonalitat: notes melòdiques (no rítmiques, ni de freqüència fixa, ni soroll), pesades per la durada
        if (colector != null) usosN[t.voice.coerceIn(0, 127)]++
        if (colector != null) acum[t.num.coerceIn(0, 15)].nota(key + t.keyM, (if (t.gate <= 0) 24 else t.gate) / 24.0, ty and 0x80 != 0, cgbType == 4, cgbType == 1 || cgbType == 2, cgbType == 3)
        colector?.let { h -> if (ty and 0x80 == 0 && sub.type and 0x08 == 0 && cgbType != 4) h[Math.floorMod(key + t.keyM, 12)] += (if (t.gate <= 0) 24 else t.gate.coerceIn(4, 96)).toDouble() }
        val ch: M4aChan
        if (cgbType != 0) {
            ch = cgb[cgbType - 1]
            if (ch.status and 0xC7 != 0 && ch.status and 0x40 == 0) {
                if (ch.prio > pri) return
                if (ch.prio == pri && (ch.track?.num ?: 0) < t.num) return
            }
        } else {
            var victim: M4aChan? = null; var free: M4aChan? = null
            var found = false; var bp = pri; var bt = t.num
            for (i in 0 until ds.size) {
                val c = ds[i]
                if (c.status and 0xC7 == 0) { free = c; break }
                val tn = c.track?.num ?: 0
                if (c.status and 0x40 != 0) {
                    if (!found) { found = true; bp = c.prio; bt = tn; victim = c }
                    else if (c.prio < bp) { bp = c.prio; bt = tn; victim = c }
                    else if (c.prio == bp) { if (tn > bt) { bt = tn; victim = c } else if (tn == bt) victim = c }
                } else if (!found) {
                    if (c.prio < bp) { bp = c.prio; bt = tn; victim = c }
                    else if (c.prio == bp) { if (tn > bt) { bt = tn; victim = c } else if (tn == bt) victim = c }
                }
            }
            ch = free ?: victim ?: return
        }
        val wavO = if (cgbType == 0) (rv.ona(rv.pt(sub.wav)) ?: return) else null
        // Agafa el canal (_081DDC40)
        clearChain(ch)
        ch.track = t; ch.trackIdx = t.num; t.chans.add(ch)
        t.lfoDelayC = t.lfoDelay
        if (t.lfoDelay != 0) clearModM(t)
        trkVolPitSet(t)
        ch.gate = t.gate; ch.midiKey = t.key; ch.vel = t.vel; ch.prio = pri
        ch.key = key; ch.rhythmPan = rhythmPan
        ch.type = sub.type
        ch.attack = sub.attack; ch.decay = sub.decay; ch.sustain = sub.sustain; ch.release = sub.release
        ch.echoVol = t.echoVol; ch.echoLen = t.echoLen
        chnVolSet(ch, t)
        var kk = key + t.keyM; if (kk < 0) kk = 0
        if (cgbType != 0) {
            ch.length = sub.length
            val ps = sub.panSweep
            ch.sweep = if (ps and 0x80 != 0) 8 else if (ps and 0x70 != 0) ps else 8
            ch.wavRaw = sub.wav
            ch.wavOff = if (cgbType == 3) rv.pt(sub.wav) else -1
            ch.fReg = if (cgbType == 4) M4aT.noiseFreq(kk) else M4aT.cgbFreq(kk, t.pitM)
            gbOwner[cgbType] = t.num
        } else {
            ch.wavO = wavO; ch.pos = 0.0; ch.ph = 0L; ch.lfo = 0L; ch.thr = 0L; ch.flt = 0
            ch.hz = wavO!!.hz * M4aT.ratioDs(kk, t.pitM)
            ch.freq = M4aT.freqDs(wavO.freq, kk, t.pitM); ch.count0 = t.unk3C
            ch.echoLen = t.echoLen
        }
        ch.status = 0x80
        t.flags = t.flags and 0xF0
    }

    // ---------------- Final del fotograma de MPlayMain: volum i to dels canals ----------------
    private fun volPitUpdate() {
        for (t in tracks) {
            if (t.flags and 0x80 == 0 || t.flags and 0x0F == 0) continue
            trkVolPitSet(t)
            cadaCanal(t.chans) { c ->
                if (c.status and 0xC7 == 0) { clearChain(c); return@cadaCanal }
                val isCgb = c.type and 7
                if (t.flags and 0x03 != 0) { chnVolSet(c, t); if (isCgb != 0) c.modify = c.modify or 1 }
                if (t.flags and 0x0C != 0) {
                    var kk = c.key + t.keyM; if (kk < 0) kk = 0
                    if (isCgb != 0) {
                        c.fReg = if (isCgb == 4) M4aT.noiseFreq(kk) else M4aT.cgbFreq(kk, t.pitM)
                        c.modify = c.modify or 2
                    } else { c.hz = (c.wavO?.hz ?: 0.0) * M4aT.ratioDs(kk, t.pitM); c.freq = M4aT.freqDs(c.wavO?.freq ?: 0L, kk, t.pitM) }
                }
            }
            t.flags = t.flags and 0xF0
        }
    }

    @Volatile private var tempoX = 1.0; private var tempoF = 0.0
    override var ritme: Double get() = tempoX; set(v) { tempoX = v }
    override val tempoPropi: Boolean get() = true
    private fun mplayMain() {
        tempoF += tempoI * tempoX; val afegeix = tempoF.toInt(); tempoF -= afegeix; tempoC += afegeix
        while (tempoC >= 150) { rellotge.tic(); tick(); tempoC -= 150 }
        volPitUpdate()
    }

    // ---------------- CgbSound ----------------
    private fun s(x: Int) = M4aT.s8(x)

    private fun cgbPan(c: M4aChan): Boolean {
        val r = c.rVol and 255; val l = c.lVol and 255
        if (r >= l) { if (r / 2 >= l) { c.pan = 0x0F; return true } } else { if (l / 2 >= r) { c.pan = 0xF0; return true } }
        return false
    }
    private fun modVol(c: M4aChan) {
        val gv = volPistes[c.trackIdx.coerceIn(0, 15)]
        if (!cgbPan(c)) { c.pan = 0xFF; c.envGoal = ((c.lVol + c.rVol) / 16 * gv).toInt() }
        else { c.envGoal = ((c.lVol + c.rVol) / 16 * gv).toInt(); if (c.envGoal > 15) c.envGoal = 15 }
        c.susGoal = (c.envGoal * c.sustain + 15) shr 4
        c.pan = c.pan and c.panMask
    }

    private fun cgbSound() {
        if (c15 != 0) c15-- else c15 = 14
        for (ch in 1..4) {
            val c = cgb[ch - 1]
            if (c.status and 0xC7 == 0) continue
            var prev = c15
            var envStd = gb.nr2[ch]
            // etiquetes del codi C
            val REPEAT = 1; val STEP = 2; val ENVC = 3; val OFF = 4; val DECST = 5; val SUSST = 6; val SUSTAIN = 7; val ECHO = 8; val DONE = 9
            var lab: Int
            if (c.status and 0x80 != 0) {
                if (c.status and 0x40 == 0) {
                    c.status = 0x03; c.modify = 3; modVol(c)
                    var initEnv = false
                    when (ch) {
                        1 -> { gb.nr10(c.sweep); gb.nr1x(1, ((c.wavRaw shl 6) + c.length) and 255); initEnv = true }
                        2 -> { gb.nr1x(2, ((c.wavRaw shl 6) + c.length) and 255); initEnv = true }
                        3 -> {
                            if (c.wavOff != c.curWave) { gb.loadWave(rvT[c.trackIdx.coerceIn(0, 15)], c.wavOff); c.curWave = c.wavOff }
                            gb.nr30 = 0; gb.nr31(c.length); c.n4 = if (c.length != 0) 0xC0 else 0x80
                        }
                        else -> { gb.nr41(c.length); gb.nr3[4] = (c.wavRaw shl 3) and 255; initEnv = true }      // `*nrx3ptr = (u32)wavePointer << 3` (registre de 8 bits)
                    }
                    if (initEnv) { envStd = c.attack + 0x08; c.n4 = if (c.length != 0) 0x40 else 0 }
                    c.envCounter = c.attack
                    if (s(c.attack) != 0) { c.envVol = 0; lab = STEP } else lab = DECST
                } else lab = OFF
            } else if (c.status and 0x04 != 0) {
                c.echoLen = (c.echoLen - 1) and 255
                lab = if (s(c.echoLen) <= 0) OFF else ENVC
            } else if (c.status and 0x40 != 0 && c.status and 0x03 != 0) {
                c.status = c.status and 0x03.inv(); c.envCounter = c.release
                if (s(c.release) != 0) { c.modify = c.modify or 1; if (ch != 3) envStd = c.release or 0; lab = STEP } else lab = ECHO
            } else lab = REPEAT
            loop@ while (true) {
                when (lab) {
                    REPEAT -> {
                        lab = STEP
                        if (c.envCounter == 0) {
                            if (ch == 3) c.modify = c.modify or 1
                            modVol(c)
                            when (c.status and 0x03) {
                                0 -> { c.envVol = (c.envVol - 1) and 255; if (s(c.envVol) <= 0) lab = ECHO else c.envCounter = c.release }
                                1 -> lab = SUSTAIN
                                2 -> { c.envVol = (c.envVol - 1) and 255; if (s(c.envVol) <= s(c.susGoal)) lab = SUSST else c.envCounter = c.decay }
                                else -> { c.envVol = (c.envVol + 1) and 255; if (c.envVol >= c.envGoal) lab = DECST else c.envCounter = c.attack }
                            }
                        }
                    }
                    SUSTAIN -> { c.envVol = c.susGoal; c.envCounter = 7; lab = STEP }
                    SUSST -> {
                        if (c.sustain == 0) { c.status = c.status and 0x03.inv(); lab = ECHO }
                        else { c.status--; c.modify = c.modify or 1; if (ch != 3) envStd = 0x08; lab = SUSTAIN }
                    }
                    DECST -> {
                        c.status--; c.envCounter = c.decay
                        if (c.envCounter and 255 != 0) { c.modify = c.modify or 1; c.envVol = c.envGoal; if (ch != 3) envStd = c.decay or 0; lab = STEP }
                        else lab = SUSST
                    }
                    ECHO -> {
                        c.envVol = ((c.envGoal * c.echoVol) + 0xFF) shr 8
                        if (c.envVol != 0) { c.status = c.status or 0x04; c.modify = c.modify or 1; if (ch != 3) envStd = 0x08; lab = ENVC } else lab = OFF
                    }
                    STEP -> {
                        c.envCounter = (c.envCounter - 1) and 255
                        if (prev == 0) { prev--; lab = REPEAT } else lab = ENVC
                    }
                    ENVC -> {
                        if (c.modify and 2 != 0) {
                            // CgbSound: to fix segons la freqüència del PWM (SOUNDBIAS_H): 32768 Hz → +2 & 0x7FC; 65536 Hz → +1 & 0x7FE; més alta, igual
                            if (ch < 4 && c.type and 0x08 != 0) { if (resolucio == 0) c.fReg = (c.fReg + 2) and 0x7FC else if (resolucio == 1) c.fReg = (c.fReg + 1) and 0x7FE }
                            if (ch != 4) gb.nrx3(ch, c.fReg and 255) else gb.nr3[4] = (gb.nr3[4] and 0x08) or (c.fReg and 255)
                            c.n4 = (c.n4 and 0xC0) + ((c.fReg shr 8) and 255)
                            gb.nrx4(ch, c.n4 and 255)
                        }
                        if (c.modify and 1 != 0) {
                            gb.nr51 = (gb.nr51 and c.panMask.inv()) or c.pan
                            if (ch == 3) {
                                gb.nr2[3] = M4aT.CGB3VOL[c.envVol.coerceIn(0, 15)]
                                if (c.n4 and 0x80 != 0) { gb.nr30 = 0x80; gb.nrx4(3, c.n4); c.n4 = c.n4 and 0x7F }
                            } else {
                                gb.nr2[ch] = (envStd and 0xF) + ((c.envVol shl 4) and 0xFF)
                                gb.nrx4(ch, c.n4 or 0x80)
                            }
                        }
                        lab = DONE
                    }
                    OFF -> { gb.oscOff(ch); c.status = 0; lab = DONE }
                    else -> break@loop
                }
            }
            c.modify = 0
        }
    }

    // ---------------- DirectSound: envolupant per fotograma (SoundMainRAM) ----------------
    private fun dsEnvelope(c: M4aChan): Boolean {
        var st = c.status
        if (st and 0xC7 == 0) return false
        var r5: Int
        if (st and 0x80 != 0) {
            if (st and 0x40 != 0) { c.status = 0; return false }
            st = 3
            if (c.wavO?.bucle == true) st = st or 0x10
            c.status = st; c.pos = 0.0; c.ph = 0L; c.lfo = 0L; c.thr = 0L; c.flt = 0; r5 = 0; c.envVol = 0
            // _081DCF6A: currentPointer = data + count; count = size - count (count = track->unk_3C, de ply_note); SF_SPECIAL (0x20) a zero
            c.cur = c.count0; c.cnt = (c.wavO?.mida ?: 0) - c.count0; c.fw = 0
            r5 += c.attack
            if (r5 >= 255) { r5 = 255; c.status = c.status - 1 }
        } else {
            r5 = c.envVol
            if (st and 0x04 != 0) {
                val o = c.echoLen; c.echoLen = (o - 1) and 255
                if (o <= 1) { c.status = 0; return false }
            } else if (st and 0x40 != 0) {
                r5 = (r5 * c.release) ushr 8
                if (r5 <= c.echoVol) { r5 = c.echoVol; if (r5 == 0) { c.status = 0; return false }; c.status = st or 0x04 }
            } else {
                val e = st and 3
                if (e == 2) {
                    r5 = (r5 * c.decay) ushr 8
                    if (r5 <= c.sustain) {
                        r5 = c.sustain
                        if (r5 == 0) { r5 = c.echoVol; if (r5 == 0) { c.status = 0; return false }; c.status = st or 0x04 }
                        else c.status = st - 1
                    }
                } else if (e == 3) {
                    r5 += c.attack
                    if (r5 >= 255) { r5 = 255; c.status = st - 1 }
                }
            }
        }
        c.envVol = r5
        val r5s = ((masterVol + 1) * r5) shr 4
        // El mesclador del Golden Sun (Func_dc8) multiplica el volum del canal per l'envolupant amb >> 9 en lloc de >> 8
        val sh = if (rom.sintGs) 9 else 8
        val gv = volPistes[c.trackIdx.coerceIn(0, 15)]
        c.envR = (((c.rVol * r5s) shr sh) * gv).toInt()
        c.envL = (((c.lVol * r5s) shr sh) * gv).toInt()
        return true
    }

    // ---------------- Mesclador (SoundMain / SoundMainRAM, m4a_1.s; l'autor 2026-11-23: «fes-ho fidel») ----------------
    // Com el joc: per cada fotograma es mesclen `spv` mostres de 8 bits (amb signe) a la freqüència pròpia del joc, en dos buffers de PCM_DMA_BUF_SIZE (1584)
    // bytes (A = dreta, B = esquerra; SOUNDCNT_H). Les sumes es fan byte a byte i ES DESBORDEN (no es limiten), com fa l'ARM amb `add r6, r1, r6, ror 8`.
    // La reverb del motor barreja el que hi havia al buffer (de fa pcmDmaPeriod fotogrames) abans de mesclar-hi els canals. La sortida manté cada mostra
    // el temps d'un període del temporitzador 0 (280896 / spv cicles) i es suma als canals de Game Boy al DAC de 10 bits (amb el límit del DAC).
    private val spv = rom.mostresPerFotograma
    private val pcmFreq = (597275 * spv + 5000) / 10000                       // SampleFreqSet
    private val divFreq = (16777216 / pcmFreq + 1) shr 1
    private val perDma = 1584 / spv                                         // pcmDmaPeriod
    private val cicMostra = 280896 / spv                                     // REG_TM0CNT_L = -(280896 / spv)
    private val bufA = ByteArray(1584); private val bufB = ByteArray(1584)
    private var dmaCnt = perDma; private var seg = 0
    /** Segment que sona ara: el DMA reprodueix el que es va mesclar al fotograma ANTERIOR (SoundMain escriu el segment següent al que llegeix el DMA), mentre que els canals de Game Boy sonen en el moment d'escriure els registres: un fotograma de diferència, com a la consola. */
    private var segSona = 0
    private val M32 = 0xFFFFFFFFL
    private var oscF = Array(16) { FloatArray(spv) }; private var oscFAnt = Array(16) { FloatArray(spv) }
    private var oscFot = false

    private fun frame(mescla: Boolean = true) {
        mplayMain()
        cgbSound()
        for (c in ds) {
            dsEnvelope(c)
            // Sintetitzador quadrat (mesclador de la ROM del Golden Sun, Func_dc8): a cada fotograma l'oscil·lador lent avança i dona el llindar del duty
            val p = c.wavO?.sint
            if (p != null && p[1] == 0 && c.status and 0xC7 != 0) {
                c.lfo = (c.lfo + (p[3].toLong() shl 24)) and M32
                var v = (c.lfo + (p[5].toLong() shl 24)) and M32
                if (v and 0x80000000L != 0L) v = v.inv() and M32
                c.thr = ((v ushr 8) * p[4] + (p[2].toLong() shl 24)) and M32
            }
        }
        for (i in 0 until tracks.size) {
            val obj = if (muts.getOrElse(i) { false } || (solo >= 0 && solo != i)) 0f else 1f
            tg[i] += (obj - tg[i]) * 0.25f
        }
        // m4aSoundVSync: el comptador de DMA baixa a cada fotograma i torna a pcmDmaPeriod quan arriba a 0 (es reinicia el DMA)
        if (--dmaCnt <= 0) dmaCnt = perDma
        segSona = seg
        if (mescla) soundMainRAM()
    }

    private fun soundMainRAM() {
        // SoundMain: segment del buffer d'aquest fotograma
        seg = if (dmaCnt - 1 > 0) spv * (perDma - (dmaCnt - 1)) else 0
        if (reverb != 0) {
            val r7 = if (dmaCnt == 2) 0 else seg + spv
            for (i in 0 until spv) {
                val a = bufA[seg + i] + bufB[seg + i] + bufA[r7 + i] + bufB[r7 + i]
                var v = (a * reverb) shr 9
                if (v and 0x80 != 0) v += 1
                bufA[seg + i] = v.toByte(); bufB[seg + i] = v.toByte()
            }
        } else { java.util.Arrays.fill(bufA, seg, seg + spv, 0); java.util.Arrays.fill(bufB, seg, seg + spv, 0) }
        oscFot = Oscil.visible && EnPantalla.v
        run { val x = oscFAnt; oscFAnt = oscF; oscF = x }
        if (oscFot) for (t in oscF) java.util.Arrays.fill(t, 0f)
        for (c in ds) {
            if (c.status and 0xC7 == 0) continue
            val o = c.wavO ?: continue
            val g = tg[c.trackIdx.coerceIn(0, 15)]
            val eR = (c.envR * g).toInt(); val eL = (c.envL * g).toInt()
            val ot = if (oscFot) oscF[c.trackIdx.coerceIn(0, 15)] else null
            val sy = o.sint
            if (sy != null) {
                // Golden Sun (Func_dc8 de la seva ROM): ones sintètiques
                val step = ((c.hz / pcmFreq / 64.0) * 4294967296.0).toLong()
                for (i in 0 until spv) {
                    val sv: Int
                    when (sy[1]) {
                        0 -> { sv = if (c.ph < c.thr) 64 else -64; c.ph = (c.ph + step) and M32 }
                        1 -> { c.ph = (c.ph + step) and M32; val r7 = c.ph.toInt(); val r9 = (r7 ushr 24) - 0x70 - ((r7 shl 1) ushr 27); c.flt = r9 + (c.flt shr 1); sv = c.flt }
                        else -> { c.ph = (c.ph + step) and M32; val r7 = c.ph.toInt(); sv = if (r7 >= 0) (r7 shr 23) - 0x80 else 0x180 - (r7 ushr 23) }
                    }
                    bufA[seg + i] = (bufA[seg + i] + ((eR * sv) shr 8)).toByte(); bufB[seg + i] = (bufB[seg + i] + ((eL * sv) shr 8)).toByte()
                    if (ot != null) ot[i] += sv * (eR + eL) / 65536f
                }
                continue
            }
            // Mostres comprimides o invertides (TONEDATA_TYPE_CMP/_REV): SoundMainRAM_Unk1
            if (c.type and 0x30 != 0) { unk1(c, o, eR, eL, ot); continue }
            val sm = o.sm; val size = o.mida
            val bucle = o.bucle && o.iniciBucle < size
            val loopLen = if (bucle) size - o.iniciBucle else 0
            if (c.type and 0x08 != 0) {
                // to fix: una mostra de l'ona per mostra del mesclador, sense interpolar (_081DD07C/_081DD0F4)
                var cur = c.cur; var cnt = c.cnt
                for (i in 0 until spv) {
                    val v = mostra(sm, cur, o.abans)
                    bufA[seg + i] = (bufA[seg + i] + ((eR * v) shr 8)).toByte(); bufB[seg + i] = (bufB[seg + i] + ((eL * v) shr 8)).toByte()
                    if (ot != null) ot[i] += v * (eR + eL) / 65536f
                    cur++; cnt--
                    if (cnt <= 0) { if (bucle) { cur = o.iniciBucle; cnt = loopLen } else { c.status = 0; break } }
                }
                c.cur = cur; c.cnt = cnt
                continue
            }
            // to variable: interpolació lineal amb fracció de 23 bits; pas = divFreq × frequency (MidiKeyToFreq, en enters) (_081DD19C)
            endavant(c, o, sm, o.abans, (divFreq.toLong() * c.freq).toInt(), bucle, loopLen, eR, eL, ot)
        }
    }

    /** Byte `i` de les dades de la mostra com el llegeix l'ARM: dins de `src` (que porta `data[size]`), `data[-1]` = l'últim byte de la capçalera; fora, 0. */
    private fun mostra(src: ByteArray, i: Int, abans: Int): Int = if (i >= 0 && i < src.size) src[i].toInt() else if (i == -1) abans else 0

    /** Interpolació cap endavant (_081DD19C..._081DD228; la mateixa a SoundMainRAM_Unk1 per a les comprimides, _081DD308). */
    private fun endavant(c: M4aChan, o: OnaG, src: ByteArray, abans: Int, step: Int, bucle: Boolean, loopLen: Int, eR: Int, eL: Int, ot: FloatArray?) {
        var cur = c.cur; var cnt = c.cnt; var fw = c.fw
        var r0 = mostra(src, cur, abans); var r1 = mostra(src, cur + 1, abans) - r0
        for (i in 0 until spv) {
            val v = r0 + ((fw * r1) shr 23)
            bufA[seg + i] = (bufA[seg + i] + ((eR * v) shr 8)).toByte(); bufB[seg + i] = (bufB[seg + i] + ((eL * v) shr 8)).toByte()
            if (ot != null) ot[i] += v * (eR + eL) / 65536f
            fw += step
            val adv = fw ushr 23
            if (adv != 0) {
                fw = fw and 0x3F800000.inv()                        // `bic r9, r9, 0x3F800000` (només els bits 23-29)
                cnt -= adv
                if (cnt <= 0) {
                    if (!bucle) { c.status = 0; return }
                    var lr = -cnt
                    while (true) { cnt += loopLen; if (cnt > 0) break; lr -= loopLen }
                    cur = o.iniciBucle + lr
                } else cur += adv
                r0 = mostra(src, cur, abans); r1 = mostra(src, cur + 1, abans) - r0
            }
        }
        c.cur = cur; c.cnt = cnt; c.fw = fw
    }

    /**
     * SoundMainRAM_Unk1 (m4a_1.s): canals amb TONEDATA_TYPE_CMP (0x20) o _REV (0x10). La primera vegada (SF_SPECIAL = 0x20) la invertida comença pel
     * final (`size - count`). Les dades es descodifiquen (Unk2) només si el `type` de la WaveData ≠ 0; un canal CMP amb una mostra no comprimida i
     * sense REV no mescla res (_081DD468 surt de seguida). El to fix fa servir pas 0x800000 i interpola igualment. La invertida no fa bucle (para en
     * acabar). Diferència no emulable: la invertida comprimida, a la darrera mostra, interpola cap a «D(-1)», que a la consola llegeix memòria
     * qualsevol (bloc 0x3FFFFFF); aquí és 0.
     */
    private fun unk1(c: M4aChan, o: OnaG, eR: Int, eL: Int, ot: FloatArray?) {
        val rev = c.type and 0x10 != 0
        if (c.status and 0x20 == 0) { c.status = c.status or 0x20; if (rev) c.cur = o.mida - c.cur }
        if (!o.cmp && !rev) return
        val src = if (o.cmp) (o.dec ?: return) else o.sm
        val abans = if (o.cmp) 0 else o.abans
        val step = if (c.type and 0x08 != 0) 0x800000 else (divFreq.toLong() * c.freq).toInt()
        if (!rev) {
            val bucle = o.bucle && o.iniciBucle < o.mida
            endavant(c, o, src, abans, step, bucle, if (bucle) o.mida - o.iniciBucle else 0, eR, eL, ot)
            return
        }
        // Invertida (_081DD3C0 comprimida, _081DD468 crua): r0 = X(p), r1 = X(p-1) - r0; es desa p + 1
        var p = c.cur - 1; var cnt = c.cnt; var fw = c.fw
        var r0 = mostra(src, p, abans); var r1 = mostra(src, p - 1, abans) - r0
        for (i in 0 until spv) {
            val v = r0 + ((fw * r1) shr 23)
            bufA[seg + i] = (bufA[seg + i] + ((eR * v) shr 8)).toByte(); bufB[seg + i] = (bufB[seg + i] + ((eL * v) shr 8)).toByte()
            if (ot != null) ot[i] += v * (eR + eL) / 65536f
            fw += step
            val adv = fw ushr 23
            if (adv != 0) {
                fw = fw and 0x3F800000.inv()
                cnt -= adv
                if (cnt <= 0) { c.status = 0; return }
                p -= adv
                r0 = mostra(src, p, abans); r1 = mostra(src, p - 1, abans) - r0
            }
        }
        c.cur = p + 1; c.cnt = cnt; c.fw = fw
    }

    /** SOUNDBIAS: + BIAS (0x200), límit 0..0x3FF i resolució de 9 - `resolucio` bits (GBATEK «Amplitude Resolution»); torna el valor amb signe. */
    private val quant = 1 + resolucio
    private fun dac(x: Double): Double {
        val u = (Math.floor(x) + 0x200).coerceIn(0.0, 1023.0).toInt()
        return (((u shr quant) shl quant) - 0x200).toDouble()
    }

    // ---------------- Mesclador propi (la 1.0; GbaSo.fidel desactivat): decimals a la freqüència de sortida, sense desbordaments ni DAC; sona net, però no és el de la consola ----------------
    private val dlR = FloatArray(16384); private val drR = FloatArray(16384); private var wpV = 0
    private val D7 = Math.ceil(perDma / 59.7275 * taux).toInt(); private val D6 = Math.ceil((perDma - 1) / 59.7275 * taux).toInt()
    private val hpK = Math.pow(0.9995, 32768.0 / taux).toFloat()
    private var xSL = 0f; private var xSR = 0f; private var hpSL = 0f; private var hpSR = 0f
    private var restaFotV = 0.0
    private val spfV = taux.toDouble() / 59.7275
    private val fidel = GbaSo.fidel                                  // es tria en començar la cançó

    private fun renderitzaVell(out: ShortArray, frames: Int) {
        var f = 0
        val fixStep = rom.mostresPerFotograma * 59.7275 / taux
        val rev = reverb / 128f * 0.25f
        val osc = Oscil.visible && EnPantalla.v
        if (osc) oscil.visibles = tracks.size.coerceIn(1, 16)
        while (f < frames) {
            if (restaFotV <= 0.0) {
                frame(false); restaFotV += spfV
                for (c in ds) { c.pas = if (c.type and 0x08 != 0) fixStep else c.hz / taux; c.pasSint = (c.hz / taux / 64.0 * 4294967296.0).toLong() }
            }
            val n = minOf(frames - f, Math.ceil(restaFotV).toInt().coerceAtLeast(1))
            for (i in 0 until n) {
                var l = 0f; var r = 0f; var sl = 0f; var sr = 0f
                // L'oscil·loscopi només desa una mostra de cada 4 (la que fa arribar `oscN` a 4): les altres 3 no cal calcular-les
                val oscAra = osc && oscN == 3
                if (oscAra) java.util.Arrays.fill(oscT, 0f)
                if (reverb != 0) {
                    val e = rev * (dlR[(wpV - D7) and 16383] + drR[(wpV - D7) and 16383] + dlR[(wpV - D6) and 16383] + drR[(wpV - D6) and 16383])
                    l = e; r = e
                }
                for (c in ds) {
                    if (c.status and 0xC7 == 0) continue
                    val o = c.wavO ?: continue
                    val sy = o.sint
                    if (sy != null) {
                        val step = c.pasSint
                        val sv: Float
                        when (sy[1]) {
                            0 -> { sv = if (c.ph < c.thr) 64f else -64f; c.ph = (c.ph + step) and M32 }
                            1 -> {
                                c.ph = (c.ph + step) and M32
                                val r7 = c.ph.toInt()
                                val r9 = (r7 ushr 24) - 0x70 - ((r7 shl 1) ushr 27)
                                c.flt = r9 + (c.flt shr 1)
                                sv = c.flt.toFloat()
                            }
                            else -> {
                                c.ph = (c.ph + step) and M32
                                val r7 = c.ph.toInt()
                                sv = (if (r7 >= 0) (r7 shr 23) - 0x80 else 0x180 - (r7 ushr 23)).toFloat()
                            }
                        }
                        val gs = tg[c.trackIdx]
                        sl += sv * c.envL * INV15 * gs; sr += sv * c.envR * INV15 * gs
                        if (oscAra) oscT[c.trackIdx.coerceIn(0, 15)] += sv * (c.envL + c.envR) / 65536f * gs
                        continue
                    }
                    val sm = o.sm
                    if (c.pos >= o.mida) {
                        if (o.bucle && o.mida > o.iniciBucle) c.pos = o.iniciBucle + (c.pos - o.mida) % (o.mida - o.iniciBucle).coerceAtLeast(1)
                        else { c.status = 0; continue }
                    }
                    val k = c.pos.toInt(); val fr = (c.pos - k).toFloat()
                    val a = sm[k].toInt()
                    val b = if (k + 1 < o.mida) sm[k + 1].toInt() else if (o.bucle) sm[o.iniciBucle].toInt() else a
                    val v = a + (b - a) * fr
                    c.pos += c.pas
                    val g = tg[c.trackIdx]
                    l += v * c.envL * INV15 * g; r += v * c.envR * INV15 * g
                    if (oscAra) oscT[c.trackIdx.coerceIn(0, 15)] += v * (c.envL + c.envR) / 65536f * g
                }
                if (sl != 0f || sr != 0f || hpSL != 0f || hpSR != 0f) {
                    // L'amplificador de la GBA no deixa passar el nivell continu (la quadrada amb duty diferent del 50% en té)
                    val yl = sl - xSL + hpK * hpSL; val yr = sr - xSR + hpK * hpSR
                    xSL = sl; xSR = sr; hpSL = yl; hpSR = yr; l += yl; r += yr
                }
                dlR[wpV and 16383] = l; drR[wpV and 16383] = r; wpV++
                // Game Boy (100%, volum mestre 7/7): cada canal al màxim ≈ 60/512 d'un canal DirectSound a ple volum
                var pl = 0.0; var pr = 0.0
                for (ch in 1..4) {
                    val v = gb.out(ch, taux) * (60.0 / 512.0) * tg[gbOwner[ch]]
                    if (gb.nr51 and (0x10 shl (ch - 1)) != 0) pl += v
                    if (gb.nr51 and (0x01 shl (ch - 1)) != 0) pr += v
                    if (oscAra) oscT[gbOwner[ch].coerceIn(0, 15)] += v.toFloat()
                }
                gb.tick()
                if (osc && ++oscN >= 4) { oscN = 0; for (k in 0 until 16) oscV[k] = oscT[k] * 0.5f + 0.5f; oscil.escriu(oscV) }
                l += hpL.f(pl).toFloat(); r += hpR.f(pr).toFloat()      // condensador de sortida de la part de Game Boy
                out[(f + i) * 2] = Satura.q(l * 1.4).toShort()
                out[(f + i) * 2 + 1] = Satura.q(r * 1.4).toShort()
            }
            restaFotV -= n
            f += n
        }
    }

    private var cicFot = 280896.0                                    // cicles dins el fotograma actual (un fotograma = 280896 cicles de 16,78 MHz)
    private val cicPerSortida = 16777216.0 / taux

    override fun renderitza(out: ShortArray, frames: Int) { if (fidel) renderitzaFidel(out, frames) else renderitzaVell(out, frames) }

    private fun renderitzaFidel(out: ShortArray, frames: Int) {
        val osc = Oscil.visible && EnPantalla.v
        if (osc) oscil.visibles = tracks.size.coerceIn(1, 16)
        for (i in 0 until frames) {
            if (cicFot >= 280896.0) { cicFot -= 280896.0; frame() }
            val k = minOf(spv - 1, (cicFot / cicMostra).toInt())
            // Mescla digital de 10 bits (GBATEK «Max Output Levels»; mGBA gba/audio.c GBAAudioSample): DirectSound al 100 % (SoundInit: SOUND_ALL_MIX_FULL) =
            // mostra × 4 (±0x200); cada canal de Game Boy = valor 0..15 × 8 × (NR50 + 1 = 8, MPlayExtender posa 0x77) >> (4 - 2: ràtio 100 %) = valor × 16
            // (un quart del rang, ±0x80). Se suma el BIAS (0x200), es limita a 0..0x3FF i es redueix a la resolució del SOUNDBIAS (8 bits a m4a).
            var pl = 0.0; var pr = 0.0
            val oscAra = osc && oscN == 3
            if (oscAra) { java.util.Arrays.fill(oscT, 0f); if (oscFot) for (t in 0 until 16) oscT[t] = oscFAnt[t][k] }
            for (ch in 1..4) {
                val v = gb.digital(ch, taux) * 16.0 * tg[gbOwner[ch]]
                if (gb.nr51 and (0x10 shl (ch - 1)) != 0) pl += v
                if (gb.nr51 and (0x01 shl (ch - 1)) != 0) pr += v
                if (oscAra) oscT[gbOwner[ch].coerceIn(0, 15)] += (v / 256.0).toFloat()
            }
            gb.tick()
            if (osc && ++oscN >= 4) { oscN = 0; for (t in 0 until 16) oscV[t] = oscT[t] * 0.5f + 0.5f; oscil.escriu(oscV) }
            val dl = dac(bufB[segSona + k] * 4 + pl) / 512.0
            val dr = dac(bufA[segSona + k] * 4 + pr) / 512.0
            // condensador de sortida (l'amplificador no deixa passar el nivell continu)
            out[i * 2] = (hpL.f(dl) * 32767).toInt().coerceIn(-32768, 32767).toShort()
            out[i * 2 + 1] = (hpR.f(dr) * 32767).toInt().coerceIn(-32768, 32767).toShort()
            cicFot += cicPerSortida
        }
    }
}

/** Fa `n` passos del LFSR del soroll del Game Boy (15 o 7 bits) de cop, a blocs; retorna (lfsr nou shl 8) or el nombre de passos amb el bit 0 a zero. */
fun lfsrPassos(l0: Int, n: Int, mode7: Boolean): Int {
    var x = l0; var z = 0; var q = n
    if (!mode7) while (q > 0) {
        val m = if (q < 14) q else 14; val mask = (1 shl m) - 1
        val bits = (x xor (x shr 1)) and mask
        z += m - Integer.bitCount((x shr 1) and mask)
        x = (x shr m) or (bits shl (15 - m)); q -= m
    } else while (q > 0) {
        val m = if (q < 6) q else 6; val mask = (1 shl m) - 1
        val t = x and 0x7F
        val bits = (t xor (t shr 1)) and mask
        z += m - Integer.bitCount((t shr 1) and mask)
        x = (((x shr m) or (bits shl (15 - m))) and 0x7F80) or (((t shr m) or (bits shl (7 - m))) and 0x7F); q -= m
    }
    return (x shl 8) or z
}
