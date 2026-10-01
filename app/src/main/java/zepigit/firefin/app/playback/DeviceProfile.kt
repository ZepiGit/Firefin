package zepigit.firefin.app.playback

import org.json.JSONObject

/**
 * Conservative Jellyfin DeviceProfile for the Fire TV Stick AFTT (API 22, ARMv7):
 * direct play restricted to H.264 + stereo AAC/MP3, transcoding ceiling
 * 1280x720 and 4 000 000 bit/s total (video+audio), HLS TS output.
 */
object DeviceProfile {

    const val PROFILE_NAME = "Firefin for Fire TV (32-bit)"
    const val MAX_WIDTH = 1280
    const val MAX_HEIGHT = 720
    const val MAX_BITRATE = 4_000_000L
    const val MAX_AUDIO_CHANNELS = 2

    fun build(): JSONObject = JSONObject().apply {
        put("Name", PROFILE_NAME)
        put("MaxStreamingBitrate", MAX_BITRATE)
        put("MaxStaticBitrate", MAX_BITRATE)
        put("MusicStreamingTranscodingBitrate", 192_000)
        put("DirectPlayProfiles", org.json.JSONArray().apply {
            put(JSONObject().apply {
                put("Container", "mp4,mkv,mov")
                put("Type", "Video")
                put("VideoCodec", "h264")
                put("AudioCodec", "aac,mp3,ac3")
            })
            put(JSONObject().apply {
                put("Container", "aac,mp3")
                put("Type", "Audio")
            })
        })
        put("TranscodingProfiles", org.json.JSONArray().apply {
            put(JSONObject().apply {
                put("Container", "ts")
                put("Type", "Video")
                put("VideoCodec", "h264")
                put("AudioCodec", "aac")
                put("Protocol", "http")
                put("CopyTimestamps", true)
                put("BreakOnNonKeyFrames", true)
            })
            put(JSONObject().apply {
                put("Container", "aac")
                put("Type", "Audio")
                put("AudioCodec", "aac")
                put("Protocol", "http")
            })
        })
        put("CodecProfiles", org.json.JSONArray().apply {
            put(JSONObject().apply {
                put("Type", "VideoCodec")
                put("Codec", "h264")
                put("Conditions", org.json.JSONArray().apply {
                    put(condition("LessThanEqual", "Width", MAX_WIDTH.toString()))
                    put(condition("LessThanEqual", "Height", MAX_HEIGHT.toString()))
                    put(condition("LessThanEqual", "VideoBitrate", MAX_BITRATE.toString()))
                })
            })
            put(JSONObject().apply {
                put("Type", "VideoAudioCodec")
                put("Codec", "aac,ac3,mp3")
                put("Conditions", org.json.JSONArray().apply {
                    put(condition("LessThanEqual", "AudioChannels", MAX_AUDIO_CHANNELS.toString()))
                })
            })
        })
        put("ResponseProfiles", org.json.JSONArray())
        put("ContainerProfiles", org.json.JSONArray())
        put("SubtitleProfiles", org.json.JSONArray().apply {
            put(JSONObject().apply { put("Format", "srt"); put("Method", "External") })
            put(JSONObject().apply { put("Format", "vtt"); put("Method", "External") })
        })
    }

    private fun condition(cond: String, property: String, value: String): JSONObject = JSONObject().apply {
        put("Condition", cond)
        put("Property", property)
        put("Value", value)
        put("IsRequired", false)
    }
}
