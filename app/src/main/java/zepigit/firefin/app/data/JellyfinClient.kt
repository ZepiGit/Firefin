package zepigit.firefin.app.data

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import zepigit.firefin.app.playback.DeviceProfile
import zepigit.firefin.app.util.Urls
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Jellyfin REST client on OkHttp. Tokens are sent via headers, never logged. */
class JellyfinClient(private val session: SessionStore) {

    private val http = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Shared HTTP stack for streaming data sources (image loader uses its own bounded client). */
    val okHttp: okhttp3.OkHttpClient get() = http

    val baseUrl: String get() = session.serverUrl

    private fun authHeader(token: String?): String {
        val model = Build.MODEL?.replace("\"", "")?.takeIf { it.isNotBlank() } ?: "Fire TV"
        val sb = StringBuilder("MediaBrowser ")
            .append("Client=\"Firefin\", ")
            .append("Device=\"").append(model).append("\", ")
            .append("DeviceId=\"").append(session.deviceId).append("\", ")
            .append("Version=\"").append(VERSION).append("\"")
        if (!token.isNullOrEmpty()) sb.append(", Token=\"").append(token).append("\"")
        return sb.toString()
    }

    private fun request(url: String, method: String, body: JSONObject? = null, token: String? = session.accessToken): okhttp3.Response {
        val builder = okhttp3.Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", authHeader(token))
        when (method) {
            "POST" -> builder.post(
                okhttp3.RequestBody.create(
                    "application/json; charset=utf-8".toMediaTypeOrNull(),
                    body?.toString() ?: "{}",
                ),
            )
            "DELETE" -> builder.delete()
            "DELETE" -> builder.delete()
        }
        return http.newCall(builder.build()).execute()
    }

    private fun bodyOf(response: okhttp3.Response): String =
        response.body?.string() ?: ""

    // ── Auth ────────────────────────────────────────────────────────────────

    suspend fun login(serverInput: String, username: String, password: String): JSONObject =
        withContext(Dispatchers.IO) {
            val server = Urls.normalizeServer(serverInput)
            val url = "$server/Users/AuthenticateByName"
            val body = JSONObject().put("Username", username).put("Pw", password)
            request(url, "POST", body, token = null).use { response ->
                val text = bodyOf(response)
                if (!response.isSuccessful) {
                    throw IOException("Login failed: HTTP ${response.code}")
                }
                val json = JSONObject(text)
                // Persist only after the server accepted the credentials.
                session.serverUrl = server
                session.accessToken = json.optString("AccessToken")
                session.userId = json.optJSONObject("User")?.optString("Id") ?: ""
                session.userName = json.optJSONObject("User")?.optString("Name") ?: ""
                session.serverName = json.optString("ServerId")
                json
            }
        }

    // ── Library ─────────────────────────────────────────────────────────────

    suspend fun views(): List<UserView> = withContext(Dispatchers.IO) {
        val json = getJson("/Users/${session.userId}/Views")
        val arr = json.optJSONArray("Items") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            UserView(o.optString("Id"), o.optString("Name"), o.optString("CollectionType"))
        }
    }

    suspend fun resume(limit: Int = 12): List<MediaItem> = withContext(Dispatchers.IO) {
        val url = "/Users/${session.userId}/Items/Resume?Limit=$limit" +
            "&MediaTypes=Video&Fields=PrimaryImageAspectRatio,ProductionYear,Overview" +
            "&EnableImageTypes=Primary,Backdrop,Thumb"
        parseItems(getJson(url))
    }

    suspend fun nextUp(limit: Int = 12): List<MediaItem> = withContext(Dispatchers.IO) {
        val url = "/Shows/NextUp?UserId=${session.userId}&Limit=$limit" +
            "&Fields=PrimaryImageAspectRatio,ProductionYear"
        parseItems(getJson(url))
    }

    suspend fun latest(parentId: String? = null, limit: Int = 12): List<MediaItem> = withContext(Dispatchers.IO) {
        var url = "/Users/${session.userId}/Items/Latest?Limit=$limit" +
            "&Fields=PrimaryImageAspectRatio,ProductionYear&EnableImageTypes=Primary,Backdrop,Thumb"
        if (!parentId.isNullOrEmpty()) url += "&ParentId=$parentId"
        val arr = JSONArray(bodyOf(request(baseUrl + url, "GET")))
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                // LatestMedia uses "ImageTags" and may lack UserData.
                MediaItem.from(o)
            }
        }
    }

    suspend fun items(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 60,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        searchTerm: String? = null,
        includeTypes: String? = null,
    ): Pair<List<MediaItem>, Int> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("/Users/${session.userId}/Items")
            .append("?StartIndex=").append(startIndex)
            .append("&Limit=").append(limit)
            .append("&SortBy=").append(sortBy)
            .append("&SortOrder=").append(sortOrder)
            .append("&Recursive=true")
            .append("&Fields=PrimaryImageAspectRatio,ProductionYear,Overview")
            .append("&EnableImageTypes=Primary,Backdrop,Thumb")
        if (!parentId.isNullOrEmpty()) sb.append("&ParentId=").append(parentId)
        if (!searchTerm.isNullOrEmpty()) sb.append("&searchTerm=").append(java.net.URLEncoder.encode(searchTerm, "UTF-8"))
        if (!includeTypes.isNullOrEmpty()) sb.append("&IncludeItemTypes=").append(includeTypes)
        val json = getJson(sb.toString())
        parseItems(json) to json.optInt("TotalRecordCount", 0)
    }

    suspend fun item(itemId: String): MediaItem = withContext(Dispatchers.IO) {
        MediaItem.from(getJson("/Users/${session.userId}/Items/$itemId"))
    }

    suspend fun children(seriesId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val seasons = getJson("/Shows/$seriesId/Seasons?UserId=${session.userId}")
            .optJSONArray("Items") ?: JSONArray()
        val result = mutableListOf<MediaItem>()
        for (s in 0 until seasons.length()) {
            val season = seasons.optJSONObject(s) ?: continue
            val episodes = getJson(
                "/Shows/$seriesId/Episodes?UserId=${session.userId}&SeasonId=${season.optString("Id")}" +
                    "&Fields=PrimaryImageAspectRatio,Overview",
            ).optJSONArray("Items") ?: JSONArray()
            for (e in 0 until episodes.length()) {
                episodes.optJSONObject(e)?.let { result.add(MediaItem.from(it)) }
            }
        }
        result
    }

    // ── Playback ────────────────────────────────────────────────────────────

    suspend fun playbackInfo(itemId: String): PlaybackSource = withContext(Dispatchers.IO) {
        val url = baseUrl + "/Items/$itemId/PlaybackInfo?UserId=${session.userId}" +
            "&AutoOpenLiveStream=true&MaxStreamingBitrate=${DeviceProfile.MAX_BITRATE}"
        val body = JSONObject()
            .put("DeviceProfile", DeviceProfile.build())
            .put("MaxStreamingBitrate", DeviceProfile.MAX_BITRATE)
            .put("AutoOpenLiveStream", true)
        request(url, "POST", body).use { response ->
            val text = bodyOf(response)
            if (!response.isSuccessful) throw IOException("PlaybackInfo failed: HTTP ${response.code}")
            val json = JSONObject(text)
            val playSessionId = json.optString("PlaySessionId")
            val sources = json.optJSONArray("MediaSources") ?: JSONArray()
            val source = sources.optJSONObject(0) ?: throw IOException("No playable media source")
            val transcodingUrl = source.optString("TranscodingUrl", null)
            val container = source.optString("Container", "")
            val mediaSourceId = source.optString("Id", itemId)
            val streamUrl = if (!transcodingUrl.isNullOrEmpty()) {
                Urls.resolveRelative(baseUrl, transcodingUrl) to true
            } else {
                Urls.directStreamUrl(baseUrl, itemId, mediaSourceId, session.accessToken) to false
            }
            PlaybackSource(playSessionId, mediaSourceId, streamUrl.first, streamUrl.second, container)
        }
    }

    suspend fun reportPlaying(itemId: String, playSessionId: String) = postSessionEvent(
        "/Sessions/Playing", itemId, playSessionId,
    )

    suspend fun reportProgress(itemId: String, playSessionId: String, positionTicks: Long, paused: Boolean) =
        postSessionEvent("/Sessions/Playing/Progress", itemId, playSessionId) {
            put("PositionTicks", positionTicks)
            put("IsPaused", paused)
        }

    suspend fun reportStopped(itemId: String, playSessionId: String, positionTicks: Long) =
        postSessionEvent("/Sessions/Playing/Stopped", itemId, playSessionId) {
            put("PositionTicks", positionTicks)
        }

    // ── Remote control (control other sessions on the server) ──────────────

    suspend fun sessions(): List<RemoteSession> = withContext(Dispatchers.IO) {
        val arr = JSONArray(bodyOf(request("$baseUrl/Sessions", "GET")))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val cmds = o.optJSONArray("PlayableMediaTypes")
            val nowPlaying = o.optJSONObject("NowPlayingItem")?.optString("Name") ?: ""
            RemoteSession(
                id = o.optString("Id"),
                deviceName = o.optString("DeviceName", ""),
                userName = o.optJSONObject("UserName")?.optString("$", "") ?: o.optString("UserName", ""),
                nowPlaying = nowPlaying,
                controllable = cmds != null && cmds.length() > 0,
            )
        }
    }

    suspend fun sendCommand(sessionId: String, command: String) = withContext(Dispatchers.IO) {
        request("$baseUrl/Sessions/$sessionId/Playing/$command", "POST").use { }
    }

    // ── User data ───────────────────────────────────────────────────────────

    suspend fun setFavorite(itemId: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        val path = if (favorite) "/Users/${session.userId}/FavoriteItems/$itemId"
        else "/Users/${session.userId}/FavoriteItems/$itemId/Delete"
        request(baseUrl + path, "POST").use { }
    }

    suspend fun setPlayed(itemId: String, played: Boolean) = withContext(Dispatchers.IO) {
        val path = if (played) "/Users/${session.userId}/PlayedItems/$itemId"
        else "/Users/${session.userId}/PlayedItems/$itemId/Delete"
        request(baseUrl + path, "POST").use { }
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private suspend fun getJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        request(baseUrl + path, "GET").use { response ->
            val text = bodyOf(response)
            if (response.code == 401) throw IOException("Session expired")
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            JSONObject(text)
        }
    }

    private suspend fun postSessionEvent(
        path: String,
        itemId: String,
        playSessionId: String,
        extra: JSONObject.() -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("ItemId", itemId)
            .put("PlaySessionId", playSessionId)
        body.extra()
        request(baseUrl + path, "POST", body).use { }
    }

    private fun parseItems(json: JSONObject): List<MediaItem> {
        val arr = json.optJSONArray("Items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optJSONObject(idx)?.let(MediaItem::from)
        }
    }

    companion object {
        const val VERSION = "0.1.0"
    }
}
