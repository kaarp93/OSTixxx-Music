package cat.ostixxx.music

// Kotlin pur (sense Android ni Compose): dades del mode concert (2026-11-17), provades a JVM (eines/prototips/ui/prova.sh).

/**
 * Batecs per al mode concert. Si el motor dona el pols (`bpm` > 0 i `batec` ≥ 0: GB amb CPU, SNES, GBA, DS), cada batec és el pas a un batec sencer nou
 * (amb `RETARD` segons de menys: el motor sintetitza una mica abans del que se sent). Si no (NES, fitxers...), es busquen pics de greus: el nivell dels greus
 * (0-1) puja per sobre de la seva mitjana lenta; com a molt un batec cada `MIN_S` segons.
 */
class DetectorBatec(private val avanc: Double = 0.0) {
    /** Segons des de l'últim batec (gran si no n'hi ha hagut). */
    var desDe = 99f; private set
    /** D'on surt l'últim batec: true = pols del motor, false = greus. */
    var delMotor = false; private set
    private var ultim = Long.MIN_VALUE
    private var mitja = 0f
    private var ant = 0f

    /** Un pas de `dt` segons. Torna true si hi ha un batec nou. Sense so (`actiu` = false) no n'hi ha cap i es reinicia. */
    fun pas(dt: Float, actiu: Boolean, batec: Double, bpm: Double, greus: Float): Boolean {
        desDe += dt
        if (!actiu) { ultim = Long.MIN_VALUE; mitja = 0f; ant = 0f; return false }
        if (bpm > 0.0 && batec >= 0.0) {
            delMotor = true
            val b = batec - bpm / 60.0 * (RETARD - avanc)          // `avanc`: el batec arriba abans (el combat comença el salt perquè el cop caigui al batec)
            val n = Math.floor(b).toLong()
            val nou = ultim != Long.MIN_VALUE && n > ultim && desDe >= MIN_S
            ultim = n          // enrere (bucle, salt) o massa a prop de l'anterior: es posa al dia sense batec
            if (nou) { desDe = 0f; return true }
            return false
        }
        delMotor = false
        val g = greus.coerceIn(0f, 1f)
        val k = 1f - Math.exp(-dt / 0.6).toFloat()
        val puja = g > ant
        ant = g
        val hi = g > mitja * 1.25f + 0.08f && puja && desDe >= MIN_S
        mitja += (g - mitja) * k
        if (hi) { desDe = 0f; return true }
        return false
    }

    companion object {
        const val RETARD = 0.10
        const val MIN_S = 0.24f
    }
}

/**
 * Notes que sonen a cada canal, **estimades** a partir de la forma d'ona del canal (els motors no donen les notes): YIN (diferència normalitzada
 * acumulada) sobre les últimes mostres de l'anell de l'oscil·loscopi, a `taxa` Hz (els motors hi escriuen a la freqüència de so / 4).
 * Torna la nota MIDI (amb decimals) o -1 si el canal calla, és soroll o no és prou periòdic.
 */
object NotesCanal {
    const val LLINDAR = 0.15f
    private const val W = 256          // finestra de comparació
    private const val TMAX = 255       // retard màxim (≈ 32 Hz a 8 kHz)
    private const val TMIN = 3         // retard mínim (≈ 2,7 kHz a 8 kHz)

    /** `tmp` ha de tenir com a mínim W + TMAX + 1 posicions (reutilitzat: cap assignació). */
    fun estima(b: FloatArray, n: Int, pos: Int, taxa: Float, tmp: FloatArray, d: FloatArray): Float {
        val tot = W + TMAX
        if (n < tot || taxa <= 0f) return -1f
        // les `tot` mostres més recents, en ordre
        val ini = pos - tot
        var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE
        for (i in 0 until tot) { val v = b[((ini + i) % n + n) % n]; tmp[i] = v; if (v < mn) mn = v; if (v > mx) mx = v }
        if (mx - mn < 0.01f) return -1f          // calla
        // suavitzat de 3 punts: els flancs de les quadrades agudes tremolen menys
        var p0 = tmp[0]; var p1 = tmp[1]
        for (i in 1 until tot - 1) { val nx = tmp[i + 1]; tmp[i] = (p0 + 2f * p1 + nx) * 0.25f; p0 = p1; p1 = nx }
        // diferència i normalització acumulada
        var acum = 0f
        d[0] = 1f
        for (t in 1..TMAX) {
            var s = 0f
            for (j in 0 until W) { val x = tmp[j] - tmp[j + t]; s += x * x }
            acum += s
            d[t] = if (acum <= 0f) 1f else s * t / acum
        }
        // primer mínim per sota del llindar; el llindar s'apuja una mica si el millor mínim és alt (les mostres a 1/4 de la freqüència fan tremolar els flancs de les notes agudes)
        var mn2 = Float.MAX_VALUE
        for (k in TMIN..TMAX) if (d[k] < mn2) mn2 = d[k]
        if (mn2 > 0.4f) return -1f
        val llindar = maxOf(LLINDAR, mn2 + 0.2f)
        var t = TMIN
        while (t <= TMAX && d[t] >= llindar) t++
        if (t > TMAX) return -1f
        while (t < TMAX && d[t + 1] < d[t]) t++
        // interpolació parabòlica
        var tf = t.toFloat()
        if (t in 1 until TMAX) {
            val a = d[t - 1]; val c = d[t]; val e = d[t + 1]
            val den = a - 2f * c + e
            if (den > 1e-6f) tf = t + 0.5f * (a - e) / den
        }
        val f = taxa / tf
        return (69.0 + 12.0 * Math.log(f / 440.0) / Math.log(2.0)).toFloat()
    }
    const val MIDA_TMP = W + TMAX + 1

    private val NOMS = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    /** Nom d'una nota MIDI (60 = C4). */
    fun nom(midi: Int): String = NOMS[((midi % 12) + 12) % 12] + (midi / 12 - 1)
    /** La tecla és negra. */
    fun negra(midi: Int): Boolean = ((midi % 12) + 12) % 12 in intArrayOf(1, 3, 6, 8, 10)
}

/**
 * Combat al ritme (mode concert), com el combat d'ImmoHub (`combat.gd`, l'autor): fins a 3 herois a la dreta (posició `X_ALIATS + i·16, 100 + i·44` de la pantalla 1:1)
 * i un grup d'1 a 3 enemics a l'esquerra (`X_ENEMICS` = 8 + 204 repartits, `y = 250 − alt − (i%2)·16`). A cada batec ataca un heroi, per torns:
 * salta cap a l'enemic en «preparat» (arc de 0,225 s i 28 px), en arribar fa l'«atac» 0,12 s (`PAUSA_COP`) i pega (l'enemic fa el sacseig lleuger del joc,
 * ±1 px a salts de 0,04 s durant 0,16 s), i torna «preparat» fins al seu lloc. Els que ataquen de lluny al joc (Pau, Conce: `distancia`) no salten.
 * Quiets: stand-by A (fotogrames 0-3, 0,2 s) i, a partir del 4t cicle, 1/10 de fer la B (40-43) un cop. Els enemics derrotats cauen (s'enfonsen a salts) i
 * desapareixen; quan cau tot el grup, en surt un altre (apareix a salts). Sense so, tot quiet al seu lloc.
 * Fotogrames dels fulls reduïts de l'app (16 per heroi): 0-3 quiet A, 4-7 atac, 8-11 preparar, 12-15 quiet B.
 */
class CombatRitme(val tipusEnemics: Int, llavor: Long = 0x5DEECE66DL) {
    /** Herois actius (1-3) i enemics per grup (1-3; 0 = variable 1-3). Es poden canviar en qualsevol moment (opcions). */
    var herois = 3
    var perGrup = 0
    /** Herois que ataquen de lluny (no salten), per índex de posició. */
    val distancia = BooleanArray(3)

    private var llavor = llavor
    private fun rnd(): Float { llavor = llavor * 6364136223846793005L + 1442695040888963407L; return ((llavor ushr 40) and 0xFFFFFF).toFloat() / 0x1000000 }
    private fun rndInt(n: Int): Int = (rnd() * n).toInt().coerceIn(0, n - 1)

    // ---- herois: 0 quiet, 1 anada, 2 cop, 3 tornada
    private val estatH = IntArray(3); private val tH = FloatArray(3); private val objH = IntArray(3)
    private val idleB = BooleanArray(3); private val idleT = FloatArray(3); private val idleCicles = IntArray(3)
    private var torn = 0
    // ---- enemics del grup: tipus, cops que queden, estat (0 viu, 1 cau, 2 mort, 3 apareix), temps, sacseig, cops en camí
    var nEnemics = 0; private set
    val tipus = IntArray(3); private val vida = IntArray(3); private val estatE = IntArray(3); private val tE = FloatArray(3); private val sac = FloatArray(3); private val enCami = IntArray(3)
    private var espera = 0f
    var tAnim = 0f; private set
    var quiet = true; private set
    var cops = 0; private set
    var grups = 0; private set

    init { nouGrup(false) }

    private fun nouGrup(apareix: Boolean) {
        nEnemics = if (perGrup in 1..3) perGrup else 1 + rndInt(3)
        for (s in 0 until nEnemics) {
            var k: Int; var prova = 0
            do { k = rndInt(tipusEnemics.coerceAtLeast(1)); prova++ } while (prova < 8 && (0 until s).any { tipus[it] == k })
            tipus[s] = k; vida[s] = 2 + rndInt(3); estatE[s] = if (apareix) 3 else 0; tE[s] = 0f; sac[s] = 0f; enCami[s] = 0
        }
        grups++
    }

    // ---- posicions (píxels d'art de la pantalla 1:1, 360x360; l'app en dibuixa la franja y 64-260)
    fun xEnemic(s: Int): Float { val a = 204f / nEnemics; return 8f + a * s + a / 2f - 32f }
    fun yEnemic(s: Int): Float = 250f - 64f - (s % 2) * 16f
    private fun xOrig(i: Int) = 240f + i * 16f
    private fun yOrig(i: Int) = 100f + i * 44f

    fun pas(dt: Float, actiu: Boolean, batec: Boolean) {
        if (!actiu) {
            // tot al seu lloc i quiet; el que estava caient ja és mort, el que apareixia ja hi és
            for (i in 0 until 3) { estatH[i] = 0; tH[i] = 0f; idleB[i] = false; idleT[i] = 0f }
            for (s in 0 until nEnemics) { sac[s] = 0f; enCami[s] = 0; if (estatE[s] == 1) estatE[s] = 2; if (estatE[s] == 3) estatE[s] = 0 }
            if ((0 until nEnemics).all { estatE[it] == 2 }) nouGrup(false)
            quiet = true; return
        }
        quiet = false
        tAnim += dt
        // enemics
        for (s in 0 until nEnemics) {
            if (sac[s] > 0f) sac[s] = maxOf(0f, sac[s] - dt)
            tE[s] += dt
            if (estatE[s] == 1 && tE[s] >= T_CAU) { estatE[s] = 2; tE[s] = 0f }
            else if (estatE[s] == 3 && tE[s] >= T_APAREIX) { estatE[s] = 0; tE[s] = 0f }
        }
        if ((0 until nEnemics).all { estatE[it] == 2 } && (0 until 3).all { estatH[it] == 0 }) {
            espera += dt
            if (espera >= T_ESPERA) { espera = 0f; nouGrup(true) }
        }
        // herois
        for (i in 0 until 3) {
            if (i >= herois) { estatH[i] = 0; continue }
            when (estatH[i]) {
                0 -> {
                    idleT[i] += dt
                    if (idleT[i] >= 4 * T_IDLE) {
                        idleT[i] -= 4 * T_IDLE
                        if (idleB[i]) idleB[i] = false
                        else { idleCicles[i]++; if (idleCicles[i] >= 4 && rnd() < 0.1f) { idleB[i] = true; idleCicles[i] = 0 } }
                    }
                }
                1 -> { tH[i] += dt; if (tH[i] >= T_SALT) { estatH[i] = 2; tH[i] = 0f } }
                2 -> {
                    val a = tH[i]; tH[i] += dt
                    if (a < T_PAUSA_COP && tH[i] >= T_PAUSA_COP) cop(objH[i])
                    if (tH[i] >= T_PAUSA_COP + T_DESPRES) { estatH[i] = if (distancia[i]) 0 else 3; tH[i] = 0f; idleT[i] = 0f; idleB[i] = false }
                }
                3 -> { tH[i] += dt; if (tH[i] >= T_SALT) { estatH[i] = 0; tH[i] = 0f; idleT[i] = 0f } }
            }
        }
        if (batec) ataca()
    }

    /** Toc a la pantalla (mode concert interactiu): ataca el següent heroi ara mateix, fora del ritme. */
    fun atacaAra() { if (!quiet) ataca() }

    private fun ataca() {
        val vius = (0 until nEnemics).filter { estatE[it] == 0 && vida[it] - enCami[it] > 0 }
        if (vius.isEmpty()) return
        for (k in 0 until herois) {
            val i = (torn + k) % herois
            if (estatH[i] != 0) continue
            val s = vius[rndInt(vius.size)]
            objH[i] = s; enCami[s]++
            estatH[i] = if (distancia[i]) 2 else 1; tH[i] = 0f
            torn = (i + 1) % herois
            return
        }
    }

    private fun cop(s: Int) {
        if (s >= nEnemics) return
        enCami[s] = maxOf(0, enCami[s] - 1)
        if (estatE[s] != 0) return
        sac[s] = T_SACSEIG; vida[s]--; cops++
        if (vida[s] <= 0) { estatE[s] = 1; tE[s] = 0f }
    }

    /** Fotograma (del full reduït de 16) de l'heroi `i`. */
    fun fotograma(i: Int): Int = when (estatH[i]) {
        1, 3 -> 8 + (tH[i] / 0.04f).toInt().coerceIn(0, 3)
        2 -> 4 + (tH[i] / 0.04f).toInt().coerceIn(0, 3)
        else -> if (quiet) 0 else (if (idleB[i]) 12 else 0) + (idleT[i] / T_IDLE).toInt().coerceIn(0, 3)
    }
    /** Posició (cantonada de dalt a l'esquerra del fotograma de 64x64) de l'heroi `i`. */
    fun xHeroi(i: Int): Float = posHeroi(i, true)
    fun yHeroi(i: Int): Float = posHeroi(i, false)
    private fun posHeroi(i: Int, x: Boolean): Float {
        val ox = xOrig(i); val oy = yOrig(i)
        val st = estatH[i]
        if (st == 0 || distancia[i]) return if (x) ox else oy
        val s = objH[i].coerceIn(0, maxOf(0, nEnemics - 1))
        val dx = xEnemic(s) + 64f - 6f; val dy = yEnemic(s)
        val p = when (st) { 1 -> (tH[i] / T_SALT).coerceIn(0f, 1f); 2 -> 1f; else -> 1f - (tH[i] / T_SALT).coerceIn(0f, 1f) }
        return if (x) ox + (dx - ox) * p else oy + (dy - oy) * p - Math.sin(p * Math.PI).toFloat() * ALT_SALT * (if (st == 2) 0f else 1f)
    }
    /** Enemic `s`: visible, desplaçament del sacseig, enfonsament i opacitat (a salts). */
    fun visible(s: Int): Boolean = s < nEnemics && estatE[s] != 2
    fun dxEnemic(s: Int): Float = if (sac[s] <= 0f) 0f else if (((T_SACSEIG - sac[s]) / 0.04f).toInt() % 2 == 0) 1f else -1f
    fun dyEnemic(s: Int): Float = if (estatE[s] == 1) (tE[s] / T_CAU * 4f).toInt().coerceIn(0, 3) * 6f else 0f
    fun alfaEnemic(s: Int): Float = when (estatE[s]) {
        1 -> 1f - (tE[s] / T_CAU * 4f).toInt().coerceIn(0, 4) / 4f
        3 -> ((tE[s] / T_APAREIX * 4f).toInt() + 1).coerceIn(0, 4) / 4f
        2 -> 0f
        else -> 1f
    }
    /** Fotograma de l'animació de l'enemic `s` (fulls de `n` fotogrames: 260 ms cadascun, desfasats). */
    fun fotEnemic(s: Int, n: Int): Int = if (n <= 1 || quiet) 0 else ((tAnim / 0.26f).toInt() + s) % n

    companion object {
        const val T_SALT = 0.225f
        const val ALT_SALT = 28f
        const val T_PAUSA_COP = 0.12f
        const val T_DESPRES = 0.12f
        const val T_SACSEIG = 0.16f
        const val T_IDLE = 0.2f
        const val T_CAU = 0.5f
        const val T_APAREIX = 0.4f
        const val T_ESPERA = 0.5f
        /** Quant abans del batec cal començar l'atac perquè el cop hi caigui (salt + pausa del cop). */
        const val AVANC = T_SALT + T_PAUSA_COP
    }
}

/**
 * Partícules i túnel (mode concert): quadrats que vénen del fons cap a la pantalla (perspectiva simple: posició / profunditat) i acceleren amb els greus.
 * Generador pseudoaleatori propi (determinista, provat a JVM).
 */
class Tunel(val n: Int = 140) {
    val angle = FloatArray(n); val radi = FloatArray(n); val z = FloatArray(n); val to = IntArray(n)
    private var llavor = 0x2545F491L
    private fun rnd(): Float { llavor = (llavor * 6364136223846793005L + 1442695040888963407L); return ((llavor ushr 40) and 0xFFFFFF).toFloat() / 0x1000000 }
    init { for (i in 0 until n) neix(i, 0.05f + rnd() * 0.95f) }
    private fun neix(i: Int, zz: Float) { angle[i] = rnd() * 6.2831855f; radi[i] = 0.25f + rnd() * 0.75f; z[i] = zz; to[i] = (rnd() * 1000).toInt() }
    /** Anells del túnel: profunditat de cadascun (es mouen amb les partícules). */
    val anells = FloatArray(8) { (it + 1) / 8f }
    var gir = 0f; private set

    /** `energia` 0-1 (greus), `batec` = un batec ara (empenta). Sense so no es mou res. */
    fun pas(dt: Float, actiu: Boolean, energia: Float, empenta: Float) {
        if (!actiu) return
        val v = 0.18f + 0.9f * energia + 1.6f * empenta
        gir += dt * (0.05f + 0.25f * energia)
        for (i in 0 until n) {
            z[i] -= dt * v * z[i].coerceAtLeast(0.15f) * 1.6f
            if (z[i] < 0.04f) neix(i, 1f)
        }
        for (k in anells.indices) { anells[k] -= dt * v * 0.35f; if (anells[k] < 0.06f) anells[k] += 1f }
    }
}
