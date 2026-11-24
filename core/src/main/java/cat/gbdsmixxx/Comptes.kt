package cat.gbdsmixxx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Reproduccions de cada cançó (l'autor, 2026-11-15: «més reproduïdes» i llista automàtica de les 50 més reproduïdes). La clau és la de `Biblioteca.clauDe` (`àlbum|id`), també per a les cançons
 * que sonen des d'una llista. Es compten cada cop que una cançó comença (vegeu `Comptes`).
 */
/** Cançons que no entren a les llistes automàtiques (sfx i descartades): Music hi posa el criteri (etiquetes de prova). `esSfx(àlbum, id)`. */
object FiltreCancons {
    @Volatile var esSfx: (String, Int) -> Boolean = { _, _ -> false }
    /** Si l'anàlisi total ha d'analitzar la cançó (àlbum, id): només les confirmades que funcionen; la posa Music (etiqueta Cançó, o àlbum ✓ sense etiquetar i no sfx). Per defecte, totes. */
    @Volatile var analitza: (String, Int) -> Boolean = { _, _ -> true }
}

/** Desades al fitxer binari de dades de l'usuari (`DadesUsuari`, tipus «c», per àlbum estable i pista); el TXT `comptes` de les preferències d'abans es migra una vegada i no es toca. */
object Comptes {
    var versio by mutableIntStateOf(0)
    private val T = MagatzemUsuari.T_COMPTE
    fun suma(clau: String) {
        if (clau.isEmpty()) return
        val alb = RadioDades.emissoraDe(clau); val id = RadioDades.idDe(clau)
        val d = DadesUsuari.v
        synchronized(this) { d.posaInt(T, alb, id, d.int(T, alb, id) + 1) }
        versio++
    }
    /** (clau `àlbum|id` amb la clau de dades de l'àlbum, `Emissora.clauDades`; P1: la mateixa que RadioDades i les llistes, reproduccions) de totes les cançons comptades. */
    private fun tots(): List<Pair<String, Int>> =
        DadesUsuari.v.totes(T).mapNotNull { (est, id, v) -> if (id == null || v !is Int) null else RadioDades.clau(est, id) to v }
    /** Les `n` cançons més reproduïdes (claus `àlbum|id`), de més a menys. */
    fun top(n: Int): List<String> = tots().sortedByDescending { it.second }.take(n).map { it.first }
    /** Els àlbums amb reproduccions, de més a menys (suma de les seves cançons): parells (clau de dades de l'àlbum, reproduccions; `Biblioteca.perNom` dona l'àlbum). */
    fun albums(): List<Pair<String, Int>> = tots().groupBy({ RadioDades.emissoraDe(it.first) }, { it.second }).map { it.key to it.value.sum() }.sortedByDescending { it.second }
    fun esborra() { DadesUsuari.v.esborraTipus(setOf(T)); versio++ }
}
