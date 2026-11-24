package cat.gbdsmixxx

/*
 * Lot G, part 2: Mario Golf (GBC), Hamtaro, ...
 */

private fun golfNoms(): List<String> {
    val l = ArrayList<String>()
    // Banc i punter de cada cançó: taula de bancs $7E:52A5 (43 entrades) i taula de punters $52D7 dins del banc de la cançó (assemblat amb la ROM).
    val bancs = intArrayOf(0x7E, 0x7E, 0x7E, 0x7E, 0x7E, 0x7E, 0x7E, 0x7E, 0x7E, 0x7E, 0x7D, 0x7D, 0x7D, 0x7D, 0x7D, 0x7D, 0x7D, 0x7D, 0x7D, 0x7D, 0x7D, 0x7D, 0x7D, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C, 0x7C)
    val ptr = intArrayOf(0x533B, 0x533C, 0x5345, 0x534E, 0x5357, 0x5360, 0x5369, 0x5372, 0x537B, 0x5384, 0x533C, 0x5345, 0x534E, 0x5357, 0x5360, 0x5369, 0x5372, 0x537B, 0x5384, 0x538D, 0x5396, 0x539F, 0x53A8, 0x533C, 0x5345, 0x534E, 0x5357, 0x5360, 0x5369, 0x5372, 0x537B, 0x5384, 0x538D, 0x5396, 0x539F, 0x53A8, 0x53B1, 0x53BA, 0x53C3, 0x53CC, 0x53D5, 0x53DE, 0x53E7)
    for (i in 0..42) l.add("Mario Golf · cançó $i · $" + gH(bancs[i]) + ":" + gH(ptr[i]))
    for (i in 0 until 111) l.add("Mario Golf · efecte $i · \$7E:4022 (prioritat \$7E:" + gH(0x5235 + i) + ")")
    return l
}

/**
 * Mario Golf (GBC, MBC5 de 2 MB, «MARIO GOLF»). De DarkRTA/mgolf (volcat mgbdis sense noms de so; assemblat amb rgbasm 0.9 la ROM surt de 2 MB): el motor de so és al banc $7E, amb una taula
 * de 11 punts d'entrada a $4000 (a la ROM0 es criden amb `rst $10` + índex de taula*2 + banc, p. ex. `rst $10; db $02,$7e` al vblank = índex 1): 0 `$4072` (init: NR52 = 0, 80; NR50 = $77; NR51 = $FF; $C4C0 = $80),
 * 1 `$40EB` (un cop per fotograma), 2 `$4016` (A = cançó < ($52A4) = 43; escriu a $C4C0), 3 `$4022` (A = efecte, 111 amb la taula de prioritats a $5235; escriu a $C4C2). Les cançons són als bancs $7E, $7D i $7C
 * (taula de banc a $7E:52A5, de punters a $52D7 dins del banc de la cançó). DEDUÏT: que $4016/$4022 són cançó/efecte (per com escriuen $C4C0/$C4C2, que $4119 consumeix). **Executat amb la ROM assemblada.**
 */
object ExpMarioGolf : ExpGbGDriver(
    "MarioGolf", 0..153, golfNoms(), true, 59.7275,
    { r -> r.size == 0x200000 && gTitol(r, "MARIO GOLF") && gHex(r, 0x1F8000, "7240EB4016402240") },
    { cpu, m -> m.banc = 0x7E; m.wb(0xFF95, 0x7E); m.wb(0xFFBF, 0x7E); cpu.crida(0x4072) },
    { cpu, m, id ->
        m.banc = 0x7E; m.wb(0xFF95, 0x7E); m.wb(0xFFBF, 0x7E)
        cpu.a = if (id < 43) id else id - 43
        cpu.crida(if (id < 43) 0x4016 else 0x4022)
    },
    { cpu, m, _ -> m.banc = 0x7E; m.wb(0xFF95, 0x7E); m.wb(0xFFBF, 0x7E); cpu.crida(0x40EB) },
)

private val HMGB_NOMS: List<String> = listOf(
    "Harvest Moon GB · entrada 0 · $1E:4001 (canal c2, dades $4169)",
    "Harvest Moon GB · entrada 1 · $1E:4005 (canal c3, dades $4218)",
    "Harvest Moon GB · entrada 2 · $1E:4009 (canal c4, dades $432F)",
    "Harvest Moon GB · entrada 3 · $1E:400D (canal c5, dades $4368)",
    "Harvest Moon GB · entrada 4 · $1E:4011 (canal c2, dades $43D7)",
    "Harvest Moon GB · entrada 5 · $1E:4015 (canal c3, dades $443C)",
    "Harvest Moon GB · entrada 6 · $1E:4019 (canal c4, dades $448B)",
    "Harvest Moon GB · entrada 7 · $1E:401D (canal c5, dades $44E2)",
    "Harvest Moon GB · entrada 8 · $1E:4021 (canal c2, dades $4557)",
    "Harvest Moon GB · entrada 9 · $1E:4025 (canal c3, dades $4668)",
    "Harvest Moon GB · entrada 10 · $1E:4029 (canal c4, dades $473F)",
    "Harvest Moon GB · entrada 11 · $1E:402D (canal c2, dades $47F6)",
    "Harvest Moon GB · entrada 12 · $1E:4031 (canal c3, dades $48E1)",
    "Harvest Moon GB · entrada 13 · $1E:4035 (canal c4, dades $498E)",
    "Harvest Moon GB · entrada 14 · $1E:4039 (canal c5, dades $49BF)",
    "Harvest Moon GB · entrada 15 · $1E:403D (canal c2, dades $4A40)",
    "Harvest Moon GB · entrada 16 · $1E:4041 (canal c3, dades $4AF5)",
    "Harvest Moon GB · entrada 17 · $1E:4045 (canal c4, dades $4B86)",
    "Harvest Moon GB · entrada 18 · $1E:4049 (canal c5, dades $4C1F)",
    "Harvest Moon GB · entrada 19 · $1E:404D (canal c2, dades $4CFA)",
    "Harvest Moon GB · entrada 20 · $1E:4051 (canal c3, dades $4DDB)",
    "Harvest Moon GB · entrada 21 · $1E:4055 (canal c4, dades $4F06)",
    "Harvest Moon GB · entrada 22 · $1E:4059 (canal c5, dades $4F83)",
    "Harvest Moon GB · entrada 23 · $1E:405D (canal c2, dades $4F88)",
    "Harvest Moon GB · entrada 24 · $1E:4061 (canal c3, dades $5085)",
    "Harvest Moon GB · entrada 25 · $1E:4065 (canal c4, dades $51EA)",
    "Harvest Moon GB · entrada 26 · $1E:4069 (canal c5, dades $52AB)",
    "Harvest Moon GB · entrada 27 · $1E:406D (canal c2, dades $52FE)",
    "Harvest Moon GB · entrada 28 · $1E:4071 (canal c3, dades $53B1)",
    "Harvest Moon GB · entrada 29 · $1E:4075 (canal c4, dades $5414)",
    "Harvest Moon GB · entrada 30 · $1E:4079 (canal c2, dades $5489)",
    "Harvest Moon GB · entrada 31 · $1E:407D (canal c3, dades $5568)",
    "Harvest Moon GB · entrada 32 · $1E:4081 (canal c4, dades $562F)",
    "Harvest Moon GB · entrada 33 · $1E:4085 (canal c5, dades $5688)",
    "Harvest Moon GB · entrada 34 · $1E:4089 (canal c2, dades $56CB)",
    "Harvest Moon GB · entrada 35 · $1E:408D (canal c3, dades $57A2)",
    "Harvest Moon GB · entrada 36 · $1E:4091 (canal c4, dades $583B)",
    "Harvest Moon GB · entrada 37 · $1E:4095 (canal c5, dades $58EE)",
    "Harvest Moon GB · entrada 38 · $1E:4099 (canal c2, dades $597F)",
    "Harvest Moon GB · entrada 39 · $1E:409D (canal c3, dades $5A24)",
    "Harvest Moon GB · entrada 40 · $1E:40A1 (canal c4, dades $5AE9)",
    "Harvest Moon GB · entrada 41 · $1E:40A5 (canal c5, dades $5B18)",
    "Harvest Moon GB · entrada 42 · $1E:40A9 (canal c2, dades $5B67)",
    "Harvest Moon GB · entrada 43 · $1E:40AD (canal c3, dades $5C28)",
    "Harvest Moon GB · entrada 44 · $1E:40B1 (canal c4, dades $5CF7)",
    "Harvest Moon GB · entrada 45 · $1E:40B5 (canal c5, dades $5D52)",
    "Harvest Moon GB · entrada 46 · $1E:40B9 (canal c0, dades $5E7D)",
    "Harvest Moon GB · entrada 47 · $1E:40BD (canal c0, dades $5E90)",
    "Harvest Moon GB · entrada 48 · $1E:40C1 (canal c0, dades $5EBB)",
    "Harvest Moon GB · entrada 49 · $1E:40C5 (canal c0, dades $5ECC)",
    "Harvest Moon GB · entrada 50 · $1E:40C9 (canal c0, dades $5EFB)",
    "Harvest Moon GB · entrada 51 · $1E:40CD (canal c0, dades $5F22)",
    "Harvest Moon GB · entrada 52 · $1E:40D1 (canal c0, dades $5F33)",
    "Harvest Moon GB · entrada 53 · $1E:40D5 (canal c0, dades $5F44)",
    "Harvest Moon GB · entrada 54 · $1E:40D9 (canal c0, dades $5F55)",
    "Harvest Moon GB · entrada 55 · $1E:40DD (canal c0, dades $5F64)",
    "Harvest Moon GB · entrada 56 · $1E:40E1 (canal c0, dades $5F75)",
    "Harvest Moon GB · entrada 57 · $1E:40E5 (canal c1, dades $5F88)",
    "Harvest Moon GB · entrada 58 · $1E:40E9 (canal c0, dades $5F95)",
    "Harvest Moon GB · entrada 59 · $1E:40ED (canal c0, dades $5FA6)",
    "Harvest Moon GB · entrada 60 · $1E:40F1 (canal c0, dades $5FC7)",
    "Harvest Moon GB · entrada 61 · $1E:40F5 (canal c0, dades $6000)",
    "Harvest Moon GB · entrada 62 · $1E:40F9 (canal c0, dades $600D)",
    "Harvest Moon GB · entrada 63 · $1E:40FD (canal c0, dades $6018)",
    "Harvest Moon GB · entrada 64 · $1E:4101 (canal c2, dades $602D)",
    "Harvest Moon GB · entrada 65 · $1E:4105 (canal c2, dades $6038)",
    "Harvest Moon GB · entrada 66 · $1E:4109 (canal c0, dades $604D)",
    "Harvest Moon GB · entrada 67 · $1E:410D (canal c1, dades $605E)",
    "Harvest Moon GB · entrada 68 · $1E:4111 (canal c0, dades $606F)",
    "Harvest Moon GB · entrada 69 · $1E:4115 (canal c1, dades $608A)",
    "Harvest Moon GB · entrada 70 · $1E:4119 (canal c0, dades $60A5)",
    "Harvest Moon GB · entrada 71 · $1E:411D (canal c1, dades $60B0)",
    "Harvest Moon GB · entrada 72 · $1E:4121 (canal c0, dades $60BB)",
    "Harvest Moon GB · entrada 73 · $1E:4125 (canal c2, dades $60C8)",
    "Harvest Moon GB · entrada 74 · $1E:4129 (canal c3, dades $60CD)",
    "Harvest Moon GB · entrada 75 · $1E:412D (canal c4, dades $60D2)",
    "Harvest Moon GB · entrada 76 · $1E:4131 (canal c5, dades $60D7)",
    "Harvest Moon GB · entrada 77 · $1E:4135 (canal c2, dades $60DC)",
    "Harvest Moon GB · entrada 78 · $1E:4139 (canal c3, dades $60DC)",
    "Harvest Moon GB · entrada 79 · $1E:413D (canal c4, dades $60DC)",
    "Harvest Moon GB · entrada 80 · $1E:4141 (canal c5, dades $60DC)",
    "Harvest Moon GB · entrada 81 · $1E:4145 (canal c0, dades $60E1)",
    "Harvest Moon GB · entrada 82 · $1E:4149 (canal c1, dades $60F2)",
    "Harvest Moon GB · entrada 83 · $1E:414D (canal c0, dades $60FD)",
    "Harvest Moon GB · entrada 84 · $1E:4151 (canal c2, dades $610C)",
    "Harvest Moon GB · entrada 85 · $1E:4155 (canal c3, dades $6283)",
    "Harvest Moon GB · entrada 86 · $1E:4159 (canal c4, dades $635A)",
    "Harvest Moon GB · entrada 87 · $1E:415D (canal c5, dades $63BB)",
    "Harvest Moon GB · entrada 88 · $1E:4161 (canal c0, dades $64DA)",
    "Harvest Moon GB · entrada 89 · $1E:4165 (canal c0, dades $64E9)"
)

/**
 * Harvest Moon GB (Bokujou Monogatari GB, MBC3 de 512 KB, «BOKUJOU GB»). De H0smax/HMGB-Disassembly (mgbdis amb HMGB.sym; assemblat amb rgbasm 0.9 surt amb l'MD5 del repo): el motor és a la ROM0 i les dades al banc $1E.
 * `MusicRelated_TBD` $2331 (HMGB.sym) l'inicia; la interrupció de vblank crida `$2444` (processa les 6 estructures de canal de $D300, de $19 bytes) un cop per fotograma. Una pista es posa en marxa
 * amb `$23D8` (A = entrada de la taula de pistes del banc $1E a $4001, 4 bytes cada una [desplaçament de canal, indicador, punter], 90 entrades; torna amb A+1; `$23D5`, `$23D2` i `$23CF` són la mateixa
 * funció cridada 1, 2 o 3 cops seguits, nom `SpawnDialogOrEvent`/`DoSomethingWithSeasonOrWeather_TBD` a HMGB.sym). Una cançó són entrades consecutives amb canal creixent ($32, $4B, $64, $7D); la pista `n`
 * engega l'entrada `n` i les següents del mateix grup. DEDUÏT: la partició en grups i que les entrades de canal 0/$19 són efectes. **Executat amb la ROM assemblada.**
 */
object ExpHarvestMoonGB : ExpGbGDriver(
    "HarvestMoonGB", 0..89, HMGB_NOMS, false, 59.7275,
    { r -> r.size == 0x80000 && gTitol(r, "BOKUJOU GB") && gHex(r, 0x1E * 0x4000, "1E32006941") },
    { cpu, m -> m.banc = 1; cpu.crida(0x2331) },
    { cpu, m, id ->
        val b = 0x1E * 0x4000 + 1
        fun c(e: Int) = m.rom[b + 4 * e].toInt() and 255
        var e = id
        var prev = -1
        while (e < 90 && c(e) > prev) {
            m.banc = 1; cpu.a = e; cpu.crida(0x23D5)
            prev = c(e); e++
        }
    },
    { cpu, m, _ -> m.banc = 1; cpu.crida(0x2444) },
)

/**
 * Wario Land 3 (GBC, MBC5 de 2 MB, «WARIOLAND3»). De froggestspirit/wland3 (assemblat amb rgbasm 0.9 amb els INCBIN de gràfics a zero: el codi i les dades de so són els reals). El motor és al banc $0C ($4000 =
 * `Logged_0x30000`, init; `$407A`, actualització; `$41B8`, engega un so) i la RAM del so és al banc de WRAM 3 ($D000). La ROM0 els crida amb tres envoltoris que fan SVBK = 3: `$0FAE` (init),
 * `$0FBC` (un cop per fotograma) i `$0FCA` (BC = número de so de 16 bits, 1 a $238; la música i els efectes comparteixen taula). La taula `SoundPointers` ($0C:5566, 8 bytes per entrada: punter, banc,
 * 0, prioritat...) té 568 entrades; el desassemblat no en dona noms. Totes les entrades són pistes. **No verificable del tot**: de la ROM assemblada només s'ha comprovat que el reconeixement, l'init i l'engegada del so deixen l'estructura de canal ben carregada (RAM $D0F4); parts del motor són `INCBIN` de la baserom (`Unknown_0x30152`...), així que no sona sense la ROM real.
 */
object ExpWarioLand3 : ExpGbGDriver(
    "WarioLand3", 1..568, (1..568).map { "Wario Land 3 · so $it · taula \$0C:" + gH(0x5566 + 8 * (it - 1)) }, true, 59.7275,
    { r -> r.size == 0x200000 && gTitol(r, "WARIOLAND3") && gHex(r, 0x0FAE, "F070F53E03E070CD003F") && gHex(r, 0x0FCA, "F070F53E03E070CD0C3F") },
    { cpu, m -> m.banc = 1; m.wb(0xC5FF, 1); cpu.crida(0x0FAE) },
    { cpu, m, id -> m.banc = 1; m.wb(0xC5FF, 1); cpu.b = id shr 8; cpu.c = id and 255; cpu.crida(0x0FCA) },
    { cpu, _, _ -> cpu.crida(0x0FBC) },
)

val EXP_GB_G2: List<GbDriver> = listOf(ExpMarioGolf, ExpHarvestMoonGB, ExpWarioLand3)
val EXP_COMPAT_G2: List<JocCompat> = listOf(JocCompat("Mario Golf", "Game Boy Color", "GB_MarioGolf"), JocCompat("Harvest Moon GB", "Game Boy", "GB_HarvestMoonGB"), JocCompat("Wario Land 3", "Game Boy Color", "GB_WarioLand3"))
