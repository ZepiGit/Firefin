package zepigit.firefin.app.preferences

import android.content.Context
import java.security.MessageDigest
import zepigit.firefin.app.data.SessionStore
import zepigit.firefin.app.playback.DeviceProfile

class PreferenceStore(context: Context, private val session: SessionStore) {
    private val prefs = context.getSharedPreferences("firefin_preferences", Context.MODE_PRIVATE)
    private fun key(name: String): String {
        val account = session.serverUrl + "\u0000" + session.userId
        val hash = MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).joinToString("") { "%02x".format(it) }
        return "$hash:$name"
    }
    fun stored(): StoredPreferences = StoredPreferences(
        prefs.getBoolean(key("backdropEnabled"), true),
        prefs.getString(key("homeSectionOrder"), "resume,nextUp,latest")!!.split(","),
        prefs.getLong(key("preferredBitrate"), DeviceProfile.MAX_BITRATE),
        prefs.getInt(key("preferredHeight"), DeviceProfile.MAX_HEIGHT),
        prefs.getString(key("audioLanguage"), "").orEmpty(),
        prefs.getString(key("subtitleLanguage"), "").orEmpty(),
    )
    fun effective() = deriveEffective(stored(), DeviceProfile.MAX_WIDTH, DeviceProfile.MAX_HEIGHT, DeviceProfile.MAX_BITRATE)
    fun save(value: StoredPreferences) {
        prefs.edit().putBoolean(key("backdropEnabled"), value.backdropEnabled)
            .putString(key("homeSectionOrder"), value.homeSectionOrder.joinToString(","))
            .putLong(key("preferredBitrate"), value.preferredBitrate).putInt(key("preferredHeight"), value.preferredHeight)
            .putString(key("audioLanguage"), value.audioLanguage).putString(key("subtitleLanguage"), value.subtitleLanguage).apply()
    }
}
