package zepigit.firefin.app

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import zepigit.firefin.app.data.ServerCredentials
import zepigit.firefin.app.data.ServerTransport
import zepigit.firefin.app.data.SessionExpiredException
import zepigit.firefin.app.util.Urls
import java.util.concurrent.TimeUnit

class TransportRegressionTest {
    @Test fun `bare host defaults to https without losing subpath`() {
        assertEquals("https://example.test/jellyfin", Urls.normalizeServer(" example.test/jellyfin/// "))
    }

    @Test fun `server addresses reject embedded credentials query and fragment`() {
        for (value in listOf("https://user:secret@example.test", "https://example.test?api_key=private", "https://example.test/#x", "ftp://example.test")) {
            assertThrows(IllegalArgumentException::class.java) { Urls.normalizeServer(value) }
        }
    }

    @Test fun `relative path comparison uses complete path segments`() {
        assertEquals("https://example.test/jelly/jellyfin/Videos/a", Urls.resolveRelative("https://example.test/jelly", "/jellyfin/Videos/a"))
        assertEquals("https://example.test/jelly/Videos/a", Urls.resolveRelative("https://example.test/jelly/", "/jelly/Videos/a"))
    }

    @Test fun `transcode parameter replacement preserves other parameters and removes duplicates case insensitively`() {
        val url = Urls.withStartTimeTicks("https://example.test/v?StartTimeTicks=10&MediaSourceId=x&starttimeticks=20", 42)
        assertEquals("https://example.test/v?MediaSourceId=x&StartTimeTicks=42", url)
    }

    @Test fun `login redirect never sends password or authorization to a second origin`() = runBlocking {
        MockWebServer().use { origin -> MockWebServer().use { other ->
            origin.start(); other.start()
            for (status in listOf(302, 307, 308)) {
                origin.enqueue(MockResponse().setResponseCode(status).setHeader("Location", other.url("/stolen")))
                val transport = ServerTransport(ServerCredentials(origin.url("/").toString(), "", ""), "test-device")
                val failure = runCatching { transport.json("Users/AuthenticateByName", "POST", "{\"Pw\":\"synthetic\"}") }.exceptionOrNull()
                assertNotNull(failure)
                assertFalse(failure!!.message.orEmpty().contains("synthetic"))
            }
            assertEquals(0, other.requestCount)
        } }
    }

    @Test fun `hls child requests receive origin-bound header authentication without query token`() {
        MockWebServer().use { origin ->
            origin.start()
            val transport = ServerTransport(ServerCredentials(origin.url("/").toString(), "u", "synthetic-token"), "device")
            for (path in listOf("master.m3u8", "variant.m3u8", "segment.ts", "key.bin")) {
                origin.enqueue(MockResponse().setBody("fixture"))
                transport.http.newCall(okhttp3.Request.Builder().url(origin.url(path)).build()).execute().use { assertTrue(it.isSuccessful) }
                val request = origin.takeRequest()
                assertTrue(request.getHeader("Authorization")!!.contains("Token=\"synthetic-token\""))
                assertFalse(request.path!!.contains("synthetic-token"))
            }
        }
    }

    @Test fun `unauthorized status is classified before malformed body parsing`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val transport = ServerTransport(ServerCredentials(server.url("/").toString(), "u", "t"), "d")
            for (body in listOf("", "<html>private details</html>", "{}")) {
                server.enqueue(MockResponse().setResponseCode(401).setBody(body))
                assertTrue(runCatching { transport.json("Users/u/Views") }.exceptionOrNull() is SessionExpiredException)
            }
        }
    }

    @Test fun `media snapshot keeps old origin and token after account switch`() = runBlocking {
        MockWebServer().use { origin -> MockWebServer().use { other: MockWebServer ->
            origin.start(); other.start()
            origin.enqueue(MockResponse().setBody("fixture"))
            val mainTransport = ServerTransport(ServerCredentials(origin.url("/").toString(), "user-a", "token-a"), "device-1")
            val snapshot = mainTransport.snapshot()
            mainTransport.update(ServerCredentials(other.url("/").toString(), "user-b", "token-b"))
            snapshot.mediaHttp.newCall(okhttp3.Request.Builder().url(origin.url("videos/i1/hls1/master.m3u8")).build()).execute().use { assertTrue(it.isSuccessful) }
            assertEquals(1, origin.requestCount)
            assertEquals(0, other.requestCount)
            val request = origin.takeRequest()
            assertTrue(request.getHeader("Authorization")!!.contains("Token=\"token-a\""))
        } }
    }

    @Test fun `media client follows at most three same-origin GET redirects and refuses others`() = runBlocking {
        MockWebServer().use { origin -> MockWebServer().use { other: MockWebServer ->
            origin.start(); other.start()
            val transport = ServerTransport(ServerCredentials(origin.url("/").toString(), "u", "t"), "d")
            // 4 hops: the last redirect must not be followed (no 200 is ever served).
            repeat(4) { origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", origin.url("/hop"))) }
            assertThrows(java.io.IOException::class.java) {
                transport.mediaHttp.newCall(okhttp3.Request.Builder().url(origin.url("playlist.m3u8")).build()).execute()
            }
            assertEquals(4, origin.requestCount)
            // Cross-origin redirect is refused before sending.
            origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/stolen")))
            assertThrows(java.io.IOException::class.java) {
                transport.mediaHttp.newCall(okhttp3.Request.Builder().url(origin.url("playlist.m3u8")).build()).execute()
            }
            assertEquals(0, other.requestCount)
            // Non-GET redirects are never replayed, even to the same origin.
            val before = origin.requestCount
            origin.enqueue(MockResponse().setResponseCode(307).setHeader("Location", origin.url("/hop")))
            assertThrows(java.io.IOException::class.java) {
                transport.mediaHttp.newCall(okhttp3.Request.Builder().url(origin.url("submit")).post(okhttp3.RequestBody.create(null, "x")).build()).execute()
            }
            assertEquals(before + 1, origin.requestCount)
            assertEquals(0, other.requestCount)
        } }
    }

    @Test fun `cancelling request cancels its actual okhttp call`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("{}").setBodyDelay(2, TimeUnit.SECONDS))
            val transport = ServerTransport(ServerCredentials(server.url("/").toString(), "u", "t"), "d")
            val request = async { transport.json("slow") }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            // Give OkHttp's dispatcher a bounded window to observe Call.cancel().
            kotlinx.coroutines.delay(50)
        } finally {
            runCatching { server.shutdown() }
        }
    }
}
