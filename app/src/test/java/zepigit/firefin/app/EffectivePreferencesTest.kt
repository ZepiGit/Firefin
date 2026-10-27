package zepigit.firefin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zepigit.firefin.app.playback.DeviceProfile
import zepigit.firefin.app.preferences.StoredPreferences
import zepigit.firefin.app.preferences.deriveEffective

class EffectivePreferencesTest {

    @Test
    fun `device limits come from the device profile, not stored prefs`() {
        val stored = StoredPreferences()
        val effective = deriveEffective(
            stored,
            DeviceProfile.MAX_WIDTH,
            DeviceProfile.MAX_HEIGHT,
            DeviceProfile.MAX_BITRATE,
        )
        assertEquals(1920, effective.maxVideoWidth)
        assertEquals(1080, effective.maxVideoHeight)
        assertEquals(4_000_000L, effective.maxStreamingBitrate)
    }

    @Test
    fun `stored preferences are preserved untouched inside the effective view`() {
        val stored = StoredPreferences(backdropEnabled = false, homeSectionOrder = listOf("nextUp", "resume"))
        val effective = deriveEffective(stored, 1280, 720, 4_000_000L)
        assertEquals(stored, effective.stored)
        assertTrue(effective.stored.homeSectionOrder.first() == "nextUp")
    }

    @Test
    fun `higher stored playback values survive while effective limits clamp and lower limits apply`() {
        val high = StoredPreferences(preferredBitrate = 20_000_000, preferredHeight = 2160)
        val effective = deriveEffective(high, 1280, 720, 4_000_000)
        assertEquals(20_000_000L, effective.stored.preferredBitrate)
        assertEquals(4_000_000L, effective.maxStreamingBitrate)
        assertEquals(720, effective.maxVideoHeight)
        val low = deriveEffective(high.copy(preferredBitrate = 2_000_000, preferredHeight = 480), 1280, 720, 4_000_000)
        assertEquals(2_000_000L, low.maxStreamingBitrate)
        assertEquals(480, low.maxVideoHeight)
    }
}
