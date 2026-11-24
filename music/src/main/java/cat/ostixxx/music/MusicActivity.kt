package cat.ostixxx.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Consoles
import cat.gbdsmixxx.ProveidorConsolesImpl
import cat.gbdsmixxx.Env
import cat.gbdsmixxx.EfectesRadio
import cat.gbdsmixxx.I18n
import cat.gbdsmixxx.RadioDades
import cat.gbdsmixxx.RadioFreq

/** OSTixxx Music: només reproductor (àlbums = ROMs i fitxers de música, llistes de reproducció), amb els mateixos motors que OSTixxx (`:core`). */
class MusicActivity : ComponentActivity() {
    private val triaFitxers = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> App.importa(this, uris ?: emptyList()) }
    private val triaCarpeta = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) App.importaCarpeta(this, uri) }
    private val triaDrivers = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> Drivers.importa(this, uris ?: emptyList()) }
    private val permisNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onStart() { super.onStart(); cat.gbdsmixxx.EnPantalla.v = true; PantallaVisible.flux.value = true }
    override fun onStop() { super.onStop(); cat.gbdsmixxx.EnPantalla.v = false; PantallaVisible.flux.value = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // de vora a vora: el fons (amb el degradat del color de la portada) passa per sota de les barres del sistema; la UI hi deixa lloc amb els «insets»
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT; window.navigationBarColor = android.graphics.Color.TRANSPARENT
        I18n.afegeix(TEXTOS_MUSIC)
        I18n.afegeix(TEXTOS_UI)
        I18n.afegeix(TEXTOS_COPIA)
        I18n.afegeix(TEXTOS_PROVA_COMPAT)
        App.env = Env(this)
        Consoles.proveidor = ProveidorConsolesImpl
        App.amagaSenseBucle = App.env.int("amaga_sense_bucle", 1) == 1
        cat.gbdsmixxx.SnesSpc.fixF1 = App.env.int("snes_f1_fix", 1) == 1
        cat.gbdsmixxx.NesApu.sortidaFidel = App.env.int("nes_sortida_fidel", 1) == 1
        cat.gbdsmixxx.GbaSo.fidel = App.env.int(cat.gbdsmixxx.GbaSo.CLAU_ENV, 0) == 1
        cat.gbdsmixxx.RegioVideo.pal = App.env.int(cat.gbdsmixxx.RegioVideo.CLAU_ENV, 0) == 1
        RadioFreq.taux = App.env.int("radio_freq", 32768).takeIf { it in RadioFreq.OPCIONS } ?: 32768
        RadioDades.carrega(this)
        TemaUi.carrega(this)          // tema (sistema, clar, fosc o AMOLED); substitueix els aspectes d'abans (preferència `skin`, ja no es llegeix)
        Vis.canal = App.env.int("vis_mode", 0) == 1
        Vis.forma = App.env.int("vis_forma", 0).coerceIn(0, 2)
        Vis.color = App.env.int("vis_color", 0).coerceIn(0, ColorsVis.NOMS.size - 1)
        Vis.estil = App.env.int("vis_estil", 3).coerceIn(0, Vis.NOMS_ESTIL.size - 1)
        EfectesRadio.carrega()            // EQ, estèreo, limitador i reverb/chorus per consola (bloc 4); migra l'EQ vell (`eq_k`) a pla
        EfectesRadio.resolConsola = { nom -> consolaDeNom(nom) }
        EfectesRadio.habilitat = true
        I18n.afegeix(TEXTOS_EFECTES)
        I18n.afegeix(TEXTOS_CONCERT)
        Concert.carrega()                 // mode concert: l'última visualització (`concert_vis`)
        if (android.os.Build.VERSION.SDK_INT >= 33) permisNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        if (App.bib == null) App.carrega(this)
        setContent { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.LocalIndication provides SenseIndicacio) { MusicArrel({ triaFitxers.launch(arrayOf("*/*")) }, { triaCarpeta.launch(null) }, { triaDrivers.launch(arrayOf("*/*")) }) } }
    }
}
