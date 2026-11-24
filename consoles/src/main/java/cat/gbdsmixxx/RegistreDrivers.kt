package cat.gbdsmixxx

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Registre únic de drivers de GB, NES i SNES (l'autor, 2026-11-16). Substitueix els getters `DRIVERS_GB/NES/SNES` que concatenaven a cada accés i `DriversExtra`.
 * - Fonts: fets a mà (CUSTOM, compilats a `:consoles`), experimentals compilats (EXP, els registra `RegistreExperimental`) i paquets binaris (`DriversBin`): el de l'app (AUTO de
 *   `drivers_builtin.txt` + EXP automàtics de NES) i el dels importats. Dels paquets només es té l'índex: cada driver es construeix la primera vegada que es demana (`carrega`).
 * - Fil-segur: l'estat és immutable i se substitueix sencer (escriptures sota `lock`, lectures sense bloqueig). Els drivers construïts es guarden a `cau` (ConcurrentHashMap).
 * - Cada driver té un `nom` intern únic dins del sistema: el d'abans per als que ja existien (les claus d'àlbum `GB_<nom>` no canvien); un AUTO_GB amb el mateix CRC que un
 *   AUTO_POKE es diu `AutoG_<CRC>`; una segona versió d'un mateix AUTO, `Auto_<CRC>_<rev>`; un EXP que xoca amb un AUTO, `<nom>_exp` (abans el de text substituïa l'experimental).
 * API per a la càrrega de la biblioteca: `candidatsPerCrc`, `reconeix`, `driversDeRom`, `carrega*`, `perId`, `refs`. Vegeu `INTERFICIE_DRIVERS.md`.
 */
object RegistreDrivers {
    /** Revisió dels drivers fets a mà (clau d'àlbum -> rev). Puja-la quan un canvi del driver hagi d'esborrar les marques de l'autor; per defecte «1». */
    val REVISIONS: Map<String, String> = emptyMap()
    /**
     * CRC32 de les ROM de cada driver fet a mà o experimental compilat (clau d'àlbum -> CRC; `CrcDrivers`). l'autor (2026-11-17): TOTS els drivers van per CRC. Un driver sense cap CRC
     * a la llista no s'activa mai (la signatura de bytes ja no activa res).
     */
    val CRCS_CONEGUTS: Map<String, Set<Long>> get() = CrcDrivers.PER_CLAU

    private class Reg(val ref: DriverRef, val obj: Any?, val pack: DriversBin.Pack?, val e: DriversBin.Entrada?, val confirmat: Boolean)
    private class Estat(val regs: List<Reg>, val versio: Int, val candidats: Set<String> = emptySet()) {
        val perId: Map<String, Reg> = regs.associateBy { it.ref.id }
        val perCrc: Map<Long, List<Reg>> = HashMap<Long, ArrayList<Reg>>().also { m -> for (r in regs) for (c in r.ref.crcs) m.getOrPut(c) { ArrayList() }.add(r) }
        val perClauAlbum: Map<String, Reg> = regs.associateBy { it.ref.clauAlbum }
    }
    private class Exp(val sistema: SistemaDriver, val obj: Any, val compat: JocCompat?)

    private val lock = Any()
    private val exps = ArrayList<Exp>()                  // sota lock
    private var packApp: DriversBin.Pack? = null         // sota lock
    private var packImp: DriversBin.Pack? = null         // sota lock
    private var packCand: DriversBin.Pack? = null        // sota lock: candidats de «Prova compatibilitat» (CandidatsDrivers), «Auto (sense validar)»
    @Volatile private var estat: Estat? = null
    private var versio = 0                               // sota lock
    private val cau = ConcurrentHashMap<String, Any>()
    @Volatile private var latch: CountDownLatch? = null
    /** Es crida (sense cap lock) cada cop que el registre canvia. */
    @Volatile var alCanviar: ((Int) -> Unit)? = null

    // ---------- fonts ----------
    /** Registra drivers experimentals compilats (`GbDriver`, `NesDriver` o `SnesDriver`) amb les seves entrades de compatibilitat (títol i sistema). */
    fun registraExp(sistema: SistemaDriver, drivers: List<Any>, compat: List<JocCompat>) {
        val perClau = compat.associateBy { it.clau }
        canvia { for (d in drivers) exps.add(Exp(sistema, d, perClau[sistema.prefix + nomObj(d)])) }
    }
    /** Avisa que l'índex dels paquets s'està carregant en un fil de fons: fins que arribi `posaPaquets`, les consultes l'esperen (com a molt 5 s). Sense aquesta crida (analitzador) no s'espera res. */
    fun preparaIndex() { synchronized(lock) { if (latch == null && packApp == null) latch = CountDownLatch(1) } }
    /** Posa els paquets (només índex) de l'app i dels importats. */
    fun posaPaquets(app: DriversBin.Pack?, importats: DriversBin.Pack?, candidats: DriversBin.Pack? = null) {
        canvia { packApp = app; packImp = importats; packCand = candidats; cau.clear() }
        latch?.countDown()
    }
    /** Substitueix el paquet d'importats (importació o esborrat). */
    fun posaImportats(importats: DriversBin.Pack?) = canvia { packImp = importats; cau.clear() }
    fun paquetImportats(): DriversBin.Pack? = synchronized(lock) { packImp }
    /** Substitueix el paquet de candidats (prova nova, confirmació o descart). */
    fun posaCandidats(candidats: DriversBin.Pack?) = canvia { packCand = candidats; cau.clear() }
    /** Confirmació d'un candidat: importats i candidats nous d'un sol cop (el driver no desapareix entremig). */
    fun posaImportatsICandidats(importats: DriversBin.Pack?, candidats: DriversBin.Pack?) = canvia { packImp = importats; packCand = candidats; cau.clear() }
    /** El driver és un candidat sense validar? (Un candidat idèntic a un importat ja no ho és: val l'importat.) */
    fun esCandidat(id: String): Boolean = id in e().candidats
    fun paquetApp(): DriversBin.Pack? = synchronized(lock) { packApp }

    private fun canvia(f: () -> Unit) {
        val v = synchronized(lock) { f(); versio++; estat = null; versio }          // l'estat es torna a fer quan algú el demana (no a cada registre)
        alCanviar?.invoke(v)
    }

    private fun esperaIndex() { val l = latch ?: return; if (l.count > 0) try { l.await(5, TimeUnit.SECONDS) } catch (e: InterruptedException) { } }
    private fun e(): Estat {
        esperaIndex()
        estat?.let { return it }
        synchronized(lock) { return estat ?: construeix(versio).also { estat = it } }
    }
    private val nomes: Boolean get() = DriversExtra.nomes
    private fun actiu(r: Reg) = !nomes || r.ref.tipus != TipusDriver.CUSTOM

    // ---------- construcció de l'estat (sota lock) ----------
    private fun nomObj(d: Any): String = when (d) { is GbDriver -> d.nom; is NesDriver -> d.nom; is SnesDriver -> d.nom; else -> d.toString() }
    private fun defDe(d: Any, sistema: String): DefDriver? = when (d) {
        is DriverGbAuto -> DriversDef.de(d, TipusDriver.EXP, sistema.contains("Color"))
        is DriverGbPoke -> DriversDef.de(d, TipusDriver.EXP, sistema.contains("Color"))
        is DriverNesAuto -> DriversDef.de(d, TipusDriver.EXP)
        else -> null
    }
    private fun subsistemaDe(s: SistemaDriver, cgb: Boolean) = when (s) { SistemaDriver.GB -> if (cgb) "Game Boy Color" else "Game Boy"; SistemaDriver.NES -> "NES"; SistemaDriver.SNES -> "SNES" }

    private fun construeix(v: Int): Estat {
        val regs = ArrayList<Reg>()
        val usats = HashMap<SistemaDriver, HashSet<String>>()
        fun reserva(s: SistemaDriver, base: String, sufix: String): String {
            val u = usats.getOrPut(s) { HashSet() }
            if (u.add(base)) return base
            var n = base + sufix; var k = 2
            while (!u.add(n)) n = base + sufix + "_" + k++
            return n
        }
        var ordre = 0
        val meta = METADADES_COMPAT.associateBy { it.clau }
        // 1. fets a mà
        for ((sis, llista) in listOf(SistemaDriver.GB to DRIVERS_GB_CUSTOM, SistemaDriver.NES to DRIVERS_NES_CUSTOM, SistemaDriver.SNES to DRIVERS_SNES_CUSTOM)) for (d in llista) {
            val nom0 = nomObj(d); val clauA = sis.prefix + nom0
            if (usats[sis]?.contains(nom0) == true) continue
            val nom = reserva(sis, nom0, "_custom"); val m = meta[clauA]
            val titol = m?.titol ?: (if (sis == SistemaDriver.SNES) TITOLS_SNES[nom0] else null) ?: nom0
            val ref = DriverRef(sis, TipusDriver.CUSTOM, nom0, REVISIONS[clauA] ?: "1", nom, titol, m?.sistema ?: subsistemaDe(sis, false), CRCS_CONEGUTS[clauA] ?: emptySet(), ordre++)
            regs.add(Reg(ref, d, null, null, m?.confirmatAbans == true))
        }
        // 2. automàtics dels paquets (primer els de l'app; un importat idèntic a un de l'app no es repeteix)
        val packs = listOfNotNull(packApp, packImp, packCand)
        val cands = HashSet<String>()
        val autos = packs.flatMap { p -> p.entrades.filter { it.origen == TipusDriver.AUTO }.map { it to p } }
        val ambPoke = autos.filter { it.first.format == DefDriver.FORMAT_POKE }.map { it.first.crc }.toSet()
        val vistos = HashSet<String>()
        fun dePack(e: DriversBin.Entrada, p: DriversBin.Pack, sufixXoc: String) {
            if (!vistos.add(e.origen.codi + ":" + e.clau + ":" + e.revHex)) return
            val sis = if (e.format == DefDriver.FORMAT_NES) SistemaDriver.NES else SistemaDriver.GB
            val base = (if (e.format == DefDriver.FORMAT_GB && e.crc in ambPoke) "AutoG_" else "Auto_") + "%08X".format(e.crc)
            val nom = reserva(sis, base, if (e.origen == TipusDriver.AUTO) "_" + e.revHex else sufixXoc)
            val ref = DriverRef(sis, e.origen, e.clau, e.revHex, nom, e.titol, subsistemaDe(sis, e.cgb), setOf(e.crc), ordre++)
            regs.add(Reg(ref, null, p, e, e.confirmat))
            if (p === packCand) cands.add(ref.id)
        }
        for ((en, p) in autos) dePack(en, p, "")
        // 3. experimentals compilats
        for (x in exps) {
            val nom0 = nomObj(x.obj); val c = x.compat
            val sub = c?.sistema ?: subsistemaDe(x.sistema, false)
            val def = defDe(x.obj, sub)
            val nom = reserva(x.sistema, nom0, "_exp")
            val obj: Any = if (def != null && nom != nom0) DriversText.construeix(def, nom) else x.obj
            val crcs: Set<Long> = if (def != null) setOf(def.crc) else CRCS_CONEGUTS[x.sistema.prefix + nom0] ?: emptySet()
            val titol = c?.titol ?: def?.titol ?: nom0
            val ref = if (def != null) DriverRef(x.sistema, TipusDriver.EXP, def.clau, IdDriver.hex8(DriversBin.rev(def)), nom, titol, sub, crcs, ordre++)
                else DriverRef(x.sistema, TipusDriver.EXP, nom0, REVISIONS[x.sistema.prefix + nom0] ?: "1", nom, titol, sub, crcs, ordre++)
            regs.add(Reg(ref, obj, null, null, c?.confirmatAbans == true))
        }
        // 4. experimentals automàtics dels paquets
        for (p in packs) for (en in p.entrades) if (en.origen == TipusDriver.EXP) dePack(en, p, "_exp")
        return Estat(regs, v, cands)
    }

    // ---------- consultes ----------
    /** Identitat de la ROM: CRC32 del fitxer sencer (fil-segur; el mateix que `crcDe` i que libretro). */
    fun crcRom(bytes: ByteArray): Long = crcDe(bytes)
    val versioActual: Int get() = e().versio
    /** Tots els drivers (actius) d'un sistema, o de tots si `sistema` és null. */
    fun refs(sistema: SistemaDriver? = null): List<DriverRef> = e().regs.filter { actiu(it) && (sistema == null || it.ref.sistema == sistema) }.map { it.ref }
    fun perId(id: String): DriverRef? = e().perId[id]?.takeIf { actiu(it) }?.ref
    /** Driver per la clau d'àlbum d'abans (`GB_Kirby`, `NES_Auto_XXXXXXXX`...): per migrar marques i dades desades. */
    fun perClauAlbum(clau: String): DriverRef? = e().perClauAlbum[clau]?.takeIf { actiu(it) }?.ref
    /** Drivers que reconeixen aquest CRC sense llegir la ROM (tots els AUTO/EXP de dades i els fets a mà amb CRC conegut). */
    fun candidatsPerCrc(crc: Long): List<DriverRef> = e().perCrc[crc and 0xFFFFFFFFL]?.filter { actiu(it) }?.map { it.ref } ?: emptyList()
    /** Drivers que es reconeixen per signatura: cap (l'autor, 2026-11-17: tots van per CRC). Es manté per a la interfície `Reconeixedor` (índex de ROM). */
    @Suppress("UNUSED_PARAMETER") fun perSignatura(sistema: SistemaDriver): List<DriverRef> = emptyList()
    /** Drivers que l'autor havia donat per bons abans de les marques per contingut (`provat = true` de la llista antiga): només per migrar-los un cop a les marques. */
    fun confirmatsAbans(): List<DriverRef> = e().regs.filter { it.confirmat }.map { it.ref }

    /** El driver reconeix aquesta ROM? Només pel CRC32 del fitxer sencer (`bytes`): si no és a la llista del driver, no (l'autor, 2026-11-17). Mai llança. */
    fun reconeix(ref: DriverRef, bytes: ByteArray): Boolean = try { crcDe(bytes) in ref.crcs } catch (x: Throwable) { false }

    /** Tots els drivers d'un sistema que reconeixen aquesta ROM pel CRC, en ordre estable (fets a mà, automàtics, experimentals). Mai llança. */
    fun driversDeRom(sistema: SistemaDriver, bytes: ByteArray): List<DriverRef> = try {
        candidatsPerCrc(crcDe(bytes)).filter { it.sistema == sistema }.sortedBy { it.ordre }
    } catch (x: Throwable) { emptyList() }

    /** El driver executable (es construeix i es guarda la primera vegada). Null si l'id no hi és o el registre del paquet és corrupte. */
    fun carrega(id: String): Any? {
        val r = e().perId[id] ?: return null
        return objDe(r)
    }
    /** Quants drivers dels paquets s'han construït (per a proves i traces). */
    fun nombreConstruits(): Int = cau.size
    fun carregaGb(id: String): GbDriver? = carrega(id) as? GbDriver
    fun carregaNes(id: String): NesDriver? = carrega(id) as? NesDriver
    fun carregaSnes(id: String): SnesDriver? = carrega(id) as? SnesDriver
    private fun objDe(r: Reg): Any? {
        r.obj?.let { return it }
        cau[r.ref.id]?.let { return it }
        val p = r.pack ?: return null; val en = r.e ?: return null
        val d = p.llegeix(en) ?: return null
        val o = DriversText.construeix(d, r.ref.nom)
        return cau.putIfAbsent(r.ref.id, o) ?: o
    }

    // ---------- compatibilitat amb el codi d'abans (llistes senceres; construeixen tots els drivers d'un sistema) ----------
    private class Llistes(val versio: Int, val nomes: Boolean, val gb: List<GbDriver>, val nes: List<NesDriver>, val snes: List<SnesDriver>, val compat: List<JocCompat>)
    @Volatile private var llistes: Llistes? = null
    private fun ll(): Llistes {
        val es = e(); val nm = nomes
        llistes?.let { if (it.versio == es.versio && it.nomes == nm) return it }
        val act = es.regs.filter { actiu(it) }
        val gb = ArrayList<GbDriver>(); val nes = ArrayList<NesDriver>(); val snes = ArrayList<SnesDriver>()
        for (r in act) when (val o = objDe(r)) { is GbDriver -> gb.add(o); is NesDriver -> nes.add(o); is SnesDriver -> snes.add(o) }
        val compat = act.map { JocCompat(it.ref.titol, it.ref.subsistema, it.ref.clauAlbum) } + (if (nm) emptyList() else METADADES_COMPAT.filter { m -> SistemaDriver.values().none { m.clau.startsWith(it.prefix) } })
        return Llistes(es.versio, nm, gb, nes, snes, compat).also { llistes = it }
    }
    fun llistaGb(): List<GbDriver> = ll().gb
    fun llistaNes(): List<NesDriver> = ll().nes
    fun llistaSnes(): List<SnesDriver> = ll().snes
    /** Llista de compatibles derivada del registre (títol, sistema i clau d'àlbum de cada driver, més els jocs de Pokémon amb motor propi). */
    fun compat(): List<JocCompat> = ll().compat
}
