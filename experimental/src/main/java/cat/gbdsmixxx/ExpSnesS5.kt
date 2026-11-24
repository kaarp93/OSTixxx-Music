package cat.gbdsmixxx

/*
 * Lot S, part 5 (experimental): Harvest Moon (SNES, US, LoROM). Font: dzik87/HarvestMoonSNES (docs/audio.md: mapa de blobs, protocol de ports, ordres de l'SPC, taules de pistes i mostres; clonat a
 * /tmp/claude-0/repos/hm_d) i ElisaS-Codes/HM-Decomp (bank_83.asm: carregador IPL a $83:843D). Seqüència real del joc (fCore, fAudio_LoadPendingTrack): motor ($AD:8000, $0FD6 bytes a ARAM $0500
 * per IPL), ordre $0B (estèreo), ordre 2 per les 7 mostres comunes ($17 $18 $19 $1A $15 $06 $0A), ordre 1 amb el bloc d'SFX ($B2:D120, $3F2 bytes), ordre 4 (lliura els espais de mostres), ordre 5 (cançó a $19F4),
 * ordre 3 per cada mostra de la cançó (més la $06) i ordre 8 (1, $40) que la fa sonar. FIDEL: tot això, tal com el documenta el repo (la doc diu que ho ha derivat estàticament, sense emulador).
 * DEDUÏT: detalls del handshake dels paquets (esperar port 3 = 1, escriure ports 0-2 i 2 al port 3, esperar l'ack 4 o 8, escriure 0; per als trossos de dades esperar port 3 = 0). Adreces de ROM de
 * les mostres i pistes de la taula de la doc (només ROM USA). No comprovat amb cap ROM.
 */
object DriverHarvestMoon : SnesDriver {
    override val nom = "HarvestMoon"
    // pista: (nom, banc, adreça, mida, ids de mostra)
    private class Pista(val nom: String, val banc: Int, val adr: Int, val mida: Int, val ids: IntArray)
    // mostra: banc, adreça, mida, desplaçament de bucle, ADSR1, ADSR2, GAIN, afinació entera, afinació fraccionària
    private class Mostra(val banc: Int, val adr: Int, val mida: Int, val bucle: Int, val a1: Int, val a2: Int, val g: Int, val ti: Int, val tf: Int)
    private val PISTES = listOf(
        Pista("Spring", 0xAD, 0x8FD6, 748, intArrayOf(0x07, 0x03, 0x01, 0x0C)),
        Pista("Summer", 0xAD, 0xB44A, 626, intArrayOf(0x01, 0x05)),
        Pista("HarvestFestival", 0xAD, 0x9FFD, 939, intArrayOf(0x03, 0x07, 0x01, 0x16, 0x05)),
        Pista("Winter", 0xAD, 0x92C2, 694, intArrayOf(0x03, 0x07, 0x01)),
        Pista("Town", 0xAD, 0x9AA7, 1091, intArrayOf(0x05, 0x01, 0x07)),
        Pista("Mountain", 0xAD, 0xAC0E, 921, intArrayOf(0x03, 0x07, 0x01)),
        Pista("Fall", 0xAD, 0xAFA7, 685, intArrayOf(0x01, 0x04, 0x0C, 0x08)),
        Pista("Dance", 0xAD, 0xB254, 502, intArrayOf(0x01, 0x07, 0x16)),
        Pista("Church", 0xAD, 0xA9AE, 608, intArrayOf(0x02)),
        Pista("NamingScreen", 0xAD, 0xBE06, 410, intArrayOf(0x05, 0x07)),
        Pista("Title", 0xAD, 0xBFA0, 398, intArrayOf(0x05, 0x01, 0x07)),
        Pista("Funeral", 0xAD, 0xA3A8, 465, intArrayOf(0x05, 0x07, 0x01)),
        Pista("EggFestival", 0xAD, 0xB6BC, 659, intArrayOf(0x05, 0x01, 0x08)),
        Pista("StarNightFestival", 0xAD, 0x986A, 573, intArrayOf(0x05)),
        Pista("TrollsCave", 0xAD, 0x9578, 754, intArrayOf(0x05, 0x0B, 0x01, 0x08)),
        Pista("Ending", 0xAD, 0xA579, 1077, intArrayOf(0x05, 0x01, 0x0C)),
        Pista("BadEnding", 0xAD, 0x9EEA, 275, intArrayOf(0x05, 0x03, 0x07)),
        Pista("Opening", 0xAD, 0xB94F, 1207, intArrayOf(0x04, 0x03, 0x07, 0x0B)),
        Pista("Rain", 0xAD, 0xC12E, 122, intArrayOf(0x0D)),
        Pista("RainIndoors", 0xAD, 0xC1A8, 128, intArrayOf(0x0D)),
        Pista("WeatherFair", 0xAD, 0xC228, 219, intArrayOf(0x15, 0x0D, 0x18)),
        Pista("Hurricane", 0xAD, 0xC303, 286, intArrayOf(0x10)),
        Pista("Id23", 0xAD, 0xC421, 176, intArrayOf(0x1D)),
        Pista("Bell", 0xAD, 0xC4D1, 92, intArrayOf(0x1D)),
        Pista("WeatherCalm", 0xAD, 0xC52D, 188, intArrayOf(0x13, 0x15)))
    private val MOSTRES = mapOf(
        0x01 to Mostra(0xAD, 0xC5E9, 4288, 432, 0xFF, 0xE0, 0xB8, 5, 0x20),
        0x02 to Mostra(0xAE, 0x8000, 12000, 630, 0xFF, 0xE0, 0xB8, 6, 0xB0),
        0x03 to Mostra(0xAD, 0xD6A9, 2992, 1503, 0xFF, 0xE3, 0xB8, 1, 0xE0),
        0x04 to Mostra(0xAD, 0xE259, 1920, 1854, 0xFF, 0xEF, 0xB8, 5, 0xF0),
        0x05 to Mostra(0xAD, 0xE9D9, 3344, 2034, 0xFF, 0xEB, 0xB8, 3, 0xE0),
        0x06 to Mostra(0xAD, 0xF6E9, 64, 27, 0xFF, 0xE0, 0xB8, 4, 0x00),
        0x07 to Mostra(0xAE, 0xAEE0, 7984, 1548, 0xFF, 0xE0, 0xB8, 5, 0xA0),
        0x08 to Mostra(0xAE, 0xCE10, 6480, 0, 0xFF, 0xE0, 0xB8, 5, 0xA0),
        0x09 to Mostra(0xAF, 0x8000, 4864, 4824, 0x86, 0xEB, 0xB8, 4, 0x00),
        0x0A to Mostra(0xAE, 0xE760, 720, 675, 0xFF, 0xEE, 0xB8, 3, 0xF0),
        0x0B to Mostra(0xAF, 0x9300, 7040, 3510, 0xFF, 0xE0, 0xB8, 3, 0x50),
        0x0C to Mostra(0xAE, 0xEA30, 832, 513, 0xFF, 0xE9, 0xB8, 1, 0xE0),
        0x0D to Mostra(0xAD, 0xF729, 1648, 27, 0xFF, 0xE0, 0xB8, 1, 0x30),
        0x0E to Mostra(0xAE, 0xED70, 3488, 0, 0xFF, 0xE0, 0xB8, 7, 0xA0),
        0x0F to Mostra(0xAF, 0xAE80, 4672, 27, 0xFF, 0xE0, 0xB8, 0, 0xD0),
        0x10 to Mostra(0xAF, 0xC0C0, 4000, 1233, 0xFF, 0xE0, 0xB8, 0, 0xB0),
        0x11 to Mostra(0xAF, 0xD060, 2000, 27, 0xFF, 0xE0, 0xB8, 0, 0x50),
        0x12 to Mostra(0xAF, 0xD830, 6976, 0, 0xFF, 0xE0, 0xB8, 3, 0xC0),
        0x13 to Mostra(0xB0, 0x8000, 7984, 0, 0xFF, 0xE0, 0xB8, 7, 0xA0),
        0x14 to Mostra(0xAF, 0xF370, 1712, 27, 0xFF, 0xE0, 0xB8, 0, 0x90),
        0x15 to Mostra(0xB0, 0x9F30, 6464, 0, 0xFF, 0xE9, 0xB8, 1, 0xE0),
        0x16 to Mostra(0xB0, 0xB870, 1840, 0, 0xFF, 0xE0, 0xB8, 7, 0xA0),
        0x17 to Mostra(0xB0, 0xBFA0, 992, 0, 0xFF, 0xE0, 0xB8, 4, 0x20),
        0x18 to Mostra(0xB0, 0xC380, 4992, 0, 0xFF, 0xE0, 0xB8, 5, 0x20),
        0x19 to Mostra(0xAF, 0xFA20, 1248, 0, 0xFF, 0xE0, 0xB8, 7, 0xA0),
        0x1A to Mostra(0xB0, 0xD700, 3200, 0, 0xFF, 0xE0, 0xB8, 7, 0xA0),
        0x1B to Mostra(0xB0, 0xE380, 3984, 0, 0xFF, 0xE0, 0xB8, 1, 0x50),
        0x1C to Mostra(0xB1, 0x8000, 3664, 0, 0xFF, 0xE0, 0xB8, 7, 0xA0),
        0x1D to Mostra(0xB1, 0x8E50, 15984, 0, 0xFF, 0xE0, 0xB8, 5, 0x40),
        0x1E to Mostra(0xB1, 0xCCC0, 3984, 0, 0xFF, 0xE0, 0xB8, 1, 0xC0),
        0x1F to Mostra(0xB1, 0xDC50, 2992, 0, 0xFF, 0xE0, 0xB8, 1, 0x10),
        0x20 to Mostra(0xB1, 0xE800, 2992, 0, 0xFF, 0xE0, 0xB8, 0, 0xB0),
        0x21 to Mostra(0xB1, 0xF3B0, 2992, 0, 0xFF, 0xE0, 0xB8, 2, 0x90),
        0x22 to Mostra(0xB2, 0x9380, 2992, 18, 0xFF, 0xE0, 0xB8, 0, 0x20),
        0x23 to Mostra(0xB2, 0x9F30, 1984, 0, 0xFF, 0xE0, 0xB8, 1, 0x10),
        0x24 to Mostra(0xB2, 0x8000, 4992, 0, 0xFF, 0xE0, 0xB8, 1, 0xA0),
        0x25 to Mostra(0xB2, 0xA6F0, 832, 513, 0xFF, 0xE9, 0xB8, 1, 0xE0),
        0x26 to Mostra(0xB2, 0xAA30, 1984, 0, 0xFF, 0xE0, 0xB8, 1, 0x00),
        0x27 to Mostra(0xB2, 0xB1F0, 7984, 0, 0xFF, 0xE0, 0xB8, 1, 0xE0))
    override val cancons: List<String> = PISTES.mapIndexed { i, p -> "${p.nom} · pista ${i + 1} · \$${hx(p.banc)}:${hx(p.adr, 4)}" }
    override fun reconeix(rom: RomSnes): Boolean =
        !rom.hiRom && rom.d.size >= 0x180000 && rom.titol.uppercase().contains("HARVEST") && rom.u8(0xAD, 0x8000) == 0x20 && rom.u8(0xAD, 0x8001) == 0xCD && rom.u8(0xAD, 0x8002) == 0xCF

    private var pressupost = 0
    private fun esperaPort(spc: SnesSpc, i: Int, v: Int) {
        val b = expSBuf.get()
        while (pressupost > 0) { if (spc.llegeixPort(i) == v) return; spc.executa(b, 16); pressupost-- }
    }
    private fun octet(rom: RomSnes, banc: Int, adr: Int, k: Int): Int = rom.u8(banc + ((adr + k) shr 15) - (adr shr 15), 0x8000 or ((adr + k) and 0x7FFF))
    /** Un paquet de paràmetres: espera port 3 = 1, escriu els ports 0-2 i 2 al port 3, espera l'ack (4 o 8) i escriu 0. */
    private fun paquet(spc: SnesSpc, a: Int, b: Int, c: Int, ack: Int) {
        esperaPort(spc, 3, 1)
        spc.escriuPort(0, a); spc.escriuPort(1, b); spc.escriuPort(2, c); spc.escriuPort(3, 2)
        esperaPort(spc, 3, ack); spc.escriuPort(3, 0)
    }
    private fun ordre(spc: SnesSpc, c: Int) {
        esperaPort(spc, 3, 1)
        spc.escriuPort(0, c); spc.escriuPort(1, 0); spc.escriuPort(2, 0); spc.escriuPort(3, 2)
        esperaPort(spc, 3, 4); spc.escriuPort(3, 0)
    }
    /** Trossos de 3 bytes: espera port 3 = 1, escriu 3 octets i 2 al port 3, espera que l'SPC el deixi a 0 i escriu 0. */
    private fun trossos(spc: SnesSpc, rom: RomSnes, banc: Int, adr: Int, mida: Int) {
        val n = (mida + 2) / 3
        var k = 0
        for (t in 0 until n) {
            esperaPort(spc, 3, 1)
            spc.escriuPort(0, octet(rom, banc, adr, k)); spc.escriuPort(1, octet(rom, banc, adr, k + 1)); spc.escriuPort(2, octet(rom, banc, adr, k + 2)); spc.escriuPort(3, 2)
            k += 3
            esperaPort(spc, 3, 0); spc.escriuPort(3, 0)
            if (pressupost <= 0) return
        }
    }
    private fun mostra(spc: SnesSpc, rom: RomSnes, cmd: Int, id: Int) {
        val m = MOSTRES[id] ?: return
        ordre(spc, cmd)
        paquet(spc, id, m.bucle and 255, m.bucle shr 8, 4)
        paquet(spc, m.a1, m.a2, m.g, 4)
        paquet(spc, m.ti, m.tf, 0, 4)
        val n = (m.mida + 2) / 3
        paquet(spc, n and 255, n shr 8, 0, 4)
        trossos(spc, rom, m.banc, m.adr, m.mida)
    }
    private fun bloc(spc: SnesSpc, rom: RomSnes, cmd: Int, banc: Int, adr: Int, mida: Int) {
        ordre(spc, cmd)
        val n = (mida + 2) / 3
        paquet(spc, n and 255, n shr 8, 0, 4)
        trossos(spc, rom, banc, adr, mida)
    }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        pressupost = 8_000_000
        val p = PISTES[id.coerceIn(1, PISTES.size) - 1]
        // arrencada: IPL de $0FD6 bytes a $0500 i execució allà
        for (k in 0 until 0x0FD6) spc.ram[0x0500 + k] = octet(rom, 0xAD, 0x8000, k).toByte()
        spc.arrenca(0x0500)
        esperaPort(spc, 3, 1)
        ordre(spc, 0x0B); paquet(spc, 0, 0, 0, 8)
        for (s in intArrayOf(0x17, 0x18, 0x19, 0x1A, 0x15, 0x06, 0x0A)) mostra(spc, rom, 2, s)
        bloc(spc, rom, 1, 0xB2, 0xD120, 0x3F2)                        // bloc d'SFX
        ordre(spc, 4); paquet(spc, 0, 0, 0, 8)                      // allibera les mostres de pista
        bloc(spc, rom, 5, p.banc, p.adr, p.mida)                    // dades de la cançó
        for (s in p.ids) mostra(spc, rom, 3, s)
        mostra(spc, rom, 3, 0x06)
        ordre(spc, 8); paquet(spc, 1, 0x40, 0, 8)                   // inici
    }
}

val EXP_SNES_S5: List<SnesDriver> = listOf(DriverHarvestMoon)
val EXP_COMPAT_S5: List<JocCompat> = listOf(JocCompat("Harvest Moon", "SNES", "SNES_HarvestMoon"))
