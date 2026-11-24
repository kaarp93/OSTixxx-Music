package cat.gbdsmixxx

import android.content.Context
import java.io.File

/**
 * Pont Android de les dades de l'usuari (`MagatzemUsuari`, `files/dades_usuari.bin`). La primera vegada (fora del fil principal, des d'`App.carrega`) migra les
 * preferències antigues (marques, etiquetes, noms de pista, ordre, comptes); les preferències NO s'esborren. La clau estable de cada àlbum la dona la biblioteca actual.
 */
object DadesUsuari {
    @Volatile private var m: MagatzemUsuari? = null

    /** Clau estable d'un àlbum: la de la biblioteca actual («CRC:tipus:clau» per a les ROM de consola) o el nom. */
    fun estable(nom: String): String = try { App.bib?.clausEstables?.get(nom) } catch (e: Throwable) { null } ?: nom
    /** Nom d'àlbum que té ara una clau estable (o la mateixa clau si no és de cap àlbum: dades amagades d'una ROM que no hi és). */
    fun nom(est: String): String = try { App.bib?.nomDeEstable(est) } catch (e: Throwable) { null } ?: est

    @Synchronized fun de(ctx: Context): MagatzemUsuari {
        m?.let { return it }
        val x = MagatzemUsuari(File(ctx.filesDir, "dades_usuari.bin")) { estable(it) }
        x.carrega()
        if (!x.migrat) {
            val r = try { x.migra(ctx.getSharedPreferences("gbdsmixxx", Context.MODE_PRIVATE).all) } catch (e: Throwable) { -1 }
            App.traca?.invoke("dades d'usuari: migració de les preferències " + (if (r >= 0) "feta ($r dades)" else "NO comprovada: es tornarà a provar"))
        }
        m = x
        return x
    }

    /** El magatzem (el carrega si cal: la primera vegada és des d'`App.carrega`, fora del fil principal). */
    val v: MagatzemUsuari get() = m ?: de(App.env.ctx)
}
