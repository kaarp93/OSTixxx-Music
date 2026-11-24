package cat.ostixxx.music

// Kotlin pur (sense Android ni Compose): es prova a JVM (eines/prototips/ui/prova.sh). Colors en ARGB (Int).

/** Mode del tema (Ajustos > Tema): segueix el sistema, clar, fosc o AMOLED (negre pur). `codi` és el que es desa a les preferències (`tema_mode`). */
enum class ModeTema(val codi: Int) {
    SISTEMA(0), CLAR(1), FOSC(2), AMOLED(3);
    /** El mode real: SISTEMA es resol amb el mode del mòbil. */
    fun resol(sistemaFosc: Boolean): ModeTema = if (this == SISTEMA) (if (sistemaFosc) FOSC else CLAR) else this
    companion object {
        fun de(codi: Int): ModeTema = values().firstOrNull { it.codi == codi } ?: FOSC
    }
}

/**
 * Paleta de la interfície, calculada a partir d'un color llavor (el de la portada que sona) i del mode. Tots els colors són ARGB opacs.
 *  - `fons`: fons de les pantalles; `fonsAlt`: color de dalt del degradat del fons (tenyit amb la portada).
 *  - `superficie` / `superficieAlta`: targetes i fulls (l'alta, per sobre); `contorn`: vores i separadors.
 *  - `text` / `tenu`: text principal i secundari; `accent` / `sobreAccent`: botó principal i el text o la icona de sobre.
 *  - `accentSuau` / `sobreAccentSuau`: contenidor tonal (xips actius, pastilla de la barra de navegació).
 */
data class Paleta(
    val mode: ModeTema,
    val fons: Int, val fonsAlt: Int,
    val superficie: Int, val superficieAlta: Int, val contorn: Int,
    val text: Int, val tenu: Int,
    val accent: Int, val sobreAccent: Int,
    val accentSuau: Int, val sobreAccentSuau: Int,
    val error: Int,
) {
    val fosc: Boolean get() = mode != ModeTema.CLAR
}

/** Aritmètica de colors (sRGB, HSL, luminància i contrast WCAG). */
object ColorsPurs {
    fun a(c: Int) = (c ushr 24) and 255
    fun r(c: Int) = (c shr 16) and 255
    fun g(c: Int) = (c shr 8) and 255
    fun b(c: Int) = c and 255
    fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    fun opac(c: Int): Int = c or (0xFF shl 24)

    private fun lin(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
    /** Luminància relativa (WCAG 2). */
    fun luminancia(c: Int): Double = 0.2126 * lin(r(c)) + 0.7152 * lin(g(c)) + 0.0722 * lin(b(c))
    /** Contrast WCAG entre dos colors opacs: d'1 a 21. */
    fun contrast(x: Int, y: Int): Double { val l1 = luminancia(x); val l2 = luminancia(y); return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05) }

    /** [to (0-360), saturació (0-1), lluminositat (0-1)]. */
    fun aHsl(c: Int): FloatArray {
        val rf = r(c) / 255f; val gf = g(c) / 255f; val bf = b(c) / 255f
        val mx = maxOf(rf, gf, bf); val mn = minOf(rf, gf, bf); val l = (mx + mn) / 2f
        if (mx == mn) return floatArrayOf(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        val h = when (mx) { rf -> ((gf - bf) / d + (if (gf < bf) 6f else 0f)); gf -> ((bf - rf) / d + 2f); else -> ((rf - gf) / d + 4f) } * 60f
        return floatArrayOf(h, s, l)
    }

    fun deHsl(h: Float, s: Float, l: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f / 360f; val ss = s.coerceIn(0f, 1f); val ll = l.coerceIn(0f, 1f)
        if (ss == 0f) { val v = Math.round(ll * 255f); return argb(255, v, v, v) }
        val q = if (ll < 0.5f) ll * (1f + ss) else ll + ss - ll * ss
        val p = 2f * ll - q
        fun canal(t0: Float): Int {
            var t = t0; if (t < 0f) t += 1f; if (t > 1f) t -= 1f
            val v = when { t < 1f / 6f -> p + (q - p) * 6f * t; t < 0.5f -> q; t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f; else -> p }
            return Math.round(v * 255f)
        }
        return argb(255, canal(hh + 1f / 3f), canal(hh), canal(hh - 1f / 3f))
    }

    /** Barreja lineal (t = 0 → x, t = 1 → y). */
    fun barreja(x: Int, y: Int, t: Float): Int {
        fun m(p: Int, q: Int) = Math.round(p + (q - p) * t.coerceIn(0f, 1f))
        return argb(m(a(x), a(y)), m(r(x), r(y)), m(g(x), g(y)), m(b(x), b(y)))
    }

    /** Puja (`dir` = 1) o baixa (`dir` = -1) la lluminositat de `c` fins que té un contrast `min` amb `fons` (o fins al límit). */
    fun ambContrast(c: Int, fons: Int, min: Double, dir: Int): Int {
        val hsl = aHsl(c); var l = hsl[2]; var x = c; var n = 0
        while (contrast(x, fons) < min && n < 60) { l = (l + 0.02f * dir).coerceIn(0f, 1f); x = deHsl(hsl[0], hsl[1], l); n++; if (l == 0f || l == 1f) break }
        return x
    }
}

/** Genera la paleta clara, fosca o AMOLED d'un color llavor. Sense llavor (o amb una de grisa) fa servir la paleta de reserva. */
object Paletes {
    /** Color de reserva (quan no hi ha portada): el violeta d'OSTixxx Music. */
    const val RESERVA: Int = 0xFF7C6CFF.toInt()
    /** Saturació mínima perquè una llavor es faci servir (per sota, la portada és gairebé grisa i es fa servir la reserva). */
    const val SAT_MIN = 0.12f
    private const val BLANC: Int = 0xFFFFFFFF.toInt()
    private const val NEGRE: Int = 0xFF101014.toInt()
    private const val NEGRE_PUR: Int = 0xFF000000.toInt()

    fun genera(llavor: Int?, mode0: ModeTema, sistemaFosc: Boolean = true): Paleta {
        val mode = mode0.resol(sistemaFosc)
        val seed = llavor?.let { ColorsPurs.opac(it) }?.takeIf { val h = ColorsPurs.aHsl(it); h[1] >= SAT_MIN && h[2] in 0.06f..0.96f } ?: RESERVA
        val hsl = ColorsPurs.aHsl(seed); val h = hsl[0]; val sA = hsl[1].coerceIn(0.45f, 0.85f)
        val C = ColorsPurs
        return if (mode == ModeTema.CLAR) {
            val fons = C.deHsl(h, 0.30f, 0.97f)
            val sup = C.deHsl(h, 0.28f, 0.93f); val supA = C.deHsl(h, 0.26f, 0.89f)
            val text = C.ambContrast(C.deHsl(h, 0.20f, 0.10f), supA, 7.0, -1)
            val tenu = C.ambContrast(C.deHsl(h, 0.10f, 0.38f), supA, 4.5, -1)
            // l'accent s'enfosqueix fins que es llegeix sobre el fons i el blanc s'hi llegeix al damunt
            val accent = C.ambContrast(C.ambContrast(C.ambContrast(C.deHsl(h, sA, 0.42f), fons, 4.5, -1), supA, 3.0, -1), BLANC, 4.6, -1)
            val suau = C.ambContrast(C.deHsl(h, sA * 0.70f, 0.85f), NEGRE, 8.0, 1)
            Paleta(mode, fons, C.deHsl(h, sA * 0.60f, 0.86f), sup, supA, C.deHsl(h, 0.16f, 0.80f), text, tenu,
                accent, sobre(accent), suau, C.ambContrast(C.deHsl(h, 0.60f, 0.15f), suau, 7.0, -1), 0xFFBA1A1A.toInt())
        } else {
            val amoled = mode == ModeTema.AMOLED
            val fons = if (amoled) NEGRE_PUR else C.deHsl(h, 0.22f, 0.07f)
            // AMOLED (l'autor, 2026-11-17: «no hi ha cap negre pur»): fons, degradat i superfícies negres de veritat i sense tenyir; les elevades, gairebé negres i neutres.
            // La separació la fan les vores (`contorn`, gris neutre), no els grisos de fons.
            val sup = if (amoled) NEGRE_PUR else C.deHsl(h, 0.18f, 0.12f)
            val supA = if (amoled) 0xFF0A0A0A.toInt() else C.deHsl(h, 0.16f, 0.17f)
            val text = C.ambContrast(C.deHsl(h, 0.10f, 0.96f), supA, 7.0, 1)
            val tenu = C.ambContrast(C.deHsl(h, 0.08f, 0.72f), supA, 4.5, 1)
            // l'accent s'aclareix fins que es llegeix sobre el fons i el negre s'hi llegeix al damunt
            val accent = C.ambContrast(C.ambContrast(C.ambContrast(C.deHsl(h, sA, 0.68f), fons, 4.5, 1), supA, 3.0, 1), NEGRE, 4.6, 1)
            val suau = C.ambContrast(C.deHsl(h, sA * 0.60f, if (amoled) 0.18f else 0.24f), BLANC, 8.0, -1)
            Paleta(mode, fons, if (amoled) NEGRE_PUR else C.deHsl(h, sA * 0.70f, 0.20f), sup, supA, if (amoled) 0xFF2A2A2A.toInt() else C.deHsl(h, 0.12f, 0.26f), text, tenu,
                accent, sobre(accent), suau, C.ambContrast(C.deHsl(h, 0.50f, 0.90f), suau, 7.0, 1), 0xFFFFB4AB.toInt())
        }
    }

    /** Text o icona sobre un color: blanc o gairebé negre, el que tingui més contrast. */
    fun sobre(c: Int): Int {
        return if (ColorsPurs.contrast(BLANC, c) >= ColorsPurs.contrast(NEGRE, c)) BLANC else NEGRE
    }
}

/**
 * Color llavor d'una portada (com Palette d'Android, però propi): histograma de 36 tons ponderat per saturació i lluminositat (els colors vius pesen més),
 * el to guanyador (sumant-hi la meitat dels veïns) i la mitjana dels seus píxels. Els píxels transparents no compten.
 * Torna null si no hi ha cap píxel opac; si la portada és gairebé grisa, torna la mitjana (que `Paletes.genera` canvia per la reserva).
 */
object ColorPortada {
    private const val CUBS = 36
    fun llavor(px: IntArray): Int? {
        val w = DoubleArray(CUBS); val sr = DoubleArray(CUBS); val sg = DoubleArray(CUBS); val sb = DoubleArray(CUBS)
        var n = 0; var tr = 0L; var tg = 0L; var tb = 0L
        for (p in px) {
            if ((p ushr 24) < 128) continue
            val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
            n++; tr += r; tg += g; tb += b
            val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
            if (mx == 0) continue
            val sat = (mx - mn) / mx.toFloat(); val v = mx / 255f
            if (sat < 0.20f || v < 0.20f) continue
            val h = ColorsPurs.aHsl(p)[0]
            val k = ((h / 360f) * CUBS).toInt().coerceIn(0, CUBS - 1)
            val pes = (sat * sat * v).toDouble()          // els colors vius i lluminosos pesen més
            w[k] += pes; sr[k] += r * pes; sg[k] += g * pes; sb[k] += b * pes
        }
        if (n == 0) return null
        var total = 0.0; for (x in w) total += x
        if (total < 0.02 * n) return ColorsPurs.argb(255, (tr / n).toInt(), (tg / n).toInt(), (tb / n).toInt())
        var millor = 0; var punts = -1.0
        for (k in 0 until CUBS) {
            val s = w[k] + 0.5 * (w[(k + CUBS - 1) % CUBS] + w[(k + 1) % CUBS])
            if (s > punts) { punts = s; millor = k }
        }
        val pw = w[millor]
        return ColorsPurs.argb(255, (sr[millor] / pw).toInt(), (sg[millor] / pw).toInt(), (sb[millor] / pw).toInt())
    }
}
