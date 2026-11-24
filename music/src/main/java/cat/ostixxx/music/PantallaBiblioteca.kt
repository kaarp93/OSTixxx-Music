@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package cat.ostixxx.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Biblioteca
import cat.gbdsmixxx.Emissora
import cat.gbdsmixxx.RadioDades
import cat.gbdsmixxx.t

/** Filtres i cerca de les pantalles: viuen fora de la composició perquè en obrir un àlbum i tornar enrere no es perdin. */
internal object FiltresUi {
    var vista by mutableIntStateOf(0)                // 0 = àlbums provats, 1 = llistes, 2 = sense provar, 3 = provats que no van
    var q by mutableStateOf("")
}

/** Revisió contínua: mentre és activa, en obrir el joc següent el mode Revisa hi comença sol. */
internal object RevisioAuto { var activa = false }

/** Fila d'un àlbum o llista: portada, títol i subtítol (tipus o consola · cançons). */
@Composable
fun FilaAlbum(e: Emissora, toc: () -> Unit) {
    Row(Modifier.fillMaxWidth().premsable(0.98f, vibra = false) { toc() }.padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Portada(e, Modifier.size(58.dp), Formes.petita)
        Column(Modifier.weight(1f)) {
            Tx(titolAlbum(e), Tipus.cosFort, M.text, linies = 2)
            Tx((if (e.esLlista) t("Llista") else nomConsola(consolaDe(e))) + " · " + e.ids.size + " " + t("cançons") + versioIcrc(e).let { if (it.isEmpty()) "" else " · $it" }, Tipus.petit, M.tenu, linies = 2)
        }
    }
}

/** Fila d'una cançó (Cerca): portada, títol, àlbum; la que sona es ressalta amb les barres. Polsació llarga = menú. */
@Composable
fun FilaCanco(titol: String, sub: String, sonant: Boolean, e: Emissora?, llarg: () -> Unit, toc: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (sonant) M.tint.copy(alpha = 0.5f) else M.fons.copy(alpha = 0f)).premsable(0.98f, vibra = false, llarg = llarg) { toc() }
        .padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Portada(e, Modifier.size(50.dp), 10.dp)
        Column(Modifier.weight(1f)) { Tx(titol, Tipus.cosFort, if (sonant) M.accent else M.text); Tx(sub, Tipus.petit, M.tenu) }
        if (sonant) BarresMusica(M.accent)
        BotoIcona(CamiIcones.MES, llarg, mida = 40.dp, icona = 20.dp, color = M.tenu, desc = t("Més opcions"))
    }
}

/**
 * Biblioteca: títol gran, pestanyes (Àlbums, Llistes, Sense provar, Provats que no van), accions (nova llista, importar), filtre de consola i
 * la llista agrupada per consola. Els càlculs (estat de prova, ordenació) es fan una vegada per versió (P2); l'scroll es recorda.
 */
@Composable
fun BibliotecaPant(b: Biblioteca, triaFitxers: () -> Unit, triaCarpeta: () -> Unit) {
    val nav = LocalNav.current
    var vista by FiltresUi::vista
    var nova by remember { mutableStateOf(false) }
    var nom by remember { mutableStateOf("") }
    val reals = remember(b, App.versio) { b.emissores.filter { !it.esLlista && it.ids.isNotEmpty() }.distinctBy { it.nom } }
    val llistes = remember(b, App.versio, LlistaTop.versio) { b.emissores.filter { it.esLlista }.distinctBy { it.nom } }
    val llista = if (vista != 1) reals else llistes
    var selCons by remember { mutableStateOf(filtreConsolaDesat()) }
    val selEf = if (selCons.isNotEmpty() && llista.none { consolaDe(it) == selCons }) "" else selCons
    if (vista != 0 && vista != 1) vista = 0          // ja no hi ha les pestanyes «Sense provar» ni «Provats que no van»: es veuen tots els jocs sempre
    val grups = remember(reals, vista, selEf) { if (vista == 1) emptyList() else grupsPerConsola(reals, selEf) }
    LazyColumn(Modifier.fillMaxSize(), state = scrollRecordat("pestanya:2"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "cap", contentType = "cap") {
            Capcalera(t("Biblioteca")) {
                BotoIcona(CamiIcones.AFEGEIX, { nova = !nova }, mida = 48.dp, fons = M.sup, desc = t("Nova llista"))
            }
        }
        item(key = "vistes", contentType = "xips") {
            val noms = listOf(t("Àlbums"), t("Llistes"))
            Pivot(noms, vista) { vista = it }
        }
        item(key = "accions", contentType = "xips") {
            LazyRow(Modifier.padding(top = 10.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "nova") { Xip(t("Nova llista"), nova, CamiIcones.LLISTA_AFEGEIX) { nova = !nova } }
            }
        }
        if (nova) item(key = "nova_llista", contentType = "camp") {
            Row(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Camp(nom, t("Nom de la llista"), Modifier.weight(1f)) { nom = it }
                BotoPle(t("D'acord"), CamiIcones.FET) {
                    if (nom.isNotBlank()) { RadioDades.llistes.add(RadioDades.Llista(nom.trim(), mutableListOf())); RadioDades.desa(); b.refaLlistes(); App.versio++ }
                    nom = ""; nova = false; vista = 1
                }
            }
        }
        if (vista != 1) item(key = "consoles", contentType = "xips") { FiltreConsola(llista, selEf) { selCons = it; desaFiltreConsola(it) } }
        if (vista == 1) {
            if (llista.isEmpty()) item(key = "buit", contentType = "nota") { Tx(t("Cap llista"), Tipus.cos, M.tenu, Modifier.padding(20.dp)) }
            items(llista, key = { "l_" + it.nom }, contentType = { "album" }) { e -> FilaAlbum(e) { nav.obreAlbum(e.nom) } }
        } else {
            if (grups.all { it.second.isEmpty() }) item(key = "buit", contentType = "nota") { Tx(t("Cap joc"), Tipus.cos, M.tenu, Modifier.padding(20.dp)) }
            for ((cons, grup) in grups) {
                if (selEf.isEmpty() && grup.isNotEmpty()) item(key = "c_$cons", contentType = "consola") {
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tx(nomConsola(cons), Tipus.gran, M.text)
                        Tx("" + grup.size, Tipus.etiqueta, M.sobreSuau, Modifier.clip(RoundedCornerShape(50)).background(M.suau).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                items(grup, key = { "a_" + it.nom }, contentType = { "album" }) { e -> FilaAlbum(e) { nav.obreAlbum(e.nom) } }
            }
        }
    }
}
