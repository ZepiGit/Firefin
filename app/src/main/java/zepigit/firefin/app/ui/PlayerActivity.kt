package zepigit.firefin.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.playback.PlaybackEngine
import zepigit.firefin.app.playback.PlaybackTimeline
import zepigit.firefin.app.util.SessionReporter
import zepigit.firefin.app.util.Ticks
import zepigit.firefin.app.util.Urls

/**
 * Fullscreen player rendered by media3 PlayerView (SurfaceView-based).
 *
 * Deterministic D-Pad contract (legacy FireTV32 parity): while the controller
 * is hidden, LEFT/RIGHT seek directly and CENTER/UP/DOWN reveal the
 * controller; while it is visible, keys go to the controller (focus
 * navigation, CENTER clicks the focused control) and MENU toggles it. Center
 * = play/pause, left/right = seek. Progress is reported every 10 s and on
 * pause; stop position is reported best-effort after release. No auto retries.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var engine: PlaybackEngine? = null
    private var reporting = true

    private var itemId = ""
    private var playSessionId = ""
    private var isTranscode = false
    private var isHls = false
    private var transcodeOffsetMs = 0L
    private var reporter: SessionReporter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        itemId = intent.getStringExtra(EXTRA_ITEM) ?: return finish()
        playSessionId = intent.getStringExtra(EXTRA_SESSION) ?: ""
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val startMs = intent.getLongExtra(EXTRA_START, 0L)
        isTranscode = intent.getBooleanExtra(EXTRA_TRANSCODE, false)
        isHls = intent.getBooleanExtra(EXTRA_HLS, false)
        val name = intent.getStringExtra(EXTRA_NAME) ?: "Firefin"
        title = name

        // One immutable credential snapshot serves BOTH the media stream and
        // session reporting, so an account switch mid-playback cannot mix
        // identities. The global client remains for browsing/images only.
        val playbackTransport = ServiceLocator.client.transportSnapshot()
        val timeline = PlaybackTimeline.start(isTranscode, isHls, startMs)
        transcodeOffsetMs = timeline.offsetMs
        val playbackUrl = if (isTranscode && !isHls) {
            // The restarted TS stream begins at the server-side offset.
            Urls.withStartTimeTicks(url, Ticks.fromMs(startMs))
        } else {
            url
        }
        val playerView = findViewById<PlayerView>(R.id.playerView)
        engine = PlaybackEngine(this, playbackTransport.mediaHttp).also { e ->
            playerView.player = e.player
            e.prepare(playbackUrl, timeline.preparePositionMs)
        }
        engine?.player?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Toast.makeText(this@PlayerActivity, error.errorCodeName, Toast.LENGTH_LONG).show()
            }
        })

        reporter = SessionReporter(
            playbackTransport,
            itemId,
            playSessionId,
            intent.getStringExtra(EXTRA_SOURCE).orEmpty(),
            if (isTranscode) "Transcode" else "DirectPlay",
            intent.getStringExtra(EXTRA_LIVE_STREAM).orEmpty(),
        )
        reporter?.playing()
        scope.launch {
            while (isActive && reporting) {
                delay(10_000)
                val p = engine?.player ?: break
                reporter?.progress(Ticks.fromMs(actualPositionMs(p)), !p.playWhenReady)
            }
        }
    }

    /** Player-local position plus the transcode offset (TS streams start at 0). */
    private fun actualPositionMs(p: androidx.media3.common.Player): Long =
        p.currentPosition + transcodeOffsetMs

    private fun seekTo(targetPlayerMs: Long) {
        val player = engine?.player ?: return
        if (!isTranscode || isHls) {
            player.seekTo(targetPlayerMs.coerceAtLeast(0))
            return
        }
        // TS-over-HTTP transcodes are a live pipe: restart the transcode at the
        // target offset instead of seeking inside the stream. The new stream
        // starts at 0, so the offset is rebased to the target position.
        val targetAbsolute = (transcodeOffsetMs + targetPlayerMs).coerceAtLeast(0)
        val url = engine?.currentUrl ?: return
        val rebuilt = Urls.withStartTimeTicks(url, Ticks.fromMs(targetAbsolute))
        transcodeOffsetMs = targetAbsolute
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(rebuilt), 0L)
        player.prepare()
        player.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        val p = engine?.player ?: return
        p.pause()
        reporter?.progress(Ticks.fromMs(actualPositionMs(p)), true)
    }

    override fun onDestroy() {
        reporting = false
        val p = engine?.player
        if (p != null) {
            val positionTicks = Ticks.fromMs(actualPositionMs(p))
            p.release()
            reporter?.stopped(positionTicks)
        }
        engine = null
        // The reporter owns its IO queue and drains terminal Stopped after the
        // Activity scope is cancelled. Do not cancel it immediately here.
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Key routing happens in dispatchKeyEvent so the PlayerView cannot consume
     * keys first while the controller is hidden. Only keys we actually handle
     * are consumed; everything else (notably BACK) reaches the framework.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val playerView = findViewById<PlayerView>(R.id.playerView)
        val controllerVisible = playerView.isControllerFullyVisible
        val keyCode = event.keyCode
        if (controllerVisible) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_MENU -> {
                        playerView.hideController()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        showTrackDialog(C.TRACK_TYPE_AUDIO, "Tonspur")
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        showTrackDialog(C.TRACK_TYPE_TEXT, "Untertitel")
                        return true
                    }
                }
            }
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val player = engine?.player
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (player != null) { if (player.isPlaying) player.pause() else player.play() }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU -> {
                playerView.showController()
                return true
            }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    seekTo((player?.currentPosition ?: 0L) - SEEK_MS)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    seekTo((player?.currentPosition ?: 0L) + SEEK_MS)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Deterministic track selection dialog (audio/subtitle) driven by ExoPlayer track state. */
    private fun showTrackDialog(trackType: Int, title: String) {
        val player = engine?.player ?: return
        androidx.media3.ui.TrackSelectionDialogBuilder(this, title, player, trackType).build().show()
    }

    companion object {
    private const val SEEK_MS = 10_000L
    private const val EXTRA_ITEM = "p.item"
    private const val EXTRA_URL = "p.url"
    private const val EXTRA_SESSION = "p.session"
    private const val EXTRA_SOURCE = "p.source"
    private const val EXTRA_START = "p.start"
    private const val EXTRA_NAME = "p.name"
        private const val EXTRA_TRANSCODE = "p.transcode"
        private const val EXTRA_HLS = "p.hls"
        private const val EXTRA_LIVE_STREAM = "p.livestream"

    fun start(
        context: Context,
        itemId: String,
        url: String,
        playSessionId: String,
        mediaSourceId: String,
        startMs: Long,
        name: String,
        isTranscode: Boolean = false,
        isHls: Boolean = false,
        liveStreamId: String = "",
    ) {
        context.startActivity(
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_ITEM, itemId)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_SESSION, playSessionId)
                .putExtra(EXTRA_SOURCE, mediaSourceId)
                .putExtra(EXTRA_START, startMs)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_TRANSCODE, isTranscode)
                .putExtra(EXTRA_HLS, isHls)
                .putExtra(EXTRA_LIVE_STREAM, liveStreamId),
        )
    }
    }
}
