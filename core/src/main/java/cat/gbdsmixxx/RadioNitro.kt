package cat.gbdsmixxx

/**
 * Taules i fórmules del motor de so de la DS (NitroSystem), tretes de la reimplementació del driver d'HaroohiePals
 * (`Snd/Player/Util.cs`, `ExChannel.cs`, `Lfo.cs`, `Track.cs`, `DSSoundContext.cs`). Les unitats de volum són dècimes de dB (-723 = silenci).
 */
object NitroT {
    const val SILENCI = -723
    val DB = shortArrayOf(
        -32768, -722, -721, -651, -601, -562, -530, -503,
        -480, -460, -442, -425, -410, -396, -383, -371,
        -360, -349, -339, -330, -321, -313, -305, -297,
        -289, -282, -276, -269, -263, -257, -251, -245,
        -239, -234, -229, -224, -219, -214, -210, -205,
        -201, -196, -192, -188, -184, -180, -176, -173,
        -169, -165, -162, -158, -155, -152, -149, -145,
        -142, -139, -136, -133, -130, -127, -125, -122,
        -119, -116, -114, -111, -109, -106, -103, -101,
        -99, -96, -94, -91, -89, -87, -85, -82,
        -80, -78, -76, -74, -72, -70, -68, -66,
        -64, -62, -60, -58, -56, -54, -52, -50,
        -49, -47, -45, -43, -42, -40, -38, -36,
        -35, -33, -31, -30, -28, -27, -25, -23,
        -22, -20, -19, -17, -16, -14, -13, -11,
        -10, -8, -7, -6, -4, -3, -1, 0)
    val VOL = byteArrayOf(
        0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2,
        2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
        3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
        4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5,
        5, 5, 5, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
        6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 8, 8, 8, 8,
        8, 8, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 10, 10, 10,
        10, 10, 10, 10, 10, 11, 11, 11, 11, 11, 11, 11, 11, 12, 12, 12, 12, 12, 12, 12,
        12, 13, 13, 13, 13, 13, 13, 14, 14, 14, 14, 14, 14, 14, 15, 15, 15, 15, 15, 16,
        16, 16, 16, 16, 16, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 19, 19, 19, 19, 20,
        20, 20, 20, 20, 21, 21, 21, 21, 22, 22, 22, 22, 23, 23, 23, 24, 24, 24, 24, 25,
        25, 25, 25, 26, 26, 26, 27, 27, 27, 28, 28, 28, 29, 29, 29, 30, 30, 30, 31, 31,
        31, 32, 32, 32, 33, 33, 34, 34, 34, 35, 35, 36, 36, 36, 37, 37, 38, 38, 39, 39,
        39, 40, 40, 41, 41, 42, 42, 43, 43, 44, 44, 45, 45, 46, 46, 47, 47, 48, 49, 49,
        50, 50, 51, 51, 52, 53, 53, 54, 54, 55, 56, 56, 57, 58, 58, 59, 60, 60, 61, 62,
        63, 63, 64, 65, 66, 66, 67, 68, 69, 69, 70, 71, 72, 73, 74, 74, 75, 76, 77, 78,
        79, 80, 81, 82, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 93, 94, 95, 96, 97, 98,
        99, 100, 101, 103, 104, 105, 106, 107, 109, 110, 111, 113, 114, 115, 117, 118, 119, 121, 122, 123,
        125, 126, 127, 32, 33, 33, 33, 34, 34, 35, 35, 35, 36, 36, 37, 37, 38, 38, 38, 39,
        39, 40, 40, 41, 41, 42, 42, 43, 43, 44, 44, 45, 45, 46, 46, 47, 47, 48, 48, 49,
        49, 50, 51, 51, 52, 52, 53, 54, 54, 55, 55, 56, 57, 57, 58, 59, 59, 60, 61, 62,
        62, 63, 64, 64, 65, 66, 67, 67, 68, 69, 70, 71, 71, 72, 73, 74, 75, 76, 77, 77,
        78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 98,
        99, 100, 101, 102, 103, 105, 106, 107, 108, 109, 111, 112, 113, 115, 116, 117, 119, 120, 121, 123,
        124, 126, 126, 64, 65, 66, 67, 67, 68, 69, 70, 71, 71, 72, 73, 74, 75, 76, 76, 77,
        78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97,
        98, 100, 101, 102, 103, 104, 105, 107, 108, 109, 110, 112, 113, 114, 116, 117, 118, 120, 121, 123,
        124, 125, 126, 64, 65, 66, 66, 67, 68, 69, 70, 70, 71, 72, 73, 74, 75, 75, 76, 77,
        78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97,
        98, 99, 101, 102, 103, 104, 105, 106, 108, 109, 110, 111, 113, 114, 115, 117, 118, 119, 121, 122,
        124, 125, 126, 127)
    val ATAC = intArrayOf(0x00, 0x01, 0x05, 0x0E, 0x1A, 0x26, 0x33, 0x3F, 0x49, 0x54, 0x5C, 0x64, 0x6D, 0x74, 0x7B, 0x7F, 0x84, 0x89, 0x8F)
    val SIN = intArrayOf(0, 6, 12, 19, 25, 31, 37, 43, 49, 54, 60, 65, 71, 76, 81, 85, 90, 94, 98, 102, 106, 109, 112, 115, 117, 120, 122, 123, 125, 126, 126, 127, 127)
    val ORDRE = intArrayOf(4, 5, 6, 7, 2, 0, 3, 1, 8, 9, 0xA, 0xB, 0xE, 0xC, 0xF, 0xD)
    val DESPL = intArrayOf(0, 1, 2, 4)

    fun db(x: Int): Int = DB[x.coerceIn(0, 127)].toInt()
    fun sinIdx(i: Int): Int = when {
        i < 32 -> SIN[i]
        i < 64 -> SIN[32 - (i - 32)]
        i < 96 -> -SIN[i - 64]
        else -> -SIN[32 - (i - 96)]
    }
    fun atac(a: Int): Int = if (a >= 0x6D) ATAC[0x7F - a] else 0xFF - a
    fun caiguda(a: Int): Int = when { a == 0x7F -> 0xFFFF; a == 0x7E -> 0x3C00; a >= 0x32 -> 0x1E00 / (0x7E - a); else -> 2 * a + 1 }
    /** CalcChannelVolume: (volum 0..127, desplaçament) per a una atenuació en dècimes de dB. */
    fun volum(dbIn: Int): Int {
        val d = dbIn.coerceIn(-723, 0)
        val v = VOL[d + 723].toInt() and 0xFF
        val sh = if (d < -240) 3 else if (d < -120) 2 else if (d < -60) 1 else 0
        return (sh shl 8) or v
    }
    /** SNDi_DecibelTable de l'ARM9 (pret/pokediamond arm9/lib/NitroSDK/src/SND_util.c): el volum dels streams (NNS_SND_sndarc_stream.s). */
    val DB_LIN = shortArrayOf(
        -32768, -421, -361, -325, -300, -281, -265, -252, -240, -230, -221, -212, -205, -198, -192, -186,
        -180, -175, -170, -165, -161, -156, -152, -148, -145, -141, -138, -134, -131, -128, -125, -122,
        -120, -117, -114, -112, -110, -107, -105, -103, -100, -98, -96, -94, -92, -90, -88, -86,
        -85, -83, -81, -79, -78, -76, -74, -73, -71, -70, -68, -67, -65, -64, -62, -61,
        -60, -58, -57, -56, -54, -53, -52, -51, -49, -48, -47, -46, -45, -43, -42, -41,
        -40, -39, -38, -37, -36, -35, -34, -33, -32, -31, -30, -29, -28, -27, -26, -25,
        -24, -23, -23, -22, -21, -20, -19, -18, -17, -17, -16, -15, -14, -13, -12, -12,
        -11, -10, -9, -9, -8, -7, -6, -6, -5, -4, -3, -3, -2, -1, -1, 0)
    /**
     * Taula de to de la BIOS de l'ARM7 (SWI 1Bh GetPitchTable, 768 entrades). round((2^(i/768) - 1) · 65536) dona exactament les 768 entrades de
     * `getpitchtbl` de DeSmuME (desmume/src/bios.cpp), comprovat una per una; el marge més petit fins a l'arrodoniment és 0,0003.
     */
    private val TO = IntArray(768) { Math.round((StrictMath.pow(2.0, it / 768.0) - 1.0) * 65536.0).toInt() }
    /** SND_CalcTimer (pret/pokediamond arm7/lib/src/SND_util.c): temporitzador del canal (cicles de 16,76 MHz per mostra) per a un to en 1/64 de semitò. */
    fun temporitzador(timer: Int, pitch: Int): Int {
        var oct = 0; var p = -pitch
        while (p < 0) { oct--; p += 768 }
        while (p >= 768) { oct++; p -= 768 }
        var r = (TO[p].toLong() + 0x10000L) * timer
        val sh = oct - 16
        if (sh <= 0) r = r ushr -sh
        else if (sh < 32) { if ((r and (-1L shl (32 - sh))) != 0L) return 0xFFFF; r = r shl sh }
        else return 0xFFFF
        return r.coerceIn(0x10L, 0xFFFFL).toInt()
    }
}

/**
 * Un canal de so del maquinari de la DS (SOUNDxCNT, TMR, PNT, LEN), mostra a mostra com `SPUChannel` de melonDS (src/SPU.cpp) i GBATEK «DS Sound»:
 * sense interpolació (cada mostra es manté fins que el temporitzador desborda), arrencada retardada (PCM 3 mostres, ADPCM 11, PSG i soroll 1),
 * PSG de 8 passos que comença per la part baixa, soroll amb el LFSR de 15 bits, volum ×N/128 (127 = 128) amb el divisor (÷1, 2, 4, 16).
 */
class NHw {
    var busy = false; var fmt = 0 /* 0 PCM8, 1 PCM16, 2 ADPCM, 3 PSG, 4 soroll */; var ona: Ona? = null
    var reload = 0; private var timer = 0; private var pos = 0; var cur = 0; var duty = 0; private var lfsr = 0x7FFF; private var penjat = false
    var vol = 0; var sh = 4; var pan = 64
    /** SOUNDxCNT bits 0-9: volum (127 = ×128/128) i divisor; melonDS desplaça la mostra 4, 3, 2 o 0 bits (÷1, ÷2, ÷4, ÷16 amb 4 bits de fracció). */
    fun posaVol(v: Int) { val m = v and 0x7F; vol = if (m == 127) 128 else m; sh = SH[(v shr 8) and 3] }
    fun posaPan(p: Int) { pan = if (p == 127) 128 else p }
    fun atura() { busy = false }
    fun arrenca() {
        timer = reload; pos = when (fmt) { 3, 4 -> -1; 2 -> -11; else -> -3 }; lfsr = 0x7FFF; cur = 0; busy = true
        penjat = fmt < 3 && (ona?.paraules ?: 0) < 4          // PNT+LEN de menys de 4 paraules: el canal queda ocupat però no sona (GBATEK)
    }
    private fun pas() {
        when (fmt) {
            3 -> { pos++; val k = pos and 7; cur = if (duty < 7 && k >= 7 - duty) 0x7FFF else -0x7FFF }
            4 -> if (lfsr and 1 != 0) { lfsr = (lfsr shr 1) xor 0x6000; cur = -0x7FFF } else { lfsr = lfsr shr 1; cur = 0x7FFF }
            else -> {
                val o = ona ?: run { busy = false; cur = 0; return }
                pos++
                if (pos < 0) return
                if (pos >= o.sm.size) {
                    if (o.bucle && o.iniciBucle < o.sm.size) pos = o.iniciBucle else { cur = 0; busy = false; return }
                }
                cur = o.sm[pos].toInt()
            }
        }
    }
    /** 512 cicles de 16,76 MHz (una mostra del mesclador a 32728,5 Hz); torna la mostra × volum (16.11 bits), com SPUChannel::Run. */
    fun corre(): Int {
        if (!busy || penjat) return 0
        timer += 512
        while ((timer ushr 16) != 0) { timer = reload + (timer - 0x10000); pas(); if (!busy) break }
        return (cur shl sh) * vol
    }
    companion object { private val SH = intArrayOf(4, 3, 2, 0) }
}

/**
 * Sortida de l'àudio de la DS: mescla de 16 canals (cada un × panoràmica /128 i tallat a 16.8 bits), volum mestre 127 (= ×128/128, el que posa
 * SND_SetMasterVolume(0x7F) a SndThread, pret/pokediamond arm7/lib/src/SND_main.c), biaix 200h i retall a 10 bits (0..3FFh), GBATEK «Channel/Mixer
 * Bit-Widths» i melonDS SPU::Mix. El mesclador treu una mostra cada 1024 cicles de 33,513982 MHz (32728,5 Hz); el mòbil en vol `taux`: cada mostra de
 * sortida és la mitjana de la sortida mantinguda del DAC durant el seu interval (sense interpolar: un filtre de caixa sobre el senyal esglaonat).
 */
class NitroDac(taux: Int, private val font: Font) {
    /** Qui fa una mostra del DS: ha de sumar cada canal amb `suma`. */
    fun interface Font { fun mostraDs(d: NitroDac) }
    private val uHw = 1024L * taux; private val uOut = 33513982L          // durades en unitats comunes: una mostra del DS i una del mòbil
    private var resta = 0L; private var curL = 0; private var curR = 0
    private var l = 0L; private var r = 0L
    fun suma(v: Int, pan: Int) { l += (v.toLong() * (128 - pan)) shr 10; r += (v.toLong() * pan) shr 10 }
    /** Volum mestre ×128/128, 16.8 → enter (floor), retall a 10 bits al voltant del biaix 200h (-200h..1FFh, ×40h = -8000h..7FC0h). */
    private fun dac(x: Long): Int = (x shr 8).coerceIn(-0x8000L, 0x7FFFL).toInt() and -64
    fun mostra(out: ShortArray, i: Int) {
        var falta = uOut; var al = 0L; var ar = 0L
        while (falta > 0) {
            if (resta == 0L) { l = 0; r = 0; font.mostraDs(this); curL = dac(l); curR = dac(r); resta = uHw }
            val t = if (resta < falta) resta else falta
            al += curL * t; ar += curR * t; resta -= t; falta -= t
        }
        out[2 * i] = Math.floorDiv(al + uOut / 2, uOut).toInt().toShort()
        out[2 * i + 1] = Math.floorDiv(ar + uOut / 2, uOut).toInt().toShort()
    }
}

/** Pista de la seqüència (`Track.cs`). */
class NTrack(val num: Int) {
    var actiu = false; var pc = 0
    var noteWait = true; var tie = false; var noteFinishWait = false; var porta = false; var cmp = true
    var prg = 0; var vol = 127; var vol2 = 127; var bend = 0; var bendRange = 2; var pan = 0
    var atac = 0xFF; var decau = 0xFF; var sost = 0xFF; var alli = 0xFF
    var prio = 64; var transp = 0; var portaKey = 60; var portaTemps = 0; var sweep = 0
    var lfoTipus = 0; var lfoVel = 16; var lfoProf = 0; var lfoRang = 1; var lfoRetard = 0
    var espera = 0; var muda = false
    val pila = IntArray(3); val bucle = IntArray(3); var fons = 0
    val canals = ArrayList<NCanal>()
    fun inicia() {
        noteWait = true; tie = false; noteFinishWait = false; porta = false; cmp = true; prg = 0; prio = 64; vol = 127; vol2 = 127; pan = 0; bend = 0
        atac = 0xFF; decau = 0xFF; sost = 0xFF; alli = 0xFF; bendRange = 2; portaKey = 60; portaTemps = 0; sweep = 0; transp = 0
        lfoTipus = 0; lfoVel = 16; lfoProf = 0; lfoRang = 1; lfoRetard = 0; espera = 0; fons = 0; muda = false
    }
}

/** Canal de maquinari amb la seva envolupant (`ExChannel.cs`). */
class NCanal(val no: Int) {
    var actiu = false; var tipus = 0 /* 0 PCM, 1 PSG, 2 soroll */; var estat = 0 /* 0 atac, 1 decaïment, 2 manteniment, 3 alliberament */
    var iniciat = false; var autoSweep = true
    var key = 60; var origKey = 60; var vel = 127; var initPan = 0; var userPan = 0; var userDecay = 0; var userDecay2 = 0; var userPitch = 0; var panRange = 127
    var envDecay = 0; var atac = 0; var sost = 127; var decau = 0; var alli = 0
    var prio = 0; var vs = 0; var pan = 64; var timer = 0; var duty = 0
    /** syncFlag del NitroSDK (SND_exChannel_shared.h): 1 arrenca, 2 atura, 4 temporitzador, 8 volum, 10h panoràmica; s'aplica al maquinari al tic següent. */
    var sync = 0
    var sweepPitch = 0; var sweepLong = 0; var sweepCont = 0; var longitud = 0
    var lfoCont = 0; var lfoRetardCont = 0; var lfoTipus = 0; var lfoVel = 16; var lfoProf = 0; var lfoRang = 1; var lfoRetard = 0
    var ona: Ona? = null
    var pistaM = 0
    var pista: NTrack? = null
    /** El canal de maquinari que aquest ExChannel governa (mateix número). */
    val hw = NHw()
    fun posaAtac(a: Int) { atac = NitroT.atac(a) }
    fun posaDecau(d: Int) { decau = NitroT.caiguda(d) }
    fun posaAlli(r: Int) { alli = NitroT.caiguda(r) }
    fun envolupant(actualitza: Boolean): Int {
        if (actualitza) when (estat) {
            0 -> { envDecay = -((-envDecay * atac) shr 8); if (envDecay == 0) estat = 1 }
            1 -> { val s = NitroT.DB[sost].toInt() shl 7; envDecay -= decau; if (envDecay <= s) { envDecay = s; estat = 2 } }
            3 -> envDecay -= alli
        }
        return envDecay shr 7
    }
    fun sweepMain(actualitza: Boolean): Int {
        if (sweepPitch == 0 || sweepCont >= sweepLong) return 0
        val r = (sweepPitch.toLong() * (sweepLong - sweepCont) / sweepLong).toInt()
        if (actualitza && autoSweep) sweepCont++
        return r
    }
    fun lfoMain(actualitza: Boolean): Int {
        var v = 0L
        if (lfoProf != 0 && lfoRetardCont >= lfoRetard) v = lfoRang.toLong() * lfoProf * NitroT.sinIdx(lfoCont shr 8)
        if (v != 0L) { v *= if (lfoTipus == 1) 60 else 64; v = v shr 14 }
        if (actualitza) {
            if (lfoRetardCont < lfoRetard) lfoRetardCont++
            else {
                val v2 = lfoVel shl 6
                var i = (lfoCont + v2) shr 8
                while (i >= 0x80) i -= 128
                lfoCont = ((lfoCont + v2) and 0xFF) or (i shl 8)
            }
        }
        return v.toInt()
    }
}

/**
 * Sintetitzador d'una seqüència SDAT: port del driver de la DS (`Player.cs`, `Track.cs`, `ExChannel.cs`, `DSSoundContext.cs`).
 * Cada 5,2095 ms (191,95 Hz): tempo i pistes, envolupants, LFO, volum (en dB), to i panoràmica; 16 canals de maquinari amb la màscara del PLAYER.
 */
/** Funsies: instruments per a una pista: arxiu de so i seqüència de la qual s'agafa el banc, i número d'instrument fix (-1 = el que demani la pista). */
class VeuDs(val sdat: Sdat, val seq: Int, val prog: Int, val oct: Int = 0)

class Sinte(private val sdat: Sdat, seq: Int, private val muts: BooleanArray, solo0: Int = -1, bancDe: Int = -1, sdatVeus: Sdat? = null, veus: Array<VeuDs?>? = null) : Sintetitzador {
    override val taux = RadioFreq.taux
    @Volatile override var solo = solo0
    private val d: ByteArray = sdat.seqFitxer(seq).let { it.copyOfRange(((it[0x18].toInt() and 0xFF) or ((it[0x19].toInt() and 0xFF) shl 8)), it.size) }
    /** Arxiu d'on surten els instruments (banc i ones): el de la cançó o, si es demana, el d'un altre joc. */
    private val sv: Sdat = sdatVeus ?: sdat
    private val banc = sv.seqBanc(if (bancDe >= 0) bancDe else seq).let { Banc(sv.bancFitxer(it), sv.bancArxius(it)) }
    // Funsies: instruments propis de cada pista (banc i ones d'una altra seqüència, i número d'instrument fix; -1 = el que digui la pista)
    private val svT = Array(16) { veus?.getOrNull(it)?.sdat ?: sv }
    private val bancT = Array(16) { k -> veus?.getOrNull(k)?.let { v -> v.sdat.seqBanc(v.seq).let { Banc(v.sdat.bancFitxer(it), v.sdat.bancArxius(it)) } } ?: banc }
    private val progT = IntArray(16) { veus?.getOrNull(it)?.prog ?: -1 }
    override val volPistes = FloatArray(16) { 1f }
    private val octT = IntArray(16) { veus?.getOrNull(it)?.oct ?: 0 }       // Funsies: octaves amunt/avall de cada pista
    private var volumJugador = sdat.seqVolum(seq).coerceIn(0, 127)
    private val prioJugador = 64
    private val mascara = sdat.mascaraPlayer(seq).let { if (Integer.bitCount(it) < 8) 0xFFFF else it }
    val pistes = Array(16) { NTrack(it) }
    private val canals = Array(16) { NCanal(it) }
    private val vars = ShortArray(32) { if (it < 16) -1 else 0 }
    private var tempo = 120; private var tempoCont = 240
    @Volatile private var tempoX = 1.0; private var tempoF = 0.0
    override var ritme: Double get() = tempoX; set(v) { tempoX = v }
    override val tempoPropi: Boolean get() = true
    private var rand = 0x12345678L
    private val tg = FloatArray(16) { 1f }
    private var fi = false
    /** Pistes que el reproductor té assignades (PlayerGetTrack != NULL): la 0 i les de la màscara 0xFE; 0x93 només obre aquestes (SND_seq.c). */
    private val assignada = BooleanArray(16)
    private val dac = NitroDac(taux) { d -> mostraDs(d) }
    /** Mig cicles de mostra fins al tic següent: el temporitzador del motor (2728 × 64 cicles de 33,5 MHz) són 170,5 mostres del mesclador (1024 cicles). */
    private var ticMig = 339          // el primer tic (on SND_StartSeq comença) cau a la primera mostra

    init { pistes[0].actiu = true; pistes[0].pc = 0; assignada[0] = true; obreMasc() }

    /** Primera ordre d'una seqüència: 0xFE + màscara de pistes (AllocTrack). */
    private fun obreMasc() {
        if (u8(0) == 0xFE) {
            var m = (u8(1) or (u8(2) shl 8)) shr 1
            pistes[0].pc = 3
            var idx = 1
            while (m != 0) { if (m and 1 != 0 && idx < 16) { pistes[idx].actiu = false; assignada[idx] = true /* s'obre amb OpenTrack */ }; idx++; m = m shr 1 }
        }
    }
    override fun pistaActiva(i: Int) = i < 16 && pistes[i].actiu
    private var pistaBucle = -1; private var voltesN = 0
    override val voltes: Int get() = voltesN
    private val rellotge = RellotgeBatec(48)
    private var colector: DoubleArray? = null
    private val acum = Array(16) { AcumPerfil() }
    private val usosN = IntArray(128)
    override val usos: IntArray? get() = usosN
    override val perfils: Array<PerfilPista?>? get() = Array(16) { acum[it].perfil() }
    /** Nivell de cada pista: la velocitat dels seus canals que sonen (més fluix quan ja s'alliberen). */
    /** Oscil·loscopi per pista (1 a 16): la contribució de cada pista a la mescla, a ~8 kHz. */
    override val oscil = OscDades(List(16) { (it + 1).toString() })
    private val oscT = FloatArray(16); private val oscV = FloatArray(16); private var oscN = 0
    /** Nota MIDI exacta de cada pista (clau del canal + octaves de la pista); -1 = callada o soroll. */
    override fun notesMidi(out: FloatArray): Boolean {
        for (k in 0 until minOf(16, out.size)) out[k] = -1f
        for (c in canals) {
            if (!c.actiu || !c.hw.busy || c.tipus == 2) continue
            val i = (c.pista?.num ?: continue).coerceIn(0, 15)
            if (i < out.size) out[i] = (c.key + 12 * octT[i]).toFloat()
        }
        return true
    }
    override val nivells: FloatArray? get() = FloatArray(16) { i ->
        var m = 0f
        for (c in ArrayList(pistes[i].canals)) { if (!c.actiu) continue; m = maxOf(m, c.vel / 127f * (if (c.estat == 3) 0.35f else 1f)) }
        m
    }
    override fun analitza(maxSegons: Int): DoubleArray? {
        val h = DoubleArray(12); colector = h
        val max = (maxSegons / 0.0052095).toInt(); var f = 0
        while (f < max && voltesN < 1 && !acabada) { actualitza(); f++ }
        colector = null
        return h
    }
    override val batec: Double get() = rellotge.posicio(tempoCont / 240.0)
    override val batecMax: Double get() = rellotge.maxim()
    /** SSEQ: 48 tics per negra; cada pas (5,2095 ms) suma `tempo` i cada 240 és un tic. */
    override val bpm: Double get() = tempo * (1.0 / NitroT_FREQ_ENV) * 60.0 / (240.0 * 48.0)
    override val acabada: Boolean get() = pistes.none { it.actiu } && canals.none { it.actiu }

    private fun u8(o: Int) = d[o].toInt() and 0xFF
    private fun calcRandom(): Int { rand = (rand * 1664525L + 1013904223L) and 0xFFFFFFFFL; return ((rand shr 16) and 0xFFFF).toInt() }

    private fun llegeix(t: NTrack, tipus: Int): Int = when (tipus) {
        0 -> u8(t.pc++)
        1 -> { val v = u8(t.pc) or (u8(t.pc + 1) shl 8); t.pc += 2; v }
        2 -> { var r = 0; while (true) { val v = u8(t.pc++); r = (r shl 7) or (v and 0x7F); if (v and 0x80 == 0) break }; r }
        3 -> {
            val mn = (u8(t.pc) or (u8(t.pc + 1) shl 8)).toShort().toInt(); val mx = (u8(t.pc + 2) or (u8(t.pc + 3) shl 8)).toShort().toInt(); t.pc += 4
            ((calcRandom() * (mx - mn + 1)) shr 16) + mn
        }
        4 -> vars[u8(t.pc++) and 31].toInt()
        else -> 0
    }

    private fun alliberaCanals(t: NTrack, alli: Int) {
        actualitzaCanals(t, alli)
        for (c in t.canals) if (c.actiu) { if (alli >= 0) c.posaAlli(alli); c.prio = 1; c.estat = 3 }
    }
    private fun alliberaLlista(t: NTrack) { for (c in t.canals) c.pista = null; t.canals.clear() }

    /** Track.UpdateChannel: passa el volum, to, panoràmica i LFO de la pista als seus canals. */
    private fun actualitzaCanals(t: NTrack, alliP: Int) {
        val ud = NitroT.db(volumJugador) + NitroT.db(t.vol) + NitroT.db(t.vol2)
        val userDecay = ud.coerceAtLeast(-32768)
        val userPitch = (t.bend * (t.bendRange shl 6)) shr 7          // TrackUpdateChannel: desplaçament aritmètic (arrodoneix cap avall els negatius)
        val userPan = t.pan.coerceIn(-128, 127)
        for (c in t.canals) {
            c.userDecay2 = 0
            if (c.estat != 3) {
                c.userDecay = userDecay; c.userPitch = userPitch; c.userPan = userPan; c.panRange = 127
                c.lfoTipus = t.lfoTipus; c.lfoVel = t.lfoVel; c.lfoProf = t.lfoProf; c.lfoRang = t.lfoRang; c.lfoRetard = t.lfoRetard
                if (c.longitud == 0 && alliP != 0) { c.prio = 1; c.estat = 3 }
            }
        }
    }

    private fun assigna(mask: Int, prio: Int): NCanal? {
        var m: NCanal? = null
        for (k in 0 until 16) {
            val no = NitroT.ORDRE[k]
            if ((mask shr no) and 1 == 0) continue
            val c = canals[no]
            if (m != null) {
                val v12 = m.prio; val v13 = c.prio
                if (v13 <= v12 && (v13 != v12 || compara(m, c) < 0)) m = c
            } else m = c
        }
        if (m != null && prio >= m.prio) {
            m.pista?.canals?.remove(m); m.pista = null
            m.sync = 2; m.actiu = false          // SND_AllocExChannel: el canal de maquinari no s'atura fins al tic següent (SND_UpdateExChannel)
            return m
        }
        return null
    }
    private fun compara(a: NCanal, b: NCanal): Int {
        val va = 16 * (a.vs and 0xFF) shr NitroT.DESPL[a.vs shr 8]; val vb = 16 * (b.vs and 0xFF) shr NitroT.DESPL[b.vs shr 8]
        return if (va == vb) 0 else if (va >= vb) -1 else 1
    }

    private fun notaOn(t: NTrack, key: Int, vel: Int, longitud0: Int) {
        // Anàlisi de tonalitat: només notes melòdiques (ni kits de bateria ni soroll), pesades per la durada
        val banc = bancT[t.num]; val prg = if (progT[t.num] >= 0) progT[t.num] else t.prg
        if (colector != null) usosN[prg.coerceIn(0, 127)]++
        if (colector != null) { val tp = banc.regio(prg, key)?.tipus ?: 0; acum[t.num.coerceIn(0, 15)].nota(key, (if (longitud0 <= 0) 48 else longitud0) / 48.0, banc.esKit(prg), tp == 3, tp == 2, false) }
        colector?.let { h -> if (!banc.esKit(prg) && banc.regio(prg, key)?.let { it.tipus != 3 } == true) h[Math.floorMod(key, 12)] += (if (longitud0 <= 0) 48 else longitud0.coerceIn(4, 192)).toDouble() }
        var longitud = longitud0
        var c: NCanal? = null
        if (t.tie && t.canals.isNotEmpty()) {
            c = t.canals[0]; c.key = key; c.vel = vel
        } else {
            val r = banc.regio(prg, key) ?: return
            val mask = when (r.tipus) { 1 -> 0xFFFF; 2 -> 0x3F00; 3 -> 0xC000; else -> return } and mascara
            var ona: Ona? = null
            if (r.tipus == 1) ona = svT[t.num].ones(banc.arxius.getOrElse(r.swar) { 0xFFFF })?.getOrNull(r.swav) ?: return
            val n = assigna(mask, t.prio + prioJugador) ?: return
            if (t.tie) longitud = -1
            var rel = r.r
            if (rel == 0xFF) { longitud = -1; rel = 0 }
            // ExChannel.Start / NoteOn
            n.tipus = r.tipus - 1; n.ona = ona
            n.timer = if (r.tipus == 1) ona!!.temps else 8006          // waveParam.timer: el de l'SWAV; PSG i soroll, 8006 (SND_StartExChannelPsg/Noise)
            if (r.tipus == 2) n.duty = r.swav and 7
            n.envDecay = -92544; n.estat = 0; n.longitud = longitud; n.lfoCont = 0; n.lfoRetardCont = 0
            n.iniciat = true; n.actiu = true
            n.prio = (t.prio + prioJugador) and 0xFF; n.vs = 127          // ExChannelSetup desa la prioritat en un u8; n.autoSweep = true; n.userDecay = 0; n.userDecay2 = 0; n.userPitch = 0; n.userPan = 0; n.panRange = 127
            n.sweepPitch = 0; n.sweepLong = 0; n.sweepCont = 0
            n.key = key; n.origKey = r.nota; n.vel = vel
            n.posaAtac(r.a); n.posaDecau(r.d); n.sost = r.s.coerceIn(0, 127); n.posaAlli(rel)
            n.initPan = r.pan - 64
            n.pista = t; t.canals.add(0, n)
            c = n
        }
        if (t.atac != 0xFF) c.posaAtac(t.atac)
        if (t.decau != 0xFF) c.posaDecau(t.decau)
        if (t.sost != 0xFF) c.sost = t.sost.coerceIn(0, 127)
        if (t.alli != 0xFF) c.posaAlli(t.alli)
        c.sweepPitch = t.sweep
        if (t.porta) c.sweepPitch += (t.portaKey - key) shl 6
        if (t.portaTemps != 0) c.sweepLong = t.portaTemps * t.portaTemps * Math.abs(c.sweepPitch) shr 11
        else { c.sweepLong = longitud; c.autoSweep = false }
        c.sweepCont = 0
    }

    /** Un tic de la seqüència per a una pista (`Track.SeqMain`). Retorna true si la pista ha acabat. */
    private fun tic(t: NTrack): Boolean {
        for (c in t.canals) {
            if (c.longitud > 0) c.longitud--
            if (!c.autoSweep && c.sweepCont < c.sweepLong) c.sweepCont++
        }
        if (t.noteFinishWait) { if (t.canals.isNotEmpty()) return false; t.noteFinishWait = false }
        if (t.espera > 0) { t.espera--; if (t.espera > 0) return false }
        var guarda = 0
        while (true) {
            if (t.espera != 0 || t.noteFinishWait) return false
            if (++guarda > 4000 || t.pc < 0 || t.pc >= d.size) return true
            rellotge.visita(t.num, t.pc)
            var ordre = u8(t.pc++)
            var executa = true; var argOv = false; var tipusOv = 0
            if (ordre == 0xA2) { ordre = u8(t.pc++); executa = t.cmp }
            if (ordre == 0xA0) { ordre = u8(t.pc++); tipusOv = 3; argOv = true }
            if (ordre == 0xA1) { ordre = u8(t.pc++); tipusOv = 4; argOv = true }
            if (ordre < 0x80) {
                val vel = u8(t.pc++)
                val len = llegeix(t, if (argOv) tipusOv else 2)
                var key = ordre + t.transp
                if (executa) {
                    key = key.coerceIn(0, 127)
                    if (!t.muda) notaOn(t, key, vel, if (len <= 0) -1 else len)
                    t.portaKey = key
                    if (t.noteWait) { t.espera = len; if (len == 0) t.noteFinishWait = true }
                }
                continue
            }
            when (ordre and 0xF0) {
                0x80 -> {
                    val a = llegeix(t, if (argOv) tipusOv else 2)          // s32 al NitroSDK (sense tallar a 16 bits)
                    if (executa) { if (ordre == 0x80) t.espera = a else if (ordre == 0x81 && a < 0x10000) t.prg = a }
                }
                0x90 -> when (ordre) {
                    0x93 -> {
                        val idx = u8(t.pc++); val off = u8(t.pc) or (u8(t.pc + 1) shl 8) or (u8(t.pc + 2) shl 16); t.pc += 3
                        if (executa && idx < 16 && assignada[idx] && pistes[idx] !== t) {
                            // TrackStop + TrackStart: allibera els canals i canvia el punter, però no torna a inicialitzar la pista (TrackInit només a SND_PrepareSeq)
                            val o = pistes[idx]; alliberaCanals(o, -1); alliberaLlista(o); o.actiu = true; o.pc = off
                        }
                    }
                    0x94 -> { val off = u8(t.pc) or (u8(t.pc + 1) shl 8) or (u8(t.pc + 2) shl 16); t.pc += 3; if (executa) { if (off < t.pc) { if (pistaBucle < 0) pistaBucle = t.num; if (pistaBucle == t.num) voltesN++; rellotge.salt(t.num, off) }; t.pc = off } }
                    0x95 -> {
                        val off = u8(t.pc) or (u8(t.pc + 1) shl 8) or (u8(t.pc + 2) shl 16); t.pc += 3
                        if (executa && t.fons < 3) { t.pila[t.fons] = t.pc; t.fons++; t.pc = off }
                    }
                }
                0xB0 -> {
                    val v = u8(t.pc++) and 31
                    val a = llegeix(t, if (argOv) tipusOv else 1).toShort().toInt()
                    if (executa) {
                        val x = vars[v].toInt()
                        when (ordre) {
                            0xB0 -> vars[v] = a.toShort(); 0xB1 -> vars[v] = (x + a).toShort(); 0xB2 -> vars[v] = (x - a).toShort(); 0xB3 -> vars[v] = (x * a).toShort()
                            0xB4 -> if (a != 0) vars[v] = (x / a).toShort()
                            0xB5 -> vars[v] = (if (a < 0) x shr -a else x shl a).toShort()
                            0xB6 -> { val neg = a < 0; val m = if (neg) -a else a; val r = (calcRandom() * (m + 1)) shr 16; vars[v] = (if (neg) -r else r).toShort() }
                            0xB8 -> t.cmp = x == a; 0xB9 -> t.cmp = x >= a; 0xBA -> t.cmp = x > a; 0xBB -> t.cmp = x <= a; 0xBC -> t.cmp = x < a; 0xBD -> t.cmp = x != a
                        }
                    }
                }
                0xC0, 0xD0 -> {
                    val a = llegeix(t, if (argOv) tipusOv else 0)
                    if (executa) when (ordre) {
                        0xC0 -> t.pan = (a - 64).toByte().toInt()
                        0xC1 -> t.vol = a
                        0xC2 -> volumJugador = a.coerceAtMost(127)
                        0xC3 -> t.transp = a.toByte().toInt()
                        0xC4 -> t.bend = a.toByte().toInt()
                        0xC5 -> t.bendRange = a
                        0xC6 -> t.prio = a
                        0xC7 -> t.noteWait = a and 1 == 1
                        0xC8 -> { t.tie = a and 1 == 1; alliberaCanals(t, -1); alliberaLlista(t) }
                        0xC9 -> { t.portaKey = (a + t.transp) and 0xFF; t.porta = true }
                        0xCA -> t.lfoProf = a
                        0xCB -> t.lfoVel = a
                        0xCC -> t.lfoTipus = a
                        0xCD -> t.lfoRang = a
                        0xCE -> t.porta = a and 1 == 1
                        0xCF -> t.portaTemps = a
                        0xD0 -> t.atac = a
                        0xD1 -> t.decau = a
                        0xD2 -> t.sost = a
                        0xD3 -> t.alli = a
                        0xD4 -> if (t.fons < 3) { t.pila[t.fons] = t.pc; t.bucle[t.fons] = a; t.fons++ }
                        0xD5 -> t.vol2 = a
                        0xD7 -> when (a) {          // TrackMute (SND_seq.c): 0 sona, 1 muda, 2 muda i allibera, 3 muda, allibera ràpid i deixa els canals
                            0 -> t.muda = false
                            1 -> t.muda = true
                            2 -> { t.muda = true; alliberaCanals(t, -1) }
                            3 -> { t.muda = true; alliberaCanals(t, 127); alliberaLlista(t) }
                        }
                    }
                }
                0xE0 -> {
                    val a = llegeix(t, if (argOv) tipusOv else 1).toShort().toInt()
                    if (executa) when (ordre) { 0xE0 -> t.lfoRetard = a and 0xFFFF; 0xE1 -> tempo = a and 0xFFFF; 0xE3 -> t.sweep = a }
                }
                0xF0 -> if (executa) when (ordre) {
                    0xFC -> if (t.fons != 0) {
                        var n = t.bucle[t.fons - 1]
                        var salta = true
                        if (n != 0) { n--; if (n == 0) { t.fons--; salta = false } }
                        if (salta) { t.bucle[t.fons - 1] = n; t.pc = t.pila[t.fons - 1] }
                    }
                    0xFD -> if (t.fons != 0) { t.fons--; t.pc = t.pila[t.fons] }
                    0xFF -> return true
                }
            }
        }
    }

    private fun tancaPista(t: NTrack) { alliberaCanals(t, -1); alliberaLlista(t); t.actiu = false; assignada[t.num] = false }

    private fun seqMain() {
        for (t in pistes) if (t.actiu) { if (tic(t)) tancaPista(t) }
    }

    /** Un pas de 5,2095 ms: tempo, pistes i canals (`TempoMain`, `UpdateChannel`, `ExChannelMain`). */
    private fun actualitza() {
        aplicaMaquinari()
        var n = 0
        while (tempoCont >= 240) { tempoCont -= 240; n++ }
        for (k in 0 until n) { rellotge.tic(); seqMain() }
        tempoF += tempo * tempoX; val afegeix = tempoF.toInt(); tempoF -= afegeix; tempoCont += afegeix
        for (t in pistes) if (t.actiu) actualitzaCanals(t, 1)
        for (c in canals) canalMain(c)
        for (c in canals) c.pistaM = c.pista?.num ?: c.pistaM
        calcRandom()          // SndThread crida SND_CalcRandom un cop per tic (SND_main.c)
        for (i in 0 until 16) { val obj = if (muts.getOrElse(i) { false } || (solo >= 0 && solo != i)) 0f else 1f; tg[i] += (obj - tg[i]) * 0.08f }
    }

    /** SND_UpdateExChannel: el primer que fa cada tic és passar al maquinari el que va calcular el tic anterior (arrencar, aturar, temporitzador, volum, pan). */
    private fun aplicaMaquinari() {
        for (c in canals) {
            val f = c.sync; if (f == 0) continue
            val h = c.hw
            if (f and 2 != 0) h.atura()
            if (f and 1 != 0) {
                h.atura()          // SND_SetupChannel* escriu SOUNDxCNT amb el bit 31 a zero
                h.fmt = when (c.tipus) { 0 -> c.ona?.format ?: 0; 1 -> 3; else -> 4 }; h.ona = c.ona; h.duty = c.duty
                h.reload = 0x10000 - c.timer; h.posaVol(c.vs); h.posaPan(c.pan)
            } else {
                if (f and 4 != 0) h.reload = 0x10000 - c.timer
                if (f and 8 != 0) h.posaVol(c.vs)
                if (f and 0x10 != 0) h.posaPan(c.pan)
            }
        }
        for (c in canals) { if (c.sync and 1 != 0) c.hw.arrenca(); c.sync = 0 }
    }

    /** SND_ExChannelMain (pret/pokediamond arm7/lib/src/SND_exChannel.c). */
    private fun canalMain(c: NCanal) {
        if (!c.actiu) return
        if (c.iniciat) { c.sync = c.sync or 1; c.iniciat = false }
        else if (!c.hw.busy) { c.pista?.canals?.remove(c); c.pista = null; c.prio = 0; c.vs = 0; c.actiu = false; return }
        val v8 = (c.key - c.origKey + 12 * (c.pista?.let { octT[it.num.coerceIn(0, 15)] } ?: 0)) shl 6
        val v9 = NitroT.db(c.vel) + c.envolupant(true)
        val v10 = c.sweepMain(true)
        var db = v9 + c.userDecay + c.userDecay2
        c.pista?.let { val g = volPistes[it.num.coerceIn(0, 15)]; if (g < 1f) db += NitroT.db((127 * g).toInt().coerceIn(0, 127)) }
        var pitch = v8 + v10 + c.userPitch
        val lfo = c.lfoMain(true)
        var v3 = 0
        when (c.lfoTipus) { 0 -> pitch += lfo; 1 -> if (db > -32768) db += lfo; 2 -> v3 = lfo }
        var v15 = v3 + c.initPan
        if (c.panRange != 127) v15 = (v15 * c.panRange + 64) shr 7
        val v17 = v15 + c.userPan
        if (c.estat == 3 && db <= -723) {
            c.sync = 2          // syncFlag = SND_CHN_SYNC_STOP (assignació, no OR)
            c.pista?.canals?.remove(c); c.pista = null; c.prio = 0; c.vs = 0; c.actiu = false
        } else {
            val vs = NitroT.volum(db)
            var tm = NitroT.temporitzador(if (c.tipus == 0) (c.ona?.temps ?: 0) else 8006, pitch)
            if (c.tipus == 1) tm = tm and 0xFFFC
            val pn = (v17 + 64).coerceIn(0, 127)
            if (vs != c.vs) { c.vs = vs; c.sync = c.sync or 8 }
            if (tm != c.timer) { c.timer = tm; c.sync = c.sync or 4 }
            if (pn != c.pan) { c.pan = pn; c.sync = c.sync or 0x10 }
        }
    }

    // ---- Maquinari: 16 canals mostra a mostra a 32728,5 Hz (NHw, NitroDac); el motor fa un tic cada 170,5 mostres ----
    private val oscCh = IntArray(16)
    private fun mostraDs(d: NitroDac) {
        ticMig += 2
        if (ticMig >= 341) { ticMig -= 341; actualitza() }          // l'alarma del motor cau entre mostres: els registres canvien abans de la mostra següent
        for (k in 0 until 16) {
            val c = canals[k]; val h = c.hw
            if (!h.busy) { oscCh[k] = 0; continue }
            var v = h.corre()
            val g = tg[c.pistaM]
            if (g < 1f) v = (v * g).toInt()          // silenci/solo de pista de l'app (no és de la consola): només quan no és 1
            oscCh[k] = v
            d.suma(v, h.pan)
        }
    }

    override fun renderitza(out: ShortArray, frames: Int) {
        val osc = Oscil.visible && EnPantalla.v
        if (osc) oscil.visibles = 16
        for (i in 0 until frames) {
            dac.mostra(out, i)
            if (osc && ++oscN >= 4) {
                oscN = 0; java.util.Arrays.fill(oscT, 0f)
                for (k in 0 until 16) oscT[canals[k].pistaM.coerceIn(0, 15)] += oscCh[k] * (1f / (32768f * 16f * 128f))
                for (k in 0 until 16) oscV[k] = oscT[k] * 0.5f + 0.5f; oscil.escriu(oscV)
            }
        }
    }
}

private const val NitroT_FREQ_ENV = 2728.0 * 64.0 / 33513982.0
