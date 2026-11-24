package cat.ostixxx.music

// Kotlin pur (sense Android ni Compose): es prova a JVM (eines/prototips/ui/prova.sh).

/**
 * Índex de la Cerca (P3): els textos ja normalitzats (minúscules, sense accents) es calculen una vegada per biblioteca, fora del fil principal;
 * cercar només recorre l'índex. `album` és el nom intern de l'emissora; `id` = -1 per a l'entrada de l'àlbum mateix.
 */
class CercaIndex(val albums: List<Entrada>, val cancons: List<Entrada>) {
    class Entrada(val album: String, val id: Int, val text: String, val sub: String) {
        val norm: String = normalitza(text)
        val normSub: String = normalitza(sub)
    }
    class Resultat(val albums: List<Entrada>, val cancons: List<Entrada>)

    fun cerca(q: String, maxAlbums: Int = 30, maxCancons: Int = 80): Resultat {
        val n = normalitza(q)
        if (n.isEmpty()) return Resultat(emptyList(), emptyList())
        val paraules = n.split(' ').filter { it.isNotEmpty() }
        fun casa(e: Entrada) = paraules.all { e.norm.contains(it) || e.normSub.contains(it) }
        val a = ArrayList<Entrada>(); for (e in albums) { if (a.size >= maxAlbums) break; if (casa(e)) a.add(e) }
        val c = ArrayList<Entrada>(); for (e in cancons) { if (c.size >= maxCancons) break; if (paraules.all { e.norm.contains(it) }) c.add(e) }
        // primer els que comencen pel text cercat
        return Resultat(a.sortedBy { if (it.norm.startsWith(n)) 0 else 1 }, c.sortedBy { if (it.norm.startsWith(n)) 0 else 1 })
    }

    companion object {
        private val MARQUES = Regex("\\p{Mn}+")
        private val ESPAIS = Regex("\\s+")
        /** Minúscules, sense accents ni diacrítics, espais simples. */
        fun normalitza(s: String): String =
            MARQUES.replace(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD), "").lowercase().replace('·', ' ').replace(ESPAIS, " ").trim()
    }
}
