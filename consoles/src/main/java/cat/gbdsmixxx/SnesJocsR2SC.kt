package cat.gbdsmixxx

/*
 * Lot R2SC de jocs de SNES amb driver (ronda 2): Kirby's Dream Land 3. No comprovat amb cap ROM: escrit des del desassemblatge.
 */

private val kdBuf = ThreadLocal.withInitial { ShortArray(32) }

/**
 * Kirby's Dream Land 3 (SNES, SA-1, 2 MB; Yoshifanatic1/Kirbys-Dream-Land-3-Disassembly, `Routine_Macros_KDL3.asm`). Els bancs $00-$3F són LoROM i els $C0-$FF són lineals de 64 KB
 * (SA-1: $C0 = bloc 0, $D0 = bloc 1). Càrrega en dues fases, emulada aquí en pas coordinat amb l'SPC700: (1) `CODE_00DD61`: pujada IPL del carregador (`DATA_D03EFC`) i, amb el carregador, els
 * fluxos `DATA_CE0000` (motor i mostres globals) i `DATA_CEA0EF` (`CODE_00DD21`: paraula de mida, paraula de destí, bytes d'un en un pel port 2 amb el comptador al port 0; mida 0 = final;
 * `CODE_00DE16` tanca); (2) per a cada cançó `n` (1-43, `CODE_00D819`): ordre $75, flux `DATA_00DEAF[n]`, banc de mostres local `DATA_D0CA2C[n+1] & $7F` (`DATA_00DF69`) i ordre $76 amb port 2 = $FF.
 * Els noms de cançó no són al desassemblatge.
 */
object DriverKirbyDL3 : SnesDriver {
    override val nom = "KirbyDL3"
    override val cancons: List<String> = List(43) { "So ${it + 1}" }
    private fun ofs(banc: Int, adr: Int): Int =
        if (banc >= 0xC0) (banc - 0xC0) * 0x10000 + (adr and 0xFFFF) else if (banc < 0x40) banc * 0x8000 + ((adr and 0xFFFF) - 0x8000) else -1
    private fun b(rom: RomSnes, banc: Int, adr: Int): Int { val o = ofs(banc, adr); return if (o in rom.d.indices) rom.d[o].toInt() and 255 else 0 }
    override fun reconeix(rom: RomSnes): Boolean =
        !rom.hiRom && (rom.d.size == 0x200000 || rom.d.size == 0x400000) && rom.titol.uppercase().startsWith("KIRBY'S DREAM LAND 3") &&
            b(rom, 0, 0xDEB2) == 0xAC && b(rom, 0, 0xDEB3) == 0xC4 && b(rom, 0, 0xDEB4) == 0xD0

    private var pressupost = 0
    /** Deixa córrer l'SPC700 fins que el port `p` (el que la CPU llegeix) val `v` (o `!= v` si `diferent`). */
    private fun espera(spc: SnesSpc, p: Int, v: Int, diferent: Boolean = false) {
        val buf = kdBuf.get()
        while (pressupost > 0) {
            if ((spc.llegeixPort(p) == v) != diferent) return
            spc.executa(buf, 16); pressupost--
        }
    }

    override fun inicia(spc: SnesSpc, rom: RomSnes, id: Int) {
        pressupost = 3_000_000
        fun r(p: Int, o: Int) = b(rom, (p shr 16) and 255, (p and 0xFFFF) + o)
        fun r16(p: Int, o: Int) = r(p, o) or (r(p, o + 1) shl 8)
        fun r24(p: Int, o: Int) = r16(p, o) or (r(p, o + 2) shl 16)
        var x = 0xFE                                                 // comptador de transaccions ($12)
        // CODE_00DE47: pujada IPL del carregador des de $D0:3EFC (blocs [mida][destí][dades], l'últim de mida 0 porta l'entrada)
        run {
            var p = 0xD03EFC; var entrada = 0; var blocs = 0
            while (blocs < 64) {
                val n = r16(p, 0); val a = r16(p, 2); p = (p and 0xFF0000) or ((p + 4) and 0xFFFF)
                if (n == 0) { entrada = a; break }
                for (k in 0 until n) spc.ram[(a + k) and 0xFFFF] = r(p, k).toByte()
                p = (p and 0xFF0000) or ((p + n) and 0xFFFF); blocs++
            }
            spc.arrenca(entrada)
            S3.corre(spc, 6000)                                      // el motor neteja els ports d'entrada en arrencar (`$F1 = $F0`): la sincronització s'envia després
        }
        // CODE_00DDDB: paraula de sincronització
        fun sincro() {
            if (spc.llegeixPort(0) != 0) {
                spc.escriuPort(1, 0); spc.escriuPort(2, 0)
                val buf = kdBuf.get()
                while (pressupost > 0 && (spc.llegeixPort(1) or spc.llegeixPort(2)) != 0) { spc.executa(buf, 16); pressupost-- }
            }
            spc.escriuPort(2, 0); spc.escriuPort(3, 0x78); spc.escriuPort(0, 0xFF); spc.escriuPort(1, 0xFE)
            espera(spc, 0, 0xFE)
            x = 0xFE
        }
        // CODE_00DD52 / DD3B: una transacció (el comptador x al port 0, la paraula als ports 2-3)
        fun tx(w: Int) {
            espera(spc, 0, x)
            spc.escriuPort(2, w and 255); spc.escriuPort(3, (w shr 8) and 255); spc.escriuPort(0, x)
            x = (x + 1) and 255
        }
        // CODE_00DD21: flux de blocs [mida16][destí16][bytes]; mida 0 = final
        fun flux(ptr: Int) {
            var o = 0
            val banc = ptr and 0xFF0000
            fun w(k: Int) = r16(banc or ((ptr + k) and 0xFFFF), 0)
            var guard = 0
            while (guard++ < 512) {
                val n = w(o); if (n == 0) break
                tx(n); o += 2
                tx(w(o)); o += 2
                for (k in 0 until n) { tx(w(o)); o += 1 }
            }
        }
        // CODE_00DE16: tancament
        fun tanca() {
            espera(spc, 0, x); spc.escriuPort(2, 0); spc.escriuPort(3, 0); spc.escriuPort(0, x)
            if (x == 0xFE) { x = 0xFF; espera(spc, 0, x); spc.escriuPort(0, x) }
            x = 0xFE
            espera(spc, 0, x); spc.escriuPort(0, x)
            espera(spc, 0, x, diferent = true)
        }
        fun ordre(v: Int) { spc.escriuPort(0, v); espera(spc, 0, v) }
        // inici (CODE_00DD61)
        sincro(); flux(0xCE0000); flux(0xCEA0EF); tanca()
        ordre(0x75); spc.escriuPort(2, 0); spc.escriuPort(1, 0); espera(spc, 1, 0)
        // cançó (CODE_00D819)
        val n = id.coerceIn(1, 43)
        ordre(0x75)
        sincro(); flux(r24(0x00DEAF, 3 * n)); tanca()
        val banc = r(0xD0CA2C, n + 1) and 0x7F
        if (banc != 0) { sincro(); flux(r24(0x00DF69, 3 * banc)); tanca() }
        spc.escriuPort(2, 0xFF); spc.escriuPort(3, 0)
        ordre(0x76)
    }
}

val DRIVERS_SNES_R2SC: List<SnesDriver> = listOf(DriverKirbyDL3)
