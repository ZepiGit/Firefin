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
    val homeSectionOrder: List<String> = listOf("resume", "nextUp", "favorites", "latest"),
    val preferredBitrate: Long = 4_000_000L,
    val preferredHeight: Int = 1080,
    val audioLanguage: String = "",
    val subtitleLanguage: String = "",
    val subtitleSize: Int = 0,
    val autoPlayNext: Boolean = true,
)

data class EffectiveDevicePreferences(
    val stored: StoredPreferences,
    // Device-enforced runtime limits, deliberately not stored and not synced.
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
    maxVideoWidth = minOf(maxVideoWidth, if (stored.preferredHeight in 1..480) 854 else if (stored.preferredHeight <= 720) 1280 else 1920),
    maxVideoHeight = minOf(maxVideoHeight, stored.preferredHeight.takeIf { it > 0 } ?: maxVideoHeight),
    maxStreamingBitrate = minOf(maxStreamingBitrate, stored.preferredBitrate.takeIf { it > 0 } ?: maxStreamingBitrate),
)
