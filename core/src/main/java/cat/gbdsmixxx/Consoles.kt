package cat.gbdsmixxx

import android.content.Context
import java.nio.channels.FileChannel

/** Resultat d'importar una ROM o fitxer de consola: `nom` = nom del joc, o null si ha fallat (el motiu és a `RomsMusica.motiu`). */
class ResultatImport(val nom: String?)

/**
 * Punt d'extensió de la música de consoles (Game Boy, NES, SNES, .nsf/.vgm/.gbs/.spc). El codi viu al mòdul `:consoles`; només OSTixxx Music
 * l'hi registra. El mesclador no el registra i, per tant, no porta cap codi de consoles.
 */
interface ProveidorConsoles {
    /** Extensions de fitxer que admet en importar una carpeta (en minúscules, sense punt). */
    val extensions: Set<String>
    /** Afegeix a `l` les emissores (jocs) de consoles importats. */
    fun emissores(ctx: Context, b: Biblioteca, l: MutableList<Emissora>)
    /** Intenta importar el fitxer (`h` = els primers 512 bytes); null si no és de consola, i `ResultatImport(null)` si ho és però falla. */
    fun importa(ctx: Context, uri: android.net.Uri, ch: FileChannel, h: ByteArray): ResultatImport?
    fun buidaCaches()
    /** Després de publicar una biblioteca (en un fil apart, amb les dades de l'usuari carregades): migracions úniques que depenen dels drivers (marques «provat» d'abans). */
    fun despresBiblioteca(b: Biblioteca, du: MagatzemUsuari) {}
}

object Consoles {
    @Volatile var proveidor: ProveidorConsoles? = null
    /** OSTixxx Debug: només admet els jocs experimentals (res de GBA, DS, jocs estables ni fitxers de rip). */
    @Volatile var nomesExperimentals = false
}
