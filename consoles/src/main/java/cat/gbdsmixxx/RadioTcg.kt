package cat.gbdsmixxx


/** Music1_Pitches de pret/poketcg i poketcg2 (idèntica): 85 valors de freqüència, generats del desassemblatge. */
internal val TONS_TCG = intArrayOf(
    0x002c, 0x009c, 0x0106, 0x016b, 0x01c9, 0x0222, 0x0278, 0x02c6, 0x0312, 0x0358, 0x039b, 0x03da,
    0x0416, 0x044e, 0x0483, 0x04b5, 0x04e5, 0x0511, 0x053c, 0x0563, 0x0589, 0x05ac, 0x05cd, 0x05ed,
    0x060b, 0x0628, 0x0642, 0x065b, 0x0672, 0x0689, 0x069e, 0x06b2, 0x06c4, 0x06d6, 0x06e7, 0x06f6,
    0x0705, 0x0714, 0x0721, 0x072d, 0x0739, 0x0744, 0x074f, 0x0759, 0x0762, 0x076b, 0x0773, 0x077b,
    0x0783, 0x078a, 0x0790, 0x0797, 0x079d, 0x07a2, 0x07a7, 0x07ac, 0x07b1, 0x07b6, 0x07ba, 0x07be,
    0x07c1, 0x07c5, 0x07c8, 0x07cb, 0x07ce, 0x07d1, 0x07d4, 0x07d6, 0x07d9, 0x07db, 0x07dd, 0x07df,
    0x07e1, 0x07e3, 0x07e4, 0x07e5, 0x07e7, 0x07e8, 0x07ea, 0x07eb, 0x07ec, 0x07ed, 0x07ee, 0x07ef,
    0x07f0
)

/**
 * Música de Pokémon Trading Card Game (GBC), sintetitzada en directe de la ROM de l'usuari. El motor és el de pret/poketcg `src/audio/music1.asm`
 * (el de `music2.asm` és idèntic, comprovat línia a línia; el joc en porta dues còpies, cadascuna amb les seves cançons). Un cop per fotograma
 * (el temporitzador del joc: 16384/68/4 = 60,235 Hz) fa el mateix que `Music1_Update`: per a cada canal, el comptador de nota, la nota, el
 * «cutoff», l'«echo» i el vibrato; i escriu els registres del so de la consola (`M4aGb`). Les taules (tons, ones, soroll, vibrato, capçaleres) es troben
 * per contingut dins de la ROM, sense dependre de la regió. Els efectes de so (`sfx.asm`) no hi són: la ràdio només fa música.
 */
class MotorTcg(val bank: Int, val tons: Int, val ones: IntArray, val soroll: IntArray, val vib: IntArray, val nCancons: Int, val bancs: IntArray, val caps: IntArray)

class RomTcg(private val d: ByteArray) {
    val mida = d.size
    fun u8(o: Int) = d[o].toInt() and 0xFF
    fun u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
    fun off(bank: Int, addr: Int) = bank * 0x4000 + addr - 0x4000
    /** Títol de la capçalera: «POKECARD». */
    val esTcg: Boolean get() = mida > 0x150 && String(d, 0x134, 8, Charsets.US_ASCII) == "POKECARD"

    // Music1_Pitches (= Music2_Pitches): taula de tons compartida amb TCG 2 (TONS_TCG, a dalt del fitxer); només serveix per trobar la taula dins la ROM
    private val TONS get() = TONS_TCG

    private fun cerca(pat: ByteArray, des: Int, fins: Int = d.size): Int {
        var i = des
        outer@ while (i <= minOf(fins, d.size) - pat.size) {
            for (k in pat.indices) if (d[i + k] != pat[k]) { i++; continue@outer }
            return i
        }
        return -1
    }
    private fun adr(o: Int) = 0x4000 + o % 0x4000

    val motors: List<MotorTcg> by lazy {
        val pat = ByteArray(TONS.size * 2) { val v = TONS[it / 2]; (if (it % 2 == 0) v and 255 else v shr 8).toByte() }
        val r = ArrayList<MotorTcg>()
        var p = 0
        while (true) {
            p = cerca(pat, p); if (p < 0) break
            try { motor(p, pat.size)?.let { r.add(it) } } catch (e: Exception) {}
            p += pat.size
        }
        r
    }

    /** Un motor: darrere dels tons hi ha, en aquest ordre, les ones (5 punters + 5x16), els instruments de soroll, els tipus de vibrato, les prioritats d'SFX i les capçaleres. */
    private fun motor(p: Int, n: Int): MotorTcg? {
        val bank = p / 0x4000; if (bank == 0) return null
        val q = p + n                                                   // Music1_WaveInstruments
        if (u16(q) != adr(q) + 10) return null
        val ones = IntArray(5) { off(bank, u16(q + 2 * it)) }
        val nz = q + 90                                                 // Music1_NoiseInstruments: 12 punters
        val soroll = IntArray(12) { u16(nz + 2 * it) }
        if (soroll.any { it < 0x4000 || it >= 0x8000 }) return null
        var fi = 0                                                       // final de les dades de soroll: 5 bytes de capçalera + valors fins a $FF
        for (a in soroll.toSet()) { var o = off(bank, a) + 5; while (u8(o) != 0xFF) o++; fi = maxOf(fi, o + 1) }
        if (u16(fi) != adr(fi) + 22) return null                       // Music1_VibratoTypes: 11 punters
        val vib = IntArray(11) { u16(fi + 2 * it) }
        val prio = ByteArray(96) { if (it == 0) 0 else 0x0A }           // Music1_SFXPriorities: $00 i 95 x $0A
        val s = cerca(prio, fi + 22, fi + 22 + 2000); if (s < 0) return null
        val nc = u8(s + 96); if (nc < 1 || nc > 64) return null         // NumberOfSongs, SongBanks (1 byte) i SongHeaderPointers (2 bytes)
        val bancs = IntArray(nc) { u8(s + 97 + it) }
        val caps = IntArray(nc) { u16(s + 97 + nc + 2 * it) }
        return MotorTcg(bank, p, ones, soroll, vib, nc, bancs, caps)
    }

    /** Cançons (id del joc -> motor) amb almenys un canal; els ids nuls d'un motor són cançons de l'altre. */
    val cancons: List<Pair<Int, MotorTcg>> by lazy {
        val r = ArrayList<Pair<Int, MotorTcg>>()
        for (id in 1 until 64) for (m in motors) {
            if (id >= m.nCancons || m.caps[id] == 0) continue
            val h = off(m.bancs[id], m.caps[id])
            if (h < 0 || h >= mida || u8(h) and 15 == 0) continue
            r.add(id to m); break
        }
        r
    }
    fun capcalera(id: Int, m: MotorTcg) = off(m.bancs[id], m.caps[id])
}

/** Sintetitzador d'una cançó: els `wMusic*` de `music1.asm` indexats pel canal (0-3 = canals 1-4 de la consola). */
class SinteTcg(private val rom: RomTcg, private val m: MotorTcg, private val id: Int, private val muts: BooleanArray, solo0: Int = -1) : Sintetitzador {
    override val taux = RadioFreq.taux
    @Volatile override var solo = solo0
    private val gb = M4aGb(false, taux)
    private val hpL = DcBlock(taux); private val hpR = DcBlock(taux)
    private val tg = FloatArray(4) { 1f }
    private val niv = FloatArray(4)
    override val nivells: FloatArray? get() = niv
    /** Oscil·loscopi per canal (P1, P2, ona i soroll): mostres de la sortida de cada canal de l'APU, a ~8 kHz. */
    override val oscil = OscDades(listOf("P1", "P2", "WV", "NO"))
    override fun notesMidi(out: FloatArray): Boolean { gb.notesMidi(out, 0); return true }          // notes exactes dels registres
    private val oscV = FloatArray(4); private var oscN = 0
    private var restaFot = 0.0
    private val spf = taux.toDouble() / (16384.0 / 68 / 4)
    private val bank = m.bancs[id]

    private val present = BooleanArray(4); private val playing = BooleanArray(4)
    private val ptr = IntArray(4); private val mainLoop = IntArray(4)
    private val tie = IntArray(4); private val speed = IntArray(4); private val octave = IntArray(4); private val duty = IntArray(4)
    private val volume = IntArray(4); private val cutoff = IntArray(4); private val echo = IntArray(4)
    private val vibDelay = IntArray(4); private val vibType = IntArray(4); private val vibType2 = IntArray(4)
    private val vibIdx = IntArray(4); private val vibCnt = IntArray(4)
    private val freqOff = IntArray(4); private val pitchOff = IntArray(4)
    private val noteCnt = IntArray(4); private val cutCnt = IntArray(4); private val pitchNib = IntArray(4)
    private val curPitch = IntArray(4)
    private val stack = Array(4) { IntArray(64) }; private val sp = IntArray(4)
    private var stereo = 0xFF                                         // wMusicStereoPanning
    private var wave = 0; private var waveChange = false
    private var nsActive = false; private var nsPtr = 0; private val ns = IntArray(4)   // wddef, wdded i wddab (LEN, ENV, POLY, GO)

    init {                                                             // Music1_BeginSong
        val h = rom.capcalera(id, m)
        val mask = rom.u8(h) and 15; var o = h + 1
        for (c in 0 until 4) if ((mask shr c) and 1 != 0) {
            val a = rom.u16(o); o += 2
            ptr[c] = a; mainLoop[c] = a; noteCnt[c] = 1; playing[c] = true; present[c] = true
            tie[c] = 0; freqOff[c] = 0; cutoff[c] = 0; vibDelay[c] = 0; pitchOff[c] = 0; sp[c] = 0
            echo[c] = if (c < 2) 0x08 else 0x40
        }
    }
    override fun pistaActiva(i: Int) = i < 4 && present[i]
    private var canalBucle = -1; private var voltesN = 0
    override val voltes: Int get() = voltesN
    override val acabada: Boolean get() = (0 until 4).none { present[it] && playing[it] }

    private fun rd(c: Int): Int { val v = rom.u8(rom.off(bank, ptr[c])); ptr[c] = (ptr[c] + 1) and 0xFFFF; return v }
    private fun tab(a: Int) = rom.off(m.bank, a)

    // ---- Ordres ($D0 i més) ----
    private fun playNext(c: Int) {
        var guarda = 0
        while (true) {
            if (guarda++ > 100000) { playing[c] = false; return }
            val b = rd(c)
            if (b < 0xD0) { nota(c, b); return }
            when (b - 0xD0) {
                0 -> speed[c] = rd(c)
                1, 2, 3, 4, 5, 6 -> octave[c] = ((b and 7) - 1 + (if (c == 2) 1 else 0)) and 0xFF   // el canal 3 és una octava més amunt
                7 -> octave[c] = (octave[c] + 1) and 0xFF
                8 -> octave[c] = (octave[c] - 1) and 0xFF
                9 -> tie[c] = 0x80
                12 -> {                                               // stereo_panning: es gira el paràmetre i la màscara tants cops com el número de canal
                    var a = rd(c); var e = 0xEE
                    for (k in 0 until c) { a = ((a shl 1) or (a shr 7)) and 255; e = ((e shl 1) or (e shr 7)) and 255 }
                    stereo = (stereo and e) or a
                }
                13 -> mainLoop[c] = (ptr[c] - 1) and 0xFFFF
                14 -> { if (canalBucle < 0) canalBucle = c; if (canalBucle == c) voltesN++; ptr[c] = mainLoop[c] }
                15 -> { val n = rd(c); val s = stack[c]; s[sp[c]] = ptr[c] and 255; s[sp[c] + 1] = ptr[c] shr 8; s[sp[c] + 2] = n; sp[c] += 3 }
                16 -> {
                    val s = stack[c]; val n = s[sp[c] - 1] - 1
                    if (n == 0) sp[c] -= 3 else { s[sp[c] - 1] = n; ptr[c] = s[sp[c] - 3] or (s[sp[c] - 2] shl 8) }
                }
                17 -> { val lo = rd(c); ptr[c] = lo or (rd(c) shl 8) }
                18 -> {                                               // music_call: es desa l'adreça de l'operand (al ret se li sumen 2)
                    val s = stack[c]; s[sp[c]] = ptr[c] and 255; s[sp[c] + 1] = ptr[c] shr 8; sp[c] += 2
                    val lo = rd(c); ptr[c] = lo or (rd(c) shl 8)
                }
                19 -> { sp[c] -= 2; val s = stack[c]; ptr[c] = ((s[sp[c]] or (s[sp[c] + 1] shl 8)) + 2) and 0xFFFF }
                20 -> freqOff[c] = rd(c)
                21 -> duty[c] = rd(c) and 0xC0
                22 -> volume[c] = rd(c)
                23 -> { wave = rd(c); waveChange = true }
                24 -> cutoff[c] = rd(c)
                25 -> echo[c] = rd(c)
                26 -> { val v = rd(c); vibType[c] = v; vibType2[c] = v }
                27 -> vibDelay[c] = rd(c)
                28 -> pitchOff[c] = rd(c)
                29 -> pitchOff[c] = (pitchOff[c] + rd(c)) and 0xFF
                else -> { playing[c] = false; return }                 // $DA, $DB, $EE-$FF: Music1_end
            }
        }
    }

    /** Music1_note: durada = velocitat x (longitud + 1) en fotogrames (aritmètica de 8 bits), tall (cutoff) i to. */
    private fun nota(c: Int, b: Int) {
        val seg = rom.u8(rom.off(bank, ptr[c]))                       // el byte que ve després (per saber si hi ha una lligadura)
        if (tie[c] != 0x80) { tie[c] = 1; vibIdx[c] = 0; vibCnt[c] = 1; vibType[c] = vibType2[c] }
        var a = (b and 15) + 1; var dd = speed[c]
        if (a < dd) { val t = a; a = dd; dd = t }
        val e = a
        while (true) { dd = (dd - 1) and 255; if (dd == 0) break; a = (a + e) and 255 }
        noteCnt[c] = a
        var tall = a
        if (seg != 0xD9 && cutoff[c] != 8) {                           // cutoff: durada x cutoff / 8
            val n = if (cutoff[c] == 0) 256 else cutoff[c]
            tall = (((a * n) and 0xFFFF) shr 3) and 0xFF
        }
        cutCnt[c] = tall
        pitchNib[c] = b and 0xF0
        if (pitchNib[c] == 0) return                                    // silenci
        val idx = (b shr 4) - 1
        if (c == 3) {                                                    // soroll: instrument = taula[nota + 12 x octava]
            val ia = m.soroll.getOrElse((idx + 12 * octave[3]) and 0xFF) { m.soroll[0] }
            val o = tab(ia)
            stereo = (stereo and 0x77) or rom.u8(o)
            ns[0] = rom.u8(o + 1); ns[1] = rom.u8(o + 2); ns[3] = rom.u8(o + 3); ns[2] = rom.u8(o + 4)
            nsPtr = ia + 5; nsActive = true
            return
        }
        var t = (2 * idx) and 255
        t = (t + 24 * octave[c]) and 255
        t = (t + 2 * pitchOff[c]) and 255
        var de = if (t / 2 < 85) rom.u16(m.tons + t) else 0
        val fo = freqOff[c]
        de = if (fo and 0x80 == 0) (de + fo) and 0xFFFF else (de - ((fo xor 0xFF) and 0xFF)) and 0xFFFF
        curPitch[c] = de
    }

    // ---- Escriptura als registres (Music1_f4714, f475a, f479c, f480a) ----
    private fun escriu(c: Int) {
        when (c) {
            0, 1 -> {
                val g = c + 1
                if (pitchNib[c] == 0) { tie[c] = 0; gb.oscOff(g); return }
                var dd = 0
                if (tie[c] != 0x80) { gb.nr2[g] = volume[c]; dd = 0x80 }
                tie[c] = 2
                if (c == 0) gb.nr10(0x08)
                gb.nr1x(g, duty[c])
                gb.nrx3(g, curPitch[c] and 255); gb.nrx4(g, ((curPitch[c] shr 8) and 0xFF) or dd)
            }
            2 -> {
                var dd = 0
                if (waveChange) {                                       // Music1_LoadWaveInstrument
                    gb.nr30 = 0
                    val wo = m.ones.getOrElse(wave) { m.ones[0] }
                    gb.setWave(IntArray(32) { val x = rom.u8(wo + it / 2); if (it % 2 == 0) x shr 4 else x and 15 })
                    waveChange = false; dd = 0x80
                }
                if (pitchNib[2] == 0) { tie[0] = 0; gb.nr30 = 0; return }   // (el joc posa a 0 la lligadura del canal 1, no la del 3: es manté)
                if (tie[2] != 0x80) { gb.nr2[3] = volume[2]; gb.nr30 = 0; dd = 0x80 }
                tie[2] = 2
                gb.nr31(0); gb.nrx3(3, curPitch[2] and 255); gb.nr30 = 0x80; gb.nrx4(3, ((curPitch[2] shr 8) and 0xFF) or dd)
            }
            else -> {
                if (pitchNib[3] == 0) { nsActive = false; gb.oscOff(4); return }
                gb.nr41(ns[0]); gb.nr2[4] = ns[1]; gb.nr43(ns[2]); gb.nrx4(4, ns[3])
            }
        }
    }

    private fun silenci(c: Int) {
        when (c) {
            0, 1 -> gb.oscOff(c + 1)
            2 -> { gb.nr2[3] = 0; gb.nrx4(3, 0x80) }
            else -> { nsActive = false; gb.oscOff(4) }
        }
    }

    /** Music1_UpdateVibrato + Music1_f490b: el to amb el vibrato s'escriu cada fotograma si el canal té retard de vibrato. */
    private fun vibra(c: Int) {
        val dl = vibDelay[c]
        if (dl == 0) return
        var p = curPitch[c]
        if (dl == vibCnt[c]) {
            var guarda = 0
            while (guarda++ < 64) {
                val vp = m.vib.getOrElse(vibType[c]) { m.vib[0] }
                val ix = vibIdx[c]; vibIdx[c] = (ix + 1) and 255
                val v = rom.u8(tab(vp + ix))
                if (v == 0x80) {                                        // final del tipus: es torna a començar o se salta a un altre tipus
                    vibIdx[c] = 0
                    val nx = rom.u8(tab(vp + ix + 1))
                    if (nx != 0x80) vibType[c] = nx
                    continue
                }
                val s = (p and 0xFF) + (if (v and 0x80 == 0) v else -(((v xor 0xFF) + 1) and 0xFF))
                p = ((((p shr 8) + (if (s < 0) -1 else s shr 8)) and 7) shl 8) or (s and 255)
                break
            }
        } else vibCnt[c] = (vibCnt[c] + 1) and 255
        val g = c + 1
        gb.nrx3(g, p and 255)
        if (c == 2) gb.nr31(0)
        gb.nrx4(g, (p shr 8) and 7)
    }

    private fun canal(c: Int) {
        if (c < 3) {
            if (!playing[c]) { silenci(c); return }
            if (pitchNib[c] != 0) {
                cutCnt[c] = (cutCnt[c] - 1) and 255
                if (cutCnt[c] == 0 && noteCnt[c] != 1) {                // tall de la nota: s'escriu l'«echo» i es reinicia el canal
                    if (c == 2) gb.nr2[3] = echo[2] else { gb.nr2[c + 1] = echo[c]; gb.nrx4(c + 1, 0x80) }
                }
            }
            noteCnt[c] = (noteCnt[c] - 1) and 255
            if (noteCnt[c] == 0) {
                playNext(c)
                if (!playing[c]) { silenci(c); return }
                escriu(c)
            }
            vibra(c)
        } else {
            if (!playing[3]) { silenci(3); return }
            noteCnt[3] = (noteCnt[3] - 1) and 255
            if (noteCnt[3] == 0) {
                playNext(3)
                if (!playing[3]) { silenci(3); return }
                escriu(3)
            } else if (nsActive) {                                       // Music1_f4839: un valor de POLY per fotograma fins a $FF
                val b = rom.u8(tab(nsPtr))
                if (b == 0xFF) { nsActive = false; gb.oscOff(4) } else { gb.nr43(b); nsPtr++ }
            }
        }
    }

    private fun frame() {
        for (c in 0 until 4) canal(c)
        gb.nr51 = stereo
        for (i in 0 until 4) { val obj = if (muts.getOrElse(i) { false } || (solo >= 0 && solo != i)) 0f else 1f; tg[i] += (obj - tg[i]) * 0.25f }
    }

    override fun renderitza(out: ShortArray, frames: Int) {
        var f = 0
        while (f < frames) {
            if (restaFot <= 0.0) { frame(); restaFot += spf }
            val n = minOf(frames - f, Math.ceil(restaFot).toInt().coerceAtLeast(1))
            // Volàtils: es llegeixen un cop per tros, no a cada mostra. Els nivells i l'oscil·loscopi només es llegeixen amb la pantalla encesa (`Motor`):
            // amb la pantalla apagada no es calculen (no afecten el so) i els nivells es posen a 0.
            val vis = EnPantalla.v; val osc = Oscil.visible && vis
            if (!vis) niv.fill(0f)
            for (i in 0 until n) {
                var l = 0f; var r = 0f
                for (ch in 1..4) {
                    val o = gb.out(ch, taux).toFloat(); if (osc) oscV[ch - 1] = o * 0.5f * tg[ch - 1] + 0.5f
                    val v = o * 0.25f * tg[ch - 1]
                    if (vis) niv[ch - 1] = maxOf(gb.nivell(ch) * tg[ch - 1], niv[ch - 1] * 0.9993f)
                    if (gb.nr51 and (0x10 shl (ch - 1)) != 0) l += v
                    if (gb.nr51 and (0x01 shl (ch - 1)) != 0) r += v
                }
                gb.tick()
                if (osc && ++oscN >= 4) { oscN = 0; oscil.escriu(oscV) }
                l = hpL.f(l.toDouble()).toFloat(); r = hpR.f(r.toDouble()).toFloat()
                out[(f + i) * 2] = SatGb.s(l)
                out[(f + i) * 2 + 1] = SatGb.s(r)
            }
            restaFot -= n
            f += n
        }
    }
}

// ======================================================================================================================================
// Pokémon Trading Card Game 2 (GBC, «Pokémon Card GB2: GR Dan Sanjou!»). Motor de pret/poketcg2 `src/audio/music1.asm` (els set `musicN.asm` són
// el mateix codi, comprovat; cadascun porta les seves cançons). És el de TCG 1 ampliat: ordres $EE-$F4 (sweep del canal 1, volum +/-, preset,
// volum alternat, còpia de seguretat), vibrato amb canvi de duty ($7F), set instruments d'ona, 12 de soroll, 19 vibratos i 37 «presets».
// ======================================================================================================================================

/** Un motor de TCG 2: `tons` = offset de la taula de tons; `ones`, `soroll`, `vib` i `pres` = offsets de ROM de les taules de punters (7 ones de 16 bytes; 12, 19 i 37 punters). */
class MotorTcg2(val bank: Int, val tons: Int, val ones: IntArray, val soroll: Int, val vib: Int, val nVib: Int, val pres: Int, val nPres: Int,
                val nCancons: Int, val bancs: IntArray, val caps: IntArray)

class RomTcg2(private val d: ByteArray) {
    val mida = d.size
    fun u8(o: Int) = d[o].toInt() and 0xFF
    fun u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
    fun off(bank: Int, addr: Int) = bank * 0x4000 + addr - 0x4000
    /** Títol de la capçalera (rgbfix -t): «POKEMON-CG2». Si una versió el canvia, es reconeix igualment per les taules (`motors`). */
    val titolTcg2: Boolean get() = mida > 0x150 && String(d, 0x134, 11, Charsets.US_ASCII) == "POKEMON-CG2"
    val esTcg2: Boolean get() = titolTcg2 || motors.isNotEmpty()

    private fun cerca(pat: ByteArray, des: Int): Int {
        var i = des
        outer@ while (i <= d.size - pat.size) {
            for (k in pat.indices) if (d[i + k] != pat[k]) { i++; continue@outer }
            return i
        }
        return -1
    }
    private fun adr(o: Int) = 0x4000 + o % 0x4000

    /** Els set motors (un per banc d'àudio: «Audio 1» a «Audio 7», bancs $77-$7D a la ROM original; els $7E i $7F són dels SFX). */
    val motors: List<MotorTcg2> by lazy {
        val pat = ByteArray(TONS_TCG.size * 2) { val v = TONS_TCG[it / 2]; (if (it % 2 == 0) v and 255 else v shr 8).toByte() }
        val r = ArrayList<MotorTcg2>()
        var p = 0
        while (true) {
            p = cerca(pat, p); if (p < 0) break
            try { motor(p, pat.size)?.let { r.add(it) } } catch (e: Exception) {}
            p += pat.size
        }
        r
    }

    /**
     * Darrere dels tons, en aquest ordre: 7 ones (7 punters + 7x16 bytes), 12 punters de soroll + les seves dades (5 bytes de capçalera i valors fins a $FF),
     * els punters dels vibratos (19) + dades (fins a $80 xx; $7F porta un operand), els «presets» (37 punters + 37x5 bytes), les prioritats d'SFX
     * ($00 i una tirada de $0A: 248 entrades a cada banc), NumberOfSongs (60), SongBanks (1 byte per cançó) i SongHeaderPointers (2 bytes per cançó).
     */
    private fun motor(p: Int, n: Int): MotorTcg2? {
        val bank = p / 0x4000; if (bank == 0) return null
        val q = p + n                                                   // Music1_WaveInstruments
        if (u16(q) != adr(q) + 14) return null
        val ones = IntArray(7) { off(bank, u16(q + 2 * it)) }
        if ((1 until 7).any { ones[it] != ones[0] + 16 * it }) return null
        val nz = q + 14 + 7 * 16                                        // Music1_NoiseInstruments: 12 punters
        val soroll = IntArray(12) { u16(nz + 2 * it) }
        if (soroll.any { it < 0x4000 || it >= 0x8000 }) return null
        var fi = 0
        for (a in soroll.toSet()) { val o0 = off(bank, a) + 5; var o = o0; while (u8(o) != 0xFF) { o++; if (o - o0 > 256) return null }; fi = maxOf(fi, o + 1) }
        val nvb = u16(fi) - adr(fi)                                     // Music1_VibratoTypes: nVib punters
        if (nvb < 2 || nvb % 2 != 0 || nvb > 64) return null
        val nVib = nvb / 2
        var pe = fi + nvb
        for (k in 0 until nVib) {
            var o = off(bank, u16(fi + 2 * k)); var g = 0
            while (true) {
                if (g++ > 512) return null
                val b = u8(o)
                if (b == 0x7F) o += 2 else if (b == 0x80) { o += 2; break } else o++
            }
            pe = maxOf(pe, o)
        }
        val npb = u16(pe) - adr(pe)                                     // Music1_Presets: punters a blocs de 5 bytes
        if (npb < 2 || npb % 2 != 0 || npb > 512) return null
        val nPres = npb / 2
        for (k in 0 until nPres) if (u16(pe + 2 * k) != u16(pe) + 5 * k) return null
        val pr = pe + npb + 5 * nPres                                   // Music1_SFXPriorities
        if (u8(pr) != 0) return null
        var s = pr + 1
        while (u8(s) == 0x0A) s++
        if (s - pr < 10) return null
        val nc = u8(s); if (nc < 1 || nc > 128) return null             // NumberOfSongs, SongBanks i SongHeaderPointers
        val bancs = IntArray(nc) { u8(s + 1 + it) }
        if (bancs.any { it * 0x4000 >= mida }) return null
        val caps = IntArray(nc) { u16(s + 1 + nc + 2 * it) }
        if (caps.any { it != 0 && (it < 0x4000 || it >= 0x8000) }) return null
        return MotorTcg2(bank, p, ones, nz, fi, nVib, pe, nPres, nc, bancs, caps)
    }

    /** Cançons (id del joc -> motor) amb almenys un canal. Les taules de capçaleres de les set còpies apunten a les mateixes cançons; es pren el primer motor que en dona una de vàlida. */
    val cancons: List<Pair<Int, MotorTcg2>> by lazy {
        val r = ArrayList<Pair<Int, MotorTcg2>>()
        for (id in 1 until 128) for (m in motors) {
            if (id >= m.nCancons || m.caps[id] == 0) continue
            val h = off(m.bancs[id], m.caps[id])
            if (h < 0 || h >= mida || u8(h) and 15 == 0) continue
            r.add(id to m); break
        }
        r
    }
    fun capcalera(id: Int, m: MotorTcg2) = off(m.bancs[id], m.caps[id])
}

/**
 * Sintetitzador d'una cançó de TCG 2: els `wMusic*` de `music1.asm` indexats pel canal (0-3 = canals 1-4 de la consola). Un cop per fotograma (60,235 Hz)
 * fa el mateix que `Music1_Update`. Respecte de TCG 1: sweep del canal 1 (ordre $EE), volum +/- (ordre $EF; al soroll és l'increment d'envolupant), presets
 * ($F0), volum alternat nota a nota ($F1-$F3), còpia de seguretat ($F4: posa a 0 els índexs de vibrato), vibrato amb canvi de duty ($7F) i el to llegit de la
 * taula de ROM sense límit (com el joc). No hi són els efectes de so (`Music1_PlaySFX`/`SFX_UpdateSFX`: la ràdio no en fa; per això wdd8c, els bits que
 * silencien canals per a un SFX, és sempre 0), ni les crides externes del joc (`_SetVolume`, `_PauseSong`/`_ResumeSong`, wddf0/wddf2) ni el canvi de banc de
 * WRAM: el sintetitzador porta l'estat propi i cada cançó en fa un de nou.
 */
class SinteTcg2(private val rom: RomTcg2, private val m: MotorTcg2, private val id: Int, private val muts: BooleanArray, solo0: Int = -1) : Sintetitzador {
    override val taux = RadioFreq.taux
    @Volatile override var solo = solo0
    private val gb = M4aGb(false, taux)
    private val hpL = DcBlock(taux); private val hpR = DcBlock(taux)
    private val tg = FloatArray(4) { 1f }
    private val niv = FloatArray(4)
    override val nivells: FloatArray? get() = niv
    /** Oscil·loscopi per canal (P1, P2, ona i soroll): mostres de la sortida de cada canal de l'APU, a ~8 kHz. */
    override val oscil = OscDades(listOf("P1", "P2", "WV", "NO"))
    override fun notesMidi(out: FloatArray): Boolean { gb.notesMidi(out, 0); return true }          // notes exactes dels registres
    private val oscV = FloatArray(4); private var oscN = 0
    private var restaFot = 0.0
    private val spf = taux.toDouble() / (16384.0 / 68 / 4)
    private val bank = m.bancs[id]

    private val present = BooleanArray(4); private val playing = BooleanArray(4)
    private val ptr = IntArray(4); private val mainLoop = IntArray(4)
    private val tie = IntArray(4); private val speed = IntArray(4); private val octave = IntArray(4); private val duty = IntArray(4)
    private val volume = IntArray(4); private val cutoff = IntArray(4); private val echo = IntArray(4)
    private val volAlt = IntArray(4); private val altOn = IntArray(4); private val altCnt = IntArray(4)     // wAudio_d07a, d080 i d07d
    private val vibDelay = IntArray(4); private val vibType = IntArray(4); private val vibType2 = IntArray(4)
    private val vibIdx = IntArray(4); private val vibCnt = IntArray(4)
    private val freqOff = IntArray(4); private val pitchOff = IntArray(4)
    private val noteCnt = IntArray(4); private val cutCnt = IntArray(4); private val pitchNib = IntArray(4)
    private val curPitch = IntArray(4)
    private val stack = Array(4) { IntArray(64) }; private val sp = IntArray(4)
    private var stereo = 0xFF                                         // wMusicStereoPanning
    private var wave = 0; private var waveChange = false
    private var sweep = 0x08                                          // wAudio_d083 (NR10: sense escombrada)
    private var volSoroll = 0                                         // wAudio_d087: se suma a l'envolupant de cada nota de soroll
    private var copia = false                                         // wAudio_d011: Music1_BackupSong al final del fotograma
    private var nsActive = false; private var nsPtr = 0; private val ns = IntArray(4)   // wddef, wdded i wddab (LEN, ENV, POLY, GO)

    init {                                                             // Music1_BeginSong
        val h = rom.capcalera(id, m)
        val mask = rom.u8(h) and 15; var o = h + 1
        for (c in 0 until 4) if ((mask shr c) and 1 != 0) {
            val a = rom.u16(o); o += 2
            ptr[c] = a; mainLoop[c] = a; noteCnt[c] = 1; playing[c] = true; present[c] = true
            tie[c] = 0; freqOff[c] = 0; cutoff[c] = 0; vibDelay[c] = 0; pitchOff[c] = 0; sp[c] = 0; altOn[c] = 0
            echo[c] = if (c < 2) 0x08 else 0x40
            if (c == 0) sweep = 0x08
            if (c == 3) volSoroll = 0
        }
    }
    override fun pistaActiva(i: Int) = i < 4 && present[i]
    private var canalBucle = -1; private var voltesN = 0
    override val voltes: Int get() = voltesN
    override val acabada: Boolean get() = (0 until 4).none { present[it] && playing[it] }

    private fun rd(c: Int): Int {
        val o = rom.off(bank, ptr[c]); ptr[c] = (ptr[c] + 1) and 0xFFFF
        return if (o < 0 || o >= rom.mida) 0xFF else rom.u8(o)
    }
    private fun tab(a: Int) = rom.off(m.bank, a)
    private fun byteRom(o: Int) = if (o < 0 || o >= rom.mida) 0 else rom.u8(o)
    private fun wordRom(o: Int) = byteRom(o) or (byteRom(o + 1) shl 8)
    /** Suma `e` a la meitat alta d'un byte (volum), mòdul 16: el `swap a; add e; and $0f; swap a` de Music1_musicef. */
    private fun sumaAlt(v: Int, e: Int) = ((((v shr 4) + e) and 15) shl 4) or (v and 15)

    // ---- Ordres ($D0 i més) ----
    private fun playNext(c: Int) {
        var guarda = 0
        while (true) {
            if (guarda++ > 100000) { playing[c] = false; return }
            val b = rd(c)
            if (b < 0xD0) { nota(c, b); return }
            when (b - 0xD0) {
                0 -> speed[c] = rd(c)
                1, 2, 3, 4, 5, 6 -> octave[c] = ((b and 7) - 1 + (if (c == 2) 1 else 0)) and 0xFF   // el canal 3 és una octava més amunt
                7 -> octave[c] = (octave[c] + 1) and 0xFF
                8 -> octave[c] = (octave[c] - 1) and 0xFF
                9 -> tie[c] = 0x80
                12 -> {                                               // stereo_panning: es gira el paràmetre i la màscara tants cops com el número de canal
                    var a = rd(c); var e = 0xEE
                    for (k in 0 until c) { a = ((a shl 1) or (a shr 7)) and 255; e = ((e shl 1) or (e shr 7)) and 255 }
                    stereo = (stereo and e) or a
                }
                13 -> mainLoop[c] = (ptr[c] - 1) and 0xFFFF
                14 -> { if (canalBucle < 0) canalBucle = c; if (canalBucle == c) voltesN++; ptr[c] = mainLoop[c] }
                15 -> { val n = rd(c); val s = stack[c]; s[sp[c]] = ptr[c] and 255; s[sp[c] + 1] = ptr[c] shr 8; s[sp[c] + 2] = n; sp[c] += 3 }
                16 -> {
                    val s = stack[c]; val n = (s[sp[c] - 1] - 1) and 255
                    if (n == 0) sp[c] -= 3 else { s[sp[c] - 1] = n; ptr[c] = s[sp[c] - 3] or (s[sp[c] - 2] shl 8) }
                }
                17 -> { val lo = rd(c); ptr[c] = lo or (rd(c) shl 8) }
                18 -> {                                               // music_call: es desa l'adreça de l'operand (al ret se li sumen 2)
                    val s = stack[c]; s[sp[c]] = ptr[c] and 255; s[sp[c] + 1] = ptr[c] shr 8; sp[c] += 2
                    val lo = rd(c); ptr[c] = lo or (rd(c) shl 8)
                }
                19 -> { sp[c] -= 2; val s = stack[c]; ptr[c] = ((s[sp[c]] or (s[sp[c] + 1] shl 8)) + 2) and 0xFFFF }
                20 -> freqOff[c] = rd(c)
                21 -> duty[c] = rd(c) and 0xC0
                22 -> volume[c] = rd(c)
                23 -> { wave = rd(c); waveChange = true }
                24 -> cutoff[c] = rd(c)
                25 -> echo[c] = rd(c)
                26 -> { val v = rd(c); vibType[c] = v; vibType2[c] = v }
                27 -> vibDelay[c] = rd(c)
                28 -> pitchOff[c] = rd(c)
                29 -> pitchOff[c] = (pitchOff[c] + rd(c)) and 0xFF
                30 -> { val v = rd(c); if (c == 0) sweep = v }       // musicee: wAudio_d083 + canal (només el canal 1 és el sweep; les dades només l'usen allà)
                31 -> {                                               // musicef: volum +/- (meitat alta del volum i del volum alternat); al soroll, wAudio_d087
                    val v = rd(c)
                    if (c == 3) volSoroll = (v and 15) shl 4
                    else { volume[c] = sumaAlt(volume[c], v); volAlt[c] = sumaAlt(volAlt[c], v) }
                }
                32 -> preset(c, rd(c))
                33 -> { volAlt[c] = rd(c); altOn[c] = 1; altCnt[c] = 0 }   // musicf1: volum alternat = operand, activat
                34 -> { altOn[c] = 1; altCnt[c] = 0 }                     // musicf2
                35 -> altOn[c] = 0                                        // musicf3
                36 -> copia = true                                        // musicf4
                else -> { playing[c] = false; return }                 // $DA, $DB, $F5-$FF: Music1_end
            }
        }
    }

    /** Music1_preset: 5 bytes (volum si no és 0, duty si no és $FF, tipus de vibrato si no és $FF, retard de vibrato si no és $FF, offset de freqüència si no és $80). */
    private fun preset(c: Int, i: Int) {
        if (i >= m.nPres) return
        val p = tab(wordRom(m.pres + 2 * i))
        val v = byteRom(p); if (v != 0) volume[c] = v
        val du = byteRom(p + 1); if (du != 0xFF) duty[c] = du
        val vt = byteRom(p + 2); if (vt != 0xFF) { vibType[c] = vt; vibType2[c] = vt }
        val vd = byteRom(p + 3); if (vd != 0xFF) vibDelay[c] = vd
        val fo = byteRom(p + 4); if (fo != 0x80) freqOff[c] = fo
    }

    /** Music1_note: durada = velocitat x (longitud + 1) en fotogrames (aritmètica de 8 bits), tall (cutoff) i to. */
    private fun nota(c: Int, b: Int) {
        val seg = byteRom(rom.off(bank, ptr[c]))                      // el byte que ve després (per saber si hi ha una lligadura)
        if (tie[c] != 0x80) { tie[c] = 1; vibIdx[c] = 0; vibCnt[c] = 1; vibType[c] = vibType2[c] }
        var a = (b and 15) + 1; var dd = speed[c]
        if (a < dd) { val t = a; a = dd; dd = t }
        val e = a
        while (true) { dd = (dd - 1) and 255; if (dd == 0) break; a = (a + e) and 255 }
        noteCnt[c] = a
        var tall = a
        if (seg != 0xD9 && cutoff[c] != 8) {                           // cutoff: durada x cutoff / 8
            val n = if (cutoff[c] == 0) 256 else cutoff[c]
            tall = (((a * n) and 0xFFFF) shr 3) and 0xFF
        }
        cutCnt[c] = tall
        pitchNib[c] = b and 0xF0
        if (pitchNib[c] == 0) return                                    // silenci
        val idx = (b shr 4) - 1
        if (c == 3) {                                                    // soroll: punter = taula + 2 x nota + (24 x octava) (8 bits)
            val ia = wordRom(m.soroll + 2 * idx + ((24 * octave[3]) and 255))
            val o = tab(ia)
            stereo = (stereo and 0x77) or byteRom(o)
            ns[0] = byteRom(o + 1); ns[1] = (byteRom(o + 2) + volSoroll) and 255; ns[3] = byteRom(o + 3); ns[2] = byteRom(o + 4)
            nsPtr = ia + 5; nsActive = true
            return
        }
        var t = (2 * idx) and 255
        t = (t + byteRom(m.tons - 8 + (octave[c] and 255))) and 255    // Music1_OctaveOffsets: els 8 bytes just abans dels tons (24 x octava)
        t = (t + 2 * pitchOff[c]) and 255
        var de = wordRom(m.tons + t)                                    // la taula es llegeix sense límit (t pot passar dels 85 tons)
        val fo = freqOff[c]
        de = if (fo and 0x80 == 0) (de + fo) and 0xFFFF else (de - ((fo xor 0xFF) and 0xFF)) and 0xFFFF
        curPitch[c] = de
    }

    /** Volum del canal 1-3 d'una nota nova (Music1_f4714, f475a, f479c): el volum, o l'alternat en les notes imparells des de $F1/$F2 (comptador wAudio_d07d). */
    private fun volumNota(c: Int): Int {
        var v = volume[c]
        if (altOn[c] != 0) { val a = altCnt[c]; altCnt[c] = (a + 1) and 255; if (a and 1 != 0) v = volAlt[c] }
        return v
    }

    // ---- Escriptura als registres (Music1_f4714, f475a, f479c, f480a) ----
    private fun escriu(c: Int) {
        when (c) {
            0, 1 -> {
                val g = c + 1
                if (pitchNib[c] == 0) { tie[c] = 0; gb.oscOff(g); return }
                var dd = 0
                if (tie[c] != 0x80) { gb.nr2[g] = volumNota(c); dd = 0x80 }
                tie[c] = 2
                if (c == 0) gb.nr10(sweep)
                gb.nr1x(g, duty[c])
                gb.nrx3(g, curPitch[c] and 255); gb.nrx4(g, ((curPitch[c] shr 8) and 0xFF) or dd)
            }
            2 -> {
                var dd = 0
                if (waveChange) {                                       // Music1_LoadWaveInstrument
                    gb.nr30 = 0
                    val wo = m.ones.getOrElse(wave) { m.ones[0] }
                    gb.setWave(IntArray(32) { val x = byteRom(wo + it / 2); if (it % 2 == 0) x shr 4 else x and 15 })
                    waveChange = false; dd = 0x80
                }
                if (pitchNib[2] == 0) { tie[2] = 0; gb.nr30 = 0; return }
                if (tie[2] != 0x80) { gb.nr2[3] = volumNota(2); gb.nr30 = 0; dd = 0x80 }
                tie[2] = 2
                gb.nr31(0); gb.nrx3(3, curPitch[2] and 255); gb.nr30 = 0x80; gb.nrx4(3, ((curPitch[2] shr 8) and 0xFF) or dd)
            }
            else -> {
                if (pitchNib[3] == 0) { nsActive = false; gb.oscOff(4); return }
                gb.nr41(ns[0]); gb.nr2[4] = ns[1]; gb.nr43(ns[2]); gb.nrx4(4, ns[3])
            }
        }
    }

    private fun silenci(c: Int) {
        when (c) {
            0, 1 -> gb.oscOff(c + 1)
            2 -> { gb.nr2[3] = 0; gb.nrx4(3, 0x80) }
            else -> { nsActive = false; gb.oscOff(4) }
        }
    }

    /** Music1_UpdateVibrato + Music1_f490b: el to amb el vibrato s'escriu cada fotograma si el canal té retard de vibrato. $7F canvia el duty (NR11/NR21) amb el byte següent. */
    private fun vibra(c: Int) {
        val dl = vibDelay[c]
        if (dl == 0) return
        var p = curPitch[c]
        if (dl == vibCnt[c]) {
            var guarda = 0
            while (guarda++ < 64) {
                val vp = wordRom(m.vib + 2 * vibType[c])
                val ix = vibIdx[c]; vibIdx[c] = (ix + 1) and 255
                val v = byteRom(tab(vp + ix))
                if (v == 0x80) {                                        // final del tipus: es torna a començar o se salta a un altre tipus
                    vibIdx[c] = 0
                    val nx = byteRom(tab(vp + ix + 1))
                    if (nx != 0x80) vibType[c] = nx
                    continue
                }
                if (v == 0x7F) {                                        // Music1_1dcaff: el byte següent s'escriu a NR11 (canal 1) o NR21 (canal 2); als altres s'ignora
                    val w = byteRom(tab(vp + ix + 1))
                    if (c == 0 || c == 1) gb.nr1x(c + 1, w)
                    vibIdx[c] = (vibIdx[c] + 1) and 255
                    continue
                }
                val s = (p and 0xFF) + (if (v and 0x80 == 0) v else -(((v xor 0xFF) + 1) and 0xFF))
                p = ((((p shr 8) + (if (s < 0) -1 else s shr 8)) and 7) shl 8) or (s and 255)
                break
            }
        } else vibCnt[c] = (vibCnt[c] + 1) and 255
        val g = c + 1
        gb.nrx3(g, p and 255)
        if (c == 2) gb.nr31(0)
        val hi = (p shr 8) and 0xFF
        gb.nrx4(g, if (c == 0) hi and 0x3F else hi)
    }

    private fun canal(c: Int) {
        if (c < 3) {
            if (!playing[c]) { silenci(c); return }
            if (pitchNib[c] != 0) {
                cutCnt[c] = (cutCnt[c] - 1) and 255
                if (cutCnt[c] == 0 && noteCnt[c] != 1) {                // tall de la nota: s'escriu l'«echo» i es reinicia el canal
                    if (c == 2) gb.nr2[3] = echo[2] else { gb.nr2[c + 1] = echo[c]; gb.nrx4(c + 1, 0x80) }
                }
            }
            noteCnt[c] = (noteCnt[c] - 1) and 255
            if (noteCnt[c] == 0) {
                playNext(c)
                if (!playing[c]) { silenci(c); return }
                escriu(c)
            }
            vibra(c)
        } else {
            if (!playing[3]) { silenci(3); return }
            noteCnt[3] = (noteCnt[3] - 1) and 255
            if (noteCnt[3] == 0) {
                playNext(3)
                if (!playing[3]) { silenci(3); return }
                escriu(3)
            } else if (nsActive) {                                       // Music1_f4839: un valor de POLY per fotograma fins a $FF
                val b = byteRom(tab(nsPtr))
                if (b == 0xFF) { nsActive = false; gb.oscOff(4) } else { gb.nr43(b); nsPtr++ }
            }
        }
    }

    private fun frame() {
        for (c in 0 until 4) canal(c)
        gb.nr51 = stereo
        if (copia) { vibIdx.fill(0); copia = false }                   // Music1_BackupSong també posa a 0 wdddb (índex de vibrato) dels quatre canals
        for (i in 0 until 4) { val obj = if (muts.getOrElse(i) { false } || (solo >= 0 && solo != i)) 0f else 1f; tg[i] += (obj - tg[i]) * 0.25f }
    }

    override fun renderitza(out: ShortArray, frames: Int) {
        var f = 0
        while (f < frames) {
            if (restaFot <= 0.0) { frame(); restaFot += spf }
            val n = minOf(frames - f, Math.ceil(restaFot).toInt().coerceAtLeast(1))
            // Volàtils: es llegeixen un cop per tros, no a cada mostra. Els nivells i l'oscil·loscopi només es llegeixen amb la pantalla encesa (`Motor`):
            // amb la pantalla apagada no es calculen (no afecten el so) i els nivells es posen a 0.
            val vis = EnPantalla.v; val osc = Oscil.visible && vis
            if (!vis) niv.fill(0f)
            for (i in 0 until n) {
                var l = 0f; var r = 0f
                for (ch in 1..4) {
                    val o = gb.out(ch, taux).toFloat(); if (osc) oscV[ch - 1] = o * 0.5f * tg[ch - 1] + 0.5f
                    val v = o * 0.25f * tg[ch - 1]
                    if (vis) niv[ch - 1] = maxOf(gb.nivell(ch) * tg[ch - 1], niv[ch - 1] * 0.9993f)
                    if (gb.nr51 and (0x10 shl (ch - 1)) != 0) l += v
                    if (gb.nr51 and (0x01 shl (ch - 1)) != 0) r += v
                }
                gb.tick()
                if (osc && ++oscN >= 4) { oscN = 0; oscil.escriu(oscV) }
                l = hpL.f(l.toDouble()).toFloat(); r = hpR.f(r.toDouble()).toFloat()
                out[(f + i) * 2] = SatGb.s(l)
                out[(f + i) * 2 + 1] = SatGb.s(r)
            }
            restaFot -= n
            f += n
        }
    }
}
