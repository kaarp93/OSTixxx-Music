package cat.ostixxx.music

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Biblioteca
import cat.gbdsmixxx.Emissora
import cat.gbdsmixxx.RadioDades
import cat.gbdsmixxx.cancons
import cat.gbdsmixxx.t

private const val D_REVISA = "revisa"
private const val D_MOTIU = "motiu"
private const val D_ESBORRA = "esborra"
private const val D_VALIDA = "valida"

/**
 * Àlbum (o llista): capçalera amb la portada gran sobre un degradat del seu color, títol, dades de libretro, acció de reproduir (color de la portada),
 * aleatori, marques ✓/✗, eines de prova (Revisa, Ordena, Accepta suggeriments, Report) i les pistes amb les etiquetes C/S/J/✗.
 * Lliscar a dreta/esquerra passa a l'àlbum anterior/següent. Els diàlegs (revisió, motiu, esborrar) són a la pila: l'enrere els tanca primer.
 */
@Composable
fun PantallaAlbum(b: Biblioteca, e: Emissora) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var marcaP by remember(e.nom) { mutableIntStateOf(0) }                // refresca marques, etiquetes i ordre
    val ids = remember(b, e, App.versio, marcaP) { cancons(b, e) }          // `e` (no només el nom): la llista TOP 50 refeta és un objecte nou (`LlistaTop`)
    var marca by remember(e.nom) { mutableIntStateOf(Marques.estat(e.nom)) }
    var ordena by remember(e.nom) { mutableStateOf(false) }
    val esJoc = !e.esLlista
    // «Valida del tot» (`Validacio`): un àlbum definitiu al catàleg no mostra els controls de validació (es tornen a obrir des del menú ⋮ d'una pista)
    val ambClau = remember(e) { Validacio.clau(e) != null }
    LaunchedEffect(e, Validacio.versio) { Validacio.llegeix(e) }
    val definitiu = ambClau && Validacio.conegut[e.nom] == true
    val valida = esJoc && !definitiu
    val pendents = remember(e.nom, ids, marcaP) { if (valida && ambClau) Validacio.pendents(e, ids) else 0 }
    var destaca by remember(e.nom) { mutableStateOf(false) }          // ressalta les pistes sense etiqueta després de prémer «Valida del tot» amb pendents
    LaunchedEffect(e.nom) { if (RevisioAuto.activa && ids.isNotEmpty() && esJoc && !definitiu) nav.obreDialeg(D_REVISA) }
    // «Prova compatibilitat»: candidat sense validar (Confirma/Descarta; mentre ho és, C S J ✗ i ✓/✗ continuen visibles)
    val candidat = remember(e.nom, App.versio) { esJoc && ProvaCompatApp.esCandidat(e) }
    // color de la portada d'aquest àlbum (no del que sona): degradat de la capçalera i botó de reproduir
    val llavor = ColorsAlbum.llavor(e)
    val sistemaFosc = isSystemInDarkTheme()
    val pal = remember(llavor, TemaUi.mode, sistemaFosc) { ColorsUi.de(Paletes.genera(llavor, TemaUi.mode, sistemaFosc)) }
    val info = Libretro.infoUi(ctx, e)
    val germans = remember(b, e.esLlista, App.versio, if (e.esLlista) LlistaTop.versio else 0) { ordenaAlbums(b.emissores.filter { it.esLlista == e.esLlista && it.ids.isNotEmpty() }) }
    val unics = remember(ids) { ids.toSet().size == ids.size }
    var arrossega by remember(e.nom) { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().pointerInput(e.nom, germans) {
        detectHorizontalDragGestures(
            onDragStart = { arrossega = 0f },
            onHorizontalDrag = { _, d -> arrossega += d },
            onDragEnd = {
                val i = germans.indexOfFirst { it.nom == e.nom }
                if (i >= 0 && arrossega <= -140f && i + 1 < germans.size) nav.substitueixAlbum(germans[i + 1].nom)
                else if (i >= 0 && arrossega >= 140f && i > 0) nav.substitueixAlbum(germans[i - 1].nom)
            },
        )
    }) {
        LazyColumn(Modifier.fillMaxSize(), state = scrollRecordat("album:" + e.nom), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "cap", contentType = "cap") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val costat = minOf(maxWidth * 0.64f, 280.dp)
                    if (!M.amoled) Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(pal.suau, M.fons))))          // a AMOLED el fons no es tenyeix
                    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Portada(e, Modifier.size(costat).shadow(28.dp, RoundedCornerShape(Formes.gran)), Formes.gran)
                        Spacer(Modifier.size(22.dp))
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Tx(titolAlbum(e), Tipus.titol, M.text, linies = 3)
                            Tx((if (e.esLlista) t("Llista") else nomConsola(consolaDe(e))) + " · " + ids.size + " " + t("cançons"), Tipus.etiqueta, M.tenu)
                            versioIcrc(e).let { if (it.isNotEmpty()) Tx(it, Tipus.etiqueta, M.tenu) }
                            val dades = info?.let { i -> listOf(i.genere, i.saga, i.desenvolupador).filter { it.isNotEmpty() } } ?: emptyList()
                            if (dades.isNotEmpty()) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (x in dades.take(3)) Tx(x, Tipus.micro, M.text, Modifier.clip(RoundedCornerShape(50)).background(M.text.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
            item(key = "accions", contentType = "accions") {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    BotoIcona(CamiIcones.ALEATORI, { Pl.alterna() }, mida = 48.dp, color = if (Pl.aleatori) pal.accent else M.tenu, desc = t("Aleatori"))
                    if (valida) MarcaAlbum(marca, { marca = if (marca == 1) 0 else 1; Marques.posa(e.nom, titolAlbum(e), marca, "") },
                        { if (marca == 2) { marca = 0; Marques.posa(e.nom, titolAlbum(e), 0, "") } else nav.obreDialeg(D_MOTIU) })
                    if (e.esLlista && e.llistaIdx >= 0) BotoIcona(CamiIcones.ESBORRA, { nav.obreDialeg(D_ESBORRA) }, mida = 48.dp, color = M.tenu, desc = t("Esborra la llista"))
                    Spacer(Modifier.weight(1f))
                    BotoPlay(false, { if (ids.isNotEmpty()) Pl.play(ctx, b, e, ids, if (Pl.aleatori) ids.indices.random() else 0) }, mida = 64.dp, color = pal.accent, sobre = pal.sobreAccent)
                }
            }
            if (candidat) item(key = "candidat", contentType = "candidat") { BlocCandidat(ctx, e) { nav.enrere() } }
            if (esJoc) item(key = "eines", contentType = "eines") {
                LazyRow(Modifier.padding(bottom = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (valida && ambClau && !candidat) item(key = "valida") {          // en un candidat, «Confirma el driver» fa aquesta feina
                        val falten = t("Falten {n} pistes per etiquetar").replace("{n}", pendents.toString())
                        Xip(if (pendents == 0) t("Valida del tot") else falten, destaca && pendents > 0, CamiIcones.FET) {
                            if (pendents == 0) nav.obreDialeg(D_VALIDA)
                            else { destaca = true; android.widget.Toast.makeText(ctx, falten, android.widget.Toast.LENGTH_SHORT).show() }
                        }
                    }
                    item(key = "ordena") { Xip(t("Ordena"), ordena, CamiIcones.ORDENA) { ordena = !ordena } }
                    if (ordena) item(key = "restaura") { Xip(t("Restaura l'ordre"), false, CamiIcones.ACTUALITZA) { Marques.restauraOrdre(e.nom); marcaP++ } }
                    if (valida) item(key = "suggeriments") {
                        Xip(t("Accepta suggeriments"), false, CamiIcones.SUGGERIMENT) {
                            val n = Marques.acceptaSuggeriments(e.nom, ids.map { it to b.etiq(e, it) }); marcaP++
                            android.widget.Toast.makeText(ctx, n.toString() + " " + t("pistes etiquetades"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            if (ids.isEmpty()) item(key = "buit", contentType = "nota") { Tx(t("Cap cançó"), Tipus.cos, M.tenu, Modifier.padding(20.dp)) }
            itemsIndexed(ids, key = { i, id -> if (unics) "p_$id" else "p_${i}_$id" }, contentType = { _, _ -> "pista" }) { i, id ->
                FilaPista(b, e, ids, i, id, ordena, valida, marcaP, pal.accent, destaca) { marcaP++ }
            }
        }
        BotoEnrere(Modifier.padding(12.dp)) { nav.enrere() }
        if (nav.te(D_REVISA) && ids.isNotEmpty()) ModeRevisio(b, e, ids, ctx, { marcaP++; marca = Marques.estat(e.nom) }, {
            // joc següent sense provar (mateix ordre i filtre de consola que la llista)
            val pend = grupsPerConsola(b.emissores.filter { !it.esLlista && it.ids.isNotEmpty() && estatProva(it) == 1 && it.nom != e.nom }, filtreConsolaDesat()).flatMap { it.second }
            val seg = pend.firstOrNull()
            if (seg != null) { RevisioAuto.activa = true; nav.substitueixAlbum(seg.nom) }
            else { RevisioAuto.activa = false; nav.tancaDialeg(D_REVISA); android.widget.Toast.makeText(ctx, t("Revisió acabada"), android.widget.Toast.LENGTH_SHORT).show() }
        }) { RevisioAuto.activa = false; nav.tancaDialeg(D_REVISA) }
        if (nav.te(D_VALIDA)) Full(t("Valida del tot") + "?", { nav.tancaDialeg(D_VALIDA) }) {
            Tx(t("Es desa al catàleg com a definitiu i s'amaguen els controls de validació d'aquest àlbum. Els pots tornar a obrir des del menú ⋮ d'una pista."), Tipus.cos, M.tenu, linies = 5)
            Tx(t("Les pistes descartades surten del joc i la resta s'ordena: cançons, jingles i sfx."), Tipus.cos, M.tenu, linies = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotoPle(t("Valida del tot"), CamiIcones.FET, Modifier.weight(1f)) { Validacio.validaDelTot(b, e); marcaP++; nav.tancaDialeg(D_VALIDA) }
                BotoTonal(t("Cancel·la"), null, Modifier.weight(1f)) { nav.tancaDialeg(D_VALIDA) }
            }
        }
        if (nav.te(D_MOTIU)) MotiuDialeg({ nav.tancaDialeg(D_MOTIU) }) { m -> marca = 2; Marques.posa(e.nom, titolAlbum(e), 2, m); nav.tancaDialeg(D_MOTIU) }
        if (nav.te(D_ESBORRA)) Dialeg(t("Esborra la llista") + "?", listOf(
            Triple(CamiIcones.ESBORRA, t("Esborra la llista")) {
                if (e.llistaIdx in RadioDades.llistes.indices) RadioDades.llistes.removeAt(e.llistaIdx)
                RadioDades.desa(); b.refaLlistes(); App.versio++; nav.tancaDialeg(D_ESBORRA); nav.enrere()
            },
            Triple(CamiIcones.TANCA, t("Cancel·la")) { nav.tancaDialeg(D_ESBORRA) },
        )) { nav.tancaDialeg(D_ESBORRA) }
    }
}

/** Una pista de l'àlbum: número (o barres si sona), nom, etiquetes de prova o fletxes d'ordre, i el menú. `esJoc` = es veuen les etiquetes (joc no validat del tot). */
@Composable
private fun FilaPista(b: Biblioteca, e: Emissora, ids: List<Int>, i: Int, id: Int, ordena: Boolean, esJoc: Boolean, marcaP: Int, accent: androidx.compose.ui.graphics.Color, destaca: Boolean, canvia: () -> Unit) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val sona = EstatRepro.sonant && EstatRepro.joc == e.nom && EstatRepro.seq == id
    val etiq = remember(e, id, marcaP, App.versio) { b.etiq(e, id) }
    val nomP = remember(e.nom, id, marcaP) { Marques.nomPista(e.nom, id) }
    val et = remember(e.nom, id, marcaP) { Marques.etiqueta(e.nom, id) }
    val pendent = destaca && esJoc && et == 0          // sense etiqueta després de prémer «Valida del tot»
    Row(Modifier.fillMaxWidth().background(if (sona) M.tint.copy(alpha = 0.45f) else if (pendent) M.error.copy(alpha = 0.14f) else M.fons.copy(alpha = 0f))
        .premsable(0.985f, vibra = false, llarg = { nav.obreMenu(e.nom, id) }) { Pl.play(ctx, b, e, ids, i) }.padding(start = 20.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) { if (sona) BarresMusica(accent) else Tx("${i + 1}", Tipus.etiqueta, M.tenu) }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Tx(if (nomP.isEmpty()) etiq else nomP, Tipus.cosFort, if (sona) accent else M.text, linies = 2)
            if (nomP.isNotEmpty() && nomP != etiq) Tx(etiq, Tipus.petit, M.tenu)
        }
        if (ordena) {
            BotoIcona(CamiIcones.AMUNT, { Marques.mouPista(e.nom, ids, i, -1); canvia() }, mida = 40.dp, icona = 20.dp, color = M.tenu, desc = t("Mou amunt"))
            BotoIcona(CamiIcones.BAIX, { Marques.mouPista(e.nom, ids, i, 1); canvia() }, mida = 40.dp, icona = 20.dp, color = M.tenu, desc = t("Mou avall"))
        } else if (esJoc) {
            val sg = if (et == 0) remember(etiq) { Marques.suggeriment(etiq) } else 0
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                BotoEtiqueta("C", et == 1, sg == 1, M.etCanco) { Marques.posaEtiqueta(e.nom, id, if (et == 1) 0 else 1); canvia() }
                BotoEtiqueta("S", et == 2, sg == 2, M.etSfx) { Marques.posaEtiqueta(e.nom, id, if (et == 2) 0 else 2); canvia() }
                BotoEtiqueta("J", et == 4, false, M.etJingle) { Marques.posaEtiqueta(e.nom, id, if (et == 4) 0 else 4); canvia() }
                BotoEtiqueta("✗", et == 3, sg == 3, M.mal) { Marques.posaEtiqueta(e.nom, id, if (et == 3) 0 else 3); canvia() }
            }
        }
        BotoIcona(CamiIcones.MES, { nav.obreMenu(e.nom, id) }, mida = 40.dp, icona = 20.dp, color = M.tenu, desc = t("Més opcions"))
    }
}
