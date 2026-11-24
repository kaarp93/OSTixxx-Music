package cat.gbdsmixxx

/**
 * Analitzador de ROM de Game Boy / Game Boy Color: com `AnalitzadorNes`, fa córrer el joc (CPU del GB, vblank com a única interrupció, Start i A de tant en tant) i, des de la
 * rutina de l'interrupció, troba el motor per fotograma (el que escriu als registres de so). Després fa la prova en fred (màquina nova, sense res del joc): inicialització
 * opcional + engega-cançó (la cançó a A, B, C, D, E o L) o variable de RAM + motor per fotograma, i dona els codis que fan sonar. El report acaba amb una línia `AUTO_GB`.
 * El «so» es mesura per notes disparades amb volum (escriptures de disparament a NR14/NR24/NR34/NR44), no per àudio.
 */

/** Cicles de rellotge (T) de cada instrucció de la Game Boy; les instruccions condicionals fan servir el cas «no pres» i s'hi suma quan salten. `0xCB` es calcula a part. */
private val CICLES_OP = intArrayOf(
    4,12,8,8,4,4,8,4, 20,8,8,8,4,4,8,4,   4,12,8,8,4,4,8,4, 12,8,8,8,4,4,8,4,
    8,12,8,8,4,4,8,4, 8,8,8,8,4,4,8,4,    8,12,8,8,12,12,12,4, 8,8,8,8,4,4,8,4,
    4,4,4,4,4,4,8,4, 4,4,4,4,4,4,8,4,     4,4,4,4,4,4,8,4, 4,4,4,4,4,4,8,4,
    4,4,4,4,4,4,8,4, 4,4,4,4,4,4,8,4,     8,8,8,8,8,8,4,8, 4,4,4,4,4,4,8,4,
    4,4,4,4,4,4,8,4, 4,4,4,4,4,4,8,4,     4,4,4,4,4,4,8,4, 4,4,4,4,4,4,8,4,
    4,4,4,4,4,4,8,4, 4,4,4,4,4,4,8,4,     4,4,4,4,4,4,8,4, 4,4,4,4,4,4,8,4,
    8,12,12,16,12,16,8,16, 8,16,12,4,12,24,8,16,   8,12,12,4,12,16,8,16, 8,16,12,4,12,4,8,16,
    12,12,8,4,4,16,8,16, 16,4,16,4,4,4,8,16,       12,12,8,4,4,16,8,16, 12,8,16,4,4,4,8,16)

object AnalitzadorGb {
    class RG(val dest: Int, val bs: IntArray, val regs: IntArray)
    /** Motor per fotograma trobat (adreça, bancs ROM/WRAM i registres de la crida original), per al pipeline de RAM. */
    /** `sp`: SP del joc en cridar-la (2026-11-22). Amb la pila a $FFFE, les escriptures de la pila trepitjaven les variables del motor a la HRAM ($FFF0-$FFFD) i el motor saltava a llocs sense codi (Battle Crusher). */
    class MotorGb(val dest: Int, val bs: IntArray, val regs: IntArray, val sp: Int = 0xFFFE)             // rutina: adreça, bancs (ROM, WRAM) i a b c d e h l en la crida original
    private class Fr(val dest: Int, val ret: Int, val sp: Int, var toca: Boolean, val regs: IntArray, val fot: Int, val esF: Boolean)
    private class Crida(val dest: Int, val regs: IntArray, val fot: Int, val bs: IntArray, val enInt: Boolean)
    private class Mot(var cnt: Int = 0, var bs: IntArray? = null, var regs: IntArray = IntArray(7))
    private class Prova(val notes: Int, val notesFinal: Int, val hash: Int, val ultim: Int, val regs: Set<Int> = emptySet())

    private fun regs(c: GbCpu) = intArrayOf(c.a, c.b, c.c, c.d, c.e, c.h, c.l)
    private fun posaRegs(c: GbCpu, r: IntArray) { c.a = r[0]; c.b = r[1]; c.c = r[2]; c.d = r[3]; c.e = r[4]; c.h = r[5]; c.l = r[6] }
    private fun posaReg(c: GbCpu, k: Int, v: Int) { when (k) { 0 -> c.a = v; 1 -> c.b = v; 2 -> c.c = v; 3 -> c.d = v; 4 -> c.e = v; else -> c.l = v } }
    private val NOMREG = "ABCDEL"

    fun analitza(nom: String, bytes: ByteArray, fotogrames: Int = 900): String {
        val sb = StringBuilder("== ").append(nom).append(" ==\n")
        if (bytes.size < 0x150) return sb.append("No és una ROM de Game Boy.\n").toString()
        val crc = java.util.zip.CRC32().also { it.update(bytes) }.value
        val tipus = bytes[0x147].toInt() and 255; val cgb = (bytes[0x143].toInt() and 0x80) != 0
        val mbc5 = tipus in 0x19..0x1E
        val titol = String(bytes, 0x134, 15, Charsets.US_ASCII).trim { it.code == 0 || it == ' ' }
        sb.append("Mida %d bytes, CRC32 %08X, títol «%s», tipus de cartutx %02X (%s), %s\n".format(bytes.size, crc, titol, tipus,
            when (tipus) { 0 -> "ROM"; in 1..3 -> "MBC1"; in 5..6 -> "MBC2 (NO SUPORTAT)"; in 0x0F..0x13 -> "MBC3 (banc de 5 bits)"; in 0x19..0x1E -> "MBC5"; else -> "NO SUPORTAT" }, if (cgb) "GBC" else "GB"))
        // l'autor (2026-11-17): els drivers es reconeixen només pel CRC32 de la ROM (`RegistreDrivers.driversDeRom`); la signatura de bytes (`reconeix`) ja no activa res.
        // S'ometen les ROM amb un driver fet a mà o experimental i els Pokémon amb motor propi (`CrcDrivers.POKEMON`); les que només tenen un driver automàtic es tornen a analitzar.
        val refs = RegistreDrivers.driversDeRom(SistemaDriver.GB, bytes)
        val fix = refs.firstOrNull { it.tipus != TipusDriver.AUTO }?.nom ?: CrcDrivers.POKEMON[crc]?.let { "Pokémon $it" }
        sb.append("Driver existent: ").append(fix ?: refs.firstOrNull()?.nom ?: "cap").append('\n')
        if (fix != null && System.getProperty("forca") == null) return sb.append("OMESA: el joc ja té driver (").append(fix).append("); no es torna a analitzar.\n").toString()
        if (refs.isNotEmpty() && fix == null) sb.append("(driver automàtic anterior: es torna a analitzar per afinar la llista de codis)\n")

        val lim = ControlAnalisi.limits; ControlAnalisi.fase = "joc"          // límits de les estructures (per defecte els de sempre; la prova de l'app els baixa)
        val mem = GbMem(bytes, mbc5); val cpu = GbCpu(mem)
        var botons = 0; var sel = 0x30
        var fot = 0
        var notes = 0; var mascara = 0
        val vol = IntArray(4)
        var pila = ArrayList<Fr>(); var enInt = false; var inF = 0
        val lecturesF = HashSet<Int>(); val escr = ArrayDeque<IntArray>(); val escr0 = ArrayList<IntArray>(); val reqRam = LinkedHashMap<Int, Int>(); val reqFot = HashMap<Int, Int>()
        var ultimaRom = 0; var enRegs = false                  // darrera adreça de ROM llegida (banc << 16 | adreça): d'on surt el valor que s'escriu a l'APU
        // registre complet de la RAM: a cada fotograma es compara tota la WRAM (32 KB) i la HRAM amb el fotograma anterior i es desen els canvis (adreça << 8 | valor; la HRAM a partir de 0x8000)
        val logRam = System.getProperty("ramLog")?.let { java.io.DataOutputStream(java.util.zip.GZIPOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(it)))) }
        val prevW = ByteArray(0x8000); val prevH = ByteArray(0x80); val prevX = ByteArray(0x2000); val deltes = ArrayList<IntArray>()
        val fontsApu = ArrayList<String>()                     // primeres escriptures a l'APU després de cada inici de so: registre, valor i adreça de ROM d'on ve
        val anell = ArrayDeque<IntArray>()                     // últimes escriptures a l'APU: fotograma, registre, valor i darrera adreça de ROM llegida
        var escrFot = -1; var escrFotN = 0                           // límit per fotograma: el de l'arrencada (memòria netejada en bucles) omplia les 900.000 entrades abans de la petició de so
        val escrRoot = ArrayList<IntArray>()                         // escriptures de RAM amb la crida de més amunt de la pila: [fotograma, adreça, valor, rutina, banc ROM, banc WRAM, a b c d e h l]
        val apuLog = ArrayList<IntArray>()                          // (fotograma, registre, valor) de totes les escriptures a l'APU (fins a 200000)
        val cadenes = HashMap<Int, List<Int>>()           // per cada instrucció que escriu a l'APU: les rutines (CALL) obertes la primera vegada, de fora cap a dins
        val escrPc = HashMap<Int, IntArray>()                // (banc << 16 | PC) de les instruccions que escriuen als registres de so -> [vegades, dins la interrupció]
        val motors = HashMap<Int, Mot>(); val mainTouch = HashMap<Int, Mot>(); val bsMap = HashMap<Int, IntArray>()
        val spMap = HashMap<Int, Int>()                  // SP abans de la primera crida de cada rutina
        val tocaRegs = HashMap<Int, HashMap<Int, Pair<IntArray, Int>>>()   // per rutina, registres de les crides que han escrit a l'APU i quantes escriptures (trampolins com `$3EFE`: HL = destinació; es fa servir el més freqüent)
        val crides = ArrayDeque<Crida>(); val crides0 = ArrayList<Crida>()
        // si en `fotogrames` no hi ha cap inici de so, es segueix fins a 3600 (jocs amb introduccions llargues, com Castlevania Legends: la música surt al fotograma ~1700)
        var limit = fotogrames; val activitat = IntArray(maxOf(fotogrames, 3600)); val inicis = ArrayList<Int>(); val sonsOnset = ArrayList<List<Crida>>()
        // Temps per cicles (4 per instrucció, aproximat): LY, STAT, DIV, temporitzador i IF coherents; interrupcions de vblank, STAT (LYC o vblank) i temporitzador
        // doble velocitat (GBC, 2026-11-22): `ciclos` és el temps de la pantalla i `cicCpu` el de la CPU (DIV i temporitzador); en doble velocitat cada instrucció val la meitat de temps de pantalla.
        // Abans KEY1 canviava la velocitat en escriure'l (no amb STOP) i els cicles no es partien: els jocs de GBC en doble velocitat anaven a la meitat de la velocitat real.
        var velArm = false; var cicCpu = 0L
        var vel = false; var dirISR = false; var isrVec = 0; var isrBs = IntArray(2); var isrRegs = IntArray(7); var ciclos = 0L; var ifReg = 0; var tima = 0; var tma = 0; var tac = 0; var accT = 0
        mem.alLlegir = { ad ->
            when (ad) {
                0xFF00 -> { var v = 0xCF or (sel and 0x30); if (sel and 0x20 == 0) { if (botons and 8 != 0) v = v and 0xF7; if (botons and 1 != 0) v = v and 0xFE }; v }
                0xFF04 -> ((cicCpu shr 8) and 255).toInt()
                0xFF05 -> tima
                0xFF0F -> ifReg or 0xE0
                0xFF44 -> ((ciclos / 456) % 154).toInt()
                0xFF41 -> { val ly = ((ciclos / 456) % 154).toInt(); val x = (ciclos % 456).toInt(); val mode = if (ly >= 144) 1 else if (x < 80) 2 else if (x < 252) 3 else 0
                    (mem.io[0x41].toInt() and 0x78) or 0x80 or mode or (if (ly == (mem.io[0x45].toInt() and 255)) 4 else 0) }
                0xFF4D -> (if (vel) 0x80 else 0) or 0x7E or (if (velArm) 1 else 0)
                else -> -1
            }
        }
        mem.alRom = { ad -> if (Math.abs(ad - cpu.pc) > 4) ultimaRom = (mem.banc shl 16) or ad }          // lectures de dades (les de codi són a prop del PC)
        // depuració: -DwatchRam=DD78,DD82 escriu qui escriu a aquestes adreces (fotograma, PC, banc, pila de crides)
        val vigila = (System.getProperty("watchRam") ?: "").split(",").mapNotNull { it.trim().takeIf { x -> x.isNotEmpty() }?.toIntOrNull(16) }.toSet()
        mem.espia = { esc, ad, v ->
            if (esc && ad in vigila) sb.append("WATCH f=%d %04X=%02X pc=%04X banc=%d pila=%s\n".format(fot, ad, v, cpu.pc, mem.banc, pila.takeLast(6).joinToString(">") { "%04X".format(it.dest) }))
            if (!esc) { if (inF > 0) lecturesF.add(ad) }
            else {
                if (fot != escrFot) { escrFot = fot; escrFotN = 0 }
                if (pila.isNotEmpty() && (ad in 0xA000..0xDFFF || ad >= 0xFF80) && escrRoot.size < lim.escrRoot && escrFotN < 3000) { escrFotN += pila.size; for (r in pila) { val b = bsMap[r.dest]; escrRoot.add(intArrayOf(fot, ad, v, r.dest, b?.get(0) ?: 1, b?.get(1) ?: 1, r.regs[0], r.regs[1], r.regs[2], r.regs[3], r.regs[4], r.regs[5], r.regs[6])) } }
                when (ad) { 0xFF4D -> velArm = v and 1 != 0; 0xFF00 -> sel = v; 0xFF05 -> tima = v; 0xFF06 -> tma = v; 0xFF07 -> tac = v; 0xFF0F -> ifReg = v and 0x1F }
                if (inF == 0 && (ad < 0xE000 || ad >= 0xFF80) && !(ad >= cpu.sp - 0x80 && ad <= cpu.sp + 0x20)) { val e = intArrayOf(fot, ad, v, if (enInt) 1 else 0); escr.addLast(e); if (escr.size > lim.escr) escr.removeFirst(); if (fot <= 8 && escr0.size < 20000) escr0.add(e) }
            }
        }
        mem.alRegistre = { ad, v ->
            when (ad) { 0xFF12 -> vol[0] = v shr 4; 0xFF17 -> vol[1] = v shr 4; 0xFF21 -> vol[3] = v shr 4; 0xFF1C -> vol[2] = (v shr 5) and 3
                0xFF14 -> if (v and 0x80 != 0 && vol[0] > 0) notes++; 0xFF19 -> if (v and 0x80 != 0 && vol[1] > 0) notes++
                0xFF1E -> if (v and 0x80 != 0 && vol[2] > 0) notes++; 0xFF23 -> if (v and 0x80 != 0 && vol[3] > 0) notes++ }
            mascara = mascara or (1 shl ((ad - 0xFF10) / 5).coerceIn(0, 7))
            if (apuLog.size < lim.apuLog) apuLog.add(intArrayOf(fot, ad - 0xFF00, v)); anell.addLast(intArrayOf(fot, ad - 0xFF00, v, ultimaRom)); if (anell.size > 400) anell.removeFirst()
            run { val k = (cpu.pc and 0xFFFF) or (mem.banc shl 16); val e = escrPc.getOrPut(k) { IntArray(2) }; e[0]++; if (enInt) e[1]++
                if (!cadenes.containsKey(k)) cadenes[k] = pila.map { it.dest } }
            for (f in pila) { f.toca = true; val m = tocaRegs.getOrPut(f.dest) { HashMap() }; if (m.size < 64 || f.regs.contentHashCode() in m) m.merge(f.regs.contentHashCode(), Pair(f.regs, 1)) { x, _ -> Pair(x.first, x.second + 1) } }
            if (enInt && pila.isEmpty()) dirISR = true
        }
        cpu.pc = 0x100; cpu.sp = 0xFFFE; cpu.a = if (cgb) 0x11 else 0x01; cpu.b = 0; cpu.c = 0x13; cpu.d = 0; cpu.e = 0xD8; cpu.h = 0x01; cpu.l = 0x4D
        val intCnt = IntArray(5)
        var ime = false; var pilaMain = pila; var intInstr = 0; var intAtura = 0; var cicloAnt = 0L
        var cyc = 4; var halted = false
        // interrupcions imbricades (2026-11-22): una rutina d'interrupció que fa EI pot ser interrompuda (Battle Dodge Ball espera dins la interrupció que una altra posi $C006);
        // abans no se servia res fins al RETI i, als 60.000 passos, es donava per acabada (el joc anava ~8 vegades més lent). Cada nivell desa el context de l'interromput.
        class NivInt(val pila: ArrayList<Fr>, val sp: Int, val vec: Int, val bs: IntArray, val regs: IntArray, val dir: Boolean, val instr: Int, val inF: Int)
        val nivells = ArrayList<NivInt>(); var spInt = -1
        fun surtInt() {
            if (dirISR) { val m = motors.getOrPut(isrVec) { Mot() }; if (m.cnt == 0) { m.bs = isrBs; m.regs = isrRegs }; m.cnt++ }
            if (nivells.isEmpty()) { enInt = false; dirISR = false; pila = pilaMain; inF = 0; spInt = -1 }
            else { val n = nivells.removeAt(nivells.size - 1); pila = n.pila; spInt = n.sp; isrVec = n.vec; isrBs = n.bs; isrRegs = n.regs; dirISR = n.dir; intInstr = n.instr; inF = n.inF }
        }
        fun pas() {
            // HALT: la CPU dorm (es compten 4 cicles per pas, sense executar res) fins que hi ha una interrupció pendent; després continua a la instrucció següent al HALT
            val pc0 = cpu.pc; val dorm = halted && (ifReg and mem.ie and 0x1F) == 0; if (!dorm) halted = false
            val op = if (dorm) 0x00 else mem.rb(pc0); val sp0 = cpu.sp
            // bucles de retard (DEC DE/BC, NOP, LD A,r, OR r, JR NZ enrere): se salten (el joc els fa servir per esperar fotogrames, p. ex. l'enviament de paquets SGB a Castlevania Legends)
            if (op == 0x20) { val d = mem.rb(pc0 + 1).toByte().toInt()
                if (d < 0 && d >= -12) {
                    var cic = 12; var ok = true; var usaDe = false; var usaBc = false
                    for (k in (pc0 + 2 + d) until pc0) { when (mem.rb(k)) { 0x1B -> { usaDe = true; cic += 8 }; 0x0B -> { usaBc = true; cic += 8 }; 0x00, 0x7A, 0x7B, 0x78, 0x79, 0xB3, 0xB1 -> cic += 4; else -> ok = false } }
                    if (ok && usaDe != usaBc) { val n = if (usaDe) (cpu.d shl 8) or cpu.e else (cpu.b shl 8) or cpu.c
                        if (n > 2) { val cc = (n - 1).toLong() * cic; cicCpu += cc; ciclos += if (vel) cc / 2 else cc; if (usaDe) { cpu.d = 0; cpu.e = 1 } else { cpu.b = 0; cpu.c = 1 } } }
                } }
            if (!dorm) cpu.step()
            // cicles reals de la instrucció (sense això el joc anava moltes vegades més ràpid que el real respecte al temporitzador i al vblank)
            run {
                val npc = cpu.pc
                var cy = if (op == 0xCB) { val cb = mem.rb(pc0 + 1); if (cb and 7 == 6) (if (cb in 0x40..0x7F) 12 else 16) else 8 } else CICLES_OP[op]
                if (op == 0x20 || op == 0x28 || op == 0x30 || op == 0x38) { if (npc != ((pc0 + 2) and 0xFFFF)) cy = 12 }
                else if (op == 0xC2 || op == 0xCA || op == 0xD2 || op == 0xDA) { if (npc != ((pc0 + 3) and 0xFFFF)) cy = 16 }
                else if (op == 0xC4 || op == 0xCC || op == 0xD4 || op == 0xDC) { if (npc != ((pc0 + 3) and 0xFFFF)) cy = 24 }
                else if (op == 0xC0 || op == 0xC8 || op == 0xD0 || op == 0xD8) { if (npc != ((pc0 + 1) and 0xFFFF)) cy = 20 }
                else if (op == 0x76 && (ifReg and mem.ie and 0x1F) == 0) halted = true           // HALT sense cap interrupció pendent: dorm
                cyc = cy; cicCpu += cy; ciclos += if (vel) cy / 2 else cy
                if (op == 0x10 && velArm) { vel = !vel; velArm = false }       // STOP amb KEY1 armat: canvi de velocitat (GBC)
            }
            when {
                op == 0xFB -> ime = true
                op == 0xF3 -> ime = false
                op == 0xD9 -> { ime = true; if (cpu.sp == sp0 + 2 && enInt) surtInt() else if (cpu.sp == sp0 + 2) retorna(pila, mem, motors, mainTouch, enInt, bsMap, cpu, sp0) { inF-- } }
                (op == 0xCD || (op and 0xE7) == 0xC4 || (op and 0xC7) == 0xC7) && cpu.sp == ((sp0 - 2) and 0xFFFF) -> {
                    val dest = cpu.pc; val esF = motors.containsKey(dest)
                    if (esF) inF++
                    pila.add(Fr(dest, (mem.rb0ret(sp0 - 2)), cpu.sp, false, regs(cpu), fot, esF))
                    val bs = mem.copiaBancs(); bsMap[dest] = bs; spMap.getOrPut(dest) { (cpu.sp + 2) and 0xFFFF }
                    val c = Crida(dest, regs(cpu), fot, bs, enInt)
                    crides.addLast(c); if (fot <= 10 && crides0.size < 3000) crides0.add(c)
                }
                // la interrupció acaba amb RET (p. ex. «EI; RET»): el RET que treu l'adreça de tornada que s'hi va desar
                (op == 0xC9 || (op and 0xE7) == 0xC0) && cpu.sp == ((sp0 + 2) and 0xFFFF) && enInt && sp0 == spInt && pila.all { it.sp < sp0 } -> surtInt()
                (op == 0xC9 || (op and 0xE7) == 0xC0) && cpu.sp == ((sp0 + 2) and 0xFFFF) -> retorna(pila, mem, motors, mainTouch, enInt, bsMap, cpu, sp0) { inF-- }
            }
            // temporitzador
            if (tac and 4 != 0) { accT += cyc; val per = when (tac and 3) { 0 -> 1024; 1 -> 16; 2 -> 64; else -> 256 }; while (accT >= per) { accT -= per; tima++; if (tima > 255) { tima = tma; ifReg = ifReg or 4 } } }
            // vblank i STAT (LYC o vblank): es marquen en creuar la línia
            val l0 = ((cicloAnt / 456) % 154).toInt(); val l1 = ((ciclos / 456) % 154).toInt()
            if (l1 != l0) {
                if (l1 == 144) { ifReg = ifReg or 1; if (mem.io[0x41].toInt() and 0x10 != 0) ifReg = ifReg or 2 }
                if (l1 == (mem.io[0x45].toInt() and 255) && (mem.io[0x41].toInt() and 0x40) != 0) ifReg = ifReg or 2
            }
            cicloAnt = ciclos
            // interrupcions
            if (ime && nivells.size < 8) {
                val pend = ifReg and mem.ie and 0x1F
                if (pend != 0) {
                    val bit = if (pend and 1 != 0) 0 else if (pend and 2 != 0) 1 else if (pend and 4 != 0) 2 else if (pend and 8 != 0) 3 else 4
                    ifReg = ifReg and (1 shl bit).inv(); intCnt[bit]++
                    val pc = cpu.pc
                    cpu.sp = (cpu.sp - 2) and 0xFFFF; mem.wb(cpu.sp, pc and 255); mem.wb(cpu.sp + 1, pc shr 8)
                    if (enInt) { nivells.add(NivInt(pila, spInt, isrVec, isrBs, isrRegs, dirISR, intInstr, inF)); inF = 0 } else pilaMain = pila
                    cpu.pc = 0x40 + 8 * bit; ime = false; cicCpu += 20; ciclos += if (vel) 10 else 20; pila = ArrayList(); enInt = true; intInstr = 0; spInt = cpu.sp
                    isrVec = cpu.pc; isrBs = mem.copiaBancs(); isrRegs = regs(cpu); dirISR = false
                }
            }
            if (enInt && ++intInstr > 60000) { intAtura++; if (intAtura <= 2) { val pc = cpu.pc; val off = if (pc < 0x4000) pc else mem.banc * 0x4000 + (pc - 0x4000); sb.append("Interrupció aturada després de 60000 instruccions a PC=\$%04X banc=%d: ".format(pc, mem.banc)).append((maxOf(0, off - 6) until minOf(bytes.size, off + 14)).joinToString(" ") { "%02X".format(bytes[it].toInt() and 255) }).append("  pila: ").append(pila.joinToString(" > ") { "\$%04X".format(it.dest) }).append('\n') }; enInt = false; pila = pilaMain; inF = 0; nivells.clear(); spInt = -1 }
        }
        var fotAct = 0; var n0 = 0
        while (fotAct < limit) {
            ControlAnalisi.comprova(); ControlAnalisi.fotograma = fotAct
            fot = fotAct; mascara = 0
            botons = if (fotAct % 120 in 20..25) 8 else if (fotAct % 120 in 60..65) 1 else 0
            n0 = notes
            while (ciclos / 70224 == fotAct.toLong()) pas()
            val f = fotAct
            run { val canvis = ArrayList<Int>(); for (i in 0 until 0x8000) { val v = mem.wram[i]; if (v != prevW[i]) { prevW[i] = v; canvis.add((i shl 8) or (v.toInt() and 255)) } }
                for (i in 0 until 0x80) { val v = mem.hram[i]; if (v != prevH[i]) { prevH[i] = v; canvis.add(((0x8000 + i) shl 8) or (v.toInt() and 255)) } }
                for (i in 0 until 0x2000) { val v = mem.xram[i]; if (v != prevX[i]) { prevX[i] = v; canvis.add(((0x8080 + i) shl 8) or (v.toInt() and 255)) } }          // RAM del cartutx
                deltes.add(canvis.toIntArray())
                if (logRam != null) { logRam.writeInt(canvis.size); for (c in canvis) logRam.writeInt(c); logRam.writeInt(mem.banc); logRam.writeInt(notes) } }
            activitat[f] = notes - n0
            while (crides.isNotEmpty() && crides.first().fot < f - 45) crides.removeFirst()
            if (activitat[f] > 0 && f >= 40 && (1..40).all { activitat[f - it] == 0 }) {
                inicis.add(f)
                if (inicis.size <= 3) { fontsApu.add("-- inici de so al fotograma $f --"); for (e in anell) if (e[0] >= f - 1) fontsApu.add("f%d NR%02X=%02X ← ROM banc %d \$%04X".format(e[0], e[1], e[2], e[3] shr 16, e[3] and 0xFFFF)) }
                if (inicis.size <= 4) {
                    val antics = crides.filter { it.fot in (f - 40)..(f - 10) }.map { it.dest }.toSet()
                    sonsOnset.add(crides.filter { it.fot in (f - 4)..f && it.dest !in antics }.distinctBy { it.dest }.take(40))
                    sb.append("--- Inici de so al fotograma ").append(f).append(": rutines noves cridades en els 4 fotogrames anteriors (adreça, banc, a b c d e h l) ---\n")
                    for (c in sonsOnset.last().take(10)) sb.append("  \$%04X banc %d  %s\n".format(c.dest, c.bs[0], c.regs.joinToString(" ") { "%02X".format(it) }))
                    for (soloMain in listOf(true, false)) { if (reqRam.isNotEmpty()) break; for (e in escr.reversed()) if (e[0] in (f - 120)..f && e[2] != 0 && e[1] in lecturesF && (!soloMain || e[3] == 0) && reqRam.size < 64) { reqRam[e[1]] = e[2]; reqFot[e[1]] = e[0] } }          // primer només el que escriu el fil principal (el motor també escriu les seves variables des de la interrupció)
                }
            }
            fotAct++
            if (fotAct == limit && fotAct == fotogrames && inicis.isEmpty()) limit = 3600
        }
        // diagnòstic: on és el joc ara (els 6 PC més freqüents en 3000 instruccions) i els bytes de l'entorn, per veure què espera si no arriba al so
        run {
            val cnt = HashMap<Int, Int>(); repeat(3000) { cnt.merge(cpu.pc, 1, Int::plus); try { cpu.step() } catch (e: Throwable) { } }
            sb.append("Interrupcions servides (vblank, STAT, timer, serial, joypad): ").append(intCnt.joinToString(" ")).append("; IF=%02X IE=%02X IME=%b; HRAM FF80-FF8F: ".format(ifReg, mem.ie, ime)).append((0x80..0x8F).joinToString(" ") { "%02X".format(mem.hram[it - 0x80].toInt() and 255) }).append("; HRAM FFE0-FFEF: ").append((0x60..0x6F).joinToString(" ") { "%02X".format(mem.hram[it].toInt() and 255) }).append("; WRAM C530-C54F: ").append((0xC530..0xC54F).joinToString(" ") { "%02X".format(mem.rb0(it) and 255) }).append('\n')
            sb.append("Estat final: banc=%d, SP=\$%04X, PC més freqüents: ".format(mem.banc, cpu.sp))
            val top = cnt.entries.sortedByDescending { it.value }.take(6)
            sb.append(top.joinToString(", ") { "\$%04X(%d)".format(it.key, it.value) }).append('\n')
            for ((a, _) in top.take(3)) sb.append("   bytes a \$%04X: ".format(a)).append((0 until 14).joinToString(" ") { "%02X".format(mem.rb((a + it) and 0xFFFF)) }).append('\n')
        }
        logRam?.close()
        sb.append("Interrupció vblank: IE=%02X, IME final=%b%s\n".format(mem.ie, ime, if (intAtura > 0) "; la rutina no ha tornat en " + intAtura + " fotogrames" else ""))
        sb.append("Notes disparades per cada 60 fotogrames: ")
        for (b in 0 until limit / 60) sb.append((b * 60 until b * 60 + 60).sumOf { activitat[it] }).append(' ')
        sb.append('\n')
        // jocs que sonen des del principi (abans del fotograma 40) o amb sons constants: no hi ha mai 40 fotogrames de silenci previ; si hi ha hagut notes, el primer fotograma amb notes és l'inici
        if (inicis.isEmpty()) { val primer = (0 until minOf(limit, activitat.size)).firstOrNull { activitat[it] > 0 }; if (primer != null) { inicis.add(primer); sb.append("Cap silenci previ: es pren com a inici de so el primer fotograma amb notes (").append(primer).append(")\n") } }
        sb.append(if (inicis.isEmpty()) "Cap inici de so detectat (silenci -> so).\n" else "Inicis de so als fotogrames: " + inicis.take(20).joinToString(", ") + "\n")
        sb.append("Rutines que escriuen als registres de so des de la interrupció: ").append(motors.entries.sortedByDescending { it.value.cnt }.take(6).joinToString(", ") { "\$%04X (%d)".format(it.key, it.value.cnt) }.ifEmpty { "cap" }).append('\n')
        for ((k, e) in escrPc.entries.sortedByDescending { it.value[0] }.take(8)) {
            val pc = k and 0xFFFF; val bk = k shr 16; val off = if (pc < 0x4000) pc else bk * 0x4000 + (pc - 0x4000)
            val ini = maxOf(0, off - 8); val fi = minOf(bytes.size, off + 12)
            sb.append("Escriptura als registres de so: PC=\$%04X banc=%d (%d vegades, %d dins la interrupció): ".format(pc, bk, e[0], e[1])).append((ini until fi).joinToString(" ") { "%02X".format(bytes[it].toInt() and 255) }).append("  cadena: ").append(cadenes[k]?.joinToString(" > ") { "\$%04X".format(it) } ?: "-").append('\n')
        }
        if (fontsApu.isNotEmpty()) { sb.append("Primeres escriptures a l'APU després dels inicis de so (valor ← adreça de ROM llegida just abans):\n"); for (l in fontsApu.take(120)) sb.append("  ").append(l).append('\n') }
        sb.append("Rutines que hi escriuen des del fil principal: ").append(mainTouch.entries.sortedByDescending { it.value.cnt }.take(6).joinToString(", ") { "\$%04X (%d)".format(it.key, it.value.cnt) }.ifEmpty { "cap" }).append('\n')
        try {
            val cands = ArrayList<Crida>(); for (l in sonsOnset) cands.addAll(l); cands.addAll(crides0.distinctBy { it.dest }.take(150))
            if (reqRam.isEmpty()) for (e in escr0) if (e[2] != 0 && e[1] in lecturesF && reqRam.size < 64) { reqRam[e[1]] = e[2]; reqFot[e[1]] = e[0] }
            auto(sb, bytes, mbc5, cgb, crc, motors, mainTouch, cands, reqRam, reqFot)
            // pipeline per RAM: candidats a «rutina de cada fotograma» = totes les rutines de la cadena de crides de cada escriptura a l'APU, ordenades per quantes escriptures en pengen
            try {
                val pes = HashMap<Int, Int>()
                for ((k, ch) in cadenes) { val n = escrPc[k]?.get(0) ?: 1; for (d in ch.toSet()) pes[d] = (pes[d] ?: 0) + n }
                for ((d, m) in motors) pes[d] = (pes[d] ?: 0) + m.cnt * 1000
                for ((d, m) in mainTouch) if (m.cnt >= 200) pes[d] = (pes[d] ?: 0) + m.cnt * 1000
                val cands = pes.entries.sortedByDescending { it.value }.take(24).map { (d, _) -> val m0 = motors[d] ?: mainTouch[d]; MotorGb(d, bsMap[d] ?: m0?.bs ?: isrBs, m0?.regs ?: tocaRegs[d]?.values?.maxByOrNull { it.second }?.first ?: IntArray(7), spMap[d] ?: 0xFFFE) }
                if (System.getProperty("dbgCands") != null) sb.append("Candidates RAM: ").append(cands.joinToString(" ") { "\$%04X:%d:%s".format(it.dest, it.bs[0], it.regs.joinToString("") { r -> "%02X".format(r) }) }).append('\n')
                System.getProperty("dumpApu")?.let { fp -> try { java.io.File(fp).writeText(apuLog.joinToString("\n") { "f" + it[0] + " FF" + "%02X".format(it[1]) + "=" + "%02X".format(it[2]) }) } catch (e: Throwable) { } }
                ControlAnalisi.fase = "ram"
                sb.append(GbRamPipeline.executa(bytes, mbc5, crc, deltes, inicis, cands, apuLog, escrRoot, System.currentTimeMillis(), 150000))
            } catch (e: Throwable) { sb.append("RAM: error ").append(e.javaClass.simpleName).append(' ').append(e.message ?: "").append('\n') }
        } catch (e: Throwable) { sb.append("AUTO: error ").append(e.javaClass.simpleName).append(' ').append(e.message ?: "").append('\n') }
        return sb.toString()
    }

    private fun GbMem.rb0ret(a: Int): Int = (rb(a and 0xFFFF) or (rb((a + 1) and 0xFFFF) shl 8)) and 0xFFFF

    private fun retorna(pila: ArrayList<Fr>, mem: GbMem, motors: HashMap<Int, Mot>, mainTouch: HashMap<Int, Mot>, enInt: Boolean, bsMap: HashMap<Int, IntArray>, cpu: GbCpu, sp0: Int, desF: () -> Unit) {
        // el RET només tanca la crida que va desar aquesta adreça de tornada (`Fr.sp` == SP abans del RET); un RET d'una adreça empilada a mà («PUSH DE; JP (HL)», trampolins de
        // canvi de banc com el de Super Black Bass `$3EFE`) no tanca res, i les crides de sota del SP (abandonades) es treuen (2026-11-22; abans es treia sempre la de dalt)
        while (pila.isNotEmpty() && pila[pila.size - 1].sp < sp0) { if (pila.removeAt(pila.size - 1).esF) desF() }
        if (pila.isEmpty() || pila[pila.size - 1].sp != sp0) return
        val f = pila.removeAt(pila.size - 1)
        if (f.esF) desF()
        // fil principal: el bucle principal no torna mai; es compta la crida de més amunt feta en aquest fotograma (com a la interrupció, on la pila comença buida)
        if (f.toca && (pila.isEmpty() || (!enInt && pila[pila.size - 1].fot < f.fot))) {
            val m = (if (enInt) motors else mainTouch).getOrPut(f.dest) { Mot() }
            if (m.cnt == 0) { m.bs = bsMap[f.dest]; m.regs = f.regs }
            m.cnt++
        }
    }

    // ----- prova en fred -----
    private fun prova(rom: ByteArray, mbc5: Boolean, ini: RG?, p: RG?, regCodi: Int, ramAd: Int, a: Int, f: RG, fots: Int, extra: Map<Int, Int> = emptyMap()): Prova {
        val mem = GbMem(rom, mbc5); val cpu = GbCpu(mem)
        var hash = 0; var notes = 0; val vol = IntArray(4); var frAct = 0; var ultim = 0; val regs = HashSet<Int>()
        mem.alRegistre = { ad, v ->
            hash = hash * 31 + ad * 257 + v; regs.add(ad * 256 + v)
            when (ad) { 0xFF12 -> vol[0] = v shr 4; 0xFF17 -> vol[1] = v shr 4; 0xFF21 -> vol[3] = v shr 4; 0xFF1C -> vol[2] = (v shr 5) and 3
                0xFF14 -> if (v and 0x80 != 0 && vol[0] > 0) { notes++; ultim = frAct }; 0xFF19 -> if (v and 0x80 != 0 && vol[1] > 0) { notes++; ultim = frAct }
                0xFF1E -> if (v and 0x80 != 0 && vol[2] > 0) { notes++; ultim = frAct }; 0xFF23 -> if (v and 0x80 != 0 && vol[3] > 0) { notes++; ultim = frAct } }
        }
        cpu.sp = 0xFFFE
        if (ini != null) { mem.posaBancs(ini.bs); posaRegs(cpu, ini.regs); cpu.sp = 0xFFFE; cpu.crida(ini.dest, 200000) }
        if (p != null) { mem.posaBancs(p.bs); posaRegs(cpu, p.regs); posaReg(cpu, regCodi, a); cpu.sp = 0xFFFE; cpu.crida(p.dest, 200000) } else { mem.wb(ramAd, a); for ((ad, v) in extra) mem.wb(ad, v) }
        var notesFinal = 0
        for (i in 0 until fots) {
            frAct = i; val n0 = notes; ControlAnalisi.comprova()
            mem.posaBancs(f.bs); posaRegs(cpu, f.regs); cpu.sp = 0xFFFE; cpu.crida(f.dest, 200000)
            if (i >= fots - 10) notesFinal += notes - n0
        }
        return Prova(notes, notesFinal, hash, ultim, regs)
    }
    private fun sona(p: Prova) = p.notes >= 3 && (p.notesFinal >= 1 || p.notes >= 8)          // música que té silencis al final dels 40 fotogrames (Castlevania Adventure) també compta si fa moltes notes
    private class Cand(val ini: RG?, val p: RG?, val reg: Int, val ramAd: Int, val extra: Map<Int, Int> = emptyMap())
    private class Res(val c: Cand, val codis: List<Pair<Int, Prova>>)

    private fun auto(sb: StringBuilder, rom: ByteArray, mbc5: Boolean, cgb: Boolean, crc: Long, motors: HashMap<Int, Mot>, mainTouch: HashMap<Int, Mot>, cands: List<Crida>, reqRam: Map<Int, Int>, reqFot: Map<Int, Int>) {
        sb.append("--- PROVA EN FRED (força bruta) ---\n"); ControlAnalisi.fase = "fred"
        // el motor per fotograma és el de la interrupció o, si el joc crida el so des del bucle principal (després d'esperar el vblank), la rutina del fil principal que més es crida
        val fm = motors.entries.filter { it.value.bs != null }.maxByOrNull { it.value.cnt } ?: mainTouch.entries.filter { it.value.bs != null && it.value.cnt >= 200 }.maxByOrNull { it.value.cnt }
        if (fm == null) { sb.append("AUTO: cap motor per fotograma (no es pot provar)\n"); return }
        val f = RG(fm.key, fm.value.bs!!, fm.value.regs)
        val inis = mainTouch.entries.filter { it.value.bs != null && it.value.cnt <= 3 && it.key != fm.key }.sortedBy { it.value.cnt }.take(3).map { RG(it.key, it.value.bs!!, it.value.regs) }
        val ps = cands.filter { it.dest != f.dest }.groupBy { it.dest }.values.take(120)
        sb.append("Motor per fotograma: \$%04X; inicialitzacions: %d; engega-cançó candidates: %d; RAM candidates: %s\n".format(f.dest, inis.size, ps.size, if (reqRam.isEmpty()) "cap" else reqRam.entries.joinToString(" ") { "\$%04X=%02X".format(it.key, it.value) }))
        val t0 = System.currentTimeMillis()
        fun escaneja(c: Cand, limit: Long): Res {
            val vistos = HashSet<Int>(); val codis = ArrayList<Pair<Int, Prova>>()
            for (a in 0..255) {
                if (System.currentTimeMillis() - t0 > limit) break
                val pr = prova(rom, mbc5, c.ini, c.p, c.reg, c.ramAd, a, f, 40, c.extra)
                if (sona(pr) && vistos.add(pr.hash)) codis.add(a to pr)
            }
            return Res(c, codis.filter { x -> codis.none { o -> o.first != x.first && o.second.regs.size * 10 > x.second.regs.size * 12 && x.second.regs.count { it in o.second.regs } * 10 >= x.second.regs.size * 9 } })
        }
        val troba = ArrayList<Cand>()
        for (ini in listOf<RG?>(null) + inis) {
            if (ini != null && troba.isNotEmpty()) break
            for (g in ps) {
                val c0 = g[0]; val p = RG(c0.dest, c0.bs, c0.regs)
                loop@ for (reg in intArrayOf(0, 2, 1, 4, 3, 5)) for (a in (listOf(c0.regs[reg]) + listOf(0, 1, 2, 3, 5)).distinct()) {
                    if (System.currentTimeMillis() - t0 > 90000) break@loop
                    if (sona(prova(rom, mbc5, ini, p, reg, -1, a, f, 40))) { troba.add(Cand(ini, p, reg, -1)); break@loop }
                }
            }
        }
        var millor: Res? = null
        // es prova fins a 6 combinacions i es queda la que fa sonar més codis «de música» (molts de 40 fotogrames amb moltes notes): un engega-so d'efectes també fa sonar, però amb poques notes
        fun puntuacio(r: Res) = r.codis.count { it.second.notes >= 15 }
        for (c in troba.distinctBy { (it.ini?.dest ?: -1) to (it.p?.dest ?: -1) }.take(6)) {
            val e = escaneja(c, 150000); if (millor == null || puntuacio(e) > puntuacio(millor)) millor = e
        }
        if ((millor?.codis?.size ?: 0) < 2) for (ini in listOf<RG?>(null) + inis) for ((ad, v) in reqRam) {
            if (System.currentTimeMillis() - t0 > 130000) break
            // variables que el joc escriu al mateix temps que la de la cançó (com a Astro Fang a NES): s'escriuen també
            val fa = reqFot[ad]
            val extra = if (fa == null) emptyMap() else reqRam.filter { it.key != ad && Math.abs((reqFot[it.key] ?: -99) - fa) <= 1 }
            for (ex in listOf(emptyMap<Int, Int>(), extra).distinct()) {
                val c = Cand(ini, null, 0, ad, ex)
                if (listOf(v, 1, 0x81, 0x82, v or 0x80).distinct().none { sona(prova(rom, mbc5, ini, null, 0, ad, it, f, 40, ex)) }) continue
                val e = escaneja(c, 160000); if (e.codis.size >= 2 && (millor == null || e.codis.size > millor.codis.size)) millor = e
            }
        }
        val m = millor
        if (m == null || m.codis.isEmpty()) { sb.append("AUTO: cap combinació fa sonar (%d s)\n".format((System.currentTimeMillis() - t0) / 1000)); return }
        val sbc = StringBuilder()
        for ((a, _) in m.codis) {
            if (System.currentTimeMillis() - t0 > 175000) { sbc.append("%02X:? ".format(a)); continue }
            val l = prova(rom, mbc5, m.c.ini, m.c.p, m.c.reg, m.c.ramAd, a, f, 660, m.c.extra)
            sbc.append("%02X:%s ".format(a, if (l.notesFinal >= 1) "L" else "%ds".format(maxOf(1, l.ultim / 60))))
        }
        fun rg(r: RG?) = if (r == null) "-" else "%04X:%d,%d:%s".format(r.dest, r.bs[0], r.bs[1], r.regs.joinToString("") { "%02X".format(it) })
        sb.append("AUTO_GB crc=%08X mbc5=%d cgb=%d f=%s ini=%s p=%s reg=%s ram=%s codis=%s\n".format(crc, if (mbc5) 1 else 0, if (cgb) 1 else 0, rg(f), rg(m.c.ini), rg(m.c.p), if (m.c.p == null) "-" else NOMREG[m.c.reg].toString(),
            if (m.c.p == null) "%04X".format(m.c.ramAd) + m.c.extra.entries.joinToString("") { "+%04X:%02X".format(it.key, it.value) } else "-", sbc.toString().trim()))
        sb.append("AUTO: %d codis amb so (L = bucle, Ns = dura N segons), via %s, %d s de prova%s\n".format(m.codis.size, if (m.c.p == null) "RAM" else "rutina", (System.currentTimeMillis() - t0) / 1000, if (m.codis.size < 2) " -- DUBTÓS" else ""))
    }
}
