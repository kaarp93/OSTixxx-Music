package cat.gbdsmixxx

/**
 * Fitxer .gbs (Game Boy Sound): capçalera de 0x70 bytes (sig «GBS», versió, nombre de cançons, primera cançó, adreça de càrrega, d'init i de play, punter de pila, TMA,
 * TAC, títol, autor i copyright de 32 bytes cada un) seguida del codi i de les dades del driver, que es carreguen a l'adreça de càrrega. S'executa amb la CPU `GbCpu`:
 * `init` amb A = número de cançó (0..n-1) i `play` a cada fotograma (vblank, o el ritme del temporitzador si TAC el porta en marxa).
 */
class GbsArxiu(private val d: ByteArray) {
    private fun u8(o: Int) = d[o].toInt() and 255
    private fun u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
    private fun text(o: Int) = String(d, o, 32, Charsets.ISO_8859_1).substringBefore('\u0000').trim()
    val valid: Boolean = d.size > 0x70 && d[0] == 'G'.code.toByte() && d[1] == 'B'.code.toByte() && d[2] == 'S'.code.toByte() && u8(3) == 1
    val nCancons get() = u8(4)
    val primera get() = u8(5)
    val carrega get() = u16(6); val init get() = u16(8); val play get() = u16(10); val sp get() = u16(12)
    val tma get() = u8(14); val tac get() = u8(15)
    val titol get() = text(0x10); val autor get() = text(0x30); val copyright get() = text(0x50)
    /** Fotogrames de so per segon: el vblank, o el ritme del temporitzador si TAC el porta en marxa (bit 2); bit 7 = velocitat doble. */
    val fps: Double get() {
        if (tac and 4 == 0) return 59.7275
        val f = doubleArrayOf(4096.0, 262144.0, 65536.0, 16384.0)[tac and 3] * (if (tac and 0x80 != 0) 2.0 else 1.0)
        return f / (256 - tma).coerceAtLeast(1)
    }
    /** Imatge de la memòria de ROM: les dades al seu lloc (adreça de càrrega), amb marge perquè les lectures de banc no surtin. */
    val imatge: ByteArray by lazy {
        val dades = d.size - 0x70
        val img = ByteArray(((carrega + dades + 0x3FFF) / 0x4000) * 0x4000 + 0x4000)
        System.arraycopy(d, 0x70, img, carrega, dades)
        img
    }
}

/** Driver d'un fitxer .gbs: la CPU corre el seu `init` i el seu `play`. Els números de cançó van d'1 a `nCancons` (a `init` es passa el número menys 1). */
class DriverGbs(private val g: GbsArxiu) : GbDriver {
    override val nom = "GBS"
    override val ids = 1..maxOf(1, g.nCancons)
    override val fps = g.fps
    override val mbc5: Boolean? = true
    override fun reconeix(rom: ByteArray) = g.valid
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = g.sp; cpu.a = id - 1; m.banc = 1
        cpu.crida(g.init)
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.sp = g.sp; cpu.crida(g.play) }
}
