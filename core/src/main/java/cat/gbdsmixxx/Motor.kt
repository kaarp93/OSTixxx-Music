package cat.gbdsmixxx

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import kotlin.math.pow

/**
 * Ràdio: reprodueix la música de Diamant/Perla des del `sound_data.sdat` de la ROM de l'usuari (es copia a la memòria privada en
 * importar). Format SDAT llegit com a GBATEK (INFO + FAT + SYMB); bancs d'instruments (SBNK), arxius d'ones (SWAR: PCM8 amb signe,
 * PCM16 i ADPCM) i partitures (SSEQ) verificats amb el fitxer real. El so es sintetitza en directe: no es desa cap cançó.
 * Aproximacions (no comprovades amb l'original): corbes d'envolupant, volum i el soroll/pols (PSG).
 */
/** Qualsevol sintetitzador de la ràdio (SDAT de la DS, M4A de GBA...): genera mostres estèreo i deixa silenciar pistes. */
/** Últimes mostres (0 a 1, a ~8 kHz) de la sortida de cada canal del xip, per a l'oscil·loscopi d'OSTixxx Music; `visibles` = quants canals tenen dades (els del VRC6 només si el joc els fa servir). */
class OscDades(val noms: List<String>, val mida: Int = 512) {
    val buf = Array(noms.size) { FloatArray(mida) }
    @Volatile var pos = 0
    @Volatile var visibles = noms.size
    fun escriu(v: FloatArray) { val p = pos; for (c in buf.indices) buf[c][p] = v[c]; pos = (p + 1) % mida }
}

/**
 * Oscil·loscopi del so total (després de l'equalitzador) per a **tots** els motors: una sola traça, a ~8 kHz. Els motors que saben donar els canals per separat (ara NES, `Sintetitzador.oscil`) en tenen també
 * una traça per canal; per als altres (GB, GBA, DS, SNES) és l'únic que hi ha. Només s'alimenta mentre l'oscil·loscopi és a la pantalla (`Oscil.visible`).
 */
object OscMestre {
    val dades = OscDades(listOf("Mix"), 1024)
    /** Esquerra i dreta per separat (0 a 1), a la mateixa freqüència: l'«Estèreo» del mode concert d'OSTixxx Music (2026-11-17). */
    val estereo = OscDades(listOf("L", "R"), 1024)
    private val un = FloatArray(1)
    private val dos = FloatArray(2)
    fun alimenta(buf: ShortArray, frames: Int) {
        val v = un; val lr = dos; var i = 0
        while (i < frames) {
            val l = buf[2 * i].toInt(); val r = buf[2 * i + 1].toInt()
            v[0] = (l + r) / 65536f + 0.5f; dades.escriu(v)
            lr[0] = l / 65536f + 0.5f; lr[1] = r / 65536f + 0.5f; estereo.escriu(lr)
            i += 4
        }
    }
}

/** L'oscil·loscopi és obert i l'app és a la pantalla: només llavors els motors desen mostres per canal (si no, no costa res). */
object Oscil { @Volatile var visible = false }

interface Sintetitzador {
    val taux: Int
    var solo: Int
    fun renderitza(out: ShortArray, frames: Int)
    fun pistaActiva(i: Int): Boolean
    /** Voltes que ha fet la cançó (cada cop que la pista de referència torna al principi del bucle); 0 si el motor no ho pot saber (SNES). */
    val voltes: Int get() = 0
    /**
     * Nota MIDI exacta que sona ara a cada canal del visualitzador (mateix ordre que `oscil`), llegida de l'estat del motor (registres de l'APU): `out[i]` = nota amb decimals o
     * -1 si el canal no sona o no té to (soroll, DMC). Retorna false si el motor no ho sap (el piano del concert les estima llavors a partir de l'ona). Es crida des de la UI.
     */
    fun notesMidi(out: FloatArray): Boolean = false
    /** Tempo actual de la cançó en negres per minut (BPM), o 0 si el motor no en té (streams, SNES) o no es pot expressar així (Game Boy). Pot canviar durant la cançó. */
    val bpm: Double get() = 0.0
    /** Posició en batecs (negres) des del principi de la cançó, amb decimals; en tornar al bucle es posa on era el cop anterior. -1 si el motor no ho sap. */
    val batec: Double get() = -1.0
    /**
     * Histograma de les 12 notes (do, do#... si) pesat per la durada de les notes melòdiques (sense bateria) de la cançó fins al primer bucle, fent córrer
     * el seqüenciador sense sintetitzar (milisegons); null si el motor no ho sap fer. **Consumeix aquest sintetitzador: cal fer-ho en una còpia nova.**
     */
    fun analitza(maxSegons: Int = 300): DoubleArray? = null
    /** La cançó ja no sona (no té bucle i ha acabat): la ràdio passa a la següent. */
    val acabada: Boolean get() = false
    /** Velocitat del seqüenciador respecte a la de la cançó (1 = original) **sense canviar el to**: només accelera el rellotge de les notes, com la comanda de tempo del joc. */
    var ritme: Double get() = 1.0; set(v) {}
    /** Quantes notes ha tocat cada número d'instrument (0 a 127) de la cançó, omplert per `analitza` (null si el motor no ho sap): són els instruments que **usa aquesta cançó**. */
    val usos: IntArray? get() = null
    /** Volum de cada pista (0 a 1, 1 = el de la cançó), canviable mentre sona (Funsies); null si el motor no ho sap fer. */
    val volPistes: FloatArray? get() = null
    /** Nivell actual (0 a 1) de cada canal del xip de so, per al visualitzador d'OSTixxx Music «depenent de canal»; null si el motor no ho sap (llavors es fa servir el so total). */
    val nivells: FloatArray? get() = null
    /** Mostres recents per canal per a l'oscil·loscopi; null si el motor no ho sap (ara: NES). */
    val oscil: OscDades? get() = null
    /** Perfil de cada pista, omplert per `analitza` (null si el motor no ho sap): per saber quin canal és bateria, baix, melodia... */
    val perfils: Array<PerfilPista?>? get() = null
    /** Si la cançó fa bucle, quan el motor ho sap sense fer-la córrer (streams); null si cal analitzar-la (`analitza` i mirar `voltes`). */
    val teBucle: Boolean? get() = null
    /** Posició més alta a què ha arribat la cançó, en batecs (el final del bucle si en té); -1 si el motor no ho sap. */
    val batecMax: Double get() = -1.0
    /** Per al mesclador: fa l'anàlisi (pols, bucle, durada) que el motor necessita i que no es fa en reproduir (pot trigar segons). Cal cridar-la en un fil de fons, abans de llegir `bpm`/`batec`/`batecMax`. */
    fun preparaMesclador() {}
    /** El motor pot canviar el tempo sense canviar el to (m4a i SSEQ); si no (streams, àudio gravat), el mesclador remostreja i el to canvia. */
    val tempoPropi: Boolean get() = false
}

/** Freqüència de sortida (Hz) de la ràdio per a GBA, DS, Game Boy i TCG; la tria l'usuari a Config > Ràdio i es llegeix en crear cada cançó. L'SNES va sempre a la freqüència real del seu DSP (32040 Hz, `RegioVideo.TAUX_SNES`); Android la remostreja a la del mòbil. */
// L'equalitzador d'abans (`EqRadio`: filtres d'un pol, feia la meitat del que deia el lliscador) l'ha substituït `CadenaEfectes` (Efectes.kt) amb l'estat d'`EfectesRadio`.

/** Espectre de la sortida de la ràdio en 12 bandes (per al visualitzador d'OSTixxx Music): energia de cada banda, de 0 a 1, amb caiguda suau. */
/** L'app és a la pantalla (activitat començada): amb la pantalla apagada o l'app en segon pla no es calcula res que només serveixi per dibuixar (espectre, nivells per canal). */
object EnPantalla { @Volatile var v = true }

object Espectre {
    const val N = 12
    @Volatile var valors = FloatArray(N)
    private val FREQ = floatArrayOf(60f, 110f, 200f, 350f, 600f, 1000f, 1700f, 2800f, 4500f, 7000f, 11000f)
    private val lp = FloatArray(N - 1)
    // coeficients (només es recalculen si canvia la freqüència) i acumuladors reutilitzats; `valors` alterna entre dos arrays (cap assignació per bloc)
    private val a = FloatArray(N - 1); private var aTaux = -1
    private val e = FloatArray(N)
    private val doble = arrayOf(FloatArray(N), FloatArray(N)); private var qual = 0
    fun actualitza(buf: ShortArray, frames: Int, taux: Int) {
        if (!EnPantalla.v) return
        if (taux != aTaux) { for (k in 0 until N - 1) a[k] = (1.0 - Math.exp(-2.0 * Math.PI * FREQ[k] / taux)).toFloat(); aTaux = taux }
        e.fill(0f)
        for (i in 0 until frames) {
            val x = (buf[2 * i].toInt() + buf[2 * i + 1].toInt()) * 0.5f
            var prev = 0f
            for (k in 0 until N - 1) { lp[k] += a[k] * (x - lp[k]); val b = lp[k] - prev; e[k] += b * b; prev = lp[k] }
            val b = x - prev; e[N - 1] += b * b
        }
        val vell = valors; qual = 1 - qual; val nou = doble[qual]
        for (k in 0 until N) nou[k] = maxOf((Math.sqrt((e[k] / frames).toDouble()).toFloat() / 32768f * 7f).coerceIn(0f, 1f), vell[k] * 0.86f)
        valors = nou
    }
}

/**
 * Comptador dels fils de fons vius (ronda d'optimització, 2026-11-18), perquè l'autor pugui comprovar a l'extractor de proves (Ajustos) que no s'acumulen:
 * `audio` = fils d'àudio de la ràdio (1 mentre sona; 2 un moment en canviar de cançó), `compara` = preparacions del mode comparació,
 * `analisi` = anàlisis de ritme calculant ara (`RitmeRom`), `total` = l'anàlisi total d'Ajustos. `maxAudio` = el màxim de fils d'àudio vius alhora des que s'ha obert l'app.
 */
object FilsVius {
    val audio = java.util.concurrent.atomic.AtomicInteger()
    val compara = java.util.concurrent.atomic.AtomicInteger()
    val analisi = java.util.concurrent.atomic.AtomicInteger()
    val total = java.util.concurrent.atomic.AtomicInteger()
    @Volatile var maxAudio = 0
    fun entraAudio(): Int { val n = audio.incrementAndGet(); if (n > maxAudio) maxAudio = n; return n }
    fun text(): String = t("Fils vius") + ": " + t("àudio") + " " + audio.get() + " (" + t("màxim") + " " + maxAudio + "), " + t("compara") + " " + compara.get() + ", " +
        t("anàlisi") + " " + analisi.get() + ", " + t("anàlisi total") + " " + total.get()
}

object RadioFreq { @Volatile var taux = 32768; val OPCIONS = listOf(22050, 32768, 44100) }

/**
 * Ona d'un SWAV ja descodificada. `temps` és el camp «Time» de l'SWAV (ARM7_CLOCK / taxa, GBATEK «DS Sound Files - SWAV»): el NitroSDK el
 * passa tal qual a SND_CalcTimer (pret/pokediamond arm7/lib/src/SND_exChannel.c, SND_StartExChannelPcm copia `waveParam`), i és el que
 * fixa el to real. `format` 0 PCM8, 1 PCM16, 2 IMA-ADPCM; `paraules` = PNT+LEN en paraules de 4 bytes (menys de 4 penja el canal, GBATEK).
 */
class Ona(val sm: ShortArray, val taxa: Int, val bucle: Boolean, val iniciBucle: Int, val temps: Int = 0, val format: Int = 0, val paraules: Int = 4)

class Regio(val tipus: Int, val swav: Int, val swar: Int, val nota: Int, val a: Int, val d: Int, val s: Int, val r: Int, val pan: Int)

class Sdat(private val d: ByteArray) {
    private fun u8(o: Int) = d[o].toInt() and 0xFF
    private fun u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
    private fun u32(o: Int) = u16(o) or (u16(o + 2) shl 16)
    private val symb = u32(0x10); private val info = u32(0x18); private val fat = u32(0x20)

    fun comptar(k: Int) = u32(info + u32(info + 8 + 4 * k))
    /** Entrada `i` de la categoria `k` del bloc INFO, o -1 si no existeix (també si l'índex és fora de la taula: jocs desconeguts en poden portar). */
    fun entrada(k: Int, i: Int): Int {
        val ro = u32(info + 8 + 4 * k)
        if (i < 0 || i >= u32(info + ro)) return -1
        val o = u32(info + ro + 4 + 4 * i); return if (o == 0 || info + o + 4 > d.size) -1 else info + o
    }
    /** Registre `i` de la categoria `k` de l'INFO; si no existeix (jocs desconeguts), una excepció que diu què falta. */
    private fun reg(k: Int, i: Int, nom: String): Int { val e = entrada(k, i); if (e < 0) throw IllegalStateException("$nom $i sense registre"); return e }
    /** `fid0` pot portar marques als bits alts (p. ex. 0x0100008B a Castlevania: Ecclesia): el número d'arxiu són els 24 bits baixos. */
    fun fitxer(fid0: Int): ByteArray {
        val fid = fid0 and 0xFFFFFF
        val n = u32(fat + 8)
        if (fid < 0 || fid >= n) throw IllegalStateException("arxiu $fid fora de la FAT ($n)")
        val fo = u32(fat + 12 + 16 * fid); val fs = u32(fat + 12 + 16 * fid + 4)
        if (fo < 0 || fs < 0 || fo + fs > d.size) throw IllegalStateException("arxiu $fid fora de l'SDAT")
        return d.copyOfRange(fo, fo + fs)
    }

    /** Si l'arxiu porta noms (bloc SYMB); si no, les cançons es mostren pel seu número. */
    val teNoms: Boolean by lazy { u32(0x14) > 16 }

    /** Nom (de SYMB) de l'element `i` de la categoria `k` (0 = seqüències). */
    fun nom(k: Int, i: Int): String? {
        if (!teNoms) return null
        val ro = u32(symb + 8 + 4 * k); val o = u32(symb + ro + 4 + 4 * i)
        if (o == 0) return null
        var e = symb + o; while (d[e].toInt() != 0) e++
        return String(d, symb + o, e - (symb + o), Charsets.US_ASCII)
    }

    fun midaFitxer(fid: Int): Int = u32(fat + 12 + 16 * fid + 4)

    // ---- Streams (STRM): categoria 7 de l'INFO; registre = número d'arxiu, volum, prioritat, reproductor ----
    private fun cap(fid0: Int, n: Int): ByteArray? {
        val fid = fid0 and 0xFFFFFF
        if (fid < 0 || fid >= u32(fat + 8)) return null
        val fo = u32(fat + 12 + 16 * fid); val fs = u32(fat + 12 + 16 * fid + 4)
        if (fo < 0 || fs < 0 || fo + fs > d.size) return null
        return d.copyOfRange(fo, fo + minOf(n, fs))
    }
    private val cacheStrm = HashMap<Int, StrmInfo?>()
    /** Capçalera del stream `i` (només es llegeixen els primers bytes de l'arxiu), o null si no existeix o no és vàlid. */
    fun strmInfo(i: Int): StrmInfo? = cacheStrm.getOrPut(i) {
        try { val e = entrada(7, i); if (e < 0) null else cap(u32(e), 0x44)?.let { StrmInfo.llegeix(it, u8(e + 4)) } } catch (ex: Exception) { null }
    }
    fun strmFitxer(i: Int): ByteArray = fitxer(u32(reg(7, i, "stream")))
    /** Streams que són música (d'almenys 20 s; els curts són veus i efectes), per als jocs desconeguts. */
    val musicaStrm: List<Int> by lazy {
        try { (0 until comptar(7)).filter { (strmInfo(it)?.durada ?: 0.0) >= 20.0 } } catch (e: Exception) { emptyList() }
    }

    /** Per a jocs desconeguts: totes les seqüències d'almenys 256 bytes (els efectes de so són més curts). */
    val musicaGen: List<Int> by lazy {
        (0 until comptar(0)).filter { i -> val e = entrada(0, i); e >= 0 && midaFitxer(u32(e)) >= 256 }
    }

    /** Seqüències de música (no efectes ni crits): índex de seqüència. */
    val musica: List<Int> by lazy {
        (0 until comptar(0)).filter { i ->
            val n = nom(0, i)
            if (!teNoms) return@filter entrada(0, i) >= 0
            entrada(0, i) >= 0 && n != null && !n.startsWith("SEQ_SE") && !n.startsWith("SEQ_PV") && !n.contains("DUMMY") && !n.contains("SILENCE")
        }
    }

    private val pvArc: Map<Int, Int> by lazy {
        (0 until comptar(3)).mapNotNull { i ->
            val n = nom(3, i)
            if (n != null && n.length == 14 && n.startsWith("WAVE_ARC_PV") && n.substring(11).all { it.isDigit() }) n.substring(11).toInt() to i else null
        }.toMap()
    }
    /** Índex de l'arxiu d'ones del crit número `pv` (WAVE_ARC_PVnnn), o null. */
    fun arcCrit(pv: Int): Int? = pvArc[pv]

    fun seqFitxer(i: Int): ByteArray { val e = reg(0, i, "seqüència"); return fitxer(u32(e)) }
    fun seqBanc(i: Int): Int = u16(reg(0, i, "seqüència") + 4)
    fun seqVolum(i: Int): Int = u8(reg(0, i, "seqüència") + 6)
    fun seqPlayer(i: Int): Int = u8(reg(0, i, "seqüència") + 9)
    /** Màscara de canals de maquinari que el PLAYER de la seqüència pot fer servir (DP/Plat 0x7FF; HeartGold/SoulSilver 0xA7FE). */
    fun mascaraPlayer(i: Int): Int { val e = entrada(4, seqPlayer(i)); return if (e < 0) 0xFFFF else u16(e + 2) }
    fun bancFitxer(b: Int): ByteArray = fitxer(u32(reg(2, b, "banc")))
    /** Els 4 arxius d'ones del banc (0xFFFF = cap). */
    fun bancArxius(b: Int): IntArray { val e = reg(2, b, "banc"); return IntArray(4) { u16(e + 4 + 2 * it) } }

    /**
     * Altres SDAT del mateix joc (importats amb aquest, l'autor 2026-11-09): si aquest no porta un arxiu d'ones (el registre falta o l'arxiu és buit),
     * es busca en aquests amb el mateix nom (SYMB) o, si no hi ha noms, el mateix número. Si l'arxiu hi és, no es toca res.
     */
    var companys: List<Sdat> = emptyList()
    private fun buit(k: Int, i: Int): Boolean = try { val e = entrada(k, i); e < 0 || midaFitxer(u32(e)) == 0 } catch (ex: Exception) { true }
    private fun enCompany(k: Int, i: Int): Pair<Sdat, Int>? {
        val n = try { nom(k, i) } catch (ex: Exception) { null }
        for (c in companys) {
            val j = if (n != null) (0 until c.comptar(k)).firstOrNull { c.nom(k, it) == n } ?: continue else i
            if (!c.buit(k, j)) return c to j
        }
        return null
    }

    private val cacheOnes = HashMap<Int, List<Ona>>()
    fun ones(arc: Int): List<Ona>? {
        if (arc == 0xFFFF) return null
        cacheOnes[arc]?.let { return it }
        if (companys.isNotEmpty() && buit(3, arc)) enCompany(3, arc)?.let { (c, j) -> return c.ones(j)?.also { cacheOnes[arc] = it } }
        val e = entrada(3, arc); if (e < 0) return null
        return swar(fitxer(u32(e))).also { cacheOnes[arc] = it }
    }

    private fun swar(b: ByteArray): List<Ona> {
        fun u16b(o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
        fun u32b(o: Int) = u16b(o) or (u16b(o + 2) shl 16)
        val n = u32b(0x38).coerceIn(0, maxOf(0, (b.size - 0x3C) / 4))
        return List(n) { i ->
            val o = u32b(0x3C + 4 * i)
            if (o < 0 || o + 12 > b.size) return@List Ona(ShortArray(0), 8000, false, 0)
            val t = b[o].toInt() and 0xFF; val bucle = b[o + 1].toInt() != 0
            val taxa = u16b(o + 2); val tm = u16b(o + 4); val ls = u16b(o + 6); val ln = u32b(o + 8)
            val len = ((ls + ln) * 4).coerceIn(0, maxOf(0, b.size - (o + 12)))
            when (t) {
                0 -> Ona(ShortArray(len) { (b[o + 12 + it].toInt() * 256).toShort() }, taxa, bucle, ls * 4, tm, 0, ls + ln)
                1 -> Ona(ShortArray(len / 2) { ((b[o + 12 + 2 * it].toInt() and 0xFF) or (b[o + 13 + 2 * it].toInt() shl 8)).toShort() }, taxa, bucle, ls * 2, tm, 1, ls + ln)
                else -> adpcm(b, o + 12, len, taxa, bucle, (ls * 4 - 4) * 2, tm, ls + ln)
            }
        }
    }

    private fun adpcm(b: ByteArray, o: Int, len: Int, taxa: Int, bucle: Boolean, ini: Int, tm: Int, paraules: Int): Ona {
        var s = ((b[o].toInt() and 0xFF) or (b[o + 1].toInt() shl 8)).toShort().toInt()
        var idx = (((b[o + 2].toInt() and 0xFF) or ((b[o + 3].toInt() and 0xFF) shl 8)) and 0x7F).coerceAtMost(88)
        val out = ShortArray(maxOf(0, (len - 4) * 2)); var k = 0
        for (j in 4 until len) {
            val by = b[o + j].toInt() and 0xFF
            for (h in 0..1) {
                val nib = if (h == 0) by and 15 else by shr 4
                val st = STEP[idx]; var df = st shr 3
                if (nib and 1 != 0) df += st shr 2
                if (nib and 2 != 0) df += st shr 1
                if (nib and 4 != 0) df += st
                s = adpcmPas(s, nib, df)
                out[k++] = s.toShort()
                idx = (idx + IDX[nib and 7]).coerceIn(0, 88)
            }
        }
        return Ona(out, taxa, bucle, ini.coerceAtLeast(0), tm, 2, paraules)
    }

    companion object {
        /** Pas de l'IMA-ADPCM de la DS: sumant satura a +7FFFh i restant a -7FFFh (mai -8000h), GBATEK «IMA-ADPCM Format» i melonDS SPU.cpp NextSample_ADPCM. */
        internal fun adpcmPas(s: Int, nib: Int, df: Int): Int = if (nib and 8 != 0) maxOf(s - df, -0x7FFF) else minOf(s + df, 0x7FFF)
        internal val IDX = intArrayOf(-1, -1, -1, -1, 2, 4, 6, 8)
        internal val STEP = intArrayOf(7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209,
            230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026,
            4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767)
    }
}

/** Banc d'instruments (SBNK): tria la regió d'un programa i una tecla. Tipus de regió: 1 = ona (PCM), 2 = pols (PSG), 3 = soroll. */
class Banc(private val b: ByteArray, val arxius: IntArray) {
    private fun u8(o: Int) = b[o].toInt() and 0xFF
    private fun u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
    private val n = u16(0x38) or (u16(0x3A) shl 16)

    private fun llegeixRegio(tipus: Int, o: Int) = Regio(tipus, u16(o), u16(o + 2), u8(o + 4), u8(o + 5), u8(o + 6), u8(o + 7), u8(o + 8), u8(o + 9))
    /** El programa és un kit de bateria (tipus 17 de l'SBNK). */
    fun esKit(prog: Int): Boolean = prog in 0 until n && u8(0x3C + 4 * prog) == 17

    fun regio(prog: Int, key: Int): Regio? {
        if (prog < 0 || prog >= n) return null
        val t = u8(0x3C + 4 * prog); val off = u16(0x3C + 4 * prog + 1)
        return when (t) {
            1, 2, 3 -> llegeixRegio(t, off)
            16 -> { val lo = u8(off); val hi = u8(off + 1); if (key < lo || key > hi) null else { val r = key - lo; llegeixRegio(u16(off + 2 + 12 * r), off + 4 + 12 * r) } }
            17 -> {
                var r = -1
                for (i in 0 until 8) { val e = u8(off + i); if (e == 0) break; if (key <= e) { r = i; break } }
                if (r < 0) null else llegeixRegio(u16(off + 8 + 12 * r), off + 10 + 12 * r)
            }
            else -> null
        }
    }
}

/** Reproductor únic de la ràdio (un fil d'àudio). L'estat de les pistes silenciades es manté entre cançons. */
object RadioMotor {
    @Volatile var sdat: Sdat? = null
    @Volatile var sinte: Sintetitzador? = null
    /** Nivells per canal del que sona ARA (no del que s'està sintetitzant: l'àudio va endarrerit pel buffer). */
    @Volatile var nivVis: FloatArray? = null
    @Volatile var seqActual = -1
    /** Icona (ARGB 32x32) del joc que sona, per a la portada de la notificació; la posa la pantalla de la ràdio en començar. */
    @Volatile var icona: IntArray? = null
    /** Nom de l'àlbum que sona: la notificació i la pantalla de bloqueig hi busquen la portada baixada de libretro. */
    /** Nom del fitxer de la portada de libretro de la cançó que sona (`Portades.nomArt`). */
    @Volatile var portadaNom: String? = null
    @Volatile var sonant = false
    @Volatile var error: String? = null
    /** Pistes silenciades de la cançó que sona; la pantalla del mesclador la canvia un moment, en crear la cançó d'un deck, perquè cada deck tingui les seves. */
    @Volatile var muts = BooleanArray(16)
    val llocMuts = Any()
    @Volatile var solo = -1
    private var generacio = 0
    /** Mida de l'anell de nivells per canal (el buffer de l'AudioTrack és d'uns 8 blocs: sobra). */
    private const val CUA = 64
    /** Cadena d'efectes lliure de l'última cançó (només OSTixxx Music): el fil d'àudio nou la pren (si la freqüència és la mateixa) i la torna en acabar; mai no la fan servir dos fils alhora. */
    private val cadenaLliure = java.util.concurrent.atomic.AtomicReference<CadenaEfectes?>(null)

    // ---- Controls multimèdia (notificació, pantalla de bloqueig, auriculars): pausa, següent i anterior ----
    /** Llista que es reprodueix (la de la categoria on s'ha donat al play): permet saltar de cançó encara que la pantalla de la ràdio no sigui oberta. */
    class Llista(val ids: List<Int>, val crea: (Int) -> Sintetitzador, val etiqueta: (Int) -> String)
    @Volatile var llista: Llista? = null
    @Volatile var pos = -1
    @Volatile var pausat = false
    @Volatile var titol = ""
    /** S'incrementa quan els controls multimèdia canvien de cançó, perquè la pantalla de la ràdio es posi al dia. */
    @Volatile var versioExt = 0
    /** El servei hi posa la manera de refer la notificació quan canvia l'estat (pausa/represa). */
    @Volatile var alCanviar: (() -> Unit)? = null
    /** Volum de sortida (0..1) de l'AudioTrack: el servei l'abaixa quan una altra app demana el focus d'àudio amb «duck» (un avís, el GPS) i el torna a 1 en recuperar-lo. No toca les mostres. */
    @Volatile var volum = 1f

    fun pausa() { if (sonant && !pausat) { pausat = true; alCanviar?.invoke() } }
    fun represa() {
        if (!(sonant && pausat)) return
        pausat = false; desperta()
        val c = alCanviar
        if (c != null) c()
        else ctxApp?.let { ctx ->          // Android havia aturat el servei en pausa (ja no era en primer pla): es torna a arrencar perquè soni amb la pantalla apagada
            try { androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, RadioServei::class.java).putExtra("titol", titol).putExtra("clau", RadioServei.clau)) } catch (e: Exception) { error = e.javaClass.simpleName }
        }
    }
    /** El fil d'àudio en pausa dorm en aquest objecte (abans es despertava cada 40 ms per mirar si calia tornar): el desperten la represa, el canvi de cançó i un canvi de driver preparat. */
    private val despertador = Object()
    private fun desperta() { synchronized(despertador) { despertador.notifyAll() } }
    fun salta(d: Int) {
        val l = llista ?: return; val ctx = ctxApp ?: return
        if (!sonant || l.ids.isEmpty()) return
        pos = (pos + d + l.ids.size) % l.ids.size
        val id = l.ids[pos]
        seqActual = id; versioExt++            // primer la cançó i després l'avís: si no, la pantalla (fil principal) podia llegir la cançó vella en canvis automàtics (fil d'àudio) i quedar-s'hi
        inicia(id, ctx, l.etiqueta(id)) { l.crea(id) }
    }

    fun carrega(ctx: Context): Sdat? {
        sdat?.let { return it }
        val f = File(File(ctx.filesDir, "musica"), "sound_data.sdat")
        if (!f.exists()) return null
        return try { Sdat(f.readBytes()).also { sdat = it } } catch (e: Throwable) { error = e.javaClass.simpleName; null }
    }

    @Volatile private var ctxApp: Context? = null

    /** Inicia (o canvia) la cançó: arrenca el servei en primer pla perquè soni amb la pantalla bloquejada. `titol` surt a la notificació. */
    /** Salt de la barra de posició (l'autor, 2026-11-13): fracció 0..1 del final/bucle de la cançó (`batecMax`); -1 = cap. El fil d'àudio el pren: si cal anar enrere, torna a crear la cançó, i després avança sense so fins al punt. */
    @Volatile var cercaF = -1.0
    /** Salt per temps (segons des de l'inici) i temps que porta la cançó: serveixen quan no hi ha anàlisi de batecs (la barra de posició sempre funciona). */
    @Volatile var cercaS = -1.0
    @Volatile var segonsActuals = 0.0
    // ---- Mode comparació d'OSTixxx Music (2026-11-17): canviar el sintetitzador (un altre driver del mateix joc) sense reiniciar la cançó, i escoltar sense efectes ----
    /** Sintetitzador nou ja avançat en silenci fins a `mostres` (múltiple de 512), esperant que el fil d'àudio el prengui. */
    private class Pendent(val si: Sintetitzador, var mostres: Long, val crea: () -> Sintetitzador, val gen: Int, val salt: Int, val clau: String?)
    /** S'incrementa a cada salt de la barra de posició: un canvi preparat abans d'un salt ja no és a la posició bona i es descarta. */
    @Volatile private var salts = 0
    @Volatile private var pendent: Pendent? = null
    /** Mostres que porta la cançó que sona (les escriu el fil d'àudio; el fil que prepara el canvi les persegueix). */
    @Volatile private var mostresAra = 0L
    /** Clau (de qui ha demanat el canvi) del sintetitzador que sona ara si és un substitut; null = el de la cançó. Torna a null en canviar de cançó. */
    @Volatile var substitut: String? = null
    /** Hi ha un canvi en preparació (s'està avançant en silenci el sintetitzador nou fins a la posició d'ara). */
    @Volatile var preparant: String? = null
    /** Escolta sense la cadena d'efectes (A/B). La cadena continua processant (la cua de la reverb no es perd) i el pas és un fos de 512 mostres. Torna a false en canviar de cançó. */
    @Volatile var senseEfectes = false

    /**
     * Canvia el sintetitzador de la cançó que sona per un de nou (`crea`) **a la mateixa posició**: en un fil a part, el nou s'avança sense so fins on és la cançó
     * (la cançó continua sonant mentrestant); després, el fil d'àudio el pren al límit d'un bloc i fa un fos encreuat de 512 mostres (sense clic, sense reiniciar).
     * `clau` identifica el canvi (`substitut`); null = torna al de la cançó. Només dura fins que canvia la cançó. Si la cançó canvia abans, el canvi es descarta.
     */
    fun substitueix(clau: String?, crea: () -> Sintetitzador) {
        if (!sonant) return
        val gen = generacio; val salt = salts
        preparant = clau ?: ""
        Thread {
            FilsVius.compara.incrementAndGet()
            try {
                val si = synchronized(llocMuts) { crea() }; si.solo = solo
                if (si.taux != sinte?.taux) { if (generacio == gen) preparant = null; return@Thread }          // una altra freqüència no es pot encadenar al mateix AudioTrack
                val tmp = ShortArray(1024); var m = 0L
                val maxM = 30L * 60 * si.taux; val t0 = System.nanoTime()
                // arriba una mica més enllà d'on és la cançó (4 blocs; en pausa, just on és): el fil d'àudio l'agafa quan hi arriba, sense haver d'avançar res ell
                while (generacio == gen && m < mostresAra + (if (pausat) 0 else 2048) && m < maxM) {
                    si.renderitza(tmp, 512); m += 512
                    if (System.nanoTime() - t0 > 25_000_000_000L) throw IllegalStateException("massa lent per atrapar la cançó")
                }
                if (generacio == gen && salts == salt) { pendent = Pendent(si, m, crea, gen, salt, clau); desperta() }
                else if (generacio == gen) preparant = null
            } catch (e: Throwable) {
                ctxApp?.let { Informe.liniaProves(it, "[compara] " + e.javaClass.simpleName + " " + (e.message ?: "")) }
                if (generacio == gen) preparant = null
            } finally { FilsVius.compara.decrementAndGet() }
        }.also { it.isDaemon = true; it.name = "compara" }.start()
    }

    /** Fa avançar `s` sense so fins que arriba al batec `objectiu` (límit: 20 minuts de cançó). Torna les mostres que ha avançat. */
    private fun avancaFins(s: Sintetitzador, objectiu: Double, gen: Int): Long {
        val tmp = ShortArray(1024); var n = 0; val max = 20 * 60 * s.taux / 512
        while (generacio == gen && s.batec in 0.0..objectiu && n < max) { s.renderitza(tmp, 512); n++ }
        return n * 512L
    }
    fun inicia(seq: Int, ctx: Context, titol: String, crea: () -> Sintetitzador) {
        try { App.bib?.let { b -> b.emissores.firstOrNull { it.nom == Reprod.joc }?.let { e -> val k = b.clauDe(e, seq); Comptes.suma(k)
            // la portada (notificació, pantalla de bloqueig) és la del joc d'on surt la cançó, també quan sona des d'una llista
            if (e.esLlista) b.perNom(RadioDades.emissoraDe(k))?.let { g -> portadaNom = Portades.nomArt(g); icona = Portades.pixels(ctx, g) }
            // efectes: reverb i chorus de la consola d'on surt la cançó (també des d'una llista)
            if (EfectesRadio.habilitat) EfectesRadio.perEmissora(if (e.esLlista) (b.perNom(RadioDades.emissoraDe(k))?.nom ?: RadioDades.emissoraDe(k)) else e.nom) } } } catch (x: Throwable) { }          // reproduccions per a «més reproduïdes»
        Mescla.atura()
        aturaFil()
        ctxApp = ctx.applicationContext
        pausat = false; this.titol = titol
        try { androidx.core.content.ContextCompat.startForegroundService(ctx.applicationContext, Intent(ctx.applicationContext, RadioServei::class.java).putExtra("titol", titol).putExtra("clau", RadioServei.clau)) } catch (e: Exception) { error = e.javaClass.simpleName }
        seqActual = seq; sonant = true; error = null
        pendent = null; preparant = null; substitut = null; senseEfectes = false; mostresAra = 0L
        val gen = ++generacio
        Thread {
            var at: AudioTrack? = null
            var cad: CadenaEfectes? = null
            val vius = FilsVius.entraAudio()
            // 1 = aquest; 2 = el de la cançó d'abans encara tancant-se (normal un moment); més vol dir que se'n queden de penjats
            if (vius > 2) Informe.liniaProves(ctx.applicationContext, "[fils] «$titol»: $vius fils d'àudio vius alhora")
            try {
                val t0 = System.nanoTime()
                var creaAct = crea          // canvia si el mode comparació posa un altre driver (els salts el tornen a crear amb aquest)
                var si = synchronized(llocMuts) { creaAct() }; si.solo = solo
                val dtCrea = (System.nanoTime() - t0) / 1000000
                if (dtCrea > 2000) Informe.liniaProves(ctx.applicationContext, "[lent] «$titol»: la creació del so ha trigat $dtCrea ms")
                var lentsRender = 0; var maxRender = 0L
                sinte = si
                val min = AudioTrack.getMinBufferSize(si.taux, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                at = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(si.taux).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(maxOf(min, 16384)).setTransferMode(AudioTrack.MODE_STREAM).build()
                at.play()
                val buf = ShortArray(1024)
                // cua de nivells per canal (per sincronitzar el visualitzador amb el que sona): anell fix de CUA arrays reutilitzats (abans un Pair i un copyOf per bloc)
                val cuaT = LongArray(CUA); val cuaV = arrayOfNulls<FloatArray>(CUA); var cIni = 0; var cN = 0; nivVis = null
                // efectes d'OSTixxx Music: la cadena es reaprofita si la freqüència no canvia (cap retard en començar), i es buida en cada salt
                cad = if (EfectesRadio.habilitat) (cadenaLliure.getAndSet(null)?.takeIf { it.fs == si.taux } ?: CadenaEfectes(si.taux)).also { it.reset() } else null
                var enPausa = false; var mostres = 0L; var volAplicat = 1f
                // mode comparació: búfers del fos encreuat (sintetitzador nou) i del bypass d'efectes (so sec retardat com el limitador)
                val bufNou = ShortArray(1024); val sec = ShortArray(1024); var gFx = 1f
                val retSec = RetardSec()
                val fadeTotal = 4L * si.taux; var fade = 0L; var enFade = false          // fade out de 4 s abans de la cançó següent
                while (generacio == gen && sonant) {
                    // canvi de driver en pausa: es pren sense fos (no sona res)
                    val pvist = pendent
                    if (pausat) { pvist?.takeIf { it.gen == gen && it.salt == salts && it.mostres <= mostres }?.let { pd ->
                        pendent = null; val tmp = ShortArray(1024)
                        while (pd.mostres < mostres) { pd.si.renderitza(tmp, 512); pd.mostres += 512 }
                        si = pd.si; creaAct = pd.crea; sinte = si; substitut = pd.clau; preparant = null; cN = 0 } }
                    if (pausat) {
                        if (!enPausa) { at.pause(); enPausa = true }
                        // dorm fins que el despertin (represa, canvi de cançó, driver preparat); la condició es torna a mirar dins del bloqueig perquè cap avís es perdi.
                        // L'espera màxima d'1 s només és una xarxa de seguretat (abans: un despertar cada 40 ms tota l'estona de la pausa)
                        synchronized(despertador) { if (pausat && generacio == gen && sonant && pendent === pvist) (despertador as Object).wait(1000) }
                        continue
                    }
                    if (enPausa) { at.play(); enPausa = false }
                    val cs = cercaS
                    if (cs >= 0.0) {
                        cercaS = -1.0; salts++
                        try {
                            val obj = (cs * si.taux).toLong()
                            at.pause(); at.flush()
                            if (mostres > obj) { si = synchronized(llocMuts) { creaAct() }; sinte = si; mostres = 0L }
                            si.solo = solo
                            val tmp = ShortArray(1024)
                            while (generacio == gen && mostres < obj) { si.renderitza(tmp, 512); mostres += 512 }
                            cN = 0; cad?.reset(); enFade = false; fade = 0L; mostresAra = mostres; at.play()
                        } catch (e: Throwable) { Informe.liniaProves(ctx.applicationContext, "[salt] «$titol»: " + e.javaClass.simpleName + " " + (e.message ?: "")) }
                    }
                    val cf = cercaF
                    if (cf >= 0.0) {
                        cercaF = -1.0; salts++
                        try {
                            val mx = si.batecMax
                            if (mx > 0.0 && si.batec >= 0.0) {
                                val obj = cf.coerceIn(0.0, 1.0) * mx
                                at.pause(); at.flush()
                                if (si.batec > obj) { si = synchronized(llocMuts) { creaAct() }; sinte = si; mostres = 0L }
                                si.solo = solo
                                mostres += avancaFins(si, obj, gen)          // (2026-11-17) abans no es comptaven: el temps i el mode comparació necessiten la posició en mostres
                                cN = 0; cad?.reset(); mostresAra = mostres; at.play()
                            }
                        } catch (e: Throwable) { Informe.liniaProves(ctx.applicationContext, "[salt] «$titol»: " + e.javaClass.simpleName + " " + (e.message ?: "")) }
                    }
                    si.solo = solo
                    val tr = System.nanoTime(); si.renderitza(buf, 512); val dtr = (System.nanoTime() - tr) / 1000000
                    // mode comparació: un sintetitzador nou a punt (ja avançat fins aquí): fos encreuat d'un bloc i es queda el nou
                    val pd = pendent
                    if (pd != null) {
                        if (pd.gen != gen || pd.salt != salts) { pendent = null; if (pd.gen == gen) preparant = null }
                        else if (pd.mostres < mostres - si.taux / 4) { pendent = null; preparant = null }          // s'ha quedat enrere (no hauria de passar): es descarta
                        else if (pd.mostres <= mostres) {
                            pendent = null
                            try {
                                val tmp = ShortArray(1024)
                                while (pd.mostres < mostres) { pd.si.renderitza(tmp, 512); pd.mostres += 512 }          // el que hagi passat des que s'ha preparat
                                pd.si.solo = solo; pd.si.renderitza(bufNou, 512)
                                for (k in 0 until 1024) { val g = (k shr 1) / 511f; buf[k] = (buf[k] * (1f - g) + bufNou[k] * g).toInt().coerceIn(-32768, 32767).toShort() }
                                si = pd.si; creaAct = pd.crea; sinte = si; substitut = pd.clau; cN = 0
                            } catch (e: Throwable) { Informe.liniaProves(ctx.applicationContext, "[compara] " + e.javaClass.simpleName + " " + (e.message ?: "")) }
                            preparant = null
                        }
                    }
                    if (dtr > maxRender) maxRender = dtr
                    // 512 mostres són uns 16 ms d'àudio: si en triga més de 100 es talla; es registra un cop (després de 40 blocs lents)
                    if (dtr > 100 && ++lentsRender == 40) Informe.liniaProves(ctx.applicationContext, "[lent] «$titol»: 40 blocs de so de 512 mostres han trigat més de 100 ms (el pitjor, $maxRender ms)")
                    if (EnPantalla.v) si.nivells?.let { nv ->
                        if (cN == CUA) { cIni = (cIni + 1) % CUA; cN-- }
                        val q = (cIni + cN) % CUA
                        val dst = cuaV[q]?.takeIf { it.size == nv.size } ?: FloatArray(nv.size).also { cuaV[q] = it }
                        System.arraycopy(nv, 0, dst, 0, nv.size); cuaT[q] = mostres + 512; cN++
                    } else cN = 0
                    // la posició de l'AudioTrack (una crida al sistema) només cal si hi ha nivells a la cua per descartar
                    if (cN > 1) { val cap = at.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                        while (cN > 1 && cuaT[(cIni + 1) % CUA] <= cap) { cIni = (cIni + 1) % CUA; cN-- } }
                    nivVis = if (cN > 0) cuaV[cIni] else null
                    // efectes (EQ compensat → chorus → reverb → amplada/mono/balanç → limitador); si tot és neutre no toca el buffer
                    if (cad != null) {
                        EfectesRadio.configura(cad)
                        val obj = if (senseEfectes) 0f else 1f
                        if (gFx == 1f && obj == 1f) { cad.process(buf, 512); retSec.buida() }
                        else {
                            // A/B sense efectes: la cadena continua (la cua no es perd); el sec es retarda com el limitador perquè el fos no faci pinta
                            retSec.copia(buf, sec, 512, if (cad.actiu && cad.limita) cad.limitador.latencia else 0)
                            cad.process(buf, 512)
                            val g0 = gFx
                            for (k in 0 until 1024) { val g = g0 + (obj - g0) * ((k shr 1) + 1) / 512f; buf[k] = (buf[k] * g + sec[k] * (1f - g)).toInt().coerceIn(-32768, 32767).toShort() }
                            gFx = obj
                        }
                    }
                    Espectre.actualitza(buf, 512, si.taux)
                    if (Oscil.visible && EnPantalla.v) OscMestre.alimenta(buf, 512)
                    if (enFade) {
                        for (k in 0 until 512) { val g = (fade - k).coerceAtLeast(0L).toFloat() / fadeTotal; buf[2 * k] = (buf[2 * k] * g).toInt().toShort(); buf[2 * k + 1] = (buf[2 * k + 1] * g).toInt().toShort() }
                        fade -= 512
                    }
                    val vol = volum
                    if (vol != volAplicat) { volAplicat = vol; try { at.setVolume(vol) } catch (e: Throwable) { } }
                    at.write(buf, 0, buf.size)
                    mostres += 512; mostresAra = mostres
                    segonsActuals = mostres.toDouble() / si.taux
                    if (llista != null) {
                        // Els bucles són il·limitats: passa a la cançó següent (amb fade out) en acabar les voltes triades o el temps màxim, el que arribi primer;
                        // si la cançó no té bucle i acaba, passa a la següent directament (no abans de 2 s, perquè una cançó trencada no faci córrer la llista)
                        if (!enFade) {
                            val lim = RadioDades.tempsMax; val nb = RadioDades.bucles
                            if ((lim > 0 && mostres >= lim.toLong() * si.taux) || (nb > 0 && si.voltes >= nb)) { enFade = true; fade = fadeTotal }
                        }
                        if ((enFade && fade <= 0L) || (si.acabada && mostres > 2L * si.taux)) { salta(1); break }
                    }
                }
            } catch (e: Throwable) {
                // Primer el lloc (fitxer:línia) perquè es vegi a la línia curta de la pantalla
                val ab = mapOf("Radio" to "Ra", "RadioNitro" to "Ni", "RadioGba" to "Ga", "RadioM4a" to "M4", "RadioAltres" to "Al", "RadioServei" to "Sv")
                val lloc = e.stackTrace.filter { it.className.startsWith("cat.gbdsmixxx") }.take(4).joinToString(" ") { (ab[it.fileName?.removeSuffix(".kt")] ?: "?") + it.lineNumber } + " "
                error = lloc + e.javaClass.simpleName.removeSuffix("Exception") + ": " + (e.message ?: "")
            }
            finally {
                FilsVius.audio.decrementAndGet()
                try { at?.stop(); at?.release() } catch (e: Throwable) {}
                cad?.let { cadenaLliure.set(it) }
                if (generacio == gen) { sonant = false; sinte = null; nivVis = null; ctxApp?.let { try { it.stopService(Intent(it, RadioServei::class.java)) } catch (e: Exception) {}; RadioServei.acaba(it) } }
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun aturaFil() { sonant = false; generacio++; desperta() }

    /** Atura la música i el servei en primer pla. */
    fun atura() { aturaFil(); ctxApp?.let { try { it.stopService(Intent(it, RadioServei::class.java)) } catch (e: Exception) {}; RadioServei.acaba(it) } }
}

/**
 * Què fa un canal (pista) d'una cançó, mesurat fent córrer la cançó en silenci fins al primer bucle: nombre de notes, nota mitjana (MIDI: 60 = do central), durada mitjana de les notes
 * en batecs i quantes són de bateria/soroll, de quadrada, d'ona o de mostra. Els instruments no tenen nom a les ROM: aquesta etiqueta és una **estimació**.
 */
class PerfilPista(val notes: Int, val keyMitja: Double, val gateBatecs: Double, val ritmiques: Int, val soroll: Int, val quadrades: Int, val ones: Int) {
    /** 0 = bateria, 1 = baix, 2 = acords (notes llargues), 3 = melodia, 4 = agut. */
    fun categoria(): Int {
        val perc = (ritmiques + soroll).toDouble() / maxOf(1, notes)
        return if (perc >= 0.5) 0 else if (keyMitja < 48.0) 1 else if (gateBatecs >= 1.5) 2 else if (keyMitja >= 72.0) 4 else 3
    }
    /** Etiqueta curta (3 lletres) i nom complet. */
    fun curta(): String = listOf("BAT", "BAI", "ACO", "MEL", "AGU")[categoria()]
    fun nom(): String = listOf("Bateria", "Baix", "Acords", "Melodia", "Agut")[categoria()] + (if (quadrades * 2 > notes) " (quadrada)" else if (ones * 2 > notes) " (ona)" else if (soroll * 2 > notes) " (soroll)" else "")
}

/** Acumulador de les notes d'una pista durant l'anàlisi. */
class AcumPerfil {
    var n = 0; var sumKey = 0.0; var sumGate = 0.0; var rit = 0; var sor = 0; var quad = 0; var ona = 0
    fun nota(key: Int, gateBatecs: Double, ritmica: Boolean, soroll: Boolean, quadrada: Boolean, ona: Boolean) {
        n++; sumKey += key; sumGate += gateBatecs; if (ritmica) rit++; if (soroll) sor++; if (quadrada) quad++; if (ona) this.ona++
    }
    fun perfil(): PerfilPista? = if (n == 0) null else PerfilPista(n, sumKey / n, sumGate / n, rit, sor, quad, ona)
}

/** Funsies: d'on surten els instruments d'un canal: joc i cançó (el seu banc de sons) i número d'instrument fix (-1 = el que demani la cançó). */
class Veu(val joc: Emissora, val canco: Int, val prog: Int, val oct: Int = 0)

/** Una emissora de la ràdio: un joc, amb les seves cançons i la manera de sintetitzar-les. */
class Emissora(val nom: String, val ids: List<Int>, val etiqueta: (Int) -> String, val codi: (Int) -> String, val tipus: (Int) -> Int, val crea: (Int) -> Sintetitzador) {
    /** Instruments d'aquest joc per deixar-los a un altre: la ROM (GBA) o, per a la cançó `id`, l'arxiu de so i la seqüència (DS). Null si no en pot deixar. */
    var romVeus: (() -> RomGba)? = null
    var sdatVeus: ((Int) -> Pair<Sdat, Int>?)? = null
    /** Crea la cançó `id` amb instruments triats per a cada canal (`veus[k]` = d'on surten els del canal k; null = els seus); null si aquest joc no ho permet. */
    var creaMix: ((Int, Array<Veu?>) -> Sintetitzador)? = null
    /** Aquest joc pot tocar amb els instruments del joc `altra` (cal que siguin del mateix tipus: GBA amb GBA, DS amb DS). */
    fun potAgafarDe(altra: Emissora): Boolean = creaMix != null && ((romVeus != null && altra.romVeus != null) || (sdatVeus != null && altra.sdatVeus != null))
    /** Nom que es veu (la regió); `nom` és la clau interna (preferències). */
    var vista: String = nom
    /** Joc de «Altres ràdios» (detectat automàticament, sense categories ni noms): una sola llista. */
    var altra = false
    /** Icona (portada): la del joc de DS amb aquest codi i, si no n'hi ha, la d'un joc de DS amb la mateixa paraula al títol (`iconaNom`). */
    var iconaCodi: String? = null; var iconaNom: String? = null
    /** Llista de reproducció de l'usuari (RadioDades): `ids` són posicions a la llista i `llistaIdx` el seu número. */
    var esLlista = false; var llistaIdx = -1
    /** CRC32 del fitxer de la ROM (el mateix que fa servir libretro-database); null si no es coneix. */
    var crc: Long? = null
    /** Mida en bytes del fitxer de la ROM (amb `crc`, la clau del catàleg definitiu); null si no es coneix. */
    var mida: Long? = null
    /** Clau estable de les dades de l'usuari d'un àlbum de consola («CRC:tipus:clau:rev», la de `Biblioteca.clausEstables`); null = la resta (fan servir `nom`). */
    var estable: String? = null
    /** Identitat de l'àlbum per a TOTES les dades de l'usuari (P1, l'autor 2026-11-22): llistes, noms, ocultes, BPM/bucle, combinacions, portades, decks, comptes i marques. */
    val clauDades: String get() = estable ?: nom
    /** Llista automàtica (p. ex. les 50 més reproduïdes): claus `àlbum|id` de les seves cançons; no és una llista de l'usuari (`llistaIdx` = -1). */
    var autoClaus: List<String>? = null
    /** Secció de l'Inici on surt una llista automàtica («Sagues», «Empreses», «Gèneres»); null si no n'és. */
    var autoGrup: String? = null
    /** Empresa de la ROM segons el codi de la seva capçalera (només GB/GBC de moment); null si no es coneix. */
    var editor: String? = null
    /** Codi de joc de 4 lletres de la capçalera (GBA i DS), que libretro té com a «serial»; null si no es coneix. */
    var serial: String? = null
}

/**
 * App «Ràdio»: emissores (Diamant, i els jocs de GBA importats), llista de cançons, botons de transport, «ALL» i fins a 12 pistes que
 * s'activen i es silencien tocant-les. Tocar el nom de l'emissora canvia d'emissora.
 */
