package zepigit.firefin.app

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zepigit.firefin.app.data.JellyfinClient
import zepigit.firefin.app.data.MediaItem
import zepigit.firefin.app.data.ServerResponseException
import zepigit.firefin.app.data.SessionStore
import java.util.concurrent.TimeUnit

/**
 * JVM contract tests against a short-lived MockWebServer (test library only,
 * no standing Jellyfin server).
 */
class JellyfinClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: JellyfinClient
    private lateinit var session: SessionStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        session = SessionStore(InMemoryPrefs())
        client = JellyfinClient(session)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login normalizes url and stores session`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"AccessToken":"tok123","ServerId":"srv1",
                    "User":{"Id":"u1","Name":"testuser","ServerId":"srv1"}}""",
            ),
        )
        val json = client.login("   ${server.url("/").toString().trimEnd('/')}/   ", "testuser", "pw")
        assertEquals("tok123", json.getString("AccessToken"))
        assertEquals(server.url("/").toString().trimEnd('/'), session.serverUrl)
        assertEquals("u1", session.userId)
        assertEquals("tok123", session.accessToken)

        val recorded = server.takeRequest()
        assertEquals("/Users/AuthenticateByName", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"Username\":\"testuser\""))
        assertTrue(body.contains("\"Pw\":\"pw\""))
        assertTrue(recorded.getHeader("Authorization")!!.startsWith("MediaBrowser Client=\"Firefin\""))
    }

    @Test
    fun `playbackInfo sends device profile with legacy ceilings`() = runBlocking {
        session.serverUrl = server.url("/").toString().trimEnd('/')
        session.userId = "u1"
        session.accessToken = "tok"
        server.enqueue(
            MockResponse().setBody(
                """{"PlaySessionId":"ps1","MediaSources":[{"Id":"ms1","Container":"mkv",
                    "TranscodingUrl":"/videos/i1/hls1/master.m3u8?TranscodingJobId=j1"}]}""",
            ),
        )
        val source = client.playbackInfo("i1")
        assertEquals("ps1", source.playSessionId)
        assertTrue(source.isTranscode)
        assertEquals(
            server.url("/").toString().trimEnd('/') + "/videos/i1/hls1/master.m3u8?TranscodingJobId=j1",
            source.url,
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"MaxStreamingBitrate\":4000000"))
        assertTrue(body.contains("Firefin for Fire TV"))
        assertTrue(body.contains("\"Width\""))
        assertTrue(body.contains("\"1920\""))
        assertTrue(body.contains("\"1080\""))
    }

    @Test fun `preferred source languages are selected before transcode and subtitle delivery retained`() = runBlocking {
        session.serverUrl = server.url("/").toString().trimEnd('/')
        session.userId = "u1"; session.accessToken = "tok"
        client = JellyfinClient(session) {
            zepigit.firefin.app.preferences.deriveEffective(zepigit.firefin.app.preferences.StoredPreferences(audioLanguage = "de", subtitleLanguage = "de"), 1280, 720, 4_000_000)
        }
        server.enqueue(MockResponse().setBody("""{"MediaSources":[{"MediaStreams":[{"Type":"Audio","Index":1,"Language":"eng"},{"Type":"Audio","Index":2,"Language":"ger"},{"Type":"Subtitle","Index":4,"Language":"deu"}]}]}"""))
        server.enqueue(MockResponse().setBody("""{"PlaySessionId":"ps","MediaSources":[{"Id":"s","TranscodingUrl":"/v.m3u8","MediaStreams":[{"Type":"Subtitle","Index":4,"Codec":"srt","DeliveryUrl":"/sub.srt"}]}]}"""))
        val source = client.playbackInfo("i1")
        assertEquals("/Users/u1/Items/i1", server.takeRequest().path)
        val body = org.json.JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(2, body.getInt("AudioStreamIndex"))
        assertEquals(4, body.getInt("SubtitleStreamIndex"))
        assertEquals(server.url("/sub.srt").toString(), source.subtitleUrl)
        assertEquals("application/x-subrip", source.subtitleMime)
    }

    @Test fun `direct stream uses negotiated remux URL not static original`() = runBlocking {
        session.serverUrl = server.url("/").toString().trimEnd('/')
        session.userId = "u1"; session.accessToken = "tok"
        server.enqueue(MockResponse().setBody("""{"PlaySessionId":"ps","MediaSources":[{"Id":"s","SupportsDirectPlay":false,"SupportsDirectStream":true,"DirectStreamUrl":"/remux.ts"}]}"""))
        val source = client.playbackInfo("i1")
        assertEquals(server.url("/remux.ts").toString(), source.url)
        assertEquals("DirectStream", source.playMethod)
    }

    private fun signIn() {
        session.serverUrl = server.url("/").toString().trimEnd('/')
        session.userId = "u1"; session.accessToken = "tok"; session.userName = "testuser"
    }

    @Test fun `favorite and watched marks set with POST and clear with DELETE on the same path`() = runBlocking {
        signIn()
        repeat(4) { server.enqueue(MockResponse().setBody("{}")) }
        client.setFavorite("i1", true)
        client.setFavorite("i1", false)
        client.setPlayed("i1", true)
        client.setPlayed("i1", false)
        val requests = (1..4).map { server.takeRequest() }.map { it.method to it.path }
        assertEquals(
            listOf(
                "POST" to "/Users/u1/FavoriteItems/i1",
                "DELETE" to "/Users/u1/FavoriteItems/i1",
                "POST" to "/Users/u1/PlayedItems/i1",
                "DELETE" to "/Users/u1/PlayedItems/i1",
            ),
            requests,
        )
    }

    @Test fun `a rejected unmark is reported instead of pretending success`() = runBlocking {
        signIn()
        server.enqueue(MockResponse().setResponseCode(500))
        val failure = runCatching { client.setPlayed("i1", false) }.exceptionOrNull()
        assertTrue(failure is ServerResponseException)
    }

    @Test fun `container children are paged by start index and keep the server total`() = runBlocking {
        signIn()
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"e61","Type":"Episode","IndexNumber":61}],"TotalRecordCount":125}"""))
        val season = MediaItem.from(org.json.JSONObject("""{"Id":"s1","Type":"Season"}"""))
        val (page, total) = client.children(season, startIndex = 60)
        assertEquals(125, total)
        assertEquals("e61", page.single().id)
        val path = server.takeRequest().path!!
        assertTrue(path, path.contains("ParentId=s1") && path.contains("StartIndex=60") && path.contains("Limit=60") && path.contains("Recursive=false"))
    }

    @Test fun `recently added rows request the overview shown in the home preview`() = runBlocking {
        signIn()
        server.enqueue(MockResponse().setBody("[]"))
        client.latest("lib1")
        assertTrue(server.takeRequest().path!!.contains("Overview"))
    }

    @Test fun `logout revokes the token on the server and keeps the address for the next sign-in`() = runBlocking {
        signIn()
        val address = session.serverUrl
        server.enqueue(MockResponse().setResponseCode(204))
        client.logout()
        session.clear()
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/Sessions/Logout", request.path)
        assertTrue(request.getHeader("Authorization")!!.contains("Token=\"tok\""))
        assertFalse(session.isLoggedIn)
        assertEquals(address, session.serverUrl)
        assertEquals("testuser", session.userName)
        assertEquals("", session.accessToken)
    }

    /** Minimal in-memory SharedPreferences so SessionStore runs in JVM tests. */
    private class InMemoryPrefs : android.content.SharedPreferences {
        private val map = mutableMapOf<String, String>()
        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String?, defValue: Int) = defValue
        override fun getLong(key: String?, defValue: Long) = defValue
        override fun getFloat(key: String?, defValue: Float) = defValue
        override fun getBoolean(key: String?, defValue: Boolean) = defValue
        override fun contains(key: String?) = map.containsKey(key)
        override fun edit() = object : android.content.SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor {
                if (value != null) map[key!!] = value else map.remove(key)
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = this
            override fun putInt(key: String?, value: Int) = this
            override fun putLong(key: String?, value: Long) = this
            override fun putFloat(key: String?, value: Float) = this
            override fun putBoolean(key: String?, value: Boolean) = this
            override fun remove(key: String?): android.content.SharedPreferences.Editor {
                map.remove(key)
                return this
            }
            override fun clear(): android.content.SharedPreferences.Editor {
                map.clear()
                return this
            }
            override fun commit() = true
            override fun apply() = Unit
        }
        override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }
}
