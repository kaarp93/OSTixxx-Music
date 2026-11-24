package cat.gbdsmixxx

/**
 * CPU de Game Boy (Sharp LR35902) i memòria mínima per executar **el driver de so original d'un joc** (el mateix codi que corre a la consola):
 * ROM amb canvi de banc (MBC1/MBC5; amb `GbMem.posaReal`, també MBC1 gran/multicart, MBC2, MBC3 i bancs de RAM), RAM de treball, HRAM i registres d'E/S; les escriptures als registres de so ($FF10-$FF3F) van a `alRegistre`.
 * No hi ha vídeo, ni botons, ni temporitzadors, ni interrupcions: el codi que es crida (rutina d'init, rutina de play) s'executa fins al seu `ret`.
 * Les instruccions es compten (no els cicles): el codi d'àudio no depèn del temps.
 */
class GbMem(val rom: ByteArray, val mbc5: Boolean) {
    /**
     * Mapper complet segons la capçalera (l'autor, 2026-11-22: «si una ROM no s'analitza per culpa del mapper, implementa'l»): MBC1 (banc de 7 bits, mode 1, multicart MBC1M),
     * MBC2 (banc amb A8 i RAM de 512 x 4 bits), MBC3 (banc de 7 bits, bancs de RAM, RTC llegit com a 0), MBC5 (bancs de RAM, amb vibració) i bancs de RAM del cartutx.
     * S'activa sol (`init`) només on el comportament d'abans no arribava a tota la ROM: MBC1 i MBC3 de més de 512 KB (inclòs el multicart). A la resta queda el d'abans
     * (els drivers que ja sonen bé no canvien); `posaReal` l'activa a mà (els bancs de RAM de més de 8 KB només hi són llavors).
     * No modelat: l'habilitació de la RAM ($0000-$1FFF; sempre activa), el rellotge del MBC3, HuC1/HuC3/MMM01/MBC6/MBC7/TAMA5 (cap ROM de l'autor els fa servir).
     */
    var real = false; private set
    /** Tipus de mapper de la capçalera ($0147): 0 = cap, 1 = MBC1, 2 = MBC2, 3 = MBC3, 5 = MBC5. */
    val tipusMbc: Int = if (rom.size < 0x150) 0 else when (rom[0x147].toInt() and 255) { 1, 2, 3 -> 1; 5, 6 -> 2; in 0x0F..0x13 -> 3; in 0x19..0x1E -> 5; else -> 0 }
    /** MBC1M (multicart, p. ex. Bomberman Collection): 1 MB amb el logotip de Nintendo també a l'inici del banc $10; el banc és (bits alts << 4) | 4 bits baixos. */
    val multicart: Boolean = tipusMbc == 1 && rom.size == 0x100000 && (0 until 0x30).all { rom[0x40104 + it] == rom[0x104 + it] }
    private val bancsRam = if (rom.size < 0x150) 1 else when (rom[0x149].toInt() and 255) { 3 -> 4; 4 -> 16; 5 -> 8; else -> 1 }
    private val vibra = rom.size >= 0x150 && (rom[0x147].toInt() and 255) in 0x1C..0x1E
    private var modeMbc1 = 0; private var altMbc1 = 0; private var baixMbc1 = 1
    /** Banc de RAM del cartutx a $A000-$BFFF (només amb `real`). */
    var bancRam = 0
        set(v) { field = v; offRam = (v % bancsRam) * 0x2000 }
    private var offRam = 0
    /** Desplaçament a `rom` de $0000-$3FFF (MBC1 en mode 1 amb ROM gran; si no, 0). */
    private var off0 = 0
    fun posaReal() { real = true; if (xram.size < bancsRam * 0x2000) xram = xram.copyOf(bancsRam * 0x2000) }
    /** MBC1: banc = bits alts ($4000) << 5 | 5 bits baixos ($2000, el 0 val 1); al multicart, << 4 i només 4 bits baixos. En mode 1 els bits alts també trien el banc de $0000 i el de RAM. */
    private fun recalcMbc1() {
        val sh = if (multicart) 4 else 5
        banc = (altMbc1 shl sh) or (if (multicart) baixMbc1 and 0x0F else baixMbc1)
        off0 = if (modeMbc1 == 1) ((altMbc1 shl sh) % nBancs) * 0x4000 else 0
        bancRam = if (modeMbc1 == 1) altMbc1 else 0
    }
    private fun wbMapper(a: Int, v: Int) {
        when (tipusMbc) {
            1 -> when {
                a < 0x2000 -> {}
                a < 0x4000 -> { baixMbc1 = (v and 0x1F).let { if (it == 0) 1 else it }; recalcMbc1() }
                a < 0x6000 -> { altMbc1 = v and 3; recalcMbc1() }
                else -> { modeMbc1 = v and 1; recalcMbc1() }
            }
            2 -> if (a < 0x4000 && (a and 0x100) != 0) banc = (v and 0x0F).let { if (it == 0) 1 else it }
            3 -> when { a in 0x2000..0x3FFF -> banc = (v and 0x7F).let { if (it == 0) 1 else it }; a in 0x4000..0x5FFF -> { val x = v and 0x0F; if (x <= 3) { bancRam = x; rtc = 0 } else rtc = if (x in 8..0x0C && ambRtc) 1 else 2 } }
            5 -> when { a in 0x2000..0x2FFF -> banc = (banc and 0x100) or v; a in 0x3000..0x3FFF -> banc = (banc and 0xFF) or ((v and 1) shl 8); a in 0x4000..0x5FFF -> bancRam = v and (if (vibra) 7 else 0x0F) }
            else -> {}
        }
    }
    /** MBC3: 0 = banc de RAM, 1 = registre del rellotge (es llegeix 0), 2 = res (es llegeix $FF, com a PyBoy). */
    private var rtc = 0
    private val ambRtc = rom.size >= 0x150 && (rom[0x147].toInt() and 255) in 0x0F..0x10
    val wram = ByteArray(0x8000); val hram = ByteArray(0x80); val io = ByteArray(0x80); val vram = ByteArray(0x2000); var xram = ByteArray(0x2000); private set
    val oam = ByteArray(0xA0)
    /** Banc de ROM a $4000-$7FFF. En canviar-lo es calcula `offBanc` (abans es feia `banc % nBancs` a cada lectura). */
    var banc = 1
        set(v) { field = v; offBanc = (v % nBancs) * 0x4000 - 0x4000 }
    /** MBC1 amb ROM de més de 512 KB (banc de 6 bits: $2000-$3FFF = 5 bits baixos, $4000-$5FFF = 2 bits alts, mode simple). Només l'activen els drivers que ho demanen (YGODM). */
    var mbc1Alt = false
    /** Banc de WRAM de la Game Boy Color ($FF70; 1-7 a $D000-$DFFF; el 0 és com l'1). Els bancs 0 i 1 són als 8 KB de dalt de `wram` com sempre. */
    private var svbk = 1
    private fun wi(a: Int): Int { val o = (a and 0x1FFF) or 0xC000 and 0xFFFF; return if (o < 0xD000) o - 0xC000 else svbk * 0x1000 + (o - 0xD000) }
    private val nBancs = maxOf(2, (rom.size + 0x3FFF) / 0x4000)
    init { if ((tipusMbc == 1 || tipusMbc == 3) && nBancs > 32) posaReal() }
    /** Desplaçament a `rom` d'una adreça $4000-$7FFF amb el banc actual: o = offBanc + adreça. */
    private var offBanc = (1 % nBancs) * 0x4000 - 0x4000
    /** Bancs ja trobats per signatura (`r2gBanc`): la ROM no canvia, la cerca es fa un sol cop per memòria (abans, a cada fotograma). */
    internal val cauBancs = java.util.IdentityHashMap<Any, Int>(4)
    /** Estat propi d'un driver lligat a aquesta memòria (p. ex. `EstatCanalsGb`): els drivers són objectes compartits entre fils (so i anàlisi). */
    var extra: Any? = null
    /** Escriptura a un registre de so ($FF10-$FF3F). */
    var alRegistre: ((Int, Int) -> Unit)? = null
    /** Cicles de rellotge (4,194304 MHz) executats per `GbCpu`: el moment de cada escriptura al so dins del fotograma (`SinteGbCpu`). */
    var cicles = 0L

    /** Per a l'analitzador de ROM: lectura personalitzada dels registres d'E/S ($FF00-$FF7F; retorna -1 per deixar la per defecte) i espia d'accessos a la RAM ($C000 i més: esCriptura, adreça, valor). */
    var alLlegir: ((Int) -> Int)? = null
    var espia: ((Boolean, Int, Int) -> Unit)? = null
        set(v) { field = v; ganxos = v != null || alRom != null }
    /** Per a l'analitzador: lectura de ROM ($0000-$7FFF), per saber d'on surt un valor que s'escriu a l'APU. */
    var alRom: ((Int) -> Unit)? = null
        set(v) { field = v; ganxos = v != null || espia != null }
    /** Hi ha `espia` o `alRom` (només l'analitzador): si no, `rb` va directament a `rb0`. */
    private var ganxos = false
    /** Registres de banc (banc de ROM i banc de WRAM) per tornar-los a posar: l'analitzador cridava rutines amb el banc que hi havia en la crida original. */
    fun copiaBancs(): IntArray = intArrayOf(banc, svbk)
    fun posaBancs(b: IntArray) { posaBancs(b[0], b[1]) }
    /** Sense crear cap array (els drivers automàtics ho fan a cada fotograma, al fil d'àudio); amb el mapper real (MBC1) recalcula els bancs. */
    fun posaBancs(b: Int, s: Int) { banc = b; svbk = s
        if (real && tipusMbc == 1) { val sh = if (multicart) 4 else 5; baixMbc1 = (banc and ((1 shl sh) - 1)).let { if (it == 0 && !multicart) 1 else it }; altMbc1 = (banc shr sh) and 3
            // multicart: cada joc corre en mode 1 amb els bits alts = número de joc ($0000-$3FFF és l'inici del seu bloc de 256 KB); el banc desat no porta el mode
            if (multicart && altMbc1 > 0) modeMbc1 = 1; recalcMbc1() } }

    fun rb(a: Int): Int = if (!ganxos) rb0(a) else rbGanxos(a)
    private fun rbGanxos(a: Int): Int = if (espia != null && a >= 0xC000) { val v = rb0(a); espia!!(false, a, v); v } else { if (a < 0x8000) alRom?.invoke(a); rb0(a) }
    fun rb0(a: Int): Int = when {
        a < 0x4000 -> { val o = off0 + a; if (o < rom.size) rom[o].toInt() and 255 else 0xFF }
        a < 0x8000 -> { val o = offBanc + a; if (o < rom.size) rom[o].toInt() and 255 else 0xFF }
        a < 0xA000 -> vram[a - 0x8000].toInt() and 255
        a < 0xC000 -> if (!real) xram[a - 0xA000].toInt() and 255 else if (tipusMbc == 2) (xram[(a - 0xA000) and 0x1FF].toInt() and 0x0F) or 0xF0 else if (rtc == 1) 0 else if (rtc == 2) 0xFF else xram[offRam + a - 0xA000].toInt() and 255
        a < 0xFE00 -> wram[wi(a)].toInt() and 255
        a < 0xFEA0 -> oam[a - 0xFE00].toInt() and 255
        a < 0xFF00 -> 0xFF
        a < 0xFF80 -> if (alLlegir != null && alLlegir!!(a) >= 0) alLlegir!!(a) else if (a == 0xFF26) (io[0x26].toInt() and 255) or 0x70 else if (a == 0xFF44) { ly = (ly + 1) % 154; ly } else io[a - 0xFF00].toInt() and 255
        else -> if (a == 0xFFFF) ie else hram[a - 0xFF80].toInt() and 255
    }
    var ie = 0
    private var ly = 0
    /** Sortida pel port sèrie ($FF02 = $81): només per provar la CPU amb les ROMs de prova. */
    var alSerie: ((Int) -> Unit)? = null
    fun wb(a: Int, v0: Int) {
        val v = v0 and 255
        if (espia != null && a >= 0xC000) espia!!(true, a, v)
        when {
            real && a < 0x8000 -> wbMapper(a, v)
            a < 0x2000 -> {}
            a < 0x4000 && mbc1Alt -> banc = (banc and 0x60) or (if (v and 0x1F == 0) 1 else v and 0x1F)
            a in 0x4000..0x5FFF && mbc1Alt -> banc = (banc and 0x1F) or ((v and 3) shl 5)
            a < 0x4000 -> if (mbc5) { if (a < 0x3000) banc = (banc and 0x100) or v else banc = (banc and 0xFF) or ((v and 1) shl 8) } else { banc = v and 0x1F; if (banc == 0) banc = 1 }
            a < 0x8000 -> {}
            a < 0xA000 -> vram[a - 0x8000] = v.toByte()
            a < 0xC000 -> if (!real) xram[a - 0xA000] = v.toByte() else if (tipusMbc == 2) xram[(a - 0xA000) and 0x1FF] = (v and 0x0F).toByte() else if (rtc == 0) xram[offRam + a - 0xA000] = v.toByte()
            a < 0xFE00 -> wram[wi(a)] = v.toByte()
            a < 0xFEA0 -> oam[a - 0xFE00] = v.toByte()
            a < 0xFF00 -> {}
            a < 0xFF80 -> { io[a - 0xFF00] = v.toByte(); if (a == 0xFF70) svbk = if (v and 7 == 0) 1 else v and 7; if (a in 0xFF10..0xFF3F) alRegistre?.invoke(a, v) else if (a == 0xFF02 && v == 0x81) alSerie?.invoke(io[1].toInt() and 255) }
            a == 0xFFFF -> ie = v
            else -> hram[a - 0xFF80] = v.toByte()
        }
    }
}

class GbCpu(val m: GbMem) {
    var a = 0; var b = 0; var c = 0; var d = 0; var e = 0; var h = 0; var l = 0
    var fz = false; var fn = false; var fh = false; var fc = false
    var sp = 0xFFFE; var pc = 0x0100
    var instruccions = 0L

    private fun rb(x: Int) = m.rb(x)
    private fun wb(x: Int, v: Int) = m.wb(x, v)
    private fun n8(): Int { val v = rb(pc); pc = (pc + 1) and 0xFFFF; return v }
    private fun n16(): Int { val lo = n8(); return lo or (n8() shl 8) }
    private fun push(v: Int) { sp = (sp - 1) and 0xFFFF; wb(sp, v shr 8); sp = (sp - 1) and 0xFFFF; wb(sp, v and 255) }
    private fun pop(): Int { val lo = rb(sp); sp = (sp + 1) and 0xFFFF; val hi = rb(sp); sp = (sp + 1) and 0xFFFF; return lo or (hi shl 8) }
    private var af: Int
        get() = (a shl 8) or (if (fz) 0x80 else 0) or (if (fn) 0x40 else 0) or (if (fh) 0x20 else 0) or (if (fc) 0x10 else 0)
        set(v) { a = v shr 8; fz = v and 0x80 != 0; fn = v and 0x40 != 0; fh = v and 0x20 != 0; fc = v and 0x10 != 0 }
    private var bc: Int get() = (b shl 8) or c; set(v) { b = v shr 8; c = v and 255 }
    private var de: Int get() = (d shl 8) or e; set(v) { d = v shr 8; e = v and 255 }
    var hl: Int get() = (h shl 8) or l; set(v) { h = v shr 8; l = v and 255 }

    private fun getR(i: Int): Int = when (i) { 0 -> b; 1 -> c; 2 -> d; 3 -> e; 4 -> h; 5 -> l; 6 -> rb(hl); else -> a }
    private fun setR(i: Int, v: Int) { when (i) { 0 -> b = v; 1 -> c = v; 2 -> d = v; 3 -> e = v; 4 -> h = v; 5 -> l = v; 6 -> wb(hl, v); else -> a = v } }
    private fun getRR(i: Int) = when (i) { 0 -> bc; 1 -> de; 2 -> hl; else -> sp }
    private fun setRR(i: Int, v: Int) { when (i) { 0 -> bc = v; 1 -> de = v; 2 -> hl = v; else -> sp = v } }
    private fun cond(i: Int) = when (i) { 0 -> !fz; 1 -> fz; 2 -> !fc; else -> fc }

    private fun alu(op: Int, v: Int) {
        when (op) {
            0 -> { val r = a + v; fh = (a and 15) + (v and 15) > 15; fc = r > 255; a = r and 255; fz = a == 0; fn = false }
            1 -> { val ci = if (fc) 1 else 0; val r = a + v + ci; fh = (a and 15) + (v and 15) + ci > 15; fc = r > 255; a = r and 255; fz = a == 0; fn = false }
            2 -> { val r = a - v; fh = (a and 15) < (v and 15); fc = a < v; a = r and 255; fz = a == 0; fn = true }
            3 -> { val ci = if (fc) 1 else 0; val r = a - v - ci; fh = (a and 15) < (v and 15) + ci; fc = a < v + ci; a = r and 255; fz = a == 0; fn = true }
            4 -> { a = a and v; fz = a == 0; fn = false; fh = true; fc = false }
            5 -> { a = a xor v; fz = a == 0; fn = false; fh = false; fc = false }
            6 -> { a = a or v; fz = a == 0; fn = false; fh = false; fc = false }
            else -> { fh = (a and 15) < (v and 15); fc = a < v; fz = a == v; fn = true }
        }
    }

    private fun cb(op: Int) {
        val r = op and 7; val k = (op shr 3) and 7
        val v = getR(r)
        when (op shr 6) {
            0 -> {
                val res: Int
                when (k) {
                    0 -> { fc = v and 0x80 != 0; res = ((v shl 1) or (v shr 7)) and 255 }
                    1 -> { fc = v and 1 != 0; res = ((v shr 1) or (v shl 7)) and 255 }
                    2 -> { val ci = if (fc) 1 else 0; fc = v and 0x80 != 0; res = ((v shl 1) or ci) and 255 }
                    3 -> { val ci = if (fc) 0x80 else 0; fc = v and 1 != 0; res = (v shr 1) or ci }
                    4 -> { fc = v and 0x80 != 0; res = (v shl 1) and 255 }
                    5 -> { fc = v and 1 != 0; res = (v shr 1) or (v and 0x80) }
                    6 -> { fc = false; res = ((v shl 4) or (v shr 4)) and 255 }
                    else -> { fc = v and 1 != 0; res = v shr 1 }
                }
                fz = res == 0; fn = false; fh = false
                setR(r, res)
            }
            1 -> { fz = v and (1 shl k) == 0; fn = false; fh = true }
            2 -> setR(r, v and (1 shl k).inv() and 255)
            else -> setR(r, v or (1 shl k))
        }
    }

    /** Executa una instrucció. */
    fun step() {
        instruccions++
        val op = n8()
        // Cicles de la instrucció (M-cicles x 4) abans d'executar-la: l'escriptura de la instrucció queda marcada al final, l'error és com a molt d'una instrucció
        m.cicles += CICLES[op].code - 48 shl 2
        if (op == 0xCB) { val q = rb(pc); m.cicles += (if (q and 7 != 6) 1 else if (q in 0x40..0x7F) 2 else 3) shl 2 }       // prefix (1) + 2, 3 o 4 en total
        else if (op and 0xE7 == 0xC0 || op and 0xE7 == 0xC4) { if (cond((op shr 3) and 3)) m.cicles += 12 }                    // RET cc i CALL cc presos: +3
        else if (op and 0xE7 == 0x20 || op and 0xE7 == 0xC2) { if (cond((op shr 3) and 3)) m.cicles += 4 }                     // JR cc i JP cc presos: +1
        when {
            op in 0x40..0x7F -> { if (op == 0x76) return; setR((op shr 3) and 7, getR(op and 7)) }
            op in 0x80..0xBF -> alu((op shr 3) and 7, getR(op and 7))
            else -> when (op) {
                0x00, 0x10 -> { if (op == 0x10) n8() }
                0x01, 0x11, 0x21, 0x31 -> setRR(op shr 4, n16())
                0x02 -> wb(bc, a); 0x12 -> wb(de, a)
                0x22 -> { wb(hl, a); hl = (hl + 1) and 0xFFFF }
                0x32 -> { wb(hl, a); hl = (hl - 1) and 0xFFFF }
                0x0A -> a = rb(bc); 0x1A -> a = rb(de)
                0x2A -> { a = rb(hl); hl = (hl + 1) and 0xFFFF }
                0x3A -> { a = rb(hl); hl = (hl - 1) and 0xFFFF }
                0x03, 0x13, 0x23, 0x33 -> { val i = op shr 4; setRR(i, (getRR(i) + 1) and 0xFFFF) }
                0x0B, 0x1B, 0x2B, 0x3B -> { val i = op shr 4; setRR(i, (getRR(i) - 1) and 0xFFFF) }
                0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x34, 0x3C -> { val i = (op shr 3) and 7; val v = getR(i); val r = (v + 1) and 255; fh = v and 15 == 15; fn = false; fz = r == 0; setR(i, r) }
                0x05, 0x0D, 0x15, 0x1D, 0x25, 0x2D, 0x35, 0x3D -> { val i = (op shr 3) and 7; val v = getR(i); val r = (v - 1) and 255; fh = v and 15 == 0; fn = true; fz = r == 0; setR(i, r) }
                0x06, 0x0E, 0x16, 0x1E, 0x26, 0x2E, 0x36, 0x3E -> setR((op shr 3) and 7, n8())
                0x07 -> { fc = a and 0x80 != 0; a = ((a shl 1) or (a shr 7)) and 255; fz = false; fn = false; fh = false }
                0x0F -> { fc = a and 1 != 0; a = ((a shr 1) or (a shl 7)) and 255; fz = false; fn = false; fh = false }
                0x17 -> { val ci = if (fc) 1 else 0; fc = a and 0x80 != 0; a = ((a shl 1) or ci) and 255; fz = false; fn = false; fh = false }
                0x1F -> { val ci = if (fc) 0x80 else 0; fc = a and 1 != 0; a = (a shr 1) or ci; fz = false; fn = false; fh = false }
                0x08 -> { val ad = n16(); wb(ad, sp and 255); wb((ad + 1) and 0xFFFF, sp shr 8) }
                0x09, 0x19, 0x29, 0x39 -> { val v = getRR(op shr 4); val r = hl + v; fh = (hl and 0xFFF) + (v and 0xFFF) > 0xFFF; fc = r > 0xFFFF; fn = false; hl = r and 0xFFFF }
                0x18 -> { val e8 = n8().toByte().toInt(); pc = (pc + e8) and 0xFFFF }
                0x20, 0x28, 0x30, 0x38 -> { val e8 = n8().toByte().toInt(); if (cond((op shr 3) and 3)) pc = (pc + e8) and 0xFFFF }
                0x27 -> {
                    var x = a
                    if (!fn) { if (fc || x > 0x99) { x += 0x60; fc = true }; if (fh || (x and 15) > 9) x += 6 }
                    else { if (fc) x -= 0x60; if (fh) x -= 6 }
                    a = x and 255; fz = a == 0; fh = false
                }
                0x2F -> { a = a xor 255; fn = true; fh = true }
                0x37 -> { fc = true; fn = false; fh = false }
                0x3F -> { fc = !fc; fn = false; fh = false }
                0xC0, 0xC8, 0xD0, 0xD8 -> if (cond((op shr 3) and 3)) pc = pop()
                0xC1 -> bc = pop(); 0xD1 -> de = pop(); 0xE1 -> hl = pop(); 0xF1 -> af = pop() and 0xFFF0
                0xC5 -> push(bc); 0xD5 -> push(de); 0xE5 -> push(hl); 0xF5 -> push(af)
                0xC2, 0xCA, 0xD2, 0xDA -> { val ad = n16(); if (cond((op shr 3) and 3)) pc = ad }
                0xC3 -> pc = n16()
                0xE9 -> pc = hl
                0xC4, 0xCC, 0xD4, 0xDC -> { val ad = n16(); if (cond((op shr 3) and 3)) { push(pc); pc = ad } }
                0xCD -> { val ad = n16(); push(pc); pc = ad }
                0xC6, 0xCE, 0xD6, 0xDE, 0xE6, 0xEE, 0xF6, 0xFE -> alu((op shr 3) and 7, n8())
                0xC7, 0xCF, 0xD7, 0xDF, 0xE7, 0xEF, 0xF7, 0xFF -> { push(pc); pc = op and 0x38 }
                0xC9, 0xD9 -> pc = pop()
                0xCB -> cb(n8())
                0xE0 -> wb(0xFF00 + n8(), a)
                0xF0 -> a = rb(0xFF00 + n8())
                0xE2 -> wb(0xFF00 + c, a)
                0xF2 -> a = rb(0xFF00 + c)
                0xE8 -> { val e8 = n8().toByte().toInt(); val r = sp + e8; fh = (sp and 15) + (e8 and 15) > 15; fc = (sp and 255) + (e8 and 255) > 255; fz = false; fn = false; sp = r and 0xFFFF }
                0xF8 -> { val e8 = n8().toByte().toInt(); val r = sp + e8; fh = (sp and 15) + (e8 and 15) > 15; fc = (sp and 255) + (e8 and 255) > 255; fz = false; fn = false; hl = r and 0xFFFF }
                0xF9 -> sp = hl
                0xEA -> wb(n16(), a)
                0xFA -> a = rb(n16())
                else -> {}                                       // DI, EI i els codis no definits: no fan res
            }
        }
        pc = pc and 0xFFFF
    }

    /** Crida la rutina `adr` (amb el banc ja triat) i l'executa fins al seu `ret`; retorna false si passa el límit d'instruccions. */
    fun crida(adr: Int, limit: Int = 400000): Boolean {
        push(MAGIC); pc = adr
        var n = 0
        while (pc != MAGIC) { step(); if (++n > limit) { instruccions += n; limits++; return false } }
        instruccions += n
        return true
    }
    /** Crides que han arribat al límit d'instruccions (el codi esperava alguna cosa que no s'emula): diagnòstic, no canvia el so. */
    var limits = 0
    /**
     * Com `crida`, però per a l'analitzador (2026-11-22): diu com ha anat. 0 = ha tornat; 1 = límit d'instruccions; 2 = 256 o més NOP seguits (salta a memòria a zero:
     * Battle Crusher); 3 = RST $38 sobre un $FF a $0038 (bucle que escriu la pila per tota la RAM: V-Rally); 4 = ha tornat, però amb més de `llarg` instruccions (bucle de retard).
     */
    /** Depuració de `cridaV`: on comença el tros de zeros (o el RST $38) i la instrucció d'abans (d'on salta). */
    var pcTrenc = 0; var srcTrenc = 0
    fun cridaV(adr: Int, limit: Int, llarg: Int = 100000): Int {
        push(MAGIC); pc = adr
        var n = 0; var nops = 0; var ant = adr
        while (pc != MAGIC) {
            val op = m.rb(pc)
            if (op == 0) { if (nops == 0) { pcTrenc = pc; srcTrenc = ant }; if (++nops >= 256) return 2 } else { nops = 0; if (op == 0xFF && pc == 0x38) { pcTrenc = pc; srcTrenc = ant; return 3 } }
            ant = pc
            step(); if (++n > limit) return 1
        }
        return if (n > llarg) 4 else 0
    }
    companion object {
        const val MAGIC = 0xFEEE
        /**
         * M-cicles de cada codi sense prefix (el mínim: la condició no presa), de https://gbdev.io/gb-opcodes/Opcodes.json (camp `cycles`, en T-cicles / 4).
         * Els codis no definits hi valen 1. CB: 2 amb registre, 4 amb (HL), 3 per a BIT n,(HL) (el mateix fitxer, `cbprefixed`).
         */
        private const val CICLES = "1322112152221121132211213222112123221121222211212322333122221121111111211111112111111121111111211111112111111121222222121111112111111121111111211111112111111121111111211111112111111121111111212334342424313624233134242431312433211424414111243321142432411124"
    }
}
