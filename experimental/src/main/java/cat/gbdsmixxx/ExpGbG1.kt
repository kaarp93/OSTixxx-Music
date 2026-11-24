package cat.gbdsmixxx

/*
 * Lot G, part 1: Keitai Denjuu Telefang, Medarot 3 i Medarot 4 (GBC, MBC5/MBC3 de 2 MB; la CPU fa servir el mode de banc de 9 bits, que serveix per als tres).
 * Cap dels tres desassemblats porta el motor de so; el que hi ha són els punts d'entrada de la ROM0 (stubs amb banc propi) i les variables de petició. Com que a Telefang el
 * motor és `InitializeSoundEngine` $0439 i, a la interrupció de vblank, `call $0464` (30 bytes més endavant: +$2B), i a Medarot 3 i 4 el patró és el mateix (init $0313 / $036E
 * i vblank `$033E` / `$0399`, també +$2B), s'hi executa tal com és. DEDUÏT: que aquests stubs són el motor de música i els números de cançó; NO comprovat amb cap ROM.
 */

private fun gNomsTelefang(): List<String> {
    val l = ArrayList<String>()
    for (i in 0..0x3F) l.add("Telefang · cançó $" + gH(i) + " · indexada per ROM0:15F5 (H1)")
    for (i in 0..0x3F) l.add("Telefang · cançó $" + gH(i) + " · valor directe a FFA0 (H2)")
    return l
}

/** Keitai Denjuu Telefang (PW/SP). De telefang/telefang: `Sound_IndexMusicSetBySong` ROM0:$15F5 (components/sound/indexing.asm:5), `W_Sound_NextBGMSelect` HRAM $FFA0 (control.asm:2), `W_Sound_MusicSet` $CFF8 (indexing.asm:2), `InitializeSoundEngine` $0439 (system/main.asm:13) i `call $0464` al vblank (lcdc/vblank_irq.asm:32). */
object ExpTelefang : ExpGbGDriver(
    "Telefang", 0..127, gNomsTelefang(), true, 59.7275,
    { r -> gTitol(r, "TELEFANG") && r.size >= 0x200000 },
    { cpu, m -> m.banc = 1; cpu.crida(0x0439) },
    { cpu, m, id ->
        val n = id and 0x3F
        if (id < 64) { cpu.a = n; cpu.crida(0x15F5); m.wb(0xFFA0, cpu.a) } else m.wb(0xFFA0, n)
        m.wb(0xFFA1, 0)
    },
    { cpu, m, _ -> cpu.crida(0x0464) },
)

private fun gNomsMed(nom: String, upd: String): List<String> = (0..0x3F).map { "$nom · cançó $" + gH(it) + " · petició + vblank $upd" }

/** Medarot 3 (Kabuto/Kuwagata). De Medabots/medarot3: `ScheduleSoundEffect` ROM0:$27DA escriu l'efecte a $FFA1 (game/src/core/sound.asm:4); init `call $0313` (core/main.asm:63) i `call $033E ; Music most likely.` al vblank (core/interrupts.asm:51). La petició de música a $FFA0 és DEDUÏDA de Telefang (mateixa disposició d'HRAM: $FFA1 efecte, $FFA8 mostra). */
object ExpMedarot3 : ExpGbGDriver(
    "Medarot3", 0..0x3F, gNomsMed("Medarot 3", "ROM0:033E"), true, 59.7275,
    { r -> gTitol(r, "MEDAROT3") },
    { cpu, m -> m.banc = 1; cpu.crida(0x0313) },
    { _, m, id -> m.wb(0xFFA1, 0); m.wb(0xFFA0, id) },
    { cpu, _, _ -> cpu.crida(0x033E) },
)

/** Medarot 4 (Kabuto/Kuwagata). De Medabots/medarot4: `ScheduleMusic` ROM0:$2B5C escriu a `W_ScheduleMusic` $CF30 i `ScheduleSoundEffect` a $CF31 (game/src/core/sound.asm:3-14); init `call $036E` (core/main.asm:63) i `HighBankSafeMusic` crida `call $0399` (text/main_script.asm:206-214) des del vblank (core/interrupts.asm:66). Els títols de música són a banc 3 $7383 (version/kuwagata/ptrlist_data.asm:1). */
object ExpMedarot4 : ExpGbGDriver(
    "Medarot4", 0..0x3F, gNomsMed("Medarot 4", "ROM0:0399"), true, 59.7275,
    { r -> gTitol(r, "MEDAROT4") },
    { cpu, m -> m.banc = 1; cpu.crida(0x036E) },
    { _, m, id -> m.wb(0xCF31, 0); m.wb(0xCF30, id) },
    { cpu, _, _ -> cpu.crida(0x0399) },
)

val EXP_GB_G1: List<GbDriver> = listOf(ExpTelefang, ExpMedarot3, ExpMedarot4)
val EXP_COMPAT_G1: List<JocCompat> = listOf(
    JocCompat("Keitai Denjuu Telefang", "Game Boy Color", "GB_Telefang", confirmatAbans = true),
    JocCompat("Medarot 3", "Game Boy Color", "GB_Medarot3"),
    JocCompat("Medarot 4", "Game Boy Color", "GB_Medarot4"),
)
