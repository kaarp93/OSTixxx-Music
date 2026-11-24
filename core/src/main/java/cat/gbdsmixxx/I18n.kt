package cat.gbdsmixxx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Textos en català (clau), anglès i castellà. Un text sense traducció es mostra tal qual. */
object I18n {
    /** "auto" (idioma del sistema), "ca", "en" o "es". */
    var triat by mutableStateOf("auto")

    val actual: String
        get() = if (triat != "auto") triat else Locale.getDefault().language.let { if (it == "ca" || it == "es") it else "en" }

    private val taula: Map<String, Array<String>> = listOf(
        // ca, en, es
        arrayOf("MESCLADOR", "MIXER", "MEZCLADOR"), arrayOf("REPRODUCTOR", "PLAYER", "REPRODUCTOR"), arrayOf("AJUSTOS", "SETTINGS", "AJUSTES"),
        arrayOf("Reproductor", "Player", "Reproductor"), arrayOf("ROMs", "ROMs", "ROMs"),
        arrayOf("Carregant...", "Loading...", "Cargando..."), arrayOf("Aturat", "Stopped", "Parado"), arrayOf("En pausa", "Paused", "En pausa"),
        arrayOf("Canals", "Channels", "Canales"), arrayOf("TANCA", "CLOSE", "CERRAR"), arrayOf("Cancel·la", "Cancel", "Cancelar"),
        arrayOf("Triar cançó", "Pick a song", "Elegir canción"), arrayOf("Cal importar una ROM de GBA o DS", "Import a GBA or DS ROM first", "Importa antes una ROM de GBA o DS"),
        arrayOf("Importar ROM (música)", "Import ROM (music)", "Importar ROM (música)"), arrayOf("Importar carpeta", "Import folder", "Importar carpeta"),
        arrayOf("Netejar llistes", "Clear lists", "Limpiar listas"), arrayOf("Segur?", "Sure?", "¿Seguro?"), arrayOf("Sí", "Yes", "Sí"), arrayOf("No", "No", "No"),
        arrayOf("Només jocs de GBA (m4a) i de DS (SDAT)", "GBA (m4a) and DS (SDAT) games only", "Solo juegos de GBA (m4a) y de DS (SDAT)"),
        arrayOf("Llegint la ROM...", "Reading the ROM...", "Leyendo la ROM..."), arrayOf("Important...", "Importing...", "Importando..."),
        arrayOf("Importats:", "Imported:", "Importados:"), arrayOf("Netejat:", "Cleared:", "Limpiado:"),
        arrayOf("Freqüència", "Sample rate", "Frecuencia"), arrayOf("a les cançons noves", "for new songs", "en las canciones nuevas"),
        arrayOf("Temps màx", "Max time", "Tiempo máx"), arrayOf("Bucles", "Loops", "Bucles"),
        arrayOf("1 min", "1 min", "1 min"), arrayOf("2 min", "2 min", "2 min"), arrayOf("3 min", "3 min", "3 min"),
        arrayOf("1 bucle", "1 loop", "1 bucle"), arrayOf("2 bucles", "2 loops", "2 bucles"), arrayOf("3 bucles", "3 loops", "3 bucles"),
        arrayOf("SALTA A BEAT", "JUMP TO BEAT", "SALTAR A BEAT"), arrayOf("REPETEIX", "REPEAT", "REPETIR"), arrayOf("Buscant...", "Seeking...", "Buscando..."), arrayOf("Instruments de la cançó triada", "Instruments of the chosen song", "Instrumentos de la canción elegida"), arrayOf("Tria una cançó a INSTRUMENTS", "Pick a song under INSTRUMENTS", "Elige una canción en INSTRUMENTOS"), arrayOf("CANÇONS", "SONGS", "CANCIONES"), arrayOf("Tria una cançó per editar-ne els canals", "Pick a song to edit its channels", "Elige una canción para editar sus canales"), arrayOf("Bateria", "Drums", "Batería"), arrayOf("notes", "notes", "notas"), arrayOf("Baix", "Bass", "Bajo"), arrayOf("Acords", "Chords", "Acordes"), arrayOf("Melodia", "Melody", "Melodía"), arrayOf("Agut", "High", "Agudo"), arrayOf("Falta la tonalitat d'algun deck", "A deck is missing its key", "Falta el tono de algún deck"), arrayOf("Cançons sense bucle (efectes de so, fanfàrries)", "Songs without a loop (sound effects, fanfares)", "Canciones sin bucle (efectos de sonido, fanfarrias)"),
        arrayOf("Amaga-les", "Hide them", "Ocultarlas"), arrayOf("Mostra-les", "Show them", "Mostrarlas"), arrayOf("No s'ha pogut obrir el fitxer", "The file could not be opened", "No se ha podido abrir el archivo"), arrayOf("ROM de NES", "NES ROM", "ROM de NES"), arrayOf("no és cap dels jocs reconeguts", "is none of the recognised games", "no es ninguno de los juegos reconocidos"),
        // GBA: so original 100 % (PanellsJugador)
        arrayOf("GBA: so original 100 %", "GBA: 100% original sound", "GBA: sonido original al 100 %"),
        arrayOf("Activat, el mesclador és el de la consola, amb el seu gra (hiss) característic. Desactivat (per defecte), se sintetitza amb el nostre mesclador: no hi ha hiss, però pot tenir errors de so. El canvi s'aplica en començar la cançó següent.", "On: the mixer is the console's, with its characteristic hiss. Off (default): our own mixer is used: no hiss, but it may have sound errors. Takes effect when the next song starts.", "Activado: el mezclador es el de la consola, con su siseo característico. Desactivado (por defecto): se sintetiza con nuestro mezclador: sin siseo, pero puede tener errores de sonido. El cambio se aplica al empezar la siguiente canción."),
        // Partitures (PartituresApp.kt)
        arrayOf("Fitxer de partitures no vàlid", "Invalid scores file", "Archivo de partituras no válido"),
        arrayOf("Partitures de", "Scores for", "Partituras de"),
        arrayOf("només es fan servir amb la seva ROM", "only used with their ROM", "solo se usan con su ROM"),
        // PlayStation (Ps1.kt)
        arrayOf("CD àudio", "CD audio", "CD audio"),
        arrayOf("Disc de PlayStation: cal «Accés a tots els fitxers» (Ajustos), perquè la imatge es llegeix d'on és i no es copia", "PlayStation disc: \"All files access\" is needed (Settings), because the image is read where it is and not copied", "Disco de PlayStation: hace falta «Acceso a todos los archivos» (Ajustes), porque la imagen se lee desde donde está y no se copia"),
        arrayOf("El .cue no té cap pista de dades", "The .cue has no data track", "El .cue no tiene ninguna pista de datos"),
        arrayOf("No es troba el fitxer de la pista de dades", "The data track file was not found", "No se encuentra el archivo de la pista de datos"),
        arrayOf("No és un disc de PlayStation", "It is not a PlayStation disc", "No es un disco de PlayStation"),
        arrayOf("Disc de PlayStation sense música XA ni CD àudio (la música seqüenciada encara no es pot llegir)", "PlayStation disc without XA or CD audio music (sequenced music cannot be read yet)", "Disco de PlayStation sin música XA ni CD audio (la música secuenciada aún no se puede leer)"),
        arrayOf("NSF no vàlid", "Invalid NSF", "NSF no válido"), arrayOf("GBS no vàlid", "Invalid GBS", "GBS no válido"), arrayOf("SPC no vàlid", "Invalid SPC", "SPC no válido"),
        arrayOf("VGM sense cap xip que sapiguem fer sonar (SN76489, Game Boy o NES) o fitxer no vàlid", "VGM with no chip we can play (SN76489, Game Boy or NES) or invalid file", "VGM sin ningún chip que sepamos reproducir (SN76489, Game Boy o NES) o archivo no válido"),
        arrayOf("ROM de Game Boy", "Game Boy ROM", "ROM de Game Boy"), arrayOf("no coincideix amb cap joc ni versió reconeguts", "does not match any recognised game or version", "no coincide con ningún juego ni versión reconocidos"),
        arrayOf("ROM de GBA", "GBA ROM", "ROM de GBA"), arrayOf("no s'hi ha trobat el motor de so M4A (Sappy)", "no M4A (Sappy) sound engine found", "no se ha encontrado el motor de sonido M4A (Sappy)"),
        arrayOf("ROM de DS", "DS ROM", "ROM de DS"), arrayOf("no s'hi ha trobat cap arxiu de so SDAT", "no SDAT sound file found", "no se ha encontrado ningún archivo de sonido SDAT"),
        arrayOf("No s'ha pogut llegir la ROM", "The ROM could not be read", "No se ha podido leer la ROM"), arrayOf("Format no reconegut", "Unrecognised format", "Formato no reconocido"),
        arrayOf("DS: el joc no porta l'arxiu de so esperat", "DS: the game does not have the expected sound file", "DS: el juego no lleva el archivo de sonido esperado"), arrayOf("DS: no s'ha pogut llegir l'arxiu de so", "DS: the sound file could not be read", "DS: no se ha podido leer el archivo de sonido"),
        arrayOf("Informe d'importació", "Import report", "Informe de importación"), arrayOf("motiu desconegut", "unknown reason", "motivo desconocido"),
        arrayOf("Avís", "Notice", "Aviso"), arrayOf("No totes les ROMs ni tots els arxius funcionaran. Cada joc porta el seu propi motor de so i només se'n reconeixen alguns: pot fallar, sonar malament o no aparèixer cap cançó.",
            "Not all ROMs and files will work. Each game has its own sound engine and only some are recognised: songs may fail, sound wrong or not show up at all.",
            "No todas las ROMs ni todos los archivos funcionarán. Cada juego lleva su propio motor de sonido y solo se reconocen algunos: puede fallar, sonar mal o no aparecer ninguna canción."),
        arrayOf("ENTÈS", "GOT IT", "ENTENDIDO"), arrayOf("NOTES", "NOTES", "NOTAS"), arrayOf("INSTRUMENTS", "INSTRUMENTS", "INSTRUMENTOS"),
        arrayOf("Tria una cançó a NOTES", "Pick a song under NOTES", "Elige una canción en NOTAS"), arrayOf("ATURA", "STOP", "PARAR"), arrayOf("TOTS", "ALL", "TODOS"), arrayOf("Instrument", "Instrument", "Instrumento"), arrayOf("Puja", "Move up", "Subir"), arrayOf("Baixa", "Move down", "Bajar"), arrayOf("(fi)", "(end)", "(fin)"),
        arrayOf("Fes-la setlist", "Use as setlist", "Usar como setlist"), arrayOf("És la setlist (toca per treure-la)", "This is the setlist (tap to unset)", "Es la setlist (toca para quitarla)"), arrayOf("DESA", "SAVE", "GUARDAR"), arrayOf("GREUS", "LOW", "GRAVES"), arrayOf("MITJOS", "MID", "MEDIOS"), arrayOf("AGUTS", "HIGH", "AGUDOS"),
        arrayOf("Instruments d'una altra cançó", "Instruments from another song", "Instrumentos de otra canción"), arrayOf("Les notes són de la cançó del deck; els instruments, d'una altra (del mateix joc o d'un altre del mateix tipus).", "The notes come from the deck's song; the instruments, from another one (same game or another game of the same type).", "Las notas son de la canción del deck; los instrumentos, de otra (del mismo juego o de otro del mismo tipo)."),
        arrayOf("Aquest deck no té cap cançó", "This deck has no song", "Este deck no tiene canción"), arrayOf("Instruments", "Instruments", "Instrumentos"), arrayOf("els seus", "its own", "los suyos"),
        arrayOf("No es pot fer amb aquest joc o llista", "Not possible with this game or playlist", "No se puede con este juego o lista"), arrayOf("CANÇÓ", "SONG", "CANCIÓN"), arrayOf("ESBORRA", "DELETE", "BORRAR"),
        arrayOf("Toca el hot cue que vols treure", "Tap the hot cue to remove", "Toca el hot cue que quieres quitar"), arrayOf("Cerca", "Search", "Buscar"), arrayOf("Ordre", "Sort", "Orden"), arrayOf("Clau", "Key", "Tono"),
        arrayOf("Analitzant...", "Analysing...", "Analizando..."), arrayOf("Nom de la cançó", "Song name", "Nombre de la canción"), arrayOf("Nom de la llista", "Playlist name", "Nombre de la lista"),
        arrayOf("Canvia nom", "Rename", "Cambiar nombre"), arrayOf("Afegir a llista", "Add to playlist", "Añadir a lista"), arrayOf("Treu de la llista", "Remove from playlist", "Quitar de la lista"),
        arrayOf("Esborra la llista", "Delete playlist", "Borrar la lista"), arrayOf("Amaga la cançó", "Hide song", "Ocultar canción"), arrayOf("Nova llista", "New playlist", "Nueva lista"),
        arrayOf("Mostra ocultes", "Show hidden", "Mostrar ocultas"), arrayOf("D'acord", "OK", "Aceptar"), arrayOf("Mesclador en marxa", "Mixer running", "Mezclador en marcha"), arrayOf("Atura", "Stop", "Parar"),
        arrayOf("Desat:", "Saved:", "Guardado:"), arrayOf("No s'ha pogut gravar", "Could not record", "No se pudo grabar"), arrayOf("CANALS", "CHANNELS", "CANALES"), arrayOf("TO", "KEY", "TONO"), arrayOf("SALTA", "JUMP", "SALTA"), arrayOf("COMPTE", "COUNT", "CUENTA"), arrayOf("Color: compatibilitat amb el DECK", "Colour: key match with DECK", "Color: compatibilidad con el DECK"), arrayOf("La cançó no arriba al batec", "The song does not reach beat", "La canción no llega al beat"),
        arrayOf("Totes", "All", "Todas"), arrayOf("Combat", "Battle", "Combate"), arrayOf("Rutes", "Routes", "Rutas"), arrayOf("Entrenadors", "Trainers", "Entrenadores"),
        arrayOf("Ciutats", "Cities", "Ciudades"), arrayOf("Altres", "Other", "Otras"),
        arrayOf("(dia)", "(day)", "(día)"), arrayOf("(nit)", "(night)", "(noche)"), arrayOf("Mirada", "Gaze", "Mirada"),
        arrayOf("Poble", "Town", "Pueblo"), arrayOf("Ciutat", "City", "Ciudad"), arrayOf("Ruta", "Route", "Ruta"), arrayOf("Victòria", "Victory", "Victoria"),
        arrayOf("Fanfàrria", "Fanfare", "Fanfarria"), arrayOf("Concurs", "Contest", "Concurso"), arrayOf("Zona", "Area", "Zona"), arrayOf("Cançó", "Song", "Canción"),
    ).associateBy { it[0] }

    /** Textos que afegeix una app (OSTixxx Music): mateix format que la taula, [ca, en, es]. */
    private val extra = HashMap<String, Array<String>>()
    fun afegeix(files: List<Array<String>>) { for (f in files) extra[f[0]] = f }

    fun t(k: String): String {
        val fila = extra[k] ?: taula[k] ?: return k
        return when (actual) { "en" -> fila[1]; "es" -> fila[2]; else -> fila[0] }
    }
}

fun t(k: String) = I18n.t(k)
