package cat.gbdsmixxx

/**
 * APU de la NES (2A03): 2 quadrades (amb envolupant, escombrat i comptador de durada), triangle (comptador lineal), soroll (LFSR de 15 bits) i DMC (delta), amb el
 * seqüenciador de fotogrames (4 o 5 passos) i el mesclador no lineal de la consola. Els registres s'escriuen amb `escriu` ($4000-$4017) i `mostra` dona una mostra
 * mono (aproximadament 0 a 1) a la freqüència de sortida; els temporitzadors s'avancen en cicles de CPU (1789773 Hz) per mostra, amb 4 submostres.
 * `llegeix` serveix al DMC per llegir de la memòria de la CPU.
 */
class NesApu(private val taux: Int, private val llegeix: (Int) -> Int) {
    private val LEN = intArrayOf(10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14, 12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30)
    private val DUTY = arrayOf(intArrayOf(0, 1, 0, 0, 0, 0, 0, 0), intArrayOf(0, 1, 1, 0, 0, 0, 0, 0), intArrayOf(0, 1, 1, 1, 1, 0, 0, 0), intArrayOf(1, 0, 0, 1, 1, 1, 1, 1))
    private val NOISE = intArrayOf(4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068)
    private val DMCR = intArrayOf(428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54)
    // Taules de la 2A07 (PAL): nesdev.org/wiki/APU_Noise i APU_DMC («Copy-friendly table»); mateixos valors que Mesen2 (NoiseChannel.h, DeltaModulationChannel.cpp)
    private val NOISE_PAL = intArrayOf(4, 8, 14, 30, 60, 88, 118, 148, 188, 236, 354, 472, 708, 944, 1890, 3778)
    private val DMCR_PAL = intArrayOf(398, 354, 316, 298, 276, 236, 210, 198, 176, 148, 132, 118, 98, 78, 66, 50)
    private var nIdx = 0; private var dIdx = 0
    private val CPS: Double get() = RegioVideo.cpuNes() / taux

    // quadrades (0 i 1)
    private val duty = IntArray(2); private val halt = BooleanArray(2); private val cons = BooleanArray(2); private val vol = IntArray(2)
    private val envStart = BooleanArray(2); private val envDiv = IntArray(2); private val envDec = IntArray(2)
    private val swEn = BooleanArray(2); private val swPer = IntArray(2); private val swNeg = BooleanArray(2); private val swSh = IntArray(2); private val swRel = BooleanArray(2); private val swDiv = IntArray(2)
    private val tim = IntArray(2); private val len = IntArray(2); private val fase = DoubleArray(2)
    // triangle
    private var triCtl = false; private var triRel = 0; private var triCnt = 0; private var triFlag = false; private var triT = 0; private var triLen = 0; private var triFase = 0.0
    // soroll
    private var nHalt = false; private var nCons = false; private var nVol = 0; private var nStart = false; private var nDiv = 0; private var nDec = 0
    private var nMode = false; private var nPer = 4; private var nSr = 1; private var nLen = 0; private var nAcc = 0.0
    // DMC
    private var dLoop = false; private var dRate = 428; private var dOut = 0; private var dAdr = 0xC000; private var dLen = 1
    private var dCur = 0; private var dLeft = 0; private var dShift = 0; private var dBits = 8; private var dBuf = -1; private var dSil = true; private var dAcc = 0.0
    private var enabled = 0
    // seqüenciador de fotogrames
    private var mode5 = false; private var pas = 0; private var fc = 0.0; private var palAnt = false
    // VRC6 (Konami, mappers 24 i 26): dos canals de pols de 16 passos i una dent de serra (accés a $9000-$9003, $A000-$A002 i $B000-$B002)
    private val v6Reg = IntArray(9); private var v6Halt = false; private var v6Shift = 0
    private val v6Fase = DoubleArray(3); private var v6Acc = 0
    private var v6SawPas = 0
    private val v6s = DoubleArray(3)
    /** Hi ha un xip VRC6 escrivint (s'activa amb la primera escriptura). */
    var tVrc6 = false
    /** Registres del VRC6: `a` és l'adreça ja normalitzada (sense A0/A1 intercanviats per al mapper 26). */
    fun escriuVrc6(a: Int, v: Int) {
        tVrc6 = true
        val r = a and 3
        when (a and 0xF000) {
            0x9000 -> if (r == 3) { v6Halt = v and 1 != 0; v6Shift = if (v and 4 != 0) 8 else if (v and 2 != 0) 4 else 0 } else v6Reg[r] = v
            0xA000 -> if (r < 3) v6Reg[3 + r] = v
            0xB000 -> if (r < 3) v6Reg[6 + r] = v
        }
        // E = 0: el comptador de passos es reinicia (pols: «the duty cycle is immediately reset and halted»; dent de serra: acumulador a 0). nesdev.org/wiki/VRC6_audio; Mesen2 Vrc6Pulse/Vrc6Saw `WriteReg`
        if (r == 2 && v and 0x80 == 0) when (a and 0xF000) { 0x9000 -> v6Fase[0] = 0.0; 0xA000 -> v6Fase[1] = 0.0; 0xB000 -> v6Fase[2] = 0.0 }
    }
    val actiu = BooleanArray(5)
    /** Oscil·loscopi: 8 canals (pols 1, pols 2, triangle, soroll, DMC i els 3 del VRC6), a 1/4 de la freqüència de mostreig; només es desa si `Oscil.visible` i l'app és a la pantalla. */
    val oscil = OscDades(listOf("P1", "P2", "TR", "NO", "DM", "V1", "V2", "V3"))
    private var oscN = 0; private val oscV = FloatArray(8)
    /** Nivell de cada canal (quadrada 1, quadrada 2, triangle, soroll, DMC), de 0 a 1, amb caiguda suau. */
    val nivells = FloatArray(5)
    /** Comptador de notes disparades (qualsevol canal), per saber si encara sona. */
    @Volatile var nota = 0
    /**
     * El triangle aturat manté l'últim nivell (com al xip real), sempre (l'autor, 2026-11-23: ja no és opcional).
     * `sortidaFidel` (per defecte SÍ, l'autor 2026-11-23): filtres de sortida de la NES (passa-alts de 90 Hz i passa-baix de 14 kHz, nesdev.org/wiki/APU_Mixer) i guany lineal, sense
     * el `tanh` (saturació pròpia de l'app, que no hi és a la consola) ni el bloqueig de continu de ~28 Hz.
     */
    companion object { @Volatile var sortidaFidel = true }
    private var triUlt = 0
    /** Valor de lectura de $4015: bits dels canals amb comptador de durada (o bytes de DMC) per acabar. */
    /**
     * Nota MIDI exacta de cada canal del visualitzador (pols 1, pols 2, triangle, soroll, DMC i els 3 del VRC6), llegida dels registres: -1 si no sona o no té to (soroll, DMC).
     * Les mateixes condicions que `mostra` fa servir per decidir si un canal sona; el to surt del període i del rellotge de la CPU (RegioVideo).
     */
    fun notesMidi(out: FloatArray) {
        fun midi(hz: Double) = if (hz > 0.0) (69.0 + 12.0 * Math.log(hz / 440.0) / Math.log(2.0)).toFloat() else -1f
        val cpu = RegioVideo.cpuNes()
        fun posa(i: Int, v: Float) { if (i < out.size) out[i] = v }
        for (i in 0 until 2) {
            val vol = if (cons[i]) vol[i] else envDec[i]
            val sona = len[i] > 0 && tim[i] >= 8 && (enabled shr i) and 1 != 0 && objectiu(i) <= 0x7FF && vol > 0
            posa(i, if (sona) midi(cpu / (16.0 * (tim[i] + 1))) else -1f)
        }
        posa(2, if (triLen > 0 && triCnt > 0 && triT >= 2 && enabled and 4 != 0) midi(cpu / (32.0 * (triT + 1))) else -1f)
        posa(3, -1f); posa(4, -1f)
        val v6on = tVrc6 && !v6Halt
        for (k in 0 until 2) {
            val r = 3 * k; val per = ((v6Reg[r + 1] or ((v6Reg[r + 2] and 15) shl 8)) shr v6Shift) + 1
            posa(5 + k, if (v6on && v6Reg[r + 2] and 0x80 != 0 && (v6Reg[r] and 15) > 0) midi(cpu / (16.0 * per)) else -1f)
        }
        val p2 = ((v6Reg[7] or ((v6Reg[8] and 15) shl 8)) shr v6Shift) + 1
        posa(7, if (v6on && v6Reg[8] and 0x80 != 0 && (v6Reg[6] and 63) > 0) midi(cpu / (14.0 * p2)) else -1f)
    }
    fun estat(): Int = (if (len[0] > 0) 1 else 0) or (if (len[1] > 0) 2 else 0) or (if (triLen > 0) 4 else 0) or (if (nLen > 0) 8 else 0) or (if (dLeft > 0) 16 else 0)

    fun escriu(a: Int, v: Int) {
        when (a) {
            0x4000, 0x4004 -> { val i = (a - 0x4000) shr 2; duty[i] = v shr 6; halt[i] = v and 0x20 != 0; cons[i] = v and 0x10 != 0; vol[i] = v and 15 }
            0x4001, 0x4005 -> { val i = (a - 0x4000) shr 2; swEn[i] = v and 0x80 != 0; swPer[i] = (v shr 4) and 7; swNeg[i] = v and 8 != 0; swSh[i] = v and 7; swRel[i] = true }
            0x4002, 0x4006 -> { val i = (a - 0x4000) shr 2; tim[i] = (tim[i] and 0x700) or v }
            0x4003, 0x4007 -> { val i = (a - 0x4000) shr 2; tim[i] = (tim[i] and 255) or ((v and 7) shl 8); if (enabled and (1 shl i) != 0) len[i] = LEN[v shr 3]; fase[i] = 0.0; envStart[i] = true; actiu[i] = true; nota++ }
            0x4008 -> { triCtl = v and 0x80 != 0; triRel = v and 0x7F }
            0x400A -> triT = (triT and 0x700) or v
            0x400B -> { triT = (triT and 255) or ((v and 7) shl 8); if (enabled and 4 != 0) triLen = LEN[v shr 3]; triFlag = true; actiu[2] = true; nota++ }
            0x400C -> { nHalt = v and 0x20 != 0; nCons = v and 0x10 != 0; nVol = v and 15 }
            0x400E -> { nMode = v and 0x80 != 0; nIdx = v and 15; nPer = (if (palAnt) NOISE_PAL else NOISE)[nIdx] }
            0x400F -> { if (enabled and 8 != 0) nLen = LEN[v shr 3]; nStart = true; actiu[3] = true; nota++ }
            0x4010 -> { dLoop = v and 0x40 != 0; dIdx = v and 15; dRate = (if (palAnt) DMCR_PAL else DMCR)[dIdx] }
            0x4011 -> dOut = v and 0x7F
            0x4012 -> dAdr = 0xC000 + v * 64
            0x4013 -> dLen = v * 16 + 1
            0x4015 -> {
                enabled = v and 31
                if (v and 1 == 0) len[0] = 0; if (v and 2 == 0) len[1] = 0; if (v and 4 == 0) triLen = 0; if (v and 8 == 0) nLen = 0
                if (v and 16 == 0) dLeft = 0 else if (dLeft == 0) { dCur = dAdr; dLeft = dLen; actiu[4] = true; nota++ }
                // El lector omple el buffer buit de seguida (2-3 cicles; APU_DMC: «whenever the sample buffer is empty and the bytes-remaining counter isn't 0»; Mesen2 `SetEnabled`), no al final del byte en curs
                if (dLeft > 0 && dBuf < 0) dmcLlegeix()
            }
            0x4017 -> { mode5 = v and 0x80 != 0; pas = 0; fc = 0.0; if (mode5) { quart(); mitja() } }
        }
    }

    private fun env(i: Int) {
        if (envStart[i]) { envStart[i] = false; envDec[i] = 15; envDiv[i] = vol[i] }
        else if (envDiv[i] == 0) { envDiv[i] = vol[i]; if (envDec[i] > 0) envDec[i]-- else if (halt[i]) envDec[i] = 15 }
        else envDiv[i]--
    }
    private fun quart() {
        env(0); env(1)
        if (nStart) { nStart = false; nDec = 15; nDiv = nVol } else if (nDiv == 0) { nDiv = nVol; if (nDec > 0) nDec-- else if (nHalt) nDec = 15 } else nDiv--
        if (triFlag) triCnt = triRel else if (triCnt > 0) triCnt--
        if (!triCtl) triFlag = false
    }
    private fun objectiu(i: Int): Int { val c = tim[i] shr swSh[i]; return if (swNeg[i]) tim[i] - c - (if (i == 0) 1 else 0) else tim[i] + c }
    private fun mitja() {
        for (i in 0..1) {
            if (!halt[i] && len[i] > 0) len[i]--
            val t = objectiu(i)
            if (swDiv[i] == 0 && swEn[i] && swSh[i] > 0 && tim[i] >= 8 && t <= 0x7FF) tim[i] = t
            if (swDiv[i] == 0 || swRel[i]) { swDiv[i] = swPer[i]; swRel[i] = false } else swDiv[i]--
        }
        if (!triCtl && triLen > 0) triLen--
        if (!nHalt && nLen > 0) nLen--
    }
    private fun fotograma() {
        fc += CPS
        // Cicles de CPU de cada pas: NTSC 7457/14913/22371/29829 (+37281), volta a 29830 (37282); PAL (2A07) 8313/16627/24939/33253 (+41565), volta a 33254 (41566).
        // nesdev.org/wiki/APU_Frame_Counter; Mesen2 ApuFrameCounter.h `_stepCyclesNtsc/_stepCyclesPal`. Abans el PAL feia servir els passos NTSC amb el rellotge PAL (envolupants i durades un 11,5 % massa ràpids).
        val pal = RegioVideo.pal
        if (pal != palAnt) { palAnt = pal; nPer = (if (pal) NOISE_PAL else NOISE)[nIdx]; dRate = (if (pal) DMCR_PAL else DMCR)[dIdx] }
        while (true) {
            val llindar = if (pal) { if (mode5) when (pas) { 0 -> 8313.0; 1 -> 16627.0; 2 -> 24939.0; 3 -> 33253.0; else -> 41565.0 } else when (pas) { 0 -> 8313.0; 1 -> 16627.0; 2 -> 24939.0; else -> 33253.0 } }
                else { if (mode5) when (pas) { 0 -> 7457.0; 1 -> 14913.0; 2 -> 22371.0; 3 -> 29829.0; else -> 37281.0 } else when (pas) { 0 -> 7457.0; 1 -> 14913.0; 2 -> 22371.0; else -> 29829.0 } }
            if (fc < llindar) break
            when (pas) { 0, 2 -> quart(); 1 -> { quart(); mitja() }; 3 -> if (!mode5) { quart(); mitja() }; else -> { quart(); mitja() } }
            pas++
            if (pas >= (if (mode5) 5 else 4)) { pas = 0; fc -= if (pal) (if (mode5) 41566.0 else 33254.0) else (if (mode5) 37282.0 else 29830.0) }
        }
    }

    private fun dmcBit() {
        if (dBits == 0 || dBits > 8) dBits = 8
        if (!dSil) { if (dShift and 1 != 0) { if (dOut <= 125) dOut += 2 } else { if (dOut >= 2) dOut -= 2 } }
        dShift = dShift shr 1; dBits--
        if (dBits == 0) {
            dBits = 8
            if (dBuf < 0) dSil = true else { dShift = dBuf; dBuf = -1; dSil = false }
            if (dLeft > 0) dmcLlegeix()
        }
    }
    private fun dmcLlegeix() {
        dBuf = llegeix(dCur) and 255; dCur = if (dCur >= 0xFFFF) 0x8000 else dCur + 1; dLeft--
        if (dLeft == 0 && dLoop) { dCur = dAdr; dLeft = dLen }
    }

    /** Una mostra de sortida (mono); `mut` = canals silenciats (quadrada 1, quadrada 2, triangle, soroll, DMC). */
    private fun nivell(i: Int, v: Double) { nivells[i] = maxOf(v.toFloat().coerceIn(0f, 1f), nivells[i] * 0.9993f) }
    fun mostra(mut: BooleanArray): Double {
        fotograma()
        var s0 = 0.0; var s1 = 0.0; var st = 0.0; var sn = 0.0; var sd = 0.0; var sth = 0.0
        v6s[0] = 0.0; v6s[1] = 0.0; v6s[2] = 0.0
        val cyc = CPS / 4.0
        // Optimització (2026-11-18): tot el que no canvia dins de les 4 submostres es calcula un cop (mateixes expressions, mateix resultat bit a bit):
        // els registres només canvien a `escriu` i al seqüenciador (`fotograma`, abans del bucle); dins del bucle només hi avancen les fases, l'LFSR i el DMC.
        val inc0 = cyc / (16.0 * (tim[0] + 1)); val inc1 = cyc / (16.0 * (tim[1] + 1))
        val sona0 = len[0] > 0 && tim[0] >= 8 && enabled and 1 != 0 && objectiu(0) <= 0x7FF && !mut[0]
        val sona1 = len[1] > 0 && tim[1] >= 8 && (enabled shr 1) and 1 != 0 && objectiu(1) <= 0x7FF && !mut[1]
        val dut0 = DUTY[duty[0] and 3]; val dut1 = DUTY[duty[1] and 3]
        val vol0 = if (cons[0]) vol[0] else envDec[0]; val vol1 = if (cons[1]) vol[1] else envDec[1]
        var f0 = fase[0]; var f1 = fase[1]
        val incT = cyc / (32.0 * (triT + 1))
        val triCorre = triLen > 0 && triCnt > 0
        val incN = cyc / nPer
        val sonaN = nLen > 0 && enabled and 8 != 0 && !mut[3]; val volN = if (nCons) nVol else nDec
        val tapN = if (nMode) 6 else 1
        val incD = cyc / dRate
        val mutD = mut[4]
        // H (halt) atura els oscil·ladors on són, però la sortida es manté (VRC6_audio: «Stops all oscillators in their current state»; Mesen2 Vrc6Audio `ClockAudio` suma les sortides igualment)
        val v6on = tVrc6; val v6corre = !v6Halt
        var per0 = 0; var per1 = 0; var per2 = 0; var en0 = false; var en1 = false; var en2 = false
        if (v6on) {
            per0 = ((v6Reg[1] or ((v6Reg[2] and 15) shl 8)) shr v6Shift) + 1; en0 = v6Reg[2] and 0x80 != 0
            per1 = ((v6Reg[4] or ((v6Reg[5] and 15) shl 8)) shr v6Shift) + 1; en1 = v6Reg[5] and 0x80 != 0
            per2 = ((v6Reg[7] or ((v6Reg[8] and 15) shl 8)) shr v6Shift) + 1; en2 = v6Reg[8] and 0x80 != 0
        }
        val incV0 = if (v6corre) cyc / (16.0 * per0) else 0.0; val incV1 = if (v6corre) cyc / (16.0 * per1) else 0.0; val incV2 = if (v6corre) cyc / (14.0 * per2) else 0.0
        for (k in 0 until 4) {
            f0 += inc0; if (f0 >= 1.0) f0 -= Math.floor(f0)
            if (sona0) s0 += dut0[(f0 * 8).toInt() and 7] * vol0
            f1 += inc1; if (f1 >= 1.0) f1 -= Math.floor(f1)
            if (sona1) s1 += dut1[(f1 * 8).toInt() and 7] * vol1
            // Triangle fidel, sempre (l'autor, 2026-11-23: sense opció). APU_Triangle: el seqüenciador només avança si els dos comptadors no són 0; aturat, manté l'últim valor i la
            // fase no avança (Mesen2 TriangleChannel `Run`); amb període 0-1 l'ultrasò es fa constant a mitja amplitud pel filtre extern («averaged into constant output at roughly half amplitude»)
            if (triCorre) {
                triFase += incT; if (triFase >= 1.0) triFase -= Math.floor(triFase)
                val p = (triFase * 32).toInt() and 31; val vt = if (p < 16) 15 - p else p - 16; triUlt = vt
                if (!mut[2]) st += if (triT < 2) 7.5 else vt.toDouble()
            } else if (!mut[2]) sth += triUlt                // sostingut: només a la sortida, no als nivells ni a l'oscil·loscopi
            nAcc += incN
            var suma = 0
            val nsh = minOf(nAcc.toInt(), 64); nAcc -= nAcc.toInt()
            var sr = nSr
            for (q in 0 until nsh) { val fb = (sr and 1) xor ((sr shr tapN) and 1); sr = (sr shr 1) or (fb shl 14); suma += if (sr and 1 == 0) 1 else 0 }
            nSr = sr
            if (sonaN) { val bitN = if (nsh == 0) (if (sr and 1 == 0) 1.0 else 0.0) else suma.toDouble() / nsh; sn += bitN * volN }
            dAcc += incD
            var gd = 0; while (dAcc >= 1.0 && gd < 8) { dAcc -= 1.0; dmcBit(); gd++ }
            if (dAcc > 4.0) dAcc = 0.0
            if (!mutD) sd += dOut
            if (v6on) {
                v6Fase[0] += incV0; if (v6Fase[0] >= 1.0) v6Fase[0] -= Math.floor(v6Fase[0])
                if (en0) { val pas = (v6Fase[0] * 16).toInt() and 15; val dutyV = (v6Reg[0] shr 4) and 7; if ((v6Reg[0] and 0x80 != 0) || pas <= dutyV) v6s[0] += (v6Reg[0] and 15).toDouble() }
                v6Fase[1] += incV1; if (v6Fase[1] >= 1.0) v6Fase[1] -= Math.floor(v6Fase[1])
                if (en1) { val pas = (v6Fase[1] * 16).toInt() and 15; val dutyV = (v6Reg[3] shr 4) and 7; if ((v6Reg[3] and 0x80 != 0) || pas <= dutyV) v6s[1] += (v6Reg[3] and 15).toDouble() }
                v6Fase[2] += incV2; if (v6Fase[2] >= 1.0) v6Fase[2] -= Math.floor(v6Fase[2])
                // dent de serra: passos 0-13 = 0,0,A,A,...,6A,6A (sis sumes i reinici; VRC6_audio, Mesen2 Vrc6Saw `Clock`); abans (pas+1)/2 donava 7A
                if (en2) { val pas = (v6Fase[2] * 14).toInt(); val ac = (pas / 2) * (v6Reg[6] and 63); v6s[2] += ((ac and 0xFF) shr 3).toDouble() }
            }
        }
        fase[0] = f0; fase[1] = f1
        val p = (s0 + s1) / 4.0; val t = (st + sth) / 4.0; val n = sn / 4.0; val d = sd / 4.0
        // sense cap array per mostra (abans `doubleArrayOf` a cada mostra: ~32.000 assignacions/s al fil d'àudio); mateixos valors
        nivell(0, s0 / 60.0); nivell(1, s1 / 60.0); nivell(2, st / 60.0); nivell(3, sn / 60.0); nivell(4, (sd / 4.0 - 40.0).coerceAtLeast(0.0) / 87.0)
        val pulse = if (p <= 0.0) 0.0 else 95.88 / (8128.0 / p + 100.0)
        val tnd = if (t + n + d <= 0.0) 0.0 else 159.79 / (1.0 / (t / 8227.0 + n / 12241.0 + d / 22638.0) + 100.0)
        if (Oscil.visible && EnPantalla.v && ++oscN >= 4) {
            oscN = 0
            oscV[0] = (s0 / 60.0).toFloat(); oscV[1] = (s1 / 60.0).toFloat(); oscV[2] = (st / 60.0).toFloat(); oscV[3] = (sn / 60.0).toFloat(); oscV[4] = (sd / 4.0 / 127.0).toFloat()
            oscV[5] = (v6s[0] / 60.0).toFloat(); oscV[6] = (v6s[1] / 60.0).toFloat(); oscV[7] = (v6s[2] / 124.0).toFloat()
            oscil.visibles = if (tVrc6) 8 else 5
            oscil.escriu(oscV)
        }
        // VRC6: es suma lineal als canals de l'APU (pols 0-15, dent de serra 0-31); l'escala és aproximada (no mesurada amb el xip)
        val v6 = (v6s[0] + v6s[1] + v6s[2]) / 4.0 * 0.0088
        return pulse + tnd + v6
    }
}

/**
 * Etapa de sortida de la NES (`SinteNesRom`, `SinteNsf`). Per defecte, la d'abans (bloqueig de continu de ~28 Hz, guany 1,6 i saturació `tanh`, que no són de la consola).
 * Amb `NesApu.sortidaFidel`: els filtres de la NES de nesdev.org/wiki/APU_Mixer («A first-order high-pass filter at 90 Hz», «A first-order low-pass filter at 14 kHz»; el passa-baix de 2n ordre
 * «comfortably above the audible range» no cal) i guany lineal 1,6 (el mateix d'abans abans del `tanh`), retallant només si passa de ±1 (amb Contra, Castlevania i Mega Man 2 el pic
 * filtrat és 0,6 com a màxim: no retalla). La Famicom només té un passa-alts de 37 Hz i el modulador
 * de RF (sense dades), que no s'ha modelat.
 */
class SortidaNes(taux: Int) {
    private val hp = DcBlock(taux)
    private val aHp = Math.exp(-2.0 * Math.PI * 90.0 / taux)
    private val aLp = 1.0 - Math.exp(-2.0 * Math.PI * 14000.0 / taux)
    private var xAnt = 0.0; private var yHp = 0.0; private var yLp = 0.0
    fun f(x: Double): Short {
        if (!NesApu.sortidaFidel) { val v = hp.f(x * 1.6); return (Math.tanh(v * 1.3) * 32767.0).toInt().toShort() }
        yHp = aHp * (yHp + x - xAnt); xAnt = x
        yLp += aLp * (yHp - yLp)
        return ((yLp * 1.6).coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
    }
}
