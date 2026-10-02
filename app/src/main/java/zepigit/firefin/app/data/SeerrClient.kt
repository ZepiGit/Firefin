package zepigit.firefin.app.data

import java.io.IOException
import java.net.URLEncoder
import okio.ByteString.Companion.decodeBase64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import zepigit.firefin.app.R

class SeerrException(val status: Int) : LocalizedIOException(
    when (status) {
        401 -> R.string.seerr_error_401
        403 -> R.string.seerr_error_403
        409 -> R.string.seerr_error_409
        502 -> R.string.seerr_error_502
        503 -> R.string.seerr_error_503
        else -> R.string.seerr_error_other
    },
    when (status) {
        401 -> "Seerr session expired. Please reconnect."
        403 -> "No permission or request limit reached."
        409 -> "This title has already been requested."
        502 -> "Seerr is currently unreachable through Moonbase."
        503 -> "Seerr is disabled on this server."
        else -> "Seerr request failed (HTTP $status)."
    },
    if (status in setOf(401, 403, 409, 502, 503)) emptyList() else listOf(status),
)

/** No password, cookie or Seerr API key is persisted. */
class SeerrClient(private var transport: ServerTransport) {
    private var prefix = "Moonfin/Jellyseerr"
    private var probeComplete = false
    private var authenticated = false
    private val stateMutex = Mutex()
    var user = SeerrUser(0, 0)
        private set

    private fun applyIdentity(json: JSONObject, allowBareId: Boolean = false): Boolean {
        val keys = if (allowBareId) {
            listOf("id", "userId", "seerrUserId", "jellyseerrUserId", "JellyseerrUserId")
        } else {
            listOf("userId", "seerrUserId", "jellyseerrUserId", "JellyseerrUserId")
        }
        val id = keys.firstNotNullOfOrNull { key -> json.optInt(key, 0).takeIf { it > 0 } } ?: 0
        val hasPermissions = json.has("permissions") || json.has("Permissions")
        if (id > 0 && hasPermissions) {
            val permissions = json.optLong("permissions", json.optLong("Permissions", 0L))
            user = SeerrUser(id, permissions)
            return true
        }
        return false
    }

    fun resetTransport(transport: ServerTransport) {
        this.transport = transport
        prefix = "Moonfin/Jellyseerr"
        probeComplete = false
        authenticated = false
        user = SeerrUser(0, 0)
    }

    fun invalidate() {
        probeComplete = false
        authenticated = false
        user = SeerrUser(0, 0)
    }

    suspend fun connect(): Boolean = stateMutex.withLock {
        if (probeComplete) return@withLock authenticated
        val ping = call("Moonfin/Ping", optional = true)
            ?: throw LocalizedIOException(R.string.moonbase_unavailable, "Moonbase is not available on this server.")
        if (!ping.optBoolean("installed", ping.optBoolean("Installed", true))) {
            throw LocalizedIOException(R.string.moonbase_not_installed, "Moonbase is not installed.")
        }
        val config = call("$prefix/Config", optional = true) ?: run {
            prefix = "Moonfin/Seerr"
            call("$prefix/Config")!!
        }
        if (!config.optBoolean("enabled", config.optBoolean("Enabled", false)) ||
            !config.optBoolean("userEnabled", config.optBoolean("UserEnabled", true))) throw SeerrException(503)
        val status = call("$prefix/Status")!!
        if (!status.optBoolean("authenticated", status.optBoolean("Authenticated", false))) {
            probeComplete = false
            authenticated = false
            user = SeerrUser(0, 0)
            return@withLock false
        }
        authenticated = applyIdentity(status, allowBareId = false) || try {
            refreshUser()
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: SeerrException) {
            false
        }
        probeComplete = authenticated
        authenticated
    }

    suspend fun login(username: String = "", password: String = "", quickConnect: Boolean = false, local: Boolean = false) = stateMutex.withLock {
        val body = JSONObject().put("authType", if (quickConnect) "quickconnect" else if (local) "local" else "jellyfin")
        if (!quickConnect) body.put("username", username).put("password", password)
        val result = try { call("$prefix/Login", "POST", body)!! } catch (e: SeerrException) {
            if (e.status != 404) throw e
            prefix = if (prefix == "Moonfin/Jellyseerr") "Moonfin/Seerr" else "Moonfin/Jellyseerr"
            call("$prefix/Login", "POST", body)!!
        }
        if (!result.optBoolean("success", result.optBoolean("Success", false))) throw LocalizedIOException(R.string.seerr_login_failed, "Seerr sign-in failed.")
        if (!applyIdentity(result, allowBareId = false)) {
            val status = call("$prefix/Status")
            if (status == null || !applyIdentity(status, allowBareId = false)) refreshUser()
        }
        probeComplete = true
        authenticated = true
    }

    private suspend fun refreshUser() {
        val me = api("auth/me")
        if (!me.has("permissions") && !me.has("Permissions")) throw SeerrException(401)
        user = SeerrUser(me.optInt("id"), me.optLong("permissions", me.optLong("Permissions", 0L)))
        if (user.id <= 0) throw SeerrException(401)
    }

    suspend fun page(category: String, page: Int, query: String = ""): SeerrPage {
        require(page > 0)
        val route = if (query.isNotBlank()) "search?query=${URLEncoder.encode(query, "UTF-8")}&page=$page" else when (category) {
            "movie" -> "discover/movies?page=$page"
            "tv" -> "discover/tv?page=$page"
            else -> "discover/trending?page=$page"
        }
        return SeerrPage.from(api(route), if (query.isBlank() && category in setOf("movie", "tv")) category else null)
    }

    suspend fun detail(type: String, id: Int): SeerrMedia {
        require(type in setOf("movie", "tv") && id > 0)
        return SeerrMedia.from(api("$type/$id"), type) ?: throw LocalizedIOException(R.string.seerr_invalid_title, "Invalid Seerr title.")
    }

    enum class RequestResult { CREATED, NOTHING_NEW, ALREADY_REQUESTED, UNKNOWN }
    suspend fun request(media: SeerrMedia, seasons: List<Int>): RequestResult {
        require(user.canRequest(media.type)) { "No permission for this media type." }
        val payload = media.requestBody(seasons).toString()
        val response = try { transport.response("$prefix/Api/request", "POST", payload) }
        catch (_: IOException) { return RequestResult.UNKNOWN }
        if (response.status == 401) {
            invalidate()
            if (transport.response("Users/Me").status == 401) throw SessionExpiredException()
            throw SeerrException(401)
        }
        return when {
            response.status == 202 -> RequestResult.UNKNOWN
            response.status in 200..299 -> if (runCatching { parseEnvelope(response.body).let { it.optInt("id", it.optInt("Id", 0)) > 0 } }.getOrDefault(false)) RequestResult.CREATED else RequestResult.UNKNOWN
            response.status == 409 -> RequestResult.ALREADY_REQUESTED
            response.status >= 500 -> RequestResult.UNKNOWN
            else -> throw SeerrException(response.status)
        }
    }

    suspend fun requests(skip: Int): Pair<List<SeerrRequest>, Int> {
        require(user.id > 0 && skip >= 0)
        val json = api("request?take=20&skip=$skip&requestedBy=${user.id}")
        val array = json.optJSONArray("results")
        val list = if (array == null) emptyList() else (0 until array.length()).mapNotNull {
            val request = array.optJSONObject(it) ?: return@mapNotNull null
            if (request.optJSONObject("requestedBy")?.optInt("id") != user.id) null else SeerrRequest.from(request)
        }
        return list to (json.optJSONObject("pageInfo")?.optInt("results") ?: list.size)
    }

    private fun parseEnvelope(raw: String): JSONObject {
        val outer = JSONObject(raw)
        val envelope = outer.optString("FileContents").takeIf { it.isNotBlank() } ?: return outer
        if (envelope.length > MAX_ENVELOPE_CHARS) throw tooLarge()
        val decoded = envelope.decodeBase64()?.toByteArray()
            ?: throw LocalizedIOException(R.string.moonbase_invalid_encoding, "The Moonbase response is not valid Base64.")
        if (decoded.size > MAX_ENVELOPE_BYTES) throw tooLarge()
        return JSONObject(String(decoded, Charsets.UTF_8))
    }

    private fun tooLarge() = LocalizedIOException(R.string.moonbase_response_too_large, "The Moonbase response is too large.")

    private companion object {
        const val MAX_ENVELOPE_CHARS = 12 * 1024 * 1024
        const val MAX_ENVELOPE_BYTES = 8 * 1024 * 1024
    }

    private suspend fun api(path: String) = call("$prefix/Api/$path")!!
    private suspend fun call(path: String, method: String = "GET", body: JSONObject? = null, optional: Boolean = false): JSONObject? {
        val result = transport.response(path, method, body?.toString())
        if (optional && result.status == 404) return null
        if (result.status == 401) {
            invalidate()
            if (transport.response("Users/Me").status == 401) throw SessionExpiredException()
            throw SeerrException(401)
        }
        if (result.status !in 200..299) throw SeerrException(result.status)
        return try {
            parseEnvelope(result.body)
        } catch (e: IOException) { throw e } catch (_: Exception) {
            throw LocalizedIOException(R.string.moonbase_invalid_json, "Moonbase did not return a valid JSON response.")
        }
    }
}
