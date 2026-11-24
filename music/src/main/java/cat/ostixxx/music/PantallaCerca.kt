package cat.ostixxx.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Biblioteca
import cat.gbdsmixxx.cancons
import cat.gbdsmixxx.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Construeix l'índex de la cerca (fora del fil principal): els àlbums i llistes, i les cançons dels àlbums (no les de les llistes, que serien repetides). */
private fun construeixIndex(b: Biblioteca): CercaIndex {
    for (intent in 0..2) {
        try {
            val alb = ArrayList<CercaIndex.Entrada>(); val can = ArrayList<CercaIndex.Entrada>()
            val vistos = HashSet<String>()
            for (e in b.emissores) {
                if (e.ids.isEmpty() || !vistos.add(e.nom)) continue
                val tit = titolAlbum(e)
                alb.add(CercaIndex.Entrada(e.nom, -1, tit, if (e.esLlista) "" else e.vista + " " + nomConsola(consolaDe(e))))
                if (!e.esLlista) for (id in cancons(b, e).distinct()) can.add(CercaIndex.Entrada(e.nom, id, b.etiq(e, id), tit))
            }
            return CercaIndex(alb, can)
        } catch (x: ConcurrentModificationException) { Thread.sleep(50) }          // les dades han canviat mentre es llegien (p. ex. un nom nou): es torna a provar
    }
    return CercaIndex(emptyList(), emptyList())
}

/** Cerca (P3): l'índex es fa en segon pla per biblioteca/versió; cada lletra espera 250 ms (debounce) i la cerca també va en segon pla. */
@Composable
fun Cerca(b: Biblioteca) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var q by FiltresUi::q
    // l'índex només es refà per la TOP 50 si apareix o desapareix (el seu títol no canvia); el mapa d'àlbums sí, perquè la fila en mostra el nombre de cançons
    val hiHaTop = remember(b, App.versio, LlistaTop.versio) { b.emissores.any { it.nom == "LLISTA_TOP50" } }
    val index by produceState<CercaIndex?>(null, b, App.versio, hiHaTop) { value = withContext(Dispatchers.Default) { construeixIndex(b) } }
    var res by remember { mutableStateOf<CercaIndex.Resultat?>(null) }
    LaunchedEffect(q, index) {
        val idx = index ?: return@LaunchedEffect
        if (q.isBlank()) { res = null; return@LaunchedEffect }
        delay(250)
        res = withContext(Dispatchers.Default) { idx.cerca(q) }
    }
    val perNom = remember(b, App.versio, LlistaTop.versio) { b.emissores.associateBy { it.nom } }
    Column(Modifier.fillMaxSize()) {
        Capcalera(t("Cerca"))
        Camp(q, t("Cerca cançons i àlbums"), Modifier.padding(horizontal = 16.dp), icona = CamiIcones.CERCA) { q = it }
        val r = res
        when {
            q.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icona(CamiIcones.CERCA, M.contorn, 64.dp)
                    Tx(t("Cerca cançons i àlbums"), Tipus.cos, M.tenu)
                }
            }
            index == null || r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BarresMusica(M.accent, 28.dp) }
            r.albums.isEmpty() && r.cancons.isEmpty() -> Tx(t("Cap resultat"), Tipus.cos, M.tenu, Modifier.padding(20.dp))
            else -> LazyColumn(Modifier.fillMaxSize(), state = scrollRecordat("pestanya:1"), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
                if (r.albums.isNotEmpty()) item(key = "h_alb", contentType = "cap") { Tx(t("Àlbums"), Tipus.seccio, M.text, Modifier.padding(start = 20.dp, top = 4.dp, bottom = 6.dp)) }
                items(r.albums, key = { "a_" + it.album }, contentType = { "album" }) { en ->
                    val e = perNom[en.album]
                    if (e != null) FilaAlbum(e) { nav.obreAlbum(e.nom) }
                }
                if (r.cancons.isNotEmpty()) item(key = "h_can", contentType = "cap") { Tx(t("Cançons"), Tipus.seccio, M.text, Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)) }
                items(r.cancons, key = { "c_" + it.album + "|" + it.id }, contentType = { "canco" }) { en ->
                    val e = perNom[en.album]
                    if (e != null) FilaCanco(en.text, en.sub, EstatRepro.sonant && EstatRepro.joc == e.nom && EstatRepro.seq == en.id, e, { nav.obreMenu(e.nom, en.id) }) {
                        // l'autor (2026-11-22): continua amb les cançons del mateix àlbum, com si s'hagués tocat des de l'àlbum (abans només sonava aquesta)
                        val ids = cancons(b, e); val i = ids.indexOf(en.id)
                        if (i >= 0) Pl.play(ctx, b, e, ids, i) else Pl.play(ctx, b, e, listOf(en.id), 0)
                    }
                }
            }
        }
    }
}
