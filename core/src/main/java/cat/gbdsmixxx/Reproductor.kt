package cat.gbdsmixxx

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Nom del joc que sona al reproductor normal (per marcar la cançó que sona només a la seva llista). */
object Reprod {
    @Volatile var joc = ""

    fun reprodueix(ctx: android.content.Context, b: Biblioteca, e: Emissora, ll: List<Int>, pos: Int) {
        val id = ll.getOrNull(pos) ?: return
        joc = e.nom
        RadioMotor.llista = RadioMotor.Llista(ll, { x -> e.crea(x) }, { x -> b.etiq(e, x) }); RadioMotor.pos = pos
        RadioMotor.icona = Portades.pixels(ctx, e); RadioMotor.portadaNom = Portades.nomArt(e)
        RadioMotor.inicia(id, ctx, b.etiq(e, id)) { e.crea(id) }
    }
}

/** Pestanya del reproductor normal: jocs, cançons per tipus, controls i pistes de la cançó que sona (sense mesclador). */
@Composable
fun Reproductor() {
    val b = App.bib ?: return
    val ctx = LocalContext.current
    Tic.v
    val llistaJocs = remember(b, App.versio) { jocs(b) }
    var j by remember { mutableIntStateOf(App.env.int("radio_j", 0)) }
    val e = llistaJocs.getOrNull(j.coerceIn(0, maxOf(0, llistaJocs.size - 1)))
    var cerca by remember(j) { androidx.compose.runtime.mutableStateOf("") }
    var menuId by remember { mutableIntStateOf(-1) }
    if (e == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Tx(t("Cal importar una ROM de GBA o DS"), Col.tenu, 16) }; return }
    androidx.compose.runtime.LaunchedEffect(e.nom, App.versio) { Analitzador.joc(b, e) }       // BPM, clau i si fa bucle (per amagar els efectes de so)
    val tipus = tipusDe(b, e)
    var cat by remember(j, b) { mutableIntStateOf(tipus.firstOrNull() ?: -1) }
    val ids = filtra(b, e, cancons(b, e, cat), cerca)
    var pos by remember(j, cat, b) { mutableIntStateOf(0) }
    val sonant = RadioMotor.sonant
    val destacada = if (sonant && Reprod.joc == e.nom) RadioMotor.seqActual else -1
    val color = Col.verd

    Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LlistaJocs(b, llistaJocs, j, color, Modifier.weight(0.26f).fillMaxHeight()) { i -> j = i; App.env.putInt("radio_j", i) }
        Column(Modifier.weight(0.40f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (tipus.isNotEmpty()) Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in tipus) Btn(Modifier.weight(1f).fillMaxHeight(), color, cat == k, false, { cat = k }) { Tx(t(TIPUS[k]), Col.text, 12, true, centre = true) }
            }
            CampText(cerca, Modifier.fillMaxWidth().height(34.dp), t("Cerca")) { cerca = it }
            LlistaCancons(b, e, ids, destacada, color, false, Modifier.fillMaxWidth().weight(1f), null, { id -> menuId = id }) { i, _ -> pos = i; Reprod.reprodueix(ctx, b, e, ids, i) }
        }
        Column(Modifier.weight(0.34f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Col.panell).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val bm = Portades.bitmap(ctx, e)
                if (bm != null) Image(bm.asImageBitmap(), null, Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)), filterQuality = FilterQuality.None)
                Column(Modifier.weight(1f)) {
                    Tx(if (sonant) RadioMotor.titol else "-", Col.text, 15, true)
                    Tx(if (!sonant) t("Aturat") else if (RadioMotor.pausat) t("En pausa") else e.vista, Col.tenu, 12)
                }
            }
            Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Btn(Modifier.size(46.dp), Col.gris, false, true, { if (sonant) RadioMotor.salta(-1) }) { Icona(3, Col.text, Modifier.size(20.dp)) }
                Btn(Modifier.size(54.dp), Col.verd, sonant && !RadioMotor.pausat, true, {
                    if (!sonant) Reprod.reprodueix(ctx, b, e, ids, pos) else if (RadioMotor.pausat) RadioMotor.represa() else RadioMotor.pausa()
                }) { Icona(if (sonant && !RadioMotor.pausat) 1 else 0, Col.verd, Modifier.size(22.dp)) }
                Btn(Modifier.size(46.dp), Col.gris, false, true, { if (sonant) RadioMotor.salta(1) }) { Icona(4, Col.text, Modifier.size(20.dp)) }
                Btn(Modifier.size(46.dp), Col.vermell, false, true, { RadioMotor.atura() }) { Icona(2, Col.vermell, Modifier.size(20.dp)) }
            }
            // Pistes de la cançó que sona
            val si = RadioMotor.sinte
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Tx(t("Canals"), Col.tenu, 12, true, Modifier.weight(1f))
                Btn(Modifier.height(28.dp).size(54.dp, 28.dp), Col.gris, false, false, { RadioMotor.muts.fill(false); RadioMotor.solo = -1 }) { Tx("ALL", Col.text, 11, true, centre = true) }
            }
            for (fila in 0..1) {
                Row(Modifier.fillMaxWidth().height(32.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (c in 0 until 6) {
                        val k = fila * 6 + c
                        val activa = si != null && si.pistaActiva(k)
                        val muda = RadioMotor.muts[k]
                        val f = RoundedCornerShape(6.dp)
                        val m = Modifier.weight(1f).fillMaxHeight().clip(f)
                        if (!activa) Box(m.border(1.dp, Col.fosc, f))
                        else Box(m.background(if (muda) androidx.compose.ui.graphics.Color.Transparent else color.copy(alpha = 0.85f), f).border(1.dp, if (muda) Col.tenu else color, f)
                            .clickable { RadioMotor.muts[k] = !RadioMotor.muts[k] }, contentAlignment = Alignment.Center) {
                            Tx("${k + 1}", if (muda) Col.tenu else Col.fons, 13, true, centre = true)
                        }
                    }
                }
            }
            RadioMotor.error?.let { Tx(it, Col.vermell, 11) }
        }
    }
    if (menuId >= 0) MenuCanco(b, e, menuId) { menuId = -1 }
    }
}
