package cat.gbdsmixxx

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/**
 * PlayStation (l'autor, 2026-11-23): la música de les imatges de disc que no cal emular, perquè és àudio, no seqüències:
 * - **XA** (CD-ROM XA, mode 2 forma 2): sectors amb subcapçalera (fitxer, canal, submode, codificació), 18 grups de 128 bytes per sector, ADPCM de 4 o 8 bits amb els
 *   mateixos filtres que la SPU. Un tema és la successió de sectors d'àudio d'un mateix fitxer i canal fins a la marca de final (submode bit 7). Verificat amb Tekken 3.
 * - **CD àudio** (pistes AUDIO del .cue): PCM de 16 bits estèreo a 44,1 kHz, tal qual.
 * La música seqüenciada (SEQ/VAB, AKAO...) encara no hi és. La imatge NO es copia (ocupa centenars de MB): es llegeix d'on és, i per això cal «Accés a tots els fitxers».
 * Es desa un descriptor binari per disc a `musica/ps1/<clau>.bin` (pistes i sectors) i la llista a `musica/ps1/index.bin`.
 */
object Ps1 {
    private val MAGIC = Durable.magic("OXP1")
    const val XA = 0; const val CD = 1
    class Pista(val tipus: Int, val ruta: String, val num: Int, val canal: Int, val estereo: Boolean, val taxa: Int, val bits8: Boolean, val sectors: IntArray, val ini: Long, val mida: Long) {
        /** Durada en segons. */
        val segons: Double get() = if (tipus == CD) mida / 4.0 / 44100 else sectors.size * (if (bits8) 4 else 8) * 18 * 28.0 / (if (estereo) 2 else 1) / taxa
        val nom: String get() = if (tipus == CD) t("CD àudio") + " " + num else "XA " + num
    }
    class Disc(val clau: String, val titol: String, val pistes: List<Pista>)

    private fun dir(ctx: Context) = File(RomsMusica.dir(ctx), "ps1")

    // ---------- reconeixement ----------
    private val SYNC = byteArrayOf(0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0)
    /** Mida del sector (2352 o 2048) si `f` és la pista de dades d'un disc de PlayStation (ISO 9660 amb «PLAYSTATION» com a sistema); 0 si no. */
    fun midaSector(f: RandomAccessFile): Int {
        if (f.length() < 17 * 2352) return 0
        val h = ByteArray(12); f.seek(0); f.readFully(h)
        val raw = h.contentEquals(SYNC)
        val pvd = sector(f, if (raw) 2352 else 2048, 16) ?: return 0
        return if (String(pvd, 1, 5, Charsets.ISO_8859_1) == "CD001" && String(pvd, 8, 32, Charsets.ISO_8859_1).trim() == "PLAYSTATION") (if (raw) 2352 else 2048) else 0
    }
    private fun sector(f: RandomAccessFile, sz: Int, n: Int): ByteArray? {
        if ((n + 1L) * sz > f.length()) return null
        val b = ByteArray(sz); f.seek(n.toLong() * sz); f.readFully(b)
        if (sz == 2048) return b
        val o = if (b[15].toInt() == 2) 24 else 16
        return b.copyOfRange(o, o + 2048)
    }
    /** Pista de dades d'un disc de PlayStation: sector 16 = descriptor ISO 9660 («CD001») amb «PLAYSTATION» com a sistema (imatge de 2352 o 2048 bytes per sector). */
    fun esDisc(ch: java.nio.channels.FileChannel): Boolean = try {
        val h = java.nio.ByteBuffer.allocate(16); ch.read(h, 0L)
        val raw = h.get(0) == 0.toByte() && (1..10).all { h.get(it) == 0xFF.toByte() } && h.get(11) == 0.toByte()
        val sz = if (raw) 2352L else 2048L
        if (ch.size() < 17 * sz) false else {
            val b = java.nio.ByteBuffer.allocate(sz.toInt()); ch.read(b, 16 * sz); val a = b.array()
            val o = if (!raw) 0 else if (a[15].toInt() == 2) 24 else 16
            String(a, o + 1, 5, Charsets.ISO_8859_1) == "CD001" && String(a, o + 8, 32, Charsets.ISO_8859_1).trim() == "PLAYSTATION"
        }
    } catch (e: Throwable) { false }
    /** Un fitxer triat (URI) és un disc de PlayStation o un .cue? Només llegeix la capçalera. */
    fun esPs1(ctx: Context, uri: android.net.Uri): Boolean = try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd -> java.io.FileInputStream(pfd.fileDescriptor).channel.use { ch ->
            val h = java.nio.ByteBuffer.allocate(0x200); ch.read(h, 0L); esCue(h.array()) || esDisc(ch) } } == true
    } catch (e: Throwable) { false }
    /** És un .cue (text amb FILE ... BINARY/TRACK)? Només mira la capçalera. */
    fun esCue(h: ByteArray): Boolean {
        val s = String(h, Charsets.ISO_8859_1).trimStart('﻿', ' ', '\r', '\n', '\t').uppercase()
        return (s.startsWith("FILE ") || s.startsWith("REM ") || s.startsWith("CATALOG ")) && s.contains("TRACK")
    }

    class TrackCue(val ruta: String, val num: Int, val audio: Boolean, val offset: Long, val pregap: Long = -1)
    /** Pistes d'un .cue (ruta absoluta de cada fitxer, número, si és d'àudio, el byte on comença l'INDEX 01 dins el seu fitxer i el de l'INDEX 00 —pregap—, -1 si no n'hi ha). */
    fun llegeixCue(cue: File): List<TrackCue> {
        val out = ArrayList<TrackCue>(); var fitxer: String? = null; var num = 0; var audio = false; var idx0 = -1L
        for (l0 in cue.readLines(Charsets.ISO_8859_1)) {
            val l = l0.trim()
            when {
                l.uppercase().startsWith("FILE ") -> { val q1 = l.indexOf('"'); val q2 = l.lastIndexOf('"'); fitxer = File(cue.parentFile, if (q1 >= 0 && q2 > q1) l.substring(q1 + 1, q2) else l.split(' ').getOrElse(1) { "" }).absolutePath }
                l.uppercase().startsWith("TRACK ") -> { val p = l.split(Regex("\\s+")); num = p.getOrNull(1)?.toIntOrNull() ?: 0; audio = p.getOrNull(2)?.uppercase() == "AUDIO"; idx0 = -1L }
                l.uppercase().startsWith("INDEX 00") -> {
                    val t = l.split(Regex("\\s+")).getOrNull(2)?.split(':') ?: continue
                    idx0 = (((t[0].toIntOrNull() ?: 0) * 60 + (t.getOrNull(1)?.toIntOrNull() ?: 0)) * 75 + (t.getOrNull(2)?.toIntOrNull() ?: 0)) * 2352L
                }
                l.uppercase().startsWith("INDEX 01") -> {
                    val t = l.split(Regex("\\s+")).getOrNull(2)?.split(':') ?: continue
                    val fr = ((t[0].toIntOrNull() ?: 0) * 60 + (t[1].toIntOrNull() ?: 0)) * 75 + (t[2].toIntOrNull() ?: 0)
                    fitxer?.let { out.add(TrackCue(it, num, audio, fr * 2352L, idx0)) }
                }
            }
        }
        return out
    }

    // ---------- anàlisi ----------
    /** Temes XA de la pista de dades (2352 bytes per sector; en una imatge de 2048 no hi ha XA). */
    fun temesXa(ruta: String, progres: ((Int) -> Unit)? = null): List<Pista> {
        val out = ArrayList<Pista>()
        RandomAccessFile(ruta, "r").use { f ->
            val n = (f.length() / 2352).toInt()
            val cur = HashMap<Int, ArrayList<Int>>(); val info = HashMap<Int, Int>()
            val blocs = 128; val b = ByteArray(blocs * 2352)
            var s = 0
            fun tanca(k: Int) { val l = cur.remove(k) ?: return; val co = info[k] ?: 0
                out.add(Pista(XA, ruta, 0, k and 255, co and 1 != 0, if ((co shr 2) and 3 == 1) 18900 else 37800, (co shr 4) and 3 == 1, l.toIntArray(), 0, 0)) }
            while (s < n) {
                val m = minOf(blocs, n - s); f.seek(s.toLong() * 2352); f.readFully(b, 0, m * 2352)
                for (i in 0 until m) {
                    val o = i * 2352
                    if (b[o + 15].toInt() != 2) continue
                    val sm = b[o + 18].toInt() and 255
                    if (sm and 4 == 0 || sm and 0x20 == 0) continue                    // àudio i forma 2
                    val k = ((b[o + 16].toInt() and 255) shl 8) or (b[o + 17].toInt() and 255)
                    if (b[o + 17].toInt() and 255 == 255) continue                      // canal FFh: sectors de farciment (Ace Combat 3, PSX-SPX «CDROM XA Subheader»; DuckStation els salta)
                    cur.getOrPut(k) { ArrayList() }.add(s + i); info[k] = b[o + 19].toInt() and 255
                    if (sm and 0x80 != 0) tanca(k)
                }
                s += m; progres?.invoke(s * 100 / n)
            }
            for (k in cur.keys.toList()) tanca(k)
        }
        // temes de menys d'1 s (restes) fora; en ordre de disc
        val bons = out.filter { it.segons >= 1.0 }.sortedBy { it.sectors[0] }
        return bons.mapIndexed { i, p -> Pista(XA, p.ruta, i + 1, p.canal, p.estereo, p.taxa, p.bits8, p.sectors, 0, 0) }
    }

    /** Analitza un disc a partir de la pista de dades (i el .cue, si n'hi ha); null si no és de PS1. */
    fun analitza(dades: String, cue: File?, titol: String, progres: ((Int) -> Unit)? = null): Disc? {
        val sz = RandomAccessFile(dades, "r").use { midaSector(it) }
        if (sz == 0) return null
        val clau = RandomAccessFile(dades, "r").use { f ->
            val c = java.util.zip.CRC32(); for (k in 16 until 32) sector(f, sz, k)?.let { c.update(it) }
            "%08X%08X".format(c.value, f.length() and 0xFFFFFFFFL)
        }
        val pistes = ArrayList<Pista>()
        if (sz == 2352) pistes += temesXa(dades, progres)
        if (cue != null) {
            val tr = llegeixCue(cue)
            for ((i, t) in tr.withIndex()) {
                if (!t.audio) continue
                val f = File(t.ruta); if (!f.isFile) continue
                val seg = tr.getOrNull(i + 1)?.takeIf { it.ruta == t.ruta }
                // la pista acaba on comença la següent: a l'INDEX 00 (pregap) de la següent, que ja és part d'aquella pista al .cue (PSX-SPX «CDROM Disk Images CUE/BIN/CDT»), no a l'INDEX 01
                val fi = seg?.let { if (it.pregap >= 0) it.pregap else it.offset } ?: f.length()
                if (fi > t.offset) pistes.add(Pista(CD, t.ruta, t.num, 0, true, 44100, false, IntArray(0), t.offset, fi - t.offset))
            }
        }
        return Disc(clau, titol, pistes)
    }

    // ---------- desament ----------
    fun desa(ctx: Context, d: Disc) {
        val dd = dir(ctx); dd.mkdirs()
        Durable.escriuDades(File(dd, d.clau + ".bin"), MAGIC, 1) { o ->
            Durable.escriuText(o, d.titol); o.writeInt(d.pistes.size)
            for (p in d.pistes) {
                o.writeInt(p.tipus); Durable.escriuText(o, p.ruta); o.writeInt(p.num); o.writeInt(p.canal); o.writeBoolean(p.estereo); o.writeInt(p.taxa); o.writeBoolean(p.bits8)
                o.writeInt(p.sectors.size); for (x in p.sectors) o.writeInt(x); o.writeLong(p.ini); o.writeLong(p.mida)
            }
        }
        val llista = llista(ctx).filter { it.first != d.clau } + (d.clau to d.titol)
        DadesBin.escriuTaula(File(dd, "index.bin"), llista.map { listOf(it.first, it.second) })
    }
    fun llista(ctx: Context): List<Pair<String, String>> = (DadesBin.llegeixTaula(File(dir(ctx), "index.bin")) ?: emptyList()).mapNotNull { if (it.size >= 2) it[0] to it[1] else null }
    fun carrega(ctx: Context, clau: String): Disc? = Durable.llegeixDades(File(dir(ctx), "$clau.bin"), MAGIC, 1) { _, i ->
        val titol = Durable.llegeixText(i); val n = i.readInt(); require(n in 0..10000)
        Disc(clau, titol, List(n) {
            val tipus = i.readInt(); val ruta = Durable.llegeixText(i); val num = i.readInt(); val canal = i.readInt(); val est = i.readBoolean(); val taxa = i.readInt(); val b8 = i.readBoolean()
            val ns = i.readInt(); require(ns in 0..2_000_000); val sec = IntArray(ns) { i.readInt() }
            Pista(tipus, ruta, num, canal, est, taxa, b8, sec, i.readLong(), i.readLong())
        })
    }
    fun te(ctx: Context, clau: String) = File(dir(ctx), "$clau.bin").exists()

    /** Importació des d'un fitxer triat (.bin, .iso o .cue). `ruta` és la ruta llegible del fitxer (cal «Accés a tots els fitxers»). Retorna el text del resultat o null + `RomsMusica.motiu`. */
    fun importa(ctx: Context, ruta: String?, nomFitxer: String, esCueF: Boolean): String? {
        if (ruta == null) return RomsMusica.no(t("Disc de PlayStation: cal «Accés a tots els fitxers» (Ajustos), perquè la imatge es llegeix d'on és i no es copia"))
        val f = File(ruta)
        var cue: File? = null; var dades: String = ruta
        if (esCueF) {
            cue = f
            val d = llegeixCue(f).firstOrNull { !it.audio } ?: return RomsMusica.no(t("El .cue no té cap pista de dades"))
            dades = d.ruta
            if (!File(dades).isFile) return RomsMusica.no(t("No es troba el fitxer de la pista de dades") + ": " + File(dades).name)
        } else {
            // el .cue de la mateixa carpeta que fa servir aquest fitxer (per a les pistes de CD àudio)
            cue = f.parentFile?.listFiles { x -> x.name.lowercase().endsWith(".cue") }?.firstOrNull { c -> try { llegeixCue(c).any { File(it.ruta).absolutePath == f.absolutePath } } catch (e: Throwable) { false } }
        }
        val titol = (cue?.name ?: File(dades).name).substringBeforeLast('.').replace(Regex("\\s*\\(Track \\d+\\)", RegexOption.IGNORE_CASE), "").trim()
        val disc = analitza(dades, cue, titol) ?: return RomsMusica.no(t("No és un disc de PlayStation"))
        if (te(ctx, disc.clau)) return RomsMusica.no(t("ja era a la biblioteca") + " (" + titol + ")")
        if (disc.pistes.isEmpty()) return RomsMusica.no(t("Disc de PlayStation sense música XA ni CD àudio (la música seqüenciada encara no es pot llegir)") + " (" + titol + ")")
        desa(ctx, disc)
        val nx = disc.pistes.count { it.tipus == XA }; val nc = disc.pistes.count { it.tipus == CD }
        return titol + " (PS1: " + nx + " XA, " + nc + " " + t("CD àudio") + ")"
    }

    fun crea(p: Pista, muts: BooleanArray): Sintetitzador = if (p.tipus == CD) SinteCdda(p, muts) else SinteXa(p, muts)
}

/**
 * Àudio XA d'un tema, com el fa el controlador de CD-ROM (2026-11-23):
 * - ADPCM: `(mostra << 12 o << 8) >> shift`, shift 13-15 = 9, i els dos termes del filtre `>> 6` per separat amb saturació a 16 bits (DuckStation cdrom.cpp
 *   `DecodeXAADPCMChunks`; Mednafen cdc.cpp `DecodeXAADPCM`; PSX-SPX «CDROM XA Audio ADPCM Compression» escriu `(old*f0+older*f1+32)/64`, ±1 LSB).
 *   L'historial continua d'un sector al següent (com al maquinari) i comença a zero.
 * - Remostreig a 44100 Hz del maquinari: 37800 Hz amb el filtre «zigzag» de 7 taules de 29 coeficients (PSX-SPX «25-point Zigzag Interpolation»; taules de DuckStation cdrom.inl
 *   `XAZigZagInterpolate`), 18900 Hz amb les 7 taules de 25 coeficients de Mednafen/DuckStation (`XAInterpolate18900`).
 * - Després, només si el mòbil no va a 44100 Hz, interpolació lineal de 44100 a `taux` (a 44100 Hz és exacte, mostra a mostra).
 * Els volums del CD-ROM (matriu L/R) i de la SPU (CD i principal) són del joc i no es coneixen sense executar-lo: aquí valen 1.
 */
class SinteXa(private val p: Ps1.Pista, private val muts: BooleanArray) : Sintetitzador {
    override val taux = RadioFreq.taux
    @Volatile override var solo = -1
    private val F0 = intArrayOf(0, 60, 115, 98); private val F1 = intArrayOf(0, 0, -52, -55)
    private val unitats = if (p.bits8) 4 else 8
    private val perSector = 18 * unitats * 28                      // mostres (totes: en estèreo, L i R entrellaçades)
    private val hist = IntArray(4)
    private var sec = 0
    // remostreig del CD-ROM
    private val ring = Array(2) { IntArray(32) }; private var rp = 0; private var six = 6
    private var cua = ShortArray(0); private var cuaN = 0; private var cuaI = 0   // frames de 44100 Hz (L, R)
    // 44100 → taux
    private var a0l = 0; private var a0r = 0; private var a1l = 0; private var a1r = 0; private var t = 0.0; private var iniciat = false
    private var fi = false; private var fet = false
    override fun pistaActiva(i: Int) = i == 0
    override val acabada: Boolean get() = fet
    override val teBucle: Boolean? get() = false

    private fun zigzag(c: Int, j: Int): Int {
        val tb = ZZ[j]; var sum = 0
        for (i in 1..29) sum += (ring[c][(rp - i) and 31] * tb[i - 1]) shr 15
        return sum.coerceIn(-32768, 32767)
    }
    private fun interp18900(c: Int, j: Int): Int {
        val tb = Z18[j]; var sum = 0
        for (i in 0 until 25) sum += ring[c][(rp - 25 + i) and 31] * tb[i]
        return (sum shr 15).coerceIn(-32768, 32767)
    }
    private fun posa(l: Int, r: Int) {
        if (cuaN * 2 + 2 > cua.size) cua = cua.copyOf(maxOf(64, cua.size * 2))
        cua[cuaN * 2] = l.toShort(); cua[cuaN * 2 + 1] = r.toShort(); cuaN++
    }
    /** Descodifica fins a 16 sectors i els passa pel remostreig del CD-ROM a la cua de 44100 Hz. */
    private fun omple(): Boolean {
        if (sec >= p.sectors.size) return false
        val n = minOf(16, p.sectors.size - sec)
        val dec = ShortArray(perSector); val sect = ByteArray(2352)
        cuaN = 0; cuaI = 0
        RandomAccessFile(p.ruta, "r").use { f ->
            for (k in 0 until n) {
                f.seek(p.sectors[sec + k].toLong() * 2352); f.readFully(sect)
                for (g in 0 until 18) {
                    val gb = 24 + g * 128
                    val o = g * unitats * 28
                    for (u in 0 until unitats) {
                        val par = sect[gb + 4 + u].toInt() and 255
                        val sh0 = par and 15; val sh = if (sh0 > 12) 9 else sh0; val fl = (par shr 4) and 3
                        val c = if (p.estereo) (u and 1) else 0
                        var s1 = hist[c * 2]; var s2 = hist[c * 2 + 1]
                        for (i in 0 until 28) {
                            val raw = if (p.bits8) sect[gb + 16 + i * 4 + u].toInt() shl 8 else {
                                val b = sect[gb + 16 + i * 4 + (u shr 1)].toInt() and 255
                                (if (u and 1 == 0) b and 15 else b shr 4) shl 12
                            }
                            val x = ((raw.toShort().toInt() shr sh) + ((s1 * F0[fl]) shr 6) + ((s2 * F1[fl]) shr 6)).coerceIn(-32768, 32767)
                            s2 = s1; s1 = x
                            val q = if (p.estereo) o + (u shr 1) * 56 + i * 2 + c else o + u * 28 + i
                            dec[q] = x.toShort()
                        }
                        hist[c * 2] = s1; hist[c * 2 + 1] = s2
                    }
                }
                val nf = if (p.estereo) perSector / 2 else perSector
                if (p.taxa == 37800) {
                    // DuckStation cdrom.cpp `ResampleXAADPCM`: 7 sortides per cada 6 entrades
                    for (i in 0 until nf) {
                        val l = if (p.estereo) dec[2 * i].toInt() else dec[i].toInt(); val r = if (p.estereo) dec[2 * i + 1].toInt() else l
                        ring[0][rp] = l; ring[1][rp] = r; rp = (rp + 1) and 31
                        if (--six == 0) { six = 6; for (j in 0 until 7) { val zl = zigzag(0, j); posa(zl, if (p.estereo) zigzag(1, j) else zl) } }
                    }
                } else {
                    // `ResampleXAADPCM18900`: 7 sortides per cada 3 entrades
                    var i = 0
                    while (i < nf) {
                        if (six >= 7) {
                            six -= 7; rp = (rp + 1) and 31
                            val l = if (p.estereo) dec[2 * i].toInt() else dec[i].toInt()
                            ring[0][rp] = l; ring[1][rp] = if (p.estereo) dec[2 * i + 1].toInt() else l; i++
                        }
                        val zl = interp18900(0, six); posa(zl, if (p.estereo) interp18900(1, six) else zl); six += 3
                    }
                }
            }
        }
        sec += n
        return true
    }
    /** Següent frame de 44100 Hz a (a1l, a1r); false si s'ha acabat. */
    private fun seguent(): Boolean {
        while (cuaI >= cuaN) if (!omple()) return false
        a1l = cua[cuaI * 2].toInt(); a1r = cua[cuaI * 2 + 1].toInt(); cuaI++
        return true
    }
    override fun renderitza(out: ShortArray, frames: Int) {
        val pas = 44100.0 / taux
        val g = !(muts.getOrElse(0) { false } || (solo >= 0 && solo != 0))
        if (!iniciat) { iniciat = true; if (seguent()) { a0l = a1l; a0r = a1r; if (!seguent()) fi = true } else { fi = true; fet = true } }
        for (i in 0 until frames) {
            var l = 0; var r = 0
            if (!fet) {
                l = (a0l + (a1l - a0l) * t).toInt(); r = (a0r + (a1r - a0r) * t).toInt()
                t += pas
                while (t >= 1.0 && !fet) { t -= 1.0; a0l = a1l; a0r = a1r; if (fi) fet = true else if (!seguent()) { fi = true; a1l = a0l; a1r = a0r } }
            }
            if (!g) { l = 0; r = 0 }
            out[2 * i] = l.coerceIn(-32768, 32767).toShort(); out[2 * i + 1] = r.coerceIn(-32768, 32767).toShort()
        }
    }

    companion object {
        /** 37800 → 44100 Hz: 7 taules × 29 coeficients (PSX-SPX «25-point Zigzag Interpolation», índexs 1-29; = DuckStation cdrom.inl `XAZigZagInterpolate`). */
        val ZZ = arrayOf(
            intArrayOf(0, 0, 0, 0, 0, -0x0002, 0x000A, -0x0022, 0x0041, -0x0054, 0x0034, 0x0009, -0x010A, 0x0400, -0x0A78, 0x234C, 0x6794, -0x1780, 0x0BCD, -0x0623, 0x0350, -0x016D, 0x006B, 0x000A, -0x0010, 0x0011, -0x0008, 0x0003, -0x0001),
            intArrayOf(0, 0, 0, -0x0002, 0, 0x0003, -0x0013, 0x003C, -0x004B, 0x00A2, -0x00E3, 0x0132, -0x0043, -0x0267, 0x0C9D, 0x74BB, -0x11B4, 0x09B8, -0x05BF, 0x0372, -0x01A8, 0x00A6, -0x001B, 0x0005, 0x0006, -0x0008, 0x0003, -0x0001, 0),
            intArrayOf(0, 0, -0x0001, 0x0003, -0x0002, -0x0005, 0x001F, -0x004A, 0x00B3, -0x0192, 0x02B1, -0x039E, 0x04F8, -0x05A6, 0x7939, -0x05A6, 0x04F8, -0x039E, 0x02B1, -0x0192, 0x00B3, -0x004A, 0x001F, -0x0005, -0x0002, 0x0003, -0x0001, 0, 0),
            intArrayOf(0, -0x0001, 0x0003, -0x0008, 0x0006, 0x0005, -0x001B, 0x00A6, -0x01A8, 0x0372, -0x05BF, 0x09B8, -0x11B4, 0x74BB, 0x0C9D, -0x0267, -0x0043, 0x0132, -0x00E3, 0x00A2, -0x004B, 0x003C, -0x0013, 0x0003, 0, -0x0002, 0, 0, 0),
            intArrayOf(-0x0001, 0x0003, -0x0008, 0x0011, -0x0010, 0x000A, 0x006B, -0x016D, 0x0350, -0x0623, 0x0BCD, -0x1780, 0x6794, 0x234C, -0x0A78, 0x0400, -0x010A, 0x0009, 0x0034, -0x0054, 0x0041, -0x0022, 0x000A, -0x0001, 0, 0x0001, 0, 0, 0),
            intArrayOf(0x0002, -0x0008, 0x0010, -0x0023, 0x002B, 0x001A, -0x00EB, 0x027B, -0x0548, 0x0AFA, -0x16FA, 0x53E0, 0x3C07, -0x1249, 0x080E, -0x0347, 0x015B, -0x0044, -0x0017, 0x0046, -0x0023, 0x0011, -0x0005, 0, 0, 0, 0, 0, 0),
            intArrayOf(-0x0005, 0x0011, -0x0023, 0x0046, -0x0017, -0x0044, 0x015B, -0x0347, 0x080E, -0x1249, 0x3C07, 0x53E0, -0x16FA, 0x0AFA, -0x0548, 0x027B, -0x00EB, 0x001A, 0x002B, -0x0023, 0x0010, -0x0008, 0x0002, 0, 0, 0, 0, 0, 0))
        /** 18900 → 44100 Hz: 7 taules × 25 coeficients (DuckStation cdrom.inl `XAInterpolate18900`, pesos de Mednafen). */
        val Z18 = arrayOf(
            intArrayOf(0x0, -0x5, 0x11, -0x23, 0x46, -0x17, -0x44, 0x15b, -0x347, 0x80e, -0x1249, 0x3c07, 0x53e0, -0x16fa, 0xafa, -0x548, 0x27b, -0xeb, 0x1a, 0x2b, -0x23, 0x10, -0x8, 0x2, 0x0),
            intArrayOf(0x0, -0x2, 0xa, -0x22, 0x41, -0x54, 0x34, 0x9, -0x10a, 0x400, -0xa78, 0x234c, 0x6794, -0x1780, 0xbcd, -0x623, 0x350, -0x16d, 0x6b, 0xa, -0x10, 0x11, -0x8, 0x3, -0x1),
            intArrayOf(-0x2, 0x0, 0x3, -0x13, 0x3c, -0x4b, 0xa2, -0xe3, 0x132, -0x43, -0x267, 0xc9d, 0x74bb, -0x11b4, 0x9b8, -0x5bf, 0x372, -0x1a8, 0xa6, -0x1b, 0x5, 0x6, -0x8, 0x3, -0x1),
            intArrayOf(-0x1, 0x3, -0x2, -0x5, 0x1f, -0x4a, 0xb3, -0x192, 0x2b1, -0x39e, 0x4f8, -0x5a6, 0x7939, -0x5a6, 0x4f8, -0x39e, 0x2b1, -0x192, 0xb3, -0x4a, 0x1f, -0x5, -0x2, 0x3, -0x1),
            intArrayOf(-0x1, 0x3, -0x8, 0x6, 0x5, -0x1b, 0xa6, -0x1a8, 0x372, -0x5bf, 0x9b8, -0x11b4, 0x74bb, 0xc9d, -0x267, -0x43, 0x132, -0xe3, 0xa2, -0x4b, 0x3c, -0x13, 0x3, 0x0, -0x2),
            intArrayOf(-0x1, 0x3, -0x8, 0x11, -0x10, 0xa, 0x6b, -0x16d, 0x350, -0x623, 0xbcd, -0x1780, 0x6794, 0x234c, -0xa78, 0x400, -0x10a, 0x9, 0x34, -0x54, 0x41, -0x22, 0xa, -0x2, 0x0),
            intArrayOf(0x0, 0x2, -0x8, 0x10, -0x23, 0x2b, 0x1a, -0xeb, 0x27b, -0x548, 0xafa, -0x16fa, 0x53e0, 0x3c07, -0x1249, 0x80e, -0x347, 0x15b, -0x44, -0x17, 0x46, -0x23, 0x11, -0x5, 0x0))
    }
}

/** Pista de CD àudio: PCM de 16 bits estèreo a 44,1 kHz, llegit a trossos. */
class SinteCdda(private val p: Ps1.Pista, private val muts: BooleanArray) : Sintetitzador {
    override val taux = RadioFreq.taux
    @Volatile override var solo = -1
    private val total = p.mida / 4
    private val buf = ShortArray(2 * 16384); private var bufIni = -1L; private var bufN = 0
    private var pos = 0.0
    override fun pistaActiva(i: Int) = i == 0
    override val acabada: Boolean get() = pos >= total
    override val teBucle: Boolean? get() = false
    private fun mostra(k: Long, c: Int): Int {
        if (k < 0 || k >= total) return 0
        if (k < bufIni || k >= bufIni + bufN) {
            val n = minOf(16384L, total - k).toInt(); val b = ByteArray(n * 4)
            RandomAccessFile(p.ruta, "r").use { f -> f.seek(p.ini + k * 4); f.readFully(b) }
            for (i in 0 until n * 2) buf[i] = ((b[2 * i].toInt() and 255) or (b[2 * i + 1].toInt() shl 8)).toShort()
            bufIni = k; bufN = n
        }
        return buf[((k - bufIni) * 2).toInt() + c].toInt()
    }
    override fun renderitza(out: ShortArray, frames: Int) {
        val pas = 44100.0 / taux
        val g = !(muts.getOrElse(0) { false } || (solo >= 0 && solo != 0))
        for (i in 0 until frames) {
            var l = 0; var r = 0
            if (g && pos < total) {
                val k = pos.toLong(); val fr = pos - k
                val a = mostra(k, 0); val b = mostra(k + 1, 0); l = (a + (b - a) * fr).toInt()
                val a2 = mostra(k, 1); val b2 = mostra(k + 1, 1); r = (a2 + (b2 - a2) * fr).toInt()
            }
            out[2 * i] = l.coerceIn(-32768, 32767).toShort(); out[2 * i + 1] = r.coerceIn(-32768, 32767).toShort()
            pos += pas
        }
    }
}
