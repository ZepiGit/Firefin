package zepigit.firefin.app.playback

/**
 * Single source of truth for the playback timeline contract.
 *
 * HLS VOD and direct play carry the full media timeline: Media3 applies the
 * resume position itself and the reported offset stays zero. A restarted
 * non-HLS transcode is a stream that begins at the requested StartTimeTicks:
 * Media3 must start at zero and the server-side offset is tracked separately,
 * exactly once, for reporting and seeks.
 */
object PlaybackTimeline {
    data class Start(val preparePositionMs: Long, val offsetMs: Long)

    fun start(isTranscode: Boolean, isHls: Boolean, startMs: Long): Start = when {
        isTranscode && !isHls -> Start(preparePositionMs = 0L, offsetMs = startMs)
        else -> Start(preparePositionMs = startMs, offsetMs = 0L)
    }

    fun absolute(playerPositionMs: Long, offsetMs: Long): Long = playerPositionMs + offsetMs

    fun targetAbsolute(currentAbsoluteMs: Long, seekDeltaMs: Long, durationMs: Long): Long =
        (currentAbsoluteMs + seekDeltaMs).coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
}
