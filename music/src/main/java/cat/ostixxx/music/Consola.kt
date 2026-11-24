package cat.ostixxx.music

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Emissora
import cat.gbdsmixxx.JOCS_COMPAT
import cat.gbdsmixxx.t

/** Consoles en l'ordre en què surten a la biblioteca; «Altres» agrupa el que no és cap d'aquestes (.vgm...). */
val ORDRE_CONSOLES = listOf("GB", "GBC", "NES", "SNES", "GBA", "DS", "PS1", "Altres")

fun nomConsola(c: String) = when (c) {
    "GB" -> "Game Boy"; "GBC" -> "Game Boy Color"; "NES" -> "NES"; "SNES" -> "SNES"; "GBA" -> "Game Boy Advance"; "DS" -> "Nintendo DS"; "PS1" -> "PlayStation"; else -> t("Altres")
}

private val GBA_ORIGINALS = setOf("EMERALD", "LEAFGREEN", "FIRERED", "RUBY", "SAPPHIRE")

/** La consola d'un àlbum, pel nom intern de l'emissora (`GB_`, `NES_`, `SNES_`, `GBA_`, `DS_`...). Els GB_ distingeixen GB i GBC per la llista de compatibles. */
fun consolaDe(e: Emissora): String = consolaDeNom(e.nom)

/** El mateix que `consolaDe`, només amb el nom de l'emissora (per als efectes per consola, `EfectesRadio.resolConsola`). */
fun consolaDeNom(n: String): String {
    return when {
        n.startsWith("SNES_") -> "SNES"
        n.startsWith("NES_") || n.startsWith("NSF_") -> "NES"
        n.startsWith("GBS_") -> "GB"
        n.startsWith("GB_") -> if (n in clausGbc()) "GBC" else "GB"
        n.startsWith("GBA_") -> "GBA"
        n.startsWith("DS_") -> "DS"
        n.startsWith("PS1_") -> "PS1"
        n.startsWith("VGM_") -> "Altres"
        n == "CRISTALL" || n == "TCG" || n == "TCG2" -> "GBC"
        n == "POKEMON_RED" || n == "POKEMON_BLUE" || n == "GROC" -> "GB"
        n.uppercase() in GBA_ORIGINALS -> "GBA"
        n == n.uppercase() -> "DS"                                  // la resta d'originals en majúscules (Diamant, Perla, HeartGold...) són de DS
        else -> "Altres"
    }
}

/**
 * Claus dels àlbums de GB que són de Game Boy Color (revisió UI, 2026-11-22): abans cada `consolaDe` d'un `GB_` recorria `JOCS_COMPAT` sencera, i es crida per fila
 * i 7 vegades per àlbum a `grupsPerConsola`. Es refà només quan `JOCS_COMPAT` és una llista nova (el registre la torna a fer quan canvia). Es pot cridar des de qualsevol fil.
 */
@Volatile private var cauGbc: Pair<List<cat.gbdsmixxx.JocCompat>, Set<String>>? = null
private fun clausGbc(): Set<String> {
    val l = JOCS_COMPAT
    cauGbc?.let { if (it.first === l) return it.second }
    val primer = HashMap<String, cat.gbdsmixxx.JocCompat>(); for (j in l) primer.putIfAbsent(j.clau, j)          // com `firstOrNull`: mana la primera entrada de cada clau
    val s = primer.values.filter { it.sistema.contains("Color") }.mapTo(HashSet()) { it.clau }
    cauGbc = l to s
    return s
}

/** Àlbums en ordre alfabètic (sense distingir majúscules ni accents). Mateix ordre que comparar amb el `Collator`, però amb `CollationKey` (cada títol es processa una vegada, no a cada comparació). */
fun ordenaAlbums(l: List<Emissora>): List<Emissora> {
    val c = java.text.Collator.getInstance(java.util.Locale("ca")).also { it.strength = java.text.Collator.PRIMARY }
    return l.map { it to c.getCollationKey(titolAlbum(it)) }.sortedBy { it.second }.map { it.first }
}

/** Estat de prova d'un joc de consola: 0 = provat (✓), 1 = sense provar (cap marca; ho són tots els àlbums, també GBA, DS i fitxers; només les llistes no), 2 = provat i no va (✗ marcat). */
fun estatProva(e: Emissora): Int {
    if (e.esLlista) return 0
    val m = Marques.estat(e.nom)
    return if (m == 2) 2 else if (m == 0) 1 else 0          // tots els jocs surten «Sense provar» fins que Revisa (o la ✓) els marca (l'autor, 2026-11-15)
}

/** Joc que no va bé (✗ marcat) o que no s'ha provat. */
fun dubtos(e: Emissora): Boolean = estatProva(e) != 0

/** Àlbums agrupats per consola i ordenats alfabèticament; amb `sel` buit surten totes les consoles, si no, només aquesta. */
fun grupsPerConsola(albums: List<Emissora>, sel: String): List<Pair<String, List<Emissora>>> {
    val ord = ordenaAlbums(albums)
    if (sel.isNotEmpty()) return listOf(sel to ord.filter { consolaDe(it) == sel })
    val per = ord.groupBy { consolaDe(it) }          // una sola passada (abans, un filtre sencer per consola)
    return ORDRE_CONSOLES.mapNotNull { c -> per[c]?.let { c to it } }
}

/** Filtre per consola (es recorda): «Totes» + les consoles que tenen àlbums. */
@Composable
fun FiltreConsola(albums: List<Emissora>, sel: String, canvia: (String) -> Unit) {
    // P2: el recompte per consola es fa una vegada per llista d'àlbums (no a cada dibuix)
    val compte = androidx.compose.runtime.remember(albums) { albums.groupingBy { consolaDe(it) }.eachCount() }
    val hi = ORDRE_CONSOLES.filter { (compte[it] ?: 0) > 0 }
    if (hi.size < 2) return
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
        item(key = "totes") { Xip(t("Totes"), sel.isEmpty()) { canvia("") } }
        items(hi, key = { it }) { c -> Xip(nomConsola(c) + " " + (compte[c] ?: 0), sel == c) { canvia(c) } }
    }
}

fun desaFiltreConsola(c: String) { App.env.putStr("filtre_consola", c) }
fun filtreConsolaDesat(): String = App.env.str("filtre_consola")
