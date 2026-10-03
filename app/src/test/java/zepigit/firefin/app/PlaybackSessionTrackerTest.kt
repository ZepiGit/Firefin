package zepigit.firefin.app

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zepigit.firefin.app.data.ServerCredentials
import zepigit.firefin.app.data.ServerTransport
import zepigit.firefin.app.util.PlaybackSessionTracker
import zepigit.firefin.app.util.SessionReporter

/**
 * Terminal session handling for the player: natural end, fatal error and
 * teardown finish an attempt once; a retry starts a new session. Requests are
 * drained only after each reporter reached its terminal state.
 */
class PlaybackSessionTrackerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(200).setBody("{}")
        }
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun reporter(session: String) =
        SessionReporter(ServerTransport(ServerCredentials(server.url("/").toString(), "user-a", "token-a"), "device-1"), "item-1", session, "source-1", "DirectPlay")

    private data class Event(val path: String, val body: String)

    private fun drain(): List<Event> = (0 until server.requestCount).map { server.takeRequest() }
        .map { Event(it.path!!.substringBefore('?'), it.path!! + it.body.readUtf8()) }

    @Test fun `end of playback sends one stop and nothing after it`() {
        val tracker = PlaybackSessionTracker()
        tracker.start(reporter("ps-1"))
        tracker.progress(10, false)
        val finished = tracker.finish(5_000)!!
        tracker.progress(20, false)
        // A later onDestroy finds no active session and sends no second stop.
        assertNull(tracker.finish(6_000))
        assertFalse(tracker.active)
        runBlocking { finished.awaitTerminal() }

        val events = drain()
        val stops = events.filter { it.path == "/Sessions/Playing/Stopped" }
        assertEquals(1, stops.size)
        assertTrue(stops.single().body.contains("\"PositionTicks\":5000"))
        val stopAt = events.indexOf(stops.single())
        assertTrue(events.drop(stopAt).none { it.path == "/Sessions/Playing/Progress" })
        assertEquals("/Sessions/Playing", events.first().path)
    }

    @Test fun `retry after an error starts a fresh session with its own start and stop`() {
        val tracker = PlaybackSessionTracker()
        tracker.start(reporter("ps-1"))
        val first = tracker.finish(1_000)!!
        tracker.start(reporter("ps-2"))
        assertTrue(tracker.active)
        tracker.progress(2_000, false)
        val second = tracker.finish(3_000)!!
        runBlocking { first.awaitTerminal(); second.awaitTerminal() }

        val events = drain()
        for (session in listOf("ps-1", "ps-2")) {
            val own = events.filter { it.body.contains(session) }.map { it.path }
            assertEquals("start of $session", "/Sessions/Playing", own.first())
            assertEquals("one stop for $session", 1, own.count { it == "/Sessions/Playing/Stopped" })
            assertTrue("cleanup after stop for $session", own.indexOf("/Videos/ActiveEncodings") > own.indexOf("/Sessions/Playing/Stopped"))
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `an active session cannot be replaced without finishing it`() {
        val tracker = PlaybackSessionTracker()
        val active = reporter("ps-1")
        tracker.start(active)
        try {
            tracker.start(reporter("ps-2"))
        } finally {
            active.cancel()
        }
    }
}
