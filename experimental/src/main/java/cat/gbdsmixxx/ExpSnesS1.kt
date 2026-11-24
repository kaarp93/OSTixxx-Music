package cat.gbdsmixxx

/*
 * Lot S (experimental, OSTixxx Debug) de jocs de SNES: Arcana i Super Mario Kart. NO comprovats amb cap ROM: escrits des dels desassemblatges.
 * Cada pista porta l'adreça al nom perquè l'autor pugui dir quines van.
 */

internal val expSBuf = ThreadLocal.withInitial { ShortArray(32) }

/** Copia a `spc.ram` els blocs en format IPL (mida, destí, dades; mida 0 = final amb l'adreça d'entrada) que comencen a `o` dins `d`. Retorna l'entrada o -1. */
private fun expSBlocs(spc: SnesSpc, d: ByteArray, o0: Int): Int {
    var o = o0
    fun u16(p: Int) = (d[p].toInt() and 255) or ((d[p + 1].toInt() and 255) shl 8)
    var n = 0
    while (o >= 0 && o + 4 <= d.size && n++ < 512) {
        val len = u16(o); val ad = u16(o + 2); o += 4
        if (len == 0) return ad
        if (o + len > d.size) return -1
        for (k in 0 until len) spc.ram[(ad + k) and 0xFFFF] = d[o + k]
        o += len
    }
    return -1
}

internal fun hx(v: Int, n: Int = 2) = v.toString(16).uppercase().padStart(n, '0')

// ---------------------------------------------------------------------------------------------------------------------------------------------------------
// Arcana (HAL, LoROM). Font: r2s_arcana-disassembly (bank_00.asm: Sub_Set_Music $00:D118, Init_SPC $00:D176, Setup_music $00:D1D6, Song_related $00:D1F4, Tbl_Songs $00:D20B,
// Wait_for_SPC $00:D2AB, Init_SPC_2 $00:D2EC, carregador IPL $00:D30D amb el motor a $00:D49D). Els noms de les cançons són els comentaris de Tbl_Songs.
// FIDEL: tot el protocol de càrrega (copiat rutina per rutina). DEDUÏT: que cada cançó $33+ es toca amb l'ordre $33 al port 0 (Sub_Set_Music) i que les ordres $01-$32
// són cançons/efectes residents (no es coneix cap nom); l'estat del contador del port 0 entre rutines.
// ---------------------------------------------------------------------------------------------------------------------------------------------------------
object DriverArcana : SnesDriver {
    override val nom = "Arcana"
    private val NOMS_33 = listOf(
        "Song33 Credits" to 0x1EA4D8, "HAL theme (Song34)" to 0x1EBADC, "Our Story's Name (Title, Song35)" to 0x1EBBF4, "Prologue to the Tale (Intro, Song36)" to 0x1EC04A,
        "Death of a Loved One (Song37)" to 0x1EC997, "A New Chapter (Song38)" to 0x1ECC55, "Two Beautiful Princesses (Song39)" to 0x1ED0A9, "Rocket ship (Song3A)" to 0x1ED31C,
        "Rocket ship falling (Song3B)" to 0x1ED469, "Boom (Song3C)" to 0x1ED660, "Souls (Song3D)" to 0x1ED7DF, "Reinoll ~ Generous Hermit (Song3E)" to 0x1ED86A,
        "Ariel ~ Soldier of Betrayal (Song3F)" to 0x1EDE50, "The Legend (Song40)" to 0x1EE133, "Rimsala Revived (Song41)" to 0x1F8001, "Trickery (Song42)" to 0x1EE603,
        "Dawin ~ Treasure Hunter (Song43)" to 0x1EEC78, "Alchemist's Workshop (Song44)" to 0x1EF11A, "Crystal Sword (Song45)" to 0x1EF3DB, "Fanatical Warlock Galneon (Song46)" to 0x1EF5B3,
        "Rooks ~ The Card Master (Song47)" to 0x1F80FC, "Birth of a Hero (Song48)" to 0x1F830B, "Journey (Song49)" to 0x1F8442, "Draven's Valley Pass (Song4A)" to 0x1F8537,
        "Shrine of Chaos, Balnea (Song4B)" to 0x1F8A6F, "Cavern of Ice (Song4C)" to 0x1F9234, "Silent Forest of Doubt (Song4D)" to 0x1F96FF, "Dark Castle Bintel (Song4E)" to 0x1F9A2E,
        "Conflict (Song4F)" to 0x1FA7F3, "Decisive Battle (Song50)" to 0x1FB0E6, "Second Armageddon (Song51)" to 0x1FBA47, "The Goddess (Song52)" to 0x1FC42D,
        "Grass Flute (Song53)" to 0x1FC8D0, "Got Item (Song54)" to 0x1FC969, "Fanfare (Song55)" to 0x1FCAEB, "Trusted Circle (Song56)" to 0x1EFE40,
        "Resting (Song57)" to 0x1FCF72, "Magician's Tent (Song58)" to 0x1EFF32, "Crowded Marketplace (Song59)" to 0x1FD04A, "Weapon Shop (Song5A)" to 0x1FD620)
    // id = valor de l'ordre del port 0 ($01-$5A)
    override val cancons: List<String> =
        List(0x32) { "Ordre residente \$${hx(it + 1)} · base \$1E:8001/98B7/BADC" } +
            NOMS_33.mapIndexed { i, (n, a) -> "$n · \$${hx(a shr 16)}:${hx(a and 0xFFFF, 4)} · ordre \$${hx(0x33 + i)}" }
    override fun reconeix(rom: RomSnes): Boolean =
        !rom.hiRom && rom.d.size >= 0x100000 && rom.titol.uppercase().contains("ARCANA") &&
            rom.u24(0, 0xD20B) == 0x1EA4D8 && rom.u24(0, 0xD20F) == 0x1EBADC && rom.u24(0, 0xD213) == 0x1EBBF4

    private var pressupost = 0
    private var comptador = 0                                         // $06 (8 bits)
    private fun espera(spc: SnesSpc, p: Int, v: Int) {
        val b = expSBuf.get()
        while (pressupost > 0) { if (spc.llegeixPort(p) == v) return; spc.executa(b, 16); pressupost-- }
    }
    private fun initSpc2(spc: SnesSpc, rom: RomSnes) {                // $00:D2EC
        spc.escriuPort(2, 0); spc.escriuPort(3, 0); spc.escriuPort(0, 0); spc.escriuPort(1, 0)
        val b = expSBuf.get()
        var intents = 0
        while (pressupost > 0 && intents++ < 8) {
            spc.escriuPort(0, 0xFF); spc.escriuPort(1, 0)
            var fet = 0
            while (pressupost > 0 && fet++ < 40000) {
                val v = spc.llegeixPort(0) or (spc.llegeixPort(1) shl 8)
                if (v == 0xEEDD) { comptador = 0xDD; return }
                if (v == 0xBBAA) {
                    val e = expSBlocs(spc, rom.d, rom.fitxer(0, 0xD49D))             // $00:D30D: carregador IPL, motor a $00:D49D
                    spc.arrenca(if (e >= 0) e else 0x0200)
                    break
                }
                spc.executa(b, 16); pressupost--
            }
        }
    }
    private fun w(rom: RomSnes, banc: Int, adr: Int) = rom.u8(banc, adr and 0xFFFF) or (rom.u8(banc, (adr + 1) and 0xFFFF) shl 8)
    private fun waitForSpc(spc: SnesSpc, rom: RomSnes, ad: Int, banc: Int) {   // $00:D2AB
        var p = ad and 0xFFFF
        var x = comptador
        var guard = 0
        while (pressupost > 0 && guard++ < 4000000) {
            val a = w(rom, banc, p)
            if (a == 0) break
            espera(spc, 0, x); spc.escriuPort(2, a and 255); spc.escriuPort(3, a shr 8); spc.escriuPort(0, x); x = (x + 1) and 255
            p = (p + 2) and 0xFFFF
            var r = (a + 2) and 0xFFFF
            while (true) {
                val v = w(rom, banc, p)
                espera(spc, 0, x); spc.escriuPort(2, v and 255); spc.escriuPort(3, v shr 8); spc.escriuPort(0, x); x = (x + 1) and 255
                p = (p + 1) and 0xFFFF; r = (r - 1) and 0xFFFF
                if (r == 0) break
                p = (p + 1) and 0xFFFF; r = (r - 1) and 0xFFFF
                if (r == 0) break
            }
        }
        comptador = x
    }
    private fun songRelated(spc: SnesSpc) {                          // $00:D1F4
        espera(spc, 0, comptador)
        spc.escriuPort(2, 0); spc.escriuPort(3, 0); spc.escriuPort(0, comptador); spc.escriuPort(1, 0)
        espera(spc, 0, 0)
    }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        pressupost = 4_000_000
        var cmd = id.coerceIn(1, 0x5A)
        // Init_SPC ($00:D176): motor + 4 blocs de dades base
        initSpc2(spc, rom)
        waitForSpc(spc, rom, 0x8001, 0x1D); waitForSpc(spc, rom, 0x8001, 0x1E); waitForSpc(spc, rom, 0x98B7, 0x1E); waitForSpc(spc, rom, 0xBADC, 0x1E)
        songRelated(spc)
        if (cmd >= 0x33) {
            // Sub_Set_Music ($00:D118) -> Setup_music ($00:D1D6): Tbl_Songs[(cmd-$33)*4] = adreça (2) + banc (1) + $00
            val t = 0xD20B + (cmd - 0x33) * 4
            initSpc2(spc, rom)
            waitForSpc(spc, rom, rom.u16(0, t), rom.u8(0, t + 2)); songRelated(spc)
            if (cmd == 0x33) { initSpc2(spc, rom); waitForSpc(spc, rom, 0xC91F, 2); songRelated(spc); cmd = 4 } else cmd = 0x33
        }
        spc.escriuPort(2, 0xD0); spc.escriuPort(3, 0); spc.escriuPort(0, cmd)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------------
// Super Mario Kart (LoROM 512 KB). Font: r2s_smk-spc700-disassembly (README: el binari de so surt de la ROM en dos trossos: 8000h bytes a l'offset 28000h i la resta a 38000h;
// SYMBOLS_MAP.txt: CODE_ENTRY $0800 i les adreces de SPC de cada cançó; BUILD/define/define.def: ordres de cançó $01-$16 al port 0).
// FIDEL: adreces del motor i ordres. DEDUÏT: que el binari és una cadena de blocs en format IPL (el desassemblat el munta amb spcblock), que l'ordre es pot escriure uns fotogrames
// després d'arrencar, i que les cançons de pista ($04 upload_norm i les versions ràpides) necessiten una cançó pujada per la CPU (adreces no trobades al repo).
// ---------------------------------------------------------------------------------------------------------------------------------------------------------
object DriverSmk : SnesDriver {
    override val nom = "SMKart"
    private val ORDRES = listOf(
        "Fanfare · SPC \$FF00" to 0x01, "Time Trial intro · SPC \$07CA" to 0x02, "GP intro · SPC \$D783" to 0x03, "upload_norm (cançó de pista pujada per la CPU)" to 0x04,
        "Final lap · SPC \$3E45" to 0x05, "fast_castle (última volta Bowser Castle)" to 0x06, "fast_norm (última volta)" to 0x07, "Starman · SPC \$10BF" to 0x08,
        "Race qualified · SPC \$DAF9" to 0x09, "No record · SPC \$03DB" to 0x0A, "Rank out · SPC \$06F0" to 0x0B, "Rank Mario · SPC \$DD3B" to 0x0C, "Rank Luigi · SPC \$DC37" to 0x0D,
        "Rank Bowser · SPC \$0506" to 0x0E, "Rank Peach · SPC \$D989" to 0x0F, "Rank DK Jr · SPC \$124E" to 0x10, "Rank Koopa · SPC \$05FE" to 0x11, "Rank Toad · SPC \$3D24" to 0x12,
        "Rank Yoshi · SPC \$13D7" to 0x13, "Game over · SPC \$DE33" to 0x14, "fast_obake (última volta Ghost Valley)" to 0x15, "Credits" to 0x16)
    override val cancons: List<String> = ORDRES.map { (n, c) -> "$n · ordre \$${hx(c)} · ROM 0x28000/0x38000" }
    override fun reconeix(rom: RomSnes): Boolean =
        rom.d.size == 0x80000 && rom.titol.uppercase().startsWith("SUPER MARIO KART")
    private fun imatge(rom: RomSnes): ByteArray {
        val a = rom.d.copyOfRange(0x28000, 0x30000)
        val b = rom.d.copyOfRange(0x38000, rom.d.size)
        return a + b
    }
    private var ordre = 1
    private var enviat = false
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        ordre = ORDRES[id.coerceIn(1, ORDRES.size) - 1].second; enviat = false
        val e = expSBlocs(spc, imatge(rom), 0)
        spc.arrenca(if (e >= 0) e else 0x0800)
    }
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {
        if (n == 0L) enviat = false
        if (!enviat && n >= 30) { spc.escriuPort(0, ordre); enviat = true }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------------------------------
// Speedy Gonzales: Los Gatos Bandidos (LoROM 1 MB, USA v1.0). Font: r2s_Speedy-Gonzales-Los-Gatos-Banditos-Disassembly, Routine_Macros_SGLGB.asm: banc $96 (API de so a $96:8000,
// CODE_9682F0 engega el motor des de DATA_16855F = $16:855F, CODE_9683D8 espera el port0 = 1), carregadors de joc de dades a $96:803F-$96:8057 (CODE_96806F...CODE_968202 + CODE_968232
// + CODE_968261 + CODE_9682D6), protocol de transferència CODE_9683F1 (ordres $0A/$0B/$0C), ordre de cançó CODE_96831F (ordre 1: port1 = cançó, port2 = X). La taula de parelles
// (joc de dades, cançó) és DATA_88FDFA ($88:FDFA).
// FIDEL: tot el protocol de càrrega i l'ordre de música. DEDUÏT: el valor de X (port 2) a l'ordre 1 (es posa 0), els volums ($7F) i que l'ordre 5/6 inicial és un «reset».
// No s'ha comprovat amb cap ROM (les V1.1 podrien tenir altres adreces).
// ---------------------------------------------------------------------------------------------------------------------------------------------------------
object DriverSpeedy : SnesDriver {
    override val nom = "Speedy"
    private val PARELLES = listOf(7 to 0, 5 to 0, 8 to 0, 9 to 0, 9 to 1, 3 to 0, 3 to 1, 2 to 0, 1 to 0, 1 to 1, 4 to 0, 6 to 0, 6 to 1, 6 to 2, 6 to 3, 6 to 4, 6 to 5, 6 to 6)
    // joc de dades 1..9 -> (punter de la llista de blocs, adreça del carregador)
    private val JOCS = listOf(0x169D07 to 0x96806F, 0x19F09B to 0x9680B2, 0x1A8000 to 0x9680E2, 0x16BB27 to 0x968112, 0x1AD0C2 to 0x968142, 0x1A97B5 to 0x968172,
        0x16CD78 to 0x9681A2, 0x1AE6B1 to 0x9681D2, 0x16E88E to 0x968202)
    override val cancons: List<String> = PARELLES.mapIndexed { i, (s, k) ->
        val j = JOCS[s - 1]
        "Joc de dades $s · cançó $k · llista \$${hx(j.first shr 16)}:${hx(j.first and 0xFFFF, 4)} · carregador \$${hx(j.second shr 16)}:${hx(j.second and 0xFFFF, 4)} · taula \$88:FDFA[$i]"
    }
    override fun reconeix(rom: RomSnes): Boolean =
        !rom.hiRom && rom.d.size == 0x100000 && rom.titol.uppercase().startsWith("SPEEDY GONZALES") && rom.u16(0x16, 0x855F) != 0 &&
            rom.u16(0x88, 0xFDFA) == 7 && rom.u16(0x88, 0xFDFC) == 0 && rom.u16(0x88, 0xFDFE) == 5
    private var pressupost = 0
    private fun p(spc: SnesSpc, i: Int, v: Int) = spc.escriuPort(i, v)
    private fun esperaPort(spc: SnesSpc, i: Int, zero: Boolean) {
        val b = expSBuf.get()
        while (pressupost > 0) { if ((spc.llegeixPort(i) == 0) == zero) return; spc.executa(b, 16); pressupost-- }
    }
    private fun esperaIgual(spc: SnesSpc, i: Int, v: Int) {
        val b = expSBuf.get()
        while (pressupost > 0) { if (spc.llegeixPort(i) == v) return; spc.executa(b, 16); pressupost-- }
    }
    private fun ack(spc: SnesSpc) { esperaPort(spc, 3, false); p(spc, 0, 0); esperaPort(spc, 3, true) }            // CODE_9684E5
    private fun ordre(spc: SnesSpc, c: Int, p1: Int = -1, p2: Int = -1) {
        if (p1 >= 0) p(spc, 1, p1)
        if (p2 >= 0) p(spc, 2, p2)
        p(spc, 0, c); ack(spc)
    }
    private fun octets(rom: RomSnes, banc: Int, adr: Int, n: Int) = ByteArray(n) { rom.u8(banc + ((adr + it) shr 15) - (adr shr 15), 0x8000 or ((adr + it) and 0x7FFF)).toByte() }
    /** CODE_9683F1: puja `n` octets de `ofont` a l'ARAM `dest`. */
    private fun puja(spc: SnesSpc, dest: Int, n: Int, ofont: ByteArray) {
        p(spc, 1, dest and 255); p(spc, 2, dest shr 8); p(spc, 0, 0x0A); ack(spc)
        p(spc, 1, n and 255); p(spc, 2, n shr 8); p(spc, 0, 0x0B); ack(spc)
        var x = n; var y = 0
        fun tanca() { p(spc, 0, 0x0C); esperaPort(spc, 3, false); p(spc, 0, 0); esperaPort(spc, 3, true) }
        while (x > 0 && pressupost > 0) {
            p(spc, 1, ofont[y].toInt() and 255); x--
            if (x == 0) { tanca(); return }
            y++; p(spc, 2, ofont[y].toInt() and 255); x--
            if (x == 0) { tanca(); return }
            y++; p(spc, 3, ofont[y].toInt() and 255); tanca(); y++; x--
        }
    }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        pressupost = 6_000_000
        val (set, sub) = PARELLES[id.coerceIn(1, PARELLES.size) - 1]
        // CODE_9682F0: motor (IPL) i espera que el port 0 valgui 1
        val e = expSBlocs(spc, rom.d, rom.fitxer(0x16, 0x855F))
        spc.arrenca(if (e >= 0) e else 0x0200)
        esperaIgual(spc, 0, 1); p(spc, 0, 0)
        // inici del selector de música ($88:FD..): ordre 5, ordre 6, volums
        ordre(spc, 5); ordre(spc, 6); ordre(spc, 3, 0x7F); ordre(spc, 4, 0x7F)
        // carregador del joc de dades (CODE_96806F i germans)
        ordre(spc, 5); ordre(spc, 6)
        val lli = JOCS[set - 1].first
        val lb = lli shr 16; val la = lli and 0xFFFF
        fun b(o: Int) = rom.u8(lb + ((la + o) shr 15) - (la shr 15), 0x8000 or ((la + o) and 0x7FFF))
        fun w(o: Int) = b(o) or (b(o + 1) shl 8)
        var y = 0
        for (k in 0 until 5) {                                         // CODE_968232
            if (w(y) == 0xFFFF) { y += 7; continue }
            val src = w(y); val sb = b(y + 2); val len = w(y + 3); val dst = w(y + 5); y += 7
            puja(spc, dst, len, octets(rom, sb, src, len))
        }
        // CODE_968261: llista de mostres [destí inicial][ids...][$FF]
        var dest = w(y); y += 1
        val dir = ByteArray(0x200); var td = 0
        while (true) {
            y += 1
            val n = b(y) and 255
            if (n == 0xFF) break
            val t = 0x169B67 + 6 * n
            val src = rom.u16(t shr 16, t and 0xFFFF); val sb = rom.u8(t shr 16, (t and 0xFFFF) + 2); val len = rom.u16(t shr 16, (t and 0xFFFF) + 4)
            puja(spc, dest, len, octets(rom, sb, src, len))
            val bucle = rom.u16(0x16, 0x9C9F + 2 * n)
            if (td + 3 < dir.size) { dir[td] = dest.toByte(); dir[td + 1] = (dest shr 8).toByte(); dir[td + 2] = (dest + bucle).toByte(); dir[td + 3] = ((dest + bucle) shr 8).toByte() }
            td += 4; dest = (dest + len) and 0xFFFF
        }
        puja(spc, 0x1700, 0x200, dir)                                    // CODE_9682D6: DIR de l'S-DSP
        for (i in 0..3) p(spc, i, 0)                                     // CODE_96809F
        // ordre de cançó (CODE_96831F: A = cançó al port 1, X al port 2)
        ordre(spc, 1, sub, 0)
    }
}

val EXP_SNES_S: List<SnesDriver> = listOf(DriverArcana, DriverSmk, DriverSpeedy)
val EXP_COMPAT_S: List<JocCompat> = listOf(
    JocCompat("Arcana", "SNES", "SNES_Arcana"),
    JocCompat("Super Mario Kart", "SNES", "SNES_SMKart"),
    JocCompat("Speedy Gonzales: Los Gatos Bandidos", "SNES", "SNES_Speedy"))
