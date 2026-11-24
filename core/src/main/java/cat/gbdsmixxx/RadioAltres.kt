package cat.gbdsmixxx

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Detecció automàtica de la música de qualsevol joc de DS (l'autor, 2026-11-08): si la ROM porta un arxiu de so SDAT, s'extreu només aquest
 * (mai la ROM) i el joc passa a «Altres ràdios», amb el títol de la bàner de la ROM; si en porta més d'un, cada SDAT surt com una llista pròpia («Títol (arxiu)»).
 */
object SdatAuto {
    private fun esSdat(b: ByteArray) = b.size > 0x40 && b[0] == 'S'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == 'A'.code.toByte() && b[3] == 'T'.code.toByte() &&
        b[4] == 0xFF.toByte() && b[5] == 0xFE.toByte()

    /** Un SDAT és bo si es pot llegir i porta almenys una seqüència de música. */
    fun valida(b: ByteArray): Boolean = try { esSdat(b) && Sdat(b).musicaGen.isNotEmpty() } catch (e: Exception) { false }

    /**
     * Passa a `f` (nom, bytes) cada SDAT bo de la ROM, un a un perquè no es quedin tots a la memòria: primer els arxius del NitroFS que acaben
     * en `.sdat` (el nom és el de l'arxiu sense carpeta ni extensió); si no n'hi ha cap amb aquest nom, escaneja la ROM sencera cada 512 bytes
     * buscant la marca `SDAT` i la mida de la capçalera (sense noms: «SDAT 1», «SDAT 2»...). Retorna quants n'ha trobat.
     */
    fun cadaSdat(ch: FileChannel, rom: NitroRom, f: (String, ByteArray) -> Unit): Int {
        var n = 0
        for (nom in rom.llista().filter { it.endsWith(".sdat", ignoreCase = true) }) {
            val b = rom.fitxer(nom) ?: continue
            if (valida(b)) { f(nom.substringAfterLast('/').substringBeforeLast('.'), b); n++ }
        }
        return if (n > 0) n else escaneja(ch, rom, f)
    }

    /** Escaneig de la ROM sencera cada 512 bytes (alineació dels arxius del NitroFS) buscant la marca `SDAT`; passa cada SDAT bo a `f`. */
    fun escaneja(ch: FileChannel, rom: NitroRom, f: (String, ByteArray) -> Unit): Int {
        val mida = ch.size(); var pos = 0L; var n = 0
        val buf = ByteBuffer.allocate(4 shl 20)
        while (pos < mida) {
            buf.clear(); val llegits = ch.read(buf, pos); if (llegits <= 0) break
            var o = 0
            while (o + 16 <= llegits) {
                if (buf.get(o) == 'S'.code.toByte() && buf.get(o + 1) == 'D'.code.toByte() && buf.get(o + 2) == 'A'.code.toByte() && buf.get(o + 3) == 'T'.code.toByte() &&
                    buf.get(o + 4) == 0xFF.toByte() && buf.get(o + 5) == 0xFE.toByte()) {
                    val sz = (buf.get(o + 8).toLong() and 255) or ((buf.get(o + 9).toLong() and 255) shl 8) or ((buf.get(o + 10).toLong() and 255) shl 16) or ((buf.get(o + 11).toLong() and 255) shl 24)
                    if (sz in 0x100..(mida - pos - o)) { val b = try { rom.llegeix(pos + o, sz.toInt()) } catch (e: Exception) { null }; if (b != null && valida(b)) { n++; f("SDAT $n", b) } }
                }
                o += 0x200
            }
            pos += llegits
        }
        return n
    }

    /** Títol que es veu: el de la bàner de la ROM; si no en porta, el de la capçalera (12 lletres). */
    fun titol(rom: NitroRom, capcalera: ByteArray): String =
        rom.titolBanner() ?: String(capcalera, 0, 12, Charsets.US_ASCII).trim { it == ' ' || it.code == 0 }.ifEmpty { "DS" }
}
