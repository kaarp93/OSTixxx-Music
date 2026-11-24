package cat.gbdsmixxx

/**
 * Jocs experimentals: els que cal revisar a fons, els fets sense desassemblat i els que s'han de provar amb ROM de l'autor. Es registren a `RegistreDrivers` (tipus EXP).
 * Cada driver nou s'afegeix aquí (llista del seu fitxer) amb la seva entrada de compatibilitat (títol i sistema).
 * Els automàtics de NES (`EXP_NES_AUTO`, 363 jocs) ja no són codi: són al paquet binari de l'app (`assets/drivers.bin`, origen EXP), que genera `eines/drivers_paquet/genera.sh`
 * a partir de `eines/drivers_paquet/ExpNesAuto.kt` (la font que escriu `eines/nes_auto.py`); així no es construeixen tots en arrencar.
 */
object RegistreExperimental {
    private var fet = false
    @Synchronized fun registra() {
        if (fet) return
        fet = true
        RegistreDrivers.registraExp(SistemaDriver.GB, EXP_GB_G1 + EXP_GB_G2 + EXP_GB_G3 + EXP_GB_G4 + EXP_GB_AUTO + EXP_GB_POKE, EXP_COMPAT_G1 + EXP_COMPAT_G2 + EXP_COMPAT_G3 + EXP_COMPAT_G4 + EXP_COMPAT_GB_AUTO + EXP_COMPAT_GB_POKE)
        RegistreDrivers.registraExp(SistemaDriver.NES, EXP_NES_N1 + EXP_NES_N2 + EXP_NES_N3 + EXP_NES_N4 + EXP_NES_MANUAL, EXP_COMPAT_NES + EXP_COMPAT_NES_MANUAL)
        RegistreDrivers.registraExp(SistemaDriver.SNES, EXP_SNES_S + EXP_SNES_S2 + EXP_SNES_S3 + EXP_SNES_S4 + EXP_SNES_S5, EXP_COMPAT_S + EXP_COMPAT_S2 + EXP_COMPAT_S3 + EXP_COMPAT_S4 + EXP_COMPAT_S5)
    }
}

/** Entrades de compatibilitat dels drivers NES experimentals (la clau és `NES_` + nom del driver). */
val EXP_COMPAT_NES: List<JocCompat> = listOf(
    JocCompat("Battle City", "NES", "NES_BattleCityN"),
    JocCompat("Pac-Man", "NES", "NES_PacManN"),
    JocCompat("Mappy", "NES", "NES_MappyN"),
    JocCompat("Tennis", "NES", "NES_TennisN"),
    JocCompat("Yie Ar Kung-Fu", "NES", "NES_YieArKungFuN"),
    JocCompat("Contra Force", "NES", "NES_ContraForceN"),
    JocCompat("Indy Heat", "NES", "NES_IndyHeatN"),
    JocCompat("Solstice", "NES", "NES_SolsticeN"),
    JocCompat("RoboCop 3", "NES", "NES_RoboCop3N"),
    JocCompat("Blaster Master", "NES", "NES_BlasterMasterN", true),
    JocCompat("StarTropics", "NES", "NES_StarTropicsN"),
    JocCompat("Lupin III", "NES", "NES_LupinIIIN"),
)
