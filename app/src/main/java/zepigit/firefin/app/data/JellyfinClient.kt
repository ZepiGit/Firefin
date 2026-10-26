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
class JellyfinClient(private val session: SessionStore, trust: Lazy<HandshakeCertificates?> = lazyOf(null), private val seerrBridgeTimeoutMs: Long = SEERR_BRIDGE_TIMEOUT_MS, private val preferences: () -> zepigit.firefin.app.preferences.EffectiveDevicePreferences = {
    zepigit.firefin.app.preferences.deriveEffective(zepigit.firefin.app.preferences.StoredPreferences(), DeviceProfile.MAX_WIDTH, DeviceProfile.MAX_HEIGHT, DeviceProfile.MAX_BITRATE)
}) {
    private val transport = ServerTransport(
        ServerCredentials("https://invalid.firefin.invalid", "", ""),
        session.deviceId,
        Build.MODEL?.takeIf { it.isNotBlank() } ?: "Fire TV",
        VERSION,
        trustSource = trust,
    )
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * User-scoped item routes come in two generations: Users/{id}/... on 10.8 and
     * UserViews, UserItems/Resume, Items?userId=, UserFavoriteItems, ... from 10.9
     * on (the only form a 12.x server has). The generation is probed once per
     * server address and corrected when a route answers 404, so a wrong guess
     * costs one request.
     */
    private enum class Routes { MODERN, LEGACY, UNKNOWN }
    private val routesByServer = java.util.concurrent.ConcurrentHashMap<String, Routes>()
    private val majorByServer = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** DELETE Videos/ActiveEncodings is a pre-12 route (spec evidence, runtime behaviour unverified). */
    fun cleanupEncodings(): Boolean = (majorByServer[session.serverUrl] ?: 0) < 12

    /** True once the signed-in server is known to be 10.8, which has no UserItems/{id}/UserData route. */
    fun legacyRoutes(): Boolean = routesByServer[session.serverUrl] == Routes.LEGACY

    /**
     * Clears only the resume position (10.9+): the server applies the fields the
     * body carries and leaves Played, PlayCount and LastPlayedDate alone, unlike
     * the unplayed mark. Server-side behaviour not exercised against a live server.
     */
    suspend fun resetResume(itemId: String) = withContext(Dispatchers.IO) {
        transportText("UserItems/${urlEncode(itemId)}/UserData?userId=${user()}", "POST", JSONObject().put("PlaybackPositionTicks", 0).toString())
    }

    private suspend fun routes(bound: ServerTransport): Routes {
        val server = bound.credentials().serverUrl
        routesByServer[server]?.let { return it }
        val probed = try {
            val version = JSONObject(bound.json("System/Info/Public")).optString("Version").split('.')
            val major = version.getOrNull(0)?.toIntOrNull()
            val minor = version.getOrNull(1)?.toIntOrNull()
            if (major == null || minor == null) Routes.UNKNOWN else {
                majorByServer[server] = major
                if (major == 10 && minor < 9) Routes.LEGACY else Routes.MODERN
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Routes.UNKNOWN
        }
        routesByServer[server] = probed
        return probed
    }

    /** Sends the route of the probed generation; a 404 tries the other generation once and remembers it. */
    private suspend fun userText(modern: String, legacy: String, method: String = "GET", body: String? = null, bound: ServerTransport? = null): String {
        val via = bound ?: transport.also { syncTransport() }
        val server = via.credentials().serverUrl
        val first = routes(via)
        val (primary, fallback) = if (first == Routes.LEGACY) legacy to modern else modern to legacy
        return try {
            via.json(primary, method, body)
        } catch (e: ServerResponseException) {
            if (e.status != 404) throw e
            val text = via.json(fallback, method, body)
            routesByServer[server] = if (first == Routes.LEGACY) Routes.MODERN else Routes.LEGACY
            text
        }
    }

    private suspend fun userJson(modern: String, legacy: String, method: String = "GET", body: String? = null, bound: ServerTransport? = null) =
        JSONObject(userText(modern, legacy, method, body, bound))

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
        val json = userJson("UserViews?userId=${user()}", "Users/${user()}/Views")
        val arr = json.optJSONArray("Items") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { UserView(it.optString("Id"), it.optString("Name"), it.optString("CollectionType")) } }
    }

    suspend fun latestExcludes(): Set<String> = withContext(Dispatchers.IO) {
        val values = getJson("Users/Me").optJSONObject("Configuration")?.optJSONArray("LatestItemsExcludes") ?: JSONArray()
        (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }.toSet()
    }

    suspend fun resume(limit: Int = 12): List<MediaItem> = withContext(Dispatchers.IO) {
        val query = "Limit=$limit&MediaTypes=Video&Fields=PrimaryImageAspectRatio,ProductionYear,Overview&EnableImageTypes=Primary,Backdrop,Thumb"
        parseItems(userJson("UserItems/Resume?userId=${user()}&$query", "Users/${user()}/Items/Resume?$query"))
    }

    suspend fun nextUp(limit: Int = 12, seriesId: String? = null): List<MediaItem> = withContext(Dispatchers.IO) {
        val path = buildString {
            append("Shows/NextUp?UserId=${session.userId}&Limit=$limit&Fields=PrimaryImageAspectRatio,ProductionYear,Overview")
            if (!seriesId.isNullOrBlank()) append("&SeriesId=").append(urlEncode(seriesId))
        }
        parseItems(getJson(path))
    }

    suspend fun mediaSegments(itemId: String, bound: ServerTransport): List<zepigit.firefin.app.playback.MediaSegment> = withContext(Dispatchers.IO) {
        try { zepigit.firefin.app.playback.SegmentCue.parse(JSONObject(bound.json("MediaSegments/${urlEncode(itemId)}"))) }
        catch (e: ServerResponseException) { if (e.status == 404) emptyList() else throw e }
    }

    suspend fun nextEpisode(seriesId: String, itemId: String, bound: ServerTransport): MediaItem? = withContext(Dispatchers.IO) {
        val path = "Shows/${urlEncode(seriesId)}/Episodes?userId=${urlEncode(bound.credentials().userId)}&startItemId=${urlEncode(itemId)}&limit=2&isMissing=false&fields=Overview"
        val items = parseItems(JSONObject(bound.json(path)))
        if (items.firstOrNull()?.id != itemId) null
        else items.getOrNull(1)?.takeIf { it.isEpisode && it.id.isNotBlank() && it.id != itemId }
    }

    suspend fun randomItem(): MediaItem? = withContext(Dispatchers.IO) {
        items(limit = 1, sortBy = "Random", sortOrder = "Ascending", includeTypes = "Movie,Series,Episode", recursive = true).first.firstOrNull()
    }

    suspend fun latest(parentId: String? = null, limit: Int = 12): List<MediaItem> = withContext(Dispatchers.IO) {
        var query = "Limit=$limit&Fields=PrimaryImageAspectRatio,ProductionYear,Overview&EnableImageTypes=Primary,Backdrop,Thumb"
        if (!parentId.isNullOrBlank()) query += "&ParentId=${urlEncode(parentId)}"
        parseArray(userText("Items/Latest?userId=${user()}&$query", "Users/${user()}/Items/Latest?$query"))
    }

    suspend fun items(parentId: String? = null, startIndex: Int = 0, limit: Int = 60, sortBy: String = "SortName", sortOrder: String = "Ascending", searchTerm: String? = null, includeTypes: String? = null, recursive: Boolean = true, filters: String? = null, genreIds: String? = null): Pair<List<MediaItem>, Int> = withContext(Dispatchers.IO) {
        val query = buildString {
            append("StartIndex=$startIndex&Limit=$limit&SortBy=${urlEncode(sortBy)}&SortOrder=${urlEncode(sortOrder)}&Recursive=$recursive")
            // Grids, children and search show cards only; Overview stays on the home rows that preview it.
            append("&Fields=ProductionYear&EnableImageTypes=Primary,Backdrop,Thumb")
            if (!parentId.isNullOrBlank()) append("&ParentId=").append(urlEncode(parentId))
            if (!searchTerm.isNullOrBlank()) append("&searchTerm=").append(urlEncode(searchTerm))
            if (!includeTypes.isNullOrBlank()) append("&IncludeItemTypes=").append(urlEncode(includeTypes))
            if (!filters.isNullOrBlank()) append("&Filters=").append(urlEncode(filters))
            if (!genreIds.isNullOrBlank()) append("&GenreIds=").append(urlEncode(genreIds))
        }
        val json = userJson("Items?userId=${user()}&$query", "Users/${user()}/Items?$query")
        parseItems(json) to json.optInt("TotalRecordCount", 0)
    }

    suspend fun genres(parentId: String, includeTypes: String?): List<UserView> = withContext(Dispatchers.IO) {
        val query = "Genres?parentId=${urlEncode(parentId)}&userId=${user()}&sortBy=SortName" +
            (includeTypes?.let { "&includeItemTypes=${urlEncode(it)}" } ?: "")
        val values = getJson(query).optJSONArray("Items") ?: JSONArray()
        (0 until values.length()).mapNotNull { i ->
            values.optJSONObject(i)?.let { json ->
                val id = json.optString("Id")
                val name = json.optString("Name")
                if (id.isBlank() || name.isBlank()) null else UserView(id, name, "")
            }
        }
    }

    suspend fun item(itemId: String): MediaItem = withContext(Dispatchers.IO) {
        MediaItem.from(userJson("Items/${urlEncode(itemId)}?userId=${user()}", "Users/${user()}/Items/${urlEncode(itemId)}"))
    }

    /** One page of a container's children plus the total count; a series returns all of its seasons at once. */
    suspend fun children(container: MediaItem, startIndex: Int = 0, limit: Int = CHILD_PAGE_SIZE): Pair<List<MediaItem>, Int> = withContext(Dispatchers.IO) {
        if (container.isSeries) {
            val seasons = parseItems(getJson("Shows/${urlEncode(container.id)}/Seasons?UserId=${urlEncode(session.userId)}"))
            seasons to seasons.size
        } else {
            items(parentId = container.id, startIndex = startIndex, recursive = false, sortBy = "IndexNumber", limit = limit)
        }
    }

    /**
     * Negotiates playback. The first PlaybackInfo carries no source pin, so every
     * version of the item is on the table and the one that direct-plays wins over
     * one that needs the server; language preferences are then resolved against
     * that source's own streams and sent in a second negotiation only when they
     * differ from the server's defaults.
     */
    suspend fun playbackInfo(itemId: String, audioIndex: Int? = null, subtitleIndex: Int? = null, pinnedTransport: ServerTransport? = null, mediaSourceId: String? = null): PlaybackSource = withContext(Dispatchers.IO) {
        val bound = pinnedTransport ?: transportSnapshot()
        val credentials = bound.credentials()
        val effective = preferences()
        val profile = DeviceProfile.build(effective.maxStreamingBitrate, effective.maxVideoWidth, effective.maxVideoHeight, baselineOnly = zepigit.firefin.app.BuildConfig.DEBUG && Build.FINGERPRINT.orEmpty().startsWith("generic"))
        suspend fun negotiate(sourceId: String?, audio: Int?, subtitle: Int?): JSONObject {
            val body = JSONObject().put("DeviceProfile", profile).put("MaxStreamingBitrate", effective.maxStreamingBitrate).put("AutoOpenLiveStream", false)
            sourceId?.let { body.put("MediaSourceId", it) }
            audio?.let { body.put("AudioStreamIndex", it) }
            subtitle?.let { body.put("SubtitleStreamIndex", it) }
            return JSONObject(bound.json("Items/${urlEncode(itemId)}/PlaybackInfo?UserId=${urlEncode(credentials.userId)}&AutoOpenLiveStream=false&MaxStreamingBitrate=${effective.maxStreamingBitrate}", "POST", body.toString()))
        }
        var selectedAudio = audioIndex
        var selectedSubtitle = subtitleIndex
        if (effective.stored.subtitleLanguage.isBlank() && selectedSubtitle == null) selectedSubtitle = -1
        var json = negotiate(mediaSourceId, selectedAudio, selectedSubtitle)
        var source = pickSource(json, mediaSourceId) ?: throw LocalizedIOException(R.string.error_no_playable_source, "No playable media source.")
        val id = source.optString("Id").takeIf { it.isNotBlank() } ?: itemId
        val wantsAudio = selectedAudio == null && effective.stored.audioLanguage.isNotBlank()
        val wantsSubtitle = selectedSubtitle == null && effective.stored.subtitleLanguage.isNotBlank()
        if (wantsAudio || wantsSubtitle) {
            val tracks = zepigit.firefin.app.playback.StreamSelection.tracks(source.optJSONArray("MediaStreams") ?: JSONArray())
            if (wantsAudio) selectedAudio = zepigit.firefin.app.playback.StreamSelection.index(tracks, "Audio", effective.stored.audioLanguage)
            if (wantsSubtitle) selectedSubtitle = zepigit.firefin.app.playback.StreamSelection.index(tracks, "Subtitle", effective.stored.subtitleLanguage)
            val serverAudio = if (source.has("DefaultAudioStreamIndex")) source.optInt("DefaultAudioStreamIndex") else null
            val serverSubtitle = if (source.has("DefaultSubtitleStreamIndex")) source.optInt("DefaultSubtitleStreamIndex") else -1
            if (wantsAudio && selectedAudio != null && selectedAudio != serverAudio || wantsSubtitle && selectedSubtitle != null && selectedSubtitle != serverSubtitle) {
                json = negotiate(id, selectedAudio, selectedSubtitle)
                source = pickSource(json, id) ?: throw LocalizedIOException(R.string.error_no_playable_source, "No playable media source.")
            }
        }
        val transcode = source.optString("TranscodingUrl").takeIf { it.isNotBlank() }
        val url = when {
            source.optBoolean("SupportsDirectPlay") && transcode == null -> Urls.directStreamUrl(credentials.serverUrl, itemId, id)
            transcode != null -> Urls.resolveRelative(credentials.serverUrl, transcode)
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
            playMethod = if (transcode == null) "DirectPlay" else if (source.optBoolean("SupportsDirectStream") && !source.optBoolean("SupportsDirectPlay")) "DirectStream" else "Transcode",
            audioIndex = selectedAudio,
            subtitleIndex = selectedSubtitle,
        )
    }

    /** Direct play first, then any source the server negotiated a URL for; flag-only sources come last so the URL error names them. */
    private fun pickSource(json: JSONObject, pinned: String?): JSONObject? {
        val sources = json.optJSONArray("MediaSources") ?: JSONArray()
        val candidates = (0 until sources.length()).mapNotNull { sources.optJSONObject(it) }.filter { pinned == null || it.optString("Id") == pinned }
        return candidates.firstOrNull { it.optBoolean("SupportsDirectPlay") }
            ?: candidates.firstOrNull { it.optString("TranscodingUrl").isNotBlank() }
            ?: candidates.firstOrNull { it.optBoolean("SupportsDirectStream") || it.optBoolean("SupportsTranscoding") }
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
        userText("UserFavoriteItems/${urlEncode(itemId)}?userId=${user()}", "Users/${user()}/FavoriteItems/${urlEncode(itemId)}", if (favorite) "POST" else "DELETE")
    }
    suspend fun setPlayed(itemId: String, played: Boolean) = withContext(Dispatchers.IO) {
        userText("UserPlayedItems/${urlEncode(itemId)}?userId=${user()}", "Users/${user()}/PlayedItems/${urlEncode(itemId)}", if (played) "POST" else "DELETE")
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
    private suspend fun transportText(path: String, method: String = "GET", body: String? = null): String { syncTransport(); return transport.json(path, method, body) }
    private fun parseItems(json: JSONObject): List<MediaItem> { val arr = json.optJSONArray("Items") ?: return emptyList(); return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(MediaItem::from) } }
    private fun parseArray(value: String): List<MediaItem> { val arr = JSONArray(value); return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(MediaItem::from) } }
    private fun syncTransport() { transport.update(ServerCredentials(session.serverUrl, session.userId, session.accessToken)) }
    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun user(): String = urlEncode(session.userId)

    companion object {
        const val VERSION = BuildConfig.VERSION_NAME
        const val CHILD_PAGE_SIZE = 60
        /** Upper bound for the whole optional Seerr step (probe plus sign-in) after a Jellyfin login. */
        const val SEERR_BRIDGE_TIMEOUT_MS = 10_000L
    }
}
