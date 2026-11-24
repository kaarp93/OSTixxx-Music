package cat.gbdsmixxx

/** Una rutina que el driver automàtic crida: adreça, registres de bancs en el moment de la crida (`NesRomMem.copiaBancs`) i A, X, Y. */
class RutinaNes(val dest: Int, val bs: IntArray, val a: Int = 0, val x: Int = 0, val y: Int = 0)

/**
 * Driver de NES guiat per dades, generat per l'analitzador (`AnalitzadorNes`, línia `AUTO_NES` del report): es reconeix pel CRC32 del fitxer i fa exactament el que va fer la prova en fred
 * de l'analitzador: màquina nova amb la RAM a zero, inicialització opcional, l'engega-cançó amb A = codi (X i Y com a la crida original) i, a cada fotograma, el motor per fotograma
 * (els tres amb els bancs que hi havia quan el joc els va cridar). No s'ha comprovat amb l'oïda: només que l'APU sona (`provat = false` a la llista de compatibles).
 */
class DriverNesAuto(override val nom: String, val titol: String, val crc: Long, val f: RutinaNes, val ini: RutinaNes?, val p: RutinaNes?, val codis: IntArray, noms: List<String>, val ramAd: Int = -1, val ramExtra: IntArray = IntArray(0)) : NesDriver {
    override val cancons: List<String> = noms
    override fun reconeix(rom: NesRom) = rom.valid && rom.crc32 == crc
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        if (ini != null) { m.posaBancs(ini.bs); cpu.a = ini.a; cpu.x = ini.x; cpu.y = ini.y; cpu.sp = 0xFD; cpu.crida(ini.dest, 60000) }
        val codi = codis[(id - 1).coerceIn(0, codis.size - 1)]
        if (p != null) { m.posaBancs(p.bs); cpu.a = codi; cpu.x = p.x; cpu.y = p.y; cpu.sp = 0xFD; cpu.crida(p.dest, 60000) }
        else { m.wb(ramAd, codi); for (i in 0 until ramExtra.size / 2) m.wb(ramExtra[2 * i], ramExtra[2 * i + 1]) }          // el motor llegeix la cançó d'una variable de RAM (i, de vegades, altres variables que el joc escriu al mateix temps)
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { m.posaBancs(f.bs); cpu.sp = 0xFD; cpu.crida(f.dest, 60000) }
}
