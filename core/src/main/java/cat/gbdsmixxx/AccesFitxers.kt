package cat.gbdsmixxx

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import java.io.File

/**
 * Accés al sistema de fitxers per llegir les ROM d'on són (com un reproductor de MP3), amb «Accés a tots els fitxers» (`MANAGE_EXTERNAL_STORAGE`, Android 11+) o
 * `READ_EXTERNAL_STORAGE` (Android 8-10). Si no hi ha permís o el fitxer triat no té ruta (Drive, un proveïdor sense camí...), la ROM es copia dins l'app com sempre.
 */
object AccesFitxers {
    fun te(ctx: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    /** Obre la pantalla del sistema per donar el permís (Android 11+) o el demana (Android 8-10). */
    fun demana(act: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                try { act.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + act.packageName))) }
                catch (e: Throwable) { act.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            } else act.requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 7301)
        } catch (e: Throwable) { }
    }

    /** Camí absolut d'un URI del selector de fitxers (document o carpeta), o null si el proveïdor no en dona cap que es pugui deduir. No comprova si es pot llegir. */
    fun ruta(ctx: Context, uri: Uri): String? = try {
        when {
            uri.scheme == "file" -> uri.path
            uri.authority == "com.android.externalstorage.documents" -> {
                val id = if (DocumentsContract.isDocumentUri(ctx, uri)) DocumentsContract.getDocumentId(uri) else DocumentsContract.getTreeDocumentId(uri)
                val vol = id.substringBefore(':'); val rel = id.substringAfter(':', "")
                val base = if (vol.equals("primary", ignoreCase = true)) Environment.getExternalStorageDirectory().path else "/storage/$vol"
                if (rel.isEmpty()) base else "$base/$rel"
            }
            uri.authority == "com.android.providers.downloads.documents" -> {
                val id = if (DocumentsContract.isDocumentUri(ctx, uri)) DocumentsContract.getDocumentId(uri) else DocumentsContract.getTreeDocumentId(uri)
                if (id.startsWith("raw:")) id.removePrefix("raw:") else null
            }
            else -> null
        }
    } catch (e: Throwable) { null }

    /** Camí d'un fitxer triat que l'app pot llegir directament ara mateix (amb el permís i la mateixa mida que el que ha obert el selector); si no, null. */
    fun rutaLlegible(ctx: Context, uri: Uri, mida: Long): String? {
        if (ModeCarrega.antic || !te(ctx)) return null
        val r = ruta(ctx, uri) ?: return null
        val f = File(r)
        return if (f.isFile && f.canRead() && f.length() == mida) f.absolutePath else null
    }
}

/**
 * Mode de càrrega. `antic` (Ajustos > «Mode antic», TEMPORAL, xarxa de seguretat): la biblioteca de consoles es fa com abans, només amb les ROM copiades dins l'app
 * (`musica_gbd_*`, `musica_nes_*`, `musica_snes_*`) i la importació les copia com abans. Els ganxos els posa `:consoles` (el nucli no en sap res).
 */
object ModeCarrega {
    val antic: Boolean get() = try { App.env.int("mode_antic", 0) == 1 } catch (e: Throwable) { false }
    fun posaAntic(v: Boolean) { App.env.putInt("mode_antic", if (v) 1 else 0) }
    /** Afegeix una carpeta (camí absolut) a les carpetes de la biblioteca i la revisa: (importats, candidats). Null si l'app no té consoles. */
    @Volatile var afegeixCarpeta: ((Context, String, MutableList<Pair<Boolean, String>>, (String) -> Unit) -> Pair<Int, Int>)? = null
}
