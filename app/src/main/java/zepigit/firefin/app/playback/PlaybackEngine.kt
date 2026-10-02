package zepigit.firefin.app.playback

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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
        }

    var currentUrl: String? = null
        private set

    fun prepare(url: String, startMs: Long, subtitleUrl: String? = null, subtitleMime: String? = null) {
        currentUrl = url
        val isHls = url.contains(".m3u8") || url.contains("/hls/")
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else null)
            .apply {
                if (subtitleUrl != null && subtitleMime != null) setSubtitleConfigurations(listOf(
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUrl))
                        .setMimeType(subtitleMime).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build(),
                ))
            }.build()
        player.setMediaItem(mediaItem, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun attach(surface: SurfaceView) {
        player.setVideoSurfaceView(surface)
    }

    fun release() {
        player.release()
    }
}
