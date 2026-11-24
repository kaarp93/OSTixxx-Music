package cat.ostixxx.music

// Kotlin pur (sense Android ni Compose): es prova a JVM (eines/prototips/ui/prova.sh). La UI hi accedeix per `EstatNav` (Navegacio.kt), que el fa observable.

/** Pantalla apilada damunt d'una pestanya: un àlbum (o llista) o els Ajustos. `clau` identifica la pantalla (i el seu scroll). */
sealed class Pantalla {
    abstract val clau: String
    class Album(val nom: String) : Pantalla() {
        override val clau: String get() = "album:$nom"
        override fun equals(other: Any?) = other is Album && other.nom == nom
        override fun hashCode() = nom.hashCode()
    }
    object Ajustos : Pantalla() { override val clau: String get() = "ajustos" }
}

/**
 * Pila de navegació explícita de Music (l'autor, 2026-11-17, P6). Ordre de l'enrere (un sol BackHandler):
 * 1) el diàleg de dalt (menú d'una cançó, motiu, revisa, esborrar...), 1b) el mode concert (pantalla completa, 2026-11-17), 2) el panell del reproductor (EQ, Canals...), 3) el reproductor gran,
 * 4) la pantalla de dalt de la pila (àlbum, ajustos), 5) tornar a Inici des d'una altra pestanya. Si no queda res, l'enrere no es consumeix (l'app es tanca).
 * L'scroll de cada pantalla es recorda (`desaScroll`/`scroll`); el d'un àlbum s'oblida quan se'n surt.
 */
class PilaNav {
    var pestanya = 0
        private set
    private val pila = ArrayList<Pantalla>()
    val pantalles: List<Pantalla> get() = pila
    var jugador = false
        private set
    var panell: String? = null
        private set
    /** Mode concert (visualització a pantalla completa damunt del reproductor gran). */
    var concert = false
        private set
    private val dialegs = ArrayList<String>()
    val dialeg: String? get() = dialegs.lastOrNull()
    private val scrolls = HashMap<String, IntArray>()
    /** Es crida després de cada canvi (la UI hi puja un comptador d'estat de Compose). */
    var alCanviar: () -> Unit = {}

    val superior: Pantalla? get() = pila.lastOrNull()
    val profunditat: Int get() = pila.size
    /** Clau del contingut principal que es veu (sota el reproductor i els diàlegs). */
    val clauContingut: String get() = superior?.clau ?: "pestanya:$pestanya"

    private fun canvi() = alCanviar()

    fun canviaPestanya(i: Int) {
        if (i == pestanya && pila.isEmpty() && dialegs.isEmpty()) return
        for (p in pila) oblida(p)
        pila.clear(); dialegs.clear(); pestanya = i; canvi()
    }

    fun obreAlbum(nom: String) {
        if (superior == Pantalla.Album(nom)) return
        pila.add(Pantalla.Album(nom)); canvi()
    }

    /** Canvia l'àlbum de dalt per un altre (lliscar a l'anterior/següent, revisió contínua) sense apilar-lo; els diàlegs oberts es mantenen. */
    fun substitueixAlbum(nom: String) {
        val s = superior
        if (s is Pantalla.Album) { if (s.nom == nom) return; oblida(s); pila[pila.size - 1] = Pantalla.Album(nom); canvi() } else obreAlbum(nom)
    }

    fun obreAjustos() {
        if (superior === Pantalla.Ajustos) return
        pila.remove(Pantalla.Ajustos); pila.add(Pantalla.Ajustos); canvi()
    }

    fun obreJugador() { if (!jugador) { jugador = true; canvi() } }
    fun tancaJugador() { if (jugador || panell != null || concert) { jugador = false; panell = null; concert = false; canvi() } }
    fun obreConcert() { if (!concert) { concert = true; canvi() } }
    fun tancaConcert() { if (concert) { concert = false; canvi() } }
    fun obrePanell(id: String) { if (panell != id) { panell = id; canvi() } }
    fun tancaPanell() { if (panell != null) { panell = null; canvi() } }

    fun obreDialeg(k: String) { if (dialeg != k) { dialegs.remove(k); dialegs.add(k); canvi() } }
    /** Tanca el diàleg `k` (o el de dalt si és null). */
    fun tancaDialeg(k: String? = null) {
        val ok = if (k == null) dialegs.removeLastOrNull() != null else dialegs.remove(k)
        if (ok) canvi()
    }
    fun te(k: String): Boolean = k in dialegs

    val potEnrere: Boolean get() = dialegs.isNotEmpty() || concert || panell != null || jugador || pila.isNotEmpty() || pestanya != 0

    /** Fa un pas enrere; false si no hi havia res a tancar. */
    fun enrere(): Boolean {
        when {
            dialegs.isNotEmpty() -> dialegs.removeAt(dialegs.size - 1)
            concert -> concert = false
            panell != null -> panell = null
            jugador -> jugador = false
            pila.isNotEmpty() -> oblida(pila.removeAt(pila.size - 1))
            pestanya != 0 -> pestanya = 0
            else -> return false
        }
        canvi(); return true
    }

    private fun oblida(p: Pantalla) { if (p is Pantalla.Album) scrolls.remove(p.clau) }

    /** Desa l'scroll (primer element visible i desplaçament) d'una pantalla. */
    fun desaScroll(clau: String, index: Int, despl: Int) {
        if (clau.startsWith("album:") && pila.none { it.clau == clau }) return          // l'àlbum ja s'ha tancat: no es recorda
        scrolls[clau] = intArrayOf(index, despl)
    }
    fun scroll(clau: String): Pair<Int, Int> = scrolls[clau]?.let { it[0] to it[1] } ?: (0 to 0)
}
