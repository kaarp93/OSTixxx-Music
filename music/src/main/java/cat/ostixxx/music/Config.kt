package cat.ostixxx.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Biblioteca
import cat.gbdsmixxx.Emissora
import cat.gbdsmixxx.Informe
import cat.gbdsmixxx.RadioDades
import cat.gbdsmixxx.RadioFreq
import cat.gbdsmixxx.t

/**
 * Ajustos d'OSTixxx Music (l'autor, 2026-11-17): seccions en targetes. Tema (sistema, clar, fosc, AMOLED; el color segueix la portada), visualitzador,
 * reproducció, biblioteca (amb el bloc de càrrega: accés, revisa, refer, mode antic), libretro, reports, extractor de proves i build.
 */
@Composable
fun Config(tanca: () -> Unit, triaFitxers: () -> Unit, triaCarpeta: () -> Unit, triaDrivers: () -> Unit) {
    val ctx = LocalContext.current
    var confirma by remember { mutableStateOf(false) }
    var freq by remember { mutableIntStateOf(RadioFreq.taux) }
    App.versio
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = scrollRecordat("ajustos"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "cap") {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotoEnrere { tanca() }
                    Tx(t("Ajustos"), Tipus.display, M.text)
                }
            }
            item(key = "tema") {
                Targeta(t("Tema"), CamiIcones.TEMA) {
                    val modes = listOf(ModeTema.SISTEMA, ModeTema.CLAR, ModeTema.FOSC, ModeTema.AMOLED)
                    Segmentat(listOf(t("Sistema"), t("Clar"), t("Fosc"), t("AMOLED")), modes.indexOf(TemaUi.mode).coerceAtLeast(0)) { i -> TemaUi.posa(modes[i]) }
                    Tx(t("Els colors segueixen la portada del joc que sona."), Tipus.petit, M.tenu, linies = 3)
                    // mostra de la paleta d'ara
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in listOf(M.accent, M.suau, M.supAlta, M.text)) Box(Modifier.size(28.dp).clip(CircleShape).background(c))
                    }
                }
            }
            item(key = "visual") { Targeta(t("Visualitzador"), CamiIcones.VISUAL) { OpcionsVisual(true) } }
            item(key = "repro") {
                Targeta(t("Reproducció"), CamiIcones.BUCLE) {
                    OpcionsBucles()
                    Tx(t("Freqüència"), Tipus.etiqueta, M.tenu)
                    Segmentat(RadioFreq.OPCIONS.map { "${it / 1000} kHz" }, RadioFreq.OPCIONS.indexOf(freq).coerceAtLeast(0)) { i -> freq = RadioFreq.OPCIONS[i]; RadioFreq.taux = freq; App.env.putInt("radio_freq", freq) }
                    FilaInterruptor(t("Amaga les cançons sense bucle"), App.amagaSenseBucle) { v -> App.amagaSenseBucle = v; App.env.putInt("amaga_sense_bucle", if (v) 1 else 0) }
                }
            }
            item(key = "biblioteca") {
                Targeta(t("Biblioteca"), CamiIcones.BIBLIOTECA) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Xip(t("Importa fitxers"), false, CamiIcones.FITXER) { triaFitxers() } }
                        item { Xip(t("Importa carpeta"), false, CamiIcones.CARPETA) { triaCarpeta() } }
                        item { Xip(t("Importa drivers"), false, CamiIcones.DESCARREGA) { triaDrivers() } }
                    }
                    BlocCarrega(ctx)          // [bloc de càrrega] accés a tots els fitxers, revisa les carpetes, refer biblioteca, mode antic
                    BlocProvaCompat(ctx)          // només el botó «Importa una ROM per analitzar» (amb el progrés i «Cancel·la» mentre n'hi ha una en curs)
                    if (RadioDades.ocults.isNotEmpty()) BotoTonal(t("Mostra les ocultes"), CamiIcones.AMAGA, Modifier.fillMaxWidth()) { RadioDades.ocults.clear(); RadioDades.desa(); App.versio++ }
                    var f1 by remember { mutableStateOf(cat.gbdsmixxx.SnesSpc.fixF1) }
                    FilaInterruptor(t("SNES: ports a l'arrencada (fix \$F1)"), f1) { v -> f1 = v; cat.gbdsmixxx.SnesSpc.fixF1 = v; App.env.putInt("snes_f1_fix", if (v) 1 else 0) }
                    // [drivers 2026-11-16] interruptor: «Joc - Custom/Auto/Exp» als àlbums dels jocs amb més d'un driver
                    var etq by remember { mutableStateOf(cat.gbdsmixxx.EtiquetaDriver.mostra) }
                    FilaInterruptor(t("Tipus de driver als àlbums"), etq) { v -> etq = v; cat.gbdsmixxx.EtiquetaDriver.mostra = v; App.env.putInt(cat.gbdsmixxx.EtiquetaDriver.CLAU_ENV, if (v) 1 else 0); App.carrega(ctx) }
                    BotoTonal(t("Analitza tots els jocs"), CamiIcones.VISUAL, Modifier.fillMaxWidth()) { App.bib?.let { b2 -> cat.gbdsmixxx.AnalisiTotal.engega(b2) { h -> android.os.Handler(android.os.Looper.getMainLooper()).post(h) } } }
                    if (!confirma) BotoTonal(t("Neteja les llistes importades"), CamiIcones.ESBORRA, Modifier.fillMaxWidth()) { confirma = true }
                    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotoPle(t("Segur?") + " " + t("Sí"), CamiIcones.FET, Modifier.weight(1f)) { confirma = false; App.netejaAltres(ctx) }
                        BotoTonal(t("Cancel·la"), null, Modifier.weight(1f)) { confirma = false }
                    }
                    if (App.missatge.isNotEmpty()) Tx(App.missatge, Tipus.etiqueta, M.accent, linies = 3)
                }
            }
            item(key = "copia") { Targeta(t("Còpia de seguretat"), CamiIcones.COPIA) { BlocCopia(ctx) } }          // [catàleg 2026-11-22] .ostx: exporta / importa
            item(key = "libretro") {
                Targeta(t("Dades i portades (libretro)"), CamiIcones.DESCARREGA) {
                    Libretro.versio
                    var dades by remember { mutableStateOf(Libretro.dadesActives()) }
                    var art by remember { mutableStateOf(Libretro.artActiu()) }
                    Tx(t("Baixa de libretro el gènere, la saga i el desenvolupador dels jocs, i les portades. Només si ho actives; després va sense connexió."), Tipus.petit, M.tenu, linies = 4)
                    FilaInterruptor(t("Baixa dades dels jocs"), dades) { v -> dades = v; Libretro.posaDades(v) }
                    FilaInterruptor(t("Baixa portades"), art) { v -> art = v; Libretro.posaArt(v) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotoTonal(t("Actualitza ara"), CamiIcones.ACTUALITZA, Modifier.weight(1f)) { App.bib?.let { Libretro.actualitza(ctx, it.reals().filter { e -> !e.esLlista }) } }
                        BotoTonal(t("Esborra les portades"), CamiIcones.ESBORRA, Modifier.weight(1f)) { Libretro.esborraArt(ctx) }
                    }
                    if (Libretro.estat.isNotEmpty()) Tx(Libretro.estat, Tipus.petit, M.text, linies = 3)
                    Tx(t("Dades de libretro-database (CC BY-SA 4.0) i portades de libretro-thumbnails."), Tipus.micro, M.tenu, linies = 3)
                }
            }
            item(key = "build") {
                Targeta(t("Build"), CamiIcones.INFO) { Tx(BuildConfig.BUILD_ID, Tipus.cos, M.text, linies = 2) }
            }
        }
    }
}

/** Menú de la polsació llarga d'una cançó, a l'arrel (des de l'àlbum, la cerca...): s'obre i es tanca per la pila de navegació. */
@Composable
fun MenuCancoArrel(b: Biblioteca) {
    val nav = LocalNav.current
    if (!nav.te(EstatNav.MENU)) return
    val (nom, id) = nav.menuCanco ?: return
    val e = remember(b, nom, App.versio, LlistaTop.versio) { b.emissores.firstOrNull { it.nom == nom } } ?: return
    MenuCancoMusic(b, e, id) { nav.tancaMenu() }
}

/** Menú d'una cançó: editar el títol, afegir-la a una llista, treure-la de la llista o eliminar-la del joc (amagar-la). */
@Composable
fun MenuCancoMusic(b: Biblioteca, e: Emissora, id: Int, tanca: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) }                // 0 opcions, 1 editar, 2 triar llista, 3 llista nova
    val clau = b.clauDe(e, id)
    var titol by remember { mutableStateOf(b.etiq(e, id)) }
    fun fet() { RadioDades.desa(); b.refaLlistes(); App.versio++; tanca() }
    when (mode) {
        1 -> Full(t("Edita les etiquetes"), tanca) {
            Tx(t("Títol de la cançó"), Tipus.etiqueta, M.tenu)
            Camp(titol, t("Títol de la cançó")) { titol = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotoPle(t("Desa"), CamiIcones.FET, Modifier.weight(1f)) { if (titol.isBlank()) RadioDades.noms.remove(clau) else RadioDades.noms[clau] = titol.trim(); fet() }
                BotoTonal(t("Cancel·la"), null, Modifier.weight(1f)) { mode = 0 }
            }
        }
        2 -> Full(t("Tria una llista"), tanca) {
            for ((k, l) in RadioDades.llistes.withIndex()) FilaOpcio(CamiIcones.NOTA, l.nom) { RadioDades.llistes[k].cancons.add(clau); fet() }
            FilaOpcio(CamiIcones.AFEGEIX, t("Nova llista"), M.accent) { titol = ""; mode = 3 }
        }
        3 -> Full(t("Nova llista"), tanca) {
            Camp(titol, t("Nom de la llista")) { titol = it }
            BotoPle(t("D'acord"), CamiIcones.FET, Modifier.fillMaxWidth()) { if (titol.isNotBlank()) { RadioDades.llistes.add(RadioDades.Llista(titol.trim(), mutableListOf(clau))); fet() } }
        }
        else -> Full(b.etiq(e, id), tanca) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Portada(e, Modifier.size(48.dp), Formes.petita)
                Tx(titolAlbum(e), Tipus.etiqueta, M.tenu, Modifier.weight(1f), 2)
            }
            FilaOpcio(CamiIcones.EDITA, t("Edita les etiquetes")) { mode = 1 }
            FilaOpcio(CamiIcones.LLISTA_AFEGEIX, t("Afegeix a una llista")) { mode = 2 }
            if (e.esLlista) FilaOpcio(CamiIcones.TANCA, t("Treu de la llista")) { RadioDades.llistes.getOrNull(e.llistaIdx)?.cancons?.let { c -> if (id in c.indices) c.removeAt(id) }; fet() }
            FilaOpcio(CamiIcones.AMAGA, t("Elimina del joc (amaga)"), M.error) { RadioDades.ocults.add(clau); fet() }
            // «Valida del tot» desfet: discret, només aquí i només si l'àlbum és definitiu (l'autor, 2026-11-22: sense «punt de no retorn»)
            if (!e.esLlista && Validacio.conegut[e.nom] == true) FilaOpcio(CamiIcones.REVISA, t("Reobre la validació"), M.tenu) { Validacio.reobre(e); tanca() }
            if (!e.esLlista) { val desc = remember(e) { Validacio.descartadesAmagades(b, e) }
                if (desc.isNotEmpty()) FilaOpcio(CamiIcones.ACTUALITZA, t("Recupera les pistes descartades") + " (" + desc.size + ")", M.tenu) { Validacio.recuperaDescartades(b, e); tanca() } }
        }
    }
}

/** L'informe d'importació ja no es mostra (l'autor, 2026-11-24: cap tipus de log). */
@Composable
fun InformeMusic() { }
