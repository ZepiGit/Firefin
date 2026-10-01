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
        }

    var currentUrl: String? = null
        private set

    fun prepare(url: String, startMs: Long) {
        currentUrl = url
        val isHls = url.contains(".m3u8") || url.contains("/hls/")
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else null)
            .build()
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
