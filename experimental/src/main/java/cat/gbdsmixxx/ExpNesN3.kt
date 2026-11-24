package cat.gbdsmixxx

/*
 * Lot N, fitxer 3: Contra Force.
 */

/**
 * Contra Force (NES, MMC3, 128 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris; constants `con_music_*` a `bank_val.inc`): el motor és al parell de bancs de PRG $04 (bancs de 8 KB $08 a $8000 i
 * $09 a $A000). La cua de so (`ram_sound_history` $0390, índexs `$0362`/`$0363`) la buida `sub_FEDE` posant l'identificador a `ram_00EE_se` ($EE) i cridant `sub_B286` ($B286, ordre de so); aquí es fa això mateix
 * directament. `sub_0x012A1F_update_sound_engine` ($AA0F) és la passada per fotograma (la NMI hi arriba des de `sub_FEAA`). Pistes: TOTS els `con_music_*` ($01 a $A8: músiques i efectes; noms del desassemblatge
 * quan en té). Es reconeix per l'entrada de la passada ($AA0F: `lda $05A1; beq; jsr; lda $0332; beq; dec $0333`) al PRG $12A0F. Verificat amb ROM assemblada.
 */
internal object ExpNesContraForce : NesDriver {
    override val nom = "ContraForceN"
    private val IDS = listOf(
        0x01 to "", 0x02 to "", 0x03 to "", 0x04 to "passos", 0x06 to "", 0x08 to "", 0x0A to "", 0x0C to "", 0x0E to "", 0x10 to "", 0x12 to "", 0x14 to "", 0x16 to "", 0x18 to "", 0x1A to "", 0x1C to "", 0x1E to "", 0x20 to "", 0x22 to "",
        0x24 to "", 0x26 to "", 0x28 to "", 0x2A to "", 0x2C to "", 0x2E to "", 0x30 to "", 0x32 to "", 0x34 to "vidre trencat (fase 5)", 0x36 to "", 0x38 to "", 0x3A to "", 0x3C to "", 0x3E to "", 0x40 to "", 0x42 to "", 0x44 to "", 0x46 to "",
        0x48 to "", 0x4A to "", 0x4C to "", 0x4E to "triar arma nova", 0x50 to "", 0x53 to "", 0x55 to "", 0x58 to "paracaigudes", 0x59 to "", 0x5B to "caure a l'aigua", 0x5D to "jugador mort", 0x5F to "caure a un forat", 0x61 to "", 0x62 to "",
        0x63 to "", 0x64 to "tret després de «press start»", 0x67 to "menú de pausa (entrada i música)", 0x6B to "", 0x70 to "menú de pausa / selecció de jugador", 0x74 to "fase 1", 0x78 to "", 0x7C to "fase 2", 0x80 to "fase 3", 0x84 to "fase 4",
        0x88 to "fase 5", 0x8C to "", 0x90 to "cap", 0x98 to "", 0x9C to "fase completada", 0xA0 to "diàleg després de les fases", 0xA4 to "game over", 0xA8 to "",
    )
    override val cancons = IDS.map { (v, n) -> (if (n.isEmpty()) "So" else n) + " · \$" + hN(v) + " (con_music_" + hN(v) + ")" }
    private val cau = CercaN(sigN("AD A1 05 F0 03 20 4A AA AD 32 03 F0 11 CE 33 03 D0 0C"))
    override fun reconeix(rom: NesRom) = rom.mapper == 4 && rom.prgBancs == 8 && cau.adr(rom) == 0x12A0F
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaMmc3(8, 9); cpu.sp = 0xFD
        m.ram[0xEE] = IDS[(id - 1).coerceIn(0, IDS.size - 1)].first
        cpu.crida(0xB286)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaMmc3(8, 9); cpu.sp = 0xFD; cpu.crida(0xAA0F) }
}

/**
 * Danny Sullivan's Indy Heat (NES, AxROM de 128 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris): el motor de so és al banc de 32 KB $02 (mapper 7), que s'activa amb el sistema `bsw` del joc; hi ha una taula
 * d'entrades a l'inici del banc ($8003 passada per fotograma, `sub_8A6D`; $8006 música, `loc_8A28`, amb l'identificador a `ram_004E_se_t01_music_id` ($4E); $8009/$800C/$800F/$8012 efectes per als canals $4000/$4004/$4008/$400C amb
 * A = identificador a $4E). `tbl_8FD9` ($8FD9) i `tbl_8FE8` ($8FE8, 8 bytes per música = 4 punters) donen 15 músiques (ids 0 a $0E; `tbl_847B_race_music` ($847B) assigna als circuits $07,$0A,$0B,$09,$01,$08,$04,$02,$0D);
 * `tbl_8FAD` ($8FAD) dona 22 efectes (ids parells 0 a $2A; el bit 0 només canvia el valor que es compara). Pistes: les 15 músiques i els 22 efectes als canals de $4004 (via $800C), $400C (via $8012) i $4000 (via $8009).
 * Es reconeix per la taula d'entrades del banc 2 (`jmp ($0008); jmp $8A6D; jmp $8A28; jmp $8C58`) al PRG $10000. Verificat amb ROM assemblada.
 */
internal object ExpNesIndyHeat : NesDriver {
    override val nom = "IndyHeatN"
    private class Pis(val nom: String, val ent: Int, val v: Int)
    private val PIS: List<Pis> = (0..14).map { Pis("Música \$${hN(it)} · \$8006 (tbl_8FE8 entrada $it)" + (if (it in listOf(7, 10, 11, 9, 1, 8, 4, 2, 13)) " · circuit" else ""), 0x8006, it) } +
        listOf(0x800C to "canal \$4004", 0x8012 to "canal \$400C", 0x8009 to "canal \$4000").flatMap { (e, c) -> (0..21).map { Pis("Efecte \$${hN(it * 2)} · $c · \$${hN(e, 4)}", e, it * 2) } }
    override val cancons = PIS.map { it.nom }
    private val cau = CercaN(sigN("6C 08 00 4C 6D 8A 4C 28 8A 4C 58 8C"))
    override fun reconeix(rom: NesRom) = rom.mapper == 7 && rom.prgBancs == 8 && cau.adr(rom) == 0x10000
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaBanc(2); cpu.sp = 0xFD
        val p = PIS[(id - 1).coerceIn(0, PIS.size - 1)]
        m.ram[0x4E] = p.v; cpu.ldaN(p.v); cpu.crida(p.ent)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaBanc(2); cpu.sp = 0xFD; cpu.crida(0x8003) }
}

/**
 * Solstice: The Quest for the Staff of Demnos (NES, AxROM de 128 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris, constants `con_music_*` i `con_sfx_*`): el motor de so és al banc de 32 KB $02 (mapper 7);
 * `sub_out_0x0141F6_play_music` ($C1E6, X = música 0 a 6: història, joc, game over, continuar, final, logo, títol), `sub_out_0x014299_play_sfx` ($C289, X = efecte 0 a 9; $01 i $06 no existeixen al joc) i
 * `sub_out_0x014304_update_music_driver` ($C2F4, a cada fotograma des de la NMI). Efectes: 0 teletransport, 2 beure poció, 3 agafar bloc, 4 deixar bloc, 5 mort, 7 paper, 8 bonus, 9 detonador. Es reconeix per l'entrada de
 * `play_music` al PRG $141E6. Verificat amb ROM assemblada.
 */
internal object ExpNesSolstice : NesDriver {
    override val nom = "SolsticeN"
    private val MUS = listOf("Història", "Joc", "Game over", "Continuar", "Final i crèdits", "Logo (CSG Imagesoft)", "Títol")
    private val SFX = listOf("Teletransport", "(no existeix) 1", "Beure poció", "Agafar bloc", "Deixar bloc", "Mort", "(no existeix) 6", "Paper (pausa)", "Bonus (botes, claus)", "Detonador")
    override val cancons = MUS.indices.map { "${MUS[it]} · música $it · \$C1E6" } + SFX.indices.map { "${SFX[it]} · efecte $it · \$C289" }
    private val cau = CercaN(sigN("20 12 C2 BD 0B D7 85 4F BD 12 D7 85 50 BD 19 D7"))
    override fun reconeix(rom: NesRom) = rom.mapper == 7 && rom.prgBancs == 8 && cau.adr(rom) == 0x141E6
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaBanc(2); cpu.sp = 0xFD
        val i = (id - 1).coerceIn(0, MUS.size + SFX.size - 1)
        cpu.x = if (i < MUS.size) i else i - MUS.size
        cpu.crida(if (i < MUS.size) 0xC1E6 else 0xC289)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaBanc(2); cpu.sp = 0xFD; cpu.crida(0xC2F4) }
}

/**
 * RoboCop 3 (NES, MMC1, 128 KB). De cyneprepou4uk/NES-Games-Disassembly (adreces als comentaris, constants `con_sound_*`): el motor de so és al banc de 16 KB $06 (visible a $8000: `sub_0x018010` = $8000 ordre per canal,
 * `sub_0x018013` = $8003 passada d'un canal). `sub_F77F_play_sound` ($F77F) / `loc_F790` ($F790) agafen `ram_sound` ($47), busquen a `tbl_F7CD` ($F7CD, 8 bytes per so = 4 parells canal/pista) i criden $8000 per a cadascun (només
 * si `ram_option_music` $06A7 i `ram_option_sound` $06A8 són diferents de 0); `loc_C422` ($C422, a cada fotograma) canvia al banc 6 i crida `sub_F905` ($F905: $8003 amb X = 0 a 3). Pistes: tots els `con_sound_*`
 * ($00 a $26 excepte el $12, que és «unused»; $22 a $26 = música de les missions 1 a 5). Es reconeix per `sub_F77F_play_sound` al PRG $1F77F. Verificat amb ROM assemblada.
 */
internal object ExpNesRoboCop3 : NesDriver {
    override val nom = "RoboCop3N"
    private val IDS = (0..0x26).filter { it != 0x12 }
    override val cancons = IDS.map { (if (it >= 0x22) "Música missió ${it - 0x21}" else "So") + " · \$" + hN(it) + " (con_sound_" + hN(it) + ")" }
    private val cau = CercaN(sigN("A5 47 F0 0D AD A7 06 F0 44 4C 90 F7 AD A8 06 F0 3C A5 47 30 38"))
    override fun reconeix(rom: NesRom) = rom.mapper == 1 && rom.prgBancs == 8 && cau.adr(rom) == 0x1F77F
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.ram[0x6A7] = 1; m.ram[0x6A8] = 1; m.ram[0x03] = 0; cpu.sp = 0xFD
        m.ram[0x47] = IDS[(id - 1).coerceIn(0, IDS.size - 1)]
        cpu.crida(0xF790)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; cpu.crida(0xC422) }
}

val EXP_NES_N3: List<NesDriver> = listOf(ExpNesContraForce, ExpNesIndyHeat, ExpNesSolstice, ExpNesRoboCop3)
