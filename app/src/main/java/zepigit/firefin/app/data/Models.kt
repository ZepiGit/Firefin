package zepigit.firefin.app.data

import org.json.JSONObject

data class MediaItem(
    val id: String,
    val name: String,
    val type: String,
    val year: Int,
    val overview: String,
    val posterTag: String?,
    val backdropTag: String?,
    val thumbTag: String?,
    val parentId: String?,
    val seriesName: String,
    val indexNumber: Int,
    val parentIndexNumber: Int,
    val runTimeTicks: Long,
    val resumeTicks: Long,
    val played: Boolean,
    val favorite: Boolean,
    val collectionType: String,
) {
    val isEpisode: Boolean get() = type == "Episode"
    val isSeries: Boolean get() = type == "Series"
    val isPlayable: Boolean get() = type == "Movie" || type == "Episode" || type == "Video"

    companion object {
        fun from(json: JSONObject): MediaItem {
            val userData = json.optJSONObject("UserData") ?: JSONObject()
            val imageTags = json.optJSONObject("ImageTags") ?: JSONObject()
            val backdropTags = json.optJSONArray("BackdropImageTags")
            return MediaItem(
                id = json.optString("Id"),
                name = json.optString("Name"),
                type = json.optString("Type"),
                year = json.optInt("ProductionYear", 0),
                overview = json.optString("Overview"),
                posterTag = imageTags.optString("Primary").takeIf { it.isNotBlank() },
                backdropTag = if (backdropTags != null && backdropTags.length() > 0) backdropTags.optString(0).takeIf { it.isNotBlank() } else null,
                thumbTag = imageTags.optString("Thumb").takeIf { it.isNotBlank() },
                parentId = json.optString("ParentId").takeIf { it.isNotBlank() },
                seriesName = json.optString("SeriesName"),
                indexNumber = json.optInt("IndexNumber", 0),
                parentIndexNumber = json.optInt("ParentIndexNumber", 0),
                runTimeTicks = json.optLong("RunTimeTicks", 0L),
                resumeTicks = userData.optLong("PlaybackPositionTicks", 0L),
                played = userData.optBoolean("Played", false),
                favorite = userData.optBoolean("IsFavorite", false),
                collectionType = json.optString("CollectionType"),
            )
        }
    }
}

data class UserView(
    val id: String,
    val name: String,
    val collectionType: String,
)

data class PlaybackSource(
    val playSessionId: String,
    val mediaSourceId: String,
    val url: String,
    val isTranscode: Boolean,
    val isHls: Boolean,
    val container: String,
    val resumeTicks: Long = 0L,
)

data class RemoteSession(
    val id: String,
    val deviceId: String,
    val deviceName: String,
    val userName: String,
    val nowPlaying: String,
    val controllable: Boolean,
)
