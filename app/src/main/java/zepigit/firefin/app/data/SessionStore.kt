package zepigit.firefin.app.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** Persisted Jellyfin session (server URL, user, token, device identity). */
class SessionStore(
    private val prefs: SharedPreferences,
) {

    constructor(context: Context) : this(
        context.getSharedPreferences("firefin_session", Context.MODE_PRIVATE),
    )

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER, value).apply()

    var userId: String
        get() = prefs.getString(KEY_USER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var serverName: String
        get() = prefs.getString(KEY_SERVER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_NAME, value).apply()

    var accessToken: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    val deviceId: String =
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    val isLoggedIn: Boolean get() = serverUrl.isNotEmpty() && accessToken.isNotEmpty() && userId.isNotEmpty()

    /** Signs out: removes the account and token, keeps server address and user name to prefill the next sign-in. */
    fun clear() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_SERVER_NAME)
            .remove(KEY_TOKEN)
            .apply()
    }

    private companion object {
        const val KEY_SERVER = "serverUrl"
        const val KEY_USER_ID = "userId"
        const val KEY_USER_NAME = "userName"
        const val KEY_SERVER_NAME = "serverName"
        const val KEY_TOKEN = "accessToken"
        const val KEY_DEVICE_ID = "deviceId"
    }
}
