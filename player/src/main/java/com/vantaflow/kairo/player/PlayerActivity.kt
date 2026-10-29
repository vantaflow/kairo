package com.vantaflow.kairo.player

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Lecteur plein écran (Media3 / ExoPlayer) : reprise là où on s'était arrêté, ← → pour
 * reculer / avancer de 10 s, OK pour pause, fichier suivant du dossier à la fin.
 */
class PlayerActivity : Activity() {
    private lateinit var player: ExoPlayer
    private lateinit var view: PlayerView
    private lateinit var resume: ResumeStore
    private lateinit var watchNext: WatchNext
    private val handler = Handler(Looper.getMainLooper())
    private var playlist: List<Uri> = emptyList()
    private var titles: Map<Uri, String> = emptyMap()

    private val saver = object : Runnable {
        override fun run() {
            saveProgress(publish = false)
            handler.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        resume = ResumeStore(this)
        watchNext = WatchNext(this)
        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        view = PlayerView(this).apply {
            player = this@PlayerActivity.player
            keepScreenOn = true
            setShowSubtitleButton(true)
            setShowNextButton(true)
            setShowPreviousButton(true)
            controllerShowTimeoutMs = 3500
        }
        setContentView(view)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    currentUri()?.let { resume.clear(it); watchNext.remove(it) }
                    finish()
                }
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                // Fichier précédent terminé : il est vu, on le retire des reprises.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val previous = playlist.getOrNull(player.currentMediaItemIndex - 1)
                    previous?.let { resume.clear(it); watchNext.remove(it) }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Lecture impossible : ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })
        open(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        saveProgress(publish = true)
        open(intent)
    }

    private fun open(intent: Intent) {
        val uri = intent.data ?: return finish()
        // Ouvert depuis Kairo Player : tout le dossier en liste de lecture ; sinon le fichier seul.
        playlist = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_PLAYLIST, Uri::class.java)?.takeIf { uri in it } ?: listOf(uri)
        titles = playlist.associateWith { titleOf(it) }
        player.setMediaItems(
            playlist.map { MediaItem.Builder().setUri(it).setMediaMetadata(MediaMetadata.Builder().setTitle(titles[it]).build()).build() },
            playlist.indexOf(uri),
            resume.get(uri)?.takeIf(::shouldResume)?.positionMs ?: 0L,
        )
        player.prepare()
        player.playWhenReady = true
        if (resume.get(uri)?.let(::shouldResume) == true) {
            Toast.makeText(this, "Reprise à ${formatDuration(resume.get(uri)!!.positionMs, music = true)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun currentUri(): Uri? = playlist.getOrNull(player.currentMediaItemIndex)

    private fun titleOf(uri: Uri): String {
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        return displayTitle(name)
    }

    private fun saveProgress(publish: Boolean) {
        val uri = currentUri() ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        val position = player.currentPosition
        resume.save(uri, position, duration)
        if (publish) {
            val progress = Progress(position, duration, System.currentTimeMillis())
            // Hors du fil principal : la vignette est générée au premier passage.
            Thread { watchNext.publish(uri, titles[uri] ?: "Vidéo", progress) }.start()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Contrôles masqués : ← → reculent / avancent directement, OK met en pause.
        if (!view.isControllerFullyVisible && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { player.seekBack(); view.showController(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { player.seekForward(); view.showController(); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (event.repeatCount == 0) player.playWhenReady = !player.playWhenReady
                    view.showController()
                    return true
                }
            }
        }
        return view.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        handler.post(saver)
    }

    override fun onStop() {
        handler.removeCallbacks(saver)
        saveProgress(publish = true)
        player.pause()
        super.onStop()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PLAYLIST = "com.vantaflow.kairo.player.PLAYLIST"
    }
}
