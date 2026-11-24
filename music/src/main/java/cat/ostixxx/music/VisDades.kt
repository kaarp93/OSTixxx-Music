package cat.ostixxx.music

// Kotlin pur (sense Android ni Compose): dades dels visualitzadors, provades a JVM (eines/prototips/ui/prova.sh).

/**
 * Nivells de les barres (rework 2026-11-17). Abans es dibuixava directament `Espectre.valors` (energia lineal, sovint saturada a 1, i a 22 Hz):
 * barres a tope o mortes i a salts. Ara: escala en decibels (40 dB útils), interpolació a tantes columnes com càpiguen, atac ràpid i caiguda suau
 * segons el temps real del fotograma (60 fps), i un «pic» per columna que s'aguanta una mica i cau. Sense so, tot cau fins a zero i `quiet` = true.
 */
class EspectreVis(val maxCols: Int = 96) {
    val niv = FloatArray(maxCols)
    val pic = FloatArray(maxCols)
    private val picT = FloatArray(maxCols)
    private val obj = FloatArray(maxCols)
    var cols = 0; private set
    /** Tot és a zero: el dibuix es pot aturar. */
    var quiet = true; private set

    /**
     * Un pas de `dt` segons. `brut` = espectre del motor (0-1, energia; `lineal` = nivells per canal, ja lineals); `actiu` = sona i no és en pausa.
     */
    fun avanca(brut: FloatArray?, nBrut: Int, cols0: Int, actiu: Boolean, dt: Float, lineal: Boolean = false) {
        val c = cols0.coerceIn(1, maxCols)
        if (c != cols) { cols = c }
        if (!actiu || brut == null || nBrut <= 0) java.util.Arrays.fill(obj, 0, c, 0f)
        else for (k in 0 until c) {
            val v = interpola(brut, nBrut, k, c)
            obj[k] = if (lineal) (v * 1.4f).coerceIn(0f, 1f) else nivellDb(v)
        }
        val atac = 1f - Math.exp(-dt / 0.035).toFloat()
        val caiguda = 1f - Math.exp(-dt / 0.20).toFloat()
        var tot0 = true
        for (k in 0 until c) {
            val o = obj[k]; val n = niv[k]
            niv[k] = if (o > n) n + (o - n) * atac else n + (o - n) * caiguda
            if (niv[k] < 0.004f) niv[k] = 0f
            if (niv[k] >= pic[k]) { pic[k] = niv[k]; picT[k] = 0.45f }
            else if (picT[k] > 0f) picT[k] -= dt
            else pic[k] = maxOf(niv[k], pic[k] - 0.9f * dt)
            if (pic[k] < 0.004f) pic[k] = 0f
            if (niv[k] > 0f || pic[k] > 0f) tot0 = false
        }
        quiet = tot0 && !actiu
    }

    companion object {
        /** Energia de `Espectre` (rms·7/32768, de 0 a 1) a una escala de 40 dB: 0,01 → 0, 0,1 → 0,5, 1 → 1. */
        fun nivellDb(v: Float): Float = if (v <= 0.01f) 0f else ((20f * Math.log10(v.toDouble()).toFloat() + 40f) / 40f).coerceIn(0f, 1f)
        /** Valor de la columna `k` de `cols` repartint les `n` bandes (interpolació lineal entre bandes veïnes). */
        fun interpola(b: FloatArray, n: Int, k: Int, cols: Int): Float {
            if (n == 1 || cols == 1) return b[0]
            val x = k * (n - 1f) / (cols - 1f)
            val i = x.toInt().coerceIn(0, n - 2); val f = x - i
            return b[i] * (1f - f) + b[i + 1] * f
        }
    }
}

/**
 * Forma d'ona per a l'oscil·loscopi (rework 2026-11-17). Abans: es reordenava el búfer sencer, es buscava el pas per zero a la part vella i s'escalava cada
 * fotograma al màxim (el soroll es veia enorme i la traça tremolava). Ara: finestra curta i recent, disparador al pas ascendent per la mitjana més proper
 * a l'última posició estable (la forma no salta), i guany automàtic suavitzat (no infla el silenci ni el soroll).
 */
class OnaVis {
    private var guany = 2f
    private var ord = FloatArray(0)
    /** Últimes traces (de la més nova a la més vella) per a la persistència de fòsfor. */
    val historia = ArrayList<FloatArray>()
    /** Memòria de la traça actual per al dibuix (la reaprofita qui dibuixa; `captura` la copia a `historia`). */
    var buf = FloatArray(0)

    /**
     * Copia la finestra de `w` punts de l'anell `b` (`n` mostres, `pos` = on s'escriurà la següent) a `out` (de -1 a 1). Torna false si no hi ha senyal (línia plana).
     * `dt` en segons, per suavitzar el guany. `persistencia` = quantes traces velles es guarden.
     */
    fun captura(b: FloatArray, n: Int, pos: Int, w: Int, out: FloatArray, dt: Float, persistencia: Int = 0): Boolean {
        if (n < 4 || w < 2) { java.util.Arrays.fill(out, 0, w.coerceAtLeast(0), 0f); return false }
        val ww = minOf(w, n / 2)
        if (ord.size != n) ord = FloatArray(n)
        for (i in 0 until n) ord[i] = b[(pos + i) % n]
        var suma = 0.0
        for (i in 0 until n) suma += ord[i]
        val mitja = (suma / n).toFloat()
        // disparador: el pas ascendent per la mitjana més recent que deixi lloc a tota la finestra
        var ini = n - ww
        var i = n - ww
        val minI = n / 4
        while (i > minI) { if (ord[i - 1] < mitja && ord[i] >= mitja) { ini = i; break }; i-- }
        var amp = 0f
        for (j in 0 until ww) { val v = Math.abs(ord[ini + j] - mitja); if (v > amp) amp = v }
        val senyal = amp > 0.004f
        // guany: que la traça ocupi ~85 % de l'alçada, sense passar d'×12 (el soroll baix no s'infla) i canviant a poc a poc
        val obj = if (senyal) (0.85f / maxOf(amp, 0.07f)).coerceAtMost(12f) else guany
        guany += (obj - guany) * (1f - Math.exp(-dt / 0.25).toFloat())
        for (j in 0 until ww) out[j] = if (senyal) ((ord[ini + j] - mitja) * guany).coerceIn(-1f, 1f) else 0f
        for (j in ww until w) out[j] = out[ww - 1]
        if (persistencia > 0) {
            val copia = if (historia.size >= persistencia) historia.removeAt(historia.size - 1) else FloatArray(w)
            val c2 = if (copia.size == w) copia else FloatArray(w)
            System.arraycopy(out, 0, c2, 0, w); historia.add(0, c2)
        } else historia.clear()
        return senyal
    }
}

/**
 * Colors dels visualitzadors (Ajustos > Visualitzador > Color): 0 = «Color del joc» (el de la portada, el del tema) i 9 colors fixos, que es veuen bé en clar, fosc i AMOLED
 * (`rampa` els enfosqueix o aclareix fins a un contrast mínim amb el fons). Cada color és una rampa de 3 tons (baix, mig, dalt) per no ser monocrom; l'«Arc de Sant Martí» canvia de to per columna.
 */
object ColorsVis {
    const val JOC = 0
    const val ARC = 9
    /** Claus de `t()`: 0 = Color del joc, 1-9. */
    val NOMS = listOf("Color del joc", "Verd", "Cian", "Blau", "Violeta", "Rosa", "Vermell", "Taronja", "Groc", "Arc de Sant Martí")
    /** To (0-360) de cada color fix (1-8); l'arc no en té. */
    private val TONS = floatArrayOf(0f, 135f, 188f, 222f, 268f, 322f, 2f, 28f, 50f)

    /** Color base del número `k` (1-8) abans d'ajustar-lo al fons. Per a `JOC` i `ARC`, el to que es doni. */
    fun base(k: Int, toJoc: Float = 260f, sat: Float = 0.80f): Int = when (k) {
        JOC, ARC -> ColorsPurs.deHsl(toJoc, sat.coerceIn(0.55f, 0.95f), 0.56f)
        else -> ColorsPurs.deHsl(TONS[k.coerceIn(1, 8)], 0.85f, 0.56f)
    }

    /**
     * Rampa de 3 colors (baix, mig, dalt) a partir de `base`: el mig 22° més enllà i el de dalt 44° i més clar (fosc) o més saturat (clar).
     * Cadascun amb contrast ≥ 3 sobre `fons`.
     */
    fun rampa(base: Int, fons: Int, fosc: Boolean): IntArray {
        val h = ColorsPurs.aHsl(base)
        val dir = if (fosc) 1 else -1
        fun aj(c: Int) = ColorsPurs.ambContrast(c, fons, 3.0, dir)
        val baix = aj(ColorsPurs.deHsl(h[0], h[1], if (fosc) 0.50f else 0.42f))
        val mig = aj(ColorsPurs.deHsl(h[0] + 22f, h[1], if (fosc) 0.58f else 0.40f))
        val dalt = aj(ColorsPurs.deHsl(h[0] + 44f, minOf(1f, h[1] + 0.1f), if (fosc) 0.70f else 0.36f))
        return intArrayOf(baix, mig, dalt)
    }

    /** To de la columna `k` de `n` a l'arc de Sant Martí (del vermell al violeta). */
    fun toArc(k: Int, n: Int): Float = if (n <= 1) 0f else 280f * k / (n - 1)
}
