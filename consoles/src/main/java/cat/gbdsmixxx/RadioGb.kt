package cat.gbdsmixxx


/**
 * Música de Game Boy: Pokémon Cristall (motor de pokecrystal `audio/engine.asm`) i Groc (motor de pokered/pokeyellow `audio/engine_*.asm`),
 * sintetitzada en directe de la ROM de l'usuari. Les adreces es troben per contingut (taula de freqüències, bateries, taula de cançons),
 * sense dependre de la revisió de la ROM. El motor fa la mateixa feina que `_UpdateSound` (un cop per fotograma, 59,7275 Hz) i escriu els
 * registres del so de la consola (`M4aGb`, el maquinari de 4 canals de la RadioM4a).
 */
class RomGb(val d: ByteArray) {
    val mida = d.size
    fun u8(o: Int) = d[o].toInt() and 0xFF
    fun u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
    val titol: String get() = String(d, 0x134, 11, Charsets.US_ASCII).trim { it.code == 0 || it == ' ' }
    val esCristall get() = titol.startsWith("PM_CRYSTAL")
    val esGroc get() = titol.startsWith("POKEMON YELLOW")
    fun off(bank: Int, addr: Int) = if (addr < 0x4000) addr else bank * 0x4000 + addr - 0x4000

    private fun cerca(pat: ByteArray, des: Int = 0): Int {
        var i = des
        outer@ while (i <= d.size - pat.size) {
            for (k in pat.indices) if (d[i + k] != pat[k]) { i++; continue@outer }
            return i
        }
        return -1
    }

    // ---- Cristall: taula de freqüències (notes.asm), ones (wave_samples.asm), bateries (drumkits.asm) i taula de cançons (music_pointers.asm) ----
    val freqCr: IntArray = intArrayOf(0, 0xf82c, 0xf89d, 0xf907, 0xf96b, 0xf9ca, 0xfa23, 0xfa77, 0xfac7, 0xfb12, 0xfb58, 0xfb9b, 0xfbda,
        0xfc16, 0xfc4e, 0xfc83, 0xfcb5, 0xfce5, 0xfd11, 0xfd3b, 0xfd63, 0xfd89, 0xfdac, 0xfdcd, 0xfded)
    val freqOff: Int by lazy {
        val pat = ByteArray(50) { val v = freqCr[it / 2]; (if (it % 2 == 0) v and 255 else v shr 8).toByte() }
        cerca(pat)
    }
    val bancMotor get() = freqOff / 0x4000
    val waveOff get() = freqOff + 50
    val drumkits: IntArray by lazy { IntArray(6) { u16(waveOff + 160 + 2 * it) } }
    val taulaCr: Int by lazy {
        if (freqOff < 0) return@lazy -1
        val b0 = bancMotor * 0x4000
        var i = b0
        while (i < b0 + 0x4000 - 103 * 3) {
            var ok = true
            for (k in 0 until 103) {
                val bk = u8(i + 3 * k); val hi = u8(i + 3 * k + 2)
                if (bk < 1 || bk >= 0x80 || hi < 0x40 || hi >= 0x80) { ok = false; break }
            }
            if (ok && u8(i) == bancMotor) return@lazy i
            i++
        }
        -1
    }
    val nCancons = 103

    // ---- Groc: cada banc d'àudio ($02, $08, $1F, $20) té el seu motor; es troben per l'ona 0 (wave_samples.asm) ----
    private val wave0 = intArrayOf(0x02, 0x46, 0x8A, 0xCE, 0xFF, 0xFE, 0xED, 0xDC, 0xCB, 0xA9, 0x87, 0x65, 0x44, 0x33, 0x22, 0x11)
    val ones0: List<Int> by lazy {
        val pat = ByteArray(16) { wave0[it].toByte() }
        val r = ArrayList<Int>(); var i = 0
        while (true) { i = cerca(pat, i); if (i < 0) break; r.add(i); i += 16 }
        r
    }
    fun ona0Banc(banc: Int): Int = ones0.firstOrNull { it / 0x4000 == banc } ?: -1
    private val tonsGroc = intArrayOf(0xF82C, 0xF89D, 0xF907, 0xF96B, 0xF9CA, 0xFA23, 0xFA77, 0xFAC7, 0xFB12, 0xFB58, 0xFB9B, 0xFBDA)
    val tons: IntArray get() = tonsGroc
}

/** Un canal del motor de Cristall (`wChannelN`). */
class GbCanal(val idx: Int, var bank: Int, var addr: Int) {
    var on = true; var noise = false; var sub = false; var looping = false
    var vib = false; var slide = false; var offs = false; var dutyLoop = false; var vibDir = false; var slideDir = false
    var loopCount = 0; var tempo = 0x100; var nlen = 1; var ndur = 0; var nmod = 0
    var octave = 0; var trans = 0; var duty = 0; var env = 0; var freq = 0
    var vdelay = 0; var vdc = 0; var vext = 0; var vrate = 0; var vrate0 = 0
    var target = 0; var amount = 0; var frac = 0; var f25 = 0; var poff = 0; var dpat = 0
    var cond = 0; var last = 0
    val tracks0 = intArrayOf(0x11, 0x22, 0x44, 0x88)[idx]
    var tracks = tracks0
    // marques de la nota d'aquest fotograma (CHANNEL_NOTE_FLAGS)
    var fRest = false; var fTrig = false; var fFreq = false; var fDuty = false; var fVib = false; var fSweep = false
}

class SinteCristall(private val rom: RomGb, song: Int, private val muts: BooleanArray, solo0: Int = -1) : Sintetitzador {
    override val taux = RadioFreq.taux
    @Volatile override var solo = solo0
    private val gb = M4aGb(false, taux)
    private val hpL = DcBlock(taux); private val hpR = DcBlock(taux)
    private val can = arrayOfNulls<GbCanal>(4)
    private var vol = 0x77; private var curDur = 0; private var nsAddr = 0; private var nsDelay = 0; private var nsSet = 0; private var pSweep = 0
    private val tg = FloatArray(4) { 1f }
    private val niv = FloatArray(4)
    override val nivells: FloatArray? get() = niv
    /** Oscil·loscopi per canal (P1, P2, ona i soroll): mostres de la sortida de cada canal de l'APU, a ~8 kHz. */
    override val oscil = OscDades(listOf("P1", "P2", "WV", "NO"))
    override fun notesMidi(out: FloatArray): Boolean { gb.notesMidi(out, 0); return true }          // notes exactes dels registres
    private val oscV = FloatArray(4); private var oscN = 0
    private var restaFot = 0.0
    private val spf = taux.toDouble() / 59.7275
    private val banc: Int

    init {
        val t = rom.taulaCr + 3 * song
        banc = rom.u8(t)
        val h = rom.off(banc, rom.u16(t + 1))
        val nc = (rom.u8(h) shr 6 and 3) + 1
        var o = h
        for (k in 0 until nc) {
            val c = rom.u8(o) and 7; val ad = rom.u16(o + 1); o += 3
            if (c < 4) can[c] = GbCanal(c, banc, ad)
        }
    }
    override fun pistaActiva(i: Int) = i < 4 && can[i] != null
    // Voltes de la cançó: cada canal compta els seus bucles infinits (un canal pot repetir un patró curt moltes vegades dins del bucle de la cançó), i una volta de la cançó és quan
    // tots els canals vius han tornat a començar: el mínim (l'autor, 2026-11-12: a Lavender Town el reproductor tallava la cançó perquè comptava les voltes del primer canal que feia bucle)
    private val voltesCan = IntArray(4)
    override val voltes: Int get() { var m = Int.MAX_VALUE; for (c in can) if (c != null && c.on) m = minOf(m, voltesCan[c.idx]); return if (m == Int.MAX_VALUE) voltesCan.max() else m }
    override val acabada: Boolean get() = can.all { it == null || !it.on }

    private fun rd(c: GbCanal): Int { val v = rom.u8(rom.off(c.bank, c.addr)); c.addr = (c.addr + 1) and 0xFFFF; return v }
    private fun rd16(c: GbCanal): Int { val lo = rd(c); return lo or (rd(c) shl 8) }

    /** GetFrequency: octava i to -> valor del registre de freqüència (11 bits). */
    private fun freqNota(c: GbCanal, octave: Int, pitch: Int): Int {
        var a = ((c.trans shr 4) and 0xF) + octave
        val idx = (c.trans and 0xF) + pitch
        var v = if (idx < rom.freqCr.size) rom.freqCr[idx] else 0
        if (v and 0x8000 != 0) v -= 0x10000
        while (a < 7) { v = v shr 1; a++ }
        return v and 0x7FF
    }

    /** SetNoteDuration. */
    private fun setDur(c: GbCanal, a: Int) {
        val l = (c.nlen * (a + 1)) and 0xFFFF
        val hl = ((l and 0xFF) * c.tempo + c.nmod) and 0xFFFF
        c.nmod = hl and 0xFF; c.ndur = hl shr 8
    }

    private fun parse(c: GbCanal) {
        while (true) {
            val b = rd(c)
            if (b == 0xFF) { if (c.sub) { cmd(c, b); continue }; c.on = false; c.fRest = true; return }
            if (b >= 0xD0) { cmd(c, b); continue }
            if (c.noise) { noteNoise(c, b); return }
            setDur(c, b and 0xF)
            val p = b shr 4
            if (p == 0) { c.fRest = true; return }
            c.freq = freqNota(c, c.octave, p)
            c.fTrig = true
            loadNote(c)
            return
        }
    }

    private fun noteNoise(c: GbCanal, b: Int) {
        if (c.idx != 3) return
        setDur(c, b and 0xF)
        val p = b shr 4
        if (p == 0) return
        val ko = rom.off(rom.bancMotor, rom.drumkits[nsSet.coerceIn(0, 5)]) + 2 * p
        nsAddr = rom.u16(ko); nsDelay = 0
    }

    /** LoadNote: prepara la lliscada de to (replica el comportament de pokecrystal, inclòs l'error de la branca ascendent). */
    private fun loadNote(c: GbCanal) {
        if (!c.slide) return
        var a = c.ndur - curDur
        if (a < 0) a = 1
        curDur = a and 0xFF
        val f = c.freq; val t = c.target
        val borrow1 = if ((f and 0xFF) < (t and 0xFF)) 1 else 0
        val d1 = (((f shr 8) and 0xFF) - borrow1) and 0xFF
        val e1 = ((f and 0xFF) - (t and 0xFF)) and 0xFF
        val res = d1 - (t shr 8)
        var e: Int; var d: Int
        if (res < 0) {
            c.slideDir = true
            e = ((t and 0xFF) - (f and 0xFF)) and 0xFF
            val br = if ((t and 0xFF) < (f and 0xFF)) 1 else 0
            val d2 = (((f shr 8) and 0xFF) - br) and 0xFF
            d = ((t shr 8) - d2) and 0xFF
        } else { c.slideDir = false; e = e1; d = res and 0xFF }
        var b = 0; val dur = if (curDur == 0) 256 else curDur
        while (true) {
            b++
            e -= dur
            if (e >= 0) continue
            e = e and 0xFF
            if (d == 0) break
            d--
        }
        c.amount = b and 0xFF; c.frac = (e + dur) and 0xFF; c.f25 = 0
    }

    /** ApplyPitchSlide. */
    private fun slideStep(c: GbCanal) {
        if (!c.slide) return
        var de = c.freq; val t = c.target
        if (c.slideDir) {
            de = (de + c.amount) and 0xFFFF
            val sm = c.frac + c.f25; c.f25 = sm and 0xFF
            if (sm > 0xFF) de = (de + 1) and 0xFFFF
            if ((t shr 8) < (de shr 8) || ((t shr 8) == (de shr 8) && (t and 0xFF) < (de and 0xFF))) { c.slide = false; c.slideDir = false; return }
        } else {
            de = (de - c.amount) and 0xFFFF
            val sm = c.frac * 2; c.frac = sm and 0xFF
            if (sm > 0xFF) de = (de - 1) and 0xFFFF
            val hi = de shr 8
            if (hi < (t shr 8) || (hi == (t shr 8) && (de and 0xFF) < (t and 0xFF))) { c.slide = false; c.slideDir = false; return }
        }
        c.freq = de; c.fFreq = true; c.fDuty = true
    }

    private fun cmd(c: GbCanal, b: Int) {
        when {
            b in 0xD0..0xD7 -> c.octave = b and 7
            b == 0xD8 -> { c.nlen = rd(c); if (c.idx != 3) c.env = rd(c) }
            b == 0xD9 -> c.trans = rd(c)
            b == 0xDA -> { val hi = rd(c); val t = (hi shl 8) or rd(c); for (k in can) if (k != null) { k.tempo = t; k.nmod = 0 } }
            b == 0xDB -> c.duty = (rd(c) and 3) shl 6
            b == 0xDC -> c.env = rd(c)
            b == 0xDD -> { pSweep = rd(c); c.fSweep = true }
            b == 0xDE -> { val v = rd(c); val r = ((v shr 2) or (v shl 6)) and 0xFF; c.dpat = r; c.dutyLoop = true; c.duty = r and 0xC0 }
            b == 0xDF -> {}
            b == 0xE0 -> {
                curDur = rd(c)
                val v = rd(c)
                c.target = freqNota(c, v shr 4, v and 0xF); c.slide = true
            }
            b == 0xE1 -> {
                c.vib = true; c.vibDir = false
                val dl = rd(c); c.vdelay = dl; c.vdc = dl
                val v = rd(c); val y = v shr 4
                c.vext = ((((y shr 1) + (y and 1)) shl 4) or (y shr 1)) and 0xFF
                val z = v and 0xF; c.vrate0 = (z shl 4) or z; c.vrate = c.vrate0
            }
            b == 0xE3 -> { if (c.noise) c.noise = false else { c.noise = true; nsSet = rd(c) } }
            b == 0xE4 || b == 0xEF -> c.tracks = c.tracks0 and rd(c)      // Music_ForceStereoPanning: part del valor per defecte de la pista
            b == 0xE5 -> vol = rd(c)
            b == 0xE6 -> { val hi = rd(c); c.poff = (hi shl 8) or rd(c); c.offs = true }
            b == 0xE9 -> { val v = rd(c); val t = (c.tempo + (if (v >= 0x80) v - 256 else v)) and 0xFFFF; for (k in can) if (k != null) { k.tempo = t; k.nmod = 0 } }
            b == 0xFA -> c.cond = rd(c)
            b == 0xFB -> { val x = rd(c); val ad = rd16(c); if (x == c.cond) c.addr = ad }
            b == 0xFC -> c.addr = rd16(c)
            b == 0xFD -> {
                val n = rd(c)
                if (!c.looping) {
                    if (n == 0) { voltesCan[c.idx]++; c.addr = rd16(c); return }
                    c.looping = true; c.loopCount = n - 1
                }
                if (c.loopCount == 0) { c.looping = false; c.addr = (c.addr + 2) and 0xFFFF; return }
                c.loopCount--
                c.addr = rd16(c)
            }
            b == 0xFE -> { val ad = rd16(c); c.last = c.addr; c.addr = ad; c.sub = true }
            b == 0xFF -> { c.sub = false; c.addr = c.last }
            else -> { c.on = false; c.fRest = true }   // ordre que cap cançó fa servir
        }
    }

    // ---- Un fotograma (_UpdateSound) ----
    private fun frame() {
        var sortida = 0
        for (c in can) {
            if (c == null || !c.on) continue
            c.fRest = false; c.fTrig = false; c.fFreq = false; c.fDuty = false; c.fVib = false; c.fSweep = false
            if (c.ndur >= 2) c.ndur-- else { c.vdc = c.vdelay; c.slide = false; parse(c) }
            slideStep(c)
            var duty = c.duty; var freq = c.freq; var env = c.env
            // HandleTrackVibrato
            if (c.dutyLoop) { c.dpat = ((c.dpat shl 2) or (c.dpat shr 6)) and 0xFF; duty = c.dpat and 0xC0; c.fDuty = true }
            if (c.offs) freq = (freq + c.poff) and 0xFFFF
            if (c.vib) {
                if (c.vdc != 0) c.vdc--
                else if (c.vext != 0) {
                    if ((c.vrate and 0xF) != 0) c.vrate--
                    else {
                        c.vrate = c.vrate0
                        var e = freq and 0xFF
                        if (c.vibDir) { c.vibDir = false; e -= (c.vext and 0xF); if (e < 0) e = 0 }
                        else { c.vibDir = true; e += (c.vext shr 4); if (e > 0xFF) e = 0xFF }
                        freq = (freq and 0xFF00) or e; c.fVib = true
                    }
                }
            }
            // HandleNoise
            if (c.noise && nsAddr != 0) {
                if (nsDelay != 0) nsDelay--
                else {
                    val o = rom.off(rom.bancMotor, nsAddr)
                    val a = rom.u8(o)
                    if (a != 0xFF) {
                        nsDelay = (a and 0xF) + 1; env = rom.u8(o + 1); freq = rom.u8(o + 2); nsAddr += 3; c.fTrig = true
                    }
                }
            }
            // UpdateChannels
            val ch = c.idx + 1
            if (c.fSweep && ch == 1) gb.nr10(pSweep)
            when (ch) {
                1 -> when {
                    c.fRest -> gb.oscOff(1)
                    c.fTrig -> { gb.setDuty(1, duty); gb.nr2[1] = env; gb.nrx3(1, freq and 255); gb.nrx4(1, ((freq shr 8) and 7) or 0x80) }
                    c.fFreq -> { gb.nrx3(1, freq and 255); gb.nrx4(1, (freq shr 8) and 7); if (c.fDuty) gb.setDuty(1, duty) }
                    c.fVib -> { gb.setDuty(1, duty); gb.nrx3(1, freq and 255) }
                    c.fDuty -> gb.setDuty(1, duty)
                }
                2 -> when {
                    c.fRest -> gb.oscOff(2)
                    c.fTrig -> { gb.setDuty(2, duty); gb.nr2[2] = env; gb.nrx3(2, freq and 255); gb.nrx4(2, ((freq shr 8) and 7) or 0x80) }
                    c.fVib -> { gb.setDuty(2, duty); gb.nrx3(2, freq and 255) }
                    c.fDuty -> gb.setDuty(2, duty)
                }
                3 -> when {
                    c.fRest -> gb.oscOff(3)
                    c.fTrig -> {
                        gb.nr30 = 0
                        val wo = rom.waveOff + 16 * (env and 0xF)
                        val nib = IntArray(32) { val b = rom.u8(wo + it / 2); if (it % 2 == 0) b shr 4 else b and 15 }
                        gb.setWave(nib)
                        gb.nr2[3] = ((env and 0xF0) shl 1) and 0xFF
                        gb.nr30 = 0x80
                        gb.nrx3(3, freq and 255); gb.nrx4(3, ((freq shr 8) and 7) or 0x80)
                    }
                    c.fVib -> gb.nrx3(3, freq and 255)
                }
                4 -> when {
                    c.fRest -> gb.oscOff(4)
                    c.fTrig -> { gb.nr2[4] = env; gb.nr43(freq and 255); gb.nrx4(4, 0x80) }
                }
            }
            sortida = sortida or c.tracks
        }
        gb.nr51 = sortida
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
            val vl = (((vol shr 4) and 7) + 1) / 8f; val vr = ((vol and 7) + 1) / 8f
            for (i in 0 until n) {
                var l = 0f; var r = 0f
                for (ch in 1..4) {
                    val o = gb.out(ch, taux).toFloat(); if (osc) oscV[ch - 1] = o * 0.5f * tg[ch - 1] + 0.5f
                    val v = o * 0.25f * tg[ch - 1]    // mesclador de la consola: suma dels 4 canals / 4
                    if (vis) niv[ch - 1] = maxOf(gb.nivell(ch) * tg[ch - 1], niv[ch - 1] * 0.9993f)
                    if (gb.nr51 and (0x10 shl (ch - 1)) != 0) l += v * vl
                    if (gb.nr51 and (0x01 shl (ch - 1)) != 0) r += v * vr
                }
                gb.tick()
                if (osc && ++oscN >= 4) { oscN = 0; oscil.escriu(oscV) }
                l = hpL.f(l.toDouble()).toFloat(); r = hpR.f(r.toDouble()).toFloat()      // condensador de sortida
                out[(f + i) * 2] = SatGb.s(l)
                out[(f + i) * 2 + 1] = SatGb.s(r)
            }
            restaFot -= n
            f += n
        }
    }
}

/** Un canal del motor de Groc (`wChannel*` de pokeyellow `audio/engine_1.asm`). */
class GrocCanal(val idx: Int, var addr: Int) {
    var viu = true; var tornada = 0; var cridat = false; var bucle = 1
    var duty = 0; var dpat = 0; var rota = false
    var vibRetard = 0; var vibRecarrega = 0; var vibExt = 0; var vibRata = 0
    var freqLo = 0
    var lliscar = false; var lliscarBaixa = false; var lliscarLong = 0; var lliscarPas = 0; var lliscarPasFrac = 0; var lliscarActFrac = 0
    var lliscarActHi = 0; var lliscarActLo = 0; var lliscarObjHi = 0; var lliscarObjLo = 0
    var retard = 1; var frac = 0; var velocitat = 1; var octava = 0; var vol = 0; var perfecte = false; var vibDir = false
}

class SinteGroc(private val rom: RomGb, private val banc: Int, capcalera: Int, nCanals: Int, private val muts: BooleanArray, solo0: Int = -1) : Sintetitzador {
    override val taux = RadioFreq.taux
    @Volatile override var solo = solo0
    private val gb = M4aGb(false, taux)
    private val hpL = DcBlock(taux); private val hpR = DcBlock(taux)
    private val can = arrayOfNulls<GrocCanal>(4)
    private val base = banc * 0x4000
    private val ondes = rom.ones0.firstOrNull() ?: 0   // el motor (i les ones) és al banc 2; els altres bancs només tenen dades
    private var tempo = 0x100; private var pan = 0xFF; private var ondaMusica = 0; private var volum = 0x77
    private val tg = FloatArray(4) { 1f }
    private val niv = FloatArray(4)
    override val nivells: FloatArray? get() = niv
    /** Oscil·loscopi per canal (P1, P2, ona i soroll): mostres de la sortida de cada canal de l'APU, a ~8 kHz. */
    override val oscil = OscDades(listOf("P1", "P2", "WV", "NO"))
    override fun notesMidi(out: FloatArray): Boolean { gb.notesMidi(out, 0); return true }          // notes exactes dels registres
    private val oscV = FloatArray(4); private var oscN = 0
    private var restaFot = 0.0
    private val spf = taux.toDouble() / 59.7275
    // bateria (canal 8 d'efectes): punter a les dades d'un instrument de soroll i el seu retard
    private var drum = 0; private var drumRetard = 1

    init {
        for (k in 0 until nCanals) {
            val o = capcalera + 3 * k
            val c = rom.u8(o) and 0xF
            if (c < 4) can[c] = GrocCanal(c, rom.u16(o + 1))
        }
        gb.nr51 = 0
    }
    override fun pistaActiva(i: Int) = i < 4 && can[i] != null
    // Voltes de la cançó: el mínim dels bucles infinits dels canals vius (vegeu SinteCristall); Lavender Town tenia un canal amb un patró curt que feia pujar el compte massa de pressa
    private val voltesCan = IntArray(4)
    override val voltes: Int get() { var m = Int.MAX_VALUE; for (c in can) if (c != null && c.viu) m = minOf(m, voltesCan[c.idx]); return if (m == Int.MAX_VALUE) voltesCan.max() else m }
    override val acabada: Boolean get() = can.all { it == null || !it.viu }

    private fun rd(c: GrocCanal): Int { val v = rom.u8(base + (c.addr and 0x3FFF)); c.addr = (c.addr + 1) and 0xFFFF; return v }

    /** Audio1_CalculateFrequency: nota (0-11) i octava (0-7) -> (d, e). */
    private fun calcFreq(nota: Int, oct: Int): Int {
        var v = rom.tons[nota.coerceIn(0, 11)]
        if (v and 0x8000 != 0) v -= 0x10000
        var a = oct
        while (a != 7) { v = v shr 1; a++ }
        val d = ((v shr 8) + 8) and 0xFF
        return (d shl 8) or (v and 0xFF)
    }

    private fun mulAdd(a0: Int, de: Int, l: Int): Int {
        var a = a0; var hl = l; var d = de
        while (true) {
            if (a and 1 != 0) hl = (hl + d) and 0xFFFF
            a = a shr 1
            d = (d shl 1) and 0xFFFF
            if (a == 0) break
        }
        return hl
    }

    private fun activaSortida(c: GrocCanal) {
        // EnableChannelOutput: els bits del canal segons la panoràmica (mono + màscara)
        val ch = c.idx
        val esq = 0x10 shl ch; val dre = 0x01 shl ch
        val mascara = esq or dre
        gb.nr51 = (gb.nr51 and mascara.inv()) or (pan and mascara)
    }

    private fun longitudNota(c: GrocCanal, d: Int, esTempoFix: Boolean = false) {
        val len = (d and 0xF) + 1
        val p = mulAdd(c.velocitat, len, 0)
        val t = if (esTempoFix) 0x100 else tempo
        val hl = mulAdd(p and 0xFF, t, c.frac)
        c.frac = hl and 0xFF
        c.retard = (hl shr 8) and 0xFF
    }

    private fun aplicaOnaIFreq(c: GrocCanal, de: Int) {
        val ch = c.idx + 1
        if (ch == 3) {
            val wo = ondes + 16 * ondaMusica.coerceIn(0, 5)
            val nib = IntArray(32) { val b = rom.u8(wo + it / 2); if (it % 2 == 0) b shr 4 else b and 15 }
            gb.nr30 = 0; gb.setWave(nib); gb.nr30 = 0x80
        }
        gb.nrx3(ch, de and 0xFF)
        gb.nrx4(ch, (((de shr 8) or 0x80) and 0xC7))
    }

    private fun iniciaLliscada(c: GrocCanal, de0: Int) {
        // InitPitchSlideVars
        c.lliscarActHi = de0 shr 8; c.lliscarActLo = de0 and 0xFF
        var a = c.retard - c.lliscarLong
        if (a < 0) a = 1
        c.lliscarLong = a and 0xFF
        val eLo = ((de0 and 0xFF) - c.lliscarObjLo) and 0xFF
        val borrow = if ((de0 and 0xFF) < c.lliscarObjLo) 1 else 0
        val r = (((de0 shr 8) and 0xFF) - borrow) and 0xFF
        val res = r - c.lliscarObjHi
        var e: Int; var d: Int
        if (res < 0) {
            // objectiu més gran: diferència (amb l'error de pokered: el préstec es resta de l'alt actual)
            d = c.lliscarActHi; e = c.lliscarActLo
            val e2 = (c.lliscarObjLo - e) and 0xFF
            val br = if (c.lliscarObjLo < e) 1 else 0
            val d2 = (d - br) and 0xFF
            e = e2; d = (c.lliscarObjHi - d2) and 0xFF
            c.lliscarBaixa = false
        } else { d = res and 0xFF; e = eLo; c.lliscarBaixa = true }
        var b = 0; val dur = if (c.lliscarLong == 0) 256 else c.lliscarLong
        while (true) {
            b++
            e -= dur
            if (e >= 0) continue
            e = e and 0xFF
            if (d == 0) break
            d--
        }
        val resta = (e + dur) and 0xFF
        c.lliscarPas = b and 0xFF; c.lliscarPasFrac = resta; c.lliscarActFrac = resta
    }

    private fun passaLliscada(c: GrocCanal) {
        var de = (c.lliscarActHi shl 8) or c.lliscarActLo
        val tHi = c.lliscarObjHi; val tLo = c.lliscarObjLo
        if (!c.lliscarBaixa) {
            de = (de + c.lliscarPas) and 0xFFFF
            val sm = c.lliscarPasFrac + c.lliscarActFrac; c.lliscarActFrac = sm and 0xFF
            if (sm > 0xFF) de = (de + 1) and 0xFFFF
            if (tHi < (de shr 8) || (tHi == (de shr 8) && tLo < (de and 0xFF))) { c.lliscar = false; c.lliscarBaixa = false; return }
        } else {
            de = (de - c.lliscarPas) and 0xFFFF
            val sm = c.lliscarPasFrac * 2; c.lliscarPasFrac = sm and 0xFF
            if (sm > 0xFF) de = (de - 1) and 0xFFFF
            val hi = de shr 8
            if (hi < tHi || (hi == tHi && (de and 0xFF) < tLo)) { c.lliscar = false; c.lliscarBaixa = false; return }
        }
        c.lliscarActHi = de shr 8; c.lliscarActLo = de and 0xFF
        val ch = c.idx + 1
        gb.nrx3(ch, de and 0xFF); gb.nrx4(ch, (de shr 8) and 7)
    }

    private fun tocaBateria(inst: Int) {
        if (drum != 0) return                       // un instrument en curs: es perd el nou (Audio1_PlaySound)
        val h = base + 3 * inst
        if (inst <= 0 || (rom.u8(h) and 0xF) != 7) return
        drum = rom.u16(h + 1); drumRetard = 1
    }

    private fun bateriaFotograma() {
        if (drum == 0) return
        if (drumRetard == 1) {
            val b = rom.u8(base + (drum and 0x3FFF))
            if (b == 0xFF) { drum = 0; return }
            val env = rom.u8(base + ((drum + 1) and 0x3FFF)); val freq = rom.u8(base + ((drum + 2) and 0x3FFF))
            drum += 3
            drumRetard = (b and 0xF) + 1
            gb.nr2[4] = env; gb.nr43(freq); gb.nrx4(4, 0x80)
            gb.nr51 = (gb.nr51 and 0x88.inv()) or (pan and 0x88)
        } else drumRetard--
    }

    /** Audio1_sound_ret: executa ordres fins arribar a una nota. */
    private fun seguent(c: GrocCanal) {
        c.vibRetard = c.vibRecarrega; c.lliscar = false; c.lliscarBaixa = false
        var guarda = 0
        while (c.viu) {
            if (++guarda > 2000) { c.viu = false; return }
            val d = rd(c)
            when {
                d == 0xFF -> {
                    if (c.cridat) { c.cridat = false; c.addr = c.tornada; continue }
                    c.viu = false
                    if (c.idx < 3) gb.nr51 = gb.nr51 and (0x11 shl c.idx).inv()
                    return
                }
                d == 0xFD -> { val lo = rd(c); val hi = rd(c); c.tornada = c.addr; c.addr = (hi shl 8) or lo; c.cridat = true }
                d == 0xFE -> {
                    val n = rd(c)
                    if (n == 0) { voltesCan[c.idx]++; val lo = rd(c); val hi = rd(c); c.addr = (hi shl 8) or lo }
                    else if (c.bucle != n) { c.bucle++; val lo = rd(c); val hi = rd(c); c.addr = (hi shl 8) or lo }
                    else { c.bucle = 1; rd(c); rd(c) }
                }
                d and 0xF0 == 0xD0 -> {
                    c.velocitat = d and 0xF
                    if (c.idx != 3) {
                        var p = rd(c)
                        if (c.idx == 2) { ondaMusica = p and 0xF; p = (p and 0x30) shl 1 }
                        c.vol = p and 0xFF
                    }
                }
                d == 0xE8 -> c.perfecte = !c.perfecte
                d == 0xEA -> {
                    val r = rd(c); c.vibRetard = r; c.vibRecarrega = r
                    val v = rd(c); val y = v shr 4
                    c.vibExt = ((((y shr 1) + (y and 1)) shl 4) or (y shr 1)) and 0xFF
                    val z = v and 0xF; c.vibRata = (z shl 4) or z
                }
                d == 0xEB -> {
                    c.lliscarLong = rd(c)
                    val v = rd(c)
                    val fr = calcFreq(v and 0xF, v shr 4)
                    c.lliscarObjHi = fr shr 8; c.lliscarObjLo = fr and 0xFF
                    c.lliscar = true
                    val nd = rd(c)
                    nota(c, nd); return
                }
                d == 0xEC -> c.duty = (rd(c) and 3) shl 6
                d == 0xED -> {
                    val hi = rd(c); tempo = (hi shl 8) or rd(c)
                    for (k in can) if (k != null) k.frac = 0
                }
                d == 0xEE -> pan = rd(c)
                d == 0xEF -> rd(c)
                d == 0xFC -> { val p = rd(c); c.dpat = p; c.duty = p and 0xC0; c.rota = true }
                d == 0xF0 -> volum = rd(c)
                d == 0xF8 -> {}
                d and 0xF0 == 0xE0 -> c.octava = d and 0xF
                else -> { nota(c, d); return }
            }
        }
    }

    /** Audio1_note: nota normal, silenci o bateria (al canal de soroll). */
    private fun nota(c: GrocCanal, d: Int) {
        if (c.idx == 3) {
            val hi = d and 0xF0
            var inst = -1; var dl = d
            if (hi == 0xB0) { inst = rd(c); dl = d and 0xF }
            else if (hi < 0xB0) { inst = hi shr 4; dl = d and 0xF }
            if (inst > 0) tocaBateria(inst)
            longitudNota(c, dl)
            return
        }
        longitudNota(c, d)
        val hi = d and 0xF0
        val ch = c.idx + 1
        if (hi == 0xC0) {
            if (ch == 3) gb.nr51 = gb.nr51 and 0x44.inv() else gb.oscOff(ch)
            return
        }
        var de = calcFreq(hi shr 4, c.octava)
        if (c.lliscar) iniciaLliscada(c, de)
        if (ch == 3) gb.nr2[3] = c.vol and 0xFF else gb.nr2[ch] = c.vol and 0xFF
        if (ch != 3) gb.setDuty(ch, c.duty)
        activaSortida(c)
        if (c.perfecte) de = (de and 0xFF00) or (((de and 0xFF) + 1) and 0xFF)
        c.freqLo = de and 0xFF
        aplicaOnaIFreq(c, de)
    }

    private fun fotogramaCanal(c: GrocCanal) {
        if (c.retard == 1) { seguent(c); return }
        c.retard = (c.retard - 1) and 0xFF
        val ch = c.idx + 1
        if (c.rota) {
            c.dpat = ((c.dpat shl 2) or (c.dpat shr 6)) and 0xFF
            if (ch != 3) gb.setDuty(ch, c.dpat and 0xC0)
        }
        if (c.idx == 3) return
        if (c.lliscar) { passaLliscada(c); return }
        if (c.vibRetard != 0) { c.vibRetard--; return }
        if (c.vibExt == 0) return
        if ((c.vibRata and 0xF) != 0) { c.vibRata--; return }
        c.vibRata = ((c.vibRata and 0xF) shl 4) or (c.vibRata shr 4) or c.vibRata
        var e = c.freqLo
        if (c.vibDir) { c.vibDir = false; e -= (c.vibExt and 0xF); if (e < 0) e = 0 }
        else { c.vibDir = true; e += (c.vibExt shr 4); if (e > 0xFF) e = 0xFF }
        gb.nrx3(ch, e and 0xFF)
    }

    private fun frame() {
        for (c in can) if (c != null && c.viu) fotogramaCanal(c)
        bateriaFotograma()
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
            val vl = (((volum shr 4) and 7) + 1) / 8f; val vr = ((volum and 7) + 1) / 8f
            for (i in 0 until n) {
                var l = 0f; var r = 0f
                for (ch in 1..4) {
                    val o = gb.out(ch, taux).toFloat(); if (osc) oscV[ch - 1] = o * 0.5f * tg[ch - 1] + 0.5f
                    val v = o * 0.25f * tg[ch - 1]    // mesclador de la consola: suma dels 4 canals / 4
                    if (vis) niv[ch - 1] = maxOf(gb.nivell(ch) * tg[ch - 1], niv[ch - 1] * 0.9993f)
                    if (gb.nr51 and (0x10 shl (ch - 1)) != 0) l += v * vl
                    if (gb.nr51 and (0x01 shl (ch - 1)) != 0) r += v * vr
                }
                gb.tick()
                if (osc && ++oscN >= 4) { oscN = 0; oscil.escriu(oscV) }
                l = hpL.f(l.toDouble()).toFloat(); r = hpR.f(r.toDouble()).toFloat()      // condensador de sortida
                out[(f + i) * 2] = SatGb.s(l)
                out[(f + i) * 2 + 1] = SatGb.s(r)
            }
            restaFot -= n
            f += n
        }
    }
}
//ANDROID
