package cat.ostixxx.music

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.EfectesRadio
import cat.gbdsmixxx.EqGrafic
import cat.gbdsmixxx.LliscadorV
import cat.gbdsmixxx.RadioFreq
import cat.gbdsmixxx.RadioMotor
import cat.gbdsmixxx.t

private val NOMS_EQ = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
private val PRESETS_EQ = listOf("Pla" to FloatArray(10), "Greus" to floatArrayOf(7f, 6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f), "Veu" to floatArrayOf(-4f, -3f, -2f, 0f, 2f, 4f, 4f, 3f, 0f, -2f), "Aguts" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 7f))
private val CONSOLES_FX = listOf("GB", "GBC", "NES", "SNES", "GBA", "DS", "Altres")

private fun pc(x: Float) = "${Math.round(x * 100)} %"

/**
 * Pestanya «Efectes» del reproductor gran (bloc 4; substitueix l'antiga «Equalitzador»): EQ de 10 bandes (el lliscador és la resposta real; «Pla» la torna a zero),
 * reverb i chorus de cada consola (es recorden per consola; per defecte, la de la cançó que sona), estèreo (mono, amplada, balanç) i limitador.
 * `EfectesRadio` no és estat de Compose: `v` (llegit aquí dins) força el redibuixat. Es desa sol 300 ms després de l'últim moviment (`EfectesRadio.programaDesa`).
 */
@Composable
fun PanellEfectes() {
    var v by remember { mutableIntStateOf(0) }
    var cons by remember { mutableStateOf(EfectesRadio.consola.ifEmpty { CONSOLES_FX[0] }) }
    v.let { }
    val fx = EfectesRadio
    fun toca() { fx.programaDesa(); v++ }

    // ---- Equalitzador (global)
    Tx(t("Equalitzador"), Tipus.gran, M.text)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((n, pr) in PRESETS_EQ) Xip(t(n), false, mod = Modifier.weight(1f)) { for (k in 0 until EfectesRadio.BANDES) fx.posaEq(k, pr[k]); v++ }
    }
    val fs = RadioMotor.sinte?.taux ?: RadioFreq.taux
    Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (k in 0 until EfectesRadio.BANDES) Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val db = fx.eq[k]
            Tx((if (db > 0.05f) "+" else "") + String.format(java.util.Locale.US, "%.0f", db), Tipus.micro, M.tenu)
            // salts de 0,5 dB: el zero és exacte (si tot és a zero, l'EQ no toca el so)
            LliscadorV((db + 12f) / 24f, M.accent, true, Modifier.weight(1f).fillMaxWidth(), { f -> fx.posaEq(k, Math.round((f * 24f - 12f) * 2f) / 2f); v++ }, { fx.posaEq(k, 0f); v++ })
            Tx(NOMS_EQ[k], Tipus.micro, if (EqGrafic.CENTRES[k] < 0.45 * fs) M.tenu else M.tenu.copy(alpha = 0.35f))
        }
    }
    if (EqGrafic.CENTRES.any { it >= 0.45 * fs }) Tx(t("A aquesta freqüència de so, les bandes més altes no fan res."), Tipus.petit, M.tenu, linies = 3)

    // ---- Reverb i chorus (per consola)
    Espai(6.dp)
    Tx(t("Reverb i chorus"), Tipus.gran, M.text)
    Tx(t("Es recorden per a cada consola."), Tipus.petit, M.tenu, linies = 2)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in CONSOLES_FX) Xip((if (c == "Altres") t("Altres") else c) + (if (c == fx.consola) " ♪" else ""), c == cons) { cons = c }
    }
    if (cons == "SNES" || cons == "GBA") Tx(t("Aquesta consola ja té l'eco o la reverb del joc: per defecte no se n'hi afegeix més."), Tipus.petit, M.tenu, linies = 3)
    val p = fx.valorsDe(cons)
    FilaLliscador(t("Chorus"), p[EfectesRadio.CHORUS], 0f..1f, pc(p[EfectesRadio.CHORUS])) { x -> fx.posaConsola(cons, EfectesRadio.CHORUS, if (x < 0.01f) 0f else x); v++ }
    if (fx.mono && p[EfectesRadio.CHORUS] > 0f) Tx(t("En mono, el chorus no s'aplica."), Tipus.petit, M.tenu, linies = 2)
    FilaLliscador(t("Reverb"), p[EfectesRadio.REVERB], 0f..1f, pc(p[EfectesRadio.REVERB])) { x -> fx.posaConsola(cons, EfectesRadio.REVERB, if (x < 0.01f) 0f else x); v++ }
    FilaLliscador(t("Sala"), p[EfectesRadio.SALA], 0f..1f, pc(p[EfectesRadio.SALA])) { x -> fx.posaConsola(cons, EfectesRadio.SALA, x); v++ }
    FilaLliscador(t("Amortiment"), p[EfectesRadio.AMORT], 0f..1f, pc(p[EfectesRadio.AMORT])) { x -> fx.posaConsola(cons, EfectesRadio.AMORT, x); v++ }
    BotoTonal(t("Valors per defecte d'aquesta consola"), CamiIcones.ACTUALITZA) { fx.restauraConsola(cons); v++ }

    // ---- Estèreo i limitador (globals)
    Espai(6.dp)
    Tx(t("Estèreo"), Tipus.gran, M.text)
    FilaInterruptor(t("Mono"), fx.mono, t("Barreja els dos canals (un sol auricular, altaveu del mòbil).")) { fx.mono = it; toca() }
    // l'amplada i el balanç s'enganxen al valor neutre (1 i 0 exactes: llavors no toquen el so)
    FilaLliscador(t("Amplada"), fx.amplada, 0f..2f, pc(fx.amplada)) { x -> fx.amplada = if (Math.abs(x - 1f) < 0.04f) 1f else x; toca() }
    val b = fx.balanc
    FilaLliscador(t("Balanç"), b, -1f..1f, if (b == 0f) t("Centre") else if (b < 0f) "◀ " + pc(-b) else pc(b) + " ▶") { x -> fx.balanc = if (Math.abs(x) < 0.04f) 0f else x; toca() }
    FilaInterruptor(t("Limitador"), fx.limita, t("Evita que el so es retalli quan s'apugen les bandes o la reverb.")) { fx.limita = it; toca() }
}

/** Lliscador horitzontal (Material 3) amb el nom a l'esquerra i el valor a la dreta. */
@Composable
private fun FilaLliscador(nom: String, valor: Float, rang: ClosedFloatingPointRange<Float>, text: String, canvia: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Tx(nom, Tipus.cos, M.text, Modifier.weight(1f))
            Tx(text, Tipus.etiqueta, M.tenu)
        }
        Slider(valor, canvia, valueRange = rang, onValueChangeFinished = { EfectesRadio.programaDesa() },
            colors = SliderDefaults.colors(thumbColor = M.accent, activeTrackColor = M.accent, inactiveTrackColor = M.supAlta))
    }
}
