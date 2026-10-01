package zepigit.firefin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import zepigit.firefin.app.util.Urls

class UrlsTest {

    @Test
    fun `normalizes scheme, trailing slashes and keeps subpath`() {
        assertEquals("https://192.168.1.10:8096", Urls.normalizeServer("192.168.1.10:8096"))
        assertEquals("http://192.168.1.10:8096", Urls.normalizeServer("http://192.168.1.10:8096/"))
        assertEquals("https://host.example/jellyfin", Urls.normalizeServer("  https://host.example/jellyfin///"))
        assertEquals("https://host.example/jellyfin", Urls.normalizeServer("host.example/jellyfin"))
    }

    @Test
    fun `rejects empty input`() {
        assertThrows(IllegalArgumentException::class.java) { Urls.normalizeServer("   ") }
    }

    @Test
    fun `image url carries class width, tag and token`() {
        val url = Urls.imageUrl("http://s:8096", "item1", "Primary", 320, "tagA", "tok")
        assertEquals("http://s:8096/Items/item1/Images/Primary?maxWidth=320&quality=90&tag=tagA", url)
    }

    @Test
    fun `poster image url can carry maxHeight`() {
        val url = Urls.imageUrl("http://s", "i", "Primary", 320, null, "t", maxHeight = 480)
        assertEquals("http://s/Items/i/Images/Primary?maxWidth=320&maxHeight=480&quality=90", url)
    }

    @Test
    fun `transcoding path without subpath joins at base`() {
        assertEquals(
            "https://host.example/jellyfin/videos/x/master.m3u8",
            Urls.resolveRelative("https://host.example/jellyfin", "/videos/x/master.m3u8"),
        )
    }

    @Test
    fun `transcoding path that already contains the subpath is not doubled`() {
        assertEquals(
            "https://host.example/jellyfin/videos/x/master.m3u8",
            Urls.resolveRelative("https://host.example/jellyfin", "/jellyfin/videos/x/master.m3u8"),
        )
    }

    @Test
    fun `cross origin absolute urls are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Urls.resolveRelative("http://base", "http://other/stream.m3u8")
        }
    }
}
