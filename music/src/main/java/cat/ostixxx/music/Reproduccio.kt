package cat.ostixxx.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cat.gbdsmixxx.Biblioteca
import cat.gbdsmixxx.Emissora
import cat.gbdsmixxx.RadioMotor
import cat.gbdsmixxx.Reprod

/** Reproducció amb aleatori: en activar-lo la llista es barreja (la cançó que sona es queda la primera) i en desactivar-lo torna a l'ordre original. (Abans era a Tema.kt; sense canvis.) */
object Pl {
    var aleatori by mutableStateOf(false)
    private var original: List<Int> = emptyList()

    fun play(ctx: android.content.Context, b: Biblioteca, e: Emissora, ids: List<Int>, pos: Int) {
        original = ids
        if (aleatori && ids.isNotEmpty()) Reprod.reprodueix(ctx, b, e, listOf(ids[pos]) + ids.filterIndexed { i, _ -> i != pos }.shuffled(), 0)
        else Reprod.reprodueix(ctx, b, e, ids, pos)
        EstatRepro.sincronitza()          // la UI ho veu a l'instant (no al cap de 250 ms)
    }

    fun alterna() {
        aleatori = !aleatori
        val l = RadioMotor.llista ?: return
        val actual = l.ids.getOrNull(RadioMotor.pos) ?: return
        val nova = if (aleatori) listOf(actual) + l.ids.filter { it != actual }.shuffled() else original.takeIf { it.contains(actual) } ?: l.ids
        RadioMotor.llista = RadioMotor.Llista(nova, l.crea, l.etiqueta)
        RadioMotor.pos = nova.indexOf(actual)
    }

    fun playPausa() { if (RadioMotor.sonant) { if (RadioMotor.pausat) RadioMotor.represa() else RadioMotor.pausa() }; EstatRepro.sincronitza() }
    fun salta(d: Int) { if (RadioMotor.sonant) RadioMotor.salta(d); EstatRepro.sincronitza() }
    fun atura() { RadioMotor.atura(); EstatRepro.sincronitza() }
}
