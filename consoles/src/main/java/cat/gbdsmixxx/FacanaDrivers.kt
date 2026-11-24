package cat.gbdsmixxx

/**
 * Pont entre l'índex de ROM (bloc de càrrega, `Reconeixedor` / `GestorRoms`) i el registre de drivers (`RegistreDrivers`). Contracte a INTERFICIE_ROM.md i INTERFICIE_DRIVERS.md.
 * Identificador de driver: «tipus:clau:rev» (`DriverRef.id`). Els drivers dels paquets binaris es construeixen només quan es demanen (`gb`/`nes`/`snes`).
 */
object FacanaDrivers {
    /**
     * Clau de les dades de l'usuari d'un àlbum de consola: «CRC:tipus:clau:rev» (l'autor, 2026-11-16, decisió 3: les marques es lliguen al contingut; si l'analitzador
     * regenera un driver amb contingut diferent, rev nova = àlbum «sense provar»). Com que el magatzem de l'usuari fa servir una sola clau per àlbum, les etiquetes, els noms
     * i l'ordre de pista també segueixen la rev (en un AUTO regenerat la numeració de les pistes pot canviar). Els fets a mà tenen rev fixa («1») i no canvien.
     */
    fun clauDades(crcRom: Long, id: String): String = IdDriver.clauMarca(crcRom, id)
    /** «tipus:clau» d'un id (sense la revisió). Ja no es fa servir per a les dades de l'usuari (vegeu `clauDades`). */
    fun senseRev(id: String): String = if (id.count { it == ':' } >= 2) id.substringBeforeLast(':') else id

    /** Vista del registre per a una construcció de biblioteca o una importació. Les consultes van al registre actual (fil-segur). */
    class Conjunt internal constructor() : Reconeixedor {
        private fun sis(c: String): SistemaDriver? = when (c) { "GB" -> SistemaDriver.GB; "NES" -> SistemaDriver.NES; "SNES" -> SistemaDriver.SNES; else -> null }
        override fun perCrc(consola: String, crc: Long): List<String> { val s = sis(consola) ?: return emptyList(); return RegistreDrivers.candidatsPerCrc(crc).filter { it.sistema == s }.map { it.id } }
        override fun perSignatura(consola: String): List<String> { val s = sis(consola) ?: return emptyList(); return RegistreDrivers.perSignatura(s).map { it.id } }
        /** Només pel CRC32 de la ROM (l'autor, 2026-11-17): la signatura de bytes no activa cap driver. */
        override fun reconeix(consola: String, id: String, bytes: ByteArray): Boolean {
            val r = RegistreDrivers.perId(id) ?: return false
            return r.sistema == sis(consola) && RegistreDrivers.reconeix(r, bytes)
        }
        /** Abans alliberava la ROM de l'última avaluació per signatura; ara no en guarda cap. */
        fun oblida() {}
        fun ref(id: String): DriverRef? = RegistreDrivers.perId(id)
        fun gb(id: String): GbDriver? = RegistreDrivers.carregaGb(id)
        fun nes(id: String): NesDriver? = RegistreDrivers.carregaNes(id)
        fun snes(id: String): SnesDriver? = RegistreDrivers.carregaSnes(id)
    }

    fun conjunt(): Conjunt = Conjunt()
}
