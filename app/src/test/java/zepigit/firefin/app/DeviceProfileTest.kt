package zepigit.firefin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zepigit.firefin.app.images.ArtworkPolicy
import zepigit.firefin.app.playback.DeviceProfile

class DeviceProfileTest {

    private val profile = DeviceProfile.build()

    @Test
    fun `profile carries firefin identity`() {
        assertTrue(profile.getString("Name").startsWith("Firefin for Fire TV"))
    }

    @Test
    fun `legacy transcode ceilings are preserved incl audio`() {
        assertEquals(1280, DeviceProfile.MAX_WIDTH)
        assertEquals(720, DeviceProfile.MAX_HEIGHT)
        assertEquals(4_000_000L, DeviceProfile.MAX_BITRATE)
        assertEquals(4_000_000L, profile.getLong("MaxStreamingBitrate"))
        assertEquals(4_000_000L, profile.getLong("MaxStaticBitrate"))
    }

    @Test
    fun `h264 codec conditions bound width, height and bitrate`() {
        val codecProfiles = profile.getJSONArray("CodecProfiles")
        val types = (0 until codecProfiles.length()).map { codecProfiles.getJSONObject(it).getString("Type") }
        // Jellyfin CodecType enum: only Video | VideoAudio | Audio are valid.
        assertTrue(types.all { it in setOf("Video", "VideoAudio", "Audio") })
        assertTrue("Video" in types && "VideoAudio" in types)
        val video = codecProfiles.getJSONObject(types.indexOf("Video"))
        assertEquals("h264", video.getString("Codec"))
        val conditions = video.getJSONArray("Conditions")
        val byProp = (0 until conditions.length()).associate {
            conditions.getJSONObject(it).getString("Property") to conditions.getJSONObject(it).getString("Value")
        }
        assertEquals("1280", byProp["Width"])
        assertEquals("720", byProp["Height"])
        assertEquals("4000000", byProp["VideoBitrate"])
    }

    @Test
    fun `audio channels capped at stereo`() {
        assertEquals("2", DeviceProfile.MAX_AUDIO_CHANNELS.toString())
    }

    @Test
    fun `transcoding produces h264 aac ts`() {
        val transcoding = profile.getJSONArray("TranscodingProfiles").getJSONObject(0)
        assertEquals("ts", transcoding.getString("Container"))
        assertEquals("h264", transcoding.getString("VideoCodec"))
        assertEquals("aac", transcoding.getString("AudioCodec"))
    }

    @Test
    fun `decode buckets round up and clamp`() {
        assertEquals(160, ArtworkPolicy.decodeBucket(1))
        assertEquals(160, ArtworkPolicy.decodeBucket(160))
        assertEquals(240, ArtworkPolicy.decodeBucket(161))
        assertEquals(960, ArtworkPolicy.decodeBucket(961))
        assertEquals(320, ArtworkPolicy.decodeBucket(320))
    }
}
