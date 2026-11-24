package cat.gbdsmixxx

/**
 * Freqüència de vídeo de NES i SNES (l'autor, 2026-11-19): 60 Hz (NTSC, per defecte) o 50 Hz (PAL), global i en viu. Canvia **només** el ritme al qual s'executa el codi del joc que crida el so
 * a cada fotograma (`drv.frame`) i, a la NES, el rellotge de la CPU de l'APU (1789773 → 1662607 Hz). NO canvia les taules de període del soroll i del DMC de la NES (les PAL són diferents) ni
 * el rellotge de l'SPC700 de la SNES, que no depèn de la regió. Valors PAL estàndard: no comprovats amb cap ROM PAL. L'app el desa a `regio_pal`.
 */
object RegioVideo {
    @Volatile var pal = false
    const val CLAU_ENV = "regio_pal"
    fun fpsNes(): Double = if (pal) 50.0070 else 60.0988
    fun cpuNes(): Double = if (pal) 1662607.0 else 1789773.0
    /**
     * Freqüència de mostreig real de l'APU de la SNES: 32040 Hz (ares, `sfc/system/system.hpp`: `apuFrequency = 32040.0 * 768.0`, la mitjana dels ressonadors
     * ceràmics de les consoles; el valor nominal de fullsnes és 24,576 MHz / 768 = 32000 Hz). No depèn de la regió.
     */
    const val TAUX_SNES = 32040
    /** Fotogrames per segon de la SNES (fullsnes, «NTSC/PAL Timings»: 21,477270 MHz / (262*1364 - 4/2) i 21,281370 MHz / (312*1364)). */
    fun fpsSnes(): Double = if (pal) 50.00697891 else 60.09880627
    /** Mostres de l'APU per fotograma de la SNES (fraccionari: ~533,12 NTSC, ~640,71 PAL). */
    fun mostresFotSnes(): Double = TAUX_SNES / fpsSnes()
    /** Mostres que dura el fotograma `n` (0, 1, 2...): diferència de les vores arrodonides cap avall, perquè la suma de `n` fotogrames no s'allunyi mai més d'una mostra de `n * mostresFotSnes()`. */
    fun mostresDelFotSnes(n: Long): Int { val m = mostresFotSnes(); return (Math.floor((n + 1) * m) - Math.floor(n * m)).toInt() }
}
