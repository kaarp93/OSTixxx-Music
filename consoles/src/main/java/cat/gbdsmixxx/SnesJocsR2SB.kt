package cat.gbdsmixxx

/*
 * Lot R2SB de jocs de SNES amb driver (ronda 2): Chrono Trigger. No comprovat amb cap ROM: escrit des del desassemblatge.
 */

private val ctBuf = ThreadLocal.withInitial { ShortArray(32) }

/**
 * Chrono Trigger (SNES, HiROM, US v1.0; dscotton/ct_disassembly, `bank_C7.asm`). Programa de so de Square: `Audio_UploadDriver` ($C7:0038) puja amb l'IPL 6 blocs `[mida16][dades]`
 * (punters a $C7:0010, destins a $C7:001C) i salta a $0200. Després `Audio_ProcessCommand` ($C7:0140) carrega cada cançó parlant amb el programa de so pels quatre ports, amb un comptador al
 * port 0 que l'SPC700 repeteix (`Apu_SendByteWaitAck`, $C7:09DA): s'emula aquí en pas coordinat. Ordre $81 (preparació), ordre $10 amb cançó/volum/eco (`Audio_SendPlaySongCmd`, $C7:0A39),
 * mostres BRR de la llista d'instruments de la cançó (`SongInstrumentTables` $C7:0E11, 16 paraules; punters `InstrumentSamplePtrs` $C7:0AEA, [mida16][BRR]; a partir de
 * `LengthPacketFollow`($C7:40B4)+$4700), directori de mostres a $1E80, ADSR ($C7:0C20) a $1F40 i afinació ($C7:0C9C) a $1FC0, seqüència (`SongSequencePtrs` $C7:0D18) a $2000, taula de
 * bateria (`DrumTables` $C7:1871, 36 bytes) a $F120 i l'ordre «endavant» $E0. L'ordre $10 no es converteix en $14 ($C7:0734 és al tros sense desassemblar). Cançons 1-82 (la 24 té dues parts al
 * desassemblatge). Les cançons «SFX» són efectes llargs de l'ambient.
 */
object DriverChronoTrigger : SnesDriver {
    override val nom = "ChronoTrigger"
    override val cancons: List<String> = listOf("Memories of Green", "Wind Scene", "Corridors of Time", "Rhythm of Wind, Sky, Earth", "Ruined World", "Millennial Fair", "Crono and Marle", "Secret of the Forest", "Zeal Palace", "Derelict Factory", "Ayla's Theme", "Courage and Pride", "Lavos' Theme", "Robo's Theme", "Morning Sunlight", "Manoria Cathedral", "SFX Birds and Waves", "SFX Leene's Bell", "Wings That Cross Time", "Schala's Theme", "Delightful Spekkio", "A Shot of Crisis", "Kingdom Trial", "Chrono Trigger Theme", "SFX Magus Flames", "SFX Wind", "Victory Fanfare", "Triumphant Jingle", "At the Bottom of Night", "Peaceful Days", "A Strange Happening", "SFX Dripping", "SFX Water Flowing", "SFX Howling Wind", "The Hidden Truth", "A Prayer to the Road", "Huh?", "The Day the World Revived", "Robo Gang Johnny", "Battle with Magus", "Boss Battle 1", "Frog's Theme", "Goodnight", "Bike Chase", "People Without Hope", "Mystery of the Past", "Underground Sewer", "A Premonition", "Undersea Palace", "Last Battle", "Dome 16 Ruin", "SFX Lavos Heartbeat", "SFX Blackbird Wind", "Burn! Bobonga!", "SFX Time Warp", "Primitive Mountain", "World Revolution", "SFX Lavos Scream", "Sealed Door", "Silent Light", "Fanfare 3", "The End of Time", "To Far Away Times", "Confusing Melody", "SFX Chanting", "Gato's Song", "SFX Wind 2", "Black Omen", "Battle 1", "Tyran Castle", "SFX Heavy Wind", "Magus Castle", "First Festival of Stars", "SFX High Wind", "SFX Waves", "SFX Heavy Breathing", "Epilogue to Good Friends", "Boss Battle 2", "Unknown Jingle", "Determination", "Battle 2", "Singing Mountain")

    override fun reconeix(rom: RomSnes): Boolean =
        rom.hiRom && rom.d.size >= 0x400000 && rom.titol.uppercase().startsWith("CHRONO TRIGGER") &&
            rom.u24(0xC7, 0x0000) == 0x00384C && rom.u24(0xC7, 0x0004) == 0x01404C && rom.u24(0xC7, 0x0038) == 0x08_0B_8B &&
            rom.u8(0xC7, 0x0AE9) in 60..120

    private var pressupost = 0
    private fun espera(spc: SnesSpc, port: Int, v: Int): Boolean {
        val b = ctBuf.get()
        while (pressupost > 0) {
            if (spc.llegeixPort(port) == v) return true
            spc.executa(b, 16); pressupost--
        }
        return false
    }

    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        fun r(a: Int) = rom.u8((a shr 16) and 255, a and 0xFFFF)
        fun r16(a: Int) = r(a) or (r(a + 1) shl 8)
        val c7 = 0xC70000
        // blocs del programa de so
        for (k in 0 until 6) {
            val p = r16(c7 + 0x10 + 2 * k); val dest = r16(c7 + 0x1C + 2 * k); val n = r16(c7 + p)
            for (j in 0 until n) spc.ram[(dest + j) and 0xFFFF] = r(c7 + p + 2 + j).toByte()
        }
        spc.arrenca(0x0200)
        pressupost = 400000
        var cnt = 0
        fun tx(c: Int, p1: Int, p2: Int, p3: Int) {
            spc.escriuPort(3, p3); spc.escriuPort(2, p2); spc.escriuPort(1, p1); spc.escriuPort(0, c)
            espera(spc, 0, c)
        }
        fun envia(p1: Int, p2: Int, p3: Int) { tx(cnt, p1, p2, p3); cnt = (cnt + 1) and 0x7F }
        // ordre $81 (preparació dels canals)
        tx(0x81, 0, 0xFF, 0xFF)
        val song = id.coerceIn(1, cancons.size)
        val eco = r(c7 + 0x241D + 2 * song) and 0x0F
        val vol = r(c7 + 0x241E + 2 * song)
        tx(0x10, song, vol, eco)
        cnt = 0x11
        // llista d'instruments de la cançó
        val llista = ArrayList<Int>()
        for (k in 0 until 16) { val w = r16(c7 + 0x0E11 + song * 32 + 2 * k); if (w == 0) break; llista.add(w) }
        val dest = HashMap<Int, Int>()
        var pos = (r16(c7 + 0x40B4) + 0x4700) and 0xFFFF
        for (ins in llista) {
            if (dest.containsKey(ins)) continue
            dest[ins] = pos
            val pt = (r(c7 + 0x0AEA + 3 * (ins - 1) + 2) shl 16) or r16(c7 + 0x0AEA + 3 * (ins - 1))
            val n = r16(pt)
            envia(3, pos and 255, pos shr 8)
            for (j in 0 until (n + 2) / 3) envia(r(pt + 2 + 3 * j), r(pt + 3 + 3 * j), r(pt + 4 + 3 * j))
            cnt = 0xE0
            pos = (pos + n) and 0xFFFF
        }
        // directori de mostres ($1E80), ADSR ($1F40) i afinació ($1FC0)
        val dir = IntArray(64)
        for ((j, ins) in llista.withIndex()) {
            val ini = dest[ins] ?: 0; val bucle = (ini + r16(c7 + 0x0BA4 + (ins - 1) * 2)) and 0xFFFF
            dir[4 * j] = ini and 255; dir[4 * j + 1] = ini shr 8; dir[4 * j + 2] = bucle and 255; dir[4 * j + 3] = bucle shr 8
        }
        envia(2, 0x80, 0x1E)
        for (k in 0 until 32) { envia(dir[2 * k], dir[2 * k + 1], 0x1E) }
        cnt = 0xE0
        for ((base, taula) in listOf(0x40 to 0x0C20, 0xC0 to 0x0C9C)) {
            envia(2, base, 0x1F)
            for (k in 0 until 16) {
                val w = if (k < llista.size) r16(c7 + taula + (llista[k] - 1) * 2) else 0
                envia(w and 255, w shr 8, 0x1F)
            }
            cnt = 0xE0
        }
        // seqüència a $2000
        val ps = c7 + 0x0D18 + 3 * song
        val sq = (r(ps + 2) shl 16) or r16(ps)
        val ln = r16(sq)
        envia(3, 0x00, 0x20)
        for (j in 0 until ln / 3 + 1) envia(r(sq + 2 + 3 * j), r(sq + 3 + 3 * j), r(sq + 4 + 3 * j))
        cnt = 0xE0
        // taula de bateria a $F120
        envia(3, 0x20, 0xF1)
        val dt = c7 + 0x1871 + 36 * song
        for (j in 0 until 12) envia(r(dt + 3 * j), r(dt + 3 * j + 1), r(dt + 3 * j + 2))
        // «endavant»
        spc.escriuPort(1, 0xE0); spc.escriuPort(0, 0xE0)
        espera(spc, 0, 0xE0)
    }
}

val DRIVERS_SNES_R2SB: List<SnesDriver> = listOf(DriverChronoTrigger)
