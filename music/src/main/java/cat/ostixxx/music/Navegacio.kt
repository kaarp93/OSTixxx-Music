package cat.ostixxx.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.App
import cat.gbdsmixxx.AvisInicial
import cat.gbdsmixxx.Comptes
import cat.gbdsmixxx.EnPantalla
import cat.gbdsmixxx.Informe
import cat.gbdsmixxx.t
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow

/**
 * Estat de navegació observable per Compose: embolcalla `PilaNav` (Kotlin pur, provat a JVM) i puja `rev` a cada canvi.
 * Tot el que la UI llegeix passa per aquí (cada lectura llegeix `rev`, i per tant es refà quan canvia).
 */
@Stable
class EstatNav {
    val pila = PilaNav()
    private var rev by mutableIntStateOf(0)
    init { pila.alCanviar = { rev++ } }
    val pestanya: Int get() { rev; return pila.pestanya }
    val superior: Pantalla? get() { rev; return pila.superior }
    val profunditat: Int get() { rev; return pila.profunditat }
    val jugador: Boolean get() { rev; return pila.jugador }
    val panell: String? get() { rev; return pila.panell }
    val concert: Boolean get() { rev; return pila.concert }
    val dialeg: String? get() { rev; return pila.dialeg }
    val potEnrere: Boolean get() { rev; return pila.potEnrere }
    fun te(k: String): Boolean { rev; return pila.te(k) }
    fun canviaPestanya(i: Int) = pila.canviaPestanya(i)
    fun obreAlbum(nom: String) = pila.obreAlbum(nom)
    fun substitueixAlbum(nom: String) = pila.substitueixAlbum(nom)
    fun obreAjustos() = pila.obreAjustos()
    fun obreJugador() = pila.obreJugador()
    fun tancaJugador() = pila.tancaJugador()
    fun obrePanell(id: String) = pila.obrePanell(id)
    fun tancaPanell() = pila.tancaPanell()
    fun obreConcert() = pila.obreConcert()
    fun tancaConcert() = pila.tancaConcert()
    fun obreDialeg(k: String) = pila.obreDialeg(k)
    fun tancaDialeg(k: String? = null) = pila.tancaDialeg(k)
    fun enrere(): Boolean = pila.enrere()

    /** Menú de la polsació llarga d'una cançó (emissora, id), si n'hi ha d'obert. */
    var menuCanco by mutableStateOf<Pair<String, Int>?>(null)
        private set
    fun obreMenu(nom: String, id: Int) { menuCanco = nom to id; pila.obreDialeg(MENU) }
    fun tancaMenu() { pila.tancaDialeg(MENU) }

    /** Rectangles (en coordenades de l'arrel) de la portada del mini reproductor i de la del reproductor gran: la portada «vola» d'un a l'altre. */
    var rectMini by mutableStateOf<Rect?>(null)
    var rectGran by mutableStateOf<Rect?>(null)
    /** Obertura del reproductor gran (0 tancat, 1 obert), animada amb una molla; es pot arrossegar. */
    val obertura = Animatable(0f)

    companion object { const val MENU = "menu" }
}

val LocalNav = staticCompositionLocalOf<EstatNav> { error("EstatNav no és a la composició") }

/** Estat d'una llista amb l'scroll recordat a la pila (`PilaNav.desaScroll`): en tornar d'un àlbum, la Biblioteca o l'Inici són on eren. */
@Composable
fun scrollRecordat(clau: String): LazyListState {
    val nav = LocalNav.current
    val st = remember(clau) { val (i, o) = nav.pila.scroll(clau); LazyListState(i, o) }
    DisposableEffect(clau) { onDispose { nav.pila.desaScroll(clau, st.firstVisibleItemIndex, st.firstVisibleItemScrollOffset) } }
    return st
}

/**
 * P1: l'estat de la reproducció es copia cada 250 ms (només-si-canvia) i el rellotge dels visualitzadors (`VisTick`, cada 45 ms) avança mentre sona. Ronda d'optimització
 * (2026-11-18): els dos bucles només existeixen quan fan feina (el de 250 ms amb l'app a la pantalla; el de 45 ms, a més, mentre sona i no és en pausa); abans giraven sempre
 * i miraven la condició a cada volta. Mateix ritme i mateix ordre (primer espera, després avança) que abans: no canvia res del que es veu.
 */
@Composable
private fun RellotgesRepro() {
    LaunchedEffect(Unit) { PantallaVisible.flux.collectLatest { vis -> if (vis) while (true) { EstatRepro.sincronitza(); delay(250) } } }
    LaunchedEffect(Unit) {
        combine(PantallaVisible.flux, snapshotFlow { EstatRepro.actiu }) { vis, actiu -> vis && actiu }.distinctUntilChanged()
            .collectLatest { on -> if (on) while (true) { delay(45); if (EnPantalla.v && EstatRepro.actiu) VisTick.v = VisTick.v + 1 } }
    }
}

/** Arrel d'OSTixxx Music: tema dinàmic, pestanyes, pila de pantalles, mini reproductor, reproductor gran, fulls i un sol BackHandler. */
@Composable
fun MusicArrel(triaFitxers: () -> Unit, triaCarpeta: () -> Unit, triaDrivers: () -> Unit) {
    val nav = remember { EstatNav() }
    val ctx = LocalContext.current
    RellotgesRepro()
    val b = App.bib
    // Llistes automàtiques (abans a l'Inici, ara un cop per biblioteca): [bloc de càrrega] es regeneren o s'apliquen a la biblioteca d'ara (App.bib)
    val nAlbums = remember(b, App.versio) { b?.emissores?.count { !it.esLlista && it.ids.isNotEmpty() } ?: 0 }
    LaunchedEffect(b, Libretro.versio, nAlbums) {
        val bb = b ?: return@LaunchedEffect
        AutoLlistes.carregaOGenera(ctx, bb) { r -> (App.bib ?: bb).let { x -> x.autoLlistes = r; x.refaLlistes() }; App.versio++ }
    }
    // «Més reproduïdes»: es refan quan es compta una reproducció (no en obrir l'app). Només puja `LlistaTop.versio` (abans `App.versio`, que ho refeia tot)
    val comptes = Comptes.versio
    val comptesVist = remember { intArrayOf(comptes) }
    LaunchedEffect(comptes) { if (comptes != comptesVist[0]) { comptesVist[0] = comptes; App.bib?.refaLlistes(); LlistaTop.versio++ } }

    // Color dinàmic: la portada del joc que sona (si no sona res, la paleta de reserva)
    val actual = remember(b, EstatRepro.joc, EstatRepro.seq, App.versio) { b?.let { bb -> jocDeCanco(bb, bb.emissores.firstOrNull { it.nom == EstatRepro.joc }, EstatRepro.seq) } }
    val llavor = if (EstatRepro.sonant) ColorsAlbum.llavor(actual) else null

    TemaArrel(llavor) {
        CompositionLocalProvider(LocalNav provides nav) {
            val informe = false
            BackHandler(nav.potEnrere || informe) { if (Informe.linies.isNotEmpty()) Informe.linies = emptyList() else nav.enrere() }
            Box(Modifier.fillMaxSize().drawBehind {
                drawRect(M.fons)
                drawRect(Brush.verticalGradient(listOf(M.fonsAlt, M.fons), endY = size.height * 0.45f), size = size.copy(height = size.height * 0.45f))
            }) {
                // visualitzadors tapats (vegeu `LocalVisTapat`): els de sota el reproductor gran obert del tot o el concert, i els del reproductor gran sota el concert
                val tapatBase = remember(nav) { derivedStateOf { nav.concert || nav.obertura.value >= 0.999f } }
                val tapatJugador = remember(nav) { derivedStateOf { nav.concert } }
                CompositionLocalProvider(LocalVisTapat provides tapatBase) {
                Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (b == null || App.carregant) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Carregant() }
                        else Contingut(nav, b, triaFitxers, triaCarpeta, triaDrivers)
                    }
                    AnimatedVisibility(visible = b != null && EstatRepro.sonant, enter = slideInVertically(Moviment.suau()) { it } + fadeIn(), exit = slideOutVertically(Moviment.suau()) { it } + fadeOut()) {
                        if (b != null) MiniJugador(b)
                    }
                    BarraNav(nav.pestanya) { nav.canviaPestanya(it) }
                }
                }
                if (b != null) CompositionLocalProvider(LocalVisTapat provides tapatJugador) { CapaJugador(b) }
                if (b != null && nav.concert) PantallaConcert(b)
                if (b != null) MenuCancoArrel(b)
                InformeMusic()
                AvisInicial(M.accent)
            }
        }
    }
}

@Composable
private fun Carregant() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BarresMusica(M.accent, 36.dp)
        Tx(t("Carregant..."), Tipus.cos, M.tenu)
    }
}

/** Clau del contingut (per a l'animació): la pantalla de dalt i la profunditat (per saber si s'entra o se surt). */
private data class Desti(val clau: String, val prof: Int, val pestanya: Int)

@Composable
private fun Contingut(nav: EstatNav, b: cat.gbdsmixxx.Biblioteca, triaFitxers: () -> Unit, triaCarpeta: () -> Unit, triaDrivers: () -> Unit) {
    val s = nav.superior
    val desti = Desti(s?.clau ?: "pestanya:" + nav.pestanya, nav.profunditat, nav.pestanya)
    AnimatedContent(targetState = desti, label = "pantalla", transitionSpec = {
        val ini = initialState; val fi = targetState
        when {
            fi.prof > ini.prof -> (slideInHorizontally(Moviment.suau()) { it / 3 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(Moviment.suau()) { -it / 8 } + fadeOut(tween(160)))
            fi.prof < ini.prof -> (slideInHorizontally(Moviment.suau()) { -it / 8 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(Moviment.suau()) { it / 3 } + fadeOut(tween(160)))
            else -> (fadeIn(tween(220, delayMillis = 60)) + scaleIn(Moviment.suau(), initialScale = 0.97f)) togetherWith fadeOut(tween(120))
        }
    }) { d ->
        val clau = d.clau
        when {
            clau == "ajustos" -> Config({ nav.enrere() }, triaFitxers, triaCarpeta, triaDrivers)
            clau.startsWith("album:") -> {
                val nom = clau.removePrefix("album:")
                val e = remember(b, nom, App.versio, LlistaTop.versio) { b.emissores.firstOrNull { it.nom == nom } }
                if (e == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Tx(t("Cap cançó"), Tipus.cos, M.tenu) }
                else PantallaAlbum(b, e)
            }
            else -> when (d.pestanya) {
                0 -> Inici(b, triaFitxers, triaCarpeta)
                1 -> Cerca(b)
                else -> BibliotecaPant(b, triaFitxers, triaCarpeta)
            }
        }
    }
}

/** Barra de navegació inferior: icones vectorials i una pastilla de l'accent darrere la pestanya triada (s'eixampla amb una molla). */
@Composable
private fun BarraNav(tab: Int, canvia: (Int) -> Unit) {
    val noms = listOf("Inici", "Cerca", "Biblioteca")
    val icones = listOf(CamiIcones.INICI, CamiIcones.CERCA, CamiIcones.BIBLIOTECA)
    Row(Modifier.fillMaxWidth().background(if (M.amoled) M.fons else M.sup.copy(alpha = 0.96f))
        .drawBehind { if (M.amoled) drawRect(M.contorn, size = size.copy(height = 1.dp.toPx())) }.navigationBarsPadding().padding(top = 8.dp, bottom = 8.dp)) {
        noms.forEachIndexed { i, n ->
            val sel = tab == i
            val ample by animateDpAsState(if (sel) 64.dp else 32.dp, Moviment.expressiu(), label = "pastilla")
            Column(Modifier.weight(1f).premsable(0.92f) { canvia(i) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(ample).height(32.dp).clip(CircleShape).background(if (sel) M.suau else M.sup.copy(alpha = 0f)), contentAlignment = Alignment.Center) {
                    Icona(icones[i], if (sel) M.sobreSuau else M.tenu, 24.dp)
                }
                Tx(t(n), Tipus.micro, if (sel) M.text else M.tenu)
            }
        }
    }
}

/** Capçalera gran d'una pestanya o pantalla (títol tipogràfic gran) amb accions a la dreta. */
@Composable
fun Capcalera(titol: String, mod: Modifier = Modifier, accions: @Composable () -> Unit = {}) {
    Row(mod.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Tx(titol, Tipus.display, M.text, Modifier.weight(1f))
        accions()
    }
}

/** Cercle d'icona per a l'esquerra de les pantalles apilades (enrere). */
@Composable
fun BotoEnrere(mod: Modifier = Modifier, toc: () -> Unit) {
    BotoIcona(CamiIcones.ENRERE, toc, mod.size(44.dp).then(if (M.amoled) Modifier.border(1.dp, M.contorn, CircleShape) else Modifier), fons = M.sup.copy(alpha = 0.85f), desc = t("Enrere"))
}
