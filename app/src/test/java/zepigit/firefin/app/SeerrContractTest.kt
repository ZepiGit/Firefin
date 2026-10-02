package zepigit.firefin.app

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import zepigit.firefin.app.data.*
import zepigit.firefin.app.images.TmdbArtwork

class SeerrContractTest {
    private fun transport(server: MockWebServer) = ServerTransport(ServerCredentials(server.url("/jellyfin/").toString(), "u", "synthetic"), "d")
    private fun connectResponses(server: MockWebServer) {
        listOf("{\"installed\":true}", "{\"Enabled\":true,\"UserEnabled\":true}", "{\"authenticated\":true}", "{\"id\":7,\"permissions\":32}").forEach { server.enqueue(MockResponse().setBody(it)) }
    }
    private fun movie(status: Int = 1) = SeerrMedia(12, "movie", "Fixture", "", null, null, 2026, status)

    @Test fun `permissions distinguish movie and tv`() {
        assertTrue(SeerrUser(7, 262144).canRequest("movie"))
        assertFalse(SeerrUser(7, 262144).canRequest("tv"))
        assertTrue(SeerrUser(7, 524288).canRequest("tv"))
        assertFalse(SeerrUser(7, 524288).canRequest("movie"))
    }
    @Test fun `request bodies exclude available blacklisted and nonexistent seasons`() {
        for (status in listOf(2,3,4,5,6)) assertThrows(IllegalArgumentException::class.java) { movie(status).requestBody(emptyList()) }
        val series = SeerrMedia(9,"tv","Series","",null,null,0,4,
            listOf(SeerrSeason(0,"Specials",1,0),SeerrSeason(1,"One",4,5),SeerrSeason(3,"Three",4,0)))
        assertEquals(listOf(3), series.eligibleSeasons.map { it.number })
        assertEquals(3, series.requestBody(listOf(3)).getJSONArray("seasons").getInt(0))
        assertThrows(IllegalArgumentException::class.java) { series.requestBody(listOf(2)) }
    }
    @Test fun `search excludes people and preserves real media ids`() {
        val page = SeerrPage.from(JSONObject("""{"page":2,"totalPages":3,"results":[{"id":5,"mediaType":"person","name":"Person"},{"id":9,"mediaType":"tv","name":"Serie"}]}"""))
        assertEquals(2,page.page); assertEquals(3,page.totalPages); assertEquals("tv:9",page.items.single().key)
    }
    @Test fun `request failed and completed labels are distinct`() {
        assertEquals(R.string.seerr_request_failed, SeerrRequest(1,2,"movie",4).statusLabelRes)
        assertEquals(R.string.seerr_request_completed, SeerrRequest(1,2,"movie",5).statusLabelRes)
    }
    @Test fun `seerr errors carry a localizable resource and an english diagnostic`() {
        assertEquals(R.string.seerr_error_401, SeerrException(401).messageRes)
        assertEquals(emptyList<Any>(), SeerrException(401).formatArgs)
        val other = SeerrException(418)
        assertEquals(R.string.seerr_error_other, other.messageRes)
        assertEquals(listOf<Any>(418), other.formatArgs)
        assertEquals("Seerr request failed (HTTP 418).", other.message)
    }
    @Test fun `tmdb URLs reject traversal userinfo and full URLs`() {
        assertNull(TmdbArtwork.url("https://evil.test/a.jpg"))
        assertNull(TmdbArtwork.url("/../evil.jpg"))
        assertNull(TmdbArtwork.url("//evil.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w342/test.jpg",TmdbArtwork.url("/test.jpg"))
    }
    @Test fun `proxy preserves subpath and never puts credentials in URL`() = runBlocking {
        MockWebServer().use { server ->
            server.start(); connectResponses(server)
            server.enqueue(MockResponse().setBody("{\"page\":1,\"totalPages\":1,\"results\":[]}"))
            val client=SeerrClient(transport(server)); assertTrue(client.connect()); client.page("trending",1,"ä &+")
            repeat(4) { server.takeRequest() }
            val request=server.takeRequest()
            assertTrue(request.path!!.startsWith("/jellyfin/Moonfin/Jellyseerr/Api/search?query="))
            assertTrue(request.getHeader("Authorization")!!.contains("synthetic"))
            assertFalse(request.path!!.contains("synthetic"))
        }
    }
    @Test fun `request 401 maps session expiry or Seerr auth separately`() = runBlocking {
        MockWebServer().use { server ->
            server.start(); connectResponses(server)
            val client=SeerrClient(transport(server)); assertTrue(client.connect())
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            assertThrows(SessionExpiredException::class.java) { runBlocking { client.request(movie(), emptyList()) } }
        }
        MockWebServer().use { server ->
            server.start(); connectResponses(server)
            val client=SeerrClient(transport(server)); assertTrue(client.connect())
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            assertThrows(SeerrException::class.java) { runBlocking { client.request(movie(), emptyList()) } }
        }
        Unit
    }

    @Test fun `auth me without permissions does not establish identity`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("{\"installed\":true}"))
            server.enqueue(MockResponse().setBody("{\"Enabled\":true,\"UserEnabled\":true}"))
            server.enqueue(MockResponse().setBody("{\"authenticated\":true}"))
            server.enqueue(MockResponse().setBody("{\"id\":7}"))
            val client = SeerrClient(transport(server))
            assertFalse(client.connect())
            assertEquals(0, client.user.id)
            connectResponses(server)
            assertTrue(client.connect())
            assertEquals(8, server.requestCount)
        }
    }

    @Test fun `invalidate forces a fresh probe after cached connection`() = runBlocking {
        MockWebServer().use { server ->
            server.start(); connectResponses(server)
            val client = SeerrClient(transport(server))
            assertTrue(client.connect())
            assertTrue(client.connect())
            assertEquals(4, server.requestCount)
            client.invalidate()
            connectResponses(server)
            assertTrue(client.connect())
            assertEquals(8, server.requestCount)
        }
    }

    @Test fun `reset transport clears cached identity and uses new origin`() = runBlocking {
        MockWebServer().use { first ->
            MockWebServer().use { second ->
                first.start(); second.start()
                connectResponses(first)
                val client = SeerrClient(transport(first))
                assertTrue(client.connect())
                client.resetTransport(transport(second))
                assertEquals(0, client.user.id)
                connectResponses(second)
                assertTrue(client.connect())
                assertEquals(4, first.requestCount)
                assertEquals(4, second.requestCount)
            }
        }
    }

    @Test fun `request outcomes 201 200 202 409 500 never retry POST`() = runBlocking {
        MockWebServer().use { server ->
            server.start(); connectResponses(server)
            val client=SeerrClient(transport(server)); assertTrue(client.connect())
            val expected = listOf(201 to SeerrClient.RequestResult.CREATED, 200 to SeerrClient.RequestResult.CREATED,
                202 to SeerrClient.RequestResult.UNKNOWN,409 to SeerrClient.RequestResult.ALREADY_REQUESTED,500 to SeerrClient.RequestResult.UNKNOWN)
            for ((status,outcome) in expected) {
                server.enqueue(MockResponse().setResponseCode(status).setBody("{\"id\":5}"))
                assertEquals(outcome,client.request(movie(),emptyList()))
            }
            assertEquals(9,server.requestCount)
        }
    }
    @Test fun `503 retry-after zero must not repeat request POST`() = runBlocking {
        MockWebServer().use { server ->
            server.start(); connectResponses(server)
            val client = SeerrClient(transport(server)); assertTrue(client.connect())
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0").setBody("{}"))
            server.enqueue(MockResponse().setResponseCode(201).setBody("{\"id\":8}"))
            assertEquals(SeerrClient.RequestResult.UNKNOWN, client.request(movie(), emptyList()))
            assertEquals(5, server.requestCount)
        }
    }

    @Test fun `snapshots share bounded resources and response string excludes body`() {
        MockWebServer().use { server ->
            server.start(); val source=transport(server); val copy=source.snapshot()
            assertSame(source.http.dispatcher,copy.http.dispatcher)
            assertSame(source.http.connectionPool,copy.mediaHttp.connectionPool)
            assertFalse(ServerTransport.JsonResponse(200,"private").toString().contains("private"))
        }
    }
    @Test fun `error body capped while chunked successful body is refused`() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val http=transport(server)
            server.enqueue(MockResponse().setResponseCode(500).setBody("x".repeat(70*1024)))
            assertEquals(64*1024,http.response("error").body.length)
            server.enqueue(MockResponse().setChunkedBody("x".repeat(8*1024*1024+1),4096))
            assertTrue(runCatching { http.response("large") }.isFailure)
        }
    }
}
