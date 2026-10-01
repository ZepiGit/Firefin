package zepigit.firefin.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import zepigit.firefin.app.R
import zepigit.firefin.app.ServiceLocator
import zepigit.firefin.app.playback.PlaybackEngine
import zepigit.firefin.app.util.Ticks

/**
 * Fullscreen player rendered by media3 PlayerView (SurfaceView-based). D-Pad:
 * center = play/pause, left/right = seek, menu = controller. Progress reported
 * every 10 s, on pause and on stop (session cleanup); no automatic retries.
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
            // Report the stop position before release so the session is closed
            // with the final position instead of being abandoned.
            runCatching {
                kotlinx.coroutines.runBlocking {
                    ServiceLocator.client.reportStopped(
                        itemId,
                        playSessionId,
                        Ticks.fromMs(p.currentPosition),
                    )
                }
            }
            p.release()
        }
        engine = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val player = engine?.player ?: return super.onKeyDown(keyCode, event)
        findViewById<PlayerView>(R.id.playerView).showController()
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                player.seekTo((player.currentPosition - SEEK_MS).coerceAtLeast(0))
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                player.seekTo(player.currentPosition + SEEK_MS)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
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
