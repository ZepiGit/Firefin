package zepigit.firefin.app

import org.junit.Assert.assertEquals
import org.junit.Test
import zepigit.firefin.app.data.SessionStore
import zepigit.firefin.app.preferences.PreferenceStore
import zepigit.firefin.app.preferences.StoredPreferences

class PreferenceStoreTest {
    @Test fun `home presets round trip and stay isolated by account`() {
        val session = SessionStore(InMemoryPrefs()).apply { serverUrl = "https://example.com"; userId = "a" }
        val prefs = InMemoryPrefs()
        val store = PreferenceStore(prefs, session)
        assertEquals(StoredPreferences(), store.stored())
        val old = StoredPreferences(homeSectionOrder = listOf("resume", "nextUp", "latest"), backdropEnabled = false, preferredHeight = 720, subtitleSize = 2, autoPlayNext = false, audioLanguage = "fr", subtitleLanguage = "pt")
        store.save(old)
        assertEquals(old, PreferenceStore(prefs, session).stored())
        session.userId = "b"
        assertEquals(StoredPreferences(), store.stored())
        session.userId = "a"
        assertEquals(old, store.stored())
    }
}
