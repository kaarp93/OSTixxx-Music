package cat.gbdsmixxx

/**
 * Jocs de Game Boy amb el motor de so desassemblat per turboboy215 / Will Trowbridge (repos «<joc>-Audio-Disasm»: el codi de so i les dades extrets de la ROM original, amb les adreces
 * originals, i una ROM de prova que els toca). El motor és a la finestra $4000-$7FFF d'un banc que no coneixem (a la ROM de prova és el 1): es busca pel contingut (finestres de bytes del codi del motor, a l'adreça
 * en què el desassemblat el posa) i s'hi executa amb `GbCpu`: inici, petició de cançó (A = número) i el que el joc crida a cada fotograma. **Verificat només amb la ROM de prova del desassemblat (no
 * amb la ROM original del joc, que no tinc).**
 */
/** El mateix que `r2gBanc(m.rom, sig)`, però la cerca es fa un sol cop per memòria (la ROM no canvia) i no a cada fotograma. */
fun r2gBanc(m: GbMem, sig: Array<Pair<Int, String>>): Int = m.cauBancs[sig] ?: r2gBanc(m.rom, sig).also { m.cauBancs[sig] = it }
fun r2gBanc(rom: ByteArray, sig: Array<Pair<Int, String>>): Int {
    val nb = rom.size / 0x4000
    for (bk in 1 until nb) {
        var ok = true
        for ((off, hex) in sig) {
            val o = bk * 0x4000 + off
            if (o + hex.length / 2 > rom.size) { ok = false; break }
            for (k in 0 until hex.length / 2) if ((rom[o + k].toInt() and 255) != hex.substring(2 * k, 2 * k + 2).toInt(16)) { ok = false; break }
            if (!ok) break
        }
        if (ok) return bk
    }
    return -1
}

open class R2gDriver(
    override val nom: String, private val sig: Array<Pair<Int, String>>, private val base: Int, private val ini: Int, private val cant: Int,
    private val play: IntArray, override val ids: IntRange, val NOMS: List<String>,
) : GbDriver {
    override fun reconeix(rom: ByteArray): Boolean = rom.size >= 0x8000 && r2gBanc(rom, sig) > 0
    protected fun banc(m: GbMem): Int = r2gBanc(m, sig)
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF
        m.banc = banc(m); cpu.crida(base + ini)
        m.banc = banc(m); cpu.a = id; cpu.crida(base + cant)
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.sp = 0xDFFF; m.banc = banc(m); for (p in play) { cpu.crida(base + p); m.banc = banc(m) } }
}

/** Mega Man 3 (Game Boy). Motor a $4000 del banc d'àudio: inici +$6, petició de cançó +$3, fotograma +$0. Cançons 59 a 82. */
object DriverMegaMan3GB : R2gDriver("MegaMan3GB", arrayOf(0 to "C3CF71C3FF70C3537AC30F40", 48 to "413F403D48494A474D4D4E42", 112 to "4C415041544158415C416041", 176 to "E441FE41084212423A421C42", 256 to "EF42C0004343EF42C0005E43"), 0x4000, 6, 3, intArrayOf(0), 59..82, listOf("Stage Select", "Stage Start", "Shadowman", "Boss1", "Geminiman", "Snakeman", "Sparkman", "End Game", "Game Over", "Boss2", "Victory", "Credits", "Dustman", "Diveman", "Skullman", "Drillman", "Wily Intro", "Final Victory", "Wily1", "Wily2", "Punk", "Get Weapon", "Title", "Password"))

/** Bionic Commando (Game Boy). Motor a $4000 del banc d'àudio: inici +$6, petició de cançó +$3, fotograma +$0. Cançons 44 a 60. */
object DriverBionicCommando : R2gDriver("BionicCommando", arrayOf(0 to "C3626EC3926DC30977C32140", 48 to "677EC3AD012E2F2D302E2D2E", 112 to "05AC05CE05ED050B06270642", 176 to "07A207A707AC07B107B607BA", 256 to "0243025702FFD70070871181"), 0x4000, 6, 3, intArrayOf(0), 44..60, listOf("So 44", "So 45", "So 46", "So 47", "So 48", "So 49", "So 50", "So 51", "So 52", "So 53", "So 54", "So 55", "So 56", "So 57", "So 58", "So 59", "So 60"))

/** Alfred Chicken (Game Boy). Motor a $4100 del banc d'àudio: inici +$6, petició de cançó +$0, fotograma +$3. Cançons 0 a 8. */
object DriverAlfredChicken : R2gDriver("AlfredChicken", arrayOf(256 to "C31541C36242C31342C3B341", 304 to "20FC21AB50092AEA04DFEA05", 368 to "DF131AEA2EDF2AEA3FDF5F2A", 432 to "00DFC9FA0CDFA72859FA00DF", 512 to "1EFA5CDFE021FA5DDFE0223E"), 0x4100, 6, 0, intArrayOf(3), 0..8, listOf("Title", "Start", "Game Over", "Ingame1", "Ingame2", "Radio", "Meka Chicken", "End Game", "Mr Peckles"))

/** The Lion King (Game Boy). Motor a $4000 del banc d'àudio: inici +$6, petició de cançó +$0, fotograma +$3. Cançons 0 a 13. */
object DriverLionKingGB : R2gDriver("LionKingGB", arrayOf(0 to "C31540C36241C31341C3B340", 48 to "20FC21274E092AEA04C7EA05", 112 to "C7131AEA2EC72AEA3FC75F2A", 176 to "00C7C9FA0CC7A72859FA00C7", 256 to "1EFA5CC7E021FA5DC7E0223E"), 0x4000, 6, 0, intArrayOf(3), 0..13, listOf("Cant Wait", "Hakuna Matata", "Love Tonight", "Circle Of Life", "Be Prepared", "Hoo Hah", "This Land", "Jingle", "To Die For", "Exile", "Under The Stars", "Bug Toss", "Death Tag", "King Tag"))

/** R-Type (Game Boy). Motor a $4000 del banc d'àudio: inici +$6, petició de cançó +$0, fotograma +$3. Cançons 0 a 7. */
object DriverRType : R2gDriver("RType", arrayOf(0 to "C31540C33241C31041C3B040", 48 to "A34C092AEA04D0EA05D02AEA", 112 to "EA2FD02AEA40D05F2AEA41D0", 176 to "FA0CD0A72859FA00D0A72053", 256 to "D0E021FA5ED0E0223E80E023"), 0x4000, 6, 0, intArrayOf(3), 0..7, listOf("Ingame1", "Boss", "Title", "Credit", "Victory", "End Game", "Ingame2", "Ingame1 Restart"))

/** R-Type II (Game Boy). Motor a $4000 del banc d'àudio: inici +$6, petició de cançó +$0, fotograma +$3. Cançons 0 a 8. */
object DriverRType2 : R2gDriver("RType2", arrayOf(0 to "C31540C36241C31341C3B340", 48 to "20FC21E04F092AEA04D7EA05", 112 to "D7131AEA2ED72AEA3FD75F2A", 176 to "00D7C9FA0CD7A72859FA00D7", 256 to "1EFA5CD7E021FA5DD7E0223E"), 0x4000, 6, 0, intArrayOf(3), 0..8, listOf("Continue", "Ingame1", "Ingame2", "Credit", "Victory", "End Game", "Game Over", "Boss", "Title"))

/** Disney's Aladdin (Game Boy). Motor a $4000 del banc d'àudio: inici +$0, petició de cançó +$6, fotograma +$C i +$F. Cançons 0 a 6. */
object DriverAladdinGB : R2gDriver("AladdinGB", arrayOf(0 to "C3DE40C3D947C32641C32B40", 48 to "48C9F024E6072807FE012803", 112 to "E017E01CE021EA76DDC93EFF", 176 to "6F3001247EFEFF2803CDD840", 256 to "EA18DDEA30DDEA48DD3EFFEA"), 0x4000, 0, 6, intArrayOf(12, 15), 0..6, listOf("Prince Ali", "Arabic Ne Naw", "Do Not Use", "One Jump Ahead", "New World", "Level Complete", "Empty"))

/** Star Wars (Game Boy). Motor a $61FF del banc d'àudio: inici +$0, petició de cançó +$6, fotograma +$C i +$F. Cançons 0 a 8. */
object DriverStarWarsGB : R2gDriver("StarWarsGB", arrayOf(8703 to "C3267CC36E7CC3BB63C32062", 8751 to "2803D60147F024E670281BFE", 8815 to "EADEDFC93EFFE025F024E607", 8879 to "B6E5123C6489ADCEEE0C2842", 8959 to "FAFA00010101020202030303"), 0x61FF, 0, 6, intArrayOf(12, 15), 0..8, listOf("Title", "Landspeeder", "Cave", "Mos Eisley", "Cantina", "Do Not Use1", "Do Not Use2", "Xwing", "End Game"))

private val EWJSIG: Array<Pair<Int, String>> = arrayOf(0 to "C30940C3B140C386402100C5", 48 to "C5EA80C5EABFC5EACEC53E08", 112 to "16E01BE0203E20E01C3E77E0", 176 to "C9FA00C5FEFF2808CDF1403E", 256 to "67694E232AFEFF2807EA03C5")
private val SB2SIG: Array<Pair<Int, String>> = arrayOf(0 to "C30C40C3D340C3D544C3FA40", 48 to "46DEEA7EDEEA37DEEA6FDEEA", 112 to "3E20E01CAFE01BE0203E77E0", 176 to "C840CDC8402B2BCDC840CDC8", 256 to "FAE1DEE680110000010001CD")
private val BMSIG: Array<Pair<Int, String>> = arrayOf(0 to "F080FE112008F0EF3CE0EFCB", 48 to "11B0FF06082A12130520FA3E", 112 to "D93EC0E0E1AFE0DDE0C8E0C9", 176 to "E01C3E80E01E3E8FEA00D0FA", 256 to "41F0B06FF0B1672A4FCB37E6")
private val EWJ_NOMS: List<String> = listOf("End Game", "Race Win", "Race Lose", "Game Over", "New Junk City", "Snot A Problem", "For Petes Sake", "Buttville2", "Buttville1", "Level5", "Andy Asteroids", "Lights Out", "What The Heck", "Do Not Use")
private val SB2_NOMS: List<String> = listOf("Title", "Fanfare1", "Fanfare2")
private val BM_NOMS: List<String> = listOf("So 0", "So 1", "So 2", "So 3", "So 4", "So 5", "So 6", "So 7", "So 8", "So 9", "So 10", "So 11", "So 12", "So 13", "So 14", "So 15")
private val MegaMan1GB_S1: Array<Pair<Int, String>> = arrayOf(0 to "C38240C32343C3DD40C3DF40", 48 to "521E53535311546251F45377", 112 to "78635B905BBD5BF53A121B0B", 176 to "F13E80E0263EEEE0253E10E0", 256 to "B6DCCD1C41380CCDC8423807")
private val MegaMan1GB_S2: Array<Pair<Int, String>> = arrayOf(0 to "C39040C33143C3EB40C3ED40", 48 to "522C5361531F547051025485", 112 to "6E715B9E5BCB5B0500000004", 176 to "F1C9F5FA31C1EA32C13EFFEA", 256 to "31C1F17DEAB7DC7CEAB8DC23")
private val MegaMan4GB_S1: Array<Pair<Int, String>> = arrayOf(0 to "C39144C3B843C3434DC30C40", 48 to "165B7AEAE0D1216EDE7E3600", 112 to "6762FF0000FF6700626EFFFF", 176 to "1A422442AC41B041B441B841", 256 to "6C42724276427C4280428642")
private val MegaMan4GB_S2: Array<Pair<Int, String>> = arrayOf(0 to "C3CE43C3F542C3804CC30C40", 48 to "41334137413D414141454149", 112 to "41DB41DF41E541E941EF41F3", 256 to "4E1B4E8000334EC000404E09")
private val MegaMan5GB_S1: Array<Pair<Int, String>> = arrayOf(0 to "C37844C39F43C32D4DC33540", 48 to "787879FF782157DE7E3600B7", 112 to "CDC501F1EA58DECD2202C325", 176 to "41D141D541DB41DF41E341E7", 256 to "424342C542B142B542B942BD")
private val MegaMan5GB_S2: Array<Pair<Int, String>> = arrayOf(0 to "C31644C33D43C3CB4CC30C40", 48 to "41514155415B415F41634167", 112 to "41FF41094203421D42214219", 176 to "426142254125412541254125", 256 to "42F74201430B431543334300")

/** Dos bancs d'àudio amb una còpia del motor cada un (Mega Man IV i V; Dr. Wily's Revenge): cada cançó és a un dels dos bancs; el joc canvia de banc, reinicia el motor i demana la cançó. `map` = parells (banc 1|2, número dins del banc). */
open class R2gDosBancs(
    override val nom: String, private val sig1: Array<Pair<Int, String>>, private val sig2: Array<Pair<Int, String>>, private val map: IntArray, private val mm1: Boolean,
    override val ids: IntRange, val NOMS: List<String>,
) : GbDriver {
    override fun reconeix(rom: ByteArray): Boolean = rom.size >= 0xC000 && r2gBanc(rom, sig1) > 0 && r2gBanc(rom, sig2) > 0
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        val k = (id - ids.first).coerceIn(0, map.size / 2 - 1)
        val banc = r2gBanc(m, if (map[2 * k] == 1) sig1 else sig2); val num = map[2 * k + 1]
        cpu.sp = 0xDFFF
        m.banc = banc
        if (mm1) {
            cpu.crida(0x4003); cpu.crida(0x4000)                              // desactiva el so, l'inicia
            val p = 0x4013 + 2 * (num + 1)                                    // taula de cançons del motor
            cpu.l = m.rb(p); cpu.h = m.rb(p + 1); cpu.crida(0x4006)           // carrega la cançó (HL)
        } else {
            cpu.crida(0x4006); cpu.a = num; cpu.crida(0x4003)                 // inici i petició
        }
    }
    override fun frame(cpu: GbCpu, m: GbMem, n: Long) { cpu.sp = 0xDFFF; cpu.crida(if (mm1) 0x400F else 0x4000) }      // el banc queda el de la cançó
}

/** Earthworm Jim (Game Boy), motor de Martin Walker: inici +$0, fotograma +$3; la música es demana escrivint-ne el número a $C501 (la RAM del motor). */
object DriverEarthwormJim : R2gDriver("EarthwormJim", EWJSIG, 0x4000, 0, 0, intArrayOf(3), 0..13, EWJ_NOMS) {
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) { cpu.sp = 0xDFFF; m.banc = banc(m); cpu.crida(0x4000); m.wb(0xC501, id) }
}

/** Speedball 2: Brutal Deluxe (Game Boy), motor de Martin Walker: inici +$0 (E = 0 dades del títol, 1 dades de joc), cançó +$6 (A = número, un menys si no és el títol), fotograma +$9 a 50 Hz. */
object DriverSpeedball2 : R2gDriver("Speedball2", SB2SIG, 0x4000, 0, 6, intArrayOf(9), 0..2, SB2_NOMS) {
    override val fps = 50.0
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) {
        cpu.sp = 0xDFFF; m.banc = banc(m)
        cpu.e = if (id == 0) 0 else 1; cpu.crida(0x4000)
        m.banc = banc(m); cpu.a = if (id == 0) 0 else id - 1; cpu.crida(0x4006)
    }
}

/** Blaster Master: Enemy Below (Game Boy Color), motor de Hirotomo Nakamura: inici +$0, fotograma +$E; la música es demana escrivint-ne el número a $D000 (la RAM del motor). */
object DriverBlasterMaster : R2gDriver("BlasterMaster", BMSIG, 0x4000, 0, 0, intArrayOf(0xE), 0..15, BM_NOMS) {
    override fun inicia(cpu: GbCpu, m: GbMem, id: Int) { cpu.sp = 0xDFFF; m.banc = banc(m); cpu.crida(0x4000); m.wb(0xD000, id) }
}

/** Mega Man: Dr. Wily's Revenge (Game Boy), motor de Kouji Murata / Hiroshi Wada: dos bancs de so, 18 cançons (0 a 17). */
object DriverMegaMan1GB : R2gDosBancs("MegaMan1GB", MegaMan1GB_S1, MegaMan1GB_S2, intArrayOf(1, 20, 1, 21, 1, 22, 1, 23, 1, 24, 1, 25, 1, 33, 1, 34, 2, 35, 1, 36, 2, 37, 1, 38, 2, 39, 2, 40, 2, 41, 2, 44, 2, 45, 1, 45), true, 0..17, listOf("Cutman", "Iceman", "Elecman", "Fireman", "Boss", "Victory", "Wily1", "Wily Unknown", "Stage Start", "Wily2", "Stage Select", "Wily Intro", "End Game", "Get Weapon", "Title", "Unknown", "Password", "End Game Alt"))

/** Mega Man IV (Game Boy), motor de Kouji Murata / Hiroshi Wada: dos bancs de so, 41 cançons (0 a 40). */
object DriverMegaMan4GB : R2gDosBancs("MegaMan4GB", MegaMan4GB_S1, MegaMan4GB_S2, intArrayOf(1, 71, 1, 72, 1, 73, 1, 74, 1, 75, 1, 76, 1, 77, 1, 78, 1, 79, 1, 80, 1, 81, 1, 82, 1, 83, 1, 84, 1, 85, 1, 86, 1, 87, 1, 88, 1, 89, 1, 90, 1, 91, 1, 92, 2, 93, 2, 94, 2, 95, 2, 96, 2, 97, 2, 98, 2, 99, 2, 100, 2, 101, 2, 102, 2, 103, 2, 104, 2, 105, 2, 106, 2, 107, 2, 108, 2, 109, 2, 110, 2, 111), false, 0..40, listOf("Brightman", "Toadman", "Ringman", "Pharaohman", "Stoneman", "Crystalman", "Chargeman", "Napalmman", "Boss2", "Protoman", "Stage Select1", "Stage Select2", "Password", "Stage Start", "Game Over", "Victory", "Final Victory", "Get Weapon", "Get Beat", "Wily Intro", "Ballade", "Boss1", "Title", "End Game", "Wily Scene1 Alt", "Wily Scene2", "Wily Boss Alt", "Wily Boss", "Wily Scene1", "Get Beat Alt", "Shop Alt", "Intro", "Wily", "Credits", "Shop", "Wily Escape", "End Wind1", "End Alarm1", "End Explode", "End Alarm2", "End Wind2"))

/** Mega Man V (Game Boy), motor de Kouji Murata / Hiroshi Wada: dos bancs de so, 33 cançons (0 a 32). */
object DriverMegaMan5GB : R2gDosBancs("MegaMan5GB", MegaMan5GB_S1, MegaMan5GB_S2, intArrayOf(1, 89, 1, 91, 1, 92, 1, 93, 1, 94, 1, 95, 1, 96, 1, 97, 1, 98, 1, 99, 1, 100, 1, 101, 1, 102, 1, 103, 1, 108, 2, 109, 2, 110, 2, 111, 2, 112, 2, 113, 2, 114, 2, 115, 2, 116, 2, 117, 2, 118, 2, 119, 2, 120, 2, 121, 2, 122, 2, 123, 2, 124, 2, 125, 2, 126), false, 0..32, listOf("Stage Select", "Wily Intro", "Uranus", "Rush Spaceship", "Neptune", "Cutscene Alt", "Boss", "Saturn", "Mercury", "Cutscene", "Mars", "Jupiter", "Venus Alt", "Pluto", "Wily", "Intro 1", "Stage Start", "Game Over", "Victory", "Final Victory A", "Get Weapon", "End Game", "Victory Alt", "Title Alt", "Title", "Terra", "Shop", "Light Intro", "Intro2", "Password", "Sunstar", "Final Victory", "Venus"))

val DRIVERS_GB_R2GA: List<GbDriver> = listOf(DriverMegaMan3GB, DriverBionicCommando, DriverAlfredChicken, DriverLionKingGB, DriverRType, DriverRType2, DriverAladdinGB, DriverStarWarsGB, DriverMegaMan1GB, DriverMegaMan4GB, DriverMegaMan5GB, DriverEarthwormJim, DriverSpeedball2, DriverBlasterMaster)
