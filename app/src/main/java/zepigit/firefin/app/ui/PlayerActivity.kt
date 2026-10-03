package zepigit.firefin.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
import zepigit.firefin.app.playback.SourceTrack
import zepigit.firefin.app.util.PlaybackSessionTracker
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
 * pause. Natural end, a fatal error, a source change and teardown each finish
 * the playback session once; a retry or new source starts a new one.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var engine: PlaybackEngine? = null
    private val session = PlaybackSessionTracker()

    private var itemId = ""
    private var isTranscode = false
    private var isHls = false
    private var transcodeOffsetMs = 0L
    private var sourceTracks = emptyList<SourceTrack>()
    private var audioIndex: Int? = null
    private var subtitleIndex: Int? = null
    private var switching = false
    private var consumedBack = false
    private var resumed = false
    private var sourceId = ""
    private var directPlay = false
    private lateinit var playbackTransport: zepigit.firefin.app.data.ServerTransport

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) endPlayback()
        }

        override fun onPlayerError(error: PlaybackException) = showPlaybackError(error)

        override fun onEvents(player: Player, events: Player.Events) {
            // Keep the screen on only while playback is wanted, so the Fire TV
            // screensaver can start on a paused, ended or failed player.
            findViewById<PlayerView>(R.id.playerView).keepScreenOn = player.playWhenReady &&
                player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        itemId = intent.getStringExtra(EXTRA_ITEM) ?: return finish()
        val playSessionId = intent.getStringExtra(EXTRA_SESSION) ?: ""
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val startMs = intent.getLongExtra(EXTRA_START, 0L)
        isTranscode = intent.getBooleanExtra(EXTRA_TRANSCODE, false)
        isHls = intent.getBooleanExtra(EXTRA_HLS, false)
        val name = intent.getStringExtra(EXTRA_NAME) ?: "Firefin"
        title = name
        sourceId = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        val playMethod = intent.getStringExtra(EXTRA_PLAY_METHOD) ?: if (isTranscode) "Transcode" else "DirectPlay"
        directPlay = playMethod == "DirectPlay"
        audioIndex = intent.getIntExtra(EXTRA_AUDIO, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
        subtitleIndex = intent.getIntExtra(EXTRA_SUBTITLE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }

        // One immutable credential snapshot serves BOTH the media stream and
        // session reporting, so an account switch mid-playback cannot mix
        // identities. The global client remains for browsing/images only.
        playbackTransport = ServiceLocator.client.transportSnapshot()
        sourceTracks = zepigit.firefin.app.playback.StreamSelection.tracks(org.json.JSONArray(intent.getStringExtra(EXTRA_TRACKS) ?: "[]"))
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
        findViewById<android.widget.Button>(R.id.playerAudio).setOnClickListener { showTrackDialog(C.TRACK_TYPE_AUDIO, getString(R.string.track_audio)) }
        findViewById<android.widget.Button>(R.id.playerSubtitles).setOnClickListener { showTrackDialog(C.TRACK_TYPE_TEXT, getString(R.string.track_subtitles)) }
        findViewById<android.widget.Button>(R.id.playerVideo).setOnClickListener { showTrackDialog(C.TRACK_TYPE_VIDEO, getString(R.string.track_video)) }
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            findViewById<android.view.View>(R.id.playerActions).visibility = visibility
        })
        engine = PlaybackEngine(this, playbackTransport.mediaHttp).also { e ->
            playerView.player = e.player
            e.player.addListener(playerListener)
            subtitleIndex?.let { e.applySubtitleChoice(it >= 0, subtitleLanguage(sourceTracks, it)) }
            e.prepare(playbackUrl, timeline.preparePositionMs, intent.getStringExtra(EXTRA_SUBTITLE_URL), intent.getStringExtra(EXTRA_SUBTITLE_MIME))
        }

        session.start(SessionReporter(playbackTransport, itemId, playSessionId, sourceId, playMethod, intent.getStringExtra(EXTRA_LIVE_STREAM).orEmpty()))
        scope.launch {
            while (isActive) {
                delay(10_000)
                val p = engine?.player ?: break
                session.progress(Ticks.fromMs(actualPositionMs(p)), !p.playWhenReady)
            }
        }
    }

    /** Player-local position plus the transcode offset (TS streams start at 0). */
    private fun actualPositionMs(p: Player): Long =
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

    /** Natural end: report the final position once and leave the player. */
    private fun endPlayback() {
        if (switching) return
        val p = engine?.player ?: return
        session.finish(Ticks.fromMs(actualPositionMs(p)))
        finish()
    }

    /** A fatal error ends the session; the user may retry at the same position or leave. */
    private fun showPlaybackError(error: PlaybackException) {
        val p = engine?.player ?: return
        val positionMs = actualPositionMs(p)
        session.finish(Ticks.fromMs(positionMs))
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(R.string.playback_error_title)
            .setMessage(playbackErrorMessage(error))
            .setPositiveButton(R.string.retry) { _, _ -> renegotiate(audioIndex, subtitleIndex, positionMs, resumePlaying = true) }
            .setNegativeButton(R.string.back) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun playbackErrorMessage(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_TIMEOUT -> getString(R.string.playback_error_network)
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> getString(R.string.playback_error_server)
        in 3000..4999 -> getString(R.string.playback_error_format)
        else -> getString(R.string.playback_error_other, error.errorCodeName)
    }

    override fun onResume() { super.onResume(); resumed = true }

    override fun onPause() {
        resumed = false
        super.onPause()
        val p = engine?.player ?: return
        p.pause()
        session.progress(Ticks.fromMs(actualPositionMs(p)), true)
    }

    override fun onDestroy() {
        val p = engine?.player
        if (p != null) {
            val positionTicks = Ticks.fromMs(actualPositionMs(p))
            p.release()
            // No-op when the end, an error or a source change already finished the session.
            session.finish(positionTicks)
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
                        showTrackDialog(C.TRACK_TYPE_AUDIO, getString(R.string.track_audio))
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        showTrackDialog(C.TRACK_TYPE_TEXT, getString(R.string.track_subtitles))
                        return true
                    }
                }
            }
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val player = engine?.player
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU -> {
                    playerView.showController()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    // A held key on a TS transcode would restart the server transcode on every repeat.
                    if (event.repeatCount > 0 && isTranscode && !isHls) return true
                    val forward = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                    seekTo((player?.currentPosition ?: 0L) + if (forward) SEEK_MS else -SEEK_MS)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Track selection. Direct play exposes every embedded audio track to Media3,
     * so audio switches locally there. Subtitles always come from the server's
     * list: external files are only delivered after a new negotiation.
     */
    private fun showTrackDialog(trackType: Int, title: String) {
        val player = engine?.player ?: return
        val subtitles = trackType == C.TRACK_TYPE_TEXT
        val available = sourceTracks.filter { it.type == if (subtitles) "Subtitle" else "Audio" }
        if (trackType == C.TRACK_TYPE_VIDEO || available.isEmpty() || (directPlay && !subtitles)) {
            androidx.media3.ui.TrackSelectionDialogBuilder(this, title, player, trackType).build().show()
            return
        }
        val labels = (if (subtitles) listOf(getString(R.string.subtitles_off)) else emptyList()) + available.map { it.title }
        val current = if (subtitles) subtitleIndex else audioIndex
        val checked = when {
            current == null -> -1
            subtitles && current == -1 -> 0
            else -> available.indexOfFirst { it.index == current }.let { if (it < 0) -1 else it + if (subtitles) 1 else 0 }
        }
        AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, chosen ->
            dialog.dismiss()
            val index = if (subtitles && chosen == 0) -1 else available[chosen - if (subtitles) 1 else 0].index
            val p = engine?.player ?: return@setSingleChoiceItems
            if (index == current) return@setSingleChoiceItems
            if (subtitles) renegotiate(audioIndex, index, actualPositionMs(p), p.playWhenReady)
            else renegotiate(index, subtitleIndex, actualPositionMs(p), p.playWhenReady)
        }.show()
    }

    /**
     * Finishes the current attempt and negotiates a new source at the same
     * absolute position with this playback's own credentials. Serves server-side
     * track changes and retry after a fatal error; the new source gets a new session.
     */
    private fun renegotiate(audio: Int?, subtitle: Int?, positionMs: Long, resumePlaying: Boolean) {
        if (switching) return
        val player = engine?.player ?: return
        val credentials = playbackTransport.credentials()
        fun sameAccount() = credentials.userId == ServiceLocator.session.userId && credentials.serverUrl == ServiceLocator.session.serverUrl && credentials.token == ServiceLocator.session.accessToken
        if (!sameAccount()) { finish(); return }
        switching = true
        player.pause()
        player.stop()
        player.clearMediaItems()
        val previous = session.finish(Ticks.fromMs(positionMs))
        scope.launch {
            try {
                // Let the old session's stop and transcode cleanup reach the server first.
                previous?.awaitTerminal()
                if (!sameAccount()) { finish(); return@launch }
                val source = ServiceLocator.client.playbackInfo(itemId, audio, subtitle, playbackTransport, sourceId)
                if (!sameAccount() || isFinishing || isDestroyed) {
                    SessionReporter(playbackTransport, itemId, source.playSessionId, source.mediaSourceId, source.playMethod, source.liveStreamId).stopped(Ticks.fromMs(positionMs))
                    if (!isFinishing && !isDestroyed) finish()
                    return@launch
                }
                sourceId = source.mediaSourceId
                directPlay = source.playMethod == "DirectPlay"
                audioIndex = source.audioIndex; subtitleIndex = source.subtitleIndex
                isTranscode = source.isTranscode; isHls = source.isHls
                sourceTracks = source.tracks
                val timeline = PlaybackTimeline.start(isTranscode, isHls, positionMs)
                transcodeOffsetMs = timeline.offsetMs
                val url = if (isTranscode && !isHls) Urls.withStartTimeTicks(source.url, Ticks.fromMs(positionMs)) else source.url
                val chosenSubtitle = subtitleIndex ?: -1
                engine?.applySubtitleChoice(chosenSubtitle >= 0, subtitleLanguage(sourceTracks, chosenSubtitle))
                engine?.prepare(url, timeline.preparePositionMs, source.subtitleUrl, source.subtitleMime)
                player.playWhenReady = resumePlaying && resumed
                session.start(SessionReporter(playbackTransport, itemId, source.playSessionId, source.mediaSourceId, source.playMethod, source.liveStreamId))
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Toast.makeText(this@PlayerActivity, R.string.track_switch_aborted, Toast.LENGTH_LONG).show()
                finish()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                Toast.makeText(this@PlayerActivity, errorMessage(e, R.string.track_switch_failed), Toast.LENGTH_LONG).show()
                finish()
            }
            finally { switching = false }
        }
    }

    private fun subtitleLanguage(tracks: List<SourceTrack>, index: Int): String? =
        tracks.firstOrNull { it.type == "Subtitle" && it.index == index }?.language

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
        private const val EXTRA_SUBTITLE_URL = "p.subtitleUrl"
        private const val EXTRA_SUBTITLE_MIME = "p.subtitleMime"
        private const val EXTRA_PLAY_METHOD = "p.playMethod"
        private const val EXTRA_AUDIO = "p.audioIndex"
        private const val EXTRA_SUBTITLE = "p.subtitleIndex"
        private const val EXTRA_TRACKS = "p.tracks"

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
            tracks: List<SourceTrack> = emptyList(),
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
                    .putExtra(EXTRA_SUBTITLE_URL, subtitleUrl).putExtra(EXTRA_SUBTITLE_MIME, subtitleMime)
                    .putExtra(EXTRA_PLAY_METHOD, playMethod)
                    .putExtra(EXTRA_AUDIO, audioIndex ?: Int.MIN_VALUE).putExtra(EXTRA_SUBTITLE, subtitleIndex ?: Int.MIN_VALUE)
                    .putExtra(EXTRA_TRACKS, org.json.JSONArray(tracks.map { track -> org.json.JSONObject().put("Index", track.index).put("Type", track.type).put("Language", track.language).put("DisplayTitle", track.title) }).toString()),
            )
        }
    }
}
