package zepigit.firefin.app.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

object Urls {
    fun normalizeServer(input: String): String {
        val value = input.trim()
        require(value.isNotEmpty()) { "Enter a server address." }
        val qualified = if (value.contains("://")) value else "https://$value"
        val url = qualified.toHttpUrl()
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
            "Server addresses must not include credentials, query parameters or fragments."
        }
        return url.toString().trimEnd('/')
    }

    fun imageUrl(
        baseUrl: String, itemId: String, imageType: String, maxWidth: Int,
        tag: String?, accessToken: String = "", maxHeight: Int? = null,
    ): String {
        val builder = (normalizeServer(baseUrl) + "/").toHttpUrl().newBuilder()
            .addPathSegment("Items").addPathSegment(itemId).addPathSegment("Images").addPathSegment(imageType)
            .addQueryParameter("maxWidth", maxWidth.coerceIn(1, 960).toString())
        maxHeight?.let { builder.addQueryParameter("maxHeight", it.coerceIn(1, 960).toString()) }
        builder.addQueryParameter("quality", "90")
        if (!tag.isNullOrBlank()) builder.addQueryParameter("tag", tag)
        // Authentication belongs to the origin-bound HTTP client, never a cache key or URI.
        return builder.build().toString()
    }

    fun directStreamUrl(baseUrl: String, itemId: String, mediaSourceId: String, accessToken: String = ""): String =
        (normalizeServer(baseUrl) + "/").toHttpUrl().newBuilder()
            .addPathSegment("Videos").addPathSegment(itemId).addPathSegment("stream")
            .addQueryParameter("static", "true").addQueryParameter("MediaSourceId", mediaSourceId).build().toString()

    fun withStartTimeTicks(url: String, ticks: Long): String {
        require(ticks >= 0)
        return replaceQuery(url.toHttpUrl(), "StartTimeTicks", ticks.toString()).toString()
    }

    fun replaceQuery(url: HttpUrl, key: String, value: String): HttpUrl {
        val builder = url.newBuilder()
        url.queryParameterNames.filter { it.equals(key, ignoreCase = true) }.forEach(builder::removeAllQueryParameters)
        return builder.addQueryParameter(key, value).build()
    }

    fun stripCredentials(url: String): String {
        val parsed = url.toHttpUrl()
        val builder = parsed.newBuilder()
        parsed.queryParameterNames.filter { it.lowercase() in setOf("api_key", "apikey", "access_token", "token") }
            .forEach(builder::removeAllQueryParameters)
        return builder.build().toString()
    }

    fun resolveRelative(baseUrl: String, path: String): String {
        val base = (normalizeServer(baseUrl) + "/").toHttpUrl()
        require(!path.startsWith("//")) { "Network-path URLs are not permitted." }
        val prefix = base.encodedPath.trimEnd('/')
        val alreadyPrefixed = prefix.isNotEmpty() && (path == prefix || path.startsWith("$prefix/") || path.startsWith("$prefix?"))
        val candidate = when {
            path.contains("://") -> path.toHttpUrl()
            alreadyPrefixed -> base.resolve(path)
            else -> base.resolve(path.removePrefix("/"))
        } ?: throw IllegalArgumentException("Invalid media URL")
        require(candidate.scheme == base.scheme && candidate.host == base.host && candidate.port == base.port &&
            candidate.username.isEmpty() && candidate.password.isEmpty() &&
            (prefix.isEmpty() || candidate.encodedPath == prefix || candidate.encodedPath.startsWith("$prefix/"))) {
            "Media URL is outside the configured server."
        }
        return candidate.toString()
    }
}
