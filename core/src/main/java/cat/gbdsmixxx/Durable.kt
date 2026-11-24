package cat.gbdsmixxx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32

/**
 * Escriptura atòmica i verificada de les dades de l'app que no són ROM (l'autor, 2026-11-16: «mai dades de l'app en TXT»).
 * Format d'un fitxer: `magic` (int, diu de quin fitxer es tracta), `versio` (int), `longitud` (int), `crc32` de la càrrega (int) i la càrrega.
 * Escriure: la versió bona anterior passa a `<nom>.bak`, la nova s'escriu a `<nom>.tmp`, es fa `fsync` i es reanomena a sobre (moviment atòmic).
 * Llegir: si el fitxer està tallat o el CRC no lliga, es llegeix el `.bak`; si cap dels dos és bo, null (i no es toca res).
 * Kotlin pur (sense Android): es prova a JVM.
 */
object Durable {
    class Lectura(val versio: Int, val dades: ByteArray, val delBak: Boolean)

    fun bak(f: File) = File(f.path + ".bak")
    private fun tmp(f: File) = File(f.path + ".tmp")

    fun crc(b: ByteArray, off: Int = 0, n: Int = b.size): Int { val c = CRC32(); c.update(b, off, n); return c.value.toInt() }

    /** Escriu `dades` a `f` de manera atòmica (tmp + fsync + rename) i deixa l'anterior bona a `.bak`. Llença IOException si no pot. */
    @Synchronized fun escriu(f: File, magic: Int, versio: Int, dades: ByteArray) {
        f.parentFile?.mkdirs()
        val ex = if (f.exists()) try { llegeixUn(f, magic) } catch (e: Throwable) { null } else null
        // un fitxer d'una versió MÉS NOVA (una app més nova l'ha escrit i ara n'hi ha una de més vella) no es perd: es guarda una sola vegada a `<nom>.v<N>`
        // (abans: la primera escriptura el passava al .bak i la segona el sobreescrivia)
        if (ex != null && ex.first > versio) {
            val cv = File(f.path + ".v" + ex.first)
            if (!cv.exists()) { val tv = tmp(cv); Files.copy(f.toPath(), tv.toPath(), StandardCopyOption.REPLACE_EXISTING); sincronitza(tv); mou(tv, cv) }
        }
        // la versió anterior, si és bona, es copia a .bak (també atòmicament): així un fitxer nou tallat sempre té una còpia sencera al costat
        if (ex != null) {
            val tb = tmp(bak(f))
            Files.copy(f.toPath(), tb.toPath(), StandardCopyOption.REPLACE_EXISTING)
            sincronitza(tb)
            mou(tb, bak(f))
        }
        val t = tmp(f)
        FileOutputStream(t, false).use { o ->
            val cap = ByteArrayOutputStream(16)
            DataOutputStream(cap).use { d -> d.writeInt(magic); d.writeInt(versio); d.writeInt(dades.size); d.writeInt(crc(dades)) }
            o.write(cap.toByteArray()); o.write(dades); o.flush(); o.fd.sync()
        }
        mou(t, f)
    }

    private fun sincronitza(f: File) { RandomAccessFile(f, "rw").use { it.fd.sync() } }
    private fun mou(de: File, a: File) {
        try { Files.move(de.toPath(), a.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (e: java.nio.file.AtomicMoveNotSupportedException) { Files.move(de.toPath(), a.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }

    /** (versió, càrrega) d'un sol fitxer si és sencer i bo; null si no. */
    private fun llegeixUn(f: File, magic: Int): Pair<Int, ByteArray>? {
        if (!f.exists() || f.length() < 16) return null
        val b = f.readBytes()
        val d = DataInputStream(ByteArrayInputStream(b))
        if (d.readInt() != magic) return null
        val v = d.readInt(); val n = d.readInt(); val c = d.readInt()
        if (n < 0 || 16 + n != b.size) return null
        if (crc(b, 16, n) != c) return null
        return v to b.copyOfRange(16, 16 + n)
    }

    /** Llegeix `f` (o el seu `.bak` si `f` no és bo). Les versions més noves que `versioMax` es tracten com a no llegibles (no es toquen). */
    fun llegeix(f: File, magic: Int, versioMax: Int): Lectura? {
        for ((k, x) in listOf(f, bak(f)).withIndex()) {
            val r = try { llegeixUn(x, magic) } catch (e: Throwable) { null } ?: continue
            if (r.first > versioMax || r.first < 1) continue
            return Lectura(r.first, r.second, k == 1)
        }
        return null
    }

    /** Escriu amb un `DataOutputStream`. */
    fun escriuDades(f: File, magic: Int, versio: Int, cos: (DataOutputStream) -> Unit) {
        val bo = ByteArrayOutputStream()
        DataOutputStream(bo).use { cos(it) }
        escriu(f, magic, versio, bo.toByteArray())
    }

    /** Llegeix amb un `DataInputStream`; null si no hi ha cap còpia bona o si `cos` falla (dades incoherents). */
    fun <T> llegeixDades(f: File, magic: Int, versioMax: Int, cos: (Int, DataInputStream) -> T): T? {
        val l = llegeix(f, magic, versioMax) ?: return null
        return try { cos(l.versio, DataInputStream(ByteArrayInputStream(l.dades))) } catch (e: Throwable) {
            // la principal té CRC bo però un contingut que no s'entén (no hauria de passar): es prova el .bak
            if (l.delBak) null else try {
                val b = llegeix(bak(f), magic, versioMax) ?: return null
                cos(b.versio, DataInputStream(ByteArrayInputStream(b.dades)))
            } catch (x: Throwable) { null }
        }
    }

    /** Magic de 4 lletres (per llegir-lo fàcilment amb un editor hexadecimal). */
    fun magic(s: String): Int = (s[0].code shl 24) or (s[1].code shl 16) or (s[2].code shl 8) or s[3].code

    /** Escriu una cadena sense el límit de 64 KB de `writeUTF`. */
    fun escriuText(d: DataOutputStream, s: String) { val b = s.toByteArray(Charsets.UTF_8); d.writeInt(b.size); d.write(b) }
    fun llegeixText(d: DataInputStream): String { val n = d.readInt(); require(n in 0..(64 shl 20)); val b = ByteArray(n); d.readFully(b); return String(b, Charsets.UTF_8) }
}

/**
 * Registre-diari binari: capçalera (`magic`, `versio`) i registres `[longitud][crc32][càrrega]` que s'afegeixen al final amb `fsync`.
 * En llegir, un registre tallat o amb CRC dolent atura la lectura i el fitxer es talla a l'últim registre sencer (es perd, com a molt, l'últim canvi).
 * `compacta` reescriu el diari sencer (només els registres vius) de manera atòmica, amb `.bak`.
 */
class Diari(val f: File, private val magic: Int, private val versio: Int) {
    @Volatile var registres = 0; private set

    /** Llegeix tots els registres bons (de `f` o, si la capçalera no és bona, del `.bak`). Retorna la versió del fitxer (0 si no n'hi ha cap). */
    @Synchronized fun llegeix(cada: (ByteArray) -> Unit): Int {
        registres = 0
        conservaMesNou()
        if (f.exists() && f.length() >= 8) {
            val v = try { llegeixDe(f, cada, true) } catch (e: Throwable) { -1 }
            if (v > 0) return v
        }
        // la principal no és bona (capçalera malmesa): es llegeix el .bak i es refà la principal a partir d'ell (sense tocar el .bak)
        val b = Durable.bak(f)
        if (b.exists() && b.length() >= 8) {
            val regs = ArrayList<ByteArray>()
            val v = try { llegeixDe(b, { regs.add(it); cada(it) }, false) } catch (e: Throwable) { -1 }
            if (v > 0) { try { escriuTot(regs, false) } catch (e: Throwable) { }; registres = regs.size; return v }
        }
        return 0
    }

    /**
     * P8 també al diari (`dades_usuari.bin`): si el fitxer és d'una versió MÉS NOVA, abans de res (llegir el `.bak` i refer-lo, o afegir-hi registres) se'n guarda una còpia
     * a `<nom>.v<N>`, una sola vegada; abans es perdia (es refeia des del `.bak` o s'hi afegien registres del format vell).
     */
    private fun conservaMesNou() {
        try {
            if (!f.exists() || f.length() < 8) return
            val cap = java.io.DataInputStream(java.io.FileInputStream(f)).use { d -> d.readInt() to d.readInt() }
            if (cap.first != magic || cap.second <= versio) return
            val cv = File(f.path + ".v" + cap.second); if (cv.exists()) return
            val t = File(cv.path + ".tmp")
            Files.copy(f.toPath(), t.toPath(), StandardCopyOption.REPLACE_EXISTING)
            RandomAccessFile(t, "rw").use { it.fd.sync() }
            Files.move(t.toPath(), cv.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Throwable) { }
    }

    private fun llegeixDe(x: File, cada: (ByteArray) -> Unit, potTallar: Boolean): Int {
        val tot = x.readBytes()                      // els diaris són petits (KB o pocs MB): es llegeixen d'un cop i es recorren en memòria
        if (tot.size < 8) return -1
        val bb = java.nio.ByteBuffer.wrap(tot)
        if (bb.getInt(0) != magic) return -1
        val v = bb.getInt(4)
        if (v < 1 || v > versio) return -1
        var bo = 8
        while (bo + 8 <= tot.size) {
            val n = bb.getInt(bo); val c = bb.getInt(bo + 4)
            if (n < 0 || bo.toLong() + 8 + n > tot.size) break
            if (Durable.crc(tot, bo + 8, n) != c) break
            cada(tot.copyOfRange(bo + 8, bo + 8 + n)); registres++
            bo += 8 + n
        }
        if (bo < tot.size && potTallar) RandomAccessFile(x, "rw").use { r -> r.setLength(bo.toLong()); r.fd.sync() }          // cua tallada o bruta: es descarta
        return v
    }

    /** Afegeix un registre al final (crea el fitxer amb la capçalera si no existeix) i fa `fsync`. */
    @Synchronized fun afegeix(dades: ByteArray) {
        f.parentFile?.mkdirs()
        if (!f.exists() || f.length() < 8) escriuTot(emptyList(), false)
        RandomAccessFile(f, "rw").use { r ->
            r.seek(r.length())
            val bo = ByteArrayOutputStream(dades.size + 8)
            DataOutputStream(bo).use { d -> d.writeInt(dades.size); d.writeInt(Durable.crc(dades)); d.write(dades) }
            r.write(bo.toByteArray()); r.fd.sync()
        }
        registres++
    }

    /** Reescriu el diari amb només aquests registres (atòmic: tmp + fsync + rename, i l'anterior a `.bak`). */
    @Synchronized fun compacta(regs: List<ByteArray>) { escriuTot(regs, true); registres = regs.size }

    private fun escriuTot(regs: List<ByteArray>, ferBak: Boolean) {
        f.parentFile?.mkdirs()
        if (ferBak && f.exists() && f.length() >= 8) {
            val tb = File(Durable.bak(f).path + ".tmp")
            Files.copy(f.toPath(), tb.toPath(), StandardCopyOption.REPLACE_EXISTING)
            RandomAccessFile(tb, "rw").use { it.fd.sync() }
            Files.move(tb.toPath(), Durable.bak(f).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        val t = File(f.path + ".tmp")
        FileOutputStream(t, false).use { o ->
            val bo = ByteArrayOutputStream()
            DataOutputStream(bo).use { d ->
                d.writeInt(magic); d.writeInt(versio)
                for (x in regs) { d.writeInt(x.size); d.writeInt(Durable.crc(x)); d.write(x) }
            }
            o.write(bo.toByteArray()); o.flush(); o.fd.sync()
        }
        Files.move(t.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

/** Memòria cau limitada per BYTES (no per nombre d'elements): en passar del límit es treuen les menys usades, però l'última que s'hi posa sempre s'hi queda. */
class LruBytes<K : Any, V : Any>(@Volatile var maxBytes: Long, private val mida: (V) -> Long) {
    private val m = LinkedHashMap<K, V>(16, 0.75f, true)
    private var total = 0L
    @Synchronized fun get(k: K): V? = m[k]
    @Synchronized fun put(k: K, v: V) {
        m.remove(k)?.let { total -= mida(it) }
        m[k] = v; total += mida(v)
        val it = m.entries.iterator()
        while (total > maxBytes && m.size > 1 && it.hasNext()) { val e = it.next(); if (e.key == k) continue; total -= mida(e.value); it.remove() }
    }
    @Synchronized fun remove(k: K): V? = m.remove(k)?.also { total -= mida(it) }
    @Synchronized fun removeIf(p: (K) -> Boolean) { val it = m.entries.iterator(); while (it.hasNext()) { val e = it.next(); if (p(e.key)) { total -= mida(e.value); it.remove() } } }
    @Synchronized fun clear() { m.clear(); total = 0 }
    @Synchronized fun bytes() = total
    @Synchronized fun size() = m.size
    @Synchronized fun claus(): List<K> = m.keys.toList()
}
