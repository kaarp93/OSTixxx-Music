package cat.gbdsmixxx

import android.os.Handler
import android.os.Looper

/**
 * Estat dels efectes de so d'OSTixxx Music (bloc 4, 2026-11-17), que el fil d'àudio de `RadioMotor` copia a la seva `CadenaEfectes` un cop per bloc.
 * - **Globals:** EQ de 10 bandes (dB reals, `EqGrafic`), amplada M/S, balanç, mono i limitador.
 * - **Per consola** (la de la cançó que sona: GB, GBC, NES, SNES, GBA, DS, Altres): chorus, reverb, sala i amortiment. Els valors per defecte són tot en pla (l'autor, 2026-11-17; els presets de
 *   `PresetEfectes` no s'apliquen sols).
 * Només OSTixxx Music ho activa (`habilitat`); a OSTixxx el bucle d'àudio no hi passa mai. Tot es desa a `App.env` (SharedPreferences), amb 300 ms de debounce.
 *
 * Claus: `fx_versio`; `fx_eq_0..9` (dB×10); `fx_amplada` (0..200), `fx_balanc` (-100..100), `fx_mono`, `fx_limita` (0/1);
 * `fx_<consola>_chorus|rev|sala|amort` (0..100). Les claus velles `eq_0..9` (l'EQ d'abans, que feia la meitat del que deia) s'esborren en migrar: l'EQ torna a pla.
 */
object EfectesRadio {
    const val BANDES = 10
    private const val VERSIO = 1
    /** Paràmetres per consola, en aquest ordre a `perCons`. */
    const val CHORUS = 0; const val REVERB = 1; const val SALA = 2; const val AMORT = 3
    private val CLAUS = arrayOf("chorus", "rev", "sala", "amort")

    @Volatile var habilitat = false
    /** dB per banda (-12..12). Es modifica en el lloc (el fil d'àudio el llegeix a cada bloc). */
    val eq = FloatArray(BANDES)
    @Volatile var amplada = 1f
    @Volatile var balanc = 0f
    @Volatile var mono = false
    @Volatile var limita = true
    /** Consola de la cançó que sona ("" si no se sap: llavors chorus i reverb a zero). */
    @Volatile var consola = ""
    /** Chorus, reverb, sala i amortiment (0..1) de `consola`. */
    @Volatile var perCons = FloatArray(4)
    /** Dona la consola d'una emissora pel seu nom (la posa OSTixxx Music: `consolaDe`). */
    @Volatile var resolConsola: ((String) -> String)? = null
    private val cache = HashMap<String, FloatArray>()
    /** Consoles amb valors tocats per l'usuari (només aquestes es desen: les altres segueixen el preset). */
    private val bruts = HashSet<String>()

    val eqActiu: Boolean get() = eq.any { Math.abs(it) > 0.05f }
    val algunActiu: Boolean get() = eqActiu || amplada != 1f || balanc != 0f || mono || perCons[CHORUS] > 0f || perCons[REVERB] > 0f

    /** Llegeix les preferències (a `MusicActivity.onCreate`). Primer cop amb aquesta versió: esborra l'EQ vell (`eq_k`), que queda a pla. */
    fun carrega() {
        val env = App.env
        if (env.int("fx_versio", 0) < VERSIO) {
            env.esborra(*Array(BANDES) { "eq_$it" })
            for (k in 0 until BANDES) env.putInt("fx_eq_$k", 0)
            env.putInt("fx_versio", VERSIO)
        }
        for (k in 0 until BANDES) eq[k] = (env.int("fx_eq_$k", 0) / 10f).coerceIn(-12f, 12f)
        amplada = env.int("fx_amplada", 100).coerceIn(0, 200) / 100f
        balanc = env.int("fx_balanc", 0).coerceIn(-100, 100) / 100f
        mono = env.int("fx_mono", 0) == 1
        limita = env.int("fx_limita", 1) == 1
        synchronized(cache) { cache.clear(); bruts.clear() }
        if (consola.isNotEmpty()) perCons = valorsDe(consola)
    }

    /** Valors per defecte d'una consola: tot en pla (l'autor, 2026-11-17); els presets de `PresetEfectes` queden com a punt de partida, però no s'apliquen sols. */
    fun perDefecte(c: String): FloatArray { val p = PresetEfectes.TOTS[0]; return floatArrayOf(p.chorus, p.reverb, p.sala, p.amortiment) }

    /** Valors desats (o per defecte) d'una consola; és el mateix array que llegeix l'àudio si és la que sona. */
    fun valorsDe(c: String): FloatArray = synchronized(cache) {
        cache.getOrPut(c) {
            val d = perDefecte(c)
            FloatArray(4) { i -> App.env.int("fx_${c}_${CLAUS[i]}", -1).let { v -> if (v < 0) d[i] else v.coerceIn(0, 100) / 100f } }
        }
    }

    /** La cançó nova és d'aquesta emissora (pel nom): tria els valors de la seva consola. */
    fun perEmissora(nom: String) {
        val c = try { resolConsola?.invoke(nom) ?: "" } catch (e: Throwable) { "" }
        consola = c
        perCons = if (c.isEmpty()) FloatArray(4) else valorsDe(c)
    }

    fun posaEq(k: Int, dB: Float) { eq[k] = dB.coerceIn(-12f, 12f); programaDesa() }
    fun posaConsola(c: String, i: Int, v: Float) { valorsDe(c)[i] = v.coerceIn(0f, 1f); synchronized(cache) { bruts.add(c) }; programaDesa() }
    fun restauraConsola(c: String) { val d = perDefecte(c); val a = valorsDe(c); for (i in 0..3) a[i] = d[i]; synchronized(cache) { bruts.add(c) }; programaDesa() }

    /** Copia l'estat a la cadena del fil d'àudio (un cop per bloc: cap assignació). */
    fun configura(c: CadenaEfectes) {
        for (k in 0 until BANDES) c.eq.posa(k, eq[k])
        val p = perCons
        c.chorus.mix = p[CHORUS]; c.reverb.wet = p[REVERB]; c.reverb.sala = p[SALA]; c.reverb.amortiment = p[AMORT]
        c.amplada = amplada; c.balanc = balanc; c.mono = mono; c.limita = limita
    }

    // ---- desa amb debounce: els lliscadors criden `programaDesa` a cada moviment; s'escriu 300 ms després de l'últim ----
    private val ma by lazy { Handler(Looper.getMainLooper()) }
    private val desa = Runnable { desaAra() }
    fun programaDesa() { ma.removeCallbacks(desa); ma.postDelayed(desa, 300) }
    fun desaAra() {
        val m = HashMap<String, Int>()
        for (k in 0 until BANDES) m["fx_eq_$k"] = Math.round(eq[k] * 10)
        m["fx_amplada"] = Math.round(amplada * 100); m["fx_balanc"] = Math.round(balanc * 100)
        m["fx_mono"] = if (mono) 1 else 0; m["fx_limita"] = if (limita) 1 else 0
        synchronized(cache) { for (c in bruts) { val a = cache[c] ?: continue; for (i in 0..3) m["fx_${c}_${CLAUS[i]}"] = Math.round(a[i] * 100) } }
        App.env.putInts(m)                 // una sola escriptura (apply)
    }
}
