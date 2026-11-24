package cat.gbdsmixxx

/**
 * ROM de SNES (amb o sense la capçalera de 512 bytes de les còpies .smc) i la seva conversió d'adreces: LoROM (el banc $xx:8000-FFFF és el trosset de 32 KB número xx) o HiROM
 * (el banc $Cx:0000-FFFF és el de 64 KB número x). Es tria el mapa pel que té una capçalera interna coherent (suma de comprovació i el seu complement) a $7FC0 o $FFC0.
 */
class RomSnes(bytes: ByteArray) {
    val d: ByteArray = if (bytes.size % 1024 == 512) bytes.copyOfRange(512, bytes.size) else bytes
    private fun u16(o: Int) = if (o + 1 < d.size) (d[o].toInt() and 255) or ((d[o + 1].toInt() and 255) shl 8) else 0
    private fun capcaleraOk(o: Int) = o + 0x30 <= d.size && (u16(o + 0x1C) xor u16(o + 0x1E)) == 0xFFFF
    val hiRom: Boolean = !capcaleraOk(0x7FC0) && capcaleraOk(0xFFC0)
    /** Títol de la capçalera interna (21 caràcters). */
    val titol: String get() = String(d, if (hiRom) 0xFFC0 else 0x7FC0, 21, Charsets.ISO_8859_1).trimEnd()
    /** Posició dins el fitxer d'una adreça SNES (`banc`:`adr`), o -1 si no hi cau. */
    fun fitxer(banc: Int, adr: Int): Int {
        val b = banc and 0xFF
        val o = if (hiRom) { if (adr < 0x8000 && (b and 0x7F) < 0x40) return -1; (b and 0x3F) * 0x10000 + adr } else { if (adr < 0x8000) return -1; (b and 0x7F) * 0x8000 + (adr - 0x8000) }
        return if (o in d.indices) o else -1
    }
    fun u8(banc: Int, adr: Int): Int { val o = fitxer(banc, adr); return if (o < 0) 0 else d[o].toInt() and 255 }
    fun u16(banc: Int, adr: Int) = u8(banc, adr) or (u8(banc, adr + 1) shl 8)
    fun u24(banc: Int, adr: Int) = u16(banc, adr) or (u8(banc, adr + 2) shl 16)
    /** Còpia de `n` bytes que comencen a `banc`:`adr` (passa al banc següent en LoROM/HiROM quan cal, com un punter de 24 bits seqüencial). */
    fun bytes(banc: Int, adr: Int, n: Int): ByteArray = ByteArray(n) { k -> val a = adr + k; u8(banc + (a shr (if (hiRom) 16 else 15)) - (if (hiRom) 0 else (adr shr 15)) , if (hiRom) a and 0xFFFF else 0x8000 or (a and 0x7FFF)).toByte() }
    /** Cerca una seqüència de bytes; retorna la posició dins el fitxer o -1. */
    fun cerca(sig: IntArray): Int {
        outer@ for (i in 0..d.size - sig.size) { for (k in sig.indices) if ((d[i + k].toInt() and 255) != sig[k]) continue@outer; return i }
        return -1
    }
}

/**
 * Un joc de SNES la música del qual s'executa amb el programa de so original: el driver posa a `spc.ram` el programa i les dades de so que la ROM puja a l'SPC700, hi arrenca
 * (`spc.arrenca(adr)`) i demana la cançó pels ports (`spc.escriuPort`/`llegeixPort`, que són els $2140-$2143 de la CPU principal). No cal emular la CPU principal: les adreces dels
 * blocs de la ROM i el protocol de comandes surten del desassemblatge del joc. `cancons` són els noms (l'identificador és la posició, a partir d'1).
 */
interface SnesDriver {
    val nom: String
    val cancons: List<String>
    fun reconeix(rom: RomSnes): Boolean
    fun inicia(spc: SnesSpc, rom: RomSnes, id: Int)
    /** Un fotograma (1/60 s): per als jocs que cal que la CPU principal vagi escrivint als ports (esperar un reconeixement, etc.). */
    fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

class SinteSnesRom(private val rom: RomSnes, private val drv: SnesDriver, id: Int, private val muts: BooleanArray, solo0: Int = -1) : Sintetitzador {
    override val taux = RegioVideo.TAUX_SNES
    @Volatile override var solo = solo0
    private var spc = SnesSpc()
    private val idCan = id
    private var fots = 0L
    private var resta = 0
    private var porFot = 533                                            // mostres del fotograma que corre (533 o 534 en NTSC, 640 o 641 en PAL: `RegioVideo.mostresDelFotSnes`)
    override fun pistaActiva(i: Int) = i < 8
    override val nivells: FloatArray? get() = FloatArray(8) { (spc.dsp.regs[it * 16 + 8] and 0x7F) / 127f }
    override val oscil: OscDades? get() = spc.dsp.oscil
    // Pols, bucle i durada: s'analitzen una sola vegada per cançó (memòria cau) en una passada en silenci (KON del DSP); els drivers guarden estat, així que
    // l'anàlisi i la inicialització d'aquesta cançó es fan seguides i sense que cap altra cançó del mateix driver s'hi barregi
    // En reproduir no es fa l'anàlisi (pot trigar fins a 5 s): només el mesclador la demana, amb `preparaMesclador()`
    private val clauAn = RitmeRom.clauRom(rom.d) + "|" + drv.nom + "|" + id
    // Bucle i durada mentre sona (l'anàlisi de 5 s en silenci no arriba a veure el bucle d'una cançó de SNES): es registren les notes noves (KON del DSP) i, cada 3 s, un fil de fons
    // busca el període; es dóna per bo quan dues passades seguides coincideixen
    private val regOn = RegSo(300 * 60)
    private val avantOn = BooleanArray(8); private val avantEnv = IntArray(8)
    @Volatile private var anOn: AnalisiRitme? = null
    private var ultimaOn: AnalisiRitme? = null
    @Volatile private var detectant = false
    private var fotsDet = 0L
    private val an: AnalisiRitme get() = RitmeRom.cauOpt(clauAn) ?: anOn ?: AnalisiRitme.BUIDA
    private var posV = PosRitme(AnalisiRitme.BUIDA)
    private val pos: PosRitme get() { val a = an; if (posV.a !== a) posV = PosRitme(a); return posV }
    init { synchronized(drv) { drv.inicia(spc, rom, id) } }
    override fun preparaMesclador() {
        synchronized(drv) {
            RitmeRom.obte(clauAn) { analisiSnes(rom, drv, idCan) }
            spc = SnesSpc(); fots = 0L; resta = 0; drv.inicia(spc, rom, idCan)          // els drivers guarden estat: es torna a posar la cançó com al principi
        }
    }
    private val fps: Double get() = RegioVideo.fpsSnes()
    private val posFots: Double get() = if (fots == 0L) 0.0 else fots - 1 + (1.0 - (resta / porFot.toDouble()).coerceIn(0.0, 1.0))
    override val voltes: Int get() = pos.voltes(posFots)
    override val bpm: Double get() = pos.bpm(fps)
    override val batec: Double get() = pos.batec(posFots)
    override val batecMax: Double get() = pos.batecMax
    // El tempo el porten els temporitzadors de l'SPC700 (a 32 kHz fixos): no es pot canviar sense canviar el to, així que `ritme` no s'implementa (tempoPropi = false) i el mesclador remostreja
    override val teBucle: Boolean? get() = if (an.teBucle) true else if (an.acaba) false else null
    override val acabada: Boolean get() = an.acaba && posFots > an.fi + 8 * fps
    override fun analitza(maxSegons: Int): DoubleArray? { pos.salt = pos.saltAlBucle() - posFots; return an.hist }
    /** Executa `n` mostres de 4 en 4 (com fa l'anàlisi: el DSP deixa l'ENVX a 0 5 mostres després d'un KON) i n'observa les notes noves. */
    private val b4 = ShortArray(8)
    private val tmp = ShortArray(642 * 2)                // el fotograma més llarg (PAL) són 641 mostres; abans s'assignaven a cada crida (i un buffer per a cada tros de fotograma)
    private fun executaObserva(buf: ShortArray, n: Int) {
        var fet = 0
        while (fet < n) {
            val k = minOf(4, n - fet)
            spc.executa(b4, k)
            System.arraycopy(b4, 0, buf, fet * 2, k * 2)
            observa()
            fet += k
        }
    }
    private fun observa() {
        if (fots <= 0L || fots > 300 * 60L) return
        val r = spc.dsp.regs; val kon = r[0x4C]
        regOn.fot = (fots - 1).toInt()
        for (v in 0 until 8) {
            val on = (kon shr v) and 1 != 0
            val env = r[v * 16 + 8]
            if (on && !avantOn[v]) {
                val clau = (r[v * 16 + 2] or (r[v * 16 + 3] shl 8)) * 256 + r[v * 16 + 4]
                regOn.reg(0x100 + v, clau); regOn.nota(v, Double.NaN, clau)
            }
            avantOn[v] = on; avantEnv[v] = env
        }
        if (fots >= 900L && fots - fotsDet >= 180L && !detectant && anOn?.teBucle != true) detecta()
    }
    private fun detecta() {
        detectant = true; fotsDet = fots
        val n = (fots - 2).toInt()
        Thread {
            try {
                val c = regOn.copia(n)
                val a0 = RitmeRom.resultatDe(c, n, fps)
                val b = RitmeRom.bucleSequencia(c, n)
                val a = if (b != null) AnalisiRitme(a0.t, a0.fase, b[0], b[1], a0.fi, a0.hist, false) else a0
                val u = ultimaOn
                if (a.teBucle && u != null && u.teBucle && Math.abs(u.ini - a.ini) <= 3.0 && Math.abs(u.llarg - a.llarg) <= 3.0) { anOn = a; RitmeRom.guardaSiFalta(clauAn, a) }
                else if (a.acaba && !a.teBucle) { anOn = a; RitmeRom.guardaSiFalta(clauAn, a) }
                ultimaOn = a
            } catch (e: Throwable) { } finally { detectant = false }
        }.also { it.isDaemon = true; it.priority = Thread.MIN_PRIORITY }.start()
    }
    override fun renderitza(out: ShortArray, frames: Int) {
        var sil = 0
        for (k in 0 until 8) if (muts.getOrElse(k) { false } || (solo >= 0 && solo != k)) sil = sil or (1 shl k)
        spc.dsp.silenci = sil
        var f = 0
        while (f < frames) {
            if (resta <= 0) { porFot = RegioVideo.mostresDelFotSnes(fots); drv.frame(spc, rom, fots++); resta = porFot }
            val n = minOf(frames - f, resta)
            executaObserva(tmp, n)
            System.arraycopy(tmp, 0, out, f * 2, n * 2)
            f += n; resta -= n
        }
    }
}

/** Jocs de SNES amb driver (cada lot d'agents n'afegeix a la seva llista `DRIVERS_SNES_<lot>`; aquí s'ajunten). */
internal val DRIVERS_SNES_CUSTOM: List<SnesDriver> = DRIVERS_SNES_S1 + DRIVERS_SNES_S2 + DRIVERS_SNES_S3 + DRIVERS_SNES_S4 + DRIVERS_SNES_R2SA + DRIVERS_SNES_R2SB + DRIVERS_SNES_R2SC

/** Títols dels jocs de SNES amb driver (clau = `nom` del driver). */
val TITOLS_SNES: Map<String, String> = mapOf("MegaMan7" to "Mega Man 7", "MegaManX" to "Mega Man X", "SMW" to "Super Mario World", "YoshisIsland" to "Super Mario World 2: Yoshi's Island", "SuperMetroid" to "Super Metroid", "DKC1" to "Donkey Kong Country", "DKC2" to "Donkey Kong Country 2", "DKC3" to "Donkey Kong Country 3", "SGnG" to "Super Ghouls 'n Ghosts", "ZAMN" to "Zombies Ate My Neighbors", "GoofTroop" to "Goof Troop", "EarthBound" to "EarthBound", "Zelda3" to "The Legend of Zelda: A Link to the Past", "FF6" to "Final Fantasy VI", "FF5" to "Final Fantasy V", "FF4" to "Final Fantasy IV", "SMT1" to "Shin Megami Tensei", "SMAS_SMB1" to "Super Mario All-Stars: SMB1 / Lost Levels", "SMAS_SMB2" to "Super Mario All-Stars: SMB2", "SMAS_SMB3" to "Super Mario All-Stars: SMB3", "Pocky2" to "Pocky & Rocky 2", "ChronoTrigger" to "Chrono Trigger", "KirbyDL3" to "Kirby's Dream Land 3")

/** Passada en silenci d'una cançó de SNES amb una SPC700 pròpia: nota nova = bit de KON del DSP que s'engega o la veu que torna a començar (ENVX que cau a prop de 0 amb KON posat). */
private fun analisiSnes(rom: RomSnes, drv: SnesDriver, id: Int): AnalisiRitme {
    val fps = RegioVideo.fpsSnes()
    val spc = SnesSpc()
    val reg = RegSo((240 * fps).toInt())
    drv.inicia(spc, rom, id)
    val buf = ShortArray(2 * 4)                     // 533 o 534 mostres per fotograma (com en reproduir: `RegioVideo.mostresDelFotSnes`), de 4 en 4; es mira cada 4 mostres perquè el DSP deixa l'ENVX a 0 durant el retard de KON (5 mostres) i així es veu el key-on repetit de la mateixa veu amb el mateix valor
    val avantOn = BooleanArray(8); val avantEnv = IntArray(8)
    return RitmeRom.analitza(reg, fps, 5000L) { f ->
        drv.frame(spc, rom, f.toLong())
        var falten = RegioVideo.mostresDelFotSnes(f.toLong())
        while (falten > 0) {
            val n4 = minOf(4, falten); falten -= n4
            spc.executa(buf, n4)
            val r = spc.dsp.regs
            val kon = r[0x4C]
            for (n in 0 until 8) {
                val on = (kon shr n) and 1 != 0
                val env = r[n * 16 + 8]
                if (on && (!avantOn[n] || (avantEnv[n] > env + 32 && env < 24))) {
                    val clau = (r[n * 16 + 2] or (r[n * 16 + 3] shl 8)) * 256 + r[n * 16 + 4]
                    reg.reg(0x100 + n, clau); reg.nota(n, Double.NaN, clau)
                }
                avantOn[n] = on; avantEnv[n] = env
            }
        }
    }
}

/** Tots els drivers (fets a mà, experimentals i automàtics), construïts. Compatibilitat amb el codi d'abans: el codi nou fa servir `RegistreDrivers` (a demanda, per CRC). */
val DRIVERS_SNES: List<SnesDriver> get() = RegistreDrivers.llistaSnes()
