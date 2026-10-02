package zepigit.firefin.app

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zepigit.firefin.app.data.*

class SeerrEnvelopeTest {
    @Test fun `wrapped request response is parsed as created`() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val client = SeerrClient(ServerTransport(ServerCredentials(server.url("/").toString(),"u","t"),"d"))
            listOf("{\"installed\":true}","{\"Enabled\":true,\"UserEnabled\":true}","{\"authenticated\":true}","{\"id\":7,\"permissions\":32}","{\"FileContents\":\"eyJpZCI6NX0=\"}").forEach { server.enqueue(MockResponse().setBody(it)) }
            assertTrue(client.connect())
            assertEquals(SeerrClient.RequestResult.CREATED, client.request(SeerrMedia(5,"movie","Wrapped","",null,null,2026,1), emptyList()))
        }
    }

    @Test fun `proxy FileContents envelope is unwrapped for discovery`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val encoded = "eyJwYWdlIjoxLCJ0b3RhbFBhZ2VzIjoxLCJyZXN1bHRzIjpbeyJpZCI6NywibWVkaWFUeXBlIjoibW92aWUiLCJ0aXRsZSI6IldyYXBwZWQifV19"
            listOf("{\"installed\":true}","{\"Enabled\":true,\"UserEnabled\":true}","{\"authenticated\":true}","{\"id\":7,\"permissions\":32}","{\"FileContents\":\"$encoded\"}").forEach { server.enqueue(MockResponse().setBody(it)) }
            val transport = ServerTransport(ServerCredentials(server.url("/").toString(),"u","t"),"d")
            val client = SeerrClient(transport)
            assertTrue(client.connect())
            assertEquals("Wrapped", client.page("trending",1).items.single().title)
        }
    }
}
