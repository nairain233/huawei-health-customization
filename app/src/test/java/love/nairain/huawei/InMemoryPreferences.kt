package love.nairain.huawei

import android.content.SharedPreferences

internal class InMemoryPreferences(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {
    private val values = initial.toMutableMap()
    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        values[key] as? Set<String> ?: defValues
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String) = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false
        override fun putString(key: String, value: String?) = update(key, value)
        override fun putStringSet(key: String, values: Set<String>?) = update(key, values)
        override fun putInt(key: String, value: Int) = update(key, value)
        override fun putLong(key: String, value: Long) = update(key, value)
        override fun putFloat(key: String, value: Float) = update(key, value)
        override fun putBoolean(key: String, value: Boolean) = update(key, value)
        override fun remove(key: String): SharedPreferences.Editor = apply { removals += key }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) values.clear()
            removals.forEach(values::remove)
            values.putAll(updates)
        }
        private fun update(key: String, value: Any?): SharedPreferences.Editor = apply {
            removals -= key
            updates[key] = value
        }
    }
}
