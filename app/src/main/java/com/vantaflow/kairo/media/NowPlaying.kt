package com.vantaflow.kairo.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Accès aux notifications : il ne sert qu'à lire les lecteurs en cours des autres applis
 * (Spotify, Anghami, Deezer, YouTube…). Aucune notification n'est lue ni affichée.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        NowPlaying.start(applicationContext)
    }
}

/** Média en cours dans une autre appli. */
data class MediaNow(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val subtitle: String?,
    val art: ImageBitmap?,
    val playing: Boolean,
)

/** Côté de l'image de la pochette gardée en mémoire. */
private const val ART_SIZE = 160

/** Commande ADB qui donne l'accès, pour les box sans écran de réglage. */
const val MEDIA_ACCESS_ADB = "adb shell cmd notification allow_listener com.vantaflow.kairo/com.vantaflow.kairo.media.MediaListener"

/**
 * Lecteur en cours des autres applis, suivi par les rappels d'Android (rien n'est interrogé en
 * boucle). La carte reste affichée quand la lecture est en pause, et disparaît à l'arrêt.
 */
object NowPlaying {
    private val mutable = MutableStateFlow<MediaNow?>(null)
    val state: StateFlow<MediaNow?> = mutable

    private val main = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var appContext: Context? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() {
            manager?.let { m -> pick(runCatching { m.getActiveSessions(component()) }.getOrDefault(emptyList())) }
        }
    }

    fun hasAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(context))

    /** À appeler au démarrage et à chaque retour sur l'accueil : sans effet si déjà suivi ou sans accès. */
    fun start(context: Context) {
        val app = context.applicationContext
        if (manager != null || !hasAccess(app)) return
        appContext = app
        val m = app.getSystemService(MediaSessionManager::class.java) ?: return
        val sessions = runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, component(app), main)
            m.getActiveSessions(component(app))
        }.getOrNull() ?: return
        manager = m
        pick(sessions)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext() ?: Unit
    fun previous() = controller?.transportControls?.skipToPrevious() ?: Unit

    /** Ouvre l'appli qui joue (son écran de lecture si elle le fournit). */
    fun open(context: Context) {
        val c = controller ?: return
        val sent = c.sessionActivity?.let { runCatching { it.send() }.isSuccess } ?: false
        if (sent) return
        val pm = context.packageManager
        (pm.getLeanbackLaunchIntentForPackage(c.packageName) ?: pm.getLaunchIntentForPackage(c.packageName))
            ?.let { runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    /** Lecteur à suivre : celui qui joue, sinon le premier en pause ; jamais Kairo (sa radio a sa propre pastille). */
    private fun pick(sessions: List<MediaController>) {
        val own = appContext?.packageName
        val candidates = sessions.filter { it.packageName != own && it.playbackState?.state.isActive() }
        val chosen = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: candidates.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = chosen
            chosen?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val state = c?.playbackState?.state
        if (c == null || !state.isActive()) {
            mutable.value = null
            return
        }
        val meta = c.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val label = appLabel(c.packageName)
        mutable.value = MediaNow(
            packageName = c.packageName,
            appLabel = label,
            title = title?.takeIf { it.isNotBlank() } ?: label,
            subtitle = meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            art = art(meta),
            playing = state == PlaybackState.STATE_PLAYING,
        )
    }

    /** Pochette réduite (une seule petite image en mémoire), réutilisée tant que le titre ne change pas. */
    private var lastArt: Pair<Bitmap, ImageBitmap>? = null

    private fun art(meta: MediaMetadata?): ImageBitmap? {
        val source = meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        lastArt?.let { (bitmap, image) -> if (bitmap === source) return image }
        val scale = ART_SIZE.toFloat() / maxOf(source.width, source.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true) else source
        return small.asImageBitmap().also { lastArt = source to it }
    }

    private fun appLabel(pkg: String): String = appContext?.packageManager?.let { pm ->
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    } ?: pkg

    private fun component(context: Context = appContext!!) = ComponentName(context, MediaListener::class.java)

    private fun Int?.isActive() = this == PlaybackState.STATE_PLAYING || this == PlaybackState.STATE_PAUSED ||
        this == PlaybackState.STATE_BUFFERING || this == PlaybackState.STATE_CONNECTING
}
