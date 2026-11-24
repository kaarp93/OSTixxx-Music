package cat.gbdsmixxx

/**
 * Driver de Game Boy per escriptures a la RAM (l'autor, 2026-11-15: «intercepta tota la RAM»). Es troba mirant la RAM sencera de l'execució real: l'estat a l'últim fotograma abans de la música, mes
 * les escriptures que el joc fa en demanar-la, i després es va treient tot el que no fa falta fins que només queden els bytes imprescindibles perquè el motor sol faci exactament les mateixes
 * escriptures a l'APU. Així no cal saber quina rutina demana la cançó ni com: `base` són les escriptures que deixa la inicialització del joc i `cancons[i]` les de la cançó `i` (adreça, valor, adreça, valor...).
 * El motor (`f`) es crida a cada fotograma.
 */
class DriverGbPoke(override val nom: String, val titol: String, val crc: Long, val mbc5Cart: Boolean, val f: RutinaGb, val base: IntArray, val cancons: List<IntArray>, val noms: List<String>, val start: RutinaGb? = null) : GbDriver {
    override val ids = 1..maxOf(1, cancons.size)
    override val mbc5: Boolean? get() = mbc5Cart
    override fun reconeix(rom: ByteArray) = rom.size >= 0x150 && crcDe(rom) == crc
    /** Adreça < $8000: índex de la WRAM (32 KB, tots els bancs); $8000-$FFFF: adreça de CPU (`wb`); >= $10000: índex de la HRAM + $10000; >= $20000: índex de la RAM del cartutx + $20000. */
    private fun escriu(m: GbMem, l: IntArray) {
        var i = 0
        while (i + 1 < l.size) { val a = l[i]; val v = l[i + 1]; if (a == -2) copiaOff = v else if (a == -3) copiaLen = v else if (a < 0) { if (copiaLen > 0) { System.arraycopy(m.rom, copiaOff, m.xram, 0, minOf(copiaLen, 0x2000, m.rom.size - copiaOff)); copiaLen = 0 }; arrenca(m, v) } else if (a >= 0x20000) m.xram[a - 0x20000] = v.toByte() else if (a >= 0x10000) m.hram[a - 0x10000] = v.toByte() else if (a < 0x8000) m.wram[a] = v.toByte() else m.wb(a, v); i += 2 }
    }
    /** Cançó demanada per número: es crida la rutina d'arrencada amb A = número (marcador `(-1, n)`). */
    private var cpuA: GbCpu? = null
    /** Cançons que viuen a la RAM del cartutx (DKL): abans d'arrencar, es copia `copiaLen` bytes de la ROM (des de `copiaOff`) a $A000. */
    private var copiaOff = 0; private var copiaLen = 0
    private fun arrenca(m: GbMem, n: Int) {
        val s = start ?: return; val c = cpuA ?: return; m.posaBancs(s.banc, s.svbk); val g = s.regs
        c.a = n; c.b = g[1]; c.c = g[2]; c.d = g[3]; c.e = g[4]; c.h = g[5]; c.l = g[6]; c.sp = if (s.sp != 0xFFFE) s.sp else f.sp; c.crida(s.dest, 200000)
    }
    /** L'APU s'encén abans (NR52 bit 7, volum general NR50 i pannejat NR51): no són RAM i el pipeline no els captura; sense això el motor escriu als registres però no se sent res. */
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpuA = cpu; m.wb(0xFF26, 0x80); m.wb(0xFF25, 0xFF); m.wb(0xFF24, 0x77)
        escriu(m, base); escriu(m, cancons[(id - 1).coerceIn(0, cancons.size - 1)])
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) {
        m.posaBancs(f.banc, f.svbk); val g = f.regs
        cpu.a = g[0]; cpu.b = g[1]; cpu.c = g[2]; cpu.d = g[3]; cpu.e = g[4]; cpu.h = g[5]; cpu.l = g[6]
        cpu.sp = f.sp; cpu.crida(f.dest, 200000)
    }
}
