package cat.gbdsmixxx

/**
 * CPU 6502 de la NES (2A03: sense mode decimal) per executar el driver d'un fitxer .nsf. Només els codis oficials; els altres (no oficials) es salten com a NOP d'un byte.
 * No es compten cicles: el codi d'àudio (init i play) s'executa instantàniament fins al seu `rts`.
 */
/** Bus de memòria de la CPU 6502. Una interfície i no lambdes `(Int) -> Int`: cridar una lambda genèrica fa boxing de l'adreça i del valor (un `Integer` per accés, milions per segon). */
interface Bus6502 { fun rb(a: Int): Int; fun wb(a: Int, v: Int) }

class Cpu6502(private val bus: Bus6502) {
    /** Compatibilitat (analitzador): amb lambdes, com abans. */
    constructor(rb: (Int) -> Int, wb: (Int, Int) -> Unit) : this(object : Bus6502 { override fun rb(a: Int) = rb(a); override fun wb(a: Int, v: Int) = wb(a, v) })
    private fun rb(a: Int): Int = bus.rb(a)
    private fun wb(a: Int, v: Int) = bus.wb(a, v)
    var a = 0; var x = 0; var y = 0; var sp = 0xFD; var pc = 0
    var fc = false; var fz = false; var fi = true; var fd = false; var fv = false; var fn = false
    var instruccions = 0L

    private fun n8(): Int { val v = rb(pc); pc = (pc + 1) and 0xFFFF; return v }
    private fun n16(): Int { val lo = n8(); return lo or (n8() shl 8) }
    private fun push(v: Int) { wb(0x100 + sp, v and 255); sp = (sp - 1) and 255 }
    private fun pull(): Int { sp = (sp + 1) and 255; return rb(0x100 + sp) }
    var p: Int
        get() = (if (fn) 0x80 else 0) or (if (fv) 0x40 else 0) or 0x20 or (if (fd) 8 else 0) or (if (fi) 4 else 0) or (if (fz) 2 else 0) or (if (fc) 1 else 0)
        set(v) { fn = v and 0x80 != 0; fv = v and 0x40 != 0; fd = v and 8 != 0; fi = v and 4 != 0; fz = v and 2 != 0; fc = v and 1 != 0 }
    private fun nz(v: Int): Int { fz = v and 255 == 0; fn = v and 0x80 != 0; return v and 255 }

    /** Adreça efectiva segons el mode (bbb) del grup `cc`. */
    private fun ea(cc: Int, bbb: Int): Int = when (cc) {
        1 -> when (bbb) {
            0 -> { val z = (n8() + x) and 255; rb(z) or (rb((z + 1) and 255) shl 8) }
            1 -> n8()
            3 -> n16()
            4 -> { val z = n8(); ((rb(z) or (rb((z + 1) and 255) shl 8)) + y) and 0xFFFF }
            5 -> (n8() + x) and 255
            6 -> (n16() + y) and 0xFFFF
            else -> (n16() + x) and 0xFFFF
        }
        else -> when (bbb) {
            1 -> n8()
            3 -> n16()
            5 -> (n8() + x) and 255
            else -> (n16() + x) and 0xFFFF
        }
    }

    private fun adc(v: Int) { val r = a + v + (if (fc) 1 else 0); fv = (a xor r) and (v xor r) and 0x80 != 0; fc = r > 255; a = nz(r) }
    private fun cmp(r: Int, v: Int) { fc = r >= v; nz(r - v) }
    private fun branca(cond: Boolean) { val o = n8().toByte().toInt(); if (cond) pc = (pc + o) and 0xFFFF }

    fun step() {
        instruccions++
        val op = n8()
        val aaa = op shr 5; val bbb = (op shr 2) and 7; val cc = op and 3
        when (op) {
            0x00 -> { pc = (pc + 1) and 0xFFFF; push(pc shr 8); push(pc); push(p or 0x10); fi = true; pc = rb(0xFFFE) or (rb(0xFFFF) shl 8) }
            0x20 -> { val ad = n16(); val r = (pc - 1) and 0xFFFF; push(r shr 8); push(r); pc = ad }
            0x40 -> { p = pull(); pc = pull() or (pull() shl 8) }
            0x60 -> { pc = ((pull() or (pull() shl 8)) + 1) and 0xFFFF }
            0x08 -> push(p or 0x10); 0x28 -> p = pull()
            0x48 -> push(a); 0x68 -> a = nz(pull())
            0x88 -> y = nz(y - 1); 0xA8 -> y = nz(a); 0xC8 -> y = nz(y + 1); 0xE8 -> x = nz(x + 1)
            0x18 -> fc = false; 0x38 -> fc = true; 0x58 -> fi = false; 0x78 -> fi = true; 0xB8 -> fv = false; 0xD8 -> fd = false; 0xF8 -> fd = true
            0x8A -> a = nz(x); 0x98 -> a = nz(y); 0x9A -> sp = x; 0xAA -> x = nz(a); 0xBA -> x = nz(sp); 0xCA -> x = nz(x - 1)
            0xEA -> {}
            0x10 -> branca(!fn); 0x30 -> branca(fn); 0x50 -> branca(!fv); 0x70 -> branca(fv)
            0x90 -> branca(!fc); 0xB0 -> branca(fc); 0xD0 -> branca(!fz); 0xF0 -> branca(fz)
            0x4C -> pc = n16()
            0x6C -> { val ad = n16(); pc = rb(ad) or (rb((ad and 0xFF00) or ((ad + 1) and 0xFF)) shl 8) }
            else -> when (cc) {
                1 -> {
                    if (op == 0x89) { n8(); return }
                    if (aaa == 4) { wb(ea(1, bbb), a); return }
                    val v = if (bbb == 2) n8() else rb(ea(1, bbb))
                    when (aaa) {
                        0 -> a = nz(a or v); 1 -> a = nz(a and v); 2 -> a = nz(a xor v); 3 -> adc(v)
                        5 -> a = nz(v); 6 -> cmp(a, v); else -> adc(v xor 255)
                    }
                }
                2 -> {
                    if (bbb == 2 && aaa < 4) {              // desplaçaments a l'acumulador
                        a = desplaca(aaa, a); return
                    }
                    if (bbb == 0 && aaa != 5) return          // no oficials
                    if (bbb == 4 || bbb == 6) return
                    when (aaa) {
                        4 -> { val ad = if (bbb == 5) (n8() + y) and 255 else ea(2, bbb); wb(ad, x) }
                        5 -> { val v = if (bbb == 0) n8() else rb(if (bbb == 5) (n8() + y) and 255 else if (bbb == 7) (n16() + y) and 0xFFFF else ea(2, bbb)); x = nz(v) }
                        6 -> { val ad = ea(2, bbb); wb(ad, nz(rb(ad) - 1)) }
                        7 -> { val ad = ea(2, bbb); wb(ad, nz(rb(ad) + 1)) }
                        else -> { val ad = ea(2, bbb); wb(ad, desplaca(aaa, rb(ad))) }
                    }
                }
                0 -> {
                    if (bbb == 4 || bbb == 6 || (bbb == 0 && aaa < 5)) return
                    when (aaa) {
                        1 -> { val v = rb(ea(2, bbb)); fz = a and v == 0; fv = v and 0x40 != 0; fn = v and 0x80 != 0 }
                        4 -> wb(ea(2, bbb), y)
                        5 -> { val v = if (bbb == 0) n8() else rb(ea(2, bbb)); y = nz(v) }
                        6 -> { val v = if (bbb == 0) n8() else rb(ea(2, bbb)); cmp(y, v) }
                        7 -> { val v = if (bbb == 0) n8() else rb(ea(2, bbb)); cmp(x, v) }
                        else -> {}
                    }
                }
                else -> {}
            }
        }
    }

    private fun desplaca(k: Int, v: Int): Int = when (k) {
        0 -> { fc = v and 0x80 != 0; nz(v shl 1) }
        1 -> { val c = if (fc) 1 else 0; fc = v and 0x80 != 0; nz((v shl 1) or c) }
        2 -> { fc = v and 1 != 0; nz(v shr 1) }
        else -> { val c = if (fc) 0x80 else 0; fc = v and 1 != 0; nz((v shr 1) or c) }
    }

    /** Crida la rutina `adr` (JSR) i l'executa fins al seu `rts`; retorna false si passa el límit d'instruccions. */
    fun crida(adr: Int, limit: Int = 600000): Boolean {
        val r = (MAGIC - 1) and 0xFFFF
        push(r shr 8); push(r); pc = adr
        var n = 0
        while (pc != MAGIC) { step(); if (++n > limit) { limits++; return false } }
        return true
    }
    /** Crides que han arribat al límit d'instruccions (el codi esperava alguna cosa que no s'emula): diagnòstic, no canvia el so. */
    var limits = 0
    companion object { const val MAGIC = 0x4800 }
}
