package zepigit.firefin.app.util

/** Jellyfin URL helpers: server base normalization, API and image URLs. */
object Urls {

    /** Normalizes a user-provided server address to "<scheme>://host[:port][/subpath]" without trailing slash. */
    fun normalizeServer(input: String): String {
        var url = input.trim()
        if (url.isEmpty()) throw IllegalArgumentException("Empty server URL")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        while (url.endsWith("/")) url = url.dropLast(1)
        return url
    }

    fun imageUrl(
        baseUrl: String,
        itemId: String,
        imageType: String,
        maxWidth: Int,
        tag: String?,
        accessToken: String,
        maxHeight: Int? = null,
    ): String {
        val sb = StringBuilder(baseUrl)
            .append("/Items/").append(itemId)
            .append("/Images/").append(imageType)
            .append("?maxWidth=").append(maxWidth)
        if (maxHeight != null) sb.append("&maxHeight=").append(maxHeight)
        sb.append("&quality=90")
        if (!tag.isNullOrEmpty()) sb.append("&tag=").append(tag)
        if (accessToken.isNotEmpty()) sb.append("&api_key=").append(accessToken)
        return sb.toString()
    }

    fun directStreamUrl(baseUrl: String, itemId: String, mediaSourceId: String, accessToken: String): String =
        "$baseUrl/Videos/$itemId/stream?static=true&MediaSourceId=$mediaSourceId&api_key=$accessToken"

    /**
     * Transcoding paths returned by PlaybackInfo are root-relative; they may or
     * may not already contain the base URL's subpath. Join at the origin when
     * the subpath is present, otherwise at the full base URL.
     */
    fun resolveRelative(baseUrl: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = baseUrl.trimEnd('/')
        val suffix = if (path.startsWith("/")) path else "/$path"
        val basePath = runCatching { java.net.URI(baseUrl).rawPath ?: "" }.getOrDefault("")
        return if (basePath.isNotEmpty() && suffix.startsWith(basePath)) {
            val origin = baseUrl.removeSuffix(basePath).trimEnd('/')
            origin + suffix
        } else {
            base + suffix
        }
    }
}
