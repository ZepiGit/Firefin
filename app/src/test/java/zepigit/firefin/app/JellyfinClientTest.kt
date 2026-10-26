package zepigit.firefin.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zepigit.firefin.app.data.JellyfinClient
import zepigit.firefin.app.data.MediaItem
import zepigit.firefin.app.data.ServerResponseException
import zepigit.firefin.app.data.SessionExpiredException
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
    private lateinit var prefs: InMemoryPrefs

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        prefs = InMemoryPrefs()
        session = SessionStore(prefs)
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
        server.enqueue(MockResponse().setResponseCode(404))
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
        val streams = """[{"Type":"Audio","Index":1,"Language":"eng"},{"Type":"Audio","Index":2,"Language":"ger"},{"Type":"Subtitle","Index":4,"Language":"deu","Codec":"srt","DeliveryUrl":"/sub.srt"}]"""
        server.enqueue(MockResponse().setBody("""{"PlaySessionId":"ps","MediaSources":[{"Id":"s","TranscodingUrl":"/v.m3u8","DefaultAudioStreamIndex":1,"MediaStreams":$streams}]}"""))
        server.enqueue(MockResponse().setBody("""{"PlaySessionId":"ps","MediaSources":[{"Id":"s","TranscodingUrl":"/v.m3u8?a=2","DefaultAudioStreamIndex":2,"DefaultSubtitleStreamIndex":4,"MediaStreams":$streams}]}"""))
        val source = client.playbackInfo("i1")
        val first = org.json.JSONObject(server.takeRequest().body.readUtf8())
        assertFalse(first.has("MediaSourceId") || first.has("AudioStreamIndex"))
        val second = org.json.JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("s", second.getString("MediaSourceId"))
        assertEquals(2, second.getInt("AudioStreamIndex"))
        assertEquals(4, second.getInt("SubtitleStreamIndex"))
        assertEquals(server.url("/v.m3u8?a=2").toString(), source.url)
        assertEquals(server.url("/sub.srt").toString(), source.subtitleUrl)
        assertEquals("application/x-subrip", source.subtitleMime)
    }

    @Test fun `language preferences already matching the server defaults need no second negotiation`() = runBlocking {
        session.serverUrl = server.url("/").toString().trimEnd('/')
        session.userId = "u1"; session.accessToken = "tok"
        client = JellyfinClient(session) {
            zepigit.firefin.app.preferences.deriveEffective(zepigit.firefin.app.preferences.StoredPreferences(audioLanguage = "de"), 1280, 720, 4_000_000)
        }
        server.enqueue(MockResponse().setBody("""{"PlaySessionId":"ps","MediaSources":[{"Id":"s","SupportsDirectPlay":true,"DefaultAudioStreamIndex":2,"MediaStreams":[{"Type":"Audio","Index":2,"Language":"ger"}]}]}"""))
        val source = client.playbackInfo("i1")
        assertEquals(1, server.requestCount)
        assertEquals(2, source.audioIndex)
        assertEquals("DirectPlay", source.playMethod)
    }

    @Test fun `direct stream uses the negotiated remux URL not the static original`() = runBlocking {
        session.serverUrl = server.url("/").toString().trimEnd('/')
        session.userId = "u1"; session.accessToken = "tok"
        server.enqueue(MockResponse().setBody("""{"PlaySessionId":"ps","MediaSources":[{"Id":"s","SupportsDirectPlay":false,"SupportsDirectStream":true,"TranscodingUrl":"/remux.m3u8"}]}"""))
        val source = client.playbackInfo("i1")
        assertEquals(server.url("/remux.m3u8").toString(), source.url)
        assertEquals("DirectStream", source.playMethod)
    }

    @Test fun `the version that direct-plays wins over a transcoding one listed first`() = runBlocking {
        session.serverUrl = server.url("/").toString().trimEnd('/')
        session.userId = "u1"; session.accessToken = "tok"
        server.enqueue(MockResponse().setBody("""{"PlaySessionId":"ps","MediaSources":[{"Id":"4k","SupportsDirectPlay":false,"SupportsTranscoding":true,"TranscodingUrl":"/4k.m3u8"},{"Id":"1080","SupportsDirectPlay":true}]}"""))
        val source = client.playbackInfo("i1")
        assertEquals("1080", source.mediaSourceId)
        assertEquals("DirectPlay", source.playMethod)
        assertTrue(source.url, source.url.endsWith("/Videos/i1/stream?static=true&MediaSourceId=1080"))
    }

    @Test fun `an explicit source pin is kept and flag-only sources are skipped for ones with a URL`() = runBlocking {
        session.serverUrl = server.url("/").toString().trimEnd('/')
        session.userId = "u1"; session.accessToken = "tok"
        server.enqueue(MockResponse().setBody("""{"PlaySessionId":"ps","MediaSources":[{"Id":"4k","SupportsTranscoding":true,"TranscodingUrl":"/4k.m3u8"},{"Id":"1080","SupportsDirectPlay":true}]}"""))
        val pinned = client.playbackInfo("i1", mediaSourceId = "4k")
        assertEquals("4k", pinned.mediaSourceId)
        assertTrue(org.json.JSONObject(server.takeRequest().body.readUtf8()).getString("MediaSourceId") == "4k")

        server.enqueue(MockResponse().setBody("""{"PlaySessionId":"ps","MediaSources":[{"Id":"a","SupportsDirectStream":true},{"Id":"b","TranscodingUrl":"/b.m3u8"}]}"""))
        assertEquals("b", client.playbackInfo("i1").mediaSourceId)
    }

    /** Signed-in session on a 10.11 server; the version probe answer is queued first. */
    private fun signIn(version: String = "10.11.0") {
        session.serverUrl = server.url("/").toString().trimEnd('/')
        session.userId = "u1"; session.accessToken = "tok"; session.userName = "testuser"
        server.enqueue(MockResponse().setBody("""{"Version":"$version"}"""))
    }

    private fun userRoutes(version: MockResponse, vararg entries: Pair<String, String>) {
        val table = entries.toMap()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                if (path == "/System/Info/Public") return version
                return table[path]?.let { MockResponse().setBody(it) } ?: MockResponse().setResponseCode(404)
            }
        }
    }

    @Test fun `user routes follow the probed server generation`() = runBlocking {
        signIn()
        userRoutes(MockResponse().setBody("""{"Version":"10.8.13"}"""), "/Users/u1/Views" to """{"Items":[]}""")
        client.views()
        assertEquals(listOf("GET /System/Info/Public", "GET /Users/u1/Views"), recorded().paths())
        assertTrue(client.cleanupEncodings())

        val fresh = JellyfinClient(session)
        userRoutes(MockResponse().setBody("""{"Version":"12.3.0"}"""), "/UserViews" to """{"Items":[]}""", "/UserFavoriteItems/i1" to "{}")
        fresh.views()
        fresh.setFavorite("i1", true)
        assertEquals(listOf("GET /System/Info/Public", "GET /UserViews", "POST /UserFavoriteItems/i1"), recorded().paths())
        assertFalse(fresh.cleanupEncodings())
    }

    @Test fun `an unknown server falls back to the other route generation on 404 and remembers it`() = runBlocking {
        signIn()
        userRoutes(MockResponse().setResponseCode(500), "/Users/u1/Views" to """{"Items":[{"Id":"lib","Name":"Filme","CollectionType":"movies"}]}""")
        assertEquals("lib", client.views().single().id)
        assertEquals("lib", client.views().single().id)
        assertEquals(
            listOf("GET /System/Info/Public", "GET /UserViews", "GET /Users/u1/Views", "GET /Users/u1/Views"),
            recorded().paths(),
        )
        assertTrue(client.cleanupEncodings())
    }

    @Test fun `favorite and watched marks set with POST and clear with DELETE on the same path`() = runBlocking {
        signIn()
        repeat(4) { server.enqueue(MockResponse().setBody("{}")) }
        client.setFavorite("i1", true)
        client.setFavorite("i1", false)
        client.setPlayed("i1", true)
        client.setPlayed("i1", false)
        server.takeRequest() // version probe
        val requests = (1..4).map { server.takeRequest() }.map { it.method to it.path }
        assertEquals(
            listOf(
                "POST" to "/UserFavoriteItems/i1?userId=u1",
                "DELETE" to "/UserFavoriteItems/i1?userId=u1",
                "POST" to "/UserPlayedItems/i1?userId=u1",
                "DELETE" to "/UserPlayedItems/i1?userId=u1",
            ),
            requests,
        )
    }

    @Test fun `resume reset posts only the position to the user-data route`() = runBlocking {
        signIn()
        server.enqueue(MockResponse().setBody("{}"))
        client.resetResume("i1")
        // No generation probe: the route has no 10.8 counterpart and goes out as is.
        val request = server.takeRequest()
        assertEquals("POST /UserItems/i1/UserData?userId=u1", "${request.method} ${request.path}")
        assertEquals("{\"PlaybackPositionTicks\":0}", request.body.readUtf8())
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
        server.takeRequest() // version probe
        val path = server.takeRequest().path!!
        assertTrue(path, path.contains("ParentId=s1") && path.contains("StartIndex=60") && path.contains("Limit=60") && path.contains("Recursive=false"))
    }

    @Test fun `recently added rows request the overview shown in the home preview`() = runBlocking {
        signIn()
        server.enqueue(MockResponse().setBody("[]"))
        client.latest("lib1")
        server.takeRequest() // version probe
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

    private val authResponse get() = MockResponse().setBody("""{"AccessToken":"tok","ServerId":"srv","User":{"Id":"u1","Name":"testuser"}}""")

    /** Serves "METHOD /path" routes; everything else is 404 like a server without the Moonbase plugin. */
    private fun routes(vararg entries: Pair<String, MockResponse>) {
        val table = entries.toMap()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                table["${request.method} ${request.requestUrl!!.encodedPath}"] ?: MockResponse().setResponseCode(404)
        }
    }

    private fun recorded(): List<RecordedRequest> = generateSequence { server.takeRequest(0, TimeUnit.MILLISECONDS) }.toList()
    private fun List<RecordedRequest>.paths() = map { "${it.method} ${it.requestUrl!!.encodedPath}" }
    private fun address() = server.url("/").toString().trimEnd('/')

    @Test fun `password sign-in opens the Seerr session with the same credentials over the Jellyfin token`() = runBlocking {
        routes(
            "POST /Users/AuthenticateByName" to authResponse,
            "GET /Moonfin/Ping" to MockResponse().setBody("""{"installed":true}"""),
            "GET /Moonfin/Jellyseerr/Config" to MockResponse().setBody("""{"enabled":true,"userEnabled":true}"""),
            "GET /Moonfin/Jellyseerr/Status" to MockResponse().setBody("""{"authenticated":false}"""),
            "POST /Moonfin/Jellyseerr/Login" to MockResponse().setBody("""{"success":true,"userId":7,"permissions":32}"""),
        )
        client.login(address(), "testuser", "s3cret-pw")

        val requests = recorded()
        assertEquals(
            listOf("POST /Users/AuthenticateByName", "GET /Moonfin/Ping", "GET /Moonfin/Jellyseerr/Config", "GET /Moonfin/Jellyseerr/Status", "POST /Moonfin/Jellyseerr/Login"),
            requests.paths(),
        )
        val login = requests.last()
        val body = JSONObject(login.body.readUtf8())
        assertEquals("jellyfin", body.getString("authType"))
        assertEquals("testuser", body.getString("username"))
        assertEquals("s3cret-pw", body.getString("password"))
        assertEquals(3, body.length())
        assertTrue(login.getHeader("Authorization")!!.contains("Token=\"tok\""))
        assertEquals(null, login.getHeader("Cookie"))
        requests.drop(1).forEach { assertTrue(it.getHeader("Authorization")!!.contains("Token=\"tok\"")) }
        assertTrue(session.isLoggedIn)
        assertFalse(prefs.all.values.any { it.toString().contains("s3cret-pw") })
    }

    @Test fun `missing plugin, disabled Seerr or an existing Seerr session send no password`() = runBlocking {
        val cases = listOf(
            arrayOf("POST /Users/AuthenticateByName" to authResponse),
            arrayOf(
                "POST /Users/AuthenticateByName" to authResponse,
                "GET /Moonfin/Ping" to MockResponse().setBody("""{"installed":true}"""),
                "GET /Moonfin/Jellyseerr/Config" to MockResponse().setBody("""{"enabled":false,"userEnabled":true}"""),
                "POST /Moonfin/Jellyseerr/Login" to MockResponse().setBody("""{"success":true,"userId":7,"permissions":32}"""),
            ),
            arrayOf(
                "POST /Users/AuthenticateByName" to authResponse,
                "GET /Moonfin/Ping" to MockResponse().setBody("""{"installed":true}"""),
                "GET /Moonfin/Jellyseerr/Config" to MockResponse().setBody("""{"enabled":true,"userEnabled":true}"""),
                "GET /Moonfin/Jellyseerr/Status" to MockResponse().setBody("""{"authenticated":true,"userId":7,"permissions":32}"""),
                "POST /Moonfin/Jellyseerr/Login" to MockResponse().setBody("""{"success":true,"userId":7,"permissions":32}"""),
            ),
        )
        for ((index, case) in cases.withIndex()) {
            session.clear()
            routes(*case)
            client.login(address(), "testuser", "s3cret-pw")
            val requests = recorded()
            assertTrue("case $index", session.isLoggedIn)
            assertEquals("case $index", 1, requests.count { it.body.clone().readUtf8().contains("s3cret-pw") })
            assertFalse("case $index", requests.paths().any { it.endsWith("/Login") })
        }
    }

    @Test fun `rejected optional Seerr sign-in keeps the Jellyfin session`() = runBlocking {
        val enabled = arrayOf(
            "POST /Users/AuthenticateByName" to authResponse,
            "GET /Moonfin/Ping" to MockResponse().setBody("""{"installed":true}"""),
            "GET /Moonfin/Jellyseerr/Config" to MockResponse().setBody("""{"enabled":true,"userEnabled":true}"""),
            "GET /Moonfin/Jellyseerr/Status" to MockResponse().setBody("""{"authenticated":false}"""),
        )
        val rejections = listOf(
            MockResponse().setBody("""{"success":false}"""),
            MockResponse().setResponseCode(403).setBody("{}"),
            MockResponse().setResponseCode(502).setBody("{}"),
            // Seerr refuses the password while Jellyfin still accepts the token.
            MockResponse().setResponseCode(401).setBody("{}"),
        )
        for (rejection in rejections) {
            session.clear()
            routes(*enabled, "POST /Moonfin/Jellyseerr/Login" to rejection, "GET /Users/Me" to MockResponse().setBody("{}"))
            val json = client.login(address(), "testuser", "s3cret-pw")
            assertEquals("tok", json.getString("AccessToken"))
            assertTrue(session.isLoggedIn)
            assertEquals("tok", session.accessToken)
            assertTrue(recorded().paths().contains("POST /Moonfin/Jellyseerr/Login"))
        }
    }

    @Test fun `redirect to another origin never forwards the bridged password`() = runBlocking {
        MockWebServer().use { other ->
            other.start()
            val elsewhere = other.url("/collect").toString()
            routes(
                "POST /Users/AuthenticateByName" to authResponse,
                "GET /Moonfin/Ping" to MockResponse().setBody("""{"installed":true}"""),
                "GET /Moonfin/Jellyseerr/Config" to MockResponse().setBody("""{"enabled":true,"userEnabled":true}"""),
                "GET /Moonfin/Jellyseerr/Status" to MockResponse().setBody("""{"authenticated":false}"""),
                "POST /Moonfin/Jellyseerr/Login" to MockResponse().setResponseCode(307).setHeader("Location", elsewhere),
            )
            client.login(address(), "testuser", "s3cret-pw")
            assertTrue(session.isLoggedIn)
            assertEquals(1, recorded().count { it.requestUrl!!.encodedPath == "/Moonfin/Jellyseerr/Login" })
            assertEquals(0, other.requestCount)
        }
    }

    @Test fun `a stalled Seerr plugin is bounded and keeps the Jellyfin session`() = runBlocking {
        client = JellyfinClient(session, seerrBridgeTimeoutMs = 300)
        routes(
            "POST /Users/AuthenticateByName" to authResponse,
            "GET /Moonfin/Ping" to MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE),
        )
        val started = System.nanoTime()
        client.login(address(), "testuser", "s3cret-pw")
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        assertTrue("took $elapsedMs ms", elapsedMs < 5_000)
        assertTrue(session.isLoggedIn)
        assertFalse(recorded().paths().any { it.endsWith("/Login") })
    }

    @Test fun `cancelling sign-in during the optional Seerr step propagates and sends no password`() = runBlocking {
        routes(
            "POST /Users/AuthenticateByName" to authResponse,
            "GET /Moonfin/Ping" to MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE),
            "GET /Moonfin/Jellyseerr/Config" to MockResponse().setBody("""{"enabled":true,"userEnabled":true}"""),
            "GET /Moonfin/Jellyseerr/Status" to MockResponse().setBody("""{"authenticated":false}"""),
            "POST /Moonfin/Jellyseerr/Login" to MockResponse().setBody("""{"success":true,"userId":7,"permissions":32}"""),
        )
        val attempt = async(Dispatchers.IO) { client.login(address(), "testuser", "s3cret-pw") }
        assertEquals("/Users/AuthenticateByName", server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.encodedPath)
        assertEquals("/Moonfin/Ping", server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.encodedPath)
        val started = System.nanoTime()
        attempt.cancel()
        val failure = runCatching { attempt.await() }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5_000)
        assertFalse(recorded().paths().any { it.endsWith("/Login") })
    }

    @Test fun `Jellyfin rejecting its fresh token during the Seerr step is a sign-in failure`() = runBlocking {
        routes(
            "POST /Users/AuthenticateByName" to authResponse,
            "GET /Moonfin/Ping" to MockResponse().setResponseCode(401).setBody("{}"),
            "GET /Users/Me" to MockResponse().setResponseCode(401).setBody("{}"),
        )
        val failure = runCatching { client.login(address(), "testuser", "s3cret-pw") }.exceptionOrNull()
        assertTrue(failure is SessionExpiredException)
        assertFalse(session.isLoggedIn)
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
