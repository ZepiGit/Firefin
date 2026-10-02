package zepigit.firefin.app.playback

import org.json.JSONArray

data class SourceTrack(val index: Int, val type: String, val language: String, val title: String)

object StreamSelection {
    fun tracks(streams: JSONArray): List<SourceTrack> = (0 until streams.length()).mapNotNull { index ->
        val stream = streams.optJSONObject(index) ?: return@mapNotNull null
        val type = stream.optString("Type")
        if (type !in setOf("Audio", "Subtitle")) return@mapNotNull null
        SourceTrack(stream.optInt("Index", index), type, stream.optString("Language"),
            stream.optString("DisplayTitle").ifBlank { stream.optString("Title").ifBlank { "$type ${stream.optInt("Index", index)} · ${stream.optString("Language")}" } })
    }
    fun index(tracks: List<SourceTrack>, type: String, language: String): Int? {
        if (language.isBlank()) return if (type == "Subtitle") -1 else null
        val aliases = when (language.lowercase()) { "de", "deu", "ger" -> setOf("de", "deu", "ger"); "en", "eng" -> setOf("en", "eng"); else -> setOf(language.lowercase()) }
        return tracks.firstOrNull { it.type == type && it.language.lowercase() in aliases }?.index
    }
}
