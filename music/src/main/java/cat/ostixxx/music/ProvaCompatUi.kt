package cat.ostixxx.music

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.AnalisiAturada
import cat.gbdsmixxx.App
import cat.gbdsmixxx.CatalegDefinitiu
import cat.gbdsmixxx.CrcDrivers
import cat.gbdsmixxx.Emissora
import cat.gbdsmixxx.EntradaRom
import cat.gbdsmixxx.MemoriaRoms
import cat.gbdsmixxx.ProvaCompat
import cat.gbdsmixxx.RegistreDrivers
import cat.gbdsmixxx.RomsConsoles
import cat.gbdsmixxx.RomsMusica
import cat.gbdsmixxx.SistemaDriver
import cat.gbdsmixxx.TitolCataleg
import cat.gbdsmixxx.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * «Prova compatibilitat» a Music (l'autor, 2026-11-22). La lògica és a `:consoles` (`ProvaCompat`, `CandidatsDrivers`); aquí hi ha la UI i el pont amb Android:
 * - Ajustos > Biblioteca (`BlocProvaCompat`): botó per triar UNA ROM de GB/GBC o NES: si ja té driver ho diu; si no, l'analitza (avís abans), progrés i «Cancel·la».
 * - Àlbum d'un candidat (`BlocCandidat`): «Auto (sense validar)», «Confirma el driver» (només el driver; passa a definitiu) i «Descarta».
 */
object ProvaCompatApp {
    private val principal = Handler(Looper.getMainLooper())
    @Volatile private var iniciat = false

    /** Una sola vegada: prioritat del fil i memòries cau que es buiden en acabar (cache drop). */
    fun inicia() {
        if (iniciat) return
        iniciat = true
        ProvaCompat.alIniciarFil = { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST) }
        ProvaCompat.alliberadors.add { RomsMusica.buidaCaches() }          // ROM de GBA/DS i, pel proveïdor de consoles, `RomsConsoles.buidaCaches` (inclou `MemoriaRoms.buida`)
        ProvaCompat.alliberadors.add { MemoriaRoms.buida() }
    }

    private fun sis(c: String) = if (c == "NES") SistemaDriver.NES else SistemaDriver.GB
    /** Màxim que es llegeix d'un fitxer triat (una ROM de GB o NES és molt més petita). */
    private const val MAX_BYTES = 16 shl 20
    private fun llegeix(ctx: Context, uri: android.net.Uri): ByteArray? = try {
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(65536); var n = 0
            while (true) { val r = ins.read(buf); if (r < 0) break; n += r; if (n > MAX_BYTES) return null; out.write(buf, 0, r) }
            out.toByteArray()
        }
    } catch (x: Throwable) { null }
    /** «NES» per la capçalera iNES, «GB» pel logo de Nintendo de la capçalera de Game Boy/GBC; null si no és cap de les dues. */
    fun consolaDe(b: ByteArray): String? = when {
        b.size > 16 && b[0] == 'N'.code.toByte() && b[1] == 'E'.code.toByte() && b[2] == 'S'.code.toByte() && b[3] == 0x1A.toByte() -> "NES"
        b.size > 0x150 && b[0x104] == 0xCE.toByte() && b[0x105] == 0xED.toByte() && b[0x106] == 0x66.toByte() && b[0x107] == 0x66.toByte() -> "GB"
        else -> null
    }
    class Triada(val crc: Long, val nom: String, val consola: String)

    /**
     * Mira el fitxer triat (fora del fil principal): torna la ROM a analitzar o, si no es pot, el motiu (ja traduït) per mostrar-lo.
     * Si la ROM ja té driver (de l'app, importat o candidat) no cal analitzar-la.
     */
    fun miraFitxer(ctx: Context, uri: android.net.Uri): Pair<Triada?, String> {
        val b = llegeix(ctx, uri) ?: return null to t("No s'ha pogut llegir el fitxer")
        val cons = consolaDe(b) ?: return null to t("No és una ROM de Game Boy ni de NES")
        val crc = MemoriaRoms.crc32(b)
        val crcTxt = "CRC32 %08X".format(crc)
        if (CrcDrivers.POKEMON[crc] != null || RegistreDrivers.candidatsPerCrc(crc).any { it.sistema == sis(cons) })
            return null to t("Aquesta ROM ja té driver: no cal analitzar-la") + " (" + crcTxt + ")"
        return Triada(crc, RomsMusica.nomFitxer(ctx, uri) ?: "%08X".format(crc), cons) to ""
    }

    /** Engega la prova d'una ROM triada (després de l'avís). Torna false si n'hi ha una en curs. */
    fun engega(ctx: Context, uri: android.net.Uri, r: Triada): Boolean {
        inicia()
        val app = ctx.applicationContext
        val nom = r.nom
        Drivers.traca("prova compatibilitat: engega " + nom)
        // la ROM es torna a llegir dins del fil de la prova (no es reté durant l'avís); el CRC es comprova en llegir-la
        return ProvaCompat.engega(r.crc, nom, r.consola, ProvaCompat.Limits(), { llegeix(app, uri) ?: ByteArray(0) }) { res ->
            // fil de la prova, després del cache drop
            val msg = when (res) {
                is ProvaCompat.Resultat.Candidat -> try { Drivers.desaCandidat(app, res.def); t("Driver candidat trobat") + ": " + res.def.noms.size + " " + t("cançons") + " · " + t("Auto (sense validar)") } catch (x: Throwable) { t("No s'ha pogut desar el candidat") + " (" + x.javaClass.simpleName + ")" }
                is ProvaCompat.Resultat.Cap -> t("No s'ha trobat cap driver per a aquesta ROM")
                is ProvaCompat.Resultat.Aturada -> when (res.motiu) {
                    AnalisiAturada.Motiu.CANCELLADA -> t("Prova cancel·lada")
                    AnalisiAturada.Motiu.TEMPS -> t("Prova aturada: s'ha acabat el temps")
                    AnalisiAturada.Motiu.MEMORIA -> t("Prova aturada: massa memòria")
                }
                is ProvaCompat.Resultat.Error -> t("La prova ha fallat") + " (" + res.motiu + ")"
            }
            Drivers.traca("prova compatibilitat: " + nom + " → " + msg)
            principal.post {
                App.missatge = msg
                android.widget.Toast.makeText(app, msg, android.widget.Toast.LENGTH_LONG).show()
                if (res is ProvaCompat.Resultat.Candidat) App.carrega(app)
            }
        }
    }

    /** Id del driver d'un àlbum de consola (de la clau estable «CRC:tipus:clau:rev»). */
    fun idDe(e: Emissora): String? = e.estable?.substringAfter(':', "")?.takeIf { it.isNotEmpty() }
    fun esCandidat(e: Emissora): Boolean = idDe(e)?.let { RegistreDrivers.esCandidat(it) } == true
    private fun midaDe(ctx: Context, crc: Long): Long? = try { val idx = RomsConsoles.index(ctx); synchronized(idx) { idx.entrades[crc]?.mida } } catch (x: Throwable) { null }
    /** L'àlbum ja és definitiu al catàleg («Valida del tot»): no mostra C S J ✗ ni ✓/✗. */
    fun esDefinitiu(ctx: Context, e: Emissora): Boolean {
        val crc = e.crc ?: return false
        if (esCandidat(e)) return false
        val m = midaDe(ctx, crc) ?: return false
        return try { CatalegDefinitiu.esDefinitiu(crc, m) } catch (x: Throwable) { false }
    }

    /** «Confirma el driver»: NOMÉS el driver (el candidat passa a driver permanent); validar etiquetes, noms i ordre és «Valida del tot», a part (l'autor, 2026-11-23). */
    fun confirma(ctx: Context, e: Emissora) {
        val app = ctx.applicationContext
        val crc = e.crc ?: return; val id = idDe(e) ?: return
        val nom = RegistreDrivers.perId(id)?.titol ?: e.vista
        Thread {
            val ok = try { Drivers.confirmaCandidat(app, crc, midaDe(app, crc) ?: 0L, nom, emptyMap()) } catch (x: Throwable) { false }
            principal.post {
                App.missatge = if (ok) t("Driver confirmat") else t("No s'ha pogut confirmar el driver")
                android.widget.Toast.makeText(app, App.missatge, android.widget.Toast.LENGTH_SHORT).show()
                App.carrega(app)
            }
        }.also { it.isDaemon = true }.start()
    }

    fun descarta(ctx: Context, e: Emissora, despres: () -> Unit) {
        val app = ctx.applicationContext
        val crc = e.crc ?: return
        Thread {
            try { Drivers.descartaCandidat(app, crc) } catch (x: Throwable) { }
            principal.post { App.missatge = t("Candidat descartat"); despres(); App.carrega(app) }
        }.also { it.isDaemon = true }.start()
    }
}

/** Ajustos > Biblioteca: botó per triar UNA ROM; si ja té driver ho diu i, si no, l'analitza (una alhora), amb progrés i «Cancel·la». */
@Composable
fun BlocProvaCompat(ctx: Context) {
    var tic by remember { mutableIntStateOf(0) }
    val f = ProvaCompat.feina()
    // mentre n'hi ha una en curs, es refresca el progrés cada mig segon
    LaunchedEffect(f != null) { while (ProvaCompat.ocupat) { delay(500); tic++ }; tic++ }
    val enCurs = t("Ja n'hi ha una en curs: només es pot analitzar una ROM alhora. Espera que acabi o cancel·la-la.")
    val tria = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) Thread {
            // PlayStation (l'autor, 2026-11-23): una imatge de disc (.bin/.iso/.cue) s'analitza buscant-hi la música XA i CD àudio i s'afegeix a la biblioteca
            if (cat.gbdsmixxx.Ps1.esPs1(ctx, u)) {
                Handler(Looper.getMainLooper()).post { android.widget.Toast.makeText(ctx, t("Disc de PlayStation: buscant-hi la música (XA i CD àudio)..."), android.widget.Toast.LENGTH_LONG).show(); App.importa(ctx, listOf(u)) }
                return@Thread
            }
            val (r, motiu) = try { ProvaCompatApp.miraFitxer(ctx, u) } catch (x: Throwable) { null to t("No s'ha pogut llegir el fitxer") }
            Handler(Looper.getMainLooper()).post {
                if (r == null) android.widget.Toast.makeText(ctx, motiu, android.widget.Toast.LENGTH_LONG).show()
                else avisa(ctx) { if (!ProvaCompatApp.engega(ctx, u, r)) android.widget.Toast.makeText(ctx, enCurs, android.widget.Toast.LENGTH_LONG).show(); tic++ }
            }
        }.also { it.isDaemon = true }.start()
    }
    Tx(t("Prova compatibilitat"), Tipus.cosFort, M.text)
    Tx(t("Tria una ROM de Game Boy o NES: si ja té driver, t'ho diu; si no, l'analitzador en pot buscar un (experimental). Només una alhora."), Tipus.petit, M.tenu, linies = 4)
    Tx(t("També una imatge de PlayStation (.bin, .iso o .cue): s'hi busca la música XA i de CD àudio. Cal «Accés a tots els fitxers»."), Tipus.petit, M.tenu, linies = 3)
    if (f != null) {
        tic
        val seg = f.ms / 1000; val lim = f.limitMs / 1000
        val fase = when (f.fase) { "joc" -> t("executant el joc") + " (" + f.fotograma + ")"; "fred" -> t("provant cançons"); "ram" -> t("provant la RAM"); else -> t("començant") }
        Tx(t("Analitzant") + " «" + f.nom + "»: " + fase + " · " + "%d:%02d / %d:%02d".format(seg / 60, seg % 60, lim / 60, lim % 60), Tipus.petit, M.text, linies = 3)
        BotoPle(t("Cancel·la"), CamiIcones.TANCA, Modifier.fillMaxWidth()) { ProvaCompat.cancela() }
    }
    // desactivat (i amb explicació) si ja n'hi ha una en curs
    BotoTonal(t("Importa una ROM per analitzar"), CamiIcones.CARPETA, Modifier.fillMaxWidth(), color = if (f == null) M.suau else M.supAlta, sobre = if (f == null) M.sobreSuau else M.tenu) {
        if (ProvaCompat.ocupat) android.widget.Toast.makeText(ctx, enCurs, android.widget.Toast.LENGTH_LONG).show()
        else tria.launch(arrayOf("*/*"))
    }
}

/** Avís abans d'engegar: experimental, pot fallar i gasta molts recursos. */
private fun avisa(ctx: Context, continua: () -> Unit) {
    android.app.AlertDialog.Builder(ctx)
        .setTitle(t("Prova compatibilitat") + " (" + t("experimental") + ")")
        .setMessage(t("És EXPERIMENTAL i pot FALLAR. Gasta MOLTS RECURSOS: CPU, bateria i memòria, durant minuts (fins a 10). Pots cancel·lar-la quan vulguis a Ajustos > Biblioteca. Si troba un driver, l'àlbum surt a la biblioteca com a «Auto (sense validar)»."))
        .setNegativeButton(t("Cancel·la"), null)
        .setPositiveButton(t("Continua")) { _, _ -> continua() }
        .show()
}

/** A l'àlbum d'un candidat: avís «sense validar», «Confirma el driver» i «Descarta». */
@Composable
fun BlocCandidat(ctx: Context, e: Emissora, tanca: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Tx(t("Auto (sense validar)") + ": " + t("driver trobat per «Prova compatibilitat». Escolta'l: si sona bé, confirma'l; si no, descarta'l. Si no el confirmes, s'esborra sol en 7 dies."), Tipus.petit, M.tenu, linies = 5)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotoPle(t("Confirma el driver"), CamiIcones.FET, Modifier.weight(1f)) { ProvaCompatApp.confirma(ctx, e) }
            BotoTonal(t("Descarta"), CamiIcones.ESBORRA, Modifier.weight(1f)) { ProvaCompatApp.descarta(ctx, e, tanca) }
        }
    }
}

/** Textos de «Prova compatibilitat»: [català (clau), anglès, castellà]. Es registren a `MusicActivity`. */
val TEXTOS_PROVA_COMPAT = listOf(
    arrayOf("Prova compatibilitat", "Compatibility test", "Prueba de compatibilidad"),
    arrayOf("Tria una ROM de Game Boy o NES: si ja té driver, t'ho diu; si no, l'analitzador en pot buscar un (experimental). Només una alhora.", "Pick a Game Boy or NES ROM: if it already has a driver, it tells you; if not, the analyser can look for one (experimental). Only one at a time.", "Elige una ROM de Game Boy o NES: si ya tiene driver, te lo dice; si no, el analizador puede buscar uno (experimental). Solo una a la vez."),
    arrayOf("Disc de PlayStation: buscant-hi la música (XA i CD àudio)...", "PlayStation disc: looking for its music (XA and CD audio)...", "Disco de PlayStation: buscando su música (XA y CD audio)..."),
    arrayOf("També una imatge de PlayStation (.bin, .iso o .cue): s'hi busca la música XA i de CD àudio. Cal «Accés a tots els fitxers».", "Also a PlayStation image (.bin, .iso or .cue): its XA and CD audio music is searched. Needs \"All files access\".", "También una imagen de PlayStation (.bin, .iso o .cue): se busca su música XA y de CD audio. Hace falta «Acceso a todos los archivos»."),
    arrayOf("Importa una ROM per analitzar", "Import a ROM to analyse", "Importar una ROM para analizar"),
    arrayOf("No s'ha pogut llegir el fitxer", "The file could not be read", "No se ha podido leer el archivo"),
    arrayOf("No és una ROM de Game Boy ni de NES", "It is not a Game Boy or NES ROM", "No es una ROM de Game Boy ni de NES"),
    arrayOf("Aquesta ROM ja té driver: no cal analitzar-la", "This ROM already has a driver: no need to analyse it", "Esta ROM ya tiene driver: no hace falta analizarla"),
    arrayOf("executant el joc", "running the game", "ejecutando el juego"),
    arrayOf("provant cançons", "testing songs", "probando canciones"),
    arrayOf("provant la RAM", "testing the RAM", "probando la RAM"),
    arrayOf("començant", "starting", "empezando"),
    arrayOf("Analitzant", "Analysing", "Analizando"),
    arrayOf("Ja n'hi ha una en curs: només es pot analitzar una ROM alhora. Espera que acabi o cancel·la-la.", "One is already running: only one ROM can be analysed at a time. Wait for it to finish or cancel it.", "Ya hay una en curso: solo se puede analizar una ROM a la vez. Espera a que acabe o cancélala."),
    arrayOf("experimental", "experimental", "experimental"),
    arrayOf("És EXPERIMENTAL i pot FALLAR. Gasta MOLTS RECURSOS: CPU, bateria i memòria, durant minuts (fins a 10). Pots cancel·lar-la quan vulguis a Ajustos > Biblioteca. Si troba un driver, l'àlbum surt a la biblioteca com a «Auto (sense validar)».",
        "It is EXPERIMENTAL and may FAIL. It uses A LOT OF RESOURCES: CPU, battery and memory, for minutes (up to 10). You can cancel it at any time in Settings > Library. If it finds a driver, the album appears in the library as \"Auto (not validated)\".",
        "Es EXPERIMENTAL y puede FALLAR. Gasta MUCHOS RECURSOS: CPU, batería y memoria, durante minutos (hasta 10). Puedes cancelarla cuando quieras en Ajustes > Biblioteca. Si encuentra un driver, el álbum sale en la biblioteca como «Auto (sin validar)»."),
    arrayOf("Continua", "Continue", "Continuar"),
    arrayOf("Driver candidat trobat", "Candidate driver found", "Driver candidato encontrado"),
    arrayOf("Auto (sense validar)", "Auto (not validated)", "Auto (sin validar)"),
    arrayOf("No s'ha pogut desar el candidat", "The candidate could not be saved", "No se ha podido guardar el candidato"),
    arrayOf("No s'ha trobat cap driver per a aquesta ROM", "No driver was found for this ROM", "No se ha encontrado ningún driver para esta ROM"),
    arrayOf("Prova cancel·lada", "Test cancelled", "Prueba cancelada"),
    arrayOf("Prova aturada: s'ha acabat el temps", "Test stopped: out of time", "Prueba detenida: se ha acabado el tiempo"),
    arrayOf("Prova aturada: massa memòria", "Test stopped: too much memory", "Prueba detenida: demasiada memoria"),
    arrayOf("La prova ha fallat", "The test failed", "La prueba ha fallado"),
    arrayOf("Driver confirmat", "Driver confirmed", "Driver confirmado"),
    arrayOf("No s'ha pogut confirmar el driver", "The driver could not be confirmed", "No se ha podido confirmar el driver"),
    arrayOf("Candidat descartat", "Candidate discarded", "Candidato descartado"),
    arrayOf("driver trobat per «Prova compatibilitat». Escolta'l: si sona bé, confirma'l; si no, descarta'l. Si no el confirmes, s'esborra sol en 7 dies.",
        "driver found by \"Compatibility test\". Listen to it: if it sounds right, confirm it; if not, discard it. If you don't confirm it, it is deleted after 7 days.",
        "driver encontrado por «Prueba de compatibilidad». Escúchalo: si suena bien, confírmalo; si no, descártalo. Si no lo confirmas, se borra solo en 7 días."),
    arrayOf("Confirma el driver", "Confirm driver", "Confirmar driver"),
    arrayOf("Descarta", "Discard", "Descartar"),
)
