package com.vantaflow.kairo.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RadioStatus { Idle, Loading, Playing, Error }

data class RadioState(val station: Station? = null, val status: RadioStatus = RadioStatus.Idle) {
    val active get() = status == RadioStatus.Loading || status == RadioStatus.Playing
}

/** Radio en cours, partagée par l'écran Radio, l'accueil et le minuteur. */
object Radio {
    internal val mutableState = MutableStateFlow(RadioState())
    val state: StateFlow<RadioState> = mutableState

    fun play(context: Context, station: Station) {
        mutableState.value = RadioState(station, RadioStatus.Loading)
        context.startForegroundService(Intent(context, RadioService::class.java).setAction(RadioService.ACTION_PLAY))
    }

    /** Arrête la radio (service du même processus : pas de démarrage de service en arrière-plan). */
    fun stop(@Suppress("UNUSED_PARAMETER") context: Context) {
        RadioService.current?.let { s -> s.mainHandler.post { s.stopSelf() } }
    }

    /** Liste d'où vient la radio en cours : suivante / précédente y circulent. */
    @Volatile var queue: List<Station> = emptyList()

    /** Radio voisine dans [queue] ([step] = +1 suivante, -1 précédente). */
    fun skip(context: Context, step: Int) {
        val list = queue.ifEmpty { return }
        val i = list.indexOfFirst { it.url == state.value.station?.url }
        play(context, list[Math.floorMod(i + step, list.size)])
    }

    fun toggle(context: Context, station: Station) {
        val s = state.value
        if (s.active && s.station?.url == station.url) stop(context) else play(context, station)
    }
}

/**
 * Lecture de la radio en arrière-plan (MediaPlayer du système : aucune bibliothèque en plus).
 * Une autre appli qui prend le son (YouTube, Netflix…) arrête la radio ; les touches lecture /
 * pause / stop de la télécommande passent par la MediaSession.
 */
class RadioService : Service() {
    private var player: MediaPlayer? = null
    private lateinit var session: MediaSession
    private lateinit var audio: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    internal val mainHandler get() = handler
    private val timeout = Runnable { fail() }
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(ATTRIBUTES)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> stopSelf()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.3f, 0.3f)
                    AudioManager.AUDIOFOCUS_GAIN -> player?.setVolume(1f, 1f)
                }
            }
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        current = this
        audio = getSystemService(AudioManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Radio", NotificationManager.IMPORTANCE_LOW),
        )
        session = MediaSession(this, "KairoRadio").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPause() = stopSelf()
                override fun onStop() = stopSelf()
                override fun onSkipToNext() = Radio.skip(this@RadioService, +1)
                override fun onSkipToPrevious() = Radio.skip(this@RadioService, -1)
                override fun onPlay() {
                    Radio.state.value.station?.let { Radio.play(this@RadioService, it) }
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val station = Radio.state.value.station
        startForeground(1, notification(station?.name ?: "Radio"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        when (intent?.action) {
            ACTION_PLAY -> if (station != null) start(station) else stopSelf()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(station: Station) {
        release()
        if (audio.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            fail()
            return
        }
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, station.name).build())
        publish(PlaybackState.STATE_BUFFERING)
        handler.postDelayed(timeout, CONNECT_TIMEOUT_MS)
        player = MediaPlayer().apply {
            setAudioAttributes(ATTRIBUTES)
            setOnPreparedListener {
                handler.removeCallbacks(timeout)
                it.start()
                Radio.mutableState.value = RadioState(station, RadioStatus.Playing)
                publish(PlaybackState.STATE_PLAYING)
            }
            setOnErrorListener { _, _, _ -> fail(); true }
            // Un flux en direct ne se termine pas : s'il s'arrête, c'est une coupure.
            setOnCompletionListener { fail() }
            try {
                setDataSource(station.url)
                prepareAsync()
            } catch (_: Exception) {
                fail()
            }
        }
    }

    private fun fail() {
        Radio.mutableState.value = Radio.state.value.copy(status = RadioStatus.Error)
        stopSelf()
    }

    private fun publish(state: Int) {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
    }

    private fun notification(title: String): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("Kairo Radio")
            .setOngoing(true)
            .build()

    private fun release() {
        handler.removeCallbacks(timeout)
        player?.release()
        player = null
    }

    override fun onDestroy() {
        release()
        audio.abandonAudioFocusRequest(focusRequest)
        publish(PlaybackState.STATE_STOPPED)
        session.release()
        if (current === this) current = null
        val s = Radio.state.value
        if (s.status != RadioStatus.Error) Radio.mutableState.value = s.copy(status = RadioStatus.Idle)
        super.onDestroy()
    }

    companion object {
        @Volatile internal var current: RadioService? = null
        const val ACTION_PLAY = "com.vantaflow.kairo.radio.PLAY"
        const val ACTION_STOP = "com.vantaflow.kairo.radio.STOP"
        private const val CHANNEL = "radio"
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private val ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
