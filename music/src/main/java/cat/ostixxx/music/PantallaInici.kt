package cat.ostixxx.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Biblioteca
import cat.gbdsmixxx.Comptes
import cat.gbdsmixxx.Emissora
import cat.gbdsmixxx.RadioDades
import cat.gbdsmixxx.t

/** Dades de l'Inici calculades una sola vegada per biblioteca i versió (P2), no a cada dibuix. */
private class DadesInici(b: Biblioteca) {
    val reals: Map<String, Emissora> = b.emissores.filter { !it.esLlista }.associateBy { it.nom }
    val albums = b.emissores.filter { !it.esLlista && it.ids.isNotEmpty() }
    private val autos = b.emissores.filter { it.esLlista && it.autoClaus != null }
    val propies = b.emissores.filter { it.esLlista && it.autoClaus == null }
    val top = autos.firstOrNull { it.nom == "LLISTA_TOP50" }
    val mes = run { val am = albums.toHashSet(); Comptes.albums().mapNotNull { (n, _) -> b.perNom(n)?.takeIf { it in am } }.distinct().take(12) }
    val grups = listOf("Sagues", "Empreses", "Gèneres", "Consoles").map { g -> g to autos.filter { it.autoGrup == g } }.filter { it.second.isNotEmpty() }
}

/**
 * Inici (l'autor, 2026-11-16: sense botons ni xips; carrusels de llistes automàtiques amb mosaic de 4 portades): salutació gran, «Continua escoltant»
 * amb el color de la portada, «Més reproduïdes» i els carrusels de sagues, empreses, gèneres, consoles i llistes pròpies. L'scroll es recorda.
 */
@Composable
fun Inici(b: Biblioteca, triaFitxers: () -> Unit, triaCarpeta: () -> Unit) {
    val nav = LocalNav.current
    val d = remember(b, App.versio, LlistaTop.versio) { DadesInici(b) }          // `LlistaTop` puja després de refer la TOP 50 (abans `Comptes.versio`, que arribava abans de refer-la)
    val h = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    Column(Modifier.fillMaxSize()) {
        Capcalera(t(if (h < 6) "Bona nit" else if (h < 13) "Bon dia" else if (h < 20) "Bona tarda" else "Bona nit")) {
            BotoIcona(CamiIcones.AJUSTOS, { nav.obreAjustos() }, mida = 48.dp, fons = M.sup, desc = t("Ajustos"))
        }
        if (d.albums.isEmpty()) BuitImporta(triaFitxers, triaCarpeta)
        else LazyColumn(Modifier.fillMaxSize(), state = scrollRecordat("pestanya:0"), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            if (EstatRepro.sonant) item(key = "continua", contentType = "continua") { ContinuaEscoltant(b, d) }
            if (d.top != null || d.mes.isNotEmpty()) item(key = "mes", contentType = "seccio") {
                Seccio(t("Més reproduïdes")) {
                    val l = listOfNotNull(d.top) + d.mes
                    Carrusel(l) { e -> nav.obreAlbum(e.nom) }
                }
            }
            for ((g, l) in d.grups) item(key = "g_$g", contentType = "seccio") { Seccio(t(g)) { Carrusel(l) { e -> nav.obreAlbum(e.nom) } } }
            if (d.propies.isNotEmpty()) item(key = "propies", contentType = "seccio") { Seccio(t("Llistes")) { Carrusel(d.propies) { e -> nav.obreAlbum(e.nom) } } }
            if (!Libretro.dadesActives()) item(key = "libretro", contentType = "nota") {
                Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().superficie(M.sup, RoundedCornerShape(Formes.mitjana)).premsable(0.98f) { nav.obreAjustos() }.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icona(CamiIcones.INFO, M.accent, 22.dp)
                    Tx(t("Activa «Baixa dades dels jocs» a Ajustos per tenir més llistes automàtiques (gèneres, empreses i sagues amb dades de libretro)."), Tipus.petit, M.tenu, Modifier.weight(1f), 4)
                }
            }
        }
    }
}

/** Targeta gran de la cançó que sona, tenyida amb el color de la portada (dinàmic). */
@Composable
private fun ContinuaEscoltant(b: Biblioteca, d: DadesInici) {
    val nav = LocalNav.current
    val joc = EstatRepro.joc; val seq = EstatRepro.seq
    val actual = remember(b, joc, seq, d) { jocDeCanco(b, b.emissores.firstOrNull { it.nom == joc }, seq) } ?: return
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(Formes.gran))
        .background(Brush.horizontalGradient(listOf(M.tint, M.sup))).then(if (M.amoled) Modifier.border(1.dp, M.contorn, RoundedCornerShape(Formes.gran)) else Modifier).premsable(0.97f) { nav.obreJugador() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Portada(actual, Modifier.size(72.dp), Formes.petita)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Tx(t("Continua escoltant"), Tipus.micro, M.sobreSuau)
            Tx(EstatRepro.titol, Tipus.gran, M.text)
            Tx(titolAlbum(actual), Tipus.petit, M.tenu)
        }
        Visualitzador(Modifier.width(44.dp).height(32.dp), M.accent, compacte = true)
    }
}

/** Secció de l'Inici: títol gran i, a sota, el carrusel. */
@Composable
private fun Seccio(titol: String, contingut: @Composable () -> Unit) {
    Column {
        Tx(titol, Tipus.seccio, M.text, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
        contingut()
    }
}

@Composable
private fun Carrusel(l: List<Emissora>, obre: (Emissora) -> Unit) {
    val b = App.bib ?: return
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(l.distinctBy { it.nom }, key = { _, e -> e.nom }, contentType = { _, _ -> "targeta" }) { i, e -> EntradaAnimada(i) { TargetaLlista(b, e) { obre(e) } } }
    }
}

/** Targeta d'una llista (o d'un àlbum): mosaic de 4 portades dels jocs que hi ha dins, el nom i el nombre de cançons. */
@Composable
private fun TargetaLlista(b: Biblioteca, e: Emissora, obre: () -> Unit) {
    val portades = remember(b, e, App.versio) {
        if (!e.esLlista) listOf(e) else {
            val claus = e.autoClaus ?: RadioDades.llistes.getOrNull(e.llistaIdx)?.cancons ?: emptyList()
            val vistos = LinkedHashSet<String>(); val out = ArrayList<Emissora>()
            for (k in claus) { val n = RadioDades.emissoraDe(k); if (vistos.add(n)) { b.perNom(n)?.let { out.add(it) }; if (out.size >= 4) break } }
            out
        }
    }
    Column(Modifier.width(156.dp).premsable(0.95f, vibra = false) { obre() }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(156.dp).clip(RoundedCornerShape(Formes.mitjana)).background(M.sup)) {
            if (portades.size >= 4) Column { for (r in 0..1) Row { for (c in 0..1) Portada(portades[r * 2 + c], Modifier.size(78.dp), 0.dp) } }
            else if (portades.isNotEmpty()) Portada(portades[0], Modifier.size(156.dp), 0.dp)
        }
        Tx(titolAlbum(e), Tipus.cosFort, M.text, linies = 2)
        Tx(e.ids.size.toString() + " " + t("cançons"), Tipus.petit, M.tenu)
    }
}

@Composable
fun BuitImporta(triaFitxers: () -> Unit, triaCarpeta: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(Formes.enorme)).background(M.suau), contentAlignment = Alignment.Center) { Icona(CamiIcones.NOTA, M.sobreSuau, 48.dp) }
        Tx(t("Importa ROMs o fitxers de música per començar"), Tipus.gran, M.text, linies = 3)
        Tx(t("Es fa des d'Ajustos."), Tipus.cos, M.tenu, linies = 2)
    }
}
