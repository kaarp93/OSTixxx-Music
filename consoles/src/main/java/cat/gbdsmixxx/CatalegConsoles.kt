package cat.gbdsmixxx

import android.content.Context
import java.io.File

/**
 * Pont entre la biblioteca de consoles i el catàleg definitiu (`CatalegDefinitiu`, core). Decideix què és un joc «que funciona» i n'omple les metadades:
 *  - un àlbum de GB/NES/SNES amb driver (clau estable «CRC:tipus:clau:rev», CRC i mida de l'índex) o d'un motor propi (Groc, Cristall, TCG, TCG2, Red, Blue);
 *  - que l'usuari ha marcat ✓ «Bé» (`Marques.posa`) o el driver del qual era «provat» abans (`confirmatAbans`; es cataloga sol en publicar la biblioteca).
 * Tots entren amb `definitiu = false`; només el botó explícit «Valida del tot» (`valida`) el posa a true (l'autor, 2026-11-22). Un àlbum marcat ✗ no s'hi afegeix.
 */
object CatalegConsoles {
    /** Àlbums dels motors propis -> codi del fitxer (`musica_<codi>.gb`). */
    private val PROPIS = mapOf("CRISTALL" to "CRY", "TCG" to "TCG", "TCG2" to "TCG2", "GROC" to "YEL", "POKEMON_RED" to "RED", "POKEMON_BLUE" to "BLU")

    fun inicia(ctx: Context) = CatalegDefinitiu.inicia(File(ctx.applicationContext.filesDir, CatalegDefinitiu.NOM_FITXER))

    @Volatile private var falten0: RomsQueFalten? = null
    /** ROM que falten (d'una còpia `.ostx`). */
    fun falten(ctx: Context): RomsQueFalten = falten0 ?: synchronized(this) { falten0 ?: RomsQueFalten(File(RomsMusica.dir(ctx.applicationContext), "roms_falten.bin")).also { falten0 = it } }

    class Identitat(val crc: Long, val mida: Long, val consola: String, val driver: String)

    /** (CRC, mida, consola, driver) d'un àlbum; null si no és un joc de consola amb driver. Per als motors propis llegeix el CRC del fitxer (memòria cau): no al fil principal. */
    fun identitat(ctx: Context, e: Emissora): Identitat? {
        PROPIS[e.nom]?.let { codi ->
            val f = RomsConsoles.fitxerGb(ctx, codi); if (!f.isFile) return null
            val crc = RomsConsoles.crcFitxer(ctx, "musica_$codi.gb") ?: return null
            return Identitat(crc, f.length(), "GB", "propi:" + e.nom)
        }
        val est = e.estable ?: return null; val crc = e.crc ?: return null; val mida = e.mida ?: return null
        val cons = when { e.nom.startsWith("GB_") -> "GB"; e.nom.startsWith("NES_") -> "NES"; e.nom.startsWith("SNES_") -> "SNES"; else -> return null }
        return Identitat(crc, mida, cons, est.substringAfter(':'))
    }

    /** Etiqueta d'una pista com `Marques.etiqueta` (amb la traducció de les marques antigues). */
    private fun etiqueta(du: MagatzemUsuari, nom: String, id: Int): Int {
        val v = du.int(MagatzemUsuari.T_PE, nom, id, -1); if (v >= 0) return v
        return if (du.int(MagatzemUsuari.T_PM, nom, id) == 2) 3 else when (du.int(MagatzemUsuari.T_PT, nom, id)) { 1 -> 1; 2 -> 2; else -> 0 }
    }

    /**
     * Pistes de l'àlbum tal com les té l'usuari: tipus (etiqueta), el seu nom (nom de pista o nom canviat a la ràdio; buit = cap) i la posició dins del seu grup segons
     * l'ordre propi (o l'original). Només les pistes amb alguna dada. Es pot cridar des de qualsevol fil (les dades de la ràdio es llegeixen amb `try`).
     */
    fun pistesUsuari(e: Emissora, du: MagatzemUsuari): Map<Int, TitolCataleg> {
        val propi = du.str(MagatzemUsuari.T_ORDRE, e.nom).split(',').mapNotNull { it.toIntOrNull() }.filter { it in e.ids }
        val ordre = propi + e.ids.filter { it !in propi }
        val tipus = ordre.associateWith { etiqueta(du, e.nom, it) }
        val posGrup = HashMap<Int, Int>(); val n = HashMap<Int, Int>()
        for (id in ordre) { val g = tipus.getValue(id); val k = (n[g] ?: 0) + 1; n[g] = k; posGrup[id] = k }
        val out = LinkedHashMap<Int, TitolCataleg>()
        for (id in ordre) {
            val nomP = du.str(MagatzemUsuari.T_PN, e.nom, id).trim()
            val ren = try { RadioDades.noms[RadioDades.clau(e.clauDades, id)] } catch (x: Throwable) { null }?.trim() ?: ""
            val tp = tipus.getValue(id); val titol = nomP.ifEmpty { ren }
            if (tp == 0 && titol.isEmpty() && propi.isEmpty()) continue
            out[id] = TitolCataleg(tp, titol, if (propi.isEmpty()) 0 else posGrup.getValue(id))
        }
        return out
    }

    /**
     * Desa (o actualitza) l'àlbum al catàleg. `definitiu` null = conserva el que hi havia (false si és nou). Les pistes que ja hi eren i que ara no tenen dades
     * (p. ex. descartades i, per tant, amagades) es conserven. Retorna l'entrada o null si l'àlbum no és catalogable. No al fil principal.
     */
    fun desa(ctx: Context, e: Emissora, du: MagatzemUsuari, definitiu: Boolean?): EntradaCataleg? {
        val id = identitat(ctx, e) ?: return null
        val prev = CatalegDefinitiu.troba(id.crc, id.mida)
        val titols = LinkedHashMap<Int, TitolCataleg>()
        if (prev != null && prev.driver == id.driver) titols.putAll(prev.titols)
        titols.putAll(pistesUsuari(e, du))
        val nom = (e.iconaNom ?: e.vista).ifBlank { prev?.nom ?: e.nom }
        val ent = EntradaCataleg(id.crc, id.mida, nom, id.consola, titols, id.driver, definitiu ?: (prev?.definitiu ?: false), maxOf(System.currentTimeMillis(), (prev?.data ?: 0) + 1))
        if (prev != null && prev.nom == ent.nom && prev.consola == ent.consola && prev.titols == ent.titols && prev.driver == ent.driver && prev.definitiu == ent.definitiu) return prev
        CatalegDefinitiu.desa(ent)
        return ent
    }

    /** Cataloga un àlbum en un fil apart (el crida `Marques.posa` amb ✓ i els canvis d'etiqueta, nom o ordre d'un àlbum ✓). */
    fun catalogaEnFons(ctx: Context, e: Emissora, definitiu: Boolean? = null) {
        val app = ctx.applicationContext
        Thread { try { desa(app, e, DadesUsuari.v, definitiu) } catch (x: Throwable) { App.traca?.invoke("catàleg: error " + x.javaClass.simpleName) } }.also { it.isDaemon = true }.start()
    }

    /** «Valida del tot» (botó de la UI): desa l'àlbum amb les seves pistes i `definitiu = true`. En un fil apart; `fet` es crida en acabar (en aquell fil). */
    fun valida(ctx: Context, e: Emissora, fet: (EntradaCataleg?) -> Unit = {}) {
        val app = ctx.applicationContext
        Thread { val r = try { desa(app, e, DadesUsuari.v, true) } catch (x: Throwable) { null }; fet(r) }.also { it.isDaemon = true }.start()
    }

    /**
     * En publicar una biblioteca (fil apart, després de `migraConfirmats`): cataloga els àlbums que funcionen i encara no hi són: marcats ✓, o d'un driver
     * `confirmatAbans` (també els motors propis amb `confirmatAbans` a `METADADES_COMPAT`) sense ✗. Retorna quants n'ha afegit.
     */
    fun despresBiblioteca(ctx: Context, b: Biblioteca, du: MagatzemUsuari): Int {
        val conf = try { RegistreDrivers.confirmatsAbans().map { it.id }.toSet() } catch (x: Throwable) { emptySet() }
        val propisConf = METADADES_COMPAT.filter { it.confirmatAbans }.map { it.clau }.toSet()
        var n = 0
        for (e in b.reals()) {
            val id = try { identitat(ctx, e) } catch (x: Throwable) { null } ?: continue
            if (CatalegDefinitiu.troba(id.crc, id.mida) != null) continue
            val marca = du.int(MagatzemUsuari.T_MARCA, e.nom)
            if (marca == 2) continue
            val confirmat = if (id.driver.startsWith("propi:")) e.nom in propisConf else id.driver in conf
            if (marca != 1 && !confirmat) continue
            if (desa(ctx, e, du, false) != null) n++
        }
        return n
    }
}
