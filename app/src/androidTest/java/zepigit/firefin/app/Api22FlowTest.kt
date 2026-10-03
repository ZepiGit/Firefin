package zepigit.firefin.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import zepigit.firefin.app.data.MediaItem
import zepigit.firefin.app.data.PageCursor
import zepigit.firefin.app.ui.LoginActivity

@RunWith(AndroidJUnit4::class)
class Api22FlowTest {
    @Before fun signedOut() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("firefin_session", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun loginScreenStartsWithoutCrashAndExposesServerField() {
        ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<android.widget.EditText>(R.id.serverUrl).isFocusable)
                assertTrue(activity.findViewById<android.widget.Button>(R.id.loginButton).isFocusable)
            }
        }
    }

    /**
     * Runs the app's own client on the API 22 runtime against a local fixture
     * server: cleartext sign-in, mark/unmark, paging past the first page and
     * sign-out. This covers request and state logic, not decoders or hardware.
     */
    @Test fun signInMarkPageAndSignOutAgainstLocalServer() = runBlocking<Unit> {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (url.encodedPath) {
                    "/Users/AuthenticateByName" -> MockResponse().setBody("""{"AccessToken":"tok","ServerId":"srv","User":{"Id":"u1","Name":"tester"}}""")
                    "/Users/u1/Items" -> {
                        val start = url.queryParameter("StartIndex")!!.toInt()
                        val limit = url.queryParameter("Limit")!!.toInt()
                        val items = JSONArray()
                        for (index in start until minOf(start + limit, EPISODES)) {
                            items.put(JSONObject().put("Id", "e$index").put("Type", "Episode").put("IndexNumber", index + 1))
                        }
                        MockResponse().setBody(JSONObject().put("Items", items).put("TotalRecordCount", EPISODES).toString())
                    }
                    else -> MockResponse().setBody("{}")
                }
            }
        }
        server.start()
        val address = server.url("/").toString().trimEnd('/')
        try {
            val client = ServiceLocator.client
            client.login(address, "tester", "pw")
            assertTrue(ServiceLocator.session.isLoggedIn)
            client.setFavorite("i1", true)
            client.setFavorite("i1", false)

            val season = MediaItem.from(JSONObject().put("Id", "s1").put("Type", "Season"))
            val cursor = PageCursor()
            val ids = mutableListOf<String>()
            while (true) {
                val start = cursor.next() ?: break
                val (page, total) = client.children(season, start)
                cursor.received(page.size, total)
                ids += page.map { it.id }
            }
            assertEquals((0 until EPISODES).map { "e$it" }, ids)

            client.logout()
            ServiceLocator.session.clear()
            val requests = generateSequence { server.takeRequest(5, TimeUnit.SECONDS) }
                .map { "${it.method} ${it.requestUrl!!.encodedPath}" }.toList()
            assertTrue(requests.contains("POST /Users/u1/FavoriteItems/i1"))
            assertTrue(requests.contains("DELETE /Users/u1/FavoriteItems/i1"))
            assertFalse(requests.any { it.endsWith("/Delete") })
            assertTrue(requests.contains("POST /Sessions/Logout"))
        } finally {
            server.shutdown()
        }

        assertFalse(ServiceLocator.session.isLoggedIn)
        ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(address, activity.findViewById<android.widget.EditText>(R.id.serverUrl).text.toString())
                assertEquals("tester", activity.findViewById<android.widget.EditText>(R.id.username).text.toString())
                assertEquals(R.id.password, activity.currentFocus?.id)
            }
        }
    }

    private companion object {
        const val EPISODES = 125
    }
}
