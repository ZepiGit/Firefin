package zepigit.firefin.app.data

import java.io.IOException
import java.net.URLEncoder
import okio.ByteString.Companion.decodeBase64
import org.json.JSONObject

class SeerrException(val status: Int) : IOException(when (status) {
    401 -> "Seerr-Sitzung abgelaufen. Bitte erneut verbinden."
    403 -> "Keine Berechtigung oder Anfrage-Limit erreicht."
    409 -> "Dieser Titel wurde bereits angefragt."
    502 -> "Seerr ist über Moonbase momentan nicht erreichbar."
    503 -> "Seerr ist auf diesem Server deaktiviert."
    else -> "Seerr-Anfrage fehlgeschlagen (HTTP $status)."
})

/** Each screen owns a snapshot; no password, cookie or Seerr API key is persisted. */
class SeerrClient(private val transport: ServerTransport) {
    private var prefix = "Moonfin/Seerr"
    var user = SeerrUser(0, 0)
        private set

    suspend fun connect(): Boolean {
        val ping = call("Moonfin/Ping", optional = true) ?: throw IOException("Moonbase ist auf diesem Server nicht verfügbar.")
        if (!ping.optBoolean("installed", ping.optBoolean("Installed", true))) throw IOException("Moonbase nicht installiert.")
        val config = call("$prefix/Config", optional = true) ?: run {
            prefix = "Moonfin/Jellyseerr"
            call("$prefix/Config")!!
        }
        if (!config.optBoolean("enabled", config.optBoolean("Enabled", false)) ||
            !config.optBoolean("userEnabled", config.optBoolean("UserEnabled", true))) throw SeerrException(503)
        val status = call("$prefix/Status")!!
        if (!status.optBoolean("authenticated", status.optBoolean("Authenticated", false))) return false
        refreshUser()
        return true
    }

    suspend fun login(username: String = "", password: String = "", quickConnect: Boolean = false, local: Boolean = false) {
        val body = JSONObject().put("authType", if (quickConnect) "quickconnect" else if (local) "local" else "jellyfin")
        if (!quickConnect) body.put("username", username).put("password", password)
        val result = call("$prefix/Login", "POST", body)!!
        if (!result.optBoolean("success", result.optBoolean("Success", false))) throw IOException("Seerr-Anmeldung fehlgeschlagen.")
        refreshUser()
    }

    private suspend fun refreshUser() {
        val me = api("auth/me")
        user = SeerrUser(me.optInt("id"), me.optLong("permissions"))
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
        return SeerrMedia.from(api("$type/$id"), type) ?: throw IOException("Ungültiger Seerr-Titel.")
    }

    enum class RequestResult { CREATED, NOTHING_NEW, ALREADY_REQUESTED, UNKNOWN }
    suspend fun request(media: SeerrMedia, seasons: List<Int>): RequestResult {
        require(user.canRequest(media.type)) { "Keine Berechtigung für diese Medienart." }
        val payload = media.requestBody(seasons).toString()
        val response = try { transport.response("$prefix/Api/request", "POST", payload) }
        catch (_: IOException) { return RequestResult.UNKNOWN }
        if (response.status == 401) {
            if (transport.response("Users/Me").status == 401) throw SessionExpiredException()
            throw SeerrException(401)
        }
        return when {
            response.status == 202 -> RequestResult.UNKNOWN
            response.status in 200..299 -> if (runCatching { parseEnvelope(response.body).optInt("id") > 0 }.getOrDefault(false)) RequestResult.CREATED else RequestResult.UNKNOWN
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
        if (envelope.length > MAX_ENVELOPE_CHARS) throw IOException("Moonbase-Antwort ist zu groß.")
        val decoded = envelope.decodeBase64()?.toByteArray() ?: throw IOException("Moonbase-Antwort ist keine gültige Base64-Antwort.")
        if (decoded.size > MAX_ENVELOPE_BYTES) throw IOException("Moonbase-Antwort ist zu groß.")
        return JSONObject(String(decoded, Charsets.UTF_8))
    }

    private companion object {
        const val MAX_ENVELOPE_CHARS = 12 * 1024 * 1024
        const val MAX_ENVELOPE_BYTES = 8 * 1024 * 1024
    }

    private suspend fun api(path: String) = call("$prefix/Api/$path")!!
    private suspend fun call(path: String, method: String = "GET", body: JSONObject? = null, optional: Boolean = false): JSONObject? {
        val result = transport.response(path, method, body?.toString())
        if (optional && result.status == 404) return null
        if (result.status == 401 && transport.response("Users/Me").status == 401) throw SessionExpiredException()
        if (result.status !in 200..299) throw SeerrException(result.status)
        return try {
            parseEnvelope(result.body)
        } catch (e: IOException) { throw e } catch (_: Exception) { throw IOException("Moonbase hat keine gültige JSON-Antwort geliefert.") }
    }
}
