package cat.gbdsmixxx

/**
 * Drivers de NES fets a mà a partir d'un desassemblat, amb la mateixa classe que els automàtics (`DriverNesAuto`).
 * Castlevania II: Simon's Quest (USA, CRC32 38E82FAB): de FoxCunning/Castlevania2 (`bank_7.asm` i `bank_0.asm`): el motor és el de Castlevania I; `AnyBankPlayTracks` ($C118, A = índex de so)
 * engega el so i `NMI_SoundCodeCall` ($C0FB) és el motor per fotograma; els índexs de cançó (4 canals cadascuna; l'índex és el número de registre + 1, perquè `Sound_Records_Locator` apunta 3 bytes abans de la taula) són $39 Town, $3D Wilderness, $41 Night,
 * $45 Mansion, $49 Ruins, $4D Dracula, $51 Game Over, $55 Password i $59 Ending. Comprovat amb una ROM muntada del mateix repo (les 9 cançons sonen amb els 5 canals i el joc real crida $C118 amb $55 i $39). Els bancs (MMC1: finestra de PRG 0, mode 3) són els de l'NMI del report de l'analitzador. No comprovat amb cap ROM.
 */
private val MMC1_BANC0 = intArrayOf(0x10, 0x1E, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
val EXP_NES_MANUAL: List<DriverNesAuto> = listOf(
    DriverNesAuto("Auto_38E82FAB", "Castlevania II: Simon's Quest", 0x38E82FABL, RutinaNes(0xC0FB, MMC1_BANC0), null, RutinaNes(0xC118, MMC1_BANC0),
        intArrayOf(0x39, 0x3D, 0x41, 0x45, 0x49, 0x4D, 0x51, 0x55, 0x59),
        listOf("Town", "Wilderness", "Night", "Mansion", "Ruins", "Dracula", "Game Over", "Password", "Ending")),
)
val EXP_COMPAT_NES_MANUAL: List<JocCompat> = EXP_NES_MANUAL.map { JocCompat(it.titol, "NES", "NES_" + it.nom, true) }
