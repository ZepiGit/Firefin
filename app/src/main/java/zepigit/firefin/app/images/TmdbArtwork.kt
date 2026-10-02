package zepigit.firefin.app.images

/** TMDB's published CDN sizes are 342/780; decode still uses our 320/960 buckets. */
object TmdbArtwork {
    fun url(path: String?, backdrop: Boolean = false): String? {
        if (path == null || !path.matches(Regex("/[A-Za-z0-9_-]+\\.(jpg|png|webp)"))) return null
        return "https://image.tmdb.org/t/p/${if (backdrop) "w780" else "w342"}$path"
    }
}
