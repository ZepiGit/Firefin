package zepigit.firefin.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the app's own JellyfinClient.login on the device runtime against a
 * local synthetic fixture: the optional Moonbase Seerr bridge after a
 * Jellyfin password sign-in, and the quick no-op when the plugin is absent.
 */
@RunWith(AndroidJUnit4::class)
class SeerrLoginBridgeTest {
    private val server = MockWebServer()

    @Before fun signedOut() {
        prefs().edit().clear().commit()
    }

    @After fun shutdown() {
        ServiceLocator.session.clear()
        server.shutdown()
    }

    private fun prefs() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("firefin_session", Context.MODE_PRIVATE)

    private fun serve(routes: Map<String, String>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                routes["${request.method} ${request.requestUrl!!.encodedPath}"]?.let { MockResponse().setBody(it) }
                    ?: MockResponse().setResponseCode(404)
        }
        server.start()
    }

    private fun recorded(): List<RecordedRequest> = generateSequence { server.takeRequest(0, TimeUnit.MILLISECONDS) }.toList()

    @Test fun passwordSignInOpensSeerrSessionWithSameCredentials() = runBlocking<Unit> {
        serve(mapOf(
            "POST /Users/AuthenticateByName" to """{"AccessToken":"tok","ServerId":"srv","User":{"Id":"u1","Name":"tester"}}""",
            "GET /Moonfin/Ping" to """{"installed":true}""",
            "GET /Moonfin/Jellyseerr/Config" to """{"enabled":true,"userEnabled":true}""",
            "GET /Moonfin/Jellyseerr/Status" to """{"authenticated":false}""",
            "POST /Moonfin/Jellyseerr/Login" to """{"success":true,"userId":7,"permissions":32}""",
        ))
        ServiceLocator.client.login(server.url("/").toString().trimEnd('/'), "tester", "synthetic-pw")

        assertTrue(ServiceLocator.session.isLoggedIn)
        val requests = recorded()
        val logins = requests.filter { it.requestUrl!!.encodedPath == "/Moonfin/Jellyseerr/Login" }
        assertEquals(1, logins.size)
        val body = JSONObject(logins.single().body.readUtf8())
        assertEquals("jellyfin", body.getString("authType"))
        assertEquals("tester", body.getString("username"))
        assertEquals("synthetic-pw", body.getString("password"))
        assertTrue(logins.single().getHeader("Authorization")!!.contains("Token=\"tok\""))
        assertFalse(prefs().all.values.any { it.toString().contains("synthetic-pw") })
    }

    @Test fun absentPluginFinishesQuicklyAndKeepsJellyfinSession() = runBlocking<Unit> {
        serve(mapOf("POST /Users/AuthenticateByName" to """{"AccessToken":"tok","ServerId":"srv","User":{"Id":"u1","Name":"tester"}}"""))
        val started = System.nanoTime()
        ServiceLocator.client.login(server.url("/").toString().trimEnd('/'), "tester", "synthetic-pw")
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        assertTrue("took $elapsedMs ms", elapsedMs < 5_000)
        assertTrue(ServiceLocator.session.isLoggedIn)
        assertEquals(
            listOf("POST /Users/AuthenticateByName", "GET /Moonfin/Ping"),
            recorded().map { "${it.method} ${it.requestUrl!!.encodedPath}" },
        )
    }
}
