package cat.gbdsmixxx

/**
 * Drivers guiats per dades (l'autor, 2026-11-15): el que escriu l'analitzador (`AUTO_NES`, `AUTO_GB`, `AUTO_POKE`) es converteix en drivers executables sense fer cap APK.
 * El text el llegeix `DriversDef.parseText`; a l'app es desa en binari (`DriversBin`) i el registre (`RegistreDrivers`) construeix cada driver a demanda amb `construeix`.
 */
object DriversText {
    /** Driver executable d'una definició; `nom` és el nom intern que li ha donat el registre (únic dins del sistema). */
    fun construeix(d: DefDriver, nom: String): Any = when (d) {
        is DefGbPoke -> DriverGbPoke(nom, d.titol, d.crc, d.mbc5, d.f.aGb(), d.base, d.songs, d.noms, d.start?.aGb())
        is DefGbAuto -> DriverGbAuto(nom, d.titol, d.crc, d.mbc5, d.f.aGb(), d.ini?.aGb(), d.p?.aGb(), d.reg, d.ram, d.codis, d.noms)
        is DefNesAuto -> DriverNesAuto(nom, d.titol, d.crc, d.f.aNes(), d.ini?.aNes(), d.p?.aNes(), d.codis, d.noms, d.ram, d.extra)
    }
}
