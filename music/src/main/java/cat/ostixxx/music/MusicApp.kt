package cat.ostixxx.music

import android.app.Application
import cat.gbdsmixxx.Consoles
import cat.gbdsmixxx.ProveidorConsolesImpl
import cat.gbdsmixxx.RegistreExperimental
import cat.gbdsmixxx.RitmeRom

/** Aplicació d'OSTixxx Music: registra la música de consoles en arrencar el procés (Android Auto pot arrencar el servei de reproducció sense passar per l'activitat). */
class MusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Consoles.proveidor = ProveidorConsolesImpl
        cat.gbdsmixxx.Informe.build = BuildConfig.BUILD_ID
        RitmeRom.inicia(filesDir)                // anàlisi (bucle i durada) ja feta de les cançons de consoles, desada a disc
        Drivers.iniciaTraca(this)
        ProvaCompatApp.inicia()                  // «Prova compatibilitat»: prioritat del fil i memòries cau del cache drop
        // drivers: en un fil de fons es registren els experimentals compilats (tipus EXP) i es llegeixen només els índexs dels paquets binaris; la biblioteca els espera si cal
        Drivers.carregaEnFons(this) { RegistreExperimental.registra() }
        cat.gbdsmixxx.EtiquetaDriver.mostra = cat.gbdsmixxx.Env(this).int(cat.gbdsmixxx.EtiquetaDriver.CLAU_ENV, 1) == 1
        // les cançons etiquetades Sfx, Descarta o Jingle (o que el text suggereix com a sfx) no entren a les llistes automàtiques
        cat.gbdsmixxx.FiltreCancons.esSfx = { nom, id ->
            val et = Marques.etiqueta(nom, id)
            et == 2 || et == 3 || et == 4 || (et == 0 && cat.gbdsmixxx.App.bib?.let { b -> b.perNom(nom)?.let { Marques.suggeriment(b.etiq(it, id)) == 2 } } == true)
        }
        // anàlisi total (bucle i durada): només les cançons confirmades que funcionen; ni sfx, ni jingles, ni descartades, ni les no provades
        cat.gbdsmixxx.FiltreCancons.analitza = { nom, id ->
            val et = Marques.etiqueta(nom, id)
            et == 1 || (et == 0 && Marques.estat(nom) == 1 && !cat.gbdsmixxx.FiltreCancons.esSfx(nom, id))
        }
    }

    /**
     * Poca memòria (2026-11-22): es buiden les memòries cau que es poden refer. App amagada o memòria justa: les portades (bitmaps). Memòria crítica o app en
     * segon pla a la llista de les que Android pot tancar: també les ROM llegides (GBA/DS, GB, NES, SNES) i les fitxes de libretro. La cançó que sona no es toca:
     * el seu sintetitzador ja té la ROM i les dades; només un salt enrere o la cançó següent les tornaran a llegir del disc. L'anàlisi (RitmeRom) no es buida.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            if (level >= TRIM_MEMORY_RUNNING_LOW) cat.gbdsmixxx.Portades.netejaCache()
            if (level >= TRIM_MEMORY_BACKGROUND || level == TRIM_MEMORY_RUNNING_CRITICAL) {
                cat.gbdsmixxx.RomsMusica.buidaCaches()
                Libretro.buidaMemoria()
            }
        } catch (e: Throwable) { }
    }
}
