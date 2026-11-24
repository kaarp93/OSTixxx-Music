package cat.gbdsmixxx

/**
 * Estat inicial de RAM per als drivers fets a mà (proposta 2026-11-16, prototip). Els drivers criden l'init del so amb la WRAM a zero, però el joc real hi arriba amb l'estat que
 * deixa la seva arrencada (cas Kirby: taula de $DC00 generada per $21BB + $4B30; arreglat dins `DriverKirby.inicia`). Mecanisme genèric, sense tocar cada driver:
 * `GbDriver.preparaRam` (per defecte aplica `PER_DRIVER[clau d'àlbum]`) es crida abans d'`inicia` a la reproducció i a l'anàlisi.
 * Les escriptures tenen el format de `base=` dels AUTO_POKE: índex de la WRAM (0-$7FFF), $10000 + índex de la HRAM, $20000 + índex de la RAM del cartutx; parell adreça, valor.
 * **Buit a propòsit**: cap driver provat per l'autor (Adventure Island II, Action Man, Kirby...) no canvia. Per afegir-ne un: trobar l'estat mínim amb `eines/valida_driver`
 * (ValidaDriver accepta els pokes com a argument) fins que la traça sigui igual a la de PyBoy, i llavors posar-lo aquí. Pendent per als altres drivers fets a mà.
 */
object EstatInicialGb {
    val PER_DRIVER: Map<String, IntArray> = emptyMap()

    /** «W1D1E=01/H7F=00/X0000=FF» (o separat per espais) -> parells adreça, valor. */
    fun llegeix(txt: String): IntArray {
        val r = ArrayList<Int>()
        for (e in txt.split('/', ' ', ',')) {
            if (e.isEmpty()) continue
            val ad = e.substringBefore('='); val v = e.substringAfter('=').toInt(16)
            r.add(when (ad[0]) { 'X' -> 0x20000 + ad.substring(1).toInt(16); 'H' -> 0x10000 + ad.substring(1).toInt(16); 'W' -> ad.substring(1).toInt(16); else -> throw IllegalArgumentException(e) }); r.add(v)
        }
        return r.toIntArray()
    }
    fun aplica(m: GbMem, pokes: IntArray) {
        var i = 0
        while (i + 1 < pokes.size) {
            val a = pokes[i]; val v = pokes[i + 1].toByte()
            if (a >= 0x20000) m.xram[a - 0x20000] = v else if (a >= 0x10000) m.hram[a - 0x10000] = v else m.wram[a] = v
            i += 2
        }
    }
}
