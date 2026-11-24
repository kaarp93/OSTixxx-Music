package cat.gbdsmixxx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val TIPUS = listOf("Combat", "Rutes", "Entrenadors", "Ciutats", "Altres")

/** Jocs amb cançons (els que es poden triar). */
fun jocs(b: Biblioteca): List<Emissora> = b.emissores.filter { it.ids.isNotEmpty() }

/** Les cançons visibles d'un joc (sense les ocultes ni, si l'opció és activa, les que no fan bucle: efectes de so i fanfàrries); `cat` filtra per tipus (només als jocs que en tenen), -1 = totes. */
fun cancons(b: Biblioteca, e: Emissora, cat: Int = -1): List<Int> {
    val l = e.ids.filter { !b.ocult(e, it) && (cat < 0 || e.tipus(it) == cat) && !(App.amagaSenseBucle && RadioDades.analisi[b.clauDe(e, it)]?.bucle == 0) }
    if (e.esLlista) return l
    val ordre = ordrePropi(e.nom)                       // ordre triat per l'autor (fletxes ▲▼ de l'àlbum); les cançons que no hi són, al final i en l'ordre del joc
    return if (ordre.isEmpty()) l else l.sortedBy { ordre.indexOf(it).let { k -> if (k < 0) Int.MAX_VALUE else k } }
}

/** Ordre de les cançons d'un àlbum desat al mòbil (`po_<àlbum>`: ids separats per comes); buit = l'ordre del joc. */
// Bloc de càrrega (2026-11-16): l'ordre propi es desa al fitxer binari de dades de l'usuari (`DadesUsuari`, tipus «po»), lligat al CRC de la ROM; el `po_` antic es migra.
fun ordrePropi(nom: String): List<Int> = DadesUsuari.v.str(MagatzemUsuari.T_ORDRE, nom).split(',').mapNotNull { it.toIntOrNull() }
fun desaOrdrePropi(nom: String, ids: List<Int>) { if (ids.isEmpty()) DadesUsuari.v.treu(MagatzemUsuari.T_ORDRE, nom) else DadesUsuari.v.posaStr(MagatzemUsuari.T_ORDRE, nom, null, ids.joinToString(",")) }

/** Tipus de cançó que té aquest joc (els jocs d'«Altres ràdios» i les llistes no en tenen: una sola llista). */
fun tipusDe(b: Biblioteca, e: Emissora): List<Int> =
    if (e.altra || e.esLlista) emptyList() else (0..4).filter { k -> cancons(b, e, k).isNotEmpty() }

/** «BPM clau» d'una cançó que ja s'ha posat en un deck i s'ha reproduït (p. ex. «95  Am 8A»), o null si no. */
fun infoCanco(b: Biblioteca, e: Emissora, id: Int): String? {
    val a = RadioDades.analisi[b.clauDe(e, id)] ?: return null
    if (a.bpm <= 0) return null
    val tn = if (a.tonica >= 0) Tonalitat(a.tonica, a.major, a.confianca / 100.0) else null
    return a.bpm.toString() + (tn?.let { "  " + it.curt() + (if (it.confianca < 0.6) "?" else "") + " " + it.camelot() } ?: "") + (if (a.beats > 0) "  ${a.beats}b" else "")
}

/** Compatibilitat de la tonalitat desada d'una cançó (ja reproduïda en un deck) amb la tonalitat de referència (la de l'altre deck): -1 si no se sap. */
fun compatCanco(b: Biblioteca, e: Emissora, id: Int, tonRef: Tonalitat?): Int {
    val a = RadioDades.analisi[b.clauDe(e, id)] ?: return -1
    if (a.tonica < 0) return -1
    return compatibilitat(Tonalitat(a.tonica, a.major, a.confianca / 100.0), tonRef)
}

/** Llista de jocs (a l'esquerra del reproductor i del triador de cançons). */
@Composable
fun LlistaJocs(b: Biblioteca, jocs: List<Emissora>, triat: Int, color: Color, mod: Modifier, alTriar: (Int) -> Unit) {
    val estat = rememberLazyListState()
    LazyColumn(mod.clip(RoundedCornerShape(10.dp)).background(Col.panell), state = estat, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(jocs.size) { i ->
            val sel = i == triat
            Row(Modifier.fillMaxWidth().height(40.dp).background(if (sel) color.copy(alpha = 0.3f) else Color.Transparent).clickable { alTriar(i) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Tx(jocs[i].vista, if (sel) Col.text else Col.tenu, 13, sel, Modifier.weight(1f))
                Tx("${jocs[i].ids.size}", Col.tenu, 11)
            }
        }
    }
}

/** Cançons de `ids` que contenen el text cercat (sense distingir majúscules); el text buit les deixa totes. */
fun filtra(b: Biblioteca, e: Emissora, ids: List<Int>, text: String): List<Int> =
    if (text.isBlank()) ids else ids.filter { b.etiq(e, it).contains(text.trim(), ignoreCase = true) }

/** Llista de cançons; `destacada` és la que sona; `info` mostra el BPM, la clau i el punt de compatibilitat. Tocar tria; mantenir premut obre el menú de la cançó. */
@Composable
fun LlistaCancons(b: Biblioteca, e: Emissora, ids: List<Int>, destacada: Int, color: Color, info: Boolean, mod: Modifier, tonRef: Tonalitat? = null,
                  alLlarg: (Int) -> Unit = {}, alTriar: (Int, Int) -> Unit) {
    val triar by rememberUpdatedState(alTriar)
    val llarg by rememberUpdatedState(alLlarg)
    LazyColumn(mod.clip(RoundedCornerShape(10.dp)).background(Col.panell), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(ids.size) { i ->
            val id = ids[i]; val sona = id == destacada
            Row(Modifier.fillMaxWidth().height(40.dp).background(if (sona) color.copy(alpha = 0.3f) else Color.Transparent)
                .pointerInput(e.nom, id, i) { detectTapGestures(onTap = { triar(i, id) }, onLongPress = { llarg(id) }) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (info) PuntCompat(compatCanco(b, e, id, tonRef))
                Tx(b.etiq(e, id), if (sona) Col.text else Col.gris, 14, sona, Modifier.weight(1f))
                if (info) infoCanco(b, e, id)?.let { Tx(it, Col.tenu, 12) }
            }
        }
    }
}

/**
 * Menú d'una cançó (en mantenir-la premuda): canviar-li el nom, afegir-la a una llista (o crear-ne una), amagar-la i, dins d'una llista, treure-la o esborrar la llista.
 */
@Composable
fun MenuCanco(b: Biblioteca, e: Emissora, id: Int, alTancar: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) }           // 0 = menú, 1 = canvia nom, 2 = tria llista, 3 = nom de la llista nova
    val clau = b.clauDe(e, id)
    fun fet() { RadioDades.desa(); b.refaLlistes(); App.versio++; alTancar() }
    if (mode == 1) { DemanaText(t("Nom de la cançó"), b.etiq(e, id), { nom -> if (clau.isNotEmpty()) { if (nom.isEmpty()) RadioDades.noms.remove(clau) else RadioDades.noms[clau] = nom }; fet() }, alTancar); return }
    if (mode == 3) { DemanaText(t("Nom de la llista"), "", { nom -> if (nom.isNotEmpty() && clau.isNotEmpty()) RadioDades.llistes.add(RadioDades.Llista(nom, mutableListOf(clau))); fet() }, alTancar); return }
    Box(Modifier.fillMaxSize().background(Color(0xF00B0B0E)).clickable { alTancar() }, contentAlignment = Alignment.Center) {
        Column(Modifier.width(320.dp).clip(RoundedCornerShape(12.dp)).background(Col.panell).clickable { }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tx(b.etiq(e, id), Col.text, 14, true)
            @Composable fun opcio(text: String, color: Color = Col.gris, alToc: () -> Unit) = Btn(Modifier.fillMaxWidth().height(36.dp), color, false, false, alToc) { Tx(text, Col.text, 13, true, centre = true) }
            if (mode == 0) {
                opcio(t("Canvia nom")) { mode = 1 }
                opcio(t("Afegir a llista")) { mode = 2 }
                if (e.esLlista) {
                    opcio(t("Puja")) { RadioDades.llistes.getOrNull(e.llistaIdx)?.let { l -> if (id in 1 until l.cancons.size) java.util.Collections.swap(l.cancons, id, id - 1) }; fet() }
                    opcio(t("Baixa")) { RadioDades.llistes.getOrNull(e.llistaIdx)?.let { l -> if (id in 0 until l.cancons.size - 1) java.util.Collections.swap(l.cancons, id, id + 1) }; fet() }
                    opcio(t("Treu de la llista")) { RadioDades.llistes.getOrNull(e.llistaIdx)?.let { l -> if (id in l.cancons.indices) l.cancons.removeAt(id) }; fet() }
                    opcio(t("Esborra la llista"), Col.vermell) { if (e.llistaIdx in RadioDades.llistes.indices) RadioDades.llistes.removeAt(e.llistaIdx); fet() }
                } else opcio(t("Amaga la cançó"), Col.vermell) { if (clau.isNotEmpty()) RadioDades.ocults.add(clau); fet() }
            } else {
                for ((k, l) in RadioDades.llistes.withIndex()) opcio(l.nom) { if (clau.isNotEmpty() && clau !in l.cancons) l.cancons.add(clau); fet() }
                opcio("+ " + t("Nova llista"), Col.verd) { mode = 3 }
            }
        }
    }
}

/**
 * Triador de cançons per a un deck: jocs a l'esquerra i cançons a la dreta, amb cercador i ordre (original, BPM o compatibilitat de tonalitat amb l'altre deck).
 * En obrir un joc se n'analitzen en segon pla les cançons que falten (BPM i clau). Tocar una cançó l'envia al deck.
 */
@Composable
fun TriaCanco(d: Int, alTancar: () -> Unit, jocsFiltre: List<Emissora>? = null, alTriar: ((Emissora, Int) -> Unit)? = null, titol: String? = null) {
    val b = App.bib ?: return
    Tic.v
    val color = Col.deck(d)
    val llistaJocs = remember(b, App.versio, jocsFiltre?.size) { jocsFiltre ?: jocs(b) }
    var j by remember { mutableIntStateOf(if (alTriar != null) 0 else App.env.int("mix_j", 0)) }
    val e = llistaJocs.getOrNull(j.coerceIn(0, maxOf(0, llistaJocs.size - 1)))
    var cat by remember(j) { mutableIntStateOf(-1) }
    var cerca by remember(j) { mutableStateOf("") }
    var ordre by remember { mutableIntStateOf(App.env.int("mix_ordre", 0)) }      // 0 = original, 1 = BPM, 2 = compatibilitat
    var menuId by remember { mutableIntStateOf(-1) }
    val tonRef = if (alTriar != null) null else Mescla.ref(d).tonalitatEfectiva()
    if (e != null) LaunchedEffect(e.nom, App.versio) { Analitzador.joc(b, e) }
    Box(Modifier.fillMaxSize().background(Color(0xE60B0B0E)).clickable { }) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tx(titol ?: ("DECK " + "ABCD"[d]), color, 16, true)
                CampText(cerca, Modifier.weight(1f).height(36.dp), t("Cerca")) { cerca = it }
                Btn(Modifier.height(36.dp).width(150.dp), color, ordre != 0, false, { ordre = (ordre + 1) % 3; App.env.putInt("mix_ordre", ordre) }) {
                    Tx(t("Ordre") + ": " + when (ordre) { 1 -> "BPM"; 2 -> t("Clau"); else -> "-" }, Col.text, 12, true, centre = true)
                }
                Btn(Modifier.height(36.dp).width(100.dp), Col.gris, false, false, alTancar) { Tx(t("TANCA"), Col.text, 13, true, centre = true) }
            }
            if (e == null) { Tx(t("Cal importar una ROM de GBA o DS"), Col.tenu, 14); return@Column }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LlistaJocs(b, llistaJocs, j, color, Modifier.weight(0.34f).fillMaxHeight()) { i -> j = i; if (alTriar == null) App.env.putInt("mix_j", i) }
                Column(Modifier.weight(0.66f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val tipus = tipusDe(b, e)
                    if (tipus.isNotEmpty()) Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Btn(Modifier.weight(1f).fillMaxHeight(), color, cat < 0, false, { cat = -1 }) { Tx(t("Totes"), Col.text, 12, true, centre = true) }
                        for (k in tipus) Btn(Modifier.weight(1f).fillMaxHeight(), color, cat == k, false, { cat = k }) { Tx(t(TIPUS[k]), Col.text, 12, true, centre = true) }
                    }
                    // Una llista de reproducció es pot fer servir de setlist (guió de la sessió): des del mesclador, «▸1» i «▸2» en posen la cançó següent a cada deck
                    if (e.esLlista) {
                        val es = Setlist.nom == e.vista
                        Btn(Modifier.fillMaxWidth().height(34.dp), Col.verd, es, false, { Setlist.activa(if (es) "" else e.vista) }) { Tx(if (es) t("És la setlist (toca per treure-la)") else t("Fes-la setlist"), Col.text, 12, true, centre = true) }
                    }
                    if (Analitzador.fent == e.nom) Tx(t("Analitzant...") + " ${Analitzador.fets}/${Analitzador.total}", Col.groc, 11)
                    val base = filtra(b, e, cancons(b, e, cat), cerca)
                    val ids = when (ordre) {
                        1 -> base.sortedBy { RadioDades.analisi[b.clauDe(e, it)]?.bpm?.takeIf { v -> v > 0 } ?: 9999 }
                        2 -> base.sortedBy { when (compatCanco(b, e, it, tonRef)) { 2 -> 0; 1 -> 1; 0 -> 2; else -> 3 } }
                        else -> base
                    }
                    LlistaCancons(b, e, ids, (if (alTriar != null) null else Mix.ultima[d])?.takeIf { it.first === e }?.second ?: -1, color, true, Modifier.fillMaxWidth().weight(1f), tonRef, { id -> menuId = id }) { _, id ->
                        if (alTriar != null) alTriar(e, id) else Mix.envia(d, e, id); alTancar()
                    }
                }
            }
        }
        if (menuId >= 0 && e != null) MenuCanco(b, e, menuId) { menuId = -1 }
    }
}
