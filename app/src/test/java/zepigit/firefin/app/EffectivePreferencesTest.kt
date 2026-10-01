package zepigit.firefin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zepigit.firefin.app.playback.DeviceProfile
import zepigit.firefin.app.preferences.StoredPreferences
import zepigit.firefin.app.preferences.deriveEffective
import zepigit.firefin.app.preferences.serializeStored

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
        assertEquals(1280, effective.maxVideoWidth)
        assertEquals(720, effective.maxVideoHeight)
        assertEquals(4_000_000L, effective.maxStreamingBitrate)
        assertFalse(effective.cardFocusExpansion)
        assertFalse(effective.mediaBarEnabled)
        assertEquals(0, effective.blurAmount)
    }

    @Test
    fun `stored preferences are preserved untouched inside the effective view`() {
        val stored = StoredPreferences(backdropEnabled = false, homeSectionOrder = listOf("nextUp", "resume"))
        val effective = deriveEffective(stored, 1280, 720, 4_000_000L)
        assertEquals(stored, effective.stored)
        assertTrue(effective.stored.homeSectionOrder.first() == "nextUp")
    }

    @Test
    fun `serialization contains only stored values, never device limits`() {
        val map = serializeStored(StoredPreferences(backdropEnabled = false))
        assertEquals(
            mapOf("backdropEnabled" to "false", "homeSectionOrder" to "resume,nextUp,latest"),
            map,
        )
        assertFalse(map.keys.any { it.contains("max", ignoreCase = true) || it.contains("blur") })
    }
}
