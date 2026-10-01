package zepigit.firefin.app.data

import android.os.Build
import zepigit.firefin.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import zepigit.firefin.app.playback.DeviceProfile
import zepigit.firefin.app.util.Urls
import java.io.IOException
import java.net.URLEncoder

/** Jellyfin REST client using one origin-bound, cancellable ServerTransport. */
class JellyfinClient(private val session: SessionStore) {
    private val transport = ServerTransport(
        ServerCredentials("https://invalid.firefin.invalid", "", ""),
        session.deviceId,
        Build.MODEL?.takeIf { it.isNotBlank() } ?: "Fire TV",
        VERSION,
    )

    val okHttp: okhttp3.OkHttpClient get() = transport.mediaHttp
    val baseUrl: String get() = session.serverUrl

    /** Immutable credential snapshot for one playback; see [ServerTransport.snapshot]. */
    fun transportSnapshot(): ServerTransport = transport.snapshot()

    suspend fun login(serverInput: String, username: String, password: String): JSONObject = withContext(Dispatchers.IO) {
        val server = Urls.normalizeServer(serverInput)
        transport.update(ServerCredentials(server, "", ""))
        val body = JSONObject().put("Username", username).put("Pw", password)
        val json = JSONObject(transport.json("Users/AuthenticateByName", "POST", body.toString()))
        val token = json.optString("AccessToken").takeIf { it.isNotBlank() }
            ?: throw IOException("Login response did not contain an access token.")
        val user = json.optJSONObject("User") ?: throw IOException("Login response did not contain a user.")
        session.serverUrl = server
        session.accessToken = token
        session.userId = user.optString("Id")
        session.userName = user.optString("Name")
        session.serverName = json.optString("ServerId")
        transport.update(ServerCredentials(server, session.userId, token))
        json
    }

    suspend fun views(): List<UserView> = withContext(Dispatchers.IO) {
        val json = getJson("Users/${session.userId}/Views")
        val arr = json.optJSONArray("Items") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { UserView(it.optString("Id"), it.optString("Name"), it.optString("CollectionType")) } }
    }

    suspend fun resume(limit: Int = 12): List<MediaItem> = withContext(Dispatchers.IO) {
        parseItems(getJson("Users/${session.userId}/Items/Resume?Limit=$limit&MediaTypes=Video&Fields=PrimaryImageAspectRatio,ProductionYear,Overview&EnableImageTypes=Primary,Backdrop,Thumb"))
    }

    suspend fun nextUp(limit: Int = 12): List<MediaItem> = withContext(Dispatchers.IO) {
        parseItems(getJson("Shows/NextUp?UserId=${session.userId}&Limit=$limit&Fields=PrimaryImageAspectRatio,ProductionYear"))
    }

    suspend fun latest(parentId: String? = null, limit: Int = 12): List<MediaItem> = withContext(Dispatchers.IO) {
        var path = "Users/${session.userId}/Items/Latest?Limit=$limit&Fields=PrimaryImageAspectRatio,ProductionYear&EnableImageTypes=Primary,Backdrop,Thumb"
        if (!parentId.isNullOrBlank()) path += "&ParentId=${urlEncode(parentId)}"
        parseArray(transportText(path))
    }

    suspend fun items(parentId: String? = null, startIndex: Int = 0, limit: Int = 60, sortBy: String = "SortName", sortOrder: String = "Ascending", searchTerm: String? = null, includeTypes: String? = null): Pair<List<MediaItem>, Int> = withContext(Dispatchers.IO) {
        val path = buildString {
            append("Users/${session.userId}/Items?StartIndex=$startIndex&Limit=$limit&SortBy=${urlEncode(sortBy)}&SortOrder=${urlEncode(sortOrder)}&Recursive=true")
            append("&Fields=PrimaryImageAspectRatio,ProductionYear,Overview&EnableImageTypes=Primary,Backdrop,Thumb")
            if (!parentId.isNullOrBlank()) append("&ParentId=").append(urlEncode(parentId))
            if (!searchTerm.isNullOrBlank()) append("&searchTerm=").append(urlEncode(searchTerm))
            if (!includeTypes.isNullOrBlank()) append("&IncludeItemTypes=").append(urlEncode(includeTypes))
        }
        val json = getJson(path)
        parseItems(json) to json.optInt("TotalRecordCount", 0)
    }

    suspend fun item(itemId: String): MediaItem = withContext(Dispatchers.IO) { MediaItem.from(getJson("Users/${session.userId}/Items/${urlEncode(itemId)}")) }

    suspend fun children(seriesId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val seasons = getJson("Shows/${urlEncode(seriesId)}/Seasons?UserId=${urlEncode(session.userId)}").optJSONArray("Items") ?: JSONArray()
        buildList {
            for (index in 0 until seasons.length()) {
                val season = seasons.optJSONObject(index) ?: continue
                val seasonId = season.optString("Id")
                val episodes = getJson("Shows/${urlEncode(seriesId)}/Episodes?UserId=${urlEncode(session.userId)}&SeasonId=${urlEncode(seasonId)}&Fields=PrimaryImageAspectRatio,Overview").optJSONArray("Items") ?: JSONArray()
                for (episode in 0 until episodes.length()) episodes.optJSONObject(episode)?.let { add(MediaItem.from(it)) }
            }
        }
    }

    suspend fun playbackInfo(itemId: String): PlaybackSource = withContext(Dispatchers.IO) {
        val body = JSONObject().put("DeviceProfile", DeviceProfile.build()).put("MaxStreamingBitrate", DeviceProfile.MAX_BITRATE).put("AutoOpenLiveStream", true)
        val json = getJson("Items/${urlEncode(itemId)}/PlaybackInfo?UserId=${urlEncode(session.userId)}&AutoOpenLiveStream=true&MaxStreamingBitrate=${DeviceProfile.MAX_BITRATE}", "POST", body.toString())
        val sources = json.optJSONArray("MediaSources") ?: JSONArray()
        val source = (0 until sources.length()).mapNotNull { sources.optJSONObject(it) }.firstOrNull { source ->
            source.optBoolean("SupportsDirectPlay", false) || source.optBoolean("SupportsDirectStream", false) || source.optBoolean("SupportsTranscoding", false) || source.optString("TranscodingUrl").isNotBlank()
        } ?: throw IOException("No playable media source.")
        val id = source.optString("Id").takeIf { it.isNotBlank() } ?: itemId
        val transcode = source.optString("TranscodingUrl").takeIf { it.isNotBlank() }
        val url = if (transcode != null) Urls.resolveRelative(baseUrl, transcode) else Urls.directStreamUrl(baseUrl, itemId, id)
        PlaybackSource(
            json.optString("PlaySessionId"), id, url, transcode != null,
            transcode?.contains(".m3u8", ignoreCase = true) == true, source.optString("Container"),
            source.optString("LiveStreamId").takeIf { it.isNotBlank() } ?: "",
        )
    }

    suspend fun reportPlaying(itemId: String, playSessionId: String, mediaSourceId: String = "", playMethod: String = "DirectPlay") = postSessionEvent("Sessions/Playing", itemId, playSessionId) {
        put("MediaSourceId", mediaSourceId); put("PlayMethod", playMethod); put("CanSeek", true)
    }
    suspend fun reportProgress(itemId: String, playSessionId: String, positionTicks: Long, paused: Boolean, mediaSourceId: String = "") = postSessionEvent("Sessions/Playing/Progress", itemId, playSessionId) {
        put("PositionTicks", positionTicks); put("IsPaused", paused); put("MediaSourceId", mediaSourceId); put("CanSeek", true)
    }
    suspend fun reportStopped(itemId: String, playSessionId: String, positionTicks: Long, mediaSourceId: String = "") = postSessionEvent("Sessions/Playing/Stopped", itemId, playSessionId) {
        put("PositionTicks", positionTicks); put("MediaSourceId", mediaSourceId)
    }

    suspend fun sessions(): List<RemoteSession> = withContext(Dispatchers.IO) {
        val arr = JSONArray(transportText("Sessions"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val now = o.optJSONObject("NowPlayingItem")?.optString("Name") ?: ""
            RemoteSession(o.optString("Id"), o.optString("DeviceId"), o.optString("DeviceName"), o.optString("UserName"), now, o.optJSONArray("Capabilities") != null)
        }
    }

    suspend fun sendCommand(sessionId: String, command: String) = withContext(Dispatchers.IO) { transportText("Sessions/${urlEncode(sessionId)}/Playing/${urlEncode(command)}", "POST", "{}") }
    suspend fun setFavorite(itemId: String, favorite: Boolean) = withContext(Dispatchers.IO) { transportText("Users/${session.userId}/FavoriteItems/${urlEncode(itemId)}${if (favorite) "" else "/Delete"}", "POST", "{}") }
    suspend fun setPlayed(itemId: String, played: Boolean) = withContext(Dispatchers.IO) { transportText("Users/${session.userId}/PlayedItems/${urlEncode(itemId)}${if (played) "" else "/Delete"}", "POST", "{}") }

    private suspend fun getJson(path: String, method: String = "GET", body: String? = null): JSONObject = JSONObject(transportText(path, method, body))
    private suspend fun postSessionEvent(path: String, itemId: String, playSessionId: String, extra: JSONObject.() -> Unit) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("ItemId", itemId).put("PlaySessionId", playSessionId).apply(extra)
        transportText(path, "POST", body.toString())
    }
    private suspend fun transportText(path: String, method: String = "GET", body: String? = null): String { syncTransport(); return transport.json(path, method, body) }
    private fun parseItems(json: JSONObject): List<MediaItem> { val arr = json.optJSONArray("Items") ?: return emptyList(); return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(MediaItem::from) } }
    private fun parseArray(value: String): List<MediaItem> { val arr = JSONArray(value); return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(MediaItem::from) } }
    private fun syncTransport() { transport.update(ServerCredentials(session.serverUrl, session.userId, session.accessToken)) }
    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object { const val VERSION = BuildConfig.VERSION_NAME }
}
