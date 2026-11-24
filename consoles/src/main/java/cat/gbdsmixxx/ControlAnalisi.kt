package cat.gbdsmixxx

/** Una anàlisi aturada des de dins: cancel·lada per l'usuari, sense temps o sense memòria (`ControlAnalisi.comprova`). */
class AnalisiAturada(val motiu: Motiu) : RuntimeException(motiu.name) {
    enum class Motiu { CANCELLADA, TEMPS, MEMORIA }
}

/**
 * Control d'una anàlisi que corre dins l'app («Prova compatibilitat», l'autor 2026-11-22): cancel·lació de debò, límit de temps i límit de memòria.
 * Només afecta el fil que ha cridat `activa` (el de la prova): l'analitzador de PC, l'APK de l'analitzador i la reproducció no en veuen res (`comprova` hi torna de seguida).
 * Els analitzadors (`AnalitzadorGb`, `GbRamPipeline`, `AnalitzadorNes`) criden `comprova()` un cop per fotograma; si cal aturar-se llença `AnalisiAturada`. Com que l'analitzador
 * atrapa els errors per seccions, l'excepció es torna a llençar a cada fotograma fins que `analitza` torna: `motiu` diu per què s'ha aturat.
 */
object ControlAnalisi {
    @Volatile private var fil: Thread? = null
    @Volatile private var cancela = false
    @Volatile private var fins = 0L
    @Volatile private var maxBytes = 0L
    @Volatile private var comptador = 0
    /** Per què s'ha aturat l'anàlisi en curs (null = no s'ha aturat). */
    @Volatile var motiu: AnalisiAturada.Motiu? = null; private set
    /** Fase actual (per mostrar el progrés): «joc», «fred», «ram»; i el fotograma del joc (0..límit). */
    @Volatile var fase: String = ""
    @Volatile var fotograma: Int = 0
    /** Límits de les estructures de l'analitzador (per defecte, els de sempre; la prova de l'app els baixa per no passar de memòria). Vegeu `Limits`. */
    @Volatile var limits: Limits = Limits.PC

    /** Límits de memòria de l'analitzador de GB. `PC` = els valors de sempre (el resultat de l'analitzador de PC no canvia). */
    class Limits(val escrRoot: Int, val apuLog: Int, val escr: Int) {
        companion object {
            val PC = Limits(900000, 200000, 40000)
        }
    }

    /** Activa el control per al fil actual. `limitMs` = temps màxim (0 = sense); `maxBytes` = memòria usada màxima de la JVM (0 = sense). */
    fun activa(limitMs: Long, maxBytes: Long, limits: Limits = Limits.PC) {
        cancela = false; motiu = null; fase = ""; fotograma = 0; comptador = 0
        this.fins = if (limitMs > 0) System.currentTimeMillis() + limitMs else 0L
        this.maxBytes = maxBytes; this.limits = limits
        fil = Thread.currentThread()
    }
    /** Desactiva el control (sempre en acabar, també amb error): restableix tots els senyals. */
    fun desactiva() { fil = null; cancela = false; fins = 0L; maxBytes = 0L; fase = ""; fotograma = 0; limits = Limits.PC }
    /** Demana aturar l'anàlisi en curs (des de qualsevol fil). */
    fun cancela() { if (fil != null) cancela = true }
    val actiu: Boolean get() = fil != null

    /** Punt de control: es crida un cop per fotograma. Fora del fil de la prova no fa res. */
    fun comprova() {
        val f = fil ?: return
        if (Thread.currentThread() !== f) return
        motiu?.let { throw AnalisiAturada(it) }
        if (cancela) atura(AnalisiAturada.Motiu.CANCELLADA)
        if (fins > 0 && System.currentTimeMillis() > fins) atura(AnalisiAturada.Motiu.TEMPS)
        if (maxBytes > 0 && (++comptador and 63) == 0) {
            val rt = Runtime.getRuntime()
            if (rt.totalMemory() - rt.freeMemory() > maxBytes) {
                System.gc()                      // la memòria usada inclou escombraries: només s'atura si després d'un GC encara en passa
                if (rt.totalMemory() - rt.freeMemory() > maxBytes) atura(AnalisiAturada.Motiu.MEMORIA)
            }
        }
    }
    private fun atura(m: AnalisiAturada.Motiu): Nothing { motiu = m; throw AnalisiAturada(m) }
}
