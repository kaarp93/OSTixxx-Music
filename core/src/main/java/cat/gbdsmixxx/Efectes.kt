package cat.gbdsmixxx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// DSP pur de Kotlin, sense cap import d'Android (es prova a la JVM: eines/efectes/prova.sh), per a la cadena d'efectes comuna. Connectat a Motor.kt (RadioMotor) per EfectesRadio.
// Cap assignació al camí calent (`process`): tots els buffers es creen al constructor o, un sol cop, si el bloc és més gran del previst.

/** Filtre biquad (Cookbook d'Audio EQ de R. Bristow-Johnson), forma directa II transposada en Double (a 31 Hz i 32 kHz el Float perd precisió). */
class Biquad {
    var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
    internal var z1 = 0.0; internal var z2 = 0.0
    fun reset() { z1 = 0.0; z2 = 0.0 }
    fun copia(o: Biquad) { b0 = o.b0; b1 = o.b1; b2 = o.b2; a1 = o.a1; a2 = o.a2 }
    fun process(x: Double): Double { val y = b0 * x + z1; z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y; return y }
    /** El mateix que `process` sobre `frames` mostres de `t` (en el lloc), amb coeficients i estat en locals. */
    fun processBloc(t: DoubleArray, frames: Int) {
        val c0 = b0; val c1 = b1; val c2 = b2; val d1 = a1; val d2 = a2; var s1 = z1; var s2 = z2
        for (i in 0 until frames) { val x = t[i]; val y = c0 * x + s1; s1 = c1 * x - d1 * y + s2; s2 = c2 * x - d2 * y; t[i] = y }
        z1 = s1; z2 = s2
    }
    companion object {
        /** `a.processBloc(ta)` i `b.processBloc(tb)` intercalats en un sol bucle (mateixes operacions per canal). */
        fun processBloc2(a: Biquad, b: Biquad, ta: DoubleArray, tb: DoubleArray, frames: Int) {
            val a0 = a.b0; val a1_ = a.b1; val a2_ = a.b2; val p1 = a.a1; val p2 = a.a2; var sa1 = a.z1; var sa2 = a.z2
            val b0_ = b.b0; val b1_ = b.b1; val b2_ = b.b2; val q1 = b.a1; val q2 = b.a2; var sb1 = b.z1; var sb2 = b.z2
            for (i in 0 until frames) {
                val x = ta[i]; val y = a0 * x + sa1; sa1 = a1_ * x - p1 * y + sa2; sa2 = a2_ * x - p2 * y; ta[i] = y
                val u = tb[i]; val v = b0_ * u + sb1; sb1 = b1_ * u - q1 * v + sb2; sb2 = b2_ * u - q2 * v; tb[i] = v
            }
            a.z1 = sa1; a.z2 = sa2; b.z1 = sb1; b.z2 = sb2
        }
    }

    private fun posa(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0
    }
    fun passa() { b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0 }
    /** Campana: `dB` al voltant de `f0` amb factor de qualitat `q`. */
    fun campana(fs: Double, f0: Double, q: Double, dB: Double) {
        val a = 10.0.pow(dB / 40.0); val w = 2 * PI * f0 / fs; val c = cos(w); val al = sin(w) / (2 * q)
        posa(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a)
    }
    fun greusEscala(fs: Double, f0: Double, dB: Double) {
        val a = 10.0.pow(dB / 40.0); val w = 2 * PI * f0 / fs; val c = cos(w); val b = 2 * sqrt(a) * (sin(w) / 2 * sqrt(2.0))
        posa(a * ((a + 1) - (a - 1) * c + b), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - b),
            (a + 1) + (a - 1) * c + b, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - b)
    }
    fun agutsEscala(fs: Double, f0: Double, dB: Double) {
        val a = 10.0.pow(dB / 40.0); val w = 2 * PI * f0 / fs; val c = cos(w); val b = 2 * sqrt(a) * (sin(w) / 2 * sqrt(2.0))
        posa(a * ((a + 1) + (a - 1) * c + b), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - b),
            (a + 1) - (a - 1) * c + b, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - b)
    }
    /** Pas alt de 2n ordre (per a un tall de continu / rumble). */
    fun pasAlt(fs: Double, f0: Double, q: Double = 0.7071) {
        val w = 2 * PI * f0 / fs; val c = cos(w); val al = sin(w) / (2 * q)
        posa((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
    }
    /** Pas baix de 2n ordre. */
    fun pasBaix(fs: Double, f0: Double, q: Double = 0.7071) {
        val w = 2 * PI * f0 / fs; val c = cos(w); val al = sin(w) / (2 * q)
        posa((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al)
    }
    /** Estable si els dos pols són dins del cercle unitat (triangle de Schur-Cohn). */
    fun estable() = abs(a2) < 1.0 && abs(a1) < 1.0 + a2
    /** Mòdul de la resposta a la freqüència `f` (Hz). */
    fun modul(fs: Double, f: Double): Double {
        val w = 2 * PI * f / fs; val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2; val ni = -(b1 * s1 + b2 * s2)
        val dr = 1 + a1 * c1 + a2 * c2; val di = -(a1 * s1 + a2 * s2)
        return sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
    }
}

/**
 * Equalitzador gràfic de 10 bandes (31 Hz a 16 kHz, de -12 a +12 dB) amb 10 biquads de campana en cascada (Q 1,41 = una octava), per canal, amb els guanys compensats perquè el lliscador sigui la resposta real als centres.
 * Les bandes a 0 dB no costen res; les que passen de 0,45·fs es descarten (a 22050 Hz, la de 16 kHz no existeix); els canvis de guany van a salts de 1,5 dB per bloc (sense clics).
 */
class EqGrafic(private val fs: Int) {
    companion object {
        val CENTRES = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        const val Q = 1.41
        const val PAS_DB = 1.5
    }
    private val n = CENTRES.size
    @Volatile var objectiu = FloatArray(n)
    private val actual = DoubleArray(n)
    private val coef = Array(n) { Biquad() }
    private val filtres = Array(2) { Array(n) { Biquad() } }
    private val viva = BooleanArray(n)
    private val llista = IntArray(n); private var nLlista = 0
    private val valida = BooleanArray(n) { CENTRES[it] < 0.45 * fs }
    // Les campanes en cascada se sumen en dB i s'escampen als veïns (a una octava, ~21 % del guany): amb 10 lliscadors a +6 dB, la resposta pujaria fins a +9 dB al mig.
    // Es compensa amb la inversa de la matriu d'interacció (resposta en dB de la banda j al centre i, per a 1 dB), calculada un sol cop al constructor.
    private val inv = Array(n) { DoubleArray(n) }
    private val objAnt = FloatArray(n) { Float.NaN }
    private val gb = DoubleArray(n)
    init {
        val idx = (0 until n).filter { valida[it] }; val m = idx.size
        val a = Array(m) { DoubleArray(2 * m) }; val b = Biquad()
        for (i in 0 until m) { for (j in 0 until m) { b.campana(fs.toDouble(), CENTRES[idx[j]], Q, 1.0); a[i][j] = 20 * Math.log10(b.modul(fs.toDouble(), CENTRES[idx[i]])) }; a[i][m + i] = 1.0 }
        for (c in 0 until m) {                      // Gauss-Jordan (la matriu és diagonal dominant: no cal pivotar gaire)
            var pv = c; for (r in c + 1 until m) if (abs(a[r][c]) > abs(a[pv][c])) pv = r
            val t = a[c]; a[c] = a[pv]; a[pv] = t
            val d = a[c][c]; for (j in 0 until 2 * m) a[c][j] /= d
            for (r in 0 until m) if (r != c) { val f = a[r][c]; if (f != 0.0) for (j in 0 until 2 * m) a[r][j] -= f * a[c][j] }
        }
        for (i in 0 until m) for (j in 0 until m) inv[idx[i]][idx[j]] = a[i][m + j]
    }
    fun posa(k: Int, dB: Float) { objectiu[k] = dB.coerceIn(-12f, 12f) }
    /** Cal cridar-ho un cop per bloc: mou els guanys cap a l'objectiu i recalcula només les bandes que han canviat. */
    fun actualitza(): Boolean {
        val ob = objectiu
        var dif = false; for (k in 0 until n) if (ob[k] != objAnt[k]) { dif = true; break }
        if (dif) {
            for (k in 0 until n) objAnt[k] = ob[k]
            for (i in 0 until n) { var v = 0.0; if (valida[i]) for (j in 0 until n) v += inv[i][j] * ob[j]; gb[i] = v.coerceIn(-18.0, 18.0) }
        }
        var canvi = false
        for (k in 0 until n) {
            val t = gb[k]
            if (actual[k] != t) {
                val d = t - actual[k]; actual[k] = if (abs(d) <= PAS_DB) t else actual[k] + (if (d > 0) PAS_DB else -PAS_DB)
                coef[k].campana(fs.toDouble(), CENTRES[k], Q, actual[k])
                for (c in 0..1) filtres[c][k].copia(coef[k])
                canvi = true
            }
            viva[k] = abs(actual[k]) > 0.02
        }
        if (canvi) { nLlista = 0; for (k in 0 until n) if (viva[k]) llista[nLlista++] = k }
        return nLlista > 0
    }
    val actiu: Boolean get() = nLlista > 0
    private var tmp = DoubleArray(1024); private var tmpR = DoubleArray(1024)
    /** Els dos canals alhora (dues cadenes independents dins del mateix bucle: el processador les fa en paral·lel); cada canal fa exactament les operacions de `process`. */
    fun processEstereo(l: FloatArray, r: FloatArray, frames: Int) {
        if (nLlista == 0) return
        if (tmp.size < frames) tmp = DoubleArray(frames)
        if (tmpR.size < frames) tmpR = DoubleArray(frames)
        val tl = tmp; val tr = tmpR; val fl = filtres[0]; val fr = filtres[1]
        for (i in 0 until frames) { tl[i] = l[i].toDouble(); tr[i] = r[i].toDouble() }
        for (j in 0 until nLlista) { val k = llista[j]; Biquad.processBloc2(fl[k], fr[k], tl, tr, frames) }
        for (i in 0 until frames) { l[i] = tl[i].toFloat(); r[i] = tr[i].toFloat() }
    }
    /**
     * Banda per banda (abans, mostra per mostra): cada banda recorre el bloc amb els coeficients i l'estat en variables locals, i el valor intermedi es
     * desa en Double, de manera que les operacions i el seu ordre per mostra són els mateixos que abans (sortida idèntica bit a bit; eines/opt/motor/run.sh).
     */
    fun process(x: FloatArray, canal: Int, frames: Int) {
        if (nLlista == 0) return
        val fl = filtres[canal]
        if (tmp.size < frames) tmp = DoubleArray(frames)
        val t = tmp
        for (i in 0 until frames) t[i] = x[i].toDouble()
        for (j in 0 until nLlista) fl[llista[j]].processBloc(t, frames)
        for (i in 0 until frames) x[i] = t[i].toFloat()
    }
    /** Resposta teòrica (dB) de tota la cascada a `f` Hz amb els guanys actuals. */
    fun respostaDb(f: Double): Double { var m = 1.0; for (k in 0 until n) if (viva[k]) m *= coef[k].modul(fs.toDouble(), f); return 20 * Math.log10(m) }
    fun reset() { for (c in 0..1) for (b in filtres[c]) b.reset() }
}

/**
 * Reverberació d'estil Freeverb (Jezar): 8 filtres de pinta amb amortiment + 4 passa-tot per canal, retards escalats a la freqüència de mostreig; el canal dret es desfasa 23 mostres.
 * És un **send**: `afegeix` suma només la part humida (a `wet`) a la sortida; la seca no es toca. Cost: 24 línies de retard (≈ 12 operacions per mostra i canal).
 */
class Reverb(private val fs: Int) {
    private val esc = fs / 44100.0
    private val pinta = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val passa = intArrayOf(556, 441, 341, 225)
    private fun mida(v: Int) = max(8, Math.round(v * esc).toInt())
    private val cL = Array(8) { FloatArray(mida(pinta[it])) }; private val cR = Array(8) { FloatArray(mida(pinta[it] + 23)) }
    private val aL = Array(4) { FloatArray(mida(passa[it])) }; private val aR = Array(4) { FloatArray(mida(passa[it] + 23)) }
    private val ciL = IntArray(8); private val ciR = IntArray(8); private val aiL = IntArray(4); private val aiR = IntArray(4)
    private val fL = FloatArray(8); private val fR = FloatArray(8)
    /** 0 a 1: mida de la sala (0 = retard curt, 1 = cua llarga). */
    @Volatile var sala = 0.5f
    /** 0 a 1: amortiment dels aguts a la cua (1 = cua fosca). */
    @Volatile var amortiment = 0.5f
    /** 0 a 1: nivell de la cua (0 dB de relació amb un soroll dret d'entrada a 1,0; vegeu les proves). */
    @Volatile var wet = 0.0f
    /** 0 a 1: obertura estèreo de la cua. */
    @Volatile var amplada = 1.0f
    companion object { const val ENTRADA = 0.015f; const val SORTIDA = 1.0f }
    /** Suma la cua de la reverb d'(`inL`,`inR`) a (`outL`,`outR`) (poden ser els mateixos arrays: es llegeix cada mostra abans d'escriure-la). */
    fun afegeix(inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray, frames: Int) {
        val fb = 0.7f + sala.coerceIn(0f, 1f) * 0.28f
        val d1 = amortiment.coerceIn(0f, 1f) * 0.4f; val d2 = 1f - d1
        val w = wet * SORTIDA; val w1 = w * (amplada / 2f + 0.5f); val w2 = w * (1f - amplada) / 2f
        for (i in 0 until frames) {
            val x = inL[i]; val y = inR[i]
            val ent = (x + y) * ENTRADA
            var sl = 0f; var sr = 0f
            for (k in 0 until 8) {
                val bl = cL[k]; val pl = ciL[k]; val ol = bl[pl]; fL[k] = ol * d2 + fL[k] * d1; bl[pl] = ent + fL[k] * fb; ciL[k] = if (pl + 1 >= bl.size) 0 else pl + 1; sl += ol
                val br = cR[k]; val pr = ciR[k]; val ord = br[pr]; fR[k] = ord * d2 + fR[k] * d1; br[pr] = ent + fR[k] * fb; ciR[k] = if (pr + 1 >= br.size) 0 else pr + 1; sr += ord
            }
            for (k in 0 until 4) {
                val bl = aL[k]; val pl = aiL[k]; val ob = bl[pl]; val inl = sl; sl = -inl + ob; bl[pl] = inl + ob * 0.5f; aiL[k] = if (pl + 1 >= bl.size) 0 else pl + 1
                val br = aR[k]; val pr = aiR[k]; val obr = br[pr]; val inr = sr; sr = -inr + obr; br[pr] = inr + obr * 0.5f; aiR[k] = if (pr + 1 >= br.size) 0 else pr + 1
            }
            outL[i] += sl * w1 + sr * w2
            outR[i] += sr * w1 + sl * w2
        }
    }
    fun reset() { for (b in cL) b.fill(0f); for (b in cR) b.fill(0f); for (b in aL) b.fill(0f); for (b in aR) b.fill(0f); fL.fill(0f); fR.fill(0f) }
}

/**
 * Chorus estèreo: línia de retard modulada (base 14 ms ± 3 ms, LFO sinus de 0,6 Hz; el dret va en quadratura) amb interpolació lineal.
 * `mix` 0 a 1 (0 = res; 1 = 50 % sec + 50 % modulat). Sobre una font mono (NES) dona pseudo-estèreo; **no és compatible amb el mono real** (la pinta s'hi sent).
 */
class Chorus(private val fs: Int) {
    private val mask = 4095
    private val bl = FloatArray(4096); private val br = FloatArray(4096)
    private var pos = 0; private var fase = 0.0
    @Volatile var mix = 0f
    @Volatile var taxa = 0.6f
    @Volatile var baseMs = 14f
    @Volatile var fonsMs = 3f
    fun process(l: FloatArray, r: FloatArray, frames: Int) {
        val m = mix.coerceIn(0f, 1f); if (m <= 0f) return
        val inc = taxa / fs.toDouble(); val bs = baseMs * fs / 1000.0; val dp = fonsMs * fs / 1000.0
        val sec = 1f - 0.5f * m; val moll = 0.5f * m
        for (i in 0 until frames) {
            bl[pos] = l[i]; br[pos] = r[i]
            val dL = bs + dp * sin(2 * PI * fase); val dR = bs + dp * sin(2 * PI * (fase + 0.25))
            l[i] = l[i] * sec + llegeix(bl, dL) * moll
            r[i] = r[i] * sec + llegeix(br, dR) * moll
            pos = (pos + 1) and mask; fase += inc; if (fase >= 1.0) fase -= 1.0
        }
    }
    private fun llegeix(b: FloatArray, d: Double): Float {
        val p = pos - d; val k = Math.floor(p).toInt(); val f = (p - k).toFloat()
        val a = b[k and mask]; val c = b[(k + 1) and mask]
        return a + (c - a) * f
    }
    fun reset() { bl.fill(0f); br.fill(0f); pos = 0 }
}

/** Estèreo mid/side i balanç: `amplada` 0 = mono real, 1 = tal qual, 2 = costats ×2; `balanç` -1 (esquerra) a 1 (dreta) sense tocar el centre. */
object Amplada {
    fun process(l: FloatArray, r: FloatArray, frames: Int, amplada: Float, balanc: Float) {
        val gl = min(1f, 1f - balanc); val gr = min(1f, 1f + balanc)
        if (amplada == 1f && balanc == 0f) return
        for (i in 0 until frames) {
            val m = (l[i] + r[i]) * 0.5f; val s = (l[i] - r[i]) * 0.5f * amplada
            l[i] = (m + s) * gl; r[i] = (m - s) * gr
        }
    }
}

/**
 * Limitador de mur (lookahead de `LA` mostres = ≈1 ms): filtre de mínim + mitjana mòbil del guany necessari, enllaçat entre canals. Mai no passa del `sostre`
 * (es prova a la JVM) i no toca res per sota (guany exactament 1). Latència: `LA` mostres. Substitueix el `coerceIn` dur després de l'EQ.
 */
class Limitador(private val fs: Int) {
    private val la = max(8, fs / 1000)            // ≈ 1 ms
    private val dl = FloatArray(la + 1); private val dr = FloatArray(la + 1)
    private val need = FloatArray(la + 1)
    private var p = 0
    private var suma = 0.0
    private val box = FloatArray(la + 1)
    private var gPrev = 1f
    // Mínim de la finestra de les últimes la+1 necessitats amb una cua monòtona (abans es recorria tota la finestra a cada mostra): dona el mateix valor
    // exacte (el mínim d'aquelles mateixes mostres; abans del principi la finestra és d'uns, i totes les necessitats són ≤ 1). Cost O(1) per mostra.
    private val dqV = FloatArray(la + 2); private val dqN = LongArray(la + 2); private var dqC = 0; private var dqM = 0; private var nMostra = 0L
    @Volatile var sostre = 0.98f
    /** Alliberament: fracció per mostra de la recuperació del guany (≈ 80 ms). */
    private val rel = (1.0 - Math.exp(-1.0 / (0.08 * fs))).toFloat()
    val latencia: Int get() = la
    init { need.fill(1f); box.fill(1f); suma = (la + 1).toDouble() }
    fun process(l: FloatArray, r: FloatArray, frames: Int) {
        val s = sostre
        for (i in 0 until frames) {
            val pk = max(abs(l[i]), abs(r[i]))
            val nd = if (pk > s) s / pk else 1f
            // finestra de mínim sobre les últimes la+1 necessitats
            need[p] = nd
            val cap = dqV.size
            while (dqM > 0) { val u = (dqC + dqM - 1) % cap; if (dqV[u] >= nd) dqM-- else break }
            val w = (dqC + dqM) % cap; dqV[w] = nd; dqN[w] = nMostra; dqM++
            if (dqN[dqC] <= nMostra - (la + 1)) { dqC = (dqC + 1) % cap; dqM-- }
            nMostra++
            val mn = dqV[dqC]
            // mitjana mòbil del mínim (mateixa llargada): garanteix gain ≤ nd a l'instant del pic
            suma += mn - box[p]; box[p] = mn
            val gb = (suma / (la + 1)).toFloat()
            val g = min(gb, gPrev + (1f - gPrev) * rel)
            gPrev = g
            val q = if (p == la) 0 else p + 1       // la mostra més antiga (retardada la mostres)
            val ol = dl[q]; val orr = dr[q]
            dl[p] = l[i]; dr[p] = r[i]
            l[i] = ol * g; r[i] = orr * g
            p = q
        }
    }
    fun reset() { dl.fill(0f); dr.fill(0f); need.fill(1f); box.fill(1f); suma = (la + 1).toDouble(); gPrev = 1f; p = 0; dqC = 0; dqM = 0; nMostra = 0L }
}

/**
 * Cadena d'efectes comuna (per a tots els motors, entre `renderitza` i l'`AudioTrack`):
 * EQ → chorus → reverb (send) → amplada/mono (M/S) + balanç → limitador. Tot a Float; entrada i sortida, `ShortArray` estèreo entrellaçat, en el lloc.
 * Amb tot a «neutre» (`!actiu`) no toca ni un bit del buffer (retorna de seguida). Cap assignació a `process` (després de la primera crida amb un bloc més gran).
 */
class CadenaEfectes(val fs: Int) {
    val eq = EqGrafic(fs); val chorus = Chorus(fs); val reverb = Reverb(fs); val limitador = Limitador(fs)
    @Volatile var amplada = 1f
    @Volatile var balanc = 0f
    @Volatile var mono = false
    @Volatile var limita = true
    private var l = FloatArray(1024); private var r = FloatArray(1024)
    val actiu: Boolean get() = eq.actiu || chorus.mix > 0f || reverb.wet > 0f || amplada != 1f || balanc != 0f || mono
    fun process(buf: ShortArray, frames: Int) {
        val hiEq = eq.actualitza()
        if (!hiEq && chorus.mix <= 0f && reverb.wet <= 0f && amplada == 1f && balanc == 0f && !mono) return
        if (l.size < frames) { l = FloatArray(frames); r = FloatArray(frames) }
        val k = 1f / 32768f
        for (i in 0 until frames) { l[i] = buf[2 * i] * k; r[i] = buf[2 * i + 1] * k }
        if (hiEq) eq.processEstereo(l, r, frames)
        val mn = mono
        if (!mn) chorus.process(l, r, frames)                     // el chorus fa pinta si després es suma a mono
        if (reverb.wet > 0f) reverb.afegeix(l, r, l, r, frames)
        if (mn) { for (i in 0 until frames) { val m = (l[i] + r[i]) * 0.5f; l[i] = m; r[i] = m } }
        else Amplada.process(l, r, frames, amplada, balanc)
        if (limita) limitador.process(l, r, frames)
        for (i in 0 until frames) {
            buf[2 * i] = Math.round(l[i] * 32768f).coerceIn(-32768, 32767).toShort()
            buf[2 * i + 1] = Math.round(r[i] * 32768f).coerceIn(-32768, 32767).toShort()
        }
    }
    fun reset() { eq.reset(); chorus.reset(); reverb.reset(); limitador.reset() }
}

/** Valors de partida per consola (no escoltats: vegeu PROPOSTA_EFECTES.md). EQ en dB per banda; la resta, escales de la cadena. */
class PresetEfectes(val nom: String, val eq: FloatArray, val chorus: Float, val reverb: Float, val sala: Float, val amortiment: Float, val amplada: Float) {
    fun aplica(c: CadenaEfectes) {
        for (k in eq.indices) c.eq.posa(k, eq[k])
        c.chorus.mix = chorus; c.reverb.wet = reverb; c.reverb.sala = sala; c.reverb.amortiment = amortiment; c.amplada = amplada; c.mono = false
    }
    companion object {
        private val PLA = FloatArray(10)
        /** NES: mono de fàbrica; chorus per obrir-la + sala petita. GB: ja té pan per canal; poc. SNES i GBA ja porten reverb/eco propis: cap reverb afegida. DS: seca. */
        val TOTS = listOf(
            PresetEfectes("Pla", PLA, 0f, 0f, 0.5f, 0.5f, 1f),
            PresetEfectes("8 bit - NES", floatArrayOf(2f, 1.5f, 1f, 0f, 0f, 0f, 0f, -1f, -2f, -3f), 0.35f, 0.08f, 0.35f, 0.6f, 1f),
            PresetEfectes("8 bit - Game Boy", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, -1f, -2f, -2f), 0f, 0.06f, 0.3f, 0.6f, 1.1f),
            PresetEfectes("16 bit - SNES", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 2f, 2f), 0f, 0f, 0.5f, 0.5f, 1f),
            PresetEfectes("GBA", floatArrayOf(2f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f), 0f, 0f, 0.5f, 0.5f, 1f),
            PresetEfectes("DS", PLA, 0f, 0.10f, 0.5f, 0.5f, 1.1f)
        )
        /** Preset d'una consola (clau de `consolaDe` d'OSTixxx Music: GB, GBC, NES, SNES, GBA, DS, Altres). A l'app només se'n fan servir el chorus i la reverb (l'EQ i l'amplada són globals). */
        fun perConsola(c: String): PresetEfectes = when (c) {
            "NES" -> TOTS[1]; "GB", "GBC" -> TOTS[2]; "SNES" -> TOTS[3]; "GBA" -> TOTS[4]; "DS" -> TOTS[5]; else -> TOTS[0]
        }
    }
}

/**
 * Còpia del so sec per al mode comparació (A/B sense efectes, 2026-11-17): `out` = `inp` retardat `d` mostres (estèreo entrellaçat), amb memòria entre blocs.
 * Amb `d` = 0 és una còpia. Cap assignació després del primer bloc.
 */
class RetardSec {
    private var cua = ShortArray(0); private var n = 0
    fun buida() { n = 0 }
    fun copia(inp: ShortArray, out: ShortArray, frames: Int, d: Int) {
        val m = 2 * frames; val dd = 2 * d
        if (dd == 0) { System.arraycopy(inp, 0, out, 0, m); n = 0; return }
        if (cua.size != dd) { cua = ShortArray(dd); n = 0 }
        if (n < dd) { java.util.Arrays.fill(cua, 0, dd, 0); n = dd }          // en començar, silenci de la mida del retard (com el limitador)
        // out = cua (dd) + inp (m - dd); cua = últimes dd mostres d'inp
        val k = minOf(dd, m)
        System.arraycopy(cua, 0, out, 0, k)
        if (m > dd) System.arraycopy(inp, 0, out, dd, m - dd)
        if (m >= dd) System.arraycopy(inp, m - dd, cua, 0, dd)
        else { System.arraycopy(cua, m, cua, 0, dd - m); System.arraycopy(inp, 0, cua, dd - m, m) }
    }
}
