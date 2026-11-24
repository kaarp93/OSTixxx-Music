package cat.gbdsmixxx

import android.content.Context
import java.io.File

/**
 * Les partitures a l'app (l'autor, 2026-11-23): els fitxers `.oxpt` que generen els analitzadors de PC s'importen com la resta de fitxers i es desen a `musica/partitures/`
 * (un per ROM: `<CRC32>_<mida>.oxpt`). Sense la ROM exacta no es poden desxifrar (`Partitures`); amb ella, la cançó se sintetitza només amb l'APU, sense CPU ni driver.
 */
object PartituresApp {
    private fun dir(ctx: Context) = File(RomsMusica.dir(ctx), "partitures")
    private fun fitxer(ctx: Context, crc: Long, mida: Int) = File(dir(ctx), "%08X_%d.oxpt".format(crc, mida))

    /** Importa un fitxer de partitures (bytes ja llegits). Retorna el text del resultat o null + `RomsMusica.motiu`. */
    fun importa(ctx: Context, bytes: ByteArray): String? {
        val cap = Partitures.capcalera(bytes) ?: return RomsMusica.no(t("Fitxer de partitures no vàlid"))
        val d = dir(ctx); d.mkdirs()
        RomsMusica.escriuAtomic(fitxer(ctx, cap.crc, cap.mida), bytes)
        synchronized(cau) { cau.remove(cap.crc to cap.mida) }
        val sis = when (cap.sistema) { Partitures.NES -> "NES"; Partitures.GBC -> "GBC"; else -> "GB" }
        return t("Partitures de") + " " + sis + " (CRC32 %08X)".format(cap.crc) + " · " + t("només es fan servir amb la seva ROM")
    }

    private val cau = LinkedHashMap<Pair<Long, Int>, Partitura?>()

    /** La partitura d'aquesta ROM (desxifrada), si n'hi ha i la ROM és la seva; es recorda en memòria. */
    fun de(ctx: Context, crc: Long, mida: Int, rom: ByteArray): Partitura? {
        val k = crc to mida
        synchronized(cau) { if (cau.containsKey(k)) return cau[k] }
        val f = fitxer(ctx, crc, mida)
        val p = try { if (f.isFile) Partitures.llegeix(f.readBytes(), rom) else null } catch (e: Throwable) { null }
        synchronized(cau) { cau[k] = p; while (cau.size > 4) cau.remove(cau.keys.first()) }
        return p
    }
    fun canco(ctx: Context, crc: Long, mida: Int, rom: ByteArray, driver: String, id: Int): CancoPart? = de(ctx, crc, mida, rom)?.canco(driver, id)
    fun buida(ctx: Context) { synchronized(cau) { cau.clear() } }
}
