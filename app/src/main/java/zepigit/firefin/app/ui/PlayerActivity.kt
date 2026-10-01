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
import zepigit.firefin.app.util.Ticks
import zepigit.firefin.app.util.SessionReporter

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
class PlayerActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var engine: PlaybackEngine? = null
    private var reporting = true

    private var itemId = ""
    private var playSessionId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        itemId = intent.getStringExtra(EXTRA_ITEM) ?: return finish()
        playSessionId = intent.getStringExtra(EXTRA_SESSION) ?: ""
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val startMs = intent.getLongExtra(EXTRA_START, 0L)
        val name = intent.getStringExtra(EXTRA_NAME) ?: "Firefin"
        title = name

        val playerView = findViewById<PlayerView>(R.id.playerView)
        engine = PlaybackEngine(this, ServiceLocator.client.okHttp).also { e ->
            playerView.player = e.player
            e.prepare(url, startMs)
        }
        engine?.player?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Toast.makeText(this@PlayerActivity, error.errorCodeName, Toast.LENGTH_LONG).show()
            }
        })

        scope.launch {
            runCatching { ServiceLocator.client.reportPlaying(itemId, playSessionId) }
            while (isActive && reporting) {
                delay(10_000)
                val p = engine?.player ?: break
                runCatching {
                    ServiceLocator.client.reportProgress(
                        itemId,
                        playSessionId,
                        Ticks.fromMs(p.currentPosition),
                        !p.isPlaying,
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val p = engine?.player ?: return
        p.pause()
        scope.launch {
            runCatching {
                ServiceLocator.client.reportProgress(
                    itemId,
                    playSessionId,
                    Ticks.fromMs(p.currentPosition),
                    true,
                )
            }
        }
    }

    override fun onDestroy() {
        reporting = false
        val p = engine?.player
        if (p != null) {
            val positionTicks = Ticks.fromMs(p.currentPosition)
            p.release()
            SessionReporter.reportStopped(ServiceLocator.client, itemId, playSessionId, positionTicks)
        }
        engine = null
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Key routing happens in dispatchKeyEvent so the PlayerView cannot consume
     * keys first while the controller is hidden.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val playerView = findViewById<PlayerView>(R.id.playerView)
        val controllerVisible = playerView.isControllerFullyVisible
        if (controllerVisible) {
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_MENU) {
                playerView.hideController()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val player = engine?.player
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            -> {
                playerView.showController()
                true
            }
            KeyEvent.KEYCODE_MENU -> {
                playerView.showController()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                player?.seekTo((player.currentPosition - SEEK_MS).coerceAtLeast(0))
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                player?.seekTo(player.currentPosition + SEEK_MS)
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
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

        fun start(
            context: Context,
            itemId: String,
            url: String,
            playSessionId: String,
            mediaSourceId: String,
            startMs: Long,
            name: String,
        ) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_ITEM, itemId)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_SESSION, playSessionId)
                    .putExtra(EXTRA_SOURCE, mediaSourceId)
                    .putExtra(EXTRA_START, startMs)
                    .putExtra(EXTRA_NAME, name),
            )
        }
    }
}
