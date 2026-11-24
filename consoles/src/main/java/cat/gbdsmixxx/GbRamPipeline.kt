package cat.gbdsmixxx

/**
 * Pipeline per RAM de l'analitzador de Game Boy (l'autor, 2026-11-15: «intercepta tota la RAM»). No endevina com el joc demana la cançó: durant l'execució es guarda cada canvi de la WRAM i la HRAM, i
 * 1. es busca el primer fotograma `R` a partir del qual el motor sol (sense el joc) ja fa sonar la cançó que el joc comença a l'inici de so;
 * 2. de l'estat de RAM d'aquell fotograma es treu tot el que no fa falta (pàgines de 256 bytes i bytes) mentre les escriptures a l'APU siguin idèntiques: queda l'estat mínim `S`;
 * 3. els punters de `S` (valors que són adreces de la ROM) es busquen a la ROM com una capçalera, l'adreça de la capçalera es busca com a entrada d'una taula de punters, i s'enumeren totes
 *    les entrades; cada cançó és `S` amb els punters de la seva capçalera, i només es conserva si el motor sol la fa sonar.
 * Surt una línia `AUTO_POKE` que és el driver sencer (vegeu `DriverGbPoke`).
 */
object GbRamPipeline {
    /** `x` = RAM del cartutx ($A000-$BFFF): alguns jocs (DKL) hi copien la partitura de la ROM abans de tocar-la. */
    class Estat(val w: ByteArray = ByteArray(0x8000), val h: ByteArray = ByteArray(0x80), val x: ByteArray = ByteArray(0x2000)) { fun copia() = Estat(w.copyOf(), h.copyOf(), x.copyOf()) }
    /** Aplica un canvi de RAM (adreça de les deltes): < $8000 WRAM, $8000-$807F HRAM, $8080+ RAM del cartutx. */
    private fun aplica(e: Estat, ad: Int, v: Byte) { if (ad < 0x8000) e.w[ad] = v else if (ad < 0x8080) e.h[ad - 0x8000] = v else e.x[ad - 0x8080] = v }
    /** Posa un byte d'estat: índex WRAM; >= $10000 HRAM; >= $20000 RAM del cartutx. */
    private fun posa(e: Estat, a: Int, v: Byte) { if (a >= 0x20000) e.x[a - 0x20000] = v else if (a >= 0x10000) e.h[a - 0x10000] = v else e.w[a] = v }
    /** `trenc`: el pitjor resultat de `GbCpu.cridaV` dels fotogrames (0 = tots han tornat bé). */
    private class Res(val notes: Int, val hash: Int, val esc: IntArray = IntArray(0), val trenc: Int = 0)

    private fun reconstrueix(deltes: List<IntArray>, fins: Int): Estat {
        val e = Estat()
        for (f in 0..minOf(fins, deltes.size - 1)) for (c in deltes[f]) { val ad = c ushr 8; val v = (c and 255).toByte(); aplica(e, ad, v) }
        return e
    }

    private fun prova(rom: ByteArray, mbc5: Boolean, e: Estat, f: AnalitzadorGb.MotorGb, fots: Int = 90, lim: Int = 400000, p: AnalitzadorGb.MotorGb? = null, pa: Int = 0): Res {
        val mem = GbMem(rom, mbc5); val cpu = GbCpu(mem)
        System.arraycopy(e.w, 0, mem.wram, 0, 0x8000); System.arraycopy(e.h, 0, mem.hram, 0, 0x80); System.arraycopy(e.x, 0, mem.xram, 0, 0x2000); mem.ie = 7
        var notes = 0; var hash = 0; val vol = IntArray(4); val esc = ArrayList<Int>(); var trenc = 0
        mem.alRegistre = { ad, x -> hash = hash * 31 + ad * 257 + x; if (esc.size < capEsc) esc.add(((ad - 0xFF00) shl 8) or x)
            when (ad) { 0xFF12 -> vol[0] = x shr 4; 0xFF17 -> vol[1] = x shr 4; 0xFF21 -> vol[3] = x shr 4; 0xFF1C -> vol[2] = (x shr 5) and 3
                0xFF14 -> if (x and 0x80 != 0 && vol[0] > 0) notes++; 0xFF19 -> if (x and 0x80 != 0 && vol[1] > 0) notes++
                0xFF1E -> if (x and 0x80 != 0 && vol[2] > 0) notes++; 0xFF23 -> if (x and 0x80 != 0 && vol[3] > 0) notes++ } }
        if (p != null) { mem.posaBancs(p.bs); val g = p.regs; cpu.a = pa; cpu.b = g[1]; cpu.c = g[2]; cpu.d = g[3]; cpu.e = g[4]; cpu.h = g[5]; cpu.l = g[6]; cpu.sp = f.sp; val t = cpu.cridaV(p.dest, 200000, 200000); if (t == 2 || t == 3) trenc = t }
        for (i in 0 until fots) {
            ControlAnalisi.comprova()
            mem.posaBancs(f.bs); val g = f.regs; cpu.a = g[0]; cpu.b = g[1]; cpu.c = g[2]; cpu.d = g[3]; cpu.e = g[4]; cpu.h = g[5]; cpu.l = g[6]
            cpu.sp = f.sp; val t = cpu.cridaV(f.dest, lim); if (t > trenc) trenc = t
            if (t >= 2 && dbgTrenc) System.err.println("TRENC %d fot %d motor %04X pc %04X des de %04X banc %d".format(t, i, f.dest, cpu.pcTrenc, cpu.srcTrenc, mem.banc))
        }
        return Res(notes, hash, esc.toIntArray(), trenc)
    }
    private fun idxE0(ad: Int, excl: Set<Int>) = (if (ad >= 0xFF80) 0x10000 + (ad - 0xFF80) else if (ad in 0xA000..0xBFFF) 0x20000 + (ad - 0xA000) else if (ad < 0xD000) ad - 0xC000 else 0x1000 + (ad - 0xD000)) in excl
    /** Les escriptures a l'APU de la referència `b` són (com a conjunt, sense exigir l'ordre) en un 85 % o més a l'execució `a`: la petició pot ser un fotograma abans o després de la rutina de cada fotograma. */
    private fun semblant(a: Res, b: Res): Boolean { val bossa = HashMap<Int, Int>(); for (x in b.esc) bossa.merge(x, 1, Int::plus); var c = 0; for (x in a.esc) { val n = bossa[x] ?: 0; if (n > 0) { bossa[x] = n - 1; c++ } }; return b.esc.size >= 20 && c * 100 >= 85 * b.esc.size }
    /** Sona i el codi no s'ha perdut (2026-11-22): salts a memòria a zero o RST $38 en bucle (Battle Crusher, V-Rally) no són cançons; amb `-DtrencEstricte`, tampoc el límit ni els bucles de retard. */
    private val dbgTrenc = System.getProperty("dbgTrenc") != null
    private val estricte = System.getProperty("trencEstricte") != null
    private fun sona(r: Res) = r.notes >= 3 && r.trenc != 2 && r.trenc != 3 && (!estricte || r.trenc == 0)

    /** Adreça de CPU d'un desplaçament de la ROM. */
    private fun adrCpu(o: Int) = if (o < 0x4000) o else 0x4000 + (o % 0x4000)
    private fun off(rom: ByteArray, adr: Int, banc: Int) = if (adr < 0x4000) adr else banc * 0x4000 + (adr - 0x4000)

    private var inicisG: List<Int> = emptyList()
    /** «Cache drop» en acabar una anàlisi de l'app (ProvaCompat): no queda res retingut d'aquesta ROM. */
    fun buida() { inicisG = emptyList() }
    private val capEsc = if (System.getProperty("dbgSub") != null) 400000 else 600
    fun executa(rom: ByteArray, mbc5: Boolean, crc: Long, deltes: List<IntArray>, inicis: List<Int>, cands: List<AnalitzadorGb.MotorGb>, apuLog: List<IntArray>, escrRoot: List<IntArray>, t0: Long, limitMs: Long): String {
        val sb = StringBuilder("--- PIPELINE PER RAM ---\n")
        if (cands.isEmpty()) return sb.append("RAM: cap rutina que escrigui a l'APU (no es pot provar)\n").toString()
        inicisG = inicis
        if (inicis.isEmpty()) return sb.append("RAM: cap inici de so\n").toString()
        fun temps() = System.currentTimeMillis() - t0 > limitMs
        // 1. per a cada rutina candidata, el primer fotograma R a partir del qual (sola, sense el joc) fa sonar; i es compara el que escriu a l'APU amb el que el joc va escriure de debò a partir de R
        val on = inicis[0]; val ini = maxOf(1, on - 150)
        var f: AnalitzadorGb.MotorGb? = null; var R = -1; var estat: Estat? = null; var millorPunts = -1
        for (c in cands) {
            if (temps()) break
            val tC = System.currentTimeMillis(); fun tempsC() = temps() || System.currentTimeMillis() - tC > 6000
            val e0 = reconstrueix(deltes, ini - 1)
            var Rc = -1; var est: Estat? = null; var r0: Res? = null
            for (F in ini..minOf(on + 1, deltes.size - 1)) {
                for (x in deltes[F]) { val ad = x ushr 8; val v = (x and 255).toByte(); aplica(e0, ad, v) }
                if (sona(prova(rom, mbc5, e0, c, 30, 60000))) { val r = prova(rom, mbc5, e0, c); if (sona(r)) { Rc = F; est = e0.copia(); r0 = r; break } }
                if (tempsC()) break
            }
            if (Rc < 0 || est == null || r0 == null) continue
            val real = ArrayList<Int>(); for (l in apuLog) if (l[0] in (Rc + 1)..(Rc + 90)) real.add((l[1] shl 8) or l[2])
            // coincidència = escriptures (registre, valor) en comú entre el que fa la rutina sola i el que va fer el joc, sense exigir el mateix ordre ni el mateix fotograma
            val bossa = HashMap<Int, Int>(); for (x in real) bossa.merge(x, 1, Int::plus)
            var lcp = 0; for (x in r0.esc) { val n = bossa[x] ?: 0; if (n > 0) { bossa[x] = n - 1; lcp++ } }
            sb.append("Candidata \$%04X: sona des del fotograma %d; coincideix amb les escriptures reals en %d de %d (de la rutina) / %d (reals)\n".format(c.dest, Rc, lcp, r0.esc.size, minOf(real.size, 600)))
            if (lcp > millorPunts) { millorPunts = lcp; f = c; R = Rc; estat = est }
        }
        if (f == null || estat == null) return sb.append("RAM: cap rutina sola fa sonar res a partir de l'estat de cap fotograma abans de l'inici de so ($on); el so no depèn només de la RAM i d'una rutina\n").toString()
        sb.append("Rutina de cada fotograma triada: \$%04X (coincideix en %d escriptures amb les reals)\n".format(f.dest, millorPunts))
        val base = prova(rom, mbc5, estat, f)
        sb.append("Fotograma de la petició: $R (inici de so $on); motor sol: ${base.notes} notes\n")
        // 2. minimització
        val (e, S) = minimitza(rom, mbc5, estat, f, base, ::temps)
        sb.append("Estat mínim: ${S.size} bytes diferents de zero\n")
        // 3. cançons: punters de S -> capçalera a la ROM -> taula
        // paraules candidates: valors de 16 bits de S que són adreces de la zona commutable de la ROM ($4000-$7FFF); si n'hi ha poques, també les de $0150-$3FFF
        val cands = LinkedHashMap<Int, ArrayList<Int>>()        // paraula -> adreces de S on hi és (lo)
        for (rang in listOf(0x4000..0x7FFF, 0x0150..0x7FFF)) {
            cands.clear()
            for ((a, v) in S) { val hi = S[a + 1] ?: continue; val w = v or (hi shl 8); if (w in rang) cands.getOrPut(w) { ArrayList() }.add(a) }
            if (cands.size >= 3) break
        }
        val ws = cands.keys.toList()
        // bytes que formen un punter de ROM (adreça de $4000-$7FFF): no poden ser un número de cançó
        val excl = HashSet<Int>(); for ((w, l) in cands) if (w in 0x4000..0x7FFF) for (a in l) { excl.add(a); excl.add(a + 1) }
        var millor = -1; var millorN = 0; var millorCamps: List<Pair<Int, Int>> = emptyList()      // (desplaçament en bytes dins la capçalera, paraula)
        val FIN = 24
        if (ws.size >= 3 && !temps()) {
            val setW = java.util.TreeSet<Int>(ws); val n = rom.size - FIN - 2
            var o = 0
            while (o < n) {
                var cnt = 0; val camps = ArrayList<Pair<Int, Int>>(); val vistes = HashSet<Int>()
                for (q in 0 until FIN) { val x = o + q; val x0 = (rom[x].toInt() and 255) or ((rom[x + 1].toInt() and 255) shl 8); val w = setW.ceiling(x0); if (w != null && w - x0 <= 48 && x0 >= 0x0150 && vistes.add(w)) { cnt++; camps.add(q to w) } }
                // preferència: més punters; a igualtat, els que estan més junts
                if (cnt > millorN || (cnt == millorN && cnt >= 3 && camps.last().first - camps.first().first < millorCamps.last().first - millorCamps.first().first)) { millorN = cnt; millor = o; millorCamps = camps }
                o++
            }
        }
        fun provaArrencada(): Arrenca? {
            var arr: Arrenca? = arrencada(rom, mbc5, deltes, R, f, base, excl, escrRoot, { temps() }, sb, 90)?.takeIf { it.songs.size >= 3 }
            // la petició de cançó pot ser molt abans del primer so (la cançó comença en silenci): altres fotogrames de petició, els més propers a l'inici de so primer, amb 300 fotogrames de comparació
            if (arr == null) {
                // frames de petició = els de les crides a rutines que el joc crida amb valors d'A diferents (un número de cançó), siguin quins siguin els bytes que escriuen
                val avs = HashMap<Int, HashSet<Int>>(); for (r in escrRoot) if (r[3] != f.dest) avs.getOrPut(r[3]) { HashSet() }.add(r[6])
                val wx = HashSet<Int>(); val frD = HashMap<Int, HashSet<Int>>(); for (r in escrRoot) { if (r[3] != f.dest) frD.getOrPut(r[3]) { HashSet() }.add(r[0]); if (idxE0(r[1], excl)) wx.add(r[3]) }
                // les que escriuen l'estat de la cançó en algun moment i es criden en pocs fotogrames (no a cada fotograma)
                val varia = avs.filter { it.value.size >= 2 && it.key in wx && (frD[it.key]?.size ?: 99) <= 12 }.keys
                                val rqs = java.util.TreeSet<Int>(); for (r in escrRoot) if (r[3] in varia && r[0] in (on - 300)..(on + 1) && r[0] != R) rqs.add(r[0])
                for (rq in rqs.descendingSet().take(4)) { if (temps()) break
                    val estRq = reconstrueix(deltes, rq); val baseRq = prova(rom, mbc5, estRq, f, 300)
                    if (!sona(baseRq)) { sb.append("Petició al fotograma $rq: el motor sol no sona\n"); continue }
                    sb.append("Petició al fotograma $rq\n")
                    arr = arrencada(rom, mbc5, deltes, rq, f, baseRq, excl, escrRoot, { temps() }, sb, 300, varia)?.takeIf { it.songs.size >= 3 }
                    if (arr != null) break } }
            return arr
        }
        if (millorN < 3) { sb.append("RAM: no he trobat cap capçalera de cançó a la ROM (només ${millorN} punters junts); provo si la cançó es demana amb un número\n")
            if (!temps()) { val ar = provaArrencada(); if (ar != null) return sb.append(linia(crc, mbc5, f, ar.estatMin, ar.songs.map { listOf(-1 to it) }, ar.p)).toString() }
            val sw = escombra(rom, mbc5, S, e, f, base, excl, null, { temps() })
            sb.append("Escombrat de valors: ${sw.size} sons diferents\n")
            return sb.append(linia(crc, mbc5, f, S, sw, null)).toString() }
        // la capçalera comença al primer punter trobat
        val q0 = millorCamps.first().first; millor += q0; millorCamps = millorCamps.map { (it.first - q0) to it.second }
        val bancHdr = millor / 0x4000; val adrHdr = adrCpu(millor)
        sb.append("Capçalera de cançó trobada a la ROM: banc $bancHdr adreça \$%04X amb %d punters (desplaçaments %s)\n".format(adrHdr, millorN, millorCamps.joinToString(",") { it.first.toString() }))
        fun paraulaA(i: Int): Int = (rom[i].toInt() and 255) or ((rom[i + 1].toInt() and 255) shl 8)
        // taula: paraules iguals a l'adreça de la capçalera; una entrada val si els camps de punter de la seva capçalera són tots adreces de ROM
        var millorTaula: List<Int> = emptyList(); var preambul = 0
        fun entradaOk(i: Int, d: Int): Int? {
            if (i < 0 || i + 1 >= rom.size) return null; val w = paraulaA(i); if (w !in 0x4000..0x7FFF) return null
            val h = off(rom, w, bancHdr) + d
            for ((q, _) in millorCamps) { val x = h + q; if (x + 1 >= rom.size) return null; if (paraulaA(x) !in 0x0150..0x7FFF) return null }
            return w
        }
        for (d in 0..6) {
            val ad = adrHdr - d; val pat0 = (ad and 255).toByte(); val pat1 = (ad shr 8).toByte()
            var p = 0
            while (p < rom.size - 1) {
                if (rom[p] == pat0 && rom[p + 1] == pat1) {
                    var i = p; while (true) { entradaOk(i - 2, d) ?: break; i -= 2 }
                    val entrades = ArrayList<Int>(); var k = i; while (true) { val w = entradaOk(k, d) ?: break; entrades.add(w); k += 2 }
                    if (entrades.size > millorTaula.size) { millorTaula = entrades; preambul = d }
                }
                p++
            }
        }
        val cancons = ArrayList<List<Pair<Int, Int>>>(); val vistos = HashSet<Int>()
        fun pokesDe(h: Int): List<Pair<Int, Int>> { val ch = ArrayList<Pair<Int, Int>>(); for ((q, w0) in millorCamps) { val nw = paraulaA(h + q); for (a in cands[w0]!!) { ch.add(a to (nw and 255)); ch.add((a + 1) to (nw shr 8)) } }; return ch }
        fun prova1(ch: List<Pair<Int, Int>>) { val ek = e.copia(); for ((a, v) in ch) posa(ek, a, v.toByte()); val r = prova(rom, mbc5, ek, f); if (sona(r) && vistos.add(r.hash)) cancons.add(ch) }
        // array de capçaleres seguides (l'ID de cançó és un índex multiplicat per la mida): es busca la mida que dona més capçaleres vàlides
        fun valida(h: Int): Boolean { if (h < 0) return false; for ((q, _) in millorCamps) { val x = h + q; if (x + 1 >= rom.size) return false; if (paraulaA(x) !in 0x4000..0x7FFF) return false }; return true }
        var arrayLlista: List<Int> = emptyList()
        for (st in (millorCamps.last().first + 2)..40) {
            var k0 = 0; while (valida(millor - (k0 + 1) * st)) k0++
            var k1 = 0; while (valida(millor + (k1 + 1) * st)) k1++
            if (k0 + k1 + 1 > arrayLlista.size) arrayLlista = (-k0..k1).map { millor + it * st }
        }
        if (arrayLlista.size < 4 || arrayLlista.size <= millorTaula.size) {
            if (millorTaula.size >= 2) {
                sb.append("Taula de capçaleres: ${millorTaula.size} entrades\n")
                val dups = HashSet<Int>()
                for (w in millorTaula) { if (temps()) break; if (!dups.add(w)) continue; prova1(pokesDe(off(rom, w, bancHdr) + preambul)) }
                sb.append("Cançons que sonen amb el motor sol: ${cancons.size}\n")
            } else sb.append("RAM: no he trobat cap taula amb aquesta capçalera\n")
        } else {
            sb.append("Array de capçaleres seguides: ${arrayLlista.size} (mida ${arrayLlista[1] - arrayLlista[0]} bytes)\n")
            for (h in arrayLlista) { if (temps()) break; prova1(pokesDe(h)) }
            sb.append("Cançons que sonen amb el motor sol: ${cancons.size}\n")
        }
        var arr: Arrenca? = null
        if (cancons.size < 3 && !temps()) arr = provaArrencada()
        if (arr == null && cancons.size < 3 && !temps()) {
            val obH = HashSet<Int>()
            val ob = observades(rom, mbc5, deltes, inicis, f, S, e, apuLog, { temps() }, obH)
            sb.append("Cançons observades al joc: ${ob.size}\n")
            val perm = ob.flatMap { c -> c.map { it.first } }.toSet().ifEmpty { null }
            val swH = HashSet<Int>()
            val sw = escombra(rom, mbc5, S, e, f, base, excl, perm, { temps() }, swH)
            // un número de cançó de debò ha de reproduir els sons que el joc ha tocat de debò; si no, és un altre paràmetre
            val coherent = obH.isEmpty() || swH.containsAll(obH)
            sb.append("Escombrat de valors: ${sw.size} sons diferents" + (if (sw.size >= 3 && !coherent) " (descartat: no inclou els sons que el joc ha tocat)" else "") + "\n")
            if (sw.size >= 3 && coherent) { cancons.clear(); cancons.addAll(sw) } else if (ob.size >= 2) { cancons.clear(); cancons.addAll(ob) }
        }
        val arrF = arr
        if (arrF != null) return sb.append(linia(crc, mbc5, f, arrF.estatMin, arrF.songs.map { listOf(-1 to it) }, arrF.p)).toString()
        return sb.append(linia(crc, mbc5, f, S, cancons, null)).toString()
    }


    /** Treu de l'estat tot el que no fa falta (pàgines de WRAM, bytes i HRAM) mentre les escriptures a l'APU siguin idèntiques; retorna l'estat i els bytes que queden (WRAM per índex; HRAM + $10000). */
    private fun minimitza(rom: ByteArray, mbc5: Boolean, estat: Estat, f: AnalitzadorGb.MotorGb, base: Res, temps: () -> Boolean, calc: ((Estat) -> Int)? = null): Pair<Estat, LinkedHashMap<Int, Int>> {
        val e = estat.copia()
        fun hashDe(x: Estat) = if (calc != null) calc(x) else prova(rom, mbc5, x, f).hash
        run { val h2 = e.h.copyOf(); java.util.Arrays.fill(e.h, 0); if (hashDe(e) != base.hash) System.arraycopy(h2, 0, e.h, 0, 0x80) }
        val pags = ArrayList<Int>()
        for (pg in 0 until 0x80) {
            if (temps()) break
            val ant = e.w.copyOfRange(pg * 0x100, pg * 0x100 + 0x100); if (ant.all { it.toInt() == 0 }) continue
            java.util.Arrays.fill(e.w, pg * 0x100, pg * 0x100 + 0x100, 0)
            if (hashDe(e) != base.hash) { System.arraycopy(ant, 0, e.w, pg * 0x100, 0x100); pags.add(pg) }
        }
        for (pg in pags) for (i in 0 until 0x100) {
            if (temps()) break
            val a = pg * 0x100 + i; val ant = e.w[a]; if (ant.toInt() == 0) continue
            e.w[a] = 0; if (hashDe(e) != base.hash) e.w[a] = ant
        }
        for (i in 0 until 0x80) { val ant = e.h[i]; if (ant.toInt() == 0) continue; if (temps()) break; e.h[i] = 0; if (hashDe(e) != base.hash) e.h[i] = ant }
        run { val pagsX = ArrayList<Int>()          // RAM del cartutx ($A000-$BFFF): pàgines de 256 bytes i després bytes
            for (pg in 0 until 0x20) { if (temps()) break; val ant = e.x.copyOfRange(pg * 0x100, pg * 0x100 + 0x100); if (ant.all { it.toInt() == 0 }) continue
                java.util.Arrays.fill(e.x, pg * 0x100, pg * 0x100 + 0x100, 0); if (hashDe(e) != base.hash) { System.arraycopy(ant, 0, e.x, pg * 0x100, 0x100); pagsX.add(pg) } }
            for (pg in pagsX) for (i in 0 until 0x100) { if (temps()) break; val a = pg * 0x100 + i; val ant = e.x[a]; if (ant.toInt() == 0) continue; e.x[a] = 0; if (hashDe(e) != base.hash) e.x[a] = ant } }
        val S = LinkedHashMap<Int, Int>()
        for (i in 0 until 0x2000) if (e.x[i].toInt() != 0) S[0x20000 + i] = e.x[i].toInt() and 255
        for (i in 0 until 0x8000) if (e.w[i].toInt() != 0) S[i] = e.w[i].toInt() and 255
        for (i in 0 until 0x80) if (e.h[i].toInt() != 0) S[0x10000 + i] = e.h[i].toInt() and 255
        return e to S
    }


    /** Mode «rutina d'arrencada»: qui escriu els bytes de punter de la cançó al fotograma de la petició és una rutina que el joc crida amb un número (A); es crida amb 0..255 i es queden els números que fan sonar sons diferents. */
    private class Arrenca(val p: AnalitzadorGb.MotorGb, val nBase: Int, val estatMin: Map<Int, Int>, val songs: List<Int>)
    private fun arrencada(rom: ByteArray, mbc5: Boolean, deltes: List<IntArray>, R: Int, f: AnalitzadorGb.MotorGb, base: Res, excl: Set<Int>, escrRoot: List<IntArray>, temps: () -> Boolean, sb: StringBuilder, fo: Int = 90, varia: Set<Int> = emptySet()): Arrenca? {
        fun idx(ad: Int) = if (ad >= 0xFF80) 0x10000 + (ad - 0xFF80) else if (ad in 0xA000..0xBFFF) 0x20000 + (ad - 0xA000) else if (ad < 0xD000) ad - 0xC000 else 0x1000 + (ad - 0xD000)
        val vots = HashMap<Int, Int>()
        for (r in escrRoot) { if (r[0] < R - 6 || r[0] > R + 1) continue; if (idx(r[1]) !in excl && r[3] !in varia) continue; if (r[3] == f.dest) continue; vots.merge(r[3], 1, Int::plus) }
        val pre = reconstrueix(deltes, R - 1)
        var P: AnalitzadorGb.MotorGb? = null; var nBase = -1
        // totes les rutines de la pila que escriuen els punters, de la que menys escriu (la més específica) a la que més
        for ((dest, _) in vots.entries.sortedBy { it.value }) {
            if (temps()) return null
            val r0 = escrRoot.first { it[3] == dest && it[0] in (R - 6)..(R + 1) && (idx(it[1]) in excl || it[3] in varia) }
            val Pk = AnalitzadorGb.MotorGb(dest, intArrayOf(r0[4], r0[5]), intArrayOf(r0[6], r0[7], r0[8], r0[9], r0[10], r0[11], r0[12]))
            var nb = -1
            for (n in listOf(Pk.regs[0]) + (0..255)) { if (temps()) return null; val r = prova(rom, mbc5, pre, f, fo, 400000, Pk, n); if (sona(r) && (r.hash == base.hash || (fo > 90 && semblant(r, base)))) { if (fo > 90 && prova(rom, mbc5, pre, f, fo, 400000, Pk, (n + 37) and 255).hash == r.hash) continue; nb = n; break } }
            sb.append("Rutina de la pila \$%04X: %s\n".format(dest, if (nb >= 0) "reprodueix la cançó amb A=%02X".format(nb) else "no reprodueix la cançó"))
            if (nb >= 0) { P = Pk; nBase = nb; break }
        }
        if (P == null) return null
        if (System.getProperty("dbgArr") != null) { val q = LinkedHashSet<String>(); for (r in escrRoot) if (r[3] == P.dest) q.add("f=%d regs=%s".format(r[0], (6..12).joinToString(",") { "%02X".format(r[it]) })); for (x in q.take(80)) sb.append("  crida $x\n") }
        val rb = prova(rom, mbc5, pre, f, fo, 400000, P, nBase)
        val (emin, Smin) = minimitza(rom, mbc5, pre, f, rb, temps) { x -> prova(rom, mbc5, x, f, fo, 400000, P, nBase).hash }
        val vistos = HashSet<Int>(); val songs = ArrayList<Int>()
        for (n in 0..255) { if (temps()) break; val r = prova(rom, mbc5, emin, f, 900, 400000, P, n); if (sona(r) && vistos.add(r.hash)) songs.add(n) }   // 900 fotogrames: moltes cançons comencen amb silenci
        if (System.getProperty("dbgArr") != null) for (de in listOf(0x0000, 0x9A40, 0xA4B7, 0xC595, 0x0810, 0xCB05)) {
            val Q = AnalitzadorGb.MotorGb(P.dest, P.bs, P.regs.copyOf().also { it[3] = de shr 8; it[4] = de and 255 }); val h = HashSet<Int>(); var nuevos = 0
            for (n in 0..255) { val r = prova(rom, mbc5, emin, f, 90, 400000, Q, n); if (sona(r)) { h.add(r.hash); if (vistos.add(r.hash)) nuevos++ } }
            sb.append("  DE=%04X: %d sons diferents, %d de nous\n".format(de, h.size, nuevos))
        }
        if (System.getProperty("dbgArr") != null) for (fo in listOf(90, 300, 900)) { val h = HashSet<Int>(); var senseNota = 0
            for (n in 0..255) { val r = prova(rom, mbc5, emin, f, fo, 4000000, P, n); if (sona(r)) h.add(r.hash) else if (r.notes > 0) senseNota++ }
            sb.append("  fotogrames=%d: %d sons diferents (i %d amb 1-2 notes)\n".format(fo, h.size, senseNota)) }
        if (System.getProperty("dbgPar2") != null) {
            // parelles (entrada de la taula $092F, n): els punters de lectura dels 4 canals (W021B, W031B, W041B, W051B) han de quedar dins la zona del seu canal
            val enes = listOf(3, 4, 5, 6, 7, 11, 12, 13, 14, 17, 19, 20)
            for (i in 0 until 14) { val o = 0x092F + 3 * i; val ad = (rom[o].toInt() and 255) or ((rom[o + 1].toInt() and 255) shl 8); val bk = rom[o + 2].toInt() and 255
                if (bk == 0 || bk >= rom.size / 0x4000 || ad !in 0x4000..0x7FFF) continue
                val res = StringBuilder()
                for (n in enes) {
                    val mem = GbMem(rom, mbc5); val cpu = GbCpu(mem); System.arraycopy(emin.w, 0, mem.wram, 0, 0x8000); System.arraycopy(emin.h, 0, mem.hram, 0, 0x80)
                    val src = bk * 0x4000 + (ad - 0x4000); System.arraycopy(rom, src, mem.xram, 0, minOf(0x4B6, rom.size - src)); mem.ie = 7
                    var notes = 0; val vol = IntArray(4); mem.alRegistre = { a, x -> when (a) { 0xFF12 -> vol[0] = x shr 4; 0xFF17 -> vol[1] = x shr 4; 0xFF21 -> vol[3] = x shr 4; 0xFF1C -> vol[2] = (x shr 5) and 3
                        0xFF14 -> if (x and 0x80 != 0 && vol[0] > 0) notes++; 0xFF19 -> if (x and 0x80 != 0 && vol[1] > 0) notes++; 0xFF1E -> if (x and 0x80 != 0 && vol[2] > 0) notes++; 0xFF23 -> if (x and 0x80 != 0 && vol[3] > 0) notes++ } }
                    mem.posaBancs(P.bs); val g = P.regs; cpu.a = n; cpu.b = g[1]; cpu.c = g[2]; cpu.d = g[3]; cpu.e = g[4]; cpu.h = g[5]; cpu.l = g[6]; cpu.sp = f.sp; cpu.crida(P.dest, 200000)
                    fun pt(a: Int) = (mem.wram[a].toInt() and 255) or ((mem.wram[a + 1].toInt() and 255) shl 8)
                    val ini = intArrayOf(pt(0x021B), pt(0x031B), pt(0x041B), pt(0x051B)); val fin = intArrayOf(ini[1], ini[2], ini[3], 0xA4B6)
                    if (System.getProperty("dbgPar2").let { it != null && it.contains("%d:%d".format(i, n)) }) sb.append("  PTRS e%d n%d ini=%s\n".format(i, n, ini.joinToString(",") { "%04X".format(it) }))
                    var fora = 0; var tots = 0
                    for (fr in 0 until 900) { mem.posaBancs(f.bs); val h2 = f.regs; cpu.a = h2[0]; cpu.b = h2[1]; cpu.c = h2[2]; cpu.d = h2[3]; cpu.e = h2[4]; cpu.h = h2[5]; cpu.l = h2[6]; cpu.sp = f.sp; cpu.crida(f.dest, 400000)
                        val q = intArrayOf(pt(0x021B), pt(0x031B), pt(0x041B), pt(0x051B)); tots++
                        if (System.getProperty("dbgPar2").let { it != null && it.contains("%d:%d".format(i, n)) } && (fr < 6 || fr % 150 == 0)) sb.append("  PTRS e%d n%d fr%d %s\n".format(i, n, fr, q.joinToString(",") { "%04X".format(it) }))
                        if ((0..3).any { q[it] < 0xA000 || q[it] >= 0xA4B8 }) fora++ }          // punters dins del bloc (els canals comparteixen patrons)
                    res.append(" n%d:%d%s".format(n, 100 - fora * 100 / tots, if (fora == 0 && notes >= 3) "*" else "")) }
                sb.append("  PAR2 entrada %d (banc %d %04X):%s\n".format(i, bk, ad, res)) } }
        if (System.getProperty("dbgSram") != null) {
            // taula de càrrega de la partitura (DKL): $092F, 3 bytes per entrada (adreça baixa, alta, banc); 0x4B6 bytes a $A000
            for (i in 0 until 40) { val o = 0x092F + 3 * i; val ad = (rom[o].toInt() and 255) or ((rom[o + 1].toInt() and 255) shl 8); val bk = rom[o + 2].toInt() and 255
                if (bk == 0 || bk >= rom.size / 0x4000 || ad !in 0x4000..0x7FFF) { sb.append("  SRAM entrada %d: %04X banc %d (fora)\n".format(i, ad, bk)); continue }
                val x = emin.copia(); System.arraycopy(rom, bk * 0x4000 + (ad - 0x4000), x.x, 0, minOf(0x4B6, rom.size - (bk * 0x4000 + (ad - 0x4000))))
                val bons = ArrayList<Int>(); for (n in 0..23) { val r = prova(rom, mbc5, x, f, 600, 400000, P, n); if (sona(r)) bons.add(n) }
                sb.append("  SRAM entrada %d: %04X banc %d -> sona amb n=%s\n".format(i, ad, bk, bons)) } }
        if (System.getProperty("dbgTaula") != null) { for (n in 0..24) { val r = prova(rom, mbc5, emin, f, 900, 400000, P, n); sb.append("  TAULA n=%d notes=%d esc=%d hash=%d%s\n".format(n, r.notes, r.esc.size, r.hash, if (n in songs) " (sona)" else "")) } }
        if (System.getProperty("dbgLect") != null) {
            val ls = HashMap<Int, List<Int>>()
            for (n in listOf(1, 8, 9, 10, 15, 16, 18, 21)) { val mem = GbMem(rom, mbc5); val cpu = GbCpu(mem); System.arraycopy(emin.w, 0, mem.wram, 0, 0x8000); System.arraycopy(emin.h, 0, mem.hram, 0, 0x80); mem.ie = 7
                val l = ArrayList<Int>(); mem.alRom = { a -> if (a >= 0x4000 && l.size < 4000) l.add(a) }
                mem.posaBancs(P.bs); val g = P.regs; cpu.a = n; cpu.b = g[1]; cpu.c = g[2]; cpu.d = g[3]; cpu.e = g[4]; cpu.h = g[5]; cpu.l = g[6]; cpu.sp = f.sp; cpu.crida(P.dest, 200000)
                ls[n] = l }
            val comu = ls.values.map { it.toSet() }.reduce { a, b -> a intersect b }
            for ((n, l) in ls) sb.append("  LECT n=%d: %d lectures; només d'aquesta n: %s\n".format(n, l.size, l.filter { it !in comu }.distinct().take(14).joinToString(" ") { "%04X".format(it) })) }
        if (System.getProperty("dbgDif") != null) {
            val dif = HashMap<Int, HashMap<Int, Int>>()          // adreça -> (cançó -> valor després)
            for (n in songs) { val mem = GbMem(rom, mbc5); val cpu = GbCpu(mem); System.arraycopy(emin.w, 0, mem.wram, 0, 0x8000); System.arraycopy(emin.h, 0, mem.hram, 0, 0x80); mem.ie = 7
                mem.posaBancs(P.bs); val g = P.regs; cpu.a = n; cpu.b = g[1]; cpu.c = g[2]; cpu.d = g[3]; cpu.e = g[4]; cpu.h = g[5]; cpu.l = g[6]; cpu.sp = f.sp; cpu.crida(P.dest, 200000)
                for (a in 0 until 0x8000) if (mem.wram[a] != emin.w[a]) dif.getOrPut(a) { HashMap() }[n] = mem.wram[a].toInt() and 255
                for (a in 0 until 0x80) if (mem.hram[a] != emin.h[a]) dif.getOrPut(0x10000 + a) { HashMap() }[n] = mem.hram[a].toInt() and 255 }
            val primeres = (System.getProperty("dbgDif") ?: "").split(",").mapNotNull { it.toIntOrNull() }.ifEmpty { songs.take(8) }
            sb.append("  DIF cançons=%s (de %d totals)\n".format(primeres, songs.size))
            for ((a, m) in dif.entries.sortedBy { it.key }) { val vs = primeres.map { m[it] }
                val totes = vs.all { it != null }; val ntot = m.size
                if (totes || ntot >= songs.size / 2) sb.append("  DIF %s %s en %d/%d: %s\n".format(if (a >= 0x10000) "H%02X".format(a - 0x10000) else "W%04X".format(a), if (totes) "TOTES-LES-PRIMERES" else "", ntot, songs.size, vs.joinToString(" ") { if (it == null) "--" else "%02X".format(it) })) } }
        if (System.getProperty("dbgBanc") != null) for (bk in 1..(rom.size / 0x4000 - 1)) {
            val Pb = AnalitzadorGb.MotorGb(P.dest, intArrayOf(bk, P.bs[1]), P.regs); val fb = AnalitzadorGb.MotorGb(f.dest, intArrayOf(bk, f.bs[1]), f.regs, f.sp); val h = HashSet<Int>(); var nous = 0
            for (n in 0..255) { val r = prova(rom, mbc5, emin, fb, 120, 30000, Pb, n); if (sona(r)) { h.add(r.hash); if (r.hash !in vistos) nous++ } }
            sb.append("  BANC %d: %d sons, %d de nous\n".format(bk, h.size, nous)) }
        if (System.getProperty("dbgEst") != null) { val es = inicisG.take(6).map { reconstrueix(deltes, it - 1) }; val d = ArrayList<String>()
            for (a in 0 until 0x8000) { val v = es.map { it.w[a].toInt() and 255 }; if (v.toSet().size > 1 && es.size > 1) d.add("W%04X=%s".format(a, v.joinToString("/") { "%02X".format(it) })) }
            for (a in 0 until 0x80) { val v = es.map { it.h[a].toInt() and 255 }; if (v.toSet().size > 1 && es.size > 1) d.add("H%02X=%s".format(a, v.joinToString("/") { "%02X".format(it) })) }
            sb.append("  EST inicis=%s difereixen %d: %s\n".format(inicisG.take(6), d.size, d.take(60).joinToString(" "))) }
        if (System.getProperty("dbgLen") != null) { val sbb = StringBuilder(); for (n in songs) { val l = listOf(900, 1800, 3600).map { prova(rom, mbc5, emin, f, it, 400000, P, n).esc.size }; sbb.append("#%d:%s ".format(n, l.joinToString("/"))) }; sb.append("  LEN " + sbb + "\n") }
        if (System.getProperty("dbgSub") != null) { val w = HashMap<Int, Set<Int>>(); for (n in songs) w[n] = prova(rom, mbc5, emin, f, 900, 400000, P, n).esc.toSet()
            System.getProperty("dbgPar")?.split(",")?.forEach { pr -> val (a, b) = pr.split(":").map { it.toInt() }; val x = w[a]!!; val y = w[b]!!
                fun can(z: Set<Int>) = z.map { it ushr 8 }.toSortedSet().joinToString(" ") { "%02X".format(it) }
                sb.append("  PAR #%d (%d esc, regs %s) dins #%d (%d esc, regs %s): %d%% dels de #%d; %d%% dels de #%d dins #%d\n".format(a, x.size, can(x), b, y.size, can(y), x.count { it in y } * 100 / maxOf(1, x.size), a, y.count { it in x } * 100 / maxOf(1, y.size), b, a)) }
            for (a in songs) { val x = w[a]!!; val top = songs.filter { it != a }.map { b -> b to x.count { it in w[b]!! } * 100 / maxOf(1, x.size) }.filter { it.second >= 70 }.sortedByDescending { it.second }.take(3); if (top.isNotEmpty()) sb.append("  SUB #%d (%d esc) %s\n".format(a, x.size, top.joinToString(" ") { "#${it.first}:${it.second}%" })) } }
        sb.append("Rutina d'arrencada: ${songs.size} números donen sons diferents\n")
        return Arrenca(P, nBase, Smin, songs)
    }

    /** Les cançons que el joc ha tocat de debò (un inici de so cada una): de cadascuna es treu l'estat mínim i es guarda el que difereix de la primera. */
    private fun observades(rom: ByteArray, mbc5: Boolean, deltes: List<IntArray>, inicis: List<Int>, f: AnalitzadorGb.MotorGb, S0: Map<Int, Int>, e0: Estat, apuLog: List<IntArray>, temps: () -> Boolean, hashes: MutableSet<Int>): List<List<Pair<Int, Int>>> {
        val out = ArrayList<List<Pair<Int, Int>>>(); val vistos = HashSet<Int>()
        for (on in inicis.take(12)) {
            if (temps()) break
            val ini = maxOf(1, on - 150); val est = reconstrueix(deltes, ini - 1); var Rk = -1; var estR: Estat? = null
            for (F in ini..minOf(on + 1, deltes.size - 1)) { for (x in deltes[F]) { val ad = x ushr 8; val v = (x and 255).toByte(); aplica(est, ad, v) }
                if (sona(prova(rom, mbc5, est, f, 30, 60000))) { val r = prova(rom, mbc5, est, f); if (sona(r)) { Rk = F; estR = est.copia(); break } }; if (temps()) break }
            if (Rk < 0 || estR == null) continue
            val rb = prova(rom, mbc5, estR, f); val (_, Sk) = minimitza(rom, mbc5, estR, f, rb, temps)
            // pokes de la cançó = el que difereix de l'estat de la primera (els bytes que a S0 hi són i a Sk no, es posen a zero)
            val ch = ArrayList<Pair<Int, Int>>(); for ((a, v) in Sk) if (S0[a] != v) ch.add(a to v); for ((a, _) in S0) if (!Sk.containsKey(a)) ch.add(a to 0)
            val ek = e0.copia(); for ((a, v) in ch) posa(ek, a, v.toByte())
            val r = prova(rom, mbc5, ek, f); if (sona(r) && vistos.add(r.hash)) { out.add(ch); hashes.add(r.hash) }
        }
        return out
    }

    /** Cançons que es demanen amb un número: de l'estat mínim es busquen els bytes que canvien el so i es barregen els 256 valors del que dona més sons diferents. */
    private fun escombra(rom: ByteArray, mbc5: Boolean, S: Map<Int, Int>, e: Estat, f: AnalitzadorGb.MotorGb, base: Res, excl: Set<Int>, permesos: Set<Int>?, temps: () -> Boolean, hashes: MutableSet<Int> = HashSet()): List<List<Pair<Int, Int>>> {
        fun amb(a: Int, v: Int): Res { val ek = e.copia(); posa(ek, a, v.toByte()); return prova(rom, mbc5, ek, f) }
        val rel = ArrayList<Int>()
        for ((a, v) in S) {
            if (temps()) break
            if (permesos != null && a !in permesos) continue      // només bytes que canvien entre les cançons que el joc ha tocat de debò
            if (a in excl) continue                  // un byte d'un punter de la ROM canvia el so per a qualsevol valor, però no és un número de cançó
            for (x in intArrayOf((v + 1) and 255, (v - 1) and 255, v xor 0x10, 0, 1, 2, 3)) { if (x == v) continue; val r = amb(a, x); if (sona(r) && r.hash != base.hash) { rel.add(a); break } }
        }
        var millor: List<List<Pair<Int, Int>>> = emptyList()
        for (a in rel) {
            if (temps()) break
            val vistos = HashSet<Int>(); val l = ArrayList<List<Pair<Int, Int>>>()
            for (x in 0..255) { if (temps()) break; val r = amb(a, x); if (sona(r) && vistos.add(r.hash)) l.add(listOf(a to x)) }
            // un número de cançó de debò dona entre 3 i 100 sons diferents; si en dona més, és un paràmetre qualsevol (afinació, volum...) que sempre canvia el so
            if (l.size in 3..100 && l.size > millor.size) { millor = l; hashes.clear(); hashes.addAll(vistos) }
        }
        return millor
    }

    private fun linia(crc: Long, mbc5: Boolean, f: AnalitzadorGb.MotorGb, S: Map<Int, Int>, cancons: List<List<Pair<Int, Int>>>, st: AnalitzadorGb.MotorGb?): String {
        val bs = f.bs.joinToString(",") { it.toString() } + (if (f.sp != 0xFFFE) ",S%04X".format(f.sp) else "")    // SP del joc (els lectors d'abans només llegeixen els dos primers camps)
        val regs = f.regs.joinToString("") { "%02X".format(it) }
        fun nomA(a: Int) = if (a >= 0x20000) "X%04X".format(a - 0x20000) else if (a >= 0x10000) "H%02X".format(a - 0x10000) else "W%04X".format(a)
        val pk = S.entries.joinToString(" ") { nomA(it.key) + "=%02X".format(it.value) }
        val cs = cancons.joinToString(" ") { ch -> ch.joinToString("/") { if (it.first < 0) "#%d".format(it.second) else nomA(it.first) + "=%02X".format(it.second) } }
        val sta = if (st == null) "" else " start=%04X:%s:%s".format(st.dest, st.bs.joinToString(",") { it.toString() }, st.regs.joinToString("") { "%02X".format(it) })
        return "AUTO_POKE crc=%08X mbc5=%d f=%04X:%s:%s%s base=%s | songs=%s\n".format(crc, if (mbc5) 1 else 0, f.dest, bs, regs, sta, pk, cs)
    }
}
