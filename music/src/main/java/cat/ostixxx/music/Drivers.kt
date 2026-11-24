package cat.ostixxx.music

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import cat.gbdsmixxx.App
import cat.gbdsmixxx.CandidatsDrivers
import cat.gbdsmixxx.DefDriver
import cat.gbdsmixxx.TitolCataleg
import cat.gbdsmixxx.DriversBin
import cat.gbdsmixxx.DriversDef
import cat.gbdsmixxx.DriversImport
import cat.gbdsmixxx.Informe
import cat.gbdsmixxx.RegistreDrivers
import cat.gbdsmixxx.t
import java.io.File

/**
 * Drivers guiats per dades (l'autor, 2026-11-16): tot en binari, mai en TXT.
 * - Els de l'app: `assets/drivers.bin` (el genera `eines/drivers_paquet/genera.sh` des de `drivers_builtin.txt` i `ExpNesAuto.kt`).
 * - Els importats (Ajustos > Importa drivers; un fitxer de drivers o un report sencer de l'analitzador): `files/drivers/importats.bin` (escriptura segura amb `.bak`).
 * En arrencar només es llegeixen els dos índexs, en un fil de fons (`carregaEnFons`); cada driver es construeix quan una ROM coincideix o se'n toca una cançó (`RegistreDrivers`).
 */
object Drivers {
    /** Traça de l'import (`files/importa_log.txt`): una línia per pas, escrita abans de continuar, amb la memòria usada (MB); si l'app peta, l'última línia diu on. */
    @Volatile private var fitxerTraca: File? = null
    /** Activa la traça des de l'arrencada de l'app (`files/importa_log.txt`, es va afegint; si passa de 300 KB, es buida). */
    fun iniciaTraca(ctx: Context) {
        val f = File(ctx.filesDir, "importa_log.txt")
        try { if (f.exists() && f.length() > 300_000) f.writeText("") } catch (e: Throwable) { }
        fitxerTraca = f; App.traca = { traca(it) }; traca("app: inici")
    }
    fun traca(s: String) {
        val f = fitxerTraca ?: return
        try { val rt = Runtime.getRuntime(); f.appendText(System.currentTimeMillis().toString().takeLast(7) + " " + s + " [" + (rt.totalMemory() - rt.freeMemory()) / 1000000 + "/" + rt.maxMemory() / 1000000 + " MB]\n") } catch (e: Throwable) { }
    }

    private val principal = Handler(Looper.getMainLooper())
    private val lockFitxer = Any()
    private fun carpeta(ctx: Context) = File(ctx.filesDir, "drivers").also { it.mkdirs() }
    private fun fitxerImportats(ctx: Context) = File(carpeta(ctx), "importats.bin")

    /** En arrencar: en un fil de fons fa `abans` (registrar els experimentals compilats), obre l'índex del paquet de l'app i el dels importats (i, un sol cop, passa a binari els `importat_*.txt` d'abans i els esborra). */
    fun carregaEnFons(ctx: Context, abans: () -> Unit = {}) {
        val app = ctx.applicationContext
        RegistreDrivers.preparaIndex()
        Thread {
            var packApp: DriversBin.Pack? = null; var imp: DriversBin.Pack? = null; var cand: DriversBin.Pack? = null
            try {
                val t0 = System.currentTimeMillis()
                try { abans() } catch (e: Throwable) { traca("drivers: error en registrar els experimentals " + e.javaClass.simpleName) }
                packApp = try { DriversBin.obre(DriversBin.FontBytes(app.assets.open("drivers.bin").use { it.readBytes() })) } catch (e: Throwable) { null }
                imp = synchronized(lockFitxer) { migraText(app) ?: DriversBin.obreFitxer(fitxerImportats(app)) }
                // candidats de «Prova compatibilitat» (els caducats es treuen aquí)
                cand = try { synchronized(lockFitxer) { CandidatsDrivers.paquet(carpetaCandidats(app)) } } catch (e: Throwable) { null }
                traca("drivers: índex de l'app " + (packApp?.entrades?.size ?: -1) + ", importats " + (imp?.entrades?.size ?: 0) + " (" + (System.currentTimeMillis() - t0) + " ms)")
            } catch (e: Throwable) { traca("drivers: error " + e.javaClass.simpleName) }
            finally { RegistreDrivers.posaPaquets(packApp, imp, cand) }
        }.also { it.isDaemon = true; it.name = "drivers-index" }.start()
    }

    /** Els `files/drivers/importat_*.txt` d'abans: es converteixen a `importats.bin` i s'esborren. Null si no n'hi ha. */
    private fun migraText(ctx: Context): DriversBin.Pack? {
        val vells = carpeta(ctx).listFiles { f -> f.isFile && f.name.endsWith(".txt") }?.sortedBy { it.name } ?: return null
        if (vells.isEmpty()) return null
        val actual = DriversBin.obreFitxer(fitxerImportats(ctx))
        val r = DriversImport.importa(null, actual, vells.map { try { DriversDef.text(it.readBytes()) } catch (e: Throwable) { "" } })
        r.paquet?.let { DriversBin.desa(fitxerImportats(ctx), it) }
        for (f in vells) f.delete()
        traca("drivers: migrats " + vells.size + " TXT a binari (" + r.llegits + " drivers, " + r.errors.size + " línies no llegides)")
        return DriversBin.obreFitxer(fitxerImportats(ctx))
    }

    /** Importa fitxers de drivers o reports de l'analitzador (en un fil de fons): els desa en binari, els registra i diu quants n'hi ha de nous, d'actualitzats i quines línies no s'han pogut llegir. */
    fun importa(ctx: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = ctx.applicationContext
        if (fitxerTraca == null) iniciaTraca(app)
        App.missatge = t("Llegint els drivers...")
        Thread {
            val linies = ArrayList<Pair<Boolean, String>>()
            var resum: String
            var refa = false
            try {
                traca("import de drivers: " + uris.size + " fitxers")
                val textos = ArrayList<String>()
                for (u in uris) try {
                    textos.add(DriversDef.text(app.contentResolver.openInputStream(u)!!.use { it.readBytes() }))
                } catch (e: Throwable) { linies.add(false to (u.lastPathSegment ?: "?") + ": " + e.javaClass.simpleName); traca("error de lectura: " + e.javaClass.simpleName) }
                val r = synchronized(lockFitxer) {
                    val f = fitxerImportats(app)
                    val res = DriversImport.importa(RegistreDrivers.paquetApp(), RegistreDrivers.paquetImportats(), textos)
                    res.paquet?.let { b -> DriversBin.desa(f, b); RegistreDrivers.posaImportats(DriversBin.obreFitxer(f)) }
                    res
                }
                traca("drivers: " + r.nous + " nous, " + r.actualitzats + " actualitzats, " + r.iguals + " iguals, " + r.errors.size + " errors")
                for (e in r.errors) { linies.add(false to e); traca("línia no llegida: " + e) }
                resum = r.nous.toString() + " " + t("drivers nous") + ", " + r.actualitzats + " " + t("actualitzats") + ", " + r.iguals + " " + t("iguals") + ", " + r.errors.size + " " + t("errors")
                if (r.llegits == 0 && r.errors.isEmpty()) resum = t("Cap línia AUTO_POKE, AUTO_GB ni AUTO_NES als fitxers")
                linies.add(0, true to resum)
                refa = r.crcsCanviats.isNotEmpty()   // driver nou o canviat: App.carrega (amb la seva guarda de generació) l'avalua només contra les ROM que encara no l'han vist
            } catch (e: Throwable) {
                resum = t("No s'han pogut importar els drivers") + " (" + e.javaClass.simpleName + ")"; linies.add(false to resum); traca("error: " + e.javaClass.simpleName + " " + (e.message ?: "").take(120))
            }
            val fi = resum; val refaBib = refa
            principal.post {
                App.missatge = fi; Informe.linies = linies
                android.widget.Toast.makeText(app, fi, android.widget.Toast.LENGTH_LONG).show()
                if (refaBib) { traca("refà la biblioteca"); App.carrega(app) }
            }
        }.also { it.isDaemon = true }.start()
    }

    /** Secció «DRVI» de la còpia `.ostx`: el paquet `importats.bin` tal qual (null si no n'hi ha cap de vàlid). */
    fun bytesImportats(ctx: Context): ByteArray? = synchronized(lockFitxer) {
        val f = fitxerImportats(ctx.applicationContext)
        if (f.isFile && DriversBin.obreFitxer(f) != null) f.readBytes() else null
    }
    fun nImportats(): Int = RegistreDrivers.paquetImportats()?.entrades?.size ?: 0

    /**
     * Aplica una secció «DRVI». Fusió: s'hi afegeixen les definicions que aquí no hi són (per CRC, format i origen; si ja hi són, es queda la d'aquí). `substitueix`: guanyen
     * les importades (les d'aquí que no hi són es queden). Abans d'escriure, còpia a `importats.bin.abans_ostx`. Retorna quantes definicions noves; llença si el paquet no és vàlid.
     * No refà la biblioteca (ho fa qui crida). Fora del fil principal.
     */
    fun importaPaquet(ctx: Context, b: ByteArray, substitueix: Boolean): Int {
        val imp = DriversBin.obre(DriversBin.FontBytes(b)) ?: throw java.io.IOException("paquet de drivers no vàlid")
        return synchronized(lockFitxer) {
            val f = fitxerImportats(ctx.applicationContext)
            val actual = RegistreDrivers.paquetImportats()
            val ara = actual?.entrades?.map { Triple(it.crc, it.format, it.origen) }?.toSet() ?: emptySet()
            val defs = imp.tot()
            val nous = defs.count { Triple(it.crc, it.format, it.origen) !in ara }
            if (nous == 0 && !substitueix) return@synchronized 0
            val vells = actual?.tot() ?: emptyList()
            val bytes = if (substitueix) DriversBin.escriu(vells + defs) else DriversBin.escriu(defs + vells)
            if (f.isFile) try { f.copyTo(File(f.path + ".abans_ostx"), true) } catch (e: Throwable) { }
            DriversBin.desa(f, bytes); RegistreDrivers.posaImportats(DriversBin.obreFitxer(f))
            traca("còpia .ostx: $nous drivers importats nous")
            nous
        }
    }

    // ---- candidats de «Prova compatibilitat» (l'autor, 2026-11-22): en binari (CandidatsDrivers), mai en TXT; aquestes funcions fan disc: fora del fil principal ----
    fun carpetaCandidats(ctx: Context) = CandidatsDrivers.carpeta(carpeta(ctx))
    /** Desa el candidat d'una ROM i el posa al registre (l'àlbum surt al proper `App.carrega`). */
    fun desaCandidat(ctx: Context, d: DefDriver) = synchronized(lockFitxer) {
        val dir = carpetaCandidats(ctx); CandidatsDrivers.desa(dir, d); RegistreDrivers.posaCandidats(CandidatsDrivers.paquet(dir))
        traca("prova compatibilitat: candidat " + d.clau + " amb " + d.noms.size + " cançons")
    }
    /** «Confirma el driver»: el candidat passa a `importats.bin` (driver permanent). El catàleg definitiu és «Valida del tot», a part. */
    fun confirmaCandidat(ctx: Context, crc: Long, mida: Long, nom: String, titols: Map<Int, TitolCataleg>): Boolean = synchronized(lockFitxer) {
        val dir = carpetaCandidats(ctx)
        val nou = CandidatsDrivers.confirma(dir, fitxerImportats(ctx), RegistreDrivers.paquetImportats(), crc, mida, nom, titols) ?: return@synchronized false
        RegistreDrivers.posaImportatsICandidats(nou, CandidatsDrivers.paquet(dir))
        traca("prova compatibilitat: confirmat %08X".format(crc)); true
    }
    /** «Descarta»: treu el candidat (fitxer i registre). */
    fun descartaCandidat(ctx: Context, crc: Long) = synchronized(lockFitxer) {
        val dir = carpetaCandidats(ctx); CandidatsDrivers.descarta(dir, crc); RegistreDrivers.posaCandidats(CandidatsDrivers.paquet(dir))
        traca("prova compatibilitat: descartat %08X".format(crc))
    }

    /** Esborra els drivers importats (els de l'app es queden) i els treu del registre. */
    fun esborraImportats(ctx: Context) {
        val app = ctx.applicationContext
        Thread {
            val hiHavia = synchronized(lockFitxer) {
                val n = RegistreDrivers.paquetImportats()?.entrades?.size ?: 0
                val f = fitxerImportats(app)
                for (x in listOf(f, File(f.path + ".bak"), File(f.path + ".tmp"))) x.delete()
                carpeta(app).listFiles { x -> x.name.endsWith(".txt") }?.forEach { it.delete() }
                RegistreDrivers.posaImportats(null)
                n
            }
            if (hiHavia > 0) principal.post { App.carrega(app) }
        }.also { it.isDaemon = true }.start()
    }
}
