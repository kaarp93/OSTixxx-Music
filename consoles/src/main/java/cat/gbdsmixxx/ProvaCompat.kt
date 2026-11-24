package cat.gbdsmixxx

import java.util.concurrent.atomic.AtomicReference

/**
 * «Prova compatibilitat» (l'autor, 2026-11-22): analitza dins l'app UNA ROM de GB/GBC o NES sense driver (CRC desconegut) amb l'analitzador de sempre (`AnalitzadorGb` +
 * `GbRamPipeline`, `AnalitzadorNes`) per trobar-ne un driver candidat. Lògica pura (sense Android; la UI és a Music, `ProvaCompatUi`).
 * - Una sola anàlisi alhora: `engega` torna false si n'hi ha una en curs (mutex amb `AtomicReference`).
 * - Fil propi de prioritat mínima, límit de temps i de memòria (`ControlAnalisi`), cancel·lació de debò (`cancela`: l'analitzador s'atura al fotograma següent).
 * - En acabar (bé, amb error o cancel·lada) fa «cache drop» (`buidaTot`): no queda cap referència a la ROM, al report ni als estats; es buiden les memòries cau temporals
 *   (`GbRamPipeline.buida` i les que registra l'app a `alliberadors`: ROM llegides, `RomsMusica.buidaCaches`...), es restableixen els senyals i es suggereix un GC.
 *   La memòria cau persistent de l'anàlisi de ritme (`RitmeRom`) no es toca.
 * El resultat és la definició (`DefDriver`) amb més cançons del report; qui crida la desa com a candidat (`CandidatsDrivers`).
 */
object ProvaCompat {
    /** Límits d'una prova. `fraccioMemoria`: s'atura si la memòria usada de la JVM passa d'aquesta fracció del màxim (després d'un GC). */
    class Limits(val tempsMs: Long = 10 * 60_000L, val fraccioMemoria: Double = 0.75, val estructures: ControlAnalisi.Limits = APP)
    /** Estructures de l'analitzador de GB a l'app: més petites que a PC (el registre d'escriptures `escrRoot` era el que més ocupava). */
    val APP = ControlAnalisi.Limits(escrRoot = 300000, apuLog = 200000, escr = 40000)

    sealed class Resultat {
        /** Hi ha candidat: `def` (crc = el de la ROM) i quantes cançons té. */
        class Candidat(val def: DefDriver, val ms: Long) : Resultat()
        /** L'analitzador ha acabat sense cap driver. */
        class Cap(val ms: Long) : Resultat()
        /** Aturada: cancel·lada, sense temps o sense memòria. */
        class Aturada(val motiu: AnalisiAturada.Motiu, val ms: Long) : Resultat()
        class Error(val motiu: String, val ms: Long) : Resultat()
    }

    /** La feina en curs (dades per a la UI). */
    class Feina(val crc: Long, val nom: String, val consola: String, val limitMs: Long) {
        val t0 = System.currentTimeMillis()
        @Volatile var fil: Thread? = null
        val ms: Long get() = System.currentTimeMillis() - t0
        val fase: String get() = ControlAnalisi.fase
        val fotograma: Int get() = ControlAnalisi.fotograma
    }

    private val enCurs = AtomicReference<Feina?>(null)
    /** Memòries cau que l'app buida en acabar (p. ex. `RomsMusica.buidaCaches()`); les registra l'app una vegada. */
    val alliberadors = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    /** Es crida al principi del fil de la prova (a Android: `Process.setThreadPriority(THREAD_PRIORITY_BACKGROUND)`). */
    @Volatile var alIniciarFil: (() -> Unit)? = null

    fun feina(): Feina? = enCurs.get()
    val ocupat: Boolean get() = enCurs.get() != null
    /** Només GB/GBC i NES (els que té l'analitzador). */
    fun admet(consola: String): Boolean = consola == "GB" || consola == "NES"

    /**
     * Engega l'anàlisi en un fil de prioritat mínima. `llegeix` dona els bytes de la ROM (es crida dins del fil; no es reté). `alAcabar` es crida des del fil de la prova
     * DESPRÉS del cache drop i d'alliberar el mutex. Torna false (i no fa res) si ja n'hi ha una en curs o la consola no s'admet.
     */
    fun engega(crc: Long, nom: String, consola: String, limits: Limits = Limits(), llegeix: () -> ByteArray, alAcabar: (Resultat) -> Unit): Boolean {
        if (!admet(consola)) return false
        val f = Feina(crc, nom, consola, limits.tempsMs)
        if (!enCurs.compareAndSet(null, f)) return false
        val t = Thread(null, {
            var res: Resultat
            try {
                try { alIniciarFil?.invoke() } catch (e: Throwable) { }
                val rt = Runtime.getRuntime()
                ControlAnalisi.activa(limits.tempsMs, (rt.maxMemory() * limits.fraccioMemoria).toLong(), limits.estructures)
                res = analitza(f, llegeix)
            } catch (e: AnalisiAturada) { res = Resultat.Aturada(e.motiu, f.ms) }
            catch (e: OutOfMemoryError) { res = Resultat.Aturada(AnalisiAturada.Motiu.MEMORIA, f.ms) }
            catch (e: Throwable) { res = Resultat.Error(e.javaClass.simpleName + " " + (e.message ?: "").take(120), f.ms) }
            finally { ControlAnalisi.desactiva(); buidaTot(); f.fil = null; enCurs.set(null) }
            try { alAcabar(res) } catch (e: Throwable) { }
        }, "prova-compat", 8L shl 20)
        t.priority = Thread.MIN_PRIORITY; t.isDaemon = true
        f.fil = t
        t.start()
        return true
    }

    /** Atura l'anàlisi en curs (si n'hi ha): l'analitzador s'atura al fotograma següent i `alAcabar` rep `Aturada(CANCELLADA)`. */
    fun cancela() { if (enCurs.get() != null) ControlAnalisi.cancela() }

    private fun analitza(f: Feina, llegeix: () -> ByteArray): Resultat {
        var bytes: ByteArray? = llegeix()
        if (crcDe(bytes!!) != (f.crc and 0xFFFFFFFFL)) return Resultat.Error("la ROM ha canviat (CRC)", f.ms)
        var report: String? = if (f.consola == "NES") AnalitzadorNes.analitza(f.nom, bytes) else AnalitzadorGb.analitza(f.nom, bytes)
        bytes = null
        // l'analitzador atrapa els errors per seccions: si s'ha aturat, ho diu `ControlAnalisi.motiu`, no l'excepció
        ControlAnalisi.motiu?.let { report = null; return Resultat.Aturada(it, f.ms) }
        val def = millor(report!!, f.crc)
        report = null
        return if (def == null) Resultat.Cap(f.ms) else Resultat.Candidat(def, f.ms)
    }

    /** De totes les línies `AUTO_*` del report amb el CRC de la ROM, la que té més cançons (com `eines/drivers_paquet`: «la de més sons»). Null si no n'hi ha cap. */
    fun millor(report: String, crc: Long): DefDriver? =
        DriversDef.parseText(report, null, TipusDriver.AUTO).filter { it.crc == (crc and 0xFFFFFFFFL) }.maxByOrNull { it.noms.size }

    /** «Cache drop»: tot el que l'anàlisi pot haver deixat retingut. Mai llança. */
    fun buidaTot() {
        try { GbRamPipeline.buida() } catch (e: Throwable) { }
        for (a in alliberadors) try { a() } catch (e: Throwable) { }
        System.gc()
    }
}
