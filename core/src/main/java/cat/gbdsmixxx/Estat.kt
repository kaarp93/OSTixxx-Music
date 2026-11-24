package cat.gbdsmixxx

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Rellotge de la interfície: els decks i el motor canvien fora de Compose, així que les pantalles el llegeixen per tornar-se a dibuixar. */
object Tic { var v by mutableIntStateOf(0) }

/** Informe de l'última importació: (va bé?, línia) per a cada fitxer; les dues apps el mostren en acabar. */
object Informe {
    var linies by mutableStateOf<List<Pair<Boolean, String>>>(emptyList())
    private fun carpeta(ctx: Context) = ctx.getExternalFilesDir(null) ?: ctx.filesDir
    /** Log d'importació: els imports de ROM fallits (a la carpeta de fitxers de l'app; l'extractor d'Ajustos el comparteix). */
    fun fitxer(ctx: Context) = java.io.File(carpeta(ctx), "errors_import.txt")
    /** Log de proves: les marques ✓/✗ dels àlbums i els avisos de rendiment del motor ([lent], [salt]); és un fitxer apart del d'importació. */
    fun fitxerProves(ctx: Context) = java.io.File(carpeta(ctx), "proves_jocs.txt")
    /** Identificador de la build (versió, número de compilació i commit); el posa l'app a l'arrencada i surt a cada línia del log. */
    @Volatile var build = ""
    private fun formata(text: String): String {
        val d = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
        return "$d  " + (if (build.isEmpty()) "" else "[$build] ") + "$text\n"
    }
    /** Afegeix una línia al log d'importació (amb la data i la build). */
    fun linia(ctx: Context, text: String) { try { fitxer(ctx).appendText(formata(text)) } catch (e: Throwable) { } }
    /** Afegeix una línia al log de proves (amb la data i la build). */
    fun liniaProves(ctx: Context, text: String) { try { fitxerProves(ctx).appendText(formata(text)) } catch (e: Throwable) { } }
    /** Marca d'un joc al log de proves: treu les línies ✓/✗ anteriors d'aquest joc i, si `text` no és null, hi afegeix la nova. Treure la marca no deixa cap rastre. */
    @Synchronized fun marca(ctx: Context, titol: String, text: String?) {
        try {
            val f = fitxerProves(ctx)
            val resta = if (f.exists()) f.readLines().filter { l -> !(l.contains("] ✓ $titol - ") || l.contains("] ✗ $titol - ") || l.contains("  ✓ $titol - ") || l.contains("  ✗ $titol - ")) } else emptyList()
            f.writeText(resta.joinToString("") { it + "\n" })
            if (text != null) f.appendText(formata(text))
        } catch (e: Throwable) { }
    }
    /** Afegeix al log d'importació les línies fallides d'un import. */
    fun desaErrors(ctx: Context, l: List<Pair<Boolean, String>>) { for (x in l) if (!x.first) linia(ctx, x.second) }
    fun errors(ctx: Context): String = try { fitxer(ctx).takeIf { it.exists() }?.readText() ?: "" } catch (e: Throwable) { "" }
    fun proves(ctx: Context): String = try { fitxerProves(ctx).takeIf { it.exists() }?.readText() ?: "" } catch (e: Throwable) { "" }
    fun esborraErrors(ctx: Context) { try { fitxer(ctx).delete() } catch (e: Throwable) { } }
    fun esborraProves(ctx: Context) { try { fitxerProves(ctx).delete() } catch (e: Throwable) { } }
}

/**
 * Anàlisi total de les ROM de consoles (GB, NES, SNES): per a cada cançó, el pols, el bucle i la durada, que es desen a disc (`RitmeRom`) i després es fan servir en reproduir
 * (barra de posició i bucles disponibles de seguida). Es fa en un fil a part, de baixa prioritat, després d'importar i amb el botó d'Ajustos; progrés a `App.missatge`.
 */
object AnalisiTotal {
    @Volatile var enMarxa = false
    @Volatile private var progres = ""
    /** Cada `engega` o `atura` puja la generació: l'anàlisi que corria s'adona al pas següent que ja no és la seva i s'acaba (no corre mai durant una importació ni mentre es refà la biblioteca). */
    @Volatile private var gen = 0
    fun atura() { gen++; enMarxa = false }
    fun engega(b: Biblioteca, post: (Runnable) -> Unit) {
        val meva = ++gen
        // Només noms i números (no els àlbums): l'anàlisi dura molt i, si guardés els àlbums de la biblioteca d'ara, cap de les ROM no es podria alliberar mentre corre i cada import en sumaria una còpia més
        val albums = b.emissores.filter { !it.esLlista && (it.nom.startsWith("GB_") || it.nom.startsWith("NES_") || it.nom.startsWith("SNES_")) }.map { Triple(it.nom, it.ids, it.vista) }
        enMarxa = true
        Thread {
            // el filtre (marques i etiquetes de cada cançó) es fa en aquest fil: abans es feia al fil principal, cançó per cançó de tota la biblioteca
            val els = albums.map { (nom, ids, vista) -> Triple(nom, ids.filter { id -> try { FiltreCancons.analitza(nom, id) } catch (x: Throwable) { false } }, vista) }.filter { it.second.isNotEmpty() }
            val total = els.sumOf { it.second.size }
            if (total == 0) { if (gen == meva) enMarxa = false; return@Thread }
            App.traca?.invoke("anàlisi total: " + total + " cançons de " + els.size + " àlbums")
            // mostreig de diagnòstic: una línia cada 3 s amb el pas actual (la memòria la hi posa la traça); si l'app peta, l'última línia diu què feia
            Thread { while (enMarxa && gen == meva) { App.traca?.invoke("anàlisi " + progres); try { Thread.sleep(3000) } catch (x: Throwable) { break } } }.also { it.isDaemon = true; it.priority = Thread.MIN_PRIORITY }.start()
            var fet = 0
            FilsVius.total.incrementAndGet()
            try {
                for ((nom, ids, vista) in els) for (id in ids) {
                    if (gen != meva) return@Thread
                    progres = "" + fet + "/" + total + " · " + vista + " #" + id
                    post(Runnable { App.missatge = t("Analitzant") + " " + (fet + 1) + "/" + total + " · " + vista })
                    try {
                        val e = App.bib?.perNom(nom) ?: continue          // l'àlbum de la biblioteca actual (la que s'ha refet mentrestant)
                        val s = synchronized(RadioMotor.llocMuts) { val anterior = RadioMotor.muts; RadioMotor.muts = BooleanArray(16); try { e.crea(id) } finally { RadioMotor.muts = anterior } }
                        s.preparaMesclador()
                    } catch (x: Throwable) { }
                    fet++
                }
            } finally {
                FilsVius.total.decrementAndGet()
                if (gen == meva) { enMarxa = false; post(Runnable { App.missatge = t("Anàlisi acabada") + ": " + total }) }
            }
        }.also { it.isDaemon = true; it.priority = Thread.MIN_PRIORITY }.start()
    }
}

/** Estat global de l'app: la biblioteca de cançons (es carrega en un fil a part), els missatges i la pantalla triada. */
object App {
    /** Traça de diagnòstic (la posa Music): cada pas de la reconstrucció de la biblioteca, amb la memòria lliure. */
    @Volatile var traca: ((String) -> Unit)? = null

    lateinit var env: Env
    var bib by mutableStateOf<Biblioteca?>(null)
    var carregant by mutableStateOf(true)
    var missatge by mutableStateOf("")
    /** 0 = mesclador, 1 = reproductor, 2 = funsies, 3 = cançons (editor de canals). */
    var pestanya by mutableIntStateOf(0)
    /** Deck per al qual s'està triant cançó (-1 = cap) i si els ajustos són oberts. */
    var triant by mutableIntStateOf(-1)
    var ajustos by mutableStateOf(false)
    /** S'incrementa quan canvien noms, llistes o cançons ocultes, perquè les llistes es refacin. */
    var versio by mutableIntStateOf(0)
    private var restaurat = false
    /** Amaga les cançons que no fan bucle (efectes de so, fanfàrries): opció dels ajustos, activada per defecte. */
    var amagaSenseBucle by mutableStateOf(true)
    private val fil = Handler(Looper.getMainLooper())

    // Guarda de generació (l'autor, 2026-11-16): mai dues biblioteques construint-se alhora (cada una pot ocupar molta memòria). Si arriba una crida mentre una corre,
    // es recorda i, quan acaba la que corre, el seu resultat es descarta (ja és vell) i se'n fa una de nova amb l'estat d'ara. Tot això al fil principal.
    private var enCurs = false
    private var pendent = false
    private var pendentAnalitza = false
    private var generacio = 0

    /** (Re)crea la biblioteca (es fa en un fil a part). Ara surt de l'índex: no llegeix cap ROM, llevat de les que tenen drivers nous per avaluar. */
    fun carrega(ctx: Context, analitza: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) { fil.post { carrega(ctx, analitza) }; return }
        AnalisiTotal.atura()                       // l'anàlisi no corre mentre es refà la biblioteca (només torna a arrencar, si toca, quan és feta)
        carregant = true
        if (enCurs) { pendent = true; pendentAnalitza = pendentAnalitza || analitza; traca?.invoke("biblioteca: n'hi ha una en marxa, es farà després"); return }
        enCurs = true
        val meva = ++generacio
        val app = ctx.applicationContext
        Thread {
            traca?.invoke("biblioteca: inici")
            try { DadesUsuari.de(app) } catch (e: Throwable) { traca?.invoke("dades d'usuari: error " + e.javaClass.simpleName) }          // es carreguen (i es migren) fora del fil principal
            val b = try { Biblioteca(env).also { traca?.invoke("biblioteca: feta") } } catch (e: Throwable) { traca?.invoke("biblioteca: error " + e.javaClass.simpleName); fil.post { missatge = e.javaClass.simpleName + ": " + (e.message ?: "") }; null }
            fil.post {
                enCurs = false
                if (pendent) {                     // ha arribat una crida mentrestant: aquesta biblioteca ja és vella; es descarta i es fa la nova
                    val a = pendentAnalitza || analitza; pendent = false; pendentAnalitza = false
                    traca?.invoke("biblioteca: descartada, se'n fa una de més nova")
                    carrega(app, a)
                    return@post
                }
                if (meva != generacio) return@post
                bib = b; carregant = false; versio++
                if (b != null) {
                    val noms = HashMap(b.clausEstables)
                    // P1: les dades de la ràdio (llistes, noms, ocultes, BPM/bucle, combinacions) amb el nom d'un àlbum de consola passen a la clau estable (abans de restaurar els decks)
                    try { RadioDades.adopta(noms).let { if (it > 0) traca?.invoke("dades de la ràdio: $it claus passades a la clau estable") } } catch (e: Throwable) { traca?.invoke("migració de la ràdio: error " + e.javaClass.simpleName) }
                    Mix.reubica(b)                  // els decks deixen d'apuntar als àlbums de la biblioteca vella
                    Thread {
                        try { Portades.migraArt(app, noms) } catch (e: Throwable) { }
                        val du = try { DadesUsuari.de(app).also { it.adopta(noms) } } catch (e: Throwable) { null }
                        if (du != null) try { Consoles.proveidor?.despresBiblioteca(b, du) } catch (e: Throwable) { traca?.invoke("migració de marques: error " + e.javaClass.simpleName) }
                    }.also { it.isDaemon = true }.start()
                }
                if (b != null && !restaurat) { restaurat = true; Mix.restaura(b) }; if (b != null && analitza) AnalisiTotal.engega(b) { fil.post(it) }
            }
        }.also { it.isDaemon = true }.start()
    }

    /** Importa ROMs (GBA o DS) triades pel sistema; en acabar refà la biblioteca. */
    fun importa(ctx: Context, uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        AnalisiTotal.atura()                       // ni durant la importació
        missatge = t("Llegint la ROM...")
        traca?.invoke("import de ROM: " + uris.size + " fitxers")
        Thread {
            var bons = 0
            val informe = ArrayList<Pair<Boolean, String>>()
            for (u in uris) {
                try { ctx.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Throwable) { }
                val nom = RomsMusica.nomFitxer(ctx, u) ?: u.lastPathSegment ?: "?"
                RomsMusica.motiu = ""
                val res = try { RomsMusica.importa(ctx, u) } catch (e: Throwable) { RomsMusica.motiu = t("No s'ha pogut llegir la ROM") + " (" + e.javaClass.simpleName + (e.message?.let { m -> ": " + m.take(120) } ?: "") + " · " + (e.stackTrace.firstOrNull { it.className.startsWith("cat.gbdsmixxx") }?.let { it.fileName + ":" + it.lineNumber } ?: "?") + ")"; null }
                if (res != null) { bons++; informe.add(true to "$nom → $res") } else informe.add(false to "$nom: " + RomsMusica.motiu.ifEmpty { t("Format no reconegut") })
            }
            traca?.invoke("import de ROM fet: " + bons + "/" + uris.size)
            Informe.desaErrors(ctx, informe)
            fil.post { missatge = t("Importats:") + " $bons/${uris.size}"; Informe.linies = informe; carrega(ctx) }
        }.also { it.isDaemon = true }.start()
    }

    fun importaCarpeta(ctx: Context, arrel: android.net.Uri) {
        try { ctx.contentResolver.takePersistableUriPermission(arrel, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Throwable) { }
        AnalisiTotal.atura()
        missatge = t("Llegint la ROM...")
        // Bloc de càrrega: si la carpeta té ruta i hi ha «Accés a tots els fitxers», es desa com a carpeta de la biblioteca i les ROM de GB/NES/SNES es llegeixen d'on són
        val afegeix = ModeCarrega.afegeixCarpeta
        val dirRuta = if (afegeix != null && !ModeCarrega.antic && AccesFitxers.te(ctx)) AccesFitxers.ruta(ctx, arrel)?.takeIf { java.io.File(it).isDirectory } else null
        if (afegeix != null && dirRuta != null) {
            Thread {
                val informe = ArrayList<Pair<Boolean, String>>()
                val (bons, total) = try { afegeix(ctx, dirRuta, informe) { nom -> fil.post { missatge = t("Important...") + " " + nom } } }
                    catch (e: Throwable) { informe.add(false to "(carpeta) " + e.javaClass.simpleName + ": " + (e.message ?: "").take(160)); 0 to 0 }
                Informe.desaErrors(ctx, informe)
                fil.post { missatge = t("Importats:") + " $bons/$total"; Informe.linies = informe; carrega(ctx) }
            }.also { it.isDaemon = true }.start()
            return
        }
        Thread {
            val informe = ArrayList<Pair<Boolean, String>>()
            val (bons, total) = try {
                RomsMusica.importaCarpeta(ctx, arrel, informe) { nom -> fil.post { missatge = t("Important...") + " " + nom } }
            } catch (e: Throwable) { informe.add(false to "(carpeta) " + e.javaClass.simpleName + ": " + (e.message ?: "").take(160)); 0 to 0 }
            Informe.desaErrors(ctx, informe)
            fil.post { missatge = t("Importats:") + " $bons/$total"; Informe.linies = informe; carrega(ctx) }
        }.also { it.isDaemon = true }.start()
    }

    fun netejaAltres(ctx: Context) {
        RadioMotor.atura(); Mescla.atura()
        val (gba, ds) = RomsMusica.netejaAltres(ctx)
        missatge = t("Netejat:") + " GBA $gba, DS $ds"
        carrega(ctx)
    }
}

/**
 * Setlist: una llista de reproducció marcada com a guió de la sessió. Des del mesclador, «▸1» i «▸2» posen la cançó següent de la setlist al deck 1 o al 2 i avancen.
 * La setlist es recorda pel nom de la llista, amb la posició on s'ha quedat.
 */
object Setlist {
    var nom by mutableStateOf("")
    var pos by mutableIntStateOf(0)
    fun llista(): RadioDades.Llista? = if (nom.isEmpty()) null else RadioDades.llistes.firstOrNull { it.nom == nom }
    fun activa(n: String) { nom = n; pos = 0; desa() }
    fun desa() { App.env.putStr("set_nom", nom); App.env.putInt("set_pos", pos) }
    fun carrega() { nom = App.env.str("set_nom"); pos = App.env.int("set_pos", 0) }
    /** Nom de la cançó que toca ara a la setlist (null si no n'hi ha o s'ha acabat). */
    fun nomSeguent(b: Biblioteca): String? {
        val c = llista()?.cancons?.getOrNull(pos) ?: return null
        val e = b.perNom(RadioDades.emissoraDe(c)) ?: return c
        return b.etiq(e, RadioDades.idDe(c))
    }
    /** Posa la cançó següent al deck `d` i avança; al final de la llista torna a començar. */
    fun seguent(d: Int) {
        val b = App.bib ?: return; val l = llista() ?: return
        if (l.cancons.isEmpty()) return
        if (pos >= l.cancons.size) pos = 0
        val c = l.cancons[pos]
        val e = b.perNom(RadioDades.emissoraDe(c))
        pos++; desa()
        if (e != null) Mix.envia(d, e, RadioDades.idDe(c))
    }
}

/** Posa cançons als dos decks i en recorda l'origen (per a «stop», que torna la cançó al principi). */
object Mix {
    val ultima = arrayOfNulls<Pair<Emissora, Int>>(4)
    /** Crea la cançó `id` de `e` per al deck `d` (amb les seves pistes silenciades). */
    fun creaPerDeck(d: Int, e: Emissora, id: Int, muts: BooleanArray): Sintetitzador = synchronized(RadioMotor.llocMuts) {
        val anterior = RadioMotor.muts; RadioMotor.muts = muts
        try { e.crea(id) } finally { RadioMotor.muts = anterior }
    }

    /** Posa la cançó `id` de l'emissora `e` al deck `d` (es crea en un fil perquè pot carregar la ROM), amb les pistes silenciades pròpies del deck. */
    fun envia(d: Int, e: Emissora, id: Int, conservaMuts: Boolean = false) {
        val b = App.bib ?: return
        val nom = b.etiq(e, id); val clau = b.clauDe(e, id); val dk = Mescla.decks[d]
        ultima[d] = e to id
        dk.sona = false; dk.pendent = false
        Thread {
            try {
                if (!conservaMuts) { java.util.Arrays.fill(dk.muts, false); dk.solo = -1 }
                val s = creaPerDeck(d, e, id, dk.muts)
                s.preparaMesclador()                         // motors de ROM: pols, bucle i durada (en reproduir no es fan)
                // BPM real del principi: una còpia de la cançó avança en silenci fins al primer quart de batec (ja ha llegit l'ordre de tempo)
                val bpm0 = try {
                    val sp = synchronized(RadioMotor.llocMuts) { val anterior = RadioMotor.muts; RadioMotor.muts = BooleanArray(16); try { e.crea(id) } finally { RadioMotor.muts = anterior } }
                    sp.preparaMesclador()
                    if (sp.bpm > 0.0) { val bf = ShortArray(128); var n = 0; while (sp.batec in 0.0..0.25 && n < sp.taux * 3) { sp.renderitza(bf, 64); n += 64 }; sp.bpm } else 0.0
                } catch (ex: Throwable) { 0.0 }
                dk.carrega(s, nom, clau)
                dk.tipusEditats = RadioDades.tipus[clau]
                dk.bpmIni = bpm0
                val nomE = e.nom                     // només el nom: el deck no ha de retenir l'àlbum (ni la biblioteca vella) quan es refà la biblioteca
                dk.fabricaFins = { bt -> try { App.bib?.emissores?.firstOrNull { it.nom == nomE }?.let { e2 -> fabrica(d, e2, id, bt) } } catch (ex: Throwable) { null } }
                dk.preparaCue()
                // Tonalitat: s'analitza una còpia nova de la cançó (l'anàlisi consumeix el sintetitzador)
                try {
                    val s2 = synchronized(RadioMotor.llocMuts) { val anterior = RadioMotor.muts; RadioMotor.muts = BooleanArray(16); try { e.crea(id) } finally { RadioMotor.muts = anterior } }
                    s2.preparaMesclador()
                    val h = s2.analitza(300)
                    if (dk.sinte === s) { dk.tonalitat = h?.let { Tonalitat.detecta(it) }; dk.perfils = s2.perfils; dk.durada = s2.batecMax; dk.iniciBucle = if (s2.voltes > 0) s2.batec else -1.0; dk.preparaFinal() }
                } catch (ex: Throwable) { }
            } catch (ex: Throwable) { dk.error = ex.javaClass.simpleName + ": " + (ex.message ?: "") }
        }.also { it.isDaemon = true }.start()
    }

    /** Crea la cançó `id` de `e` (amb les pistes silenciades del deck) i l'avança en silenci fins al batec `beat`; null si la cançó s'acaba o fa bucle abans (o triga més de 60 s). */
    fun fabrica(d: Int, e: Emissora, id: Int, beat: Double): Sintetitzador? {
        try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) } catch (ex: Throwable) { }      // no ha de robar processador al fil d'àudio (microtalls)
        val dk = Mescla.decks[d]
        val s = creaPerDeck(d, e, id, dk.muts)
        s.solo = dk.solo
        val buf = ShortArray(512); val t0 = System.currentTimeMillis()
        if (beat <= 0.0) return s                       // principi de la cançó (també per a les que no tenen compte de batecs)
        while (s.batec < beat && !s.acabada && s.voltes == 0 && System.currentTimeMillis() - t0 < 60000L) s.renderitza(buf, 256)
        return if (s.batec >= beat) s else null
    }

    /**
     * Salta el deck al batec `beat` (4, 8, 12...): torna a crear la cançó i l'avança en silenci (sintetitzant sense sortida) fins a aquell batec; el deck no sona mentre es busca
     * (màxim 60 s) i, en acabar, continua si sonava. Només amb cançons amb BPM (m4a i SSEQ).
     */
    fun saltaBeat(d: Int, beat: Int) {
        val (e, id) = ultima[d] ?: return
        val dk = Mescla.decks[d]
        if (dk.buscant || dk.bpm() <= 0.0) return
        dk.buscant = true                                 // el deck segueix sonant mentre es prepara el salt
        Thread {
            try {
                val s = fabrica(d, e, id, beat.toDouble())
                if (s == null) {                                  // la cançó s'acaba o fa bucle abans d'aquest batec: es deixa el deck com estava
                    val m = t("La cançó no arriba al batec") + " $beat"
                    dk.error = m
                    Thread.sleep(3000)
                    if (dk.error == m) dk.error = null
                } else {
                    dk.canviaSinte(s)
                }
            } catch (ex: Throwable) { dk.error = ex.javaClass.simpleName + ": " + (ex.message ?: "") }
            finally { dk.buscant = false }
        }.also { it.isDaemon = true }.start()
    }

    /**
     * Mou la posició del deck `delta` batecs (-1, -0,5, 0,5, 1) **sense parar-lo**: crea la cançó de nou i l'avança en silenci fins a la posició que tindrà el deck quan estigui a punt
     * (un petit marge calculat amb el temps de la creació) i la hi canvia; si cal, espera uns mil·lisegons perquè el deck arribi al punt exacte. Cal BPM.
     */
    fun mou(d: Int, delta: Double) {
        val (e, id) = ultima[d] ?: return
        val dk = Mescla.decks[d]
        val s0 = dk.sinte ?: return
        if (dk.buscant || dk.bpm() <= 0.0) return
        if (delta > 0.0) { dk.avancaBatecs(delta); return }        // endavant: a l'instant, el mateix sintetitzador avança en silenci
        dk.buscant = true
        Thread {
            try {
                val p0 = dk.batecAudible()
                if (p0 < 0.0) return@Thread
                val t0 = System.currentTimeMillis()
                val batecsPerSeg = dk.bpm() * dk.mult() / 60.0
                val s = fabrica(d, e, id, maxOf(0.0, p0 + delta + 0.05 * batecsPerSeg)) ?: throw IllegalStateException(t("La cançó no arriba al batec"))
                val buf = ShortArray(512); val limit = System.currentTimeMillis() + 3000L
                while (System.currentTimeMillis() < limit) {
                    val need = maxOf(0.0, dk.batecAudible() + delta)
                    if (s.batec < need - 0.01) { while (s.batec < need && !s.acabada && s.voltes == 0) s.renderitza(buf, 128) }
                    else if (s.batec > need + 0.01 && dk.sona) Thread.sleep(2)       // el deck encara no hi és: s'espera
                    else break
                }
                dk.canviaSinte(s)
                if (dk.sona) Mescla.continua(d)
            } catch (ex: Throwable) {
                val m = ex.message ?: ex.javaClass.simpleName
                dk.error = m
                Thread.sleep(3000)
                if (dk.error == m) dk.error = null
            } finally { dk.buscant = false }
        }.also { it.isDaemon = true }.start()
    }

    /** Torna a posar els tipus de canal editats als decks que tenen aquesta cançó. */
    fun refrescaTipus(clau: String) { for (dk in Mescla.decks) if (dk.clau == clau) dk.tipusEditats = RadioDades.tipus[clau] }

    /** Desa l'estat del mesclador (cançó, tempo, rang, volum i key lock de cada deck, crossfader i pestanya) per recuperar-lo en tornar a obrir l'app. */
    fun desaEstat() {
        val env = App.env
        for (d in 0..3) {
            val dk = Mescla.decks[d]; val u = ultima[d]
            env.putStr("deck${d}_joc", u?.first?.clauDades ?: ""); env.putInt("deck${d}_id", u?.second ?: -1)
            env.putInt("deck${d}_tempo", Math.round(dk.tempoPc * 100).toInt()); env.putInt("deck${d}_rang", dk.rang)
            env.putInt("deck${d}_vol", dk.volum); env.putInt("deck${d}_lock", if (dk.bloqueigTo) 1 else 0)
        }
        env.putInt("creuament", Math.round(Mescla.creuament * 100)); env.putInt("pestanya", App.pestanya); env.putInt("vis0", Mescla.visible[0]); env.putInt("vis1", Mescla.visible[1])
    }
    /** Recupera l'estat desat (les cançons es carreguen aturades, al principi). */
    fun restaura(b: Biblioteca) {
        val env = App.env
        App.pestanya = env.int("pestanya", 0).coerceIn(0, 3)
        Mescla.creuament = env.int("creuament", 50).coerceIn(0, 100) / 100f
        Mescla.visible[0] = if (env.int("vis0", 0) == 2) 2 else 0; Mescla.visible[1] = if (env.int("vis1", 1) == 3) 3 else 1
        for (d in 0..3) {
            val dk = Mescla.decks[d]
            dk.rang = env.int("deck${d}_rang", 16).takeIf { it in listOf(6, 10, 16, 50) } ?: 16
            dk.tempoPc = (env.int("deck${d}_tempo", 0) / 100.0).coerceIn(-dk.rang.toDouble(), dk.rang.toDouble())
            dk.volum = env.int("deck${d}_vol", 100).coerceIn(0, 100); dk.bloqueigTo = env.int("deck${d}_lock", 1) == 1
            val nom = env.str("deck${d}_joc"); val id = env.int("deck${d}_id", -1)
            val e = b.perNom(nom) ?: b.emissores.firstOrNull { it.nom == nom } ?: continue          // clau de dades (P1) o, desat abans, el nom (també llistes)
            if (dk.sinte == null && id in e.ids) envia(d, e, id)
        }
    }

    /** En publicar una biblioteca nova, l'última cançó de cada deck passa a apuntar a l'àlbum nou amb el mateix nom (o a res), i així la vella es pot alliberar. */
    fun reubica(b: Biblioteca) {
        for (d in ultima.indices) { val u = ultima[d] ?: continue; ultima[d] = (if (u.first.esLlista) b.emissores.firstOrNull { it.nom == u.first.nom } else b.perNom(u.first.clauDades))?.let { it to u.second } }
    }

    /** Stop: atura el deck i torna la cançó al principi (conserva les pistes silenciades). */
    fun atura(d: Int) {
        Mescla.pausa(d)
        ultima[d]?.let { (e, id) -> envia(d, e, id, true) }
    }
}

/**
 * Portades: la icona de 32x32 de la ROM ampliada o la baixada de libretro. Memòria cau limitada per BYTES (24 MB) i les portades grans es descodifiquen reduïdes
 * (`inSampleSize`, costat màxim ~512 px). Al fil principal `bitmap` no llegeix mai el disc: si no hi és, la carrega en un fil apart i torna null; en acabar puja `versio`
 * (estat de Compose, que `bitmap` llegeix), i la pantalla es torna a dibuixar sola. Fora del fil principal (notificació, Android Auto) la carrega al moment.
 */
object Portades {
    var versio by mutableIntStateOf(0)
    private class Ent(val bm: Bitmap?)
    private val cache = LruBytes<String, Ent>(24L shl 20) { it.bm?.byteCount?.toLong() ?: 64L }
    private val enCurs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val exec = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "portades").also { it.isDaemon = true; it.priority = Thread.MIN_PRIORITY } }
    private val fil = Handler(Looper.getMainLooper())
    fun pixels(ctx: Context, e: Emissora): IntArray? =
        e.iconaCodi?.let { RomsMusica.icona(ctx, it) }          // només la icona del propi joc de DS: els jocs d'altres consoles ja no agafen la d'un joc de DS amb el mateix nom
    /** Portada baixada de libretro (Ajustos > Libretro): `libretro/art/<àlbum>.jpg`; si hi és, té prioritat sobre la icona de píxels. Es redueix a ~512 px. */
    /** Nom del fitxer de la portada de libretro (sense «.jpg»): la clau de dades de l'àlbum (P1), amb «:» → «_». Abans era `Emissora.nom` (es migra a `RadioDades.adopta`). */
    fun nomArt(e: Emissora): String = e.clauDades.replace(':', '_')
    /** P1: les portades desades amb el nom d'un àlbum de consola (`noms`: nom -> clau estable) passen al nom nou (es reanomenen; si ja n'hi ha una amb el nom nou, la vella es queda). */
    fun migraArt(ctx: Context, noms: Map<String, String>): Int {
        val dir = java.io.File(ctx.filesDir, "libretro/art"); if (!dir.isDirectory) return 0
        var n = 0
        for ((nom, est) in noms) {
            if (nom == est) continue
            val vell = java.io.File(dir, "$nom.jpg"); if (!vell.exists()) continue
            val nou = java.io.File(dir, est.replace(':', '_') + ".jpg"); if (nou.exists()) continue
            if (vell.renameTo(nou)) n++
        }
        if (n > 0) fil.post { netejaCache() }
        return n
    }
    private fun art(ctx: Context, e: Emissora): Bitmap? = try {
        val f = java.io.File(ctx.filesDir, "libretro/art/${nomArt(e)}.jpg")
        if (!f.exists()) null else {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.path, o)
            var mostra = 1; while (maxOf(o.outWidth, o.outHeight) / (mostra * 2) >= 512) mostra *= 2
            android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = mostra })
        }
    } catch (ex: Throwable) { null }
    fun netejaCache() { cache.clear(); fil.post { versio++ } }
    // Revisió UI (2026-11-22): les portades que arriben seguides (en obrir la Biblioteca n'arriben desenes) fan un sol `versio++` (abans, un per portada, i cada un
    // refeia totes les portades pendents de la pantalla). Si ja n'hi ha un d'enviat al fil principal, no se n'envia cap més: el que hi ha les recull totes.
    private val avisPendent = java.util.concurrent.atomic.AtomicBoolean(false)
    private fun avisa() { if (avisPendent.compareAndSet(false, true)) fil.post { avisPendent.set(false); versio++ } }
    private fun fes(ctx: Context, e: Emissora): Bitmap? {
        art(ctx, e)?.let { return it }
        val p = pixels(ctx, e) ?: return null
        return try { Bitmap.createBitmap(p, 32, 32, Bitmap.Config.ARGB_8888) } catch (ex: Throwable) { null }
    }
    fun bitmap(ctx: Context, e: Emissora): Bitmap? {
        cache.get(e.clauDades)?.let { return it.bm }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            @Suppress("UNUSED_VARIABLE") val v = versio          // la pantalla es torna a dibuixar quan arriba la portada
            if (enCurs.add(e.clauDades)) {
                val app = ctx.applicationContext
                exec.execute {
                    val bm = try { fes(app, e) } catch (ex: Throwable) { null }
                    cache.put(e.clauDades, Ent(bm)); enCurs.remove(e.clauDades)
                    avisa()
                }
            }
            return null
        }
        val bm = fes(ctx, e); cache.put(e.clauDades, Ent(bm)); return bm
    }
}
