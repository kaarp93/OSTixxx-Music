package cat.ostixxx.music

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.AccesFitxers
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Informe
import cat.gbdsmixxx.ModeCarrega
import cat.gbdsmixxx.RomsConsoles
import cat.gbdsmixxx.t

/**
 * Bloc de càrrega (2026-11-16) a Ajustos > Biblioteca: «Accés a tots els fitxers» (les ROM de GB/NES/SNES es llegeixen d'on són, sense copiar-les), «Revisa les carpetes»
 * (només quan l'usuari ho demana), «Refer biblioteca» (emergència) i «Mode antic» (TEMPORAL, xarxa de seguretat).
 * UI (2026-11-17): només ha canviat la presentació (interruptors i botons tonals del disseny nou); la lògica (`feina`, `AccesFitxers`, `ModeCarrega`) és la mateixa.
 */
@Composable
fun BlocCarrega(ctx: Context) {
    App.versio
    val acces = AccesFitxers.te(ctx)
    var confirmaRefer by remember { mutableStateOf(false) }
    var antic by remember { mutableStateOf(ModeCarrega.antic) }
    Tx(t("Les ROM de Game Boy, NES i SNES es llegeixen d'on són, com un reproductor de música: no se'n fa cap còpia. Per fer-ho cal «Accés a tots els fitxers»; sense, es copien dins l'app com abans."), Tipus.petit, M.tenu, linies = 6)
    // l'interruptor obre la pantalla del sistema (el permís només es dona o es treu allà)
    FilaInterruptor(t("Accés a tots els fitxers"), acces) { (ctx as? android.app.Activity)?.let { AccesFitxers.demana(it) } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotoTonal(t("Revisa les carpetes"), CamiIcones.ACTUALITZA, Modifier.weight(1f)) { feina(ctx, false) }
        if (!confirmaRefer) BotoTonal(t("Refer biblioteca"), CamiIcones.BIBLIOTECA, Modifier.weight(1f)) { confirmaRefer = true }
    }
    if (confirmaRefer) {
        Tx(t("Refer biblioteca torna a llegir totes les ROM (pot trigar). No s'esborra cap fitxer ni cap marca."), Tipus.petit, M.tenu, linies = 4)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotoPle(t("Segur?") + " " + t("Sí"), CamiIcones.FET, Modifier.weight(1f)) { confirmaRefer = false; feina(ctx, true) }
            BotoTonal(t("Cancel·la"), null, Modifier.weight(1f)) { confirmaRefer = false }
        }
    }
    FilaInterruptor(t("Mode antic (temporal)"), antic, if (antic) t("Mode antic: només les ROM copiades dins l'app, com abans. És temporal: es traurà quan el mode nou estigui provat.") else null) {
        antic = !antic; ModeCarrega.posaAntic(antic); App.carrega(ctx)
    }
}

/** Revisa les carpetes o refà la biblioteca en un fil apart; en acabar, missatge, informe i biblioteca nova. */
internal fun feina(ctx: Context, refer: Boolean) {
    if (ModeCarrega.antic) { App.missatge = t("Amb el mode antic no hi ha carpetes"); return }
    val app = ctx.applicationContext
    val fil = android.os.Handler(android.os.Looper.getMainLooper())
    cat.gbdsmixxx.AnalisiTotal.atura()
    App.missatge = if (refer) t("Refent la biblioteca...") else t("Revisant les carpetes")
    Thread {
        val informe = ArrayList<Pair<Boolean, String>>()
        val (bons, total) = try {
            if (refer) RomsConsoles.referBiblioteca(app, informe) { nom -> fil.post { App.missatge = nom } }
            else RomsConsoles.revisa(app, informe) { nom -> fil.post { App.missatge = nom } }
        } catch (e: Throwable) { informe.add(false to e.javaClass.simpleName + ": " + (e.message ?: "").take(160)); 0 to 0 }
        Informe.desaErrors(app, informe)
        fil.post { App.missatge = t("Importats:") + " $bons/$total"; Informe.linies = informe; App.carrega(app) }
    }.also { it.isDaemon = true }.start()
}
