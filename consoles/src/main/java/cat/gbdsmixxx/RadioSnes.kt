package cat.gbdsmixxx

/**
 * Reproductor de fitxers .spc (música de Super Nintendo): CPU SPC700 + DSP (S-DSP), una mostra cada 32 cicles (32040 Hz, `RegioVideo.TAUX_SNES`).
 *
 * Port de l'emulador **ares** (https://github.com/ares-emulator/ares: `ares/component/processor/spc700`, `ares/sfc/smp` i `ares/sfc/dsp`),
 * llicència ISC: Copyright (c) 2004-2025 ares team, Near et al. Es permet usar, copiar i modificar aquest programari amb o sense pagament,
 * sempre que aquest avís i el de la llicència apareguin a totes les còpies. El programari es ofereix «tal qual», sense cap garantia.
 *
 * Diferències amb l'original: la CPU executa instrucció a instrucció comptant un cicle per accés (idle/lectura/escriptura, estats d'espera a 0;
 * el registre TEST no canvia els estats d'espera); el DSP fa un dels seus 32 passos a cada cicle de la CPU, com a la consola; el temporitzador 2 es reinicia en la transició 0→1 com els
 * altres dos (a ares porta una negació que sembla un error). La ROM d'arrencada (IPL) són els 64 bytes estàndard de la consola.
 */
class SnesDsp(private val ram: ByteArray) {
    val regs = IntArray(128)
    @Volatile var silenci = 0                       // bit n = veu n silenciada (només per a la ràdio)

    private class Voice(val index: Int) {
        val volume = IntArray(2); var pitch = 0; var source = 0; var adsr0 = 0; var adsr1 = 0; var gain = 0; var envx = 0
        var keyon = false; var keyoff = false; var modulate = false; var noise = false; var echo = false; var end = false
        val buffer = IntArray(12); var bufferOffset = 0; var gaussianOffset = 0; var brrAddress = 0; var brrOffset = 1
        var keyonDelay = 0; var envelopeMode = 0; var envelope = 0; var envelope2 = 0
        var keylatch = false; var keyon2 = false; var keyoff2 = false; var modulate2 = false; var noise2 = false; var echo2 = false
        var end2 = false; var looped = false
    }
    private val voice = Array(8) { Voice(it shl 4) }

    private var counter = 0; private var sampleClock = true
    private var mvReset = true; private var mvMute = true; private val mvVol = IntArray(2); private val mvOut = IntArray(2)
    private var efFeedback = 0; private val efVol = IntArray(2); private val efFir = IntArray(8); private val efHist = Array(2) { IntArray(8) }
    private var efPage = 0; private var efDelay = 0; private var efReadonly = true; private val efIn = IntArray(2); private val efOut = IntArray(2)
    private var ePage = 0; private var eReadonly = false; private var eAddress = 0; private var eOffset = 0; private var eLength = 0; private var eHist = 0
    private var noiseFreq = 0; private var lfsr = 0x4000
    private var brrBank = 0; private var bBank = 0; private var bSource = 0; private var bAddress = 0; private var bNext = 0; private var bHeader = 0; private var bByte = 0
    private var lAdsr0 = 0; private var lEnvx = 0; private var lOutx = 0; private var lPitch = 0; private var lOutput = 0
    private val gauss = IntArray(512)
    companion object {
        /** Per a cada valor del comptador (0..30719), el bit `rate` diu si `counterPoll(rate)` és cert (mateixes taules d'ares; 120 KB, es calcula un cop per procés). */
        private val POLL: IntArray = run {
            val rt = intArrayOf(0, 2048, 1536, 1280, 1024, 768, 640, 512, 384, 320, 256, 192, 160, 128, 96, 80, 64, 48, 40, 32, 24, 20, 16, 12, 10, 8, 6, 5, 4, 3, 2, 1)
            val of = intArrayOf(0, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 0, 0)
            IntArray(2048 * 5 * 3) { c -> var m = 0; for (r in 1 until 32) if ((c + of[r]) % rt[r] == 0) m = m or (1 shl r); m }
        }
    }

    /** Mostra de sortida de l'última crida a `mostra()`. */
    var sortidaL = 0; var sortidaR = 0; var hiHaMostra = false

    init {
        val t = DoubleArray(512)
        for (n in 0 until 512) {
            val k = 0.5 + n
            val s = Math.sin(Math.PI * k * 1.280 / 1024)
            val tt = (Math.cos(Math.PI * k * 2.000 / 1023) - 1) * 0.50
            val u = (Math.cos(Math.PI * k * 4.000 / 1023) - 1) * 0.08
            t[511 - n] = s * (tt + u + 1.0) / k
        }
        for (phase in 0 until 128) {
            val sum = t[phase] + t[phase + 256] + t[511 - phase] + t[255 - phase]
            val scale = 2048.0 / sum
            gauss[phase] = (t[phase] * scale + 0.5).toInt().toShort().toInt()
            gauss[phase + 256] = (t[phase + 256] * scale + 0.5).toInt().toShort().toInt()
            gauss[511 - phase] = (t[511 - phase] * scale + 0.5).toInt().toShort().toInt()
            gauss[255 - phase] = (t[255 - phase] * scale + 0.5).toInt().toShort().toInt()
        }
    }

    private fun s16(x: Int) = x.toShort().toInt()
    private fun s17(x: Int) = (x shl 15) shr 15
    private fun clamp16(x: Int) = if (x > 32767) 32767 else if (x < -32768) -32768 else x
    private fun ram(a: Int) = ram[a and 0xFFFF].toInt() and 0xFF

    fun read(address: Int): Int = regs[address and 0x7F]
    /** ENDX d'un .spc (sense l'efecte d'escriure'l, que el posa a 0). */
    fun posaEndx(v: Int) { for (n in 0 until 8) voice[n].end2 = (v shr n) and 1 != 0; regs[0x7c] = v and 0xFF }

    fun write(address0: Int, data: Int) {
        val address = address0 and 0x7F
        regs[address] = data
        when (address) {
            0x0c -> mvVol[0] = data.toByte().toInt()
            0x1c -> mvVol[1] = data.toByte().toInt()
            0x2c -> efVol[0] = data.toByte().toInt()
            0x3c -> efVol[1] = data.toByte().toInt()
            0x4c -> for (n in 0 until 8) { voice[n].keyon = (data shr n) and 1 != 0; voice[n].keylatch = (data shr n) and 1 != 0 }
            0x5c -> for (n in 0 until 8) voice[n].keyoff = (data shr n) and 1 != 0
            0x6c -> { noiseFreq = data and 31; efReadonly = (data shr 5) and 1 != 0; mvMute = (data shr 6) and 1 != 0; mvReset = (data shr 7) and 1 != 0 }
            0x7c -> { for (n in 0 until 8) voice[n].end2 = false; regs[0x7c] = 0 }
            0x0d -> efFeedback = data.toByte().toInt()
            0x2d -> { for (n in 0 until 8) voice[n].modulate = (data shr n) and 1 != 0; voice[0].modulate = false }
            0x3d -> for (n in 0 until 8) voice[n].noise = (data shr n) and 1 != 0
            0x4d -> for (n in 0 until 8) voice[n].echo = (data shr n) and 1 != 0
            0x5d -> brrBank = data
            0x6d -> efPage = data
            0x7d -> efDelay = data and 15
        }
        val n = (address shr 4) and 7
        when (address and 15) {
            0x00 -> voice[n].volume[0] = data.toByte().toInt()
            0x01 -> voice[n].volume[1] = data.toByte().toInt()
            0x02 -> voice[n].pitch = (voice[n].pitch and 0x3F00) or (data and 255)
            0x03 -> voice[n].pitch = (voice[n].pitch and 0x00FF) or ((data and 0x3F) shl 8)
            0x04 -> voice[n].source = data
            0x05 -> voice[n].adsr0 = data
            0x06 -> voice[n].adsr1 = data
            0x07 -> voice[n].gain = data
            0x08 -> lEnvx = data
            0x09 -> lOutx = data
            0x0f -> efFir[n] = data.toByte().toInt()
        }
    }

    // ---- comptador d'esdeveniments (counter.cpp) ----
    private val counterRate = intArrayOf(0, 2048, 1536, 1280, 1024, 768, 640, 512, 384, 320, 256, 192, 160, 128, 96, 80, 64, 48, 40, 32, 24, 20, 16, 12, 10, 8, 6, 5, 4, 3, 2, 1)
    private val counterOffset = intArrayOf(0, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 536, 0, 1040, 0, 0)
    private fun counterTick() { if (counter == 0) counter = 2048 * 5 * 3; counter-- }
    private fun counterPoll(rate: Int): Boolean = (POLL[counter] ushr rate) and 1 != 0      // = rate != 0 && (counter + counterOffset[rate]) % counterRate[rate] == 0 (taula precalculada)

    // ---- envolupant (envelope.cpp) ----
    private fun envelopeRun(v: Voice) {
        var envelope = v.envelope
        if (v.envelopeMode == 0) {                      // Release
            envelope -= 0x8; if (envelope < 0) envelope = 0
            v.envelope = envelope; return
        }
        var rate: Int
        var envelopeData = v.adsr1
        if ((lAdsr0 shr 7) and 1 != 0) {                // ADSR
            if (v.envelopeMode >= 2) {                  // Decay, Sustain
                envelope--; envelope -= envelope shr 8
                rate = envelopeData and 0x1f
                if (v.envelopeMode == 2) rate = ((lAdsr0 shr 4) and 7) * 2 + 16
            } else {                                    // Attack
                rate = (lAdsr0 and 15) * 2 + 1
                envelope += if (rate < 31) 0x20 else 0x400
            }
        } else {                                        // GAIN
            envelopeData = v.gain
            val mode = envelopeData shr 5
            if (mode < 4) { envelope = envelopeData shl 4; rate = 31 }
            else {
                rate = envelopeData and 0x1f
                if (mode == 4) envelope -= 0x20
                else if (mode < 6) { envelope--; envelope -= envelope shr 8 }
                else {
                    envelope += 0x20
                    if (mode > 6 && (v.envelope2.toLong() and 0xFFFFFFFFL) >= 0x600) envelope += 0x8 - 0x20
                }
            }
        }
        if ((envelope shr 8) == (envelopeData shr 5) && v.envelopeMode == 2) v.envelopeMode = 3
        v.envelope2 = envelope
        if ((envelope.toLong() and 0xFFFFFFFFL) > 0x7ff) {
            envelope = if (envelope < 0) 0 else 0x7ff
            if (v.envelopeMode == 1) v.envelopeMode = 2
        }
        if (counterPoll(rate)) v.envelope = envelope
    }

    // ---- BRR (brr.cpp) ----
    private fun brrDecode(v: Voice) {
        var nybbles = (bByte shl 8) or ram(v.brrAddress + v.brrOffset + 1)
        val filter = (bHeader shr 2) and 3
        val scale = bHeader shr 4
        for (k in 0 until 4) {
            var s = s16(nybbles) shr 12
            nybbles = nybbles shl 4
            if (scale <= 12) { s = s shl scale; s = s shr 1 } else s = s and 0x7ff.inv()
            var off = v.bufferOffset
            if (--off < 0) off = 11; val p1 = v.buffer[off]
            if (--off < 0) off = 11; val p2 = v.buffer[off] shr 1
            when (filter) {
                1 -> { s += p1 shr 1; s += (-p1) shr 5 }
                2 -> { s += p1; s -= p2; s += p2 shr 4; s += (p1 * -3) shr 6 }
                3 -> { s += p1; s -= p2; s += (p1 * -13) shr 7; s += (p2 * 3) shr 4 }
            }
            s = clamp16(s)
            s = s16(s shl 1)
            v.buffer[v.bufferOffset] = s
            if (++v.bufferOffset >= 12) v.bufferOffset = 0
        }
    }

    // ---- interpolació gaussiana (gaussian.cpp) ----
    private fun gaussianInterpolate(v: Voice): Int {
        var offset = (v.gaussianOffset shr 4) and 0xFF
        val fwd = 255 - offset; val rev = offset
        offset = v.bufferOffset + (v.gaussianOffset shr 12); if (offset >= 12) offset -= 12      // bufferOffset < 12 i gaussianOffset <= 0x7fff: la suma és < 24
        var output = (gauss[fwd] * v.buffer[offset]) shr 11; if (++offset >= 12) offset = 0
        output += (gauss[fwd + 256] * v.buffer[offset]) shr 11; if (++offset >= 12) offset = 0
        output += (gauss[rev + 256] * v.buffer[offset]) shr 11; if (++offset >= 12) offset = 0
        output = s16(output)
        output += (gauss[rev] * v.buffer[offset]) shr 11
        return clamp16(output) and 1.inv()
    }

    // ---- veus (voice.cpp) ----
    private fun voiceOutput(v: Voice, ch: Int) {
        val amp = if ((silenci shr (v.index shr 4)) and 1 != 0) 0 else (lOutput * v.volume[ch]) shr 7
        mvOut[ch] = clamp16(mvOut[ch] + amp)
        if (v.echo2) efOut[ch] = clamp16(efOut[ch] + amp)
    }
    private fun voice1(v: Voice) { bAddress = ((bBank shl 8) + (bSource shl 2)) and 0xFFFF; bSource = v.source }
    private fun voice2(v: Voice) {
        var address = bAddress
        if (v.keyonDelay == 0) address += 2
        bNext = ram(address) or (ram(address + 1) shl 8)
        lAdsr0 = v.adsr0
        lPitch = v.pitch and 0xff
    }
    private fun voice3(v: Voice) { voice3a(v); voice3b(v); voice3c(v) }
    private fun voice3a(v: Voice) { lPitch = (lPitch or (v.pitch and 0xff.inv())) and 0x7FFF }
    private fun voice3b(v: Voice) { bByte = ram(v.brrAddress + v.brrOffset); bHeader = ram(v.brrAddress) }
    private fun voice3c(v: Voice) {
        if (v.modulate2) lPitch = (lPitch + (((lOutput shr 5) * lPitch) shr 10)) and 0x7FFF
        if (v.keyonDelay != 0) {
            if (v.keyonDelay == 5) { v.brrAddress = bNext; v.brrOffset = 1; v.bufferOffset = 0; bHeader = 0 }
            v.envelope = 0; v.envelope2 = 0
            v.gaussianOffset = 0
            v.keyonDelay--
            if (v.keyonDelay and 3 != 0) v.gaussianOffset = 0x4000
            lPitch = 0
        }
        var output = gaussianInterpolate(v)
        if (v.noise2) output = s16(lfsr shl 1)
        lOutput = ((output * v.envelope) shr 11) and 1.inv()
        v.envx = v.envelope shr 4
        if (mvReset || (bHeader and 3) == 1) { v.envelopeMode = 0; v.envelope = 0 }
        if (sampleClock) {
            if (v.keyoff2) v.envelopeMode = 0
            if (v.keyon2) { v.keyonDelay = 5; v.envelopeMode = 1 }
        }
        if (v.keyonDelay == 0) envelopeRun(v)
    }
    private fun voice4(v: Voice) {
        v.looped = false
        if (v.gaussianOffset >= 0x4000) {
            brrDecode(v)
            v.brrOffset += 2
            if (v.brrOffset >= 9) {
                v.brrAddress = (v.brrAddress + 9) and 0xFFFF
                if (bHeader and 1 != 0) { v.brrAddress = bNext; v.looped = true }
                v.brrOffset = 1
            }
        }
        v.gaussianOffset = (v.gaussianOffset and 0x3fff) + lPitch
        if (v.gaussianOffset > 0x7fff) v.gaussianOffset = 0x7fff
        voiceOutput(v, 0)
    }
    private fun voice5(v: Voice) { voiceOutput(v, 1); v.end2 = v.end2 or v.looped; if (v.keyonDelay == 5) v.end2 = false }
    private fun voice6(v: Voice) { lOutx = lOutput shr 8 }
    private fun voice7(v: Voice) { var r = 0; for (n in 0 until 8) if (voice[n].end2) r = r or (1 shl n); regs[0x7c] = r; lEnvx = v.envx }
    private fun voice8(v: Voice) { regs[v.index or 0x09] = lOutx and 0xFF }
    private fun voice9(v: Voice) { regs[v.index or 0x08] = lEnvx and 0xFF }

    // ---- eco (echo.cpp) ----
    private fun fir(ch: Int, index: Int): Int = (efHist[ch][(eHist + index + 1) and 7] * efFir[index]) shr 6
    private fun echoOutput(ch: Int): Int {
        val m = s16((mvOut[ch] * mvVol[ch]) shr 7)
        val e = s16((efIn[ch] * efVol[ch]) shr 7)
        return clamp16(m + e)
    }
    private fun echoRead(ch: Int) {
        val a = (eAddress + ch * 2) and 0xFFFF
        val s = s16((ram(a + 1) shl 8) + ram(a))
        efHist[ch][eHist] = s shr 1
    }
    private fun echoWrite(ch: Int) {
        if (!eReadonly) {
            val a = (eAddress + ch * 2) and 0xFFFF; val s = efOut[ch]
            ram[a] = s.toByte(); ram[(a + 1) and 0xFFFF] = (s shr 8).toByte()
        }
        efOut[ch] = 0
    }
    private fun echo22() {
        eHist = (eHist + 1) and 7
        eAddress = ((ePage shl 8) + eOffset) and 0xFFFF
        echoRead(0)
        efIn[0] = s17(fir(0, 0)); efIn[1] = s17(fir(1, 0))
    }
    private fun echo23() {
        efIn[0] = s17(efIn[0] + fir(0, 1) + fir(0, 2)); efIn[1] = s17(efIn[1] + fir(1, 1) + fir(1, 2))
        echoRead(1)
    }
    private fun echo24() {
        efIn[0] = s17(efIn[0] + fir(0, 3) + fir(0, 4) + fir(0, 5)); efIn[1] = s17(efIn[1] + fir(1, 3) + fir(1, 4) + fir(1, 5))
    }
    private fun echo25() {
        var l = efIn[0] + fir(0, 6); var r = efIn[1] + fir(1, 6)
        l = s16(l); r = s16(r)
        l += s16(fir(0, 7)); r += s16(fir(1, 7))
        efIn[0] = clamp16(l) and 1.inv(); efIn[1] = clamp16(r) and 1.inv()
    }
    private fun echo26() {
        mvOut[0] = echoOutput(0)
        val l = efOut[0] + s16((efIn[0] * efFeedback) shr 7)
        val r = efOut[1] + s16((efIn[1] * efFeedback) shr 7)
        efOut[0] = clamp16(l) and 1.inv(); efOut[1] = clamp16(r) and 1.inv()
    }
    private fun echo27() {
        var outl = mvOut[0]; var outr = echoOutput(1)
        mvOut[0] = 0; mvOut[1] = 0
        if (mvMute) { outl = 0; outr = 0 }
        sortidaL = outl; sortidaR = outr; hiHaMostra = true
    }
    private fun echo28() { eReadonly = efReadonly }
    private fun echo29() {
        ePage = efPage
        if (eOffset == 0) eLength = (efDelay shl 11) and 0xFFFF
        eOffset = (eOffset + 4) and 0xFFFF
        if (eOffset >= eLength) eOffset = 0
        echoWrite(0)
        eReadonly = efReadonly
    }
    private fun echo30() { echoWrite(1) }

    // ---- diversos (misc.cpp) ----
    private fun misc27() { for (v in voice) v.modulate2 = v.modulate }
    private fun misc28() { for (v in voice) { v.noise2 = v.noise; v.echo2 = v.echo }; bBank = brrBank }
    private fun misc29() {
        sampleClock = !sampleClock
        if (sampleClock) for (v in voice) v.keylatch = v.keylatch and !v.keyon2
    }
    private fun misc30() {
        if (sampleClock) for (v in voice) { v.keyon2 = v.keylatch; v.keyoff2 = v.keyoff }
        counterTick()
        if (counterPoll(noiseFreq)) {
            val feedback = (lfsr shl 13) xor (lfsr shl 14)
            lfsr = (feedback and 0x4000) or (lfsr shr 1)
        }
    }

    /**
     * Un pas del DSP (DSP::main d'ares, entre dos `tick()`): a la consola l'S-DSP i l'SPC700 van amb el mateix rellotge i fan un pas per cicle de la CPU
     * (fullsnes, «SNES APU Low Level Timings»: 32 cicles per mostra). `b` = 0..31; la mostra de sortida surt al pas 27 (echo27).
     */
    fun pas(b: Int) {
        when (b) {
            0 -> { voice5(voice[0]); voice2(voice[1]) }
            1 -> { voice6(voice[0]); voice3(voice[1]) }
            2 -> { voice7(voice[0]); voice4(voice[1]); voice1(voice[3]) }
            3 -> { voice8(voice[0]); voice5(voice[1]); voice2(voice[2]) }
            4 -> { voice9(voice[0]); voice6(voice[1]); voice3(voice[2]) }
            5 -> { voice7(voice[1]); voice4(voice[2]); voice1(voice[4]) }
            6 -> { voice8(voice[1]); voice5(voice[2]); voice2(voice[3]) }
            7 -> { voice9(voice[1]); voice6(voice[2]); voice3(voice[3]) }
            8 -> { voice7(voice[2]); voice4(voice[3]); voice1(voice[5]) }
            9 -> { voice8(voice[2]); voice5(voice[3]); voice2(voice[4]) }
            10 -> { voice9(voice[2]); voice6(voice[3]); voice3(voice[4]) }
            11 -> { voice7(voice[3]); voice4(voice[4]); voice1(voice[6]) }
            12 -> { voice8(voice[3]); voice5(voice[4]); voice2(voice[5]) }
            13 -> { voice9(voice[3]); voice6(voice[4]); voice3(voice[5]) }
            14 -> { voice7(voice[4]); voice4(voice[5]); voice1(voice[7]) }
            15 -> { voice8(voice[4]); voice5(voice[5]); voice2(voice[6]) }
            16 -> { voice9(voice[4]); voice6(voice[5]); voice3(voice[6]) }
            17 -> { voice1(voice[0]); voice7(voice[5]); voice4(voice[6]) }
            18 -> { voice8(voice[5]); voice5(voice[6]); voice2(voice[7]) }
            19 -> { voice9(voice[5]); voice6(voice[6]); voice3(voice[7]) }
            20 -> { voice1(voice[1]); voice7(voice[6]); voice4(voice[7]) }
            21 -> { voice8(voice[6]); voice5(voice[7]); voice2(voice[0]) }
            22 -> { voice3a(voice[0]); voice9(voice[6]); voice6(voice[7]); echo22() }
            23 -> { voice7(voice[7]); echo23() }
            24 -> { voice8(voice[7]); echo24() }
            25 -> { voice3b(voice[0]); voice9(voice[7]); echo25() }
            26 -> { echo26() }
            27 -> { misc27(); echo27(); val o = out; if (o != null && outPos < outMax) { o[2 * outPos] = sortidaL.toShort(); o[2 * outPos + 1] = sortidaR.toShort(); outPos++ } }
            28 -> { misc28(); echo28() }
            29 -> { misc29(); echo29() }
            30 -> { misc30(); voice3c(voice[0]); echo30() }
            else -> { voice4(voice[0]); voice1(voice[2]); if (Oscil.visible && EnPantalla.v) osc() }
        }
    }
    /** Sortida: `pas(27)` hi escriu la mostra (estèreo) a `out[outPos]` mentre `outPos < outMax`. */
    @JvmField var out: ShortArray? = null; @JvmField var outPos = 0; @JvmField var outMax = 0
    // oscil·loscopi: la sortida (OUTX, 8 bits amb signe) de cada veu, a ~8 kHz
    private fun osc() {
        if (++oscN < 4) return
        oscN = 0
        for (k in 0 until 8) oscV[k] = if (silenci and (1 shl k) != 0) 0.5f else (regs[k * 16 + 9].toByte().toInt() / 127f) * 0.5f + 0.5f
        oscil.escriu(oscV)
    }
    /** Oscil·loscopi per veu (1 a 8). */
    val oscil = OscDades(listOf("1", "2", "3", "4", "5", "6", "7", "8"))
    private val oscV = FloatArray(8); private var oscN = 0
}

private const val OP_OR = 0; private const val OP_AND = 1; private const val OP_EOR = 2; private const val OP_CMP = 3; private const val OP_ADC = 4
private const val OP_SBC = 5; private const val OP_LD = 6; private const val OP_ASL = 7; private const val OP_LSR = 8; private const val OP_ROL = 9
private const val OP_ROR = 10; private const val OP_INC = 11; private const val OP_DEC = 12; private const val OP_ADW = 13; private const val OP_CPW = 14
private const val OP_LDW = 15; private const val OP_SBW = 16

/** SMP (CXP1100Q-1: SPC700 + ports + temporitzadors) amb el DSP; la memòria de 64 KiB és compartida. */
class SnesSpc {
    val ram = ByteArray(65536)
    val dsp = SnesDsp(ram)
    // ROM d'arrencada (IPL) de la consola, 64 bytes (de $FFC0)
    private val ipl = intArrayOf(0xCD, 0xEF, 0xBD, 0xE8, 0x00, 0xC6, 0x1D, 0xD0, 0xFC, 0x8F, 0xAA, 0xF4, 0x8F, 0xBB, 0xF5, 0x78, 0xCC, 0xF4, 0xD0, 0xFB, 0x2F, 0x19,
        0xEB, 0xF4, 0xD0, 0xFC, 0x7E, 0xF4, 0xD0, 0x0B, 0xE4, 0xF5, 0xCB, 0xF4, 0xD7, 0x00, 0xFC, 0xD0, 0xF3, 0xAB, 0x01, 0x10, 0xEF, 0x7E, 0xF4, 0x10, 0xEB, 0xBA,
        0xF6, 0xDA, 0x00, 0xBA, 0xF4, 0xC4, 0xF4, 0xDD, 0x5D, 0xD0, 0xDB, 0x1F, 0x00, 0x00, 0xC0, 0xFF)

    private var pc = 0; private var a = 0; private var x = 0; private var y = 0; private var sp = 0
    private var c = false; private var z = false; private var i = false; private var h = false; private var b = false; private var p = false; private var v = false; private var n = false
    private var halted = false

    private var timersDisable = false; private var ramWritable = true; private var ramDisable = false; private var timersEnable = true
    private var iplEnable = true; private var dspAddress = 0
    private val portIn = IntArray(4); private val portOut = IntArray(4); private var aux4 = 0; private var aux5 = 0

    private inner class Timer(val freq: Int) {
        var stage1 = 1; var stage2 = 0; var stage3 = 0; var line = false; var enable = false; var target = 0
        // Abans `step(2)` a cada cicle amb un `stage0` propi; com que `stage0` començava a 0 i només avançava de 2 en 2, el pas `stage0 >= freq` cau exactament
        // cada freq/2 cicles: ara `cycle()` compta els cicles un cop per als tres temporitzadors i el DSP i crida `toggle()` en aquests cicles (mateix resultat).
        // `stage1` comença a 1 perquè el flanc 1→0 (el que fa avançar el comptador) caigui als passos 0 i 16 del DSP, com diu fullsnes (T1 i T17).
        fun toggle() { stage1 = stage1 xor 1; sync() }
        fun sync() {
            var level = stage1 != 0
            if (!timersEnable) level = false
            if (timersDisable) level = false
            val fell = line && !level; line = level
            if (!fell) return
            if (!enable) return
            stage2 = (stage2 + 1) and 255
            if (stage2 != target) return
            stage2 = 0; stage3 = (stage3 + 1) and 15
        }
    }
    private val t0 = Timer(128); private val t1 = Timer(128); private val t2 = Timer(16)

    private var cicles = 0                                  // cicles de la CPU (mòdul 2^32; els múltiples de 8, 32 i 64 es conserven en donar la volta)

    /**
     * Un cicle de la CPU: el DSP fa un dels seus 32 passos (la mostra surt al pas 27; 32 cicles = una mostra a 32 kHz) i després avancen els temporitzadors,
     * com a ares (`SMP::wait`: `step` sincronitza el DSP i després `stepTimers`, abans de l'accés a la memòria). Abans el DSP feia la mostra sencera de cop cada 32 cicles:
     * el que l'SPC700 escrivia als registres del DSP (KON, KOFF, to, volums...) es veia fins a una mostra abans que a la consola, i el que llegia (ENVX, OUTX, ENDX) era d'una altra fase.
     * Fase dels temporitzadors (fullsnes, «SNES APU Low Level Timings»): el temporitzador 2 avança als passos T1 i T17 (= passos 0 i 16 d'ares) i el 0 i l'1, al T1.
     */
    private fun cycle() {
        val k = cicles++
        dsp.pas(k and 31)
        if (k and 7 == 0) { if (k and 63 == 0) { t0.toggle(); t1.toggle() }; t2.toggle() }      // t2 (freq 16) canvia de nivell cada 8 cicles; t0 i t1 (freq 128) cada 64
    }

    private fun readRam(ad: Int): Int {
        if (ad >= 0xffc0 && iplEnable) return ipl[ad and 0x3f]
        if (ramDisable) return 0x5a
        return ram[ad].toInt() and 0xFF
    }
    private fun writeRam(ad: Int, d: Int) { if (ramWritable && !ramDisable) ram[ad] = d.toByte() }

    // Mode de prova (només per als tests): memòria plana i registre dels accessos (tipus 0 = idle, 1 = lectura, 2 = escriptura)
    var plain = false; var traza: IntArray? = null; var trazaN = 0
    private fun log(t: Int, ad: Int, d: Int) { val z = traza ?: return; if (trazaN + 3 <= z.size) { z[trazaN++] = t; z[trazaN++] = ad; z[trazaN++] = d } }
    fun provaEstat(pc0: Int, a0: Int, x0: Int, y0: Int, sp0: Int, psw: Int) { pc = pc0; a = a0; x = x0; y = y0; sp = sp0; setFlags(psw); plain = true }
    fun provaPas() = instruction()
    fun provaRegs() = intArrayOf(pc, a, x, y, sp, flags())

    private fun idle() { if (plain) log(0, 0, 0) else cycle() }
    private fun read(ad0: Int): Int {
        val ad = ad0 and 0xFFFF
        if (plain) { val d = ram[ad].toInt() and 0xFF; log(1, ad, d); return d }
        cycle()
        var d = readRam(ad)
        if ((ad and 0xfff0) == 0x00f0) d = readIO(ad)
        return d
    }
    private fun write(ad0: Int, d0: Int) {
        val ad = ad0 and 0xFFFF; val d = d0 and 0xFF
        if (plain) { ram[ad] = d.toByte(); log(2, ad, d); return }
        cycle()
        writeRam(ad, d)
        if ((ad and 0xfff0) == 0x00f0) writeIO(ad, d)
    }

    private fun readIO(ad: Int): Int = when (ad) {
        0xf2 -> dspAddress
        0xf3 -> dsp.read(dspAddress)
        0xf4, 0xf5, 0xf6, 0xf7 -> portIn[ad - 0xf4]
        0xf8 -> aux4
        0xf9 -> aux5
        0xfd -> { val d = t0.stage3; t0.stage3 = 0; d }
        0xfe -> { val d = t1.stage3; t1.stage3 = 0; d }
        0xff -> { val d = t2.stage3; t2.stage3 = 0; d }
        else -> 0
    }
    private fun writeIO(ad: Int, d: Int) {
        when (ad) {
            0xf0 -> if (!p) {
                timersDisable = d and 1 != 0; ramWritable = d and 2 != 0; ramDisable = d and 4 != 0; timersEnable = d and 8 != 0
                t0.sync(); t1.sync(); t2.sync()
            }
            0xf1 -> {
                for ((k, t) in arrayOf(t0, t1, t2).withIndex()) {        // la transició 0→1 reinicia el temporitzador
                    val nou = (d shr k) and 1 != 0
                    if (nou && !t.enable) { t.stage2 = 0; t.stage3 = 0 }
                    t.enable = nou
                }
                if (d and 0x10 != 0) { portIn[0] = 0; portIn[1] = 0 }
                if (d and 0x20 != 0) { portIn[2] = 0; portIn[3] = 0 }
                iplEnable = d and 0x80 != 0
                alliberaPendents()
            }
            0xf2 -> dspAddress = d
            0xf3 -> if (dspAddress and 0x80 == 0) dsp.write(dspAddress, d)
            0xf4, 0xf5, 0xf6, 0xf7 -> portOut[ad - 0xf4] = d
            0xf8 -> aux4 = d
            0xf9 -> aux5 = d
            0xfa -> t0.target = d
            0xfb -> t1.target = d
            0xfc -> t2.target = d
        }
    }

    // ---- accés a la memòria de la CPU (memory.cpp) ----
    private fun fetch(): Int { val d = read(pc); pc = (pc + 1) and 0xFFFF; return d }
    private fun load(ad: Int): Int = read((if (p) 0x100 else 0) or (ad and 255))
    private fun store(ad: Int, d: Int) = write((if (p) 0x100 else 0) or (ad and 255), d)
    private fun pull(): Int { sp = (sp + 1) and 255; return read(0x100 or sp) }
    private fun push(d: Int) { write(0x100 or sp, d); sp = (sp - 1) and 255 }
    private fun flags() = (if (c) 1 else 0) or (if (z) 2 else 0) or (if (i) 4 else 0) or (if (h) 8 else 0) or (if (b) 16 else 0) or (if (p) 32 else 0) or (if (v) 64 else 0) or (if (n) 128 else 0)
    private fun setFlags(d: Int) { c = d and 1 != 0; z = d and 2 != 0; i = d and 4 != 0; h = d and 8 != 0; b = d and 16 != 0; p = d and 32 != 0; v = d and 64 != 0; n = d and 128 != 0 }
    private fun getR(id: Int) = when (id) { 0 -> a; 1 -> x; 2 -> y; else -> sp }
    private fun setR(id: Int, d: Int) { when (id) { 0 -> a = d and 255; 1 -> x = d and 255; 2 -> y = d and 255; else -> sp = d and 255 } }
    private fun rel(d: Int) { pc = (pc + d.toByte().toInt()) and 0xFFFF }

    // ---- algorismes (algorithms.cpp) ----
    private fun adc(x0: Int, y0: Int): Int {
        val zz = x0 + y0 + (if (c) 1 else 0)
        c = zz > 0xff; z = (zz and 255) == 0; h = ((x0 xor y0 xor zz) and 0x10) != 0
        v = (((x0 xor y0).inv()) and (x0 xor zz) and 0x80) != 0; n = (zz and 0x80) != 0
        return zz and 255
    }
    private fun alu(op: Int, x0: Int, y0: Int): Int {
        when (op) {
            OP_OR -> { val r = (x0 or y0) and 255; z = r == 0; n = r and 0x80 != 0; return r }
            OP_AND -> { val r = x0 and y0; z = r == 0; n = r and 0x80 != 0; return r }
            OP_EOR -> { val r = x0 xor y0; z = r == 0; n = r and 0x80 != 0; return r }
            OP_CMP -> { val zz = x0 - y0; c = zz >= 0; z = (zz and 255) == 0; n = zz and 0x80 != 0; return x0 }
            OP_ADC -> return adc(x0, y0)
            OP_SBC -> return adc(x0, y0.inv() and 255)
            OP_LD -> { z = y0 == 0; n = y0 and 0x80 != 0; return y0 }
            OP_ASL -> { c = x0 and 0x80 != 0; val r = (x0 shl 1) and 255; z = r == 0; n = r and 0x80 != 0; return r }
            OP_LSR -> { c = x0 and 1 != 0; val r = x0 shr 1; z = r == 0; n = r and 0x80 != 0; return r }
            OP_ROL -> { val cc = if (c) 1 else 0; c = x0 and 0x80 != 0; val r = ((x0 shl 1) or cc) and 255; z = r == 0; n = r and 0x80 != 0; return r }
            OP_ROR -> { val cc = if (c) 0x80 else 0; c = x0 and 1 != 0; val r = cc or (x0 shr 1); z = r == 0; n = r and 0x80 != 0; return r }
            OP_INC -> { val r = (x0 + 1) and 255; z = r == 0; n = r and 0x80 != 0; return r }
            OP_DEC -> { val r = (x0 - 1) and 255; z = r == 0; n = r and 0x80 != 0; return r }
            OP_ADW -> { c = false; var zz = adc(x0 and 255, y0 and 255); zz = zz or (adc(x0 shr 8, y0 shr 8) shl 8); z = zz == 0; return zz }
            OP_CPW -> { val zz = x0 - y0; c = zz >= 0; z = (zz and 0xFFFF) == 0; n = zz and 0x8000 != 0; return x0 }
            OP_LDW -> { z = y0 == 0; n = y0 and 0x8000 != 0; return y0 }
            else -> { c = true; var zz = alu(OP_SBC, x0 and 255, y0 and 255); zz = zz or (alu(OP_SBC, x0 shr 8, y0 shr 8) shl 8); z = zz == 0; return zz }   // OP_SBW
        }
    }

    // ---- instruccions (instructions.cpp) ----
    private fun absoluteBitModify(mode: Int) {
        var address = fetch(); address = address or (fetch() shl 8)
        val bit = address shr 13; address = address and 0x1fff
        var data = read(address)
        val bv = (data shr bit) and 1 != 0
        when (mode) {
            0 -> { idle(); c = c or bv }
            1 -> { idle(); c = c or !bv }
            2 -> c = c and bv
            3 -> c = c and !bv
            4 -> { idle(); c = c xor bv }
            5 -> c = bv
            6 -> { idle(); data = if (c) data or (1 shl bit) else data and (1 shl bit).inv(); write(address, data) }
            else -> { data = data xor (1 shl bit); write(address, data) }
        }
    }
    private fun absoluteBitSet(bit: Int, value: Boolean) {
        val address = fetch(); var data = load(address)
        data = if (value) data or (1 shl bit) else data and (1 shl bit).inv()
        store(address, data)
    }
    private fun absoluteRead(op: Int, target: Int) { var ad = fetch(); ad = ad or (fetch() shl 8); val d = read(ad); setR(target, alu(op, getR(target), d)) }
    private fun absoluteModify(op: Int) { var ad = fetch(); ad = ad or (fetch() shl 8); val d = read(ad); write(ad, alu(op, d, 0)) }
    private fun absoluteWrite(src: Int) { var ad = fetch(); ad = ad or (fetch() shl 8); read(ad); write(ad, getR(src)) }
    private fun absoluteIndexedRead(op: Int, index: Int) { var ad = fetch(); ad = ad or (fetch() shl 8); idle(); val d = read(ad + getR(index)); a = alu(op, a, d) }
    private fun absoluteIndexedWrite(index: Int) { var ad = fetch(); ad = ad or (fetch() shl 8); idle(); read(ad + getR(index)); write(ad + getR(index), a) }
    private fun branch(take: Boolean) { val d = fetch(); if (!take) return; idle(); idle(); rel(d) }
    private fun branchBit(bit: Int, match: Boolean) {
        val address = fetch(); val data = load(address); idle(); val disp = fetch()
        if (((data shr bit) and 1 != 0) != match) return
        idle(); idle(); rel(disp)
    }
    private fun branchNotDirect() { val address = fetch(); val data = load(address); idle(); val disp = fetch(); if (a == data) return; idle(); idle(); rel(disp) }
    private fun branchNotDirectDecrement() {
        val address = fetch(); var data = load(address); data = (data - 1) and 255; store(address, data)
        val disp = fetch(); if (data == 0) return; idle(); idle(); rel(disp)
    }
    private fun branchNotDirectIndexed(index: Int) {
        val address = fetch(); idle(); val data = load(address + getR(index)); idle(); val disp = fetch()
        if (a == data) return; idle(); idle(); rel(disp)
    }
    private fun branchNotYDecrement() { read(pc); idle(); val disp = fetch(); y = (y - 1) and 255; if (y == 0) return; idle(); idle(); rel(disp) }
    private fun iBreak() {
        read(pc); push(pc shr 8); push(pc and 255); push(flags()); idle()
        var ad = read(0xffde); ad = ad or (read(0xffdf) shl 8)
        pc = ad; i = false; b = true
    }
    private fun callAbsolute() {
        var ad = fetch(); ad = ad or (fetch() shl 8); idle()
        push(pc shr 8); push(pc and 255); idle(); idle(); pc = ad
    }
    private fun callPage() { val ad = fetch(); idle(); push(pc shr 8); push(pc and 255); idle(); pc = 0xff00 or ad }
    private fun callTable(vector: Int) {
        read(pc); idle(); push(pc shr 8); push(pc and 255); idle()
        val ad = (0xffde - (vector shl 1)) and 0xFFFF
        var np = read(ad); np = np or (read(ad + 1) shl 8); pc = np
    }
    private fun complementCarry() { read(pc); idle(); c = !c }
    private fun decimalAdjustAdd() {
        read(pc); idle()
        if (c || a > 0x99) { a = (a + 0x60) and 255; c = true }
        if (h || (a and 15) > 0x09) a = (a + 0x06) and 255
        z = a == 0; n = a and 0x80 != 0
    }
    private fun decimalAdjustSub() {
        read(pc); idle()
        if (!c || a > 0x99) { a = (a - 0x60) and 255; c = false }
        if (!h || (a and 15) > 0x09) a = (a - 0x06) and 255
        z = a == 0; n = a and 0x80 != 0
    }
    private fun directRead(op: Int, target: Int) { val address = fetch(); val d = load(address); setR(target, alu(op, getR(target), d)) }
    private fun directModify(op: Int) { val address = fetch(); val d = load(address); store(address, alu(op, d, 0)) }
    private fun directWrite(src: Int) { val address = fetch(); load(address); store(address, getR(src)) }
    private fun directDirectCompare(op: Int) { val s = fetch(); val rhs = load(s); val t = fetch(); val lhs = load(t); alu(op, lhs, rhs); idle() }
    private fun directDirectModify(op: Int) { val s = fetch(); val rhs = load(s); val t = fetch(); val lhs = load(t); store(t, alu(op, lhs, rhs)) }
    private fun directDirectWrite() { val s = fetch(); val d = load(s); val t = fetch(); store(t, d) }
    private fun directImmediateCompare(op: Int) { val imm = fetch(); val address = fetch(); val d = load(address); alu(op, d, imm); idle() }
    private fun directImmediateModify(op: Int) { val imm = fetch(); val address = fetch(); val d = load(address); store(address, alu(op, d, imm)) }
    private fun directImmediateWrite() { val imm = fetch(); val address = fetch(); load(address); store(address, imm) }
    private fun ya() = (y shl 8) or a
    private fun setYa(w: Int) { a = w and 255; y = (w shr 8) and 255 }
    private fun directCompareWord(op: Int) { val address = fetch(); var d = load(address); d = d or (load(address + 1) shl 8); setYa(alu(op, ya(), d)) }
    private fun directReadWord(op: Int) { val address = fetch(); var d = load(address); idle(); d = d or (load(address + 1) shl 8); setYa(alu(op, ya(), d)) }
    private fun directModifyWord(adjust: Int) {
        val address = fetch()
        var d = (load(address) + adjust) and 0xFFFF
        store(address, d and 255)
        d = (d + (load(address + 1) shl 8)) and 0xFFFF
        store(address + 1, d shr 8)
        z = d == 0; n = d and 0x8000 != 0
    }
    private fun directWriteWord() { val address = fetch(); load(address); store(address, a); store(address + 1, y) }
    private fun directIndexedRead(op: Int, target: Int, index: Int) { val address = fetch(); idle(); val d = load(address + getR(index)); setR(target, alu(op, getR(target), d)) }
    private fun directIndexedModify(op: Int, index: Int) { val address = fetch(); idle(); val d = load(address + getR(index)); store(address + getR(index), alu(op, d, 0)) }
    private fun directIndexedWrite(src: Int, index: Int) { val address = fetch(); idle(); load(address + getR(index)); store(address + getR(index), getR(src)) }
    private fun divide() {
        read(pc); for (k in 0 until 10) idle()
        val yy = ya()
        h = (y and 15) >= (x and 15); v = y >= x
        if (y < (x shl 1)) { a = (yy / x) and 255; y = (yy % x) and 255 }
        else { a = (255 - (yy - (x shl 9)) / (256 - x)) and 255; y = (x + (yy - (x shl 9)) % (256 - x)) and 255 }
        z = a == 0; n = a and 0x80 != 0
    }
    private fun exchangeNibble() { read(pc); idle(); idle(); idle(); a = ((a shr 4) or (a shl 4)) and 255; z = a == 0; n = a and 0x80 != 0 }
    private fun flagSet(flag: Int, value: Boolean) { read(pc); if (flag == 2) idle(); when (flag) { 0 -> c = value; 1 -> p = value; else -> i = value } }
    private fun immediateRead(op: Int, target: Int) { val d = fetch(); setR(target, alu(op, getR(target), d)) }
    private fun impliedModify(op: Int, target: Int) { read(pc); setR(target, alu(op, getR(target), 0)) }
    private fun indexedIndirectRead(op: Int, index: Int) {
        val ind = fetch(); idle()
        var ad = load(ind + getR(index)); ad = ad or (load(ind + getR(index) + 1) shl 8)
        val d = read(ad); a = alu(op, a, d)
    }
    private fun indexedIndirectWrite(src: Int, index: Int) {
        val ind = fetch(); idle()
        var ad = load(ind + getR(index)); ad = ad or (load(ind + getR(index) + 1) shl 8)
        read(ad); write(ad, getR(src))
    }
    private fun indirectIndexedRead(op: Int, index: Int) {
        val ind = fetch(); idle()
        var ad = load(ind); ad = ad or (load(ind + 1) shl 8)
        val d = read(ad + getR(index)); a = alu(op, a, d)
    }
    private fun indirectIndexedWrite(src: Int, index: Int) {
        val ind = fetch()
        var ad = load(ind); ad = ad or (load(ind + 1) shl 8)
        idle(); read(ad + getR(index)); write(ad + getR(index), getR(src))
    }
    private fun indirectXRead(op: Int) { read(pc); val d = load(x); a = alu(op, a, d) }
    private fun indirectXWrite(src: Int) { read(pc); load(x); store(x, getR(src)) }
    private fun indirectXIncrementRead(target: Int) {
        read(pc); val d = load(x); x = (x + 1) and 255; idle()
        setR(target, d); z = d == 0; n = d and 0x80 != 0
    }
    private fun indirectXIncrementWrite(src: Int) { read(pc); idle(); store(x, getR(src)); x = (x + 1) and 255 }
    private fun indirectXCompareIndirectY(op: Int) { read(pc); val rhs = load(y); val lhs = load(x); alu(op, lhs, rhs); idle() }
    private fun indirectXWriteIndirectY(op: Int) { read(pc); val rhs = load(y); val lhs = load(x); store(x, alu(op, lhs, rhs)) }
    private fun jumpAbsolute() { var ad = fetch(); ad = ad or (fetch() shl 8); pc = ad }
    private fun jumpIndirectX() {
        var ad = fetch(); ad = ad or (fetch() shl 8); idle()
        var np = read(ad + x); np = np or (read(ad + x + 1) shl 8); pc = np
    }
    private fun multiply() { read(pc); for (k in 0 until 7) idle(); val r = y * a; a = r and 255; y = (r shr 8) and 255; z = y == 0; n = y and 0x80 != 0 }
    private fun noOperation() { read(pc) }
    private fun overflowClear() { read(pc); h = false; v = false }
    private fun iPull(target: Int) { read(pc); idle(); setR(target, pull()) }
    private fun pullP() { read(pc); idle(); setFlags(pull()) }
    private fun iPush(d: Int) { read(pc); push(d); idle() }
    private fun returnInterrupt() { read(pc); idle(); setFlags(pull()); var ad = pull(); ad = ad or (pull() shl 8); pc = ad }
    private fun returnSubroutine() { read(pc); idle(); var ad = pull(); ad = ad or (pull() shl 8); pc = ad }
    private fun iStop() { halted = true }
    private fun iSleep() { halted = true }
    private fun testSetBitsAbsolute(set: Boolean) {
        var ad = fetch(); ad = ad or (fetch() shl 8)
        val d = read(ad)
        z = ((a - d) and 255) == 0; n = (a - d) and 0x80 != 0
        read(ad); write(ad, if (set) d or a else d and a.inv())
    }
    private fun transfer(from: Int, to: Int) { read(pc); val d = getR(from); setR(to, d); if (to == 3) return; z = d == 0; n = d and 0x80 != 0 }

    private fun instruction() {
        when (fetch()) {
            0x00 -> noOperation()
            0x01 -> callTable(0)
            0x02 -> absoluteBitSet(0, true)
            0x03 -> branchBit(0, true)
            0x04 -> directRead(OP_OR, 0)
            0x05 -> absoluteRead(OP_OR, 0)
            0x06 -> indirectXRead(OP_OR)
            0x07 -> indexedIndirectRead(OP_OR, 1)
            0x08 -> immediateRead(OP_OR, 0)
            0x09 -> directDirectModify(OP_OR)
            0x0a -> absoluteBitModify(0)
            0x0b -> directModify(OP_ASL)
            0x0c -> absoluteModify(OP_ASL)
            0x0d -> iPush(flags())
            0x0e -> testSetBitsAbsolute(true)
            0x0f -> iBreak()
            0x10 -> branch(!n)
            0x11 -> callTable(1)
            0x12 -> absoluteBitSet(0, false)
            0x13 -> branchBit(0, false)
            0x14 -> directIndexedRead(OP_OR, 0, 1)
            0x15 -> absoluteIndexedRead(OP_OR, 1)
            0x16 -> absoluteIndexedRead(OP_OR, 2)
            0x17 -> indirectIndexedRead(OP_OR, 2)
            0x18 -> directImmediateModify(OP_OR)
            0x19 -> indirectXWriteIndirectY(OP_OR)
            0x1a -> directModifyWord(-1)
            0x1b -> directIndexedModify(OP_ASL, 1)
            0x1c -> impliedModify(OP_ASL, 0)
            0x1d -> impliedModify(OP_DEC, 1)
            0x1e -> absoluteRead(OP_CMP, 1)
            0x1f -> jumpIndirectX()
            0x20 -> flagSet(1, false)
            0x21 -> callTable(2)
            0x22 -> absoluteBitSet(1, true)
            0x23 -> branchBit(1, true)
            0x24 -> directRead(OP_AND, 0)
            0x25 -> absoluteRead(OP_AND, 0)
            0x26 -> indirectXRead(OP_AND)
            0x27 -> indexedIndirectRead(OP_AND, 1)
            0x28 -> immediateRead(OP_AND, 0)
            0x29 -> directDirectModify(OP_AND)
            0x2a -> absoluteBitModify(1)
            0x2b -> directModify(OP_ROL)
            0x2c -> absoluteModify(OP_ROL)
            0x2d -> iPush(a)
            0x2e -> branchNotDirect()
            0x2f -> branch(true)
            0x30 -> branch(n)
            0x31 -> callTable(3)
            0x32 -> absoluteBitSet(1, false)
            0x33 -> branchBit(1, false)
            0x34 -> directIndexedRead(OP_AND, 0, 1)
            0x35 -> absoluteIndexedRead(OP_AND, 1)
            0x36 -> absoluteIndexedRead(OP_AND, 2)
            0x37 -> indirectIndexedRead(OP_AND, 2)
            0x38 -> directImmediateModify(OP_AND)
            0x39 -> indirectXWriteIndirectY(OP_AND)
            0x3a -> directModifyWord(+1)
            0x3b -> directIndexedModify(OP_ROL, 1)
            0x3c -> impliedModify(OP_ROL, 0)
            0x3d -> impliedModify(OP_INC, 1)
            0x3e -> directRead(OP_CMP, 1)
            0x3f -> callAbsolute()
            0x40 -> flagSet(1, true)
            0x41 -> callTable(4)
            0x42 -> absoluteBitSet(2, true)
            0x43 -> branchBit(2, true)
            0x44 -> directRead(OP_EOR, 0)
            0x45 -> absoluteRead(OP_EOR, 0)
            0x46 -> indirectXRead(OP_EOR)
            0x47 -> indexedIndirectRead(OP_EOR, 1)
            0x48 -> immediateRead(OP_EOR, 0)
            0x49 -> directDirectModify(OP_EOR)
            0x4a -> absoluteBitModify(2)
            0x4b -> directModify(OP_LSR)
            0x4c -> absoluteModify(OP_LSR)
            0x4d -> iPush(x)
            0x4e -> testSetBitsAbsolute(false)
            0x4f -> callPage()
            0x50 -> branch(!v)
            0x51 -> callTable(5)
            0x52 -> absoluteBitSet(2, false)
            0x53 -> branchBit(2, false)
            0x54 -> directIndexedRead(OP_EOR, 0, 1)
            0x55 -> absoluteIndexedRead(OP_EOR, 1)
            0x56 -> absoluteIndexedRead(OP_EOR, 2)
            0x57 -> indirectIndexedRead(OP_EOR, 2)
            0x58 -> directImmediateModify(OP_EOR)
            0x59 -> indirectXWriteIndirectY(OP_EOR)
            0x5a -> directCompareWord(OP_CPW)
            0x5b -> directIndexedModify(OP_LSR, 1)
            0x5c -> impliedModify(OP_LSR, 0)
            0x5d -> transfer(0, 1)
            0x5e -> absoluteRead(OP_CMP, 2)
            0x5f -> jumpAbsolute()
            0x60 -> flagSet(0, false)
            0x61 -> callTable(6)
            0x62 -> absoluteBitSet(3, true)
            0x63 -> branchBit(3, true)
            0x64 -> directRead(OP_CMP, 0)
            0x65 -> absoluteRead(OP_CMP, 0)
            0x66 -> indirectXRead(OP_CMP)
            0x67 -> indexedIndirectRead(OP_CMP, 1)
            0x68 -> immediateRead(OP_CMP, 0)
            0x69 -> directDirectCompare(OP_CMP)
            0x6a -> absoluteBitModify(3)
            0x6b -> directModify(OP_ROR)
            0x6c -> absoluteModify(OP_ROR)
            0x6d -> iPush(y)
            0x6e -> branchNotDirectDecrement()
            0x6f -> returnSubroutine()
            0x70 -> branch(v)
            0x71 -> callTable(7)
            0x72 -> absoluteBitSet(3, false)
            0x73 -> branchBit(3, false)
            0x74 -> directIndexedRead(OP_CMP, 0, 1)
            0x75 -> absoluteIndexedRead(OP_CMP, 1)
            0x76 -> absoluteIndexedRead(OP_CMP, 2)
            0x77 -> indirectIndexedRead(OP_CMP, 2)
            0x78 -> directImmediateCompare(OP_CMP)
            0x79 -> indirectXCompareIndirectY(OP_CMP)
            0x7a -> directReadWord(OP_ADW)
            0x7b -> directIndexedModify(OP_ROR, 1)
            0x7c -> impliedModify(OP_ROR, 0)
            0x7d -> transfer(1, 0)
            0x7e -> directRead(OP_CMP, 2)
            0x7f -> returnInterrupt()
            0x80 -> flagSet(0, true)
            0x81 -> callTable(8)
            0x82 -> absoluteBitSet(4, true)
            0x83 -> branchBit(4, true)
            0x84 -> directRead(OP_ADC, 0)
            0x85 -> absoluteRead(OP_ADC, 0)
            0x86 -> indirectXRead(OP_ADC)
            0x87 -> indexedIndirectRead(OP_ADC, 1)
            0x88 -> immediateRead(OP_ADC, 0)
            0x89 -> directDirectModify(OP_ADC)
            0x8a -> absoluteBitModify(4)
            0x8b -> directModify(OP_DEC)
            0x8c -> absoluteModify(OP_DEC)
            0x8d -> immediateRead(OP_LD, 2)
            0x8e -> pullP()
            0x8f -> directImmediateWrite()
            0x90 -> branch(!c)
            0x91 -> callTable(9)
            0x92 -> absoluteBitSet(4, false)
            0x93 -> branchBit(4, false)
            0x94 -> directIndexedRead(OP_ADC, 0, 1)
            0x95 -> absoluteIndexedRead(OP_ADC, 1)
            0x96 -> absoluteIndexedRead(OP_ADC, 2)
            0x97 -> indirectIndexedRead(OP_ADC, 2)
            0x98 -> directImmediateModify(OP_ADC)
            0x99 -> indirectXWriteIndirectY(OP_ADC)
            0x9a -> directReadWord(OP_SBW)
            0x9b -> directIndexedModify(OP_DEC, 1)
            0x9c -> impliedModify(OP_DEC, 0)
            0x9d -> transfer(3, 1)
            0x9e -> divide()
            0x9f -> exchangeNibble()
            0xa0 -> flagSet(2, true)
            0xa1 -> callTable(10)
            0xa2 -> absoluteBitSet(5, true)
            0xa3 -> branchBit(5, true)
            0xa4 -> directRead(OP_SBC, 0)
            0xa5 -> absoluteRead(OP_SBC, 0)
            0xa6 -> indirectXRead(OP_SBC)
            0xa7 -> indexedIndirectRead(OP_SBC, 1)
            0xa8 -> immediateRead(OP_SBC, 0)
            0xa9 -> directDirectModify(OP_SBC)
            0xaa -> absoluteBitModify(5)
            0xab -> directModify(OP_INC)
            0xac -> absoluteModify(OP_INC)
            0xad -> immediateRead(OP_CMP, 2)
            0xae -> iPull(0)
            0xaf -> indirectXIncrementWrite(0)
            0xb0 -> branch(c)
            0xb1 -> callTable(11)
            0xb2 -> absoluteBitSet(5, false)
            0xb3 -> branchBit(5, false)
            0xb4 -> directIndexedRead(OP_SBC, 0, 1)
            0xb5 -> absoluteIndexedRead(OP_SBC, 1)
            0xb6 -> absoluteIndexedRead(OP_SBC, 2)
            0xb7 -> indirectIndexedRead(OP_SBC, 2)
            0xb8 -> directImmediateModify(OP_SBC)
            0xb9 -> indirectXWriteIndirectY(OP_SBC)
            0xba -> directReadWord(OP_LDW)
            0xbb -> directIndexedModify(OP_INC, 1)
            0xbc -> impliedModify(OP_INC, 0)
            0xbd -> transfer(1, 3)
            0xbe -> decimalAdjustSub()
            0xbf -> indirectXIncrementRead(0)
            0xc0 -> flagSet(2, false)
            0xc1 -> callTable(12)
            0xc2 -> absoluteBitSet(6, true)
            0xc3 -> branchBit(6, true)
            0xc4 -> directWrite(0)
            0xc5 -> absoluteWrite(0)
            0xc6 -> indirectXWrite(0)
            0xc7 -> indexedIndirectWrite(0, 1)
            0xc8 -> immediateRead(OP_CMP, 1)
            0xc9 -> absoluteWrite(1)
            0xca -> absoluteBitModify(6)
            0xcb -> directWrite(2)
            0xcc -> absoluteWrite(2)
            0xcd -> immediateRead(OP_LD, 1)
            0xce -> iPull(1)
            0xcf -> multiply()
            0xd0 -> branch(!z)
            0xd1 -> callTable(13)
            0xd2 -> absoluteBitSet(6, false)
            0xd3 -> branchBit(6, false)
            0xd4 -> directIndexedWrite(0, 1)
            0xd5 -> absoluteIndexedWrite(1)
            0xd6 -> absoluteIndexedWrite(2)
            0xd7 -> indirectIndexedWrite(0, 2)
            0xd8 -> directWrite(1)
            0xd9 -> directIndexedWrite(1, 2)
            0xda -> directWriteWord()
            0xdb -> directIndexedWrite(2, 1)
            0xdc -> impliedModify(OP_DEC, 2)
            0xdd -> transfer(2, 0)
            0xde -> branchNotDirectIndexed(1)
            0xdf -> decimalAdjustAdd()
            0xe0 -> overflowClear()
            0xe1 -> callTable(14)
            0xe2 -> absoluteBitSet(7, true)
            0xe3 -> branchBit(7, true)
            0xe4 -> directRead(OP_LD, 0)
            0xe5 -> absoluteRead(OP_LD, 0)
            0xe6 -> indirectXRead(OP_LD)
            0xe7 -> indexedIndirectRead(OP_LD, 1)
            0xe8 -> immediateRead(OP_LD, 0)
            0xe9 -> absoluteRead(OP_LD, 1)
            0xea -> absoluteBitModify(7)
            0xeb -> directRead(OP_LD, 2)
            0xec -> absoluteRead(OP_LD, 2)
            0xed -> complementCarry()
            0xee -> iPull(2)
            0xef -> iSleep()
            0xf0 -> branch(z)
            0xf1 -> callTable(15)
            0xf2 -> absoluteBitSet(7, false)
            0xf3 -> branchBit(7, false)
            0xf4 -> directIndexedRead(OP_LD, 0, 1)
            0xf5 -> absoluteIndexedRead(OP_LD, 1)
            0xf6 -> absoluteIndexedRead(OP_LD, 2)
            0xf7 -> indirectIndexedRead(OP_LD, 2)
            0xf8 -> directRead(OP_LD, 1)
            0xf9 -> directIndexedRead(OP_LD, 1, 2)
            0xfa -> directDirectWrite()
            0xfb -> directIndexedRead(OP_LD, 2, 1)
            0xfc -> impliedModify(OP_INC, 2)
            0xfd -> transfer(0, 2)
            0xfe -> branchNotYDecrement()
            0xff -> iStop()
        }
    }

    /** Executa instruccions fins a produir `frames` mostres estèreo a `out` (una cada 32 cicles: `RegioVideo.TAUX_SNES`). Si la CPU s'ha aturat (SLEEP/STOP), només avancen els cicles. */
    fun executa(out: ShortArray, frames: Int) {
        if (arrencant) { mostresArrenca += frames; if (mostresArrenca > 20000) alliberaPendents() }
        if (frames <= 0) return
        val d = dsp
        d.out = out; d.outPos = 0; d.outMax = frames
        val objectiu = cicles + ((27 - (cicles and 31)) and 31) + 32 * (frames - 1)       // cicle en què el DSP fa la mostra número `frames` (pas 27)
        while (cicles - objectiu <= 0) {
            if (halted) { read(pc); idle() } else instruction()
        }
        d.out = null
    }

    /** Per als drivers de ROM: salta al programa de so ja posat a `ram` (sense l'IPL), amb l'estat que deixa un reinici, i buida els ports. */
    fun arrenca(adr: Int) {
        // Estat en què l'IPL salta al programa (fullsnes, «Boot ROM Disassembly»: `jmp [0000+x]` amb A=X=Y=0, SP=EFh, PSW=02h); l'IPL no toca $F1, així que la ROM
        // d'arrencada continua visible a $FFC0-$FFFF fins que el programa escriu $F1 (ares: `iplromEnable = true` en engegar)
        pc = adr; this.a = 0; x = 0; y = 0; sp = 0xEF; setFlags(0x02); halted = false
        timersDisable = false; ramWritable = true; ramDisable = false; timersEnable = true; iplEnable = true
        for (k in 0 until 4) { portIn[k] = 0; portOut[k] = 0; pendent[k] = -1 }
        arrencant = fixF1; mostresArrenca = 0
    }
    // Els motors de so posen a zero els ports d'entrada en arrencar (`$F1 = $F0`, EarthBound, Kirby DL3...): a la consola la CPU espera que l'SPC700 sigui llest abans d'escriure-hi.
    // Aquí, el que s'escriu als ports abans que l'SPC700 toqui `$F1` (o passi ~0,6 s) es guarda i s'aplica just després, perquè l'ordre no es perdi.
    private val pendent = IntArray(4) { -1 }
    companion object {
        /** Fix de `$F1` (l'autor, 2026-11-13): amb `true`, el que s'escriu als ports abans que l'SPC700 arrenqui s'aplica després; amb `false`, s'escriu a l'instant (com abans). Es tria a Ajustos. */
        @Volatile var fixF1 = true
    }
    @Volatile private var arrencant = false
    private var mostresArrenca = 0
    private fun alliberaPendents() {
        if (!arrencant) return
        arrencant = false
        for (k in 0 until 4) if (pendent[k] >= 0) { portIn[k] = pendent[k]; pendent[k] = -1 }
    }
    /** Escriu al port `i` (0-3) com ho faria la CPU principal: l'SPC700 ho llegeix a $F4+i. */
    fun escriuPort(i: Int, v: Int) { if (arrencant) pendent[i and 3] = v and 255 else portIn[i and 3] = v and 255 }
    /** El que l'SPC700 ha escrit al port `i` (la CPU principal ho llegeix a $2140+i). */
    fun llegeixPort(i: Int): Int = portOut[i and 3]

    /** Carrega l'estat d'un fitxer .spc (format «SNES-SPC700 Sound File Data»). */
    fun carrega(d: ByteArray) {
        fun u8(o: Int) = d[o].toInt() and 0xFF
        for (k in 0 until 65536) ram[k] = d[0x100 + k]
        val f1 = u8(0x100 + 0xF1)
        if (f1 and 0x80 != 0) for (k in 0 until 64) ram[0xFFC0 + k] = d[0x101C0 + k]       // la RAM que queda sota l'IPL
        pc = u8(0x25) or (u8(0x26) shl 8); a = u8(0x27); x = u8(0x28); y = u8(0x29); setFlags(u8(0x2A)); sp = u8(0x2B)
        halted = false
        timersDisable = false; ramWritable = true; ramDisable = false; timersEnable = true
        t0.enable = false; t1.enable = false; t2.enable = false
        writeIO(0xF1, f1 and 0x87)
        dspAddress = u8(0x100 + 0xF2)
        t0.target = u8(0x100 + 0xFA); t1.target = u8(0x100 + 0xFB); t2.target = u8(0x100 + 0xFC)
        t0.stage3 = u8(0x100 + 0xFD) and 15; t1.stage3 = u8(0x100 + 0xFE) and 15; t2.stage3 = u8(0x100 + 0xFF) and 15
        for (k in 0 until 4) portIn[k] = u8(0x100 + 0xF4 + k)
        aux4 = u8(0x100 + 0xF8); aux5 = u8(0x100 + 0xF9)
        for (r in 0 until 128) dsp.write(r, u8(0x10100 + r))
        dsp.posaEndx(u8(0x10100 + 0x7C))              // escriure ENDX el buida; el .spc en desa el valor (com fa Spc_Dsp::load de blargg, que copia els registres tal qual)
    }
}

/** Dades d'un .spc: validació i etiquetes ID666 (text): títol de la cançó, del joc i artista. */
class SpcArxiu(private val d: ByteArray) {
    val valid: Boolean get() = d.size >= 0x10200 && String(d, 0, 27, Charsets.US_ASCII) == "SNES-SPC700 Sound File Data"
    private fun text(o: Int, n: Int): String = String(d, o, n, Charsets.ISO_8859_1).substringBefore('\u0000').trim().filter { it.code >= 32 }
    private val etiqueta: Boolean get() = valid && (d[0x23].toInt() and 0xFF) == 0x1A
    val canco: String get() = if (etiqueta) text(0x2E, 32) else ""
    val joc: String get() = if (etiqueta) text(0x4E, 32) else ""
    val artista: String get() = if (etiqueta) text(0xB0, 32) else ""
}

/** Sintetitzador d'un .spc: 8 veus del DSP a la freqüència real de l'APU (`RegioVideo.TAUX_SNES`, 32040 Hz). */
class SinteSpc(dades: ByteArray, private val muts: BooleanArray, solo0: Int = -1) : Sintetitzador {
    override val taux = RegioVideo.TAUX_SNES
    @Volatile override var solo = solo0
    private val spc = SnesSpc().also { it.carrega(dades) }
    override fun pistaActiva(i: Int) = i < 8
    /** Nivell de cada veu del DSP (registre ENVX, l'envolupant actual: 0 a 127). */
    override val nivells: FloatArray? get() = FloatArray(8) { (spc.dsp.regs[it * 16 + 8] and 0x7F) / 127f }
    override val oscil: OscDades? get() = spc.dsp.oscil
    override fun renderitza(out: ShortArray, frames: Int) {
        var sil = 0
        for (k in 0 until 8) if (muts.getOrElse(k) { false } || (solo >= 0 && solo != k)) sil = sil or (1 shl k)
        spc.dsp.silenci = sil
        spc.executa(out, frames)
    }
}
