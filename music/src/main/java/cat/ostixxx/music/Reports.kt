package cat.ostixxx.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import cat.gbdsmixxx.App
import cat.gbdsmixxx.cancons
import cat.gbdsmixxx.t

/**
 * Reports automàtics en TXT (l'autor, 2026-11-15): cada cop que canvia una etiqueta, un nom, una marca Bé/No bé o l'ordre d'un àlbum, el seu report es desa sol, com a `<joc>.txt`,
 * a la carpeta que l'autor triï (Ajustos > Reports), i també `_tots_els_reports.txt`. No es desa enlloc més. Accés per SAF: l'app només escriu a la carpeta triada.
 */
object Reports {
    private const val CLAU = "reports_dir"
    var estat = androidx.compose.runtime.mutableStateOf("")
    fun carpeta(): Uri? = null          // els reports en TXT s'han retirat (l'autor, 2026-11-24): no es desa res enlloc
    fun nomCarpeta(): String = carpeta()?.let { runCatching { Uri.decode(it.lastPathSegment ?: "") }.getOrDefault("") } ?: ""

    fun triaCarpeta(ctx: Context, uri: Uri?) {
        if (uri == null) return
        try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (e: Throwable) { }
        App.env.putStr(CLAU, uri.toString()); estat.value = ""
        desaTot()
    }
    fun treuCarpeta() { App.env.putStr(CLAU, ""); estat.value = "" }

    private fun nomFitxer(s: String) = s.map { if (it in "\\/:*?\"<>|") '_' else it }.joinToString("").trim().ifEmpty { "joc" } + ".txt"

    /** Desa el report d'un àlbum (i el de tots) en un fil apart; no fa res si no hi ha carpeta. */
    // Revisió UI (2026-11-22): abans cada etiqueta, nom o ordre obria un Thread que escrivia el report de l'àlbum i `_tots_els_reports.txt`; tocant C/S/J seguit
    // n'hi havia molts alhora escrivint els mateixos fitxers (i dos que no trobaven el fitxer en podien crear dos, «joc (1).txt»). Ara un sol fil en sèrie, i els àlbums
    // que es demanen mentre un desat és pendent s'ajunten en una sola passada (el report de tots, una vegada per passada).
    private val exec = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "reports").also { it.isDaemon = true } }
    private val pendents = java.util.LinkedHashSet<String>()          // sota `this`
    private var programat = false                                    // sota `this`
    fun desaAlbum(nom: String) {
        if (carpeta() == null) return
        synchronized(this) { pendents.add(nom); if (programat) return; programat = true }
        exec.execute {
            val ns = synchronized(this) { programat = false; ArrayList(pendents).also { pendents.clear() } }
            try { for (n in ns) desa(n); desaTotFitxer() } catch (e: Throwable) { estat.value = t("Error") + ": " + (e.message ?: "") }
        }
    }
    fun desaTot() { if (carpeta() != null) exec.execute { try { App.bib ?: return@execute; for (n in Marques.albumsEtiquetats()) desa(n); desaTotFitxer(); estat.value = t("Desat") } catch (e: Throwable) { estat.value = t("Error") + ": " + (e.message ?: "") } } }

    private fun desa(nom: String) {
        val b = App.bib ?: return; val e = b.emissores.firstOrNull { it.nom == nom } ?: return
        val txt = Marques.report(titolAlbum(e), nom, cancons(b, e).map { it to b.etiq(e, it) })
        escriu(nomFitxer(titolAlbum(e) + " [" + nom + "]"), txt)
    }
    private fun desaTotFitxer() { val b = App.bib ?: return; escriu("_tots_els_reports.txt", Marques.reportTot(b)) }

    private fun escriu(nomF: String, text: String) {
        val ctx = App.env.ctx; val arbre = carpeta() ?: return; val cr = ctx.contentResolver
        val id = DocumentsContract.getTreeDocumentId(arbre)
        val pare = DocumentsContract.buildDocumentUriUsingTree(arbre, id)
        var doc: Uri? = null
        cr.query(DocumentsContract.buildChildDocumentsUriUsingTree(arbre, id), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(1) == nomF) { doc = DocumentsContract.buildDocumentUriUsingTree(arbre, c.getString(0)); break } }
        val d = doc ?: DocumentsContract.createDocument(cr, pare, "text/plain", nomF) ?: throw IllegalStateException("no s'ha pogut crear $nomF")
        cr.openOutputStream(d, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        estat.value = t("Desat")
    }
}
