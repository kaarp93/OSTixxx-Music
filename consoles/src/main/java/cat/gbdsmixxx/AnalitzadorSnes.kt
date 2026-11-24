package cat.gbdsmixxx

/**
 * Analitzador de ROM de SNES (2026-11-24): executa la ROM des del vector de reinici amb la CPU 65816 (`Cpu65816`) i l'SPC700 de l'app (`SnesSpc`, amb la seva ROM d'arrencada),
 * mira què escriu el joc als ports $2140-$2143 i en treu:
 * - la pujada del programa de so (protocol de la ROM d'arrencada de l'SPC700): blocs (adreça de destí, mida i, si són a la ROM sense comprimir, la posició al fitxer) i l'adreça d'execució;
 * - les ordres que el joc envia als ports un cop el motor de so ha arrencat (identificadors de cançó).
 * Només escriu un informe de text i una línia `AUTO_SNES`; el driver automàtic que fa sonar el joc amb aquestes dades encara no existeix.
 * No emula la PPU: els jocs que esperen coses de la pantalla (p. ex. el rellotge del raster) poden no arribar a pujar el so.
 */
class SnesPrincipal(private val rom: RomSnes, private val spc: SnesSpc) : Bus65816 {
    private val wram = ByteArray(0x20000)
    private var wramAdr = 0
    private val regs = IntArray(0x8000)               // $4200-$437F (índex = adreça - $4200)
    private val portW = IntArray(4); private var brut = false
    var nmitimen = 0; var vblank = false; var nmiFlag = false; private var hbl = false
    var joy = 0
    var frame = 0
    private var mulA = 0; private var mulR = 0; private var divQ = 0; private var divR = 0
    val xocs = ArrayList<IntArray>()                  // instantànies dels ports (frame, p0, p1, p2, p3) cada cop que la CPU llegeix un port després d'haver-hi escrit

    private fun llegeixPort(i: Int): Int {
        if (brut) { xocs.add(intArrayOf(frame, portW[0], portW[1], portW[2], portW[3])); brut = false }
        return spc.llegeixPort(i)
    }
    override fun rd(a: Int): Int {
        val bk = (a shr 16) and 255; val o = a and 0xFFFF
        if (bk == 0x7E) return wram[o].toInt() and 255
        if (bk == 0x7F) return wram[0x10000 + o].toInt() and 255
        if ((bk and 0x7F) < 0x40) {
            if (o < 0x2000) return wram[o].toInt() and 255
            if (o < 0x2100) return 0
            if (o < 0x2140) return 0
            if (o < 0x2180) return llegeixPort(o and 3)
            if (o == 0x2180) { val v = wram[wramAdr and 0x1FFFF].toInt() and 255; wramAdr++; return v }
            if (o < 0x4000) return 0
            if (o < 0x4200) return 0
            if (o < 0x4380) return when (o) {
                0x4210 -> { val v = (if (nmiFlag) 0x80 else 0) or 2; nmiFlag = false; v }
                0x4211 -> 0
                0x4212 -> { hbl = !hbl; (if (vblank) 0x80 else 0) or (if (hbl) 0x40 else 0) }
                0x4214 -> divQ and 255; 0x4215 -> (divQ shr 8) and 255
                0x4216 -> mulR and 255; 0x4217 -> (mulR shr 8) and 255
                0x4218 -> joy and 255; 0x4219 -> (joy shr 8) and 255
                in 0x421A..0x421F -> 0
                else -> regs[o - 0x4200]
            }
        }
        val f = rom.fitxer(bk, o)
        return if (f >= 0) rom.d[f].toInt() and 255 else 0
    }
    override fun wr(a: Int, v0: Int) {
        val v = v0 and 255; val bk = (a shr 16) and 255; val o = a and 0xFFFF
        if (bk == 0x7E) { wram[o] = v.toByte(); return }
        if (bk == 0x7F) { wram[0x10000 + o] = v.toByte(); return }
        if ((bk and 0x7F) >= 0x40) return
        if (o < 0x2000) { wram[o] = v.toByte(); return }
        if (o in 0x2140..0x217F) { portW[o and 3] = v; brut = true; spc.escriuPort(o and 3, v); return }
        wrB(o, v)
    }
    /** Escriptura al bus B / registres de la CPU ($2100-$21FF, $4200-$43FF). */
    private fun wrB(o: Int, v: Int) {
        when (o) {
            0x2180 -> { wram[wramAdr and 0x1FFFF] = v.toByte(); wramAdr++ }
            0x2181 -> wramAdr = (wramAdr and 0x1FF00) or v
            0x2182 -> wramAdr = (wramAdr and 0x100FF) or (v shl 8)
            0x2183 -> wramAdr = (wramAdr and 0xFFFF) or ((v and 1) shl 16)
            0x4200 -> nmitimen = v
            0x4202 -> mulA = v
            0x4203 -> mulR = mulA * v
            0x4204 -> regs[0x4204 - 0x4200] = v
            0x4205 -> regs[0x4205 - 0x4200] = v
            0x4206 -> { val n = regs[4] or (regs[5] shl 8); if (v == 0) { divQ = 0xFFFF; divR = n } else { divQ = n / v; divR = n % v }; mulR = divR }
            0x420B -> dma(v)
            in 0x4200..0x437F -> regs[o - 0x4200] = v
        }
    }
    private fun lectB(o: Int): Int = if (o == 0x2180) { val v = wram[wramAdr and 0x1FFFF].toInt() and 255; wramAdr++; v } else if (o in 0x2140..0x2143) llegeixPort(o and 3) else 0
    private fun dma(mask: Int) {
        for (c in 0 until 8) {
            if ((mask shr c) and 1 == 0) continue
            val r = 0x100 + c * 16; val ctl = regs[r]; val dest = regs[r + 1]
            var src = regs[r + 2] or (regs[r + 3] shl 8) or (regs[r + 4] shl 16); var n = regs[r + 5] or (regs[r + 6] shl 8); if (n == 0) n = 0x10000
            val pat = when (ctl and 7) { 0 -> intArrayOf(0); 1 -> intArrayOf(0, 1); 2, 6 -> intArrayOf(0, 0); 3, 7 -> intArrayOf(0, 0, 1, 1); 4 -> intArrayOf(0, 1, 2, 3); else -> intArrayOf(0, 1, 0, 1) }
            for (k in 0 until n) {
                val bo = 0x2100 + ((dest + pat[k % pat.size]) and 255)
                if (ctl and 0x80 == 0) wrB(bo, rd(src)) else wr(src, lectB(bo))
                if (ctl and 8 == 0) src = (src and 0xFF0000) or ((src + (if (ctl and 0x10 != 0) -1 else 1)) and 0xFFFF)
            }
        }
    }
}

object AnalitzadorSnes {
    private class Bloc(val adr: Int, val dades: java.io.ByteArrayOutputStream = java.io.ByteArrayOutputStream())

    private fun cercaRom(d: ByteArray, b: ByteArray): Int {
        if (b.size < 16) return -1
        val n = minOf(b.size, 32); val f = b[0]
        var i = 0
        while (i + b.size <= d.size) {
            if (d[i] == f) { var k = 1; while (k < n && d[i + k] == b[k]) k++; if (k == n) { var j = n; while (j < b.size && d[i + j] == b[j]) j++; if (j == b.size) return i } }
            i++
        }
        return -1
    }

    fun analitza(nom: String, bytes: ByteArray, frames: Int = 2400): String {
        val sb = StringBuilder("== $nom ==\n")
        val rom = RomSnes(bytes)
        val crc = java.util.zip.CRC32().also { it.update(bytes) }.value
        sb.append("Mida %d bytes, CRC32 %08X, %s, títol «%s»\n".format(bytes.size, crc, if (rom.hiRom) "HiROM" else "LoROM", rom.titol))
        val refs = RegistreDrivers.driversDeRom(SistemaDriver.SNES, bytes)
        if (refs.isNotEmpty()) sb.append("Driver existent: ").append(refs.joinToString { it.nom }).append('\n')
        val spc = SnesSpc(); val bus = SnesPrincipal(rom, spc); val cpu = Cpu65816(bus)
        val out = ShortArray(64)
        try {
            cpu.reset()
            for (f in 0 until frames) {
                bus.frame = f; bus.vblank = true; bus.nmiFlag = true
                // el joc surt de la pantalla de títol amb Start/A a intervals
                bus.joy = if (f % 240 in 120..128) 0x1080 else 0
                if (bus.nmitimen and 0x80 != 0) cpu.nmi()
                var n = 0
                while (n < 14000) {
                    if (n >= 3000) bus.vblank = false
                    for (k in 0 until 128) cpu.pas()
                    n += 128
                    spc.executa(out, 5)
                }
            }
        } catch (e: Throwable) { sb.append("La CPU s'ha aturat al frame %d (PC %02X:%04X): %s\n".format(bus.frame, cpu.pbr, cpu.pc, e.message ?: e.javaClass.simpleName)) }
        sb.append("Estat final de la CPU: PC %02X:%04X, ports de l'SPC700 %02X %02X %02X %02X, NMI %s\n".format(cpu.pbr, cpu.pc, spc.llegeixPort(0), spc.llegeixPort(1), spc.llegeixPort(2), spc.llegeixPort(3), if (bus.nmitimen and 0x80 != 0) "activada" else "desactivada"))
        // pujada del programa de so (protocol de la ROM d'arrencada de l'SPC700)
        val blocs = ArrayList<Bloc>(); var cur: Bloc? = null; var cnt = 0; var exec = -1; var inici = 0; var comencat = false
        for ((ix, s) in bus.xocs.withIndex()) {
            val p0 = s[1]; val p1 = s[2]; val adr = s[3] or (s[4] shl 8)
            if (cur != null && p0 == cnt) { cur.dades.write(p1); cnt = (cnt + 1) and 255; continue }
            if (!comencat && p0 != 0xCC) continue                          // abans de la primera ordre d'inici (0xCC) no és la pujada
            if (p1 != 0) { cur = Bloc(adr); blocs.add(cur); cnt = 0; comencat = true } else { exec = adr; inici = ix + 1; break }
        }
        sb.append("Pujada de so: ${blocs.size} blocs").append(if (exec >= 0) ", execució a SPC \$%04X\n".format(exec) else " (sense execució: el joc no ha arribat a arrencar el motor)\n")
        val auto = StringBuilder()
        for (b in blocs) {
            val d = b.dades.toByteArray(); val pos = cercaRom(rom.d, d)
            sb.append("  SPC \$%04X, %d bytes, %s\n".format(b.adr, d.size, if (pos >= 0) "a la ROM, posició \$%X" .format(pos) else "NO és a la ROM tal qual (comprimit o muntat per codi)"))
            if (pos >= 0) auto.append("%X:%X:%X;".format(pos, b.adr, d.size))
        }
        if (exec >= 0) {
            val ords = bus.xocs.subList(inici, bus.xocs.size)
            sb.append("Ordres als ports després d'arrencar el motor: ${ords.size}. Les primeres:\n")
            for (s in ords.take(40)) sb.append("  frame %4d: %02X %02X %02X %02X\n".format(s[0], s[1], s[2], s[3], s[4]))
            if (blocs.isNotEmpty() && blocs.all { cercaRom(rom.d, it.dades.toByteArray()) >= 0 })
                sb.append("AUTO_SNES crc=%08X blocs=%s exec=%X ordres=%s\n".format(crc, auto.toString().trimEnd(';'), exec, ords.take(12).joinToString(",") { "%d:%02X%02X%02X%02X".format(it[0], it[1], it[2], it[3], it[4]) }))
        }
        return sb.toString()
    }
}
