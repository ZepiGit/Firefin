package zepigit.firefin.app.images

/**
 * Port of the FireTV32 image size policy: fixed server request classes, a
 * decode-bucket ladder, and the 1..4096 input clamp. Server requests cap
 * posters at 320x480; landscape/backdrop classes cap by width only.
 *
 * Parity note: the legacy decoder used width-only sampling for portrait art
 * and height-only sampling for landscape art; both bound the bitmap to the
 * same bucket footprint. The native loader samples by width against the same
 * bucket ladder, which yields equal or smaller bitmaps and preserves the
 * single-axis cache-key property.
 */
object ArtworkPolicy {
    const val POSTER_WIDTH = 320
    const val POSTER_MAX_HEIGHT = 480
    const val LANDSCAPE_WIDTH = 640
    const val BACKDROP_WIDTH = 960
    const val NON_TV_BACKDROP_WIDTH = 1920

    val DECODE_BUCKETS = intArrayOf(160, 240, 320, 480, 640, 960)

    /** Smallest bucket >= requested width (clamped to 1..4096 like the legacy policy). */
    fun decodeBucket(requestedWidth: Int): Int {
        val clamped = requestedWidth.coerceIn(1, 4096)
        for (bucket in DECODE_BUCKETS) {
            if (clamped <= bucket) return bucket
        }
        return DECODE_BUCKETS.last()
    }
}
