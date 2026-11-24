package cat.gbdsmixxx

/**
 * Identitat dels drivers (l'autor, 2026-11-16): `id` = «tipus:clau:rev».
 * - CUSTOM: fets a mà (DriverKirby, DriverZelda...): clau = nom del driver, rev = revisió manual (`RegistreDrivers.REVISIONS`, per defecte «1»).
 * - AUTO: els de text de l'analitzador (AUTO_POKE, AUTO_GB, AUTO_NES): clau = «CRC.format» (p. ex. «1226499E.poke»), rev = CRC32 del contingut que fa sonar (sense els noms de les cançons).
 * - EXP: experimentals compilats (ExpGbG*, ExpNes*...; els automàtics experimentals viuen al paquet binari amb origen EXP): clau = nom o «CRC.format», rev igual que dalt.
 * Una marca (✓/✗) es lliga a (CRC de la ROM, id): si l'id canvia (rev nova), la marca no hi és i el driver torna a «sense provar». Vegeu `INTERFICIE_DRIVERS.md`.
 */
enum class TipusDriver(val codi: String, val etiqueta: String) {
    CUSTOM("custom", "Custom"), AUTO("auto", "Auto"), EXP("exp", "Exp");
    companion object {
        fun deCodi(c: String): TipusDriver? = values().firstOrNull { it.codi == c }
    }
}

enum class SistemaDriver(val prefix: String) { GB("GB_"), NES("NES_"), SNES("SNES_") }

/**
 * Referència a un driver (dades pures; el driver executable es construeix amb `RegistreDrivers.carrega`).
 * `nom` = nom intern únic dins del sistema (la clau de l'àlbum és `clauAlbum` = prefix + nom, la mateixa d'abans per als drivers que ja existien);
 * `crcs` = CRC32 de les ROM que reconeix (buit = no s'activa mai: l'autor, 2026-11-17, tots els drivers van per CRC); `ordre` = ordre estable (per numerar «Auto 1», «Auto 2»...).
 */
class DriverRef(val sistema: SistemaDriver, val tipus: TipusDriver, val clau: String, val rev: String, val nom: String, val titol: String, val subsistema: String, val crcs: Set<Long>, val ordre: Int) {
    val id: String = tipus.codi + ":" + clau + ":" + rev
    val clauAlbum: String get() = sistema.prefix + nom
    /** Clau de la marca d'aquest driver per a la ROM `crcRom` (CRC32 del fitxer sencer). */
    fun clauMarca(crcRom: Long): String = IdDriver.clauMarca(crcRom, id)
    override fun toString() = id
    override fun equals(other: Any?) = other is DriverRef && other.id == id && other.sistema == sistema
    override fun hashCode() = id.hashCode() * 31 + sistema.hashCode()
}

object IdDriver {
    /** «AAAAAAAA:tipus:clau:rev»: la clau de les marques (i de la resta de dades de l'usuari) d'un àlbum de consola (`Biblioteca.clausEstables`). */
    fun clauMarca(crcRom: Long, id: String): String = "%08X".format(crcRom and 0xFFFFFFFFL) + ":" + id
    /** (tipus, clau, rev) d'un id, o null si no té el format. La clau pot contenir «:» només si no és l'últim camp; la rev és sempre l'últim. */
    fun parteix(id: String): Triple<TipusDriver, String, String>? {
        val a = id.indexOf(':'); val b = id.lastIndexOf(':')
        if (a <= 0 || b <= a + 1 || b == id.length - 1) return null
        val t = TipusDriver.deCodi(id.substring(0, a)) ?: return null
        return Triple(t, id.substring(a + 1, b), id.substring(b + 1))
    }
    fun hex8(v: Int): String = "%08X".format(v.toLong() and 0xFFFFFFFFL)
}
