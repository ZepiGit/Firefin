package zepigit.firefin.app.preferences

import android.content.Context
import java.security.MessageDigest
import zepigit.firefin.app.data.SessionStore
import zepigit.firefin.app.playback.DeviceProfile

class PreferenceStore(private val prefs: android.content.SharedPreferences, private val session: SessionStore) {
    constructor(context: Context, session: SessionStore) : this(context.getSharedPreferences("firefin_preferences", Context.MODE_PRIVATE), session)
    private fun key(name: String): String {
        val account = session.serverUrl + "\u0000" + session.userId
        val hash = MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).joinToString("") { "%02x".format(it) }
        return "$hash:$name"
    }
    fun stored(): StoredPreferences = StoredPreferences(
        prefs.getBoolean(key("backdropEnabled"), true),
        prefs.getString(key("homeSectionOrder"), "resume,nextUp,favorites,latest")!!.split(","),
        prefs.getLong(key("preferredBitrate"), DeviceProfile.MAX_BITRATE),
        prefs.getInt(key("preferredHeight"), DeviceProfile.MAX_HEIGHT),
        prefs.getString(key("audioLanguage"), "").orEmpty(),
        prefs.getString(key("subtitleLanguage"), "").orEmpty(),
        prefs.getInt(key("subtitleSize"), 0).coerceIn(0, 2),
        prefs.getBoolean(key("autoPlayNext"), true),
    )
    fun effective() = deriveEffective(stored(), DeviceProfile.MAX_WIDTH, DeviceProfile.MAX_HEIGHT, DeviceProfile.MAX_BITRATE)
    fun save(value: StoredPreferences) {
        prefs.edit().putBoolean(key("backdropEnabled"), value.backdropEnabled)
            .putString(key("homeSectionOrder"), value.homeSectionOrder.joinToString(","))
            .putLong(key("preferredBitrate"), value.preferredBitrate).putInt(key("preferredHeight"), value.preferredHeight)
            .putString(key("audioLanguage"), value.audioLanguage).putString(key("subtitleLanguage"), value.subtitleLanguage)
            .putInt(key("subtitleSize"), value.subtitleSize.coerceIn(0, 2))
            .putBoolean(key("autoPlayNext"), value.autoPlayNext).apply()
    }
}
