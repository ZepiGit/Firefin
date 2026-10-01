package zepigit.firefin.app

import org.junit.Assert.assertEquals
import org.junit.Test
import zepigit.firefin.app.playback.PlaybackTimeline

/** Contract for the single playback timeline: no double resume/offset application. */
class PlaybackTimelineTest {

    @Test
    fun `hls and direct play apply resume inside media3 with zero offset`() {
        for (transcode in listOf(false, true)) {
            val start = PlaybackTimeline.start(isTranscode = transcode, isHls = true, startMs = 60_000)
            assertEquals(60_000, start.preparePositionMs)
            assertEquals(0, start.offsetMs)
        }
    }

    @Test
    fun `non-hls transcode restarts at server offset with local zero start`() {
        val start = PlaybackTimeline.start(isTranscode = true, isHls = false, startMs = 60_000)
        assertEquals(0, start.preparePositionMs)
        assertEquals(60_000, start.offsetMs)
    }

    @Test
    fun `first reported position equals resume point exactly once`() {
        val start = PlaybackTimeline.start(isTranscode = true, isHls = false, startMs = 60_000)
        // Stream restarts at the offset; player-local position is ~0 there.
        assertEquals(60_000, PlaybackTimeline.absolute(0, start.offsetMs))
        // HLS applies resume locally; player-local position already is 60 s.
        val hls = PlaybackTimeline.start(isTranscode = true, isHls = true, startMs = 60_000)
        assertEquals(60_000, PlaybackTimeline.absolute(60_000, hls.offsetMs))
    }

    @Test
    fun `seek stays inside duration and never below zero`() {
        assertEquals(50_000, PlaybackTimeline.targetAbsolute(currentAbsoluteMs = 60_000, seekDeltaMs = -10_000, durationMs = 0))
        assertEquals(0, PlaybackTimeline.targetAbsolute(currentAbsoluteMs = 5_000, seekDeltaMs = -60_000, durationMs = 0))
        assertEquals(300_000, PlaybackTimeline.targetAbsolute(currentAbsoluteMs = 299_000, seekDeltaMs = 10_000, durationMs = 300_000))
    }
}
