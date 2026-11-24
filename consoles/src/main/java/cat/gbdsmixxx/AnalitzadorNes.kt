package cat.gbdsmixxx

/**
 * Analitzador de ROM de NES: fa córrer el joc amb una PPU simulada (només `$2002` i els comandaments), prem Start i A de tant en tant, i escriu un report de text amb el que
 * cal per fer un driver de so: capçalera, mapper, vectors, línia de temps de l'activitat de l'APU (i del VRC6), les rutines que escriuen als registres de so des de l'NMI (el «motor
 * per fotograma») i, a cada inici de so, les rutines noves cridades just abans (candidates a «engega la cançó», amb A, X i Y) amb el desassemblat. No fa cap driver: dona les dades.
 */
object AnalitzadorNes {
    private val MAPPERS_OK = setOf(0, 1, 2, 3, 4, 7, 11, 24, 26)
    private class Crida(val dest: Int, val a: Int, val x: Int, val y: Int, val fot: Int, val enNmi: Boolean, val bancs: String, val octets: IntArray, val bs: IntArray)
    /** Una rutina per a la prova en fred: adreça, bancs en el moment de la crida i registres. */
    private class Cand(val dest: Int, val bs: IntArray, val a: Int, val x: Int, val y: Int)
    private class Motor(var cnt: Int = 0, var bancs: String = "", var octets: IntArray = IntArray(0), val crides: ArrayList<String> = ArrayList(), var bs: IntArray? = null, var a: Int = 0, var x: Int = 0, var y: Int = 0)

    fun analitza(nom: String, bytes: ByteArray, fotogrames: Int = 900): String {
        val sb = StringBuilder()
        val r = NesRom(bytes)
        sb.append("== ").append(nom).append(" ==\n")
        if (!r.valid) return sb.append("No és una ROM iNES.\n").toString()
        val crc = java.util.zip.CRC32().also { it.update(bytes) }.value
        sb.append("Mida %d bytes, CRC32 %08X, mapper %d%s, PRG %d x 16 KB, CHR %d x 8 KB\n".format(bytes.size, crc, r.mapper, if (r.mapper in MAPPERS_OK) "" else " (NO SUPORTAT: s'emula com a NROM)", r.prgBancs, bytes[5].toInt() and 255))
        // Només pel CRC32 de la ROM (l'autor, 2026-11-17), com a GB: s'ometen les ROM amb driver fet a mà o experimental.
        val refs = RegistreDrivers.driversDeRom(SistemaDriver.NES, bytes)
        val fix = refs.firstOrNull { it.tipus != TipusDriver.AUTO }?.nom
        sb.append("Driver existent: ").append(fix ?: refs.firstOrNull()?.nom ?: "cap").append('\n')
        if (fix != null && System.getProperty("forca") == null) return sb.append("OMESA: el joc ja té driver (").append(fix).append("); no es torna a analitzar.\n").toString()
        if (refs.isNotEmpty() && fix == null) sb.append("(driver automàtic anterior: es torna a analitzar per afinar la llista de codis)\n")
        var ap: NesApu? = null
        val mem = NesRomMem(r) { ap }
        ap = NesApu(8000) { mem.rb(it) }
        val apu = ap
        val vrc6 = r.mapper == 24 || r.mapper == 26
        var vb = 0; var nmiOn = false; var botons = 0; var strobe = false; var shift = 0
        var escrFot = 0; var mascara = 0; var enNmi = false; var curFot = 0                // local: l'analitzador es pot cridar des de diversos fils alhora
        var inF = 0; val lecturesF = HashSet<Int>(); val escrRam = ArrayDeque<IntArray>(); val escrRam0 = ArrayList<IntArray>(); val reqRam = LinkedHashMap<Int, Int>(); val reqFot = HashMap<Int, Int>()
        var pila = ArrayList<IntArray>()
        var tocat = false
        var cpuPc0 = 0                                   // PC de l'instrucció en curs (per saber d'on surten les escriptures a l'APU)
        val motors = HashMap<Int, Motor>()                 // rutines (JSR des de l'NMI) que han escrit a l'APU
        val mainTouch = HashMap<Int, Motor>()              // el mateix, des del fil principal
        val escrPc = HashMap<Int, IntArray>()            // PC de l'instrucció que escriu a l'APU -> [vegades, de dins l'NMI]
        fun toca(bit: Int) {
            val e = escrPc.getOrPut(cpuPc0) { IntArray(2) }; e[0]++; if (enNmi) e[1]++
            tocat = true
            escrFot++; mascara = mascara or bit
            for (f in pila) f[3] = 1
        }
        val rb: (Int) -> Int = { ad ->
            if (ad in 0x2000..0x3FFF) { if ((ad and 7) == 2) { vb++; when (vb and 3) { 1 -> 0x80; 2 -> 0xC0; 3 -> 0x40; else -> 0 } } else 0 }
            else if (ad == 0x4016) { if (strobe) botons and 1 else { val b = shift and 1; shift = (shift shr 1) or 0x80; b } }
            else if (ad == 0x4017) 0 else { if (inF > 0 && ad < 0x800) lecturesF.add(ad); mem.rb(ad) }
        }
        val wb: (Int, Int) -> Unit = { ad, v ->
            if (ad == 0x2000) nmiOn = v and 0x80 != 0
            else if (ad == 0x4016) { strobe = v and 1 != 0; if (strobe) shift = botons }
            if (ad in 0x4000..0x4013) toca(1 shl ((ad - 0x4000) / 4)) else if (ad == 0x4015) toca(32)
            else if (vrc6 && ad in 0x9000..0xB003) toca(64)
            if (inF == 0 && ad < 0x800) { val e = intArrayOf(curFot, ad, v); escrRam.addLast(e); if (escrRam.size > 40000) escrRam.removeFirst(); if (curFot <= 8 && escrRam0.size < 20000) escrRam0.add(e) }
            mem.wb(ad, v)
        }
        val cpu = Cpu6502(rb, wb)
        val nmiV = rb(0xFFFA) or (rb(0xFFFB) shl 8); val rstV = rb(0xFFFC) or (rb(0xFFFD) shl 8); val irqV = rb(0xFFFE) or (rb(0xFFFF) shl 8)
        sb.append("Vectors: NMI=\$%04X RESET=\$%04X IRQ=\$%04X\n".format(nmiV, rstV, irqV))
        cpu.pc = rstV; cpu.sp = 0xFD
        val crides = ArrayDeque<Crida>(); val crides0 = ArrayList<Crida>(); val bsMap = HashMap<Int, IntArray>()
        val sonsOnset = ArrayList<List<Crida>>()
        val pics = DoubleArray(fotogrames); val escr = IntArray(fotogrames); val masc = IntArray(fotogrames)
        val inicis = ArrayList<Int>()
        var primerNmi = -1; var nmiAtura = 0
        val muts = BooleanArray(5)
        fun registra(f: IntArray) {
            val m = (if (enNmi) motors else mainTouch).getOrPut(f[0]) { Motor() }
            if (m.cnt == 0) { m.bancs = mem.estatBancs(); m.octets = IntArray(48) { rb((f[0] + it) and 0xFFFF) }; m.bs = bsMap[f[0]]; m.a = f[4]; m.x = f[5]; m.y = f[6] }
            if (m.crides.size < 8 && (m.cnt < 6 || f[4] != 0)) m.crides.add("fot=%d A=\$%02X X=\$%02X Y=\$%02X".format(f[7], f[4], f[5], f[6]))
            m.cnt++
        }
        fun pas() {
            val pc0 = cpu.pc; val op = rb(pc0); cpuPc0 = pc0
            if (op == 0x20) {
                val dest = rb(pc0 + 1) or (rb(pc0 + 2) shl 8)
                val esF = if (motors.containsKey(dest)) 1 else 0; if (esF == 1) inF++
                pila.add(intArrayOf(dest, (pc0 + 3) and 0xFFFF, cpu.sp, 0, cpu.a, cpu.x, cpu.y, curFot, esF, 0))
                if (dest >= 0x8000) {
                    val bs = mem.copiaBancs(); bsMap[dest] = bs
                    val c = Crida(dest, cpu.a, cpu.x, cpu.y, curFot, enNmi, mem.estatBancs(), IntArray(24) { rb((dest + it) and 0xFFFF) }, bs)
                    crides.addLast(c); if (curFot <= 10 && crides0.size < 3000) crides0.add(c)
                }
            }
            cpu.step()
            // el motor es registra a la primera escriptura a l'APU de la crida de més amunt de la pila (no a l'RTS): hi ha motors que despatxen amb «RTS» com a salt i desquadren la pila
            if (tocat) {
                tocat = false
                if (pila.isNotEmpty()) { val f = pila[0]; if (f[9] == 0 && f[0] >= 0x8000) { f[9] = 1; registra(f) }
                    // fil principal amb una crida de més amunt que no torna mai (bucle principal o planificador de tasques: Mega Man IV i V, Mario Bros.): només es registrava una vegada;
                    // ara es registra la crida més amunt feta en aquest mateix fotograma (com fa l'NMI, que comença la pila a cada fotograma)
                    else if (!enNmi && f[9] == 1) { val g = pila.firstOrNull { it[7] == curFot && it[0] >= 0x8000 }; if (g != null && g[9] == 0) { g[9] = 1; registra(g) } } }
            }
            if (op == 0x60 && pila.isNotEmpty()) {
                val f = pila.removeAt(pila.size - 1)
                if (f.size > 8 && f[8] == 1 && inF > 0) inF--
                if (f[3] == 1 && f[0] >= 0x8000 && pila.isEmpty() && f[9] == 0) { f[9] = 1; registra(f) }
            }
        }
        var pilaMain = pila; var nmiInstr = 0; val nivellsNmi = ArrayList<Pair<ArrayList<IntArray>, Int>>()
        fun interrupcio() {
            // NMI com al maquinari: s'empilen PC i P del fil que s'interromp i el gestor corre sense restaurar res, perquè els jocs que
            // s'esperen en un bucle («BNE *» fins a l'NMI) puguin sortir-ne manipulant la pila
            val pc = cpu.pc
            wb(0x100 + cpu.sp, pc shr 8); cpu.sp = (cpu.sp - 1) and 255
            wb(0x100 + cpu.sp, pc and 255); cpu.sp = (cpu.sp - 1) and 255
            wb(0x100 + cpu.sp, cpu.p and 0xEF); cpu.sp = (cpu.sp - 1) and 255
            cpu.fi = true; cpu.pc = nmiV
            if (enNmi) nivellsNmi.add(pila to nmiInstr) else pilaMain = pila
            pila = ArrayList(); enNmi = true; nmiInstr = 0
        }
        // NMI imbricada (2026-11-22): l'NMI no es pot emmascarar; si el gestor encara corre al vblank següent (Marble Madness: tot el joc és a l'NMI i s'hi espera la següent), n'entra una altra
        fun surtNmi() { if (nivellsNmi.isNotEmpty()) { val n = nivellsNmi.removeAt(nivellsNmi.size - 1); pila = n.first; nmiInstr = n.second } else { enNmi = false; pila = pilaMain; inF = 0 } }          // inF = 0: si un motor desquadra la pila (RTS com a salt) el comptador no baixaria mai i les escriptures del fil principal no es veurien
        ControlAnalisi.fase = "joc"
        for (fot in 0 until fotogrames) {
            ControlAnalisi.comprova(); ControlAnalisi.fotograma = fot
            curFot = fot; escrFot = 0; mascara = 0
            // comandaments: Start cada 120 fotogrames i A a la meitat, perquè el joc avanci dels menús
            botons = if (fot % 120 in 20..25) 8 else if (fot % 120 in 60..65) 1 else 0
            // si l'NMI anterior és al seu nivell de dalt (cap JSR obert) es dona per acabada: Marble Madness surt de l'NMI amb «JMP» al bucle principal, sense RTI
            if (nmiOn && enNmi && pila.isEmpty()) { nivellsNmi.clear(); surtNmi() }
            if (nmiOn && (!enNmi || nivellsNmi.size < 3)) { if (primerNmi < 0) primerNmi = fot; interrupcio() }
            for (k in 0 until 12000) {
                val eraRti = enNmi && rb(cpu.pc) == 0x40
                pas()
                if (enNmi) { if (eraRti) surtNmi() else if (++nmiInstr > 60000) { nmiAtura++; if (nmiAtura <= 2) sb.append("NMI aturada després de 60000 instruccions a PC=\$%04X (%s), pila: %s\n".format(cpu.pc, mem.estatBancs(), pila.joinToString(" > ") { "\$%04X".format(it[0]) })); nivellsNmi.clear(); surtNmi() } }
            }
            var pic = 0.0
            val buf = DoubleArray(134)
            for (i in 0 until 134) { val v = Math.abs(apu.mostra(muts)); if (v > pic) pic = v }
            pics[fot] = pic; escr[fot] = escrFot; masc[fot] = mascara
            while (crides.isNotEmpty() && crides.first().fot < fot - 45) crides.removeFirst()
            if (pic > 0.05 && fot >= 25 && (1..15).all { pics[fot - it] < 0.03 }) {
                inicis.add(fot)
                if (inicis.size <= 4) {
                    sonsOnset.add(informaInici(sb, fot, crides))
                    for (e in escrRam.reversed()) if (e[0] in (fot - 120)..fot && e[2] != 0 && e[1] in lecturesF && e[1] !in 0x100..0x1FF && reqRam.size < 64) { reqRam[e[1]] = e[2]; reqFot[e[1]] = e[0] }
                }
            }
        }
        // diagnòstic: on és el joc ara (els 6 PC més freqüents en 3000 instruccions) i el desassemblat, per veure què espera si no arriba al so
        run {
            val cnt = HashMap<Int, Int>(); repeat(3000) { cnt.merge(cpu.pc, 1, Int::plus); try { pas() } catch (e: Throwable) { } }
            val top = cnt.entries.sortedByDescending { it.value }.take(6)
            sb.append("Estat final: ").append(mem.estatBancs()).append(", PC més freqüents: ").append(top.joinToString(", ") { "\$%04X(%d)".format(it.key, it.value) }).append('\n')
            for ((a, _) in top.take(2)) { sb.append("   a \$%04X:\n".format(a)); for (l in Dis6502.dis(rb, a, 6)) sb.append("      ").append(l).append('\n') }
        }
        // resum
        sb.append("NMI actiu des del fotograma ").append(if (primerNmi < 0) "MAI (el joc no activa l'NMI)" else primerNmi.toString())
        if (nmiAtura > 0) sb.append("; l'NMI no ha acabat en ").append(nmiAtura).append(" fotogrames (bucle?)")
        sb.append('\n')
        sb.append("Línia de temps (cada 60 fotogrames; pic de volum, escriptures, canals P1 P2 TR NO DM 15 V6):\n")
        for (b in 0 until fotogrames / 60) {
            var pk = 0.0; var w = 0; var mk = 0
            for (f in b * 60 until b * 60 + 60) { if (pics[f] > pk) pk = pics[f]; w += escr[f]; mk = mk or masc[f] }
            sb.append("  f%03d-%03d  pic=%.3f  escr=%-5d ".format(b * 60, b * 60 + 59, pk, w))
            val noms = listOf("P1", "P2", "TR", "NO", "DM", "15", "V6"); for (i in 0..6) if (mk and (1 shl i) != 0) sb.append(noms[i]).append(' ')
            sb.append('\n')
        }
        if (inicis.isEmpty()) sb.append("Cap inici de so detectat (silenci → so).\n") else sb.append("Inicis de so (silenci → so) als fotogrames: ").append(inicis.take(20).joinToString(", ")).append('\n')
        informaMotors(sb, "Rutines que escriuen a l'APU des de l'NMI (candidates a motor per fotograma)", motors, mem)
        informaMotors(sb, "Rutines que escriuen a l'APU des del fil principal", mainTouch, mem)
        sb.append("Instruccions que escriuen a l'APU (PC, vegades, de dins l'NMI): ").append(escrPc.entries.sortedByDescending { it.value[0] }.take(6).joinToString(", ") { "\$%04X(%d,%d)".format(it.key, it.value[0], it.value[1]) }).append('\n')
        // Prova en fred (força bruta): motor per fotograma + engega-cançó (+ inicialització) + codis que fan sonar l'APU
        try {
            val cands = ArrayList<Crida>()
            for (l in sonsOnset) cands.addAll(l)
            cands.addAll(crides0.distinctBy { it.dest }.take(150))                 // les crides dels primers fotogrames (música des de l'inici)
            if (reqRam.isEmpty()) for (e in escrRam0) if (e[2] != 0 && e[1] in lecturesF && e[1] !in 0x100..0x1FF && reqRam.size < 64) { reqRam[e[1]] = e[2]; reqFot[e[1]] = e[0] }
            auto(sb, r, crc, motors, mainTouch, cands, reqRam, reqFot)
        } catch (e: Throwable) { sb.append("AUTO: error ").append(e.javaClass.simpleName).append(' ').append(e.message ?: "").append('\n') }
        return sb.toString()
    }

    private fun informaMotors(sb: StringBuilder, tit: String, m: HashMap<Int, Motor>, mem: NesRomMem) {
        sb.append(tit).append(":\n")
        if (m.isEmpty()) { sb.append("  cap\n"); return }
        for ((adr, x) in m.entries.sortedByDescending { it.value.cnt }.take(6)) {
            sb.append("  \$%04X  (%d vegades, %s)\n".format(adr, x.cnt, x.bancs))
            if (x.crides.isNotEmpty()) sb.append("      primeres crides: ").append(x.crides.joinToString("; ")).append('\n')
            for (l in Dis6502.dis({ i -> val o = i - adr; if (o in x.octets.indices) x.octets[o] else 0 }, adr, 14)) sb.append("      ").append(l).append('\n')
        }
    }

    private fun informaInici(sb: StringBuilder, fot: Int, crides: ArrayDeque<Crida>): List<Crida> {
        sb.append("--- Inici de so al fotograma ").append(fot).append(": rutines cridades en els 4 fotogrames anteriors que no es cridaven 10-40 fotogrames abans ---\n")
        val antics = crides.filter { it.fot in (fot - 40)..(fot - 10) }.map { it.dest }.toSet()
        val totes = crides.filter { it.fot in (fot - 4)..fot && it.dest !in antics }.distinctBy { it.dest to it.a }.take(40); val noves = totes.take(8)
        if (noves.isEmpty()) sb.append("  cap rutina nova (el so surt de rutines que ja es cridaven)\n")
        for (c in noves) {
            sb.append("  \$%04X  A=\$%02X X=\$%02X Y=\$%02X  fot=%d%s  %s\n".format(c.dest, c.a, c.x, c.y, c.fot, if (c.enNmi) " (NMI)" else "", c.bancs))
            for (l in Dis6502.dis({ i -> val o = i - c.dest; if (o in c.octets.indices) c.octets[o] else 0 }, c.dest, 8)) sb.append("      ").append(l).append('\n')
        }
        return totes
    }

    // ----------------------------------------------------------------------------------------------------------------------------------------------------------
    // Prova en fred: per a cada combinació (inicialització opcional, engega-cançó, A) es crea una màquina nova (RAM a zero, bancs com en la crida original), es crida l'engega-cançó
    // amb A i el motor per fotograma durant uns fotogrames, i es mesura si l'APU sona. És exactament el que fa un driver, així que el que passa aquí passa al reproductor.
    // ----------------------------------------------------------------------------------------------------------------------------------------------------------
    private class Prova(val pic: Double, val picFinal: Double, val hash: Int, val fotsSo: Int, val regs: Set<Int> = emptySet())

    /** Una prova en fred: `p` = engega-cançó cridat amb A = `a`; si `p` és null, la cançó es demana escrivint `a` a la RAM `ramAd` (motors que llegeixen una variable de petició). */
    private fun prova(r: NesRom, ini: Cand?, p: Cand?, ramAd: Int, a: Int, f: Cand, fots: Int, extra: Map<Int, Int> = emptyMap()): Prova {
        var ap: NesApu? = null
        val mem = NesRomMem(r) { ap }
        val apu = NesApu(8000) { mem.rb(it) }; ap = apu
        apu.escriu(0x4015, 0x0F)
        var hash = 0; val regs = HashSet<Int>()
        // sense PPU simulada ni comandaments: la màquina és la mateixa que farà servir el reproductor (DriverNesAuto), així el que es prova aquí és el que sonarà
        val cpu = Cpu6502({ ad -> mem.rb(ad) },
            { ad, v -> if (ad in 0x4000..0x4013 || ad == 0x4015 || ad in 0x9000..0xB003) { hash = hash * 31 + ad * 257 + v; regs.add(ad * 256 + v) }; mem.wb(ad, v) })
        val muts = BooleanArray(5)
        cpu.sp = 0xFD
        if (ini != null) { mem.posaBancs(ini.bs); cpu.a = ini.a; cpu.x = ini.x; cpu.y = ini.y; cpu.sp = 0xFD; cpu.crida(ini.dest, 60000) }
        if (p != null) { mem.posaBancs(p.bs); cpu.a = a; cpu.x = p.x; cpu.y = p.y; cpu.sp = 0xFD; cpu.crida(p.dest, 60000) } else { mem.wb(ramAd, a); for ((ad, v) in extra) mem.wb(ad, v) }
        var pic = 0.0; var picFinal = 0.0; var so = 0
        for (fot in 0 until fots) {
            ControlAnalisi.comprova()
            mem.posaBancs(f.bs); cpu.sp = 0xFD; cpu.crida(f.dest, 60000)
            var pf = 0.0
            for (i in 0 until 134) { val v = Math.abs(apu.mostra(muts)); if (v > pf) pf = v }
            if (pf > pic) pic = pf
            if (pf > 0.05) so++
            if (fot >= fots - 10 && pf > picFinal) picFinal = pf
        }
        return Prova(pic, picFinal, hash, so, regs)
    }

    /** Treu els codis que només són un tros d'un altre: els que fan sonar un subconjunt estricte de les escriptures d'un altre codi (un registre d'una cançó de diversos canals engega només els seus últims canals). */
    private fun fragments(codis: List<Pair<Int, Prova>>): List<Pair<Int, Prova>> =
        codis.filter { c -> codis.none { o -> o.first != c.first && o.second.regs.size * 10 > c.second.regs.size * 12 && c.second.regs.count { it in o.second.regs } * 10 >= c.second.regs.size * 9 } }

    private fun sona(pr: Prova) = pr.pic > 0.05 && pr.picFinal > 0.02

    private class Res(val ini: Cand?, val p: Cand?, val ramAd: Int, val codis: List<Pair<Int, Prova>>, val extra: Map<Int, Int> = emptyMap())

    private fun auto(sb: StringBuilder, r: NesRom, crc: Long, motors: HashMap<Int, Motor>, mainTouch: HashMap<Int, Motor>, cands: List<Crida>, reqRam: Map<Int, Int>, reqFot: Map<Int, Int>) {
        sb.append("--- PROVA EN FRED (força bruta) ---\n"); ControlAnalisi.fase = "fred"
        // motor per fotograma: la rutina que l'NMI crida i escriu a l'APU; si l'NMI només posa un flag i la música la fa el fil principal (Astro Fang, Astro Robo Sasa...),
        // els candidats són les rutines del fil principal que més vegades han escrit a l'APU (a la prova en fred es crida cada fotograma, igual que fa el bucle principal)
        val nmi = motors.entries.filter { it.value.bs != null }.maxByOrNull { it.value.cnt }
        val fms = if (nmi != null) listOf(nmi to false) else mainTouch.entries.filter { it.value.bs != null && it.value.cnt >= 4 }.sortedByDescending { it.value.cnt }.take(3).map { it to true }
        if (fms.isEmpty()) { sb.append("AUTO: cap motor per fotograma amb bancs (no es pot provar)\n"); return }
        for ((fm, delMain) in fms) if (autoMotor(sb, r, crc, fm, delMain, mainTouch, cands, reqRam, reqFot)) return
    }

    /** Prova en fred amb `fm` com a motor per fotograma; retorna true si escriu la línia AUTO_NES. */
    private fun autoMotor(sb: StringBuilder, r: NesRom, crc: Long, fm: Map.Entry<Int, Motor>, delMain: Boolean, mainTouch: HashMap<Int, Motor>, cands: List<Crida>, reqRam: Map<Int, Int>, reqFot: Map<Int, Int>): Boolean {
        val f = Cand(fm.key, fm.value.bs!!, fm.value.a, fm.value.x, fm.value.y)
        val inis = mainTouch.entries.filter { it.value.bs != null && it.value.cnt <= 3 && it.key != f.dest }.sortedBy { it.value.cnt }.take(3).map { Cand(it.key, it.value.bs!!, it.value.a, it.value.x, it.value.y) }
        val ps = cands.filter { it.dest != f.dest }.groupBy { it.dest }.values.take(160)
        sb.append("Motor per fotograma%s: \$%04X; inicialitzacions candidates: %d; engega-cançó candidates: %d; variables de RAM candidates: %s\n".format(if (delMain) " (del fil principal)" else "", f.dest, inis.size, ps.size,
            if (reqRam.isEmpty()) "cap" else reqRam.entries.joinToString(" ") { "\$%04X=%02X".format(it.key, it.value) }))
        val t0 = System.currentTimeMillis()
        fun escaneja(ini: Cand?, p: Cand?, ramAd: Int, limit: Long, extra: Map<Int, Int> = emptyMap()): Res {
            val vistos = HashSet<Int>(); val codis = ArrayList<Pair<Int, Prova>>()
            for (a in 0..255) {
                if (System.currentTimeMillis() - t0 > limit) break
                val pr = prova(r, ini, p, ramAd, a, f, 40, extra)
                if (sona(pr) && vistos.add(pr.hash)) codis.add(a to pr)
            }
            return Res(ini, p, ramAd, fragments(codis), extra)
        }
        // Mode 1: engega-cançó cridat amb A (primer sense inicialització; només si no surt res s'hi afegeixen les candidates)
        class Comb(val ini: Cand?, val p: Cand)
        val troba = ArrayList<Comb>()
        for (ini in listOf<Cand?>(null) + inis) {
            if (ini != null && troba.isNotEmpty()) break
            for (grup in ps) {
                val c0 = grup[0]
                for (a in (grup.map { it.a } + listOf(0, 1, 2, 3, 5, 8, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38, 0x40, 0x48, 0x50, 0x58, 0x60, 0x70, 0x80, 0x90, 0xA0)).distinct()) {
                    if (System.currentTimeMillis() - t0 > 90000) break
                    val cp = Cand(c0.dest, c0.bs, a, c0.x, c0.y)
                    if (sona(prova(r, ini, cp, -1, a, f, 40))) { troba.add(Comb(ini, cp)); break }
                }
            }
        }
        var millor: Res? = null
        // es prova fins a 6 combinacions i es queda la que fa sonar més codis «de música» (sonen la major part dels 40 fotogrames): un engega-so d'efectes també fa sonar, però poca estona
        fun puntuacio(r: Res) = r.codis.count { it.second.fotsSo >= 30 }
        for (c in troba.distinctBy { (it.ini?.dest ?: -1) to it.p.dest }.take(6)) {
            val e = escaneja(c.ini, c.p, -1, 150000)
            if (millor == null || puntuacio(e) > puntuacio(millor)) millor = e
        }
        // Mode 2: la cançó es demana escrivint un valor a una variable de RAM que llegeix el motor (com Super Mario Bros.); només si el mode 1 no ha donat res fiable
        var viaRam = false
        if ((millor?.codis?.size ?: 0) < 2) {
            for (ini in listOf<Cand?>(null) + inis) for ((ad, v) in reqRam) {
                if (System.currentTimeMillis() - t0 > 130000) break
                // variables que el joc escriu al mateix temps que la de la cançó (Astro Fang: $FF = cançó i $FE = màscara de canals): s'escriuen també
                val fa = reqFot[ad]
                val extra = if (fa == null) emptyMap() else reqRam.filter { it.key != ad && Math.abs((reqFot[it.key] ?: -99) - fa) <= 1 }
                for (ex in listOf(emptyMap<Int, Int>(), extra).distinct()) {
                    if (listOf(v, 1, 0x81, 0x82, v or 0x80).distinct().none { sona(prova(r, ini, null, ad, it, f, 40, ex)) }) continue          // alguns motors (Astro Fang) demanen la cançó amb el bit 7 alt
                    val e = escaneja(ini, null, ad, 160000, ex)
                    if (e.codis.size >= 2 && (millor == null || e.codis.size > millor.codis.size)) { millor = e; viaRam = true }
                }
            }
        }
        val m = millor
        if (m == null || m.codis.isEmpty()) { sb.append("AUTO: cap combinació fa sonar l'APU de manera sostinguda (%d s)\n".format((System.currentTimeMillis() - t0) / 1000)); return false }
        // durada de cada codi (11 s): bucle si encara sona al final; si no, els segons que ha sonat
        val sbc = StringBuilder()
        for ((a, _) in m.codis) {
            if (System.currentTimeMillis() - t0 > 175000) { sbc.append("%02X:?:0 ".format(a)); continue }
            val l = prova(r, m.ini, m.p, m.ramAd, a, f, 660, m.extra)
            val canals = l.regs.filter { val ad = (it shr 8) - 0x4000; val v = it and 255; ad in 0..0x13 && v != 0 && (ad / 4 == 4 || ad % 4 >= 2) }.map { ((it shr 8) - 0x4000) / 4 }.toSet().size          // canals de l'APU amb notes (període != 0; la inicialització només escriu volums): 2 quadrades, triangle, soroll, DMC
            sbc.append("%02X:%s:%d ".format(a, if (l.picFinal > 0.02) "L" else "%ds".format(maxOf(1, l.fotsSo / 60)), canals))
        }
        fun bs(b: IntArray) = b.joinToString(",") { "%X".format(it) }
        sb.append("AUTO_NES crc=%08X mapper=%d f=%04X:%s ini=%s p=%s ram=%s codis=%s\n".format(crc, r.mapper, f.dest, bs(f.bs),
            m.ini?.let { "%04X:%s:%02X:%02X:%02X".format(it.dest, bs(it.bs), it.a, it.x, it.y) } ?: "-",
            m.p?.let { "%04X:%s:%02X:%02X".format(it.dest, bs(it.bs), it.x, it.y) } ?: "-", if (m.p == null) "%04X".format(m.ramAd) + m.extra.entries.joinToString("") { "+%04X:%02X".format(it.key, it.value) } else "-", sbc.toString().trim()))
        sb.append("AUTO: %d codis amb so (L = bucle, Ns = dura N segons), via %s, %d s de prova%s\n".format(m.codis.size, if (m.p == null) "RAM" else "rutina", (System.currentTimeMillis() - t0) / 1000,
            if (m.codis.size < 2) " -- DUBTÓS: només 1 codi fa sonar, probablement l'engega-cançó no és aquesta" else ""))
        return true
    }
}
