package cat.gbdsmixxx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Error en llegir un `.ostx`; `motiu` diu quin (la UI el tradueix amb `t()`). */
class ErrorOstx(val motiu: Int) : java.io.IOException("ostx: $motiu") {
    companion object { const val NO_OSTX = 1; const val MALMES = 2; const val MES_NOU = 3; const val SENSE_CATALEG = 4 }
}

/**
 * Còpia de seguretat de l'usuari: fitxer `.ostx` (l'autor, 2026-11-22). Porta totes les dades de l'usuari i la biblioteca, però mai les ROM, les portades ni les dades de
 * libretro. Cada secció porta el seu tipus, la seva versió, la seva longitud i el seu CRC32: una secció trencada o d'un tipus desconegut s'ignora sense perdre les altres.
 *
 * Format (big-endian): magic «OSTX» (int) · versió del contenidor (int, ara 1) · nombre de seccions (int) · i, per secció: tipus (int, 4 lletres) · versió de la
 * secció (int) · longitud (int) · CRC32 dels bytes (int) · bytes. Un contenidor d'una versió més nova es rebutja sencer (`MES_NOU`); un fitxer tallat es llegeix fins a
 * l'última secció sencera.
 */
object Ostx {
    val MAGIC = Durable.magic("OSTX")
    const val VERSIO = 1
    const val EXTENSIO = "ostx"
    /** Seccions d'ara (el nom de cada una = 4 lletres). */
    val CATALEG = Durable.magic("CATL")          // catàleg definitiu (`CatalegDefinitiu`)
    val USUARI = Durable.magic("DUSR")           // dades_usuari.bin (`MagatzemUsuari`): marques, etiquetes, noms de pista, ordre, comptes
    val RADIO = Durable.magic("RDIO")            // radio_dades.bin (`DadesBin.Radio`): llistes, noms, ocults, anàlisi, combinacions, tipus, temps
    val DRIVERS = Durable.magic("DRVI")          // drivers/importats.bin (paquet `DriversBin`, tal qual)
    val PREFS = Durable.magic("PREF")            // preferències (`App.env`) sense les dades d'usuari antigues ni els permisos de carpetes
    val INDEX = Durable.magic("IDXR")            // índex de la biblioteca (`IndexRoms`: entrades i carpetes) sense cap ROM

    class Seccio(val tipus: Int, val versio: Int, val dades: ByteArray) {
        val nom: String get() = String(byteArrayOf((tipus ushr 24).toByte(), (tipus ushr 16).toByte(), (tipus ushr 8).toByte(), tipus.toByte()), Charsets.ISO_8859_1)
    }
    /** `seccions` bones; `trencades` = quantes s'han ignorat (CRC dolent o tallades). */
    class Lectura(val seccions: List<Seccio>, val trencades: Int)

    fun escriu(out: java.io.OutputStream, seccions: List<Seccio>) {
        val bo = ByteArrayOutputStream()
        DataOutputStream(bo).use { d ->
            d.writeInt(MAGIC); d.writeInt(VERSIO); d.writeInt(seccions.size)
            for (s in seccions) { d.writeInt(s.tipus); d.writeInt(s.versio); d.writeInt(s.dades.size); d.writeInt(Durable.crc(s.dades)); d.write(s.dades) }
        }
        out.write(bo.toByteArray()); out.flush()          // es munta sencer a la memòria i s'escriu d'un sol cop
    }

    fun llegeix(inp: java.io.InputStream, max: Int = 256 shl 20): Lectura {
        val b = llegeixMax(inp, max)
        if (b.size < 12) throw ErrorOstx(ErrorOstx.NO_OSTX)
        val d = DataInputStream(ByteArrayInputStream(b))
        if (d.readInt() != MAGIC) throw ErrorOstx(ErrorOstx.NO_OSTX)
        val v = d.readInt()
        if (v > VERSIO) throw ErrorOstx(ErrorOstx.MES_NOU)
        if (v < 1) throw ErrorOstx(ErrorOstx.MALMES)
        val n = d.readInt()
        if (n < 0 || n > 10000) throw ErrorOstx(ErrorOstx.MALMES)
        val out = ArrayList<Seccio>(); var trencades = 0
        var pos = 12
        for (k in 0 until n) {
            if (pos + 16 > b.size) { trencades += n - k; break }
            val bb = java.nio.ByteBuffer.wrap(b, pos, 16)
            val t = bb.int; val sv = bb.int; val len = bb.int; val c = bb.int
            if (len < 0 || pos.toLong() + 16 + len > b.size) { trencades += n - k; break }
            if (Durable.crc(b, pos + 16, len) == c) out.add(Seccio(t, sv, b.copyOfRange(pos + 16, pos + 16 + len))) else trencades++
            pos += 16 + len
        }
        return Lectura(out, trencades)
    }

    private fun llegeixMax(inp: java.io.InputStream, max: Int): ByteArray {
        val bo = ByteArrayOutputStream(); val buf = ByteArray(65536)
        while (true) { val r = inp.read(buf); if (r < 0) break; bo.write(buf, 0, r); if (bo.size() > max) throw ErrorOstx(ErrorOstx.NO_OSTX) }
        return bo.toByteArray()
    }
}
