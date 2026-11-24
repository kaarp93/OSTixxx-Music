package cat.gbdsmixxx

/*
 * Lot S2 de drivers de SNES (la música s'executa a l'SPC700 amb el programa de so original del joc; aquí es reprodueix amb Kotlin el que fa la CPU principal per pujar-lo i demanar
 * la cançó). Cap ROM al repo: tot surt de la ROM de l'usuari.
 */

private fun s2Copia(spc: SnesSpc, dades: ByteArray, dest: Int) {
    val n = minOf(dades.size, 0x10000 - dest)
    if (n > 0) System.arraycopy(dades, 0, spc.ram, dest, n)
}

/** Bloc `[mida16][destí16][dades]` a `banc:adr`; retorna l'adreça (dins el banc) del que segueix. */
private fun s2Bloc(spc: SnesSpc, rom: RomSnes, banc: Int, adr: Int): Int {
    val n = rom.u16(banc, adr)
    val dest = rom.u16(banc, adr + 2)
    s2Copia(spc, rom.bytes(banc, adr + 4, n), dest)
    return adr + 4 + n
}

private val s2Buf = ThreadLocal.withInitial { ShortArray(512) }

/** Deixa córrer l'SPC700 `mostres` mostres (sense fer servir el so): per als jocs que cal esperar-ne una resposta durant `inicia`. */
private fun s2Corre(spc: SnesSpc, mostres: Int) { spc.executa(s2Buf.get()!!, mostres) }

private fun s2Noms(s: String) = s.split(',').map { it.trim() }

/* ================================================================================================================================================================ */

/**
 * Donkey Kong Country (SNES, HiROM 4 MB, USA v1.0). De Yoshifanatic1/Donkey-Kong-Country-1-Disassembly (les etiquetes `DATA_xxxxxx` porten l'adreça): el programa de so de Rare
 * (`DATA_8AA36A`, `$D7E` bytes a `$04E0`; els primers 256 bytes són la taula «id de mostra -> índex del directori», que la CPU hi escriu després) i els efectes globals
 * (`DATA_C92D95`, bloc `[mida][destí][dades]`) es pugen al principi; per a cada cançó `n` (identificador intern 0-26 = `!Define_DKC1_MusicID_*`) la CPU (`CODE_B99036`) puja les mostres
 * de la llista global (`DATA_8AB245`, ids de mostra acabats en `$FFFF`) i de la llista de la cançó (`DATA_8AB20D[n]`) -- cada mostra és a `DATA_89FF00 + 3*id` amb capçalera `[inici
 * del bucle][mida]`, es posa a `$3300+` i el directori (inici, bucle) a `$3200+` --, després el bloc de la cançó (`DATA_8AB159 + 3*n`) i salta a `$05E0`; quan el programa respon
 * `$FF` al port 1 s'envia l'ordre `$FE` (inici) amb el port 0 canviat (`CODE_8AB1AA`). Es reconeix pel títol de la capçalera i els bytes d'entrada del programa de so (`CLRP; MOV
 * X,#$FF; MOV SP,X; MOV A,$F4; MOV $04D6,A`). No comprovat amb cap ROM (no se'n té cap): escrit des del desassemblatge.
 */
object DriverDkc1 : SnesDriver {
    override val nom = "DKC1"
    private val NOMS = s2Noms("DK Island Swing, Cave Dweller Concert, Misty Menace, Aquatic Ambience, Mine Cart Madness, Northern Hemispheres, Voices Of The Temple, Fear Factory, Life In The Mines, Title Theme, Splash Screen Fanfare, Ice Cave Chant, Simian Segue, Forest Frenzy, Credits Concerto, Game Over, Bonus Room Blitz, Death Music, Victory, Treetop Rock, Funky's Fugue, Bad Boss Boogie, Candy's Love Song, Cranky's Theme, Gangplank Galleon, Failure, Beat Level")
    override val cancons = NOMS
    private val SIG = intArrayOf(0x20, 0xCD, 0xFF, 0xBD, 0xE4, 0xF4, 0xC5, 0xD6, 0x04)
    override fun reconeix(rom: RomSnes): Boolean {
        if (!rom.hiRom || rom.d.size < 0x400000 || rom.titol != "DONKEY KONG COUNTRY") return false
        return SIG.indices.all { rom.u8(0x8A, 0xA36A + 0x100 + it) == SIG[it] }
    }
    private var pas = 0
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val cn = (id - 1).coerceIn(0, NOMS.size - 1)
        s2Copia(spc, rom.bytes(0x8A, 0xA36A, 0xD7E), 0x04E0)                   // programa de so
        s2Bloc(spc, rom, 0xC9, 0x2D95)                                          // efectes globals
        val taula = ByteArray(256)
        var dir = 0x3200; var mostra = 0x3300; var cont = 0
        fun llista(adr: Int) {
            var a = adr
            while (true) {
                val sid = rom.u16(0x8A, a); a += 2
                if (sid == 0xFFFF) break
                taula[sid and 255] = cont.toByte(); cont++
                val p = rom.u24(0x89, 0xFF00 + 3 * sid)
                val banc = p shr 16; val ad = p and 0xFFFF
                val bucle = rom.u16(banc, ad); val mida = rom.u16(banc, ad + 2)
                s2Copia(spc, rom.bytes(banc, ad + 4, mida), mostra)
                val dd = byteArrayOf(mostra.toByte(), (mostra shr 8).toByte(), (mostra + bucle).toByte(), ((mostra + bucle) shr 8).toByte())
                s2Copia(spc, dd, dir)
                dir += 4; mostra += mida
            }
        }
        llista(0xB245)
        llista(rom.u16(0x8A, 0xB20D + 2 * cn))
        s2Copia(spc, taula.copyOf(255), 0x04E0)
        val p = rom.u24(0x8A, 0xB159 + 3 * cn)
        s2Bloc(spc, rom, p shr 16, p and 0xFFFF)
        spc.arrenca(0x05E0)
        spc.escriuPort(0, 0x00)
        pas = 0
    }
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {
        if (pas == 0 && spc.llegeixPort(1) == 0xFF && spc.llegeixPort(0) == 0x00) {
            spc.escriuPort(1, 0xFE); spc.escriuPort(2, 0x00); spc.escriuPort(0, 0x81)
            pas = 1
        }
    }
}

/* ================================================================================================================================================================ */

/**
 * Super Ghouls 'n Ghosts (SNES, LoROM 1 MB, versions US/JP). De fredyeye/super-ghouls-n-ghosts-disassembly: tot el programa de so de Capcom (codi, seqüències de música i mostres) es
 * puja d'un cop (`_01931E`, protocol de l'IPL) des de `spc_code_start` (`$06:8000`) com una llista de blocs `[mida][destí][dades]` acabada amb mida 0 i l'adreça d'entrada (`$0300`).
 * Les cançons són a la taula de `$0E02` (identificadors de `sound_defines.asm`); la CPU espera que l'SPC700 posi `1` al port 0 i hi escriu l'id (`_00853D`). Es reconeix pel primer bloc
 * (`$0933` bytes a `$0300`, `CLRP; MOV X,#$CF; MOV SP,X`). No comprovat amb cap ROM: escrit des del desassemblat.
 */
object DriverSgng : SnesDriver {
    override val nom = "SGnG"
    private val IDS = intArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16)
    override val cancons = s2Noms("Stage 1, Stage 2, Stage 5, Stage 4, Stage 3, Stage 6-7, Samael, Stage 1 boss, Stage 2 boss, Stage 5 boss, Stage 4 boss, Stage 3 boss, Stage 6-7 boss, Defeat boss, Game over, Map, Continue, Talk princess, Intro, Ending")
    private val SIG = intArrayOf(0x33, 0x09, 0x00, 0x03, 0x20, 0xCD, 0xCF, 0xBD)
    override fun reconeix(rom: RomSnes): Boolean = !rom.hiRom && rom.d.size >= 0x100000 && SIG.indices.all { rom.u8(0x06, 0x8000 + it) == SIG[it] }
    private var pas = 0
    private var cmd = 0
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        cmd = IDS[(id - 1).coerceIn(0, IDS.size - 1)]
        var o = rom.fitxer(0x06, 0x8000)
        var entrada = 0x0300
        while (o >= 0 && o + 4 <= rom.d.size) {
            val n = (rom.d[o].toInt() and 255) or ((rom.d[o + 1].toInt() and 255) shl 8)
            val dest = (rom.d[o + 2].toInt() and 255) or ((rom.d[o + 3].toInt() and 255) shl 8)
            if (n == 0) { entrada = dest; break }
            if (o + 4 + n > rom.d.size) break
            s2Copia(spc, rom.d.copyOfRange(o + 4, o + 4 + n), dest)
            o += 4 + n
        }
        spc.arrenca(entrada)
        pas = 0
    }
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {
        if (pas == 0 && spc.llegeixPort(0) == 1) { spc.escriuPort(0, cmd); pas = 1 }
    }
}

/* ================================================================================================================================================================ */

/**
 * Zombies Ate My Neighbors (SNES, LoROM 1 MB, USA). De moshferatu/zombies-ate-my-neighbors-pc-port (`docs/asset-formats.md`, `src/assets/music.c`, `src/sound/driver.c`): el
 * programa de so (David Warhol, 1992) es forma amb `$91:8000` (`$8000` bytes) + `$95:BA3D` (`$1A86` bytes), que són blocs `[mida][destí][dades]` acabats en mida 0 + adreça d'entrada
 * (2999 bytes a `$0600` i 36547 de mostres a `$1340`). La resta de dades (taula de 16 conjunts de 4 bytes a `$80:CCDE`: adreça + banc de 16 bits; 2-11 = cançons, 12-15 = jocs de mostres)
 * es puja a través del propi programa, una ordre per byte: `$2141` = paràmetre, `$2142` = ordre, `$2143` = comptador (comença a `$A2` i s'incrementa; la CPU espera que l'SPC700 l'ecoï
 * abans de la següent). Una cançó: ordre `$08` (paràmetre = conjunt), per bloc `[mida16][bytes]` ordre `$0A` (paràmetre = els dos bytes de la mida en OR) i `$06` per byte, el conjunt de
 * mostres 15 (sense `$08`) i `$01` amb paràmetre 0 per engegar la cançó (el `$14` és un so amb mostra pròpia). El desassemblatge no diu quin joc de mostres va amb cada cançó (és al registre de cada nivell): s'usa el 3
 * (el de la pantalla de títol i del nivell 1). Els noms de cançó no són al desassemblatge: «So N». Es reconeix pels quatre primers bytes del bloc del programa i la primera entrada de
 * la taula. Comprovat amb la ROM USA: sonen les 10 cançons.
 */
object DriverZamn : SnesDriver {
    override val nom = "ZAMN"
    override val cancons = (2..11).map { "So $it" }
    override fun reconeix(rom: RomSnes): Boolean = !rom.hiRom && rom.d.size >= 0x100000 &&
        rom.u8(0x91, 0x8000) == 0xB7 && rom.u8(0x91, 0x8001) == 0x0B && rom.u8(0x91, 0x8002) == 0x00 && rom.u8(0x91, 0x8003) == 0x06 &&
        rom.u16(0x80, 0xCCDE) == 0 && rom.u16(0x80, 0xCCE0) == 0x7F
    private var seq = 0xA2
    private fun ordre(spc: SnesSpc, c: Int, p: Int) {
        var k = 0
        while (spc.llegeixPort(3) != seq && k++ < 60000) s2Corre(spc, 4)
        spc.escriuPort(1, p); spc.escriuPort(2, c)
        seq = (seq + 1) and 255
        spc.escriuPort(3, seq)
    }
    private fun conjunt(spc: SnesSpc, rom: RomSnes, index: Int, selecciona: Boolean) {
        val e = 0x80CCDE + index * 4
        val adr = rom.u16(0x80, e and 0xFFFF); val banc = rom.u16(0x80, (e and 0xFFFF) + 2)
        if (selecciona) ordre(spc, 0x08, index)
        var a = adr
        while (true) {
            val n = rom.u16(banc, a); a += 2
            if (n == 0) break
            ordre(spc, 0x0A, (n and 255) or (n shr 8))
            val b = rom.bytes(banc, a, n)
            for (x in b) ordre(spc, 0x06, x.toInt() and 255)
            a += n
        }
    }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val song = (id - 1).coerceIn(0, 9) + 2
        val img = rom.bytes(0x91, 0x8000, 0x8000) + rom.bytes(0x95, 0xBA3D, 0x1A86)
        var o = 0; var entrada = 0x0600
        while (o + 4 <= img.size) {
            val n = (img[o].toInt() and 255) or ((img[o + 1].toInt() and 255) shl 8)
            val dest = (img[o + 2].toInt() and 255) or ((img[o + 3].toInt() and 255) shl 8)
            o += 4
            if (n == 0) { entrada = dest; break }
            if (o + n > img.size) break
            s2Copia(spc, img.copyOfRange(o, o + n), dest)
            o += n
        }
        spc.arrenca(entrada)
        seq = 0xA2
        conjunt(spc, rom, song, true)
        conjunt(spc, rom, 12 + 3, false)
        ordre(spc, 0x01, 0x00)                  // $01 = engega la cançó PARAM (la primera registrada); $14 és un so amb mostra pròpia
    }
}


/* ================================================================================================================================================================ */

/**
 * Base comuna dels programes de so de Rare de DKC2 i DKC3 (el mateix: `$0D7E`... en realitat `$0CFE` bytes a `$0560`, entrada `$0672`). La CPU (`.play_song` de DKC2, `CODE_B280AA`
 * de DKC3) puja la llista de mostres globals + les de la cançó (directori a `$3100`, dades a `$3400`; la taula «id de mostra -> índex del directori», 256 bytes, va a `$0560`), els
 * blocs `[destí][paraules][dades]` de la cançó i dels efectes, i salta a `$0672`; després, amb el comptador de transaccions al port 0 (la CPU espera l'eco, escriu l'ordre al port 1
 * i el paràmetre al port 2, i incrementa el comptador), envia `$FB` (transició, paràmetre = byte alt de l'id), `$FE` (inicia) i `$FA` (mono/estèreo). El programa porta el comptador
 * esperat a `$E9`; aquí s'hi posa 1 amb el port 0 a 0.
 */
abstract class RareDk : SnesDriver {
    abstract val enginePtr: Pair<Int, Int>
    abstract val stereo: Int
    protected val cua = ArrayDeque<Pair<Int, Int>>()
    private var seq = 0
    protected fun llista(spc: SnesSpc, rom: RomSnes, banc: Int, adr: Int, taula: ByteArray, estat: IntArray, tauMostres: Pair<Int, Int>) {
        var a = adr; var ent = 0
        while (ent++ < 256) {                                    // una llista de mostres no en té més de 256 (la taula té 256 entrades): si no acaba en $FFFF, l'adreça és errònia
            val sid = rom.u16(banc, a); a += 2
            if (sid == 0xFFFF || sid >= 256) break
            taula[sid and 255] = estat[2].toByte(); estat[2]++
            val p = rom.u24(tauMostres.first, tauMostres.second + 3 * sid)
            val b = p shr 16; val ad = p and 0xFFFF
            if (rom.fitxer(b, ad) < 0) continue
            val bucle = rom.u16(b, ad); val mida = rom.u16(b, ad + 2)
            if (mida > 0x8000 || estat[1] + mida > 0x10000) break                  // no cap a la RAM de l'SPC: dades errònies
            s2Copia(spc, rom.bytes(b, ad + 4, mida), estat[1])
            s2Copia(spc, byteArrayOf(estat[1].toByte(), (estat[1] shr 8).toByte(), (estat[1] + bucle).toByte(), ((estat[1] + bucle) shr 8).toByte()), estat[0])
            estat[0] += 4; estat[1] += mida
        }
    }
    /** Bloc `[destí][paraules][dades]` en un punter de 24 bits (s'ignora si no apunta a la ROM). */
    protected fun blocPtr(spc: SnesSpc, rom: RomSnes, p: Int) {
        val b = p shr 16; val a = p and 0xFFFF
        if (rom.fitxer(b, a) < 0) return
        val dest = rom.u16(b, a); val w = rom.u16(b, a + 2)
        s2Copia(spc, rom.bytes(b, a + 4, w * 2), dest)
    }
    protected fun arrenca(spc: SnesSpc, rom: RomSnes, taula: ByteArray) {
        s2Copia(spc, rom.bytes(enginePtr.first, enginePtr.second, 0xCFE), 0x0560)
    }
    protected fun fi(spc: SnesSpc, taula: ByteArray, param: Int) {
        // ESA ($6D): pàgina de la memòria d'eco. Al DSP real ja val $FB (el motor de DKC l'hi deixa en arrencar, i les cançons amb eco no el canvien); amb ESA = 0 i l'eco actiu, el DSP
        // escriu a $0000-$0003 a cada mostra i esborra el punter de pista del motor ($00/$01): totes les veus acabaven al primer esdeveniment (DKC3: cançons silencioses).
        spc.dsp.write(0x6D, 0xFB)
        s2Copia(spc, taula.copyOf(256), 0x0560)
        spc.arrenca(0x0672)
        spc.ram[0xE9] = 1
        seq = 0
        cua.clear()
        cua.add(0xFB to param); cua.add(0xFE to 0); cua.add(0xFA to stereo)
    }
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {
        if (n < 2 || cua.isEmpty() || spc.llegeixPort(0) != seq) return
        val (c, p) = cua.removeFirst()
        spc.escriuPort(1, c); spc.escriuPort(2, p)
        seq = (seq + 1) and 255
        spc.escriuPort(0, seq)
    }
}

/**
 * Donkey Kong Country 3 (SNES, HiROM 4 MB). De Yoshifanatic1/Donkey-Kong-Country-3-Disassembly (`CODE_B2802D`, `CODE_B280AA`, `CODE_B28325`, `CODE_B282B5`, `CODE_B282CD`, `CODE_B28349`,
 * `CODE_B2846C`): programa de so a `DATA_ED0088` (`$CFE` bytes a `$0560`), efectes globals `DATA_F1B663`, llista de mostres globals `DATA_ED0F36`, taula de punters de mostra
 * `DATA_EE078A` (3 bytes per id; capçalera `[bucle][mida]`), i per a cada cançó una entrada de 9 bytes a `DATA_ED0D86 + 9*n` (bloc 1, llista de mostres, bloc 2). L'id de cançó és el
 * `!Define_DKC3_MusicID_*` (byte baix = cançó, alt = paràmetre de `$FB`). Es reconeix pel títol i els bytes del programa de so a `+$100`. No comprovat amb cap ROM.
 */
object DriverDkc3 : RareDk() {
    override val nom = "DKC3"
    override val enginePtr = 0xED to 0x0088
    override val stereo = 0
    private val IDS = intArrayOf(0x0401, 0x0301, 0x0201, 0x0004, 0x0104, 0x0005, 0x0105, 0x0205, 0x0006, 0x0007, 0x0008, 0x0009, 0x0109, 0x000A, 0x000B, 0x000C, 0x000D, 0x000E, 0x000F, 0x0010, 0x0011,
        0x0012, 0x0013, 0x0014, 0x0015, 0x0016, 0x0017, 0x0018, 0x0019, 0x001A, 0x001B, 0x001C, 0x001D, 0x001E, 0x0020, 0x0021, 0x0022, 0x0023, 0x002A, 0x002E)
    override val cancons = s2Noms("Bonus Time, Bonus Win, Bonus Lost, Brothers Bear, Brothers Bear Blues, Get Fit A Go Go, Wrinkly 64, Wrinkly's Save Cave, Water World, Hot Pursuit, Nuts And Bolts, Swanky's Sideshow, Cranky's Showdown, Cascade Capers, Treetop Tumble, Boss Boogie, Jangle Bells, Mill Fever, Stilt Village, Enchanted Riverbank, Submap Shuffle, Krematoa Koncerto, Northern Kremisphere, Game Over, Hangin At Funky's, Fanfare, Frosty Frolics, Dixie Beat, Pokey Pipes, Big Boss Blues, Crystal Chasm, Rocket Run, Jungle Jitter, Cavern Caprice, Baddies On Parade, Rockface Rumble, Crazy Calypso, Mama Bird, Banana Bird Queen Chase, Defeated Boss")
    private val SIG = intArrayOf(0xE5, 0x5D, 0x05, 0x1C, 0xFD, 0xF6, 0x12, 0x13)
    override fun reconeix(rom: RomSnes): Boolean = rom.hiRom && rom.d.size >= 0x400000 && rom.titol == "DONKEY KONG COUNTRY 3" && SIG.indices.all { rom.u8(0xED, 0x0088 + 0x100 + it) == SIG[it] }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val v = IDS[(id - 1).coerceIn(0, IDS.size - 1)]
        val cn = v and 255
        arrenca(spc, rom, ByteArray(0))
        blocPtr(spc, rom, 0xF1B663)
        val taula = ByteArray(256)
        val estat = intArrayOf(0x3100, 0x3400, 0)
        val tm = 0xEE to 0x078A
        llista(spc, rom, 0xED, 0x0F36, taula, estat, tm)
        val e = 0x0D86 + 9 * cn
        val pl = rom.u24(0xED, e + 3)
        if (pl != 0xED0F36) llista(spc, rom, pl shr 16, pl and 0xFFFF, taula, estat, tm)
        blocPtr(spc, rom, rom.u24(0xED, e))
        blocPtr(spc, rom, rom.u24(0xED, e + 6))
        fi(spc, taula, v shr 8)
    }
}

/**
 * Donkey Kong Country 2 (SNES, HiROM 4 MB). De p4plus2/DKC2-disassembly (`bank_B5.asm`: `.play_song_with_transition`, `.upload_song_sample_set`, `.sample_uploader`, `.upload_song_data`,
 * `.upload_song_sound_effects`; `music.txt` dona els noms, `bank_EE.asm` les taules): el mateix programa de so que DKC3, a `EE:0088` (just abans de `sample_table`, `EE:0D86`, 256 punters
 * de 3 bytes), llista global `EE:11F9`, cançons `song_data = EE:1086` (6 bytes: bloc + llista de mostres), efectes de la cançó `EE:117B + 3*n`, efectes globals `sound_effect_data` =
 * `F2:E730`. Les adreces de `song_data`, `sample_table`, `global_sample_map` i `sound_effect_data` no apareixen escrites al desassemblatge: es dedueixen comptant les dades entre
 * etiquetes amb adreça (`DATA_EE1179`, `DATA_EE1223`, `DATA_F2E72C`) i la mida del programa; casen amb `EE:0088 + $CFE = EE:0D86`. Es reconeix pel títol (`DIDDY'S KONG QUEST`) i els bytes
 * del programa de so. No comprovat amb cap ROM.
 */
object DriverDkc2 : RareDk() {
    override val nom = "DKC2"
    override val enginePtr = 0xEE to 0x0088
    override val stereo = 1
    override val cancons = s2Noms("Island map, Main theme, Swamp, Swanky, Enchanted wood, Ship deck, Mine, Funky, Brambles, Klubba, Wasp hive, Wrinkly, Lava, Roller coaster, Bonus, Ship hold, Fanfare, Ship deck 2, Rescue kong, Game over, Big boss, Castle, Haunted, Select, Cranky, Ice, Jungle, Lost world, Rigging, Credits, K. Rool")
    private val SIG = intArrayOf(0xE5, 0x5D, 0x05, 0x1C, 0xFD, 0xF6, 0x12, 0x13)
    override fun reconeix(rom: RomSnes): Boolean = rom.hiRom && rom.d.size >= 0x400000 && (rom.titol.startsWith("DIDDY") || rom.titol == "DONKEY KONG COUNTRY 2") &&
        SIG.indices.all { rom.u8(0xEE, 0x0088 + 0x100 + it) == SIG[it] }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val cn = (id - 1).coerceIn(0, cancons.size - 1) + 1
        arrenca(spc, rom, ByteArray(0))
        // efectes globals: bloc en línia [destí][paraules][dades]
        run {
            val dest = rom.u16(0xF2, 0xE730); val w = rom.u16(0xF2, 0xE732)
            s2Copia(spc, rom.bytes(0xF2, 0xE734, w * 2), dest)
        }
        val taula = ByteArray(256)
        val estat = intArrayOf(0x3100, 0x3400, 0)
        val tm = 0xEE to 0x0D86
        llista(spc, rom, 0xEE, 0x11F9, taula, estat, tm)
        val sd = 0x1086 + 6 * cn
        val pl = rom.u24(0xEE, sd + 3)
        if (pl != 0xEE11F9) llista(spc, rom, pl shr 16, pl and 0xFFFF, taula, estat, tm)
        blocPtr(spc, rom, rom.u24(0xEE, sd))
        blocPtr(spc, rom, rom.u24(0xEE, 0x117B + 3 * cn))
        fi(spc, taula, 0)
        cua.removeFirst()                                                       // `.play_song` no envia `$FB`
    }
}

/* ================================================================================================================================================================ */

/**
 * Goof Troop (SNES, LoROM). De Yoshifanatic1/Goof-Troop-Disassembly (`CODE_8098DC`, `CODE_8099C0`, `CODE_8099EE`, `DATA_848000`, `DATA_838630`, `Misc_Defines`): taula de pujades a
 * `$84:8000` (3 bytes per índex, relatius a `$84:8000`: `Y = mot & $7FFF`, banc = tercer byte OR `$84`, adreça `$8000+Y`); cada pujada és una llista de blocs `[mida][destí][dades]` en el
 * format de l'IPL (mida 0 = entrada). Arrencada: índexs 0 (el programa de so; el bloc final de mida 0 porta l'adreça d'entrada), 1 i 2; cada cançó `$10-$22` és la pujada d'índex igual a l'id
 * (`DATA_838630` és la identitat). Després la CPU espera que el port 2 valgui el comptador (1) i envia `$FB` al port 0 amb el paràmetre 0 al port 1. Les adreces ARAM són a les capçaleres
 * dels blocs (el desassemblatge només les dona amb noms simbòlics); no es sap què fa el programa de so a l'adreça d'entrada 0 de les pujades de cançó, ni si el joc envia `$FB` just
 * després de pujar-la: no comprovat amb cap ROM i pot fallar.
 */
object DriverGoofTroop : SnesDriver {
    override val nom = "GoofTroop"
    override val cancons = s2Noms("Capcom, To The South, Illusion, Lose My Way, Sea Robber, Let's Start, Break, Map, Go On, The End, Goofy Or Max, Fight Fight, Satisfied, Flashback, Hamlet, Fight A Battle, Staff Roll, Game Over, Rest")
    private val SIG = intArrayOf(0x69, 0x00, 0x00, 0xBD, 0x0A, 0x00, 0x26, 0x18, 0x00)
    override fun reconeix(rom: RomSnes): Boolean = !rom.hiRom && rom.titol == "GOOF TROOP" && SIG.indices.all { rom.u8(0x84, 0x8000 + it) == SIG[it] }
    private var pas = 0
    private fun puja(spc: SnesSpc, rom: RomSnes, index: Int): Int {
        val w = rom.u16(0x84, 0x8000 + 3 * index); val b = rom.u8(0x84, 0x8000 + 3 * index + 2) or 0x84
        var o = rom.fitxer(b, 0x8000 + (w and 0x7FFF))
        var entrada = 0
        while (o >= 0 && o + 4 <= rom.d.size) {
            val n = (rom.d[o].toInt() and 255) or ((rom.d[o + 1].toInt() and 255) shl 8)
            val dest = (rom.d[o + 2].toInt() and 255) or ((rom.d[o + 3].toInt() and 255) shl 8)
            if (n == 0) { entrada = dest; break }
            if (o + 4 + n > rom.d.size) break
            s2Copia(spc, rom.d.copyOfRange(o + 4, o + 4 + n), dest)
            o += 4 + n
        }
        return entrada
    }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val entrada = puja(spc, rom, 0)
        puja(spc, rom, 1); puja(spc, rom, 2)
        puja(spc, rom, 0x10 + (id - 1).coerceIn(0, 18))
        spc.arrenca(entrada)
        pas = 0
    }
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {
        if (pas == 0 && n > 2 && spc.llegeixPort(2) == 1) { spc.escriuPort(1, 0); spc.escriuPort(0, 0xFB); pas = 1 }
    }
}

val DRIVERS_SNES_S2: List<SnesDriver> = listOf(DriverDkc1, DriverDkc2, DriverDkc3, DriverSgng, DriverZamn, DriverGoofTroop)
