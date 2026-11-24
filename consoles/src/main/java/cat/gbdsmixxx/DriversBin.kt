package cat.gbdsmixxx

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32

/**
 * Paquet binari de drivers guiats per dades amb índex per CRC (l'autor, 2026-11-16: mai dades de l'app en TXT). Es carrega a demanda: en obrir-lo només es llegeix l'índex;
 * cada registre es llegeix i es valida quan es necessita (una ROM coincideix o se'n toca una cançó).
 *
 * Format (versió 2, enters en big-endian):
 * - `"OSTD"` u32 · versió u16 · n u32 · llargada de la capçalera u32 (inclou el CRC final)
 * - n entrades: crc u32 · format u8 (1 poke, 2 gb, 3 nes) · origen u8 (1 auto, 2 exp) · senyals u8 (bit 0 = GBC, bit 1 = confirmat abans) · offset u32 · llargada u32 · rev u32 · chk u32 · títol (u16 + UTF-8)
 * - CRC32 de tot l'anterior u32
 * - registres: la part que fa sonar (rutines, pokes, codis; enters varint amb zigzag) i després els noms de les cançons. `rev` = CRC32 de la part que fa sonar (identitat de contingut:
 *   canviar només els noms no la canvia); `chk` = CRC32 del registre sencer (integritat).
 * Les entrades van ordenades per (crc, format, origen); no n'hi ha dues d'iguals.
 */
object DriversBin {
    const val MAGIC = 0x4F535444
    const val VERSIO = 2

    class Entrada(val crc: Long, val format: Int, val origen: TipusDriver, val cgb: Boolean, val confirmat: Boolean, val off: Int, val len: Int, val rev: Int, val chk: Int, val titol: String) {
        val clau: String get() = DriversDef.clau(crc, format)
        val revHex: String get() = IdDriver.hex8(rev)
    }

    /** Origen de bytes: un array (asset) o un fitxer amb lectura a demanda. */
    interface Font { val mida: Long; fun llegeix(off: Long, len: Int): ByteArray }
    class FontBytes(private val b: ByteArray) : Font {
        override val mida get() = b.size.toLong()
        override fun llegeix(off: Long, len: Int): ByteArray { require(off >= 0 && len >= 0 && off + len <= b.size); return b.copyOfRange(off.toInt(), off.toInt() + len) }
    }
    class FontFitxer(private val f: File) : Font {
        override val mida get() = f.length()
        override fun llegeix(off: Long, len: Int): ByteArray = java.io.RandomAccessFile(f, "r").use { r -> require(off >= 0 && len >= 0 && off + len <= r.length()); r.seek(off); ByteArray(len).also { r.readFully(it) } }
    }

    // ---- varint ----
    private fun DataOutputStream.v(x: Int) { var z = (x shl 1) xor (x shr 31); while (z and 0x7F.inv() != 0) { writeByte((z and 0x7F) or 0x80); z = z ushr 7 }; writeByte(z) }
    private fun DataOutputStream.vs(a: IntArray) { v(a.size); for (x in a) v(x) }
    private fun DataOutputStream.s(t: String) { val b = t.toByteArray(Charsets.UTF_8); v(b.size); write(b) }
    private fun DataOutputStream.rut(r: RutDef?) { if (r == null) { writeByte(0); return }; writeByte(1); v(r.dest); vs(r.bs); vs(r.v) }

    private class Lector(val b: ByteArray) {
        var p = 0
        fun byte(): Int { if (p >= b.size) throw IllegalStateException("registre curt"); return b[p++].toInt() and 255 }
        fun v(): Int { var z = 0; var sh = 0; while (true) { val x = byte(); z = z or ((x and 0x7F) shl sh); if (x and 0x80 == 0) break; sh += 7; if (sh > 28) throw IllegalStateException("varint") }; return (z ushr 1) xor -(z and 1) }
        fun mida(): Int { val n = v(); if (n < 0 || n > b.size - p) throw IllegalStateException("mida"); return n }
        fun vs() = IntArray(mida()) { v() }
        fun s(): String { val n = mida(); if (p + n > b.size) throw IllegalStateException("text"); return String(b, p, n, Charsets.UTF_8).also { p += n } }
        fun rut(): RutDef? = if (byte() == 0) null else RutDef(v(), vs(), vs())
    }

    /** Part que fa sonar (determinista): la seva CRC32 és la `rev`. */
    fun contingut(d: DefDriver): ByteArray {
        val bo = ByteArrayOutputStream(); val o = DataOutputStream(bo)
        when (d) {
            is DefGbPoke -> { o.writeByte(if (d.mbc5) 2 else 0); o.rut(d.f); o.rut(d.start); o.vs(d.base); o.v(d.songs.size); for (x in d.songs) o.vs(x) }
            is DefGbAuto -> { o.writeByte(if (d.mbc5) 2 else 0); o.rut(d.f); o.rut(d.ini); o.rut(d.p); o.v(d.reg); o.v(d.ram); o.vs(d.codis) }
            is DefNesAuto -> { o.rut(d.f); o.rut(d.ini); o.rut(d.p); o.vs(d.codis); o.v(d.ram); o.vs(d.extra) }
        }
        return bo.toByteArray()
    }
    private fun noms(d: DefDriver): ByteArray { val bo = ByteArrayOutputStream(); val o = DataOutputStream(bo); o.v(d.noms.size); for (n in d.noms) o.s(n); return bo.toByteArray() }
    /** Revisió (identitat de contingut) d'una definició. */
    fun rev(d: DefDriver): Int = crc(contingut(d))
    /** CRC del registre sencer (contingut + noms): el `chk` que tindria al paquet. */
    fun chk(d: DefDriver): Int = crc(contingut(d) + noms(d))

    private fun llegeixRegistre(e: Entrada, b: ByteArray): DefDriver {
        val r = Lector(b)
        val d: DefDriver = when (e.format) {
            DefDriver.FORMAT_POKE -> { val fl = r.byte(); val f = r.rut()!!; val st = r.rut(); val base = r.vs(); val n = r.mida(); val songs = List(n) { r.vs() }; val noms = List(r.mida()) { r.s() }
                DefGbPoke(e.crc, e.titol, e.cgb, fl and 2 != 0, f, base, songs, st, noms, e.origen) }
            DefDriver.FORMAT_GB -> { val fl = r.byte(); val f = r.rut()!!; val ini = r.rut(); val p = r.rut(); val reg = r.v(); val ram = r.v(); val codis = r.vs(); val noms = List(r.mida()) { r.s() }
                DefGbAuto(e.crc, e.titol, e.cgb, fl and 2 != 0, f, ini, p, reg, ram, codis, noms, e.origen) }
            DefDriver.FORMAT_NES -> { val f = r.rut()!!; val ini = r.rut(); val p = r.rut(); val codis = r.vs(); val ram = r.v(); val extra = r.vs(); val noms = List(r.mida()) { r.s() }
                DefNesAuto(e.crc, e.titol, f, ini, p, codis, noms, ram, extra, e.origen) }
            else -> throw IllegalArgumentException("format ${e.format}")
        }
        if (r.p != b.size) throw IllegalStateException("bytes sobrers")
        d.confirmat = e.confirmat
        return d
    }

    private fun crc(b: ByteArray, o: Int = 0, l: Int = b.size) = CRC32().also { it.update(b, o, l) }.value.toInt()
    private fun clauUnica(d: DefDriver) = Triple(d.crc, d.format, d.origen)

    /** Escriu el paquet sencer. Si hi ha dues definicions amb el mateix (crc, format, origen), val l'última. Determinista. */
    fun escriu(defs: Collection<DefDriver>): ByteArray {
        val unics = LinkedHashMap<Triple<Long, Int, TipusDriver>, DefDriver>(); for (d in defs) unics[clauUnica(d)] = d
        val llista = unics.values.sortedWith(compareBy<DefDriver>({ it.crc }, { it.format }, { it.origen.ordinal }))
        val conts = llista.map { contingut(it) }
        val regs = llista.mapIndexed { i, d -> conts[i] + noms(d) }
        val titols = llista.map { it.titol.toByteArray(Charsets.UTF_8).let { b -> if (b.size > 0xFFFF) b.copyOf(0xFFFF) else b } }
        val cap = 4 + 2 + 4 + 4 + titols.sumOf { 4 + 1 + 1 + 1 + 4 + 4 + 4 + 4 + 2 + it.size } + 4
        val bo = ByteArrayOutputStream(); val o = DataOutputStream(bo)
        o.writeInt(MAGIC); o.writeShort(VERSIO); o.writeInt(llista.size); o.writeInt(cap)
        var off = cap
        for ((i, d) in llista.withIndex()) {
            o.writeInt(d.crc.toInt()); o.writeByte(d.format); o.writeByte(if (d.origen == TipusDriver.EXP) 2 else 1); o.writeByte((if (d.cgb) 1 else 0) or (if (d.confirmat) 2 else 0))
            o.writeInt(off); o.writeInt(regs[i].size); o.writeInt(crc(conts[i])); o.writeInt(crc(regs[i]))
            o.writeShort(titols[i].size); o.write(titols[i])
            off += regs[i].size
        }
        o.writeInt(crc(bo.toByteArray()))
        for (r in regs) o.write(r)
        return bo.toByteArray()
    }

    /** Un paquet obert: només té l'índex en memòria. Immutable: es pot llegir des de diversos fils. */
    class Pack internal constructor(private val font: Font, val entrades: List<Entrada>) {
        fun perCrc(crc: Long): List<Entrada> {
            var lo = 0; var hi = entrades.size
            while (lo < hi) { val m = (lo + hi) ushr 1; if (entrades[m].crc < crc) lo = m + 1 else hi = m }
            val out = ArrayList<Entrada>(); var i = lo
            while (i < entrades.size && entrades[i].crc == crc) out.add(entrades[i++])
            return out
        }
        /** Llegeix i valida un registre; null si no és íntegre. */
        fun llegeix(e: Entrada): DefDriver? = try {
            val b = font.llegeix(e.off.toLong(), e.len)
            if (crc(b) != e.chk) null else llegeixRegistre(e, b)
        } catch (x: Throwable) { null }
        fun tot(): List<DefDriver> = entrades.mapNotNull { llegeix(it) }
    }

    /** Obre un paquet llegint només la capçalera; null si no és un paquet vàlid o la capçalera és corrupta. */
    fun obre(font: Font): Pack? = try {
        if (font.mida < 18) null else {
            val h = DataInputStream(font.llegeix(0, 14).inputStream())
            if (h.readInt() != MAGIC || h.readUnsignedShort() != VERSIO) null else {
                val n = h.readInt(); val cap = h.readInt()
                if (n < 0 || cap < 18 || cap > font.mida || n > cap / 25) null else {
                    val b = font.llegeix(0, cap); val i = DataInputStream(b.inputStream()); i.skipBytes(14)
                    val es = List(n) {
                        val crc = i.readInt().toLong() and 0xFFFFFFFFL; val fmt = i.readUnsignedByte(); val org = i.readUnsignedByte(); val fl = i.readUnsignedByte()
                        val off = i.readInt(); val len = i.readInt(); val rev = i.readInt(); val chk = i.readInt()
                        val tb = ByteArray(i.readUnsignedShort()); i.readFully(tb)
                        Entrada(crc, fmt, if (org == 2) TipusDriver.EXP else TipusDriver.AUTO, fl and 1 != 0, fl and 2 != 0, off, len, rev, chk, String(tb, Charsets.UTF_8))
                    }
                    val ok = i.readInt() == crc(b, 0, cap - 4) && es.all { it.off >= cap && it.len >= 0 && it.off.toLong() + it.len <= font.mida && it.format in 1..3 }
                    if (ok) Pack(font, es) else null
                }
            }
        }
    } catch (x: Throwable) { null }

    /** Afegeix/substitueix definicions (per (crc, format, origen)) i torna el paquet nou sencer. Les entrades del paquet actual que no es poden llegir es perden (estaven corruptes). */
    fun fusiona(actual: Pack?, nous: Collection<DefDriver>): ByteArray = escriu((actual?.tot() ?: emptyList()) + nous)

    /** Escriptura segura: temporal + `.bak` de l'anterior + `renameTo`. Si el canvi de nom falla, es restaura l'anterior. */
    fun desa(f: File, bytes: ByteArray) {
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp"); val bak = File(f.path + ".bak")
        java.io.FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
        if (f.exists()) { bak.delete(); if (!f.renameTo(bak)) throw java.io.IOException("no es pot fer la còpia .bak") }
        if (!tmp.renameTo(f)) { if (bak.exists()) bak.renameTo(f); throw java.io.IOException("no es pot desar " + f.name) }
    }
    /** Obre un fitxer de paquet (es llegeix sencer a memòria: els importats són petits i així una escriptura posterior no el pot canviar per sota); si és corrupte o no hi és, prova la còpia `.bak`. */
    fun obreFitxer(f: File): Pack? {
        fun prova(x: File): Pack? = try { if (x.isFile) obre(FontBytes(x.readBytes())) else null } catch (e: Throwable) { null }
        return prova(f) ?: prova(File(f.path + ".bak"))
    }
}
