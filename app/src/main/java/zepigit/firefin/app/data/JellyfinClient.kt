package zepigit.firefin.app.data

import android.os.Build
import zepigit.firefin.app.BuildConfig
import zepigit.firefin.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.tls.HandshakeCertificates
import org.json.JSONArray
import org.json.JSONObject
import zepigit.firefin.app.playback.DeviceProfile
import zepigit.firefin.app.util.Urls
import java.io.IOException
import java.net.URLEncoder

/** Jellyfin REST client using one origin-bound, cancellable ServerTransport. */
class JellyfinClient(private val session: SessionStore, trust: HandshakeCertificates? = null, private val seerrBridgeTimeoutMs: Long = SEERR_BRIDGE_TIMEOUT_MS, private val preferences: () -> zepigit.firefin.app.preferences.EffectiveDevicePreferences = {
    zepigit.firefin.app.preferences.deriveEffective(zepigit.firefin.app.preferences.StoredPreferences(), DeviceProfile.MAX_WIDTH, DeviceProfile.MAX_HEIGHT, DeviceProfile.MAX_BITRATE)
}) {
    private val transport = ServerTransport(
        ServerCredentials("https://invalid.firefin.invalid", "", ""),
        session.deviceId,
        Build.MODEL?.takeIf { it.isNotBlank() } ?: "Fire TV",
        VERSION,
        trust = trust,
    )
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val okHttp: okhttp3.OkHttpClient get() = transport.mediaHttp
    val baseUrl: String get() = session.serverUrl

    /** Immutable credential snapshot for one playback; see [ServerTransport.snapshot]. */
    fun transportSnapshot(): ServerTransport { syncTransport(); return transport.snapshot() }

    suspend fun login(serverInput: String, username: String, password: String): JSONObject = withContext(Dispatchers.IO) {
        val server = Urls.normalizeServer(serverInput)
        transport.update(ServerCredentials(server, "", ""))
        val body = JSONObject().put("Username", username).put("Pw", password)
        val json = JSONObject(transport.json("Users/AuthenticateByName", "POST", body.toString()))
        val token = json.optString("AccessToken").takeIf { it.isNotBlank() }
            ?: throw LocalizedIOException(R.string.error_login_response, "Login response did not contain an access token.")
        val user = json.optJSONObject("User") ?: throw LocalizedIOException(R.string.error_login_response, "Login response did not contain a user.")
        session.serverUrl = server
        session.accessToken = token
        session.userId = user.optString("Id")
        session.userName = user.optString("Name")
        session.serverName = json.optString("ServerId")
        transport.update(ServerCredentials(server, session.userId, token))
        if (password.isNotEmpty()) connectSeerr(username, password)
        json
    }

    /**
     * Legacy Moonfin parity: an explicit password sign-in also opens the
     * Moonbase Seerr session, so Seerr works without a second prompt. Seerr is
     * optional, so an absent, disabled or failing plugin never fails the
     * Jellyfin sign-in, and the step is bounded so a stalled plugin cannot hold
     * the login screen for several transport call timeouts. The password goes
     * out once over the same origin-bound transport and is not kept.
     */
    private suspend fun connectSeerr(username: String, password: String) {
        try {
            withTimeoutOrNull(seerrBridgeTimeoutMs) {
                val seerr = SeerrClient(transport.snapshot())
                if (!seerr.connect()) seerr.login(username, password)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpiredException) {
            // Jellyfin itself rejected the token it just issued; keeping it would only fail later.
            session.clear()
            throw e
        } catch (_: Exception) {
            // Optional: Seerr stays reachable through the fallback prompt in SeerrActivity.
        }
    }

    suspend fun views(): List<UserView> = withContext(Dispatchers.IO) {
        val json = getJson("Users/${session.userId}/Views")
        val arr = json.optJSONArray("Items") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { UserView(it.optString("Id"), it.optString("Name"), it.optString("CollectionType")) } }
    }

    suspend fun resume(limit: Int = 12): List<MediaItem> = withContext(Dispatchers.IO) {
        parseItems(getJson("Users/${session.userId}/Items/Resume?Limit=$limit&MediaTypes=Video&Fields=PrimaryImageAspectRatio,ProductionYear,Overview&EnableImageTypes=Primary,Backdrop,Thumb"))
    }

    suspend fun nextUp(limit: Int = 12, seriesId: String? = null): List<MediaItem> = withContext(Dispatchers.IO) {
        val path = buildString {
            append("Shows/NextUp?UserId=${session.userId}&Limit=$limit&Fields=PrimaryImageAspectRatio,ProductionYear,Overview")
            if (!seriesId.isNullOrBlank()) append("&SeriesId=").append(urlEncode(seriesId))
        }
        parseItems(getJson(path))
    }

    suspend fun randomItem(): MediaItem? = withContext(Dispatchers.IO) {
        items(limit = 1, sortBy = "Random", sortOrder = "Ascending", includeTypes = "Movie,Series,Episode", recursive = true).first.firstOrNull()
    }

    suspend fun latest(parentId: String? = null, limit: Int = 12): List<MediaItem> = withContext(Dispatchers.IO) {
        var path = "Users/${session.userId}/Items/Latest?Limit=$limit&Fields=PrimaryImageAspectRatio,ProductionYear,Overview&EnableImageTypes=Primary,Backdrop,Thumb"
        if (!parentId.isNullOrBlank()) path += "&ParentId=${urlEncode(parentId)}"
        parseArray(transportText(path))
    }

    suspend fun items(parentId: String? = null, startIndex: Int = 0, limit: Int = 60, sortBy: String = "SortName", sortOrder: String = "Ascending", searchTerm: String? = null, includeTypes: String? = null, recursive: Boolean = true, favorites: Boolean = false): Pair<List<MediaItem>, Int> = withContext(Dispatchers.IO) {
        val path = buildString {
            append("Users/${session.userId}/Items?StartIndex=$startIndex&Limit=$limit&SortBy=${urlEncode(sortBy)}&SortOrder=${urlEncode(sortOrder)}&Recursive=$recursive")
            append("&Fields=PrimaryImageAspectRatio,ProductionYear,Overview&EnableImageTypes=Primary,Backdrop,Thumb")
            if (!parentId.isNullOrBlank()) append("&ParentId=").append(urlEncode(parentId))
            if (!searchTerm.isNullOrBlank()) append("&searchTerm=").append(urlEncode(searchTerm))
            if (!includeTypes.isNullOrBlank()) append("&IncludeItemTypes=").append(urlEncode(includeTypes))
            if (favorites) append("&Filters=IsFavorite")
        }
        val json = getJson(path)
        parseItems(json) to json.optInt("TotalRecordCount", 0)
    }

    suspend fun item(itemId: String): MediaItem = withContext(Dispatchers.IO) { MediaItem.from(getJson("Users/${session.userId}/Items/${urlEncode(itemId)}")) }

    /** One page of a container's children plus the total count; a series returns all of its seasons at once. */
    suspend fun children(container: MediaItem, startIndex: Int = 0, limit: Int = CHILD_PAGE_SIZE): Pair<List<MediaItem>, Int> = withContext(Dispatchers.IO) {
        if (container.isSeries) {
            val seasons = parseItems(getJson("Shows/${urlEncode(container.id)}/Seasons?UserId=${urlEncode(session.userId)}"))
            seasons to seasons.size
        } else {
            items(parentId = container.id, startIndex = startIndex, recursive = false, sortBy = "IndexNumber", limit = limit)
        }
    }

    suspend fun playbackInfo(itemId: String, audioIndex: Int? = null, subtitleIndex: Int? = null, pinnedTransport: ServerTransport? = null, mediaSourceId: String? = null): PlaybackSource = withContext(Dispatchers.IO) {
        val bound = pinnedTransport ?: transportSnapshot()
        val credentials = bound.credentials()
        suspend fun request(path: String, method: String = "GET", body: String? = null) = JSONObject(bound.json(path, method, body))
        val effective = preferences()
        val body = JSONObject().put("DeviceProfile", DeviceProfile.build(effective.maxStreamingBitrate, effective.maxVideoWidth, effective.maxVideoHeight, baselineOnly = zepigit.firefin.app.BuildConfig.DEBUG && Build.FINGERPRINT.orEmpty().startsWith("generic")))
            .put("MaxStreamingBitrate", effective.maxStreamingBitrate).put("AutoOpenLiveStream", false)
        var selectedAudio = audioIndex
        var selectedSubtitle = subtitleIndex
        if (selectedAudio == null && effective.stored.audioLanguage.isNotBlank() || selectedSubtitle == null && effective.stored.subtitleLanguage.isNotBlank()) {
            val metadata = request("Users/${urlEncode(credentials.userId)}/Items/${urlEncode(itemId)}")
            val sources = metadata.optJSONArray("MediaSources")
            val selected = sources?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) }.firstOrNull { mediaSourceId == null || it.optString("Id") == mediaSourceId } }
            val streams = selected?.optJSONArray("MediaStreams") ?: metadata.optJSONArray("MediaStreams") ?: JSONArray()
            selected?.optString("Id")?.takeIf { it.isNotBlank() }?.let { body.put("MediaSourceId", it) }
            val tracks = zepigit.firefin.app.playback.StreamSelection.tracks(streams)
            if (selectedAudio == null) selectedAudio = zepigit.firefin.app.playback.StreamSelection.index(tracks, "Audio", effective.stored.audioLanguage)
            if (selectedSubtitle == null) selectedSubtitle = zepigit.firefin.app.playback.StreamSelection.index(tracks, "Subtitle", effective.stored.subtitleLanguage)
        }
        selectedAudio?.let { body.put("AudioStreamIndex", it) }
        if (effective.stored.subtitleLanguage.isBlank() && selectedSubtitle == null) selectedSubtitle = -1
        selectedSubtitle?.let { body.put("SubtitleStreamIndex", it) }
        mediaSourceId?.let { body.put("MediaSourceId", it) }
        val json = request("Items/${urlEncode(itemId)}/PlaybackInfo?UserId=${urlEncode(credentials.userId)}&AutoOpenLiveStream=false&MaxStreamingBitrate=${effective.maxStreamingBitrate}", "POST", body.toString())
        val sources = json.optJSONArray("MediaSources") ?: JSONArray()
        val source = (0 until sources.length()).mapNotNull { sources.optJSONObject(it) }.filter { mediaSourceId == null || it.optString("Id") == mediaSourceId }.firstOrNull { source ->
            source.optBoolean("SupportsDirectPlay", false) || source.optBoolean("SupportsDirectStream", false) || source.optBoolean("SupportsTranscoding", false) || source.optString("TranscodingUrl").isNotBlank()
        } ?: throw LocalizedIOException(R.string.error_no_playable_source, "No playable media source.")
        val id = source.optString("Id").takeIf { it.isNotBlank() } ?: itemId
        val transcode = source.optString("TranscodingUrl").takeIf { it.isNotBlank() }
        val directStream = source.optString("DirectStreamUrl").takeIf { it.isNotBlank() }
        val url = when {
            source.optBoolean("SupportsDirectPlay") && transcode == null -> Urls.directStreamUrl(credentials.serverUrl, itemId, id)
            transcode != null -> Urls.resolveRelative(credentials.serverUrl, transcode)
            directStream != null -> Urls.resolveRelative(credentials.serverUrl, directStream)
            source.optBoolean("SupportsDirectStream") || source.optBoolean("SupportsTranscoding") -> throw LocalizedIOException(R.string.error_no_playable_source, "Server did not supply a negotiated media URL.")
            else -> throw LocalizedIOException(R.string.error_no_playable_source, "Media source is not playable on this device.")
        }
        val streams = source.optJSONArray("MediaStreams") ?: JSONArray()
        val subtitle = (0 until streams.length()).mapNotNull { streams.optJSONObject(it) }
            .firstOrNull { it.optString("Type") == "Subtitle" && it.optInt("Index") == selectedSubtitle }
        val subtitlePath = subtitle?.optString("DeliveryUrl")?.takeIf { it.isNotBlank() }
        PlaybackSource(
            json.optString("PlaySessionId"), id, url, transcode != null,
            transcode?.contains(".m3u8", ignoreCase = true) == true, source.optString("Container"),
            source.optString("LiveStreamId").takeIf { it.isNotBlank() } ?: "",
            tracks = zepigit.firefin.app.playback.StreamSelection.tracks(streams),
            subtitleUrl = subtitlePath?.let { Urls.resolveRelative(credentials.serverUrl, it) },
            subtitleMime = when (subtitle?.optString("Codec")) { "srt" -> "application/x-subrip"; "vtt", "webvtt" -> "text/vtt"; else -> null },
            playMethod = if (transcode != null) "Transcode" else if (directStream != null && !source.optBoolean("SupportsDirectPlay")) "DirectStream" else "DirectPlay",
            audioIndex = selectedAudio,
            subtitleIndex = selectedSubtitle,
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
            RemoteSession(o.optString("Id"), o.optString("DeviceId"), o.optString("DeviceName"), o.optString("UserName"), now,
                o.optBoolean("SupportsMediaControl") || o.optJSONObject("Capabilities")?.optBoolean("SupportsMediaControl") == true)
        }
    }

    suspend fun sendCommand(sessionId: String, command: String) = withContext(Dispatchers.IO) { transportText("Sessions/${urlEncode(sessionId)}/Playing/${urlEncode(command)}", "POST", "{}") }
    // Jellyfin marks with POST and unmarks with DELETE on the same resource path.
    suspend fun setFavorite(itemId: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        transportText("Users/${session.userId}/FavoriteItems/${urlEncode(itemId)}", if (favorite) "POST" else "DELETE")
    }
    suspend fun setPlayed(itemId: String, played: Boolean) = withContext(Dispatchers.IO) {
        transportText("Users/${session.userId}/PlayedItems/${urlEncode(itemId)}", if (played) "POST" else "DELETE")
    }

    /**
     * Revokes the current access token on the server. Best effort and detached:
     * the local sign-out never waits for it, and a failure leaves only a stale
     * server-side session that the administrator can remove.
     */
    fun logout() {
        if (!session.isLoggedIn) return
        val bound = transportSnapshot()
        background.launch { runCatching { bound.json("Sessions/Logout", "POST") } }
    }

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

    companion object {
        const val VERSION = BuildConfig.VERSION_NAME
        const val CHILD_PAGE_SIZE = 60
        /** Upper bound for the whole optional Seerr step (probe plus sign-in) after a Jellyfin login. */
        const val SEERR_BRIDGE_TIMEOUT_MS = 10_000L
    }
}
