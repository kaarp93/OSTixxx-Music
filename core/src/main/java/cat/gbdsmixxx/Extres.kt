package cat.gbdsmixxx

/**
 * Anàlisi d'un joc sencer en segon pla (BPM del principi i tonalitat de cada cançó), perquè la llista del triador en mostri el BPM, la clau i la compatibilitat sense haver
 * de fer sonar cada cançó. Es fa en obrir el joc al triador, només per a les cançons que encara no tenen anàlisi, i es desa a `RadioDades.analisi`.
 */
object Analitzador {
    @Volatile private var gen = 0
    /** Nom intern del joc que s'analitza ara («» = cap), i progrés. */
    @Volatile var fent = ""
    @Volatile var fets = 0
    @Volatile var total = 0

    fun joc(b: Biblioteca, e: Emissora) {
        val pendents = e.ids.filter { val c = b.clauDe(e, it); c.isNotEmpty() && (RadioDades.analisi[c]?.bucle ?: -1) < 0 }
        val g = ++gen
        if (pendents.isEmpty()) { fent = ""; return }
        fent = e.nom; fets = 0; total = pendents.size
        Thread {
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) } catch (ex: Throwable) { }
            var n = 0
            for (id in pendents) {
                if (g != gen) break
                val clau = b.clauDe(e, id)
                try {
                    fun crea() = synchronized(RadioMotor.llocMuts) { val anterior = RadioMotor.muts; RadioMotor.muts = BooleanArray(16); try { e.crea(id) } finally { RadioMotor.muts = anterior } }
                    val sp = crea()
                    var bpm = 0.0
                    if (sp.bpm > 0.0) { val bf = ShortArray(128); var k = 0; while (sp.batec in 0.0..0.25 && k < sp.taux * 3) { sp.renderitza(bf, 64); k += 64 }; bpm = sp.bpm }
                    // Bucle: els streams ho diuen ells mateixos; la resta es fa córrer en silenci fins al primer bucle o fins que s'acaba
                    val s2 = crea(); val sabut = s2.teBucle
                    val h = if (sabut == null) s2.analitza(300) else null
                    val bucle = if (sabut != null) (if (sabut) 1 else 0) else if (h == null) -1 else if (s2.voltes > 0) 1 else if (s2.acabada) 0 else -1
                    val tn = if (bpm > 0.0) h?.let { Tonalitat.detecta(it) } else null
                    RadioDades.analisi[clau] = RadioDades.Analisi(Math.round(bpm).toInt(), tn?.tonica ?: -1, tn?.major ?: true, Math.round((tn?.confianca ?: 0.0) * 100).toInt(), if (bucle < 0) 2 else bucle, if (sabut == null && bpm > 0.0) Math.round(s2.batecMax).toInt() else 0)
                } catch (ex: Throwable) { RadioDades.analisi[clau] = RadioDades.Analisi(0, -1, true, 0, 2) }
                fets++
                if (++n % 25 == 0) RadioDades.desa()
            }
            RadioDades.desa()
            if (g == gen) fent = ""
        }.also { it.isDaemon = true }.start()
    }
}
