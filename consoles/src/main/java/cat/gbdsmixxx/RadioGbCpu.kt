package cat.gbdsmixxx


/**
 * Sintetitzador comú de les cançons de Game Boy/GBC (l'autor, 2026-11-23): l'APU (`M4aGb`), el filtre de sortida, la cua d'escriptures amb el cicle on passen i tot el que
 * veu la interfície. Les escriptures vénen de dues fonts: la CPU del joc amb el seu driver (`SinteGbCpu`) o una partitura gravada (`SintePartGb`, sense CPU ni driver).
 * Com que l'APU és la mateixa, el so és idèntic. `executaFrame` fa cridar `apuEscriu` per a cada escriptura del fotograma, amb el cicle relatiu a l'inici del fotograma.
 */
abstract class SinteGbBase(protected val rom: ByteArray, fps0: Double, protected val muts: BooleanArray, solo0: Int) : Sintetitzador {
    override val taux = RadioFreq.taux
    @Volatile override var solo = solo0
    protected val gb = M4aGb(false, taux)
    // Condensador de sortida: 0,999958 per cicle per a tots els models, com SameBoy (Core/apu.c, `highpass_rate = pow(0.999958, ...)`). Pan Docs («Obscure Behavior»)
    // diu 0,998943 per a MGB/CGB (un pas alt de ~700 Hz): provat, treia 8-10 dB als jocs de GBC (Oracle, SMB Deluxe); no s'aplica sense una mesura del maquinari.
    private val hpL = DcBlock(taux); private val hpR = DcBlock(taux)
    /**
     * Escriptures al so del fotograma en curs, amb el cicle de CPU on passen (des de l'inici del fotograma): s'apliquen a la mostra que els toca, com a la consola,
     * i no totes juntes al principi del fotograma. `posCua` és la mostra (des de l'inici del fotograma), `regCua` = adreça << 8 | valor.
     */
    private var posCua = IntArray(256); private var regCua = IntArray(256); private var nCua = 0; private var iCua = 0
    protected var enFrame = false
    private var mostraFot = 0
    protected var spf = taux.toDouble() / fps0
    private var restaFot = 0.0
    protected var fots = 0L
    private var ultimaNota = 0L
    private val tocat = BooleanArray(4)
    private val ram = IntArray(16)
    private val niv = FloatArray(4)
    override val nivells: FloatArray? get() = niv
    /** Oscil·loscopi per canal (P1, P2, ona i soroll): mostres de la sortida de cada canal de l'APU, a ~8 kHz. */
    override val oscil = OscDades(listOf("P1", "P2", "WV", "NO"))
    override fun notesMidi(out: FloatArray): Boolean { gb.notesMidi(out, 0); return true }          // notes exactes dels registres
    private val oscV = FloatArray(4); private var oscN = 0
    /** Pols, bucle i durada de la cançó (de l'anàlisi en silenci o de la partitura). */
    abstract val an: AnalisiRitme
    private var posV = PosRitme(AnalisiRitme.BUIDA)
    private val pos: PosRitme get() { val a = an; if (posV.a !== a) posV = PosRitme(a); return posV }
    @Volatile protected var fpsAct = fps0
    @Volatile private var ritmeV = 1.0
    private val volP = FloatArray(4) { 1f }
    private val soK = BooleanArray(4)
    /** Escriptures de l'engegada (abans del primer fotograma): s'han de gravar com a part del fotograma 0. */
    protected var escInit = IntArray(64); protected var nInit = 0
    /** Gravador (analitzadors): rep cada fotograma amb el ritme, els cicles crus i les escriptures. */
    var grabador: ((Double, IntArray, IntArray, Int) -> Unit)? = null

    /** Un fotograma de la cançó: crida `apuEscriu` per a cada escriptura al so, amb el cicle relatiu a l'inici del fotograma. */
    protected abstract fun executaFrame()
    /** El ritme (fotogrames per segon) que val després d'executar el fotograma. */
    protected abstract fun fpsDespres(): Double

    /** Wave RAM en engegar la consola: a la DMG és AC DD DA 48 36 02 CF 16 2C 04 E5 2C AC DD DA 48 (el patró que R-Type II carrega explícitament); a la GBC, 00 FF repetit.
     * R-Type (i qualsevol joc que no la carregui) confia en aquest valor: amb zeros el canal 3 queda mut. */
    protected fun iniciaApu() {
        val cgb = (rom[0x143].toInt() and 0x80) != 0
        val dmg = intArrayOf(0xAC, 0xDD, 0xDA, 0x48, 0x36, 0x02, 0xCF, 0x16, 0x2C, 0x04, 0xE5, 0x2C, 0xAC, 0xDD, 0xDA, 0x48)
        for (k in 0 until 16) gbRegistre(gb, ram, 0xFF30 + k, if (cgb) (if (k % 2 == 0) 0x00 else 0xFF) else dmg[k])
    }

    protected fun apuEscriu(a: Int, v: Int, cicleRel: Long) {
        if (enFrame) {
            if (nCua == posCua.size) { posCua = posCua.copyOf(nCua * 2); regCua = regCua.copyOf(nCua * 2) }
            posCua[nCua] = cicleRel.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(); regCua[nCua] = (a shl 8) or v; nCua++
        } else {
            gbRegistre(gb, ram, a, v)
            if (fots == 0L) { if (nInit == escInit.size) escInit = escInit.copyOf(nInit * 2); escInit[nInit++] = (a shl 8) or v }
        }
        when (a) { 0xFF14 -> marca(0, v); 0xFF19 -> marca(1, v); 0xFF1E -> marca(2, v); 0xFF23 -> marca(3, v) }
    }
    private fun marca(ch: Int, v: Int) { if (v and 0x80 != 0) { tocat[ch] = true; ultimaNota = fots } }

    private fun frame() {
        buidaCua()
        enFrame = true
        try { executaFrame() } finally { enFrame = false }
        fots++
        fpsAct = fpsDespres()
        grabador?.invoke(fpsAct, posCua, regCua, nCua)          // cicles crus, abans de passar-los a mostres
        spf = taux.toDouble() / fpsAct / ritmeV
        // Cicle -> mostra: el fotograma dura 4194304 / fps cicles i `spf` mostres. El que passa del final del fotograma (rutines més llargues que un fotograma) s'aplica al final.
        val k = spf * fpsAct / 4194304.0; val lim = Math.ceil(spf).toInt() - 1
        for (i in 0 until nCua) posCua[i] = minOf((posCua[i] * k).toInt(), lim)
        iCua = 0; mostraFot = 0
    }
    /** Executa un fotograma sense generar so (per gravar partitures). */
    fun avancaFotograma() { frame() }
    /** Aplica les escriptures del fotograma fins a la mostra `fins` (inclosa); amb `Int.MAX_VALUE`, totes. */
    private fun aplicaCua(fins: Int) { while (iCua < nCua && posCua[iCua] <= fins) { val x = regCua[iCua++]; gbRegistre(gb, ram, x shr 8, x and 255) } }
    private fun buidaCua() { aplicaCua(Int.MAX_VALUE); nCua = 0; iCua = 0 }
    /** Fotogrames de la cançó reproduïts (amb decimals). */
    private val posFots: Double get() = if (fots == 0L) 0.0 else fots - 1 + (1.0 - (restaFot / spf).coerceIn(0.0, 1.0))

    override fun pistaActiva(i: Int) = i < 4 && tocat[i]
    override val voltes: Int get() = pos.voltes(posFots)
    override val bpm: Double get() = pos.bpm(fpsAct)
    override val batec: Double get() = pos.batec(posFots)
    override val batecMax: Double get() = pos.batecMax
    override var ritme: Double get() = ritmeV; set(v) { ritmeV = v.coerceIn(0.25, 4.0); spf = taux.toDouble() / fpsAct / ritmeV }
    override val tempoPropi: Boolean get() = true
    override val volPistes: FloatArray? get() = volP
    /** Si l'anàlisi no ha trobat bucle però la cançó s'acaba sola, no en fa; si no se sap, se suposa que en fa (no s'amaguen cançons per això). */
    override val teBucle: Boolean? get() = !an.acaba
    /** Una cançó que no fa sonar res durant 8 s (després de començar) s'ha acabat; les que fan bucle no s'aturen mai. */
    override val acabada: Boolean get() = fots > 120 && fots - ultimaNota > 480
    /** Histograma de les 12 notes de la cançó fins al primer bucle (de l'anàlisi); deixa aquest sintetitzador al principi del bucle amb una volta feta. */
    override fun analitza(maxSegons: Int): DoubleArray? { pos.salt = pos.saltAlBucle() - posFots; return an.hist }

    override fun renderitza(out: ShortArray, frames: Int) {
        var f = 0
        while (f < frames) {
            if (restaFot <= 0.0) { frame(); restaFot += spf }
            val n = minOf(frames - f, Math.ceil(restaFot).toInt().coerceAtLeast(1))
            // Volàtils: es llegeixen un cop per tros, no a cada mostra. Els nivells i l'oscil·loscopi només es llegeixen amb la pantalla encesa (`Motor`):
            // amb la pantalla apagada no es calculen (no afecten el so) i els nivells es posen a 0.
            val vis = EnPantalla.v; val osc = Oscil.visible && vis
            if (!vis) niv.fill(0f)
            val sl = solo; for (k in 0 until 4) soK[k] = !muts[k] && (sl < 0 || sl == k)
            for (i in 0 until n) {
                if (iCua < nCua) aplicaCua(mostraFot)
                mostraFot++
                var l = 0f; var r = 0f
                for (ch in 1..4) {
                    val k = ch - 1
                    val so = soK[k]
                    val o = if (so) gb.out(ch, taux).toFloat() else 0f; if (osc) oscV[k] = o * 0.5f + 0.5f
                    val v = o * 0.25f * volP[k]
                    if (vis) niv[k] = maxOf(if (so) gb.nivell(ch) else 0f, niv[k] * 0.9993f)
                    if (gb.nr51 and (0x10 shl k) != 0) l += v
                    if (gb.nr51 and (0x01 shl k) != 0) r += v
                }
                // NR50: volum general (v+1)/8 de cada costat, abans del condensador (Pan Docs, «Audio details»: mesclador, NR50 i després el filtre)
                l *= (((gb.nr50 shr 4) and 7) + 1) * 0.125f; r *= ((gb.nr50 and 7) + 1) * 0.125f
                gb.tick()
                if (osc && ++oscN >= 4) { oscN = 0; oscil.escriu(oscV) }
                l = hpL.f(l.toDouble()).toFloat(); r = hpR.f(r.toDouble()).toFloat()
                out[(f + i) * 2] = SatGb.s(l)
                out[(f + i) * 2 + 1] = SatGb.s(r)
            }
            restaFot -= n
            f += n
        }
    }
}

/**
 * Cançó d'un joc de Game Boy amb driver de so executable (`GbDriver`): s'executa el codi original del joc amb la CPU `GbCpu` i les escriptures als
 * registres de so van als 4 canals de la consola (`M4aGb`, el mateix que fan servir Cristall, Groc i TCG). La ROM no es modifica ni es copia.
 */
class SinteGbCpu(rom: ByteArray, private val driver: GbDriver, id: Int, muts: BooleanArray, solo0: Int = -1) : SinteGbBase(rom, driver.fps, muts, solo0) {
    private val mem = GbMem(rom, driver.mbc5 ?: ((rom[0x147].toInt() and 255) >= 0x19))
    private val cpu = GbCpu(mem)
    private var c0 = 0L
    // Pols, bucle i durada: s'analitzen una sola vegada per cançó (memòria cau) en una passada en silenci amb una còpia del driver
    private val clauAn = RitmeRom.clauRom(rom) + "|" + driver.nom + "|" + id
    private val idCan = id
    override val an: AnalisiRitme get() = RitmeRom.cauOpt(clauAn) ?: AnalisiRitme.BUIDA
    override fun preparaMesclador() { RitmeRom.obte(clauAn) { analisiGb(rom, driver, idCan) } }
    /** L'anàlisi (bucle i durada), esperant-la si cal (per als analitzadors). */
    fun analisiCompleta(): AnalisiRitme = RitmeRom.obte(clauAn) { analisiGb(rom, driver, idCan) }
    // L'anàlisi (bucle i durada) tarda de menys d'un segon a uns quants (Oracle of Ages/Seasons: 3 s a l'ordinador, molt més al mòbil): es fa en un fil a part perquè la cançó soni a l'instant;
    // mentre no és a punt, `an` és buida (se suposa que fa bucle) i el mesclador l'espera amb `preparaMesclador`.
    init { if (!SOLS_GRAVAR.get()) Thread { RitmeRom.obte(clauAn) { analisiGb(rom, driver, idCan) } }.also { it.isDaemon = true }.start() }

    init {
        mem.alRegistre = { a, v -> apuEscriu(a, v, mem.cicles - c0) }
        iniciaApu()
        driver.preparaRam(cpu, mem); driver.inicia(cpu, mem, id)
    }

    override fun executaFrame() { c0 = mem.cicles; driver.frame(cpu, mem, fots) }
    override fun fpsDespres(): Double = driver.fpsActual(mem)

    /**
     * Grava la cançó (per als analitzadors): executa `maxFots` fotogrames sense so i guarda les escriptures a l'APU amb els seus cicles; l'anàlisi (bucle, pols, durada) es calcula abans
     * i diu quants fotogrames calen (fins a la fi de la primera volta del bucle, o fins a la fi de la cançó).
     */
    fun grava(driverId: String, id: Int, maxFots: Int = 240 * 60): CancoPart {
        val an = analisiCompleta()
        val g = GrabaCanco(driverId, id); g.iniciEscriptures(escInit, nInit)
        val fins = (if (an.teBucle) Math.ceil(an.ini + an.llarg).toInt() else if (an.acaba && an.fi > 0) an.fi else maxFots).coerceIn(1, maxFots)
        grabador = { f, c, a, n -> g.fotograma(f, c, a, n) }
        for (i in 0 until fins) avancaFotograma()
        grabador = null
        return g.fes(fins, an)
    }
    companion object { /** Els analitzadors graven sense engegar el fil d'anàlisi de fons de la reproducció. */ val SOLS_GRAVAR = java.util.concurrent.atomic.AtomicBoolean(false) }
}

/** Cançó de Game Boy/GBC reproduïda des d'una partitura gravada: sense CPU ni driver; només l'APU (`SinteGbBase`). La ROM només serveix per desxifrar la partitura i per saber el model. */
class SintePartGb(rom: ByteArray, private val c: CancoPart, muts: BooleanArray, solo0: Int = -1) : SinteGbBase(rom, c.fps0, muts, solo0) {
    override val an: AnalisiRitme get() = c.an
    private var idx = 0
    init {
        iniciaApu()
        // escriptures de l'engegada del driver (abans del primer fotograma): estan gravades com a primers esdeveniments del fotograma 0 (cicle 0)
        enFrame = false
    }
    override fun executaFrame() {
        val g = c.fotGravat(fots); idx = g
        if (g >= 0) for (k in c.iniFot[g] until c.iniFot[g + 1]) apuEscriu(c.adr[k], c.val_[k], c.cicle[k].toLong())
    }
    override fun fpsDespres(): Double = c.fpsFot(if (idx >= 0) idx else c.nFot - 1)
}

/** Registres de so de la consola ($FF10-$FF3F) -> els 4 canals de `M4aGb`; `ram` són els 16 bytes de la RAM d'ones. */
fun gbRegistre(gb: M4aGb, ram: IntArray, a: Int, v: Int) {
    // APU apagada (NR52 bit 7 = 0): només s'hi pot escriure NR52, la RAM d'ones i, a la DMG, la longitud de NRx1 (Pan Docs, «FF26 — NR52»; gbdev wiki, «Power Control»)
    if (!gb.encesa && a < 0xFF26) {
        when (a) { 0xFF11 -> gb.len[1] = 64 - (v and 63); 0xFF16 -> gb.len[2] = 64 - (v and 63); 0xFF1B -> gb.nr31(v); 0xFF20 -> gb.nr41(v) }
        return
    }
    when (a) {
        0xFF10 -> gb.nr10(v)
        0xFF11 -> gb.nr1x(1, v)
        0xFF12 -> gb.nrx2(1, v)
        0xFF13 -> gb.nrx3(1, v)
        0xFF14 -> gb.nrx4(1, v)
        0xFF16 -> gb.nr1x(2, v)
        0xFF17 -> gb.nrx2(2, v)
        0xFF18 -> gb.nrx3(2, v)
        0xFF19 -> gb.nrx4(2, v)
        0xFF1A -> gb.nr30w(v)
        0xFF1B -> gb.nr31(v)
        0xFF1C -> gb.nr2[3] = v
        0xFF1D -> gb.nrx3(3, v)
        0xFF1E -> gb.nrx4(3, v)
        0xFF20 -> gb.nr41(v)
        0xFF21 -> gb.nrx2(4, v)
        0xFF22 -> gb.nr43(v)
        0xFF23 -> gb.nrx4(4, v)
        0xFF24 -> gb.nr50 = v
        0xFF25 -> gb.nr51 = v
        0xFF26 -> gb.nr52(v)
        in 0xFF30..0xFF3F -> {
            // Abans es refeia tota l'ona (IntArray nova de 32) a cada byte: només canvien els dos nibbles d'aquest byte (l'ona de `gb` només la toca aquesta funció)
            val k = a - 0xFF30; ram[k] = v
            gb.wave[2 * k] = (v shr 4) and 15; gb.wave[2 * k + 1] = v and 15
        }
    }
}

/** Passada en silenci d'una cançó de Game Boy (còpia pròpia de la CPU): fotograma de cada nota nova (bit 7 de NR14/24/34/44) i hash de les escriptures als registres de so. */
private fun analisiGb(rom: ByteArray, driver: GbDriver, id: Int): AnalisiRitme {
    val mem = GbMem(rom, driver.mbc5 ?: ((rom[0x147].toInt() and 255) >= 0x19))
    val cpu = GbCpu(mem)
    val fps = driver.fps
    val reg = RegSo((240 * fps).toInt())
    val sh = IntArray(0x100)
    mem.alRegistre = { a, v ->
        reg.reg(a, v); sh[a and 0xFF] = v
        val ch = when (a) { 0xFF14 -> 0; 0xFF19 -> 1; 0xFF1E -> 2; 0xFF23 -> 3; else -> -1 }
        if (ch >= 0 && v and 0x80 != 0) {
            if (ch == 3) reg.nota(3, Double.NaN, sh[0x22])
            else { val x = sh[(a - 1) and 0xFF] or ((v and 7) shl 8); reg.nota(ch, RitmeRom.midi((if (ch == 2) 65536.0 else 131072.0) / (2048 - x)), x) }
        }
    }
    driver.preparaRam(cpu, mem); driver.inicia(cpu, mem, id)
    return RitmeRom.analitza(reg, fps, 3000L) { f -> driver.frame(cpu, mem, f.toLong()) }
}

/**
 * Sortida de 16 bits. La consola és lineal fins a la sortida (DAC, mesclador, NR50 i condensador; Pan Docs, «Audio details»): abans hi havia una saturació suau
 * inventada (`tanh(l * 1,2)`) que comprimia els passatges forts. Ara és lineal: el fons d'escala del mesclador (4 canals a ±1, x 0,25) és el fons d'escala de 16 bits,
 * i només es retalla si el sobrepic del condensador se'n passa. (Amb guany x 1,2, el pendent de l'antiga tanh, Mega Man II #7 retallava el 0,4 % de les mostres.)
 * Sona ~1 dB més fluix que abans als passatges mitjans i sense compressió als forts.
 */
object SatGb {
    fun s(l: Float): Short { val y = l * 32767f; return (if (y >= 32767f) 32767 else if (y <= -32768f) -32768 else y.toInt()).toShort() }
}
