package cat.gbdsmixxx

/*
 * Lot G (Game Boy / Game Boy Color) d'OSTixxx Debug. Peces comunes. Cada driver experimental del lot és un `ExpGbGDriver`: porta la llista `NOMS` (una pista per entrada candidata, amb
 * l'adreça al nom) que el proveïdor de consoles ha de fer servir com als altres drivers (`is ExpGbGDriver -> drv.NOMS`). Cap s'ha provat amb ROM.
 */

fun gB(rom: ByteArray, off: Int, vararg v: Int): Boolean {
    if (off < 0 || off + v.size > rom.size) return false
    for (i in v.indices) if (v[i] >= 0 && (rom[off + i].toInt() and 255) != v[i]) return false
    return true
}

/** La capçalera ($134) comença per `t`. */
fun gTitol(rom: ByteArray, t: String): Boolean {
    if (rom.size < 0x150) return false
    for (i in t.indices) if ((rom[0x134 + i].toInt() and 255) != t[i].code) return false
    return true
}

/** Bytes hexadecimals (sense espais) a un desplaçament de fitxer. */
fun gHex(rom: ByteArray, off: Int, hex: String): Boolean {
    if (off < 0 || off + hex.length / 2 > rom.size) return false
    for (k in 0 until hex.length / 2) if ((rom[off + k].toInt() and 255) != hex.substring(2 * k, 2 * k + 2).toInt(16)) return false
    return true
}

/** Primer desplaçament de fitxer on hi ha els bytes `hex` (fins a `fins`), o -1. */
fun gCerca(rom: ByteArray, hex: String, des: Int = 0, fins: Int = rom.size): Int {
    val n = hex.length / 2
    val s = IntArray(n) { hex.substring(2 * it, 2 * it + 2).toInt(16) }
    var i = des
    val lim = minOf(fins, rom.size) - n
    while (i <= lim) {
        var ok = true
        for (k in 0 until n) if ((rom[i + k].toInt() and 255) != s[k]) { ok = false; break }
        if (ok) return i
        i++
    }
    return -1
}

fun gH(v: Int) = v.toString(16).uppercase()

/**
 * Driver experimental genèric: `reconeix`, `ini` (arrencar el motor), `peticio` (demanar la pista `id`) i `fotograma`. `ids` són tots els candidats; `NOMS[i]` és el nom de la pista `ids.first + i`.
 * La CPU executa el codi de so original de la ROM.
 */
open class ExpGbGDriver(
    override val nom: String,
    override val ids: IntRange,
    val NOMS: List<String>,
    private val mbc5Flag: Boolean?,
    private val fpsVal: Double,
    private val rec: (ByteArray) -> Boolean,
    private val ini: (GbCpu, GbMem) -> Unit,
    private val peticio: (GbCpu, GbMem, Int) -> Unit,
    private val fot: (GbCpu, GbMem, Long) -> Unit,
) : GbDriver {
    override val mbc5: Boolean? get() = mbc5Flag
    override val fps: Double get() = fpsVal
    override fun reconeix(rom: ByteArray): Boolean = rec(rom)
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) { cpu.sp = 0xDFFF; ini(cpu, m); peticio(cpu, m, id) }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.sp = 0xDFFF; fot(cpu, m, n) }
}

/** Com `gCerca`, però amb patró de bytes hexadecimals separats per espais on «??» és un comodí (només dins del banc donat: de `des` a `fins`). */
fun gCercaW(rom: ByteArray, pat: String, des: Int, fins: Int): Int {
    val p = pat.trim().split(" ").map { if (it == "??") -1 else it.toInt(16) }
    var i = des
    val lim = minOf(fins, rom.size) - p.size
    while (i <= lim) {
        var ok = true
        for (k in p.indices) if (p[k] >= 0 && (rom[i + k].toInt() and 255) != p[k]) { ok = false; break }
        if (ok) return i
        i++
    }
    return -1
}
