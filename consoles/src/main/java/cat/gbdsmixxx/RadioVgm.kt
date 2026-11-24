package cat.gbdsmixxx

/**
 * Fitxer .vgm (Video Game Music; els .vgz són el mateix amb gzip): una sol·licitud de registres de xips amb esperes en mostres a 44100 Hz. Es fan sonar els xips que ja
 * tenim: **SN76489** (Master System, Game Gear, Mega Drive...), **Game Boy DMG** (reutilitza `M4aGb`) i **NES APU** (`NesApu`). Els altres (YM2612, YM2151, OPL...) s'ignoren:
 * un fitxer que només en porta d'aquests surt sense so.
 */
class VgmArxiu(private val d: ByteArray) {
    private fun u8(o: Int) = if (o in d.indices) d[o].toInt() and 255 else 0
    private fun u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
    fun u32(o: Int) = u16(o) or (u16(o + 2) shl 16)
    val valid = d.size > 0x40 && d[0] == 'V'.code.toByte() && d[1] == 'g'.code.toByte() && d[2] == 'm'.code.toByte() && d[3] == ' '.code.toByte()
    val versio get() = u32(8)
    val clockSn get() = u32(0xC) and 0x3FFFFFFF
    val clockGb get() = if (versio >= 0x161) u32(0x80) and 0x3FFFFFFF else 0
    val clockNes get() = if (versio >= 0x161) u32(0x84) and 0x3FFFFFFF else 0
    val snFb get() = if (versio >= 0x110) u16(0x28) else 0
    val inici get() = if (versio >= 0x150 && u32(0x34) != 0) 0x34 + u32(0x34) else 0x40
    val buclePos get() = if (u32(0x1C) != 0) 0x1C + u32(0x1C) else 0
    val suportat get() = clockSn != 0 || clockGb != 0 || clockNes != 0
    fun b(o: Int) = u8(o)
    val mida get() = d.size
    /** Etiquetes GD3: [títol, títol (JP), joc, joc (JP), sistema, sistema (JP), autor, autor (JP), data, qui l'ha fet, notes]. */
    val gd3: List<String> by lazy {
        val r = ArrayList<String>()
        try {
            val off = u32(0x14); if (off != 0 && u8(0x14 + off) == 'G'.code) {
                var o = 0x14 + off + 12
                val sb = StringBuilder()
                while (o + 1 < d.size && r.size < 11) { val c = u16(o); o += 2; if (c == 0) { r.add(sb.toString()); sb.setLength(0) } else sb.append(c.toChar()) }
            }
        } catch (e: Exception) { }
        r
    }
}

/** SN76489: 3 quadrades i 1 soroll, amb atenuació de 2 dB per pas; registres pel port únic (bit 7 = byte de «latch»). */
class Psg(private val taux: Int, private val clock: Int, fb: Int) {
    private val mascara = if (fb != 0) fb else 0x0009
    private val freq = IntArray(3) { 1 }; private val att = IntArray(4) { 15 }
    private var latch = 0; private var nc = 0; private var lfsr = 0x8000
    private val fase = DoubleArray(3); private var nAcc = 0.0; private var nBit = 1
    val actiu = BooleanArray(4)
    fun escriu(b: Int) {
        if (b and 0x80 != 0) {
            latch = (b shr 4) and 7; val v = b and 15
            when (latch) { 0, 2, 4 -> freq[latch / 2] = (freq[latch / 2] and 0x3F0) or v; 6 -> { nc = v and 7; lfsr = 0x8000 }; else -> att[(latch - 1) / 2] = v }
        } else {
            val v = b and 0x3F
            when (latch) { 0, 2, 4 -> freq[latch / 2] = (freq[latch / 2] and 15) or (v shl 4); 6 -> { nc = v and 7; lfsr = 0x8000 }; else -> att[(latch - 1) / 2] = b and 15 }
        }
        for (i in 0..3) if (att[i] < 15) actiu[i] = true
    }
    // Atenuació (2 dB per pas): taula dels 16 valors (abans un Math.pow per canal i submostra), amb el mateix càlcul
    private val AMP = DoubleArray(16) { a -> if (a >= 15) 0.0 else Math.pow(10.0, -a * 2.0 / 20.0) }
    private fun amp(a: Int) = AMP[a]
    // Passos per submostra: es tornen a dividir només quan canvia el registre de freqüència (o el del soroll)
    private val pasN = IntArray(3) { -1 }; private val pasV = DoubleArray(3)
    private var sorollNc = -1; private var sorollF = -1; private var sorollV = 0.0
    fun mostra(mut: BooleanArray): Double {
        var s = 0.0
        for (k in 0 until 4) {
            for (c in 0..2) {
                val n = if (freq[c] == 0) 1024 else freq[c]
                if (pasN[c] != n) { pasN[c] = n; pasV[c] = clock / (32.0 * n) / (taux * 4.0) }
                fase[c] += pasV[c]; if (fase[c] >= 1.0) fase[c] -= Math.floor(fase[c])
                if (!mut[c]) s += (if (fase[c] < 0.5) 1.0 else -1.0) * amp(att[c]) * 0.25
            }
            if (sorollNc != nc || sorollF != freq[2]) {
                sorollNc = nc; sorollF = freq[2]
                val taxa = when (nc and 3) { 0 -> clock / 512.0; 1 -> clock / 1024.0; 2 -> clock / 2048.0; else -> clock / (32.0 * (if (freq[2] == 0) 1024 else freq[2])) }
                sorollV = taxa / (taux * 4.0)
            }
            nAcc += sorollV
            var q = 0; while (nAcc >= 1.0 && q < 64) {
                nAcc -= 1.0; q++
                val retro = if (nc and 4 != 0) Integer.bitCount(lfsr and mascara) and 1 else lfsr and 1
                lfsr = (lfsr shr 1) or (retro shl 15); nBit = lfsr and 1
            }
            if (nAcc > 4.0) nAcc = 0.0
            if (!mut[3]) s += (if (nBit == 1) 1.0 else -1.0) * amp(att[3]) * 0.25
        }
        return s / 4.0
    }
}

class SinteVgm(private val v: VgmArxiu, private val dades: ByteArray, private val muts: BooleanArray, solo0: Int = -1) : Sintetitzador {
    override val taux = RadioFreq.taux
    @Volatile override var solo = solo0
    private val psg = if (v.clockSn != 0) Psg(taux, v.clockSn, v.snFb) else null
    private val gb = if (v.clockGb != 0) M4aGb(false, taux) else null
    private val gbRam = IntArray(16)
    private val nes = if (v.clockNes != 0) NesApu(taux) { 0 } else null
    private val hpL = DcBlock(taux); private val hpR = DcBlock(taux)
    private var pos = v.inici
    private var espera = 0.0
    private var finit = false
    private var voltesN = 0
    private val mut = BooleanArray(5)
    private val pas = 44100.0 / taux
    private val niv = FloatArray(13)
    override val nivells: FloatArray? get() = niv

    private fun u8(o: Int) = if (o in dades.indices) dades[o].toInt() and 255 else 0
    private fun u16(o: Int) = u8(o) or (u8(o + 1) shl 8)

    private fun ordres() {
        var guard = 0
        while (espera <= 0.0 && !finit && guard++ < 100000) {
            val op = u8(pos); pos++
            when {
                op == 0x4F -> pos += 1
                op == 0x50 -> { psg?.escriu(u8(pos)); pos += 1 }
                op in 0x51..0x5F -> pos += 2
                op == 0x61 -> { espera += u16(pos); pos += 2 }
                op == 0x62 -> espera += 735.0
                op == 0x63 -> espera += 882.0
                op == 0x66 -> { if (v.buclePos != 0 && voltesN < 100000) { pos = v.buclePos; voltesN++ } else finit = true }
                op == 0x67 -> { val mida = u8(pos + 2) or (u8(pos + 3) shl 8) or (u8(pos + 4) shl 16) or (u8(pos + 5) shl 24); pos += 6 + mida }
                op == 0x68 -> pos += 11
                op in 0x70..0x7F -> espera += (op and 15) + 1
                op in 0x80..0x8F -> espera += op and 15
                op == 0x90 || op == 0x91 -> pos += 4
                op == 0x92 -> pos += 5
                op == 0x93 -> pos += 10
                op == 0x94 -> pos += 1
                op == 0x95 -> pos += 4
                op in 0x30..0x3F -> pos += 1
                op in 0x40..0x4E -> pos += 2
                op == 0xB3 -> { val a = u8(pos); val x = u8(pos + 1); pos += 2; gb?.let { g -> gbRegistre(g, gbRam, 0xFF10 + a, x) } }
                op == 0xB4 -> { val a = u8(pos); val x = u8(pos + 1); pos += 2; if (a < 0x20) nes?.escriu(0x4000 + a, x) }
                op in 0xA0..0xBF -> pos += 2
                op in 0xC0..0xDF -> pos += 3
                op >= 0xE0 -> pos += 4
                else -> {}
            }
            if (pos >= dades.size) finit = true
        }
    }

    override fun pistaActiva(i: Int) = (i < 4 && psg?.actiu?.get(i) == true) || (i < 4 && gb != null) || (nes?.actiu?.get(i) == true)
    override val voltes: Int get() = voltesN
    override val teBucle: Boolean? get() = v.buclePos != 0
    override val acabada: Boolean get() = finit

    override fun renderitza(out: ShortArray, frames: Int) {
        for (i in 0 until frames) {
            if (!finit) { ordres(); espera -= pas }
            for (k in 0 until 5) mut[k] = muts[k] || (solo >= 0 && solo != k)
            var l = 0.0; var r = 0.0
            psg?.let { val x = it.mostra(mut) * 0.8; l += x; r += x; niv[0] = maxOf(Math.abs(x).toFloat() * 3f, niv[0] * 0.9993f) }
            nes?.let { val x = it.mostra(mut) * 1.2; l += x; r += x; for (q in 0 until 5) niv[8 + q] = it.nivells[q] }
            gb?.let { g ->
                for (ch in 1..4) {
                    val so = !mut[ch - 1]
                    val x = if (so) g.out(ch, taux) * 0.25 else 0.0
                    niv[3 + ch] = maxOf(if (so) g.nivell(ch) else 0f, niv[3 + ch] * 0.9993f)
                    // NR50: volum general de la Game Boy, (v+1)/8 per costat (Pan Docs, «FF24 — NR50»)
                    if (g.nr51 and (0x10 shl (ch - 1)) != 0) l += x * (((g.nr50 shr 4) and 7) + 1) * 0.125
                    if (g.nr51 and (0x01 shl (ch - 1)) != 0) r += x * ((g.nr50 and 7) + 1) * 0.125
                }
                g.tick()
            }
            out[2 * i] = Satura.q(hpL.f(l) * 1.2).toShort()
            out[2 * i + 1] = Satura.q(hpR.f(r) * 1.2).toShort()
        }
    }
}
