package cat.gbdsmixxx

/*
 * Lot SNES S1: Super Mario World, Super Mario World 2: Yoshi's Island i Super Metroid.
 * Els tres pugen a l'SPC700 blocs en el format de l'IPL (longitud, adreça SPC, dades; el bloc final de longitud 0 porta l'adreça d'execució).
 * Les adreces de la ROM surten dels desassemblatges (SMWDisX, yoshisisland-disassembly i supermetroid de strager).
 */

/** Copia a `spc.ram` els blocs en format IPL que comencen a la posició `ini` del fitxer. Retorna (posició després del terminador, adreça d'execució), o null si surt de la ROM. */
private fun s1Cadena(rom: RomSnes, spc: SnesSpc, ini: Int): Pair<Int, Int>? {
    var o = ini
    val d = rom.d
    while (true) {
        if (o < 0 || o + 4 > d.size) return null
        val len = (d[o].toInt() and 255) or ((d[o + 1].toInt() and 255) shl 8)
        val ad = (d[o + 2].toInt() and 255) or ((d[o + 3].toInt() and 255) shl 8)
        o += 4
        if (len == 0) return Pair(o, ad)
        if (o + len > d.size) return null
        for (k in 0 until len) spc.ram[(ad + k) and 0xFFFF] = d[o + k]
        o += len
    }
}

private fun s1U16(rom: RomSnes, o: Int): Int = if (o >= 0 && o + 1 < rom.d.size) (rom.d[o].toInt() and 255) or ((rom.d[o + 1].toInt() and 255) shl 8) else 0

// ---------------------------------------------------------------------------------------------------------------------------------------------------------
// Super Mario World (SMWDisX: UploadSPCEngine/UploadSamples/UploadMusicBank1/UploadLevelMusic/UploadCreditsMusic a bank_00.asm; dades a bank_0E/0F i bank_03)
// ---------------------------------------------------------------------------------------------------------------------------------------------------------

object DriverSmw : SnesDriver {
    override val nom = "SMW"
    // Banc 1 (mapa): ids 1-9; banc 2 (nivells): ids 1-29; banc 3 (crèdits): ids 9-11. Noms de les constants !BGM_* de constants.asm.
    private val b1 = listOf("Title Screen", "Donut Plains", "Yoshi's Island", "Vanilla Dome", "Star World", "Forest of Illusion", "Valley of Bowser", "Valley Opens", "Special World")
    private val b2 = listOf("Athletic", "Overworld", "Underwater", "Bowser Start", "Boss Fight", "Underground", "Ghost House", "Castle", "Death", "Game Over", "Boss Clear", "Level Clear",
        "Star Power", "P-Switch", "Keyhole", "Keyhole 2", "Spotlight", "Bonus Game", "Cutscene Full", "Bonus Over", "Cutscene Intro", "Bowser Interlude", "Bowser Zoom Out",
        "Bowser Zoom In", "Bowser Phase 2", "Bowser Phase 3", "Bowser Defeated", "Peach Saved", "Bowser Interlude 2")
    private val b3 = listOf("Staff Credits", "Credits Yoshi's House", "Credits Thank You")
    override val cancons: List<String> = b1 + b2 + b3

    private const val ENGINE = 0x0E * 0x8000      // 0E:8000 (SPC700Engine)
    private const val SAMPLES = 0x0F * 0x8000     // 0F:8000 (MusicSamples)

    override fun reconeix(rom: RomSnes): Boolean =
        !rom.hiRom && rom.d.size >= 0x80000 && rom.titol.uppercase().replace(" ", "").startsWith("SUPERMARIOWORLD") &&
            s1U16(rom, ENGINE + 2) == 0x0500 && s1U16(rom, ENGINE) > 0x100 && s1U16(rom, SAMPLES) > 0x100 && s1U16(rom, SAMPLES + 2) != 0

    /** Banc de crèdits (MusicBank3): bloc amb la mateixa adreça de dades que el banc 1, taula de 12 paraules S01..S04 repetides i terminador 0 després de les dades. */
    private fun cercaBanc3(rom: RomSnes, adrDades: Int): Int {
        val d = rom.d
        var o = 0
        while (o + 4 + 24 + 4 < d.size && o < ENGINE) {
            if (s1U16(rom, o + 2) == adrDades) {
                val len = s1U16(rom, o)
                if (len in 0x800..0x6000 && o + 4 + len + 4 <= d.size && s1U16(rom, o + 4 + len) == 0 && s1U16(rom, o + 6 + len) == 0x0500) {
                    val e = IntArray(12) { s1U16(rom, o + 4 + 2 * it) }
                    if (e[0] == e[4] && e[4] == e[8] && e[1] == e[5] && e[5] == e[9] && e[2] == e[6] && e[6] == e[10] && e[0] != e[1] && e[1] != e[2]) return o
                }
            }
            o++
        }
        return -1
    }

    private var idSo = 0
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val i = (id - 1).coerceIn(0, cancons.size - 1)
        idSo = if (i < b1.size) i + 1 else if (i < b1.size + b2.size) i - b1.size + 1 else 9 + (i - b1.size - b2.size)
        // Motor + efectes + banc 1 (el terminador és al final del banc 1), mostres, i el banc que toca si no és el 1.
        val c1 = s1Cadena(rom, spc, ENGINE) ?: return
        s1Cadena(rom, spc, SAMPLES)
        if (i >= b1.size && i < b1.size + b2.size) s1Cadena(rom, spc, c1.first)                 // MusicBank2 va just després del terminador del banc 1
        else if (i >= b1.size + b2.size) {
            val adr = s1U16(rom, ENGINE + 6)                                                      // adreça on el banc 1 posa les dades (MusicData)
            // el segon bloc del motor són els efectes; el banc 1 hi comença just després
            val o2 = ENGINE + 4 + s1U16(rom, ENGINE)
            val o3 = o2 + 4 + s1U16(rom, o2)
            val dades = s1U16(rom, o3 + 2).let { if (it != 0) it else adr }
            val o = cercaBanc3(rom, dades)
            if (o >= 0) s1Cadena(rom, spc, o)
        }
        spc.arrenca(0x0500)
    }

    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {
        if (n == 10L) spc.escriuPort(2, idSo)                                                     // $1DFB -> $2142 (SPCIO2)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------------
// Super Mario World 2: Yoshi's Island (yoshisisland-disassembly: bank00.asm SPC_ptr $0084AC, spc_data_blocks $0084E8, spc_block_set_indexes $00852F,
// pujada a SPC700Upload/upload_music_data; taula de pistes $01B24B)
// ---------------------------------------------------------------------------------------------------------------------------------------------------------

object DriverYoshiIsland : SnesDriver {
    override val nom = "YoshisIsland"
    // (valor de r_header_music, nom). Els noms són els comentaris dels blocs de música del desassemblatge; les pistes 9 i 12 són del bloc «default music track».
    private val llista = listOf(
        0 to "Grassland", 1 to "Forest/Jungle", 2 to "Castle/Fort", 3 to "X-4 Boss", 4 to "Cave", 5 to "Default Music 9 (X-4 set)", 6 to "Bonus Challenge",
        7 to "X-8 Boss", 8 to "Default Music 9 (X-8 set)", 9 to "Default Music 12", 10 to "Athletic", 11 to "Athletic 2", 12 to "Big Baby Bowser",
        14 to "Title Screen", 17 to "Intro", 18 to "Overworld", 19 to "Ending")
    override val cancons: List<String> = llista.map { it.second }

    private fun off(rom: RomSnes, banc: Int, adr: Int) = rom.fitxer(banc, adr)

    override fun reconeix(rom: RomSnes): Boolean =
        !rom.hiRom && rom.d.size >= 0x200000 && rom.titol.uppercase().startsWith("YOSHI'S ISLAND") && rom.u24(0, 0x84AC) == 0x4E0000 && rom.u8(0, 0x84E8) == 0x2B

    private var pista = 0

    /** Puja el conjunt de blocs `fila` (offset en bytes dins spc_data_blocks): primer el bloc 0, després el n-1 fins a l'1, com SPC700Upload. Retorna l'adreça d'execució. */
    private fun pujaConjunt(rom: RomSnes, spc: SnesSpc, fila: Int): Int {
        val ptrs = ArrayList<Int>()
        for (k in 0 until 4) {
            val b = rom.u8(0, 0x84E8 + fila + k)
            if (b == 0xFF) continue
            val p = rom.u24(0, 0x84AC + b - 1)                       // SPC_ptr-1,x amb x = b
            ptrs.add(((p shr 16) and 0x3F) * 0x10000 + (p and 0xFFFF))  // hirom_mirror: banc $4x -> fitxer lineal
        }
        if (ptrs.isEmpty()) return -1
        val ordre = ArrayList<Int>().also { l -> l.add(ptrs[0]); for (k in ptrs.size - 1 downTo 1) l.add(ptrs[k]) }
        var entrada = -1
        for (p in ordre) entrada = s1Cadena(rom, spc, p)?.second ?: return -1
        return entrada
    }

    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val hdr = llista[(id - 1).coerceIn(0, llista.size - 1)].first
        val fila = rom.u8(0, 0x852F + hdr)
        pujaConjunt(rom, spc, 0)                                      // motor + música del títol (conjunt 0)
        var entrada = 0x0400
        if (fila != 0) pujaConjunt(rom, spc, fila).let { if (it > 0) entrada = it }
        // taula de pistes de $01B24B (la del capítol/ending i el Baby Bowser no la porten: el joc hi posa la pista a part)
        pista = if (hdr == 12 || hdr == 19) 1 else rom.u8(1, 0xB24B + hdr)
        spc.arrenca(entrada)
    }

    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {
        if (n == 10L && pista != 0) spc.escriuPort(0, pista)          // $2140 = identificador de pista
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------------
// Super Metroid (strager/supermetroid: bank80.asm, pujada a $80:8024-8059, taula de conjunts a $8F:E7E1 amb punters de 3 bytes, motor a $CF:8000)
// ---------------------------------------------------------------------------------------------------------------------------------------------------------

object DriverSuperMetroid : SnesDriver {
    override val nom = "SuperMetroid"
    // Conjunts de música $03..$48 (24, de 3 en 3) x pistes 1..8. El desassemblatge no dona noms ni nombre de pistes per conjunt.
    private const val CONJUNTS = 24
    private const val PISTES = 8
    override val cancons: List<String> = List(CONJUNTS * PISTES) { "So ${it + 1}" }

    override fun reconeix(rom: RomSnes): Boolean =
        !rom.hiRom && rom.d.size >= 0x300000 && rom.titol.uppercase().startsWith("SUPER METROID") &&
            rom.u24(0x8F, 0xE7E1) == 0xCF8000 && rom.u24(0x8F, 0xE7E4) == 0xD0E20D

    private var pista = 0
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val i = (id - 1).coerceIn(0, cancons.size - 1)
        val conjunt = (i / PISTES + 1) * 3
        pista = i % PISTES + 1
        val motor = rom.u24(0x8F, 0xE7E1)
        val dades = rom.u24(0x8F, 0xE7E1 + conjunt)
        var entrada = 0x1500
        s1Cadena(rom, spc, rom.fitxer(motor shr 16, motor and 0xFFFF))?.let { entrada = it.second }
        s1Cadena(rom, spc, rom.fitxer(dades shr 16, dades and 0xFFFF))?.let { entrada = it.second }
        spc.arrenca(entrada)
    }

    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {
        if (n == 20L) spc.escriuPort(0, pista)                         // IO_APUI00 = pista
    }
}

val DRIVERS_SNES_S1: List<SnesDriver> = listOf(DriverSmw, DriverYoshiIsland, DriverSuperMetroid)
