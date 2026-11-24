package cat.gbdsmixxx

/*
 * Lot R2SA de jocs de SNES amb driver (ronda 2). Super Mario All-Stars (bonimy/SMAS-Disassembly; noms de les cançons de Yoshifanatic1/SMW-SMAS-SMASW-Disassembly).
 * Cap ROM al repo: tot surt de la ROM de l'usuari. No comprovat amb cap ROM (escrit des del desassemblatge).
 */

private fun r2aU16(d: ByteArray, o: Int): Int = if (o >= 0 && o + 1 < d.size) (d[o].toInt() and 255) or ((d[o + 1].toInt() and 255) shl 8) else 0

/** Copia a `spc.ram` els blocs `[mida][destí][dades]` d'un fluxe de bytes (acabat amb mida 0). Retorna false si el format no quadra. */
private fun r2aBlocs(spc: SnesSpc, d: ByteArray): Boolean {
    var p = 0
    while (p + 4 <= d.size) {
        val n = r2aU16(d, p); val a = r2aU16(d, p + 2); p += 4
        if (n == 0) return true
        if (p + n > d.size) return false
        for (k in 0 until n) spc.ram[(a + k) and 0xFFFF] = d[p + k]
        p += n
    }
    return false
}

/**
 * Super Mario All-Stars (SNES, LoROM 2 MB). `SPCUpload` ($00:8BAC) és el protocol de l'IPL amb un punter de 24 bits; la sala del menú (`CODE_008A4F`) hi puja `$07:FC00`
 * ($400 bytes) seguits de `$3B:8000` ($8000 bytes) -- el motor de so (bloc `$210F` bytes a `$0500`, que comença a `$07:FCC0`) i les mostres comunes--, i cada joc (`PNTR_0089F1`,
 * `CODE_008B17`/`8B27`/`8B37`) hi puja un sol bloc de dades de música: SMB1 i The Lost Levels `$1F:8000` (`$3761` bytes a `$C000`), SMB2 `$1F:C000`, SMB3 `$0C:8000` (`$346D` bytes
 * a `$C000`), tots acabats amb l'entrada `$0500`. La música es demana escrivint l'identificador (`!Define_<joc>_LevelMusic_*`) al port 2 (`$0062` -> `$2142`, `CODE_0087CC`).
 * De SMB3 només es poden fer sonar les cançons de nivell (les del mapa porten un banc a part, `LoadOverworldMusicBank`).
 */
private class SmasJoc(override val nom: String, val bancData: Int, val adrData: Int, val llargData: Int, val ids: List<Pair<Int, String>>) : SnesDriver {
    override val cancons: List<String> = ids.map { it.second }
    private fun hall(rom: RomSnes): ByteArray? {
        val a = rom.fitxer(0x07, 0xFC00); val b = rom.fitxer(0x3B, 0x8000)
        if (a < 0 || b < 0 || b + 0x8000 > rom.d.size) return null
        return rom.d.copyOfRange(a, a + 0x400) + rom.d.copyOfRange(b, b + 0x8000)
    }
    override fun reconeix(rom: RomSnes): Boolean {
        if (rom.hiRom || rom.d.size < 0x200000 || !rom.titol.uppercase().startsWith("SUPER MARIO ALL_STARS")) return false
        val h = rom.fitxer(0x07, 0xFCBC); val g = rom.fitxer(bancData, adrData)
        return h >= 0 && g >= 0 && r2aU16(rom.d, h) == 0x210F && r2aU16(rom.d, h + 2) == 0x0500 &&
            r2aU16(rom.d, g) == llargData && r2aU16(rom.d, g + 2) == 0xC000
    }
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val h = hall(rom) ?: return
        r2aBlocs(spc, h)
        // El joc (`CODE_008A8E`/`CODE_008ABC`) puja a continuació `$0B:8000`: el directori de mostres de 28 entrades ($3C00) i els 31 KB de mostres dels tres jocs (el del menú només en té 10 de vàlides);
        // sense aquest pas, els instruments d'índex 12 o més apuntaven a mostres inexistents i la resta sonava a una altra octava.
        val sb = rom.fitxer(0x0B, 0x8000)
        if (sb >= 0) r2aBlocs(spc, rom.d.copyOfRange(sb, minOf(rom.d.size, sb + 0x8000)))
        val g = rom.fitxer(bancData, adrData)
        if (g >= 0) r2aBlocs(spc, rom.d.copyOfRange(g, minOf(rom.d.size, g + llargData + 8)))
        musica = ids[(id - 1).coerceIn(0, ids.size - 1)].first
        spc.arrenca(0x0500)
    }
    private var musica = 0
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {
        if (n == 10L) spc.escriuPort(2, musica)                       // $0062 -> $2142
    }
}

private val SMB1_IDS = listOf(0x01 to "Bonus Room", 0x02 to "Overworld", 0x03 to "Underwater", 0x04 to "Bowser Battle", 0x05 to "Final Bowser Battle", 0x06 to "Underground",
    0x08 to "Castle", 0x09 to "Mario Died", 0x0A to "Game Over", 0x0B to "Passed Boss", 0x0C to "Passed Level", 0x0D to "Star", 0x0E to "SMB1 Peach Rescued",
    0x10 to "Lost Levels Peach Rescued", 0x13 to "Enter Pipe Cutscene", 0x14 to "SMB1 Title Screen", 0x15 to "Lost Levels Title Screen")
private val SMB2_IDS = listOf(0x01 to "Overworld", 0x04 to "Character Select", 0x05 to "Subspace", 0x06 to "Underground", 0x07 to "Wart Battle", 0x08 to "Danger", 0x09 to "Death",
    0x0A to "Game Over", 0x0B to "World Clear", 0x0C to "Fanfare 1", 0x0D to "Star", 0x0E to "Ending", 0x0F to "Fanfare 2", 0x10 to "Won Slots", 0x11 to "Title Screen",
    0x12 to "Unknown Fanfare", 0x13 to "Warp Zone")
private val SMB3_IDS = listOf(0x01 to "Athletic", 0x02 to "Overworld", 0x03 to "Underwater", 0x04 to "Toad House", 0x05 to "Bowser Battle", 0x06 to "Underground", 0x07 to "Victory",
    0x08 to "Fortress", 0x09 to "Mario Died", 0x0A to "Game Over", 0x0B to "Passed Boss", 0x0C to "Passed Level", 0x0D to "Star", 0x0E to "Bowser Fell", 0x0F to "Throne Room",
    0x10 to "Airship", 0x11 to "Hammer Bro Encounter", 0x12 to "Boss Battle", 0x13 to "World Clear", 0x14 to "Cloud Bonus Room", 0x15 to "Title Screen", 0x16 to "Battle Mode",
    0x17 to "Bowser Letter", 0x18 to "Peach Letter")

val DriverSmasSmb1: SnesDriver = SmasJoc("SMAS_SMB1", 0x1F, 0x8000, 0x3761, SMB1_IDS)
val DriverSmasSmb2: SnesDriver = SmasJoc("SMAS_SMB2", 0x1F, 0xC000, 0x2CCE, SMB2_IDS)
val DriverSmasSmb3: SnesDriver = SmasJoc("SMAS_SMB3", 0x0C, 0x8000, 0x346D, SMB3_IDS)


/** Dades en brut d'un fluxe de blocs (el destí de cada bloc s'ignora, com fa la rutina de recepció `$FE` del motor de Pocky & Rocky 2): es copien seguides a partir de `dest`. */
private fun r2aFluxeBrut(spc: SnesSpc, rom: RomSnes, ptr: Int, dest: Int) {
    fun b(o: Int) = rom.u8((o shr 16) and 255, o and 0xFFFF)
    var p = ptr; var blocs = 0; var d = dest
    while (blocs++ < 256) {
        val n = b(p) or (b(p + 1) shl 8); p += 4
        if (n == 0) return
        for (k in 0 until n) spc.ram[(d + k) and 0xFFFF] = b(p + k).toByte()
        p += n; d += n
    }
}

/** Puja el fluxe de blocs `[mida][destí][dades]` que comença a l'adreça lineal de 24 bits `ptr` (banc i adreça com `($20),Y indirecte llarg` del joc); retorna l'adreça d'entrada del final, o -1. */
private fun r2aFluxe(spc: SnesSpc, rom: RomSnes, ptr: Int): Int {
    fun b(o: Int) = rom.u8((o shr 16) and 255, o and 0xFFFF)
    var p = ptr
    var blocs = 0
    while (blocs < 256) {
        val n = b(p) or (b(p + 1) shl 8); val a = b(p + 2) or (b(p + 3) shl 8); p += 4
        if (n == 0) return a
        for (k in 0 until n) spc.ram[(a + k) and 0xFFFF] = b(p + k).toByte()
        p += n; blocs++
    }
    return -1
}

/**
 * Pocky & Rocky 2 (SNES, LoROM; spannerisms/pr2dasm, `bank_0A.asm`). `InitializeAPU` ($0A:8006) fa dues pujades en format IPL: `APUDataStart` ($0A:853C, directori de mostres) i el motor
 * de Natsume ($0B:8000, entrada $0400). `RequestSong_local` ($0A:802D): `MusicPointers` ($0A:8124, paraules) apunta a un `SongPack` de 4 bytes [llista de transferència, valor del port 0];
 * la llista són punters de 24 bits a fluxes en format IPL (acabada en paraula 0) que es pugen un darrere l'altre, i després el valor del port 0 (1 o 2) engega la cançó. 37 cançons
 * (packs 1-$25; el 0 és silenci). El desassemblatge no en dona els noms. No comprovat amb cap ROM: escrit des del desassemblatge.
 */
object DriverPocky2 : SnesDriver {
    override val nom = "Pocky2"
    override val cancons: List<String> = List(37) { "So ${it + 1}" }
    override fun reconeix(rom: RomSnes): Boolean {
        if (rom.hiRom || rom.d.size < 0x100000) return false
        return rom.u16(0x0A, 0x8124) == 0x8170 && rom.u16(0x0A, 0x8126) == 0x8174 && rom.u16(0x0A, 0x8170) == 0x8204 &&
            rom.u16(0x0A, 0x853C) == 0x005C && rom.u16(0x0A, 0x853E) == 0x3D80 && rom.u16(0x0B, 0x8000) != 0
    }
    private var port0 = 0
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        val i = id.coerceIn(1, 37)
        r2aFluxe(spc, rom, 0x0A853C)
        var entrada = r2aFluxe(spc, rom, 0x0B8000)
        val pack = rom.u16(0x0A, 0x8124 + 2 * i)
        var llista = rom.u16(0x0A, pack)
        port0 = rom.u16(0x0A, pack + 2)
        // `PrepSongPackTransfer` ($0A:8053): als dos primers trossos el joc envia l'ordre $FF (el motor els rep amb la rutina $12E8: blocs `[mida][destí][dades]` normals; el segon és el directori de
        // mostres de la cançó, a $3D00) i a la resta l'ordre $FE: el motor (rutina $1351) en posa les dades en brut a l'adreça d'inici de la propera entrada diferent d'aquest directori, de 4 bytes
        // (inici, bucle), començant a $3D00; les entrades repetides (mateix inici) se salten. Els fluxes $FE porten tots el destí $4000, que el motor ignora.
        var tros = 0
        var punter = 0x3D00
        fun w(a: Int) = (spc.ram[a and 0xFFFF].toInt() and 255) or ((spc.ram[(a + 1) and 0xFFFF].toInt() and 255) shl 8)
        while (llista != 0 && rom.u16(0x0A, llista) != 0) {
            val ptr = rom.u24(0x0A, llista)
            if (tros < 2) {
                val e = r2aFluxe(spc, rom, ptr)
                if (e > 0 && e != 0xFFC0) entrada = e                  // $FFC0 = l'SPC700 torna a l'IPL: no és una entrada del motor
            } else {
                val dest = w(punter)
                r2aFluxeBrut(spc, rom, ptr, dest)
                punter += 4
                var guard = 0
                while (guard++ < 64 && w(punter) == dest) punter += 4
            }
            tros++
            llista += 3
        }
        spc.arrenca(if (entrada > 0) entrada else 0x0400)
    }
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {
        if (n == 10L) { spc.escriuPort(1, 0); spc.escriuPort(0, port0) }          // APUIO0 (16 bits: port 0 = valor, port 1 = 0)
    }
}

val DRIVERS_SNES_R2SA: List<SnesDriver> = listOf(DriverSmasSmb1, DriverSmasSmb2, DriverSmasSmb3, DriverPocky2)
