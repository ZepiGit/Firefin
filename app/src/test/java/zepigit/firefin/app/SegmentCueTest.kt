package zepigit.firefin.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import zepigit.firefin.app.playback.SegmentCue
import zepigit.firefin.app.playback.PlaybackTimeline

class SegmentCueTest {
    @Test fun `invalid unsupported and zero duration cues are ignored with end exclusive`() {
        val cues = SegmentCue.parse(JSONObject("""{"Items":[
            {"Type":"Intro","StartTicks":10000000,"EndTicks":50000000},
            {"Type":"Outro","StartTicks":0,"EndTicks":50000000},
            {"Type":"Recap","StartTicks":500,"EndTicks":500},
            {"Type":"Commercial","StartTicks":-1,"EndTicks":50000000},
            {"Type":"Preview","StartTicks":50000000,"EndTicks":100000000}]}"""))
        assertEquals(2, cues.size)
        assertNull(SegmentCue.active(cues, 999, emptySet()))
        assertEquals("Intro", SegmentCue.active(cues, 1000, emptySet())?.type)
        assertEquals("Preview", SegmentCue.active(cues, 5000, emptySet())?.type)
        assertNull(SegmentCue.active(cues, 1000, setOf(1000L)))
        assertNull(SegmentCue.active(cues, 10000, emptySet()))
    }

    @Test fun `absolute segment seek applies the TS offset only once including backwards seeks`() {
        for ((target, offset) in listOf(90000L to 60000L, 10000L to 60000L, 90000L to 0L)) {
            assertEquals(target, PlaybackTimeline.absolute(PlaybackTimeline.relative(target, offset), offset))
        }
    }
}
