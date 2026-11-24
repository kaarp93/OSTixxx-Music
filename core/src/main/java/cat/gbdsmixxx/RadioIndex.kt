package cat.gbdsmixxx

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Índex dels jocs importats d'«Altres ràdios» i de DS/GBA (l'autor, 2026-11-09): per a cada joc desa les cançons (números) i, a DS, el nom
 * original de cada una, amb un segell (mida i data del fitxer) per saber si s'ha de refer. Així la ràdio mostra les llistes sense llegir
 * cap ROM ni SDAT; el fitxer pesat només es carrega en reproduir. Si no hi ha índex d'un joc (o el fitxer ha canviat), es carrega com abans i es desa.
 * Es desa en binari (`musica/radio_index.bin`, `DadesBin`, atòmic amb `.bak`); el `radio_index.json` d'abans es migra una vegada i es reanomena a `.migrat`.
 * Els `put` no escriuen al moment: marquen l'índex com a canviat i l'escriptura (una sola, en un fil apart) es fa en acabar de construir la biblioteca (`desaSiCal`).
 */
object RadioIndex {
    class Entrada(val segell: String, val ids: List<Int>, val noms: Map<Int, String>)

    private var mapa: HashMap<String, Entrada>? = null
    private var brut = false
    private fun dir(ctx: Context) = File(ctx.filesDir, "musica")
    private fun fitxer(ctx: Context) = File(dir(ctx), "radio_index.bin")

    @Synchronized private fun carrega(ctx: Context): HashMap<String, Entrada> {
        mapa?.let { return it }
        val m = HashMap<String, Entrada>()
        val bin = DadesBin.llegeixIndex(fitxer(ctx))
        if (bin != null) for ((k, e) in bin) m[k] = Entrada(e.segell, e.ids, e.noms)
        else {
            val json = File(dir(ctx), "radio_index.json")
            if (json.exists()) {
                try {
                    val j = JSONObject(json.readText())
                    for (k in j.keys()) {
                        val o = j.getJSONObject(k); val a = o.getJSONArray("ids"); val n = o.optJSONObject("noms")
                        val noms = HashMap<Int, String>()
                        if (n != null) for (id in n.keys()) noms[id.toInt()] = n.getString(id)
                        m[k] = Entrada(o.getString("segell"), List(a.length()) { a.getInt(it) }, noms)
                    }
                    escriu(ctx, m)
                    if (DadesBin.llegeixIndex(fitxer(ctx))?.size == m.size) json.renameTo(File(dir(ctx), "radio_index.json.migrat"))
                } catch (e: Exception) { m.clear() }          // és una memòria cau: si el JSON està malmès, es refà sola
            }
        }
        mapa = m
        return m
    }

    /** L'entrada del joc `clau` si el segell coincideix; si no, null (cal tornar a llegir el fitxer). */
    @Synchronized fun get(ctx: Context, clau: String, segell: String): Entrada? = carrega(ctx)[clau]?.takeIf { it.segell == segell }

    @Synchronized fun put(ctx: Context, clau: String, e: Entrada) { carrega(ctx)[clau] = e; brut = true }

    /** Treu els índexs dels jocs les claus dels quals comencen per algun dels prefixos (en netejar llistes). */
    @Synchronized fun treu(ctx: Context, prefixos: List<String>) { if (carrega(ctx).keys.removeAll { k -> prefixos.any { k.startsWith(it) } }) brut = true; desaSiCal(ctx) }

    /** Escriu l'índex si ha canviat (una sola escriptura per construcció de la biblioteca, no una per joc). */
    @Synchronized fun desaSiCal(ctx: Context) {
        if (!brut) return
        brut = false
        escriu(ctx, carrega(ctx))
    }

    private fun escriu(ctx: Context, m: Map<String, Entrada>) {
        try { DadesBin.escriuIndex(fitxer(ctx), m.mapValues { (_, e) -> DadesBin.EntradaIndex(e.segell, e.ids, e.noms) }) } catch (e: Throwable) { }
    }
}
