package cat.gbdsmixxx

/** Una rutina que el driver automàtic de GB crida: adreça, banc de ROM i de WRAM de la crida original i els registres a b c d e h l. */
/** `sp`: pila amb què es crida (la del joc; per defecte $FFFE, com abans). */
class RutinaGb(val dest: Int, val banc: Int, val svbk: Int, val regs: IntArray, val sp: Int = 0xFFFE)

/** Últim CRC calculat, per fil (abans era una sola variable global compartida per tots els fils: l'analitzador i la biblioteca es podien trepitjar). No reté la ROM (referència feble). */
private class CauCrc(val rom: java.lang.ref.WeakReference<ByteArray>, val crc: Long)
private val cauCrc = ThreadLocal<CauCrc?>()
/** CRC32 del fitxer sencer (identitat de la ROM: el mateix que fa servir libretro). Fil-segur. */
internal fun crcDe(rom: ByteArray): Long {
    val c = cauCrc.get()
    if (c != null && c.rom.get() === rom) return c.crc
    val v = java.util.zip.CRC32().also { it.update(rom) }.value
    cauCrc.set(CauCrc(java.lang.ref.WeakReference(rom), v))
    return v
}

/**
 * Driver de Game Boy guiat per dades, generat per l'analitzador (`AnalitzadorGb`, línia `AUTO_GB`): es reconeix pel CRC32 i fa el mateix que la prova en fred de l'analitzador:
 * inicialització opcional, engega-cançó (la cançó al registre `reg`: 0 = A, 1 = B, 2 = C, 3 = D, 4 = E, 5 = L; o bé s'escriu a la RAM `ramAd`) i, a cada fotograma, el motor, tots amb els
 * bancs i registres de la crida original. No s'ha comprovat amb l'oïda (`provat = false`).
 */
class DriverGbAuto(override val nom: String, val titol: String, val crc: Long, val mbc5Cart: Boolean, val f: RutinaGb, val ini: RutinaGb?, val p: RutinaGb?, val reg: Int,
                   val ramAd: Int, val codis: IntArray, val noms: List<String>) : GbDriver {
    override val ids = 1..codis.size
    override val mbc5: Boolean? get() = mbc5Cart
    override fun reconeix(rom: ByteArray) = rom.size >= 0x150 && crcDe(rom) == crc
    private fun crida(cpu: GbCpu, m: GbMem, r: RutinaGb, codi: Int = -1, k: Int = 0) {
        m.posaBancs(r.banc, r.svbk)
        val g = r.regs
        cpu.a = g[0]; cpu.b = g[1]; cpu.c = g[2]; cpu.d = g[3]; cpu.e = g[4]; cpu.h = g[5]; cpu.l = g[6]
        if (codi >= 0) when (k) { 0 -> cpu.a = codi; 1 -> cpu.b = codi; 2 -> cpu.c = codi; 3 -> cpu.d = codi; 4 -> cpu.e = codi; else -> cpu.l = codi }
        cpu.sp = 0xFFFE; cpu.crida(r.dest, 200000)
    }
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        if (ini != null) crida(cpu, m, ini)
        val codi = codis[(id - 1).coerceIn(0, codis.size - 1)]
        if (p != null) crida(cpu, m, p, codi, reg) else m.wb(ramAd, codi)
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { crida(cpu, m, f) }
}
