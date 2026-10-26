package zepigit.firefin.app.playback

import org.json.JSONObject
import zepigit.firefin.app.util.Ticks

data class MediaSegment(val type: String, val startMs: Long, val endMs: Long)

object SegmentCue {
    private val supported = setOf("Intro", "Recap", "Preview", "Commercial")

    fun parse(json: JSONObject): List<MediaSegment> {
        val items = json.optJSONArray("Items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val segment = items.optJSONObject(index) ?: return@mapNotNull null
            val type = segment.optString("Type")
            val start = segment.optLong("StartTicks", -1)
            val end = segment.optLong("EndTicks", -1)
            if (type !in supported || start < 0 || end <= start) return@mapNotNull null
            val cue = MediaSegment(type, Ticks.toMs(start), Ticks.toMs(end))
            cue.takeIf { it.endMs > it.startMs }
        }.sortedBy { it.startMs }
    }

    fun active(segments: List<MediaSegment>, absoluteMs: Long, dismissed: Set<Long>): MediaSegment? =
        segments.firstOrNull { absoluteMs >= it.startMs && absoluteMs < it.endMs && it.startMs !in dismissed }
}
