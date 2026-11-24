package cat.ostixxx.music

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import cat.gbdsmixxx.App
import cat.gbdsmixxx.Biblioteca
import cat.gbdsmixxx.CatalegDefinitiu
import cat.gbdsmixxx.Emissora
import cat.gbdsmixxx.EntradaCataleg
import cat.gbdsmixxx.RadioDades
import cat.gbdsmixxx.TitolCataleg
import cat.gbdsmixxx.cancons
import cat.gbdsmixxx.desaOrdrePropi
import cat.gbdsmixxx.t

/**
 * «Valida del tot» (l'autor, 2026-11-22). Un àlbum de ROM amb driver (GB, GBC, NES, SNES amb CRC i mida coneguts) és «validat del tot» **només** quan l'usuari ha premut
 * «Valida del tot» i l'entrada del catàleg definitiu (`CatalegDefinitiu`, clau CRC + mida) té `definitiu = true`. Les marques (✓ antic, `confirmatAbans`) no ho fan soles.
 * Validat del tot: la pantalla de l'àlbum amaga els controls de validació (✓/✗ de l'àlbum, C/S/J/✗ de les pistes, «Revisa», «Accepta suggeriments»).
 * «Reobre la validació» (menú ⋮ d'una pista) torna `definitiu = false`. El catàleg es llegeix i s'escriu en un fil propi (la primera lectura llegeix el disc).
 * Per als àlbums candidats, «Confirma el driver» és la mateixa acció: pot cridar `validaDelTot`.
 *
 * Especificació de l'autor (2026-11-22). Només es pot validar si totes les pistes que es veuen (`cancons`) tenen etiqueta (Cançó, Jingle, Sfx o Descarta). En validar:
 *  a) es desa al catàleg què és cada pista (`titols`: tipus i nom de l'usuari, buit = el del driver) i `definitiu = true`;
 *  b) les pistes Descarta s'amaguen del joc (`RadioDades.ocults`, com «Elimina del joc»: ni àlbum, ni llistes, ni cerca); només tornen amb «Recupera les pistes descartades»;
 *  c) l'ordre de l'àlbum (`po_`, `desaOrdrePropi`) passa a ser: cançons, jingles i sfx; dins de cada grup, l'ordre que hi havia (el propi de l'usuari o el del joc);
 *  d) noms: el que hi ha posat l'usuari es conserva; si no, «Cançó 1, 2...», «Jingle 1, 2...», «SFX 1, 2...» (amb `t()`, a `RadioDades.noms`), numerats per grup.
 *     Un nom que va posar una validació anterior (al catàleg el títol és buit) es torna a numerar.
 * Els identificadors de les pistes no canvien (marques i comptes van per CRC + id): només la visibilitat, l'ordre i els noms.
 */
object Validacio {
    /** Puja quan una validació o una reobertura és desada: la pantalla de l'àlbum torna a llegir el catàleg. */
    var versio by mutableIntStateOf(0); private set
    /** Últim estat llegit de cada àlbum (`nom` -> definitiu): evita que els controls surtin un instant abans de saber-ho i el fa servir el menú ⋮ (sense llegir el disc). */
    val conegut = mutableStateMapOf<String, Boolean>()
    private val exec = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "validacio").also { it.isDaemon = true } }
    private val fil = Handler(Looper.getMainLooper())
    /** Última entrada del catàleg llegida de cada àlbum (fil principal): per saber quins noms va posar una validació i quines pistes es van descartar. */
    private val entrades = HashMap<String, EntradaCataleg?>()

    /** Pistes visibles sense etiqueta (no es pot validar mentre n'hi hagi). */
    fun pendents(e: Emissora, ids: List<Int>): Int = ids.count { Marques.etiqueta(e.nom, it) == 0 }

    /** Pistes descartades per una validació que encara són amagades (per a «Recupera les pistes descartades»). */
    fun descartadesAmagades(b: Biblioteca, e: Emissora): List<Int> =
        entrades[e.nom]?.titols?.filter { it.value.tipus == TitolCataleg.DESCARTA }?.keys?.filter { b.ocult(e, it) } ?: emptyList()

    /** Torna a mostrar les pistes descartades per la validació (no canvia `definitiu`). */
    fun recuperaDescartades(b: Biblioteca, e: Emissora) {
        val l = descartadesAmagades(b, e); if (l.isEmpty()) return
        for (id in l) RadioDades.ocults.remove(b.clauDe(e, id))
        RadioDades.desa(); b.refaLlistes(); App.versio++
    }

    /** Clau del catàleg (CRC, mida) d'un àlbum de ROM amb driver; null si no n'és o no se'n sap el CRC o la mida. */
    fun clau(e: Emissora): Pair<Long, Long>? {
        if (e.esLlista || consolaDe(e) !in CONSOLES) return null
        val c = e.crc ?: return null; val m = e.mida ?: return null
        return c to m
    }
    private val CONSOLES = setOf("GB", "GBC", "NES", "SNES")

    /** Llegeix el catàleg (no al fil principal). */
    fun definitiu(e: Emissora): Boolean {
        val k = clau(e) ?: return false
        return CatalegDefinitiu.troba(k.first, k.second)?.definitiu == true
    }

    /** Valida del tot (vegeu l'especificació a dalt). Torna false (i no fa res) si queda alguna pista visible sense etiqueta. Al fil principal. */
    fun validaDelTot(b: Biblioteca, e: Emissora): Boolean {
        val k = clau(e) ?: return false
        val ids = cancons(b, e)
        if (pendents(e, ids) > 0) return false
        val ant = entrades[e.nom]
        val nom = ant?.nom ?: (e.iconaNom ?: e.vista)
        val consola = ant?.consola ?: if (consolaDe(e) == "GBC") "GB" else consolaDe(e)
        val driver = ant?.driver ?: e.estable?.substringAfter(':')?.takeIf { it.isNotEmpty() } ?: "propi:${e.nom}"
        val tipus = ids.associateWith { Marques.etiqueta(e.nom, it) }
        // noms de l'usuari: el de la pista (Marques) o el de «Edita les etiquetes» (RadioDades.noms), llevat que el posés una validació anterior (al catàleg, títol buit)
        fun nomUsuari(id: Int): String {
            Marques.nomPista(e.nom, id).takeIf { it.isNotEmpty() }?.let { return it }
            val n = RadioDades.noms[b.clauDe(e, id)] ?: return ""
            return if (ant?.titols?.get(id)?.titol == "") "" else n
        }
        val usuari = ids.associateWith { nomUsuari(it) }
        // a) catàleg: tipus i nom de l'usuari (buit = el del driver) de cada pista visible; les que ja hi eren i no es veuen es conserven
        val titols = LinkedHashMap<Int, TitolCataleg>(ant?.titols ?: emptyMap())
        for (id in ids) titols[id] = TitolCataleg(tipus.getValue(id), usuari.getValue(id))
        // b) Descarta: fora del joc
        for (id in ids) if (tipus[id] == TitolCataleg.DESCARTA) RadioDades.ocults.add(b.clauDe(e, id))
        // c) ordre: cançons, jingles, sfx (cada grup en l'ordre d'ara)
        val grups = listOf(TitolCataleg.CANCO to "Cançó", TitolCataleg.JINGLE to "Jingle", TitolCataleg.SFX to "SFX")
        desaOrdrePropi(e.nom, grups.flatMap { (tp, _) -> ids.filter { tipus[it] == tp } })
        // d) noms per grup
        for ((tp, etiq) in grups) {
            var n = 0
            for (id in ids) if (tipus[id] == tp) { n++; if (usuari.getValue(id).isEmpty()) RadioDades.noms[b.clauDe(e, id)] = t(etiq) + " " + n }
        }
        RadioDades.desa(); b.refaLlistes(); App.versio++
        Reports.desaAlbum(e.nom)
        val nova = EntradaCataleg(k.first, k.second, nom, consola, titols, driver, true, System.currentTimeMillis())
        entrades[e.nom] = nova; conegut[e.nom] = true
        exec.execute { CatalegDefinitiu.desa(nova); fil.post { versio++ } }
        return true
    }

    /** «Reobre la validació»: `definitiu = false` (l'entrada es conserva) i els controls tornen a sortir. */
    fun reobre(e: Emissora) {
        val k = clau(e) ?: return
        conegut[e.nom] = false
        entrades[e.nom]?.let { a -> entrades[e.nom] = EntradaCataleg(a.crc, a.mida, a.nom, a.consola, a.titols, a.driver, false, System.currentTimeMillis()) }
        exec.execute {
            val ant = CatalegDefinitiu.troba(k.first, k.second)
            if (ant != null) CatalegDefinitiu.desa(EntradaCataleg(ant.crc, ant.mida, ant.nom, ant.consola, ant.titols, ant.driver, false, System.currentTimeMillis()))
            fil.post { versio++ }
        }
    }

    /** Llegeix l'estat en el fil propi i el desa a `conegut` (la pantalla de l'àlbum el crida en obrir-se i quan puja `versio`). */
    fun llegeix(e: Emissora) {
        if (clau(e) == null) return
        exec.execute {
            val k = clau(e)!!
            val ent = try { CatalegDefinitiu.troba(k.first, k.second) } catch (x: Throwable) { null }
            fil.post { entrades[e.nom] = ent; conegut[e.nom] = ent?.definitiu == true }
        }
    }
}
