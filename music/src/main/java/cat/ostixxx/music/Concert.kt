package cat.ostixxx.music

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Biblioteca
import cat.gbdsmixxx.Espectre
import cat.gbdsmixxx.RadioMotor
import cat.gbdsmixxx.t
import kotlinx.coroutines.delay

/**
 * Mode concert (l'autor, 2026-11-17): visualització a pantalla completa des del reproductor gran. La pantalla es queda encesa i les barres del sistema s'amaguen.
 * Es surt amb el botó de sortir (sempre visible, a dalt a l'esquerra) o amb l'enrere; tocar la pantalla només mostra o amaga els controls (selector, opcions i títol),
 * que s'amaguen sols al cap de 4 s. Onze visualitzacions sense fons (sobre el fons del tema; a AMOLED, negre pur), amb blocs quadrats i els colors de `vis_color`
 * (o un de propi). Cada una té les seves opcions (engranatge), que es desen a `App.env` (`concert_<vis>_<clau>`) i tenen «Valors per defecte».
 * Les dades pures (batecs, notes, combat, túnel) són a `ConcertDades.kt`; el dibuix de cada visualització, a `ConcertVis.kt`.
 */
object Concert {
    /** Claus de `t()` de les visualitzacions (l'índex és el que es desa a `concert_vis`: no canviïs l'ordre). */
    val NOMS = listOf("Espectre circular", "Ones per canal", "Piano", "Túnel", "Combat al ritme", "Espectrograma", "Estèreo", "Ones als batecs", "Pluja de blocs", "Feixos de llum", "Mirall",
        "Estels", "Focs artificials", "Boles", "Aigua", "Molles", "Pad de colors")
    var vis by mutableIntStateOf(0)
    fun carrega() { vis = App.env.int("concert_vis", 0).coerceIn(0, NOMS.size - 1) }
    fun posa(i: Int) { vis = (i + NOMS.size) % NOMS.size; App.env.putInt("concert_vis", vis) }

    /** Una opció: clau d'`Env`, nom (clau de `t()`), noms dels valors (claus de `t()`, o text tal qual si comença per «=») i el valor per defecte (índex). */
    class Op(val clau: String, val nom: String, val valors: List<String>, val def: Int)
    private val SN = listOf("Sí", "No")
    private val VEL = listOf("Lenta", "Normal", "Ràpida")
    private val MIDA = listOf("Petits", "Mitjans", "Grans")
    private val COMUNES = listOf(
        Op("color", "Color", listOf("Global") + ColorsVis.NOMS, 0),
        Op("sens", "Sensibilitat", listOf("Baixa", "Normal", "Alta"), 1),
        Op("titol", "Títol de la cançó", SN, 0),
        Op("toc", "Interacció (tocar i arrossegar)", SN, 0),
    )
    private val FORMA = listOf("Quadrats", "Cercles", "Estels")
    private val PROPIES: List<List<Op>> = listOf(
        listOf(Op("cols", "Columnes", listOf("=32", "=48", "=72"), 1), Op("mirall", "Simètric", SN, 0), Op("portada", "Portada", SN, 0), Op("pic", "Pic", SN, 0)),
        listOf(Op("forma", "Forma", listOf("Blocs", "Línia"), 0), Op("bloc", "Mida dels blocs", MIDA, 1), Op("noms", "Noms dels canals", SN, 0)),
        listOf(Op("rastre", "Rastre", listOf("Cap", "Curt", "Llarg"), 2), Op("octaves", "Teclat", listOf("4 octaves", "6 octaves"), 1), Op("llegenda", "Notes a dalt", SN, 0)),
        listOf(Op("n", "Partícules", listOf("Pocs", "Normal", "Molts"), 1), Op("forma", "Forma", FORMA, 0), Op("vel", "Velocitat", VEL, 1), Op("anells", "Anells", SN, 0)),
        listOf(Op("herois", "Herois", listOf("=1", "=2", "=3"), 2), Op("enemics", "Enemics", listOf("=1", "=2", "=3", "Variable"), 3), Op("ritme", "Ataquen", listOf("Cada batec", "Cada 2 batecs"), 0), Op("mida", "Mida", listOf("Gran", "Mitjana"), 0)),
        listOf(Op("vel", "Velocitat", VEL, 1), Op("bandes", "Columnes", listOf("=24", "=48"), 1), Op("dir", "Direcció", listOf("Cau", "Puja"), 0)),
        listOf(Op("forma", "Forma", listOf("Blocs", "Punts", "Línia"), 0), Op("gir", "Gira 45°", SN, 0), Op("rastre", "Rastre", listOf("Cap", "Curt", "Llarg"), 1), Op("bloc", "Mida dels blocs", MIDA, 1)),
        listOf(Op("forma", "Forma", listOf("Quadrada", "Rodona", "Cercle suau"), 0), Op("vel", "Velocitat", VEL, 1), Op("greus", "Ones amb els greus", SN, 0)),
        listOf(Op("dens", "Densitat", listOf("Baixa", "Normal", "Alta"), 1), Op("vel", "Velocitat", VEL, 1), Op("bloc", "Mida dels blocs", MIDA, 1)),
        listOf(Op("n", "Feixos", listOf("=4", "=6", "=8"), 1), Op("forma", "Forma", listOf("Blocs", "Llum"), 0), Op("vel", "Moviment", VEL, 1), Op("flaix", "Flaix als batecs", SN, 0)),
        listOf(Op("cols", "Columnes", listOf("=16", "=32", "=48"), 1), Op("orient", "Orientació", listOf("Horitzontal", "Vertical"), 0), Op("pic", "Pic", SN, 0)),
        // noves i interactives (ronda 3)
        listOf(Op("mida", "Mida", listOf("Petits", "Mitjans", "Grans"), 1), Op("durada", "Rastre", listOf("Curt", "Mitjà", "Llarg"), 1), Op("sols", "Estels sols amb la música", SN, 0)),
        listOf(Op("n", "Partícules", listOf("Pocs", "Normal", "Molts"), 1), Op("grav", "Gravetat", listOf("Cap", "Poca", "Molta"), 1), Op("auto", "Focs sols als batecs", SN, 0), Op("forma", "Forma", FORMA, 1)),
        listOf(Op("mida", "Mida", listOf("Petits", "Mitjans", "Grans"), 1), Op("max", "Màxim de boles", listOf("=8", "=16", "=32"), 1), Op("rebot", "Rebot", listOf("Poc", "Normal", "Molt"), 1), Op("forma", "Forma", listOf("Cercles", "Quadrats"), 0)),
        listOf(Op("malla", "Malla", listOf("Ampla", "Normal", "Densa"), 1), Op("amort", "Durada de les ones", listOf("Curta", "Normal", "Llarga"), 1), Op("forma", "Forma", listOf("Punts", "Quadrats"), 0)),
        listOf(Op("nodes", "Nodes", listOf("=12", "=20", "=32"), 1), Op("rigid", "Rigidesa", listOf("Tova", "Normal", "Dura"), 1), Op("forma", "Forma", listOf("Anell", "Cadena"), 0)),
        listOf(Op("pads", "Pads", listOf("3x3", "4x4", "6x6"), 1), Op("forma", "Forma", listOf("Quadrats", "Rodons"), 0), Op("encesa", "Encesa", listOf("Curta", "Llarga"), 0)),
    )
    // Revisió UI (2026-11-22): `op` es crida moltes vegades per fotograma des del dibuix; abans cada crida feia una llista nova (`PROPIES[v] + COMUNES`) i dos textos
    // (`concert_<v>_<k>`). Ara les llistes es fan una vegada i el valor es desa per índex d'opció (-1 = encara no llegit d'`App.env`). Mateixes claus d'`App.env`.
    private val OPCIONS: List<List<Op>> = PROPIES.map { it + COMUNES }
    fun opcions(v: Int): List<Op> = OPCIONS[v]

    /** Versió de les opcions (estat de Compose: el dibuix es refà en canviar-les). */
    var versio by mutableIntStateOf(0)
    private val cau: Array<IntArray> = Array(OPCIONS.size) { IntArray(OPCIONS[it].size) { -1 } }
    private fun clauEnv(v: Int, k: String) = "concert_${v}_$k"
    /** Valor (índex) de l'opció `k` de la visualització `v`. */
    fun op(v: Int, k: String): Int {
        val ops = OPCIONS[v]
        var i = 0; while (i < ops.size && ops[i].clau != k) i++
        if (i == ops.size) return 0
        val c = cau[v]
        if (c[i] < 0) { val o = ops[i]; c[i] = App.env.int(clauEnv(v, k), o.def).coerceIn(0, o.valors.size - 1) }
        return c[i]
    }
    fun posaOp(v: Int, k: String, i: Int) {
        val j = OPCIONS[v].indexOfFirst { it.clau == k }
        if (j >= 0) cau[v][j] = i
        App.env.putInt(clauEnv(v, k), i); versio++
    }
    fun perDefecte(v: Int) {
        val ks = OPCIONS[v].map { clauEnv(v, it.clau) }
        java.util.Arrays.fill(cau[v], -1)
        App.env.esborra(*ks.toTypedArray()); versio++
    }
    fun si(v: Int, k: String): Boolean = op(v, k) == 0
    /** Color de la visualització `v` (índex de `ColorsVis`): el global de Visual o el propi. */
    fun color(v: Int): Int { val c = op(v, "color"); return if (c == 0) Vis.color else c - 1 }
    /** Factor de la sensibilitat (sobre l'energia de l'espectre). */
    fun sensibilitat(v: Int): Float = when (op(v, "sens")) { 0 -> 0.55f; 2 -> 1.9f; else -> 1f }
    /** Factor de velocitat d'una opció «Lenta/Normal/Ràpida». */
    fun vel(v: Int, k: String = "vel"): Float = when (op(v, k)) { 0 -> 0.5f; 2 -> 1.8f; else -> 1f }
    fun mida(v: Int, k: String = "bloc"): Float = when (op(v, k)) { 0 -> 0.7f; 2 -> 1.5f; else -> 1f }
    /** La interacció (tocar i arrossegar) és activa. */
    fun interactiu(v: Int): Boolean = si(v, "toc")
    /** Text d'un valor. */
    fun nomValor(s: String): String = if (s.startsWith("=")) s.substring(1) else t(s)

    const val DIALEG_OPCIONS = "concert_opcions"
}

/** Estat de les visualitzacions del concert (un per pantalla oberta). */
internal class ModelConcert {
    val esp = EspectreVis()
    val det = DetectorBatec()
    val detCombat = DetectorBatec(CombatRitme.AVANC.toDouble())
    val tunel = Tunel(280)
    val combat = CombatRitme(64, System.nanoTime())
    /** Herois del combat (files del full `herois.png`): 3 diferents a l'atzar en obrir el concert. */
    val herois: IntArray = (0 until HEROIS_N).shuffled().take(3).toIntArray()
    val ones = ArrayList<OnaVis>()
    val pintor = Pintor()
    var t0 = 0L
    var temps = 0f
    /** Greus (0-1) suavitzats, l'últim valor sense suavitzar i l'empenta del batec (1 en el batec, cau en ~0,3 s). */
    var greus = 0f
    var greusBrut = 0f
    var empenta = 0f
    var batecs = 0
    var batecsCombat = 0
    private val esc = FloatArray(Espectre.N)
    // piano
    var notes = IntArray(0)
    val historia = ArrayList<IntArray>()
    var tNotes = 0f
    var exactes = FloatArray(0)          // notes MIDI exactes dels registres del motor (GB, NES)
    var notesExactes = false
    val tmp = FloatArray(NotesCanal.MIDA_TMP); val dif = FloatArray(NotesCanal.MIDA_TMP)
    var buf = FloatArray(0)
    // espectrograma, estèreo, ones als batecs, pluja
    val files = ArrayList<FloatArray>(); var tFila = 0f
    val rastreEst = ArrayList<FloatArray>()
    val anells = ArrayList<FloatArray>(); var tGreus = 0f          // [edat, força]
    val gotes = ArrayList<FloatArray>()          // [columna, y (cel·les), velocitat, llargada]
    private var llavor = 0x2545F4914F6CDD1DL
    fun rnd(): Float { llavor = llavor * 6364136223846793005L + 1442695040888963407L; return ((llavor ushr 40) and 0xFFFFFF).toFloat() / 0x1000000 }

    fun dt(t: Long): Float { val d = if (t0 == 0L) 0.016f else ((t - t0) / 1e9f).coerceIn(0f, 0.1f); t0 = t; return d }

    /** Un pas de les dades comunes: espectre (amb la sensibilitat), greus i batec. Torna si hi ha batec. */
    fun pas(dt: Float, cols: Int, sens: Float): Boolean {
        val actiu = EstatRepro.actiu
        val v = Espectre.valors
        for (k in 0 until Espectre.N) esc[k] = v[k] * sens
        esp.avanca(esc, Espectre.N, cols, actiu, dt)
        val g = if (actiu) EspectreVis.nivellDb((esc[0] + esc[1] + esc[2]) / 3f) else 0f
        greusBrut = g
        greus += (g - greus) * (1f - Math.exp(-dt / 0.08).toFloat())
        if (actiu) temps += dt
        viu = maxOf(0f, viu - dt)
        val si = RadioMotor.sinte
        val bat = det.pas(dt, actiu, si?.batec ?: -1.0, si?.bpm ?: 0.0, g)
        empenta = if (bat) 1f else maxOf(0f, empenta - dt / 0.3f)
        if (bat) batecs++
        return bat
    }
    // ---- interacció (ronda 3): tocs nous, dit que prem i arrossegament acumulat des de l'últim fotograma (fil principal)
    var dit: androidx.compose.ui.geometry.Offset? = null; private set
    private val tocs = ArrayList<androidx.compose.ui.geometry.Offset>()
    private var arr = androidx.compose.ui.geometry.Offset.Zero
    /** Per no aturar el rellotge mentre es toca o es mou alguna cosa d'una visualització interactiva. */
    var viu = 0f
    fun prem(p: androidx.compose.ui.geometry.Offset) { tocs.add(p); dit = p; viu = 3f; tocar++ }
    fun mou(p: androidx.compose.ui.geometry.Offset) { dit?.let { arr += p - it }; dit = p; viu = 3f }
    fun deixa() { dit = null }
    /** Comptador que el gest puja: el rellotge torna a anar encara que no soni res. */
    var tocar by androidx.compose.runtime.mutableIntStateOf(0)
    /** Tocs nous des de l'últim fotograma (i els buida); buit si la interacció és apagada. */
    fun prenTocs(actiu: Boolean): List<androidx.compose.ui.geometry.Offset> { if (tocs.isEmpty()) return emptyList(); val l = ArrayList(tocs); tocs.clear(); return if (actiu) l else emptyList() }
    fun prenArr(actiu: Boolean): androidx.compose.ui.geometry.Offset { val a = arr; arr = androidx.compose.ui.geometry.Offset.Zero; return if (actiu) a else androidx.compose.ui.geometry.Offset.Zero }
    fun ditSi(actiu: Boolean) = if (actiu) dit else null
    // estat propi de cada visualització interactiva (ConcertVis2.kt)
    var rotacio = 0f; var congelat = false; var canalTriat = -1; var invers = false
    var focus: androidx.compose.ui.geometry.Offset? = null
    val part = ArrayList<FloatArray>()
    var aigua: FloatArray? = null; var aiguaV: FloatArray? = null; var aiguaN = 0
    var molles: FloatArray? = null
    var padTo = -1f
    val pads = FloatArray(64)

    val quiet: Boolean get() = esp.quiet && greus < 0.01f && empenta <= 0f && anells.isEmpty() && gotes.isEmpty() && viu <= 0f

    companion object { const val HEROIS_N = 10 }
}

/** Temps del fotograma mentre sona; en pausa continua fins que tot és quiet i s'atura (cap dibuix més). */
@Composable
private fun rellotgeConcert(tocar: Int, quiet: () -> Boolean): State<Long> {
    val t = remember { mutableLongStateOf(0L) }
    val actiu = EstatRepro.actiu
    val desperta = tocar > 0 && !actiu          // en pausa, un toc torna a engegar el rellotge fins que tot torna a estar quiet
    LaunchedEffect(actiu, if (desperta) tocar else 0) {
        while (true) {
            withFrameNanos { t.longValue = it }
            if (!actiu && quiet()) break
        }
    }
    return t
}

private fun activitat(c: Context): Activity? { var x: Context? = c; while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }; return null }

@Composable
fun PantallaConcert(b: Biblioteca) {
    val nav = LocalNav.current
    val view = LocalView.current
    // pantalla encesa i barres del sistema amagades (tornen lliscant des de la vora) mentre el concert és obert
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val w = activitat(view.context)?.window
        val ctl = w?.let { WindowCompat.getInsetsController(it, view) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { view.keepScreenOn = false; ctl?.show(WindowInsetsCompat.Type.systemBars()); nav.tancaDialeg(Concert.DIALEG_OPCIONS) }
    }
    val joc = remember(b, EstatRepro.joc, EstatRepro.seq, App.versio) { jocDeCanco(b, b.emissores.firstOrNull { it.nom == EstatRepro.joc }, EstatRepro.seq) }
    val m = remember { ModelConcert() }
    val t = rellotgeConcert(m.tocar) { m.quiet && m.combat.quiet }
    val opcionsObert = nav.te(Concert.DIALEG_OPCIONS)
    LaunchedEffect(Concert.vis) { m.part.clear(); m.anells.clear(); m.gotes.clear(); m.focus = null; m.canalTriat = -1; m.congelat = false; m.invers = false }
    // controls: es veuen en obrir, en tocar la pantalla i en canviar de visualització; s'amaguen sols al cap de 4 s (no mentre les opcions són obertes)
    var controls by remember { mutableStateOf(true) }
    var toc by remember { mutableIntStateOf(0) }
    LaunchedEffect(toc, controls, opcionsObert) { if (controls && !opcionsObert) { delay(4000); controls = false } }
    val dist = with(LocalDensity.current) { 60.dp.toPx() }
    val v = Concert.vis
    Box(Modifier.fillMaxSize()
        .drawBehind {
            // el fons del tema, com l'arrel (a AMOLED, negre pur; si no, el degradat de dalt): cap panell darrere les visualitzacions
            drawRect(M.fons)
            if (!M.amoled) drawRect(Brush.verticalGradient(listOf(M.fonsAlt, M.fons), endY = size.height * 0.45f), size = size.copy(height = size.height * 0.45f))
        }) {
        // ronda 3: el toc i l'arrossegament són per a la visualització (interacció); el doble toc mostra o amaga els controls
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 72.dp, bottom = 40.dp).pointerInput(Unit) {
            var ultim = 0L; var pos0 = androidx.compose.ui.geometry.Offset.Zero
            awaitEachGesture {
                val d = awaitFirstDown()
                if (d.uptimeMillis - ultim < 320 && (d.position - pos0).getDistance() < dist) { controls = !controls; toc++; ultim = 0L } else { ultim = d.uptimeMillis; pos0 = d.position }
                m.prem(d.position)
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == d.id } ?: break
                    if (!ch.pressed) break
                    m.mou(ch.position); ch.consume()
                }
                m.deixa()
            }
        }) {
            Concert.versio
            when (v) {
                0 -> EspectreCircular(m, t, joc, v)
                1 -> OnesCanal(m, t, v)
                2 -> Piano(m, t, v)
                3 -> TunelVis(m, t, v)
                4 -> Combat(m, t, v)
                5 -> Espectrograma(m, t, v)
                6 -> Estereo(m, t, v)
                7 -> OnesBatecs(m, t, v)
                8 -> Pluja(m, t, v)
                9 -> Feixos(m, t, v)
                10 -> Mirall(m, t, v)
                11 -> Estels(m, t, v)
                12 -> Focs(m, t, v)
                13 -> Boles(m, t, v)
                14 -> Aigua(m, t, v)
                15 -> Molles(m, t, v)
                else -> PadColors(m, t, v)
            }
        }
        // a dalt: sortir (sempre visible, zona de toc gran), selector i opcions (s'amaguen)
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 8.dp, start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(M.supAlta.copy(alpha = if (controls) 0.9f else 0.55f)).botoAccessible(t("Surt del concert")).premsable(0.9f) { nav.tancaConcert() }, contentAlignment = Alignment.Center) {
                Icona(CamiIcones.TANCA, M.text, 28.dp)
            }
            // mostrar o amagar els controls (també amb doble toc)
            if (!controls) BotoIcona(CamiIcones.MES, { controls = true; toc++ }, Modifier.padding(start = 8.dp), mida = 48.dp, icona = 24.dp, color = M.text, fons = M.supAlta.copy(alpha = 0.55f), desc = t("Mostra els controls"))
            AnimatedVisibility(controls, Modifier.weight(1f), enter = fadeIn(), exit = fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ll = rememberLazyListState()
                    LaunchedEffect(Concert.vis) { ll.animateScrollToItem(Concert.vis) }
                    LazyRow(Modifier.weight(1f), state = ll, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        itemsIndexed(Concert.NOMS) { i, n -> Xip(t(n), i == Concert.vis) { Concert.posa(i); toc++ } }
                    }
                    BotoIcona(CamiIcones.AJUSTOS, { nav.obreDialeg(Concert.DIALEG_OPCIONS) }, mida = 52.dp, icona = 26.dp, color = M.text, fons = M.supAlta.copy(alpha = 0.9f), desc = t("Opcions"))
                }
            }
        }
        AnimatedVisibility(controls, Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 10.dp, start = 16.dp, end = 16.dp), enter = fadeIn(), exit = fadeOut()) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (Concert.si(v, "titol")) Tx(if (EstatRepro.sonant) EstatRepro.titol else t("No sona res"), Tipus.etiqueta, M.tenu)
                Tx(t("Toca dues vegades per mostrar o amagar els controls."), Tipus.micro, M.tenu)
            }
        }
        if (opcionsObert) OpcionsConcert(v) { nav.tancaDialeg(Concert.DIALEG_OPCIONS) }
    }
}

/** Full d'opcions de la visualització `v`. */
@Composable
private fun OpcionsConcert(v: Int, tanca: () -> Unit) {
    Full(t("Opcions") + ": " + t(Concert.NOMS[v]), tanca) {
        Concert.versio
        for (o in Concert.opcions(v)) {
            Tx(t(o.nom), Tipus.etiqueta, M.tenu)
            val sel = Concert.op(v, o.clau)
            if (o.valors.size <= 4) Segmentat(o.valors.map { Concert.nomValor(it) }, sel) { i -> Concert.posaOp(v, o.clau, i) }
            else {
                val ll = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, sel - 1))
                LazyRow(Modifier.fillMaxWidth(), state = ll, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(o.valors) { i, n -> Xip(Concert.nomValor(n), i == sel) { Concert.posaOp(v, o.clau, i) } }
                }
            }
        }
        BotoTonal(t("Valors per defecte"), CamiIcones.ACTUALITZA, Modifier.fillMaxWidth().padding(top = 8.dp)) { Concert.perDefecte(v) }
    }
}

// ---------------------------------------------------------------- colors

internal fun argbDe(c: Color): Int = ColorsPurs.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

/** Rampa (baix, mig, dalt) de cada canal: el color triat girat de to per canal (l'arc de Sant Martí, del vermell al violeta). */
// Revisió UI (2026-11-22): `rampesCanals` es crida a cada fotograma (Estels, Focs, Boles, Aigua...); abans refeia les n rampes (arrays i llistes) cada vegada.
// Ara es recorda l'última (només n'hi ha una visualització oberta): es refà si canvia n, el color del joc, el color triat, el fons o el mode fosc. Només fil principal.
private var rampesN = -1; private var rampesSel = -1; private var rampesFosc = false
private var rampesJoc = Color.Unspecified; private var rampesFons = Color.Unspecified
private var rampesUltimes: Array<Array<Color>> = emptyArray()

internal fun rampesCanals(n: Int, joc: Color, sel: Int): Array<Array<Color>> {
    val fons = M.fons; val fosc = M.fosc
    if (n == rampesN && sel == rampesSel && fosc == rampesFosc && joc == rampesJoc && fons == rampesFons) return rampesUltimes
    val r = rampesCanalsNoves(n, joc, sel)
    rampesN = n; rampesSel = sel; rampesFosc = fosc; rampesJoc = joc; rampesFons = fons; rampesUltimes = r
    return r
}

private fun rampesCanalsNoves(n: Int, joc: Color, sel: Int): Array<Array<Color>> {
    val fons = ColorsPurs.opac(argbDe(M.fons))
    val hsl = ColorsPurs.aHsl(ColorsPurs.opac(if (sel == ColorsVis.JOC) argbDe(joc) else ColorsVis.base(sel)))
    return Array(n) { i ->
        val h = if (sel == ColorsVis.ARC) ColorsVis.toArc(i, n) else hsl[0] + i * 360f / n.coerceAtLeast(1)
        val base = ColorsPurs.deHsl(((h % 360f) + 360f) % 360f, hsl[1].coerceIn(0.55f, 0.95f), 0.56f)
        ColorsVis.rampa(ColorsPurs.opac(base), fons, M.fosc).map { Color(it) }.toTypedArray()
    }
}
