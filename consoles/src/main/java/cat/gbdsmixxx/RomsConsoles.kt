package cat.gbdsmixxx

import android.content.Context
import java.io.File

/** Fitxers i importació de la música de consoles (Game Boy, NES, SNES, .nsf/.vgm/.gbs/.spc); el nucli (`RomsMusica`) no en sap res. */
object RomsConsoles {
    private fun <V> lru(max: Int): MutableMap<String, V> = RomsMusica.lru(max)
    private fun dir(ctx: Context) = RomsMusica.dir(ctx)
    private val crcCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
    /** CRC32 d'una ROM desada a l'app (el mateix que fa servir libretro-database), en flux (sense carregar-la); es recalcula si el fitxer canvia (mida o data). Només el mode antic. */
    fun crcFitxer(ctx: Context, nom: String): Long? = try {
        val f = File(dir(ctx), nom)
        if (!f.exists()) null else { val seg = "${f.length()}:${f.lastModified()}"; crcCache[nom]?.takeIf { it.first == seg }?.second ?: MemoriaRoms.crc32(f).also { crcCache[nom] = seg to it } }
    } catch (e: Throwable) { null }
    private fun no(m: String): String? = RomsMusica.no(m)
    private fun crc32(b: ByteArray) = RomsMusica.crc32(b)
    private fun nomFitxer(ctx: Context, uri: android.net.Uri) = RomsMusica.nomFitxer(ctx, uri)

    /** Jocs de Game Boy amb motor propi: Cristall, Groc, Vermell, Blau, Trading Card Game i Trading Card Game 2, reconeguts pel CRC (`CrcDrivers.POKEMON`); es desen com `musica_CRY.gb` / `musica_YEL.gb` / `musica_TCG.gb` / `musica_TCG2.gb`. */
    val GB = mapOf("CRY" to "Cristall", "YEL" to "Groc", "TCG" to "Pokémon TCG", "TCG2" to "Pokémon TCG 2", "RED" to "Pokémon Red", "BLU" to "Pokémon Blue")
    private val cacheGb = lru<RomGb>(2)
    fun fitxerGb(ctx: Context, codi: String) = File(dir(ctx), "musica_$codi.gb")
    /** Jocs de Pokémon desats el CRC dels quals és a la llista (`CrcDrivers.POKEMON`): una ROM desada abans pel títol amb un CRC desconegut ja no surt. */
    fun gbDisponibles(ctx: Context): List<String> = GB.keys.filter { fitxerGb(ctx, it).exists() && crcFitxer(ctx, "musica_$it.gb") in CrcDrivers.pokemon(it) }
    /** MODE ANTIC (temporal): tots els drivers que reconeixen alguna de les ROM de GB desades (`musica_gbd_*.gb`): una ROM pot tenir-ne més d'un (fet a mà i automàtic) i tots coexisteixen; no cal tornar a importar-la quan s'afegeix un driver. */
    fun gbDrivers(ctx: Context): List<Pair<GbDriver, ByteArray>> {
        val fitxers = dir(ctx).listFiles { f -> f.name.startsWith("musica_gbd_") && f.name.endsWith(".gb") }?.sortedBy { it.name } ?: return emptyList()
        val vistos = HashSet<Long>(); val out = ArrayList<Pair<GbDriver, ByteArray>>()
        for (f in fitxers) {
            val bytes = try { f.readBytes() } catch (e: Throwable) { continue }
            if (!vistos.add(crcDe(bytes))) continue                // la mateixa ROM desada amb dos noms: es compta una sola vegada
            for (r in RegistreDrivers.driversDeRom(SistemaDriver.GB, bytes)) RegistreDrivers.carregaGb(r.id)?.let { out.add(it to bytes) }          // només pel CRC (l'autor, 2026-11-17)
        }
        return out
    }
    fun carregaGb(ctx: Context, codi: String): RomGb? = cacheGb[codi] ?: try { RomGb(MemoriaRoms.llegeixFitxer(fitxerGb(ctx, codi))).also { cacheGb[codi] = it } } catch (e: Throwable) { null }

    /** MODE ANTIC (temporal): jocs de NES amb driver executable copiats dins l'app: (driver, ROM). */
    fun nesDrivers(ctx: Context): List<Pair<NesDriver, NesRom>> = RegistreDrivers.refs(SistemaDriver.NES).mapNotNull { r ->
        val d = RegistreDrivers.carregaNes(r.id) ?: return@mapNotNull null
        val f = File(dir(ctx), "musica_nes_${d.nom}.nes")
        if (f.exists()) try { val b = f.readBytes(); if (RegistreDrivers.reconeix(r, b)) d to NesRom(b) else null } catch (e: Throwable) { null } else null          // només si el CRC és del driver
    }

    /** MODE ANTIC (temporal): jocs de SNES amb driver executable (`DRIVERS_SNES`) copiats dins l'app: (driver, ROM). */
    fun snesDrivers(ctx: Context): List<Pair<SnesDriver, RomSnes>> = RegistreDrivers.refs(SistemaDriver.SNES).mapNotNull { r ->
        val d = RegistreDrivers.carregaSnes(r.id) ?: return@mapNotNull null
        val f = File(dir(ctx), "musica_snes_${d.nom}.sfc")
        if (f.exists()) try { val b = f.readBytes(); if (RegistreDrivers.reconeix(r, b)) d to RomSnes(b) else null } catch (e: Throwable) { null } else null          // només si el CRC és del driver
    }

    // ---- NES Sound Format (.nsf): es copien a l'app; un àlbum per fitxer ----
    private fun dirNsf(ctx: Context) = File(dir(ctx), "nsf")
    fun nsfLlista(ctx: Context): List<Pair<String, NsfArxiu>> = (dirNsf(ctx).listFiles { f -> f.name.endsWith(".nsf") } ?: emptyArray()).sortedBy { it.name }.mapNotNull { f ->
        try { val n = NsfArxiu(f.readBytes()); if (n.valid) f.name to n else null } catch (e: Throwable) { null }
    }
    fun importaNsf(ctx: Context, bytes: ByteArray): Boolean {
        if (!NsfArxiu(bytes).valid) return false
        val d = dirNsf(ctx); d.mkdirs()
        RomsMusica.escriuAtomic(File(d, java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) } + ".nsf"), bytes)
        return true
    }

    // ---- Video Game Music (.vgm / .vgz): es desen descomprimits; un àlbum per joc (etiqueta GD3) i una cançó per fitxer ----
    private fun dirVgm(ctx: Context) = File(dir(ctx), "vgm")
    /** Cançons VGM importades: (fitxer, joc, cançó). */
    fun vgmLlista(ctx: Context): List<Triple<String, String, String>> = try {
        DadesBin.taulaAmbMigracio(File(dirVgm(ctx), "index.bin"), File(dirVgm(ctx), "index.txt")).mapNotNull { p -> if (p.size >= 3) Triple(p[0], p[1], p[2]) else null }
    } catch (e: Exception) { emptyList() }
    fun carregaVgm(ctx: Context, nom: String): ByteArray? = try { File(dirVgm(ctx), nom).readBytes() } catch (e: Exception) { null }
    fun importaVgm(ctx: Context, bytes0: ByteArray, nomOrig: String): Boolean {
        var bytes = bytes0
        if (bytes.size > 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) bytes = try { java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(bytes)).use { it.readBytes() } } catch (e: Exception) { return false }
        val v = VgmArxiu(bytes)
        if (!v.valid || !v.suportat) return false
        val g = v.gd3
        val joc = (g.getOrNull(2)?.ifBlank { null } ?: g.getOrNull(3)?.ifBlank { null } ?: "VGM").replace('\t', ' ').replace('\n', ' ')
        val canco = (g.getOrNull(0)?.ifBlank { null } ?: g.getOrNull(1)?.ifBlank { null } ?: nomOrig.ifEmpty { "VGM" }).replace('\t', ' ').replace('\n', ' ')
        val d = dirVgm(ctx); d.mkdirs()
        val nom = java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) } + ".vgm"
        RomsMusica.escriuAtomic(File(d, nom), bytes)
        DadesBin.escriuTaula(File(d, "index.bin"), (vgmLlista(ctx).filter { it.first != nom } + Triple(nom, joc, canco)).map { listOf(it.first, it.second, it.third) })
        return true
    }

    // ---- Game Boy Sound (.gbs): es copien a l'app (musica/gbs/<hash>.gbs); un àlbum per fitxer ----
    private fun dirGbs(ctx: Context) = File(dir(ctx), "gbs")
    fun gbsLlista(ctx: Context): List<Pair<String, GbsArxiu>> = (dirGbs(ctx).listFiles { f -> f.name.endsWith(".gbs") } ?: emptyArray()).sortedBy { it.name }.mapNotNull { f ->
        try { val g = GbsArxiu(f.readBytes()); if (g.valid) f.name to g else null } catch (e: Throwable) { null }
    }
    fun importaGbs(ctx: Context, bytes: ByteArray): Boolean {
        if (!GbsArxiu(bytes).valid) return false
        val d = dirGbs(ctx); d.mkdirs()
        RomsMusica.escriuAtomic(File(d, java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) } + ".gbs"), bytes)
        return true
    }

    // ---- SNES: fitxers .spc (es copien a l'app; el nom del fitxer porta un hash per no repetir-los); joc i cançó, de les etiquetes ID666 ----
    private fun dirSpc(ctx: Context) = File(dir(ctx), "spc")
    /** Cançons SPC importades: (fitxer, joc, cançó). */
    fun spcLlista(ctx: Context): List<Triple<String, String, String>> = try {
        DadesBin.taulaAmbMigracio(File(dirSpc(ctx), "index.bin"), File(dirSpc(ctx), "index.txt")).mapNotNull { p -> if (p.size >= 3) Triple(p[0], p[1], p[2]) else null }
    } catch (e: Exception) { emptyList() }
    fun carregaSpc(ctx: Context, nom: String): ByteArray? = try { File(dirSpc(ctx), nom).readBytes() } catch (e: Exception) { null }
    /** Importa un .spc des de bytes (ja llegits): retorna false si no és un SPC. */
    fun importaSpc(ctx: Context, bytes: ByteArray, nomOrig: String): Boolean {
        val spc = SpcArxiu(bytes)
        if (!spc.valid) return false
        val joc = spc.joc.ifEmpty { "SNES" }.replace('\t', ' ').replace('\n', ' ')
        val canco = spc.canco.ifEmpty { nomOrig.ifEmpty { "SPC" } }.replace('\t', ' ').replace('\n', ' ')
        val d = dirSpc(ctx); d.mkdirs()
        val nom = java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) } + ".spc"
        RomsMusica.escriuAtomic(File(d, nom), bytes)
        val llista = spcLlista(ctx).filter { it.first != nom } + Triple(nom, joc, canco)
        DadesBin.escriuTaula(File(d, "index.bin"), llista.map { listOf(it.first, it.second, it.third) })
        return true
    }

    /** Una ROM de SNES es coneix per la capçalera interna ($7FC0 o $FFC0, amb o sense els 512 bytes de les còpies .smc): el complement i la suma de comprovació són coherents i el títol és text; així no depèn del nom del fitxer (el selector no sempre en dona l'extensió). */
    private fun capcaleraSnes(ch: java.nio.channels.FileChannel): Boolean {
        val mida = ch.size()
        if (mida !in 0x40000L..0x800000L || (mida % 0x8000L != 0L && mida % 0x8000L != 512L)) return false
        val extra = if (mida % 1024L == 512L) 512L else 0L
        for (base in longArrayOf(0x7FC0L, 0xFFC0L)) {
            val o = base + extra
            if (o + 0x30 > mida) continue
            val b = java.nio.ByteBuffer.allocate(0x30)
            ch.position(o); while (b.hasRemaining()) if (ch.read(b) < 0) break
            val a = b.array()
            val compl = (a[0x1C].toInt() and 255) or ((a[0x1D].toInt() and 255) shl 8)
            val suma = (a[0x1E].toInt() and 255) or ((a[0x1F].toInt() and 255) shl 8)
            if ((compl xor suma) == 0xFFFF && (0 until 12).all { a[it].toInt() in 0x20..0x7E }) return true
        }
        return false
    }

    /** Valor intern: el fitxer no és de consola (null vol dir «ho és, però ha fallat»). */
    private const val NO_ES = "\u0000"

    private fun importaText(ctx: Context, uri: android.net.Uri, ch: java.nio.channels.FileChannel, h: ByteArray): String? {
        val d = dir(ctx); d.mkdirs()
        val nomes = DriversExtra.nomes                       // OSTixxx Debug: res de formats de rip ni de Pokémon
        // Partitures (l'autor, 2026-11-23): fitxer `.oxpt` dels analitzadors; es desa i només es fa servir amb la seva ROM
        if (!nomes && Partitures.esPartitura(h) && ch.size() < 200_000_000L) {
            ch.position(0)
            val bytes = java.nio.ByteBuffer.allocate(ch.size().toInt()).also { b -> while (b.hasRemaining()) if (ch.read(b) < 0) break }.array()
            return PartituresApp.importa(ctx, bytes)
        }
        // PlayStation (l'autor, 2026-11-23): .cue o pista de dades (.bin de 2352 o .iso de 2048) amb «PLAYSTATION»: música XA i CD àudio; la imatge no es copia (`Ps1`)
        if (!nomes && (Ps1.esCue(h) || Ps1.esDisc(ch))) return Ps1.importa(ctx, AccesFitxers.rutaLlegible(ctx, uri, ch.size()), nomFitxer(ctx, uri) ?: "", Ps1.esCue(h))
        // ROM de NES (iNES) d'un joc amb driver executable (`DRIVERS_NES`): es desa com `musica_nes_<joc>.nes`
        if (h[0] == 'N'.code.toByte() && h[1] == 'E'.code.toByte() && h[2] == 'S'.code.toByte() && h[3] == 0x1A.toByte() && ch.size() < 2_000_000L) {
            if (!ModeCarrega.antic) return registraImport(ctx, uri, ch, "NES", "nes")
            ch.position(0)
            val bytes = java.nio.ByteBuffer.allocate(ch.size().toInt()).also { b -> while (b.hasRemaining()) if (ch.read(b) < 0) break }.array()
            val r = NesRom(bytes); val drv = RegistreDrivers.driversDeRom(SistemaDriver.NES, bytes).firstNotNullOfOrNull { RegistreDrivers.carregaNes(it.id) }          // només pel CRC
                ?: return no(t("ROM de NES") + " (mapper ${r.mapper}, ${bytes.size / 1024} KB, CRC32 ${crc32(bytes)}): " + t("no és cap dels jocs reconeguts") + " (" + DRIVERS_NES.joinToString(", ") { it.nom } + ")")
            File(d, "musica_nes_${drv.nom}.nes").writeBytes(bytes)
            return drv.nom
        }
        // ROM de SNES (.sfc/.smc) d'un joc amb driver executable (`DRIVERS_SNES`): es desa com `musica_snes_<joc>.sfc`
        val extNom = (nomFitxer(ctx, uri) ?: "").substringAfterLast('.', "").lowercase()
        if ((extNom == "sfc" || extNom == "smc" || capcaleraSnes(ch)) && ch.size() in 0x40000L..0x800000L) {
            if (!ModeCarrega.antic) return registraImport(ctx, uri, ch, "SNES", if (ch.size() % 1024L == 512L) "smc" else "sfc")
            ch.position(0)
            val bytes = java.nio.ByteBuffer.allocate(ch.size().toInt()).also { b -> while (b.hasRemaining()) if (ch.read(b) < 0) break }.array()
            val r = RomSnes(bytes)
            // Una ROM pot portar més d'un joc (Super Mario All-Stars: SMB1/Lost Levels, SMB2 i SMB3): es desa una còpia per a cada driver que la reconeix
            val drvs = RegistreDrivers.driversDeRom(SistemaDriver.SNES, bytes).mapNotNull { RegistreDrivers.carregaSnes(it.id) }          // només pel CRC
            val drv = drvs.firstOrNull()
                ?: return no(t("ROM de SNES") + " (${if (r.hiRom) "HiROM" else "LoROM"}, «${r.titol}», ${bytes.size / 1024} KB, CRC32 ${crc32(bytes)}): " + t("no és cap dels jocs reconeguts") + " (" + DRIVERS_SNES.joinToString(", ") { it.nom } + ")")
            for (x in drvs) File(d, "musica_snes_${x.nom}.sfc").writeBytes(bytes)
            return drvs.joinToString(" + ") { it.nom }
        }
        // NES Sound Format (.nsf), Video Game Music (.vgm i .vgz, amb gzip)
        val esNsf = h[0] == 'N'.code.toByte() && h[1] == 'E'.code.toByte() && h[2] == 'S'.code.toByte() && h[3] == 'M'.code.toByte() && h[4] == 0x1A.toByte()
        val esVgm = (h[0] == 'V'.code.toByte() && h[1] == 'g'.code.toByte() && h[2] == 'm'.code.toByte() && h[3] == ' '.code.toByte()) || (h[0] == 0x1F.toByte() && h[1] == 0x8B.toByte())
        if (!nomes && (esNsf || esVgm) && ch.size() < 16_000_000L) {
            ch.position(0)
            val bytes = java.nio.ByteBuffer.allocate(ch.size().toInt()).also { b -> while (b.hasRemaining()) if (ch.read(b) < 0) break }.array()
            return if (esNsf) (if (importaNsf(ctx, bytes)) "NSF" else no(t("NSF no vàlid")))
                else (if (importaVgm(ctx, bytes, (nomFitxer(ctx, uri) ?: "").substringBeforeLast('.'))) "VGM" else no(t("VGM sense cap xip que sapiguem fer sonar (SN76489, Game Boy o NES) o fitxer no vàlid")))
        }
        // Game Boy Sound: fitxer .gbs
        if (!nomes && h[0] == 'G'.code.toByte() && h[1] == 'B'.code.toByte() && h[2] == 'S'.code.toByte() && h[3] == 1.toByte() && ch.size() < 4_000_000L) {
            ch.position(0)
            val bytes = java.nio.ByteBuffer.allocate(ch.size().toInt()).also { b -> while (b.hasRemaining()) if (ch.read(b) < 0) break }.array()
            return if (importaGbs(ctx, bytes)) "GBS" else no(t("GBS no vàlid"))
        }
        // SNES: fitxer .spc (capçalera «SNES-SPC700 Sound File Data»)
        if (!nomes && String(h, 0, 11, Charsets.US_ASCII) == "SNES-SPC700" && ch.size() < 1_000_000L) {
            ch.position(0)
            val bytes = java.nio.ByteBuffer.allocate(ch.size().toInt()).also { b -> while (b.hasRemaining()) if (ch.read(b) < 0) break }.array()
            return if (importaSpc(ctx, bytes, (nomFitxer(ctx, uri) ?: "").substringBeforeLast('.'))) "SPC" else no(t("SPC no vàlid"))
        }
        // Game Boy / Game Boy Color: logo de Nintendo a 0x104 i títol a 0x134 (de moment només els jocs de Pokémon amb motor propi)
        if (h[0x104] == 0xCE.toByte() && h[0x105] == 0xED.toByte() && h[0x106] == 0x66.toByte() && ch.size() <= 8_000_000L) {
            val titol = String(h, 0x134, 11, Charsets.US_ASCII)
            ch.position(0)
            val bytes = java.nio.ByteBuffer.allocate(ch.size().toInt()).also { b -> while (b.hasRemaining()) if (ch.read(b) < 0) break }.array()
            val codi = CrcDrivers.POKEMON[crcDe(bytes)]          // Pokémon (motors propis): només pel CRC (l'autor, 2026-11-17), abans pel títol
            if (codi != null && !nomes) { RomsMusica.escriuAtomic(File(d, "musica_$codi.gb"), bytes); cacheGb.remove(codi); return GB[codi] }          // Pokémon (motors propis): es copien com sempre
            if (!ModeCarrega.antic) return registraBytesImport(ctx, uri, bytes, ch.size(), "GB", if ((h[0x143].toInt() and 0x80) != 0) "gbc" else "gb")
            // Altres jocs de Game Boy: els que tenen un driver de so executable es reconeixen pel CRC32 de la ROM (`RegistreDrivers`)
            val drv = RegistreDrivers.driversDeRom(SistemaDriver.GB, bytes).firstNotNullOfOrNull { RegistreDrivers.carregaGb(it.id) }          // només pel CRC
                ?: return no(t("ROM de Game Boy") + " «" + titol.trim { it.code == 0 || it == ' ' } + "» (" + bytes.size / 1024 + " KB, CRC32 " + crc32(bytes) + "): " + t("no coincideix amb cap joc ni versió reconeguts") + " (" + DRIVERS_GB.joinToString(", ") { it.nom } + ", Pokémon)")
            File(d, "musica_gbd_${drv.nom}.gb").writeBytes(bytes); cacheGb.remove("gbd_${drv.nom}")
            return drv.nom
        }
        return NO_ES
    }

    fun importa(ctx: Context, uri: android.net.Uri, ch: java.nio.channels.FileChannel, h: ByteArray): ResultatImport? {
        val r = importaText(ctx, uri, ch, h)
        return if (r == NO_ES) null else ResultatImport(r)
    }

    fun buidaCaches() { cacheGb.clear(); tcgUlt = null; tcg2Ult = null; MemoriaRoms.buida() }

    // ---- TCG i TCG2: la ROM convertida es fa un cop per ROM (i la ROM ve de la memòria cau limitada) ----
    @Volatile private var tcgUlt: Pair<RomGb, RomTcg>? = null
    @Volatile private var tcg2Ult: Pair<RomGb, RomTcg2>? = null
    fun tcg(ctx: Context): RomTcg { val r = carregaGb(ctx, "TCG") ?: throw IllegalStateException("ROM no trobada"); tcgUlt?.let { if (it.first === r) return it.second }; return RomTcg(r.d).also { tcgUlt = r to it } }
    fun tcg2(ctx: Context): RomTcg2 { val r = carregaGb(ctx, "TCG2") ?: throw IllegalStateException("ROM no trobada"); tcg2Ult?.let { if (it.first === r) return it.second }; return RomTcg2(r.d).also { tcg2Ult = r to it } }

    // =====================================================================================================================================
    // Bloc de càrrega (2026-11-16): índex binari de ROM de GB/GBC, NES i SNES (`IndexRoms`), ROM per referència (llegides d'on són) i drivers avaluats una sola vegada.
    // =====================================================================================================================================
    private val pany = Any()
    @Volatile private var index: IndexRoms? = null
    /** L'índex (es carrega del disc la primera vegada). */
    fun index(ctx: Context): IndexRoms = synchronized(pany) { index ?: IndexRoms(File(dir(ctx), "index_roms.bin")).also { it.carrega(); index = it } }
    private fun gestor(ctx: Context, conj: FacanaDrivers.Conjunt = FacanaDrivers.conjunt()): GestorRoms =
        GestorRoms(index(ctx), conj, dir(ctx)).also { g -> g.editor = { c, b -> if (c != "GB") null else (try { EditorsGb.de(b) } catch (e: Throwable) { null }) } }
    /** Fa una operació sobre l'índex amb el pany (no n'hi ha mai dues alhora) i el desa (atòmic) si ha canviat. */
    fun <T> ambIndex(ctx: Context, conj: FacanaDrivers.Conjunt = FacanaDrivers.conjunt(), f: (GestorRoms) -> T): T = synchronized(pany) {
        val g = gestor(ctx, conj)
        try { f(g) } finally { try { g.idx.desaSiCal() } catch (e: Throwable) { App.traca?.invoke("índex de ROM: no s'ha pogut desar " + e.javaClass.simpleName) }; conj.oblida() }
    }

    /**
     * Abans de construir la biblioteca: (1) la primera vegada, registra les ROM que ja estaven copiades dins l'app (`musica_gbd_*.gb`, `musica_nes_*.nes`, `musica_snes_*.sfc`, `roms/`)
     * com a fonts internes, sense moure-les; (2) avalua els drivers per signatura nous (només llegeix les ROM que en tenen de pendents). Retorna el conjunt de drivers fet servir.
     */
    fun preparaIndex(ctx: Context): Pair<FacanaDrivers.Conjunt, List<EntradaRom>> {
        val conj = FacanaDrivers.conjunt()
        val l = ambIndex(ctx, conj) { g ->
            if (!g.idx.internesRegistrades) {
                val fs = (dir(ctx).listFiles { f -> (f.name.startsWith("musica_gbd_") && f.name.endsWith(".gb")) || (f.name.startsWith("musica_nes_") && f.name.endsWith(".nes")) || (f.name.startsWith("musica_snes_") && f.name.endsWith(".sfc")) }?.sortedBy { it.name } ?: emptyList()) +
                    (File(dir(ctx), "roms").listFiles { f -> f.isFile && !f.name.endsWith(".tmp") }?.sortedBy { it.name } ?: emptyList())
                var n = 0
                for (f in fs) if (g.registra(f, OrigenRom.INTERNA) is GestorRoms.Resultat.Nova) n++
                g.idx.internesRegistrades = true; g.idx.marcaBrut()
                App.traca?.invoke("índex de ROM: registrades $n ROM internes de " + fs.size + " fitxers")
            }
            val tretes = g.avaluaPendents(AccesFitxers.te(ctx))          // sense permís no es treu res: les ROM per referència no es poden llegir, però hi són
            if (tretes.isNotEmpty()) App.traca?.invoke("índex de ROM: " + tretes.size + " ROM ja no hi són")
            g.idx.llista().map { e -> e }
        }
        return conj to l
    }

    /** Drivers d'una entrada de l'índex (amb el conjunt de drivers d'aquesta construcció). */
    fun driversDe(ctx: Context, conj: FacanaDrivers.Conjunt, e: EntradaRom): List<String> = GestorRoms(index(ctx), conj, dir(ctx)).driversDe(e)

    /** Bytes d'una ROM de l'índex per reproduir-la; si ja no hi és, es treu de la biblioteca (les dades de l'usuari queden guardades pel CRC) i es llença l'error. */
    fun obre(ctx: Context, r: RomRef): ByteArray = try { r.obre() } catch (e: RomPerduda) { perdudaO(ctx, r, e) }
    /** El mateix per a les ROM de SNES ja convertides (`RomSnes`, a la memòria cau per bytes). */
    fun obreSnes(ctx: Context, r: RomRef): RomSnes = try { MemoriaRoms.objecte("snes", r, { x: RomSnes -> x.d.size.toLong() }) { RomSnes(it) } } catch (e: RomPerduda) { perdudaO(ctx, r, e) }
    fun obreNes(ctx: Context, r: RomRef): NesRom = try { MemoriaRoms.objecte("nes", r, { x: NesRom -> x.d.size.toLong() * 2 }) { NesRom(it) } } catch (e: RomPerduda) { perdudaO(ctx, r, e) }
    /** Una ROM per referència sense permís de lectura NO es dona per perduda (només s'avisa); si hi ha permís i no hi és, surt de la biblioteca. Sempre llença. */
    private fun perdudaO(ctx: Context, r: RomRef, e: RomPerduda): Nothing {
        if (r.origen == OrigenRom.REFERENCIA && !AccesFitxers.te(ctx)) throw IllegalStateException(t("Cal «Accés a tots els fitxers» per llegir aquesta ROM"))
        perduda(ctx, r.crc); throw e
    }

    /** Una ROM ja no hi és: fora de l'índex i la biblioteca es refà (barat: no llegeix cap ROM). */
    fun perduda(ctx: Context, crc: Long) {
        val treta = try { ambIndex(ctx) { it.perduda(crc) } } catch (e: Throwable) { false }
        if (treta) { App.traca?.invoke("ROM %08X ja no hi és: es treu de la biblioteca".format(crc)); App.carrega(ctx.applicationContext) }
    }

    /** Importació d'un fitxer de ROM de GB/NES/SNES triat: per referència si es pot llegir d'on és; si no, còpia dins l'app (amb avís). Sense driver també es registra. */
    private fun registraImport(ctx: Context, uri: android.net.Uri, ch: java.nio.channels.FileChannel, cons: String, ext: String): String? {
        ch.position(0)
        val bytes = java.nio.ByteBuffer.allocate(ch.size().toInt()).also { b -> while (b.hasRemaining()) if (ch.read(b) < 0) break }.array()
        return registraBytesImport(ctx, uri, bytes, ch.size(), cons, ext)
    }
    private fun registraBytesImport(ctx: Context, uri: android.net.Uri, bytes: ByteArray, mida: Long, cons: String, ext: String): String? {
        val crc = MemoriaRoms.crc32(bytes)
        val nomCrc = "CRC32 %08X".format(crc)
        if (index(ctx).te(crc)) return t("ja era a la biblioteca") + " (" + nomCrc + ")"
        val ruta = AccesFitxers.rutaLlegible(ctx, uri, mida)
        return ambIndex(ctx) { g ->
            val font = if (ruta != null) IndexRoms.font(OrigenRom.REFERENCIA, File(ruta)) else g.copiaInterna(bytes, ext)
            val avis = if (ruta != null) "" else " · " + t("copiada dins l'app: per llegir-la d'on és, dona «Accés a tots els fitxers» a Ajustos")
            when (val r = g.registraBytes(bytes, cons, font)) {
                is GestorRoms.Resultat.Nova -> {
                    val ds = g.driversDe(r.e).map { it.split(':').getOrElse(1) { it } }
                    (if (ds.isEmpty()) t("sense driver encara: es guarda i sortirà sola quan n'hi hagi un") + " (" + nomCrc + ")" else ds.joinToString(" + ")) + avis
                }
                is GestorRoms.Resultat.Duplicada -> t("ja era a la biblioteca") + " (" + nomCrc + ")"
                else -> no(t("No s'ha pogut llegir la ROM"))
            }
        }
    }

    /** Afegeix una carpeta (camí) a la biblioteca i la revisa. Ganxo de `ModeCarrega.afegeixCarpeta`. */
    fun afegeixCarpeta(ctx: Context, ruta: String, informe: MutableList<Pair<Boolean, String>>, progres: (String) -> Unit): Pair<Int, Int> {
        ambIndex(ctx) { g -> if (g.idx.carpetes.add(File(ruta).absolutePath)) g.idx.marcaBrut() }
        return revisa(ctx, informe, progres)
    }

    /**
     * «Revisa les carpetes» (només quan l'usuari ho demana; mai en arrencar): les ROM que ja no hi són surten de la biblioteca (les dades de l'usuari es conserven pel CRC),
     * les noves de les carpetes afegides s'hi registren per referència i els fitxers d'altres formats nous (GBA, DS, NSF...) s'importen com sempre. Retorna (afegits, candidats).
     */
    fun revisa(ctx: Context, informe: MutableList<Pair<Boolean, String>>, progres: (String) -> Unit): Pair<Int, Int> {
        val altresExt = setOf("gba", "nds", "nsf", "vgm", "vgz", "gbs", "spc", "cue", "iso")          // PS1: el .cue (o la .iso) porta tot el disc
        if (!AccesFitxers.te(ctx)) { informe.add(false to t("Cal «Accés a tots els fitxers» per revisar les carpetes")); return 0 to 0 }          // sense permís, totes les ROM per referència semblarien perdudes
        val rv = ambIndex(ctx) { g -> progres(t("Revisant les carpetes")); g.revisa(altresExt) }
        for (c in rv.tretes) informe.add(false to "%08X: ".format(c) + t("ja no hi és; es treu de la biblioteca (les marques es conserven)"))
        for (e in rv.afegides) informe.add(true to (e.fonts.firstOrNull()?.ruta?.substringAfterLast('/') ?: "?") + " → " + t("afegida per referència"))
        var bons = rv.afegides.size
        // Pokémon (motors propis: Cristall, Groc, Vermell, Blau, TCG i TCG2) no són drivers de l'índex: la ROM d'una carpeta es copia com sempre a `musica_<codi>.gb` perquè surti l'àlbum
        for (en in ambIndex(ctx) { g -> g.idx.entrades.values.toList() }) {
            val codi = CrcDrivers.POKEMON[en.crc] ?: continue
            if (fitxerGb(ctx, codi).exists() && crcFitxer(ctx, "musica_$codi.gb") == en.crc) continue
            val f = en.fonts.firstOrNull { File(it.ruta).exists() } ?: continue
            try {
                RomsMusica.escriuAtomic(File(dir(ctx), "musica_$codi.gb"), File(f.ruta).readBytes()); cacheGb.remove(codi); bons++
                informe.add(true to File(f.ruta).name + " → " + t("Pokémon: copiada per al seu motor propi"))
            } catch (e: Throwable) { informe.add(false to File(f.ruta).name + ": " + e.javaClass.simpleName) }
        }
        // els fitxers ja importats es marquen com a vistos per lots (abans: l'índex sencer es reescrivia, amb .bak i fsync, després de CADA fitxer). Si l'app mor a mig lot,
        // només es tornen a importar els d'aquell lot (els GBA/DS/NSF/VGM/GBS/SPC es desen pel codi o pel hash: no es dupliquen)
        val lot = ArrayList<java.io.File>()
        fun desaLot() { if (lot.isNotEmpty()) { val l = lot.toList(); lot.clear(); ambIndex(ctx) { g -> for (x in l) g.marcaVist(x) } } }
        try {
            for (f in rv.altres) {
                progres(f.name)
                RomsMusica.motiu = ""
                val res = try { RomsMusica.importa(ctx, android.net.Uri.fromFile(f)) } catch (e: Throwable) { null }
                if (res != null) bons++
                informe.add(if (res != null) true to f.name + " → " + res else false to f.name + ": " + RomsMusica.motiu.ifEmpty { t("Format no reconegut") })
                lot.add(f)
                if (lot.size >= 25) desaLot()
            }
        } finally { desaLot() }
        return bons to (rv.afegides.size + rv.duplicades + rv.altres.size)
    }

    /**
     * «Refer biblioteca» (botó d'emergència d'Ajustos): torna a registrar les ROM internes, torna a avaluar TOTS els drivers de totes les ROM (les llegeix totes, una
     * darrere l'altra), revisa les carpetes i reconstrueix l'índex. No esborra cap fitxer ni cap dada de l'usuari.
     */
    fun referBiblioteca(ctx: Context, informe: MutableList<Pair<Boolean, String>>, progres: (String) -> Unit): Pair<Int, Int> {
        ambIndex(ctx) { g -> g.idx.internesRegistrades = false; g.oblidaAvaluacions() }
        MemoriaRoms.buida()
        preparaIndex(ctx)
        return revisa(ctx, informe, progres)
    }
}
