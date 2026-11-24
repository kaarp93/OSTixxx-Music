package cat.ostixxx.music

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cat.gbdsmixxx.App
import cat.gbdsmixxx.CatalegConsoles
import cat.gbdsmixxx.CatalegDefinitiu
import cat.gbdsmixxx.DadesUsuari
import cat.gbdsmixxx.Durable
import cat.gbdsmixxx.ErrorOstx
import cat.gbdsmixxx.Informe
import cat.gbdsmixxx.Ostx
import cat.gbdsmixxx.RadioDades
import cat.gbdsmixxx.RomsConsoles
import cat.gbdsmixxx.RomsMusica
import cat.gbdsmixxx.SeccioIndex
import cat.gbdsmixxx.SeccioPrefs
import cat.gbdsmixxx.t
import java.io.File

/**
 * Còpia de seguretat de l'usuari, fitxer `.ostx` (l'autor, 2026-11-22; format a `Ostx`): catàleg definitiu, dades de l'usuari, dades de la ràdio, drivers importats,
 * preferències i índex de la biblioteca (sense cap ROM, portada ni dada de libretro). Ajustos > «Còpia de seguretat».
 * Importar: abans de tocar res es copia cada fitxer afectat a `<nom>.abans_ostx`; per defecte FUSIONA sense esborrar res; «Substitueix tot» (amb confirmació) fa que
 * guanyin les dades importades (l'índex de ROM sempre es fusiona: mai es treu una ROM d'aquí). Tot fora del fil principal, menys la ràdio (on la UI toca les seves dades).
 */
object CopiaSeguretat {
    private val principal = Handler(Looper.getMainLooper())
    private const val PREFS = "gbdsmixxx"

    fun nomProposat(): String = "ostixxx-cataleg-" + java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date()) + "." + Ostx.EXTENSIO

    /** Quantitat de cada secció d'aquest mòbil (per a Ajustos). Fora del fil principal. */
    fun quantitats(ctx: Context): List<Pair<String, Int>> {
        val app = ctx.applicationContext
        CatalegConsoles.inicia(app)
        val idx = try { RomsConsoles.index(app).llista().size } catch (e: Throwable) { -1 }
        val falten = try { CatalegConsoles.falten(app).llista().size } catch (e: Throwable) { 0 }
        return listOf(
            t("Catàleg") to CatalegDefinitiu.nombre(),
            t("Dades de l'usuari") to (try { DadesUsuari.de(app).mida() } catch (e: Throwable) { -1 }),
            t("Llistes i dades de la ràdio") to RadioDades.mida(),
            t("Drivers importats") to Drivers.nImportats(),
            t("Preferències") to app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.keys.count { SeccioPrefs.exportable(it) },
            t("ROM de la biblioteca") to idx,
            t("ROM que falten") to falten,
        )
    }

    /** Fa la còpia a `uri` (triat amb SAF). La ràdio es copia ara (fil principal); la resta, en un fil apart. `fet` rep el missatge (al fil principal). */
    fun exporta(ctx: Context, uri: Uri, fet: (String) -> Unit) {
        val app = ctx.applicationContext
        val radio = try { RadioDades.exportaBytes() } catch (e: Throwable) { null }
        Thread {
            val msg = try {
                CatalegConsoles.inicia(app)
                val s = ArrayList<Ostx.Seccio>()
                s.add(CatalegDefinitiu.seccio())
                s.add(Ostx.Seccio(Ostx.USUARI, 1, DadesUsuari.de(app).exportaBytes()))
                radio?.let { s.add(Ostx.Seccio(Ostx.RADIO, 1, it)) }
                Drivers.bytesImportats(app)?.let { s.add(Ostx.Seccio(Ostx.DRIVERS, 1, it)) }
                s.add(Ostx.Seccio(Ostx.PREFS, SeccioPrefs.VERSIO, SeccioPrefs.exporta(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all)))
                val fl = CatalegConsoles.falten(app)
                s.add(Ostx.Seccio(Ostx.INDEX, SeccioIndex.VERSIO, RomsConsoles.ambIndex(app) { g -> SeccioIndex.exporta(g.idx, fl) }))
                // es munta sencera a la memòria i s'escriu d'un sol cop (SAF no deixa fer un canvi de nom atòmic)
                val bo = java.io.ByteArrayOutputStream(); Ostx.escriu(bo, s)
                app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bo.toByteArray()); it.flush() }
                t("Còpia desada") + " (" + s.size + " " + t("seccions") + ", " + (bo.size() / 1024) + " KB)"
            } catch (e: Throwable) { t("No s'ha pogut desar la còpia") + " (" + e.javaClass.simpleName + ")" }
            principal.post { fet(msg) }
        }.also { it.isDaemon = true }.start()
    }

    private fun copiaAbans(f: File) { if (f.isFile) try { f.copyTo(File(f.path + ".abans_ostx"), true) } catch (e: Throwable) { } }

    /** Text de l'error d'un `.ostx`. */
    fun motiu(e: Throwable): String = when ((e as? ErrorOstx)?.motiu) {
        ErrorOstx.NO_OSTX -> t("No és una còpia d'OSTixxx (.ostx)")
        ErrorOstx.MES_NOU -> t("Aquesta còpia és d'una versió més nova de l'app: actualitza-la abans")
        ErrorOstx.MALMES -> t("La còpia està malmesa")
        ErrorOstx.SENSE_CATALEG -> t("La còpia no porta cap catàleg")
        else -> t("No s'ha pogut llegir la còpia") + " (" + e.javaClass.simpleName + ")"
    }

    /**
     * Importa una còpia `.ostx`. `fet` rep (al fil principal) el resum i si cal proposar «Revisa les carpetes» (hi ha ROM que falten). Cada secció es fa a part: si una
     * falla, les altres continuen i es diu al resum. En acabar es refà la biblioteca.
     */
    fun importa(ctx: Context, uri: Uri, substitueix: Boolean, fet: (List<Pair<Boolean, String>>, Boolean) -> Unit) {
        val app = ctx.applicationContext
        val nomF = try { RomsMusica.nomFitxer(app, uri) } catch (e: Throwable) { null }
        if (nomF != null && !nomF.lowercase().endsWith("." + Ostx.EXTENSIO)) { fet(listOf(false to t("No és una còpia d'OSTixxx (.ostx)") + ": " + nomF), false); return }
        Thread {
            val r = ArrayList<Pair<Boolean, String>>(); var falten = 0
            val lectura = try { app.contentResolver.openInputStream(uri)!!.use { Ostx.llegeix(it) } } catch (e: Throwable) { r.add(false to motiu(e)); null }
            if (lectura != null) {
                if (lectura.trencades > 0) r.add(false to lectura.trencades.toString() + " " + t("seccions malmeses ignorades"))
                CatalegConsoles.inicia(app)
                for (s in lectura.seccions) {
                    try {
                        when (s.tipus) {
                            Ostx.CATALEG -> { copiaAbans(File(app.filesDir, CatalegDefinitiu.NOM_FITXER)); CatalegDefinitiu.espera(); val n = CatalegDefinitiu.importaSeccio(s, substitueix); CatalegDefinitiu.espera(); r.add(true to t("Catàleg") + ": " + n + " " + t("jocs afegits o actualitzats")) }
                            Ostx.USUARI -> { if (s.versio > 1) throw ErrorOstx(ErrorOstx.MES_NOU); copiaAbans(File(app.filesDir, "dades_usuari.bin")); val n = DadesUsuari.de(app).importaBytes(s.dades, substitueix); r.add(true to t("Dades de l'usuari") + ": " + n + " " + t("canvis")) }
                            Ostx.RADIO -> {
                                if (s.versio > 1) throw ErrorOstx(ErrorOstx.MES_NOU)
                                val l = java.util.concurrent.CountDownLatch(1); var n = -1; var err: Throwable? = null
                                principal.post { try { n = RadioDades.importaBytes(s.dades, substitueix) } catch (e: Throwable) { err = e }; l.countDown() }
                                l.await(30, java.util.concurrent.TimeUnit.SECONDS); err?.let { throw it }
                                r.add(true to t("Llistes i dades de la ràdio") + ": " + n + " " + t("canvis"))
                            }
                            Ostx.DRIVERS -> { if (s.versio > 1) throw ErrorOstx(ErrorOstx.MES_NOU); val n = Drivers.importaPaquet(app, s.dades, substitueix); r.add(true to t("Drivers importats") + ": " + n + " " + t("nous")) }
                            Ostx.PREFS -> {
                                if (s.versio > SeccioPrefs.VERSIO) throw ErrorOstx(ErrorOstx.MES_NOU)
                                val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                val imp = SeccioPrefs.llegeix(s.dades)
                                val nous = SeccioPrefs.aEscriure(sp.all, imp, substitueix)
                                if (nous.isNotEmpty()) {
                                    try { Durable.escriu(File(app.filesDir, "preferencies.abans_ostx"), Ostx.PREFS, SeccioPrefs.VERSIO, SeccioPrefs.exporta(sp.all)) } catch (e: Throwable) { }
                                    val ed = sp.edit()
                                    @Suppress("UNCHECKED_CAST")
                                    for ((k, v) in nous) when (v) { is Int -> ed.putInt(k, v); is Long -> ed.putLong(k, v); is Float -> ed.putFloat(k, v); is Boolean -> ed.putBoolean(k, v); is String -> ed.putString(k, v); is Set<*> -> ed.putStringSet(k, v as Set<String>) }
                                    ed.commit()
                                }
                                r.add(true to t("Preferències") + ": " + nous.size + " " + t("canvis") + (if (nous.isNotEmpty()) " (" + t("tanca i torna a obrir l'app perquè s'apliquin totes") + ")" else ""))
                            }
                            Ostx.INDEX -> {
                                if (s.versio > SeccioIndex.VERSIO) throw ErrorOstx(ErrorOstx.MES_NOU)
                                val fl = CatalegConsoles.falten(app)
                                copiaAbans(File(RomsMusica.dir(app), "index_roms.bin")); copiaAbans(fl.f)
                                val rs = RomsConsoles.ambIndex(app) { g -> SeccioIndex.importa(s.dades, g.idx, fl) }
                                fl.desa()
                                falten = fl.llista().size
                                r.add(true to t("Àlbums restaurats") + ": " + rs.restaurades.size + " · " + t("ja hi eren") + ": " + rs.jaHiEren + " · " + t("carpetes noves") + ": " + rs.carpetesNoves)
                                if (rs.falten.isNotEmpty()) {
                                    r.add(false to t("ROM que falten") + ": " + rs.falten.size)
                                    for (e in rs.falten) r.add(false to "  " + (CatalegDefinitiu.troba(e.crc, e.mida)?.nom?.ifBlank { null } ?: e.titol.ifBlank { null } ?: (e.consola + " %08X".format(e.crc))))
                                }
                            }
                            else -> r.add(false to t("Secció desconeguda ignorada") + ": " + s.nom)
                        }
                    } catch (e: Throwable) { r.add(false to s.nom + ": " + motiu(e)) }
                }
            }
            principal.post { Informe.linies = r; fet(r, falten > 0); App.carrega(app) }
        }.also { it.isDaemon = true }.start()
    }
}

/** Bloc d'Ajustos > «Còpia de seguretat»: quantitat de cada secció, Exporta, Importa (fusió o «Substitueix tot» amb confirmació) i el resum de l'última importació. */
@Composable
fun BlocCopia(ctx: Context) {
    var versio by remember { mutableStateOf(0) }
    var quant by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var subst by remember { mutableStateOf(false) }
    var confirma by remember { mutableStateOf(false) }
    var resum by remember { mutableStateOf<List<Pair<Boolean, String>>>(emptyList()) }
    var revisa by remember { mutableStateOf(false) }
    var missatge by remember { mutableStateOf("") }
    LaunchedEffect(versio) { val app = ctx.applicationContext; Thread { val q = try { CopiaSeguretat.quantitats(app) } catch (e: Throwable) { emptyList() }; Handler(Looper.getMainLooper()).post { quant = q } }.also { it.isDaemon = true }.start() }
    val exporta = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) { u ->
        if (u != null) { missatge = t("Desant la còpia..."); CopiaSeguretat.exporta(ctx, u) { missatge = it } }
    }
    val importa = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { missatge = t("Llegint la còpia..."); resum = emptyList(); CopiaSeguretat.importa(ctx, u, subst) { r, f -> resum = r; revisa = f; missatge = ""; versio++ } }
    }
    Tx(t("Desa en un fitxer .ostx el catàleg, les marques, etiquetes i noms de pista, les llistes, els drivers importats, les preferències i la biblioteca (sense cap ROM ni portada)."), Tipus.petit, M.tenu, linies = 5)
    for ((nom, n) in quant) Tx(nom + ": " + (if (n < 0) "?" else n.toString()), Tipus.petit, M.text)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotoTonal(t("Exporta"), CamiIcones.DESCARREGA, Modifier.weight(1f)) { exporta.launch(CopiaSeguretat.nomProposat()) }
        if (!confirma) BotoTonal(t("Importa"), CamiIcones.CARPETA, Modifier.weight(1f)) { if (subst) confirma = true else importa.launch(arrayOf("*/*")) }
    }
    FilaInterruptor(t("Substitueix tot"), subst, if (subst) t("En importar, les dades de la còpia substitueixen les d'aquí (les ROM de la biblioteca no es treuen mai).") else null) { subst = !subst; confirma = false }
    if (confirma) {
        Tx(t("Les dades d'aquí se substituiran per les de la còpia. Abans se'n desa una còpia (.abans_ostx)."), Tipus.petit, M.tenu, linies = 4)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotoPle(t("Segur?") + " " + t("Sí"), CamiIcones.FET, Modifier.weight(1f)) { confirma = false; importa.launch(arrayOf("*/*")) }
            BotoTonal(t("Cancel·la"), null, Modifier.weight(1f)) { confirma = false }
        }
    }
    if (missatge.isNotEmpty()) Tx(missatge, Tipus.etiqueta, M.accent, linies = 3)
    for ((bo, l) in resum.take(60)) Tx(l, Tipus.petit, if (bo) M.text else M.mal, linies = 3)
    if (resum.size > 60) Tx("… +" + (resum.size - 60), Tipus.petit, M.tenu)
    if (revisa) BotoTonal(t("Revisa les carpetes"), CamiIcones.ACTUALITZA, Modifier.fillMaxWidth()) { revisa = false; feina(ctx, false) }
}
