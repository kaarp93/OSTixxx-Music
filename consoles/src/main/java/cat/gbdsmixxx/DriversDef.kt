package cat.gbdsmixxx

/**
 * Definicions de drivers guiats per dades com a DADES pures (sense CPU ni Android): el que llegeix `DriversDef.parseText` (línies `AUTO_POKE`, `AUTO_GB` i `AUTO_NES` d'un report de
 * l'analitzador) i el que desa/llegeix `DriversBin` (paquet binari amb índex per CRC). `DriversText.construeix` les converteix en drivers executables. Vegeu `INTERFICIE_DRIVERS.md`.
 */

/** Una rutina que el driver crida. GB: `bs` = [banc de ROM, banc de WRAM] i `v` = els 7 registres a b c d e h l. NES: `bs` = els 15 registres de banc i `v` = [a, x, y]. */
class RutDef(val dest: Int, val bs: IntArray, val v: IntArray) {
    fun aGb() = RutinaGb(dest, bs[0], bs[1], v, if (bs.size > 2) bs[2] else 0xFFFE)
    fun aNes() = RutinaNes(dest, bs, v[0], v[1], v[2])
    companion object {
        fun deGb(r: RutinaGb) = RutDef(r.dest, if (r.sp != 0xFFFE) intArrayOf(r.banc, r.svbk, r.sp) else intArrayOf(r.banc, r.svbk), r.regs.copyOf())
        fun deNes(r: RutinaNes) = RutDef(r.dest, r.bs.copyOf(), intArrayOf(r.a, r.x, r.y))
    }
}

/** Un so descobert per l'analitzador: `codi` (valor que es demana), `dur` en segons (0 = bucle) i `nc` = nombre de canals (-1 = no se sap). Només serveix per fer-ne el nom. */
class SoDef(val codi: Int, val dur: Int, val nc: Int, private val durTxt: String = dur.toString()) {
    /** El nom que surt a la llista de l'àlbum (idèntic al de `DriversText` d'abans). */
    val nom: String get() = "So $" + "%02X".format(codi) + " · " + (if (dur == 0) "bucle" else "$durTxt s") + (if (nc > 0) " · ${nc}c" else "")
}

/**
 * Un driver guiat per dades. `format`: 1 = GB per escriptures a la RAM (`AUTO_POKE`), 2 = GB per codis (`AUTO_GB`), 3 = NES per codis (`AUTO_NES`).
 * `origen`: AUTO (report de l'analitzador) o EXP (experimental compilat convertit al paquet). Els `noms` de les cançons no entren a la revisió (`rev`): canviar-los no esborra les marques.
 */
sealed class DefDriver {
    abstract val crc: Long
    abstract val titol: String
    abstract val format: Int
    abstract val cgb: Boolean
    abstract val noms: List<String>
    abstract val origen: TipusDriver
    /** «AAAAAAAA.poke», «AAAAAAAA.gb» o «AAAAAAAA.nes»: la `clau` de l'identificador (`DriverRef.id`). */
    val clau: String get() = DriversDef.clau(crc, format)
    val sistema: SistemaDriver get() = if (format == FORMAT_NES) SistemaDriver.NES else SistemaDriver.GB
    /** l'autor l'havia donat per bo abans de les marques per contingut (`provat = true` antic); només per migrar-lo un cop a les marques. No entra a la `rev`. */
    var confirmat: Boolean = false
    companion object {
        const val FORMAT_POKE = 1
        const val FORMAT_GB = 2
        const val FORMAT_NES = 3
    }
}

class DefGbPoke(override val crc: Long, override val titol: String, override val cgb: Boolean, val mbc5: Boolean, val f: RutDef, val base: IntArray, val songs: List<IntArray>, val start: RutDef?,
                override val noms: List<String> = songs.indices.map { "Cançó ${it + 1}" }, override val origen: TipusDriver = TipusDriver.AUTO) : DefDriver() {
    override val format get() = FORMAT_POKE
}

class DefGbAuto(override val crc: Long, override val titol: String, override val cgb: Boolean, val mbc5: Boolean, val f: RutDef, val ini: RutDef?, val p: RutDef?, val reg: Int, val ram: Int, val codis: IntArray,
                override val noms: List<String>, override val origen: TipusDriver = TipusDriver.AUTO) : DefDriver() {
    override val format get() = FORMAT_GB
}

class DefNesAuto(override val crc: Long, override val titol: String, val f: RutDef, val ini: RutDef?, val p: RutDef?, val codis: IntArray, override val noms: List<String>, val ram: Int, val extra: IntArray,
                 override val origen: TipusDriver = TipusDriver.AUTO) : DefDriver() {
    override val format get() = FORMAT_NES
    override val cgb get() = false
}

object DriversDef {
    private fun hex(s: String) = s.toInt(16)
    fun clau(crc: Long, format: Int) = "%08X".format(crc) + "." + when (format) { DefDriver.FORMAT_POKE -> "poke"; DefDriver.FORMAT_GB -> "gb"; else -> "nes" }

    fun titol(fitxer: String): String {
        var t = fitxer.replace(Regex("\\.(nes|gbc?|sfc|smc)$", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]"), "").trim(' ', '-')
        val m = Regex("^(.*), (The|A|An)$").find(t)
        return if (m != null) m.groupValues[2] + " " + m.groupValues[1] else t
    }
    private fun camps(l: String): Map<String, String> = Regex("(\\w+)=(\\S+)").findAll(l.substringBefore(" | ")).associate { it.groupValues[1] to it.groupValues[2] }

    private fun rutinaNes(s: String, amb: Boolean): RutDef? {
        if (s == "-") return null
        val p = s.split(":")
        val bs = p[1].split(",").map { hex(it) }.toIntArray()
        return if (amb) RutDef(hex(p[0]), bs, intArrayOf(hex(p[2]), hex(p[3]), hex(p[4]))) else RutDef(hex(p[0]), bs, intArrayOf(0, hex(p[2]), hex(p[3])))
    }
    private fun rutinaGb(s: String): RutDef? {
        if (s == "-") return null
        val p = s.split(":"); val b = p[1].split(",")
        val regs = IntArray(7) { hex(p[2].substring(2 * it, 2 * it + 2)) }
        // tercer camp opcional `S` + SP en hexadecimal (2026-11-22): la pila del joc; els lectors d'abans l'ignoren
        val sp = b.getOrNull(2)?.takeIf { it.startsWith("S") }?.let { hex(it.substring(1)) }
        return RutDef(hex(p[0]), if (sp != null) intArrayOf(b[0].toInt(), b[1].toInt(), sp) else intArrayOf(b[0].toInt(), b[1].toInt()), regs)
    }
    /** `XX:L` = bucle, `XX:Ns` (N >= 2) = dura N s, opcionalment `XX:L:nc`. Els sons de menys de 2 s es descarten (són efectes). */
    private fun sos(txt: String): List<SoDef> {
        val out = ArrayList<SoDef>()
        for (e in txt.trim().split(Regex("\\s+"))) {
            val p = e.split(":"); if (p.size < 2) continue
            val c = hex(p[0]); val k = p[1]; val nc = p.getOrNull(2)?.toIntOrNull() ?: -1
            if (k == "L") out.add(SoDef(c, 0, nc))
            else if (k.endsWith("s")) { val n = k.dropLast(1).toIntOrNull(); if (n != null && n >= 2) out.add(SoDef(c, n, nc, k.dropLast(1))) }
        }
        return out
    }
    /** Pokes: W/H/X<idx>=VV; `#n` = crida l'arrencada amb A = n; `#n@OFFSET_ROM+LONGITUD` (hex) copia abans la partitura de la ROM a $A000. Els sentinelles -1/-2/-3 són els de `DriverGbPoke`. */
    private fun pokes(s: String): IntArray {
        val r = ArrayList<Int>()
        for (e in s.split("/")) {
            if (e.isEmpty()) continue
            if (e[0] == '#') {
                val t = e.substring(1)
                if ('@' in t) { val c = t.substringAfter('@'); r.add(-2); r.add(hex(c.substringBefore('+'))); r.add(-3); r.add(hex(c.substringAfter('+'))) }
                r.add(-1); r.add(t.substringBefore('@').toInt()); continue
            }
            val ad = e.substringBefore("="); val v = hex(e.substringAfter("="))
            r.add(if (ad[0] == 'X') 0x20000 + hex(ad.substring(1)) else if (ad[0] == 'H') 0x10000 + hex(ad.substring(1)) else if (ad[0] == 'W') hex(ad.substring(1)) else hex(ad)); r.add(v)
        }
        return r.toIntArray()
    }

    /** Text d'un fitxer de drivers o d'un report: UTF-16 si porta BOM; si no, UTF-8 sense els bytes nuls (com `eines/nes_auto.py`). */
    fun text(b: ByteArray): String =
        if (b.size >= 2 && ((b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) || (b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()))) String(b, Charsets.UTF_16)
        else String(b.filter { it != 0.toByte() }.toByteArray(), Charsets.UTF_8).removePrefix("﻿")

    /**
     * Llegeix un fitxer de drivers o un report sencer de l'analitzador: només les capçaleres `== fitxer ==` i les línies que comencen per `AUTO_POKE`, `AUTO_GB` o `AUTO_NES`; la resta s'ignora.
     * Les línies que no es poden llegir es deixen i `errors` (si es dona) en rep una per línia amb el motiu. `origen` = AUTO (reports) o EXP (font dels experimentals).
     */
    fun parseText(text: String, errors: MutableList<String>? = null, origen: TipusDriver = TipusDriver.AUTO): List<DefDriver> {
        val out = ArrayList<DefDriver>(); var fitxer = ""
        for (linia in text.lines()) {
            val l = linia.trim()
            if (l.startsWith("== ") && l.endsWith(" ==")) { fitxer = l.substring(3, l.length - 3); continue }
            try {
                if (l.startsWith("AUTO_POKE ")) {
                    val c = camps(l); val crc = c["crc"]!!.toLong(16)
                    val f = rutinaGb(c["f"]!!)!!
                    val base = pokes(l.substringAfter("base=").substringBefore(" | ").trim().split(" ").filter { it.isNotEmpty() }.joinToString("/"))
                    val songs = l.substringAfter("songs=", "").trim().split(" ").filter { it.isNotEmpty() }.map { pokes(it) }
                    if (songs.isEmpty()) { errors?.add("$fitxer: AUTO_POKE sense cançons"); continue }
                    out.add(DefGbPoke(crc, titol(fitxer), c["cgb"] == "1" || fitxer.endsWith(".gbc", ignoreCase = true), c["mbc5"] == "1", f, base, songs, c["start"]?.let { rutinaGb(it) }, origen = origen))
                } else if (l.startsWith("AUTO_GB ")) {
                    val c = camps(l); if (c["ram"]?.contains("+") == true) { errors?.add("$fitxer: AUTO_GB amb ram composta (no suportat)"); continue }
                    val crc = c["crc"]!!.toLong(16); val cs = sos(l.substringAfter("codis=")); if (cs.size < 2) { errors?.add("$fitxer: AUTO_GB amb menys de 2 sons"); continue }
                    val ram = c["ram"] ?: "-"
                    out.add(DefGbAuto(crc, titol(fitxer), c["cgb"] == "1" || fitxer.endsWith(".gbc", ignoreCase = true), c["mbc5"] == "1", rutinaGb(c["f"]!!)!!, rutinaGb(c["ini"] ?: "-"), rutinaGb(c["p"] ?: "-"),
                        "ABCDEL".indexOf((c["reg"] ?: "A")[0]).coerceAtLeast(0), if (ram == "-") -1 else hex(ram), cs.map { it.codi }.toIntArray(), cs.map { it.nom }, origen))
                } else if (l.startsWith("AUTO_NES ")) {
                    val c = camps(l); val crc = c["crc"]!!.toLong(16); val cs = sos(l.substringAfter("codis=")); if (cs.size < 2) { errors?.add("$fitxer: AUTO_NES amb menys de 2 sons"); continue }
                    val ram = c["ram"] ?: "-"; val rp = ram.split("+")
                    val extra = ArrayList<Int>(); for (x in rp.drop(1)) { val q = x.split(":"); extra.add(hex(q[0])); extra.add(hex(q[1])) }
                    out.add(DefNesAuto(crc, titol(fitxer), rutinaNes(c["f"]!!.let { it.substringBefore(":") + ":" + it.substringAfter(":") + ":0:0:0" }, true)!!, rutinaNes(c["ini"] ?: "-", true), rutinaNes(c["p"] ?: "-", false),
                        cs.map { it.codi }.toIntArray(), cs.map { it.nom }, if (rp[0] == "-") -1 else hex(rp[0]), extra.toIntArray(), origen))
                }
            } catch (e: Throwable) { errors?.add("$fitxer: " + l.take(40) + "… " + e.javaClass.simpleName) }
        }
        return out
    }

    // ---- drivers compilats -> definicions (per passar els experimentals automàtics al paquet; eines/drivers_paquet) ----
    fun de(d: DriverGbAuto, origen: TipusDriver, cgb: Boolean): DefGbAuto = DefGbAuto(d.crc, d.titol, cgb, d.mbc5Cart, RutDef.deGb(d.f), d.ini?.let { RutDef.deGb(it) }, d.p?.let { RutDef.deGb(it) }, d.reg, d.ramAd, d.codis.copyOf(), d.noms, origen)
    fun de(d: DriverGbPoke, origen: TipusDriver, cgb: Boolean): DefGbPoke = DefGbPoke(d.crc, d.titol, cgb, d.mbc5Cart, RutDef.deGb(d.f), d.base.copyOf(), d.cancons.map { it.copyOf() }, d.start?.let { RutDef.deGb(it) }, d.noms, origen)
    fun de(d: DriverNesAuto, origen: TipusDriver): DefNesAuto = DefNesAuto(d.crc, d.titol, RutDef.deNes(d.f), d.ini?.let { RutDef.deNes(it) }, d.p?.let { RutDef.deNes(it) }, d.codis.copyOf(), d.cancons, d.ramAd, d.ramExtra.copyOf(), origen)
}
