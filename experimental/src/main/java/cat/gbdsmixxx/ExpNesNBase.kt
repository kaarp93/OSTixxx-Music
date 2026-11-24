package cat.gbdsmixxx

/*
 * Lot N (NES) d'OSTixxx Debug. Peces comunes dels drivers experimentals ExpNes*N*. Cap d'aquests drivers s'ha provat amb una ROM de l'autor: els que diuen «verificat amb ROM assemblada»
 * ho han estat amb la ROM muntada del desassemblatge (ca65/ld65) i `SinteNesRom`; la resta, només escrits des del desassemblatge. Cada pista porta l'adreça al nom.
 */

/** Equivalent a LDA #v: posa l'acumulador i també les banderes N i Z. */
internal fun Cpu6502.ldaN(v: Int) { a = v and 255; fz = a == 0; fn = a and 0x80 != 0 }

/** Cerca la signatura a la PRG una sola vegada per ROM (els drivers són objectes compartits). */
internal class CercaN(private val sig: IntArray) {
    private val cau = java.util.WeakHashMap<NesRom, Int>()
    fun adr(rom: NesRom): Int = synchronized(cau) { cau.getOrPut(rom) { rom.cerca(sig) } }
}

/** Signatura a partir d'una cadena hexadecimal; `??` = qualsevol byte. */
internal fun sigN(h: String): IntArray = h.trim().split(Regex("\\s+")).map { if (it == "??") -1 else it.toInt(16) }.toIntArray()

internal fun hN(v: Int, d: Int = 2) = v.toString(16).uppercase().padStart(d, '0')

/** Una escriptura a la RAM (adreça, valor) que demana una pista. */
internal class NR(val addr: Int, val v: Int = 1)

/** Una pista: nom (amb l'adreça) i les escriptures a la RAM que la demanen. */
internal class NP(val nom: String, vararg val r: NR)

/**
 * Driver genèric «peticions per la RAM» (motors amb banderes d'efecte/música a la RAM): `ini` arrenca el motor, cada pista posa les seves escriptures a la RAM i `fr` és la passada per fotograma.
 */
internal open class ExpNesRam(
    override val nom: String,
    private val pistes: List<NP>,
    private val rec: (NesRom) -> Boolean,
    private val ini: (Cpu6502, NesRomMem, NesRom) -> Unit,
    private val fr: (Cpu6502, NesRomMem, NesRom, Long) -> Unit,
) : NesDriver {
    override val cancons: List<String> = pistes.map { it.nom }
    override fun reconeix(rom: NesRom) = rec(rom)
    override fun inicia(cpu: Cpu6502, m: NesRomMem, rom: NesRom, id: Int) {
        cpu.sp = 0xFD; ini(cpu, m, rom)
        for (x in pistes[(id - 1).coerceIn(0, pistes.size - 1)].r) m.ram[x.addr and 0x7FF] = x.v
    }
    override fun frame(cpu: Cpu6502, m: NesRomMem, rom: NesRom, n: Long) { cpu.sp = 0xFD; fr(cpu, m, rom, n) }
}
