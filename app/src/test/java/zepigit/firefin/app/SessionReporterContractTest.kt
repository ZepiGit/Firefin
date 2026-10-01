package zepigit.firefin.app

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zepigit.firefin.app.data.ServerCredentials
import zepigit.firefin.app.data.ServerTransport
import zepigit.firefin.app.util.SessionReporter

/**
 * Contract tests for the per-playback session reporter: ordered events with
 * conflated progress, terminal stop plus one-shot cleanup, all bound to an
 * immutable credential snapshot. Tests drain recorded requests only after the
 * reporter reached its terminal state; conflation asserts the contract
 * (at-most-latest, none after terminal stop) rather than exact send counts,
 * so remaining scheduler freedom between wakeups cannot break them.
 */
class SessionReporterContractTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(200).setBody("{}")
        }
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun transport() = ServerTransport(ServerCredentials(server.url("/").toString(), "user-a", "token-a"), "device-1")

    private fun SessionReporter.runAndDrain(block: SessionReporter.() -> Unit): List<RecordedRequest> {
        try {
            block()
            runBlocking { awaitTerminal() }
        } finally {
            cancel()
        }
        return (0 until server.requestCount).map { server.takeRequest() }
    }

    private fun List<RecordedRequest>.paths() = map { it.path!!.substringBefore('?') }

    @Test
    fun `progress conflation keeps only the latest state before terminal stop`() {
        val requests = SessionReporter(transport(), "item-1", "session-1", "ms-1", "DirectPlay").runAndDrain {
            progress(100, false)
            progress(200, false)
            progress(300, false)
            progress(300, false)
            stopped(400)
        }
        // Ordered contract: Playing first, terminal Stopped, cleanup after it.
        assertEquals("/Sessions/Playing", requests.first().path)
        assertEquals(
            listOf("/Sessions/Playing/Stopped", "/Videos/ActiveEncodings"),
            requests.paths().takeLast(2),
        )
        // Conflation: every progress carries at most the latest observed state.
        val progressRequests = requests.filter { it.path!!.startsWith("/Sessions/Playing/Progress") }
        assertTrue(progressRequests.isNotEmpty())
        assertTrue(progressRequests.all { it.body.readUtf8().contains("\"PositionTicks\":300") })
        // No progress may appear after the terminal stop.
        val stopIndex = requests.indexOfFirst { it.path!!.startsWith("/Sessions/Playing/Stopped") }
        assertTrue(requests.drop(stopIndex + 1).none { it.path!!.startsWith("/Sessions/Playing/Progress") })
        val cleanup = requests.last()
        assertEquals("DELETE", cleanup.method)
        assertTrue(cleanup.path!!.contains("PlaySessionId=session-1") && cleanup.path!!.contains("DeviceId=device-1"))
    }

    @Test
    fun `progress after stop is dropped and stop is idempotent`() {
        val requests = SessionReporter(transport(), "item-1", "session-1", "ms-1", "DirectPlay").runAndDrain {
            stopped(500)
            progress(900, false)
            stopped(501)
        }
        assertEquals(listOf("/Sessions/Playing", "/Sessions/Playing/Stopped", "/Videos/ActiveEncodings"), requests.paths())
        assertTrue(requests[1].body.readUtf8().contains("\"PositionTicks\":500"))
    }

    @Test
    fun `reporter snapshot never adopts credentials of a switched account`() {
        val mainTransport = transport()
        val snapshot = mainTransport.snapshot()
        var other: MockWebServer? = null
        try {
            other = MockWebServer()
            other.dispatcher = server.dispatcher
            other.start()
            val requests = SessionReporter(snapshot, "item-1", "session-1", "ms-1", "DirectPlay").runAndDrain {
                // The shared/main transport switches accounts mid-playback; the snapshot must not follow.
                mainTransport.update(ServerCredentials(other.url("/").toString(), "user-b", "token-b"))
                progress(120, false)
                stopped(180)
            }
            assertEquals(0, other.requestCount)
            assertEquals(
                listOf("/Sessions/Playing", "/Sessions/Playing/Progress", "/Sessions/Playing/Stopped", "/Videos/ActiveEncodings"),
                requests.paths(),
            )
            assertTrue(requests.all { it.getHeader("Authorization")!!.contains("Token=\"token-a\"") })
            assertTrue(requests[1].body.readUtf8().contains("\"PositionTicks\":120"))
            assertTrue(requests[2].body.readUtf8().contains("\"PositionTicks\":180"))
        } finally {
            other?.shutdown()
        }
    }

    @Test
    fun `live stream close runs once after stop for live sources`() {
        val requests = SessionReporter(transport(), "item-live", "session-live", "ms-live", "Transcode", liveStreamId = "ls-7").runAndDrain {
            stopped(250)
        }
        assertEquals(
            listOf("/Sessions/Playing", "/Sessions/Playing/Stopped", "/Videos/ActiveEncodings", "/LiveStreams/Close"),
            requests.paths(),
        )
        assertTrue(requests[3].body.readUtf8().contains("\"LiveStreamId\":\"ls-7\""))
    }

    @Test
    fun `cleanup 404 is treated as already cleaned without retries`() {
        var cleanupCalls = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path!!.startsWith("/Videos/ActiveEncodings")) {
                    cleanupCalls++
                    MockResponse().setResponseCode(404)
                } else {
                    MockResponse().setResponseCode(200).setBody("{}")
                }
        }
        val requests = SessionReporter(transport(), "item-1", "session-1", "ms-1", "DirectPlay").runAndDrain {
            stopped(300)
        }
        assertEquals(1, cleanupCalls)
        assertEquals(3, requests.size)
    }
}
