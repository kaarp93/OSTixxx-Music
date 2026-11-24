package cat.gbdsmixxx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Dades de l'usuari en un sol fitxer binari propi (`files/dades_usuari.bin`, registre-diari `Diari`, magic «OXDU», versió 1): marques ✓/✗ i motius, etiquetes i noms
 * de pista, ordre propi i comptes de reproducció. Cada canvi és un registre amb CRC que s'afegeix al final (en un fil apart, amb `fsync`); quan hi ha massa registres
 * vells es compacta (atòmic, amb `.bak`).
 *
 * Claus: `tipus` + SEP + àlbum [+ SEP + pista]. L'àlbum és la clau ESTABLE (`estable(nom)`: «CRC:tipus:clau» per a les ROM de consola, el nom de l'àlbum per a la resta).
 * Si una dada només hi és amb el nom antic de l'àlbum (migrada de les preferències), en llegir-la s'adopta: es passa a la clau estable. Res no s'esborra quan
 * una ROM desapareix: les dades queden guardades pel CRC i tornen a sortir si la ROM es torna a afegir. Kotlin pur: es prova a JVM.
 */
class MagatzemUsuari(f: File, private val estable: (String) -> String) {
    private val diari = Diari(f, MAGIC, VERSIO)
    private val m = LinkedHashMap<String, Any>()
    private val fil = Executors.newSingleThreadExecutor { r -> Thread(r, "dades-usuari").also { it.isDaemon = true } }
    @Volatile var carregat = false; private set
    /** El fitxer ja porta la migració de les preferències antigues (registre «migrat»). */
    val migrat: Boolean get() = synchronized(m) { m[CLAU_MIGRAT] == 1 }

    fun carrega() {
        synchronized(m) {
            if (carregat) return
            m.clear()
            diari.llegeix { b -> aplica(b) }
            carregat = true
        }
    }

    private fun aplica(b: ByteArray) {
        val d = DataInputStream(ByteArrayInputStream(b))
        when (d.readByte().toInt()) {
            1 -> { val k = d.readUTF(); m.remove(k); m[k] = d.readInt() }
            2 -> { val k = d.readUTF(); m.remove(k); m[k] = Durable.llegeixText(d) }
            3 -> m.remove(d.readUTF())
        }
    }

    private fun reg(k: String, v: Any?): ByteArray {
        val bo = ByteArrayOutputStream()
        DataOutputStream(bo).use { d ->
            when (v) {
                null -> { d.writeByte(3); d.writeUTF(k) }
                is Int -> { d.writeByte(1); d.writeUTF(k); d.writeInt(v) }
                else -> { d.writeByte(2); d.writeUTF(k); Durable.escriuText(d, v.toString()) }
            }
        }
        return bo.toByteArray()
    }

    /** Escriu el canvi en memòria ara i al disc en el fil de les dades (en ordre). */
    private fun posaBrut(k: String, v: Any?) {
        carrega()
        val r: ByteArray
        synchronized(m) { if (v == null) { if (m.remove(k) == null) return } else { if (m[k] == v) return; m.remove(k); m[k] = v }; r = reg(k, v) }
        fil.execute {
            try {
                diari.afegeix(r)
                val viu = synchronized(m) { m.size }
                if (diari.registres > 2 * viu + 1000) compactaAra()
            } catch (e: Throwable) { }
        }
    }

    private fun compactaAra() { val regs = synchronized(m) { m.entries.map { reg(it.key, it.value) } }; diari.compacta(regs) }

    /** Espera que totes les escriptures pendents siguin al disc (proves i abans de sortir). */
    fun espera(ms: Long = 5000) { val l = java.util.concurrent.CountDownLatch(1); fil.execute { l.countDown() }; l.await(ms, TimeUnit.MILLISECONDS) }

    fun clau(tipus: String, album: String, pista: Int? = null) = tipus + SEP + album + (if (pista == null) "" else SEP + pista)

    /** Valor de la clau estable; si no n'hi ha i n'hi ha amb el nom antic de l'àlbum, s'adopta. */
    private fun valor(tipus: String, album: String, pista: Int?): Any? {
        carrega()
        val est = estable(album)
        val k = clau(tipus, est, pista)
        synchronized(m) { m[k]?.let { return it } }
        if (est == album) return null
        val vell = clau(tipus, album, pista)
        val v = synchronized(m) { m[vell] } ?: return null
        posaBrut(k, v); posaBrut(vell, null)
        return v
    }

    fun int(tipus: String, album: String, pista: Int? = null, def: Int = 0): Int = (valor(tipus, album, pista) as? Int) ?: def
    fun str(tipus: String, album: String, pista: Int? = null, def: String = ""): String = (valor(tipus, album, pista) as? String) ?: def
    fun posaInt(tipus: String, album: String, pista: Int?, v: Int) { valor(tipus, album, pista); posaBrut(clau(tipus, estable(album), pista), v) }
    fun posaStr(tipus: String, album: String, pista: Int?, v: String) { valor(tipus, album, pista); posaBrut(clau(tipus, estable(album), pista), v) }
    fun treu(tipus: String, album: String, pista: Int? = null) { valor(tipus, album, pista); posaBrut(clau(tipus, estable(album), pista), null) }

    /** Àlbums (clau estable, en ordre d'aparició) que tenen alguna clau d'aquests tipus amb un valor que compleix `p`. */
    fun albums(tipus: Set<String>, p: (Any) -> Boolean): List<String> {
        carrega()
        val out = LinkedHashSet<String>()
        synchronized(m) { for ((k, v) in m) { val parts = k.split(SEP); if (parts.size >= 2 && parts[0] in tipus && p(v)) out.add(parts[1]) } }
        return out.toList()
    }
    /** Totes les claus d'un tipus: (àlbum estable, pista o null, valor). */
    fun totes(tipus: String): List<Triple<String, Int?, Any>> {
        carrega()
        synchronized(m) { return m.entries.mapNotNull { (k, v) -> val p = k.split(SEP); if (p.size >= 2 && p[0] == tipus) Triple(p[1], p.getOrNull(2)?.toIntOrNull(), v) else null } }
    }
    /** Esborra totes les claus d'aquests tipus. */
    fun esborraTipus(tipus: Set<String>) {
        carrega()
        val ks = synchronized(m) { m.keys.filter { it.substringBefore(SEP) in tipus } }
        for (k in ks) posaBrut(k, null)
    }
    /**
     * Adopta d'un cop totes les dades guardades amb el nom antic d'un àlbum que ara té clau estable (`noms`: nom de l'àlbum -> clau estable). Es crida quan es publica una
     * biblioteca nova (en un fil apart), perquè les dades migrades passin al CRC encara que no s'obri mai aquell àlbum.
     */
    fun adopta(noms: Map<String, String>) {
        carrega()
        val moure = ArrayList<Pair<String, String>>()
        synchronized(m) {
            for (k in m.keys) {
                val p = k.split(SEP); if (p.size < 2) continue
                val est = noms[p[1]] ?: continue
                if (est == p[1]) continue
                val nova = (listOf(p[0], est) + p.drop(2)).joinToString(SEP)
                if (!m.containsKey(nova)) moure.add(k to nova)
            }
        }
        for ((vell, nova) in moure) { val v = synchronized(m) { m[vell] } ?: continue; posaBrut(nova, v); posaBrut(vell, null) }
    }
    /**
     * Migració única de les marques «provat» d'abans (`RegistreDrivers.confirmatsAbans()`, l'autor 2026-11-22): `albums` = (clau estable «CRC:tipus:clau:rev», títol). Per a cada
     * parella (CRC, id) que encara no s'ha migrat (registre `T_CONF`), si l'àlbum no té cap marca se li posa ✓ (1) i el títol; una marca que ja hi era (✓ o ✗) no es toca.
     * Cada parella es migra una sola vegada (si l'autor treu la marca després, no torna). Abans d'escriure res es compacta el fitxer (atòmic: l'anterior queda a `.bak`).
     * Escriu directament amb la clau estable (no passa per `Marques.posa`: ni log de proves ni report). Retorna quantes marques ha posat.
     */
    fun migraConfirmats(albums: List<Pair<String, String>>): Int {
        carrega()
        val pendents = synchronized(m) { albums.distinctBy { it.first }.filter { m[clau(T_CONF, it.first)] == null } }
        if (pendents.isEmpty()) return 0
        fil.submit { compactaAra() }.get()          // al fil de les dades: cap altra escriptura del diari alhora
        var n = 0
        for ((est, titol) in pendents) {
            val (teMarca, teTitol) = synchronized(m) { (m[clau(T_MARCA, est)] != null) to (m[clau(T_TITOL, est)] != null) }
            if (!teMarca) { posaBrut(clau(T_MARCA, est), 1); if (!teTitol && titol.isNotEmpty()) posaBrut(clau(T_TITOL, est), titol); n++ }
            posaBrut(clau(T_CONF, est), 1)
        }
        espera()
        return n
    }
    fun mida(): Int = synchronized(m) { m.size }

    /** Secció «DUSR» de la còpia `.ostx`: totes les dades (amb les claus estables «CRC:tipus:clau:rev»), sense el registre de migració. Format: int n i n registres (int longitud + registre del diari). */
    fun exportaBytes(): ByteArray {
        carrega()
        val regs = synchronized(m) { m.entries.filter { it.key != CLAU_MIGRAT }.map { reg(it.key, it.value) } }
        val bo = ByteArrayOutputStream()
        DataOutputStream(bo).use { d -> d.writeInt(regs.size); for (r in regs) { d.writeInt(r.size); d.write(r) } }
        return bo.toByteArray()
    }
    /** Quantes dades porta una secció «DUSR» (sense aplicar-la); -1 si no s'entén. */
    fun compta(b: ByteArray): Int = try { llegeixSeccio(b).size } catch (e: Throwable) { -1 }
    private fun llegeixSeccio(b: ByteArray): LinkedHashMap<String, Any> {
        val d = DataInputStream(ByteArrayInputStream(b))
        val n = d.readInt(); require(n in 0..10_000_000)
        val out = LinkedHashMap<String, Any>()
        repeat(n) {
            val l = d.readInt(); require(l in 1..(64 shl 20)); val r = ByteArray(l); d.readFully(r)
            val x = DataInputStream(ByteArrayInputStream(r))
            when (x.readByte().toInt()) { 1 -> { val k = x.readUTF(); out[k] = x.readInt() }; 2 -> { val k = x.readUTF(); out[k] = Durable.llegeixText(x) }; 3 -> out.remove(x.readUTF()) }
        }
        out.remove(CLAU_MIGRAT)
        return out
    }
    /**
     * Aplica una secció «DUSR». Fusió (per defecte): s'afegeixen les dades que aquí no hi són; si una clau ja hi és es queda la d'aquí (el diari no guarda dates), excepte
     * els comptes de reproduccions, que es queden amb el més gran. `substitueix`: totes les dades passen a ser les importades (es conserva la marca de migració).
     * S'escriu d'un cop amb una compactació atòmica (l'anterior queda a `.bak`). Retorna quantes dades s'han afegit o canviat. Llença si la secció no s'entén (no toca res).
     */
    fun importaBytes(b: ByteArray, substitueix: Boolean): Int {
        val imp = llegeixSeccio(b)
        carrega()
        return fil.submit<Int> {
            val tot: LinkedHashMap<String, Any>; var n = 0
            synchronized(m) {
                if (substitueix) {
                    tot = LinkedHashMap(imp); m[CLAU_MIGRAT]?.let { tot[CLAU_MIGRAT] = it }
                    for ((k, v) in tot) if (m[k] != v) n++
                    for (k in m.keys) if (k !in tot) n++
                } else {
                    tot = LinkedHashMap(m)
                    for ((k, v) in imp) {
                        val ara = tot[k]
                        if (ara == null) { tot[k] = v; n++ }
                        else if (k.substringBefore(SEP) == T_COMPTE && v is Int && ara is Int && v > ara) { tot[k] = v; n++ }
                    }
                }
            }
            if (n > 0) {
                diari.compacta(tot.entries.map { reg(it.key, it.value) })
                synchronized(m) { m.clear(); m.putAll(tot) }
            }
            n
        }.get()
    }
    /** Còpia de totes les dades (per comprovar la migració). */
    fun copia(): Map<String, Any> = synchronized(m) { LinkedHashMap(m) }

    /**
     * Migració de les preferències antigues (`SharedPreferences "gbdsmixxx"`): `marca_`, `marcamot_`, `marcatit_`, `pm_`, `pt_`, `pe_`, `pn_`, `po_` i el TXT `comptes`.
     * Les dades es desen amb el nom de l'àlbum d'aleshores (s'adopten a la clau estable en llegir-les). Es fa d'un sol cop (compactació atòmica), es torna a llegir el fitxer
     * per comprovar-la i només llavors s'hi posa la marca «migrat». Les preferències NO s'esborren. Retorna quantes dades s'han migrat, o -1 si la comprovació ha fallat.
     */
    fun migra(prefs: Map<String, *>): Int {
        carrega()
        if (migrat) return 0
        val nou = LinkedHashMap<String, Any>()
        // primer els àlbums marcats en l'ordre de la llista antiga (així «noms()» manté l'ordre)
        val ordre = (prefs["marques_llista"] as? String)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
        for (a in ordre) (prefs["marca_$a"] as? Int)?.let { nou[clau(T_MARCA, a)] = it }
        fun pista(resta: String): Pair<String, Int>? { val i = resta.lastIndexOf('_'); if (i <= 0) return null; val id = resta.substring(i + 1).toIntOrNull() ?: return null; return resta.substring(0, i) to id }
        for ((k, v) in prefs) {
            when {
                k.startsWith("marcamot_") && v is String -> if (v.isNotEmpty()) nou[clau(T_MOTIU, k.removePrefix("marcamot_"))] = v
                k.startsWith("marcatit_") && v is String -> nou[clau(T_TITOL, k.removePrefix("marcatit_"))] = v
                k.startsWith("marca_") && v is Int -> nou.putIfAbsent(clau(T_MARCA, k.removePrefix("marca_")), v)
                k.startsWith("pm_") && v is Int -> pista(k.removePrefix("pm_"))?.let { (a, id) -> if (v != 0) nou[clau(T_PM, a, id)] = v }
                k.startsWith("pt_") && v is Int -> pista(k.removePrefix("pt_"))?.let { (a, id) -> if (v != 0) nou[clau(T_PT, a, id)] = v }
                k.startsWith("pe_") && v is Int -> pista(k.removePrefix("pe_"))?.let { (a, id) -> nou[clau(T_PE, a, id)] = v }
                k.startsWith("pn_") && v is String -> pista(k.removePrefix("pn_"))?.let { (a, id) -> if (v.isNotBlank()) nou[clau(T_PN, a, id)] = v }
                k.startsWith("po_") && v is String -> if (v.isNotBlank()) nou[clau(T_ORDRE, k.removePrefix("po_"))] = v
            }
        }
        (prefs["comptes"] as? String)?.lines()?.forEach { l ->
            val i = l.lastIndexOf('\t'); if (i <= 0) return@forEach
            val n = l.substring(i + 1).toIntOrNull() ?: return@forEach
            val c = l.substring(0, i); val j = c.lastIndexOf('|'); if (j <= 0) return@forEach
            val id = c.substring(j + 1).toIntOrNull() ?: return@forEach
            nou[clau(T_COMPTE, c.substring(0, j), id)] = n
        }
        // el que ja hi hagués al fitxer nou (no hauria de passar) té prioritat
        val tot = LinkedHashMap<String, Any>(nou); synchronized(m) { tot.putAll(m) }
        espera()
        diari.compacta(tot.entries.map { reg(it.key, it.value) })
        // comprovació: es torna a llegir el fitxer en un magatzem nou i ha de ser idèntic
        val prova = LinkedHashMap<String, Any>()
        Diari(diari.f, MAGIC, VERSIO).llegeix { b -> val d = DataInputStream(ByteArrayInputStream(b)); when (d.readByte().toInt()) { 1 -> prova[d.readUTF()] = d.readInt(); 2 -> prova[d.readUTF()] = Durable.llegeixText(d); 3 -> prova.remove(d.readUTF()) } }
        if (prova != tot) return -1
        synchronized(m) { m.clear(); m.putAll(tot) }
        posaBrut(CLAU_MIGRAT, 1)
        espera()
        return nou.size
    }

    companion object {
        val MAGIC = Durable.magic("OXDU")
        const val VERSIO = 1
        const val SEP = "\u0001"
        const val T_MARCA = "m"; const val T_MOTIU = "mm"; const val T_TITOL = "mt"
        const val T_PM = "pm"; const val T_PT = "pt"; const val T_PE = "pe"; const val T_PN = "pn"
        const val T_ORDRE = "po"; const val T_COMPTE = "c"
        /** Parella (CRC, id) ja migrada des de `confirmatsAbans` (valor 1). */
        const val T_CONF = "cf"
        const val CLAU_MIGRAT = "_migrat"
    }
}
