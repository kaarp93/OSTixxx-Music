package cat.gbdsmixxx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.browse.MediaBrowser
import android.media.MediaDescription
import android.os.Bundle
import android.service.media.MediaBrowserService
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Servei en primer pla de la ràdio: manté l'àudio amb la pantalla bloquejada o l'app en segon pla (notificació de reproducció + bloqueig
 * parcial de CPU perquè el sintetitzador no es quedi sense processador). Té una sessió multimèdia: anterior, pausa/play i següent responen des de la
 * notificació, la pantalla de bloqueig i els auriculars. S'atura amb el botó de la notificació o quan la ràdio s'atura; en desconnectar els auriculars es posa en pausa
 * (2026-11-22: abans s'aturava i ja no es podia reprendre). En pausa deixa el primer pla: la notificació es pot esborrar (i esborrar-la atura la ràdio).
 */
class RadioServei : MediaBrowserService() {
    companion object {
        /**
         * El servei és exportat (Android Auto s'hi ha d'enllaçar), així que qualsevol app el podria arrencar amb ATURA/PAUSA/... (2026-11-22). Les ordres
         * bones porten aquesta clau (aleatòria, només en memòria del procés): les posen `RadioMotor.inicia` i els botons de la notificació. Sense clau, no es fa res.
         */
        val clau: String = java.util.UUID.randomUUID().toString()
        /** Qui pot navegar per la biblioteca (`onGetRoot`), a més del sistema i d'OSTixxx: Android Auto i els controls multimèdia del sistema (represa de l'última cançó). */
        private val NAVEGADORS = setOf("com.google.android.projection.gearhead", "com.android.systemui")
        /**
         * La sessió multimèdia sobreviu al servei si Android l'atura en pausa (en pausa ja no és en primer pla i, amb l'app en segon pla, el sistema l'atura al cap
         * d'uns minuts): així el botó de l'auricular i la notificació (que es queda, deslligada) poden reprendre; la represa torna a arrencar el servei.
         */
        @Volatile private var sessioViva: MediaSession? = null
        @Volatile private var viu = false
        /** La ràdio s'ha aturat: si el servei ja no hi és (l'havia tancat Android en pausa), treu la notificació deslligada i allibera la sessió que s'havia quedat. */
        fun acaba(ctx: Context) {
            if (viu) return          // el servei viu ho fa a onDestroy (stopService)
            try { sessioViva?.release() } catch (e: Throwable) { }
            sessioViva = null
            try { (ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(7) } catch (e: Throwable) { }
        }
    }
    /** El servei és en primer pla (mentre sona). En pausa en surt i la notificació es pot esborrar. */
    private var enPrimerPla = false
    private var bloqueig: PowerManager.WakeLock? = null
    private var carregantDelNavegador = false
    private var sessio: MediaSession? = null

    // ---- Focus d'àudio (2026-11-22): pausa en trucades o quan una altra app el pren, abaixa el volum amb avisos curts i torna a sonar sola si era una pèrdua temporal ----
    /** La pausa l'ha feta el focus (pèrdua temporal): en recuperar-lo, es reprèn sola. Una pausa o represa de l'usuari ho anul·la. */
    private var represaAuto = false
    private var teFocus = false
    /** Ara mateix pausa el focus (no l'usuari): `alCanviar` no ha d'anul·lar la represa automàtica. */
    private var propi = false
    private val canviFocus = AudioManager.OnAudioFocusChangeListener { f ->
        when (f) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                teFocus = true; RadioMotor.volum = 1f
                if (represaAuto && RadioMotor.pausat) { represaAuto = false; propi = true; try { RadioMotor.represa() } finally { propi = false } }
                represaAuto = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> RadioMotor.volum = 0.2f           // només arriba si el sistema no abaixa el volum sol (Android 8+ ho fa sol)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {                                          // trucada, assistent de veu: pausa i, en acabar, torna
                RadioMotor.volum = 1f
                if (RadioMotor.sonant && !RadioMotor.pausat) { propi = true; try { RadioMotor.pausa() } finally { propi = false }; represaAuto = true }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {                                                    // una altra app de música o vídeo: pausa i no torna sola
                teFocus = false; represaAuto = false; RadioMotor.volum = 1f
                RadioMotor.pausa()
            }
        }
    }
    private val peticioFocus: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(canviFocus).build()
    }
    /** Demana el focus abans de sonar; si el sistema el nega (hi ha una trucada en curs), la cançó es queda en pausa. */
    private fun demanaFocus(): Boolean {
        if (teFocus) return true
        val r = try { (getSystemService(AUDIO_SERVICE) as AudioManager).requestAudioFocus(peticioFocus) } catch (e: Throwable) { AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
        teFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (teFocus) RadioMotor.volum = 1f
        return teFocus
    }
    private fun deixaFocus() {
        if (!teFocus) return
        teFocus = false; represaAuto = false; RadioMotor.volum = 1f
        try { (getSystemService(AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(peticioFocus) } catch (e: Throwable) { }
    }
    /** Sonant sense focus (represa de l'usuari, cançó nova): el demana; si no el dona, torna a la pausa. */
    private fun comprovaFocus() { if (RadioMotor.sonant && !RadioMotor.pausat && !demanaFocus()) RadioMotor.pausa() }
    private val soroll = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { represaAuto = false; RadioMotor.pausa() }          // pausa de l'usuari (tampoc no torna sola si ja era en pausa per una trucada) (cap represa automàtica): es reprèn des de la notificació, l'auricular o l'app
    }

    // MediaBrowserService (la que fa servir Android Auto) respon a la seva acció; per a la resta d'intents no hi ha enllaç
    override fun onBind(i: Intent?): IBinder? = super.onBind(i)

    override fun onCreate() {
        super.onCreate()
        // Sessió multimèdia: els botons dels auriculars, de la pantalla de bloqueig i de la notificació criden aquests mètodes del motor
        viu = true
        sessio = (sessioViva ?: MediaSession(applicationContext, "gbdsmixxx-radio")).also { s ->
            sessioViva = s
            s.setCallback(object : MediaSession.Callback() {
                override fun onPlay() { RadioMotor.represa() }
                override fun onPause() { RadioMotor.pausa() }
                override fun onSkipToNext() { RadioMotor.salta(1) }
                override fun onSkipToPrevious() { RadioMotor.salta(-1) }
                override fun onStop() { RadioMotor.atura() }
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) { reprodueixDelNavegador(mediaId) }
            })
            s.isActive = true
            setSessionToken(s.sessionToken)
        }
        // el bloqueig i l'auricular es preparen aquí (abans, a la primera cançó): si Android ha aturat el servei en pausa, la represa (PLAY, auricular) en crea un de nou
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("radio", "Ràdio", NotificationManager.IMPORTANCE_LOW))
            bloqueig = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gbdsmixxx:radio").also { it.setReferenceCounted(false) }
            registerReceiver(soroll, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        } catch (e: Throwable) { RadioMotor.error = "Servei: " + e.javaClass.simpleName }
        RadioMotor.alCanviar = {
            if (!propi) represaAuto = false          // pausa o represa de l'usuari (des de qualsevol lloc: l'app, la notificació, l'auricular)
            comprovaFocus()
            ajustaBloqueig()
            primerPla(construeix(RadioMotor.titol))
        }
    }

    // ---- Android Auto: arbre de navegació (Àlbums i Llistes -> cançons) ----
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        val permes = clientUid == android.os.Process.myUid() || clientUid == android.os.Process.SYSTEM_UID ||
            (clientPackageName in NAVEGADORS && (try { packageManager.getPackagesForUid(clientUid)?.contains(clientPackageName) == true } catch (e: Throwable) { false }))
        return if (permes) BrowserRoot("arrel", null) else null          // null = connexió refusada: cap altra app no veu els jocs ni les llistes
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        result.detach()
        Thread {
            val fills = try { fillsDe(parentId) } catch (e: Throwable) { mutableListOf() }
            result.sendResult(fills)
        }.also { it.isDaemon = true }.start()
    }

    /** La biblioteca, carregant-la si Android Auto arrenca el servei abans que l'activitat (espera fins a 90 s). */
    private fun biblioteca(): Biblioteca? {
        val inicialitzat = try { App.env; true } catch (e: UninitializedPropertyAccessException) { false }
        if (!inicialitzat) {
            App.env = Env(applicationContext)
            RadioDades.carrega(applicationContext)
            App.amagaSenseBucle = App.env.int("amaga_sense_bucle", 1) == 1
        }
        if (App.bib == null && !carregantDelNavegador) { carregantDelNavegador = true; App.carrega(applicationContext) }
        var t = 0
        while (App.bib == null && t++ < 450) Thread.sleep(200)
        return App.bib
    }

    private fun item(id: String, titol: String, sub: String?, navegable: Boolean, e: Emissora? = null): MediaBrowser.MediaItem {
        val d = MediaDescription.Builder().setMediaId(id).setTitle(titol).setSubtitle(sub)
        e?.let { Portades.bitmap(applicationContext, it)?.let { b -> try { d.setIconBitmap(android.graphics.Bitmap.createScaledBitmap(b, 128, 128, b.width > 32)) } catch (x: Throwable) { } } }
        return MediaBrowser.MediaItem(d.build(), if (navegable) MediaBrowser.MediaItem.FLAG_BROWSABLE else MediaBrowser.MediaItem.FLAG_PLAYABLE)
    }

    private fun nomJoc(e: Emissora) = e.iconaNom ?: e.vista

    private fun fillsDe(pare: String): MutableList<MediaBrowser.MediaItem> {
        val b = biblioteca() ?: return mutableListOf()
        val l = ArrayList<MediaBrowser.MediaItem>()
        when {
            pare == "arrel" -> { l.add(item("albums", "Àlbums", null, true)); l.add(item("llistes", "Llistes", null, true)) }
            pare == "albums" -> for (e in b.reals().filter { it.ids.isNotEmpty() }) l.add(item("e:" + e.nom, nomJoc(e), cancons(b, e).size.toString() + " cançons", true, e))
            pare == "llistes" -> for (e in b.emissores.filter { it.esLlista }) l.add(item("e:" + e.nom, e.vista, cancons(b, e).size.toString() + " cançons", true, e))
            pare.startsWith("e:") -> {
                val e = b.emissores.firstOrNull { it.nom == pare.removePrefix("e:") } ?: return l
                l.add(item("a:" + e.nom, "▶ " + nomJoc(e), null, false, e))
                for (id in cancons(b, e).take(250)) l.add(item("c:" + e.nom + ":" + id, b.etiq(e, id), nomJoc(e), false))
            }
        }
        return l
    }

    /** Reprodueix el que s'ha triat a la pantalla del cotxe: «a:<joc>» tot l'àlbum des del principi, «c:<joc>:<cançó>» aquella cançó i les següents. */
    private fun reprodueixDelNavegador(mediaId: String?) {
        if (mediaId == null) return
        Thread {
            val b = biblioteca() ?: return@Thread
            try {
                if (mediaId.startsWith("a:")) {
                    val e = b.emissores.firstOrNull { it.nom == mediaId.removePrefix("a:") } ?: return@Thread
                    Reprod.reprodueix(applicationContext, b, e, cancons(b, e), 0)
                } else if (mediaId.startsWith("c:")) {
                    val resta = mediaId.removePrefix("c:")
                    val e = b.emissores.firstOrNull { it.nom == resta.substringBeforeLast(':') } ?: return@Thread
                    val ids = cancons(b, e)
                    Reprod.reprodueix(applicationContext, b, e, ids, ids.indexOf(resta.substringAfterLast(':').toIntOrNull() ?: -1).coerceAtLeast(0))
                }
            } catch (e: Throwable) { RadioMotor.error = "Auto: " + e.javaClass.simpleName }
        }.also { it.isDaemon = true }.start()
    }

    private var portadaPixels: IntArray? = null; private var portadaBmp: android.graphics.Bitmap? = null
    /** La icona del joc (RadioMotor.icona) ampliada 8 vegades sense suavitzar, per a la portada de la notificació i de la pantalla de bloqueig. */
    private var artClau: String? = null; private var artBmp: android.graphics.Bitmap? = null
    private fun portada(): android.graphics.Bitmap? {
        val nomArt = RadioMotor.portadaNom
        if (nomArt != null) {                                     // portada baixada de libretro: és l'«album art» de la notificació i de la pantalla de bloqueig
            val f = java.io.File(filesDir, "libretro/art/$nomArt.jpg")
            if (f.exists()) { val clau = nomArt + f.lastModified(); if (artClau != clau) { artClau = clau; artBmp = try { android.graphics.BitmapFactory.decodeFile(f.path) } catch (e: Throwable) { null } }; if (artBmp != null) return artBmp }
        }
        val p = RadioMotor.icona ?: return null
        if (p !== portadaPixels) {
            portadaPixels = p
            portadaBmp = try { android.graphics.Bitmap.createScaledBitmap(android.graphics.Bitmap.createBitmap(p, 32, 32, android.graphics.Bitmap.Config.ARGB_8888), 256, 256, false) } catch (e: Exception) { null }
        }
        return portadaBmp
    }

    private fun accio(codi: Int, nom: String) = PendingIntent.getService(this, codi, Intent(this, RadioServei::class.java).setAction(nom).putExtra("clau", clau), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Notificació de reproducció amb estil multimèdia (anterior, pausa/play, següent, atura) i estat de la sessió. */
    private fun construeix(titol: String): Notification {
        val pausat = RadioMotor.pausat
        sessio?.let { s ->
            s.setPlaybackState(PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP)
                .setState(if (pausat) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build())
            s.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, titol).putString(MediaMetadata.METADATA_KEY_ARTIST, "OSTixxx").also { m -> portada()?.let { m.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }.build())
        }
        val obre = PendingIntent.getActivity(this, 0, (packageManager.getLaunchIntentForPackage(packageName) ?: Intent()), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(this, "radio")
            .setContentTitle(titol).setContentText("OSTixxx")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(obre).setOngoing(!pausat).setDeleteIntent(accio(5, "ESBORRA"))
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_previous), "Anterior", accio(1, "ANTERIOR")).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, if (pausat) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause),
                if (pausat) "Play" else "Pausa", accio(2, if (pausat) "PLAY" else "PAUSA")).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_next), "Següent", accio(3, "SEGUENT")).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Atura", accio(4, "ATURA")).build())
        portada()?.let { b.setLargeIcon(it) }
        sessio?.let { b.setStyle(Notification.MediaStyle().setMediaSession(it.sessionToken).setShowActionsInCompactView(0, 1, 2)) }
        return b.build()
    }

    /**
     * Sonant: primer pla amb la notificació fixa. En pausa (2026-11-22): surt del primer pla deixant la notificació (deslligada, es pot esborrar; esborrar-la atura
     * la ràdio). A Android 12+ tornar al primer pla amb l'app en segon pla només es pot des de la notificació, l'auricular o Android Auto (el sistema ho permet en
     * aquests casos); si el refusa, la música continua (amb el bloqueig de CPU) i la notificació només s'actualitza.
     */
    @Synchronized private fun primerPla(n: Notification) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (RadioMotor.pausat) {
            if (enPrimerPla) { try { stopForeground(STOP_FOREGROUND_DETACH) } catch (e: Throwable) { }; enPrimerPla = false }
            nm.notify(7, n)
        } else try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(7, n)
            enPrimerPla = true
        } catch (e: Throwable) { RadioMotor.error = "Servei: " + e.javaClass.simpleName; nm.notify(7, n) }
    }

    /**
     * Bloqueig parcial de CPU només mentre sona (ronda d'optimització, 2026-11-18): en pausa s'allibera (abans es mantenia fins a 6 h amb la música aturada i el mòbil
     * no podia dormir); el servei continua en primer pla amb la notificació, i la represa (notificació, auriculars, app) el torna a agafar.
     */
    @Synchronized private fun ajustaBloqueig() {
        val b = bloqueig ?: return
        try { if (RadioMotor.pausat) { if (b.isHeld) b.release() } else if (!b.isHeld) b.acquire(6 * 3600 * 1000L) } catch (e: Throwable) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getStringExtra("clau") != clau) { rebutja(); return START_NOT_STICKY }
        when (intent?.action) {
            "ATURA" -> { RadioMotor.atura(); stopSelf(); return START_NOT_STICKY }
            "ESBORRA" -> { if (!RadioMotor.sonant || RadioMotor.pausat) { RadioMotor.atura(); stopSelf() }; return START_NOT_STICKY }          // notificació esborrada (només es pot en pausa)
            "ANTERIOR" -> { RadioMotor.salta(-1); return START_NOT_STICKY }
            "SEGUENT" -> { RadioMotor.salta(1); return START_NOT_STICKY }
            "PAUSA" -> { RadioMotor.pausa(); return START_NOT_STICKY }
            "PLAY" -> { RadioMotor.represa(); return START_NOT_STICKY }
        }
        // Si Android refusa el primer pla (restriccions d'Android 12+) o falla la notificació, la ràdio segueix sonant sense servei en comptes de tombar l'app
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("radio", "Ràdio", NotificationManager.IMPORTANCE_LOW))
            val n = construeix(intent?.getStringExtra("titol") ?: RadioMotor.titol)
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(7, n)
            enPrimerPla = true
            ajustaBloqueig()          // una cançó nova (inicia: pausat = false) torna a agafar el bloqueig si s'havia alliberat en pausa
            represaAuto = false
            comprovaFocus()
        } catch (e: Throwable) { RadioMotor.error = "Servei: " + e.javaClass.simpleName; stopSelf() }
        return START_NOT_STICKY
    }

    /**
     * Ordre sense la clau: d'una altra app o d'una notificació d'un procés anterior (Android ha tancat l'app en pausa). No es toca la música. Si no sona res,
     * es treu la notificació òrfena i el servei s'atura; per si l'han arrencat amb startForegroundService, primer passa pel primer pla (si no, Android tomba l'app).
     */
    private fun rebutja() {
        if (RadioMotor.sonant) {          // la música no es toca; només es compleix el primer pla per si de cas i es torna a l'estat que toca
            val n = construeix(RadioMotor.titol)
            try { if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(7, n); enPrimerPla = true } catch (e: Throwable) { }
            primerPla(n); return
        }
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("radio", "Ràdio", NotificationManager.IMPORTANCE_LOW))
            val n = Notification.Builder(this, "radio").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("OSTixxx").build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(7, n)
        } catch (e: Throwable) { }
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (e: Throwable) { }
        try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(7) } catch (e: Throwable) { }
        stopSelf()
    }

    override fun onDestroy() {
        RadioMotor.alCanviar = null
        viu = false
        deixaFocus()
        if (RadioMotor.sonant && RadioMotor.pausat) {
            // Android ha aturat el servei en pausa: la sessió i la notificació es queden perquè es pugui reprendre (RadioMotor.represa torna a arrencar el servei)
        } else {
            try { sessio?.release() } catch (e: Exception) {}
            sessioViva = null
            try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(7) } catch (e: Throwable) { }
        }
        sessio = null
        try { unregisterReceiver(soroll) } catch (e: Exception) {}
        bloqueig?.let { if (it.isHeld) it.release() }; bloqueig = null
        super.onDestroy()
    }
}
