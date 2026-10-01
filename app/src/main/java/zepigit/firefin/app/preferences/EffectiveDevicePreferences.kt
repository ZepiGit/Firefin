package zepigit.firefin.app.preferences

/**
 * Stored (user/synced) preferences vs. effective device behavior.
 *
 * Ported rule from the legacy FireTV32 app: locally enforced device limits
 * (lean AFTT runtime) must never be written back into stored/synced settings,
 * so a device cap cannot reconfigure the server profile or other devices.
 */
data class StoredPreferences(
    val backdropEnabled: Boolean = true,
    val homeSectionOrder: List<String> = listOf("resume", "nextUp", "latest"),
)

data class EffectiveDevicePreferences(
    val stored: StoredPreferences,
    // Device-enforced runtime limits — deliberately NOT stored and NOT synced.
    val cardFocusExpansion: Boolean = false,
    val mediaBarEnabled: Boolean = false,
    val episodePreviewEnabled: Boolean = false,
    val blurAmount: Int = 0,
    val maxVideoWidth: Int,
    val maxVideoHeight: Int,
    val maxStreamingBitrate: Long,
)

/**
 * Derives effective behavior from stored preferences without mutating them.
 * The returned object is the only thing the UI/runtime may read.
 */
fun deriveEffective(
    stored: StoredPreferences,
    maxVideoWidth: Int,
    maxVideoHeight: Int,
    maxStreamingBitrate: Long,
): EffectiveDevicePreferences = EffectiveDevicePreferences(
    stored = stored,
    maxVideoWidth = maxVideoWidth,
    maxVideoHeight = maxVideoHeight,
    maxStreamingBitrate = maxStreamingBitrate,
)

/** Serializes ONLY stored preferences (device limits are excluded by type). */
fun serializeStored(prefs: StoredPreferences): Map<String, String> = mapOf(
    "backdropEnabled" to prefs.backdropEnabled.toString(),
    "homeSectionOrder" to prefs.homeSectionOrder.joinToString(","),
)
