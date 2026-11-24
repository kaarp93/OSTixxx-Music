package cat.gbdsmixxx

/** ROM de NES en format iNES (capçalera de 16 bytes: «NES», 0x1A, nombre de bancs de PRG de 16 KB i de CHR de 8 KB, mapper als nibbles de les banderes 6 i 7). */
class NesRom(val d: ByteArray) {
    val valid = d.size > 16 && d[0] == 'N'.code.toByte() && d[1] == 'E'.code.toByte() && d[2] == 'S'.code.toByte() && d[3] == 0x1A.toByte()
    val prgBancs get() = d[4].toInt() and 255
    /** CRC32 de tot el fitxer (com al report de l'analitzador); es fa servir per reconèixer els jocs dels drivers automàtics. */
    val crc32: Long by lazy { java.util.zip.CRC32().also { it.update(d) }.value }
    val mapper get() = ((d[6].toInt() and 255) shr 4) or (d[7].toInt() and 0xF0)
    private val inici get() = 16 + (if (d[6].toInt() and 4 != 0) 512 else 0)
    val prg: ByteArray by lazy { d.copyOfRange(inici, minOf(d.size, inici + prgBancs * 0x4000)) }
    /** Primera posició de la PRG on hi ha aquesta seqüència de bytes (-1 = qualsevol byte); -1 si no hi és. */
    fun cerca(sig: IntArray): Int {
        // Memòria cau (optimització 2026-11-18): molts drivers la criden a cada fotograma (60 cops/s sobre tota la PRG); la ROM no canvia, el resultat tampoc.
        // Clau = còpia de la signatura (es compara pel contingut); pot cridar-se des del fil d'àudio i del de l'anàlisi alhora.
        val c = cauCerca
        for (e in c) if (e.first.contentEquals(sig)) return e.second
        val r = cercaSenseCau(sig)
        synchronized(this) { if (cauCerca.size < 64) cauCerca = cauCerca + Pair(sig.copyOf(), r) }
        return r
    }
    @Volatile private var cauCerca: Array<Pair<IntArray, Int>> = emptyArray()
    private fun cercaSenseCau(sig: IntArray): Int {
        val p = prg
        var i = 0
        while (i + sig.size <= p.size) { var ok = true; for (k in sig.indices) if (sig[k] >= 0 && (p[i + k].toInt() and 255) != sig[k]) { ok = false; break }; if (ok) return i; i++ }
        return -1
    }
}

/**
 * Memòria d'una NES: RAM de 2 KB, RAM de 8 KB a $6000, PRG a $8000-$FFFF amb els mappers més habituals (0 NROM, 1 MMC1, 2 UNROM, 4 MMC3, 7 AxROM; els altres es tracten com
 * NROM) i els registres de l'APU.
 */
class NesRomMem(private val r: NesRom, val apu: () -> NesApu?) : Bus6502 {
    /** Gravador de partitures: rep cada escriptura als registres de l'APU ($4000-$4017) i totes les de mapper ($8000-$FFFF) i de triaBanc/triaMmc3 (adreces $20000-$20002), tal com les fa el driver, perquè la DMC llegeix de la ROM amb el banc que toca. */
    @JvmField var alRegistre: ((Int, Int) -> Unit)? = null
    val ram = IntArray(0x800); val sram = IntArray(0x2000)
    private val prg = r.prg
    private val mapper = r.mapper                 // abans es tornava a calcular de la capçalera a cada lectura de la PRG
    private val n16 = maxOf(1, prg.size / 0x4000); private val n8 = maxOf(1, prg.size / 0x2000); private val n32 = maxOf(1, prg.size / 0x8000)
    // MMC1
    private var m1Shift = 0x10; private var m1Ctl = 0x0C; private var m1Prg = 0
    // UNROM / AxROM / Color Dreams (11)
    private var banc = 0
    // VRC6 (mappers 24 i 26): banc de 16 KB a $8000 i de 8 KB a $C000; $E000 fix a l'últim
    private var v6B16 = 0; private var v6B8 = 0
    // MMC3
    private var m3Sel = 0; private val m3R = IntArray(8)

    private fun prgOff(a: Int): Int = when (mapper) {
        1 -> {
            val mode = (m1Ctl shr 2) and 3
            when (mode) {
                0, 1 -> ((m1Prg and 0xE) % n16) * 0x4000 + (a - 0x8000)
                2 -> if (a < 0xC000) a - 0x8000 else (m1Prg and 0xF) % n16 * 0x4000 + (a - 0xC000)
                else -> if (a < 0xC000) (m1Prg and 0xF) % n16 * 0x4000 + (a - 0x8000) else (n16 - 1) * 0x4000 + (a - 0xC000)
            }
        }
        2 -> if (a < 0xC000) (banc % n16) * 0x4000 + (a - 0x8000) else (n16 - 1) * 0x4000 + (a - 0xC000)
        7, 11 -> (banc % n32) * 0x8000 + (a - 0x8000)
        24, 26 -> if (a < 0xC000) (v6B16 % n16) * 0x4000 + (a - 0x8000) else if (a < 0xE000) (v6B8 % n8) * 0x2000 + (a - 0xC000) else (n8 - 1) * 0x2000 + (a - 0xE000)
        4 -> {
            val mode = (m3Sel shr 6) and 1
            val b = when ((a - 0x8000) shr 13) {
                0 -> if (mode == 0) m3R[6] else n8 - 2
                1 -> m3R[7]
                2 -> if (mode == 0) n8 - 2 else m3R[6]
                else -> n8 - 1
            }
            ((b % n8) * 0x2000) + (a and 0x1FFF)
        }
        else -> (a - 0x8000) % maxOf(1, prg.size)
    }

    /** Instantània dels registres de banc (per a l'analitzador: tornar a posar els bancs que hi havia en cridar una rutina). */
    fun copiaBancs(): IntArray = intArrayOf(m1Shift, m1Ctl, m1Prg, banc, m3Sel, m3R[0], m3R[1], m3R[2], m3R[3], m3R[4], m3R[5], m3R[6], m3R[7], v6B16, v6B8)
    fun posaBancs(a: IntArray) { m1Shift = a[0]; m1Ctl = a[1]; m1Prg = a[2]; banc = a[3]; m3Sel = a[4]; for (i in 0..7) m3R[i] = a[5 + i]; v6B16 = a[13]; v6B8 = a[14] }
    /** Registres de banc actuals (per als reports de l'analitzador). */
    fun estatBancs(): String = when (mapper) { 1 -> "MMC1 prg=%d ctl=%02X".format(m1Prg, m1Ctl); 2, 7, 11 -> "banc=$banc"; 4 -> "MMC3 R6=%d R7=%d sel=%02X".format(m3R[6], m3R[7], m3Sel); 24, 26 -> "VRC6 b16=%d b8=%d".format(v6B16, v6B8); else -> "fix" }

    /** MMC3: tria els bancs de PRG de 8 KB visibles a $8000 (R6) i a $A000 (R7). */
    fun triaMmc3(b6: Int, b7: Int) { alRegistre?.invoke(0x20001, b6); alRegistre?.invoke(0x20002, b7); if (mapper == 4) { m3R[6] = b6; m3R[7] = b7 } }

    /** Tria el banc de 16 KB de PRG que és visible a $8000 (mappers 1 i 2; per a la resta no fa res). */
    fun triaBanc(b: Int) { alRegistre?.invoke(0x20000, b); when (mapper) { 1 -> m1Prg = b; 2, 7, 11 -> banc = b } }

    override fun rb(a: Int): Int = when {
        a < 0x2000 -> ram[a and 0x7FF]
        a == 0x4015 -> apu()?.estat() ?: 0
        a < 0x6000 -> 0
        a < 0x8000 -> sram[a - 0x6000]
        else -> { val o = prgOff(a); if (o in prg.indices) prg[o].toInt() and 255 else 0 }
    }

    override fun wb(a: Int, v0: Int) {
        val v = v0 and 255
        when {
            a < 0x2000 -> ram[a and 0x7FF] = v
            a in 0x4000..0x4017 && a != 0x4014 && a != 0x4016 -> { alRegistre?.invoke(a, v); apu()?.escriu(a, v) }
            a in 0x6000..0x7FFF -> sram[a - 0x6000] = v
            a >= 0x8000 -> { alRegistre?.invoke(a, v); when (mapper) {
                1 -> {
                    if (v and 0x80 != 0) { m1Shift = 0x10; m1Ctl = m1Ctl or 0x0C }
                    else {
                        val fi = m1Shift and 1 != 0
                        m1Shift = (m1Shift shr 1) or ((v and 1) shl 4)
                        if (fi) { when ((a shr 13) and 3) { 0 -> m1Ctl = m1Shift and 31; 3 -> m1Prg = m1Shift and 31 }; m1Shift = 0x10 }
                    }
                }
                2, 7 -> banc = v and 0x0F
                11 -> banc = v and 3                      // Color Dreams: bits 0-1 = banc de 32 KB de PRG (els 4-7 són de la CHR, que aquí no es fa servir)
                24, 26 -> {
                    val aa = if (mapper == 26) (a and 0xFFFC) or ((a and 1) shl 1) or ((a shr 1) and 1) else a      // el mapper 26 té A0 i A1 intercanviats
                    when (aa and 0xF000) {
                        0x8000 -> if (aa and 3 == 0) v6B16 = v and 0x0F
                        0xC000 -> if (aa and 3 == 0) v6B8 = v and 0x1F
                        0x9000, 0xA000, 0xB000 -> apu()?.escriuVrc6(aa and 0xF003, v)
                    }
                }
                4 -> if (a < 0xA000) { if (a and 1 == 0) m3Sel = v else m3R[m3Sel and 7] = v }
            } }
        }
    }
}

/**
 * Un joc de NES el driver de so del qual s'executa tal com és: com es reconeix (per contingut), com es demana la cançó i què fa el joc a cada interrupció
 * de vblank (NMI) per al so. `cancons` són els noms de les cançons que es poden demanar (l'identificador és la posició, a partir d'1).
 */
interface NesDriver {
    val nom: String
    val cancons: List<String>
    fun reconeix(rom: NesRom): Boolean
    fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int)
    fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long)
}

/**
 * Super Mario Bros. De threecreepio/smb-disassembly: `SoundEngine` (que el joc crida a cada NMI) es troba per la seva seqüència de bytes
 * (`lda OperMode; bne; sta $4015; rts; lda #$ff; sta $4017; lda #$0f; sta $4015`); `OperMode` ($0770) ha de ser diferent de 0 perquè soni. La música es demana a la
 * zero page: `AreaMusicQueue` ($FB: terra $01, aigua $02, subterrani $04, castell $08, núvols $10, intro del tub $20, estrella $40) o `EventMusicQueue` ($FC: mort $01,
 * game over $02, victòria $04, final del castell $08, game over alternatiu $10, final del nivell $20, es fa tard $40). Comprovat al harness amb Mega Man 3 USA i Europa (PRG1): sonen els 57 ids provats.
 */
object DriverSmb : NesDriver {
    override val nom = "SMB"
    override val cancons = listOf("Superfície", "Aigua", "Subterrani", "Castell", "Núvols", "Intro del tub", "Estrella", "Mort", "Game over", "Victòria", "Final del castell", "Game over (alternatiu)", "Final del nivell", "Es fa tard")
    private val SIG = intArrayOf(0xAD, 0x70, 0x07, 0xD0, 0x04, 0x8D, 0x15, 0x40, 0x60, 0xA9, 0xFF, 0x8D, 0x17, 0x40, 0xA9, 0x0F, 0x8D, 0x15, 0x40)
    private fun adr(rom: NesRom): Int { val o = rom.cerca(SIG); return if (o < 0) -1 else 0x8000 + o }
    override fun reconeix(rom: NesRom) = rom.mapper == 0 && rom.prgBancs == 2 && adr(rom) >= 0
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.ram[0x0770] = 1                                       // OperMode: el so en marxa
        if (id <= 7) m.ram[0xFB] = 1 shl (id - 1) else m.ram[0xFC] = 1 shl (id - 8)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(adr(rom)) }
}

/**
 * The Legend of Zelda (NES, MMC1). De aldonunez/zelda1-disassembly: `DriveAudio` (al banc 0 de PRG, es crida a cada fotograma des del banc fix) es troba per la seva
 * seqüència de bytes (`lda Paused ($E0); beq; lda #0; sta $4015; lda #$0F; sta $4015; bne; lda #$FF; sta $4017`). La cançó es demana a `SongRequest` ($0600), que el
 * motor posa a 0 en llegir-la: $80 títol, $01 món exterior, $40 mazmorres, $20 nivell 9, $02 Ganon, $04 final de nivell, $06 fanfàrria de Zelda, $10 final del joc.
 * Comprovat al harness amb Mega Man 3 USA i Europa (PRG1): sonen els 57 ids provats.
 */
object DriverZelda1 : NesDriver {
    override val nom = "Zelda1"
    override val cancons = listOf("Món exterior", "Mazmorres", "Nivell 9", "Ganon", "Final de nivell", "Fanfàrria de Zelda", "Títol", "Final del joc")
    private val VALORS = intArrayOf(0x01, 0x40, 0x20, 0x02, 0x04, 0x06, 0x80, 0x10)
    private val SIG = intArrayOf(0xA5, 0xE0, 0xF0, 0x0C, 0xA9, 0x00, 0x8D, 0x15, 0x40, 0xA9, 0x0F, 0x8D, 0x15, 0x40, 0xD0, 0x11, 0xA9, 0xFF, 0x8D, 0x17, 0x40)
    override fun reconeix(rom: NesRom) = rom.mapper == 1 && rom.cerca(SIG) >= 0
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) { m.ram[0x600] = VALORS[(id - 1).coerceIn(0, VALORS.size - 1)] }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) {
        val o = rom.cerca(SIG)
        m.triaBanc(o / 0x4000); cpu.sp = 0xFD; cpu.crida(0x8000 + (o % 0x4000))
    }
}

/**
 * Metroid (NES, MMC1). De metroidret/m1disasm (porta l'adreça de cada rutina als comentaris): `SoundEngine` ($B3B4; cada banc d'àrea en porta una còpia i la NMI la crida
 * sense canviar de banc) es troba per la seva seqüència de bytes (`lda #$C0; sta $4017; lda $0680; lsr; bcs`). La música es demana fent un OR a `MusicInitFlags`
 * ($0685): Brinstar $01, Mother Brain $02, fugida $04, Norfair $08, Kraid $10, sala d'objectes $20, Tourian $40, Ridley $80. Comprovat al harness amb Mega Man 3 USA i Europa (PRG1): sonen els 57 ids provats.
 */
object DriverMetroid1 : NesDriver {
    override val nom = "Metroid"
    override val cancons = listOf("Brinstar", "Mother Brain", "Fugida", "Norfair", "Kraid", "Sala d'objectes", "Tourian", "Ridley")
    private val SIG = intArrayOf(0xA9, 0xC0, 0x8D, 0x17, 0x40, 0xAD, 0x80, 0x06, 0x4A, 0xB0)
    override fun reconeix(rom: NesRom) = rom.mapper == 1 && rom.cerca(SIG) >= 0
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) { m.ram[0x685] = m.ram[0x685] or (1 shl (id - 1)) }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) {
        val o = rom.cerca(SIG)
        m.triaBanc(o / 0x4000); cpu.sp = 0xFD; cpu.crida(0x8000 + (o % 0x4000))
    }
}

/**
 * Contra (NES, UNROM). De vermiceli/nes-contra-us (amb la taula de codis de so a `docs/Sound Documentation.md`): el so és al banc 1 de PRG; a cada NMI el joc el posa a
 * $8000 i crida `handle_sound_slots`; una cançó es demana amb `init_sound_code_vars` (A = codi de so; una sola crida inicia els 4 canals de la cançó). Les dues rutines
 * es troben per la seva seqüència de bytes. Codis de música: $26 títol, $2A jungla/hangar, $2E cascada, $32 neu, $36 zona d'energia, $3A cau dels aliens, $3E base,
 * $42 cap, $46 final de nivell, $4A crèdits, $4E game over. Comprovat al harness amb Mega Man 3 USA i Europa (PRG1): sonen els 57 ids provats.
 */
object DriverContra : NesDriver {
    override val nom = "Contra"
    override val cancons = listOf("Títol", "Jungla / hangar", "Cascada", "Camp de neu", "Zona d'energia", "Cau dels aliens", "Base", "Cap de la base", "Final de nivell", "Crèdits", "Game over")
    private val CODIS = intArrayOf(0x26, 0x2A, 0x2E, 0x32, 0x36, 0x3A, 0x3E, 0x42, 0x46, 0x4A, 0x4E)
    private val SIG_SLOTS = intArrayOf(0x20, -1, -1, 0xA2, 0x00, 0xA0, 0x00, 0x8E, 0x20, 0x01, 0x8C, 0x23, 0x01, 0xBD, 0x06, 0x01, 0xF0, -1, 0xA8, 0x20)
    private val SIG_INIT = intArrayOf(0x8D, 0x22, 0x01, 0xC9, 0x01, 0xD0, 0x03, 0x4C, -1, -1, 0xC9, 0x5A, 0x90, 0x03, 0x4C)
    override fun reconeix(rom: NesRom) = rom.mapper == 2 && rom.cerca(SIG_SLOTS) >= 0 && rom.cerca(SIG_INIT) >= 0
    private fun adr(o: Int) = 0x8000 + (o % 0x4000)
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        val o = rom.cerca(SIG_INIT)
        m.triaBanc(o / 0x4000); cpu.sp = 0xFD; cpu.a = CODIS[(id - 1).coerceIn(0, CODIS.size - 1)]
        cpu.crida(adr(o))
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) {
        val o = rom.cerca(SIG_SLOTS)
        m.triaBanc(o / 0x4000); cpu.sp = 0xFD; cpu.crida(adr(o))
    }
}

/** Mighty Final Fight (USA i Europa) fa servir el motor del Mega Man 3 amb altres cançons; l'autor va confirmar que la versió USA sona bé (2026-11-14: «funciona, però és Mighty Final Fight»). */
val CRC_MIGHTY_FINAL_FIGHT = setOf(0x84C1E146L, 0x5CC018A8L)
object DriverMightyFinalFight : NesDriver {
    override val nom = "MightyFinalFight"
    override val cancons = DriverMegaMan3.cancons
    override fun reconeix(rom: NesRom) = rom.mapper == 4 && DriverMegaMan3.banc(rom) >= 0 && rom.crc32 in CRC_MIGHTY_FINAL_FIGHT
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) = DriverMegaMan3.iniciaAmb(cpu, m, rom, id)
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) = DriverMegaMan3.frame(cpu, m, rom, n)
}

/**
 * Mega Man 3 (NES, MMC3). De Raidenthequick/megaman3-disassembly (amb totes les adreces): el driver de so és als bancs $16 ($8000) i $17 ($A000) de PRG de 8 KB. El joc
 * (`play_sounds`, al banc fix) hi crida `$8003` amb A = identificador de so (0 a $38) per a cada so de la cua i `$8000` a cada fotograma; el driver es troba per la seva
 * seqüència d'entrada (`jmp $806C; jmp $80FE; lda #0; sta $C2; ldy #8`). Els identificadors inclouen música i efectes (no s'han separat). Comprovat al harness amb Mega Man 3 USA i Europa (PRG1): sonen els 57 ids provats.
 */
object DriverMegaMan3 : NesDriver {
    override val nom = "MegaMan3"
    override val cancons = (1..57).map { "So $it" }
    private val SIG = intArrayOf(0x4C, 0x6C, 0x80, 0x4C, 0xFE, 0x80, 0xA9, 0x00, 0x85, 0xC2, 0xA0, 0x08)
    /**
     * Banc de 8 KB del motor (visible a $8000; les dades són al següent). El desassemblat el posa al banc $16; es busca la seva entrada (`jmp $806C; jmp ????; lda #0; sta $C2; ldy #8`, amb el
     * segon salt lliure (adreça de 16 bits): a Mega Man 3 Europa (PRG1) és `jmp $8116`, no `$80FE`) i, si n'hi ha diversos, hi guanya el $16: a la ROM europea hi ha una còpia vella al banc 8 que no sona.
     */
    fun banc(rom: NesRom): Int {
        fun coincideix(b: Int): Boolean { val o = b * 0x2000; if (o + SIG.size > rom.prg.size) return false; for (i in SIG.indices) { if (i == 4 || i == 5) continue; if ((rom.prg[o + i].toInt() and 255) != SIG[i]) return false }; return true }
        if (coincideix(0x16)) return 0x16
        for (b in 0 until rom.prgBancs * 2) if (coincideix(b)) return b
        return -1
    }
    override fun reconeix(rom: NesRom): Boolean = rom.mapper == 4 && banc(rom) >= 0 && rom.crc32 !in CRC_MIGHTY_FINAL_FIGHT
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) = iniciaAmb(cpu, m, rom, id)
    fun iniciaAmb(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        val b = banc(rom)
        m.triaMmc3(b, b + 1); cpu.sp = 0xFD; cpu.a = (id - 1).coerceIn(0, 0x38)
        cpu.crida(0x8003)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) {
        val b = banc(rom)
        m.triaMmc3(b, b + 1); cpu.sp = 0xFD; cpu.crida(0x8000)
    }
}

/**
 * Final Fantasy (NES, MMC1, 256 KB). De Disch/FF1Disassembly (el seu motor és al banc $0D, que es veu a $8000): `MusicPlay` ($B099, s'ha de cridar a cada fotograma) i
 * `Music_NewSong` ($B003). Es reconeix per la taula de freqüències `lut_NoteFreqs` ($B2F9 del banc $0D, o sigui el PRG $372F9). La música es demana escrivint a
 * `music_track` ($4B) el número de cançó amb el bit 6 posat ($41 = cançó 1...); la taula de cançons ($8000 del banc $0D) en té 24 (8 bytes per cançó: punters de
 * quadrada 1, quadrada 2 i triangle). **Només s'hi dona nom a les que el desassemblatge anomena als comentaris.** Verificat amb una ROM assemblada del propi desassemblatge.
 */
object DriverFf1 : NesDriver {
    override val nom = "FF1"
    override val cancons = listOf("Cristall", "Escena del pont", "Epíleg", "Món exterior", "Vaixell", "Aeronau", "So 7", "So 8", "So 9", "So 10", "So 11", "So 12", "So 13", "So 14", "Botiga", "So 16", "Menú", "Game over", "Fanfàrria", "Fanfàrria especial", "Cristall", "Jingle de desar", "Jingle de PV", "Cofre del tresor")
    private val SIG = intArrayOf(0x57, 0x03, 0x27, 0x03, 0xFA, 0x02, 0xCF, 0x02, 0xA7, 0x02, 0x81, 0x02, 0x5D, 0x02, 0x3B, 0x02, 0x1B, 0x02, 0xFC, 0x01, 0xE0, 0x01, 0xC5, 0x01, 0xAB, 0x01, 0x93, 0x01, 0x7D, 0x01, 0x67, 0x01, 0x53, 0x01, 0x40, 0x01, 0x2E, 0x01, 0x1D, 0x01, 0x0D, 0x01, 0xFE, 0x00, 0xF0, 0x00, 0xE2, 0x00, 0xD6, 0x00, 0xCA, 0x00, 0xBE, 0x00, 0xB4, 0x00, 0xAA, 0x00, 0xA0, 0x00, 0x97, 0x00, 0x8F, 0x00, 0x87, 0x00, 0x7F, 0x00, 0x78, 0x00, 0x71, 0x00, 0x6B, 0x00, 0x65, 0x00, 0x5F, 0x00, 0x5A, 0x00, 0x55, 0x00, 0x50, 0x00, 0x4C, 0x00, 0x47, 0x00, 0x43, 0x00, 0x40, 0x00, 0x3C, 0x00, 0x39, 0x00)
    override fun reconeix(rom: NesRom) = rom.mapper == 1 && rom.prgBancs == 16 && rom.cerca(SIG) == 0x372F9
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) { m.triaBanc(0x0D); m.ram[0x4B] = 0x40 or ((id - 1).coerceIn(0, 23) + 1) }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaBanc(0x0D); cpu.sp = 0xFD; cpu.crida(0xB099) }
}

/** Els dels lots nous van primer: són més específics (p. ex. Mega Man 4-6 comparteixen l'entrada del motor amb Mega Man 3, que no els ha de quedar). */
internal val DRIVERS_NES_CUSTOM: List<NesDriver> = DRIVERS_NES_R2NA + DRIVERS_NES_N2 + DRIVERS_NES_N3 + DRIVERS_NES_N1 + listOf(DriverSmb, DriverZelda1, DriverMetroid1, DriverContra, DriverMightyFinalFight, DriverMegaMan3, DriverFf1)

/**
 * Sintetitzador comú de les cançons de NES (l'autor, 2026-11-23): l'APU (`NesApu`), la sortida, el ritme i el que veu la interfície. Els fotogrames vénen de dues fonts:
 * el driver del joc executat amb la CPU (`SinteNesRom`) o una partitura gravada (`SinteNesPart`, sense CPU ni driver). L'APU és la mateixa, el so és idèntic.
 */
abstract class SinteNesBase(protected val rom: NesRom, private val muts: BooleanArray, solo0: Int) : Sintetitzador {
    override val taux = RadioFreq.taux
    @Volatile override var solo = solo0
    protected var apu: NesApu? = null
    protected val mem = NesRomMem(rom) { apu }
    private val sortida = SortidaNes(taux)
    protected val spf: Double get() = taux.toDouble() / RegioVideo.fpsNes() / ritmeV          // segons la regió de vídeo triada (RegioVideo)
    private var restaFot = 0.0
    protected var fots = 0L
    private var ultimaNota = 0L
    private var notaVista = 0
    private val mut = BooleanArray(5)
    override val nivells: FloatArray? get() = apu?.nivells
    override val oscil: OscDades? get() = apu?.oscil
    override fun notesMidi(out: FloatArray): Boolean { val a = apu ?: return false; a.notesMidi(out); return true }          // notes exactes dels registres
    /** Pols, bucle i durada de la cançó (de l'anàlisi en silenci o de la partitura). */
    abstract val an: AnalisiRitme
    private var posV = PosRitme(AnalisiRitme.BUIDA)
    private val pos: PosRitme get() { val a = an; if (posV.a !== a) posV = PosRitme(a); return posV }
    @Volatile private var ritmeV = 1.0
    init { apu = NesApu(taux) { mem.rb(it) } }

    /** Un fotograma de la cançó: el driver (o la partitura) escriu als registres de l'APU. */
    protected abstract fun executaFrame()
    /** Executa un fotograma sense generar so (per gravar partitures). */
    fun avancaFotograma() { executaFrame(); fots++ }

    override fun pistaActiva(i: Int) = i < 5 && apu?.actiu?.get(i) == true
    /** Fotogrames de la cançó reproduïts (amb decimals). */
    private val posFots: Double get() = if (fots == 0L) 0.0 else fots - 1 + (1.0 - (restaFot / spf).coerceIn(0.0, 1.0))
    override val voltes: Int get() = pos.voltes(posFots)
    override val bpm: Double get() = pos.bpm(RegioVideo.fpsNes())
    override val batec: Double get() = pos.batec(posFots)
    override val batecMax: Double get() = pos.batecMax
    override var ritme: Double get() = ritmeV; set(v) { ritmeV = v.coerceIn(0.25, 4.0) }
    override val tempoPropi: Boolean get() = true
    override val teBucle: Boolean? get() = !an.acaba
    override val acabada: Boolean get() = fots > 180 && fots - ultimaNota > 600
    /** Histograma de les 12 notes de la cançó fins al primer bucle (de l'anàlisi); deixa aquest sintetitzador al principi del bucle amb una volta feta. */
    override fun analitza(maxSegons: Int): DoubleArray? { pos.salt = pos.saltAlBucle() - posFots; return an.hist }

    override fun renderitza(out: ShortArray, frames: Int) {
        val ap = apu ?: return
        var f = 0
        while (f < frames) {
            if (restaFot <= 0.0) {
                executaFrame(); fots++; restaFot += spf
                if (ap.nota != notaVista) { notaVista = ap.nota; ultimaNota = fots }
            }
            val n = minOf(frames - f, Math.ceil(restaFot).toInt().coerceAtLeast(1))
            for (i in 0 until n) {
                for (k in 0 until 5) mut[k] = muts[k] || (solo >= 0 && solo != k)
                val s = sortida.f(ap.mostra(mut))
                out[(f + i) * 2] = s; out[(f + i) * 2 + 1] = s
            }
            restaFot -= n
            f += n
        }
    }
}

class SinteNesRom(rom: NesRom, private val drv: NesDriver, id: Int, muts: BooleanArray, solo0: Int = -1) : SinteNesBase(rom, muts, solo0) {
    private val cpu = Cpu6502(mem)
    // Pols, bucle i durada: s'analitzen una sola vegada per cançó (memòria cau) en una passada en silenci
    private val clauAn = RitmeRom.clauRom(rom.prg) + "|" + drv.nom + "|" + id
    private val idCan = id
    override val an: AnalisiRitme get() = RitmeRom.cauOpt(clauAn) ?: AnalisiRitme.BUIDA
    override fun preparaMesclador() { RitmeRom.obte(clauAn) { analisiNes(rom, drv, idCan) } }
    /** L'anàlisi (bucle i durada), esperant-la si cal (per als analitzadors). */
    fun analisiCompleta(): AnalisiRitme = RitmeRom.obte(clauAn) { analisiNes(rom, drv, idCan) }
    // L'anàlisi (fins a uns quants segons, més al mòbil) es fa en un fil a part perquè la cançó soni a l'instant; mentre no és a punt, `an` és buida i el mesclador l'espera amb `preparaMesclador`.
    init { if (!SOLS_GRAVAR.get()) Thread { RitmeRom.obte(clauAn) { analisiNes(rom, drv, idCan) } }.also { it.isDaemon = true }.start() }

    // Gravador: escriptures als registres de l'APU (i al so de l'VRC6) en ordre, més l'estat dels bancs de la ROM (que només importa per a la lectura de la DMC)
    private var esc = IntArray(256); private var nEsc = 0
    private fun anota(a: Int, v: Int) { if (nEsc == esc.size) esc = esc.copyOf(nEsc * 2); esc[nEsc++] = (a shl 8) or (v and 255) }
    private var gravant = false

    init {
        mem.alRegistre = { a, v -> if (gravant) anota(a, v) }
        gravant = true
        apu?.escriu(0x4015, 0x0F); anota(0x4015, 0x0F)
        cpu.sp = 0xFD
        drv.inicia(cpu, mem, rom, id)
        gravant = false
    }

    override fun executaFrame() { drv.frame(cpu, mem, rom, fots) }

    /**
     * Grava la cançó (per als analitzadors): executa els fotogrames sense so i guarda les escriptures a l'APU; l'anàlisi (bucle, pols, durada) diu quants calen
     * (fins a la fi de la primera volta del bucle, o fins a la fi de la cançó). Els bancs es graven sencers al fotograma 0, a l'inici del bucle i quan canvien.
     */
    fun grava(driverId: String, id: Int, maxFots: Int = 240 * 60): CancoPart {
        val an = analisiCompleta()
        val g = GrabaCanco(driverId, id)
        val fins = (if (an.teBucle) Math.ceil(an.ini + an.llarg).toInt() else if (an.acaba && an.fi > 0) an.fi else maxFots).coerceIn(1, maxFots)
        val iniBucle = if (an.teBucle) an.ini.toInt() else -1
        val inici = NesRomMem(rom) { null }.copiaBancs()
        var ant = inici
        val fps = RegioVideo.fpsNes()
        for (f in 0 until fins) {
            val primer = f == 0
            if (primer) { g.iniciEscriptures(IntArray(nEsc) { esc[it] }, nEsc); nEsc = 0 }
            gravant = true
            avancaFotograma()
            gravant = false
            val b = mem.copiaBancs()
            if (primer || f == iniBucle || !b.contentEquals(ant)) for (i in b.indices) { require(b[i] in 0..255); anota(BANC_ADR + i, b[i]) }
            ant = b
            val cic = IntArray(nEsc)
            g.fotograma(fps, cic, esc, nEsc)
            nEsc = 0
        }
        return g.fes(fins, an)
    }
    companion object {
        /** Els analitzadors graven sense engegar el fil d'anàlisi de fons de la reproducció. */
        val SOLS_GRAVAR = java.util.concurrent.atomic.AtomicBoolean(false)
        /** Les escriptures amb adreça `BANC_ADR + i` no són a l'APU: posen el registre de banc `i` de la ROM (`NesRomMem.copiaBancs`). */
        const val BANC_ADR = 0x10000
    }
}

/** Cançó de NES reproduïda des d'una partitura gravada: sense CPU ni driver; només l'APU. La ROM només serveix per desxifrar la partitura i per a la lectura de la DMC. */
class SinteNesPart(rom: NesRom, private val c: CancoPart, muts: BooleanArray, solo0: Int = -1) : SinteNesBase(rom, muts, solo0) {
    override val an: AnalisiRitme get() = c.an
    private val bancs = mem.copiaBancs()
    private var b6 = 0
    private fun aplica(g: Int) {
        var canviBancs = false
        for (k in c.iniFot[g] until c.iniFot[g + 1]) {
            val a = c.adr[k]; val v = c.val_[k]
            if (a == 0x20000) mem.triaBanc(v) else if (a == 0x20001) b6 = v else if (a == 0x20002) mem.triaMmc3(b6, v)
            else if (a >= SinteNesRom.BANC_ADR) { bancs[a - SinteNesRom.BANC_ADR] = c.val_[k]; canviBancs = true } else mem.wb(a, c.val_[k])
        }
        if (canviBancs) mem.posaBancs(bancs)
    }
    override fun executaFrame() {
        val g = c.fotGravat(fots)
        if (g >= 0) aplica(g)
    }
}

/** Passada en silenci d'una cançó de NES: es fa córrer el driver amb una APU a mostreig baix (perquè els comptadors de durada avancin) i es registren les escriptures a $4000-$4017. */
private fun analisiNes(rom: NesRom, drv: NesDriver, id: Int): AnalisiRitme {
    val taux = 7200
    var apu: NesApu? = null
    val mem = NesRomMem(rom) { apu }
    val fps = 60.0988
    val reg = RegSo((240 * fps).toInt())
    val sh = IntArray(0x20)
    val cpu = Cpu6502(object : Bus6502 { override fun rb(a: Int) = mem.rb(a); override fun wb(a: Int, v: Int) {
        if (a in 0x4000..0x4017) {
            reg.reg(a, v); sh[a - 0x4000] = v
            when (a) {
                0x4003, 0x4007 -> reg.nota((a - 0x4000) shr 2, RitmeRom.midi(1789773.0 / (16.0 * ((sh[a - 0x4001] or ((v and 7) shl 8)) + 1))), sh[a - 0x4001] or ((v and 7) shl 8))
                0x400B -> reg.nota(2, RitmeRom.midi(1789773.0 / (32.0 * ((sh[0xA] or ((v and 7) shl 8)) + 1))), sh[0xA] or ((v and 7) shl 8))
                0x400F -> reg.nota(3, Double.NaN, sh[0xE])
            }
        }
        mem.wb(a, v)
    } })
    val ap = NesApu(taux) { mem.rb(it) }; apu = ap
    ap.escriu(0x4015, 0x0F)
    cpu.sp = 0xFD
    drv.inicia(cpu, mem, rom, id)
    val sil = BooleanArray(5) { true }
    val spf = taux / fps
    var rest = 0.0
    return RitmeRom.analitza(reg, fps, 3000L) { f ->
        drv.frame(cpu, mem, rom, f.toLong())
        rest += spf
        while (rest >= 1.0) { ap.mostra(sil); rest -= 1.0 }
    }
}

/** Tots els drivers (fets a mà, experimentals i automàtics), construïts. Compatibilitat amb el codi d'abans: el codi nou fa servir `RegistreDrivers` (a demanda, per CRC). */
val DRIVERS_NES: List<NesDriver> get() = RegistreDrivers.llistaNes()
