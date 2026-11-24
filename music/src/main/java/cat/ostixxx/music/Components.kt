@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package cat.ostixxx.music

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import cat.gbdsmixxx.t
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.Emissora
import cat.gbdsmixxx.Portades
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- superfícies

/** Fons d'una superfície (targeta, full, camp, fila...): el color i, a AMOLED (on tot és negre), una vora fina neutra perquè es vegi on comença. */
fun Modifier.superficie(color: Color, forma: androidx.compose.ui.graphics.Shape): Modifier =
    this.clip(forma).background(color, forma).then(if (M.amoled) Modifier.border(1.dp, M.contorn, forma) else Modifier)

// ---------------------------------------------------------------- icones

/** Icones vectorials (camins de `CamiIcones`), fetes una sola vegada. */
object Icones {
    private val cache = HashMap<String, ImageVector>()
    fun de(cami: String): ImageVector = cache.getOrPut(cami) {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(cami), fill = SolidColor(Color.Black)).build()
    }
}

/** Una icona (camí de `CamiIcones`) del color i la mida que es vulgui. */
@Composable
fun Icona(cami: String, color: Color = M.text, mida: Dp = 24.dp, mod: Modifier = Modifier) {
    Icon(Icones.de(cami), null, mod.size(mida), tint = color)
}

// ---------------------------------------------------------------- interacció

/** Vibració lleu (la del teclat; Android la desactiva si l'usuari ha tret la resposta tàctil). */
fun android.view.View.toc() { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }

/**
 * Element premsable: en prémer s'encongeix amb una molla (resposta expressiva, sense ombra grisa), vibració lleu (`vibra`) i, si n'hi ha, polsació llarga.
 */
fun Modifier.premsable(escala: Float = 0.94f, vibra: Boolean = true, actiu: Boolean = true, llarg: (() -> Unit)? = null, toc: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val premut by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (premut) escala else 1f, Moviment.premut(), label = "premut")
    val view = LocalView.current
    this.graphicsLayer { scaleX = s; scaleY = s }
        .combinedClickable(
            interactionSource = src, indication = null, enabled = actiu,
            onLongClick = if (llarg == null) null else ({ view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); llarg() }),
            onClick = { if (vibra) view.toc(); toc() },
        )
}

// ---------------------------------------------------------------- botons

/** Semàntica d'un botó d'icona per a TalkBack (l'autor, 2026-11-22): rol de botó i, si n'hi ha, el nom (`desc`, ja passat per `t()`). No canvia res del que es veu. */
fun Modifier.botoAccessible(desc: String?): Modifier = this.semantics { role = Role.Button; if (desc != null) contentDescription = desc }

/** Botó rodó amb una icona; `fons` = color del cercle (null = transparent). `desc` = el que diu TalkBack (amb `t()`). */
@Composable
fun BotoIcona(cami: String, toc: () -> Unit, mod: Modifier = Modifier, mida: Dp = 44.dp, icona: Dp = 24.dp, color: Color = M.text, fons: Color? = null, desc: String? = null) {
    Box(mod.size(mida).clip(CircleShape).then(if (fons != null) Modifier.background(fons) else Modifier).botoAccessible(desc).premsable(0.86f) { toc() }, contentAlignment = Alignment.Center) {
        Icona(cami, color, icona)
    }
}

/**
 * Botó de reproduir/pausa expressiu: en pausa és un cercle; sonant, un quadrat arrodonit (la forma canvia amb una molla, com a Material 3 Expressive).
 */
@Composable
fun BotoPlay(sonant: Boolean, toc: () -> Unit, mod: Modifier = Modifier, mida: Dp = 72.dp, color: Color = M.accent, sobre: Color = M.sobreAccent) {
    val radi by animateFloatAsState(if (sonant) 30f else 50f, Moviment.expressiu(), label = "forma")
    Box(mod.size(mida).clip(RoundedCornerShape(radi.toInt().coerceIn(0, 50))).background(color).botoAccessible(if (sonant) t("Pausa") else t("Reprodueix")).premsable(0.9f) { toc() }, contentAlignment = Alignment.Center) {
        androidx.compose.animation.Crossfade(sonant, label = "play") { s -> Icona(if (s) CamiIcones.PAUSA else CamiIcones.PLAY, sobre, mida * 0.46f) }
    }
}

/** Xip (pastilla): actiu = contenidor tonal de l'accent; inactiu = vora fina. Opcionalment amb icona. */
@Composable
fun Xip(text: String, actiu: Boolean, icona: String? = null, mod: Modifier = Modifier, toc: () -> Unit) {
    val fons by animateColorAsState(if (actiu) M.suau else Color.Transparent, tween(200), label = "xip")
    val tx = if (actiu) M.sobreSuau else M.text
    Row(mod.height(36.dp).clip(CircleShape).background(fons).then(if (actiu) Modifier else Modifier.border(1.dp, M.contorn, CircleShape))
        .premsable(0.95f) { toc() }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        if (icona != null) Icona(icona, tx, 18.dp)
        Tx(text, Tipus.etiqueta, tx)
    }
}

/** Botó tonal (contenidor suau de l'accent), amb icona opcional. */
@Composable
fun BotoTonal(text: String, icona: String? = null, mod: Modifier = Modifier, color: Color = M.suau, sobre: Color = M.sobreSuau, toc: () -> Unit) {
    Row(mod.heightIn(min = 44.dp).clip(RoundedCornerShape(Formes.mitjana)).background(color).premsable(0.95f) { toc() }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        if (icona != null) Icona(icona, sobre, 20.dp)
        Tx(text, Tipus.cosFort, sobre)
    }
}

/** Botó ple (accent): l'acció principal d'una pantalla o d'un full. */
@Composable
fun BotoPle(text: String, icona: String? = null, mod: Modifier = Modifier, toc: () -> Unit) = BotoTonal(text, icona, mod, M.accent, M.sobreAccent, toc)

/** Grup de botons connectats (només un actiu): la peça triada es tenyeix d'accent amb una animació. */
@Composable
fun Segmentat(opcions: List<String>, triat: Int, mod: Modifier = Modifier, canvia: (Int) -> Unit) {
    Row(mod.fillMaxWidth().superficie(M.supAlta, CircleShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        opcions.forEachIndexed { i, n ->
            val sel = i == triat
            val fons by animateColorAsState(if (sel) M.accent else Color.Transparent, Moviment.suau(), label = "seg")
            Box(Modifier.weight(1f).height(38.dp).clip(CircleShape).background(fons).premsable(0.94f) { canvia(i) }, contentAlignment = Alignment.Center) {
                Tx(n, Tipus.etiqueta, if (sel) M.sobreAccent else M.text)
            }
        }
    }
}

/** Fila amb un interruptor (Material 3), amb títol i explicació opcional. Tota la fila es pot tocar. */
@Composable
fun FilaInterruptor(titol: String, valor: Boolean, sub: String? = null, canvia: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Formes.petita)).premsable(0.98f) { canvia(!valor) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Tx(titol, Tipus.cos, M.text, linies = 2)
            if (sub != null) Tx(sub, Tipus.petit, M.tenu, linies = 4)
        }
        Switch(valor, { canvia(it) }, colors = SwitchDefaults.colors(checkedThumbColor = M.sobreAccent, checkedTrackColor = M.accent, uncheckedThumbColor = M.tenu, uncheckedTrackColor = M.supAlta, uncheckedBorderColor = M.contorn))
    }
}

/** Fila d'un menú o d'un full: icona, text i, si cal, color d'avís. */
@Composable
fun FilaOpcio(cami: String?, text: String, color: Color = M.text, toc: () -> Unit) {
    Row(Modifier.fillMaxWidth().superficie(M.supAlta, RoundedCornerShape(Formes.mitjana)).premsable(0.97f) { toc() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (cami != null) Icona(cami, color, 22.dp)
        Tx(text, Tipus.cos, color, Modifier.weight(1f), 2)
    }
}

// ---------------------------------------------------------------- contenidors

/** Targeta d'una secció (Ajustos...): superfície arrodonida amb títol i icona. */
@Composable
fun Targeta(titol: String? = null, icona: String? = null, mod: Modifier = Modifier, contingut: @Composable ColumnScope.() -> Unit) {
    Column(mod.fillMaxWidth().superficie(M.sup, RoundedCornerShape(Formes.gran)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (titol != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icona != null) Box(Modifier.size(32.dp).clip(CircleShape).background(M.suau), contentAlignment = Alignment.Center) { Icona(icona, M.sobreSuau, 18.dp) }
            Tx(titol, Tipus.gran, M.text)
        }
        contingut()
    }
}

/**
 * Full que surt de baix (opcions d'una cançó, EQ, canals, bucles, informe...): entra amb una molla, el fons s'enfosqueix i tocar fora el tanca.
 * El contingut es desplaça si no hi cap (no hi posis cap llista «lazy» sense alçada màxima).
 */
@Composable
fun Full(titol: String, tanca: () -> Unit, contingut: @Composable ColumnScope.() -> Unit) {
    val entra = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entra.animateTo(1f, Moviment.suau()) }
    val alt = LocalConfiguration.current.screenHeightDp.dp
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = entra.value.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = 0.55f)).premsable(1f, vibra = false) { tanca() }, contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().heightIn(max = alt * 0.88f)
            .graphicsLayer { translationY = (1f - entra.value) * size.height }
            .superficie(M.sup, RoundedCornerShape(topStart = Formes.enorme, topEnd = Formes.enorme))
            .premsable(1f, vibra = false) { }
            .navigationBarsPadding().imePadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) { Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(M.contorn)) }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Tx(titol, Tipus.gran, M.text, Modifier.weight(1f), 2)
                BotoIcona(CamiIcones.TANCA, tanca, mida = 40.dp, icona = 20.dp, color = M.tenu, fons = M.supAlta, desc = t("Tanca"))
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { contingut() }
        }
    }
}

/** Full amb una llista d'opcions (text i acció). */
@Composable
fun Dialeg(titol: String, opcions: List<Triple<String?, String, () -> Unit>>, tanca: () -> Unit) {
    Full(titol, tanca) { for ((ic, n, f) in opcions) FilaOpcio(ic, n) { f() } }
}

/** Camp de text arrodonit amb indicació (`pista`) quan és buit. */
@Composable
fun Camp(valor: String, pista: String, mod: Modifier = Modifier, unaLinia: Boolean = true, icona: String? = null, canvia: (String) -> Unit) {
    Row(mod.fillMaxWidth().heightIn(min = 52.dp).superficie(M.supAlta, RoundedCornerShape(Formes.mitjana)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icona != null) Icona(icona, M.tenu, 22.dp)
        Box(Modifier.weight(1f)) {
            if (valor.isEmpty()) Tx(pista, Tipus.cos, M.tenu)
            BasicTextField(valor, canvia, Modifier.fillMaxWidth(), singleLine = unaLinia, textStyle = TextStyle(color = M.text, fontSize = Tipus.cos.fontSize, fontFamily = Tipus.cos.fontFamily), cursorBrush = SolidColor(M.accent))
        }
        if (valor.isNotEmpty()) BotoIcona(CamiIcones.TANCA, { canvia("") }, mida = 32.dp, icona = 18.dp, color = M.tenu, desc = t("Esborra el text"))
    }
}

/** Entrada animada d'un element de llista: apareix i puja una mica, amb un retard segons la posició (només els 12 primers; la resta, de cop). */
@Composable
fun EntradaAnimada(index: Int, mod: Modifier = Modifier, contingut: @Composable BoxScope.() -> Unit) {
    if (index >= 12) { Box(mod, content = contingut); return }
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(index * 35L); a.animateTo(1f, Moviment.suau()) }
    Box(mod.graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1f - a.value) * 24.dp.toPx() }, content = contingut)
}

/** Espai buit (llegibilitat). */
@Composable
fun Espai(alt: Dp) = Spacer(Modifier.height(alt))

// ---------------------------------------------------------------- portades

/**
 * Portada d'un àlbum: la de libretro o la icona de la ROM (les de 32x32 es veuen pixelades, sense suavitzar). Es carrega fora del fil principal (`Portades`) i,
 * mentre no hi és (o si no n'hi ha), es veu un degradat amb les inicials i una nota. La mida la dona `mod`.
 */
@Composable
fun Portada(e: Emissora?, mod: Modifier, radi: Dp = Formes.petita) {
    val ctx = LocalContext.current
    val bm = e?.let { Portades.bitmap(ctx, it) }
    val forma = RoundedCornerShape(radi)
    if (bm != null) {
        val img = remember(bm) { bm.asImageBitmap() }
        Image(img, null, mod.clip(forma), contentScale = ContentScale.Crop, filterQuality = if (bm.width > 32) FilterQuality.Medium else FilterQuality.None)
    } else {
        val nom = e?.let { it.iconaNom ?: it.vista } ?: "?"
        val h = ((nom.hashCode() and 0x7fffffff) % 360).toFloat()
        val fons = remember(h) { Brush.linearGradient(listOf(Color.hsv(h, 0.50f, 0.78f), Color.hsv((h + 40f) % 360f, 0.62f, 0.42f))) }
        Box(mod.clip(forma).background(fons), contentAlignment = Alignment.Center) {
            Icona(CamiIcones.NOTA, Color.White.copy(alpha = 0.85f), 28.dp)
        }
    }
}
