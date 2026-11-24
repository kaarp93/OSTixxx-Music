package cat.gbdsmixxx

/**
 * Mode «només experimentals» (`Consoles.nomesExperimentals`). Les llistes de drivers d'abans (`gb`, `nes`, `snes`, `compat`, que es mutaven sense sincronització) ja no hi són:
 * tot passa per `RegistreDrivers` (`registraExp`, `refs`, `llistaGb`...).
 */
object DriversExtra {
    val nomes: Boolean get() = Consoles.nomesExperimentals
}
