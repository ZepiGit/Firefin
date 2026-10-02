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
    private var sourceTracks = emptyList<zepigit.firefin.app.playback.SourceTrack>()
    private var audioIndex: Int? = null
    private var subtitleIndex: Int? = null
    private var switching = false
    private var consumedBack = false
    private var resumed = false
    private var sourceId = ""
    private var directPlay = false
    private lateinit var playbackTransport: zepigit.firefin.app.data.ServerTransport

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
        sourceId = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        directPlay = intent.getStringExtra("p.playMethod") == "DirectPlay"
        audioIndex = intent.getIntExtra("p.audioIndex", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
        subtitleIndex = intent.getIntExtra("p.subtitleIndex", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }

        // One immutable credential snapshot serves BOTH the media stream and
        // session reporting, so an account switch mid-playback cannot mix
        // identities. The global client remains for browsing/images only.
        playbackTransport = ServiceLocator.client.transportSnapshot()
        sourceTracks = zepigit.firefin.app.playback.StreamSelection.tracks(org.json.JSONArray(intent.getStringExtra("p.tracks") ?: "[]"))
        val timeline = PlaybackTimeline.start(isTranscode, isHls, startMs)
        transcodeOffsetMs = timeline.offsetMs
        val playbackUrl = if (isTranscode && !isHls) {
            // The restarted TS stream begins at the server-side offset.
            Urls.withStartTimeTicks(url, Ticks.fromMs(startMs))
        } else {
            url
        }
        val playerView = findViewById<PlayerView>(R.id.playerView)
        findViewById<android.widget.TextView>(R.id.playerTitle).text = name
        findViewById<android.widget.Button>(R.id.playerAudio).setOnClickListener { showTrackDialog(C.TRACK_TYPE_AUDIO, "Tonspur") }
        findViewById<android.widget.Button>(R.id.playerSubtitles).setOnClickListener { showTrackDialog(C.TRACK_TYPE_TEXT, "Untertitel") }
        findViewById<android.widget.Button>(R.id.playerVideo).setOnClickListener { showTrackDialog(C.TRACK_TYPE_VIDEO, "Video") }
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            findViewById<android.view.View>(R.id.playerActions).visibility = visibility
        })
        engine = PlaybackEngine(this, playbackTransport.mediaHttp).also { e ->
            playerView.player = e.player
            e.prepare(playbackUrl, timeline.preparePositionMs, intent.getStringExtra("p.subtitleUrl"), intent.getStringExtra("p.subtitleMime"))
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
            intent.getStringExtra("p.playMethod") ?: if (isTranscode) "Transcode" else "DirectPlay",
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
        PlaybackTimeline.absolute(p.currentPosition, transcodeOffsetMs)

    private fun seekTo(targetPlayerMs: Long) {
        val player = engine?.player ?: return
        if (!isTranscode || isHls) {
            player.seekTo(targetPlayerMs.coerceAtLeast(0))
            return
        }
        // TS-over-HTTP transcodes are a live pipe: restart the transcode at the
        // target offset instead of seeking inside the stream. The new stream
        // starts at 0, so the offset is rebased to the target position.
        val targetAbsolute = PlaybackTimeline.targetAbsolute(
            currentAbsoluteMs = transcodeOffsetMs + targetPlayerMs,
            seekDeltaMs = 0,
            durationMs = if (player.duration > 0) player.duration + transcodeOffsetMs else 0,
        )
        val url = engine?.currentUrl ?: return
        val rebuilt = Urls.withStartTimeTicks(url, Ticks.fromMs(targetAbsolute))
        transcodeOffsetMs = targetAbsolute
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(rebuilt), 0L)
        player.prepare()
        player.playWhenReady = true
    }

    override fun onResume() { super.onResume(); resumed = true }

    override fun onPause() {
        resumed = false
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
        if (switching && keyCode != KeyEvent.KEYCODE_BACK) return true
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && consumedBack) {
            consumedBack = false
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && event.action == KeyEvent.ACTION_DOWN) {
            engine?.player?.let { if (it.playWhenReady) it.pause() else it.play() }
            return true
        }
        if (controllerVisible) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_MENU -> {
                        playerView.hideController()
                        return true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        consumedBack = true
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
        val type = if (trackType == C.TRACK_TYPE_AUDIO) "Audio" else "Subtitle"
        val available = sourceTracks.filter { it.type == type }
        if (directPlay || trackType == C.TRACK_TYPE_VIDEO || available.isEmpty()) {
            androidx.media3.ui.TrackSelectionDialogBuilder(this, title, player, trackType).build().show()
            return
        }
        val labels = (if (type == "Subtitle") listOf("Aus") else emptyList()) + available.map { it.title }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setItems(labels.toTypedArray()) { _, chosen ->
            val index = if (type == "Subtitle" && chosen == 0) -1 else available[chosen - if (type == "Subtitle") 1 else 0].index
            switchSourceTrack(type, index)
        }.show()
    }

    private fun switchSourceTrack(type: String, index: Int) {
        if (switching) return
        val player = engine?.player ?: return
        val credentials = playbackTransport.credentials()
        fun sameAccount() = credentials.userId == ServiceLocator.session.userId && credentials.serverUrl == ServiceLocator.session.serverUrl && credentials.token == ServiceLocator.session.accessToken
        if (!sameAccount()) return
        switching = true
        val position = actualPositionMs(player)
        val wasPlaying = player.playWhenReady
        player.pause()
        player.stop()
        player.clearMediaItems()
        if (type == "Audio") audioIndex = index else subtitleIndex = index
        scope.launch {
            try {
                reporter?.stopped(Ticks.fromMs(position))
                reporter?.awaitTerminal()
                reporter = null
                if (!sameAccount()) { finish(); return@launch }
                val source = ServiceLocator.client.playbackInfo(itemId, audioIndex, subtitleIndex, playbackTransport, sourceId)
                if (!sameAccount() || isFinishing || isDestroyed) {
                    SessionReporter(playbackTransport, itemId, source.playSessionId, source.mediaSourceId, source.playMethod, source.liveStreamId).stopped(Ticks.fromMs(position))
                    if (!isFinishing && !isDestroyed) finish()
                    return@launch
                }
                sourceId = source.mediaSourceId
                directPlay = source.playMethod == "DirectPlay"
                audioIndex = source.audioIndex; subtitleIndex = source.subtitleIndex
                playSessionId = source.playSessionId
                isTranscode = source.isTranscode; isHls = source.isHls
                sourceTracks = source.tracks
                val timeline = PlaybackTimeline.start(isTranscode, isHls, position)
                transcodeOffsetMs = timeline.offsetMs
                val url = if (isTranscode && !isHls) Urls.withStartTimeTicks(source.url, Ticks.fromMs(position)) else source.url
                engine?.prepare(url, timeline.preparePositionMs, source.subtitleUrl, source.subtitleMime)
                player.playWhenReady = wasPlaying && resumed
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitleIndex == -1).build()
                reporter = SessionReporter(playbackTransport, itemId, playSessionId, source.mediaSourceId, source.playMethod, source.liveStreamId)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Toast.makeText(this@PlayerActivity, "Spurwechsel abgebrochen: Sitzung konnte nicht beendet werden.", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                Toast.makeText(this@PlayerActivity, e.message ?: "Spurwechsel fehlgeschlagen", Toast.LENGTH_LONG).show()
                finish()
            }
            finally { switching = false }
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
        subtitleUrl: String? = null,
        subtitleMime: String? = null,
        playMethod: String = if (isTranscode) "Transcode" else "DirectPlay",
        tracks: List<zepigit.firefin.app.playback.SourceTrack> = emptyList(),
        audioIndex: Int? = null,
        subtitleIndex: Int? = null,
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
                .putExtra(EXTRA_LIVE_STREAM, liveStreamId)
                .putExtra("p.subtitleUrl", subtitleUrl).putExtra("p.subtitleMime", subtitleMime)
                .putExtra("p.playMethod", playMethod)
                .putExtra("p.audioIndex", audioIndex ?: Int.MIN_VALUE).putExtra("p.subtitleIndex", subtitleIndex ?: Int.MIN_VALUE)
                .putExtra("p.tracks", org.json.JSONArray(tracks.map { track -> org.json.JSONObject().put("Index", track.index).put("Type", track.type).put("Language", track.language).put("DisplayTitle", track.title) }).toString()),
        )
    }
    }
}
