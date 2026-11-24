package cat.gbdsmixxx

/*
 * Lot S4 de jocs de SNES amb driver (motor de so de Capcom executat a l'SPC700).
 *  - Mega Man 7: del desassemblatge (Yoshifanatic1/Mega-Man-7-Disassembly): taula de fitxers de so a $C5:0000, carregador a $C0:3015, selecció de banc a $C0:30C5.
 *  - Mega Man X: de les proves del recompilador (mstan/MegaManXSNESRecomp): taula de bancs a $89:8000 i taules de cançó a $06:806B/$06:807B.
 */

/** Còpia a l'ARAM dels blocs «mida, adreça, dades» d'un fitxer de so (format de l'IPL); retorna l'adreça d'entrada del final, o -1 si el format no quadra. */
private fun s4Blocs(spc: SnesSpc, d: ByteArray, inici: Int): Int {
    var p = inici
    fun u16(o: Int) = (d[o].toInt() and 255) or ((d[o + 1].toInt() and 255) shl 8)
    var blocs = 0
    while (p >= 0 && p + 4 <= d.size && blocs < 256) {
        val n = u16(p); val a = u16(p + 2); p += 4
        if (n == 0) return a
        if (p + n > d.size) return -1
        for (k in 0 until n) spc.ram[(a + k) and 0xFFFF] = d[p + k]
        p += n; blocs++
    }
    return -1
}

/** Mega Man 7 (USA, HiROM 2 MB): 37 cançons (ordres $11-$35 del joc, com les demana el carregador de $C0:30C5). */
object DriverMegaMan7 : SnesDriver {
    override val nom = "MegaMan7"
    override val cancons: List<String> = List(37) { "So ${it + 1}" }
    private const val BANC = 0xC5
    override fun reconeix(rom: RomSnes): Boolean =
        rom.hiRom && rom.d.size >= 0x200000 && rom.titol.startsWith("MEGAMAN 7") &&
            rom.u24(BANC, 0) == 0xEA && rom.u24(BANC, 3) == 0x0BEA && rom.u24(BANC, 6) == 0x292D && rom.u24(BANC, 17 * 3) == 0x979E
    /** Posició al fitxer del fitxer de so número `i` de la taula de $C5:0000 (0 = motor, 1 i 2 = dades comunes, $11-$35 = bancs de cançó, $40-$4D = jocs d'instruments), o -1 si està buit. */
    private fun fitxer(rom: RomSnes, i: Int): Int {
        val o = rom.u24(BANC, i * 3)
        return if (i > 0 && o == 0) -1 else rom.fitxer(BANC, 0) + o
    }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        // $C0:0568-0577 (arrencada) carrega els fitxers 0 (motor), 1 i 2; $C0:30C5 després carrega els extres i el banc de la cançó.
        val entrada = s4Blocs(spc, rom.d, fitxer(rom, 0))
        s4Blocs(spc, rom.d, fitxer(rom, 1)); s4Blocs(spc, rom.d, fitxer(rom, 2))
        // $80:9221 (u16 de la cançó $10+k) apunta a un registre de $80:9231-$10: [banc, extres..., $FF]
        val rec = 0x9221 + rom.u8(0x80, 0x9221 + id.coerceIn(1, 37))
        var j = 1
        while (j < 16 && rom.u8(0x80, rec + j) != 0xFF) { s4Blocs(spc, rom.d, fitxer(rom, rom.u8(0x80, rec + j))); j++ }
        val banc = rom.u8(0x80, rec)
        if (banc != 0) s4Blocs(spc, rom.d, fitxer(rom, banc))
        spc.arrenca(if (entrada >= 0) entrada else 0x02C0)
    }
    private var enviat = false
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {
        if (n == 0L) enviat = false
        // El motor posa $F6 = 1 quan ha acabat d'arrencar; llavors l'ordre $FB (CODE_05A9) engega la cançó del banc carregat.
        if (!enviat && (n >= 3 && spc.llegeixPort(2) == 1 || n >= 40)) { spc.escriuPort(0, 0xFB); spc.escriuPort(1, 0); enviat = true }
    }
}

/** Mega Man X (USA, LoROM 1 MB): 33 cançons, ordres $10-$30 del joc. */
object DriverMegaManX : SnesDriver {
    override val nom = "MegaManX"
    // Ordres de música que el joc passa a $80:87A2 (LDA #ordre / JSL; ordres $1A-$30 a la ROM USA, comprovat)
    private val ORDRES = intArrayOf(0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x21, 0x23, 0x24, 0x25, 0x26, 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2E, 0x2F, 0x30)
    override val cancons: List<String> = List(ORDRES.size) { "Ordre \$%02X".format(ORDRES[it]) }
    // $81:B617: LDA #cançó / JSL $80:87A2 (l'ordre de música del controlador d'entrada de Chill Penguin)
    private val SIG = intArrayOf(0xA9, -1, 0x22, 0xA2, 0x87, 0x80)
    override fun reconeix(rom: RomSnes): Boolean {
        if (rom.hiRom || (rom.d.size != 0x100000 && rom.d.size != 0x180000) || !rom.titol.startsWith("MEGAMAN X") || rom.titol.startsWith("MEGAMAN X2") || rom.titol.startsWith("MEGAMAN X3")) return false
        // El LDA #cançó / JSL $80:87A2 és a prop de $81:B617, però el desplaçament canvia entre revisions (a la ROM de 1,5 MB és a $B60D i $B536): es busca a $B000-$BFFF
        var ok = false
        var i = 0xB000
        while (!ok && i < 0xC000 && i + SIG.size <= rom.d.size) { ok = SIG.indices.all { k -> SIG[k] < 0 || (rom.d[i + k].toInt() and 255) == SIG[k] }; i++ }
        return ok
    }
    /** Posició al fitxer del banc de so `b` de la taula de $89:8000 (punter de 3 bytes: adreça dins de $8000 + banc relatiu a $89). */
    private fun banc(rom: RomSnes, b: Int): Int {
        val o = 0x48000 + b * 3
        if (o + 2 >= rom.d.size) return -1
        val lo = (rom.d[o].toInt() and 255) or ((rom.d[o + 1].toInt() and 255) shl 8)
        return (9 + (rom.d[o + 2].toInt() and 255)) * 0x8000 + (lo and 0x7FFF)
    }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val cmd = ORDRES[(id - 1).coerceIn(0, ORDRES.size - 1)]
        var entrada = 0x02C0
        for (b in 0..2) { val e = s4Blocs(spc, rom.d, banc(rom, b)); if (b == 0 && e >= 0) entrada = e }
        // $80:87B0: tipus = taula[$06:806B + ordre]; banc = taula[$06:807B + tipus]; la llista de bancs extra és a $06:807C + tipus fins a $FF
        // (es carreguen amb $F9) i el banc de la cançó al final (amb $FA = carrega i engega la cançó de $0DAF; el motor la llegeix de la RAM)
        val tipus = rom.d[0x3006B + cmd].toInt() and 255
        val bancCanco = rom.d[0x3007B + tipus].toInt() and 255
        var y = tipus + 1
        while (0x3007C + y < rom.d.size && (rom.d[0x3007C + y].toInt() and 255) != 0xFF) { s4Blocs(spc, rom.d, banc(rom, rom.d[0x3007C + y].toInt() and 255)); y++ }
        if (bancCanco != 0) s4Blocs(spc, rom.d, banc(rom, bancCanco))
        spc.arrenca(entrada)
    }
    private var enviat = false
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {
        if (n == 0L) enviat = false
        // El motor deixa $F6 = 1 quan és llest (prova del recompilador: outPorts[2] == 1); llavors es demana la cançó pel port 0 (port 1 = 0).
        if (!enviat && (n >= 3 && spc.llegeixPort(2) == 1 || n >= 40)) {
            spc.escriuPort(1, 0); spc.escriuPort(0, 0xFB); enviat = true
        }
    }
}

val DRIVERS_SNES_S4: List<SnesDriver> = listOf(DriverMegaMan7, DriverMegaManX)
