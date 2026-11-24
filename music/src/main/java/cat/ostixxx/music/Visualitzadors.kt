package cat.ostixxx.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import cat.gbdsmixxx.t
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Espectre
import cat.gbdsmixxx.OscDades
import cat.gbdsmixxx.OscMestre
import cat.gbdsmixxx.Oscil
import cat.gbdsmixxx.RadioMotor

/**
 * Visualitzadors (rework de l'autor, 2026-11-17): **sense fons** (dibuixats directament sobre la pantalla), **en color** (9 colors + «Color del joc», Ajustos),
 * barres sempre fetes de **blocs quadrats** i tres formes: barres, oscil·loscopi i «Barres i ones» (a l'estil del Windows Media Player).
 * Les èpoques (90's VMU, 2000's LCD, 2010's LED, 2020's detallat) canvien la mida i el tractament dels blocs i de la traça.
 * Les dades es llegeixen de nou a cada fotograma (60 fps) amb `EspectreVis` i `OnaVis` (Kotlin pur, provats a JVM); sense so, cauen i es queden quiets (el rellotge s'atura).
 */
object Vis {
    /** Barres segons el so total (false) o segons el canal (true, si el motor ho sap). Preferència `vis_mode`. */
    var canal by androidx.compose.runtime.mutableStateOf(false)
    /** 0 = barres, 1 = oscil·loscopi, 2 = barres i ones (WMP). Preferència `vis_forma`. */
    var forma by mutableIntStateOf(0)
    /** 0 = 90's (VMU), 1 = 2000's (LCD), 2 = 2010's (LED), 3 = 2020's (detallat). Preferència `vis_estil`. */
    var estil by mutableIntStateOf(3)
    /** `ColorsVis`: 0 = color del joc, 1-9. Preferència `vis_color`. */
    var color by mutableIntStateOf(0)
    val NOMS_ESTIL = listOf("90's", "2000's", "2010's", "2020's")
    /** Claus de `t()` de les formes. */
    val NOMS_FORMA = listOf("Barres", "Oscil·loscopi", "Barres i ones")
}

/** Rellotge ràpid de les peces petites que segueixen la música (barra de progrés, portada que batega, barres de la fila que sona). ~22 Hz, només si sona. */
object VisTick { var v by mutableIntStateOf(0) }

/**
 * Els visualitzadors d'aquesta capa queden tapats del tot per una capa opaca (revisió UI, 2026-11-22): el mini reproductor i l'Inici sota el reproductor gran obert del tot
 * o el concert, i el reproductor gran sota el concert. Mentre és cert, no demanen fotogrames ni dibuixen (abans es dibuixaven sota, a 60 fps, sense que es veiessin).
 * No canvia el ritme dels que es veuen.
 */
val LocalVisTapat = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.State<Boolean>> { NoTapat }
private object NoTapat : androidx.compose.runtime.State<Boolean> { override val value: Boolean get() = false }

/** Quants visualitzadors d'ona hi ha a la pantalla: els motors només alimenten l'oscil·loscopi mentre n'hi ha algun. */
object OscUs { var n = 0 }

/** El visualitzador triat a Ajustos (forma, època i color). `color` és el color del joc (l'accent d'ara o el de la portada). */
@Composable
fun Visualitzador(mod: Modifier, color: Color, compacte: Boolean = false, mirall: Boolean = false) {
    when (Vis.forma) {
        1 -> Osciloscopi(mod, color, compacte)
        2 -> BarresIOnes(mod, color, compacte)
        else -> Barres(mod, color, mirall)
    }
}

// ---------------------------------------------------------------- rellotge i colors

/** Temps del fotograma (ns) mentre sona; quan s'atura, continua fins que `quiet()` diu que ja no es mou res i llavors s'atura (cap dibuix més). */
@Composable
private fun rellotge(quiet: () -> Boolean): androidx.compose.runtime.State<Long> {
    val t = remember { mutableLongStateOf(0L) }
    val actiu = EstatRepro.actiu
    val tapat = LocalVisTapat.current.value
    LaunchedEffect(actiu, tapat) {
        if (tapat) return@LaunchedEffect          // tapat: cap fotograma (torna a anar en destapar-se)
        while (true) {
            withFrameNanos { t.longValue = it }
            if (!actiu && quiet()) break
        }
    }
    return t
}

/** Rampes de color de cada columna (baix, mig, dalt), recalculades només si canvia el color triat, el del joc, el fons o el nombre de columnes. */
internal class Pintor {
    // la clau eren 5 camps dins d'un text nou a cada fotograma; ara es comparen els camps (revisió UI, 2026-11-22)
    private var kSel = -1; private var kJoc = Color.Unspecified; private var kFons = Color.Unspecified; private var kFosc = false; private var kN = -1
    var rampes: Array<Array<Color>> = emptyArray(); private set
    fun prepara(sel: Int, joc: Color, fons: Color, fosc: Boolean, cols: Int) {
        val n = if (sel == ColorsVis.ARC) cols.coerceAtLeast(1) else 1
        if (sel == kSel && joc == kJoc && fons == kFons && fosc == kFosc && n == kN && rampes.isNotEmpty()) return
        kSel = sel; kJoc = joc; kFons = fons; kFosc = fosc; kN = n
        val fonsI = ColorsPurs.opac(argb(fons))
        rampes = Array(n) { i ->
            val base = when (sel) {
                ColorsVis.JOC -> argb(joc)
                ColorsVis.ARC -> ColorsVis.base(ColorsVis.ARC, ColorsVis.toArc(i, n))
                else -> ColorsVis.base(sel)
            }
            ColorsVis.rampa(ColorsPurs.opac(base), fonsI, fosc).map { Color(it) }.toTypedArray()
        }
    }
    /** Rampa de la columna `k` (de `n`). */
    fun de(k: Int, n: Int): Array<Color> = if (rampes.size <= 1) rampes[0] else rampes[(k * rampes.size / n.coerceAtLeast(1)).coerceIn(0, rampes.size - 1)]
    /** Color d'una alçada `f` (0 baix, 1 dalt) dins la rampa. */
    fun alcada(r: Array<Color>, f: Float): Color = if (f < 0.55f) r[0] else if (f < 0.82f) r[1] else r[2]

    private fun argb(c: Color): Int = ColorsPurs.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
}

private class Model { val esp = EspectreVis(); val ones = ArrayList<OnaVis>(); var t0 = 0L; val pintor = Pintor(); val pintorOnes = Pintor(); var quiet = true; var buf = FloatArray(0) }

/** Segons des de l'últim fotograma (entre 0 i 0,1). */
private fun Model.dt(t: Long): Float { val d = if (t0 == 0L) 0.016f else ((t - t0) / 1e9f).coerceIn(0f, 0.1f); t0 = t; return d }

// ---------------------------------------------------------------- barres

/** Mida del bloc (px) i de l'espai entre blocs per a cada època, segons l'alçada disponible. */
private fun DrawScope.bloc(estil: Int, compacte: Boolean): Pair<Float, Float> {
    val h = size.height
    val c = when (estil) {
        0 -> maxOf(2.5.dp.toPx(), h / 26f)                                   // VMU: píxels petits
        1 -> (h / 15f).coerceIn(3.dp.toPx(), 10.dp.toPx())                  // LCD: segments mitjans
        2 -> (h / 12f).coerceIn(3.5.dp.toPx(), 12.dp.toPx())               // LED: quadrats grans
        else -> (h / 18f).coerceIn(3.dp.toPx(), 9.dp.toPx())                // detallat
    }.let { if (compacte) minOf(it, h / 5f) else it }
    val gap = when (estil) { 0 -> maxOf(1f, c * 0.18f); 1 -> c * 0.30f; 2 -> c * 0.28f; else -> c * 0.22f }
    return c to gap
}

/** Barres de blocs quadrats (també a la vista prèvia i al mini reproductor). `mirall` = reflex a sota (reproductor gran, època 2020's). */
@Composable
fun Barres(mod: Modifier, color: Color, mirall: Boolean = false) {
    val m = remember { Model() }
    val t by rellotge { m.quiet }
    val tapat = LocalVisTapat.current
    Canvas(mod) {
        if (tapat.value) return@Canvas
        val dt = m.dt(t)
        dibuixaBarres(m, color, Vis.estil, mirall && Vis.estil == 3, size.height, 0f, dtExt = dt)
        m.quiet = m.esp.quiet
    }
}

/** Dibuixa les barres a la franja [top, top + alt]. */
private fun DrawScope.dibuixaBarres(m: Model, joc: Color, estil: Int, mirall: Boolean, alt: Float, top: Float, compacte: Boolean = false, dtExt: Float = -1f) {
    val (c, gap) = bloc(estil, compacte || alt < 40.dp.toPx())
    val pas = c + gap
    val ampleCol = if (estil == 0) 2 * c + gap else c                  // VMU: columnes de 2 píxels
    val cols = ((size.width + gap * 2) / (ampleCol + (if (estil == 0) gap * 2 else gap))).toInt().coerceIn(1, m.esp.maxCols)
    val altUtil = if (mirall) alt * 0.78f else alt
    val files = (altUtil / pas).toInt().coerceAtLeast(1)
    val canal = if (Vis.canal) RadioMotor.nivVis else null
    val brut = canal ?: Espectre.valors
    m.esp.avanca(brut, brut.size, cols, EstatRepro.actiu, if (dtExt >= 0f) dtExt else 1f / 60f, lineal = canal != null)
    m.pintor.prepara(Vis.color, joc, M.fons, M.fosc, cols)
    val ampleTot = cols * ampleCol + (cols - 1) * (if (estil == 0) gap * 2 else gap)
    val x0 = (size.width - ampleTot) / 2f
    val baseY = top + files * pas
    for (k in 0 until cols) {
        val x = x0 + k * (ampleCol + (if (estil == 0) gap * 2 else gap))
        val r = m.pintor.de(k, cols)
        val encesos = Math.round(m.esp.niv[k] * files)
        val pic = (m.esp.pic[k] * files).toInt().coerceIn(0, files - 1)
        for (f in 0 until files) {
            val y = baseY - (f + 1) * pas + gap / 2f
            val fr = f / (files - 1f).coerceAtLeast(1f)
            val col = m.pintor.alcada(r, fr)
            val ences = f < encesos
            when (estil) {
                // sense fons: els blocs apagats no es dibuixen; només la fila de baix, molt tènue, perquè es vegi on és el visualitzador
                0, 1 -> if (ences) drawRect(col, Offset(x, y), Size(ampleCol, c)) else if (f == 0) drawRect(col.copy(alpha = 0.22f), Offset(x, y), Size(ampleCol, c))
                2 -> if (ences) {                                            // LED: halo quadrat + LED
                    drawRect(col.copy(alpha = 0.22f), Offset(x - gap / 2f, y - gap / 2f), Size(c + gap, c + gap)); drawRect(col, Offset(x, y), Size(c, c))
                } else if (f == 0) drawRect(col.copy(alpha = 0.22f), Offset(x, y), Size(c, c))
                else -> if (ences) drawRect(col, Offset(x, y), Size(c, c)) else if (f == 0) drawRect(col.copy(alpha = 0.22f), Offset(x, y), Size(c, c))
            }
        }
        // pic: un bloc que s'aguanta i cau (en un to més clar)
        if (m.esp.pic[k] > 0.02f && pic >= encesos) {
            val y = baseY - (pic + 1) * pas + gap / 2f
            drawRect(r[2], Offset(x, y), Size(ampleCol, c))
        }
        // reflex (2020's): els primers blocs, de cap per avall i esvaïts
        if (mirall) for (f in 0 until minOf(encesos, 4)) {
            val y = baseY + gap + f * pas
            drawRect(m.pintor.alcada(r, f / (files - 1f).coerceAtLeast(1f)).copy(alpha = 0.18f * (1f - f / 4f)), Offset(x, y), Size(c, c))
        }
    }
}

// ---------------------------------------------------------------- oscil·loscopi

/** Dades d'ona: per canal al reproductor gran (si el motor en té), si no el so total. */
private fun dadesOna(compacte: Boolean): OscDades = (if (!compacte) RadioMotor.sinte?.oscil else null) ?: OscMestre.dades

@Composable
internal fun OscVisible() {
    DisposableEffect(Unit) { OscUs.n++; Oscil.visible = true; onDispose { OscUs.n--; if (OscUs.n <= 0) { OscUs.n = 0; Oscil.visible = false } } }
}

/** Oscil·loscopi: una traça per canal (reproductor gran, si el motor en té) o el so total; disparador estable, guany suau i persistència de fòsfor. */
@Composable
fun Osciloscopi(mod: Modifier, color: Color, compacte: Boolean = false) {
    OscVisible()
    val m = remember { Model() }
    val t by rellotge { m.quiet }
    val tapat = LocalVisTapat.current
    Canvas(mod) {
        if (tapat.value) return@Canvas
        val dt = m.dt(t)
        val o = dadesOna(compacte)
        val traces = if (o !== OscMestre.dades) maxOf(1, minOf(o.visibles, o.buf.size)) else 1
        m.pintor.prepara(Vis.color, color, M.fons, M.fosc, traces)
        val h = size.height / traces
        var senyal = false
        for (c in 0 until traces) {
            while (m.ones.size <= c) m.ones.add(OnaVis())
            val r = m.pintor.de(c, traces)
            senyal = dibuixaOna(m.ones[c], o, c, r, h * c, h, Vis.estil, compacte, dt) || senyal
        }
        m.quiet = !senyal
    }
}

/** Una traça a la franja [top, top + h]. Torna si hi havia senyal. */
private fun DrawScope.dibuixaOna(ona: OnaVis, o: OscDades, canal: Int, r: Array<Color>, top: Float, h: Float, estil: Int, compacte: Boolean, dt: Float, persist: Int = -1): Boolean {
    val w = when (estil) { 0 -> (size.width / maxOf(2.5.dp.toPx(), h / 22f)).toInt(); 2 -> (size.width / maxOf(5.dp.toPx(), h / 14f)).toInt(); else -> if (compacte) 64 else 220 }.coerceIn(8, 256)
    if (ona.buf.size != w) ona.buf = FloatArray(w)          // abans, un FloatArray nou per traça i fotograma (la història en fa còpia: es pot reaprofitar)
    val buf = ona.buf
    val per = if (persist >= 0) persist else when (estil) { 1 -> 3; 3 -> if (compacte) 0 else 4; else -> 0 }
    val senyal = EstatRepro.actiu && ona.captura(o.buf[canal], o.mida, o.pos, w, buf, dt, per)
    if (!senyal) java.util.Arrays.fill(buf, 0f)
    val y0 = top + h / 2f; val alt = h * 0.44f
    when (estil) {
        0 -> {                                                              // VMU: escala de píxels quadrats
            val cel = size.width / w; val c = cel * 0.82f
            val files = (h / cel).toInt().coerceAtLeast(3)
            var ant = -1
            for (i in 0 until w) {
                val f = ((1f - (buf[i] * 0.5f + 0.5f)) * (files - 1)).toInt().coerceIn(0, files - 1)
                val a = if (ant < 0) f else ant
                for (q in minOf(a, f)..maxOf(a, f)) drawRect(r[if (Math.abs(q - (files - 1) / 2) > files / 3) 2 else 0], Offset(i * cel, top + q * cel), Size(c, c))
                ant = f
            }
        }
        2 -> {                                                              // LED: punts quadrats amb halo
            val pas = size.width / w; val c = pas * 0.62f
            for (i in 0 until w) {
                val y = y0 - alt * buf[i]
                val col = r[if (Math.abs(buf[i]) > 0.6f) 2 else if (Math.abs(buf[i]) > 0.25f) 1 else 0]
                drawRect(col.copy(alpha = 0.22f), Offset(i * pas - c * 0.3f, y - c * 0.8f), Size(c * 1.6f, c * 1.6f))
                drawRect(col, Offset(i * pas, y - c / 2f), Size(c, c))
            }
        }
        else -> {                                                           // LCD (gruixuda, amb rastre) i 2020's (fina, brillant, amb rastre)
            val gruix = (if (estil == 1) 3.2f else if (compacte) 1.6f else 2.4f).dp.toPx()
            val brush = Brush.verticalGradient(0f to r[2], 0.35f to r[1], 0.5f to r[0], 0.65f to r[1], 1f to r[2], startY = top, endY = top + h)
            for ((k, vella) in ona.historia.withIndex()) {
                if (k == 0) continue
                drawPath(cami(vella, top, h, alt), brush, alpha = 0.30f / k, style = Stroke(gruix, cap = StrokeCap.Square, join = StrokeJoin.Round))
            }
            val p = cami(buf, top, h, alt)
            if (estil == 3) drawPath(p, brush, alpha = 0.25f, style = Stroke(gruix * 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(p, brush, style = Stroke(gruix, cap = if (estil == 1) StrokeCap.Square else StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    return senyal
}

private fun DrawScope.cami(v: FloatArray, top: Float, h: Float, alt: Float): Path {
    val p = Path(); val y0 = top + h / 2f; val n = v.size
    for (i in 0 until n) { val x = size.width * i / (n - 1).coerceAtLeast(1); val y = y0 - alt * v[i]; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
    return p
}

// ---------------------------------------------------------------- barres i ones (WMP)

/**
 * «Barres i ones», a l'estil del Windows Media Player («Bars and Waves»): a baix, l'espectre en blocs quadrats; a dalt, l'ona actual i les anteriors
 * que pugen i s'esvaeixen (com «Ocean Mist»), cada una un to més enllà. Fa servir el color triat; no depèn de l'època.
 */
@Composable
fun BarresIOnes(mod: Modifier, color: Color, compacte: Boolean = false) {
    OscVisible()
    val m = remember { Model() }
    val t by rellotge { m.quiet }
    val tapat = LocalVisTapat.current
    Canvas(mod) {
        if (tapat.value) return@Canvas
        val dt = m.dt(t)
        val altBarres = size.height * 0.42f
        val altOnes = size.height - altBarres
        dibuixaBarres(m, color, 3, false, altBarres, altOnes, compacte, dt)
        if (m.ones.isEmpty()) m.ones.add(OnaVis())
        val ona = m.ones[0]
        val o = OscMestre.dades
        val traces = if (compacte) 4 else 14
        val w = if (compacte) 48 else 128
        if (m.buf.size != w) m.buf = FloatArray(w)
        val senyal = EstatRepro.actiu && ona.captura(o.buf[0], o.mida, o.pos, w, m.buf, dt, traces)
        if (!senyal) ona.historia.clear()
        m.pintorOnes.prepara(Vis.color, color, M.fons, M.fosc, traces)
        val hTr = altOnes * 0.55f; val pujada = (altOnes - hTr) / traces.coerceAtLeast(1)
        val gruix = (if (compacte) 1.4f else 2.2f).dp.toPx()
        // de la més vella a la més nova: cada traça vella és més amunt, més clara i d'un altre to
        for (k in ona.historia.indices.reversed()) {
            val v = ona.historia[k]
            val r = m.pintorOnes.de(k % maxOf(1, m.pintorOnes.rampes.size), maxOf(1, m.pintorOnes.rampes.size))
            val col = r[(k * 3 / traces.coerceAtLeast(1)).coerceIn(0, 2)]
            val top = altOnes - hTr - k * pujada
            drawPath(cami(v, top, hTr, hTr * 0.42f), col, alpha = (1f - k / traces.toFloat()).coerceIn(0.06f, 1f), style = Stroke(gruix * (1f - 0.04f * k).coerceAtLeast(0.5f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (ona.historia.isEmpty()) {
            val r = m.pintorOnes.de(0, 1)
            drawLine(r[0].copy(alpha = 0.5f), Offset(0f, altOnes - hTr / 2f), Offset(size.width, altOnes - hTr / 2f), gruix * 0.6f)
        }
        m.quiet = m.esp.quiet && !senyal
    }
}

// ---------------------------------------------------------------- indicador petit

/** Indicador de la cançó que sona (llistes): quatre columnes de blocs quadrats que segueixen bandes de l'espectre. */
@Composable
fun BarresMusica(color: Color, mida: Dp = 18.dp) {
    Canvas(Modifier.size(mida)) {
        VisTick.v
        val v = Espectre.valors
        val bandes = intArrayOf(1, 4, 7, 10)
        val actiu = EstatRepro.actiu
        val files = 4
        val c = size.width / 5.2f; val gap = c * 0.4f
        for ((i, b) in bandes.withIndex()) {
            val n = if (actiu) Math.round(EspectreVis.nivellDb(v[b.coerceIn(0, v.size - 1)]) * files).coerceIn(1, files) else 1
            for (f in 0 until n) drawRect(color, Offset(i * (c + gap), size.height - (f + 1) * (c + gap) + gap), Size(c, c))
        }
    }
}

/** Opcions de color del visualitzador (Ajustos i panell «Visual»): cercles de color; el primer és el del joc, l'últim l'arc de Sant Martí. */
@Composable
fun TriaColorVis() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in ColorsVis.NOMS.indices) {
            val sel = Vis.color == k
            val mostra: Brush = when (k) {
                ColorsVis.JOC -> Brush.sweepGradient(listOf(M.accent, M.suau, M.accent))
                ColorsVis.ARC -> Brush.sweepGradient((0..6).map { Color(ColorsPurs.deHsl(it * 47f, 0.85f, 0.56f)) })
                else -> SolidColor(Color(ColorsVis.base(k)))
            }
            Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(mostra)
                .then(if (sel) Modifier.border(3.dp, M.text, CircleShape) else Modifier.border(1.dp, M.contorn, CircleShape))
                .botoAccessible(t(ColorsVis.NOMS[k])).premsable(0.85f) { Vis.color = k; App.env.putInt("vis_color", k) }, contentAlignment = Alignment.Center) {
                if (sel) Icona(CamiIcones.FET, Color.White, 16.dp)
            }
        }
    }
    Tx(t(ColorsVis.NOMS[Vis.color.coerceIn(0, ColorsVis.NOMS.size - 1)]), Tipus.petit, M.tenu)
}
