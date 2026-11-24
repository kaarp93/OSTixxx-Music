package cat.ostixxx.music

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.gbdsmixxx.OscDades
import cat.gbdsmixxx.OscMestre
import cat.gbdsmixxx.RadioMotor
import cat.gbdsmixxx.t
import kotlin.math.cos
import kotlin.math.sin

// Dibuix de les visualitzacions del mode concert (2026-11-17). Totes: sense fons, blocs quadrats, color de `Concert.color(v)`, opcions de `Concert.op(v, ...)`.

/** Quadrat de costat `c` centrat a (x, y). */
internal fun DrawScope.quadrat(col: Color, x: Float, y: Float, c: Float) = drawRect(col, Offset(x - c / 2f, y - c / 2f), Size(c, c))

private val camiForma = androidx.compose.ui.graphics.Path()          // només fil principal (dibuix)

/** Una partícula de forma `f`: 0 quadrat, 1 cercle, 2 estel (creu de quatre puntes). */
internal fun DrawScope.forma(f: Int, col: Color, x: Float, y: Float, c: Float) {
    when (f) {
        1 -> drawCircle(col, c / 2f, Offset(x, y))
        2 -> {
            // un sol camí que es reaprofita (abans, un Path nou per partícula i fotograma: fins a 900 als Estels). El dibuix en desa una còpia, així que reiniciar-lo és segur.
            val p = camiForma; p.reset()
            val r = c * 0.75f; val q = c * 0.16f
            p.moveTo(x, y - r); p.lineTo(x + q, y - q); p.lineTo(x + r, y); p.lineTo(x + q, y + q); p.lineTo(x, y + r); p.lineTo(x - q, y + q); p.lineTo(x - r, y); p.lineTo(x - q, y - q); p.close()
            drawPath(p, col)
        }
        else -> quadrat(col, x, y, c)
    }
}

private fun graus(r: Float) = r * 57.29578f

// ---------------------------------------------------------------- 0. espectre circular

@Composable
internal fun EspectreCircular(m: ModelConcert, t: State<Long>, joc: cat.gbdsmixxx.Emissora?, v: Int) {
    val ambPortada = Concert.si(v, "portada")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val costat = minOf(maxWidth, maxHeight) * (if (ambPortada) 0.34f else 0.22f)
        Canvas(Modifier.fillMaxSize()) {
            val dt = m.dt(t.value)
            val cols = when (Concert.op(v, "cols")) { 0 -> 32; 2 -> 72; else -> 48 }
            val mirall = Concert.si(v, "mirall")
            val bandes = if (mirall) cols / 2 else cols
            m.pas(dt, bandes, Concert.sensibilitat(v))
            m.pintor.prepara(Concert.color(v), M.accent, M.fons, M.fosc, cols)
            val c0 = center
            // interacció: tocar fa bategar l'anell; arrossegar el fa girar
            val act = Concert.interactiu(v)
            if (m.prenTocs(act).isNotEmpty()) m.empenta = 1f
            val dr = m.prenArr(act); val dd = m.ditSi(act)
            if (dd != null && dr != Offset.Zero) m.rotacio += graus(kotlin.math.atan2(dd.y - c0.y, dd.x - c0.x) - kotlin.math.atan2(dd.y - dr.y - c0.y, dd.x - dr.x - c0.x))
            val r0 = costat.toPx() / 2f * 1.22f * (1f + 0.07f * m.greus + 0.08f * m.empenta)
            val rMax = minOf(size.width, size.height) / 2f * 0.97f
            val c = minOf(2f * Math.PI.toFloat() * r0 / cols * 0.62f, (rMax - r0) / 8f).coerceAtLeast(2f)
            val pas = c * 1.32f
            val files = ((rMax - r0) / pas).toInt().coerceAtLeast(1)
            val ambPic = Concert.si(v, "pic")
            for (k in 0 until cols) {
                val kk = if (!mirall) k else if (k < bandes) k else cols - 1 - k
                val r = m.pintor.de(k, cols)
                val encesos = Math.round(m.esp.niv[kk] * files)
                val pic = (m.esp.pic[kk] * files).toInt().coerceIn(0, files - 1)
                rotate(-90f + m.rotacio + (k + 0.5f) * 360f / cols, c0) {
                    for (f in 0 until files) {
                        val x = c0.x + r0 + f * pas
                        val fr = f / (files - 1f).coerceAtLeast(1f)
                        if (f < encesos) drawRect(m.pintor.alcada(r, fr), Offset(x, c0.y - c / 2f), Size(c, c))
                        else if (f == 0) drawRect(r[0].copy(alpha = 0.12f), Offset(x, c0.y - c / 2f), Size(c, c))
                    }
                    if (ambPic && m.esp.pic[kk] > 0.02f && pic >= encesos) drawRect(r[2], Offset(c0.x + r0 + pic * pas, c0.y - c / 2f), Size(c, c))
                }
            }
            if (!ambPortada) {
                // sense portada: un quadrat al mig que batega
                val r = m.pintor.de(0, cols)
                quadrat(r[1].copy(alpha = 0.35f + 0.65f * m.greus), c0.x, c0.y, costat.toPx() * (0.5f + 0.3f * m.greus))
            }
        }
        if (ambPortada) Portada(joc, Modifier.size(costat).graphicsLayer { t.value; val e = 1f + 0.08f * m.greus; scaleX = e; scaleY = e }, 14.dp)
    }
}

// ---------------------------------------------------------------- 1. ones per canal

/** Dades d'ona per canal (si el motor en dona) o el so total; i si són per canal. */
private fun dadesCanals(): Pair<OscDades, Boolean> {
    val o = RadioMotor.sinte?.oscil
    return if (o != null) o to true else OscMestre.dades to false
}

@Composable
internal fun OnesCanal(m: ModelConcert, t: State<Long>, v: Int) {
    OscVisible()
    val mesura = rememberTextMeasurer()
    var perCanal by remember { mutableIntStateOf(1) }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val dt = m.dt(t.value)
            m.pas(dt, 12, Concert.sensibilitat(v))
            val (o, canals) = dadesCanals()
            perCanal = if (canals) 1 else 0
            val n = if (canals) maxOf(1, minOf(o.visibles, o.buf.size)) else 1
            val rampes = rampesCanals(n, M.accent, Concert.color(v))
            val h = size.height / n
            val cel = maxOf(3.dp.toPx(), minOf(h / 14f, 6.dp.toPx())) * Concert.mida(v)
            val w = (size.width / cel).toInt().coerceIn(8, 256)
            if (m.buf.size != w) m.buf = FloatArray(w)
            val etiq = TextStyle(fontSize = 11.sp)
            val ambNoms = Concert.si(v, "noms")
            val linia = Concert.op(v, "forma") == 1
            // interacció: tocar una franja la destaca (les altres s'apaguen); tocar-la un altre cop ho desfà
            for (p in m.prenTocs(Concert.interactiu(v))) { val c = (p.y / h).toInt().coerceIn(0, n - 1); m.canalTriat = if (m.canalTriat == c) -1 else c }
            if (m.canalTriat >= n) m.canalTriat = -1
            for (c in 0 until n) {
                while (m.ones.size <= c) m.ones.add(OnaVis())
                val r = rampes[c]
                val top = h * c
                val files = (h / cel).toInt().coerceAtLeast(3)
                val senyal = EstatRepro.actiu && m.ones[c].captura(o.buf[c], o.mida, o.pos, w, m.buf, dt, 0)
                val apaga = if (m.canalTriat >= 0 && m.canalTriat != c) 0.25f else 1f
                if (linia) {
                    // ona suau (línia contínua), sense blocs
                    val path = androidx.compose.ui.graphics.Path()
                    for (i in 0 until w) { val x = i * cel + cel / 2f; val y = top + h / 2f - m.buf[i] * h * 0.42f; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    drawPath(path, (if (senyal) r[2] else r[0]).copy(alpha = (if (senyal) 1f else 0.35f) * apaga), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx() * Concert.mida(v), join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    if (ambNoms && canals && h > 22.dp.toPx()) drawText(mesura, o.noms.getOrElse(c) { "${c + 1}" }, Offset(4.dp.toPx(), top + 2.dp.toPx()), etiq.copy(color = r[2].copy(alpha = apaga)))
                    continue
                }
                var ant = -1
                for (i in 0 until w) {
                    val f = ((1f - (m.buf[i] * 0.42f + 0.5f)) * (files - 1)).toInt().coerceIn(0, files - 1)
                    val a = if (ant < 0) f else ant
                    for (ff in minOf(a, f)..maxOf(a, f)) {
                        val col = if (!senyal) r[0].copy(alpha = 0.35f * apaga) else (if (ff == f) r[2] else r[1]).copy(alpha = apaga)
                        drawRect(col, Offset(i * cel, top + ff * cel), Size(cel * 0.82f, cel * 0.82f))
                    }
                    ant = f
                }
                if (ambNoms && canals && h > 22.dp.toPx()) drawText(mesura, o.noms.getOrElse(c) { "${c + 1}" }, Offset(4.dp.toPx(), top + 2.dp.toPx()), etiq.copy(color = r[2]))
            }
        }
        if (perCanal == 0) Tx(t("Aquest joc no separa els canals: es veu el so total."), Tipus.petit, M.tenu, Modifier.align(Alignment.BottomCenter).padding(8.dp), 2)
    }
}

// ---------------------------------------------------------------- 2. piano

@Composable
internal fun Piano(m: ModelConcert, t: State<Long>, v: Int) {
    OscVisible()
    val mesura = rememberTextMeasurer()
    var perCanal by remember { mutableIntStateOf(1) }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val dt = m.dt(t.value)
            m.pas(dt, 12, Concert.sensibilitat(v))
            val (o, canals) = dadesCanals()
            perCanal = if (canals) 1 else 0
            if (!canals) return@Canvas
            val notaMin = if (Concert.op(v, "octaves") == 0) 36 else 24
            val notaMax = if (Concert.op(v, "octaves") == 0) 84 else 96
            val maxHist = when (Concert.op(v, "rastre")) { 0 -> 0; 1 -> 40; else -> 120 }
            val n = maxOf(1, minOf(o.visibles, o.buf.size))
            val rampes = rampesCanals(n, M.accent, Concert.color(v))
            // notes estimades (30 cops per segon)
            m.tNotes += dt
            if (m.tNotes >= 1f / 30f) {
                m.tNotes = 0f
                if (m.notes.size != n) m.notes = IntArray(n)
                val taxa = (RadioMotor.sinte?.taux ?: 32768) / 4f
                // notes EXACTES dels registres del motor quan els sap donar (GB, NES); si no (SNES, GBA, DS...), estimades a partir de l'ona
                if (m.exactes.size < n) m.exactes = FloatArray(n)
                val exacte = EstatRepro.actiu && (RadioMotor.sinte?.notesMidi(m.exactes) == true)
                if (EstatRepro.actiu) m.notesExactes = exacte
                for (c in 0 until n) {
                    val e = if (!EstatRepro.actiu) -1f
                        else if (exacte) (if (RadioMotor.muts.getOrElse(c) { false } || (RadioMotor.solo >= 0 && RadioMotor.solo != c)) -1f else m.exactes[c])
                        else NotesCanal.estima(o.buf[c], o.mida, o.pos, taxa, m.tmp, m.dif)
                    val k = if (e < 0f) -1 else Math.round(e)
                    m.notes[c] = if (k in notaMin..notaMax) k else -1
                }
                if (EstatRepro.actiu && maxHist > 0) {
                    while (m.historia.size > maxHist) m.historia.removeAt(m.historia.size - 1)
                    val cop = if (m.historia.size >= maxHist) m.historia.removeAt(m.historia.size - 1) else IntArray(n)
                    val c2 = if (cop.size == n) cop else IntArray(n)
                    System.arraycopy(m.notes, 0, c2, 0, n); m.historia.add(0, c2)
                } else m.historia.clear()
            }
            // teclat a baix
            val blanques = (notaMin..notaMax).count { !NotesCanal.negra(it) }
            val wk = size.width / blanques
            val kh = minOf(size.height * 0.30f, 150.dp.toPx())
            val y0 = size.height - kh
            val xs = FloatArray(notaMax + 1)
            var bi = 0
            for (k in notaMin..notaMax) if (!NotesCanal.negra(k)) { xs[k] = bi * wk + wk / 2f; bi++ } else xs[k] = bi * wk
            fun canalsDe(k: Int): List<Int> = m.notes.indices.filter { m.notes[it] == k }
            for (k in notaMin..notaMax) if (!NotesCanal.negra(k)) {
                val x = xs[k] - wk / 2f
                val cs = canalsDe(k)
                if (cs.isEmpty()) drawRect(M.text.copy(alpha = 0.10f), Offset(x + 1f, y0), Size(wk - 2f, kh))
                else cs.forEachIndexed { j, c -> val ww = (wk - 2f) / cs.size; drawRect(rampes[c][1], Offset(x + 1f + j * ww, y0), Size(ww, kh)) }
            }
            val bw = wk * 0.62f
            for (k in notaMin..notaMax) if (NotesCanal.negra(k)) {
                val x = xs[k] - bw / 2f
                val cs = canalsDe(k)
                drawRect(M.fons, Offset(x - 1f, y0), Size(bw + 2f, kh * 0.6f + 1f))
                if (cs.isEmpty()) drawRect(M.text.copy(alpha = 0.26f), Offset(x, y0), Size(bw, kh * 0.6f))
                else cs.forEachIndexed { j, c -> val ww = bw / cs.size; drawRect(rampes[c][2], Offset(x + j * ww, y0), Size(ww, kh * 0.6f)) }
            }
            // interacció: tocar una tecla l'encén un moment i en diu el nom (no sona: el so és el del joc)
            for (p in m.prenTocs(Concert.interactiu(v))) if (p.y >= y0) {
                var tecla = -1
                for (k in notaMin..notaMax) if (NotesCanal.negra(k) && p.y < y0 + kh * 0.6f && Math.abs(p.x - xs[k]) < bw / 2f) tecla = k
                if (tecla < 0) for (k in notaMin..notaMax) if (!NotesCanal.negra(k) && Math.abs(p.x - xs[k]) <= wk / 2f) tecla = k
                if (tecla >= 0) m.part.add(floatArrayOf(tecla.toFloat(), 0f))
            }
            val itp = m.part.iterator()
            while (itp.hasNext()) { val q = itp.next(); q[1] += dt; if (q[1] > 0.6f) itp.remove() }
            val estT = TextStyle(fontSize = 13.sp)
            for (q in m.part) {
                val k = q[0].toInt(); if (k !in notaMin..notaMax) continue
                val a = 1f - q[1] / 0.6f
                val negra = NotesCanal.negra(k)
                drawRect(M.accent.copy(alpha = a), Offset(xs[k] - (if (negra) bw else wk - 2f) / 2f, y0), Size(if (negra) bw else wk - 2f, if (negra) kh * 0.6f else kh))
                val lay = mesura.measure(NotesCanal.nom(k), estT)
                drawText(lay, M.text.copy(alpha = a), Offset(xs[k] - lay.size.width / 2f, y0 - lay.size.height - 4.dp.toPx() - q[1] * 30.dp.toPx()))
            }
            // rastre: quadrats que pugen des de les tecles
            val cel = minOf(wk * 0.8f, 8.dp.toPx())
            val pasY = cel * 1.05f
            val maxFiles = ((y0 - 30.dp.toPx()) / pasY).toInt().coerceAtLeast(0)
            for ((j, nt) in m.historia.withIndex()) {
                if (j >= maxFiles) break
                val y = y0 - (j + 1) * pasY - 2.dp.toPx()
                val a = 1f - j / maxFiles.coerceAtLeast(1).toFloat()
                for (c in nt.indices) { val k = nt[c]; if (k in notaMin..notaMax && c < rampes.size) drawRect(rampes[c][1].copy(alpha = a), Offset(xs[k] - cel / 2f, y), Size(cel, cel)) }
            }
            // llegenda: canal i nota
            if (Concert.si(v, "llegenda")) {
                val est = TextStyle(fontSize = 11.sp)
                var x = 4.dp.toPx()
                for (c in 0 until n) {
                    val nt = m.notes.getOrElse(c) { -1 }
                    val s = o.noms.getOrElse(c) { "${c + 1}" } + " " + (if (nt >= 0) NotesCanal.nom(nt) else "·")
                    val lay = mesura.measure(s, est)
                    if (x + lay.size.width > size.width) break
                    drawText(lay, rampes[c][2], Offset(x, 0f))
                    x += lay.size.width + 10.dp.toPx()
                }
            }
        }
        if (perCanal == 0) Tx(t("Aquest joc no dona els canals per separat: no es poden mostrar les notes."), Tipus.cos, M.tenu, Modifier.align(Alignment.Center).padding(24.dp), 3)
        else Tx(t(if (m.notesExactes) "Notes llegides dels registres del motor." else "Notes estimades a partir del so de cada canal."), Tipus.micro, M.tenu, Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
    }
}

// ---------------------------------------------------------------- 3. túnel

@Composable
internal fun TunelVis(m: ModelConcert, t: State<Long>, v: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val dt = m.dt(t.value)
        m.pas(dt, 12, Concert.sensibilitat(v))
        val tu = m.tunel
        tu.pas(dt * Concert.vel(v), EstatRepro.actiu, m.greus, m.empenta)
        val cols = 12
        m.pintor.prepara(Concert.color(v), M.accent, M.fons, M.fosc, cols)
        // interacció: arrossegar mou el punt de fuga (la «càmera"); tocar-lo dues vegades el torna al mig
        val act = Concert.interactiu(v)
        val dr = m.prenArr(act)
        if (m.prenTocs(act).isNotEmpty() && m.focus == null) m.focus = center
        if (dr != Offset.Zero) { val f = (m.focus ?: center) + dr; m.focus = Offset(f.x.coerceIn(0f, size.width), f.y.coerceIn(0f, size.height)) }
        if (m.dit == null) m.focus = m.focus?.let { f -> val g = f + (center - f) * (1f - Math.exp(-dt / 1.5).toFloat()); if ((g - center).getDistance() < 1f) null else g }
        val c0 = m.focus ?: center
        val mn = minOf(size.width, size.height)
        val gir = tu.gir * 57.29578f
        val fm = Concert.op(v, "forma")
        if (Concert.si(v, "anells")) {
            val r0 = mn * 0.06f
            val ordre = tu.anells.indices.sortedByDescending { tu.anells[it] }
            for (k in ordre) {
                val z = tu.anells[k]
                val half = r0 / z
                if (half > maxOf(size.width, size.height)) continue
                val r = m.pintor.de(k % cols, cols)
                val a = (1f - z).coerceIn(0f, 1f)
                rotate(gir * (1f - z) * 2f, c0) {
                    drawRect(r[1].copy(alpha = a * 0.8f), Offset(c0.x - half, c0.y - half), Size(2 * half, 2 * half),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = (2.dp.toPx() + 4.dp.toPx() * m.empenta) * (1f - z * 0.6f)))
                }
            }
        }
        // partícules: quadrats que vénen cap a la pantalla
        val quants = when (Concert.op(v, "n")) { 0 -> 70; 2 -> 280; else -> 140 }.coerceAtMost(tu.n)
        val r1 = mn * 0.16f
        val base = 2.5.dp.toPx() * (1f + 0.6f * m.empenta)
        for (i in 0 until quants) {
            val z = tu.z[i]
            val d = tu.radi[i] * r1 / z
            val x = c0.x + cos(tu.angle[i] + tu.gir) * d; val y = c0.y + sin(tu.angle[i] + tu.gir) * d
            val s = (base / z).coerceAtMost(26.dp.toPx())
            if (x < -s || y < -s || x > size.width + s || y > size.height + s) continue
            val r = m.pintor.de(tu.to[i] % cols, cols)
            val col = if (z < 0.3f) r[2] else if (z < 0.65f) r[1] else r[0]
            forma(fm, col.copy(alpha = (1.15f - z).coerceIn(0f, 1f)), x, y, s)
        }
    }
}

// ---------------------------------------------------------------- 4. combat al ritme

/**
 * Sprites d'ImmoHub (`assets/concert/`): `herois.png` = 10 files (una per personatge)
 * de 16 fotogrames de 64x64 (0-3 quiet A, 4-7 atac, 8-11 preparar, 12-15 quiet B); `enemics.png` = 64 enemics de 64x64 en una graella de 8x8.
 */
private const val FILA_PAU = 1
private const val FILA_CONCE = 4

private fun carregaSprite(ctx: Context, nom: String): ImageBitmap? = try { ctx.assets.open("concert/$nom.png").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() } catch (e: Throwable) { null }

@Composable
internal fun Combat(m: ModelConcert, t: State<Long>, v: Int) {
    val ctx = LocalContext.current
    val herois = remember { carregaSprite(ctx, "herois") }
    val enemics = remember { carregaSprite(ctx, "enemics") }
    var delMotor by remember { mutableIntStateOf(-1) }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val dt = m.dt(t.value)
            m.pas(dt, 12, Concert.sensibilitat(v))
            val cb = m.combat
            cb.herois = Concert.op(v, "herois") + 1
            cb.perGrup = when (Concert.op(v, "enemics")) { 0 -> 1; 1 -> 2; 2 -> 3; else -> 0 }
            for (i in 0 until 3) cb.distancia[i] = m.herois[i] == FILA_PAU || m.herois[i] == FILA_CONCE          // al joc ataquen de lluny (veu, cartes): no salten
            val si = RadioMotor.sinte
            var bat = m.detCombat.pas(dt, EstatRepro.actiu, si?.batec ?: -1.0, si?.bpm ?: 0.0, m.greusBrut)
            if (bat && Concert.op(v, "ritme") == 1) { m.batecsCombat++; bat = m.batecsCombat % 2 == 0 }
            cb.pas(dt, EstatRepro.actiu, bat)
            if (m.prenTocs(Concert.interactiu(v)).isNotEmpty()) cb.atacaAra()          // tocar: ataca el següent heroi ara mateix
            delMotor = if (!EstatRepro.actiu) -1 else if (m.detCombat.delMotor) 1 else 0
            // escena de la pantalla 1:1 del joc (360 d'ample), franja y 60-260; escala entera (píxels sense suavitzar)
            val sMax = minOf((size.width / 360f).toInt(), (size.height / 200f).toInt()).coerceAtLeast(1)
            val s = if (Concert.op(v, "mida") == 1) (sMax - 1).coerceAtLeast(1) else sMax
            val ox = (size.width - 360 * s) / 2f; val oy = (size.height - 200 * s) / 2f - 60 * s
            fun DrawScope.sprite(img: ImageBitmap?, sx: Int, sy: Int, x: Float, y: Float, alfa: Float = 1f) {
                if (img == null || alfa <= 0f) return
                drawImage(img, IntOffset(sx, sy), IntSize(64, 64), IntOffset((ox + Math.round(x) * s).toInt(), (oy + Math.round(y) * s).toInt()), IntSize(64 * s, 64 * s), alpha = alfa, filterQuality = FilterQuality.None)
            }
            for (e in 0 until cb.nEnemics) if (cb.visible(e)) {
                val k = cb.tipus[e] % 64
                sprite(enemics, (k % 8) * 64, (k / 8) * 64, cb.xEnemic(e) + cb.dxEnemic(e), cb.yEnemic(e) + cb.dyEnemic(e), cb.alfaEnemic(e))
            }
            for (i in 0 until cb.herois) sprite(herois, cb.fotograma(i) * 64, m.herois[i] * 64, cb.xHeroi(i), cb.yHeroi(i))
        }
        if (delMotor >= 0) Tx(t(if (delMotor == 1) "Ritme: el pols del joc" else "Ritme: els cops de greus"), Tipus.micro, M.tenu, Modifier.align(Alignment.BottomCenter))
    }
}

// ---------------------------------------------------------------- 5. espectrograma

@Composable
internal fun Espectrograma(m: ModelConcert, t: State<Long>, v: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val dt = m.dt(t.value)
        val cols = if (Concert.op(v, "bandes") == 0) 24 else 48
        m.pas(dt, cols, Concert.sensibilitat(v))
        m.pintor.prepara(Concert.color(v), M.accent, M.fons, M.fosc, cols)
        val cel = size.width / cols
        val files = (size.height / cel).toInt().coerceAtLeast(1)
        // interacció: tocar congela la cascada (un altre toc la desglaça)
        if (m.prenTocs(Concert.interactiu(v)).isNotEmpty()) m.congelat = !m.congelat
        // una fila nova cada 1/40 s (a velocitat normal) mentre sona
        if (EstatRepro.actiu && !m.congelat) {
            m.tFila += dt * Concert.vel(v)
            while (m.tFila >= 1f / 40f) {
                m.tFila -= 1f / 40f
                val f = if (m.files.size >= files) m.files.removeAt(m.files.size - 1) else FloatArray(cols)
                val ff = if (f.size == cols) f else FloatArray(cols)
                for (k in 0 until cols) ff[k] = m.esp.niv[k]
                m.files.add(0, ff)
            }
        }
        while (m.files.size > files) m.files.removeAt(m.files.size - 1)
        val puja = Concert.op(v, "dir") == 1
        for ((j, f) in m.files.withIndex()) {
            if (f.size != cols) continue
            val y = if (puja) size.height - (j + 1) * cel else j * cel
            for (k in 0 until cols) {
                val nv = f[k]; if (nv < 0.06f) continue
                val r = m.pintor.de(k, cols)
                drawRect(m.pintor.alcada(r, nv).copy(alpha = (0.25f + 0.75f * nv).coerceIn(0f, 1f)), Offset(k * cel + cel * 0.08f, y + cel * 0.08f), Size(cel * 0.84f, cel * 0.84f))
            }
        }
    }
}

// ---------------------------------------------------------------- 6. estèreo (Lissajous)

@Composable
internal fun Estereo(m: ModelConcert, t: State<Long>, v: Int) {
    OscVisible()
    Canvas(Modifier.fillMaxSize()) {
        val dt = m.dt(t.value)
        m.pas(dt, 12, Concert.sensibilitat(v))
        m.pintor.prepara(Concert.color(v), M.accent, M.fons, M.fosc, 12)
        val o = OscMestre.estereo
        val n = 384
        val cel = 4.dp.toPx() * Concert.mida(v)
        val mn = minOf(size.width, size.height) * 0.46f
        val gir = Concert.si(v, "gir")
        val fm = Concert.op(v, "forma")
        // interacció: arrossegar fa girar la figura
        val act = Concert.interactiu(v)
        m.prenTocs(act)
        val dr = m.prenArr(act); val dd = m.ditSi(act)
        if (dd != null && dr != Offset.Zero) m.rotacio += graus(kotlin.math.atan2(dd.y - center.y, dd.x - center.x) - kotlin.math.atan2(dd.y - dr.y - center.y, dd.x - dr.x - center.x))
        val rc = Math.cos(Math.toRadians(m.rotacio.toDouble())).toFloat(); val rs = Math.sin(Math.toRadians(m.rotacio.toDouble())).toFloat()
        val maxRastre = when (Concert.op(v, "rastre")) { 0 -> 0; 1 -> 3; else -> 8 }
        val punts = if (m.rastreEst.size > maxRastre) m.rastreEst.removeAt(m.rastreEst.size - 1) else FloatArray(2 * n)
        val p = if (punts.size == 2 * n) punts else FloatArray(2 * n)
        var amp = 0.02f
        val pos = o.pos
        for (i in 0 until n) { val q = ((pos - n + i) % o.mida + o.mida) % o.mida; amp = maxOf(amp, Math.abs(o.buf[0][q] - 0.5f), Math.abs(o.buf[1][q] - 0.5f)) }
        val g = (0.9f / amp).coerceAtMost(12f) * Concert.sensibilitat(v)
        for (i in 0 until n) {
            val q = ((pos - n + i) % o.mida + o.mida) % o.mida
            val l = if (EstatRepro.actiu) (o.buf[0][q] - 0.5f) * g else 0f; val r = if (EstatRepro.actiu) (o.buf[1][q] - 0.5f) * g else 0f
            val x0 = if (gir) (l - r) * 0.7071f else l
            val y0 = if (gir) (l + r) * 0.7071f else r
            val x = x0 * rc - y0 * rs; val y = x0 * rs + y0 * rc
            // a la graella de blocs (si és de blocs); si no, lliure
            val px = x.coerceIn(-1f, 1f) * mn; val py = -y.coerceIn(-1f, 1f) * mn
            p[2 * i] = if (fm == 0) Math.round(px / cel) * cel else px; p[2 * i + 1] = if (fm == 0) Math.round(py / cel) * cel else py
        }
        m.rastreEst.add(0, p)
        while (m.rastreEst.size > maxRastre + 1) m.rastreEst.removeAt(m.rastreEst.size - 1)
        for ((j, pp) in m.rastreEst.withIndex().reversed()) {
            val a = 1f - j / (maxRastre + 1f)
            if (fm == 2) {
                val path = androidx.compose.ui.graphics.Path()
                for (i in 0 until n) { if (i == 0) path.moveTo(center.x + pp[0], center.y + pp[1]) else path.lineTo(center.x + pp[2 * i], center.y + pp[2 * i + 1]) }
                val r = m.pintor.de(j % 12, 12)
                drawPath(path, (if (j == 0) r[2] else r[0]).copy(alpha = a), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx() * Concert.mida(v)))
                continue
            }
            for (i in 0 until n) {
                val r = m.pintor.de(i * 12 / n, 12)
                forma(if (fm == 1) 1 else 0, (if (j == 0) r[2] else r[0]).copy(alpha = a), center.x + pp[2 * i], center.y + pp[2 * i + 1], cel * 0.85f)
            }
        }
    }
}

// ---------------------------------------------------------------- 7. ones als batecs

@Composable
internal fun OnesBatecs(m: ModelConcert, t: State<Long>, v: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val dt = m.dt(t.value)
        val bat = m.pas(dt, 12, Concert.sensibilitat(v))
        val cols = 12
        m.pintor.prepara(Concert.color(v), M.accent, M.fons, M.fosc, cols)
        val vel = Concert.vel(v)
        if (bat) m.anells.add(floatArrayOf(0f, 1f, (m.batecs % cols).toFloat(), 0f, 0f))
        // interacció: tocar fa sortir una ona d'on toques (amb la força dels greus d'ara)
        for (p in m.prenTocs(Concert.interactiu(v))) m.anells.add(floatArrayOf(0f, 0.6f + 0.4f * m.greus, (m.rnd() * cols), p.x - center.x, p.y - center.y))
        if (Concert.si(v, "greus") && EstatRepro.actiu) {
            m.tGreus += dt
            if (m.tGreus >= 0.25f) { m.tGreus = 0f; if (m.greus > 0.15f) m.anells.add(floatArrayOf(0f, 0.35f * m.greus, (m.rnd() * cols), 0f, 0f)) }
        }
        val dur = 2.2f / vel
        val itr = m.anells.iterator()
        while (itr.hasNext()) { val a = itr.next(); a[0] += dt; if (a[0] >= dur) itr.remove() }
        if (!EstatRepro.actiu && m.anells.isNotEmpty() && m.esp.quiet && m.viu <= 0f) m.anells.clear()
        val rMax = maxOf(size.width, size.height) * 0.75f
        val cel = 5.dp.toPx()
        val fo = Concert.op(v, "forma")
        for (a in m.anells) {
            val cx = center.x + a.getOrElse(3) { 0f }; val cy = center.y + a.getOrElse(4) { 0f }
            val p = a[0] / dur
            val rad = 12.dp.toPx() + p * rMax
            val alfa = (1f - p).coerceIn(0f, 1f) * a[1].coerceIn(0f, 1f)
            val r = m.pintor.de(a[2].toInt().coerceIn(0, cols - 1), cols)
            val col = r[if (p < 0.3f) 2 else 1].copy(alpha = alfa)
            val c = cel * (1f + a[1])
            if (fo == 2) drawCircle(col, rad, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = c * 0.8f))
            else if (fo == 1) {
                val nb = (2f * Math.PI.toFloat() * rad / (c * 1.6f)).toInt().coerceIn(8, 400)
                for (k in 0 until nb) { val an = k * 2f * Math.PI.toFloat() / nb; quadrat(col, cx + cos(an) * rad, cy + sin(an) * rad, c) }
            } else {
                val nb = (2f * rad / (c * 1.6f)).toInt().coerceIn(2, 200)
                for (k in 0..nb) {
                    val d = -rad + k * 2f * rad / nb
                    quadrat(col, cx + d, cy - rad, c); quadrat(col, cx + d, cy + rad, c)
                    if (k in 1 until nb) { quadrat(col, cx - rad, cy + d, c); quadrat(col, cx + rad, cy + d, c) }
                }
            }
        }
        // al mig, un quadrat que batega amb els greus
        val r = m.pintor.de(0, cols)
        quadrat(r[1].copy(alpha = 0.3f + 0.7f * m.greus), center.x, center.y, 10.dp.toPx() + 26.dp.toPx() * (m.greus + 0.5f * m.empenta))
    }
}

// ---------------------------------------------------------------- 8. pluja de blocs (matriu)

@Composable
internal fun Pluja(m: ModelConcert, t: State<Long>, v: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val dt = m.dt(t.value)
        val cel = 7.dp.toPx() * Concert.mida(v)
        val cols = (size.width / cel).toInt().coerceIn(4, 96)
        val bat = m.pas(dt, cols, Concert.sensibilitat(v))
        m.pintor.prepara(Concert.color(v), M.accent, M.fons, M.fosc, cols)
        val files = size.height / cel
        val dens = when (Concert.op(v, "dens")) { 0 -> 0.4f; 2 -> 2.2f; else -> 1f }
        val vel = Concert.vel(v)
        if (EstatRepro.actiu) {
            for (k in 0 until cols) {
                val pr = dt * dens * (0.15f + 5f * m.esp.niv[k] * m.esp.niv[k])
                if (m.rnd() < pr && m.gotes.size < 600) m.gotes.add(floatArrayOf(k.toFloat(), -1f, (10f + 18f * m.rnd()) * vel, 4f + 10f * m.rnd()))
            }
            if (bat) repeat((cols / 4 * dens).toInt().coerceAtLeast(1)) { if (m.gotes.size < 600) m.gotes.add(floatArrayOf((m.rnd() * cols).toInt().toFloat(), -1f, 26f * vel, 8f + 8f * m.rnd())) }
        }
        // interacció: tocar o arrossegar fa ploure de la columna del dit
        val x0g = (size.width - cols * cel) / 2f
        val act = Concert.interactiu(v)
        for (p in m.prenTocs(act)) repeat(6) { if (m.gotes.size < 600) m.gotes.add(floatArrayOf(((p.x - x0g) / cel).toInt().coerceIn(0, cols - 1).toFloat(), p.y / cel - m.rnd() * 3f, (16f + 16f * m.rnd()) * vel, 5f + 8f * m.rnd())) }
        m.prenArr(act)
        m.ditSi(act)?.let { p -> if (m.rnd() < 0.6f && m.gotes.size < 600) m.gotes.add(floatArrayOf(((p.x - x0g) / cel).toInt().coerceIn(0, cols - 1).toFloat(), p.y / cel, (16f + 16f * m.rnd()) * vel, 5f + 8f * m.rnd())) }
        val itr = m.gotes.iterator()
        while (itr.hasNext()) {
            val g = itr.next()
            if (EstatRepro.actiu) g[1] += g[2] * dt else g[1] += g[2] * dt * 2f          // en pausa, les que queden acaben de caure
            if (g[1] - g[3] > files) itr.remove()
        }
        val x0 = (size.width - cols * cel) / 2f
        for (g in m.gotes) {
            val k = g[0].toInt().coerceIn(0, cols - 1)
            val r = m.pintor.de(k, cols)
            val cap = g[1].toInt()
            val llarg = g[3].toInt()
            for (j in 0..llarg) {
                val f = cap - j; if (f < 0 || f > files) continue
                val col = if (j == 0) r[2] else if (j < 3) r[1] else r[0]
                drawRect(col.copy(alpha = (1f - j / (llarg + 1f)).coerceIn(0f, 1f)), Offset(x0 + k * cel + cel * 0.1f, f * cel + cel * 0.1f), Size(cel * 0.8f, cel * 0.8f))
            }
        }
    }
}

// ---------------------------------------------------------------- 9. feixos de llum

@Composable
internal fun Feixos(m: ModelConcert, t: State<Long>, v: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val dt = m.dt(t.value)
        val n = when (Concert.op(v, "n")) { 0 -> 4; 2 -> 8; else -> 6 }
        m.pas(dt, n, Concert.sensibilitat(v))
        m.pintor.prepara(Concert.color(v), M.accent, M.fons, M.fosc, n)
        val vel = Concert.vel(v)
        val flaix = if (Concert.si(v, "flaix")) m.empenta else 0f
        val cel = 6.dp.toPx()
        val llarg = size.height * 1.05f
        val act = Concert.interactiu(v)
        m.prenTocs(act); m.prenArr(act)
        val dd = m.ditSi(act)
        val llum = Concert.op(v, "forma") == 1
        for (k in 0 until n) {
            val bx = size.width * (k + 0.5f) / n
            val by = size.height
            val fase = k * 1.7f
            // interacció: amb el dit a la pantalla, tots els feixos hi apunten
            val angle = if (dd != null) graus(kotlin.math.atan2(dd.x - bx, by - dd.y)).coerceIn(-80f, 80f)
                else sin(m.temps * 0.9f * vel + fase) * 32f * (if (k % 2 == 0) 1f else -1f)
            val niv = m.esp.niv[k]
            val r = m.pintor.de(k, n)
            val abast = llarg * (0.35f + 0.65f * niv)
            rotate(angle, Offset(bx, by)) {
                if (llum) {
                    // feix de llum suau (un con amb degradat), sense blocs
                    val path = androidx.compose.ui.graphics.Path()
                    val obert = abast * 0.22f
                    path.moveTo(bx - cel / 2f, by); path.lineTo(bx - obert, by - abast); path.lineTo(bx + obert, by - abast); path.lineTo(bx + cel / 2f, by); path.close()
                    val a0 = ((0.25f + 0.75f * niv) + 0.4f * flaix).coerceIn(0f, 1f)
                    drawPath(path, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(r[1].copy(alpha = 0f), r[2].copy(alpha = a0 * 0.8f)), startY = by - abast, endY = by))
                    return@rotate
                }
                var d = 0f
                while (d < abast) {
                    val p = d / llarg
                    val ample = cel * (1f + p * 4f)
                    val alfa = ((1f - d / abast) * (0.25f + 0.75f * niv) + 0.4f * flaix).coerceIn(0f, 1f)
                    val col = if (p < 0.2f) r[2] else if (p < 0.55f) r[1] else r[0]
                    // una fila de blocs que s'eixampla amb la distància (el feix)
                    val nb = (ample / cel).toInt().coerceAtLeast(1)
                    for (j in 0 until nb) quadrat(col.copy(alpha = alfa * (if (j == 0 || j == nb - 1) 0.6f else 1f)), bx - (nb - 1) * cel / 2f + j * cel, by - d, cel * 0.8f)
                    d += cel
                }
            }
            // el focus
            quadrat(r[2], bx, by - cel / 2f, cel * 1.6f)
        }
    }
}

// ---------------------------------------------------------------- 10. mirall

@Composable
internal fun Mirall(m: ModelConcert, t: State<Long>, v: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val dt = m.dt(t.value)
        val cols = when (Concert.op(v, "cols")) { 0 -> 16; 2 -> 48; else -> 32 }
        m.pas(dt, cols, Concert.sensibilitat(v))
        m.pintor.prepara(Concert.color(v), M.accent, M.fons, M.fosc, cols)
        val vertical = Concert.op(v, "orient") == 1
        val ambPic = Concert.si(v, "pic")
        // interacció: tocar inverteix el mirall (les barres neixen de les vores cap al mig)
        if (m.prenTocs(Concert.interactiu(v)).isNotEmpty()) m.invers = !m.invers
        val llarg = if (vertical) size.height else size.width          // on es reparteixen les columnes
        val alt = (if (vertical) size.width else size.height) / 2f     // mitja alçada (cap a cada costat del mig)
        val c = (llarg / cols * 0.78f).coerceAtMost(alt / 4f)
        val pas = llarg / cols
        val passos = (alt / (c * 1.25f)).toInt().coerceAtLeast(1)
        for (k in 0 until cols) {
            val r = m.pintor.de(k, cols)
            val encesos = Math.round(m.esp.niv[k] * passos)
            val pic = (m.esp.pic[k] * passos).toInt().coerceIn(0, passos - 1)
            val p0 = k * pas + (pas - c) / 2f + c / 2f
            for (f in 0 until passos) {
                val ences = f < encesos
                if (!ences && f != 0) continue
                val col = if (ences) m.pintor.alcada(r, f / (passos - 1f).coerceAtLeast(1f)) else r[0].copy(alpha = 0.12f)
                val d = if (m.invers) alt - (f + 0.5f) * c * 1.25f else (f + 0.5f) * c * 1.25f
                if (vertical) { quadrat(col, size.width / 2f - d, p0, c); quadrat(col, size.width / 2f + d, p0, c) }
                else { quadrat(col, p0, size.height / 2f - d, c); quadrat(col, p0, size.height / 2f + d, c) }
            }
            if (ambPic && m.esp.pic[k] > 0.02f && pic >= encesos) {
                val d = if (m.invers) alt - (pic + 0.5f) * c * 1.25f else (pic + 0.5f) * c * 1.25f
                if (vertical) { quadrat(r[2], size.width / 2f - d, p0, c); quadrat(r[2], size.width / 2f + d, p0, c) }
                else { quadrat(r[2], p0, size.height / 2f - d, c); quadrat(r[2], p0, size.height / 2f + d, c) }
            }
        }
    }
}
