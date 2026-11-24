package cat.gbdsmixxx

/**
 * Nom de l'àlbum quan un joc té més d'un driver (l'autor, 2026-11-16): «Joc - Custom», «Joc - Auto», «Joc - Exp»; si n'hi ha més d'un del mateix tipus, «Joc - Auto 1», «Joc - Auto 2»...
 * (numerats per `DriverRef.ordre` i després per id: el mateix joc dona sempre els mateixos números). Amb un sol driver, o amb l'interruptor d'Ajustos apagat, només el títol.
 */
object EtiquetaDriver {
    /** Interruptor d'Ajustos («Mostra el tipus de driver als àlbums»): el posa Music en arrencar (Env `drivers_etiqueta`, 1 per defecte). */
    @Volatile var mostra: Boolean = true
    /** Clau d'`Env` de l'interruptor. */
    const val CLAU_ENV = "drivers_etiqueta"

    /**
     * Funció pura. `driversDelJoc` = tots els drivers que reconeixen aquesta ROM (inclòs `driver`); `tr` tradueix «Custom»/«Auto»/«Exp» (a l'app, `t`).
     */
    fun nom(titol: String, driversDelJoc: List<DriverRef>, driver: DriverRef, mostra: Boolean, tr: (String) -> String = { it }): String {
        val tots = if (driversDelJoc.any { it.id == driver.id }) driversDelJoc else driversDelJoc + driver
        val unics = tots.distinctBy { it.id }
        if (!mostra || unics.size <= 1) return titol
        val mateix = unics.filter { it.tipus == driver.tipus }.sortedWith(compareBy<DriverRef>({ it.ordre }, { it.id }))
        val et = tr(driver.tipus.etiqueta)
        return if (mateix.size <= 1) "$titol - $et" else "$titol - $et ${mateix.indexOfFirst { it.id == driver.id } + 1}"
    }
}
