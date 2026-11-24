package cat.gbdsmixxx

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Resultat d'analitzar una cançó d'un driver de ROM (GB, NES, SNES): el pols (període `t` en fotogrames del driver i `fase` = fotograma del batec 0), el bucle (`ini` i `llarg`
 * en fotogrames; `llarg` 0 = no s'ha trobat), si la cançó s'acaba sola (`fi` = fotograma de l'última nota; `acaba` = després d'ella hi ha hagut més de 8 s de silenci i no hi ha bucle) i l'histograma de les 12 notes. Tot són deduccions heurístiques.
 */
class AnalisiRitme(val t: Double, val fase: Double, val ini: Double, val llarg: Double, val fi: Int, val hist: DoubleArray?, val acaba: Boolean = false) {
    val tePols get() = t > 0.0
    val teBucle get() = llarg > 0.0
    companion object { val BUIDA = AnalisiRitme(0.0, 0.0, 0.0, 0.0, -1, null) }
}

/** Escriptures als registres de so i notes noves (en fotograma del driver) durant una passada en silenci. */
class RegSo(val maxFots: Int) {
    val hash = IntArray(maxFots + 1)          // totes les escriptures de cada fotograma
    val hashNotes = IntArray(maxFots + 1)     // només les notes noves (to inclòs)
    var fot = 0
    private val nF = ArrayList<Int>(); private val nC = ArrayList<Int>(); private val nM = ArrayList<Double>(); private val nK = ArrayList<Int>()
    /** Còpia dels primers `n` fotogrames (per analitzar-los en un altre fil mentre aquest segueix escrivint). */
    @Synchronized fun copia(n: Int): RegSo {
        val c = RegSo(maxFots)
        System.arraycopy(hash, 0, c.hash, 0, minOf(n + 1, hash.size)); System.arraycopy(hashNotes, 0, c.hashNotes, 0, minOf(n + 1, hashNotes.size))
        for (i in 0 until nF.size) if (nF[i] < n) { c.nF.add(nF[i]); c.nC.add(nC[i]); c.nM.add(nM[i]); c.nK.add(nK[i]) }
        return c
    }
    fun reg(a: Int, v: Int) { if (fot <= maxFots) hash[fot] = (hash[fot] * 16777619) xor (a * 256 + v + 1) }
    /** Nota nova al canal `canal` amb to MIDI `midi` (NaN si és soroll/percussió o no se sap). */
    @Synchronized fun nota(canal: Int, midi: Double, clau: Int) {
        if (fot > maxFots) return
        hashNotes[fot] = (hashNotes[fot] * 16777619) xor (canal * 65536 + clau + 1)
        nF.add(fot); nC.add(canal); nM.add(midi); nK.add(clau)
    }
    val nNotes get() = nF.size
    fun fotNota(i: Int) = nF[i]
    fun canalNota(i: Int) = nC[i]
    fun midiNota(i: Int) = nM[i]
    fun clauNota(i: Int) = nK[i]
}

object RitmeRom {
    private val cau = ConcurrentHashMap<String, Lazy<AnalisiRitme>>()
    // ---- Memòria cau persistent (l'autor, 2026-11-13: «super anàlisi de tot quan s'importa, i es desa a l'app»; 2026-11-16: «no desis en txt»).
    // Bloc de càrrega (2026-11-16): `ritme_cau2.bin` és un registre-diari (`Diari`, magic «OXRT»): cada registre porta la seva longitud i un CRC32, s'hi afegeix amb `fsync`
    // i, si l'app mor escrivint, només es perd l'últim. Un registre: UTF versió de l'anàlisi, UTF clau, pols, fase, inici del bucle, llargada, fi, si s'acaba i l'histograma.
    // La clau porta el hash de la ROM (`clauRom`), així que l'anàlisi es conserva lligada al contingut de la ROM (encara que canviï de lloc o es torni a afegir).
    // `VERSIO` s'ha de pujar quan canvia l'anàlisi (els registres d'una altra versió s'ignoren). El `ritme_cau.bin` (v1, sense CRC) i el `ritme_cau.txt` d'abans es migren
    // una vegada i es reanomenen a `.migrat` (no s'esborren). La càrrega es fa en un fil apart (no a l'arrencada, al fil principal).
    private const val VERSIO = "v1"
    private val MAGIC = Durable.magic("OXRT")
    @Volatile private var diari: Diari? = null
    @Volatile private var iniciat = false
    private val llest = java.util.concurrent.CountDownLatch(1)
    private fun registre(clau: String, a: AnalisiRitme): ByteArray {
        val bo = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bo).use { d ->
            d.writeUTF(VERSIO); d.writeUTF(clau); d.writeDouble(a.t); d.writeDouble(a.fase); d.writeDouble(a.ini); d.writeDouble(a.llarg); d.writeInt(a.fi); d.writeBoolean(a.acaba)
            val h = a.hist; d.writeInt(h?.size ?: -1); if (h != null) for (x in h) d.writeDouble(x)
        }
        return bo.toByteArray()
    }
    /** Arrenca la càrrega de la memòria cau del disc en un fil apart (un cop per procés, des de `MusicApp`); no bloqueja. */
    fun inicia(dir: java.io.File) {
        synchronized(this) { if (iniciat) return; iniciat = true }
        Thread { try { carregaAra(dir) } catch (e: Throwable) { } finally { llest.countDown() } }.also { it.isDaemon = true; it.priority = Thread.MIN_PRIORITY }.start()
    }
    /** Espera (com a molt `ms`) que la memòria cau del disc s'hagi carregat; mai al fil principal. */
    private fun esperaCarrega(ms: Long) { if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return; if (iniciat && llest.count > 0L) try { llest.await(ms, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (e: Throwable) { } }
    private fun carregaAra(dir: java.io.File) {
        val d = Diari(java.io.File(dir, "ritme_cau2.bin"), MAGIC, 1)
        var regs = 0
        d.llegeix { b ->
            regs++
            val i = java.io.DataInputStream(java.io.ByteArrayInputStream(b))
            if (i.readUTF() == VERSIO) {
                val clau = i.readUTF(); val t = i.readDouble(); val fase = i.readDouble(); val ini = i.readDouble(); val llarg = i.readDouble(); val fi = i.readInt(); val acaba = i.readBoolean()
                val n = i.readInt(); val hist = if (n < 0) null else DoubleArray(n) { i.readDouble() }
                cau.putIfAbsent(clau, lazyOf(AnalisiRitme(t, fase, ini, llarg, fi, hist, acaba)))
            }
        }
        diari = d
        buidaPendents(d)
        // el binari v1 d'abans (sense CRC per registre): es passa al diari i es reanomena
        val v1 = java.io.File(dir, "ritme_cau.bin")
        if (v1.exists()) {
            try {
                val bytes = v1.readBytes(); val bi = java.io.ByteArrayInputStream(bytes); val i = java.io.DataInputStream(bi)
                if (try { i.readUTF() == VERSIO } catch (e: Throwable) { false }) {
                    try {
                        while (bi.available() > 0) {
                            val clau = i.readUTF(); val t = i.readDouble(); val fase = i.readDouble(); val ini = i.readDouble(); val llarg = i.readDouble(); val fi = i.readInt(); val acaba = i.readBoolean()
                            val n = i.readInt(); if (n > 4096) break
                            val hist = if (n < 0) null else DoubleArray(n) { i.readDouble() }
                            val a = AnalisiRitme(t, fase, ini, llarg, fi, hist, acaba)
                            if (cau.putIfAbsent(clau, lazyOf(a)) == null) d.afegeix(registre(clau, a))
                        }
                    } catch (e: Throwable) { }          // registre tallat al final: es descarta
                }
                v1.renameTo(java.io.File(dir, "ritme_cau.bin.migrat"))
            } catch (e: Throwable) { }
        }
        // el fitxer de text més antic
        val vell = java.io.File(dir, "ritme_cau.txt")
        if (vell.exists()) {
            try {
                val ls = vell.readLines()
                if (ls.firstOrNull() == VERSIO) for (l in ls.drop(1)) {
                    val c = l.split('\t'); if (c.size < 8 || cau.containsKey(c[0])) continue
                    val hist = if (c[7] == "-") null else c[7].split(',').map { it.toDouble() }.toDoubleArray()
                    val a = AnalisiRitme(c[1].toDouble(), c[2].toDouble(), c[3].toDouble(), c[4].toDouble(), c[5].toInt(), hist, c[6] == "1")
                    if (cau.putIfAbsent(c[0], lazyOf(a)) == null) d.afegeix(registre(c[0], a))
                }
            } catch (e: Throwable) { }
            vell.renameTo(java.io.File(dir, "ritme_cau.txt.migrat"))
        }
        // compactació: si hi ha molts registres repetits o d'una altra versió, es reescriu (atòmic, amb .bak) només amb els vius
        if (regs > cau.size + 200) {
            val vius = cau.entries.mapNotNull { (k, l) -> if (l.isInitialized()) registre(k, l.value) else null }
            d.compacta(vius)
        }
    }
    /** Anàlisis fetes mentre el diari encara es llegia del disc (abans es perdien: no s'escrivien mai). S'escriuen en acabar la lectura o amb la desada següent. */
    private val pendents = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()
    private fun buidaPendents(d: Diari) { while (true) { val r = pendents.poll() ?: break; try { d.afegeix(r) } catch (e: Throwable) { } } }
    private fun desa(clau: String, a: AnalisiRitme) {
        val d = diari
        if (d == null) { if (iniciat && llest.count > 0L) pendents.add(registre(clau, a)); return }          // sense `inicia` (eines de PC) no hi ha disc: com abans
        buidaPendents(d)
        try { d.afegeix(registre(clau, a)) } catch (e: Throwable) { }
    }
    /** Desa l'anàlisi d'una cançó feta mentre sonava (SNES: bucle o final confirmats) si encara no n'hi ha cap a la memòria cau; així la propera vegada ja hi és. */
    fun guardaSiFalta(clau: String, a: AnalisiRitme) { if (cau.putIfAbsent(clau, lazyOf(a)) == null) desa(clau, a) }
    /** Quantes cançons tenen l'anàlisi feta (memòria cau). */
    fun fetes(): Int = cau.size
    const val BPM_MIN = 80.0
    const val BPM_MAX = 170.0

    /**
     * Bucle per la seqüència de notes noves (canal i to/mostra), sense mirar el temps: el període més petit `p` (en notes) tal que la cua de la seqüència repeteix la d'una volta abans
     * (almenys `minRep` notes i tres quarts de la volta) i que dura 20 s o més (una cançó pot tenir una frase que es repeteix més curta que el bucle). Retorna [inici, llargada] en fotogrames.
     */
    fun bucleSequencia(reg: RegSo, n: Int, minRep: Int = 48): DoubleArray? {
        val fr = ArrayList<Int>(); val sy = ArrayList<Int>()
        for (i in 0 until reg.nNotes) { val f = reg.fotNota(i); if (f < n) { fr.add(f); sy.add(reg.canalNota(i) * 1000003 + reg.clauNota(i)) } }
        val m = sy.size
        if (m < 2 * minRep) return null
        for (p in minRep / 2..m / 2) {
            var k = 0
            while (k < m - p && sy[m - 1 - k] == sy[m - 1 - k - p]) k++
            if (k >= maxOf(minRep, (p * 3) / 4) && m - k - p >= 0) {
                val ini = fr[m - k - p]; val llarg = fr[m - k] - ini
                if (llarg >= 1200) return doubleArrayOf(ini.toDouble(), llarg.toDouble())
            }
        }
        return null
    }

    /** Resultat de l'anàlisi dels primers `n` fotogrames d'un registre (per a la detecció mentre sona). */
    fun resultatDe(reg: RegSo, n: Int, fps: Double): AnalisiRitme = resultat(reg, n, fps, null)

    /** L'anàlisi d'una clau si ja és feta (no la calcula mai: es pot cridar des de la UI o el fil d'àudio). */
    fun cauOpt(clau: String): AnalisiRitme? { val l = cau[clau]; return if (l != null && l.isInitialized()) l.value else null }

    /** Anàlisi memoritzada (una sola vegada per clau = ROM + cançó); si falla, queda sense dades. */
    fun obte(clau: String, calcula: () -> AnalisiRitme): AnalisiRitme {
        if (!cau.containsKey(clau)) esperaCarrega(5000)          // la memòria cau del disc encara s'està llegint: s'espera una mica abans de calcular-la de nou
        return cau.computeIfAbsent(clau) { lazy(LazyThreadSafetyMode.SYNCHRONIZED) { FilsVius.analisi.incrementAndGet(); try { calcula().also { desa(clau, it) } } catch (e: Throwable) { AnalisiRitme.BUIDA } finally { FilsVius.analisi.decrementAndGet() } } }.value
    }

    /** Clau curta d'una ROM: mida i hash d'un mostreig (no es llegeix tota). */
    fun clauRom(rom: ByteArray): String {
        var h = 1125899906842597L
        val pas = maxOf(1, rom.size / 65536)
        var i = 0
        while (i < rom.size) { h = h * 31 + rom[i]; i += pas }
        return rom.size.toString() + ":" + java.lang.Long.toHexString(h)
    }

    /** To MIDI d'una freqüència en Hz. */
    fun midi(hz: Double) = if (hz > 0.0) 69.0 + 12.0 * ln(hz / 440.0) / ln(2.0) else Double.NaN

    /** Fotogrames d'una passada d'anàlisi: primer els del pols (`PRIMERS`), i es va allargant per trobar el bucle mentre no s'esgoti el temps. */
    fun fases(fps: Double): IntArray = intArrayOf((20 * fps).toInt(), (40 * fps).toInt(), (80 * fps).toInt(), (160 * fps).toInt(), (240 * fps).toInt())

    /**
     * Fa córrer `pas(f)` (un fotograma del driver, amb `reg.fot` posat) per etapes fins que es troba el bucle, la cançó acaba o s'esgota el temps (`msMax`), i en treu
     * el pols, el bucle i l'histograma. `fps` són els fotogrames de driver per segon.
     */
    fun analitza(reg: RegSo, fps: Double, msMax: Long, pas: (Int) -> Unit): AnalisiRitme {
        val t0 = System.currentTimeMillis()
        val etapes = fases(fps)
        var f = 0
        var res: AnalisiRitme? = null
        var pols: DoubleArray? = null
        var confirmat = false
        for ((e, lim) in etapes.withIndex()) {
            val fins = minOf(lim, reg.maxFots)
            while (f < fins) {
                reg.fot = f; pas(f); f++
                if (e > 0 && f % 120 == 0 && System.currentTimeMillis() - t0 > msMax) break      // sense temps: es queda amb el que hi ha (mai abans de la primera etapa)
            }
            val ant = res
            res = resultat(reg, f, fps, pols)
            if (pols == null && res.tePols) pols = doubleArrayOf(res.t, res.fase)
            // un bucle només es dóna per bo si l'etapa següent el confirma (si no, un tros repetit al final de la finestra podria passar per bucle)
            confirmat = res.teBucle && ant != null && ant.teBucle && abs(ant.ini - res.ini) <= 3.0 && abs(ant.llarg - res.llarg) <= 3.0
            if (confirmat || (res.fi >= 0 && f - res.fi > (8 * fps).toInt()) || System.currentTimeMillis() - t0 > msMax || f >= reg.maxFots || f < lim) break
        }
        val r = res ?: return AnalisiRitme.BUIDA
        return if (r.teBucle && !confirmat) AnalisiRitme(r.t, r.fase, 0.0, 0.0, r.fi, r.hist, false) else r
    }

    private fun resultat(reg: RegSo, n: Int, fps: Double, polsFix: DoubleArray?): AnalisiRitme {
        val nots = (0 until reg.nNotes).map { reg.fotNota(it) }.filter { it < n }
        val unics = nots.distinct().sorted().toIntArray()
        val fi = if (unics.isEmpty()) -1 else unics.last()
        val bucle = detectaBucle(reg.hash, n, unics) ?: detectaBucle(reg.hashNotes, n, unics) ?: detectaBucleNotes(reg, n)
        val pols = polsFix ?: polsDe(unics, fps)
        val hist = histograma(reg, n, bucle)
        return AnalisiRitme(pols?.get(0) ?: 0.0, pols?.get(1) ?: 0.0, bucle?.get(0) ?: 0.0, bucle?.get(1) ?: 0.0, fi, hist, bucle == null && fi >= 0 && n - fi > (8 * fps).toInt())
    }

    /**
     * Menor període P (mínim mig segon) i inici s tals que la seqüència `h` es repeteix sense cap diferència des de s fins al final de l'anàlisi `n`, i s'ha vist almenys
     * tres vegades (s + 3P <= n, perquè un tros que només es repeteix una vegada abans de tallar l'anàlisi no passi per bucle). El tros repetit ha de tenir notes (el silenci final d'una cançó sense bucle no compta). Retorna [s, P] o null.
     */
    fun detectaBucle(h: IntArray, n: Int, notes: IntArray): DoubleArray? {
        val minP = 30
        for (p in minP..n / 3) {
            var s = 0
            var f = n - p - 1
            while (f >= 0) { if (h[f] != h[f + p]) { s = f + 1; break }; f-- }
            if (s + 3 * p > n) continue
            var k = 0
            for (x in notes) if (x >= s && x < s + p) k++
            if (k >= 3) return doubleArrayOf(s.toDouble(), p.toDouble())
        }
        return null
    }

    /**
     * Bucle per la seqüència de notes noves (canal i to), amb una tolerància de +-3 fotogrames en el temps entre repeticions: els drivers amb tempo fraccionari mouen les notes
     * un fotograma d'una volta a l'altra i el hash exacte no serveix. Es busca el menor nombre de notes K tal que la seqüència es repeteixi cada K notes fins al final, amb un
     * període de temps constant, durant 2,5 períodes com a mínim. Retorna [inici, període] en fotogrames (període mitjà) o null.
     */
    fun detectaBucleNotes(reg: RegSo, n: Int): DoubleArray? {
        val m = reg.nNotes
        if (m < 9) return null
        for (k in 1..m / 3) {
            val dref = reg.fotNota(m - 1) - reg.fotNota(m - 1 - k)
            if (dref < 30) continue
            var s = 0
            var i = m - 1 - k
            while (i >= 0) {
                val d = reg.fotNota(i + k) - reg.fotNota(i)
                if (reg.canalNota(i) != reg.canalNota(i + k) || reg.clauNota(i) != reg.clauNota(i + k) || abs(d - dref) > 3) { s = i + 1; break }
                i--
            }
            if (s > m - 1 - k) continue
            var suma = 0.0
            for (j in s..m - 1 - k) suma += reg.fotNota(j + k) - reg.fotNota(j)
            val p = suma / (m - k - s)
            if (p < 30.0 || reg.fotNota(m - 1) - reg.fotNota(s) < 2.5 * p || k < 3) continue
            return doubleArrayOf(reg.fotNota(s).toDouble(), p)
        }
        return null
    }

    /** Pols [T, fase] en fotogrames (T a 80-170 BPM) a partir dels fotogrames de les notes noves; null si n'hi ha massa poques. */
    fun polsDe(on: IntArray, fps: Double): DoubleArray? {
        if (on.size < 8) return null
        // cerca grollera en els primers 10 s: màxim de coincidències (z) amb múltiples de T, tolerància +-1 fotograma
        val fin = (10 * fps).toInt()
        val prim = on.filter { it < fin }.toIntArray().let { if (it.size >= 8) it else on }
        val n = prim.size
        var mz = -1e9; var mT = 0.0; var mP = 0.0
        var t = 8.0
        val rel = 1.0 + 1.0 / fin
        // Coincidències per fase: abans es provava cada nota amb cada fase (fases × notes). Una nota només pot coincidir amb les fases a menys d'1 fotograma (± el període) de
        // la seva posició dins del període: es proven només les fases a menys d'1,5 fotogrames (amb la mateixa expressió de sempre), i la resta no poden fer «hit»
        // (són a més de 0,5 fotogrames del límit; l'error d'arrodoniment és de l'ordre de 1e-13). Mateix resultat, ~10 vegades menys feina.
        val hitsQ = IntArray(182)
        while (t <= 90.0) {
            val p0 = minOf(0.9, 3.0 / t)
            val sd = sqrt(n * p0 * (1 - p0))
            val passos = (t * 2).toInt()
            java.util.Arrays.fill(hitsQ, 0, passos, 0)
            for (x in prim) {
                val c = x - t * Math.floor(x / t)
                for (j in -1..1) {
                    val cc = c + j * t
                    val q0 = maxOf(0, Math.floor((cc - 1.5) * 2).toInt()); val q1 = minOf(passos - 1, Math.ceil((cc + 1.5) * 2).toInt())
                    for (q in q0..q1) { val ph = q * 0.5; val u = (x - ph) / t; if (abs(u - Math.rint(u)) * t <= 1.0) hitsQ[q]++ }
                }
            }
            for (q in 0 until passos) {
                val ph = q * 0.5
                val hits = hitsQ[q]
                val z = (hits - n * p0) / sd
                if (z > mz) { mz = z; mT = t; mP = ph }
            }
            t *= rel
        }
        // refinament per mínims quadrats creixent la finestra (T no és enter ni exacte)
        var T = mT; var a = mP
        var fins = fin
        val darrer = on.last()
        while (true) {
            val sk = ArrayList<Int>(); val sx = ArrayList<Int>()
            for (x in on) if (x < fins) { val u = (x - a) / T; if (abs(u - Math.rint(u)) * T <= 1.2) { sk.add(Math.rint(u).toInt()); sx.add(x) } }
            if (sk.size >= 4) {
                val m = sk.size.toDouble()
                val mk = sk.sum() / m; val mx = sx.sum() / m
                var num = 0.0; var den = 0.0
                for (i in sk.indices) { num += (sk[i] - mk) * (sx[i] - mx); den += (sk[i] - mk) * (sk[i] - mk) }
                if (den > 0.0) { val nT = num / den; if (nT > 0.0 && abs(nT - T) < T * 0.05) { T = nT; a = mx - T * mk } }
            }
            if (fins >= darrer + 1) break
            fins += maxOf(fin / 2, 1)
        }
        // plega'l a 80-170 BPM (si cal doblar T, tria l'alineació amb més coincidències)
        val tMin = fps * 60.0 / BPM_MAX; val tMax = fps * 60.0 / BPM_MIN
        fun hits(tt: Double, ph: Double): Int { var h = 0; for (x in on) { val u = (x - ph) / tt; if (abs(u - Math.rint(u)) * tt <= 1.2) h++ }; return h }
        var ph = a
        while (T < tMin) { val a1 = hits(T * 2, ph); val a2 = hits(T * 2, ph + T); if (a2 > a1) ph += T; T *= 2 }
        while (T > tMax) T /= 2
        // el batec 0 és el de la primera nota
        val k0 = Math.rint((on[0] - ph) / T)
        return doubleArrayOf(T, ph + k0 * T)
    }

    /** Histograma de les 12 notes pesat per la durada (fins a la nota següent del mateix canal, màxim 1,5 s) de les notes melòdiques fins al final del primer bucle. */
    private fun histograma(reg: RegSo, n: Int, bucle: DoubleArray?): DoubleArray? {
        val lim = if (bucle != null) (bucle[0] + bucle[1]).toInt() else n
        val h = DoubleArray(12)
        var tot = 0.0
        for (i in 0 until reg.nNotes) {
            val f = reg.fotNota(i); if (f >= lim) break
            val m = reg.midiNota(i); if (m.isNaN()) continue
            var seg = lim
            for (j in i + 1 until reg.nNotes) if (reg.canalNota(j) == reg.canalNota(i)) { seg = reg.fotNota(j); break }
            val w = minOf(seg - f, 90).coerceAtLeast(1).toDouble()
            val pc = (Math.rint(m).toInt() % 12 + 12) % 12
            h[pc] += w; tot += w
        }
        return if (tot > 0.0) h else null
    }
}

/**
 * Posició d'una cançó d'un driver de ROM a partir dels fotogrames reproduïts: batec, voltes i durada segons l'anàlisi. `salt` s'afegeix als fotogrames (després d'`analitza`, perquè
 * la còpia consumida quedi al principi del bucle amb una volta feta).
 */
class PosRitme(val a: AnalisiRitme) {
    @Volatile var salt = 0.0
    private fun absolut(pf: Double) = pf + salt
    private fun plegat(pf: Double): Double {
        val x = absolut(pf)
        return if (a.teBucle && x >= a.ini + a.llarg) a.ini + (x - a.ini) % a.llarg else x
    }
    fun batec(pf: Double): Double = if (!a.tePols) -1.0 else maxOf(0.0, (plegat(pf) - a.fase) / a.t)
    fun voltes(pf: Double): Int = if (!a.teBucle) 0 else { val x = absolut(pf); if (x < a.ini + a.llarg) 0 else ((x - a.ini) / a.llarg).toInt() }
    val batecMax: Double get() = if (!a.tePols) -1.0 else if (a.teBucle) (a.ini + a.llarg - a.fase) / a.t else if (a.acaba) (a.fi - a.fase) / a.t else -1.0
    fun bpm(fps: Double): Double = if (a.tePols) 60.0 * fps / a.t else 0.0
    /** Quin salt cal perquè la posició quedi al principi del bucle amb una volta feta. */
    fun saltAlBucle(): Double = if (a.teBucle) a.ini + a.llarg else 0.0
}
