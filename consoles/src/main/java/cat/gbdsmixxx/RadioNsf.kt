package cat.gbdsmixxx

/**
 * Fitxer .nsf (NES Sound Format): capçalera de 0x80 bytes («NESM», 0x1A, versió, nombre de cançons, primera, adreces de càrrega, d'init i de play, títol, autor i copyright,
 * velocitat de play en µs, els 8 bancs inicials de 4 KB, bits PAL i xips d'expansió) i les dades del driver. S'executa amb la CPU 6502 (`Cpu6502`) i l'APU `NesApu`.
 * El so de la 2A03 i del VRC6 (si la capçalera el declara); els altres xips d'expansió (FDS, MMC5, N163, etc.) no sonen.
 */
class NsfArxiu(private val d: ByteArray) {
    private fun u8(o: Int) = d[o].toInt() and 255
    private fun u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
    private fun text(o: Int) = String(d, o, 32, Charsets.ISO_8859_1).substringBefore('\u0000').trim()
    val valid = d.size > 0x80 && d[0] == 'N'.code.toByte() && d[1] == 'E'.code.toByte() && d[2] == 'S'.code.toByte() && d[3] == 'M'.code.toByte() && u8(4) == 0x1A
    val nCancons get() = u8(6); val primera get() = u8(7)
    val carrega get() = u16(8); val init get() = u16(0xA); val play get() = u16(0xC)
    val titol get() = text(0xE); val autor get() = text(0x2E); val copyright get() = text(0x4E)
    val usaBancs: Boolean get() = (0x70 until 0x78).any { u8(it) != 0 }
    val xips get() = u8(0x7B)
    /** Ritme de play: NTSC a $6E, PAL a $78 (µs per crida; nesdev.org/wiki/NSF). Amb la regió PAL (`RegioVideo`) es fa servir el de PAL. */
    val fps: Double get() { val pal = RegioVideo.pal; val us = u16(if (pal) 0x78 else 0x6E); return if (us in 1000..100000) 1_000_000.0 / us else RegioVideo.fpsNes() }
    fun banc0(i: Int) = u8(0x70 + i)
    val dades: ByteArray get() = d.copyOfRange(0x80, d.size)
}

/** Memòria de la NES per a un NSF: RAM de 2 KB (reflectida), RAM de 8 KB a $6000, PRG a $8000-$FFFF (amb canvi de bancs de 4 KB a $5FF8-$5FFF) i els registres de l'APU. */
class NesMem(private val n: NsfArxiu, val apu: () -> NesApu?) : Bus6502 {
    val ram = IntArray(0x800); val sram = IntArray(0x2000)
    private val banc = IntArray(8) { n.banc0(it) }
    private val imatge: ByteArray
    init {
        val dades = n.dades
        if (n.usaBancs) { val pad = n.carrega and 0xFFF; imatge = ByteArray(pad + dades.size); System.arraycopy(dades, 0, imatge, pad, dades.size) }
        else { imatge = ByteArray(0x8000); val o = n.carrega - 0x8000; if (o >= 0) System.arraycopy(dades, 0, imatge, o, minOf(dades.size, 0x8000 - o)) }
    }
    override fun rb(a: Int): Int = when {
        a < 0x2000 -> ram[a and 0x7FF]
        a == 0x4015 -> apu()?.estat() ?: 0
        a < 0x6000 -> 0
        a < 0x8000 -> sram[a - 0x6000]
        else -> {
            val o = if (n.usaBancs) banc[(a - 0x8000) shr 12] * 0x1000 + (a and 0xFFF) else a - 0x8000
            if (o in imatge.indices) imatge[o].toInt() and 255 else 0
        }
    }
    override fun wb(a: Int, v: Int) {
        when {
            a < 0x2000 -> ram[a and 0x7FF] = v
            a in 0x4000..0x4017 && a != 0x4014 && a != 0x4016 -> apu()?.escriu(a, v)
            a in 0x5FF8..0x5FFF -> banc[a - 0x5FF8] = v and 255
            // VRC6 (bit 0 dels xips d'expansió, capçalera $7B): $9000-$9003, $A000-$A002, $B000-$B002 (nesdev.org/wiki/NSF, «Expansion audio»; VRC6_audio). Abans no sonava.
            a in 0x9000..0xBFFF && n.xips and 1 != 0 -> apu()?.escriuVrc6(a and 0xF003, v and 255)
            a in 0x6000..0x7FFF -> sram[a - 0x6000] = v
        }
    }
}

class SinteNsf(private val nsf: NsfArxiu, id: Int, private val muts: BooleanArray, solo0: Int = -1) : Sintetitzador {
    override val taux = RadioFreq.taux
    @Volatile override var solo = solo0
    private var apu: NesApu? = null
    private val mem = NesMem(nsf) { apu }
    private val cpu = Cpu6502(mem)
    private val sortida = SortidaNes(taux)
    private val spf = taux.toDouble() / nsf.fps
    private var restaFot = 0.0
    private var fots = 0L
    private var ultimaNota = 0L
    private var notaVista = 0
    private val mut = BooleanArray(5)
    override val nivells: FloatArray? get() = apu?.nivells

    init {
        val ap = NesApu(taux) { mem.rb(it) }; apu = ap
        for (r in 0x4000..0x4013) ap.escriu(r, 0)
        ap.escriu(0x4015, 0); ap.escriu(0x4015, 0x0F); ap.escriu(0x4017, 0x40)
        java.util.Arrays.fill(ap.actiu, false); ap.nota = 0
        cpu.sp = 0xFD; cpu.a = (id - 1) and 255; cpu.x = if (RegioVideo.pal) 1 else 0          // X: 0 = NTSC, 1 = PAL (nesdev.org/wiki/NSF, INIT)
        cpu.crida(nsf.init)
    }

    override fun pistaActiva(i: Int) = i < 5 && apu?.actiu?.get(i) == true
    override val voltes: Int get() = 0
    override val teBucle: Boolean? get() = true
    override val acabada: Boolean get() = fots > 180 && fots - ultimaNota > 600

    override fun renderitza(out: ShortArray, frames: Int) {
        val ap = apu ?: return
        var f = 0
        while (f < frames) {
            if (restaFot <= 0.0) {
                cpu.sp = 0xFD; cpu.crida(nsf.play); fots++; restaFot += spf
                if (ap.nota != notaVista) { notaVista = ap.nota; ultimaNota = fots }
            }
            val n = minOf(frames - f, Math.ceil(restaFot).toInt().coerceAtLeast(1))
            for (i in 0 until n) {
                for (k in 0 until 5) mut[k] = muts[k] || (solo >= 0 && solo != k)
                val s = sortida.f(ap.mostra(mut))
                out[(f + i) * 2] = s; out[(f + i) * 2 + 1] = s
            }
            restaFot -= n
            f += n
        }
    }
}
