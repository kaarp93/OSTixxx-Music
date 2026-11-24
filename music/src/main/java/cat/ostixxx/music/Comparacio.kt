package cat.ostixxx.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Biblioteca
import cat.gbdsmixxx.EfectesRadio
import cat.gbdsmixxx.Emissora
import cat.gbdsmixxx.EtiquetaDriver
import cat.gbdsmixxx.RadioDades
import cat.gbdsmixxx.RadioMotor
import cat.gbdsmixxx.RegistreDrivers
import cat.gbdsmixxx.t
import kotlinx.coroutines.delay

/**
 * Mode comparació (l'autor, 2026-11-17): a la mateixa posició de la cançó, (1) els drivers del mateix joc (els àlbums de la mateixa ROM: «Auto 1», «Auto 2», «Custom», «Exp»)
 * i (2) amb i sense la cadena d'efectes. El canvi és en viu i sense reiniciar: el motor avança el driver nou en silenci fins on és la cançó i hi fa un fos d'un bloc
 * (`RadioMotor.substitueix`); els efectes es treuen amb un fos (`RadioMotor.senseEfectes`). Dura fins que canvia la cançó.
 */
object Comparacio {
    /** Un driver del joc que sona: el seu àlbum, l'etiqueta («Auto 2»...) i la cançó equivalent (null = aquest driver no la té). */
    class Opcio(val e: Emissora, val etiqueta: String, val id: Int?)

    /** «B · Auto 2» del driver que sona si no és el de la cançó (el posa el panell en triar-lo). Estat de Compose: el reproductor el mostra. */
    var sonant by mutableStateOf<String?>(null)
    private val etiquetes = HashMap<String, String>()

    /** Id de la cançó dins del joc (si sona des d'una llista, el de la cançó al seu joc). */
    fun idReal(b: Biblioteca, origen: Emissora?, seq: Int): Int =
        if (origen != null && origen.esLlista) RadioDades.idDe(b.clauDe(origen, seq)) else seq

    /** Drivers del joc `joc`: els àlbums de consola amb la mateixa ROM (CRC) i el mateix sistema, en l'ordre de la biblioteca. El primer és sempre `joc`. */
    fun opcions(b: Biblioteca, joc: Emissora, idReal: Int): List<Opcio> {
        val crc = joc.crc
        val sis = joc.nom.substringBefore('_')
        val germans = if (crc == null || joc.esLlista || b.clausEstables[joc.nom] == null) listOf(joc)
            else listOf(joc) + b.emissores.filter { it !== joc && !it.esLlista && it.crc == crc && it.nom.substringBefore('_') == sis && b.clausEstables[it.nom] != null }
        val refs = germans.map { e -> b.clausEstables[e.nom]?.substringAfter(':')?.let { RegistreDrivers.perId(it) } }
        val totes = refs.filterNotNull()
        val pos = joc.ids.indexOf(idReal)
        return germans.mapIndexed { i, e ->
            val r = refs[i]
            val et = if (r != null) EtiquetaDriver.nom("", totes, r, true) { k -> t(k) }.removePrefix(" - ").ifEmpty { t(r.tipus.etiqueta) } else e.vista
            val id = if (e === joc) idReal else if (idReal in e.ids) idReal else e.ids.getOrNull(pos)
            Opcio(e, et, id)
        }
    }

    fun lletra(i: Int): String = ('A' + i).toString()

    /** Fa sonar l'opció `i` (0 = la de la cançó) a la mateixa posició. */
    fun tria(b: Biblioteca, ops: List<Opcio>, i: Int) {
        val o = ops.getOrNull(i) ?: return
        val id = o.id ?: return
        if (i == 0) {
            val seq = RadioMotor.seqActual
            val ll = RadioMotor.llista
            RadioMotor.substitueix(null) { ll?.crea?.invoke(seq) ?: o.e.crea(id) }
        } else {
            etiquetes[o.e.nom] = lletra(i) + " · " + o.etiqueta
            RadioMotor.substitueix(o.e.nom) { o.e.crea(id) }
        }
    }

    /** Es crida cada 250 ms (`EstatRepro.sincronitza`). */
    fun sincronitza() {
        val s = RadioMotor.substitut
        val v = if (s == null) null else etiquetes[s] ?: s
        if (sonant != v) sonant = v
    }
}

/** Contingut del full «Compara» del reproductor gran. */
@Composable
fun PanellComparacio() {
    val b = App.bib ?: return
    // ronda d'optimització: abans el bucle feia `v++` i refeia tot el panell cada 120 ms; ara només copia els tres valors del motor que es miren aquí
    // (escriure el mateix valor no refà res), i el panell es refà només quan en canvia algun
    var sub by remember { mutableStateOf(RadioMotor.substitut) }
    var prepEst by remember { mutableStateOf(RadioMotor.preparant) }
    var fxActiu by remember { mutableStateOf(EfectesRadio.algunActiu) }
    LaunchedEffect(Unit) { while (true) { delay(120); EstatRepro.sincronitza(); sub = RadioMotor.substitut; prepEst = RadioMotor.preparant; fxActiu = EfectesRadio.algunActiu } }
    val joc0 = remember(b, EstatRepro.joc, App.versio) { b.emissores.firstOrNull { it.nom == EstatRepro.joc } }
    val joc = remember(joc0, EstatRepro.seq) { jocDeCanco(b, joc0, EstatRepro.seq) }
    if (!EstatRepro.sonant || joc == null) { Tx(t("No sona res"), Tipus.cos, M.tenu); return }
    val idR = remember(joc0, EstatRepro.seq) { Comparacio.idReal(b, joc0, EstatRepro.seq) }
    val ops = remember(joc, idR, App.versio) { Comparacio.opcions(b, joc, idR) }
    val prep = prepEst
    val sonaIdx = if (sub == null) 0 else ops.indexOfFirst { it.e.nom == sub }.coerceAtLeast(0)

    // ---- drivers
    Tx(t("Drivers"), Tipus.etiqueta, M.tenu)
    if (ops.size <= 1) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Formes.mitjana)).border(1.dp, M.contorn, RoundedCornerShape(Formes.mitjana)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Tx(t("Aquest joc només té un driver: no hi ha res a comparar."), Tipus.cos, M.tenu, linies = 3)
        }
    } else {
        ops.forEachIndexed { i, o ->
            val ara = i == sonaIdx
            val esPrep = prep != null && (if (i == 0) prep.isEmpty() else prep == o.e.nom)
            OpcioAB(Comparacio.lletra(i), o.etiqueta + if (i == 0) " (" + t("el de l'àlbum") + ")" else "",
                when { o.id == null -> t("Aquest driver no té aquesta cançó."); esPrep -> t("Preparant: s'avança fins a la mateixa posició..."); else -> b.etiq(o.e, o.id) },
                ara, o.id != null && prep == null) { Comparacio.tria(b, ops, i); EstatRepro.sincronitza() }
        }
        Tx(t("Les cançons de cada driver s'aparellen pel número o per l'ordre de la llista: poden no ser la mateixa."), Tipus.petit, M.tenu, linies = 3)
    }

    // ---- efectes
    Tx(t("Efectes"), Tipus.etiqueta, M.tenu, Modifier.padding(top = 8.dp))
    val sense = EstatRepro.senseEfectes
    OpcioAB("A", t("Amb efectes"), null, !sense, true) { RadioMotor.senseEfectes = false; EstatRepro.sincronitza() }
    OpcioAB("B", t("Sense efectes"), null, sense, true) { RadioMotor.senseEfectes = true; EstatRepro.sincronitza() }
    if (!fxActiu) Tx(t("Ara no hi ha cap efecte actiu: A i B sonen igual."), Tipus.petit, M.tenu, linies = 2)
    Tx(t("La comparació dura fins que canvia la cançó."), Tipus.petit, M.tenu, Modifier.padding(top = 6.dp), 2)
}

/** Una opció A/B: la lletra en un quadrat (ple si és la que sona), el nom i una línia de sota. */
@Composable
private fun OpcioAB(lletra: String, nom: String, sota: String?, sona: Boolean, actiu: Boolean, toc: () -> Unit) {
    val forma = RoundedCornerShape(Formes.mitjana)
    Row(Modifier.fillMaxWidth().clip(forma).background(if (sona) M.suau else Color.Transparent).border(1.dp, if (sona) M.accent else M.contorn, forma)
        .premsable(0.97f, actiu = actiu && !sona) { toc() }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (sona) M.accent else M.supAlta), contentAlignment = Alignment.Center) {
            Tx(lletra, Tipus.gran, if (sona) M.sobreAccent else M.text)
        }
        Column(Modifier.weight(1f)) {
            Tx(nom, Tipus.cosFort, if (actiu || sona) (if (sona) M.sobreSuau else M.text) else M.tenu, linies = 2)
            if (sota != null) Tx(sota, Tipus.petit, if (sona) M.sobreSuau else M.tenu, linies = 2)
        }
        if (sona) Tx(t("Sona"), Tipus.etiqueta, M.sobreSuau)
    }
}
