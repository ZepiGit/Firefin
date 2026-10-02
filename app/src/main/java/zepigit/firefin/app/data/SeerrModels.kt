package zepigit.firefin.app.data

import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.array(name: String): List<JSONObject> = optJSONArray(name)?.let { a ->
    (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}.orEmpty()

data class SeerrUser(val id: Int, val permissions: Long) {
    fun canRequest(type: String): Boolean = id == 1 || permissions and 2L != 0L ||
        permissions and 32L != 0L || permissions and (if (type == "movie") 262144L else 524288L) != 0L
}

data class SeerrSeason(val number: Int, val name: String, val episodes: Int, val status: Int)

data class SeerrMedia(
    val id: Int, val type: String, val title: String, val overview: String,
    val poster: String?, val backdrop: String?, val year: Int, val status: Int,
    val seasons: List<SeerrSeason> = emptyList(),
    val pendingSeasons: Set<Int> = emptySet(),
) {
    val key: String get() = "$type:$id"
    val statusLabel: String get() = when (status) {
        2 -> "Angefragt"
        3 -> "In Bearbeitung"
        4 -> "Teilweise verfügbar"
        5 -> "Verfügbar"
        6 -> "Gesperrt"
        7 -> "Gelöscht"
        else -> "Noch nicht verfügbar"
    }
    fun card(): MediaItem = MediaItem(
        key, title, if (type == "movie") "Movie" else "Series", year, overview,
        poster, backdrop, null, null, "", 0, 0, 0, 0, status == 5, false, "",
    )
    val eligibleSeasons: List<SeerrSeason> get() = if (status == 6) emptyList() else seasons.filter {
        it.number > 0 && it.episodes > 0 && it.status !in setOf(2, 3, 4, 5, 6) && it.number !in pendingSeasons
    }
    val requestable: Boolean get() = if (type == "movie") status !in setOf(2, 3, 4, 5, 6) else eligibleSeasons.isNotEmpty()
    fun requestBody(selectedSeasons: List<Int>): JSONObject {
        require(id > 0 && type in setOf("movie", "tv") && requestable) { "Dieser Titel ist nicht anforderbar." }
        val body = JSONObject().put("mediaType", type).put("mediaId", id).put("is4k", false)
        if (type == "tv") {
            val eligible = eligibleSeasons.map { it.number }
            require(selectedSeasons.isNotEmpty() && selectedSeasons.all { it in eligible }) { "Keine anforderbaren Staffeln gewählt." }
            body.put("seasons", JSONArray(selectedSeasons.distinct().sorted()))
        }
        return body
    }
    companion object {
        fun from(json: JSONObject, forcedType: String? = null): SeerrMedia? {
            val type = forcedType ?: json.optString("mediaType")
            val id = json.optInt("id")
            if (type !in setOf("movie", "tv") || id <= 0) return null
            val info = json.optJSONObject("mediaInfo") ?: JSONObject()
            val seasonStatus = info.array("seasons").associate { it.optInt("seasonNumber") to it.optInt("status") }
            return SeerrMedia(
                id, type, json.optString(if (type == "movie") "title" else "name"), json.optString("overview"),
                json.optString("posterPath").takeIf { it.startsWith("/") },
                json.optString("backdropPath").takeIf { it.startsWith("/") },
                json.optString(if (type == "movie") "releaseDate" else "firstAirDate").take(4).toIntOrNull() ?: 0,
                info.optInt("status"),
                json.array("seasons").map { SeerrSeason(it.optInt("seasonNumber"), it.optString("name"), it.optInt("episodeCount"), seasonStatus[it.optInt("seasonNumber")] ?: 0) },
                info.array("requests").filter { it.optInt("status") in setOf(1, 2) && !it.optBoolean("is4k") }
                    .flatMap { it.array("seasons") }.map { it.optInt("seasonNumber") }.toSet(),
            )
        }
    }
}

data class SeerrPage(val page: Int, val totalPages: Int, val items: List<SeerrMedia>) {
    companion object {
        fun from(json: JSONObject, type: String? = null) = SeerrPage(
            json.optInt("page", 1), json.optInt("totalPages", 1),
            json.array("results").mapNotNull { SeerrMedia.from(it, type) },
        )
    }
}

data class SeerrRequest(val id: Int, val mediaId: Int, val type: String, val status: Int) {
    val statusLabel: String get() = when (status) {
        1 -> "Ausstehend"
        2 -> "Genehmigt"
        3 -> "Abgelehnt"
        4 -> "Fehlgeschlagen"
        5 -> "Abgeschlossen"
        else -> "Unbekannt"
    }
    companion object {
        fun from(json: JSONObject): SeerrRequest {
            val media = json.optJSONObject("media") ?: JSONObject()
            return SeerrRequest(json.optInt("id"), media.optInt("tmdbId"), json.optString("type", media.optString("mediaType")), json.optInt("status"))
        }
    }
}
