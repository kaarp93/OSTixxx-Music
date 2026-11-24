package cat.gbdsmixxx

/*
 * Drivers de NES del lot N2: Mega Man, Mega Man 4, 5 i 6 i Milon's Secret Castle.
 * Saltats (sense desassemblatge del motor de so amb adreces): Castlevania, Life Force i Super Dodge Ball (els repos de rumbleminze són ports a SNES amb la PRG com a bytes
 * en brut), Faxanadu, Duck Hunt i Adventure Island II (recompilacions estàtiques sense el motor de so documentat) i Fire Emblem (el desassemblatge no inclou el so).
 */

/** Cerca la signatura a la PRG una sola vegada per ROM (els drivers són objectes compartits i frame() es crida a cada fotograma). */
private class CercaCau(private val sig: IntArray) {
    private val cau = java.util.WeakHashMap<NesRom, Int>()
    fun adr(rom: NesRom): Int = synchronized(cau) { cau.getOrPut(rom) { rom.cerca(sig) } }
}

/**
 * Mega Man (NES, UNROM de 128 KB). De TheRealQuantam/mm1dasm: el reproductor de so és al banc 4 ($9000 a $BFFF; banc de 16 KB, visible a $8000). `SoundCodePlay` ($9000) es
 * crida a cada fotograma i `SoundCodeInit` ($9003) amb A = identificador de so (0 a $32: 17 músiques i 34 efectes, taula `MusicAndSfxTable` a $9A60); A = $FE reinicia el
 * sistema de so. El joc crida primer Play i després Init per a cada so de la cua. Es reconeix per l'entrada del reproductor (`jmp UpdateSound; cmp #$FC; bne; jmp $912A...`)
 * a l'offset $1000 d'un banc de 16 KB. Verificat assemblant el desassemblatge amb ca65 (només el banc 4 és real; la resta de bancs, a zero) i fent-lo sonar amb `SinteNesRom`.
 */
object DriverMegaMan1 : NesDriver {
    override val nom = "MegaMan1"
    override val cancons = listOf(
        "Wily derrotat", "Selecció de fase", "Cap seleccionat", "Game over", "Fase completada", "Cutman", "Fireman", "Fireman (repetit)", "Bombman", "Elecman", "Gutsman",
        "Cap de les fases de Wily", "Fases de Wily 1 i 2", "Cap", "Final", "Iceman", "Fases de Wily 3 i 4",
        "Platet de Wily", "Bomba", "Electricitat", "Mega Blaster", "Bala enemiga", "Mega Man tocat", "Llançar roca", "Recàrrega de la barra", "Aterratge", "Perla de bonus",
        "Cap tocat", "Inici al títol", "So 30", "So 31", "Moure pel menú", "Transformació", "So 34", "Pausa", "Gutsman aixeca", "Porta del cap", "Tisora", "Tisores de Cutman",
        "So 40", "Gutsman trepitja", "Salt del Big Eye", "Raig de gel", "Salt a l'aigua", "Replicació de blocs", "So 46", "So 47", "So 48", "Vent", "Mega Man mort", "Vida extra",
    )
    private val SIG = intArrayOf(0x4C, 0x3F, 0x92, 0xC9, 0xFC, 0xD0, 0x03, 0x4C, 0x2A, 0x91, 0xC9, 0xFD, 0xD0, 0x03, 0x4C, 0x2E, 0x91, 0xC9, 0xFE, 0xD0, 0x0B, 0xA9, 0x01, 0x85, 0xE4)
    private val cau = CercaCau(SIG)
    override fun reconeix(rom: NesRom): Boolean { val o = cau.adr(rom); return rom.mapper == 2 && rom.prgBancs == 8 && o >= 0 && o % 0x4000 == 0x1000 }
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        m.triaBanc(cau.adr(rom) / 0x4000); cpu.sp = 0xFD
        cpu.a = 0xFE; cpu.y = 0; cpu.crida(0x9003)
        cpu.sp = 0xFD; cpu.a = (id - 1).coerceIn(0, 0x32); cpu.y = 0; cpu.crida(0x9003)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.triaBanc(cau.adr(rom) / 0x4000); cpu.sp = 0xFD; cpu.crida(0x9000) }
}

/**
 * Mega Man 4, 5 i 6 (NES, MMC3). Dels desassemblatges de Raidenthequick (MM4 i MM5) i refreshing-lemonade (MM6), que porten l'adreça de cada instrucció. Tots tres comparteixen
 * el motor del Mega Man 3 (`DriverMegaMan3`): dos bancs de PRG de 8 KB consecutius, visibles a $8000 i $A000 (MM4 $1E i $1F, MM5 $18 i $19, MM6 $34 i $35), amb `JMP $806C`,
 * `JMP $80FE` a l'entrada. El joc (`play_sounds`, al banc fix) crida `$8003` amb A = identificador de so per a cada so de la cua i `$8000` a cada fotograma. El nombre de sons
 * és el byte de $8A40 del banc (MM4 $47, MM5 $4C, MM6 $66; el motor fa el mòdul per aquest valor). Es reconeix per la seqüència d'entrada més aquest byte i la mida de la ROM.
 * Els identificadors inclouen música i efectes (no s'han separat; el desassemblatge no els dona nom). Verificat assemblant els dos bancs de motor de cada joc amb ca65 (les
 * 150 etiquetes cauen a l'adreça que porten al nom) i fent-los sonar amb `SinteNesRom`.
 */
open class DriverMegaManMmc3(override val nom: String, private val nSons: Int, private val prgBancs16: Int) : NesDriver {
    override val cancons = (1..nSons).map { "So $it" }
    private val SIG = intArrayOf(0x4C, 0x6C, 0x80, 0x4C, 0xFE, 0x80, 0xA9, 0x00, 0x85, 0xC2, 0xA0, 0x08)
    private val cau = CercaCau(SIG)
    override fun reconeix(rom: NesRom): Boolean {
        val o = cau.adr(rom)
        return rom.mapper == 4 && rom.prgBancs == prgBancs16 && o >= 0 && o % 0x2000 == 0 && o + 0xA40 < rom.prg.size && (rom.prg[o + 0xA40].toInt() and 255) == nSons
    }
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        val b = cau.adr(rom) / 0x2000
        m.triaMmc3(b, b + 1); cpu.sp = 0xFD; cpu.a = (id - 1).coerceIn(0, nSons - 1); cpu.y = 0
        cpu.crida(0x8003)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) {
        val b = cau.adr(rom) / 0x2000
        m.triaMmc3(b, b + 1); cpu.sp = 0xFD; cpu.crida(0x8000)
    }
}

object DriverMegaMan4 : DriverMegaManMmc3("MegaMan4", 0x47, 32)
object DriverMegaMan5 : DriverMegaManMmc3("MegaMan5", 0x4C, 16)
object DriverMegaMan6 : DriverMegaManMmc3("MegaMan6", 0x66, 32)

/**
 * Milon's Secret Castle (NES, mapper 3 CNROM: 32 KB de PRG fixos, només canvia la CHR, així que va com una NROM). De jasonmray/MilonsSecretCastle (cada instrucció porta
 * l'adreça i els bytes als comentaris). La NMI crida `f_processMusic` ($EFA4) i `f_processSoundEffects` ($ED24) a cada fotograma. La música es demana escrivint a `songId`
 * ($00BE) el número de cançó (1 a 18; el bit 7 vol dir que ja sona); els efectes, a `soundEffectId` ($00E6, 1 a 23). Aquí els ids 1 a 18 són les cançons i 19 a 41 els efectes.
 * Es reconeix per l'inici de `f_processMusic` a la PRG $6FA4. Verificat construint la PRG a partir dels bytes que porten els comentaris del desassemblatge (codi i taules)
 * i fent-la sonar amb `SinteNesRom`.
 */
object DriverMilon : NesDriver {
    override val nom = "Milon"
    override val cancons = listOf(
        "Inici", "Exterior (swing)", "Continua", "Vals de les sales", "El pou", "Victòria", "Game over", "Entrar a la porta", "Sortir de la porta", "Mort", "Fase de bonus",
        "Rescat de la bombolla", "Bonus 1", "Bonus 2", "Bonus 3", "Bonus 4", "Objecte (cristall, bresca)", "Cap",
        "Efecte: taula de molla", "Efecte: salt", "Efecte: so 3", "Efecte: foc del cap", "Efecte: cop al bloc", "Efecte: bloc que es mou", "Efecte: entrar a la finestra",
        "Efecte: cop sord", "Efecte: clau revelada", "Efecte: clau recollida", "Efecte: abella revelada", "Efecte: paraigua revelat", "Efecte: abella o paraigua recollit",
        "Efecte: bloc fals", "Efecte: bloc que s'esfondra", "Efecte: diners", "Efecte: bombolla", "Efecte: foc del cap final", "Efecte: cop al cap", "Efecte: cop a l'enemic",
        "Efecte: Milon tocat", "Efecte: cor", "Efecte: pausa",
    )
    private val SIG = intArrayOf(0xA5, 0xBF, 0xD0, 0xE4, 0xA5, 0xBE, 0x30, 0x4D, 0xA5, 0xBE, 0x85, 0xE5, 0xC9, 0x11, 0xB0, 0x03, 0x8D, 0xFF, 0x07)
    override fun reconeix(rom: NesRom) = rom.mapper == 3 && rom.prgBancs == 2 && rom.cerca(SIG) == 0x6FA4
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        val i = (id - 1).coerceIn(0, 40)
        if (i < 18) m.ram[0xBE] = i + 1 else m.ram[0xE6] = i - 17
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) {
        cpu.sp = 0xFD; cpu.crida(0xEFA4)
        cpu.sp = 0xFD; cpu.crida(0xED24)
    }
}

val DRIVERS_NES_N2: List<NesDriver> = listOf(DriverMegaMan1, DriverMegaMan4, DriverMegaMan5, DriverMegaMan6, DriverMilon)
