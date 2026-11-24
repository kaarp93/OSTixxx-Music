package cat.gbdsmixxx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colors de l'app: fons fosc, un color per deck (rosa el 1, cian el 2) i colors de funció per als botons de transport. */
object Col {
    val fons = Color(0xFF0B0B0E)
    val panell = Color(0xFF15151B)
    val fosc = Color(0xFF22222B)
    val text = Color(0xFFEDEDED)
    val tenu = Color(0xFF8A8A95)
    val d1 = Color(0xFFFF2D8A)
    val d2 = Color(0xFF22B8F0)
    val verd = Color(0xFF3DDC84)
    val groc = Color(0xFFFFB020)
    val vermell = Color(0xFFFF4D4D)
    val gris = Color(0xFF9A9AA5)
    val d3 = Color(0xFFFF9F1C)
    val d4 = Color(0xFFA06BFF)
    /** Color del deck `d` (0 a 3): rosa, cian, taronja i violeta. */
    fun deck(d: Int): Color = when (d) { 0 -> d1; 1 -> d2; 2 -> d3; else -> d4 }
}

/** Text d'una línia amb els valors per defecte de l'app. */
@Composable
fun Tx(s: String, color: Color = Col.text, mida: Int = 12, negreta: Boolean = false, mod: Modifier = Modifier, centre: Boolean = false) {
    Text(s, modifier = mod, color = color, fontSize = mida.sp, fontWeight = if (negreta) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (centre) TextAlign.Center else TextAlign.Start)
}

/** Punt de color de compatibilitat de tonalitat d'una cançó amb l'altre deck (`c` de `compatibilitat`): verd, groc o vermell; transparent si no se sap (-1). */
@Composable
fun PuntCompat(c: Int) {
    val col = when (c) { 2 -> Col.verd; 1 -> Col.groc; 0 -> Col.vermell; else -> Color.Transparent }
    Box(Modifier.size(14.dp).clip(CircleShape).background(col))
}

/** Botó amb vora de color (rodó o amb les cantonades arrodonides); `actiu` l'omple lleugerament. */
@Composable
fun Btn(mod: Modifier, color: Color, actiu: Boolean = false, rodo: Boolean = false, alToc: () -> Unit, contingut: @Composable BoxScope.() -> Unit) {
    val forma = if (rodo) CircleShape else RoundedCornerShape(10.dp)
    Box(mod.clip(forma).background(if (actiu) color.copy(alpha = 0.35f) else Color.Transparent, forma).border(2.dp, color, forma).clickable { alToc() },
        contentAlignment = Alignment.Center, content = contingut)
}

/** Botó del mesclador: actua **en tocar-lo** (no en deixar-lo anar), com els botons d'un equip de DJ. No s'ha de posar dins d'una zona que es desplaci. */
@Composable
fun Pad(mod: Modifier, color: Color, actiu: Boolean = false, ple: Boolean = false, alPremer: () -> Unit, contingut: @Composable BoxScope.() -> Unit) {
    val cb by rememberUpdatedState(alPremer)
    val forma = RoundedCornerShape(8.dp)
    Box(mod.clip(forma).background(if (ple) color.copy(alpha = 0.85f) else if (actiu) color.copy(alpha = 0.35f) else Color.Transparent, forma).border(2.dp, color, forma)
        .pointerInput(Unit) { detectTapGestures(onPress = { cb() }) },
        contentAlignment = Alignment.Center, content = contingut)
}

/** Botó que actua mentre es té premut (pitch bend): `alPremer(true)` en tocar i `alPremer(false)` en deixar-lo anar. */
@Composable
fun BtnPremut(mod: Modifier, color: Color, actiu: Boolean, alPremer: (Boolean) -> Unit, contingut: @Composable BoxScope.() -> Unit) {
    val cb by rememberUpdatedState(alPremer)
    val forma = RoundedCornerShape(10.dp)
    Box(mod.clip(forma).background(if (actiu) color.copy(alpha = 0.35f) else Color.Transparent, forma).border(2.dp, color, forma)
        .pointerInput(Unit) { detectTapGestures(onPress = { cb(true); try { tryAwaitRelease() } finally { cb(false) } }) },
        contentAlignment = Alignment.Center, content = contingut)
}

/** Icones de transport dibuixades: 0 = play, 1 = pausa, 2 = stop, 3 = anterior, 4 = següent. */
@Composable
fun Icona(tipus: Int, color: Color, mod: Modifier) {
    Canvas(mod) {
        val w = size.width; val h = size.height
        when (tipus) {
            0 -> drawPath(Path().apply { moveTo(w * 0.22f, h * 0.12f); lineTo(w * 0.88f, h * 0.5f); lineTo(w * 0.22f, h * 0.88f); close() }, color)
            1 -> { drawRect(color, Offset(w * 0.15f, h * 0.1f), Size(w * 0.28f, h * 0.8f)); drawRect(color, Offset(w * 0.57f, h * 0.1f), Size(w * 0.28f, h * 0.8f)) }
            2 -> drawRect(color, Offset(w * 0.15f, h * 0.15f), Size(w * 0.7f, h * 0.7f))
            3 -> { drawRect(color, Offset(w * 0.1f, h * 0.12f), Size(w * 0.14f, h * 0.76f))
                drawPath(Path().apply { moveTo(w * 0.9f, h * 0.12f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.9f, h * 0.88f); close() }, color) }
            else -> { drawRect(color, Offset(w * 0.76f, h * 0.12f), Size(w * 0.14f, h * 0.76f))
                drawPath(Path().apply { moveTo(w * 0.1f, h * 0.12f); lineTo(w * 0.7f, h * 0.5f); lineTo(w * 0.1f, h * 0.88f); close() }, color) }
        }
    }
}

/**
 * Lliscador vertical: `frac` va de 0 (baix) a 1 (dalt). Es mou arrossegant o tocant; el doble toc crida `alDobleToc` (p. ex. pitch a 0).
 * `marcaCentre` dibuixa una marca al mig (pitch).
 */
@Composable
fun LliscadorV(frac: Float, color: Color, marcaCentre: Boolean, mod: Modifier, alCanviar: (Float) -> Unit, alDobleToc: () -> Unit = {}) {
    val cb by rememberUpdatedState(alCanviar)
    val cd by rememberUpdatedState(alDobleToc)
    fun fraccio(y: Float, h: Float, m: Float) = (1f - (y - m) / (h - 2 * m)).coerceIn(0f, 1f)
    Box(mod.clip(RoundedCornerShape(10.dp)).background(Col.fosc)
        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { cd() }, onTap = { o -> cb(fraccio(o.y, size.height.toFloat(), 16.dp.toPx())) }) }
        .pointerInput(Unit) {
            detectDragGestures(onDragStart = { o -> cb(fraccio(o.y, size.height.toFloat(), 16.dp.toPx())) }) { c, _ ->
                c.consume(); cb(fraccio(c.position.y, size.height.toFloat(), 16.dp.toPx()))
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val m = 16.dp.toPx(); val w = size.width; val h = size.height
            val cx = w / 2f
            drawRect(color.copy(alpha = 0.25f), Offset(cx - 3.dp.toPx(), m), Size(6.dp.toPx(), h - 2 * m))
            if (marcaCentre) drawRect(Col.gris, Offset(w * 0.15f, h / 2f - 1.dp.toPx()), Size(w * 0.7f, 2.dp.toPx()))
            val y = m + (1f - frac.coerceIn(0f, 1f)) * (h - 2 * m)
            drawRect(color.copy(alpha = 0.6f), Offset(cx - 3.dp.toPx(), y), Size(6.dp.toPx(), (h - m) - y))
            drawRect(Col.text, Offset(w * 0.08f, y - 7.dp.toPx()), Size(w * 0.84f, 14.dp.toPx()))
            drawRect(color, Offset(w * 0.08f, y - 2.dp.toPx()), Size(w * 0.84f, 4.dp.toPx()))
        }
    }
}

/** Lliscador horitzontal (crossfader): `frac` va de 0 (esquerra) a 1 (dreta); el doble toc crida `alDobleToc` (centre). */
@Composable
fun LliscadorH(frac: Float, mod: Modifier, alCanviar: (Float) -> Unit, alDobleToc: () -> Unit = {}, color: Color? = null) {
    val cb by rememberUpdatedState(alCanviar)
    val cd by rememberUpdatedState(alDobleToc)
    fun fraccio(x: Float, w: Float, m: Float) = ((x - m) / (w - 2 * m)).coerceIn(0f, 1f)
    Box(mod.clip(RoundedCornerShape(10.dp)).background(Col.fosc)
        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { cd() }, onTap = { o -> cb(fraccio(o.x, size.width.toFloat(), 20.dp.toPx())) }) }
        .pointerInput(Unit) {
            detectDragGestures(onDragStart = { o -> cb(fraccio(o.x, size.width.toFloat(), 20.dp.toPx())) }) { c, _ ->
                c.consume(); cb(fraccio(c.position.x, size.width.toFloat(), 20.dp.toPx()))
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val m = 20.dp.toPx(); val w = size.width; val h = size.height; val cy = h / 2f
            val x = m + frac.coerceIn(0f, 1f) * (w - 2 * m)
            if (color == null) {            // crossfader: un color a cada costat
                drawRect(Col.d1.copy(alpha = 0.6f), Offset(m, cy - 2.dp.toPx()), Size(x - m, 4.dp.toPx()))
                drawRect(Col.d2.copy(alpha = 0.6f), Offset(x, cy - 2.dp.toPx()), Size(w - m - x, 4.dp.toPx()))
            } else {
                drawRect(color.copy(alpha = 0.25f), Offset(m, cy - 2.dp.toPx()), Size(w - 2 * m, 4.dp.toPx()))
                drawRect(color.copy(alpha = 0.7f), Offset(m, cy - 2.dp.toPx()), Size(x - m, 4.dp.toPx()))
            }
            drawRect(Col.gris, Offset(w / 2f - 1.dp.toPx(), h * 0.15f), Size(2.dp.toPx(), h * 0.7f))
            drawRect(Col.text, Offset(x - 9.dp.toPx(), h * 0.1f), Size(18.dp.toPx(), h * 0.8f))
            drawRect(Col.fons, Offset(x - 1.dp.toPx(), h * 0.1f), Size(2.dp.toPx(), h * 0.8f))
        }
    }
}

/** Camp de text d'una línia (cercador, noms); `pista` es veu quan és buit. */
@Composable
fun CampText(valor: String, mod: Modifier, pista: String = "", alCanviar: (String) -> Unit) {
    Box(mod.clip(RoundedCornerShape(8.dp)).background(Col.fosc).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
        if (valor.isEmpty() && pista.isNotEmpty()) Tx(pista, Col.tenu, 13)
        androidx.compose.foundation.text.BasicTextField(valor, alCanviar, Modifier.fillMaxWidth(), singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Col.text, fontSize = 13.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(Col.text))
    }
}

/** Finestra que demana un text (nom d'una cançó o d'una llista) amb D'acord i Cancel·la. */
@Composable
fun DemanaText(titol: String, inicial: String, alAcceptar: (String) -> Unit, alCancelar: () -> Unit) {
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(inicial) }
    Box(Modifier.fillMaxSize().background(Color(0xF00B0B0E)).clickable { }, contentAlignment = Alignment.TopCenter) {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp).fillMaxWidth(0.7f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tx(titol, Col.text, 15, true)
            CampText(text, Modifier.fillMaxWidth().height(42.dp)) { text = it }
            Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn(Modifier.weight(1f).fillMaxHeight(), Col.verd, false, false, { alAcceptar(text.trim()) }) { Tx(t("D'acord"), Col.text, 13, true, centre = true) }
                Btn(Modifier.weight(1f).fillMaxHeight(), Col.gris, false, false, alCancelar) { Tx(t("Cancel·la"), Col.text, 13, true, centre = true) }
            }
        }
    }
}

/**
 * Botó d'un canal: tocar-lo el silencia o el torna a activar (`alPremer`, en tocar); mantenir-lo premut el deixa sol (`alMantenir(true)`) fins que es deixa anar (`alMantenir(false)`).
 */
@Composable
fun PadCanal(mod: Modifier, color: Color, ple: Boolean, alPremer: () -> Unit, alMantenir: (Boolean) -> Unit, contingut: @Composable BoxScope.() -> Unit) {
    val cp by rememberUpdatedState(alPremer)
    val cm by rememberUpdatedState(alMantenir)
    val forma = RoundedCornerShape(8.dp)
    Box(mod.clip(forma).background(if (ple) color.copy(alpha = 0.85f) else Color.Transparent, forma).border(2.dp, color, forma)
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                cp()
                val deixat = kotlinx.coroutines.withTimeoutOrNull(350L) { tryAwaitRelease() }
                if (deixat == null) { cm(true); try { tryAwaitRelease() } finally { cm(false) } }
            })
        },
        contentAlignment = Alignment.Center, content = contingut)
}

/** Mesurador de nivell (vertical o horitzontal): verd, groc a partir de 0,7 i vermell a partir d'1 (saturació). */
@Composable
fun Mesurador(nivell: Float, mod: Modifier, vertical: Boolean = true) {
    Canvas(mod.clip(RoundedCornerShape(3.dp)).background(Col.fosc)) {
        val n = nivell.coerceIn(0f, 1.2f) / 1.2f
        val col = if (nivell >= 1f) Col.vermell else if (nivell >= 0.7f) Col.groc else Col.verd
        if (vertical) drawRect(col, Offset(0f, size.height * (1f - n)), Size(size.width, size.height * n))
        else drawRect(col, Offset(0f, 0f), Size(size.width * n, size.height))
    }
}

/** Barra de posició dins la cançó: on és el deck (blanc), on torna el bucle (gris), el punt de cue (groc) i els hot cues (color del deck). `durada` en batecs. */
@Composable
fun BarraPosicio(pos: Double, durada: Double, iniciBucle: Double, cue: Double, hots: DoubleArray, color: Color, mod: Modifier) {
    Canvas(mod.clip(RoundedCornerShape(3.dp)).background(Col.fosc)) {
        if (durada <= 0.0) return@Canvas
        fun x(b: Double) = (b / durada).coerceIn(0.0, 1.0).toFloat() * size.width
        if (pos >= 0.0) drawRect(color.copy(alpha = 0.45f), Offset(0f, 0f), Size(x(pos), size.height))
        if (iniciBucle > 0.0) drawRect(Col.gris, Offset(x(iniciBucle) - 1f, 0f), Size(2.dp.toPx(), size.height))
        for (h in hots) if (h >= 0.0) drawRect(color, Offset(x(h) - 1f, 0f), Size(2.dp.toPx(), size.height))
        if (cue >= 0.0) drawRect(Col.groc, Offset(x(cue) - 1f, 0f), Size(2.dp.toPx(), size.height))
        if (pos >= 0.0) drawRect(Col.text, Offset(x(pos) - 1f, 0f), Size(2.dp.toPx(), size.height))
    }
}
