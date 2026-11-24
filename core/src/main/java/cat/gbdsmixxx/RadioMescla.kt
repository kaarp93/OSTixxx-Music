package cat.gbdsmixxx

/**
 * Mesclador de la ràdio (l'autor, 2026-11-09): dos decks que reprodueixen cançons de qualsevol motor alhora. Cada deck té volum, pitch (que
 * canvia també la velocitat, com un plat: ±16% en passos de 0,25%) i les seves pistes silenciables. Es toca amb la mateixa generació en
 * directe de la ràdio i es remostreja (interpolació lineal) a la freqüència de sortida.
 */
/** Compte de batecs d'una cançó: tics del seqüenciador entre tics per negra; en saltar enrere (bucle) torna a la posició que tenia aquell punt el primer cop. */
class RellotgeBatec(private val ticsPerBatec: Int) {
    private var tics = 0L; private var offset = 0L
    private var visites: HashMap<Int, HashMap<Int, Long>>? = HashMap()      // pista -> adreça -> tics (només fins al primer bucle)
    private var pistaBucle = -1
    private val destins = HashMap<Int, Long>()
    private var maxim = 0L
    fun tic() { tics++; if (tics + offset > maxim) maxim = tics + offset }
    /** Posició més alta a què ha arribat (en batecs): en una cançó amb bucle, el final del bucle. */
    fun maxim(): Double = maxim.toDouble() / ticsPerBatec
    /** La pista `pista` llegeix l'ordre de l'adreça `adr` (es recorda el primer cop). */
    fun visita(pista: Int, adr: Int) { val v = visites ?: return; val m = v.getOrPut(pista) { HashMap() }; if (!m.containsKey(adr)) m[adr] = tics + offset }
    /** La pista `pista` salta cap enrere a `desti`: la primera que ho fa és la de referència; el compte torna a on era aquell punt. */
    fun salt(pista: Int, desti: Int) {
        if (pistaBucle < 0) {
            pistaBucle = pista
            visites?.get(pista)?.get(desti)?.let { destins[desti] = it }
            visites = null                                   // ja no cal recordar res més
        }
        if (pista != pistaBucle) return
        destins[desti]?.let { offset = it - tics }
    }
    fun posicio(fraccio: Double): Double = (tics + offset + fraccio) / ticsPerBatec
}

/** Tonalitat d'una cançó: nota fonamental (0 = do ... 11 = si), mode i confiança (correlació de 0 a 1). */
class Tonalitat(val tonica: Int, val major: Boolean, val confianca: Double) {
    /** Nom curt: «La», «Sol#m». */
    fun curt() = NOTES[tonica] + (if (major) "" else "m")
    fun nom() = NOTES[tonica] + (if (major) " major" else " menor")
    /** Codi de la roda de Camelot (8A = La menor, 8B = Do major): dues tonalitats barregen bé si el número és el mateix o difereix en 1. */
    fun camelot(): String = (if (major) NUM_MAJOR[tonica] else NUM_MAJOR[(tonica + 3) % 12]).toString() + (if (major) "B" else "A")
    /** Número del codi de Camelot (1-12). */
    fun numeroCamelot(): Int = if (major) NUM_MAJOR[tonica] else NUM_MAJOR[(tonica + 3) % 12]
    /** La mateixa tonalitat pujada `semitons` (el pitch del mesclador canvia també l'altura). */
    fun desplacada(semitons: Int) = Tonalitat(Math.floorMod(tonica + semitons, 12), major, confianca)

    companion object {
        private val NOTES = arrayOf("Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")
        private val NUM_MAJOR = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)
        // Perfils de tonalitat de Krumhansl i Kessler
        private val PERFIL_MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
        private val PERFIL_MENOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

        private fun correlacio(h: DoubleArray, p: DoubleArray, gir: Int): Double {
            var mh = 0.0; var mp = 0.0
            for (i in 0 until 12) { mh += h[(i + gir) % 12]; mp += p[i] }
            mh /= 12; mp /= 12
            var sxy = 0.0; var sxx = 0.0; var syy = 0.0
            for (i in 0 until 12) { val x = h[(i + gir) % 12] - mh; val y = p[i] - mp; sxy += x * y; sxx += x * x; syy += y * y }
            return if (sxx <= 0.0 || syy <= 0.0) 0.0 else sxy / Math.sqrt(sxx * syy)
        }

        /** La tonalitat (de les 24) amb més correlació amb l'histograma de notes; null si hi ha massa poques notes. */
        fun detecta(h: DoubleArray): Tonalitat? {
            if (h.size != 12 || h.sum() < 100.0) return null
            var millor: Tonalitat? = null
            for (t in 0 until 12) for (major in booleanArrayOf(true, false)) {
                val r = correlacio(h, if (major) PERFIL_MAJOR else PERFIL_MENOR, t)
                if (millor == null || r > millor.confianca) millor = Tonalitat(t, major, r)
            }
            return millor
        }
    }
}

/**
 * Semàfor de compatibilitat de tonalitat (roda de Camelot, com el mescla harmònica): 2 = verd (mateix codi, ±1 amb la mateixa lletra, o el relatiu: mateix número amb l'altra lletra),
 * 1 = groc (±2 amb la mateixa lletra), 0 = vermell (la resta), -1 = no es pot dir (alguna tonalitat desconeguda o amb correlació baixa, <0,6).
 */
fun compatibilitat(a: Tonalitat?, b: Tonalitat?): Int {
    if (a == null || b == null || a.confianca < 0.6 || b.confianca < 0.6) return -1
    val d = Math.abs(a.numeroCamelot() - b.numeroCamelot()).let { minOf(it, 12 - it) }
    return if (a.major == b.major) (if (d <= 1) 2 else if (d == 2) 1 else 0) else (if (d == 0) 2 else 0)
}

/** Diferència de fase entre dues posicions en batecs, de -0,5 a 0,5 (positiva si `a` va per davant de `b`). */
fun difFase(a: Double, b: Double): Double {
    var d = (a - Math.floor(a)) - (b - Math.floor(b))
    if (d >= 0.5) d -= 1.0; if (d < -0.5) d += 1.0
    return d
}

class Deck(val num: Int) {
    @Volatile var sinte: Sintetitzador? = null
    @Volatile var titol = ""
    @Volatile var sona = false
    @Volatile var volum = 100                 // 0..100
    /** Tempo triat amb el lliscador, en % (0 = original), i rang del lliscador (±6, ±10, ±16 o ±50 «WIDE»). */
    @Volatile var tempoPc = 0.0
    @Volatile var rang = 16
    /** Pitch bend mentre es manté la tecla: -1 frena, 1 accelera (un 8% més per sobre o per sota del pitch triat), 0 res. */
    @Volatile var bend = 0
    @Volatile var error: String? = null
    val muts = BooleanArray(16)
    @Volatile var solo = -1

    private val CH = 256                      // tros que es demana al motor cada cop: petit perquè els canvis (canals, tempo, salts) se sentin de seguida
    private val BEND = 0.08
    /** Compte «1 2 3 4»: batecs per compàs (4 o 3) i quants batecs s'ha desplaçat l'«1» respecte al principi de la cançó. */
    @Volatile var compas = 4
    @Volatile var desplacament = 0
    /** Sync: el deck segueix el BPM i la fase de batec de l'altre. */
    @Volatile var sinc = false
    /** Tonalitat de la cançó (es calcula en carregar-la, en un fil a part); null mentre no es coneix o si el motor no ho pot fer. */
    @Volatile var tonalitat: Tonalitat? = null
    /** Clau de la cançó (per desar-ne el BPM i la tonalitat), segons que ha sonat des de carregar-la i si ja s'ha desat. */
    @Volatile var clau = ""
    private var sonat = 0.0
    private var gravat = false
    /** Quantització: el deck espera que l'altre arribi a la seva mateixa fase de batec per començar. */
    @Volatile var pendent = false
    /** BPM fixat (després d'un SYNC): el deck es manté a aquest BPM efectiu encara que la cançó porti ordres de canvi de tempo; 0 = cap (mana el lliscador de tempo). */
    @Volatile var bpmFix = 0.0
    /** BPM real del principi de la cançó (mesurat en carregar-la): el motor, abans de començar a sonar, encara no ha llegit l'ordre de tempo i en dona un per defecte. */
    @Volatile var bpmIni = 0.0
    /** BPM de la cançó ara (sense el tempo del deck): el del motor o, si encara no ha passat del principi, el mesurat en carregar-la. 0 si no en té. */
    fun bpm(): Double {
        val s = sinte ?: return 0.0
        val b = s.bpm; if (b <= 0.0) return 0.0
        return if (bpmIni > 0.0 && s.batec in 0.0..0.25) bpmIni else b
    }
    /** Fosa curta (mostres de sortida) en entrar a un punt nou (salt, cue, volta del bucle) i abans de la volta del bucle: evita el clic del tall. */
    private val FOSA = 96
    private var entra = 0
    private val src = ShortArray(2 * (CH + 1)); private val tmp = ShortArray(2 * CH)
    // ---- Equalitzador de 3 bandes (greus fins a ~250 Hz, mitjos, aguts des de ~2,5 kHz) amb «kill» per banda, i mesurador de nivell ----
    /** Guany de cada banda (greus, mitjos, aguts): 0..4, 1 = sense canvi. */
    val eq = floatArrayOf(1f, 1f, 1f)
    /** Banda apagada del tot (kill). */
    val eqKill = BooleanArray(3)
    private var fL1 = 0f; private var fL2 = 0f; private var fR1 = 0f; private var fR2 = 0f
    /** Pic del nivell de sortida del deck (0..1 o més), amb caiguda; el llegeix la pantalla. */
    @Volatile var pic = 0f
    /** Llargada de la cançó en batecs (fins al final o fins on fa el bucle) i batec on torna el bucle (-1 si no en fa o no se sap); es mesuren en carregar-la. */
    @Volatile var durada = -1.0
    private val PUNT_FINAL = 32.0
    /** Què fa cada canal (bateria, baix, acords, melodia, agut), mesurat en carregar la cançó; null si no se sap. */
    @Volatile var perfils: Array<PerfilPista?>? = null
    /** Tipus de canal editats a mà per a aquesta cançó (16 valors; -1 = el detectat); es posen en carregar-la i quan s'editen a la pestanya Cançons. */
    @Volatile var tipusEditats: IntArray? = null
    /** Tipus del canal `k`: l'editat a mà o, si no n'hi ha, el detectat (0 bateria, 1 baix, 2 acords, 3 melodia, 4 agut); -1 si no se sap. */
    fun categoria(k: Int): Int = tipusEditats?.getOrNull(k)?.takeIf { it >= 0 } ?: perfils?.getOrNull(k)?.categoria() ?: -1
    /** Etiqueta curta del canal `k` (BAT, BAI, ACO, MEL, AGU) o null. */
    fun etiqueta(k: Int): String? = categoria(k).let { if (it < 0) null else listOf("BAT", "BAI", "ACO", "MEL", "AGU")[it] }
    @Volatile var iniciBucle = -1.0
    private var ple = false; private var idx = CH.toDouble()

    /** Posa una cançó nova al deck (parat i al principi). */
    @Synchronized fun carrega(s: Sintetitzador, nom: String, clau: String = "") { semitons = 0; repGen++; recanvi = null; repeteix = 0.0; for (k in 0 until 5) { hotBeat[k] = -1.0; hotGen[k]++; hotSinte[k] = null }; cueGen++; cueSinte = null; cueBeat = 0.0; enCue = true; fabricaFins = null; perfils = null; tipusEditats = null; durada = -1.0; iniciBucle = -1.0; bpmFix = 0.0; bpmIni = 0.0; sinc = false; sona = false; sinte = s; titol = nom; this.clau = clau; sonat = 0.0; gravat = false; ple = false; idx = CH.toDouble(); error = null; tonalitat = null }

    /** Desa el BPM i la tonalitat de la cançó un cop ha sonat uns segons (el BPM del principi pot ser un valor per defecte). */
    fun recorda(desa: (clau: String, bpm: Int, tonica: Int, major: Boolean, confianca: Int) -> Unit) {
        if (!sona || gravat || clau.isEmpty()) return
        sonat += 256.0 / 44100.0
        val bpm = bpm()
        val tn = tonalitat
        if (sonat < 2.0 || (tn == null && sonat < 6.0)) return
        gravat = true
        if (bpm > 0.0) desa(clau, Math.round(bpm).toInt(), tn?.tonica ?: -1, tn?.major ?: true, Math.round((tn?.confianca ?: 0.0) * 100).toInt())
    }

    // ---- Repetició de batecs (només OSTixxx): es repeteixen en bucle els N batecs que **vénen** des d'on és el deck ----
    // En lloc de gravar àudio, cada passada el sintetitzador es refà al batec d'inici (un de recanvi, preparat en segon pla amb `fabricaFins`); així, en deixar de repetir, la
    // cançó continua des del punt del bucle on era (batec 7, 8, 9...) i no des d'on «hauria arribat».
    /** Batecs que es repeteixen ara (1/8 ... 64), o 0. */
    @Volatile var repeteix = 0.0
    /** Crea la cançó d'aquest deck i l'avança en silenci fins al batec indicat (null si no pot); la posa el mesclador en carregar cada cançó. */
    @Volatile var fabricaFins: ((Double) -> Sintetitzador?)? = null
    @Volatile private var recanvi: Sintetitzador? = null
    @Volatile private var creantRecanvi = false
    @Volatile private var repGen = 0
    private var repBeat0 = 0.0; private var repPos = 0.0
    /** El deck està buscant un batec (es crea la cançó i s'avança en silenci); mentre dura, no sona. */
    @Volatile var buscant = false

    /** Activa la repetició dels `n` batecs que venen (4, 8, 16, 32); tornar a prémer el mateix valor, o `n` = 0, la desactiva i la cançó continua d'on era dins del bucle. */
    /** Batecs ja fets dins de la passada actual del LOOP (per saber quants en queden). */
    fun quedaLoopFet(): Double = repPos.coerceIn(0.0, repeteix)

    @Synchronized fun repeteixBatecs(n: Double, quant: Boolean = false) {
        repGen++; recanvi = null
        if (n <= 0.0 || n == repeteix) { repeteix = 0.0; return }
        val s = sinte ?: return
        if (bpm() <= 0.0 || !sona || fabricaFins == null) { repeteix = 0.0; return }
        val b = batecAudible()
        if (b < 0.0) { repeteix = 0.0; return }
        repBeat0 = if (quant) Math.round(b).toDouble() else b          // amb quantització el bucle comença al batec més proper (com a rekordbox)
        repeteix = n; repPos = b - repBeat0
        demanaRecanvi()
    }
    /** Amb un bucle actiu, en divideix (factor 0,5) o en dobla (2) la llargada, entre 1/8 i 64 batecs; el punt d'inici no canvia. */
    @Synchronized fun escalaBucle(factor: Double) { if (repeteix > 0.0) repeteix = (repeteix * factor).coerceIn(0.125, 64.0) }

    // ---- Hot cues (4 per deck): punts als quals se salta a l'instant; cada un té un sintetitzador de recanvi preparat ----
    /** 4 hot cues + una cinquena posició reservada: el punt «32 batecs abans del final» (botó −32), preparat en segon pla perquè el salt sigui instantani. */
    val hotBeat = DoubleArray(5) { -1.0 }
    private val hotSinte = arrayOfNulls<Sintetitzador>(5)
    private val hotGen = IntArray(5)
    private fun preparaHot(k: Int) {
        val f = fabricaFins ?: return
        val b0 = hotBeat[k]; val gen = hotGen[k]; if (b0 < 0.0) return
        Thread {
            try { val r = f(b0); synchronized(this) { if (gen == hotGen[k] && hotSinte[k] == null) hotSinte[k] = r } } catch (e: Throwable) { }
        }.also { it.isDaemon = true }.start()
    }
    /** Desa el hot cue `k` on és ara el deck (al batec més proper si `quant`). */
    @Synchronized fun posaHot(k: Int, quant: Boolean) {
        val b = batecAudible(); if (b < 0.0) return
        hotBeat[k] = if (quant) Math.round(b).toDouble() else b
        hotGen[k]++; hotSinte[k] = null
        preparaHot(k)
    }
    @Synchronized fun esborraHot(k: Int) { hotBeat[k] = -1.0; hotGen[k]++; hotSinte[k] = null }
    /** Salta al hot cue `k` a l'instant (fals si no n'hi ha o el recanvi encara no és a punt). */
    @Synchronized fun vesAHot(k: Int): Boolean {
        if (hotBeat[k] < 0.0) return false
        val r = hotSinte[k] ?: return false
        hotSinte[k] = null; repGen++; recanvi = null; repeteix = 0.0
        sinte = r; ple = false; idx = CH.toDouble(); enCue = false; entra = FOSA
        preparaHot(k)
        return true
    }

    // ---- CUE (com als CDJ/rekordbox): un punt de la cançó al qual es torna a l'instant. Hi ha sempre un sintetitzador de recanvi preparat en aquell punt. ----
    /** Posició del cue en batecs (0 = principi de la cançó). */
    @Volatile var cueBeat = 0.0
    /** El deck és aturat al punt de cue (o al principi, acabat de carregar). */
    @Volatile var enCue = true
    @Volatile private var cueSinte: Sintetitzador? = null
    @Volatile private var cueGen = 0
    /** Prepara en segon pla el sintetitzador del punt de cue (cal `fabricaFins`). */
    fun preparaCue() {
        val f = fabricaFins ?: return
        val b0 = cueBeat; val gen = cueGen
        Thread {
            try { val r = f(b0); if (gen == cueGen && cueSinte == null) cueSinte = r } catch (e: Throwable) { }
        }.also { it.isDaemon = true }.start()
    }
    /** Posa el punt de cue on és ara el deck (al batec més proper si `quant`); només amb cançons amb compte de batecs. */
    @Synchronized fun posaCue(quant: Boolean) {
        val b = batecAudible(); if (b < 0.0) return
        cueBeat = if (quant) Math.round(b).toDouble() else b
        cueGen++; cueSinte = null; enCue = true
        preparaCue()
    }
    /** Torna al punt de cue a l'instant (fals si el recanvi encara no és a punt); no canvia si sona o no. */
    @Synchronized fun vesACue(): Boolean {
        val r = cueSinte ?: return false
        cueSinte = null; repGen++; recanvi = null; repeteix = 0.0
        sinte = r; ple = false; idx = CH.toDouble(); enCue = true; entra = FOSA
        preparaCue()
        return true
    }

    /**
     * Salta endavant `delta` batecs a l'instant: el mateix sintetitzador avança en silenci (un batec són pocs mil·lisegons de càlcul). Retorna fals si no té compte de batecs.
     */
    @Synchronized fun avancaBatecs(delta: Double): Boolean {
        val s = sinte ?: return false
        val b = batecAudible(); if (b < 0.0 || bpm() <= 0.0 || delta <= 0.0) return false
        val desti = b + delta; val v0 = s.voltes
        val max = (delta * 60.0 / bpm() * s.taux * 2.0).toInt() + s.taux; var fet = 0
        val buf = ShortArray(64)
        while (s.batec < desti && !s.acabada && s.voltes == v0 && fet < max) { s.renderitza(buf, 32); fet += 32 }
        ple = false; idx = CH.toDouble(); enCue = false; entra = FOSA
        if (repeteix > 0.0) { repGen++; recanvi = null; repeteix = 0.0 }
        return true
    }

    /** Salta a `n` batecs abans del final de la cançó (on torna al bucle), a l'instant i sense parar el deck. Fals si no té compte de batecs o ja és a l'últim tros. */
    @Synchronized fun vesAlFinal(n: Double): Boolean {
        val p = batecAudible(); if (durada <= 0.0 || p < 0.0) return false
        val delta = durada - n - p
        if (delta <= 0.0) return false
        if (n == PUNT_FINAL && hotBeat[4] >= 0.0 && vesAHot(4)) return true           // el motor ja és preparat al punt: canvi instantani, sense talls
        return avancaBatecs(delta)                                                       // encara no és a punt: avança en silenci (pot fer un tall)
    }
    /** Prepara en segon pla el motor a `PUNT_FINAL` batecs abans del final (quan es coneix la durada). */
    @Synchronized fun preparaFinal() {
        val b = durada - PUNT_FINAL; if (b <= 0.0) return
        hotBeat[4] = b; hotGen[4]++; hotSinte[4] = null
        preparaHot(4)
    }

    /** Prepara en segon pla un sintetitzador al batec d'inici del bucle, per fer-lo servir quan acabi la passada. */
    private fun demanaRecanvi() {
        val f = fabricaFins ?: return
        if (creantRecanvi) return
        creantRecanvi = true
        val b0 = repBeat0; val gen = repGen
        Thread {
            try { val r = f(b0); if (repeteix > 0.0 && gen == repGen) recanvi = r } catch (e: Throwable) { }
            finally { creantRecanvi = false }
        }.also { it.isDaemon = true }.start()
    }

    /** Torna a preparar els sintetitzadors de recanvi del cue i dels hot cues (p. ex. quan canvien els instruments). */
    @Synchronized fun refesRecanvis() { cueGen++; cueSinte = null; preparaCue(); for (k in 0 until 5) { hotGen[k]++; hotSinte[k] = null; preparaHot(k) } }

    /** Canvia el sintetitzador (p. ex. en saltar a un batec) sense tocar el títol ni la tonalitat; el deck queda al principi del que es generi. */
    @Synchronized fun canviaSinte(s: Sintetitzador) { repGen++; recanvi = null; repeteix = 0.0; sinte = s; ple = false; idx = CH.toDouble(); enCue = false; entra = FOSA }

    /** Master tempo (bloqueig del to, com a rekordbox): el tempo canvia sense canviar el to; si és fals, el to segueix el tempo com en un plat. Només als motors que ho poden fer (m4a i SSEQ). */
    @Volatile var bloqueigTo = true
    /** Canvi de to independent del tempo, en semitons (-12..12). */
    @Volatile var semitons = 0
    /** Factor de to respecte al de la cançó (1 = original): el que es remostreja. Als motors sense tempo propi el to i el tempo van units. */
    fun factorTo(): Double {
        val sem = Math.pow(2.0, semitons / 12.0)
        return if (sinte?.tempoPropi == true) (if (bloqueigTo) 1.0 else mult()) * sem else mult() * sem
    }
    /** Velocitat respecte a la de la cançó (1 = original): pitch triat, bend o sync. */
    fun mult(): Double { val b = bpm(); return (if (bpmFix > 0.0 && b > 0.0) (bpmFix / b).coerceIn(0.5, 2.0) else 1.0 + tempoPc / 100.0) + bend * BEND }

    /** Posició en batecs del que se sent ara (la del motor menys el que ja s'ha generat i encara no ha sonat), o -1. */
    fun batecAudible(): Double {
        val s = sinte ?: return -1.0
        val b = s.batec; if (b < 0.0) return -1.0
        if (!ple) return b
        return b - (CH - idx).coerceAtLeast(0.0) / s.taux * bpm() * (if (s.tempoPropi) mult() / factorTo() else 1.0) / 60.0
    }
    /** Número del batec dins del compàs (0 = l'«1»), o -1 si es desconeix. */
    fun numeroBatec(): Int { val b = batecAudible(); return if (b < 0.0) -1 else Math.floorMod(Math.floor(b).toLong() + desplacament, compas.toLong()).toInt() }
    /** La tonalitat amb el pitch aplicat: cada semitò (6%) d'accelerar puja l'altura una nota. */
    fun tonalitatEfectiva(): Tonalitat? {
        val t = tonalitat ?: return null
        return t.desplacada(Math.round(12.0 * Math.log(factorTo()) / Math.log(2.0)).toInt())
    }
    /** Passa el sync al pitch manual (el que tenia en aquest moment). */
    fun trencaSync() { sinc = false; bpmFix = 0.0 }
    /**
     * Sync: fixa el BPM efectiu d'aquest deck al que té ara l'altre (`bpmFix`): es manté encara que la cançó porti canvis de tempo; no segueix l'altre deck ni alinea els batecs.
     * Retorna false si algun dels dos no té BPM.
     */
    fun igualaBpm(o: Deck): Boolean {
        val s = sinte; val so = o.sinte
        if (s == null || so == null || bpm() <= 0.0 || o.bpm() <= 0.0) return false
        bpmFix = o.bpm() * (o.mult() - o.bend * BEND); sinc = true
        return true
    }

    /**
     * Key sync (com a rekordbox): posa els semitons d'aquest deck perquè la seva tonalitat quedi igual que la de l'altre deck (`o`); si els modes són diferents (major/menor), a la
     * tonalitat relativa (mateix número a la roda de Camelot: compatible). Retorna els semitons triats o null si algun dels dos no té tonalitat. Compta el canvi de to que ja porta el tempo (KEY LOCK apagat).
     */
    fun igualaTo(o: Deck): Int? {
        val t = tonalitat ?: return null; val r = o.tonalitatEfectiva() ?: return null
        val extra = Math.round(12.0 * Math.log(factorTo() / Math.pow(2.0, semitons / 12.0)) / Math.log(2.0)).toInt()
        val objectiu = if (t.major == r.major) r.tonica else if (r.major) Math.floorMod(r.tonica - 3, 12) else Math.floorMod(r.tonica + 3, 12)
        var s = Math.floorMod(objectiu - t.tonica - extra, 12); if (s > 6) s -= 12
        semitons = s
        return s
    }

    private fun omple(s: Sintetitzador) {
        if (ple) { src[0] = src[2 * CH]; src[1] = src[2 * CH + 1] } else { src[0] = 0; src[1] = 0 }
        s.solo = solo; s.renderitza(tmp, CH)
        System.arraycopy(tmp, 0, src, 2, 2 * CH)
        ple = true
    }

    /** Suma `frames` mostres estèreo (a `taxa` Hz) del deck a `out` (floats de -1 a 1 aprox.); no fa res si està parat. */
    @Synchronized fun mescla(out: FloatArray, frames: Int, taxa: Int, guany: Float = 1f) {
        var s = sinte ?: return
        if (!sona) return
        val p = factorTo()
        if (s.tempoPropi) s.ritme = mult() / p          // m4a i SSEQ: el tempo canvia el rellotge del seqüenciador; el que es remostreja és el to (p). La resta de motors: to i tempo units
        val ratio = s.taux.toDouble() / taxa * p
        val g = volum / 100f * 0.7f / 32768f * guany
        enCue = false
        val perMostra = bpm() * mult() / (60.0 * taxa)          // batecs per mostra de sortida
        val a1 = (1.0 - Math.exp(-2.0 * Math.PI * 250.0 / taxa)).toFloat(); val a2 = (1.0 - Math.exp(-2.0 * Math.PI * 2500.0 / taxa)).toFloat()
        val gG = if (eqKill[0]) 0f else eq[0]; val gM = if (eqKill[1]) 0f else eq[1]; val gA = if (eqKill[2]) 0f else eq[2]
        val pla = gG == 1f && gM == 1f && gA == 1f
        var maxim = pic * 0.97f
        for (o in 0 until frames) {
            var env = 1f
            // Repetició: en acabar els N batecs, es torna al batec d'inici amb el sintetitzador de recanvi (si encara no és a punt, la cançó segueix i ho prova a cada mostra)
            if (repeteix > 0.0) {
                repPos += perMostra
                if (repPos >= repeteix) {
                    val r = recanvi
                    if (r != null) {
                        recanvi = null; sinte = r; s = r; ple = false; idx = CH.toDouble(); repPos %= repeteix; entra = FOSA
                        if (r.tempoPropi) r.ritme = mult() / p
                        demanaRecanvi()
                    }
                } else if (recanvi != null && perMostra > 0.0) {
                    val falten = (repeteix - repPos) / perMostra                     // mostres fins a la volta: fosa de sortida
                    if (falten < FOSA) env = (falten / FOSA).toFloat().coerceIn(0f, 1f)
                }
            }
            if (entra > 0) { env *= (FOSA - entra).toFloat() / FOSA; entra-- }
            if (idx >= CH) { idx -= CH; omple(s) }
            val k = idx.toInt(); val f = (idx - k).toFloat()
            val l0 = src[2 * k].toInt(); val l1 = src[2 * k + 2].toInt(); val r0 = src[2 * k + 1].toInt(); val r1 = src[2 * k + 3].toInt()
            var xl = (l0 + (l1 - l0) * f) * g * env; var xr = (r0 + (r1 - r0) * f) * g * env
            // Dos filtres d'un pol per canal reparteixen el senyal en tres bandes que sumades donen l'original
            fL1 += a1 * (xl - fL1); fL2 += a2 * (xl - fL2); fR1 += a1 * (xr - fR1); fR2 += a2 * (xr - fR2)
            if (!pla) { xl = fL1 * gG + (fL2 - fL1) * gM + (xl - fL2) * gA; xr = fR1 * gG + (fR2 - fR1) * gM + (xr - fR2) * gA }
            val ab = Math.max(Math.abs(xl), Math.abs(xr)); if (ab > maxim) maxim = ab
            out[2 * o] += xl
            out[2 * o + 1] += xr
            idx += ratio
        }
        pic = maxim
        if (s.acabada) sona = false        // una cançó sense bucle que acaba para el deck
    }
}

//ANDROID
/** Els dos decks i el fil d'àudio que els mescla (44100 Hz, estèreo): només sona mentre algun deck està en marxa. */
object Mescla {
    const val TAXA = 44100
    /**
     * Quatre decks: A, B, C i D (0 a 3). A i C són del costat esquerre i B i D del dret; a la pantalla se'n veu **un de cada costat**, i es tria de manera independent (A o C a
     * l'esquerra, B o D a la dreta: A+B, A+D, C+B o C+D). Els quatre sonen alhora; el crossfader atenua el costat esquerre (A i C) o el dret (B i D).
     */
    val decks = Array(4) { Deck(it) }
    /** Deck que es veu a l'esquerra (0 = A, 2 = C) i a la dreta (1 = B, 3 = D). */
    val visible = intArrayOf(0, 1)
    /** El deck del costat contrari que es veu: la referència del SYNC, del KEY SYNC i de la quantització. */
    fun ref(d: Int): Deck = decks[visible[1 - (d % 2)]]
    /** Referència per a la quantització: el deck del costat contrari que es veu, si sona; si no, l'altre deck d'aquell costat, si sona. */
    fun refSonant(d: Int): Deck? { val v = ref(d); return if (v.sona) v else decks[v.num xor 2].takeIf { it.sona } }
    /** Quantització (opció): començar un deck mentre l'altre sona l'ajorna fins a la mateixa fase de batec. Desactivada, comença a l'instant (algunes cançons no hi quadren). */
    @Volatile var quantitza = true
    @Volatile private var generacio = 0
    @Volatile private var fil = false

    /** Inicia o atura el deck (si ja té cançó). En iniciar, la ràdio normal s'atura. */
    fun alterna(d: Int) {
        val k = decks[d]; if (k.sinte == null) return
        if (preview[d]) { preview[d] = false; return }          // play mentre es manté el CUE: segueix sonant en deixar-lo anar (com als CDJ)
        if (k.pendent) { k.pendent = false; return }
        if (k.sona) { k.sona = false; return }
        RadioMotor.atura()
        val o = refSonant(d)
        // Quantització: si un altre deck sona (el màster, si sona), aquest comença quan l'altre arriba a la mateixa fase de batec
        if (quantitza && o != null && k.batecAudible() >= 0.0 && o.batecAudible() >= 0.0) k.pendent = true else k.sona = true
        arrenca()
    }
    fun volum(d: Int, delta: Int) { val k = decks[d]; k.volum = (k.volum + delta).coerceIn(0, 100) }
    /** Posa el tempo del deck en % (dins del rang triat); deixa anar el BPM fix del SYNC. */
    fun fixaTempo(d: Int, pc: Double) { val k = decks[d]; k.trencaSync(); k.tempoPc = pc.coerceIn(-k.rang.toDouble(), k.rang.toDouble()) }
    /** Canvia el rang del lliscador de tempo: ±6, ±10, ±16, ±50 (WIDE). */
    fun canviaRang(d: Int) { val k = decks[d]; val r = listOf(6, 10, 16, 50); k.rang = r[(r.indexOf(k.rang) + 1) % r.size]; k.tempoPc = k.tempoPc.coerceIn(-k.rang.toDouble(), k.rang.toDouble()) }
    /** Crossfader: 0 = només el costat esquerre (A i C), 0,5 = tots dos sencers, 1 = només el dret (B i D). */
    @Volatile var creuament = 0.5f
    fun guanyCreuament(d: Int): Float { val c = creuament; return if (d % 2 == 0) minOf(1f, 2f * (1f - c)) else minOf(1f, 2f * c) }
    /** Limitador suau: fins a 0,7 no toca res; per sobre, corba que mai no passa d'1 (els dos decks sumats ja no es retallen en sec). */
    fun limita(x: Float): Float { val a = Math.abs(x); return if (a <= 0.7f) x else Math.signum(x) * (0.7f + 0.3f * Math.tanh(((a - 0.7f) / 0.3f).toDouble()).toFloat()) }
    /** Pic de la sortida general abans del limitador (per al mesurador). */
    @Volatile var picMestre = 0f
    /** Hot cue `k` del deck: si no n'hi ha, el desa on és; si n'hi ha, hi salta i sona (com a rekordbox). Amb `esborra`, el treu. */
    fun hot(d: Int, k: Int, esborra: Boolean) {
        val dk = decks[d]; if (dk.sinte == null) return
        if (esborra) { dk.esborraHot(k); return }
        if (dk.hotBeat[k] < 0.0) { dk.posaHot(k, quantitza); return }
        if (!dk.vesAHot(k)) { Thread { for (i in 0 until 150) { if (dk.vesAHot(k)) break; Thread.sleep(20) }; continuaSiCal(d) }.also { it.isDaemon = true }.start(); return }
        continuaSiCal(d)
    }
    private fun continuaSiCal(d: Int) { val dk = decks[d]; if (!dk.sona) { RadioMotor.atura(); dk.pendent = false; dk.sona = true; arrenca() } }
    /** S'avisa quan el fil d'àudio del mesclador arrenca i quan s'atura (servei en primer pla) i a cada bloc mesclat (gravació). */
    @Volatile var alArrencar: (() -> Unit)? = null
    @Volatile var alAturar: (() -> Unit)? = null
    @Volatile var alBloc: ((ShortArray, Int) -> Unit)? = null
    /** Play: inicia el deck (amb quantització) si no sona. */
    fun play(d: Int) { val k = decks[d]; if (k.sinte != null && !k.sona && !k.pendent) alterna(d) }
    /** Pausa: atura el deck on és (o cancel·la l'espera de la quantització). */
    fun pausa(d: Int) { val k = decks[d]; if (k.pendent) k.pendent = false else k.sona = false }
    private val preview = BooleanArray(4)
    /**
     * Botó CUE premut (com als CDJ/rekordbox): si el deck sona, torna al punt de cue i s'atura; si és aturat fora del cue, posa el cue on és; si és aturat al cue, sona
     * mentre es manté el botó (`cueSolta` el torna al cue).
     */
    fun cuePrem(d: Int) {
        val k = decks[d]; if (k.sinte == null) return
        if (k.sona || k.pendent) {
            k.sona = false; k.pendent = false
            if (!k.vesACue()) Thread { for (i in 0 until 150) { if (k.sona || k.vesACue()) break; Thread.sleep(20) } }.also { it.isDaemon = true }.start()
        } else if (k.enCue) { RadioMotor.atura(); preview[d] = true; k.sona = true; arrenca() }
        else k.posaCue(quantitza)
    }
    fun cueSolta(d: Int) {
        val k = decks[d]
        if (!preview[d]) return
        preview[d] = false; k.sona = false
        if (!k.vesACue()) Thread { for (i in 0 until 150) { if (k.sona || k.vesACue()) break; Thread.sleep(20) } }.also { it.isDaemon = true }.start()
    }
    /** Sync: iguala el BPM del deck al de l'altre (un sol cop; cal que els dos tinguin BPM). */
    fun igualaBpm(d: Int) { decks[d].igualaBpm(ref(d)) }
    /** Key sync: posa el to del deck igual (o relatiu) al del màster; retorna els semitons o null si falta alguna tonalitat. */
    fun igualaTo(d: Int): Int? = decks[d].igualaTo(ref(d))
    /** Mou l'«1» del compte un batec (deck d). */
    fun desplaca(d: Int) { val k = decks[d]; k.desplacament = (k.desplacament + 1) % k.compas }
    /** Compte en 3 o en 4 (deck d). */
    fun compas(d: Int) { val k = decks[d]; k.compas = if (k.compas == 4) 3 else 4; k.desplacament = 0 }
    /** Torna a fer sonar el deck (després de buscar un batec): no passa per la quantització. */
    fun continua(d: Int) { decks[d].sona = true; arrenca() }
    /** Atura els dos decks (quan comença la ràdio normal). */
    fun atura() { for (k in decks) { k.sona = false; k.pendent = false; k.sinc = false } }

    @Synchronized private fun arrenca() {
        if (fil) return
        fil = true
        val gen = ++generacio
        Thread {
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) } catch (e: Throwable) { }
            var at: android.media.AudioTrack? = null
            try {
                val min = android.media.AudioTrack.getMinBufferSize(TAXA, android.media.AudioFormat.CHANNEL_OUT_STEREO, android.media.AudioFormat.ENCODING_PCM_16BIT)
                at = android.media.AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(android.media.AudioFormat.Builder().setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).setSampleRate(TAXA).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(maxOf(min, 4096)).setPerformanceMode(android.media.AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).setTransferMode(android.media.AudioTrack.MODE_STREAM).build()
                at.play()
                try { alArrencar?.invoke() } catch (e: Throwable) { }
                val f = FloatArray(512); val b = ShortArray(512)
                var enPausa = false; var ocios = 0L
                // El fil es queda viu 30 s sense cap deck sonant (amb la sortida en pausa): així el play següent és immediat
                while (generacio == gen) {
                    if (!decks.any { it.sona || it.pendent }) {
                        if (!enPausa) { at.pause(); at.flush(); enPausa = true; ocios = System.currentTimeMillis(); picMestre = 0f; for (k in decks) k.pic = 0f }
                        if (System.currentTimeMillis() - ocios > 30000L) break
                        Thread.sleep(3); continue
                    }
                    if (enPausa) { at.play(); enPausa = false }
                    java.util.Arrays.fill(f, 0f)
                    for (k in decks) {
                        if (k.pendent) {                       // quantització: comença quan l'altre deck és a la mateixa fase de batec
                            val o = refSonant(k.num); val bk = k.batecAudible(); val bo = o?.batecAudible() ?: -1.0
                            val tol = 128.0 / TAXA * ((o?.bpm() ?: 0.0).takeIf { it > 0.0 } ?: 120.0) / 60.0 * (o?.mult() ?: 1.0)
                            if (o == null || bk < 0.0 || bo < 0.0 || Math.abs(difFase(bo, bk)) <= tol) { k.pendent = false; k.sona = true }
                        }
                    }
                    for (k in decks) try { k.mescla(f, 256, TAXA, guanyCreuament(k.num)) } catch (e: Throwable) { k.sona = false; k.error = e.javaClass.simpleName + ": " + (e.message ?: "") }
                    for (k in decks) try { k.recorda { c, b, t, m, f -> RadioDades.gravaAnalisi(c, RadioDades.Analisi(b, t, m, f)) } } catch (e: Throwable) { }
                    var pm = picMestre * 0.97f
                    for (i in f.indices) { val v = f[i]; val av = Math.abs(v); if (av > pm) pm = av; b[i] = (limita(v) * 32767f).toInt().toShort() }
                    picMestre = pm
                    at.write(b, 0, b.size)
                    try { alBloc?.invoke(b, b.size) } catch (e: Throwable) { }
                }
            } catch (e: Throwable) { for (k in decks) { k.sona = false; k.error = e.javaClass.simpleName } }
            finally { try { at?.stop(); at?.release() } catch (e: Throwable) {}; try { alAturar?.invoke() } catch (e: Throwable) {}; synchronized(this@Mescla) { if (generacio == gen) fil = false } ; if (decks.any { it.sona || it.pendent }) arrenca() }
        }.also { it.isDaemon = true }.start()
    }
}
