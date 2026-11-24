package cat.ostixxx.music

// Kotlin pur: camins SVG (viewBox 24x24) de les icones de Material Symbols (Apache 2.0), sense dependència nova. `Icones.kt` en fa ImageVector.
// Els camins es validen a JVM (eines/prototips/ui/prova.sh) perquè un camí mal escrit no faci petar l'app.

object CamiIcones {
    const val PLAY = "M8,5v14l11,-7z"
    /** Mode concert (pantalla completa). */
    const val CONCERT = "M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z"
    /** Mode comparació (A/B). */
    const val COMPARA = "M9.01,14H2v2h7.01v3L13,15l-3.99,-4v3zM14.99,13v-3H22V8h-7.01V5L11,9l3.99,4z"
    const val PAUSA = "M6,19h4V5H6v14zM14,5v14h4V5h-4z"
    const val SEGUENT = "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z"
    const val ANTERIOR = "M6,6h2v12H6zM9.5,12l8.5,6V6z"
    const val ATURA = "M6,6h12v12H6z"
    const val ALEATORI = "M10.59,9.17L5.41,4L4,5.41l5.17,5.17l1.42,-1.41zM14.5,4l2.04,2.04L4,18.59L5.41,20L17.96,7.46L20,9.5V4h-5.5zM14.83,13.41l-1.41,1.41l3.13,3.13L14.5,20H20v-5.5l-2.04,2.04l-3.13,-3.13z"
    const val AVALL = "M16.59,8.59L12,13.17L7.41,8.59L6,10l6,6l6,-6z"
    const val ENRERE = "M20,11H7.83l5.59,-5.59L12,4l-8,8l8,8l1.41,-1.41L7.83,13H20v-2z"
    const val MES = "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2s-2,0.9 -2,2s0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2s2,-0.9 2,-2s-0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2s2,-0.9 2,-2s-0.9,-2 -2,-2z"
    const val TANCA = "M19,6.41L17.59,5L12,10.59L6.41,5L5,6.41L10.59,12L5,17.59L6.41,19L12,13.41L17.59,19L19,17.59L13.41,12z"
    const val AFEGEIX = "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"
    const val FET = "M9,16.17L4.83,12l-1.42,1.41L9,19L21,7l-1.41,-1.41z"
    const val CARPETA = "M10,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z"
    const val FITXER = "M6,2c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6H6zM13,9V3.5L18.5,9H13z"
    const val AMUNT = "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8l-8,8z"
    const val BAIX = "M20,12l-1.41,-1.41L13,16.17V4h-2v12.17l-5.58,-5.59L4,12l8,8l8,-8z"
    const val BUCLE = "M7,7h10v3l4,-4l-4,-4v3H5v6h2V7zM17,17H7v-3l-4,4l4,4v-3h12v-6h-2v4z"
    const val EQ = "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z"
    const val CANALS = "M10,20h4V4h-4v16zM4,20h4v-8H4v8zM16,9v11h4V9h-4z"
    const val VISUAL = "M7,18h2V6H7v12zM11,22h2V2h-2v20zM3,14h2v-4H3v4zM15,18h2V6h-2v12zM19,10v4h2v-4h-2z"
    const val INICI = "M10,20v-6h4v6h5v-8h3L12,3L2,12h3v8z"
    const val CERCA = "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5C16,5.91 13.09,3 9.5,3S3,5.91 3,9.5S5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5S14,7.01 14,9.5S11.99,14 9.5,14z"
    const val BIBLIOTECA = "M20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM18,7h-3v5.5c0,1.38 -1.12,2.5 -2.5,2.5S10,13.88 10,12.5s1.12,-2.5 2.5,-2.5c0.57,0 1.08,0.19 1.5,0.51V5h4v2zM4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6z"
    const val AJUSTOS = "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"
    const val LLISTA_AFEGEIX = "M14,10H2v2h12v-2zM14,6H2v2h12V6zM18,14v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zM2,16h8v-2H2v2z"
    const val ESBORRA = "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"
    const val EDITA = "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83l3.75,3.75l1.83,-1.83z"
    const val AMAGA = "M12,7c2.76,0 5,2.24 5,5c0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75c-1.73,-4.39 -6,-7.5 -11,-7.5c-1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7zM2,4.27l2.28,2.28l0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5c1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22L21,20.73L3.27,3L2,4.27zM7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65c0,1.66 1.34,3 3,3c0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53c-2.76,0 -5,-2.24 -5,-5c0,-0.79 0.2,-1.53 0.53,-2.2z"
    const val NOTA = "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55c-2.21,0 -4,1.79 -4,4s1.79,4 4,4s4,-1.79 4,-4V7h4V3h-6z"
    const val COPIA = "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z"
    const val SUGGERIMENT = "M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9zM11.5,9.5L9,4L6.5,9.5L1,12l5.5,2.5L9,20l2.5,-5.5L17,12l-5.5,-2.5zM19,15l-1.25,2.75L15,19l2.75,1.25L19,23l1.25,-2.75L23,19l-2.75,-1.25L19,15z"
    const val ORDENA = "M3,18h6v-2H3v2zM3,6v2h18V6H3zM3,13h12v-2H3v2z"
    const val REVISA = "M19,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM10,17l-5,-5l1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z"
    const val COMPARTEIX = "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81c1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3s-3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65c0,1.61 1.31,2.92 2.92,2.92s2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z"
    const val TEMA = "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c0.83,0 1.5,-0.67 1.5,-1.5c0,-0.39 -0.15,-0.74 -0.39,-1.01c-0.23,-0.26 -0.38,-0.61 -0.38,-0.99c0,-0.83 0.67,-1.5 1.5,-1.5H16c2.76,0 5,-2.24 5,-5c0,-4.42 -4.03,-8 -9,-8zM6.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,9 6.5,9S8,9.67 8,10.5S7.33,12 6.5,12zM9.5,8C8.67,8 8,7.33 8,6.5S8.67,5 9.5,5S11,5.67 11,6.5S10.33,8 9.5,8zM14.5,8c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.67,5 14.5,5S16,5.67 16,6.5S15.33,8 14.5,8zM17.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S16.67,9 17.5,9S19,9.67 19,10.5S18.33,12 17.5,12z"
    const val INFO = "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10s10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z"
    const val DESCARREGA = "M19,9h-4V3H9v6H5l7,7l7,-7zM5,18v2h14v-2H5z"
    const val ACTUALITZA = "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4c-3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"

    /** Totes, per validar-les. */
    val TOTES: List<Pair<String, String>> get() = listOf(
        "PLAY" to PLAY, "CONCERT" to CONCERT, "COMPARA" to COMPARA, "PAUSA" to PAUSA, "SEGUENT" to SEGUENT, "ANTERIOR" to ANTERIOR, "ATURA" to ATURA, "ALEATORI" to ALEATORI, "AVALL" to AVALL,
        "ENRERE" to ENRERE, "MES" to MES, "TANCA" to TANCA, "AFEGEIX" to AFEGEIX, "FET" to FET, "CARPETA" to CARPETA, "FITXER" to FITXER, "AMUNT" to AMUNT,
        "BAIX" to BAIX, "BUCLE" to BUCLE, "EQ" to EQ, "CANALS" to CANALS, "VISUAL" to VISUAL, "INICI" to INICI, "CERCA" to CERCA, "BIBLIOTECA" to BIBLIOTECA,
        "AJUSTOS" to AJUSTOS, "LLISTA_AFEGEIX" to LLISTA_AFEGEIX, "ESBORRA" to ESBORRA, "EDITA" to EDITA, "AMAGA" to AMAGA, "NOTA" to NOTA, "COPIA" to COPIA,
        "SUGGERIMENT" to SUGGERIMENT, "ORDENA" to ORDENA, "REVISA" to REVISA, "COMPARTEIX" to COMPARTEIX, "TEMA" to TEMA, "INFO" to INFO,
        "DESCARREGA" to DESCARREGA, "ACTUALITZA" to ACTUALITZA,
    )

    private val ARGS = mapOf('M' to 2, 'L' to 2, 'H' to 1, 'V' to 1, 'C' to 6, 'S' to 4, 'Q' to 4, 'T' to 2, 'A' to 7, 'Z' to 0)
    /** Comprova un camí: només ordres conegudes i, per a cada una, un nombre d'arguments múltiple del que toca. Torna null si és correcte, o el motiu. */
    fun valida(d: String): String? {
        val toks = Regex("[A-Za-z]|-?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?").findAll(d).map { it.value }.toList()
        val net = d.replace(Regex("[A-Za-z]|-?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?|[\\s,]"), "")
        if (net.isNotEmpty()) return "caràcters estranys: $net"
        if (toks.isEmpty() || toks[0].uppercase() != "M") return "no comença amb M"
        var i = 0
        while (i < toks.size) {
            val c = toks[i]; if (c.length != 1 || !c[0].isLetter()) return "número sense ordre a la posició $i"
            val n = ARGS[c[0].uppercaseChar()] ?: return "ordre desconeguda $c"
            var j = i + 1; while (j < toks.size && !(toks[j].length == 1 && toks[j][0].isLetter())) j++
            val k = j - i - 1
            if (n == 0 && k != 0) return "$c amb arguments"
            if (n > 0 && (k == 0 || k % n != 0)) return "$c amb $k arguments"
            i = j
        }
        return null
    }
}
