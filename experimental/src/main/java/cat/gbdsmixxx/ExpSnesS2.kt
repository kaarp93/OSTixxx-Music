package cat.gbdsmixxx

/*
 * Lot S, part 2 (experimental, OSTixxx Debug): «driver mínim per adreça» per als jocs de SNES del framework de Yoshifanatic1 (etiquetes DATA_BBAAAA = adreça SNES de la ROM) i d'altres repos
 * amb etiquetes d'adreça. Una pista per cada adreça de dades de so trobada (motor, bancs de dades, músiques; les mostres BRR soles no són cançons i no hi són). Cada pista puja a l'SPC700, en
 * format IPL (mida, destí, dades; mida 0 = entrada), el motor del joc (els fluxos marcats com a motor) i després el flux de la pista, i arrenca a l'entrada del motor.
 * DEDUÏT: que cada fitxer és una cadena de blocs en format IPL; els que no ho són (músiques sense capçalera, blocs que el joc puja amb el seu protocol de comandes, dades comprimides) no
 * sonaran. NO es fa cap ordre de cançó pels ports (el protocol de cada motor és diferent i no està portat). No comprovat amb cap ROM.
 */

/** Reconeix la ROM pel títol intern (algun dels fragments, en majúscules). */
internal fun expTitolConte(rom: RomSnes, vararg frags: String): Boolean {
    val t = rom.titol.uppercase()
    return frags.any { t.contains(it) }
}

private fun expCadenaBlocs(spc: SnesSpc, rom: RomSnes, adr: Int): Int {
    val o = rom.fitxer((adr shr 16) and 0xFF, adr and 0xFFFF)
    if (o < 0) return -1
    val d = rom.d
    fun u16(p: Int) = (d[p].toInt() and 255) or ((d[p + 1].toInt() and 255) shl 8)
    var p = o; var n = 0
    while (p + 4 <= d.size && n++ < 512) {
        val len = u16(p); val ad = u16(p + 2); p += 4
        if (len == 0) return ad
        if (p + len > d.size) return -1
        for (k in 0 until len) spc.ram[(ad + k) and 0xFFFF] = d[p + k]
        p += len
        // els blocs creuen el límit de banc: en LoROM/HiROM l'offset de fitxer segueix de manera lineal (com un punter seqüencial)
    }
    return -1
}

/** `pistes` = (adreça SNES de 24 bits, és motor). El motor (tots els fluxos marcats) es puja primer; després el flux de la pista; l'entrada és la del primer motor que la dona. */
internal fun expCadenaInicia(spc: SnesSpc, rom: RomSnes, pistes: List<Pair<Int, Boolean>>, id: Int) {
    val p = pistes[id.coerceIn(1, pistes.size) - 1]
    var entrada = -1
    for (m in pistes) if (m.second) { val e = expCadenaBlocs(spc, rom, m.first); if (entrada < 0 && e >= 0) entrada = e }
    if (!p.second) { val e = expCadenaBlocs(spc, rom, p.first); if (entrada < 0 && e >= 0) entrada = e }
    spc.arrenca(if (entrada >= 0) entrada else 0x0200)
}

object DriverMsPacMan : SnesDriver {
    override val nom = "MsPacMan"
    private val PISTES = listOf(
        Triple("DATA_828342 · \$82:8342 · Routine_Macros:13579", 0x828342, false),
        Triple("%MSPM_HandleSplitSPCEngine() · \$83:8C82 · Routine_Macros:15694", 0x838C82, true),
        Triple("DATA_83A389 · \$83:A389 · Routine_Macros:15702", 0x83A389, false),
        Triple("DATA_83A43F · \$83:A43F · Routine_Macros:15705", 0x83A43F, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "MSPACMAN")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverFrogger : SnesDriver {
    override val nom = "Frogger"
    private val PISTES = listOf(
        Triple("DATA_8E8008 · \$8E:8008 · Routine_Macros:20761", 0x8E8008, false),
        Triple("DATA_8E820A · \$8E:820A · Routine_Macros:20765", 0x8E820A, false),
        Triple("DATA_8F8F1C · \$8F:8F1C · Routine_Macros:20785", 0x8F8F1C, false),
        Triple("%FROGGER_HandleSplitSPCEngine() · \$8F:D461 · Routine_Macros:20789", 0x8FD461, true))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "FROGGER")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverPacMan2 : SnesDriver {
    override val nom = "PacMan2"
    private val PISTES = listOf(
        Triple("dd PM2_SPCEngine · \$81:8546 · Routine_Macros:4784", 0x818546, true),
        Triple("DATA_AB8000 · \$AB:8000 · Routine_Macros:116632", 0xAB8000, false),
        Triple("DATA_AC8371 · \$AC:8371 · Routine_Macros:116635", 0xAC8371, false),
        Triple("DATA_ACB47D · \$AC:B47D · Routine_Macros:116642", 0xACB47D, false),
        Triple("DATA_ACC0EE · \$AC:C0EE · Routine_Macros:116645", 0xACC0EE, false),
        Triple("DATA_ACC555 · \$AC:C555 · Routine_Macros:116648", 0xACC555, false),
        Triple("DATA_ACCC7B · \$AC:CC7B · Routine_Macros:116651", 0xACCC7B, false),
        Triple("DATA_ACD237 · \$AC:D237 · Routine_Macros:116654", 0xACD237, false),
        Triple("DATA_ACD7A0 · \$AC:D7A0 · Routine_Macros:116657", 0xACD7A0, false),
        Triple("DATA_ACDFC3 · \$AC:DFC3 · Routine_Macros:116660", 0xACDFC3, false),
        Triple("DATA_ACE521 · \$AC:E521 · Routine_Macros:116663", 0xACE521, false),
        Triple("DATA_ACEDE3 · \$AC:EDE3 · Routine_Macros:116666", 0xACEDE3, false),
        Triple("DATA_ACF0D2 · \$AC:F0D2 · Routine_Macros:116669", 0xACF0D2, false),
        Triple("DATA_ACF4E9 · \$AC:F4E9 · Routine_Macros:116672", 0xACF4E9, false),
        Triple("DATA_ACFAEF · \$AC:FAEF · Routine_Macros:116675", 0xACFAEF, false),
        Triple("DATA_AD810F · \$AD:810F · Routine_Macros:116678", 0xAD810F, false),
        Triple("DATA_AD89A5 · \$AD:89A5 · Routine_Macros:116681", 0xAD89A5, false),
        Triple("DATA_AD9298 · \$AD:9298 · Routine_Macros:116684", 0xAD9298, false),
        Triple("DATA_AD9F2D · \$AD:9F2D · Routine_Macros:116687", 0xAD9F2D, false),
        Triple("DATA_ADAA3D · \$AD:AA3D · Routine_Macros:116690", 0xADAA3D, false),
        Triple("DATA_ADB632 · \$AD:B632 · Routine_Macros:116693", 0xADB632, false),
        Triple("DATA_ADBE6B · \$AD:BE6B · Routine_Macros:116696", 0xADBE6B, false),
        Triple("DATA_ADC648 · \$AD:C648 · Routine_Macros:116699", 0xADC648, false),
        Triple("DATA_ADCC96 · \$AD:CC96 · Routine_Macros:116702", 0xADCC96, false),
        Triple("DATA_ADD8AF · \$AD:D8AF · Routine_Macros:116705", 0xADD8AF, false),
        Triple("DATA_ADDC2A · \$AD:DC2A · Routine_Macros:116708", 0xADDC2A, false),
        Triple("DATA_ADEC89 · \$AD:EC89 · Routine_Macros:116711", 0xADEC89, false),
        Triple("DATA_ADF09B · \$AD:F09B · Routine_Macros:116714", 0xADF09B, false),
        Triple("DATA_ADF90A · \$AD:F90A · Routine_Macros:116717", 0xADF90A, false),
        Triple("DATA_AE85E0 · \$AE:85E0 · Routine_Macros:116720", 0xAE85E0, false),
        Triple("DATA_AE8F89 · \$AE:8F89 · Routine_Macros:116723", 0xAE8F89, false),
        Triple("DATA_AE9190 · \$AE:9190 · Routine_Macros:116726", 0xAE9190, false),
        Triple("DATA_AE9C51 · \$AE:9C51 · Routine_Macros:116729", 0xAE9C51, false),
        Triple("DATA_AEA728 · \$AE:A728 · Routine_Macros:116732", 0xAEA728, false),
        Triple("DATA_AEB1F8 · \$AE:B1F8 · Routine_Macros:116735", 0xAEB1F8, false),
        Triple("DATA_AEB9EB · \$AE:B9EB · Routine_Macros:116738", 0xAEB9EB, false),
        Triple("DATA_AEC4AC · \$AE:C4AC · Routine_Macros:116741", 0xAEC4AC, false),
        Triple("DATA_AED5CB · \$AE:D5CB · Routine_Macros:116744", 0xAED5CB, false),
        Triple("DATA_AEDD01 · \$AE:DD01 · Routine_Macros:116747", 0xAEDD01, false),
        Triple("DATA_AEEE20 · \$AE:EE20 · Routine_Macros:116750", 0xAEEE20, false),
        Triple("DATA_AEFAC9 · \$AE:FAC9 · Routine_Macros:116753", 0xAEFAC9, false),
        Triple("DATA_AF8318 · \$AF:8318 · Routine_Macros:116756", 0xAF8318, false),
        Triple("DATA_AF86A5 · \$AF:86A5 · Routine_Macros:116759", 0xAF86A5, false),
        Triple("DATA_AF8DD1 · \$AF:8DD1 · Routine_Macros:116762", 0xAF8DD1, false),
        Triple("DATA_AF99C7 · \$AF:99C7 · Routine_Macros:116765", 0xAF99C7, false),
        Triple("DATA_AFAC55 · \$AF:AC55 · Routine_Macros:116768", 0xAFAC55, false),
        Triple("DATA_AFBBEF · \$AF:BBEF · Routine_Macros:116771", 0xAFBBEF, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "PACMAN2")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverEWJ2 : SnesDriver {
    override val nom = "EWJ2"
    private val PISTES = listOf(
        Triple("DATA_C0F523 · \$C0:F523 · Routine_Macros:21774", 0xC0F523, false),
        Triple("DATA_C1EC50 · \$C1:EC50 · Routine_Macros:48769", 0xC1EC50, false),
        Triple("DATA_C1EC50 · \$C2:0000 · Routine_Macros:48782", 0xC20000, false),
        Triple("DATA_C2D910 · \$C2:D910 · Routine_Macros:74600", 0xC2D910, false),
        Triple("DATA_C2D910 · \$C3:0000 · Routine_Macros:74613", 0xC30000, false),
        Triple("DATA_C3DFC8 · \$C3:DFC8 · Routine_Macros:80591", 0xC3DFC8, false),
        Triple("DATA_C3DFC8 · \$C4:0000 · Routine_Macros:80604", 0xC40000, false),
        Triple("DATA_C4965C · \$C4:965C · Routine_Macros:84127", 0xC4965C, false),
        Triple("DATA_C4FD54 · \$C4:FD54 · Routine_Macros:84130", 0xC4FD54, false),
        Triple("DATA_C4FD54 · \$C5:0000 · Routine_Macros:84143", 0xC50000, false),
        Triple("DATA_C5FD90 · \$C5:FD90 · Routine_Macros:84150", 0xC5FD90, false),
        Triple("DATA_C5FD90 · \$C6:0000 · Routine_Macros:84163", 0xC60000, false),
        Triple("DATA_C67FEA · \$C6:7FEA · Routine_Macros:84207", 0xC67FEA, false),
        Triple("DATA_C6C564 · \$C6:C564 · Routine_Macros:84212", 0xC6C564, false),
        Triple("DATA_C6E252 · \$C6:E252 · Routine_Macros:84215", 0xC6E252, false),
        Triple("%EWJ2_HandleSplitSPCEngine() · \$EA:81B2 · Routine_Macros:101841", 0xEA81B2, true),
        Triple("DATA_EA9D3E · \$EA:9D3E · Routine_Macros:101847", 0xEA9D3E, false),
        Triple("Voice_Manta · \$EA:BC48 · Routine_Macros:101852", 0xEABC48, false),
        Triple("SalamanderScream · \$EA:C886 · Routine_Macros:101857", 0xEAC886, false),
        Triple("Voice_Woah · \$EA:E030 · Routine_Macros:101862", 0xEAE030, false),
        Triple("CowPanic · \$EB:0002 · Routine_Macros:102213", 0xEB0002, false),
        Triple("BackTap · \$EB:15AC · Routine_Macros:102218", 0xEB15AC, false),
        Triple("JimHeadPops · \$EB:1A00 · Routine_Macros:102223", 0xEB1A00, false),
        Triple("MarshmallowBounce · \$EB:1FCE · Routine_Macros:102228", 0xEB1FCE, false),
        Triple("Splat · \$EB:2ADA · Routine_Macros:102233", 0xEB2ADA, false),
        Triple("PeterPuppyYelp · \$EB:327C · Routine_Macros:102238", 0xEB327C, false),
        Triple("LoudCrash · \$EB:3ADC · Routine_Macros:102243", 0xEB3ADC, false),
        Triple("BobTheKillerGoldfishScreech · \$EB:5CE6 · Routine_Macros:102248", 0xEB5CE6, false),
        Triple("Beep · \$EB:644A · Routine_Macros:102253", 0xEB644A, false),
        Triple("DATA_EB66A6 · \$EB:66A6 · Routine_Macros:102258", 0xEB66A6, false),
        Triple("ZoomOff · \$EB:8E0A · Routine_Macros:102263", 0xEB8E0A, false),
        Triple("Slam · \$EB:A146 · Routine_Macros:102268", 0xEBA146, false),
        Triple("AccordianNote1 · \$EB:AE1C · Routine_Macros:102273", 0xEBAE1C, false),
        Triple("ShotgunPump · \$EB:B4F8 · Routine_Macros:102278", 0xEBB4F8, false),
        Triple("Creaking · \$EB:BF62 · Routine_Macros:102283", 0xEBBF62, false),
        Triple("AngryPeterPuppy · \$EB:C8C6 · Routine_Macros:102288", 0xEBC8C6, false),
        Triple("FlaminYawnLaunchPlate · \$EB:D716 · Routine_Macros:102293", 0xEBD716, false),
        Triple("PigLandsInFishbowl · \$EB:DB8E · Routine_Macros:102298", 0xEBDB8E, false),
        Triple("AccordianNote2 · \$EB:E6D0 · Routine_Macros:102303", 0xEBE6D0, false),
        Triple("Voice_Fresh · \$EB:EDBE · Routine_Macros:102308", 0xEBEDBE, false),
        Triple("SnotParachute · \$EB:F866 · Routine_Macros:102313", 0xEBF866, false),
        Triple("AccordianScareCord · \$EC:0002 · Routine_Macros:102397", 0xEC0002, false),
        Triple("EnemyDies · \$EC:128A · Routine_Macros:102402", 0xEC128A, false),
        Triple("Unknown1 · \$EC:1C76 · Routine_Macros:102407", 0xEC1C76, false),
        Triple("PigSqueal · \$EC:1F90 · Routine_Macros:102412", 0xEC1F90, false),
        Triple("PigGrunt · \$EC:2A9C · Routine_Macros:102417", 0xEC2A9C, false),
        Triple("FallingGranny · \$EC:2E84 · Routine_Macros:102422", 0xEC2E84, false),
        Triple("PigOink · \$EC:3638 · Routine_Macros:102427", 0xEC3638, false),
        Triple("Punch · \$EC:39F4 · Routine_Macros:102432", 0xEC39F4, false),
        Triple("ScaredPuppy · \$EC:3ED8 · Routine_Macros:102437", 0xEC3ED8, false),
        Triple("ShotgunFire · \$EC:4834 · Routine_Macros:102442", 0xEC4834, false),
        Triple("Voice_Funky · \$EC:50B8 · Routine_Macros:102447", 0xEC50B8, false),
        Triple("SaltShaker · \$EC:630A · Routine_Macros:102452", 0xEC630A, false),
        Triple("DATA_EC675E · \$EC:675E · Routine_Macros:102457", 0xEC675E, false),
        Triple("Splash · \$EC:95DE · Routine_Macros:102462", 0xEC95DE, false),
        Triple("JimScream · \$EC:A488 · Routine_Macros:102467", 0xECA488, false),
        Triple("JimLiftGrunt · \$EC:B182 · Routine_Macros:102472", 0xECB182, false),
        Triple("WeaponSwitch · \$EC:B804 · Routine_Macros:102477", 0xECB804, false),
        Triple("JimPinballBumper · \$EC:BC62 · Routine_Macros:102482", 0xECBC62, false),
        Triple("12PinballBumper · \$EC:C4D4 · Routine_Macros:102487", 0xECC4D4, false),
        Triple("GreenPsyCrowPinballBumper · \$EC:C9CA · Routine_Macros:102492", 0xECC9CA, false),
        Triple("PurplePsyCrowPinballBumper · \$EC:D0C2 · Routine_Macros:102497", 0xECD0C2, false),
        Triple("Voice_WellDone · \$EC:D3EE · Routine_Macros:102502", 0xECD3EE, false),
        Triple("EnterForked · \$EC:E086 · Routine_Macros:102507", 0xECE086, false),
        Triple("SuitMalfunction · \$ED:0002 · Routine_Macros:102725", 0xED0002, false),
        Triple("Voice_A · \$ED:1552 · Routine_Macros:102730", 0xED1552, false),
        Triple("Voice_B · \$ED:1DF0 · Routine_Macros:102735", 0xED1DF0, false),
        Triple("Voice_Y · \$ED:2890 · Routine_Macros:102740", 0xED2890, false),
        Triple("Voice_Ammo · \$ED:315C · Routine_Macros:102745", 0xED315C, false),
        Triple("Voice_Groovy · \$ED:3BA2 · Routine_Macros:102750", 0xED3BA2, false),
        Triple("Voice_Woopie · \$ED:4878 · Routine_Macros:102755", 0xED4878, false),
        Triple("Voice_Tender · \$ED:55A0 · Routine_Macros:102760", 0xED55A0, false),
        Triple("Voice_WayCool · \$ED:665E · Routine_Macros:102765", 0xED665E, false),
        Triple("DATA_ED76B8 · \$ED:76B8 · Routine_Macros:102770", 0xED76B8, false),
        Triple("Applause · \$EE:0710 · Routine_Macros:102775", 0xEE0710, false),
        Triple("Zap · \$EE:1FFE · Routine_Macros:102780", 0xEE1FFE, false),
        Triple("DartOff · \$EE:2734 · Routine_Macros:102785", 0xEE2734, false),
        Triple("ElectricWhirr · \$EE:3120 · Routine_Macros:102790", 0xEE3120, false),
        Triple("DATA_EE42CE · \$EE:42CE · Routine_Macros:102793", 0xEE42CE, false),
        Triple("DATA_EE4C30 · \$EE:4C30 · Routine_Macros:102796", 0xEE4C30, false),
        Triple("DATA_EE56F6 · \$EE:56F6 · Routine_Macros:102799", 0xEE56F6, false),
        Triple("DATA_EE61FE · \$EE:61FE · Routine_Macros:102802", 0xEE61FE, false),
        Triple("DATA_EE6354 · \$EE:6354 · Routine_Macros:102805", 0xEE6354, false),
        Triple("DATA_EE7A6E · \$EE:7A6E · Routine_Macros:102808", 0xEE7A6E, false),
        Triple("DATA_EE93A2 · \$EE:93A2 · Routine_Macros:102811", 0xEE93A2, false),
        Triple("Unknown3 · \$EE:B408 · Routine_Macros:102814", 0xEEB408, false),
        Triple("Unknown4 · \$EE:B5F4 · Routine_Macros:102817", 0xEEB5F4, false),
        Triple("DATA_EEB7C8 · \$EE:B7C8 · Routine_Macros:102820", 0xEEB7C8, false),
        Triple("DATA_EECCBC · \$EE:CCBC · Routine_Macros:102823", 0xEECCBC, false),
        Triple("DATA_EECE14 · \$EE:CE14 · Routine_Macros:102828", 0xEECE14, false),
        Triple("DATA_EEDFFE · \$EE:DFFE · Routine_Macros:102833", 0xEEDFFE, false),
        Triple("DATA_EEE4EC · \$EE:E4EC · Routine_Macros:102838", 0xEEE4EC, false),
        Triple("Unknown2 · \$EE:FBDC · Routine_Macros:102843", 0xEEFBDC, false),
        Triple("DATA_EF8CCA · \$EF:8CCA · Routine_Macros:102846", 0xEF8CCA, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "EARTHWORM JIM 2")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverLester : SnesDriver {
    override val nom = "Lester"
    private val PISTES = listOf(
        Triple("blocs 0 · \$C7:33EC · Routine_Macros:9818", 0xC733EC, true),
        Triple("blocs 1 · \$C7:3430 · Routine_Macros:9828", 0xC73430, false),
        Triple("Music_TitleScreen · \$C8:CE85 · Routine_Macros:10955", 0xC8CE85, false),
        Triple("Music_Beach · \$C8:DB7F · Routine_Macros:10958", 0xC8DB7F, false),
        Triple("Music_IntroCutscene · \$C8:F89F · Routine_Macros:10961", 0xC8F89F, false),
        Triple("Music_BurialGrounds · \$C8:FFC8 · Routine_Macros:10964", 0xC8FFC8, false),
        Triple("Music_Ending · \$C9:0C68 · Routine_Macros:10979", 0xC90C68, false),
        Triple("Music_Cave · \$C9:1540 · Routine_Macros:10982", 0xC91540, false),
        Triple("Music_Ape · \$C9:1AF5 · Routine_Macros:10985", 0xC91AF5, false),
        Triple("Music_PirateShips · \$C9:2044 · Routine_Macros:10988", 0xC92044, false),
        Triple("Music_TotemPoles · \$C9:2792 · Routine_Macros:10991", 0xC92792, false),
        Triple("Music_River · \$C9:2E14 · Routine_Macros:10994", 0xC92E14, false),
        Triple("Music_Village · \$C9:3A35 · Routine_Macros:10997", 0xC93A35, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "LESTER")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverRiseRobots : SnesDriver {
    override val nom = "RiseRobots"
    private val PISTES = listOf(
        Triple("blocs 2 · \$C1:1505 · Routine_Macros:18153", 0xC11505, false),
        Triple("blocs 3 · \$C2:0000 · Routine_Macros:18182", 0xC20000, false),
        Triple("blocs 4 · \$C2:6E10 · Routine_Macros:18192", 0xC26E10, false),
        Triple("blocs 5 · \$C3:004A · Routine_Macros:18230", 0xC3004A, false),
        Triple("blocs 6 · \$C5:44A5 · Routine_Macros:18294", 0xC544A5, false),
        Triple("blocs 7 · \$C6:11F3 · Routine_Macros:18336", 0xC611F3, false),
        Triple("blocs 8 · \$C6:5331 · Routine_Macros:18346", 0xC65331, false),
        Triple("blocs 9 · \$C6:8ADF · Routine_Macros:18356", 0xC68ADF, false),
        Triple("blocs 10 · \$C7:11F3 · Routine_Macros:18390", 0xC711F3, false),
        Triple("blocs 11 · \$C7:4364 · Routine_Macros:18400", 0xC74364, false),
        Triple("blocs 12 · \$C7:7675 · Routine_Macros:18410", 0xC77675, false),
        Triple("blocs 13 · \$C8:B25D · Routine_Macros:18537", 0xC8B25D, false),
        Triple("blocs 14 · \$D2:DFC6 · Routine_Macros:21415", 0xD2DFC6, false),
        Triple("blocs 1 · \$D4:F342 · Routine_Macros:22224", 0xD4F342, false),
        Triple("blocs 15 · \$DB:E9CF · Routine_Macros:24717", 0xDBE9CF, false),
        Triple("blocs 0 · \$DC:FEE8 · Routine_Macros:25013", 0xDCFEE8, true),
        Triple("blocs 16 · \$DF:E772 · Routine_Macros:25657", 0xDFE772, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "RISE OF THE ROBOTS")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverFaceball : SnesDriver {
    override val nom = "Faceball"
    private val PISTES = listOf(
        Triple("AsteroidZone · \$08:8000 · Routine_Macros:35156", 0x088000, false),
        Triple("IceZone · \$08:A1A2 · Routine_Macros:35159", 0x08A1A2, false),
        Triple("SpaceZone · \$08:CF4C · Routine_Macros:35162", 0x08CF4C, false),
        Triple("TitleScreen · \$08:EC0A · Routine_Macros:35165", 0x08EC0A, false),
        Triple("MarsZone · \$09:8000 · Routine_Macros:35178", 0x098000, false),
        Triple("StarZone · \$09:AB98 · Routine_Macros:35181", 0x09AB98, false),
        Triple("Engine · \$09:D405 · Routine_Macros:35184", 0x09D405, true),
        Triple("GlobalSamples · \$0A:8000 · Routine_Macros:35197", 0x0A8000, false),
        Triple("CityZone · \$0B:B4C9 · Routine_Macros:35214", 0x0BB4C9, false),
        Triple("DesertZone · \$0B:D659 · Routine_Macros:35217", 0x0BD659, false),
        Triple("InitializeSPC700 · \$0B:FA47 · Routine_Macros:35220", 0x0BFA47, true))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "FACEBALL")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverSimCity : SnesDriver {
    override val nom = "SimCity"
    private val PISTES = listOf(
        Triple("CompressedSPCBlock1 · \$0D:D77C · Routine_Macros:46079", 0x0DD77C, false),
        Triple("CompressedSPCBlock2 · \$0E:C242 · Routine_Macros:46082", 0x0EC242, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "SIMCITY")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverMarioPaint : SnesDriver {
    override val nom = "MarioPaint"
    private val PISTES = listOf(
        Triple("MusicToolStatusBar · \$02:9600 · Routine_Macros:30555", 0x029600, false),
        Triple("MusicSelectionMenu · \$02:AC80 · Routine_Macros:30569", 0x02AC80, false),
        Triple("MusicToolScreen_Layer1 · \$02:BC00 · Routine_Macros:30578", 0x02BC00, false),
        Triple("MusicToolScreen_Layer2 · \$02:C100 · Routine_Macros:30581", 0x02C100, false),
        Triple("PreComposedMusicToolSong1 · \$02:F310 · Routine_Macros:30974", 0x02F310, false),
        Triple("PreComposedMusicToolSong2 · \$02:F560 · Routine_Macros:30978", 0x02F560, false),
        Triple("PreComposedMusicToolSong3 · \$02:F7B0 · Routine_Macros:30982", 0x02F7B0, false),
        Triple("ChantingSampleBank · \$16:8000 · Routine_Macros:34446", 0x168000, false),
        Triple("ChantingSampleBank · \$17:8000 · Routine_Macros:34459", 0x178000, false),
        Triple("AudienceSampleBank · \$17:9000 · Routine_Macros:34466", 0x179000, false),
        Triple("Engine · \$18:8000 · Routine_Macros:34481", 0x188000, true),
        Triple("TitleScreenSampleBank · \$18:B1BE · Routine_Macros:34484", 0x18B1BE, false),
        Triple("TitleScreenSampleBank · \$19:8000 · Routine_Macros:34497", 0x198000, false),
        Triple("MainSampleBank · \$1A:8000 · Routine_Macros:34512", 0x1A8000, false),
        Triple("MainSampleBank · \$1B:8000 · Routine_Macros:34525", 0x1B8000, false),
        Triple("MusicToolSampleBank · \$1C:8000 · Routine_Macros:34540", 0x1C8000, false),
        Triple("MusicToolSampleBank · \$1D:8000 · Routine_Macros:34553", 0x1D8000, false),
        Triple("FlySwattingSampleBank · \$1E:8000 · Routine_Macros:34568", 0x1E8000, false),
        Triple("FlySwattingSampleBank · \$1F:8000 · Routine_Macros:34581", 0x1F8000, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "MARIOPAINT")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverWariosWoods : SnesDriver {
    override val nom = "WariosWoods"
    private val PISTES = listOf(
        Triple("Engine · \$8B:8CEC · Routine_Macros:77166", 0x8B8CEC, true),
        Triple("DATA_8B935E · \$8B:935E · Routine_Macros:77242", 0x8B935E, false),
        Triple("DATA_8B9ABF · \$8B:9ABF · Routine_Macros:77245", 0x8B9ABF, false),
        Triple("DATA_8B9CF6 · \$8B:9CF6 · Routine_Macros:77248", 0x8B9CF6, false),
        Triple("DATA_8C91E9 · \$8C:91E9 · Routine_Macros:77775", 0x8C91E9, false),
        Triple("DATA_8C96DC · \$8C:96DC · Routine_Macros:77778", 0x8C96DC, false),
        Triple("DATA_8C9928 · \$8C:9928 · Routine_Macros:77781", 0x8C9928, false),
        Triple("DATA_8CA28A · \$8C:A28A · Routine_Macros:77784", 0x8CA28A, false),
        Triple("DATA_8CAA07 · \$8C:AA07 · Routine_Macros:77787", 0x8CAA07, false),
        Triple("DATA_8CB7CC · \$8C:B7CC · Routine_Macros:77790", 0x8CB7CC, false),
        Triple("DATA_8CBD0C · \$8C:BD0C · Routine_Macros:77793", 0x8CBD0C, false),
        Triple("DATA_8CBF99 · \$8C:BF99 · Routine_Macros:77796", 0x8CBF99, false),
        Triple("DATA_8CC0E8 · \$8C:C0E8 · Routine_Macros:77799", 0x8CC0E8, false),
        Triple("DATA_8CC231 · \$8C:C231 · Routine_Macros:77802", 0x8CC231, false),
        Triple("DATA_8CC35B · \$8C:C35B · Routine_Macros:77805", 0x8CC35B, false),
        Triple("DATA_8CC588 · \$8C:C588 · Routine_Macros:77808", 0x8CC588, false),
        Triple("DATA_8CC6A1 · \$8C:C6A1 · Routine_Macros:77811", 0x8CC6A1, false),
        Triple("DATA_8CC81B · \$8C:C81B · Routine_Macros:77814", 0x8CC81B, false),
        Triple("DATA_8CC8FF · \$8C:C8FF · Routine_Macros:77817", 0x8CC8FF, false),
        Triple("DATA_8CCA8E · \$8C:CA8E · Routine_Macros:77820", 0x8CCA8E, false),
        Triple("DATA_8CD0B8 · \$8C:D0B8 · Routine_Macros:77823", 0x8CD0B8, false),
        Triple("DATA_8CD21F · \$8C:D21F · Routine_Macros:77826", 0x8CD21F, false),
        Triple("DATA_8CD815 · \$8C:D815 · Routine_Macros:77829", 0x8CD815, false),
        Triple("DATA_8CDB03 · \$8C:DB03 · Routine_Macros:77832", 0x8CDB03, false),
        Triple("DATA_8CE332 · \$8C:E332 · Routine_Macros:77835", 0x8CE332, false),
        Triple("DATA_8CE596 · \$8C:E596 · Routine_Macros:77838", 0x8CE596, false),
        Triple("DATA_8CE702 · \$8C:E702 · Routine_Macros:77841", 0x8CE702, false),
        Triple("DATA_8CF0F7 · \$8C:F0F7 · Routine_Macros:77844", 0x8CF0F7, false),
        Triple("DATA_8CF4AD · \$8C:F4AD · Routine_Macros:77847", 0x8CF4AD, false),
        Triple("DATA_8CFA16 · \$8C:FA16 · Routine_Macros:77850", 0x8CFA16, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "WARIO")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverTMNT4 : SnesDriver {
    override val nom = "TMNT4"
    private val PISTES = listOf(
        Triple("Music_BigApple3AM · \$1D:8000 · Routine_Macros:95246", 0x1D8000, false),
        Triple("Music_AlleycatBlues · \$1D:88C9 · Routine_Macros:95251", 0x1D88C9, false),
        Triple("Music_LevelClear · \$1D:8F65 · Routine_Macros:95256", 0x1D8F65, false),
        Triple("Music_BossBattle · \$1D:907C · Routine_Macros:95261", 0x1D907C, false),
        Triple("Music_CharacterSelect · \$1D:9589 · Routine_Macros:95266", 0x1D9589, false),
        Triple("Music_EveningNewsScene · \$1D:9752 · Routine_Macros:95271", 0x1D9752, false),
        Triple("Music_EasyOrNormalEnding · \$1D:9AEC · Routine_Macros:95276", 0x1D9AEC, false),
        Triple("Music_StarbaseWhereNoTurtleHasGoneBefore · \$1D:9ECB · Routine_Macros:95281", 0x1D9ECB, false),
        Triple("Music_TechnodromeTheFinalShellShock · \$1D:A4FF · Routine_Macros:95286", 0x1DA4FF, false),
        Triple("Music_GameOver · \$1D:C012 · Routine_Macros:95301", 0x1DC012, false),
        Triple("Music_IntroScene · \$1D:C130 · Routine_Macros:95306", 0x1DC130, false),
        Triple("Music_VersusMode · \$1D:C3FE · Routine_Macros:95311", 0x1DC3FE, false),
        Triple("Music_SewerSurfin · \$1D:C8EF · Routine_Macros:95316", 0x1DC8EF, false),
        Triple("Music_PrehistoricTurtlesauras · \$1D:CFEF · Routine_Macros:95321", 0x1DCFEF, false),
        Triple("Music_SkullAndCrossbones · \$1D:D745 · Routine_Macros:95326", 0x1DD745, false),
        Triple("Music_BuryMyShellAtWoundedKnee · \$1D:DC01 · Routine_Macros:95331", 0x1DDC01, false),
        Triple("Music_NeonNightRiders · \$1D:E01B · Routine_Macros:95336", 0x1DE01B, false),
        Triple("Music_VersusModeFinish · \$1D:E4FF · Routine_Macros:95341", 0x1DE4FF, false),
        Triple("Music_SentForwardInTime · \$1D:E8D6 · Routine_Macros:95346", 0x1DE8D6, false),
        Triple("Music_TechnodromeElevator · \$1D:EAC6 · Routine_Macros:95351", 0x1DEAC6, false),
        Triple("Music_SuperShredderScreamSound · \$1D:EEE6 · Routine_Macros:95356", 0x1DEEE6, false),
        Triple("Music_KonamiLogoScreen · \$1D:EF66 · Routine_Macros:95361", 0x1DEF66, false),
        Triple("Music_TimeTrialVictory · \$1D:EFF2 · Routine_Macros:95366", 0x1DEFF2, false),
        Triple("Music_TimeTrialLevel · \$1D:F20C · Routine_Macros:95371", 0x1DF20C, false),
        Triple("Music_ContinueScreen · \$1D:F569 · Routine_Macros:95376", 0x1DF569, false),
        Triple("Music_EndingCrowdCheerSound · \$1D:F69D · Routine_Macros:95381", 0x1DF69D, false),
        Triple("Music_BossExplosionSound · \$1D:FDA1 · Routine_Macros:95406", 0x1DFDA1, false),
        Triple("GlobalSampleBank · \$1E:8000 · Routine_Macros:95420", 0x1E8000, false),
        Triple("SPC700_Engine_TMNTIV · \$1F:8000 · Routine_Macros:95432", 0x1F8000, true),
        Triple("Music_UnknownSong1 · \$1F:C749 · Routine_Macros:95441", 0x1FC749, false),
        Triple("Music_StatueOfLibertyReturned · \$1F:E5CF · Routine_Macros:95451", 0x1FE5CF, false),
        Triple("Music_Credits · \$1F:E6BB · Routine_Macros:95456", 0x1FE6BB, false),
        Triple("Music_TechnodromeLetsKickShell · \$1F:EB55 · Routine_Macros:95461", 0x1FEB55, false),
        Triple("Music_TechnodromeAppears · \$1F:F273 · Routine_Macros:95466", 0x1FF273, false),
        Triple("Music_SentBackInTime · \$1F:F3B4 · Routine_Macros:95471", 0x1FF3B4, false),
        Triple("Music_TimeTrialMenu · \$1F:F507 · Routine_Macros:95476", 0x1FF507, false),
        Triple("Music_CastOfCharacters · \$1F:F6B1 · Routine_Macros:95481", 0x1FF6B1, false),
        Triple("Music_ShredderSendsTurtlesAway · \$1F:FC3F · Routine_Macros:95486", 0x1FFC3F, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "T.M.N.T", "T.M.H.T")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverSuperPunchOut : SnesDriver {
    override val nom = "SuperPunchOut"
    private val PISTES = listOf(
        Triple("blocs 1 · \$01:F580 · Routine_Macros:29978", 0x01F580, false),
        Triple("blocs 0 · \$02:8000 · Routine_Macros:31142", 0x028000, true),
        Triple("blocs 6 · \$02:903D · Routine_Macros:31146", 0x02903D, false),
        Triple("blocs 24 · \$02:E3C9 · Routine_Macros:31150", 0x02E3C9, false),
        Triple("blocs 36 · \$02:FF3C · Routine_Macros:31160", 0x02FF3C, false),
        Triple("blocs 14 · \$02:FF50 · Routine_Macros:31164", 0x02FF50, false),
        Triple("blocs 6 · \$03:8000 · Routine_Macros:31178", 0x038000, false),
        Triple("blocs 3 · \$03:B9B1 · Routine_Macros:31182", 0x03B9B1, false),
        Triple("blocs 25 · \$03:F97F · Routine_Macros:31192", 0x03F97F, false),
        Triple("blocs 2 · \$04:8000 · Routine_Macros:31206", 0x048000, false),
        Triple("blocs 4 · \$04:9979 · Routine_Macros:31210", 0x049979, false),
        Triple("blocs 5 · \$04:B866 · Routine_Macros:31214", 0x04B866, false),
        Triple("blocs 32 · \$04:EC61 · Routine_Macros:31224", 0x04EC61, false),
        Triple("blocs 8 · \$04:F9D9 · Routine_Macros:31228", 0x04F9D9, false),
        Triple("blocs 31 · \$05:F82B · Routine_Macros:31272", 0x05F82B, false),
        Triple("blocs 26 · \$05:F83C · Routine_Macros:31276", 0x05F83C, false),
        Triple("blocs 15 · \$05:F84D · Routine_Macros:31280", 0x05F84D, false),
        Triple("blocs 17 · \$06:94EA · Routine_Macros:31303", 0x0694EA, false),
        Triple("blocs 27 · \$06:A0BC · Routine_Macros:31310", 0x06A0BC, false),
        Triple("blocs 13 · \$06:A0CE · Routine_Macros:31314", 0x06A0CE, false),
        Triple("blocs 37 · \$06:A0E6 · Routine_Macros:31318", 0x06A0E6, false),
        Triple("blocs 11 · \$06:A146 · Routine_Macros:31322", 0x06A146, false),
        Triple("blocs 39 · \$06:C3FF · Routine_Macros:31332", 0x06C3FF, false),
        Triple("blocs 28 · \$06:D5E9 · Routine_Macros:31336", 0x06D5E9, false),
        Triple("blocs 23 · \$21:E6C7 · Routine_Macros:80461", 0x21E6C7, false),
        Triple("blocs 16 · \$21:EDE8 · Routine_Macros:80465", 0x21EDE8, false),
        Triple("blocs 33 · \$27:E6C7 · Routine_Macros:80748", 0x27E6C7, false),
        Triple("blocs 38 · \$27:F27D · Routine_Macros:80752", 0x27F27D, false),
        Triple("blocs 9 · \$2B:F1BE · Routine_Macros:80841", 0x2BF1BE, false),
        Triple("blocs 10 · \$2E:F1C7 · Routine_Macros:81006", 0x2EF1C7, false),
        Triple("blocs 21 · \$2F:EF4D · Routine_Macros:81030", 0x2FEF4D, false),
        Triple("blocs 34 · \$30:FA6A · Routine_Macros:81057", 0x30FA6A, false),
        Triple("blocs 18 · \$34:F30C · Routine_Macros:81140", 0x34F30C, false),
        Triple("blocs 7 · \$34:F95A · Routine_Macros:81144", 0x34F95A, false),
        Triple("blocs 19 · \$35:F879 · Routine_Macros:81195", 0x35F879, false),
        Triple("blocs 20 · \$37:FB1A · Routine_Macros:81298", 0x37FB1A, false),
        Triple("blocs 29 · \$38:FAB8 · Routine_Macros:81322", 0x38FAB8, false),
        Triple("blocs 35 · \$39:E48E · Routine_Macros:81343", 0x39E48E, false),
        Triple("blocs 30 · \$39:F874 · Routine_Macros:81347", 0x39F874, false),
        Triple("blocs 22 · \$3B:E598 · Routine_Macros:81464", 0x3BE598, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "SUPER PUNCH-OUT")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverSMRPG : SnesDriver {
    override val nom = "SMRPG"
    private val PISTES = listOf(
        Triple("Engine · \$C4:0D0D · Routine_Macros:116143", 0xC40D0D, true),
        Triple("DATA_C42826 · \$C4:2826 · Routine_Macros:116395", 0xC42826, false),
        Triple("DATA_C43E26 · \$C4:3E26 · Routine_Macros:116398", 0xC43E26, false),
        Triple("DATA_C45426 · \$C4:5426 · Routine_Macros:116401", 0xC45426, false),
        Triple("DATA_C454A6 · \$C4:54A6 · Routine_Macros:116404", 0xC454A6, false),
        Triple("DATA_C45526 · \$C4:5526 · Routine_Macros:116407", 0xC45526, false),
        Triple("DATA_C45FB5 · \$C4:5FB5 · Routine_Macros:116410", 0xC45FB5, false),
        Triple("DATA_C46A5B · \$C4:6A5B · Routine_Macros:116413", 0xC46A5B, false),
        Triple("DATA_C4710A · \$C4:710A · Routine_Macros:116416", 0xC4710A, false),
        Triple("DATA_C47B8F · \$C4:7B8F · Routine_Macros:116419", 0xC47B8F, false),
        Triple("DATA_C480CC · \$C4:80CC · Routine_Macros:116422", 0xC480CC, false),
        Triple("DATA_C4879F · \$C4:879F · Routine_Macros:116425", 0xC4879F, false),
        Triple("DATA_C48E61 · \$C4:8E61 · Routine_Macros:116428", 0xC48E61, false),
        Triple("DATA_C49080 · \$C4:9080 · Routine_Macros:116431", 0xC49080, false),
        Triple("DATA_C49301 · \$C4:9301 · Routine_Macros:116434", 0xC49301, false),
        Triple("DATA_C495CA · \$C4:95CA · Routine_Macros:116437", 0xC495CA, false),
        Triple("DATA_C4976C · \$C4:976C · Routine_Macros:116440", 0xC4976C, false),
        Triple("DATA_C49CB1 · \$C4:9CB1 · Routine_Macros:116443", 0xC49CB1, false),
        Triple("DATA_C4A4A2 · \$C4:A4A2 · Routine_Macros:116446", 0xC4A4A2, false),
        Triple("DATA_C4ADB9 · \$C4:ADB9 · Routine_Macros:116449", 0xC4ADB9, false),
        Triple("DATA_C4B216 · \$C4:B216 · Routine_Macros:116452", 0xC4B216, false),
        Triple("DATA_C4B8BB · \$C4:B8BB · Routine_Macros:116455", 0xC4B8BB, false),
        Triple("DATA_C4BCAA · \$C4:BCAA · Routine_Macros:116458", 0xC4BCAA, false),
        Triple("DATA_C4C405 · \$C4:C405 · Routine_Macros:116461", 0xC4C405, false),
        Triple("DATA_C4C4C3 · \$C4:C4C3 · Routine_Macros:116464", 0xC4C4C3, false),
        Triple("DATA_C4C54E · \$C4:C54E · Routine_Macros:116467", 0xC4C54E, false),
        Triple("DATA_C4C730 · \$C4:C730 · Routine_Macros:116470", 0xC4C730, false),
        Triple("DATA_C4D199 · \$C4:D199 · Routine_Macros:116473", 0xC4D199, false),
        Triple("DATA_C4D2CA · \$C4:D2CA · Routine_Macros:116476", 0xC4D2CA, false),
        Triple("DATA_C4D653 · \$C4:D653 · Routine_Macros:116479", 0xC4D653, false),
        Triple("DATA_C4E088 · \$C4:E088 · Routine_Macros:116482", 0xC4E088, false),
        Triple("DATA_C4E9FA · \$C4:E9FA · Routine_Macros:116485", 0xC4E9FA, false),
        Triple("DATA_C4ED6B · \$C4:ED6B · Routine_Macros:116488", 0xC4ED6B, false),
        Triple("DATA_C4F158 · \$C4:F158 · Routine_Macros:116491", 0xC4F158, false),
        Triple("DATA_C4F604 · \$C4:F604 · Routine_Macros:116494", 0xC4F604, false),
        Triple("DATA_C4F86F · \$C4:F86F · Routine_Macros:116497", 0xC4F86F, false),
        Triple("DATA_C4FC3B · \$C4:FC3B · Routine_Macros:116500", 0xC4FC3B, false),
        Triple("DATA_C50B54 · \$C5:0B54 · Routine_Macros:116515", 0xC50B54, false),
        Triple("DATA_C5144E · \$C5:144E · Routine_Macros:116518", 0xC5144E, false),
        Triple("DATA_C51650 · \$C5:1650 · Routine_Macros:116521", 0xC51650, false),
        Triple("DATA_C520B3 · \$C5:20B3 · Routine_Macros:116524", 0xC520B3, false),
        Triple("DATA_C520FD · \$C5:20FD · Routine_Macros:116527", 0xC520FD, false),
        Triple("DATA_C521A3 · \$C5:21A3 · Routine_Macros:116530", 0xC521A3, false),
        Triple("DATA_C529CF · \$C5:29CF · Routine_Macros:116533", 0xC529CF, false),
        Triple("DATA_C534B2 · \$C5:34B2 · Routine_Macros:116536", 0xC534B2, false),
        Triple("DATA_C53563 · \$C5:3563 · Routine_Macros:116539", 0xC53563, false),
        Triple("DATA_C53C4A · \$C5:3C4A · Routine_Macros:116542", 0xC53C4A, false),
        Triple("DATA_C54772 · \$C5:4772 · Routine_Macros:116545", 0xC54772, false),
        Triple("DATA_C54790 · \$C5:4790 · Routine_Macros:116548", 0xC54790, false),
        Triple("DATA_C547EA · \$C5:47EA · Routine_Macros:116551", 0xC547EA, false),
        Triple("DATA_C549F4 · \$C5:49F4 · Routine_Macros:116554", 0xC549F4, false),
        Triple("DATA_C54ADD · \$C5:4ADD · Routine_Macros:116557", 0xC54ADD, false),
        Triple("DATA_C54C47 · \$C5:4C47 · Routine_Macros:116560", 0xC54C47, false),
        Triple("DATA_C5544B · \$C5:544B · Routine_Macros:116563", 0xC5544B, false),
        Triple("DATA_C55506 · \$C5:5506 · Routine_Macros:116566", 0xC55506, false),
        Triple("DATA_C55F76 · \$C5:5F76 · Routine_Macros:116569", 0xC55F76, false),
        Triple("DATA_C56B13 · \$C5:6B13 · Routine_Macros:116572", 0xC56B13, false),
        Triple("DATA_C56DD5 · \$C5:6DD5 · Routine_Macros:116575", 0xC56DD5, false),
        Triple("DATA_C56DED · \$C5:6DED · Routine_Macros:116578", 0xC56DED, false),
        Triple("DATA_C57DBC · \$C5:7DBC · Routine_Macros:116581", 0xC57DBC, false),
        Triple("DATA_C57F5A · \$C5:7F5A · Routine_Macros:116584", 0xC57F5A, false),
        Triple("DATA_C5800C · \$C5:800C · Routine_Macros:116587", 0xC5800C, false),
        Triple("DATA_C581E7 · \$C5:81E7 · Routine_Macros:116590", 0xC581E7, false),
        Triple("DATA_C583F1 · \$C5:83F1 · Routine_Macros:116593", 0xC583F1, false),
        Triple("DATA_C588A8 · \$C5:88A8 · Routine_Macros:116596", 0xC588A8, false),
        Triple("DATA_C58AB1 · \$C5:8AB1 · Routine_Macros:116599", 0xC58AB1, false),
        Triple("DATA_C59403 · \$C5:9403 · Routine_Macros:116602", 0xC59403, false),
        Triple("DATA_C5993C · \$C5:993C · Routine_Macros:116605", 0xC5993C, false),
        Triple("DATA_C59FD9 · \$C5:9FD9 · Routine_Macros:116608", 0xC59FD9, false),
        Triple("DATA_C5A473 · \$C5:A473 · Routine_Macros:116611", 0xC5A473, false),
        Triple("DATA_C5A521 · \$C5:A521 · Routine_Macros:116614", 0xC5A521, false),
        Triple("DATA_C5AAE9 · \$C5:AAE9 · Routine_Macros:116617", 0xC5AAE9, false),
        Triple("DATA_C5B418 · \$C5:B418 · Routine_Macros:116620", 0xC5B418, false),
        Triple("DATA_C5BB6A · \$C5:BB6A · Routine_Macros:116623", 0xC5BB6A, false),
        Triple("DATA_C5CA96 · \$C5:CA96 · Routine_Macros:116626", 0xC5CA96, false),
        Triple("DATA_C5D747 · \$C5:D747 · Routine_Macros:116629", 0xC5D747, false),
        Triple("DATA_C5E301 · \$C5:E301 · Routine_Macros:116632", 0xC5E301, false),
        Triple("DATA_C605FA · \$C6:05FA · Routine_Macros:116647", 0xC605FA, false),
        Triple("DATA_C60939 · \$C6:0939 · Routine_Macros:116650", 0xC60939, false),
        Triple("DATA_C61E80 · \$C6:1E80 · Routine_Macros:116653", 0xC61E80, false),
        Triple("DATA_C63D45 · \$C6:3D45 · Routine_Macros:116656", 0xC63D45, false),
        Triple("DATA_C64611 · \$C6:4611 · Routine_Macros:116659", 0xC64611, false),
        Triple("DATA_C64B59 · \$C6:4B59 · Routine_Macros:116662", 0xC64B59, false),
        Triple("DATA_C65062 · \$C6:5062 · Routine_Macros:116665", 0xC65062, false),
        Triple("DATA_C6558F · \$C6:558F · Routine_Macros:116668", 0xC6558F, false),
        Triple("DATA_C65BEE · \$C6:5BEE · Routine_Macros:116671", 0xC65BEE, false),
        Triple("DATA_C65D7C · \$C6:5D7C · Routine_Macros:116674", 0xC65D7C, false),
        Triple("DATA_C6715B · \$C6:715B · Routine_Macros:116677", 0xC6715B, false),
        Triple("DATA_C678EC · \$C6:78EC · Routine_Macros:116680", 0xC678EC, false),
        Triple("DATA_C67AB0 · \$C6:7AB0 · Routine_Macros:116683", 0xC67AB0, false),
        Triple("DATA_C67C2C · \$C6:7C2C · Routine_Macros:116686", 0xC67C2C, false),
        Triple("DATA_C67EC8 · \$C6:7EC8 · Routine_Macros:116689", 0xC67EC8, false),
        Triple("DATA_C683AD · \$C6:83AD · Routine_Macros:116692", 0xC683AD, false),
        Triple("DATA_C68637 · \$C6:8637 · Routine_Macros:116695", 0xC68637, false),
        Triple("DATA_C68A9E · \$C6:8A9E · Routine_Macros:116698", 0xC68A9E, false),
        Triple("DATA_C69133 · \$C6:9133 · Routine_Macros:116701", 0xC69133, false),
        Triple("DATA_C69777 · \$C6:9777 · Routine_Macros:116704", 0xC69777, false),
        Triple("DATA_C69F47 · \$C6:9F47 · Routine_Macros:116707", 0xC69F47, false),
        Triple("DATA_C6AC78 · \$C6:AC78 · Routine_Macros:116710", 0xC6AC78, false),
        Triple("DATA_C6B595 · \$C6:B595 · Routine_Macros:116713", 0xC6B595, false),
        Triple("DATA_C6BA32 · \$C6:BA32 · Routine_Macros:116716", 0xC6BA32, false),
        Triple("DATA_C6CE50 · \$C6:CE50 · Routine_Macros:116719", 0xC6CE50, false),
        Triple("DATA_C6D470 · \$C6:D470 · Routine_Macros:116722", 0xC6D470, false),
        Triple("DATA_C6DC91 · \$C6:DC91 · Routine_Macros:116725", 0xC6DC91, false),
        Triple("DATA_C6EC77 · \$C6:EC77 · Routine_Macros:116728", 0xC6EC77, false),
        Triple("DATA_C6EFFD · \$C6:EFFD · Routine_Macros:116731", 0xC6EFFD, false),
        Triple("DATA_C6FAF7 · \$C6:FAF7 · Routine_Macros:116734", 0xC6FAF7, false),
        Triple("DATA_C70117 · \$C7:0117 · Routine_Macros:116749", 0xC70117, false),
        Triple("DATA_C72C2A · \$C7:2C2A · Routine_Macros:116752", 0xC72C2A, false),
        Triple("DATA_C7387A · \$C7:387A · Routine_Macros:116755", 0xC7387A, false),
        Triple("DATA_C74E24 · \$C7:4E24 · Routine_Macros:116758", 0xC74E24, false),
        Triple("DATA_C756F0 · \$C7:56F0 · Routine_Macros:116761", 0xC756F0, false),
        Triple("DATA_C75FBC · \$C7:5FBC · Routine_Macros:116764", 0xC75FBC, false),
        Triple("DATA_C76423 · \$C7:6423 · Routine_Macros:116767", 0xC76423, false),
        Triple("DATA_C76B09 · \$C7:6B09 · Routine_Macros:116770", 0xC76B09, false),
        Triple("DATA_C76CFA · \$C7:6CFA · Routine_Macros:116773", 0xC76CFA, false),
        Triple("DATA_C7818D · \$C7:818D · Routine_Macros:116776", 0xC7818D, false),
        Triple("DATA_C787F5 · \$C7:87F5 · Routine_Macros:116779", 0xC787F5, false),
        Triple("DATA_C799EE · \$C7:99EE · Routine_Macros:116782", 0xC799EE, false),
        Triple("DATA_C7A4E8 · \$C7:A4E8 · Routine_Macros:116785", 0xC7A4E8, false),
        Triple("DATA_C7ADB4 · \$C7:ADB4 · Routine_Macros:116788", 0xC7ADB4, false),
        Triple("DATA_C7B449 · \$C7:B449 · Routine_Macros:116791", 0xC7B449, false),
        Triple("DATA_C7C786 · \$C7:C786 · Routine_Macros:116794", 0xC7C786, false),
        Triple("DATA_C7CAA9 · \$C7:CAA9 · Routine_Macros:116797", 0xC7CAA9, false),
        Triple("DATA_C7CDCC · \$C7:CDCC · Routine_Macros:116800", 0xC7CDCC, false),
        Triple("DATA_C7D113 · \$C7:D113 · Routine_Macros:116803", 0xC7D113, false),
        Triple("DATA_C7D9DF · \$C7:D9DF · Routine_Macros:116806", 0xC7D9DF, false),
        Triple("DATA_C7E4D9 · \$C7:E4D9 · Routine_Macros:116809", 0xC7E4D9, false),
        Triple("DATA_C7E586 · \$C7:E586 · Routine_Macros:116812", 0xC7E586, false),
        Triple("DATA_C7E633 · \$C7:E633 · Routine_Macros:116815", 0xC7E633, false),
        Triple("DATA_C7F337 · \$C7:F337 · Routine_Macros:116818", 0xC7F337, false),
        Triple("DATA_C7F9CC · \$C7:F9CC · Routine_Macros:116821", 0xC7F9CC, false),
        Triple("DATA_C7FF65 · \$C7:FF65 · Routine_Macros:116824", 0xC7FF65, false),
        Triple("DATA_C804E3 · \$C8:04E3 · Routine_Macros:116839", 0xC804E3, false),
        Triple("DATA_C80B78 · \$C8:0B78 · Routine_Macros:116842", 0xC80B78, false),
        Triple("DATA_C8194B · \$C8:194B · Routine_Macros:116845", 0xC8194B, false),
        Triple("DATA_C8220E · \$C8:220E · Routine_Macros:116848", 0xC8220E, false),
        Triple("DATA_C828A3 · \$C8:28A3 · Routine_Macros:116851", 0xC828A3, false),
        Triple("DATA_C82C7A · \$C8:2C7A · Routine_Macros:116854", 0xC82C7A, false),
        Triple("DATA_C83546 · \$C8:3546 · Routine_Macros:116857", 0xC83546, false),
        Triple("DATA_C84292 · \$C8:4292 · Routine_Macros:116860", 0xC84292, false),
        Triple("DATA_C8476E · \$C8:476E · Routine_Macros:116863", 0xC8476E, false),
        Triple("DATA_C84DA9 · \$C8:4DA9 · Routine_Macros:116866", 0xC84DA9, false),
        Triple("DATA_C85084 · \$C8:5084 · Routine_Macros:116869", 0xC85084, false),
        Triple("DATA_C85A5E · \$C8:5A5E · Routine_Macros:116872", 0xC85A5E, false),
        Triple("DATA_C8671A · \$C8:671A · Routine_Macros:116875", 0xC8671A, false),
        Triple("DATA_C86AA0 · \$C8:6AA0 · Routine_Macros:116878", 0xC86AA0, false),
        Triple("DATA_C87228 · \$C8:7228 · Routine_Macros:116881", 0xC87228, false),
        Triple("DATA_C8768F · \$C8:768F · Routine_Macros:116884", 0xC8768F, false),
        Triple("DATA_C883C0 · \$C8:83C0 · Routine_Macros:116887", 0xC883C0, false),
        Triple("DATA_C8869B · \$C8:869B · Routine_Macros:116890", 0xC8869B, false),
        Triple("DATA_C88CB2 · \$C8:8CB2 · Routine_Macros:116893", 0xC88CB2, false),
        Triple("DATA_C891C4 · \$C8:91C4 · Routine_Macros:116896", 0xC891C4, false),
        Triple("DATA_C899D3 · \$C8:99D3 · Routine_Macros:116899", 0xC899D3, false),
        Triple("DATA_C89E3A · \$C8:9E3A · Routine_Macros:116902", 0xC89E3A, false),
        Triple("DATA_C8A81D · \$C8:A81D · Routine_Macros:116905", 0xC8A81D, false),
        Triple("DATA_C8AC84 · \$C8:AC84 · Routine_Macros:116908", 0xC8AC84, false),
        Triple("DATA_C8BBF5 · \$C8:BBF5 · Routine_Macros:116911", 0xC8BBF5, false),
        Triple("DATA_C8C0BF · \$C8:C0BF · Routine_Macros:116914", 0xC8C0BF, false),
        Triple("DATA_C8D0C0 · \$C8:D0C0 · Routine_Macros:116917", 0xC8D0C0, false),
        Triple("DATA_C8E136 · \$C8:E136 · Routine_Macros:116920", 0xC8E136, false),
        Triple("DATA_C8E42C · \$C8:E42C · Routine_Macros:116923", 0xC8E42C, false),
        Triple("DATA_C8F07C · \$C8:F07C · Routine_Macros:116926", 0xC8F07C, false),
        Triple("DATA_C8F948 · \$C8:F948 · Routine_Macros:116929", 0xC8F948, false),
        Triple("DATA_C8FEC6 · \$C8:FEC6 · Routine_Macros:116932", 0xC8FEC6, false),
        Triple("DATA_C909C0 · \$C9:09C0 · Routine_Macros:116947", 0xC909C0, false),
        Triple("DATA_C90BDE · \$C9:0BDE · Routine_Macros:116950", 0xC90BDE, false),
        Triple("DATA_C9115C · \$C9:115C · Routine_Macros:116953", 0xC9115C, false),
        Triple("DATA_C916DA · \$C9:16DA · Routine_Macros:116956", 0xC916DA, false),
        Triple("DATA_C9220A · \$C9:220A · Routine_Macros:116959", 0xC9220A, false),
        Triple("DATA_C933A0 · \$C9:33A0 · Routine_Macros:116962", 0xC933A0, false),
        Triple("DATA_D46000 · \$D4:6000 · Routine_Macros:145711", 0xD46000, false),
        Triple("DATA_D46014 · \$D4:6014 · Routine_Macros:145714", 0xD46014, false),
        Triple("DATA_D46079 · \$D4:6079 · Routine_Macros:145717", 0xD46079, false),
        Triple("DATA_D460A8 · \$D4:60A8 · Routine_Macros:145720", 0xD460A8, false),
        Triple("DATA_D460FB · \$D4:60FB · Routine_Macros:145723", 0xD460FB, false),
        Triple("DATA_D461C3 · \$D4:61C3 · Routine_Macros:145726", 0xD461C3, false),
        Triple("DATA_D46309 · \$D4:6309 · Routine_Macros:145729", 0xD46309, false),
        Triple("DATA_D46503 · \$D4:6503 · Routine_Macros:145732", 0xD46503, false),
        Triple("DATA_D46637 · \$D4:6637 · Routine_Macros:145735", 0xD46637, false),
        Triple("DATA_D46828 · \$D4:6828 · Routine_Macros:145738", 0xD46828, false),
        Triple("DATA_D469EC · \$D4:69EC · Routine_Macros:145741", 0xD469EC, false),
        Triple("DATA_D47B70 · \$D4:7B70 · Routine_Macros:145744", 0xD47B70, false),
        Triple("DATA_DC8000 · \$DC:8000 · Routine_Macros:150894", 0xDC8000, false),
        Triple("DATA_DC9FAF · \$DC:9FAF · Routine_Macros:150897", 0xDC9FAF, false),
        Triple("DATA_DCBEBC · \$DC:BEBC · Routine_Macros:150900", 0xDCBEBC, false),
        Triple("DATA_DCDE2C · \$DC:DE2C · Routine_Macros:150903", 0xDCDE2C, false),
        Triple("DATA_DCE347 · \$DC:E347 · Routine_Macros:150906", 0xDCE347, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "SUPER MARIO RPG")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverBSZelda : SnesDriver {
    override val nom = "BSZelda"
    private val PISTES = listOf(
        Triple("SPC700_Engine_BSZ1 · \$C2:0000 · Routine_Macros:27725", 0xC20000, true),
        Triple("DATA_C2AE00 · \$C2:AE00 · Routine_Macros:27737", 0xC2AE00, false),
        Triple("DATA_C2D000 · \$C2:D000 · Routine_Macros:27740", 0xC2D000, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "BS ZELDA")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverISSDeluxe : SnesDriver {
    override val nom = "ISSDeluxe"
    private val PISTES = listOf(
        Triple("Music_KonamiScreen · \$AF:8000 · Routine_Macros:185224", 0xAF8000, false),
        Triple("Music_Unknown44 · \$AF:80F6 · Routine_Macros:185229", 0xAF80F6, false),
        Triple("Music_ScenarioMenu · \$B0:BF89 · Routine_Macros:185254", 0xB0BF89, false),
        Triple("UnknownSampleBank · \$B0:C2BE · Routine_Macros:185258", 0xB0C2BE, false),
        Triple("Music_ScenarioModeClear · \$B2:F6D1 · Routine_Macros:185322", 0xB2F6D1, false),
        Triple("Music_WorldSeries · \$B6:8000 · Routine_Macros:185441", 0xB68000, false),
        Triple("Music_MainMenu · \$B6:C7AE · Routine_Macros:185463", 0xB6C7AE, false),
        Triple("Music_Unknown4C · \$B6:CC60 · Routine_Macros:185468", 0xB6CC60, false),
        Triple("Music_Unknown40 · \$B6:CCB8 · Routine_Macros:185473", 0xB6CCB8, false),
        Triple("Music_ShortLeague · \$B6:CDD2 · Routine_Macros:185478", 0xB6CDD2, false),
        Triple("Music_StaffRoll · \$B7:8000 · Routine_Macros:185503", 0xB78000, false),
        Triple("Music_GameOver · \$B7:8B36 · Routine_Macros:185508", 0xB78B36, false),
        Triple("Music_Unknown34 · \$B8:A575 · Routine_Macros:185558", 0xB8A575, false),
        Triple("Music_FurtherChallenges · \$B8:A643 · Routine_Macros:185563", 0xB8A643, false),
        Triple("Music_International · \$B8:ABF4 · Routine_Macros:185568", 0xB8ABF4, false),
        Triple("Music_Unknown08 · \$B8:B027 · Routine_Macros:185573", 0xB8B027, false),
        Triple("Music_TrainingModeMenu · \$B8:B445 · Routine_Macros:185578", 0xB8B445, false),
        Triple("Music_Unknown30 · \$B8:C014 · Routine_Macros:185588", 0xB8C014, false),
        Triple("Music_Practicing · \$B8:FBA7 · Routine_Macros:185610", 0xB8FBA7, false),
        Triple("Music_Unknown48 · \$B8:FFD2 · Routine_Macros:185615", 0xB8FFD2, false),
        Triple("Music_IntroCutscene · \$BA:DAED · Routine_Macros:185692", 0xBADAED, false),
        Triple("SPC700_Engine_ISSD · \$BD:8000 · Routine_Macros:185786", 0xBD8000, true),
        Triple("GlobalSampleBank · \$BF:C0B6 · Routine_Macros:185874", 0xBFC0B6, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "SUPERSTAR SOCCER")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

object DriverJurassicPark : SnesDriver {
    override val nom = "JurassicPark"
    private val PISTES = listOf(
        Triple("LDX.w #JP_SPCEngine · \$80:8000 · Routine_Macros:81", 0x808000, true),
        Triple("InitializeSPC700 · \$80:F9D5 · Routine_Macros:10938", 0x80F9D5, true),
        Triple("LDX.w #JP_SPCEngine · \$81:A8DD · Routine_Macros:15310", 0x81A8DD, true),
        Triple("BoneCrunchSamples · \$83:8000 · Routine_Macros:19684", 0x838000, false),
        Triple("DATA_B48000 · \$B4:8000 · Routine_Macros:99559", 0xB48000, false),
        Triple("WelcomeToJurassicParkSamples · \$B4:D212 · Routine_Macros:99562", 0xB4D212, false),
        Triple("DATA_B4EFFB · \$B4:EFFB · Routine_Macros:99565", 0xB4EFFB, false),
        Triple("AlanSamples · \$B5:D7B6 · Routine_Macros:99590", 0xB5D7B6, false),
        Triple("HelicopterSamples · \$B5:DFD3 · Routine_Macros:99593", 0xB5DFD3, false),
        Triple("DATA_B68000 · \$B6:8000 · Routine_Macros:99617", 0xB68000, false),
        Triple("DATA_B78000 · \$B7:8000 · Routine_Macros:99635", 0xB78000, false),
        Triple("DATA_B88000 · \$B8:8000 · Routine_Macros:99683", 0xB88000, false),
        Triple("WaterSplashSamples · \$B8:F660 · Routine_Macros:99686", 0xB8F660, false),
        Triple("DATA_B98000 · \$B9:8000 · Routine_Macros:99704", 0xB98000, false),
        Triple("TRexRoarSamples · \$B9:CACE · Routine_Macros:99707", 0xB9CACE, false),
        Triple("GrantSamples · \$B9:E92C · Routine_Macros:99710", 0xB9E92C, false),
        Triple("MrGrantSamples · \$B9:F206 · Routine_Macros:99713", 0xB9F206, false),
        Triple("DATA_BA8000 · \$BA:8000 · Routine_Macros:99731", 0xBA8000, false),
        Triple("DATA_BB8000 · \$BB:8000 · Routine_Macros:99756", 0xBB8000, false),
        Triple("DATA_BC8000 · \$BC:8000 · Routine_Macros:99784", 0xBC8000, false),
        Triple("DATA_BD8000 · \$BD:8000 · Routine_Macros:99802", 0xBD8000, false),
        Triple("UnknownSamples · \$BD:E792 · Routine_Macros:99805", 0xBDE792, false),
        Triple("DATA_BE8000 · \$BE:8000 · Routine_Macros:99823", 0xBE8000, false),
        Triple("RaptorSamples · \$BE:E361 · Routine_Macros:99826", 0xBEE361, false),
        Triple("DATA_BF8000 · \$BF:8000 · Routine_Macros:99844", 0xBF8000, false),
        Triple("DATA_BFD6D8 · \$BF:D6D8 · Routine_Macros:99847", 0xBFD6D8, false),
        Triple("MrDNAChatterSamples · \$BF:F19D · Routine_Macros:99850", 0xBFF19D, false))
    override val cancons: List<String> = PISTES.map { it.first }
    override fun reconeix(rom: RomSnes): Boolean = expTitolConte(rom, "JURASSIC PARK")
    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) = expCadenaInicia(spc, rom, PISTES.map { it.second to it.third }, id)
    override fun frame(spc: SnesSpc, rom: RomSnes, n: Long) {}
}

val EXP_SNES_S2: List<SnesDriver> = listOf(DriverMsPacMan, DriverFrogger, DriverPacMan2, DriverEWJ2, DriverLester, DriverRiseRobots, DriverFaceball, DriverSimCity, DriverMarioPaint, DriverWariosWoods, DriverTMNT4, DriverSuperPunchOut, DriverSMRPG, DriverBSZelda, DriverISSDeluxe, DriverJurassicPark)
val EXP_COMPAT_S2: List<JocCompat> = listOf(
    JocCompat("Ms. Pac-Man", "SNES", "SNES_MsPacMan"),
    JocCompat("Frogger", "SNES", "SNES_Frogger"),
    JocCompat("Pac-Man 2: The New Adventures", "SNES", "SNES_PacMan2"),
    JocCompat("Earthworm Jim 2", "SNES", "SNES_EWJ2"),
    JocCompat("Lester the Unlikely", "SNES", "SNES_Lester"),
    JocCompat("Rise of the Robots", "SNES", "SNES_RiseRobots"),
    JocCompat("Faceball 2000", "SNES", "SNES_Faceball"),
    JocCompat("SimCity", "SNES", "SNES_SimCity"),
    JocCompat("Mario Paint", "SNES", "SNES_MarioPaint"),
    JocCompat("Wario's Woods", "SNES", "SNES_WariosWoods"),
    JocCompat("Teenage Mutant Ninja Turtles IV: Turtles in Time", "SNES", "SNES_TMNT4"),
    JocCompat("Super Punch-Out!!", "SNES", "SNES_SuperPunchOut"),
    JocCompat("Super Mario RPG", "SNES", "SNES_SMRPG"),
    JocCompat("BS Zelda no Densetsu", "SNES", "SNES_BSZelda"),
    JocCompat("International Superstar Soccer Deluxe", "SNES", "SNES_ISSDeluxe"),
    JocCompat("Jurassic Park", "SNES", "SNES_JurassicPark"))
