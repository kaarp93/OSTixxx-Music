package cat.gbdsmixxx

/** Desassemblador mínim de 6502 (instruccions oficials) per als reports de l'analitzador de NES. */
object Dis6502 {
    private val N = HashMap<Int, Pair<String, String>>()
    private val SZ = mapOf("imm" to 2, "zp" to 2, "zpx" to 2, "zpy" to 2, "abs" to 3, "absx" to 3, "absy" to 3, "indx" to 2, "indy" to 2, "ind" to 3, "rel" to 2, "acc" to 1, "imp" to 1)
    init {
        val modes = listOf("imm", "zp", "zpx", "abs", "absx", "absy", "indx", "indy")
        val grup = mapOf(
            "ADC" to intArrayOf(0x69, 0x65, 0x75, 0x6D, 0x7D, 0x79, 0x61, 0x71), "AND" to intArrayOf(0x29, 0x25, 0x35, 0x2D, 0x3D, 0x39, 0x21, 0x31),
            "CMP" to intArrayOf(0xC9, 0xC5, 0xD5, 0xCD, 0xDD, 0xD9, 0xC1, 0xD1), "EOR" to intArrayOf(0x49, 0x45, 0x55, 0x4D, 0x5D, 0x59, 0x41, 0x51),
            "LDA" to intArrayOf(0xA9, 0xA5, 0xB5, 0xAD, 0xBD, 0xB9, 0xA1, 0xB1), "ORA" to intArrayOf(0x09, 0x05, 0x15, 0x0D, 0x1D, 0x19, 0x01, 0x11),
            "SBC" to intArrayOf(0xE9, 0xE5, 0xF5, 0xED, 0xFD, 0xF9, 0xE1, 0xF1), "STA" to intArrayOf(-1, 0x85, 0x95, 0x8D, 0x9D, 0x99, 0x81, 0x91))
        for ((nm, l) in grup) for (i in l.indices) if (l[i] >= 0) N[l[i]] = nm to modes[i]
        fun e(op: Int, nm: String, m: String) { N[op] = nm to m }
        for ((nm, b) in listOf("ASL" to 0x06, "LSR" to 0x46, "ROL" to 0x26, "ROR" to 0x66)) { e(b + 4, nm, "acc"); e(b, nm, "zp"); e(b + 0x10, nm, "zpx"); e(b + 8, nm, "abs"); e(b + 0x18, nm, "absx") }
        for ((nm, b) in listOf("DEC" to 0xC6, "INC" to 0xE6)) { e(b, nm, "zp"); e(b + 0x10, nm, "zpx"); e(b + 8, nm, "abs"); e(b + 0x18, nm, "absx") }
        e(0xA2, "LDX", "imm"); e(0xA6, "LDX", "zp"); e(0xB6, "LDX", "zpy"); e(0xAE, "LDX", "abs"); e(0xBE, "LDX", "absy")
        e(0xA0, "LDY", "imm"); e(0xA4, "LDY", "zp"); e(0xB4, "LDY", "zpx"); e(0xAC, "LDY", "abs"); e(0xBC, "LDY", "absx")
        e(0x86, "STX", "zp"); e(0x96, "STX", "zpy"); e(0x8E, "STX", "abs"); e(0x84, "STY", "zp"); e(0x94, "STY", "zpx"); e(0x8C, "STY", "abs")
        e(0xE0, "CPX", "imm"); e(0xE4, "CPX", "zp"); e(0xEC, "CPX", "abs"); e(0xC0, "CPY", "imm"); e(0xC4, "CPY", "zp"); e(0xCC, "CPY", "abs"); e(0x24, "BIT", "zp"); e(0x2C, "BIT", "abs")
        e(0x4C, "JMP", "abs"); e(0x6C, "JMP", "ind"); e(0x20, "JSR", "abs")
        for ((op, nm) in listOf(0x60 to "RTS", 0x40 to "RTI", 0x00 to "BRK", 0x18 to "CLC", 0x38 to "SEC", 0x58 to "CLI", 0x78 to "SEI", 0xB8 to "CLV", 0xD8 to "CLD", 0xF8 to "SED", 0xAA to "TAX", 0x8A to "TXA", 0xA8 to "TAY", 0x98 to "TYA",
            0xBA to "TSX", 0x9A to "TXS", 0xCA to "DEX", 0xE8 to "INX", 0x88 to "DEY", 0xC8 to "INY", 0x48 to "PHA", 0x68 to "PLA", 0x08 to "PHP", 0x28 to "PLP", 0xEA to "NOP")) e(op, nm, "imp")
        for ((op, nm) in listOf(0x10 to "BPL", 0x30 to "BMI", 0x50 to "BVC", 0x70 to "BVS", 0x90 to "BCC", 0xB0 to "BCS", 0xD0 to "BNE", 0xF0 to "BEQ")) e(op, nm, "rel")
    }
    /** Desassembla `n` instruccions des de `a` llegint amb `rb`; una línia per instrucció («$C1A7: LDA #$10»). */
    fun dis(rb: (Int) -> Int, a: Int, n: Int): List<String> {
        val out = ArrayList<String>(); var pc = a
        repeat(n) {
            val op = rb(pc and 0xFFFF); val d = N[op]
            if (d == null) { out.add("%04X: .db %02X".format(pc, op)); pc = (pc + 1) and 0xFFFF; return@repeat }
            val m = d.second; val sz = SZ[m]!!
            var v = 0; for (i in 1 until sz) v = v or (rb((pc + i) and 0xFFFF) shl (8 * (i - 1)))
            val s = when (m) {
                "imm" -> "#$%02X".format(v); "zp" -> "$%02X".format(v); "zpx" -> "$%02X,X".format(v); "zpy" -> "$%02X,Y".format(v)
                "abs" -> "$%04X".format(v); "absx" -> "$%04X,X".format(v); "absy" -> "$%04X,Y".format(v); "indx" -> "($%02X,X)".format(v); "indy" -> "($%02X),Y".format(v)
                "ind" -> "($%04X)".format(v); "rel" -> "$%04X".format((pc + 2 + (if (v > 127) v - 256 else v)) and 0xFFFF); "acc" -> "A"; else -> ""
            }
            out.add("%04X: %s %s".format(pc, d.first, s).trimEnd()); pc = (pc + sz) and 0xFFFF
        }
        return out
    }
}
