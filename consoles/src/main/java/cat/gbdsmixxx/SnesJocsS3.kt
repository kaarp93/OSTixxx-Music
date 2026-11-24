package cat.gbdsmixxx

/*
 * Lot S3 de drivers de SNES (EarthBound/Mother 2, Final Fantasy IV, V i VI, Soul Blazer, Shin Megami Tensei, BS Zelda i A Link to the Past).
 * Cap d'aquests jocs s'ha pogut provar amb una ROM: les adreces i els protocols surten dels desassemblatges (vegeu el comentari de cada objecte).
 */

/** Peces comunes dels drivers del lot S3 (còpia de blocs de la ROM a la RAM de l'SPC700 i petit «CPU principal» que parla amb els ports). */
internal object S3 {
    /** Copia a `spc.ram` els blocs `[mida u16][adreça u16][dades]` que comencen a `banc`:`adr` fins a un bloc de mida 0. Retorna l'adreça d'entrada (la del bloc final), -2 si s'arriba a `fiBanc`:`fiAdr` abans del bloc final o -1 si res no quadra. */
    fun blocs(spc: SnesSpc, rom: RomSnes, banc: Int, adr: Int, fiBanc: Int = -1, fiAdr: Int = 0): Int {
        var a = adr; var b = banc
        var n = 0
        while (n++ < 4096) {
            if (b == fiBanc && a == fiAdr) return -2
            val mida = rom.u16(b, a); val dest = rom.u16(b, a + 2)
            if (mida == 0) return dest
            val dades = rom.bytes(b, a + 4, mida)
            for (k in 0 until mida) if (dest + k < 65536) spc.ram[dest + k] = dades[k]
            val seg = a + 4 + mida
            if (rom.hiRom) { b += seg shr 16; a = seg and 0xFFFF } else { b += (seg - 0x8000) shr 15; a = 0x8000 or (seg and 0x7FFF) }
        }
        return -1
    }
    /** Copia `n` bytes de la ROM (fitxer) a la RAM de l'SPC700. */
    fun copia(spc: SnesSpc, rom: RomSnes, fitxer: Int, n: Int, dest: Int) {
        for (k in 0 until n) if (dest + k < 65536 && fitxer + k < rom.d.size) spc.ram[dest + k] = rom.d[fitxer + k]
    }
    /** Mostres que ocupa un pas: executar l'SPC700 fa avançar el DSP (32 cicles per mostra). */
    private val buf = ShortArray(64)
    fun corre(spc: SnesSpc, mostres: Int) { var r = mostres; while (r > 0) { val n = minOf(r, 32); spc.executa(buf, n); r -= n } }
    /** Espera (executant l'SPC700) que el port `p` valgui `v`; fals si no arriba mai. */
    fun espera(spc: SnesSpc, p: Int, v: Int, marge: Boolean = false): Boolean {
        var k = 0
        while (spc.llegeixPort(p) != (v and 255)) { spc.executa(buf, 1); if (++k > 400000) return false }
        // L'SPC700 posa l'ack abans d'haver llegit els altres ports: a la consola la CPU triga prou entre l'ack i la propera escriptura (uns 100 cicles) perquè els llegeixi;
        // sense aquesta espera, la propera escriptura trepitjava els bytes encara no llegits (FF VI: el punter de bucle de les mostres sortia corromput).
        if (marge) spc.executa(buf, 4)          // només si el driver del joc la demana (esperaAck); per defecte, res
        return true
    }
    /** Com `espera`, amb l'espera extra de 4 cicles després de l'ack (afegida per a FF VI): només la fa servir FF VI. */
    fun esperaAck(spc: SnesSpc, p: Int, v: Int): Boolean = espera(spc, p, v, true)
    /** Contrari: espera que el port `p` deixi de valer `v`. */
    fun esperaCanvi(spc: SnesSpc, p: Int, v: Int): Boolean {
        var k = 0
        while (spc.llegeixPort(p) == (v and 255)) { spc.executa(buf, 1); if (++k > 400000) return false }
        return true
    }
    fun w(spc: SnesSpc, p: Int, v: Int) = spc.escriuPort(p, v)
    fun w16(spc: SnesSpc, p: Int, v: Int) { spc.escriuPort(p, v and 255); spc.escriuPort(p + 1, (v shr 8) and 255) }
    /** Adreça lineal de 24 bits (banc:adr) sumada a un desplaçament, retornant (banc, adr). */
    fun u16a(rom: RomSnes, lineal: Int): Int = rom.u16((lineal shr 16) and 255, lineal and 0xFFFF)
    fun u8a(rom: RomSnes, lineal: Int): Int = rom.u8((lineal shr 16) and 255, lineal and 0xFFFF)
}

/**
 * EarthBound (EUA) / Mother 2 (Japó). Desassemblatge: Herringway/ebsrc (`src/audio/change_music.asm`, `load_spc700_data.asm`, `initialize_music_subsystem.asm`, `get_audio_bank.asm`,
 * `src/data/music/dataset_table.asm`, `pack_pointer_table.asm`, `include/constants/music.asm`; `mother2.yml`/`earthbound.yml` donen els desplaçaments dels paquets d'àudio a la ROM).
 * El joc puja a l'SPC700 «paquets» de blocs `[mida][adreça][dades]` acabats en mida 0 (entrada $0500): primer el paquet de seqüència de la fila 0 de la taula de conjunts (programa de so, `main.spc700.bin`
 * a $0500), després, per a la cançó `id`, el paquet de mostres primari, el secundari i el de seqüència (si no valen $FF ni el paquet ja carregat) i, finalment, escriu `id` al port 0.
 */
object DriverEarthBound : SnesDriver {
    override val nom = "EarthBound"
    override val cancons: List<String> = EB_NOMS
    // Primeres sis files de MUSIC_DATASET_TABLE (3 bytes per cançó: mostres primàries, secundàries, seqüència)
    private val SIG_TAULA = intArrayOf(0x00, 0xFF, 0x01, 0x02, 0x03, 0x04, 0x02, 0x03, 0x04, 0x05, 0xFF, 0xFF, 0x05, 0xFF, 0xFF, 0x05, 0xFF, 0xFF)
    // Dos primers punters de MUSIC_PACK_POINTER_TABLE (banc, adreça): EUA $E2:77F0 i $E6:0000; Mother 2 els emmagatzema menys $E2
    private val SIG_PUNTERS_US = intArrayOf(0xE2, 0xF0, 0x77, 0xE6, 0x00, 0x00)
    private val SIG_PUNTERS_JP = intArrayOf(0x00, 0xF0, 0x77, 0x04, 0x00, 0x00)
    private const val N_CANCONS = 191
    private class Taules(val dataset: Int, val punters: Int, val jp: Boolean)
    private fun taules(rom: RomSnes): Taules? {
        if (!rom.hiRom || rom.d.size < 0x300000) return null
        val t = rom.cerca(SIG_TAULA); if (t < 0 || t + N_CANCONS * 3 > rom.d.size) return null
        var jp = false
        var p = rom.cerca(SIG_PUNTERS_US)
        if (p < 0) { p = rom.cerca(SIG_PUNTERS_JP); jp = true }
        if (p < 0) return null
        return Taules(t, p, jp)
    }
    override fun reconeix(rom: RomSnes) = taules(rom) != null
    private fun paquet(spc: SnesSpc, rom: RomSnes, t: Taules, n: Int) {
        val p = t.punters + 3 * n
        var banc = rom.d[p].toInt() and 255
        if (t.jp) banc = (banc + 0xE2) and 255                           // GET_AUDIO_BANK (JPN): + banc d'AUDIO_PACK_108 ($E2)
        val adr = (rom.d[p + 1].toInt() and 255) or ((rom.d[p + 2].toInt() and 255) shl 8)
        S3.blocs(spc, rom, banc, adr)
    }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val t = taules(rom) ?: return
        val fila = (id.coerceIn(1, N_CANCONS) - 1) * 3
        val motor = rom.d[t.dataset + 2].toInt() and 255                // INITIALIZE_MUSIC_SUBSYSTEM: paquet de seqüència de la fila 0
        paquet(spc, rom, t, motor)
        val prim = rom.d[t.dataset + fila].toInt() and 255
        val sec = rom.d[t.dataset + fila + 1].toInt() and 255
        val seq = rom.d[t.dataset + fila + 2].toInt() and 255
        if (prim != 0xFF) paquet(spc, rom, t, prim)                      // CHANGE_MUSIC
        if (sec != 0xFF && sec != motor) paquet(spc, rom, t, sec)
        if (seq != 0xFF) paquet(spc, rom, t, seq)
        spc.arrenca(0x0500)                                              // cada paquet acaba amb el salt a $0500
        S3.corre(spc, 12000)                                             // el motor neteja la RAM i, amb CONTROL=$F0, els ports d'entrada: l'ordre s'escriu després (la CPU espera APUIO0=0)
        spc.escriuPort(0, id.coerceIn(1, N_CANCONS))                     // UNKNOWN_C0ABBD: APUIO0 = número de cançó
    }
}

/**
 * The Legend of Zelda: A Link to the Past (EUA, Zelda no Densetsu: Kamigami no Triforce i la versió recompilada, que fa servir la mateixa ROM). Desassemblatge: spannerisms/jpdasm
 * (`bank_00.asm` `LoadSongBank`, `LoadIntroSongBank`, `LoadOverworldSongs`, `LoadUnderworldSongs`, `LoadCreditsSongs`; `bank_19.asm` `SamplePointers`, `SPCEngine`; `bank_1A.asm`, `bank_1B.asm`).
 * Transferències de l'IPL (`[mida][adreça][dades]`, acabades amb mida 0 i entrada $0800): el bloc d'introducció ($19:8000: punters de mostres, instruments, mostres, motor i sons; continua sense
 * tall fins al banc Overworld $1A:9EF5, ids 1-15) i, per als altres, el d'Underworld ($1B:8000, ids 16-31) o el de Credits ($1A:D380, ids 32-34) en lloc de l'Overworld. L'id és el valor que el motor llegeix del port 0.
 */
object DriverZelda3 : SnesDriver {
    override val nom = "Zelda3"
    override val cancons = listOf("Triforce Intro", "Light World Overture", "Rain", "Bunny Theme", "Lost Woods", "Legends Theme (attract)", "Kakariko Village", "Mirror Warp", "Dark World",
        "Pulling The Master Sword", "Fairy Theme", "Fugitive", "Skull Woods March", "Minigame Theme", "Intro Fanfare", "Hyrule Castle", "Pendant Dungeon", "Cave", "Fanfare", "Sanctuary", "Boss",
        "Crystal Dungeon", "Shop", "Cave 2", "Zelda Rescue", "Crystal Maiden", "Big Fairy", "Suspense", "Agahnim Escapes", "Meeting Ganon", "King Of Thieves", "Triforce Room", "Ending Theme", "Credits")
    override fun reconeix(rom: RomSnes): Boolean {
        if (rom.hiRom || rom.d.size < 0x100000) return false
        return rom.u16(0x19, 0x8000) == 0x0070 && rom.u16(0x19, 0x8002) == 0x3C00 && rom.u16(0x19, 0xFBCA) == 0x0F9E && rom.u16(0x19, 0xFBCC) == 0x0800 &&
            rom.u16(0x1A, 0x9EF5) == 0x2DAE && rom.u16(0x1A, 0x9EF7) == 0xD000 && rom.u16(0x1B, 0x8000) == 0x2CBF && rom.u16(0x1A, 0xD380) == 0x1060
    }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val i = id.coerceIn(1, 34)
        if (i <= 15) S3.blocs(spc, rom, 0x19, 0x8000)                    // el flux d'introducció continua fins al banc Overworld (acaba a $1A:D333)
        else {
            S3.blocs(spc, rom, 0x19, 0x8000, 0x1A, 0x9EF5)               // només l'introducció (motor, mostres i sons), fins on comença el banc Overworld
            if (i <= 31) S3.blocs(spc, rom, 0x1B, 0x8000) else S3.blocs(spc, rom, 0x1A, 0xD380)
        }
        spc.arrenca(0x0800)
        spc.escriuPort(0, i)
    }
}

private val EB_NOMS: List<String> = listOf("Gas station","Naming screen","Setup screen","None2","You won1","Level up","You lose","Battle swirl1","Battle swirl2","What the heck","New friend","You won2","Teleport out","Teleport fail","Falling underground","Dr andonuts lab","Monotoli building","Sloppy house","Neighbours house","Arcade","Pokeys house","Hospital","Nesss house","Paulas theme","Chaos theatre","Hotel","Good morning","Department store","Onett at night 1","Your sanctuary 1","Your sanctuary 2","Giant step","Lilliput steps","Milky well","Rainy circle","Magnet hill","Pink cloud","Lumine hall","Fire spring","Near a boss","Alien investigation","Fire springs hall","Belch base","Zombie threed","Spooky cave","Onett","Fourside","Saturn valley","Monkey caves","Moonside","Dusty dunes desert","Peaceful rest valley","Zombie threed2","Winters","Cave near a boss","Summers","Jackies cafe","Sailing to scaraba","Dalaam","Mu training","Bazaar","Scaraba desert","Pyramid","Deep darkness","Tenda village","Welcome home","Sea of eden","Lost underworld","First step back","Second step back","The place","Giygas awakens","Giygas phase2","Giygas weakened2","Giygas death2","Runaway5 concert 1","Runaway5 tour bus","Runaway5 concert 2","Power","Venus concert","Yellow submarine","Bicycle","Sky runner","Sky runner falling","Bulldozer","Tessie","City bus","Fuzzy pickles","Delivery","Return to your body","Phase distorter time travel","Coffee break","Because i love you","Good friends bad friends","Smiles and tears","Vs cranky lady","Vs spinning robo","Vs struttin evil mushroom","Vs master belch","Vs new age retro hippie","Vs runaway dog","Vs cave boy","Vs your sanctuary boss","Vs kraken","Pokey means business","Inside the dungeon","Megaton walk","Sea of eden2","Explosion","Sky runner crash","Magic cake","Pokeys house buzz buzz","Buzz buzz swatted","Onett at night buzz buzz","Phone call","Knock knock right","Rabbit cave","Onett at night 3","Apple of enlightenment","Hotel of the living dead","Onett intro","Sunrise onett","Someone joins","Enter starman jr","Boarding school","Phase distorter","Phase distorter 2","Boy meets girl","Happy threed","Runaway5 are freed","Flying man","Onett at night 2","Hidden song","Your sanctuary boss","Teleport in","Saturn valley cave","Elevator down","Elevator up","Elevator stop","So 140","Vs master barf","Going to magicant","Leaving magicant","Kraken defeated","Stonehenge destruction","Tessie sighting","Meteor fall","Vs starman jr","Runaway5 help out","Knock knock","Onett after meteor1","Onett after meteor2","Pokey theme","Onett at night buzz buzz2","Your sanctuary boss2","Meteor strike","Attract mode","Name confirmation","Peaceful rest valley2","Soundstone recording giant step","Soundstone recording lilliput steps","Soundstone recording milky well","Soundstone recording rainy circle","Soundstone recording magnet hill","Soundstone recording pink cloud","Soundstone recording lumine hall","Soundstone recording fire spring","Soundstone bgm","Eight melodies","Dalaam intro","Winters intro","Pokey escapes","Good morning moonside","Gas station 2","Title screen","Battle swirl4","Pokey intro","Good morning scaraba","Robotomy1","Pokey escapes2","Return to your body2","Giygas static","Sudden victory","You won3","Giygas phase3","Giygas phase1","Give us strength","Good morning2","Sound stone","Giygas death","Giygas weakened")

/**
 * Final Fantasy VI (EUA, FF3 US). Desassemblatge: everything8215/ff6 (`src/sound/sound_main.asm` al banc $C5, mapa a `notes/rom-map.txt`). `inicia` fa el que la CPU principal fa a `InitSound`
 * (puja a l'SPC700 els sis blocs `[mida][dades]` de la taula $C5:0010/$C5:001C i l'arrenca a $0200) i, amb l'SPC700 en marxa, repeteix amb codi Kotlin `ExecSound` -> `PlaySong` -> `TfrSong` -> `TfrSongScript`
 * per a l'ordre $10 (carregar cançó) amb l'estat de partida nova (cap instrument carregat): ports 0-3, comptador d'acusaments, mostres BRR, punters, multiplicadors, ADSR i script de la cançó.
 * Taules de dades (absolutes, $C5): SampleBRRPtrs $3C5F, SampleLoopStart $3D1C, SampleFreqMult $3D9A, SampleADSR $3E18, SongScriptPtrs $3E96, SongSamples $3F95.
 */
object DriverFf6 : SnesDriver {
    override val nom = "FF6"
    override val cancons: List<String> = FF6_NOMS
    private val SIG = intArrayOf(0x4C, 0x34, 0x00, 0xEA, 0x4C, 0x31, 0x01, 0xEA, 0x4C, 0x1C, 0x03, 0xEA, 0x4C, 0xEA, 0x03, 0xEA)
    override fun reconeix(rom: RomSnes): Boolean {
        if (!rom.hiRom || rom.d.size < 0x300000) return false
        for (k in SIG.indices) if (rom.u8(0xC5, k) != SIG[k]) return false
        return rom.u16(0xC5, 0x28) == 0x3E96 && rom.u16(0xC5, 0x2A) == 0x3C5F && rom.u8(0xC5, 0x3C5E) in 80..100
    }
    private fun lin(banc: Int, adr: Int) = (banc shl 16) or adr
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val song = (id - 1).coerceIn(0, rom.u8(0xC5, 0x3C5E) - 1)
        // InitSound: blocs d'SPC (mida a l'inici de cada un) -> adreces de destí; programa a $0200
        var mida2 = 0
        for (i in 0 until 6) {
            val src = rom.u16(0xC5, 0x10 + 2 * i); val dest = rom.u16(0xC5, 0x1C + 2 * i)
            val n = rom.u16(0xC5, src)
            if (i == 2) mida2 = n
            val dades = rom.bytes(0xC5, src + 2, n)
            for (k in 0 until n) if (dest + k < 65536) spc.ram[dest + k] = dades[k]
        }
        spc.arrenca(0x0200)
        S3.corre(spc, 4000)
        val m = ByteArray(0x300)                                          // DP $1300-$15FF
        fun g8(a: Int) = m[a - 0x1300].toInt() and 255
        fun g16(a: Int) = g8(a) or (g8(a + 1) shl 8)
        fun s16(a: Int, v: Int) { m[a - 0x1300] = v.toByte(); m[a + 1 - 0x1300] = (v shr 8).toByte() }
        s16(0x13A0, (mida2 + 0x4800) and 0xFFFF)                         // $a0 = destí de la primera mostra d'instrument
        var cnt = 0
        fun espera(a: Int): Boolean = S3.esperaAck(spc, 0, a)
        fun waitTfr(a: Int): Int { S3.w(spc, 0, a); espera(a); return (a + 1) and 0x7F }
        // PlaySong (ordre $10; CheckPauseSong)
        var cmd = 0x10
        var x = 0
        while (true) { val b = rom.u8(0xC5, 0x06F9 + x); if (b >= 0x80) break; if (b == song) { cmd = cmd or 4; break }; x++ }
        S3.w(spc, 2, 0xFF); S3.w(spc, 1, song); S3.w(spc, 0, cmd)
        if (!espera(cmd)) return
        cnt = (cmd + 1) and 0x7F
        // instruments de la cançó (16 paraules) i els que cal transferir (cap carregat)
        val ss = lin(0xC5, 0x3F95) + song * 32
        var nl = 0
        for (k in 0 until 16) {
            val v = S3.u16a(rom, ss + 2 * k)
            s16(0x1400 + 2 * k, v)
            if (v != 0) { var ja = false; for (j in 0 until nl) if (g16(0x1420 + 2 * j) == v) ja = true; if (!ja) { s16(0x1420 + 2 * nl, v); nl++ } }
        }
        // TfrSong / TfrSamples
        var sx = 0
        for (j in 0 until nl) {
            val id1 = g16(0x1420 + 2 * j)
            s16(0x1320 + sx, id1)
            val p = rom.u24(0xC5, 0x3C5F + 3 * (id1 - 1))
            S3.w(spc, 2, g8(0x13A0 + sx)); S3.w(spc, 3, g8(0x13A1 + sx)); S3.w(spc, 1, 3); S3.w(spc, 0, cnt)
            if (!espera(cnt)) return
            cnt = (cnt + 1) and 0x7F
            val size = S3.u16a(rom, p)
            s16(0x1360 + sx, size); s16(0x13A2 + sx, (g16(0x13A0 + sx) + size) and 0xFFFF)
            var rem = size; var q = p + 2
            while (rem > 0) {
                S3.w(spc, 1, S3.u8a(rom, q)); S3.w(spc, 2, S3.u8a(rom, q + 1)); S3.w(spc, 3, S3.u8a(rom, q + 2)); q += 3
                cnt = waitTfr(cnt); if (spc.llegeixPort(0) != ((cnt - 1) and 0x7F)) return
                rem -= 3
            }
            cnt = 0xF0
            sx += 2
        }
        // TfrSongScript: punters BRR + inici de bucle
        var pp = 0x1480
        for (k in 0 until 16) {
            val inst = g16(0x1400 + 2 * k)
            var trobat = -1
            if (inst != 0) for (y in 0 until 0x40 step 2) if (g16(0x1320 + y) == inst) { trobat = y; break }
            if (trobat >= 0) {
                val d = g16(0x13A0 + trobat)
                s16(pp, d); s16(pp + 2, (d + rom.u16(0xC5, 0x3D1C + 2 * (inst - 1))) and 0xFFFF)
            } else { s16(pp, 0); s16(pp + 2, 0) }
            pp += 4
        }
        fun inici(dest: Int) {
            S3.w(spc, 2, dest and 255); S3.w(spc, 3, dest shr 8); S3.w(spc, 1, 2); S3.w(spc, 0, cnt)
            espera(cnt); cnt = (cnt + 1) and 0x7F
        }
        inici(0x1B80)
        for (k in 0 until 0x40 step 2) { S3.w(spc, 2, g8(0x1480 + k)); S3.w(spc, 3, g8(0x1481 + k)); cnt = waitTfr(cnt) }
        cnt = 0xF0
        for ((dest, taula) in listOf(0x1A40 to 0x3D9A, 0x1AC0 to 0x3E18)) {
            inici(dest)
            for (k in 0 until 16) {
                val inst = g16(0x1400 + 2 * k)
                val v = S3.u16a(rom, lin(0xC5, taula) + (((inst - 1) * 2) and 0xFFFF))
                S3.w16(spc, 2, v); cnt = waitTfr(cnt)
            }
            cnt = 0xF0
        }
        // script de la cançó a $1C00
        inici(0x1C00)
        val sp = rom.u24(0xC5, 0x3E96 + 3 * song)
        val mida = S3.u16a(rom, sp)
        var q = sp + 2; var r = mida
        do {
            S3.w(spc, 2, S3.u8a(rom, q)); S3.w(spc, 3, S3.u8a(rom, q + 1)); q += 2
            cnt = waitTfr(cnt); r -= 2
        } while (r >= 0)
        S3.w(spc, 1, 0xF0); S3.w(spc, 0, 0xF0)
        S3.corre(spc, 4000)
    }
}

private val FF6_NOMS: List<String> = listOf("Silence","Prelude","Opening theme 1","Opening theme 2","Opening theme 3","Awakening","Terra","Shadow","Strago","Gau","Figaro","Coin song","Cyan","Locke","Forever rachel","Relm","Setzer","Epitaph","Celes","Techno de chocobo","Decisive battle","Johnny c bad","Kefka","Narshe","Phantom forest","Veldt","Save them","Gestahl","Troops march on","Under martial law","Waterfall","Metamorphosis","Phantom train","Esper world","Grand finale 2","Mt kolts","Battle theme","Fanfare","Wedding waltz 1","Aria di mezzo caraterre","Serpent trench","Slam shuffle","Kids run through the city","Huh","Grand finale 1","Gogo","Returners","Victory fanfare","Umaro","Mog","The unforgiven","Fierce battle","Day after","Blackjack","Catastrophe","Magic house","Nighty night","Wind","Windy shores","Dancing mad 1 2 3","Train braking","Spinach rag","Rest in peace","Chocobos running","Dream of a train","Overture 1","Overture 2","Overture 3","Wedding waltz 2","Wedding waltz 3","Wedding waltz 4","Devils lab","Fire explosion","Cranes rising","Burning house","New continent","Searching for friends","Fanatics","Last dungeon","Dark world","Dancing mad 5","Song 81","Dancing mad 4","Ending theme 1","Ending theme 2")

/**
 * Final Fantasy V (EUA/Japó, ROM HiROM). Desassemblatge: everything8215/ff5 (`src/sound/sound-main.asm`, banc $C4). Com amb Final Fantasy VI, `inicia` fa `InitSound` (sis blocs `[mida][dades]` de la taula
 * $C4:0008, destins a $C4:0014, programa a $0200) i, amb l'SPC700 en marxa, repeteix `ExecSound` -> `_0188` (ordre 1, o 3 si la cançó suspèn l'anterior, llista $C4:0641) amb l'estat de partida
 * nova: script de la cançó a $1C00 (comptador d'acusaments des de 5), mostres BRR de cada instrument a continuació de les d'efectes, i les taules de multiplicadors ($1A40), punters i inicis de bucle ($1B80) i ADSR ($1AC0).
 */
object DriverFf5 : SnesDriver {
    override val nom = "FF5"
    override val cancons: List<String> = FF5_NOMS
    private val SIG = intArrayOf(0x4C, 0x2C, 0x00, 0xEA, 0x4C, 0x4C, 0x01, 0xEA)
    private val DEST = intArrayOf(0x0200, 0x2C00, 0x4800, 0x1B00, 0x1A80, 0x1A00)
    override fun reconeix(rom: RomSnes): Boolean {
        if (!rom.hiRom || rom.d.size < 0x200000) return false
        for (k in SIG.indices) if (rom.u8(0xC4, k) != SIG[k]) return false
        for (k in DEST.indices) if (rom.u16(0xC4, 0x14 + 2 * k) != DEST[k]) return false
        return rom.u16(0xC4, 0x08) == 0x064D
    }
    private fun lin(banc: Int, adr: Int) = (banc shl 16) or adr
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        var mida2 = 0
        for (i in 0 until 6) {
            val src = rom.u16(0xC4, 0x08 + 2 * i); val dest = rom.u16(0xC4, 0x14 + 2 * i)
            val n = rom.u16(0xC4, src)
            if (i == 2) mida2 = n
            val dades = rom.bytes(0xC4, src + 2, n)
            for (k in 0 until n) if (dest + k < 65536) spc.ram[dest + k] = dades[k]
        }
        spc.arrenca(0x0200)
        S3.corre(spc, 4000)
        val tScript = rom.u16(0xC4, 0x20); val tBrr = rom.u16(0xC4, 0x22); val tLoop = rom.u16(0xC4, 0x24)
        val tAdsr = rom.u16(0xC4, 0x26); val tFreq = rom.u16(0xC4, 0x28); val tSamples = rom.u16(0xC4, 0x2A)
        val song = (id - 1).coerceIn(0, 71)
        val m = ByteArray(0x200)                                          // DP $1D00-$1EFF
        fun g8(a: Int) = m[a - 0x1D00].toInt() and 255
        fun g16(a: Int) = g8(a) or (g8(a + 1) shl 8)
        fun s8(a: Int, v: Int) { m[a - 0x1D00] = v.toByte() }
        fun s16(a: Int, v: Int) { s8(a, v); s8(a + 1, v shr 8) }
        s16(0x1D48, (mida2 + 0x4800) and 0xFFFF)
        // _05e0: ordre 3 si la cançó suspèn l'anterior
        var cmd = 1
        var x = 0
        while (true) { val b = rom.u8(0xC4, 0x0641 + x); if (b >= 0x80) break; if (b == song) { cmd = 3; break }; x++ }
        S3.w(spc, 1, song); S3.w(spc, 2, 0x08); S3.w(spc, 3, 0x0F)
        S3.esperaCanvi(spc, 0, cmd)
        S3.w(spc, 0, cmd); if (!S3.espera(spc, 0, cmd)) return
        S3.w(spc, 1, 2); S3.w16(spc, 2, 0x1C00); S3.w(spc, 0, 2); if (!S3.espera(spc, 0, 2)) return
        // script de la cançó
        val sp = rom.u24(0xC4, tScript + 3 * song)
        val mida = S3.u16a(rom, sp)
        var q = sp + 2; var r = mida; var cnt = 5
        do {
            S3.w(spc, 2, S3.u8a(rom, q)); S3.w(spc, 3, S3.u8a(rom, q + 1)); q += 2
            S3.w(spc, 0, cnt); if (!S3.espera(spc, 0, cnt)) return
            cnt = (cnt + 1) and 255; if (cnt == 0) cnt = 1
            r -= 2
        } while (r >= 0)
        // instruments de la cançó i els que cal transferir (cap de carregat)
        val ss = lin(0xC4, tSamples) + song * 32
        var nl = 0
        for (k in 0 until 16) {
            val v = S3.u16a(rom, ss + 2 * k)
            s16(0x1DA8 + 2 * k, v)
            if (v != 0) { s16(0x1DC8 + 2 * nl, v); nl++ }
        }
        if (nl > 0) {
            S3.w(spc, 1, 3); S3.w(spc, 2, g8(0x1D48)); S3.w(spc, 3, g8(0x1D49)); S3.w(spc, 0, 0)
            if (!S3.espera(spc, 0, 0)) return
            var c = 1; var slot = 0
            for (k in 0 until nl) {
                val inst = g8(0x1DC8 + 2 * k)
                s8(0x1D28 + slot, inst)
                val p = rom.u24(0xC4, tBrr + 3 * (inst - 1))
                val size = S3.u16a(rom, p)
                s16(0x1D68 + slot, size); s16(0x1D4A + slot, (g16(0x1D48 + slot) + size) and 0xFFFF)
                slot += 2
                var rem = size; var b = p + 2
                while (rem > 0) {
                    S3.w(spc, 1, S3.u8a(rom, b)); S3.w(spc, 2, S3.u8a(rom, b + 1)); S3.w(spc, 3, S3.u8a(rom, b + 2)); b += 3
                    S3.w(spc, 0, c); if (!S3.espera(spc, 0, c)) return
                    c = (c + 1) and 255; if (c == 0) c = 1
                    rem -= 3
                }
            }
        }
        if (g16(0x1DA8) != 0) {
            var pf = 0x1E00; var pl = 0x1E40; var pa = 0x1EC0
            for (k in 0 until 16) {
                val inst = g16(0x1DA8 + 2 * k); if (inst == 0) break
                var y = 0; while (y < 0x20 && g16(0x1D28 + y) != inst) y += 2
                if (y >= 0x20) y = 0
                val xi = ((inst - 1) * 2) and 0xFFFF
                s16(pf, S3.u16a(rom, lin(0xC4, tFreq) + xi)); pf += 2
                val d = g16(0x1D48 + y)
                s16(pl, d); pl += 2
                s16(pl, (d + S3.u16a(rom, lin(0xC4, tLoop) + xi)) and 0xFFFF); pl += 2
                s16(pa, S3.u16a(rom, lin(0xC4, tAdsr) + xi)); pa += 2
            }
            for ((dest, base, n) in listOf(Triple(0x1A40, 0x1E00, 32), Triple(0x1B80, 0x1E40, 64), Triple(0x1AC0, 0x1EC0, 32))) {
                S3.w(spc, 1, 2); S3.w16(spc, 2, dest); S3.w(spc, 0, 0); if (!S3.espera(spc, 0, 0)) return
                for (k in 0 until n) {
                    S3.w(spc, 2, g8(base + 2 * k)); S3.w(spc, 3, g8(base + 2 * k + 1))
                    val c = (k + 1) and 255; S3.w(spc, 0, c); if (!S3.espera(spc, 0, c)) return
                }
            }
        }
        S3.w(spc, 1, 0); S3.w(spc, 0, 0)
        S3.corre(spc, 4000)
    }
}

private val FF5_NOMS: List<String> = listOf("Ahead on our way","The fierce battle","A presentiment","Go go boko","Pirates ahoy","Tenderness in the air","Fate in haze","Critter tripper fritter","The prelude","The last battle","Requiem","Nostalgia","Cursed earths","Lennas theme","Victorys fanfare","Deception","The day will come","Silence","Exdeaths castle","My home sweet home","Waltz suomi","Sealed away","The four warriors of dawn","Danger","The firepowered ship","As i feel you feel","Mambo de chocobo","Music box song","Intension of the earth","The dragon spreads its wings","Beyond the deep blue sea","Prelude of empty skies","Searching for the light","Harvest","Gilgamesh","Four valiant hearts","The book of sealings","What","Hurry hurry","Unknown lands","The airship","Fanfare 1","Fanfare 2","The battle","Walking the snowy mountains","The evil lord exdeath","The castle of dawn","Im a dancer","Reminiscence","Run","The ancient library","Royal palace","Good night","Piano lesson 1","Piano lesson 2","Piano lesson 3","Piano lesson 4","Piano lesson 5","Piano lesson 6","Piano lesson 7","Piano lesson 8","Musica machina","A meteor is falling","The land unknown","The decisive battle","The silent beyond","Dear friends","Final fantasy","A new origin","Crickets chirping","A shore","The tide rolls in")

/**
 * Final Fantasy IV (Final Fantasy II EUA / Japó, ROM LoROM). Desassemblatge: everything8215/ff4 (`sound/sound.asm`, `sound/sound_data.asm`; `notes/ff4j-sfc-rom-map.txt`: programa d'SPC a $04:8683-$04:BBCF, dades de so
 * relatives a $04:C000). `inicia` puja el programa d'SPC (blocs `[mida][adreça][dades]` des de $04:8683, entrada al bloc final) com `InitSound` i, amb l'SPC700 en marxa, repeteix `PlaySong` (ordre 1) amb
 * l'estat de partida nova: script de la cançó (1 byte per acusament, comptador des de l'ordre), ordre $11 (cap mostra carregada), mostres BRR a partir de $3000, inicis de bucle i multiplicadors de freqüència.
 */
object DriverFf4 : SnesDriver {
    override val nom = "FF4"
    override val cancons: List<String> = FF4_NOMS
    private val SIG = intArrayOf(0x20, 0x08, 0x80, 0x6B, 0x20, 0x07, 0x81, 0x6B)
    private const val SD = 0x24000                                         // $04:C000 (SoundData) al fitxer
    override fun reconeix(rom: RomSnes): Boolean {
        if (rom.hiRom || rom.d.size < 0x100000) return false
        for (k in SIG.indices) if (rom.u8(4, 0x8000 + k) != SIG[k]) return false
        val mid = rom.u16(4, 0x8683)
        return mid in 1..0x3000 && rom.u24(4, 0xC000) != 0 && rom.u24(4, 0xC000) < 0x80000
    }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val ent = S3.blocs(spc, rom, 4, 0x8683)
        if (ent < 0) return
        spc.arrenca(ent)
        S3.corre(spc, 4000)
        val song = (id - 1).coerceIn(0, 69)
        fun d(o: Int) = rom.d[o].toInt() and 255
        fun u16(o: Int) = d(o) or (d(o + 1) shl 8)
        fun u24(o: Int) = d(o) or (d(o + 1) shl 8) or (d(o + 2) shl 16)
        val tScript = SD + u24(SD); val tBrr = SD + u24(SD + 3); val tSongSamples = SD + u24(SD + 6)
        val tLoop = SD + u24(SD + 9); val tFreq = SD + u24(SD + 12)
        if (song == 0) return                                             // la cançó 0 és el silenci (i coincideix amb $05 = 0: no fa res)
        val cmd = 1
        var cnt = cmd
        fun sig() { cnt = (cnt + 1) and 255; if (cnt == 0) cnt = 1 }
        // script de la cançó: mida (2 bytes) i un byte per acusament
        val sc = SD + u24(tScript + 3 * song)
        val mida = u16(sc)
        for (k in 0 until mida) {
            S3.w(spc, 2, d(sc + 2 + k)); S3.w(spc, 0, cnt)
            if (!S3.espera(spc, 0, cnt)) return
            sig()
        }
        // mostres de la cançó (16 paraules) i ordre $11: cap mostra carregada
        val llista = IntArray(16) { u16(tSongSamples + song * 32 + 2 * it) }
        S3.w(spc, 1, 0x11); S3.w(spc, 0, 0)
        if (!S3.espera(spc, 0, 0)) return
        cnt = 1
        val ids = IntArray(16); val dest = IntArray(16); var n = 0
        var desti = 0x3000
        for (v in llista) {
            if (v == 0) continue
            val b = SD + u24(tBrr + 3 * v)
            val size = u16(b)
            ids[n] = v; dest[n] = desti; n++
            var rem = size; var p = b + 2
            while (rem > 0) {
                S3.w(spc, 1, d(p)); S3.w(spc, 2, d(p + 1)); S3.w(spc, 3, d(p + 2)); p += 3
                S3.w(spc, 0, cnt); if (!S3.espera(spc, 0, cnt)) return
                sig(); rem -= 3
            }
            desti = (desti + size) and 0xFFFF
        }
        // inicis de mostra i de bucle (cada un més l'adreça de destí) i multiplicadors
        cnt = 0
        for (v in llista) {
            if (v == 0) break
            var x = 0; while (x < n && ids[x] != v) x++
            if (x >= n) x = 0
            val b = tLoop + v * 4
            S3.w16(spc, 2, (u16(b) + dest[x]) and 0xFFFF); S3.w(spc, 0, cnt); if (!S3.espera(spc, 0, cnt)) return; cnt = (cnt + 1) and 255
            S3.w16(spc, 2, (u16(b + 2) + dest[x]) and 0xFFFF); S3.w(spc, 0, cnt); if (!S3.espera(spc, 0, cnt)) return; cnt = (cnt + 1) and 255
        }
        cnt = 0
        for (v in llista) {
            if (v == 0) break
            S3.w16(spc, 2, u16(tFreq + v)); S3.w(spc, 0, cnt); if (!S3.espera(spc, 0, cnt)) return; cnt = (cnt + 1) and 255
        }
        S3.w(spc, 1, 0); S3.w(spc, 0, cnt); S3.espera(spc, 0, cnt)
        S3.w(spc, 0, 0)
        S3.corre(spc, 4000)
    }
}

private val FF4_NOMS: List<String> = listOf("Silence","Prologue","Long Way to Go","Unknown Fanfare","Chocobo Chocobo","Samba de Chocobo","Land of Dwarves","The Final Battle","Victory Fanfare","Welcome to Our Town","Rydia","The Dreadful Fight","Mt. Ordeals","Main Theme","The Big Whale","Cry in Sorrow","Sleepy Time","Golbeza Clad in the Dark","Edward's Harp","Theme of Love","Kingdom Baron","The Prelude","Somewhere in the World","Tower of Bab'il","The Airship","Tower of Zot","Battle 2","Within the Giant","Illusionary World","Bomb Ring","The Lunarians","Dun-Dun-Dun!","Giott, King of the Dwarves","Palom & Porom","Dancing Calbrena","Run!","Hey Cid!","Into the Darkness","Dancer Theme","Battle 1","Damcyan Castle","Fanfare","Sorrow Intro","Chocobo Forest","The Red Wings","Suspicion","Fabul","Another Fanfare","Hello, Big Chocobo","Another Moon","Troian Beauty","Mystic Mysidia","The Core","Epilogue 1","Epilogue 2","Epilogue 3","So 56","So 57","So 58","So 59","So 60","So 61","So 62","So 63","So 64","So 65","So 66","So 67","Waterfall","So 69")

/**
 * Shin Megami Tensei (J1.0, Super Famicom, LoROM). Desassemblatge: spannerisms/smt1dasm (`bank_0C.asm`: `InitializeSPC`, `MusicTransferCommand`, `SyncAPUPort1`, `SongPacks`, `InitialAPUData`; `spc.asm`).
 * El joc puja el motor i les cançons residents amb l'IPL (`InitialAPUData`, $0C:B508, un bloc de $3795 bytes a $0300) i, per a les cançons $44-$5F, les transfereix amb el protocol propi de
 * `MusicTransferCommand` (ordre $FE, resposta $EE al port 1, paraula de comptador als ports 0-1, dades de 16 bits al port 2-3) abans d'escriure el número de cançó al port 0.
 */
object DriverSmt1 : SnesDriver {
    override val nom = "SMT1"
    override val cancons: List<String> = SMT_NOMS
    override fun reconeix(rom: RomSnes): Boolean {
        if (rom.hiRom || rom.d.size < 0x180000) return false
        return rom.u16(0x0C, 0xB508) == 0x3795 && rom.u16(0x0C, 0xB50A) == 0x0300 && rom.u16(0x0C, 0xECA1) == 0 && rom.u16(0x0C, 0xECA3) == 0x0300 && rom.u16(0x0C, 0x814C) == 0x8184
    }
    private fun esperaW(spc: SnesSpc, v: Int): Boolean {
        var k = 0
        while (spc.llegeixPort(0) != (v and 255) || spc.llegeixPort(1) != ((v shr 8) and 255)) { S3.corre(spc, 1); if (++k > 400000) return false }
        return true
    }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val s = id.coerceIn(1, 0x5F)
        val ent = S3.blocs(spc, rom, 0x0C, 0xB508)
        if (ent < 0) return
        spc.arrenca(ent)
        S3.corre(spc, 4000)
        if (s >= 0x44) {
            var pk = rom.u16(0x0C, 0x814C + 2 * (s - 0x44))                // SongPacks (adreça al banc $0C)
            if (!S3.espera(spc, 1, 0)) return
            S3.w(spc, 0, 0xFE)
            while (true) {
                val lo = rom.u16(0x0C, pk); val bk = rom.u8(0x0C, pk + 2)
                if (!S3.espera(spc, 1, 0xEE)) return
                val w0 = rom.u16(bk, lo)
                S3.w(spc, 0, w0 and 255); S3.w(spc, 1, w0 shr 8)
                var n = w0 and 0x3FFF
                S3.w16(spc, 2, rom.u16(bk, lo + 2))
                if (!esperaW(spc, 0)) return
                var cnt = 0; var y = 4
                while (true) {
                    S3.w16(spc, 2, rom.u16(bk, lo + y))
                    S3.w(spc, 0, cnt and 255); S3.w(spc, 1, cnt shr 8)
                    val seg = (cnt + 1) and 0xFFFF
                    if (!esperaW(spc, seg)) return
                    cnt = seg; y += 2
                    n--; if (n <= 0) break
                    n--; if (n <= 0) break
                }
                pk += 4
                if (rom.u16(0x0C, pk) == 0) break
                S3.w(spc, 0, 0xFF); S3.w(spc, 1, 0xFF)
            }
            S3.w(spc, 0, 0); S3.w(spc, 1, 0)
            S3.corre(spc, 2000)
        }
        S3.espera(spc, 1, 0)
        S3.w(spc, 0, s)
        S3.corre(spc, 4000)
    }
}

private val SMT_NOMS: List<String> = listOf("So 1","So 2","So 3","So 4","So 5","So 6","So 7","So 8","So 9","So 10","So 11","So 12","So 13","So 14","So 15","So 16","So 17","So 18","So 19","So 20","So 21","So 22","So 23","So 24","So 25","So 26","So 27","So 28","So 29","So 30","So 31","So 32","So 33","So 34","So 35","So 36","So 37","So 38","So 39","So 40","So 41","So 42","So 43","So 44","So 45","So 46","So 47","So 48","So 49","So 50","So 51","So 52","So 53","So 54","So 55","So 56","So 57","So 58","So 59","So 60","So 61","So 62","So 63","So 64","So 65","So 66","So 67","Jakyou","Law","Chaos","Neutral","Ginza","Cathedral","Shibuya","Shitenou's Lair","Embassy","Arcade","Kichijoi","Ruins","Shop","Boss Battle","Dream","Home","Pascal's Theme","Unused song","Game Over","Terminal","Epilogue","Ginza","Ginza","Ginza","Ginza","Title - opening","Title screen","Ending")
val DRIVERS_SNES_S3: List<SnesDriver> = listOf(DriverEarthBound, DriverZelda3, DriverFf6, DriverFf5, DriverFf4, DriverSmt1)
