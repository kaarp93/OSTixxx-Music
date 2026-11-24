package cat.gbdsmixxx

import android.content.Context
import java.io.File
import kotlin.math.pow

/**
 * Música de Game Boy Advance (motor M4A / «Sappy») sintetitzada en directe des de la ROM de l'usuari (Emerald, LeafGreen...). La taula de
 * cançons es troba per estructura (entrades de 8 bytes: punter a la capçalera + dos u16 iguals), sense adreces fixes; el format de capçalera,
 * pistes, grups de veus i mostres s'ha verificat amb les ROM d'Emerald i LeafGreen (609 cançons, 2082 pistes i 386.446 notes sense ordres
 * desconegudes a Emerald). Aproximacions no comprovades a l'oïda: la velocitat/profunditat del LFO, les envolupants dels canals de Game Boy
 * i el descodificat de DPCM (només a algunes mostres de FireRed/LeafGreen).
 */
/**
 * Mostra DirectSound (WaveData). `sm`: els bytes crus de les dades tal com són a la ROM, amb UN byte més (`data[size]`, que el mesclador llegeix per
 * interpolar l'última mostra: m4a_1.s `ldrsb r1, [r3, 0x1]!` a _081DD19C/_081DD1FC); `mida`: el camp `size`; `freq`: el camp `freq` cru (Hz·1024,
 * MidiKeyToFreq); `cmp`: el camp `type` ≠ 0 (comprimida; només es descodifica per SoundMainRAM_Unk1, és a dir, si el canal porta TONEDATA_TYPE_CMP
 * o _REV); `dec`: les mostres descodificades (SoundMainRAM_Unk2) fins a l'índex `mida` inclòs; `abans`: `data[-1]` (l'últim byte de la capçalera,
 * que llegeix la mostra invertida en arribar al principi).
 */
class OnaG(val hz: Double, val bucle: Boolean, val iniciBucle: Int, val sint: IntArray? = null, val mida: Int = 0, val freq: Long = 0L, val cmp: Boolean = false,
           val abans: Int = 0, crus: () -> ByteArray = { ByteArray(0) }, desc: (() -> ByteArray)? = null) {
    val sm: ByteArray by lazy(LazyThreadSafetyMode.PUBLICATION, crus)
    val dec: ByteArray? by lazy(LazyThreadSafetyMode.PUBLICATION) { desc?.invoke() }
}

class RomGba(private val d: ByteArray) {
    val mida = d.size
    fun u8(o: Int) = d[o].toInt() and 0xFF
    fun u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
    fun u32(o: Int) = u16(o) or (u16(o + 2) shl 16)
    fun s8(o: Int) = d[o].toInt()
    fun pt(v: Int): Int = if (v >= 0x08000000 && v - 0x08000000 < mida) v - 0x08000000 else -1
    val codi: String get() = String(d, 0xAC, 4, Charsets.US_ASCII)

    private fun capValida(h: Int): Boolean {
        if (h < 0 || h + 12 > mida) return false
        val p = u8(h); if (p > 16) return false
        if (p == 0) return true
        if (pt(u32(h + 4)) < 0) return false
        for (i in 0 until p) { val o = h + 8 + 4 * i; if (o + 4 > mida || pt(u32(o)) < 0) return false }
        return true
    }

    private val trobada: Pair<Int, Int> by lazy {
        var millor = -1; var millorN = 0; var o = 0
        while (o < mida - 8) {
            var q = o; var c = 0
            while (q + 8 <= mida) {
                val h = pt(u32(q))
                if (capValida(h) && u16(q + 4) == u16(q + 6) && u16(q + 4) < 40) { c++; q += 8 } else break
            }
            if (c > millorN) { millorN = c; millor = o }
            o += if (c == 0) 4 else c * 8
        }
        Pair(millor, millorN)
    }
    val taula get() = trobada.first
    val nEntrades get() = trobada.second
    fun capcalera(i: Int) = pt(u32(taula + 8 * i))
    fun nPistes(i: Int) = u8(capcalera(i))

    /**
     * Mode de so del joc (`m4aSoundMode(mode)`: reverb bits 0-6, canals de mostres bits 8-11, volum mestre bits 12-15, freqüència de mescla bits 16-19):
     * es busca la crida `ldr r0,=mode; bl SoundMode` (Thumb) amb un valor de mode coherent. A Emerald i LeafGreen dona 0x0094C500 (5 canals, volum 12,
     * 13379 Hz), com al codi font de pokeemerald; a Golden Sun, 0x0097F800 (8 canals, volum 15, 21024 Hz). 0 si no se'n troba cap.
     */
    val modeSo: Int by lazy {
        var r = 0
        var a = 0
        while (a < mida - 12 && r == 0) {
            val h = u16(a)
            if ((h and 0xF800) == 0x4800 && ((h shr 8) and 7) == 0) {
                val imm = h and 0xFF
                for (k in intArrayOf(2, 4)) {
                    val h1 = u16(a + k); val h2 = u16(a + k + 2)
                    if ((h1 and 0xF800) == 0xF000 && (h2 and 0xF800) == 0xF800) {
                        val lit = ((a + 4) and 3.inv()) + imm * 4
                        // Destí del BL: ha de ser m4aSoundMode (comença amb push {r4,r5,lr} i fa `soundInfo->ident++` = adds r0,r1,#1 al principi;
                        // igual a Emerald, LeafGreen, Golden Sun, KH CoM i Final Fight One): així no es pren una crida qualsevol amb un valor semblant
                        var off = ((h1 and 0x7FF) shl 12) or ((h2 and 0x7FF) shl 1); if (off and 0x400000 != 0) off -= 0x800000
                        val tgt = a + k + 4 + off
                        val esSoundMode = tgt >= 0 && tgt + 24 <= mida && u16(tgt) == 0xB530 && (2 until 24 step 2).any { u16(tgt + it) == 0x1C48 }
                        if (lit + 4 <= mida && esSoundMode) {
                            val w = u32(lit)
                            if ((w and 0xFF000000.toInt()) == 0 && (w and 0x00800000) != 0 && ((w shr 16) and 15) in 1..12 && ((w shr 8) and 15) <= 12) r = w
                        }
                        break
                    }
                }
            }
            a += 2
        }
        r
    }
    /**
     * Taula de reproductors (`gMPlayTable`, entrades de 12 bytes: punter a RAM de la info, punter a RAM de les pistes, nombre de pistes i marques): es busca
     * la tira més llarga d'entrades coherents (almenys 2). Cada cançó diu quin reproductor la toca (el segon número de la seva entrada de la taula de
     * cançons) i `MPlayStart` només carrega min(pistes de la cançó, pistes del reproductor). Buida si no se'n troba cap.
     */
    val pistesReproductor: IntArray by lazy {
        // La taula ha de tenir una entrada per a cada reproductor que fa servir la taula de cançons (el seu segon número); si no, és una coincidència
        // amb codi o dades del joc (a Golden Sun, una tira de 2 entrades al principi de la ROM tallava pistes a 4 cançons) i no s'aplica cap límit.
        val t = taula
        var maxMs = 0
        if (t >= 0) for (i in 0 until nEntrades) maxMs = maxOf(maxMs, u16(t + 8 * i + 4))
        var millor = IntArray(0)
        var o = 0
        while (o + 12 <= mida) {
            var n = 0
            while (o + 12 * (n + 1) <= mida) {
                val p = o + 12 * n
                val info = u32(p); val trk = u32(p + 4); val c = u8(p + 8)
                if (info ushr 24 == 3 && info and 0xFFFFFF < 0x8000 && (trk ushr 24 == 3 || trk ushr 24 == 2) && c in 1..16) n++ else break
            }
            if (n >= 2 && n >= maxMs + 1 && n > millor.size) millor = IntArray(n) { u8(o + 12 * it + 8) }
            o += if (n >= 2) 12 * n else 4
        }
        millor
    }
    /** Pistes que el reproductor de la cançó `i` pot tocar (16 si no es coneix la taula de reproductors). */
    fun maxPistes(i: Int): Int {
        val t = taula; val r = pistesReproductor
        if (t < 0 || r.isEmpty() || i < 0 || i >= nEntrades) return 16
        val ms = u16(t + 8 * i + 4)
        return if (ms < r.size) r[ms] else 16
    }
    /** Canals de mostres (DirectSound) del joc; 5 si no es troba el mode (valor de Pokémon). Un camp a 0 no canvia el valor de SoundInit (8; m4a.c). */
    val maxChans: Int get() = if (modeSo != 0) ((modeSo shr 8) and 15).let { if (it == 0) 8 else it } else 5
    /** Volum mestre del joc (0-15); 12 si no es troba el mode; un camp a 0 deixa el de SoundInit (15). */
    val volMestre: Int get() = if (modeSo != 0) ((modeSo shr 12) and 15).let { if (it == 0) 15 else it } else 12
    /** Reverb global del joc (m4aSoundMode: `temp = mode & 0xFF; if (temp) reverb = temp & 0x7F`); la cançó la canvia si porta el bit 0x80 (MPlayStart). */
    val reverbMode: Int get() = if (modeSo and 0xFF != 0) modeSo and 0x7F else 0
    /** Resolució del DAC (SOUNDBIAS bits 14-15: 0 = 9 bits/32768 Hz, 1 = 8 bits/65536 Hz...): m4aSoundMode `(mode & 0x300000) >> 14`; SoundInit posa 1. */
    val resolucio: Int get() = if (modeSo != 0) (modeSo shr 20) and 3 else 1
    /** Mostres per fotograma segons la freqüència de mescla (gPcmSamplesPerVBlankTable); 224 (13379 Hz) si no es troba el mode. */
    val mostresPerFotograma: Int get() = intArrayOf(96, 132, 176, 224, 264, 304, 352, 448, 528, 608, 672, 704)[(if (modeSo != 0) ((modeSo shr 16) and 15) else 4).coerceIn(1, 12) - 1]

    private val cacheOnes = HashMap<Int, OnaG?>()

    /** Mostra DirectSound (capçalera de 16 bytes: tipus u16, estat u16 (bit 0x4000 = bucle), freqüència·1024, inici de bucle, mida en mostres).
     *  Tipus 1 = comprimida (m4a_1.s SoundMainRAM_Unk2): blocs de 33 bytes (mostra inicial + 32 bytes de deltes) = 64 mostres. */
    /** El mesclador del Golden Sun (Func_dc8) té un sintetitzador per a les ones de mida 0; es reconeix per la seva seqüència (movs r2,r2; orreq r0,#0x40; strbeq r0,[r4,#1]). */
    val sintGs: Boolean by lazy {
        val p = intArrayOf(0x02, 0x20, 0xB0, 0xE1, 0x40, 0x00, 0x80, 0x03, 0x01, 0x00, 0xC4, 0x05)
        var i = 0; var ok = false
        while (i + p.size <= d.size && !ok) { if ((d[i].toInt() and 0xFF) == p[0]) { var j = 1; while (j < p.size && (d[i + j].toInt() and 0xFF) == p[j]) j++; ok = j == p.size }; i += 4 }
        ok
    }
    fun ona(w: Int): OnaG? = cacheOnes.getOrPut(w) {
        if (w < 0 || w + 16 > mida) return@getOrPut null
        val tipus = u16(w); val estat = u16(w + 2); val hz = u32(w + 4) / 1024.0
        val ini = u32(w + 8); val mida0 = u32(w + 12)
        // Golden Sun: una ona de mida 0 és un instrument sintètic (el mesclador de la ROM, Func_dc8, el detecta pel comptador a 0);
        // les dades són 0x80, tipus (0 quadrada, 1 dent de serra, 2 triangular) i els paràmetres de la quadrada (duty, velocitat, fondària, desfasament)
        if (mida0 == 0 && sintGs && w + 22 <= mida) return@getOrPut OnaG(hz, estat and 0x4000 != 0, 0, IntArray(6) { u8(w + 16 + it) }, freq = u32(w + 4).toLong() and 0xFFFFFFFFL)
        if (mida0 <= 0 || mida0 > 4_000_000) return@getOrPut null
        // Bytes crus: `size` + 1 (el que hi hagi a la ROM; fora de la ROM, 0). Comprimida: les mostres de SoundMainRAM_Unk2 fins a l'índex `size`.
        val nCru = minOf(mida0 + 1, mida - (w + 16)).coerceAtLeast(0)
        OnaG(hz, estat and 0x4000 != 0, ini.coerceIn(0, maxOf(0, mida0 - 1)), mida = mida0, freq = u32(w + 4).toLong() and 0xFFFFFFFFL, cmp = tipus != 0,
            abans = s8(w + 15), crus = { ByteArray(nCru) { d[w + 16 + it] } }, desc = if (tipus != 0) ({ dpcm(w + 16, mida0 + 1) }) else null)
    }

    /** SoundMainRAM_Unk2 (m4a_1.s): blocs de 33 bytes = 64 mostres (byte inicial cru, i deltes de gDeltaEncodingTable: del segon byte només el nibble baix). */
    private fun dpcm(o: Int, n: Int): ByteArray {
        val taulaDelta = intArrayOf(0, 1, 4, 9, 16, 25, 36, 49, -64, -49, -36, -25, -16, -9, -4, -1)
        val out = ByteArray(n); var k = 0; var p = o
        while (k < n && p + 33 <= mida) {
            var s = s8(p); out[k++] = s.toByte()
            if (k < n) { s += taulaDelta[u8(p + 1) and 15]; out[k++] = s.toByte() }        // el primer byte de deltes només fa servir el nibble baix
            for (j in 2 until 33) {                                                        // els altres: nibble alt i després baix
                val b = u8(p + j)
                if (k < n) { s += taulaDelta[b shr 4]; out[k++] = s.toByte() }
                if (k < n) { s += taulaDelta[b and 15]; out[k++] = s.toByte() }
            }
            p += 33
        }
        return out
    }
}

/** ROM de música desades a la memòria privada: GBA (`poketch/musica_<codi>.gba`) i DS (`poketch/musica_<codi>.sdat`, només l'arxiu de so). */
object RomsMusica {
    /** Cache de mida limitada (les 2 últimes): una ROM o un SDAT ocupen desenes de MB i, sense límit, amb diversos jocs importats es quedava sense memòria. */
    fun <V> lru(max: Int): MutableMap<String, V> = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, V>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?) = size > max
    })
    // Memòries cau limitades per BYTES (no per nombre): una ROM de GBA fa fins a 32 MB i un SDAT desenes; amb un límit per nombre, dues de grosses ja feien petar l'app
    private val cache = LruBytes<String, RomGba>(40L shl 20) { it.mida.toLong() }
    private val cacheSdat = LruBytes<String, MemoriaRoms.AmbMida>(40L shl 20) { it.mida }
    fun dir(ctx: Context) = File(ctx.filesDir, "musica")
    fun fitxer(ctx: Context, codi: String) = File(dir(ctx), "musica_$codi.gba")

    /** Jocs de GBA que es poden importar: codi de joc (offset 0xAC) -> nom. Totes tenen el mateix motor (M4A). */
    val GBA = mapOf("BPEE" to "Emerald", "BPGE" to "LeafGreen", "BPRE" to "FireRed", "AXVE" to "Ruby", "AXPE" to "Sapphire")

    /** Jocs de DS (primeres 3 lletres del codi, offset 0x0C): tots fan servir l'arxiu de so SDAT (el mateix format que Diamant). */
    /** Nom de l'arxiu de so de cada joc de DS (a `data/sound/`). */
    private val NOM_SDAT = mapOf("ADA" to "sound_data.sdat", "APA" to "sound_data.sdat", "CPU" to "pl_sound_data.sdat", "IPK" to "gs_sound_data.sdat", "IPG" to "gs_sound_data.sdat",
        "IRB" to "wb_sound_data.sdat", "IRA" to "wb_sound_data.sdat", "IRE" to "swan_sound_data.sdat", "IRD" to "swan_sound_data.sdat")
    val NDS = mapOf("ADA" to "Diamant", "APA" to "Perla", "CPU" to "Plati", "IPK" to "HeartGold", "IPG" to "SoulSilver",
        "IRB" to "Black", "IRA" to "White", "IRE" to "Black 2", "IRD" to "White 2")

    fun disponibles(ctx: Context): List<String> = GBA.keys.filter { fitxer(ctx, it).exists() }

    /** Arxius de so de DS importats: codi (4 lletres) -> nom del joc. */
    fun sdatDisponibles(ctx: Context): List<Pair<String, String>> =
        (dir(ctx).listFiles { f -> f.name.startsWith("musica_") && f.name.endsWith(".sdat") } ?: emptyArray()).sortedBy { it.name }.mapNotNull { f ->
            val codi = f.name.removePrefix("musica_").removeSuffix(".sdat")
            NDS[codi.take(3)]?.let { codi to it }
        }

    /** Jocs de DS que no són a la taula (Altres ràdios): codi (4 lletres) -> títol de la bàner de la ROM. */
    fun altresDisponibles(ctx: Context): List<Pair<String, String>> =
        (dir(ctx).listFiles { f -> f.name.startsWith("musica_") && f.name.endsWith(".sdat") } ?: emptyArray()).sortedBy { it.name }.mapNotNull { f ->
            val codi = f.name.removePrefix("musica_").removeSuffix(".sdat")
            if (NDS[codi.take(3)] != null) null
            else codi to (try { File(dir(ctx), "musica_$codi.nom").readText().trim() } catch (e: Exception) { "" }).ifEmpty { codi }
        }

    /** Els altres SDAT del mateix joc (importats amb ell: `musica_<codi>.sdat`, `musica_<codi>_2.sdat`...), per enllaçar-ne els arxius d'ones. */
    fun germansSdat(ctx: Context, codi: String): List<Sdat> {
        val base = codi.replace(Regex("_\\d+$"), "")
        return altresDisponibles(ctx).map { it.first }.filter { it != codi && it.replace(Regex("_\\d+$"), "") == base }.mapNotNull { carregaSdat(ctx, it) }
    }

    // ---- Altres ràdios, GBA: la ROM NO es copia; es desa l'URI (amb permís persistent) i es llegeix d'on sigui al mòbil (l'autor, 2026-11-08) ----
    // La llista es desa en binari (`altres_gba.bin`, `DadesBin`); l'`altres_gba.txt` d'abans es migra una vegada i es reanomena a `.migrat`.
    private fun fitxerAltres(ctx: Context) = File(dir(ctx), "altres_gba.bin")
    private fun fitxerAltresTxt(ctx: Context) = File(dir(ctx), "altres_gba.txt")
    private val cacheAltres = LruBytes<String, RomGba>(40L shl 20) { it.mida.toLong() }
    fun buidaCaches() { cache.clear(); cacheSdat.clear(); cacheAltres.clear(); Consoles.proveidor?.buidaCaches() }

    /** Jocs de GBA d'«Altres ràdios»: (codi, títol, URI de la ROM). */
    fun altresGba(ctx: Context): List<Triple<String, String, String>> = try {
        DadesBin.taulaAmbMigracio(fitxerAltres(ctx), fitxerAltresTxt(ctx)).mapNotNull { p -> if (p.size >= 3) Triple(p[0], p[1], p[2]) else null }
    } catch (e: Exception) { emptyList() }

    /** Llegeix tot el fitxer d'un URI en un sol array de la mida exacta (`readBytes` el duplica mentre creix i amb ROMs grans podia fer petar l'app); null si no es pot. */
    fun llegeixUri(ctx: Context, uri: android.net.Uri): ByteArray? = try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                val n = ch.size()
                if (n <= 0L || n > Int.MAX_VALUE) null
                else java.nio.ByteBuffer.allocate(n.toInt()).also { b -> while (b.hasRemaining()) if (ch.read(b) < 0) break }.array()
            }
        } ?: ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Throwable) { null }

    /** La ROM llegida de la seva ubicació (null si ja no hi és o no es pot llegir). */
    fun carregaAltreGba(ctx: Context, codi: String): RomGba? {
        cacheAltres.get(codi)?.let { return it }
        val uri = altresGba(ctx).firstOrNull { it.first == codi }?.third ?: return null
        return try {
            llegeixUri(ctx, android.net.Uri.parse(uri))?.let { RomGba(it).also { r -> cacheAltres.put(codi, r) } }
        } catch (e: Throwable) { null }
    }

    /** Una ROM de GBA és de música M4A si té una taula de cançons coherent amb prou cançons de 2 pistes o més. */
    private fun esM4a(r: RomGba) = r.taula >= 0 && r.nEntrades >= 8 && (0 until r.nEntrades).count { r.nPistes(it) >= 2 } >= 3

    /**
     * Importa tots els fitxers d'una carpeta (i subcarpetes) que siguin ROMs de música (.gba, .nds i, si l'app ho admet, de consola). Retorna (importats, candidats).
     * `progres` rep el nom de cada fitxer abans d'importar-lo.
     */
    fun importaCarpeta(ctx: Context, arrel: android.net.Uri, informe: MutableList<Pair<Boolean, String>>? = null, progres: (String) -> Unit): Pair<Int, Int> {
        var bons = 0; var total = 0
        val cr = ctx.contentResolver
        fun camina(docId: String) {
            val llista = ArrayList<Triple<String, String, String>>()
            try {
                val fills = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(arrel, docId)
                cr.query(fills, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                    while (c.moveToNext()) llista.add(Triple(c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2) ?: ""))
                }
            } catch (e: Exception) { return }
            for ((id, nom, mime) in llista) {
                if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) { camina(id); continue }
                val ext = nom.substringAfterLast('.', "").lowercase()
                if (ext != "gba" && ext != "nds" && ext !in (Consoles.proveidor?.extensions ?: emptySet<String>())) continue
                total++; progres(nom)
                val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(arrel, id)
                motiu = ""
                val res = try { importa(ctx, uri) } catch (e: Throwable) { motiu = t("No s'ha pogut llegir la ROM") + " (" + e.javaClass.simpleName + ")"; null }
                if (res != null) bons++
                informe?.add(if (res != null) true to "$nom → $res" else false to "$nom: " + motiu.ifEmpty { t("Format no reconegut") })
            }
        }
        try { camina(android.provider.DocumentsContract.getTreeDocumentId(arrel)) } catch (e: Exception) { }
        return bons to total
    }

    /**
     * «Netejar llistes»: treu les importacions de GBA i DS que no són Pokémon (les d'«Altres ràdios»): la llista de ROMs de GBA llegides d'on són i els
     * SDAT de DS desconeguts, i les seves cançons de les llistes, noms i ocultes. Retorna (GBA, DS) que s'han tret. No toca Pokémon.
     */
    fun netejaAltres(ctx: Context): Pair<Int, Int> {
        val gba = altresGba(ctx).size
        try { DadesBin.escriuTaula(fitxerAltres(ctx), emptyList()) } catch (e: Exception) { }          // buida la llista (neteja demanada i confirmada per l'usuari)
        cacheAltres.clear()
        var ds = 0
        for ((codi, _) in altresDisponibles(ctx)) {
            try { File(dir(ctx), "musica_$codi.sdat").delete(); File(dir(ctx), "musica_$codi.nom").delete(); File(dir(ctx), "musica_$codi.ico").delete() } catch (e: Exception) { }
            cacheSdat.remove(codi); ds++
        }
        RadioDades.carrega(ctx); RadioDades.treuEmissores(listOf("GBA_", "DS_")); RadioDades.desa()
        RadioIndex.treu(ctx, listOf("DSU_", "GBU_"))
        return gba to ds
    }

    /** Nom del fitxer de la ROM sense l'extensió (el que es veu a «Altres ràdios»), o null si no es pot llegir. */
    fun nomFitxer(ctx: Context, uri: android.net.Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) { null }

    private val nomsUri = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Nom del joc a partir del seu fitxer (sense l'extensió), cada cop que es fa la biblioteca: el nom que el sistema dona per a la URI i, si no el dona, l'últim tros del camí. Null si no es pot saber (llavors es manté el desat en importar). */
    fun nomDelFitxer(ctx: Context, uri: String): String? {
        nomsUri[uri]?.let { return it }
        val u = try { android.net.Uri.parse(uri) } catch (e: Exception) { return null }
        val n = nomFitxer(ctx, u) ?: try { android.net.Uri.decode(u.lastPathSegment ?: "").substringAfterLast('/').substringAfterLast(':').substringBeforeLast('.').trim().takeIf { it.isNotEmpty() } } catch (e: Exception) { null }
        if (n != null) nomsUri[uri] = n
        return n
    }

    private fun importaAltreGba(ctx: Context, uri: android.net.Uri, bytes: ByteArray, codi: String): String? {
        val rom = try { RomGba(bytes) } catch (e: Exception) { return null }
        if (!esM4a(rom)) return null
        try { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
        val titol = nomFitxer(ctx, uri) ?: String(bytes, 0xA0, 12, Charsets.US_ASCII).trim { it == ' ' || it.code == 0 }.lowercase().split(' ').filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }.ifEmpty { codi }
        val altres = altresGba(ctx).filter { it.first != codi } + Triple(codi, titol.replace('\t', ' '), uri.toString())
        val d = dir(ctx); d.mkdirs()
        DadesBin.escriuTaula(fitxerAltres(ctx), altres.map { listOf(it.first, it.second, it.third) })
        cacheAltres.put(codi, rom)
        return titol
    }

    fun carrega(ctx: Context, codi: String): RomGba? = cache.get(codi) ?: try { RomGba(MemoriaRoms.llegeixFitxer(fitxer(ctx, codi))).also { cache.put(codi, it) } } catch (e: Throwable) { null }

    fun fitxerSdat(ctx: Context, codi: String) = File(dir(ctx), "musica_$codi.sdat")

    /** Icona de 32x32 (ARGB; 0 = transparent) del joc de DS importat `codi`, de la bàner de la ROM (`musica_<codi>.ico`), o null. */
    fun icona(ctx: Context, codi: String): IntArray? = try {
        val b = File(dir(ctx), "musica_$codi.ico").readBytes()
        if (b.size < 0x220) null else {
            val pal = IntArray(16) { k ->
                val v = (b[0x200 + 2 * k].toInt() and 255) or ((b[0x201 + 2 * k].toInt() and 255) shl 8)
                if (k == 0) 0 else (0xFF shl 24) or (((v and 31) * 255 / 31) shl 16) or ((((v shr 5) and 31) * 255 / 31) shl 8) or (((v shr 10) and 31) * 255 / 31)
            }
            IntArray(1024) { i ->
                val x = i % 32; val y = i / 32
                val by = b[((y / 8) * 4 + (x / 8)) * 32 + (y % 8) * 4 + (x % 8) / 2].toInt() and 255
                pal[if (x % 2 == 0) by and 15 else by shr 4]
            }
        }
    } catch (e: Exception) { null }

    /** Paraula que identifica un joc per cercar-li icona: la primera del títol (sense accents ni el que va entre parèntesis); si és «The», «Super»..., les dues primeres. */
    private fun clauNom(t: String): String {
        val n = java.text.Normalizer.normalize(t.substringBefore('('), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
        val p = Regex("[a-z0-9]+").findAll(n).map { it.value }.toList()
        if (p.isEmpty()) return ""
        return if (p[0] in setOf("the", "a", "an", "super", "new") && p.size > 1) p[0] + " " + p[1] else p[0]
    }

    /** Icona per a un joc sense (GBA, Game Boy, SNES...): la d'un joc de DS amb la mateixa paraula al títol («Sonic» amb «Sonic»); si n'hi ha diversos, la més antiga exportada. */
    fun iconaDe(ctx: Context, nom: String): IntArray? {
        val k = clauNom(nom); if (k.isEmpty()) return null
        val fs = (dir(ctx).listFiles { f -> f.name.startsWith("musica_") && f.name.endsWith(".ico") } ?: emptyArray()).sortedBy { it.lastModified() }
        for (f in fs) {
            val codi = f.name.removePrefix("musica_").removeSuffix(".ico")
            val titol = NDS[codi.take(3)]?.let { "Pokémon $it" } ?: (try { File(dir(ctx), "musica_$codi.nom").readText().trim() } catch (e: Exception) { "" })
            if (clauNom(titol) == k) icona(ctx, codi)?.let { return it }
        }
        return null
    }
    fun segellFitxer(f: File) = "${f.length()}:${f.lastModified()}"
    /** Segell (mida i data) d'una ROM llegida d'on és: si el proveïdor no ho diu, l'URI (llavors l'índex no es refà sol). */
    fun segellUri(ctx: Context, uri: String): String = try {
        ctx.contentResolver.query(android.net.Uri.parse(uri), arrayOf(android.provider.OpenableColumns.SIZE, android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)
            ?.use { c -> if (c.moveToFirst()) "${c.getLong(0)}:${c.getLong(1)}" else uri } ?: uri
    } catch (e: Exception) { uri }
    fun carregaSdat(ctx: Context, codi: String): Sdat? = (cacheSdat.get(codi)?.valor as? Sdat) ?: try {
        val f = File(dir(ctx), "musica_$codi.sdat"); Sdat(MemoriaRoms.llegeixFitxer(f)).also { cacheSdat.put(codi, MemoriaRoms.AmbMida(it, f.length())) }
    } catch (e: Throwable) { null }

    /**
     * Importa una ROM de música: de GBA (es reconeix pel codi de la capçalera i se'n desa una còpia) o de DS (en cerca l'arxiu `.sdat` per tota la
     * ROM i només en desa aquest). Retorna el nom del joc o null si no es reconeix.
     */
    /** Per què l'última importació no ha anat bé (es posa abans de tornar null): es mostra a l'informe d'importació. */
    @Volatile var motiu = ""
    fun no(m: String): String? { motiu = m; return null }
    /** Escriu un fitxer de dades sencer de manera atòmica (tmp + fsync + rename): una mort a mig import no deixa una ROM/SDAT tallat. */
    fun escriuAtomic(f: File, b: ByteArray) {
        val t = File(f.path + ".tmp")
        java.io.FileOutputStream(t).use { o -> o.write(b); o.flush(); o.fd.sync() }
        if (!t.renameTo(f)) { f.delete(); if (!t.renameTo(f)) throw java.io.IOException("no es pot desar " + f.name) }
    }
    fun crc32(b: ByteArray): String { val c = java.util.zip.CRC32(); c.update(b); return "%08X".format(c.value) }

    fun importa(ctx: Context, uri: android.net.Uri): String? {
        motiu = ""
        buidaCaches(); System.gc()                       // les ROM de GBA/DS en memòria ocupen desenes de MB: es buiden abans de llegir-ne una de nova (una ROM de SNES de 4 MB donava OutOfMemoryError)
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return no(t("No s'ha pogut obrir el fitxer"))
        pfd.use {
            java.io.FileInputStream(it.fileDescriptor).channel.use { ch ->
                val cap = java.nio.ByteBuffer.allocate(0x200); ch.position(0); ch.read(cap)
                val h = cap.array()
                val d = dir(ctx); d.mkdirs()
                Consoles.proveidor?.importa(ctx, uri, ch, h)?.let { return it.nom }
                if (Consoles.nomesExperimentals) return no(t("Aquest reproductor només admet els jocs experimentals"))
                // GBA: byte fix 0x96 a 0xB2 i codi de joc a 0xAC
                if (h[0xB2] == 0x96.toByte() && ch.size() < 40_000_000L) {
                    val codi = String(h, 0xAC, 4, Charsets.US_ASCII)
                    val nom = GBA[codi]
                    ch.position(0)
                    val bytes = java.nio.ByteBuffer.allocate(ch.size().toInt()).also { b -> while (b.hasRemaining()) if (ch.read(b) < 0) break }.array()
                    if (nom == null) return importaAltreGba(ctx, uri, bytes, codi) ?: no(t("ROM de GBA") + " «$codi»: " + t("no s'hi ha trobat el motor de so M4A (Sappy)"))      // joc desconegut: només si porta M4A, i la ROM es llegeix d'on és
                    escriuAtomic(File(d, "musica_$codi.gba"), bytes); cache.remove(codi)
                    return nom
                }
                // DS
                val codi = String(h, 0xC, 4, Charsets.US_ASCII)
                val nom = NDS[codi.take(3)]
                val rom = NitroRom(ch)
                if (nom == null) {                                    // joc desconegut: es busquen tots els SDAT i només es desen aquests (Altres ràdios)
                    return try {
                        val titol = nomFitxer(ctx, uri) ?: SdatAuto.titol(rom, h)
                        val icona = rom.icona()
                        // Es treuen els SDAT d'una importació anterior del mateix joc (el principal i els «_2», «_3»...)
                        (d.listFiles { f -> f.name.startsWith("musica_$codi.") || f.name.startsWith("musica_${codi}_") } ?: emptyArray()).forEach { f -> try { f.delete() } catch (e: Exception) { } }
                        cacheSdat.removeIf { it == codi || it.startsWith("${codi}_") }
                        val noms = ArrayList<String>()
                        val trobats = SdatAuto.cadaSdat(ch, rom) { nomSdat, bytes ->
                            File(d, "musica_${codi}_tmp${noms.size}.sdat").writeBytes(bytes); noms.add(nomSdat)
                        }
                        if (trobats == 0) return no(t("ROM de DS") + " «$codi»: " + t("no s'hi ha trobat cap arxiu de so SDAT"))
                        icona?.let { File(d, "musica_$codi.ico").writeBytes(it) }
                        // El més gran passa a ser el principal (`musica_<codi>.sdat`, com sempre); els altres, `_2`, `_3`..., amb el títol «Títol (arxiu)»
                        val ordre = noms.indices.sortedByDescending { File(d, "musica_${codi}_tmp$it.sdat").length() }
                        for ((k, i) in ordre.withIndex()) {
                            val tmp = File(d, "musica_${codi}_tmp$i.sdat")
                            if (k == 0) { tmp.renameTo(File(d, "musica_$codi.sdat")); File(d, "musica_$codi.nom").writeText(titol) }
                            else { tmp.renameTo(File(d, "musica_${codi}_${k + 1}.sdat")); File(d, "musica_${codi}_${k + 1}.nom").writeText(titol + " (" + noms[i] + ")") }
                        }
                        titol
                    } catch (e: Exception) { no(t("No s'ha pogut llegir la ROM") + " (" + e.javaClass.simpleName + ")") }
                }
                // Cada joc porta un arxiu de so amb el seu nom; HeartGold/SoulSilver en porten un segon (`pbr/sound_data.sdat`: el de Diamant/Perla,
                // per a Pal Park), així que NO es pot agafar «el primer .sdat»: es tria pel nom que li toca a aquest joc.
                val esperat = NOM_SDAT[codi.take(3)] ?: return no(t("Format no reconegut"))
                val sdats = rom.llista().filter { f -> f.endsWith(".sdat", ignoreCase = true) }
                val camí = sdats.firstOrNull { f -> f.startsWith("data/sound/") && f.endsWith("/$esperat") }
                    ?: sdats.firstOrNull { f -> f == esperat || f.endsWith("/$esperat") } ?: sdats.singleOrNull() ?: return no(t("DS: el joc no porta l'arxiu de so esperat"))
                val bytes = rom.fitxer(camí) ?: return no(t("DS: no s'ha pogut llegir l'arxiu de so"))
                escriuAtomic(File(d, "musica_$codi.sdat"), bytes); cacheSdat.remove(codi)
                rom.icona()?.let { File(d, "musica_$codi.ico").writeBytes(it) }
                return nom
            }
        }
    }
}
