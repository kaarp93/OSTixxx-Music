package cat.gbdsmixxx

/*
 * Lot S, part 4 (experimental): Star Fox (SNES, LoROM, Super FX). Font: sf_spy = SpyderTL/StarFoxDisassembly, bank03.asm: `AudioPacketData` (taula d'entrades: ordre de música + paquets de punter 24 bits i mida;
 * zero de 3 bytes = final), rutina `LoadAudio` (protocol IPL: cada paquet és una cadena de blocs mida, destí, dades amb un bloc final de mida 0; en acabar, entrada a $0400) i `UpdateMusic` a bank02.asm
 * (escriu l'ordre al port 0 fins que l'SPC la repeteix i després posa 0). El motor és el primer paquet (entrada 0, SGSOUND0.BIN, $18:8000, $28C2 bytes). La taula es busca a la ROM pel contingut
 * de les dues primeres entrades. FIDEL: protocol i formats. DEDUÏT: que reescriure les dades i tornar a arrencar a $0400 equival al canvi de cançó real (el joc reentra a l'IPL amb l'ordre $FF);
 * versions de ROM diferents de la 1.2 USA podrien tenir una taula diferent. No comprovat amb cap ROM.
 */
object DriverStarFox : SnesDriver {
    override val nom = "StarFox"
    private val NOMS = listOf(
        "Initialization Data · ordre \$00 · taula entrada 0 · paquets \$18:8000(10434)",
        "Intro · ordre \$12 · taula entrada 1 · paquets \$18:A8C2(29644), \$19:9C8E(13684), \$0C:FA33(1372)",
        "Title · ordre \$12 · taula entrada 2 · paquets \$1B:B5BE(23015)",
        "Controls · ordre \$12 · taula entrada 3 · paquets \$18:A8C2(29644), \$0F:FBCD(1054)",
        "Training · ordre \$03 · taula entrada 4 · paquets \$19:D202(11696), \$1E:B945(2035)",
        "Map · ordre \$01 · taula entrada 5 · paquets \$19:9C8E(13684), \$1B:A6A2(3868)",
        "Continue · ordre \$0A · taula entrada 6 · paquets \$1D:9575(4478)",
        "Black Hole · ordre \$03 · taula entrada 7 · paquets \$0A:FC71(424)",
        "Scramble 1 · ordre \$10 · taula entrada 8 · paquets \$1E:94BA(2983), \$1F:8E77(1482), \$1A:927E(37924)",
        "Corneria 1 · ordre \$03 · taula entrada 9 · paquets \$18:A8C2(29644), \$19:D202(11696), \$1E:94BA(2983), \$1F:8E77(1482)",
        "Asteroid 1 · ordre \$03 · taula entrada 10 · paquets \$1E:CDC1(3138), \$11:FB89(683)",
        "Space Armada (Blast In · ordre \$09 · taula entrada 11 · paquets \$1E:DA03(1715), \$1F:9F4A(1086)",
        "Space Armada · ordre \$03 · taula entrada 12 · paquets \$1E:DA03(1715), \$1F:9F4A(1086)",
        "Meteor · ordre \$03 · taula entrada 13 · paquets \$19:D202(11696), \$19:FFB2(4812), \$1E:A061(3262), \$1F:9A38(1298)",
        "Venom 1 Atmosphere · ordre \$03 · taula entrada 14 · paquets \$1E:CDC1(3138), \$1F:A388(1019)",
        "Venom 1 · ordre \$03 · taula entrada 15 · paquets \$1E:F144(1794), \$1F:A388(1019), \$1F:AFB0(2101)",
        "Scramble 2 · ordre \$10 · taula entrada 16 · paquets \$1E:94BA(2983), \$1F:8E77(1482), \$1A:927E(37924)",
        "Silence 2 · ordre \$03 · taula entrada 17 · paquets \$18:A8C2(29644), \$19:D202(11696)",
        "Sector X · ordre \$03 · taula entrada 18 · paquets \$1E:EBAC(1432), \$11:FB89(683)",
        "Titania · ordre \$03 · taula entrada 19 · paquets \$19:D202(11696), \$19:FFB2(4812), \$1E:AD1F(3110), \$1F:9441(1527)",
        "Sector Y · ordre \$03 · taula entrada 20 · paquets \$10:F879(1866), \$11:FB89(683)",
        "Venom 2 Atmosphere · ordre \$03 · taula entrada 21 · paquets \$1E:CDC1(3138), \$1F:A388(1019)",
        "Highway · ordre \$03 · taula entrada 22 · paquets \$1E:E0B6(2806), \$1F:A388(1019), \$1F:AFB0(2101)",
        "Scramble 3 · ordre \$10 · taula entrada 23 · paquets \$1E:94BA(2983), \$1F:8E77(1482), \$1A:927E(37924)",
        "Silence 3 · ordre \$03 · taula entrada 24 · paquets \$18:A8C2(29644), \$19:D202(11696)",
        "Asteroid 3 · ordre \$03 · taula entrada 25 · paquets \$1E:CDC1(3138), \$11:FB89(683)",
        "Fortuna · ordre \$03 · taula entrada 26 · paquets \$19:D202(11696), \$19:FFB2(4812), \$08:F2B6(2302), \$0E:FC62(894)",
        "Sector Z · ordre \$03 · taula entrada 27 · paquets \$1E:EBAC(1432), \$1F:9F4A(1086)",
        "Macbeth · ordre \$03 · taula entrada 28 · paquets \$19:D202(11696), \$1E:C138(3209), \$11:FE34(453)",
        "Venom 3 Atmosphere · ordre \$03 · taula entrada 29 · paquets \$1E:CDC1(3138), \$1F:A783(2093)",
        "Venom 3 · ordre \$03 · taula entrada 30 · paquets \$1E:F144(1794), \$1F:A783(2093), \$1F:AFB0(2101)",
        "Ending · ordre \$03 · taula entrada 31 · paquets \$1C:8FA5(34256)",
        "Credits · ordre \$12 · taula entrada 32 · paquets \$1D:A6F3(28103)",
        "Game Over · ordre \$0C · taula entrada 33 · paquets \$09:F8D0(301)",
        "Out Of This Dimension · ordre \$03 · taula entrada 34 · paquets \$19:D202(11696), \$1E:F846(5681)",
        "Boss Tunnel · ordre \$12 · taula entrada 35 · paquets \$09:F8D0(301)")
    override val cancons: List<String> = NOMS.drop(1)
    // entrada 0 de la taula (motor) + entrada 1 (Intro) com a signatura
    private val SIG = intArrayOf(0x00, 0x00, 0x80, 0x18, 0xC2, 0x28, 0x00, 0x00, 0x00, 0x12, 0xC2, 0xA8, 0x18, 0xCC, 0x73)
    override fun reconeix(rom: RomSnes): Boolean = !rom.hiRom && rom.d.size >= 0x100000 && (rom.titol.uppercase().contains("STAR FOX") || rom.titol.uppercase().contains("STARWING")) && rom.cerca(SIG) >= 0
    private fun fitxerDe(rom: RomSnes, banc: Int, adr: Int) = rom.fitxer(banc, adr)
    /** Posició al fitxer de l'entrada `k` de la taula (cada entrada: ordre, paquets de 5 bytes, 0 0 0). */
    private fun entrada(rom: RomSnes, k: Int): Int {
        var o = rom.cerca(SIG)
        var i = 0
        while (i < k && o in 0 until rom.d.size - 12) {
            o += 1
            while (o + 2 < rom.d.size && !(rom.d[o].toInt() == 0 && rom.d[o + 1].toInt() == 0 && rom.d[o + 2].toInt() == 0)) o += 5
            o += 3; i++
        }
        return o
    }
    private fun copia(spc: SnesSpc, rom: RomSnes, banc0: Int, adr0: Int) {
        // cadena de blocs [mida][destí][dades]; mida 0 = final del paquet; l'adreça segueix al banc següent com el punter del joc (Y -> $8000, banc + 1)
        var banc = banc0; var y = adr0
        fun b(): Int { val v = rom.u8(banc, y); y++; if (y > 0xFFFF) { y = 0x8000; banc++ }; return v }
        var guard = 0
        while (guard++ < 4096) {
            val len = b() or (b() shl 8)
            if (len == 0) return
            val dest = b() or (b() shl 8)
            for (k in 0 until len) spc.ram[(dest + k) and 0xFFFF] = b().toByte()
        }
    }
    private var ordre = 0
    private var estat = 0
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val k = id.coerceIn(1, NOMS.size - 1)
        estat = 0
        for (e in intArrayOf(0, k)) {
            var o = entrada(rom, e)
            if (o < 0) continue
            if (e == k) ordre = rom.d[o].toInt() and 255
            o += 1
            var guard = 0
            while (o + 5 < rom.d.size && guard++ < 16 && !(rom.d[o].toInt() == 0 && rom.d[o + 1].toInt() == 0 && rom.d[o + 2].toInt() == 0)) {
                val adr = (rom.d[o].toInt() and 255) or ((rom.d[o + 1].toInt() and 255) shl 8)
                val banc = rom.d[o + 2].toInt() and 255
                copia(spc, rom, banc, adr)
                o += 5
            }
        }
        spc.arrenca(0x0400)
    }
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {
        if (n == 0L) estat = 0
        if (n < 40) return
        when (estat) {
            0 -> { spc.escriuPort(0, ordre); estat = 1 }
            1 -> if (spc.llegeixPort(0) == ordre) { spc.escriuPort(0, 0); estat = 2 } else if (n > 600) { spc.escriuPort(0, 0); estat = 2 }
        }
    }
}

val EXP_SNES_S4: List<SnesDriver> = listOf(DriverStarFox)
val EXP_COMPAT_S4: List<JocCompat> = listOf(JocCompat("Star Fox", "SNES", "SNES_StarFox"))
