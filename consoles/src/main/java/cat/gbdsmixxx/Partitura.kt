package cat.gbdsmixxx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Partitures (l'autor, 2026-11-23): per a cada cançó d'un joc de Game Boy/GBC o NES, la seqüència d'escriptures als registres de l'APU (amb el cicle de CPU on passen i
 * els fotogrames), gravada un sol cop pels analitzadors de PC. Music la reprodueix només amb l'APU emulada, sense CPU ni driver (`SintePartGb`).
 * - NO porta cap byte de la ROM: només valors de registres. Sense la ROM, no sona res: el contingut va xifrat amb AES-256-GCM i la clau és el SHA-256 de TOTA la ROM
 *   (amb un domini fix). Si la ROM no és exactament la de l'analitzador, el xifrat no es pot desfer i el fitxer és soroll. La capçalera (en clar) porta el sistema, el CRC32 i la mida
 *   de la ROM per saber quina ROM cal i authenticada com a dades associades de GCM.
 * - Fitxer: «OXPT», versió (1 byte), sistema (1 byte: 0 GB, 1 GBC, 2 NES), CRC32 (4), mida (4), nonce (12), longitud del xifrat (4), xifrat (GCM, amb l'etiqueta de 16 bytes al final).
 *   Dins, un bloc comprimit (Deflate) amb les cançons.
 */
class CancoPart(
    /** Identificador del driver que va generar la partitura (`DriverRef.id`): un joc pot tenir més d'un driver amb numeracions de cançó diferents. */
    val driver: String,
    val id: Int,
    /** Fotogrames gravats (bucle: fins al final de la primera volta; si s'acaba sola, fins al final). */
    val nFot: Int,
    /** Fotogrames per segon al principi i canvis (índex de fotograma, fps): els jocs amb temporitzador canvien el ritme amb la cançó. */
    val fps0: Double, val canvisFpsFot: IntArray, val canvisFps: DoubleArray,
    /** `iniFot[f]..iniFot[f+1]` són les escriptures del fotograma `f` (en ordre). */
    val iniFot: IntArray, val cicle: IntArray, val adr: IntArray, val val_: IntArray,
    val an: AnalisiRitme
) {
    /** Fotograma gravat que correspon al fotograma `f` de la reproducció (amb bucle, es repeteix la volta des de l'inici del bucle); -1 = res més (silenci). */
    fun fotGravat(f: Long): Int {
        if (f < nFot) return f.toInt()
        if (!an.teBucle) return -1
        val ini = an.ini.toInt().coerceIn(0, nFot - 1); val llarg = (nFot - ini).coerceAtLeast(1)
        return ini + ((f - nFot) % llarg).toInt()
    }
    /** El ritme (fps) amb què es va gravar el fotograma `f`. */
    fun fpsFot(f: Int): Double {
        var r = fps0
        for (i in canvisFpsFot.indices) { if (canvisFpsFot[i] <= f) r = canvisFps[i] else break }
        return r
    }
}

class Partitura(val sistema: Int, val crc: Long, val mida: Int, val cancons: List<CancoPart>) {
    fun canco(driver: String, id: Int): CancoPart? = cancons.firstOrNull { it.id == id && it.driver == driver }
}

/** Gravador d'una cançó: el sintetitzador li dona cada fotograma amb les seves escriptures (en cicles de CPU). */
class GrabaCanco(val driver: String, val id: Int) {
    private val canvisFot = ArrayList<Int>(); private val canvisFps = ArrayList<Double>()
    private var fps0 = 0.0; private var fpsUlt = 0.0
    private val iniFot = ArrayList<Int>(); private var cic = IntArray(4096); private var adr = IntArray(4096); private var v = IntArray(4096); private var n = 0
    private var init = IntArray(0)
    val nFot: Int get() = iniFot.size
    /** Escriptures de l'engegada (abans del primer fotograma): es graven al principi del fotograma 0, al cicle 0. */
    fun iniciEscriptures(adrV: IntArray, nEsc: Int) { init = adrV.copyOf(nEsc) }
    private fun posa(c: Int, a: Int, valor: Int) {
        if (n == cic.size) { cic = cic.copyOf(n * 2); adr = adr.copyOf(n * 2); v = v.copyOf(n * 2) }
        cic[n] = c; adr[n] = a; v[n] = valor; n++
    }
    fun fotograma(fps: Double, cicles: IntArray, adrV: IntArray, nEsc: Int) {
        if (iniFot.isEmpty()) { fps0 = fps; fpsUlt = fps } else if (fps != fpsUlt) { canvisFot.add(iniFot.size); canvisFps.add(fps); fpsUlt = fps }
        iniFot.add(n)
        if (iniFot.size == 1) for (x in init) posa(0, x shr 8, x and 255)
        for (i in 0 until nEsc) posa(cicles[i], adrV[i] shr 8, adrV[i] and 255)
    }
    /** `fots` = fotogrames que es queden (els primers); `an` = l'anàlisi (bucle, pols i durada) d'aquesta cançó. */
    fun fes(fots: Int, an: AnalisiRitme): CancoPart {
        val f = minOf(fots, iniFot.size)
        val ini = IntArray(f + 1) { if (it < f) iniFot[it] else if (f < iniFot.size) iniFot[f] else n }
        val tot = ini[f]
        val cf = canvisFot.filter { it < f }
        return CancoPart(driver, id, f, fps0, cf.toIntArray(), canvisFps.take(cf.size).toDoubleArray(), ini, cic.copyOf(tot), adr.copyOf(tot), v.copyOf(tot), an)
    }
}

object Partitures {
    private const val MAGIC = 0x4F585054          // «OXPT»
    private const val VERSIO = 1
    private val DOMINI = "OSTixxx partitura v1".toByteArray(Charsets.UTF_8)
    const val GB = 0; const val GBC = 1; const val NES = 2

    private fun clau(rom: ByteArray): SecretKeySpec {
        val md = MessageDigest.getInstance("SHA-256"); md.update(DOMINI); md.update(rom)
        return SecretKeySpec(md.digest(), "AES")
    }
    fun crc32(rom: ByteArray): Long { val c = java.util.zip.CRC32(); c.update(rom); return c.value }

    private fun escriuVar(o: DataOutputStream, x: Int) { var y = x; while (y and 0x7F.inv() != 0) { o.writeByte((y and 0x7F) or 0x80); y = y ushr 7 }; o.writeByte(y) }
    private fun llegeixVar(i: DataInputStream): Int { var r = 0; var s = 0; while (true) { val b = i.readUnsignedByte(); r = r or ((b and 0x7F) shl s); if (b and 0x80 == 0) return r; s += 7; require(s < 35) } }

    private fun cos(p: Partitura): ByteArray {
        val bo = ByteArrayOutputStream(); val o = DataOutputStream(bo)
        o.writeByte(p.sistema); escriuVar(o, p.cancons.size)
        for (c in p.cancons) {
            o.writeUTF(c.driver); escriuVar(o, c.id); escriuVar(o, c.nFot); o.writeDouble(c.fps0)
            escriuVar(o, c.canvisFpsFot.size); var ant = 0
            for (i in c.canvisFpsFot.indices) { escriuVar(o, c.canvisFpsFot[i] - ant); ant = c.canvisFpsFot[i]; o.writeDouble(c.canvisFps[i]) }
            val a = c.an
            o.writeDouble(a.t); o.writeDouble(a.fase); o.writeDouble(a.ini); o.writeDouble(a.llarg); o.writeInt(a.fi); o.writeBoolean(a.acaba)
            val h = a.hist; if (h == null) o.writeByte(0) else { o.writeByte(h.size); for (x in h) o.writeDouble(x) }
            for (f in 0 until c.nFot) {
                val d = c.iniFot[f]; val e = c.iniFot[f + 1]; escriuVar(o, e - d)
                for (k in d until e) { escriuVar(o, c.cicle[k]); escriuVar(o, c.adr[k]); o.writeByte(c.val_[k]) }
            }
        }
        o.flush(); return bo.toByteArray()
    }
    private fun deCos(b: ByteArray, crc: Long, mida: Int): Partitura {
        val i = DataInputStream(ByteArrayInputStream(b)); val sis = i.readUnsignedByte(); val nc = llegeixVar(i); require(nc in 0..100000)
        val out = ArrayList<CancoPart>()
        repeat(nc) {
            val drv = i.readUTF(); val id = llegeixVar(i); val nFot = llegeixVar(i); require(nFot in 0..2_000_000); val fps0 = i.readDouble()
            val ncf = llegeixVar(i); require(ncf in 0..nFot); val cf = IntArray(ncf); val cfp = DoubleArray(ncf); var ant = 0
            for (k in 0 until ncf) { ant += llegeixVar(i); cf[k] = ant; cfp[k] = i.readDouble() }
            val t = i.readDouble(); val fase = i.readDouble(); val ini = i.readDouble(); val llarg = i.readDouble(); val fi = i.readInt(); val acaba = i.readBoolean()
            val nh = i.readUnsignedByte(); val hist = if (nh == 0) null else DoubleArray(nh) { i.readDouble() }
            val iniFot = IntArray(nFot + 1); var cic = IntArray(1024); var adr = IntArray(1024); var vv = IntArray(1024); var n = 0
            for (f in 0 until nFot) {
                iniFot[f] = n; val ne = llegeixVar(i); require(ne in 0..10_000_000)
                for (k in 0 until ne) {
                    if (n == cic.size) { cic = cic.copyOf(n * 2); adr = adr.copyOf(n * 2); vv = vv.copyOf(n * 2) }
                    cic[n] = llegeixVar(i); adr[n] = llegeixVar(i); vv[n] = i.readUnsignedByte(); n++
                }
            }
            iniFot[nFot] = n
            out.add(CancoPart(drv, id, nFot, fps0, cf, cfp, iniFot, cic.copyOf(n), adr.copyOf(n), vv.copyOf(n), AnalisiRitme(t, fase, ini, llarg, fi, hist, acaba)))
        }
        return Partitura(sis, crc, mida, out)
    }

    /** Fitxer xifrat amb la clau de `rom`. */
    fun escriu(p: Partitura, rom: ByteArray): ByteArray {
        val crc = crc32(rom); require(p.crc == crc && p.mida == rom.size) { "la partitura no és d'aquesta ROM" }
        val pla = cos(p)
        val df = Deflater(9); df.setInput(pla); df.finish(); val bz = ByteArrayOutputStream(); val buf = ByteArray(65536)
        while (!df.finished()) { val k = df.deflate(buf); bz.write(buf, 0, k) }; df.end()
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cap = ByteArrayOutputStream(); val co = DataOutputStream(cap)
        co.writeInt(MAGIC); co.writeByte(VERSIO); co.writeByte(p.sistema); co.writeInt(crc.toInt()); co.writeInt(rom.size)
        val capB = cap.toByteArray()
        val ci = Cipher.getInstance("AES/GCM/NoPadding"); ci.init(Cipher.ENCRYPT_MODE, clau(rom), GCMParameterSpec(128, nonce)); ci.updateAAD(capB)
        val xifrat = ci.doFinal(bz.toByteArray())
        val bo = ByteArrayOutputStream(); val o = DataOutputStream(bo)
        o.write(capB); o.write(nonce); o.writeInt(xifrat.size); o.write(xifrat)
        return bo.toByteArray()
    }

    class Capcalera(val sistema: Int, val crc: Long, val mida: Int)
    /** Capçalera en clar (per saber quina ROM cal); null si no és un fitxer de partitures. */
    fun capcalera(b: ByteArray): Capcalera? {
        if (b.size < 14 + 12 + 4) return null
        val i = DataInputStream(ByteArrayInputStream(b))
        if (i.readInt() != MAGIC || i.readUnsignedByte() != VERSIO) return null
        val sis = i.readUnsignedByte(); val crc = i.readInt().toLong() and 0xFFFFFFFFL; val mida = i.readInt()
        return Capcalera(sis, crc, mida)
    }
    fun esPartitura(h: ByteArray): Boolean = h.size >= 5 && h[0] == 'O'.code.toByte() && h[1] == 'X'.code.toByte() && h[2] == 'P'.code.toByte() && h[3] == 'T'.code.toByte() && h[4].toInt() == VERSIO

    /** Desxifra amb la ROM; null si no és el fitxer, o si la ROM no és la d'aquest fitxer (el xifrat falla). */
    fun llegeix(b: ByteArray, rom: ByteArray): Partitura? {
        val cap = capcalera(b) ?: return null
        if (cap.crc != crc32(rom) || cap.mida != rom.size) return null
        return try {
            val nonce = b.copyOfRange(14, 26); val n = DataInputStream(ByteArrayInputStream(b, 26, 4)).readInt()
            if (n < 16 || 30 + n > b.size) return null
            val ci = Cipher.getInstance("AES/GCM/NoPadding"); ci.init(Cipher.DECRYPT_MODE, clau(rom), GCMParameterSpec(128, nonce)); ci.updateAAD(b, 0, 14)
            val comprimit = ci.doFinal(b, 30, n)
            val inf = Inflater(); inf.setInput(comprimit); val bo = ByteArrayOutputStream(); val buf = ByteArray(65536)
            while (!inf.finished()) { val k = inf.inflate(buf); if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break; bo.write(buf, 0, k) }; inf.end()
            deCos(bo.toByteArray(), cap.crc, cap.mida)
        } catch (e: Throwable) { null }
    }
}
