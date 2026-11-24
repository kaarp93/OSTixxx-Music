package cat.gbdsmixxx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TEMPS = listOf(60 to "1 min", 120 to "2 min", 180 to "3 min", 0 to "No")
private val BUCLES = listOf(1 to "1 bucle", 2 to "2 bucles", 3 to "3 bucles", 0 to "No")

/** Pantalla d'ajustos: importar ROM (GBA i DS), importar carpeta, netejar llistes, freqüència de mostreig, temps màxim i bucles del reproductor. */
@Composable
fun Ajustos(alTancar: () -> Unit, triaRoms: () -> Unit, triaCarpeta: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var confirma by remember { mutableStateOf(false) }
    var freq by remember { mutableStateOf(RadioFreq.taux) }
    var temps by remember { mutableStateOf(RadioDades.tempsMax) }
    var bucles by remember { mutableStateOf(RadioDades.bucles) }
    Box(Modifier.fillMaxSize().background(Color(0xF00B0B0E)).clickable { }) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Tx(t("AJUSTOS"), Col.text, 16, true, Modifier.weight(1f))
                Btn(Modifier.height(36.dp).width(110.dp), Col.gris, false, false, alTancar) { Tx(t("TANCA"), Col.text, 13, true, centre = true) }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tx(t("ROMs"), Col.tenu, 12, true)
                    Btn(Modifier.fillMaxWidth().height(44.dp), Col.verd, false, false, triaRoms) { Tx(t("Importar ROM (música)"), Col.text, 14, true, centre = true) }
                    Btn(Modifier.fillMaxWidth().height(44.dp), Col.verd, false, false, triaCarpeta) { Tx(t("Importar carpeta"), Col.text, 14, true, centre = true) }
                    if (!confirma) Btn(Modifier.fillMaxWidth().height(44.dp), Col.vermell, false, false, { confirma = true }) { Tx(t("Netejar llistes"), Col.text, 14, true, centre = true) }
                    else Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn(Modifier.weight(1f).fillMaxHeight(), Col.vermell, true, false, { confirma = false; App.netejaAltres(ctx) }) { Tx(t("Segur?") + " " + t("Sí"), Col.text, 14, true, centre = true) }
                        Btn(Modifier.weight(1f).fillMaxHeight(), Col.gris, false, false, { confirma = false }) { Tx(t("Cancel·la"), Col.text, 14, true, centre = true) }
                    }
                    Tx(t("Només jocs de GBA (m4a) i de DS (SDAT)"), Col.tenu, 11)
                    App.versio
                    if (RadioDades.ocults.isNotEmpty()) Btn(Modifier.fillMaxWidth().height(44.dp), Col.gris, false, false, { RadioDades.ocults.clear(); RadioDades.desa(); App.versio++ }) { Tx(t("Mostra ocultes") + " (${RadioDades.ocults.size})", Col.text, 14, true, centre = true) }
                    if (App.missatge.isNotEmpty()) Tx(App.missatge, Col.groc, 13, true)
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tx(t("Freqüència") + " (" + t("a les cançons noves") + ")", Col.tenu, 12, true)
                    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (f in RadioFreq.OPCIONS) Btn(Modifier.weight(1f).fillMaxHeight(), Col.d2, f == freq, false, {
                            freq = f; RadioFreq.taux = f; App.env.putInt("radio_freq", f)
                        }) { Tx("${f / 1000} kHz", Col.text, 13, true, centre = true) }
                    }
                    Tx(t("Cançons sense bucle (efectes de so, fanfàrries)"), Col.tenu, 12, true)
                    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Btn(Modifier.weight(1f).fillMaxHeight(), Col.d2, App.amagaSenseBucle, false, { App.amagaSenseBucle = true; App.env.putInt("amaga_sense_bucle", 1) }) { Tx(t("Amaga-les"), Col.text, 13, true, centre = true) }
                        Btn(Modifier.weight(1f).fillMaxHeight(), Col.d2, !App.amagaSenseBucle, false, { App.amagaSenseBucle = false; App.env.putInt("amaga_sense_bucle", 0) }) { Tx(t("Mostra-les"), Col.text, 13, true, centre = true) }
                    }
                    Tx(t("Reproductor") + ": " + t("Temps màx"), Col.tenu, 12, true)
                    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((v, n) in TEMPS) Btn(Modifier.weight(1f).fillMaxHeight(), Col.d1, v == temps, false, {
                            temps = v; RadioDades.tempsMax = v; RadioDades.desa()
                        }) { Tx(t(n), Col.text, 13, true, centre = true) }
                    }
                    Tx(t("Reproductor") + ": " + t("Bucles"), Col.tenu, 12, true)
                    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((v, n) in BUCLES) Btn(Modifier.weight(1f).fillMaxHeight(), Col.d1, v == bucles, false, {
                            bucles = v; RadioDades.bucles = v; RadioDades.desa()
                        }) { Tx(t(n), Col.text, 13, true, centre = true) }
                    }
                }
            }
        }
    }
}
