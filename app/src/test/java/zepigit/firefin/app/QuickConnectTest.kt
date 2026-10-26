package zepigit.firefin.app

import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import zepigit.firefin.app.data.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class QuickConnectTest {
    private lateinit var server: MockWebServer
    private lateinit var prefs: InMemoryPrefs
    private lateinit var session: SessionStore
    private val polls = AtomicInteger()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        prefs = InMemoryPrefs()
        session = SessionStore(prefs)
    }
    @After fun tearDown() { server.shutdown() }

    private fun client(timeout: Long = 5_000) = JellyfinClient(session, quickConnectPollMs = 1, quickConnectTimeoutMs = timeout)
    private fun address() = server.url("/jellyfin/").toString()
    private fun serve(version: String = "10.11.0", authenticated: Boolean = true, initiateStatus: Int = 200, enabledStatus: Int = 200, enabled: Boolean = true) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/jellyfin/QuickConnect/Enabled" -> MockResponse().setResponseCode(enabledStatus).setBody(enabled.toString())
                "/jellyfin/System/Info/Public" -> MockResponse().setBody("""{"Version":"$version"}""")
                "/jellyfin/QuickConnect/Initiate" -> if (request.method == "POST" && initiateStatus != 200) MockResponse().setResponseCode(initiateStatus)
                    else MockResponse().setBody("""{"Secret":"ephemeral-secret","Code":"123456"}""")
                "/jellyfin/QuickConnect/Connect" -> MockResponse().setBody("""{"Authenticated":${authenticated && polls.incrementAndGet() >= 2}}""")
                "/jellyfin/Users/AuthenticateWithQuickConnect" -> MockResponse().setBody("""{"AccessToken":"token","User":{"Id":"u1","Name":"Tester"}}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
    }
    private fun requests() = generateSequence { server.takeRequest(0, TimeUnit.MILLISECONDS) }.toList()

    @Test fun `modern handshake polls and persists only the final session on entered server`() = runBlocking {
        serve()
        session.serverUrl = "https://old.example.com"; session.userId = "old"; session.accessToken = "old-token"
        var code = ""
        client().quickConnect(address()) {
            code = it
            assertEquals("old-token", session.accessToken)
        }
        assertEquals("123456", code)
        assertEquals("u1", session.userId)
        assertEquals("token", session.accessToken)
        assertEquals(address().trimEnd('/'), session.serverUrl)
        assertFalse(prefs.all.values.any { it.toString().contains("ephemeral-secret") || it == code })
        val sent = requests()
        assertEquals("POST", sent.single { it.path!!.contains("Initiate") }.method)
        assertEquals(2, sent.count { it.path!!.contains("/Connect?") })
        assertTrue(sent.last().body.readUtf8().contains("ephemeral-secret"))
        assertTrue(sent.none { it.path!!.contains("Moonfin") || it.getHeader("Authorization")!!.contains("old-token") })
    }

    @Test fun `legacy uses GET and unknown generation falls back only on missing method`() = runBlocking {
        serve(version = "10.8.13")
        client().quickConnect(address()) {}
        assertEquals("GET", requests().single { it.path!!.contains("Initiate") }.method)
        serve(version = "unknown", initiateStatus = 405)
        client().quickConnect(address()) {}
        assertEquals(listOf("POST", "GET"), requests().filter { it.path!!.contains("Initiate") }.map { it.method })
    }

    @Test fun `authorization and missing endpoint failures never persist a session`() = runBlocking {
        for (status in listOf(401, 404)) {
            serve(enabledStatus = status)
            val failure = runCatching { client().quickConnect(address()) {} }.exceptionOrNull()
            assertTrue(failure is SessionExpiredException || failure is ServerResponseException)
            assertFalse(session.isLoggedIn)
            assertEquals(1, requests().size)
        }
        serve(enabled = false)
        assertTrue(runCatching { client().quickConnect(address()) {} }.exceptionOrNull() is LocalizedIOException)
        assertFalse(session.isLoggedIn)
    }

    @Test fun `timeout and cancellation leave no credentials or secret behind`() = runBlocking {
        serve(authenticated = false)
        assertTrue(runCatching { client(timeout = 500).quickConnect(address()) {} }.exceptionOrNull() is LocalizedIOException)
        assertFalse(session.isLoggedIn)
        requests()
        serve()
        val task = launch(start = CoroutineStart.LAZY) {
            val ownJob = currentCoroutineContext()[Job]!!
            client().quickConnect(address()) { ownJob.cancel() }
        }
        task.start(); task.join()
        assertTrue(task.isCancelled)
        assertFalse(session.isLoggedIn)
        assertTrue(requests().none { it.path!!.contains("AuthenticateWithQuickConnect") })
        assertFalse(prefs.all.values.any { it.toString().contains("ephemeral-secret") })
    }
    @Test fun `cancelling while authentication response is in flight preserves the old session`() = runBlocking {
        session.serverUrl = "https://old.example.com"; session.userId = "old"; session.accessToken = "old-token"
        val authStarted = CompletableDeferred<Unit>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath.substringAfter("/jellyfin/")) {
                "QuickConnect/Enabled" -> MockResponse().setBody("true")
                "System/Info/Public" -> MockResponse().setBody("""{"Version":"10.11.0"}""")
                "QuickConnect/Initiate" -> MockResponse().setBody("""{"Code":"123456","Secret":"ephemeral-secret"}""")
                "QuickConnect/Connect" -> MockResponse().setBody("""{"Authenticated":true}""")
                "Users/AuthenticateWithQuickConnect" -> {
                    authStarted.complete(Unit)
                    MockResponse().setBody("""{"AccessToken":"new-token","User":{"Id":"new"}}""").setBodyDelay(1, TimeUnit.SECONDS)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        val task = launch { client().quickConnect(address()) {} }
        withTimeout(5_000) { authStarted.await() }
        task.cancelAndJoin()
        assertEquals("old", session.userId)
        assertEquals("old-token", session.accessToken)
        assertEquals("https://old.example.com", session.serverUrl)
        assertFalse(prefs.all.values.any { it.toString().contains("ephemeral-secret") })
    }
}
