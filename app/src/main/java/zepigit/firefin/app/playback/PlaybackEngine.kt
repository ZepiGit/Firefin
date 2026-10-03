package zepigit.firefin.app.playback

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource

/**
 * PlaybackEngine wraps a single Media3 ExoPlayer instance on a SurfaceView.
 * Audio focus and noisy-pair handling are delegated to ExoPlayer so there is
 * exactly one owner of focus per playback session.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackEngine(context: Context, okHttpClient: okhttp3.OkHttpClient) {

    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context).setEnableDecoderFallback(true),
    )
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(OkHttpDataSource.Factory(okHttpClient)),
        )
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            setHandleAudioBecomingNoisy(true)
            val prefs = zepigit.firefin.app.ServiceLocator.preferences.effective()
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSize(prefs.maxVideoWidth, prefs.maxVideoHeight)
                .setMaxVideoBitrate((prefs.maxStreamingBitrate - 192_000).coerceAtLeast(128_000).toInt())
                .setPreferredAudioLanguage(prefs.stored.audioLanguage.takeIf { it.isNotBlank() })
                .setPreferredTextLanguage(prefs.stored.subtitleLanguage.takeIf { it.isNotBlank() })
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, prefs.stored.subtitleLanguage.isBlank()).build()
            addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) = selectExternalSubtitle(tracks)
            })
        }

    /** True until the sideloaded subtitle of the current item has been selected explicitly. */
    private var externalSubtitlePending = false

    var currentUrl: String? = null
        private set

    /**
     * Applies a negotiated subtitle choice: text on or off, preferring [language].
     * Call before [prepare]; the sideloaded file is then pinned once its track appears.
     */
    fun applySubtitleChoice(enabled: Boolean, language: String?) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .setPreferredTextLanguage(language?.takeIf { it.isNotBlank() })
            .build()
    }

    fun prepare(url: String, startMs: Long, subtitleUrl: String? = null, subtitleMime: String? = null) {
        currentUrl = url
        externalSubtitlePending = subtitleUrl != null && subtitleMime != null
        val isHls = url.contains(".m3u8") || url.contains("/hls/")
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else null)
            .apply {
                if (subtitleUrl != null && subtitleMime != null) setSubtitleConfigurations(listOf(
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUrl))
                        .setId(EXTERNAL_SUBTITLE_ID).setMimeType(subtitleMime).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build(),
                ))
            }.build()
        player.setMediaItem(mediaItem, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * A direct-played file can carry embedded subtitles in the same language;
     * the user picked the server-delivered file, so select that track by its id.
     */
    private fun selectExternalSubtitle(tracks: Tracks) {
        if (!externalSubtitlePending) return
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (index in 0 until group.length) {
                if (group.getTrackFormat(index).id?.endsWith(EXTERNAL_SUBTITLE_ID) != true) continue
                externalSubtitlePending = false
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                    .build()
                return
            }
        }
    }

    fun attach(surface: SurfaceView) {
        player.setVideoSurfaceView(surface)
    }

    fun release() {
        player.release()
    }

    private companion object {
        const val EXTERNAL_SUBTITLE_ID = "firefin-external-subtitle"
    }
}
