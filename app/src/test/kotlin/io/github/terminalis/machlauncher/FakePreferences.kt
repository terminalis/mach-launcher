package io.github.terminalis.machlauncher

import android.content.SharedPreferences

class FakePreferences(initial: Map<String, Any> = emptyMap()) : SharedPreferences {
    private val values = HashMap(initial)

    override fun getAll(): Map<String, *> = values
    override fun getString(key: String, default: String?): String? = values[key] as String? ?: default
    override fun getStringSet(key: String, default: Set<String>?): Set<String>? = throw UnsupportedOperationException()
    override fun getInt(key: String, default: Int): Int = values[key] as Int? ?: default
    override fun getLong(key: String, default: Long): Long = values[key] as Long? ?: default
    override fun getFloat(key: String, default: Float): Float = values[key] as Float? ?: default
    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as Boolean? ?: default
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value!! }
        override fun putStringSet(key: String, value: Set<String>?) = throw UnsupportedOperationException()
        override fun putInt(key: String, value: Int) = apply { values[key] = value }
        override fun putLong(key: String, value: Long) = apply { values[key] = value }
        override fun putFloat(key: String, value: Float) = apply { values[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
        override fun remove(key: String) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit() = true
        override fun apply() = Unit
    }
}
