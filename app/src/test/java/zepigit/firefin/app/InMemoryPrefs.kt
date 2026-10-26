package zepigit.firefin.app

/** Minimal in-memory SharedPreferences so SessionStore runs in JVM tests. */
class InMemoryPrefs : android.content.SharedPreferences {
    private val map = mutableMapOf<String, Any>()
    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String?, defValue: Int) = map[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = map[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = map[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun contains(key: String?) = map.containsKey(key)
    override fun edit() = object : android.content.SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor {
            if (value != null) map[key!!] = value else map.remove(key)
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?) = this
        override fun putInt(key: String?, value: Int) = apply { map[key!!] = value }
        override fun putLong(key: String?, value: Long) = apply { map[key!!] = value }
        override fun putFloat(key: String?, value: Float) = apply { map[key!!] = value }
        override fun putBoolean(key: String?, value: Boolean) = apply { map[key!!] = value }
        override fun remove(key: String?): android.content.SharedPreferences.Editor {
            map.remove(key)
            return this
        }
        override fun clear(): android.content.SharedPreferences.Editor {
            map.clear()
            return this
        }
        override fun commit() = true
        override fun apply() = Unit
    }
    override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
