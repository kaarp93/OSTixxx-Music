package cat.ostixxx.music

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.App
import cat.gbdsmixxx.t

/**
 * Marques de prova per joc (àlbum): 1 = va bé, 2 = no va (amb el motiu). Es desen al fitxer binari de dades de l'usuari (`DadesUsuari`, bloc de càrrega 2026-11-16),
 * lligades a la ROM (CRC + driver) i no al nom de l'àlbum; les claus `marca_*`, `pm_*`, `pt_*`, `pe_*`, `pn_*` de les preferències d'abans es migren una vegada i no s'esborren.
 * L'extractor d'Ajustos les recull en text.
 */
object Marques {
    private val du get() = cat.gbdsmixxx.DadesUsuari.v
    private const val M = cat.gbdsmixxx.MagatzemUsuari.T_MARCA
    private const val MOT = cat.gbdsmixxx.MagatzemUsuari.T_MOTIU
    private const val TIT = cat.gbdsmixxx.MagatzemUsuari.T_TITOL
    private const val PM = cat.gbdsmixxx.MagatzemUsuari.T_PM
    private const val PT = cat.gbdsmixxx.MagatzemUsuari.T_PT
    private const val PE = cat.gbdsmixxx.MagatzemUsuari.T_PE
    private const val PN = cat.gbdsmixxx.MagatzemUsuari.T_PN
    /** Àlbums marcats (✓ o ✗), en l'ordre en què es van marcar. */
    fun noms(): List<String> = du.albums(setOf(M)) { it is Int && it != 0 }.map { cat.gbdsmixxx.DadesUsuari.nom(it) }
    fun estat(nom: String) = du.int(M, nom)
    fun motiu(nom: String) = du.str(MOT, nom)
    fun titol(nom: String) = du.str(TIT, nom, null, nom)
    fun posa(nom: String, titol: String, estat: Int, motiu: String) {
        if (estat == 0) du.treu(M, nom) else du.posaInt(M, nom, null, estat)
        if (estat == 2) du.posaStr(MOT, nom, null, motiu) else du.treu(MOT, nom)
        du.posaStr(TIT, nom, null, titol); Reports.desaAlbum(nom)
        if (estat == 1) cataloga(nom)          // catàleg definitiu: un joc marcat «Bé» que té driver hi entra (sense validar)
        val etq = if (nom.startsWith("SNES_")) " [fix \$F1: " + (if (cat.gbdsmixxx.SnesSpc.fixF1) "sí" else "no") + "]" else ""
        cat.gbdsmixxx.Informe.marca(App.env.ctx, titol, when (estat) { 1 -> "✓ $titol - va bé$etq"; 2 -> "✗ $titol - no perquè \"$motiu\"$etq"; else -> null })
    }
    /** Catàleg definitiu (2026-11-22): desa l'àlbum (si és de consola amb driver) en un fil apart. Només els marcats ✓. */
    private fun cataloga(nom: String) {
        if (estat(nom) != 1) return
        val e = App.bib?.reals()?.firstOrNull { it.nom == nom } ?: return
        try { cat.gbdsmixxx.CatalegConsoles.catalogaEnFons(App.env.ctx, e) } catch (x: Throwable) { }
    }
    /** Esborra totes les marques (àlbums i pistes), els motius i les etiquetes. L'ordre i els noms de pista es conserven (com abans). */
    fun esborraTot() = du.esborraTipus(setOf(M, MOT, TIT, PM, PT, PE))
    /** Marca d'una pista d'un joc: 0 = sense marca, 1 = ✓ (sona bé), 2 = ✗ (no sona bé). Surt al report de l'àlbum. */
    fun pista(nom: String, id: Int) = du.int(PM, nom, id)
    fun posaPista(nom: String, id: Int, v: Int) { du.posaInt(PM, nom, id, v) }
    /** Tipus d'una pista: 0 = sense dir, 1 = C (cançó), 2 = S (sfx). Surt al report. */
    fun tipus(nom: String, id: Int) = du.int(PT, nom, id)
    fun posaTipus(nom: String, id: Int, v: Int) { du.posaInt(PT, nom, id, v) }

    // ---- Etiqueta única per pista (l'autor, 2026-11-15): 0 = sense, 1 = Cançó, 2 = Sfx, 3 = Descarta, 4 = Jingle. Cançó, Sfx i Jingle ja volen dir que funciona.
    /** Àlbums amb alguna pista etiquetada o amb nom (per al report de tots els jocs). */
    fun albumsEtiquetats(): List<String> = du.albums(setOf(PE, PN)) { (it is Int && it != 0) || (it is String && it.isNotBlank()) }.map { cat.gbdsmixxx.DadesUsuari.nom(it) }
    /** Etiqueta d'una pista; si no n'hi ha de nova es tradueix l'antiga (✗ = Descarta, C = Cançó, S = Sfx). */
    fun etiqueta(nom: String, id: Int): Int {
        val v = du.int(PE, nom, id, -1)
        if (v >= 0) return v
        return if (pista(nom, id) == 2) 3 else when (tipus(nom, id)) { 1 -> 1; 2 -> 2; else -> 0 }
    }
    /** Nom que l'autor dona a una pista en provar-la; buit = sense nom. */
    fun nomPista(nom: String, id: Int) = du.str(PN, nom, id)
    fun posaNomPista(nom: String, id: Int, v: String) { if (v.isBlank()) du.treu(PN, nom, id) else du.posaStr(PN, nom, id, v.trim()); Reports.desaAlbum(nom); cataloga(nom) }
    fun posaEtiqueta(nom: String, id: Int, v: Int) { du.posaInt(PE, nom, id, v); Reports.desaAlbum(nom); cataloga(nom) }
    /** Mou la pista `i` de la llista `ids` una posició (`delta` = -1 amunt, +1 avall) i desa l'ordre sencer de l'àlbum. */
    fun mouPista(nom: String, ids: List<Int>, i: Int, delta: Int) {
        val j = i + delta; if (i !in ids.indices || j !in ids.indices) return
        val l = ids.toMutableList(); val x = l[i]; l[i] = l[j]; l[j] = x
        cat.gbdsmixxx.desaOrdrePropi(nom, l); Reports.desaAlbum(nom); cataloga(nom)
    }
    fun restauraOrdre(nom: String) { cat.gbdsmixxx.desaOrdrePropi(nom, emptyList()); Reports.desaAlbum(nom); cataloga(nom) }
    /** Proposta d'etiqueta a partir del text de la pista: «Cançó N» i bucle = Cançó; «Sfx N» i durada fixa = Sfx; bucle d'un sol canal (`1c`) = Descarta; 0 si no se sap. */
    fun suggeriment(text: String): Int = when {
        text.startsWith("Cançó") -> 1
        text.startsWith("Sfx") -> 2
        text.contains("· bucle") && text.contains("· 1c") -> 3          // un bucle d'un sol canal és un tros de cançó
        text.contains("· bucle") -> 1
        RE_SEGONS.containsMatchIn(text) -> 2
        else -> 0
    }
    /** Compilada un sol cop: `suggeriment` es crida per a cada cançó des de `FiltreCancons.esSfx` (llistes automàtiques, anàlisi total). */
    private val RE_SEGONS = Regex("· \\d+ s")
    /** Accepta la proposta a les pistes sense etiqueta d'un àlbum; retorna quantes n'ha etiquetat. */
    fun acceptaSuggeriments(nom: String, pistes: List<Pair<Int, String>>): Int {
        var n = 0
        for ((id, tx) in pistes) if (etiqueta(nom, id) == 0) { val sg = suggeriment(tx); if (sg != 0) { posaEtiqueta(nom, id, sg); n++ } }
        return n
    }
    /** Report en text d'un àlbum: capçalera (joc, build, data i clau de l'àlbum, que porta el CRC dels drivers automàtics) i una línia per pista etiquetada: «C 1. …» (cançó), «S 2. …» (sfx), «J 4. …» (jingle) o «✗ 3. …» (descartada). */
    fun report(titol: String, nom: String, pistes: List<Pair<Int, String>>): String {
        val d = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
        val sb = StringBuilder("$titol [${cat.gbdsmixxx.Informe.build}] $d · $nom\n")
        when (estat(nom)) { 1 -> sb.append("Joc: BÉ\n"); 2 -> sb.append("Joc: NO BÉ — ").append(motiu(nom)).append('\n') }          // $nom = clau de l'àlbum (NES_Auto_<CRC>): identifica la versió de ROM
        for ((i, p) in pistes.withIndex()) {
            val l = when (etiqueta(nom, p.first)) { 1 -> "C"; 2 -> "S"; 3 -> "✗"; 4 -> "J"; else -> null } ?: continue
            sb.append(l).append(" ${i + 1}. ").append(p.second).append(" (#").append(p.first).append(')').append(nomPista(nom, p.first).let { if (it.isEmpty()) "" else " — " + it }).append('\n')
        }
        return sb.toString().trimEnd()
    }
    /** Un sol report amb tots els jocs que tenen pistes etiquetades, un darrere l'altre separats per una línia en blanc. */
    fun reportTot(b: cat.gbdsmixxx.Biblioteca): String {
        val out = ArrayList<String>()
        for (nom in albumsEtiquetats()) {
            val e = b.emissores.firstOrNull { it.nom == nom } ?: continue
            val ids = cat.gbdsmixxx.cancons(b, e)
            val r = report(titolAlbum(e), nom, ids.map { it to b.etiq(e, it) })
            if (r.lines().size > 1) out.add(r)
        }
        return out.joinToString("\n\n")
    }
    /** Una línia per joc marcat: «Joc - va bé» o «Joc - no perquè "motiu"». */
    fun text(): String = noms().filter { estat(it) != 0 }.joinToString("\n") {
        titol(it) + " - " + (if (estat(it) == 1) "va bé" else "no perquè \"" + motiu(it) + "\"")
    }
}

/** ✓ i ✗ de l'àlbum: ✓ marca «va bé»; ✗ demana el motiu (`alCreu`) i, si ja hi és, la treu. Tocar la marca activa la treu. */
@Composable
fun MarcaAlbum(v: Int, alTic: () -> Unit, alCreu: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BotoIcona(CamiIcones.FET, alTic, mida = 44.dp, icona = 22.dp, color = if (v == 1) Color.White else M.tenu, fons = if (v == 1) M.bo else null, desc = t("Marca que va bé"))
        BotoIcona(CamiIcones.TANCA, alCreu, mida = 44.dp, icona = 22.dp, color = if (v == 2) Color.White else M.tenu, fons = if (v == 2) M.mal else null, desc = t("Marca que no va"))
    }
}

/** Botó rodó d'una etiqueta de pista; `suggerit` el deixa amb la vora de color (proposta sense confirmar). */
@Composable
fun BotoEtiqueta(lletra: String, actiu: Boolean, suggerit: Boolean, color: Color, alToc: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(if (actiu) color else Color.Transparent).then(if (suggerit && !actiu) Modifier.border(1.5.dp, color, CircleShape) else Modifier)
        .premsable(0.85f) { alToc() }, contentAlignment = Alignment.Center) {
        Tx(lletra, Tipus.cosFort, if (actiu) Color.White else if (suggerit) color else M.tenu)
    }
}

private const val D_MOTIU_REV = "motiu_rev"

/** Botó gran del mode revisió (etiqueta o Bé/No bé): ple si és l'actiu, tonal si no, amb vora si és el suggerit. */
@Composable
private fun BotoGran(text: String, color: Color, actiu: Boolean, suggerit: Boolean, toc: () -> Unit) {
    val forma = RoundedCornerShape(Formes.gran)
    Box(Modifier.fillMaxWidth().height(68.dp).clip(forma).background(color.copy(alpha = if (actiu) 1f else 0.20f))
        .then(if (suggerit && !actiu) Modifier.border(2.dp, color, forma) else Modifier).premsable(0.96f) { toc() }, contentAlignment = Alignment.Center) {
        Tx(text, Tipus.gran, if (actiu) Color.White else M.text)
    }
}

/** Mode revisió: una pista cada cop, quatre botons grans (Cançó, Sfx, Jingle, Descarta); en triar-ne un es desa i passa sola a la següent, que ja sona. Comença per la primera sense etiqueta.
 *  En acabar les pistes del joc surt la pantalla «Bé / No bé» (amb el motiu si no va bé), es desa la marca de l'àlbum i `seguent` passa al joc següent. */
@Composable
fun ModeRevisio(b: cat.gbdsmixxx.Biblioteca, e: cat.gbdsmixxx.Emissora, ids: List<Int>, ctx: android.content.Context, canvia: () -> Unit, seguent: () -> Unit, tanca: () -> Unit) {
    val nav = LocalNav.current
    // tot l'estat va lligat al joc (`e.nom`): en passar al joc següent la revisió comença de zero, no amb el «Joc acabat» de l'anterior
    var i by remember(e.nom) { mutableStateOf(ids.indexOfFirst { Marques.etiqueta(e.nom, it) == 0 }.coerceAtLeast(0)) }
    var v by remember(e.nom) { mutableStateOf(0) }
    var fi by remember(e.nom) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(e.nom, i, fi) { if (ids.isNotEmpty() && !fi) Pl.play(ctx, b, e, ids, i.coerceIn(0, ids.size - 1)) }
    Box(Modifier.fillMaxSize().background(M.fons).premsable(1f, vibra = false) { }) {
        if (fi) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tx(t("Joc acabat"), Tipus.etiqueta, M.tenu, Modifier.weight(1f))
                    BotoIcona(CamiIcones.TANCA, tanca, mida = 44.dp, color = M.tenu, fons = M.sup, desc = t("Tanca"))
                }
                Portada(e, Modifier.size(120.dp), Formes.mitjana)
                Tx(titolAlbum(e), Tipus.titol, M.text, linies = 3)
                Tx(t("Com ha anat el joc?"), Tipus.cos, M.tenu)
                Box(Modifier.weight(1f))
                BotoGran(t("Bé"), M.bo, false, false) { Marques.posa(e.nom, titolAlbum(e), 1, ""); canvia(); seguent() }
                BotoGran(t("No bé"), M.mal, false, false) { nav.obreDialeg(D_MOTIU_REV) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Xip(t("Enrere"), false, CamiIcones.ENRERE) { fi = false; i = ids.size - 1 }
                    Xip(t("Salta"), false, CamiIcones.SEGUENT) { seguent() }
                }
            }
            if (nav.te(D_MOTIU_REV)) MotiuDialeg({ nav.tancaDialeg(D_MOTIU_REV) }) { m -> Marques.posa(e.nom, titolAlbum(e), 2, m); nav.tancaDialeg(D_MOTIU_REV); canvia(); seguent() }
        } else PistaRevisio(b, e, ids, i, v, { i = it }, { v++ }, { fi = true }, canvia, tanca)
    }
}

/** Una pista del mode revisió (estat a `ModeRevisio`). */
@Composable
private fun PistaRevisio(b: cat.gbdsmixxx.Biblioteca, e: cat.gbdsmixxx.Emissora, ids: List<Int>, i: Int, v: Int, posaI: (Int) -> Unit, refresca: () -> Unit, acaba: () -> Unit, canvia: () -> Unit, tanca: () -> Unit) {
    run {
        val ii = i.coerceIn(0, ids.size - 1)
        val id = ids[ii]; val et = v.let { Marques.etiqueta(e.nom, id) }
        val tx = b.etiq(e, id); val sg = if (et == 0) Marques.suggeriment(tx) else 0
        var nomP by remember(e.nom, ii) { mutableStateOf(Marques.nomPista(e.nom, id)) }
        fun desaNom() { Marques.posaNomPista(e.nom, id, nomP); refresca(); canvia() }
        fun tria(x: Int) {
            desaNom(); Marques.posaEtiqueta(e.nom, id, x); refresca(); canvia()
            if (ii + 1 < ids.size) posaI(ii + 1) else acaba()
        }
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Portada(e, Modifier.size(48.dp), Formes.petita)
                Column(Modifier.weight(1f)) {
                    Tx(titolAlbum(e), Tipus.etiqueta, M.tenu)
                    Tx("${ii + 1}/${ids.size}", Tipus.cosFort, M.text)
                }
                BotoIcona(CamiIcones.TANCA, tanca, mida = 44.dp, color = M.tenu, fons = M.sup, desc = t("Tanca"))
            }
            // progrés de la revisió
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(M.pista)) {
                Box(Modifier.fillMaxWidth((ii + 1f) / ids.size).height(6.dp).clip(CircleShape).background(M.accent))
            }
            Tx(tx, Tipus.titol, M.text, linies = 3)
            Camp(nomP, t("Nom de la pista")) { nomP = it }
            Box(Modifier.weight(1f))
            BotoGran(t("Cançó"), M.etCanco, et == 1, sg == 1) { tria(1) }
            BotoGran(t("Sfx"), M.etSfx, et == 2, sg == 2) { tria(2) }
            BotoGran(t("Jingle"), M.etJingle, et == 4, false) { tria(4) }
            BotoGran(t("Descarta"), M.mal, et == 3, sg == 3) { tria(3) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Xip(t("Enrere"), false, CamiIcones.ENRERE) { desaNom(); if (ii > 0) posaI(ii - 1) }
                Xip(t("Salta"), false, CamiIcones.SEGUENT) { desaNom(); if (ii + 1 < ids.size) posaI(ii + 1) else acaba() }
            }
        }
    }
}

/** Full que demana el motiu de la creu; `ok` rep el text escrit (no buit). */
@Composable
fun MotiuDialeg(tanca: () -> Unit, ok: (String) -> Unit) {
    var motiu by remember { mutableStateOf("") }
    Full(t("Per què no va?"), tanca) {
        Camp(motiu, t("Motiu"), unaLinia = false) { motiu = it }
        BotoPle(t("D'acord"), CamiIcones.FET, Modifier.fillMaxWidth()) { if (motiu.isNotBlank()) ok(motiu.trim()) }
    }
}

/** Extractor d'Ajustos: el report de tots els jocs, el log de proves (marques ✓/✗ amb el motiu i avisos del motor) i la traça de l'import, amb «Comparteix» i «Esborra». */
@Composable
fun ExtractorMarques(ctx: android.content.Context) {
    var versio by remember { mutableStateOf(0) }
    @Composable
    fun bloc(titol: String, text: String, buit: String, esborra: () -> Unit) {
        Column(Modifier.fillMaxWidth().superficie(M.supAlta, RoundedCornerShape(Formes.mitjana)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tx(titol, Tipus.cosFort, M.text)
            if (text.isEmpty()) Tx(buit, Tipus.petit, M.tenu, linies = 3)
            else {
                Tx(text, Tipus.petit, M.text, linies = 300)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Xip(t("Comparteix"), false, CamiIcones.COMPARTEIX) {
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                        ctx.startActivity(Intent.createChooser(i, t("Comparteix")))
                    }
                    Xip(t("Esborra el log"), false, CamiIcones.ESBORRA) { esborra(); versio++ }
                }
            }
        }
    }
    BotoTonal(t("Copia el report de tots els jocs revisats"), CamiIcones.COPIA, Modifier.fillMaxWidth()) {
        val b = App.bib; val txt = if (b == null) "" else Marques.reportTot(b)
        if (txt.isEmpty()) android.widget.Toast.makeText(ctx, t("Cap joc revisat"), android.widget.Toast.LENGTH_SHORT).show()
        else {
            (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("report", txt))
            android.widget.Toast.makeText(ctx, t("Report copiat al portaretalls"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    BotoTonal(t("Esborra totes les marques"), CamiIcones.ESBORRA, Modifier.fillMaxWidth(), M.mal.copy(alpha = 0.18f), M.text) { Marques.esborraTot(); cat.gbdsmixxx.Informe.esborraProves(ctx); versio++ }
    // comptador de fils de fons (ronda d'optimització): es veu aquí i va al final del log de proves quan es comparteix
    val fils = versio.let { cat.gbdsmixxx.FilsVius.text() }
    Tx(fils, Tipus.petit, M.tenu, linies = 2)
    val pro = versio.let { cat.gbdsmixxx.Informe.proves(ctx).trim() }.let { if (it.isEmpty()) it else it + "\n\n" + fils }
    bloc(t("Log de proves"), pro, t("Encara no hi ha cap línia al log. Marca els jocs amb ✓ o ✗ a l'àlbum.")) { cat.gbdsmixxx.Informe.esborraProves(ctx) }
    val trF = java.io.File(ctx.filesDir, "importa_log.txt")
    val tr = versio.let { try { trF.readText().trim() } catch (e: Throwable) { "" } }
    bloc(t("Traça de l'import"), tr, t("Encara no hi ha cap import.")) { try { trF.delete() } catch (e: Throwable) { } }
}
