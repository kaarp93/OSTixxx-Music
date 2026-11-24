package cat.gbdsmixxx

/** Bus de 24 bits de la CPU principal de la SNES (65816). */
interface Bus65816 { fun rd(a: Int): Int; fun wr(a: Int, v: Int) }

/**
 * CPU 65C816 de la SNES (per a l'analitzador de SNES): totes les instruccions, els modes d'adreçament, el mode d'emulació i els modes de 8/16 bits. Sense cicles exactes (només s'executa
 * instrucció a instrucció) i sense el mode decimal (D) de l'ADC/SBC, que els jocs de SNES gairebé no fan servir.
 */
class Cpu65816(private val b: Bus65816) {
    var a = 0; var x = 0; var y = 0; var sp = 0x1FF; var dp = 0; var dbr = 0; var pbr = 0; var pc = 0
    var fn = false; var fv = false; var fm = true; var fx = true; var fd = false; var fi = true; var fz = false; var fc = false; var e = true
    var parada = false; var espera = false
    private val m8 get() = e || fm
    private val x8 get() = e || fx
    private fun xr() = if (x8) x and 255 else x
    private fun yr() = if (x8) y and 255 else y

    fun reset() {
        e = true; fm = true; fx = true; fi = true; fd = false; sp = 0x1FF; dp = 0; dbr = 0; pbr = 0; parada = false; espera = false
        pc = b.rd(0xFFFC) or (b.rd(0xFFFD) shl 8)
    }

    private fun f8(): Int { val v = b.rd((pbr shl 16) or pc); pc = (pc + 1) and 0xFFFF; return v }
    private fun f16() = f8() or (f8() shl 8)
    private fun f24() = f16() or (f8() shl 16)
    private fun ld(ea: Int, w8: Boolean): Int = if (w8) b.rd(ea and 0xFFFFFF) else b.rd(ea and 0xFFFFFF) or (b.rd((ea + 1) and 0xFFFFFF) shl 8)
    private fun st(ea: Int, v: Int, w8: Boolean) { b.wr(ea and 0xFFFFFF, v and 255); if (!w8) b.wr((ea + 1) and 0xFFFFFF, (v shr 8) and 255) }
    private fun r16b0(p: Int) = b.rd(p and 0xFFFF) or (b.rd((p + 1) and 0xFFFF) shl 8)
    private fun r24b0(p: Int) = r16b0(p) or (b.rd((p + 2) and 0xFFFF) shl 16)
    private fun push(v: Int) { b.wr(sp, v and 255); sp = if (e) 0x100 or ((sp - 1) and 255) else (sp - 1) and 0xFFFF }
    private fun pull(): Int { sp = if (e) 0x100 or ((sp + 1) and 255) else (sp + 1) and 0xFFFF; return b.rd(sp) }
    private fun push16(v: Int) { push(v shr 8); push(v) }
    private fun pull16(): Int { val l = pull(); return l or (pull() shl 8) }

    private fun p(): Int = (if (fn) 0x80 else 0) or (if (fv) 0x40 else 0) or (if (fm) 0x20 else 0) or (if (fx) 0x10 else 0) or (if (fd) 8 else 0) or (if (fi) 4 else 0) or (if (fz) 2 else 0) or (if (fc) 1 else 0)
    private fun setP(v: Int) {
        fn = v and 0x80 != 0; fv = v and 0x40 != 0; fd = v and 8 != 0; fi = v and 4 != 0; fz = v and 2 != 0; fc = v and 1 != 0
        if (e) { fm = true; fx = true } else { fm = v and 0x20 != 0; fx = v and 0x10 != 0 }
        if (x8) { x = x and 255; y = y and 255 }
    }
    private fun nz(v: Int, w8: Boolean) { if (w8) { fz = (v and 255) == 0; fn = v and 0x80 != 0 } else { fz = (v and 0xFFFF) == 0; fn = v and 0x8000 != 0 } }

    /** Interrupció NMI (la CPU la crida al començament del vblank si el joc l'ha activada). */
    fun nmi() {
        espera = false
        if (!e) push(pbr)
        push16(pc); push(p() and (if (e) 0xEF else 0xFF)); fi = true; fd = false; pbr = 0
        val v = if (e) 0xFFFA else 0xFFEA; pc = b.rd(v) or (b.rd(v + 1) shl 8)
    }

    private fun ea(md: String): Int = when (md) {
        "dp" -> (dp + f8()) and 0xFFFF
        "dpx" -> (dp + f8() + xr()) and 0xFFFF
        "dpy" -> (dp + f8() + yr()) and 0xFFFF
        "abs" -> (dbr shl 16) or f16()
        "absx" -> ((dbr shl 16) + f16() + xr()) and 0xFFFFFF
        "absy" -> ((dbr shl 16) + f16() + yr()) and 0xFFFFFF
        "long" -> f24()
        "longx" -> (f24() + xr()) and 0xFFFFFF
        "ind" -> (dbr shl 16) or r16b0((dp + f8()) and 0xFFFF)
        "indx" -> (dbr shl 16) or r16b0((dp + f8() + xr()) and 0xFFFF)
        "indy" -> ((dbr shl 16) + r16b0((dp + f8()) and 0xFFFF) + yr()) and 0xFFFFFF
        "indl" -> r24b0((dp + f8()) and 0xFFFF)
        "indly" -> (r24b0((dp + f8()) and 0xFFFF) + yr()) and 0xFFFFFF
        "sr" -> (sp + f8()) and 0xFFFF
        "sry" -> ((dbr shl 16) + r16b0((sp + f8()) and 0xFFFF) + yr()) and 0xFFFFFF
        else -> throw IllegalStateException(md)
    }
    private fun imm(w8: Boolean): Int { val r = (pbr shl 16) or pc; pc = (pc + (if (w8) 1 else 2)) and 0xFFFF; return r }
    private fun ma(md: String, w8: Boolean) = if (md == "imm" || md == "immx") imm(w8) else ea(md)

    private fun adc(v: Int, w8: Boolean) {
        val msk = if (w8) 0xFF else 0xFFFF; val msb = if (w8) 0x80 else 0x8000; val ac = a and msk
        val r = ac + v + (if (fc) 1 else 0)
        fv = ((ac xor v) and msb == 0) && ((ac xor r) and msb != 0)
        fc = r > msk; val rr = r and msk; setA(rr, w8); nz(rr, w8)
    }
    private fun setA(v: Int, w8: Boolean) { a = if (w8) (a and 0xFF00) or (v and 255) else v and 0xFFFF }
    private fun cmp(r: Int, v: Int, w8: Boolean) { val msk = if (w8) 0xFF else 0xFFFF; val rr = (r and msk) - v; fc = rr >= 0; nz(rr and msk, w8) }
    private fun br(c: Boolean) { val o = f8().toByte().toInt(); if (c) pc = (pc + o) and 0xFFFF }
    private fun shift(mn: String, v: Int, w8: Boolean): Int {
        val msb = if (w8) 0x80 else 0x8000; val msk = if (w8) 0xFF else 0xFFFF; val c0 = if (fc) 1 else 0
        val r = when (mn) {
            "ASL" -> { fc = v and msb != 0; (v shl 1) and msk }
            "LSR" -> { fc = v and 1 != 0; v shr 1 }
            "ROL" -> { fc = v and msb != 0; ((v shl 1) or c0) and msk }
            else -> { fc = v and 1 != 0; (v shr 1) or (if (c0 == 1) msb else 0) }
        }
        nz(r, w8); return r
    }
    private fun xbaSet() { val lo = a and 255; val hi = (a shr 8) and 255; a = (lo shl 8) or hi; nz(a and 255, true) }

    /** Executa una instrucció. */
    fun pas() {
        if (parada || espera) return
        val op = f8(); val mn = MN[op]; val md = MD[op]
        when (mn) {
            "ORA", "AND", "EOR", "ADC", "SBC", "CMP", "LDA", "STA" -> {
                val w8 = m8; val ad = ma(md, w8)
                if (mn == "STA") { st(ad, a, w8); return }
                val v = ld(ad, w8); val msk = if (w8) 0xFF else 0xFFFF
                when (mn) {
                    "ORA" -> { setA((a or v) and msk, w8); nz(a, w8) }
                    "AND" -> { setA((a and v) and msk, w8); nz(a, w8) }
                    "EOR" -> { setA((a xor v) and msk, w8); nz(a, w8) }
                    "ADC" -> adc(v, w8)
                    "SBC" -> adc(v.inv() and msk, w8)
                    "CMP" -> cmp(a, v, w8)
                    else -> { setA(v, w8); nz(a, w8) }
                }
            }
            "LDX", "LDY", "CPX", "CPY" -> {
                val w8 = x8; val v = ld(ma(md, w8), w8)
                when (mn) { "LDX" -> { x = v; nz(v, w8) }; "LDY" -> { y = v; nz(v, w8) }; "CPX" -> cmp(x, v, w8); else -> cmp(y, v, w8) }
            }
            "STX" -> st(ma(md, x8), x, x8)
            "STY" -> st(ma(md, x8), y, x8)
            "STZ" -> st(ma(md, m8), 0, m8)
            "BIT" -> { val w8 = m8; val ad = ma(md, w8); val v = ld(ad, w8); fz = ((a and v) and (if (w8) 0xFF else 0xFFFF)) == 0; if (md != "imm") { fn = v and (if (w8) 0x80 else 0x8000) != 0; fv = v and (if (w8) 0x40 else 0x4000) != 0 } }
            "TSB", "TRB" -> { val w8 = m8; val ad = ea(md); val v = ld(ad, w8); fz = (a and v) == 0; st(ad, if (mn == "TSB") v or a else v and a.inv(), w8) }
            "ASL", "LSR", "ROL", "ROR" -> {
                val w8 = m8
                if (md == "acc") { val r = shift(mn, if (w8) a and 255 else a, w8); setA(r, w8) } else { val ad = ea(md); st(ad, shift(mn, ld(ad, w8), w8), w8) }
            }
            "INC", "DEC" -> {
                val w8 = m8; val d = if (mn == "INC") 1 else -1
                if (md == "acc") { val r = ((if (w8) a and 255 else a) + d) and (if (w8) 0xFF else 0xFFFF); setA(r, w8); nz(r, w8) }
                else { val ad = ea(md); val r = (ld(ad, w8) + d) and (if (w8) 0xFF else 0xFFFF); st(ad, r, w8); nz(r, w8) }
            }
            "INX" -> { x = (x + 1) and (if (x8) 0xFF else 0xFFFF); nz(x, x8) }
            "INY" -> { y = (y + 1) and (if (x8) 0xFF else 0xFFFF); nz(y, x8) }
            "DEX" -> { x = (x - 1) and (if (x8) 0xFF else 0xFFFF); nz(x, x8) }
            "DEY" -> { y = (y - 1) and (if (x8) 0xFF else 0xFFFF); nz(y, x8) }
            "BPL" -> br(!fn); "BMI" -> br(fn); "BVC" -> br(!fv); "BVS" -> br(fv); "BCC" -> br(!fc); "BCS" -> br(fc); "BNE" -> br(!fz); "BEQ" -> br(fz); "BRA" -> br(true)
            "BRL" -> { val o = f16().toShort().toInt(); pc = (pc + o) and 0xFFFF }
            "JMP" -> when (md) {
                "abs" -> pc = f16()
                "absind" -> pc = r16b0(f16())
                "absindx" -> { val ptr = (f16() + xr()) and 0xFFFF; pc = b.rd((pbr shl 16) or ptr) or (b.rd((pbr shl 16) or ((ptr + 1) and 0xFFFF)) shl 8) }
                else -> throw IllegalStateException(md)
            }
            "JML" -> if (md == "long") { val t = f24(); pc = t and 0xFFFF; pbr = t shr 16 } else { val t = r24b0(f16()); pc = t and 0xFFFF; pbr = t shr 16 }
            "JSR" -> if (md == "abs") { val t = f16(); push16((pc - 1) and 0xFFFF); pc = t } else { val ptr = (f16() + xr()) and 0xFFFF; push16((pc - 1) and 0xFFFF); val base = (pbr shl 16) or ptr; pc = b.rd(base) or (b.rd((base and 0xFF0000) or ((ptr + 1) and 0xFFFF)) shl 8) }
            "JSL" -> { val t = f24(); push(pbr); push16((pc - 1) and 0xFFFF); pc = t and 0xFFFF; pbr = t shr 16 }
            "RTS" -> pc = (pull16() + 1) and 0xFFFF
            "RTL" -> { pc = (pull16() + 1) and 0xFFFF; pbr = pull() }
            "RTI" -> { setP(pull()); pc = pull16(); if (!e) pbr = pull() }
            "BRK", "COP" -> {
                f8(); if (!e) push(pbr); push16(pc); push(p() or (if (e) 0x10 else 0)); fi = true; fd = false; pbr = 0
                val v = if (mn == "COP") (if (e) 0xFFF4 else 0xFFE4) else (if (e) 0xFFFE else 0xFFE6); pc = b.rd(v) or (b.rd(v + 1) shl 8)
            }
            "CLC" -> fc = false; "SEC" -> fc = true; "CLI" -> fi = false; "SEI" -> fi = true; "CLD" -> fd = false; "SED" -> fd = true; "CLV" -> fv = false
            "REP" -> { val v = f8(); setP(p() and v.inv()) }
            "SEP" -> { val v = f8(); setP(p() or v) }
            "XCE" -> { val c = fc; fc = e; e = c; if (e) { fm = true; fx = true; x = x and 255; y = y and 255; sp = 0x100 or (sp and 255) } }
            "NOP" -> {}
            "WDM" -> f8()
            "WAI" -> espera = true
            "STP" -> parada = true
            "PHA" -> if (m8) push(a) else push16(a)
            "PHX" -> if (x8) push(x) else push16(x)
            "PHY" -> if (x8) push(y) else push16(y)
            "PLA" -> if (m8) { setA(pull(), true); nz(a, true) } else { a = pull16(); nz(a, false) }
            "PLX" -> if (x8) { x = pull(); nz(x, true) } else { x = pull16(); nz(x, false) }
            "PLY" -> if (x8) { y = pull(); nz(y, true) } else { y = pull16(); nz(y, false) }
            "PHP" -> push(p() or (if (e) 0x10 else 0))
            "PLP" -> setP(pull())
            "PHB" -> push(dbr); "PLB" -> { dbr = pull(); nz(dbr, true) }
            "PHD" -> push16(dp); "PLD" -> { dp = pull16(); nz(dp, false) }
            "PHK" -> push(pbr)
            "PEA" -> push16(f16())
            "PEI" -> push16(r16b0((dp + f8()) and 0xFFFF))
            "PER" -> { val o = f16(); push16((pc + o) and 0xFFFF) }
            "TAX" -> { x = if (x8) a and 255 else a; nz(x, x8) }
            "TAY" -> { y = if (x8) a and 255 else a; nz(y, x8) }
            "TXA" -> { setA(x, m8); nz(a, m8) }
            "TYA" -> { setA(y, m8); nz(a, m8) }
            "TSX" -> { x = if (x8) sp and 255 else sp; nz(x, x8) }
            "TXS" -> sp = if (e) 0x100 or (x and 255) else x
            "TXY" -> { y = x; nz(y, x8) }
            "TYX" -> { x = y; nz(x, x8) }
            "TCD" -> { dp = a; nz(dp, false) }
            "TDC" -> { a = dp; nz(a, false) }
            "TCS" -> sp = if (e) 0x100 or (a and 255) else a
            "TSC" -> { a = sp; nz(a, false) }
            "XBA" -> xbaSet()
            "MVN", "MVP" -> {
                val d = f8(); val s = f8(); dbr = d; val inc = if (mn == "MVN") 1 else -1; val mk = if (x8) 0xFF else 0xFFFF
                do { b.wr((d shl 16) or y, b.rd((s shl 16) or x)); x = (x + inc) and mk; y = (y + inc) and mk; a = (a - 1) and 0xFFFF } while (a != 0xFFFF)
            }
            else -> throw IllegalStateException("opcode $op $mn")
        }
    }

    companion object {
        private val TAULA = """BRK imm8,ORA indx,COP imm8,ORA sr,TSB dp,ORA dp,ASL dp,ORA indl,PHP imp,ORA imm,ASL acc,PHD imp,TSB abs,ORA abs,ASL abs,ORA long,
BPL rel,ORA indy,ORA ind,ORA sry,TRB dp,ORA dpx,ASL dpx,ORA indly,CLC imp,ORA absy,INC acc,TCS imp,TRB abs,ORA absx,ASL absx,ORA longx,
JSR abs,AND indx,JSL long,AND sr,BIT dp,AND dp,ROL dp,AND indl,PLP imp,AND imm,ROL acc,PLD imp,BIT abs,AND abs,ROL abs,AND long,
BMI rel,AND indy,AND ind,AND sry,BIT dpx,AND dpx,ROL dpx,AND indly,SEC imp,AND absy,DEC acc,TSC imp,BIT absx,AND absx,ROL absx,AND longx,
RTI imp,EOR indx,WDM imm8,EOR sr,MVP blk,EOR dp,LSR dp,EOR indl,PHA imp,EOR imm,LSR acc,PHK imp,JMP abs,EOR abs,LSR abs,EOR long,
BVC rel,EOR indy,EOR ind,EOR sry,MVN blk,EOR dpx,LSR dpx,EOR indly,CLI imp,EOR absy,PHY imp,TCD imp,JML long,EOR absx,LSR absx,EOR longx,
RTS imp,ADC indx,PER rel16,ADC sr,STZ dp,ADC dp,ROR dp,ADC indl,PLA imp,ADC imm,ROR acc,RTL imp,JMP absind,ADC abs,ROR abs,ADC long,
BVS rel,ADC indy,ADC ind,ADC sry,STZ dpx,ADC dpx,ROR dpx,ADC indly,SEI imp,ADC absy,PLY imp,TDC imp,JMP absindx,ADC absx,ROR absx,ADC longx,
BRA rel,STA indx,BRL rel16,STA sr,STY dp,STA dp,STX dp,STA indl,DEY imp,BIT imm,TXA imp,PHB imp,STY abs,STA abs,STX abs,STA long,
BCC rel,STA indy,STA ind,STA sry,STY dpx,STA dpx,STX dpy,STA indly,TYA imp,STA absy,TXS imp,TXY imp,STZ abs,STA absx,STZ absx,STA longx,
LDY immx,LDA indx,LDX immx,LDA sr,LDY dp,LDA dp,LDX dp,LDA indl,TAY imp,LDA imm,TAX imp,PLB imp,LDY abs,LDA abs,LDX abs,LDA long,
BCS rel,LDA indy,LDA ind,LDA sry,LDY dpx,LDA dpx,LDX dpy,LDA indly,CLV imp,LDA absy,TSX imp,TYX imp,LDY absx,LDA absx,LDX absy,LDA longx,
CPY immx,CMP indx,REP imm8,CMP sr,CPY dp,CMP dp,DEC dp,CMP indl,INY imp,CMP imm,DEX imp,WAI imp,CPY abs,CMP abs,DEC abs,CMP long,
BNE rel,CMP indy,CMP ind,CMP sry,PEI dp,CMP dpx,DEC dpx,CMP indly,CLD imp,CMP absy,PHX imp,STP imp,JML absindl,CMP absx,DEC absx,CMP longx,
CPX immx,SBC indx,SEP imm8,SBC sr,CPX dp,SBC dp,INC dp,SBC indl,INX imp,SBC imm,NOP imp,XBA imp,CPX abs,SBC abs,INC abs,SBC long,
BEQ rel,SBC indy,SBC ind,SBC sry,PEA abs,SBC dpx,INC dpx,SBC indly,SED imp,SBC absy,PLX imp,XCE imp,JSR absindx,SBC absx,INC absx,SBC longx"""
        private val MN = Array(256) { "" }
        private val MD = Array(256) { "" }
        init {
            val t = TAULA.replace("\n", "").split(',').map { it.trim() }
            require(t.size == 256) { "taula d'opcodes: ${t.size}" }
            for (i in 0 until 256) { val q = t[i].split(' '); MN[i] = q[0]; MD[i] = q[1] }
        }
    }
}
