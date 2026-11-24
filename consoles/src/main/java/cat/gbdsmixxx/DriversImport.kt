package cat.gbdsmixxx

/**
 * Importació de drivers (lògica pura, sense Android; la fa servir `Drivers.importa` de Music). Entra el text d'un fitxer de drivers o d'un report sencer de l'analitzador
 * (només compten les capçaleres `== fitxer ==` i les línies `AUTO_*`) i surt el paquet binari d'importats nou, o res si no canvia res.
 * - nou: cap driver amb aquest (CRC, format) ni a l'app ni als importats;
 * - actualitzat: ja n'hi havia un d'importat i el contingut és diferent (rev nova: la marca d'abans no s'hi aplica) o només canvien els noms (mateixa rev: la marca es manté);
 * - igual: idèntic al de l'app o al ja importat (no s'escriu res).
 * Un driver diferent del de l'app no el substitueix: queden tots dos (versions «Auto 1» i «Auto 2»).
 */
object DriversImport {
    class Resultat(val paquet: ByteArray?, val nous: Int, val actualitzats: Int, val iguals: Int, val errors: List<String>, val crcsCanviats: Set<Long>) {
        val llegits: Int get() = nous + actualitzats + iguals
    }

    fun importa(app: DriversBin.Pack?, importats: DriversBin.Pack?, textos: List<String>): Resultat {
        val errors = ArrayList<String>()
        val defs = LinkedHashMap<Triple<Long, Int, TipusDriver>, DefDriver>()
        for (t in textos) for (d in DriversDef.parseText(t, errors, TipusDriver.AUTO)) { val k = Triple(d.crc, d.format, d.origen); defs.remove(k); defs[k] = d }
        var nous = 0; var act = 0; var iguals = 0
        val canviats = ArrayList<DefDriver>(); val crcs = HashSet<Long>()
        for (d in defs.values) {
            val rev = DriversBin.rev(d); val chk = DriversBin.chk(d)
            fun igual(p: DriversBin.Pack?) = p?.perCrc(d.crc)?.any { it.format == d.format && it.origen == d.origen && it.rev == rev && it.chk == chk && it.titol == d.titol && it.cgb == d.cgb } == true
            if (igual(app) || igual(importats)) { iguals++; continue }
            if (importats?.perCrc(d.crc)?.any { it.format == d.format && it.origen == d.origen } == true) act++ else nous++
            canviats.add(d); crcs.add(d.crc)
        }
        val paquet = if (canviats.isEmpty()) null else DriversBin.fusiona(importats, canviats)
        return Resultat(paquet, nous, act, iguals, errors, crcs)
    }
}
