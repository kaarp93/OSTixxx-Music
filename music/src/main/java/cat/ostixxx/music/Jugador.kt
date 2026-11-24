package cat.ostixxx.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import cat.gbdsmixxx.Biblioteca
import cat.gbdsmixxx.Espectre
import cat.gbdsmixxx.RadioDades
import cat.gbdsmixxx.RadioMotor
import cat.gbdsmixxx.t
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** El joc que sona ara i l'àlbum o la llista d'on es reprodueix. */
private class Sonant(val origen: cat.gbdsmixxx.Emissora?, val joc: cat.gbdsmixxx.Emissora?)

@Composable
private fun sonantAra(b: Biblioteca): Sonant {
    val joc = EstatRepro.joc; val seq = EstatRepro.seq
    return remember(b, joc, seq, cat.gbdsmixxx.App.versio) {
        val o = b.emissores.firstOrNull { it.nom == joc }
        Sonant(o, jocDeCanco(b, o, seq))
    }
}

/**
 * Mini reproductor flotant (damunt la barra de navegació): portada, títol, visualitzador i play/pausa. Tocar-lo o lliscar amunt obre el reproductor gran;
 * la seva portada és el punt de sortida de la que «vola» fins al gran.
 */
@Composable
fun MiniJugador(b: Biblioteca) {
    val nav = LocalNav.current
    val s = sonantAra(b)
    val amagaPortada by remember { derivedStateOf { nav.obertura.value > 0f } }
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().superficie(M.supAlta, RoundedCornerShape(Formes.mitjana))
            .pointerInput(Unit) { detectVerticalDragGestures { _, d -> if (d < -12f) nav.obreJugador() } }
            .premsable(0.98f, vibra = false) { nav.obreJugador() }) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).onGloballyPositioned { val r = it.boundsInRoot(); if (nav.rectMini != r) nav.rectMini = r }.graphicsLayer { alpha = if (amagaPortada) 0f else 1f }) {
                    Portada(s.joc, Modifier.fillMaxSize(), 10.dp)
                }
                Column(Modifier.weight(1f)) {
                    Tx(EstatRepro.titol, Tipus.cosFort, M.text)
                    Tx(if (EstatRepro.pausat) t("En pausa") else s.joc?.let { titolAlbum(it) } ?: "", Tipus.petit, M.tenu)
                }
                Visualitzador(Modifier.width(30.dp).height(22.dp), M.accent, compacte = true)
                BotoIcona(if (EstatRepro.pausat) CamiIcones.PLAY else CamiIcones.PAUSA, { Pl.playPausa() }, mida = 44.dp, icona = 26.dp, desc = if (EstatRepro.pausat) t("Reprodueix") else t("Pausa"))
                BotoIcona(CamiIcones.SEGUENT, { Pl.salta(1) }, mida = 44.dp, icona = 26.dp, desc = t("Següent"))
            }
            // progrés (només es redibuixa la línia)
            Box(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 12.dp).drawBehind {
                val si = RadioMotor.sinte; VisTick.v; EstatRepro.segons
                drawRoundRect(M.pista, cornerRadius = CornerRadius(size.height))
                val bt = si?.batec ?: -1.0; val mx = si?.batecMax ?: -1.0
                if (bt >= 0 && mx > 0) drawRoundRect(M.accent, size = Size(size.width * (bt / mx).coerceIn(0.0, 1.0).toFloat(), size.height), cornerRadius = CornerRadius(size.height))
            })
        }
    }
}

/**
 * Capa del reproductor gran: el full puja des de baix (molla), el contingut apareix i la portada vola del mini reproductor al seu lloc (transició compartida feta a mà:
 * Compose 1.6 no té `SharedTransitionLayout`). Es pot arrossegar avall per tancar-lo. Pestanyes extensibles (EQ, Canals, Bucles, Visual...): `PestanyesJugador`.
 */
@Composable
fun CapaJugador(b: Biblioteca) {
    val nav = LocalNav.current
    val p = nav.obertura
    val obert = nav.jugador
    LaunchedEffect(obert) { p.animateTo(if (obert) 1f else 0f, Moviment.suau()) }
    val visible by remember { derivedStateOf { p.value > 0.001f } }
    if (!obert && !visible) return
    val s = sonantAra(b)
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val altPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        // full de fons que puja: degradat del color de la portada al fons
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = (1f - p.value) * altPx }.drawBehind {
            drawRect(M.fons)
            if (!M.amoled) drawRect(Brush.verticalGradient(listOf(M.suau, M.fons), endY = size.height * 0.7f))          // a AMOLED, negre pur
        }.premsable(1f, vibra = false) { })
        // contingut (apareix quan el full és gairebé dalt)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((p.value - 0.45f) / 0.55f).coerceIn(0f, 1f) }) {
            ContingutJugador(b, s, arrossega = { d ->
                scope.launch { p.snapTo((p.value - d / (altPx * 0.6f)).coerceIn(0f, 1f)) }
            }, deixa = {
                if (p.value < 0.8f) { view.toc(); nav.tancaJugador() } else scope.launch { p.animateTo(1f, Moviment.suau()) }
            })
        }
        // la portada que vola
        PortadaVoladora(s.joc, altPx)
        // el panell obert (EQ, Canals...)
        val pan = nav.panell
        val pest = PestanyesJugador.llista.firstOrNull { it.id == pan }
        if (pest != null) Full(t(pest.titol), { nav.tancaPanell() }) { pest.contingut { nav.tancaPanell() } }
    }
}

@Composable
private fun PortadaVoladora(e: cat.gbdsmixxx.Emissora?, altPx: Float) {
    val nav = LocalNav.current
    val p = nav.obertura
    Box(Modifier.layout { m, c ->
        val v = p.value
        val g = nav.rectGran
        val a = nav.rectMini ?: g?.let { Rect(it.center.x - 24f, altPx - 120f, it.center.x + 24f, altPx - 72f) }
        if (g == null || a == null) {
            val pl = m.measure(Constraints.fixed(0, 0)); layout(c.maxWidth, c.maxHeight) { pl.place(0, 0) }
        } else {
            val r = Rect(lerp(a.left, g.left, v), lerp(a.top, g.top, v), lerp(a.right, g.right, v), lerp(a.bottom, g.bottom, v))
            val pl = m.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(0), r.height.roundToInt().coerceAtLeast(0)))
            layout(c.maxWidth, c.maxHeight) { pl.place(r.left.roundToInt(), r.top.roundToInt()) }
        }
    }.graphicsLayer {
        val v = p.value
        // batega amb els greus quan és al seu lloc
        VisTick.v
        val esp = Espectre.valors
        val bat = if (v >= 0.999f && EstatRepro.actiu) 1f + 0.04f * ((esp[0] + esp[1] + esp[2]) / 3f).coerceIn(0f, 1f) else 1f
        scaleX = bat; scaleY = bat
        shadowElevation = 24.dp.toPx() * v
        shape = RoundedCornerShape(lerp(10.dp.toPx(), 24.dp.toPx(), v))
        clip = true
    }) {
        Portada(e, Modifier.fillMaxSize(), 0.dp)
    }
}

@Composable
private fun ContingutJugador(b: Biblioteca, s: Sonant, arrossega: (Float) -> Unit, deixa: () -> Unit) {
    val nav = LocalNav.current
    val eLlista = s.origen; val e = s.joc
    val sonant = EstatRepro.sonant
    // títol de la cançó sense el nom del joc (a les llistes va davant), com a l'àlbum
    val nomCanco = remember(eLlista, e, EstatRepro.seq, cat.gbdsmixxx.App.versio) {
        if (eLlista != null && eLlista.esLlista && e != null && !e.esLlista) b.etiq(e, RadioDades.idDe(b.clauDe(eLlista, EstatRepro.seq))) else null
    }
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
    // mida de la portada: tan gran com hi càpiga deixant lloc als controls
    val costat = minOf(maxWidth - 64.dp, maxHeight * 0.40f, 380.dp)
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // capçalera: arrossegar-la avall tanca el reproductor
        Column(Modifier.fillMaxWidth().pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { deixa() }, onDragCancel = { deixa() }) { _, d -> arrossega(d) } }) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BotoIcona(CamiIcones.AVALL, { nav.tancaJugador() }, mida = 44.dp, icona = 30.dp, desc = t("Tanca el reproductor"))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Tx(if (eLlista != null && eLlista.esLlista) t("Reproduint llista") else t("Reproduint"), Tipus.micro, M.tenu)
                    Tx(eLlista?.let { titolAlbum(it) } ?: "", Tipus.etiqueta, M.text)
                }
                BotoIcona(CamiIcones.ATURA, { Pl.atura(); nav.tancaJugador() }, mida = 44.dp, icona = 22.dp, color = M.tenu, desc = t("Atura"))
            }
            // lloc de la portada (la dibuixa `PortadaVoladora`)
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(costat).onGloballyPositioned { val r = it.boundsInRoot(); if (nav.rectGran != r) nav.rectGran = r })
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Tx(if (sonant) (nomCanco ?: EstatRepro.titol) else "-", Tipus.titol, M.text)
                Tx(e?.let { titolAlbum(it) } ?: "", Tipus.cos, M.tenu)
            }
            BotoIcona(CamiIcones.CONCERT, { nav.obreConcert() }, mida = 44.dp, color = M.tenu, desc = t("Mode concert"))          // mode concert (pantalla completa)
            BotoIcona(CamiIcones.ALEATORI, { Pl.alterna() }, mida = 44.dp, color = if (Pl.aleatori) M.accent else M.tenu, desc = t("Aleatori"))
        }
        // mode comparació: què sona si no és l'original (driver B o sense efectes)
        val cmp = listOfNotNull(Comparacio.sonant, if (EstatRepro.senseEfectes) t("B · Sense efectes") else null)
        if (cmp.isNotEmpty()) Tx(t("Comparant") + ": " + cmp.joinToString(" · "), Tipus.etiqueta, M.accent, Modifier.fillMaxWidth().padding(top = 4.dp), 2)
        BarraProgres()
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            BotoIcona(CamiIcones.ANTERIOR, { Pl.salta(-1) }, mida = 64.dp, icona = 36.dp, desc = t("Anterior"))
            BotoPlay(sonant && !EstatRepro.pausat, { Pl.playPausa() }, mida = 84.dp)
            BotoIcona(CamiIcones.SEGUENT, { Pl.salta(1) }, mida = 64.dp, icona = 36.dp, desc = t("Següent"))
        }
        EstatRepro.error?.let { Tx(it, Tipus.petit, M.error, Modifier.padding(top = 6.dp), 3) }
        // pestanyes del reproductor (extensibles: el bloc d'efectes hi afegirà «Efectes»)
        LazyRow(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            items(PestanyesJugador.llista, key = { it.id }) { pe ->
                Xip(t(pe.titol) + pe.etiqueta().let { if (it.isEmpty()) "" else " $it" }, pe.activa(), pe.icona) { nav.obrePanell(pe.id) }
            }
        }
        // a sota, el visualitzador (barres o oscil·loscopi, segons Ajustos)
        Visualitzador(Modifier.fillMaxWidth().weight(1f).heightIn(min = 40.dp).padding(top = 12.dp, bottom = 12.dp), M.accent, mirall = true)
    }
    }
}

/**
 * Barra de posició: amb l'anàlisi del so va per batecs; sense, per temps (fins al temps màxim o 3 minuts). Es pot tocar o arrossegar per saltar
 * (el so es torna a sintetitzar fins allà). En arrossegar, la barra s'engreixa (resposta expressiva). Només es redibuixa ella.
 */
@Composable
private fun BarraProgres() {
    var arr by remember { mutableFloatStateOf(-1f) }
    val gruix by animateFloatAsState(if (arr >= 0f) 10f else 6f, Moviment.expressiu(), label = "gruix")
    val perBatecs = EstatRepro.batecMax > 0 && EstatRepro.batec >= 0
    val durada = if (RadioDades.tempsMax > 0) RadioDades.tempsMax.toDouble() else 180.0
    fun salta(f: Double) { if (perBatecs) RadioMotor.cercaF = f.coerceIn(0.0, 1.0) else RadioMotor.cercaS = f.coerceIn(0.0, 1.0) * durada }
    val view = LocalView.current
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Box(Modifier.fillMaxWidth().height(28.dp)
            .pointerInput(perBatecs, durada) { detectTapGestures { o -> view.toc(); salta((o.x / size.width).toDouble()) } }
            .pointerInput(perBatecs, durada) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> arr = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { if (arr >= 0f) { view.toc(); salta(arr.toDouble()) }; arr = -1f },
                    onDragCancel = { arr = -1f },
                ) { ch, _ -> arr = (ch.position.x / size.width).coerceIn(0f, 1f) }
            }
            .drawBehind {
                VisTick.v; EstatRepro.segons
                val si = RadioMotor.sinte
                val bt = si?.batec ?: -1.0; val mx = si?.batecMax ?: -1.0
                val f = if (arr >= 0f) arr else if (bt >= 0 && mx > 0) (bt / mx).coerceIn(0.0, 1.0).toFloat() else (RadioMotor.segonsActuals / durada).coerceIn(0.0, 1.0).toFloat()
                val g = gruix.dp.toPx(); val y = (size.height - g) / 2f
                drawRoundRect(M.pista, Offset(0f, y), Size(size.width, g), CornerRadius(g / 2f))
                drawRoundRect(M.accent, Offset(0f, y), Size(size.width * f, g), CornerRadius(g / 2f))
                drawCircle(M.text, if (arr >= 0f) g * 1.1f else g * 0.9f, Offset(size.width * f, size.height / 2f))
            })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (perBatecs) {
                Tx("${if (arr >= 0f) (arr * EstatRepro.batecMax).toInt() else EstatRepro.batec}", Tipus.micro, M.tenu)
                Tx("${EstatRepro.batecMax}", Tipus.micro, M.tenu)
            } else {
                val sg = if (arr >= 0f) (arr * durada).toInt() else EstatRepro.segons
                Tx("%d:%02d".format(sg / 60, sg % 60), Tipus.micro, M.tenu)          // només el temps transcorregut: no se sap la durada total
            }
        }
    }
}
