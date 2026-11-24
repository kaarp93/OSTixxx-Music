package cat.gbdsmixxx

/**
 * Streams (STRM) de l'SDAT: àudio gravat (PCM8, PCM16 o ADPCM) en comptes de partitures. Alguns jocs de DS hi guarden la música (p. ex. Advance Wars:
 * Days of Ruin, segons l'autor, 2026-11-09). El format de la capçalera (HEAD) i els blocs d'ADPCM s'han comprovat amb els streams d'Castlevania: Order of
 * Ecclesia (tots mono, ADPCM); l'estèreo (blocs alternats canal 0 / canal 1) i el PCM no s'han pogut comprovar amb cap fitxer real.
 */
class StrmInfo(
    val tipus: Int, val bucle: Boolean, val canals: Int, val taxa: Int, val iniciBucle: Int, val mostres: Int,
    val offDades: Int, val blocs: Int, val midaBloc: Int, val mostresBloc: Int, val midaUltim: Int, val mostresUltim: Int, val volum: Int,
    /** Camp «Time» de la capçalera (01Eh) = ARM7_CLOCK / taxa / 32 (GBATEK «DS Sound Files - STRM»); NNS_SndStrmSetup el passa al canal ×32 (`lsl #5`). */
    val temps: Int = 0,
) {
    val durada: Double get() = if (taxa > 0) mostres.toDouble() / taxa else 0.0

    companion object {
        /** Capçalera d'un fitxer STRM (només cal el principi del fitxer), o null si no ho és. `volum` ve del registre de l'INFO. */
        fun llegeix(f: ByteArray, volum: Int): StrmInfo? {
            if (f.size < 0x40 || f[0] != 'S'.code.toByte() || f[1] != 'T'.code.toByte() || f[2] != 'R'.code.toByte() || f[3] != 'M'.code.toByte()) return null
            fun u8(o: Int) = f[o].toInt() and 0xFF
            fun u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
            fun u32(o: Int) = u16(o) or (u16(o + 2) shl 16)
            val canals = u8(0x1A); val taxa = u16(0x1C)
            if (canals !in 1..2 || taxa <= 0 || u8(0x18) !in 0..2) return null
            val i = StrmInfo(u8(0x18), u8(0x19) != 0, canals, taxa, u32(0x20), u32(0x24), u32(0x28), u32(0x2C), u32(0x30), u32(0x34), u32(0x38), u32(0x3C), volum, u16(0x1E))
            return if (i.mostres <= 0 || i.blocs <= 0 || i.midaBloc <= 0 || i.mostresBloc <= 0) null else i
        }
    }
}

/** Reprodueix un stream de l'SDAT; els blocs es descodifiquen sota demanda perquè una cançó sencera no ocupi la memòria. */
class SinteStrm(sdat: Sdat, strm: Int, private val muts: BooleanArray, solo0: Int = -1) : Sintetitzador {
    override val taux = RadioFreq.taux
    @Volatile override var solo = solo0
    private val info = sdat.strmInfo(strm) ?: throw IllegalStateException("stream $strm no vàlid")
    private val f = sdat.strmFitxer(strm)
    private val vol = info.volum.coerceIn(0, 127) / 127f
    private var pos = 0.0
    private val cache = LinkedHashMap<Int, ShortArray>()
    private val ultB = intArrayOf(-1, -1); private val ultBl = arrayOfNulls<ShortArray>(2)
    override fun pistaActiva(i: Int) = i == 0
    private var voltesN = 0
    override val voltes: Int get() = voltesN
    override val acabada: Boolean get() = !info.bucle && kC[info.canals - 1] >= info.mostres
    override val teBucle: Boolean? get() = info.bucle

    private fun u8(o: Int) = if (o in f.indices) f[o].toInt() and 0xFF else 0
    private fun s16(o: Int) = (u8(o) or (u8(o + 1) shl 8)).toShort().toInt()

    private fun descodifica(c: Int, b: Int): ShortArray {
        val ultim = b == info.blocs - 1
        val len = if (ultim) info.midaUltim else info.midaBloc
        val n = if (ultim) info.mostresUltim else info.mostresBloc
        val off = info.offDades + b * info.canals * info.midaBloc + c * len
        return when (info.tipus) {
            0 -> ShortArray(n) { (u8(off + it).toByte() * 256).toShort() }
            1 -> ShortArray(n) { s16(off + 2 * it).toShort() }
            else -> {
                var s = s16(off); var idx = (u8(off + 2) or (u8(off + 3) shl 8)).and(0x7F).coerceAtMost(88)
                val out = ShortArray(minOf(n, maxOf(0, (len - 4) * 2))); var k = 0
                var j = 4
                while (k < out.size && j < len) {
                    val by = u8(off + j++)
                    for (h in 0..1) {
                        if (k >= out.size) break
                        val nib = if (h == 0) by and 15 else by shr 4
                        val st = Sdat.STEP[idx]; var df = st shr 3
                        if (nib and 1 != 0) df += st shr 2
                        if (nib and 2 != 0) df += st shr 1
                        if (nib and 4 != 0) df += st
                        s = Sdat.adpcmPas(s, nib, df)
                        out[k++] = s.toShort()
                        idx = (idx + Sdat.IDX[nib and 7]).coerceIn(0, 88)
                    }
                }
                out
            }
        }
    }

    private fun mostra(c: Int, k: Int): Float {
        if (k < 0) return 0f
        val b = k / info.mostresBloc
        if (b >= info.blocs) return 0f
        val bl: ShortArray
        if (ultB[c] == b) bl = ultBl[c]!!          // el bloc de l'última mostra d'aquest canal: sense passar per la memòria cau (clau amb boxing) a cada mostra
        else {
            val clau = b * 2 + c
            bl = cache[clau] ?: descodifica(c, b).also { cache[clau] = it; while (cache.size > 6) cache.remove(cache.keys.first()) }
            ultB[c] = b; ultBl[c] = bl
        }
        val i = k - b * info.mostresBloc
        return if (i < bl.size) bl[i].toFloat() else 0f
    }

    // ---- Maquinari: el stream sona per 1 o 2 canals de la DS sense interpolar, al temporitzador del fitxer, amb el volum en dB del NitroSystem ----
    /** Temporitzador del canal: el de la capçalera ×32; si falta (fitxers estranys), el de la taxa. */
    private val tmr = (if (info.temps > 0) info.temps shl 5 else (16756991 / info.taxa.coerceAtLeast(1))).coerceIn(0x10, 0xFFFF)
    private val reload = 0x10000 - tmr
    private val tmC = IntArray(2) { reload }; private val kC = IntArray(2) { -1 }; private val curC = IntArray(2)
    /** NNS_SndStrmSetVolume: SND_CalcChannelVolume(SNDi_DecibelTable[volum]) (NNS_SND_stream.s i NNS_SND_sndarc_stream.s de pret/pokediamond). */
    private val hwv = NHw().also { it.posaVol(NitroT.volum(NitroT.DB_LIN[info.volum.coerceIn(0, 127)].toInt())) }
    /** NNS_SndStrmSetup posa la panoràmica 40h (centre) a tots els canals; en estèreo es manté 0 / 127 (no comprovat: aquest NNS no en posa cap altra). */
    private val panC = if (info.canals >= 2) intArrayOf(0, 127) else intArrayOf(64, 64)
    private var finit = false
    private val dac = NitroDac(taux) { d ->
        var g = if (muts.getOrElse(0) { false } || (solo >= 0 && solo != 0)) 0 else 1
        for (c in 0 until info.canals) {
            if (!finit) {
                tmC[c] += 512
                while ((tmC[c] ushr 16) != 0) {
                    tmC[c] = reload + (tmC[c] - 0x10000)
                    var k = kC[c] + 1
                    if (k >= info.mostres) { if (info.bucle && info.iniciBucle < info.mostres) { k = info.iniciBucle; if (c == 0) voltesN++ } else { finit = c == info.canals - 1; curC[c] = 0; kC[c] = k; break } }
                    kC[c] = k; curC[c] = mostra(c, k).toInt()
                }
            }
            val v = (curC[c] shl hwv.sh) * hwv.vol * g
            d.suma(v, if (panC[c] == 127) 128 else panC[c])
        }
        pos = kC[0].coerceAtLeast(0).toDouble()
    }

    override fun renderitza(out: ShortArray, frames: Int) {
        for (i in 0 until frames) dac.mostra(out, i)
    }
}
