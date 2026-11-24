package cat.ostixxx.music

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cat.gbdsmixxx.Biblioteca
import cat.gbdsmixxx.Emissora
import cat.gbdsmixxx.Portades
import cat.gbdsmixxx.RadioMotor
import cat.gbdsmixxx.Reprod

/**
 * Estat de la reproducció per a la UI (P1, substitueix el rellotge global `Tic` que refeia tota la interfície cada 400 ms): el motor no és estat de Compose,
 * així que un sol bucle (`MusicArrel`) en copia els valors cada 250 ms; escriure el mateix valor a un estat de Compose no refà res, de manera que només es
 * tornen a dibuixar les peces que llegeixen el camp que ha canviat (el títol quan canvia de cançó, els segons un cop per segon...).
 */
object EstatRepro {
    var sonant by mutableStateOf(false); private set
    var pausat by mutableStateOf(false); private set
    var titol by mutableStateOf(""); private set
    var joc by mutableStateOf(""); private set
    var seq by mutableIntStateOf(-1); private set
    var error by mutableStateOf<String?>(null); private set
    /** Segons sencers des del començament de la cançó. */
    var segons by mutableIntStateOf(0); private set
    /** Batec actual i total (enters; -1 si el motor no els sap). */
    var batec by mutableIntStateOf(-1); private set
    var batecMax by mutableIntStateOf(-1); private set
    /** Mode comparació: s'escolta sense efectes (B). El driver que sona és a `Comparacio.sonant`. */
    var senseEfectes by mutableStateOf(false); private set
    /** Sona i no és en pausa (els visualitzadors només s'animen llavors). */
    val actiu: Boolean get() = sonant && !pausat

    fun sincronitza() {
        sonant = RadioMotor.sonant; pausat = RadioMotor.pausat; titol = RadioMotor.titol
        joc = Reprod.joc; seq = RadioMotor.seqActual; error = RadioMotor.error
        segons = RadioMotor.segonsActuals.toInt()
        val si = RadioMotor.sinte
        batec = si?.batec?.takeIf { it >= 0.0 }?.toInt() ?: -1
        batecMax = si?.batecMax?.takeIf { it > 0.0 }?.toInt() ?: -1
        senseEfectes = RadioMotor.senseEfectes
        Comparacio.sincronitza()
    }
}

/**
 * L'app és a la pantalla (`onStart`/`onStop`), com a flux: els rellotges de la UI (`RellotgesRepro`) s'aturen del tot en segon pla. Abans continuaven despertant el fil
 * principal cada 45 i 250 ms amb la pantalla apagada només per mirar `EnPantalla.v` (el recompositor en pausa no cancel·la els `LaunchedEffect`, però un flux sí que els arriba).
 */
object PantallaVisible { val flux = kotlinx.coroutines.flow.MutableStateFlow(true) }

/**
 * Versió de la llista «Les 50 més reproduïdes» (l'autor, 2026-11-22): puja quan es compta una reproducció i `refaLlistes` ha refet les llistes. Abans es pujava `App.versio`,
 * que és la clau de tota la UI (Biblioteca, Cerca, àlbum obert, Inici, Comparació...), i cada cançó que començava ho refeia tot. Ara només la llegeix el que pot mostrar
 * la llista TOP 50 o les «Més reproduïdes»: l'Inici, la pestanya Llistes de la Biblioteca, l'àlbum obert (si és una llista) i la Cerca (el mapa d'àlbums; l'índex, només si
 * la TOP 50 apareix o desapareix). Els àlbums de joc no canvien (`refaLlistes` conserva els mateixos objectes).
 */
object LlistaTop { var versio by mutableIntStateOf(0) }

/** El joc (àlbum) d'on surt la cançó que sona: si es reprodueix des d'una llista és el de la cançó, no la llista; si no, l'àlbum mateix. */
fun jocDeCanco(b: Biblioteca, e: Emissora?, seq: Int): Emissora? {
    if (e == null || !e.esLlista) return e
    val k = b.clauDe(e, seq)
    val nom = cat.gbdsmixxx.RadioDades.emissoraDe(k)
    return b.perNom(nom) ?: e
}

/**
 * Color llavor de la portada de cada àlbum (per al color dinàmic): es calcula fora del fil principal a partir de la portada ja carregada (`Portades`),
 * reduïda a 48x48, amb `ColorPortada.llavor` (Kotlin pur). Memòria cau en memòria (un Int per àlbum). Sense portada → null (paleta de reserva).
 */
object ColorsAlbum {
    var versio by mutableIntStateOf(0); private set
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Int>()          // 0 = la portada no té color (reserva)
    private val enCurs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val exec = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "colors").also { it.isDaemon = true; it.priority = Thread.MIN_PRIORITY } }
    private val fil = Handler(Looper.getMainLooper())

    @Composable
    fun llavor(e: Emissora?): Int? {
        if (e == null) return null
        val ctx = LocalContext.current
        @Suppress("UNUSED_VARIABLE") val v = versio
        cache[e.clauDades]?.let { return if (it == 0) null else it }
        val bm = Portades.bitmap(ctx, e) ?: return null
        if (enCurs.add(e.clauDades)) {
            val nom = e.clauDades
            exec.execute {
                val c = try { extreu(bm) } catch (x: Throwable) { null }
                cache[nom] = c ?: 0; enCurs.remove(nom)
                fil.post { versio++ }
            }
        }
        return null
    }

    private fun extreu(bm: Bitmap): Int? {
        val s = if (bm.width > 48 || bm.height > 48) Bitmap.createScaledBitmap(bm, 48, 48, true) else bm
        val px = IntArray(s.width * s.height)
        s.getPixels(px, 0, s.width, 0, 0, s.width, s.height)
        return ColorPortada.llavor(px)
    }
}
