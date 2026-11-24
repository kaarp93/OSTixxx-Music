package cat.gbdsmixxx

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Lector del sistema de fitxers (NitroFS) d'una ROM de Nintendo DS. */
class NitroRom(private val ch: FileChannel) {
    fun llegeix(off: Long, len: Int): ByteArray {
        val b = ByteBuffer.allocate(len)
        var pos = off
        while (b.hasRemaining()) {
            val n = ch.read(b, pos)
            if (n < 0) throw IOException("ROM massa curta")
            pos += n
        }
        return b.array()
    }

    private fun u32(off: Long): Long = u32(llegeix(off, 4), 0)

    /** Codi de joc de 4 lletres (p. ex. ADAE = Diamant EUA). */
    fun codi(): String = String(llegeix(0xC, 4), Charsets.US_ASCII)

    /** Icona de la bàner: 0x220 bytes a +0x20 (bitmap de 32x32 a 4 bits en rajoles de 8x8, 0x200 bytes, i paleta de 16 colors BGR555), o null. */
    fun icona(): ByteArray? = try { val o = u32(0x68); if (o <= 0L) null else llegeix(o + 0x20, 0x220) } catch (e: Exception) { null }

    /** Títol del joc de la bàner (anglès, a +0x340): totes les línies menys l'última (l'editor), o null si no n'hi ha. */
    fun titolBanner(): String? = try {
        val o = u32(0x68)
        if (o <= 0L) null else {
            val s = String(llegeix(o + 0x340, 0x100), Charsets.UTF_16LE).trimEnd('\u0000', ' ')
            val l = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            (if (l.size >= 2) l.dropLast(1).joinToString(" ") else l.firstOrNull())?.takeIf { it.isNotBlank() }
        }
    } catch (e: Exception) { null }

    /** Totes les rutes de fitxer del NitroFS (per trobar un arxiu pel nom sense dependre del camí, que canvia entre jocs). */
    fun llista(): List<String> {
        val fnt = llegeix(u32(0x40), u32(0x44).toInt())
        val out = ArrayList<String>()
        fun camina(dir: Int, prefix: String) {
            var p = u32(fnt, dir * 8).toInt()
            while (p < fnt.size) {
                val l = fnt[p].toInt() and 0xFF; p++
                if (l == 0) break
                val len = l and 0x7F; val nm = String(fnt, p, len, Charsets.US_ASCII); p += len
                if (l and 0x80 != 0) { val did = u16(fnt, p) and 0xFFF; p += 2; camina(did, "$prefix$nm/") } else out.add(prefix + nm)
            }
        }
        camina(0, "")
        return out
    }

    /** Contingut del fitxer `ruta` (p. ex. «poketool/pokegra/pokegra.narc») o null si no hi és. */
    fun fitxer(ruta: String): ByteArray? {
        val fntOff = u32(0x40); val fntLen = u32(0x44).toInt(); val fatOff = u32(0x48)
        val fnt = llegeix(fntOff, fntLen)
        var dir = 0
        val parts = ruta.split("/")
        for ((k, nom) in parts.withIndex()) {
            val sub = u32(fnt, dir * 8).toInt(); val primer = u16(fnt, dir * 8 + 4)
            var p = sub; var id = primer; var trobat = -1; var esDir = false
            while (p < fnt.size) {
                val l = fnt[p].toInt() and 0xFF; p++
                if (l == 0) break
                val len = l and 0x7F
                val nm = String(fnt, p, len, Charsets.US_ASCII); p += len
                if (l and 0x80 != 0) {
                    val did = u16(fnt, p); p += 2
                    if (nm == nom) { trobat = did and 0xFFF; esDir = true; break }
                } else {
                    if (nm == nom) { trobat = id; break }
                    id++
                }
            }
            if (trobat < 0) return null
            if (k == parts.size - 1) {
                if (esDir) return null
                val ini = u32(fatOff + trobat * 8L); val fi = u32(fatOff + trobat * 8L + 4)
                return llegeix(ini, (fi - ini).toInt())
            }
            if (!esDir) return null
            dir = trobat
        }
        return null
    }

    companion object {
        fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
        fun u32(b: ByteArray, o: Int): Long = (u16(b, o).toLong()) or (u16(b, o + 2).toLong() shl 16)
    }
}

/** Fitxers d'un NARC (arxiu de fitxers de Nintendo). */
class Narc(private val d: ByteArray) {
    val nFitxers: Int
    private val dades: Int
    private val btaf = 16

    init {
        if (String(d, 0, 4, Charsets.US_ASCII) != "NARC") throw IOException("No és un NARC")
        nFitxers = NitroRom.u16(d, btaf + 8)
        val btafLen = NitroRom.u32(d, btaf + 4).toInt()
        val btnf = btaf + btafLen
        val btnfLen = NitroRom.u32(d, btnf + 4).toInt()
        dades = btnf + btnfLen + 8
    }

    fun fitxer(i: Int): ByteArray {
        val e = btaf + 12 + i * 8
        val ini = NitroRom.u32(d, e).toInt(); val fi = NitroRom.u32(d, e + 4).toInt()
        return d.copyOfRange(dades + ini, dades + fi)
    }
}

/** Descomprimeix LZ77 de la BIOS de la DS (tipus 0x10); si les dades no són comprimides les retorna tal qual. */
fun descomprimeixLz(d: ByteArray): ByteArray {
    if (d.size < 4 || (d[0].toInt() and 0xFF) != 0x10) return d
    val mida = (d[1].toInt() and 0xFF) or ((d[2].toInt() and 0xFF) shl 8) or ((d[3].toInt() and 0xFF) shl 16)
    val out = ByteArray(mida); var o = 0; var p = 4
    while (o < mida && p < d.size) {
        val flags = d[p++].toInt() and 0xFF
        for (bit in 7 downTo 0) {
            if (o >= mida || p >= d.size) break
            if ((flags shr bit) and 1 == 0) { out[o++] = d[p++] }
            else {
                val b1 = d[p++].toInt() and 0xFF; val b2 = d[p++].toInt() and 0xFF
                val len = (b1 shr 4) + 3; val disp = (((b1 and 0xF) shl 8) or b2) + 1
                for (k in 0 until len) { if (o >= mida || o - disp < 0) break; out[o] = out[o - disp]; o++ }
            }
        }
    }
    return out
}
