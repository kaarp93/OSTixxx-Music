package cat.gbdsmixxx

/**
 * CRC32 de les ROM que activen cada driver fet a mà (CUSTOM) i cada experimental compilat (EXP): el del fitxer sencer, capçalera iNES inclosa (el mateix que
 * `crcDe`, libretro i els dat de No-Intro). l'autor (2026-11-17): «TOTS els drivers han d'anar per CRC». Un driver només s'activa si el CRC de la ROM és a la seva llista;
 * la signatura de bytes (`reconeix`) ja no activa res. Clau = clau d'àlbum (prefix del sistema + nom del driver). D'on surt cada CRC: CRC_DRIVERS.md.
 * Generat a partir de les ROM i els informes de l'autor i dels dat de libretro-database; cap valor de memòria.
 */
object CrcDrivers {
    val PER_CLAU: Map<String, Set<Long>> = mapOf(
        "GB_Zelda" to setOf(
            0x8CF27C90L,   // Legend of Zelda, The - Link's Awakening (USA, Europe).gb · ROM de l'autor
        ),
        "GB_Kirby" to setOf(
            0x40F25740L,   // Kirby's Dream Land (USA, Europe).gb · ROM de l'autor
        ),
        "GB_Metroid2" to setOf(
            0xDEE05370L,   // Metroid II - Return of Samus (World).gb · ROM de l'autor
        ),
        "GB_MarioLand" to setOf(
            0x2C27EC70L,   // Super Mario Land (World) (Rev 1).gb · desassemblatge: SHA1 de kaspermeerts/supermarioland
        ),
        "GB_Tetris" to setOf(
            0x46DF91ADL,   // Tetris (World) (Rev 1).gb · desassemblatge: MD5 de osnr/tetris
        ),
        "GB_WarioLand" to setOf(
            0x40BE3889L,   // Wario Land - Super Mario Land 3 (World).gb · única versió a No-Intro
        ),
        "GB_MegaMan2" to setOf(
            0x5E90CB48L,   // Mega Man II (Europe).gb · ROM de l'autor
        ),
        "GB_FFA" to setOf(
            0x18C78B3AL,   // Final Fantasy Adventure (USA).gb · ROM de l'autor
        ),
        "GB_DonkeyKong94" to setOf(
            0xF777A5D8L,   // Donkey Kong (World) (Rev 1) (SGB Enhanced).gb · ROM de l'autor
        ),
        "GB_MarioTennis" to setOf(
            0xA781C63CL,   // Mario Tennis (USA).gbc · ROM de l'autor
        ),
        "GB_MoleMania" to setOf(
            0x2C36C74CL,   // Mole Mania (USA, Europe) (SGB Enhanced).gb · desassemblatge: «Mole Mania US rom» (froggestspirit/mmania)
        ),
        "GB_PocketLove" to setOf(
            0xD570A9DFL,   // Pocket Love (Japan) (SGB Enhanced).gb · única versió a No-Intro
        ),
        "GB_PokePinball" to setOf(
            0x03CE8D9AL,   // Pokemon Pinball (USA, Australia) (Rumble Version) (SGB Enhanced) (GB Compatible).gbc · ROM de l'autor
        ),
        "GB_PokePuzzle" to setOf(
            0xD06BBA96L,   // Pokemon Puzzle Challenge (USA, Australia).gbc · desassemblatge: SHA1 de pret/pokepuzzle
        ),
        "GB_OracleAges" to setOf(
            0x3800A387L,   // Legend of Zelda, The - Oracle of Ages (USA, Australia).gbc · ROM de l'autor
        ),
        "GB_OracleSeasons" to setOf(
            0xD7E9F5D7L,   // Legend of Zelda, The - Oracle of Seasons (USA, Australia).gbc · ROM de l'autor
        ),
        "GB_CarrotCrazy" to setOf(
            0x11FB5617L,   // Looney Tunes - Carrot Crazy (USA) (En,Fr,Es) (GB Compatible).gbc · desassemblatge: MD5 de huderlem/carrotcrazy
        ),
        "GB_BattleTanx" to setOf(
            0x4431F8E7L,   // BattleTanx (USA).gbc · ROM de l'autor
        ),
        "GB_Robopon" to setOf(
            0xCB0B8003L,   // Robot Poncots - Sun Version (Japan) (SGB Enhanced) (GB Compatible).gbc · desassemblatge: MD5 de PikalaxALT/robopon (Sun)
            0xB28F32ACL,   // Robot Poncots - Star Version (Japan) (SGB Enhanced) (GB Compatible).gbc · desassemblatge: MD5 de PikalaxALT/robopon (Star)
        ),
        "GB_MegaMan3GB" to setOf(
            0x5175D761L,   // Mega Man III (USA).gb · ROM de l'autor
        ),
        "GB_BionicCommando" to setOf(
            0x41DBB5FBL,   // Bionic Commando (USA).gb · ROM de l'autor
        ),
        "GB_AlfredChicken" to setOf(
            0x3B9C3BC6L,   // Alfred Chicken (USA).gb · ROM de l'autor
        ),
        "GB_LionKingGB" to setOf(
            0x3430D261L,   // Lion King, The (USA).gb · versió USA (única USA a No-Intro; sense ROM)
        ),
        "GB_RType" to setOf(
            0xE0F23FC0L,   // R-Type (USA, Europe).gb · ROM de l'autor
        ),
        "GB_RType2" to setOf(
            0x6002E291L,   // R-Type II (Japan) (En).gb · ROM de l'autor
            0x2FE2A72DL,   // R-Type II (Europe).gb · ROM de l'autor
        ),
        "GB_AladdinGB" to setOf(
            0xAF6BDC50L,   // Aladdin (USA) (SGB Enhanced).gb · versió USA (única USA de GB a No-Intro; sense ROM)
        ),
        "GB_MegaMan1GB" to setOf(
            0x47E70E08L,   // Mega Man - Dr. Wily's Revenge (USA).gb · ROM de l'autor
        ),
        "GB_MegaMan4GB" to setOf(
            0xABCEA00DL,   // Mega Man IV (USA).gb · ROM de l'autor
        ),
        "GB_MegaMan5GB" to setOf(
            0x72E6D21DL,   // Mega Man V (USA) (SGB Enhanced).gb · ROM de l'autor
        ),
        "GB_EarthwormJim" to setOf(
            0x259FF267L,   // Earthworm Jim (USA).gb · versió USA (única USA a No-Intro; sense ROM)
        ),
        "GB_Speedball2" to setOf(
            0xFD06E22DL,   // Speedball 2 - Brutal Deluxe (USA, Europe).gb · única versió comercial a No-Intro
        ),
        "GB_BlasterMaster" to setOf(
            0x2F91E17CL,   // Blaster Master - Enemy Below (USA, Europe) (SGB Enhanced) (GB Compatible).gbc · única versió comercial a No-Intro
        ),
        "GB_KidIcarusOMM" to setOf(
            0x0C042862L,   // Kid Icarus - Of Myths and Monsters (USA, Europe).gb · desassemblatge: hash de ordinary-hacker/kid-icarus-omm
        ),
        "GB_JungleBook" to setOf(
            0xB64D27EEL,   // Jungle Book, The (USA, Europe).gb · ROM de l'autor
        ),
        "GB_PocketKing" to setOf(
            0xDEE71D44L,   // Pocket King (Japan) (SGB Enhanced) (GB Compatible).gbc · única versió a No-Intro
        ),
        "NES_MegaMan2Nes" to setOf(
            0x80E08660L,   // Mega Man 2 (USA).nes · informe de l'autor
            0xF78ACB96L,   // Megaman II (E) [!].nes · informe de l'autor
        ),
        "NES_SuperC" to setOf(
            0x8BE2AC58L,   // Super C (USA).nes · informe de l'autor
        ),
        "NES_CaptainAmerica" to setOf(
            0x876A3A30L,   // Captain America and The Avengers (E).nes · informe de l'autor
            0xE37E3F95L,   // Captain America and the Avengers (USA).nes · informe de l'autor
        ),
        "NES_FelixCat" to setOf(
            0x26DC493BL,   // Felix the Cat (E) [!].nes · informe de l'autor
            0x3856F581L,   // Felix the Cat (USA) (Hudson).nes · informe de l'autor
        ),
        "NES_IceClimber" to setOf(
            0x70044A74L,   // Ice Climber (U) [!].nes · informe de l'autor
            0x913F07B0L,   // Ice Climber (USA, Europe, Asia) (En).nes · informe de l'autor
        ),
        "NES_Excitebike" to setOf(
            0x625BF658L,   // Excitebike (Japan, USA) (En).nes · informe de l'autor
            0xE811FCF8L,   // Excitebike (E) [!].nes · informe de l'autor
        ),
        "NES_NutsMilk" to setOf(
            0x11879FE8L,   // ? · desassemblatge: CRC de cyneprepou4uk (Nuts & Milk (J) [!])
        ),
        "NES_SonSon" to setOf(
            0xF7C8F42DL,   // ? · desassemblatge: CRC de cyneprepou4uk (Son Son (J) [!])
        ),
        "NES_AdventureIsland" to setOf(
            0x995B3FFFL,   // Adventure Island (USA).nes · informe de l'autor
            0xC9AD622CL,   // Hudson's Adventure Island (E) [!].nes · informe de l'autor
        ),
        "NES_TecmoWorldCup" to setOf(
            0x19F24980L,   // ? · desassemblatge: CRC de cyneprepou4uk (Tecmo World Cup Soccer (J) [!])
        ),
        "NES_BugsBunny" to setOf(
            0xDB0C3656L,   // Bugs Bunny Crazy Castle, The (U) [!].nes · informe de l'autor
            0xFC7EB9B9L,   // Bugs Bunny Crazy Castle, The (USA).nes · informe de l'autor
        ),
        "NES_LittleMermaid" to setOf(
            0x08EB97DBL,   // Little Mermaid, The (U) [!].nes · informe de l'autor
        ),
        "NES_DoubleDragon2" to setOf(
            0x8CF69E4BL,   // ? · desassemblatge: CRC de cyneprepou4uk (Double Dragon II (J))
        ),
        "NES_NinjaGaiden" to setOf(
            0x80D3B95EL,   // Ninja Gaiden (USA).nes · informe de l'autor
        ),
        "NES_LegacyWizard" to setOf(
            0x8E87B159L,   // Legacy of the Wizard (USA).nes · informe de l'autor
            0xB534EB48L,   // Legacy of the Wizard (U) [!].nes · informe de l'autor
        ),
        "NES_BubbleBobble2" to setOf(
            0x5B2C21C8L,   // Bubble Bobble Part 2 (USA).nes · informe de l'autor
            0xCA06CB60L,   // Bubble Bobble Part 2 (U) [!].nes · informe de l'autor
        ),
        "NES_MegaMan1" to setOf(
            0xCA2AB7E2L,   // Mega Man (USA).nes · informe de l'autor
        ),
        "NES_MegaMan4" to setOf(
            0x7ABBBF23L,   // Mega Man 4 (USA) (Rev 1).nes · informe de l'autor
        ),
        "NES_MegaMan5" to setOf(
            0xBD918F62L,   // Mega Man 5 (USA).nes · informe de l'autor
        ),
        "NES_MegaMan6" to setOf(
            0x1EFCAC48L,   // Megaman VI (U) [!].nes · informe de l'autor
            0xFA9EC0C4L,   // Mega Man 6 (USA).nes · informe de l'autor
        ),
        "NES_Milon" to setOf(
            0x1741286BL,   // Milon's Secret Castle (U) [!].nes · informe de l'autor
        ),
        "NES_Akumajou" to setOf(
            0x2EAD04C5L,   // Akumajou Densetsu (Japan).nes · ROM de l'autor
        ),
        "NES_Gradius" to setOf(
            0x54F1AF1FL,   // Gradius (USA).nes · ROM de l'autor
        ),
        "NES_Castlevania" to setOf(
            0xAE0FA667L,   // Castlevania (USA).nes · ROM de l'autor
            0x12A6CB14L,   // Castlevania (USA) (Rev 1).nes · informe de l'autor
            0x9A3CF4D6L,   // Castlevania (E) [!].nes · informe de l'autor
        ),
        "NES_Zelda2" to setOf(
            0x861C3FE6L,   // Zelda II - The Adventure of Link (USA).nes · ROM de l'autor
            0xBF7692BCL,   // Zelda II - The Adventure of Link (Europe) (Rev 2).nes · ROM de l'autor
        ),
        "NES_Crystalis" to setOf(
            0x1BD39032L,   // Crystalis (U) [!].nes · informe de l'autor
            0xF8F1865DL,   // Crystalis (USA).nes · informe de l'autor
        ),
        "NES_SMB2" to setOf(
            0xDEA55F53L,   // Super Mario Bros. 2 (USA) (Rev 1).nes · informe de l'autor
        ),
        "NES_SMB3" to setOf(
            0xE8C3AF69L,   // Super Mario Bros. 3 (USA) (Rev 1).nes · informe de l'autor
        ),
        "NES_TecmoSB" to setOf(
            0xFC5E400FL,   // Tecmo Super Bowl (USA).nes · informe de l'autor
        ),
        "NES_Mother" to setOf(
            0xCB084AC5L,   // Mother (Japan).nes · ROM de l'autor
            0xABBE02FAL,   // Earth Bound (USA) (Proto).nes · informe de l'autor
        ),
        "NES_MarioBros" to setOf(
            0x0305E5D6L,   // Mario Bros. (World).nes · desassemblatge: versió NTSC «World» de RussianManSMWC/Mario-Bros.-Dis
        ),
        "NES_TetrisNES" to setOf(
            0xD16EA396L,   // Tetris (USA).nes · informe de l'autor
        ),
        "NES_Elite" to setOf(
            0x4CF12D39L,   // Elite (E) [!].nes · informe de l'autor
            0xEAB04ED0L,   // Elite (Europe) (En,Fr,De).nes · informe de l'autor
        ),
        "NES_Transformers" to setOf(
            0x8B75BA7EL,   // Tatakae! Chou Robot Seimeitai Transformers - Convoy no Nazo (Japan).nes · única versió de cartutx a No-Intro
        ),
        "NES_DrMario" to setOf(
            0x3F4E1ECFL,   // Dr. Mario (E) [!].nes · informe de l'autor
            0x92BED9D7L,   // Dr. Mario (Japan, USA) (En) (Rev 1).nes · informe de l'autor
        ),
        "NES_DonkeyKong" to setOf(
            0xE40B593BL,   // Donkey Kong (U) (PRG1) [!].nes · informe de l'autor
        ),
        "NES_BalloonFight" to setOf(
            0x6EDDA6B3L,   // Balloon Fight (E) [!].nes · informe de l'autor
            0x87D538E4L,   // Balloon Fight (USA).nes · informe de l'autor
        ),
        "NES_DragonWarrior" to setOf(
            0xABF3C899L,   // Dragon Warrior (USA) (Rev 1).nes · informe de l'autor
            0xD4D5F5D7L,   // Dragon Warrior (U) (PRG1) [!].nes · informe de l'autor
        ),
        "NES_DonaldLand" to setOf(
            0xC44397DDL,   // Donald Land (J).nes · informe de l'autor
        ),
        "NES_SMB" to setOf(
            0x393A432FL,   // Super Mario Bros. (World).nes · informe de l'autor
        ),
        "NES_Zelda1" to setOf(
            0x3C07A00AL,   // Legend of Zelda, The (E) (PRG1) [!].nes · informe de l'autor
            0xE47960E0L,   // Legend of Zelda, The (USA) (Rev 1).nes · informe de l'autor
        ),
        "NES_Metroid" to setOf(
            0xA591CC24L,   // Metroid (E) [!].nes · informe de l'autor
            0xD1A02CABL,   // Metroid (USA).nes · informe de l'autor
        ),
        "NES_Contra" to setOf(
            0x52CD5CD8L,   // Contra (USA).nes · ROM de l'autor
        ),
        "NES_MightyFinalFight" to setOf(
            0x84C1E146L,   // Mighty Final Fight (USA).nes · codi (CRC_MIGHTY_FINAL_FIGHT; USA confirmada per l'autor)
            0x5CC018A8L,   // Mighty Final Fight (Europe) · codi (CRC_MIGHTY_FINAL_FIGHT)
        ),
        "NES_MegaMan3" to setOf(
            0x45134A3EL,   // Mega Man 3 (USA).nes · ROM de l'autor
            0xDE4499B9L,   // Megaman_III_E_PRG1_.nes · ROM de l'autor
        ),
        "NES_FF1" to setOf(
            0x8125B019L,   // Final Fantasy (USA).nes · informe de l'autor
            0x92B1B80AL,   // Final Fantasy (J) (PRG1) [!].nes · informe de l'autor
        ),
        "SNES_SMW" to setOf(
            0xB19ED489L,   // Super Mario World (USA).sfc · versió USA (única USA a No-Intro; sense ROM)
        ),
        "SNES_SuperMetroid" to setOf(
            0xD63ED5F8L,   // Super Metroid (Japan, USA) (En,Ja).sfc · única versió NTSC a No-Intro
        ),
        "SNES_DKC1" to setOf(
            0xC946DCA0L,   // Donkey Kong Country (USA).sfc · ROM de l'autor
        ),
        "SNES_DKC2" to setOf(
            0x006364DBL,   // Donkey Kong Country 2 - Diddy's Kong Quest (USA) (En,Fr).sfc · ROM de l'autor
        ),
        "SNES_DKC3" to setOf(
            0x448EEC19L,   // Donkey Kong Country 3 - Dixie Kong's Double Trouble! (USA) (En,Fr).sfc · ROM de l'autor
        ),
        "SNES_SGnG" to setOf(
            0x6AABA901L,   // Super Ghouls 'N Ghosts (USA).sfc · ROM de l'autor
        ),
        "SNES_ZAMN" to setOf(
            0x7CFC0C7CL,   // Zombies Ate My Neighbors (USA).sfc · ROM de l'autor
        ),
        "SNES_GoofTroop" to setOf(
            0x4AAFA462L,   // Goof Troop (USA).sfc · ROM de l'autor
        ),
        "SNES_EarthBound" to setOf(
            0xDC9BB451L,   // EarthBound (USA).sfc · ROM de l'autor
            0x2019FABEL,   // Mother 2 - Gyiyg no Gyakushuu (Japan).sfc · ROM de l'autor
        ),
        "SNES_Zelda3" to setOf(
            0x777AAC2FL,   // Legend of Zelda, The - A Link to the Past (USA).sfc · ROM de l'autor
        ),
        "SNES_FF6" to setOf(
            0x45EF5AC8L,   // Final Fantasy VI (Japan).sfc · ROM de l'autor
        ),
        "SNES_FF5" to setOf(
            0xC1BC267DL,   // Final Fantasy V (Japan).sfc · ROM de l'autor
        ),
        "SNES_FF4" to setOf(
            0x96CFA5C9L,   // Final Fantasy II (USA, Europe) (Rev 1) (Virtual Console).sfc · ROM de l'autor
            0x21027C5DL,   // Final Fantasy IV (Japan).sfc · ROM de l'autor
        ),
        "SNES_SMT1" to setOf(
            0xA3D40A74L,   // Shin Megami Tensei (Japan) (Rev 1).sfc · ROM de l'autor
        ),
        "SNES_MegaMan7" to setOf(
            0x2D947536L,   // Mega Man 7 (USA).sfc · ROM de l'autor
        ),
        "SNES_MegaManX" to setOf(
            0x1033EBA4L,   // Mega Man X (USA).sfc · ROM de l'autor
        ),
        "SNES_SMAS_SMB1" to setOf(
            0x925637C7L,   // Super Mario All-Stars (USA).sfc · ROM de l'autor
        ),
        "SNES_SMAS_SMB2" to setOf(
            0x925637C7L,   // Super Mario All-Stars (USA).sfc · ROM de l'autor
        ),
        "SNES_SMAS_SMB3" to setOf(
            0x925637C7L,   // Super Mario All-Stars (USA).sfc · ROM de l'autor
        ),
        "SNES_Pocky2" to setOf(
            0xFC00BB1BL,   // Pocky & Rocky 2 (USA).sfc · ROM de l'autor
        ),
        "SNES_ChronoTrigger" to setOf(
            0x2D206BF7L,   // Chrono Trigger (USA).sfc · ROM de l'autor
        ),
        "SNES_KirbyDL3" to setOf(
            0xEC8A48F6L,   // Kirby's Dream Land 3 (USA).sfc · ROM de l'autor
        ),
        "GB_Telefang" to setOf(
            0x8B61CFCCL,   // Keitai Denjuu Telefang - Power Version (Japan) (GB Compatible).gbc · desassemblatge: hash de telefang/telefang (Power)
            0xFB528F66L,   // Keitai Denjuu Telefang - Speed Version (Japan) (GB Compatible).gbc · desassemblatge: hash de telefang/telefang (Speed)
        ),
        "GB_Medarot3" to setOf(
            0xE655632CL,   // Medarot 3 - Kabuto Version (Japan).gbc · desassemblatge: hash de Medabots/medarot3 (Kabuto)
            0xBC617834L,   // Medarot 3 - Kuwagata Version (Japan).gbc · desassemblatge: hash de Medabots/medarot3 (Kuwagata)
        ),
        "GB_Medarot4" to setOf(
            0xC192A368L,   // Medarot 4 - Kabuto Version (Japan).gbc · desassemblatge: hash de Medabots/medarot4 (Kabuto)
            0x6A29B9D8L,   // Medarot 4 - Kuwagata Version (Japan).gbc · desassemblatge: hash de Medabots/medarot4 (Kuwagata)
        ),
        "GB_MarioGolf" to setOf(
            0x905AD0CBL,   // Mario Golf (USA).gbc · desassemblatge: «mgolf.gbc (USA)» (DarkRTA/mgolf)
        ),
        "GB_HarvestMoonGB" to setOf(
            0xD3E2FD02L,   // Bokujou Monogatari GB (Japan) (SGB Enhanced).gb · desassemblatge: MD5 de H0smax/HMGB-Disassembly
        ),
        "GB_WarioLand3" to setOf(
            0x480D0259L,   // Wario Land 3 (World) (En,Ja).gbc · ROM de l'autor
        ),
        "GB_StarOceanBS" to setOf(
            0x8C7DDBDAL,   // Star Ocean - Blue Sphere (Japan) (SGB Enhanced) (GB Compatible).gbc · única versió a No-Intro
        ),
        "GB_DejaVu12" to setOf(
            0xAD0937F4L,   // Deja Vu I & II - The Casebooks of Ace Harding (USA).gbc · desassemblatge: SHA1 de Hoshiruna/djv1-2disasm (versió US, la del driver)
        ),
        "GB_Robopon1D" to setOf(
            0xCB0B8003L,   // Robot Poncots - Sun Version (Japan) (SGB Enhanced) (GB Compatible).gbc · desassemblatge: MD5 de PikalaxALT/robopon (Sun)
            0xB28F32ACL,   // Robot Poncots - Star Version (Japan) (SGB Enhanced) (GB Compatible).gbc · desassemblatge: MD5 de PikalaxALT/robopon (Star)
        ),
        "GB_PokemonGS" to setOf(
            0x6BDE3C3EL,   // Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc · desassemblatge: hash de pret/pokegold (Gold)
            0x8AD48636L,   // Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc · desassemblatge: hash de pret/pokegold (Silver)
        ),
        "GB_DuckTales" to setOf(
            0x2BBBB54DL,   // DuckTales (USA).gb · document del driver: versió USA
        ),
        "GB_YugiohDM" to setOf(
            0x8875EC54L,   // Yu-Gi-Oh! Duel Monsters (Japan) (SGB Enhanced).gb · desassemblatge: SHA1 de ElectroDeoxys/ygodm (versió japonesa, la del driver)
        ),
        "GB_ResidentEvilGBC" to setOf(
            0x53F7BBA1L,   // Resident Evil (World) (Proto 2).gbc · desassemblatge: hash de ecruells/residentevilgbc
        ),
        "GB_MagiNation" to setOf(
            0x5042450BL,   // Magi Nation (USA).gbc · desassemblatge: hash de GauChoob/magination
        ),
        "GB_XLunarChase" to setOf(
            0x75E1D268L,   // X (Japan).gb · única versió a No-Intro
        ),
        "GB_KirbyTiltNTumble" to setOf(
            0xE541ACF1L,   // Kirby - Tilt 'n' Tumble (USA).gbc · única versió a No-Intro (USA)
        ),
        "GB_Alleyway" to setOf(
            0x5CC01586L,   // Alleyway (World).gb · informe de l'autor
        ),
        "GB_Flipull" to setOf(
            0xA2F30B4BL,   // Flipull (USA).gb · document del driver: versió US
        ),
        "GB_TetrisAttack" to setOf(
            0xB76C769BL,   // Tetris Attack (USA, Europe) (SGB Enhanced).gb · document del driver: US V1.0
        ),
        "GB_FinalFantasyLegend2" to setOf(
            0x58314182L,   // Final Fantasy Legend II (USA).gb · versió USA (única comercial a No-Intro)
        ),
        "NES_BattleCityN" to setOf(
            0xF599A07EL,   // Battle City (J).nes · informe de l'autor
        ),
        "NES_MappyN" to setOf(
            0x3F0E8E0AL,   // Mappy (J) [!].nes · informe de l'autor
        ),
        "NES_YieArKungFuN" to setOf(
            0x0D526208L,   // ? · desassemblatge: CRC de cyneprepou4uk (Yie Ar Kung-Fu (J) (V1.4) [!])
        ),
        "NES_ContraForceN" to setOf(
            0x83D69922L,   // Contra Force (U) [!].nes · informe de l'autor
        ),
        "NES_IndyHeatN" to setOf(
            0x311B5A58L,   // Danny Sullivan's Indy Heat (U) [!].nes · informe de l'autor
        ),
        "NES_SolsticeN" to setOf(
            0x1D60732EL,   // ? · desassemblatge: CRC de cyneprepou4uk (Solstice (U) [!])
        ),
        "NES_RoboCop3N" to setOf(
            0xFBBB58A6L,   // ? · desassemblatge: CRC de cyneprepou4uk (RoboCop 3 (U) [!])
        ),
        "NES_BlasterMasterN" to setOf(
            0xC3961CE2L,   // Blaster Master (USA).nes · versió USA (única USA a No-Intro; sense ROM)
        ),
        "NES_StarTropicsN" to setOf(
            0x5F212B6FL,   // StarTropics (USA).nes · desassemblatge: «StarTropics (USA)» (jogalleciez/StarTropics-decomp)
        ),
        "NES_LupinIIIN" to setOf(
            0x0977F982L,   // Lupin Sansei - Pandora no Isan (J) [T+Eng_Vice].nes · informe de l'autor
        ),
        "SNES_Arcana" to setOf(
            0xC891B297L,   // Arcana (USA).sfc · única versió a No-Intro
        ),
        "SNES_SMKart" to setOf(
            0x56410E5EL,   // Super Mario Kart (Europe).sfc · ROM de l'autor
            0xCD80DB86L,   // Super Mario Kart (USA).sfc · ROM de l'autor
            0xC8002453L,   // Super Mario Kart (Japan).sfc · ROM de l'autor
        ),
        "SNES_Speedy" to setOf(
            0xE2DBAD76L,   // Speedy Gonzales - Los Gatos Bandidos (USA).sfc · document del driver: V1.0 USA
        ),
        "SNES_MsPacMan" to setOf(
            0x74D210D3L,   // Ms. Pac-Man (USA).sfc · versió USA (única USA a No-Intro; sense ROM)
        ),
        "SNES_Frogger" to setOf(
            0x2488B8F2L,   // Frogger (USA).sfc · única versió a No-Intro
        ),
        "SNES_PacMan2" to setOf(
            0x9FC62BCCL,   // Pac-Man 2 - The New Adventures (USA).sfc · versió USA (única USA a No-Intro; sense ROM)
        ),
        "SNES_EWJ2" to setOf(
            0x393DE197L,   // Earthworm Jim 2 (USA).sfc · versió USA (única USA a No-Intro; sense ROM)
        ),
        "SNES_Lester" to setOf(
            0x1A52FEE5L,   // Lester the Unlikely (USA).sfc · única versió a No-Intro
        ),
        "SNES_RiseRobots" to setOf(
            0x83BA0AC0L,   // Rise of the Robots (USA).sfc · versió USA (única USA a No-Intro; sense ROM)
        ),
        "SNES_Faceball" to setOf(
            0x8D4DF815L,   // Faceball 2000 (USA).sfc · única versió a No-Intro
        ),
        "SNES_SimCity" to setOf(
            0x8AEDD3A1L,   // SimCity (USA).sfc · versió USA (única USA a No-Intro; sense ROM)
        ),
        "SNES_MarioPaint" to setOf(
            0x38C9626CL,   // Mario Paint (Japan, USA) (En).sfc · versió NTSC (Japan, USA), única a No-Intro
        ),
        "SNES_WariosWoods" to setOf(
            0x2D1004F1L,   // Wario's Woods (USA).sfc · versió USA (única USA a No-Intro; sense ROM)
        ),
        "SNES_TMNT4" to setOf(
            0x5940BD99L,   // Teenage Mutant Ninja Turtles IV - Turtles in Time (USA).sfc · versió USA de cartutx (única a No-Intro; sense ROM)
        ),
        "SNES_SuperPunchOut" to setOf(
            0xE2F92F84L,   // Super Punch-Out!! (USA).sfc · versió USA (única USA a No-Intro; sense ROM)
        ),
        "SNES_SMRPG" to setOf(
            0x1B8A0625L,   // Super Mario RPG - Legend of the Seven Stars (USA).sfc · versió USA (única USA a No-Intro; sense ROM)
        ),
        "SNES_ISSDeluxe" to setOf(
            0x0A20E602L,   // International Superstar Soccer Deluxe (USA).sfc · versió USA (única USA a No-Intro; sense ROM)
        ),
        "SNES_Plok" to setOf(
            0xBBD678F6L,   // Plok (USA).sfc · versió USA (única USA a No-Intro; sense ROM)
        ),
        "SNES_SpiderXMen" to setOf(
            0xAE878CCBL,   // Spider-Man - X-Men - Arcade's Revenge (USA).sfc · versió USA de cartutx (única a No-Intro; sense ROM)
        ),
        "SNES_StarFox" to setOf(
            0x8FC4E6D0L,   // Star Fox (USA) (Rev 2).sfc · document del driver: USA 1.2 (Rev 2)
        ),
        "SNES_HarvestMoon" to setOf(
            0xF829129EL,   // Harvest Moon (USA).sfc · document del driver: versió US
        ),
    )

    /** Jocs de Pokémon amb motor propi (es desen com `musica_<codi>.gb`): CRC -> codi de `RomsConsoles.GB`. Abans es reconeixien pel títol de la capçalera. */
    val POKEMON: Map<Long, String> = mapOf(
        0x9F7FDD53L to "RED",   // Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb · desassemblatge: SHA1 de pret/pokered
        0xD6DA8A1AL to "BLU",   // Pokemon - Blue Version (USA, Europe) (SGB Enhanced).gb · desassemblatge: SHA1 de pret/pokered
        0x7D527D62L to "YEL",   // Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).gb · ROM de l'autor (i SHA1 de pret/pokeyellow)
        0xEE6F5188L to "CRY",   // Pokemon - Crystal Version (USA).gbc · ROM de l'autor (i SHA1 de pret/pokecrystal)
        0x81069D53L to "TCG",   // Pokemon Trading Card Game (USA, Australia) (SGB Enhanced) (GB Compatible).gbc · ROM de l'autor (i SHA1 de pret/poketcg)
        0x6C933A14L to "TCG2",   // Pokemon Card GB 2 - GR Dan Sanjou! (Japan).gbc · ROM de l'autor (i SHA1 de pret/poketcg2)
    )
    fun pokemon(codi: String): Set<Long> = POKEMON.filterValues { it == codi }.keys
}
