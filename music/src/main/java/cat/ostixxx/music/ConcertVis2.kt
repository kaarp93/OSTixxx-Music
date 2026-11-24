package cat.ostixxx.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Visualitzacions interactives del mode concert (ronda 3, 2026-11-17): formes lliures (no cal que siguin de blocs), i tocar o arrossegar hi fa alguna cosa.
// Totes respecten «Interacció» (opció comuna): apagada, el dit no fa res i es mouen només amb la música.

/** Rampa (baix, mig, dalt) a partir d'un to (0-360), ajustada al fons del tema. */
private fun rampaTo(h: Float): Array<Color> {
    val fons = ColorsPurs.opac(argbDe(M.fons))
    val base = ColorsPurs.deHsl(((h % 360f) + 360f) % 360f, 0.85f, 0.56f)
    return ColorsVis.rampa(ColorsPurs.opac(base), fons, M.fosc).map { Color(it) }.toTypedArray()
}

private const val MAX_PART = 900

// ---------------------------------------------------------------- 11. estels

/** El dit deixa un rastre d'estels que brillen amb els aguts i s'inflen al batec; amb la música (opció) també en neixen sols. Partícula: x, y, vx, vy, edat, vida, color, mida. */
@Composable
internal fun Estels(m: ModelConcert, t: State<Long>, v: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val dt = m.dt(t.value)
        m.pas(dt, 12, Concert.sensibilitat(v))
        val rampes = rampesCanals(12, M.accent, Concert.color(v))
        val act = Concert.interactiu(v)
        val vida = when (Concert.op(v, "durada")) { 0 -> 1f; 2 -> 4f; else -> 2.2f }
        val mida = 6.dp.toPx() * Concert.mida(v, "mida")
        fun neix(x: Float, y: Float, f: Float) {
            if (m.part.size >= MAX_PART) return
            val a = m.rnd() * 6.2832f; val vel = (20f + 60f * m.rnd()) * f
            m.part.add(floatArrayOf(x, y, cos(a) * vel, sin(a) * vel - 15f, 0f, vida * (0.6f + 0.4f * m.rnd()), (m.rnd() * 12f), 0.5f + m.rnd()))
        }
        for (p in m.prenTocs(act)) repeat(14) { neix(p.x, p.y, 2f) }
        m.prenArr(act)
        m.ditSi(act)?.let { p -> repeat(3) { neix(p.x, p.y, 1f) } }
        if (EstatRepro.actiu && Concert.si(v, "sols")) {
            val n = (m.greus * 40f * dt + m.rnd()).toInt()
            repeat(n) { neix(m.rnd() * size.width, m.rnd() * size.height, 0.4f) }
        }
        val aguts = (m.esp.niv.getOrElse(9) { 0f } + m.esp.niv.getOrElse(10) { 0f }) / 2f
        val itr = m.part.iterator()
        while (itr.hasNext()) {
            val q = itr.next()
            q[4] += dt; if (q[4] >= q[5]) { itr.remove(); continue }
            q[2] *= (1f - 1.5f * dt); q[3] = q[3] * (1f - 1.5f * dt) - 6f * dt
            q[0] += q[2] * dt; q[1] += q[3] * dt
            val vi = 1f - q[4] / q[5]
            val brill = 0.65f + 0.35f * sin(q[4] * 9f + q[6] * 3f) + 0.5f * aguts
            val r = rampes[q[6].toInt().coerceIn(0, 11)]
            forma(2, (if (vi > 0.6f) r[2] else r[1]).copy(alpha = vi.coerceIn(0f, 1f)), q[0], q[1], mida * q[7] * brill * (1f + 0.6f * m.empenta))
        }
        if (m.part.isNotEmpty()) m.viu = maxOf(m.viu, 0.2f)          // en pausa, els que queden s'acaben d'apagar
    }
}

// ---------------------------------------------------------------- 12. focs artificials

/** Tocar fa esclatar un foc on toques (més gran com més so); als batecs (opció) n'esclaten sols. Partícula: x, y, vx, vy, edat, vida, color, mida. */
@Composable
internal fun Focs(m: ModelConcert, t: State<Long>, v: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val dt = m.dt(t.value)
        val bat = m.pas(dt, 12, Concert.sensibilitat(v))
        val rampes = rampesCanals(12, M.accent, Concert.color(v))
        val act = Concert.interactiu(v)
        val n = when (Concert.op(v, "n")) { 0 -> 30; 2 -> 120; else -> 60 }
        val grav = when (Concert.op(v, "grav")) { 0 -> 0f; 2 -> 260f; else -> 110f }
        val fm = Concert.op(v, "forma")
        val c = 5.dp.toPx()
        fun esclata(x: Float, y: Float, forca: Float) {
            val col = m.rnd() * 12f
            for (i in 0 until n) {
                if (m.part.size >= MAX_PART) break
                val a = i * 6.2832f / n + m.rnd() * 0.2f; val vel = (120f + 220f * m.rnd()) * forca
                m.part.add(floatArrayOf(x, y, cos(a) * vel, sin(a) * vel, 0f, 1.1f + 0.8f * m.rnd(), col, 0.6f + 0.6f * m.rnd()))
            }
        }
        for (p in m.prenTocs(act)) esclata(p.x, p.y, 0.6f + 0.8f * m.greus)
        m.prenArr(act)
        if (bat && Concert.si(v, "auto")) esclata(size.width * (0.15f + 0.7f * m.rnd()), size.height * (0.15f + 0.45f * m.rnd()), 0.5f + 0.7f * m.greus)
        val itr = m.part.iterator()
        while (itr.hasNext()) {
            val q = itr.next()
            q[4] += dt; if (q[4] >= q[5] || q[1] > size.height + 40f) { itr.remove(); continue }
            q[2] *= (1f - 1.2f * dt); q[3] = q[3] * (1f - 1.2f * dt) + grav * dt
            q[0] += q[2] * dt; q[1] += q[3] * dt
            val vi = 1f - q[4] / q[5]
            val r = rampes[q[6].toInt().coerceIn(0, 11)]
            val col = if (vi > 0.7f) r[2] else if (vi > 0.35f) r[1] else r[0]
            // espurneig al final i una mica més grans amb els greus
            if (vi < 0.3f && m.rnd() < 0.3f) continue
            forma(fm, col.copy(alpha = vi.coerceIn(0f, 1f)), q[0], q[1], c * q[7] * (1f + 0.5f * m.greus))
        }
        if (m.part.isNotEmpty()) m.viu = maxOf(m.viu, 0.2f)
    }
}

// ---------------------------------------------------------------- 13. boles

/** Tocar fa caure una bola; arrossegar-ne una la llença; amb els batecs, les que són a terra boten. Bola: x, y, vx, vy, radi, color, banda. */
@Composable
internal fun Boles(m: ModelConcert, t: State<Long>, v: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val dt = m.dt(t.value)
        val bat = m.pas(dt, 12, Concert.sensibilitat(v))
        val rampes = rampesCanals(12, M.accent, Concert.color(v))
        val act = Concert.interactiu(v)
        val max = when (Concert.op(v, "max")) { 0 -> 8; 2 -> 32; else -> 16 }
        val rebot = when (Concert.op(v, "rebot")) { 0 -> 0.5f; 2 -> 0.92f; else -> 0.75f }
        val r0 = 14.dp.toPx() * Concert.mida(v, "mida")
        val quadrats = Concert.op(v, "forma") == 1
        // sense cap bola, en surten tres perquè es vegi què fa
        if (m.part.isEmpty()) repeat(3) { m.part.add(floatArrayOf(size.width * (0.25f + 0.25f * it), size.height * 0.3f, 0f, 0f, r0 * (0.7f + 0.6f * m.rnd()), m.rnd() * 12f, (m.rnd() * 12f))) }
        for (p in m.prenTocs(act)) {
            // si es toca una bola, s'agafa; si no, en cau una de nova
            val agafada = m.part.indexOfFirst { (Offset(it[0], it[1]) - p).getDistance() < it[4] * 1.3f }
            m.canalTriat = agafada
            if (agafada < 0) {
                if (m.part.size >= max) m.part.removeAt(0)
                m.part.add(floatArrayOf(p.x, p.y, 0f, 0f, r0 * (0.7f + 0.6f * m.rnd()), m.rnd() * 12f, (m.rnd() * 12f)))
            }
        }
        val dr = m.prenArr(act)
        val dd = m.ditSi(act)
        val agafada = if (dd != null) m.canalTriat else -1
        if (dd == null) m.canalTriat = -1
        val g = 900f
        for ((i, b) in m.part.withIndex()) {
            if (i == agafada && dd != null) {
                b[0] = dd.x; b[1] = dd.y
                if (dt > 0f) { b[2] = dr.x / dt * 0.6f; b[3] = dr.y / dt * 0.6f }
                continue
            }
            if (EstatRepro.actiu || m.viu > 0f || Math.abs(b[3]) > 1f) {
                b[3] += g * dt; b[0] += b[2] * dt; b[1] += b[3] * dt
                b[2] *= (1f - 0.3f * dt)
            }
            val r = b[4]
            if (b[1] > size.height - r) { b[1] = size.height - r; b[3] = -Math.abs(b[3]) * rebot; if (Math.abs(b[3]) < 30f) b[3] = 0f; b[2] *= 0.98f }
            if (b[1] < r) { b[1] = r; b[3] = Math.abs(b[3]) * rebot }
            if (b[0] < r) { b[0] = r; b[2] = Math.abs(b[2]) * rebot }
            if (b[0] > size.width - r) { b[0] = size.width - r; b[2] = -Math.abs(b[2]) * rebot }
            // batec: les que són a terra (o a prop) boten amb els greus
            if (bat && b[1] > size.height - r * 3f) { b[3] = -(380f + 700f * m.greus) * (0.7f + 0.6f * m.rnd()); b[2] += (m.rnd() - 0.5f) * 160f }
            if (Math.abs(b[3]) > 1f || b[1] < size.height - r - 1f) m.viu = maxOf(m.viu, 0.2f)          // mentre alguna es mou, el rellotge no s'atura
        }
        for (b in m.part) {
            val rr = b[4] * (1f + 0.25f * m.esp.niv[b[6].toInt().coerceIn(0, m.esp.cols.coerceAtLeast(1) - 1)])
            val ram = rampes[b[5].toInt().coerceIn(0, 11)]
            // ombra a terra
            val alt = ((size.height - b[1]) / size.height).coerceIn(0f, 1f)
            drawOval(Color.Black.copy(alpha = 0.18f * (1f - alt)), Offset(b[0] - rr, size.height - rr * 0.18f), Size(2 * rr, rr * 0.36f))
            if (quadrats) {
                drawRect(ram[1], Offset(b[0] - rr, b[1] - rr), Size(2 * rr, 2 * rr))
                drawRect(ram[2], Offset(b[0] - rr * 0.6f, b[1] - rr * 0.6f), Size(rr * 0.5f, rr * 0.5f))
            } else {
                drawCircle(ram[1], rr, Offset(b[0], b[1]))
                drawCircle(ram[2], rr * 0.32f, Offset(b[0] - rr * 0.35f, b[1] - rr * 0.35f))
            }
        }
    }
}

// ---------------------------------------------------------------- 14. aigua

/** Superfície d'aigua (malla de punts): el dit hi fa ones, els batecs hi deixen caure una gota i els greus la fan tremolar. Equació d'ona en dues capes. */
@Composable
internal fun Aigua(m: ModelConcert, t: State<Long>, v: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val dt = m.dt(t.value)
        val bat = m.pas(dt, 12, Concert.sensibilitat(v))
        val rampes = rampesCanals(12, M.accent, Concert.color(v))
        val act = Concert.interactiu(v)
        val cel = when (Concert.op(v, "malla")) { 0 -> 24.dp; 2 -> 12.dp; else -> 17.dp }.toPx()
        val cols = (size.width / cel).toInt().coerceIn(4, 120); val files = (size.height / cel).toInt().coerceIn(4, 200)
        val clau = cols * 1000 + files
        if (m.aiguaN != clau || m.aigua == null) { m.aigua = FloatArray(cols * files); m.aiguaV = FloatArray(cols * files); m.aiguaN = clau }
        val a = m.aigua!!; val b = m.aiguaV!!
        val amort = when (Concert.op(v, "amort")) { 0 -> 0.94f; 2 -> 0.992f; else -> 0.975f }
        fun gota(x: Float, y: Float, f: Float) {
            val i = (x / cel).toInt().coerceIn(1, cols - 2); val j = (y / cel).toInt().coerceIn(1, files - 2)
            a[j * cols + i] += f
        }
        for (p in m.prenTocs(act)) gota(p.x, p.y, 6f)
        m.prenArr(act)
        m.ditSi(act)?.let { gota(it.x, it.y, 2.5f) }
        if (bat) gota(size.width * (0.2f + 0.6f * m.rnd()), size.height * (0.2f + 0.6f * m.rnd()), 4f + 4f * m.greus)
        if (EstatRepro.actiu && m.rnd() < m.greus * 0.4f) gota(m.rnd() * size.width, m.rnd() * size.height, 1.2f * m.greus)
        // dos passos per fotograma (l'ona va més de pressa) si sona o si hi ha moviment
        var energia = 0f
        repeat(2) {
            for (j in 1 until files - 1) for (i in 1 until cols - 1) {
                val k = j * cols + i
                val nou = (a[k - 1] + a[k + 1] + a[k - cols] + a[k + cols]) * 0.5f - b[k]
                b[k] = nou * amort
            }
            // intercanvi: b és la nova capa actual
            for (k in a.indices) { val x = a[k]; a[k] = b[k]; b[k] = x }
        }
        val punts = Concert.op(v, "forma") == 0
        for (j in 0 until files) for (i in 0 until cols) {
            val h = a[j * cols + i]
            energia += Math.abs(h)
            val f = (h * 0.25f).coerceIn(-1f, 1f)
            val r = rampes[(i * 12 / cols).coerceIn(0, 11)]
            val col = if (f > 0.25f) r[2] else if (f > -0.1f) r[1] else r[0]
            val x = i * cel + cel / 2f; val y = j * cel + cel / 2f + f * cel * 0.6f
            val s = cel * (0.18f + 0.22f * Math.abs(f)) * (1f + 0.3f * m.greus)
            val al = (0.25f + 0.75f * Math.abs(f)).coerceIn(0f, 1f)
            if (punts) drawCircle(col.copy(alpha = al), s, Offset(x, y)) else quadrat(col.copy(alpha = al), x, y, s * 1.8f)
        }
        if (energia > 0.5f) m.viu = maxOf(m.viu, 0.2f)          // mentre l'aigua es mou, el rellotge no s'atura
    }
}

// ---------------------------------------------------------------- 15. molles

/** Un anell (o una cadena) de nodes units per molles: cada node s'estira amb la seva banda; arrossega'n un i deixa'l anar. Node: x, y, vx, vy. */
@Composable
internal fun Molles(m: ModelConcert, t: State<Long>, v: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val dt = m.dt(t.value)
        val n = when (Concert.op(v, "nodes")) { 0 -> 12; 2 -> 32; else -> 20 }
        m.pas(dt, n, Concert.sensibilitat(v))
        val rampes = rampesCanals(n, M.accent, Concert.color(v))
        val act = Concert.interactiu(v)
        val k = when (Concert.op(v, "rigid")) { 0 -> 18f; 2 -> 90f; else -> 40f }
        val cadena = Concert.op(v, "forma") == 1
        val rad = minOf(size.width, size.height) * 0.28f
        fun repos(i: Int): Offset {
            val niv = m.esp.niv[i] + 0.5f * m.empenta
            return if (cadena) Offset(size.width * (i + 0.5f) / n, center.y - niv * rad * 0.9f)
            else { val a = i * 6.2832f / n - 1.5708f; val r = rad * (1f + 0.45f * niv); Offset(center.x + cos(a) * r, center.y + sin(a) * r) }
        }
        var s = m.molles
        if (s == null || s.size != 4 * n) { s = FloatArray(4 * n); for (i in 0 until n) { val p = repos(i); s[4 * i] = p.x; s[4 * i + 1] = p.y }; m.molles = s; m.canalTriat = -1 }
        for (p in m.prenTocs(act)) {
            var millor = -1; var dm = Float.MAX_VALUE
            for (i in 0 until n) { val d = (Offset(s[4 * i], s[4 * i + 1]) - p).getDistance(); if (d < dm) { dm = d; millor = i } }
            m.canalTriat = if (dm < 80.dp.toPx()) millor else -1
        }
        m.prenArr(act)
        val dd = m.ditSi(act)
        if (dd == null) m.canalTriat = -1
        val pas = (dt / 4f)
        repeat(4) {
            for (i in 0 until n) {
                if (i == m.canalTriat && dd != null) { s[4 * i] = dd.x; s[4 * i + 1] = dd.y; s[4 * i + 2] = 0f; s[4 * i + 3] = 0f; continue }
                val p = repos(i)
                var fx = (p.x - s[4 * i]) * k; var fy = (p.y - s[4 * i + 1]) * k
                // veïns: les molles entre nodes estiren els del costat
                for (jj in intArrayOf(i - 1, i + 1)) {
                    val j = if (cadena) jj else (jj + n) % n
                    if (j < 0 || j >= n) continue
                    fx += (s[4 * j] - s[4 * i]) * k * 0.25f; fy += (s[4 * j + 1] - s[4 * i + 1]) * k * 0.25f
                    val q = repos(j); val pi = repos(i)
                    fx -= (q.x - pi.x) * k * 0.25f; fy -= (q.y - pi.y) * k * 0.25f
                }
                s[4 * i + 2] = (s[4 * i + 2] + fx * pas) * (1f - 2.2f * pas); s[4 * i + 3] = (s[4 * i + 3] + fy * pas) * (1f - 2.2f * pas)
                s[4 * i] += s[4 * i + 2] * pas; s[4 * i + 1] += s[4 * i + 3] * pas
            }
        }
        var mou = 0f
        for (i in 0 until n) mou += Math.abs(s[4 * i + 2]) + Math.abs(s[4 * i + 3])
        if (mou > 20f) m.viu = maxOf(m.viu, 0.2f)
        // les molles (línia suau) i els nodes
        val path = Path()
        for (i in 0 until n) { if (i == 0) path.moveTo(s[0], s[1]) else path.lineTo(s[4 * i], s[4 * i + 1]) }
        if (!cadena) path.close()
        drawPath(path, rampes[0][1].copy(alpha = 0.6f), style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
        if (!cadena) for (i in 0 until n) drawLine(rampes[i][0].copy(alpha = 0.25f), center, Offset(s[4 * i], s[4 * i + 1]), 1.dp.toPx())
        for (i in 0 until n) {
            val r = rampes[i]
            val rr = (5.dp.toPx() + 6.dp.toPx() * m.esp.niv[i]) * (if (i == m.canalTriat) 1.6f else 1f)
            drawCircle(r[2], rr, Offset(s[4 * i], s[4 * i + 1]))
        }
    }
}

// ---------------------------------------------------------------- 16. pad de colors

/** Graella de pads que s'encenen amb la seva banda i als batecs; tocar-ne un tria el to de tots (fins que es torna a tocar el mateix) i fa una onada de llum. */
@Composable
internal fun PadColors(m: ModelConcert, t: State<Long>, v: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val dt = m.dt(t.value)
        val g = when (Concert.op(v, "pads")) { 0 -> 3; 2 -> 6; else -> 4 }
        val n = g * g
        val bat = m.pas(dt, n, Concert.sensibilitat(v))
        val act = Concert.interactiu(v)
        val costat = minOf(size.width, size.height) * 0.94f
        val pas = costat / g; val c = pas * 0.86f
        val x0 = (size.width - costat) / 2f; val y0 = (size.height - costat) / 2f
        val rodons = Concert.op(v, "forma") == 1
        val caiguda = if (Concert.op(v, "encesa") == 0) 3.5f else 1.3f
        for (p in m.prenTocs(act)) {
            val i = ((p.x - x0) / pas).toInt(); val j = ((p.y - y0) / pas).toInt()
            if (i in 0 until g && j in 0 until g) {
                val to = (j * g + i) * 360f / n
                m.padTo = if (m.padTo == to) -1f else to
                m.part.add(floatArrayOf(i.toFloat(), j.toFloat(), 0f))          // onada des d'aquest pad
            }
        }
        m.prenArr(act)
        // brillantor de cada pad: la seva banda, el batec (un a l'atzar) i les onades
        if (bat) m.pads[(m.rnd() * n).toInt().coerceIn(0, n - 1)] = 1f
        val itr = m.part.iterator()
        while (itr.hasNext()) { val q = itr.next(); q[2] += dt; if (q[2] > 1.2f) itr.remove() }
        val sel = Concert.color(v)
        val rampes = if (m.padTo >= 0f) null else rampesCanals(n, M.accent, sel)
        for (j in 0 until g) for (i in 0 until g) {
            val k = j * g + i
            m.pads[k] = maxOf(0f, m.pads[k] - dt * caiguda)
            var ona = 0f
            for (q in m.part) { val d = sqrt((i - q[0]) * (i - q[0]) + (j - q[1]) * (j - q[1])); val f = q[2] * 6f - d; if (f in 0f..1f) ona = maxOf(ona, 1f - f) }
            val b = maxOf(m.esp.niv[k], m.pads[k], ona).coerceIn(0f, 1f)
            val r = rampes?.get(k) ?: rampaTo(m.padTo + (k % 3) * 12f)
            val col = (if (b > 0.7f) r[2] else if (b > 0.3f) r[1] else r[0]).copy(alpha = 0.15f + 0.85f * b)
            val x = x0 + i * pas + (pas - c) / 2f; val y = y0 + j * pas + (pas - c) / 2f
            val cc = c * (0.88f + 0.12f * b)
            val off = (c - cc) / 2f
            if (rodons) drawCircle(col, cc / 2f, Offset(x + c / 2f, y + c / 2f))
            else drawRoundRect(col, Offset(x + off, y + off), Size(cc, cc), CornerRadius(cc * 0.12f))
        }
        if (m.part.isNotEmpty() || m.pads.any { it > 0f }) m.viu = maxOf(m.viu, 0.2f)
    }
}
