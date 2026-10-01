package zepigit.firefin.app

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zepigit.firefin.app.data.ServerCredentials
import zepigit.firefin.app.data.ServerTransport
import zepigit.firefin.app.util.SessionReporter

/**
 * Contract tests for the per-playback session reporter: ordered events with
 * conflated progress, terminal stop plus one-shot cleanup, all bound to an
 * immutable credential snapshot.
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
        server.shutdown()
    }

    private fun transport() = ServerTransport(ServerCredentials(server.url("/").toString(), "user-a", "token-a"), "device-1")

    @Test
    fun `progress conflation keeps only the latest state before terminal stop`() = runBlocking {
        val reporter = SessionReporter(transport(), "item-1", "session-1", "ms-1", "DirectPlay")
        // Wait until Playing was actually sent, so the strict sequence below is deterministic.
        assertEquals("/Sessions/Playing", server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)?.path)
        reporter.progress(100, false)
        reporter.progress(200, false)
        reporter.progress(300, false)
        reporter.stopped(400)
        reporter.awaitTerminal()

        val progress = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("/Sessions/Playing/Progress", progress.path)
        assertTrue(progress.body.readUtf8().contains("\"PositionTicks\":300"))
        val stop = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("/Sessions/Playing/Stopped", stop.path)
        assertTrue(stop.body.readUtf8().contains("\"PositionTicks\":400"))
        val cleanup = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("DELETE", cleanup.method)
        assertEquals("/Videos/ActiveEncodings", cleanup.path!!.substringBefore('?'))
        assertTrue(cleanup.path!!.contains("PlaySessionId=session-1") && cleanup.path!!.contains("DeviceId=device-1"))
        // Conflation: no further progress may appear after the terminal stop.
        assertEquals(null, server.takeRequest(300, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `progress after stop is dropped and stop is idempotent`() = runBlocking {
        val reporter = SessionReporter(transport(), "item-1", "session-1", "ms-1", "DirectPlay")
        kotlinx.coroutines.delay(150)
        reporter.stopped(500)
        reporter.progress(900, false)
        reporter.stopped(501)
        reporter.awaitTerminal()

        val paths = (0 until server.requestCount).map { server.takeRequest() }
        assertEquals(listOf("/Sessions/Playing", "/Sessions/Playing/Stopped", "/Videos/ActiveEncodings"), paths.map { it.path!!.substringBefore('?') })
        assertTrue(paths[1].body.readUtf8().contains("\"PositionTicks\":500"))
    }

    @Test
    fun `reporter snapshot never adopts credentials of a switched account`() = runBlocking {
        val mainTransport = transport()
        val snapshot = mainTransport.snapshot()
        val reporter = SessionReporter(snapshot, "item-1", "session-1", "ms-1", "DirectPlay")
        assertEquals("/Sessions/Playing", server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)?.path)
        MockWebServer().use { other: MockWebServer ->
            other.dispatcher = server.dispatcher
            other.start()
            // The shared/main transport switches accounts mid-playback; the snapshot must not follow.
            mainTransport.update(ServerCredentials(other.url("/").toString(), "user-b", "token-b"))
            reporter.progress(120, false)
            reporter.stopped(180)
            reporter.awaitTerminal()

            assertEquals(0, other.requestCount)
            val progress = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)!!
            assertEquals("/Sessions/Playing/Progress", progress.path)
            assertTrue(progress.getHeader("Authorization")!!.contains("Token=\"token-a\""))
            assertTrue(progress.body.readUtf8().contains("\"PositionTicks\":120"))
            val stop = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS)!!
            assertEquals("/Sessions/Playing/Stopped", stop.path)
            assertTrue(stop.getHeader("Authorization")!!.contains("Token=\"token-a\""))
            assertNotNull(server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS))
        }
        Unit
    }

    @Test
    fun `live stream close runs once after stop for live sources`() = runBlocking {
        val reporter = SessionReporter(transport(), "item-live", "session-live", "ms-live", "Transcode", liveStreamId = "ls-7")
        kotlinx.coroutines.delay(150)
        reporter.stopped(250)
        reporter.awaitTerminal()

        val paths = (0 until server.requestCount).map { server.takeRequest() }
        assertEquals(
            listOf("/Sessions/Playing", "/Sessions/Playing/Stopped", "/Videos/ActiveEncodings", "/LiveStreams/Close"),
            paths.map { it.path!!.substringBefore('?') },
        )
        assertTrue(paths[3].body.readUtf8().contains("\"LiveStreamId\":\"ls-7\""))
    }

    @Test
    fun `cleanup 404 is treated as already cleaned without retries`() = runBlocking {
        var calls = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                calls++
                return if (request.path!!.startsWith("/Videos/ActiveEncodings")) MockResponse().setResponseCode(404) else MockResponse().setResponseCode(200).setBody("{}")
            }
        }
        val reporter = SessionReporter(transport(), "item-1", "session-1", "ms-1", "DirectPlay")
        kotlinx.coroutines.delay(150)
        reporter.stopped(300)
        reporter.awaitTerminal()
        assertEquals(3, calls)
    }
}
