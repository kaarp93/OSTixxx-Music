package cat.ostixxx.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Emissora

/**
 * Sistema de disseny d'OSTixxx Music (l'autor, 2026-11-17: «hauria de ser del 2026»; referents Apple Music, Spotify i Material 3 Expressive).
 * Substitueix els aspectes d'abans (`Skin`, 0 vidre, 1 neó, 2 retro, 3 color): ara hi ha un sol disseny amb tres modes (clar, fosc, AMOLED, o el del sistema)
 * i el COLOR DINÀMIC de la portada que sona. Les estètiques retro només queden als visualitzadors (`Visualitzadors.kt`).
 * Colors: `Paletes.genera` (Kotlin pur, provat a JVM); aquí només es passen a Compose i s'animen.
 */
object TemaUi {
    /** Mode triat a Ajustos > Tema (preferència `tema_mode`). */
    var mode by mutableStateOf(ModeTema.FOSC)
    /** Colors d'ara (animats quan canvia la portada o el mode). Els llegeix `M`. */
    var colors by mutableStateOf(ColorsUi.de(Paletes.genera(null, ModeTema.FOSC)))
        internal set

    /** A l'arrencada (abans del primer dibuix, perquè no es vegi un fotograma del mode equivocat). */
    fun carrega(ctx: android.content.Context) {
        mode = ModeTema.de(App.env.int("tema_mode", ModeTema.FOSC.codi))
        val fosc = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        colors = ColorsUi.de(Paletes.genera(null, mode, fosc))
    }
    fun posa(m: ModeTema) { mode = m; App.env.putInt("tema_mode", m.codi) }
}

/** La paleta en colors de Compose. */
@Immutable
data class ColorsUi(
    val fosc: Boolean, val amoled: Boolean,
    val fons: Color, val fonsAlt: Color, val sup: Color, val supAlta: Color, val contorn: Color,
    val text: Color, val tenu: Color, val accent: Color, val sobreAccent: Color, val suau: Color, val sobreSuau: Color, val error: Color,
) {
    companion object {
        fun de(p: Paleta) = ColorsUi(p.fosc, p.mode == ModeTema.AMOLED, Color(p.fons), Color(p.fonsAlt), Color(p.superficie), Color(p.superficieAlta), Color(p.contorn),
            Color(p.text), Color(p.tenu), Color(p.accent), Color(p.sobreAccent), Color(p.accentSuau), Color(p.sobreAccentSuau), Color(p.error))
    }
}

/** Colors d'ara (es poden llegir des de qualsevol lloc, també des del dibuix). */
object M {
    private val c get() = TemaUi.colors
    val fons: Color get() = c.fons
    val fonsAlt: Color get() = c.fonsAlt
    val sup: Color get() = c.sup
    val supAlta: Color get() = c.supAlta
    val contorn: Color get() = c.contorn
    val text: Color get() = c.text
    val tenu: Color get() = c.tenu
    val accent: Color get() = c.accent
    val sobreAccent: Color get() = c.sobreAccent
    val suau: Color get() = c.suau
    val sobreSuau: Color get() = c.sobreSuau
    val error: Color get() = c.error
    val fosc: Boolean get() = c.fosc
    /** Mode AMOLED: fons i superfícies negre pur; la separació la fan les vores. */
    val amoled: Boolean get() = c.amoled
    /** Color de la taca tonal dels degradats i ressalts de fons: a AMOLED, el fons (negre: el fons no es tenyeix mai). */
    val tint: Color get() = if (c.amoled) c.fons else c.suau
    /** Pista de les barres de progrés i dels lliscadors. */
    val pista: Color get() = c.text.copy(alpha = 0.16f)
    /** Colors fixos de les etiquetes de pista (Cançó, Sfx, Jingle, Descarta) i de les marques ✓/✗: no canvien amb la portada. */
    val bo = Color(0xFF2E9D4A); val mal = Color(0xFFD93C3C)
    val etCanco = Color(0xFF2F6FDB); val etSfx = Color(0xFFE07A1F); val etJingle = Color(0xFF8E44C9)
}

/** Tipografia: la del sistema (Roboto / la del mòbil), gran i amb pes; els títols grans una mica més junts. */
object Tipus {
    // Work Sans (SIL OFL 1.1, fitxer OFL-WorkSans.txt)
    private val f = FontFamily(
        androidx.compose.ui.text.font.Font(R.font.worksans_light, FontWeight.Light),
        androidx.compose.ui.text.font.Font(R.font.worksans_regular, FontWeight.Normal),
        androidx.compose.ui.text.font.Font(R.font.worksans_medium, FontWeight.Medium),
        androidx.compose.ui.text.font.Font(R.font.worksans_semibold, FontWeight.SemiBold),
        androidx.compose.ui.text.font.Font(R.font.worksans_bold, FontWeight.Bold))
    /** Filtres estil Windows Phone (pivot): paraules grans i primes en minúscula. */
    val pivot = TextStyle(fontFamily = f, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp)
    val pivotPetit = TextStyle(fontFamily = f, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.2).sp)
    val display = TextStyle(fontFamily = f, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
    val titol = TextStyle(fontFamily = f, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    val seccio = TextStyle(fontFamily = f, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
    val gran = TextStyle(fontFamily = f, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val cos = TextStyle(fontFamily = f, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val cosFort = TextStyle(fontFamily = f, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val etiqueta = TextStyle(fontFamily = f, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp)
    val petit = TextStyle(fontFamily = f, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp)
    val micro = TextStyle(fontFamily = f, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
}

/** Formes: cantonades grans i arrodonides (Material 3 Expressive). */
object Formes {
    val petita: Dp = 12.dp
    val mitjana: Dp = 18.dp
    val gran: Dp = 26.dp
    val enorme: Dp = 32.dp
}

/** Moviment: molles (spring) en lloc de durades fixes. */
object Moviment {
    /** Per a canvis de pantalla i fulls: ràpid i sense rebot. */
    fun <T> suau(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 420f)
    /** Expressiu: amb un rebot petit (botons, portada, pastilla de la barra). */
    fun <T> expressiu(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
    /** Premut: molt ràpid. */
    fun <T> premut(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 700f)
}

/** Tema de l'arrel: calcula la paleta de la llavor (portada que sona) i del mode, l'anima, la publica a `TemaUi.colors`, posa el Material 3 i la barra d'estat. */
@Composable
fun TemaArrel(llavor: Int?, contingut: @Composable () -> Unit) {
    val sistemaFosc = isSystemInDarkTheme()
    val mode = TemaUi.mode
    val obj = remember(llavor, mode, sistemaFosc) { ColorsUi.de(Paletes.genera(llavor, mode, sistemaFosc)) }
    val es = tween<Color>(550)
    val fons by animateColorAsState(obj.fons, es, label = "fons"); val fonsAlt by animateColorAsState(obj.fonsAlt, es, label = "fonsAlt")
    val sup by animateColorAsState(obj.sup, es, label = "sup"); val supAlta by animateColorAsState(obj.supAlta, es, label = "supAlta")
    val contorn by animateColorAsState(obj.contorn, es, label = "contorn"); val text by animateColorAsState(obj.text, es, label = "text")
    val tenu by animateColorAsState(obj.tenu, es, label = "tenu"); val accent by animateColorAsState(obj.accent, es, label = "accent")
    val sobreAccent by animateColorAsState(obj.sobreAccent, es, label = "sobreAccent"); val suau by animateColorAsState(obj.suau, es, label = "suau")
    val sobreSuau by animateColorAsState(obj.sobreSuau, es, label = "sobreSuau"); val error by animateColorAsState(obj.error, es, label = "error")
    val actual = ColorsUi(obj.fosc, obj.amoled, fons, fonsAlt, sup, supAlta, contorn, text, tenu, accent, sobreAccent, suau, sobreSuau, error)
    SideEffect { TemaUi.colors = actual }          // estat global (igualtat estructural: escriure el mateix valor no refà res)
    val esquema = remember(obj) {
        if (obj.fosc) darkColorScheme(primary = obj.accent, onPrimary = obj.sobreAccent, primaryContainer = obj.suau, onPrimaryContainer = obj.sobreSuau,
            secondary = obj.accent, onSecondary = obj.sobreAccent, background = obj.fons, onBackground = obj.text, surface = obj.sup, onSurface = obj.text,
            surfaceVariant = obj.supAlta, onSurfaceVariant = obj.tenu, outline = obj.contorn, error = obj.error)
        else lightColorScheme(primary = obj.accent, onPrimary = obj.sobreAccent, primaryContainer = obj.suau, onPrimaryContainer = obj.sobreSuau,
            secondary = obj.accent, onSecondary = obj.sobreAccent, background = obj.fons, onBackground = obj.text, surface = obj.sup, onSurface = obj.text,
            surfaceVariant = obj.supAlta, onSurfaceVariant = obj.tenu, outline = obj.contorn, error = obj.error)
    }
    // de vora a vora (MusicActivity): barres del sistema transparents, amb les icones fosques en mode clar
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        val ic = androidx.core.view.WindowCompat.getInsetsController(w, view)
        ic.isAppearanceLightStatusBars = !obj.fosc; ic.isAppearanceLightNavigationBars = !obj.fosc
    }
    MaterialTheme(colorScheme = esquema, content = contingut)
}

/** Text d'una línia (o `linies`) amb la tipografia de l'app. `mida` en sp; `negreta` = semibold. Es manté pels blocs que el feien servir (Marques, CarregaUi). */
@Composable
fun Tx2(s: String, mida: Int = 14, color: Color = M.text, negreta: Boolean = false, mod: Modifier = Modifier, linies: Int = 1) {
    Text(s, modifier = mod, color = color, fontSize = mida.sp, fontWeight = if (negreta) FontWeight.SemiBold else FontWeight.Normal, maxLines = linies, overflow = TextOverflow.Ellipsis)
}

/** Text amb un estil de `Tipus`. */
@Composable
fun Tx(s: String, estil: TextStyle, color: Color = M.text, mod: Modifier = Modifier, linies: Int = 1) {
    Text(s, modifier = mod, color = color, style = estil, maxLines = linies, overflow = TextOverflow.Ellipsis)
}

/** Nom que es mostra: `vista`, que porta l'etiqueta del driver («Joc - Auto», «Joc - Exp») quan la ROM en té més d'un (abans es mostrava `iconaNom`, el títol net, i l'etiqueta no es veia mai). */
fun titolAlbum(e: Emissora) = e.vista

/** Regions i revisions del nom de libretro/No-Intro, p. ex. «Mega Man 6 (USA) (Rev 1)» → «USA · Rev 1»; buit si no hi ha dades de libretro. */
fun versioDeNom(nom: String): String = Regex("\\(([^)]*)\\)").findAll(nom).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.joinToString(" · ")

/** Versió i CRC d'un àlbum de ROM (l'autor, 2026-11-19: perquè es distingeixin dues ROM del mateix joc): «USA · FA9EC0C4». La regió surt de libretro (si s'ha baixat); el CRC, de la ROM. Buit per a llistes i fitxers sense CRC. */
@androidx.compose.runtime.Composable
fun versioIcrc(e: Emissora): String {
    if (e.esLlista) return ""
    val crc = e.crc ?: return ""
    val v = Libretro.infoUi(androidx.compose.ui.platform.LocalContext.current, e)?.nom?.let { versioDeNom(it) } ?: ""
    return (if (v.isEmpty()) "" else "$v · ") + "%08X".format(crc)
}

/** Sense efecte de pulsació per defecte: cada element tria el seu (vegeu `premsable`, que encongeix l'element amb una molla). */
object SenseIndicacio : androidx.compose.foundation.Indication {
    @androidx.compose.runtime.Composable
    override fun rememberUpdatedInstance(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.foundation.IndicationInstance =
        object : androidx.compose.foundation.IndicationInstance { override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.drawIndication() { drawContent() } }
}
