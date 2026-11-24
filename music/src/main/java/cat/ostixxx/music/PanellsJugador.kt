package cat.ostixxx.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.App
import cat.gbdsmixxx.EfectesRadio
import cat.gbdsmixxx.RadioDades
import cat.gbdsmixxx.RadioMotor
import cat.gbdsmixxx.t

/**
 * Una pestanya del reproductor gran: un xip a la fila de pestanyes que obre un full amb el seu contingut.
 * `titol` és la clau de `t()`; `activa` pinta el xip com a actiu (p. ex. EQ en marxa); `etiqueta` hi afegeix un text curt (p. ex. «×2»).
 */
class PestanyaJugador(
    val id: String,
    val titol: String,
    val icona: String,
    val activa: () -> Boolean = { false },
    val etiqueta: () -> String = { "" },
    val contingut: @Composable (tanca: () -> Unit) -> Unit,
)

/**
 * Pestanyes del reproductor gran, en ordre. **Extensible**: un altre bloc (p. ex. el d'efectes) hi afegeix la seva amb
 * `PestanyesJugador.registra(PestanyaJugador("efectes", "Efectes", CamiIcones.EQ) { tanca -> ... })` (abans que es mostri el reproductor, p. ex. a `MusicActivity.onCreate`),
 * sense tocar el reproductor. Registrar un id que ja hi és el substitueix.
 */
object PestanyesJugador {
    val llista = mutableStateListOf<PestanyaJugador>()
    fun registra(p: PestanyaJugador) {
        val i = llista.indexOfFirst { it.id == p.id }
        if (i >= 0) llista[i] = p else llista.add(p)
    }
    init {
        registra(PestanyaJugador("efectes", "Efectes", CamiIcones.EQ, activa = { EfectesRadio.algunActiu }) { PanellEfectes() })          // substitueix l'antiga «Equalitzador» (bloc 4)
        registra(PestanyaJugador("canals", "Canals", CamiIcones.CANALS) { PanellCanals() })
        registra(PestanyaJugador("bucles", "Bucles", CamiIcones.BUCLE, activa = { RadioDades.bucles > 0 || RadioDades.tempsMax > 0 }, etiqueta = { etiquetaBucles() }) { OpcionsBucles() })
        registra(PestanyaJugador("visual", "Visual", CamiIcones.VISUAL) { OpcionsVisual(true) })
        // mode comparació (2026-11-17): drivers del mateix joc i amb/sense efectes, a la mateixa posició; el xip diu «B» si sona alguna cosa que no és l'original
        registra(PestanyaJugador("compara", "Compara", CamiIcones.COMPARA, activa = { Comparacio.sonant != null || EstatRepro.senseEfectes },
            etiqueta = { if (Comparacio.sonant != null || EstatRepro.senseEfectes) "B" else "" }) { PanellComparacio() })
    }
}

private fun etiquetaBucles(): String {
    val v = RadioDades.bucles; val m = RadioDades.tempsMax
    return if (v > 0) "×$v" else if (m > 0) "${m / 60}m" else "∞"
}

private val OPCIONS_VOLTES = listOf(1, 2, 3, 4, 0)
private val OPCIONS_TEMPS = listOf(0, 60, 120, 180, 300)

/** Voltes per cançó i temps màxim (el mateix a Ajustos i al reproductor). */
@Composable
fun OpcionsBucles() {
    var v by remember { mutableIntStateOf(RadioDades.bucles) }
    var m by remember { mutableIntStateOf(RadioDades.tempsMax) }
    Tx(t("Voltes per cançó"), Tipus.etiqueta, M.tenu)
    Segmentat(OPCIONS_VOLTES.map { if (it == 0) "∞" else "×$it" }, OPCIONS_VOLTES.indexOf(v).coerceAtLeast(0)) { i -> v = OPCIONS_VOLTES[i]; RadioDades.bucles = v; RadioDades.desa() }
    Tx(t("Temps màxim"), Tipus.etiqueta, M.tenu)
    Segmentat(OPCIONS_TEMPS.map { if (it == 0) "∞" else "${it / 60} min" }, OPCIONS_TEMPS.indexOf(m).coerceAtLeast(0)) { i -> m = OPCIONS_TEMPS[i]; RadioDades.tempsMax = m; RadioDades.desa() }
}

/** Forma (barres, oscil·loscopi, barres i ones), estil d'època, color i font del visualitzador, amb una vista prèvia (el mateix a Ajustos i al reproductor). */
@Composable
fun OpcionsVisual(ambVista: Boolean) {
    Tx(t("Forma"), Tipus.etiqueta, M.tenu)
    Segmentat(Vis.NOMS_FORMA.map { t(it) }, Vis.forma) { i -> Vis.forma = i; App.env.putInt("vis_forma", i) }
    Tx(t("Estil"), Tipus.etiqueta, M.tenu)
    Segmentat(Vis.NOMS_ESTIL, Vis.estil) { k -> Vis.estil = k; App.env.putInt("vis_estil", k) }
    Tx(t("Color"), Tipus.etiqueta, M.tenu)
    TriaColorVis()
    Tx(t("Gràfic"), Tipus.etiqueta, M.tenu)
    Segmentat(listOf(t("Segons el so total"), t("Segons el canal")), if (Vis.canal) 1 else 0) { i -> Vis.canal = i == 1; App.env.putInt("vis_mode", i) }
    if (ambVista) Visualitzador(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(Formes.mitjana)), M.accent, mirall = true)
}

/** Canals: tocar-ne un el silencia; «Tots» els torna a activar. `RadioMotor.muts` no és estat de Compose: `v` força el redibuixat. */
@Composable
private fun PanellCanals() {
    var v by remember { mutableIntStateOf(0) }
    val si = RadioMotor.sinte
    v.let { }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Xip(t("Tots"), false, CamiIcones.ACTUALITZA) { RadioMotor.muts.fill(false); RadioMotor.solo = -1; v++ } }
    val cons = App.bib?.emissores?.firstOrNull { it.nom == EstatRepro.joc }?.let { consolaDe(it) }
    val esNes = cons == "NES"
    // NES i SNES: freqüència de vídeo 60 Hz (NTSC) o 50 Hz (PAL), global i en viu; canvia el ritme del codi del joc que crida el so (no les taules PAL del soroll/DMC de la NES)
    if (esNes || cons == "SNES") {
        var pal by remember { mutableStateOf(cat.gbdsmixxx.RegioVideo.pal) }
        FilaInterruptor(t("NES i SNES: 50 Hz (PAL)"), pal, t("Per defecte, 60 Hz (NTSC). Canvia el ritme del codi del joc que crida el so; el canvi és immediat.")) { x -> pal = x; cat.gbdsmixxx.RegioVideo.pal = x; App.env.putInt(cat.gbdsmixxx.RegioVideo.CLAU_ENV, if (x) 1 else 0) }
    }
    if (cons == "GBA") {
        var fg by remember { mutableStateOf(cat.gbdsmixxx.GbaSo.fidel) }
        FilaInterruptor(t("GBA: so original 100 %"), fg, t("Activat, el mesclador és el de la consola, amb el seu gra (hiss) característic. Desactivat (per defecte), se sintetitza amb el nostre mesclador: no hi ha hiss, però pot tenir errors de so. El canvi s'aplica en començar la cançó següent.")) { x -> fg = x; cat.gbdsmixxx.GbaSo.fidel = x; App.env.putInt(cat.gbdsmixxx.GbaSo.CLAU_ENV, if (x) 1 else 0) }
    }
    if (esNes) {
        // sortida de la consola (revisió de fidelitat, 2026-11-23): filtres de la NES (90 Hz i 14 kHz) i guany lineal, sense saturació; global i desat a `nes_sortida_fidel`
        var sf by remember { mutableStateOf(cat.gbdsmixxx.NesApu.sortidaFidel) }
        FilaInterruptor(t("NES: filtres de la consola"), sf, t("Filtres de sortida de la NES (passa-alts de 90 Hz i passa-baix de 14 kHz), sense saturació. Sona més fluix i amb menys greus.")) { x -> sf = x; cat.gbdsmixxx.NesApu.sortidaFidel = x; App.env.putInt("nes_sortida_fidel", if (x) 1 else 0) }
    }
    for (fila in 0..1) Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (c in 0 until 8) {
            val k = fila * 8 + c
            val activa = si != null && si.pistaActiva(k)
            val muda = v.let { RadioMotor.muts[k] }
            val m = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(Formes.petita))
            if (!activa) Box(m.background(M.supAlta.copy(alpha = 0.4f)))
            else Box(m.background(if (muda) M.supAlta else M.accent).premsable(0.9f) { RadioMotor.muts[k] = !RadioMotor.muts[k]; v++ }, contentAlignment = Alignment.Center) {
                Tx("${k + 1}", Tipus.cosFort, if (muda) M.tenu else M.sobreAccent)
            }
        }
    }
}
