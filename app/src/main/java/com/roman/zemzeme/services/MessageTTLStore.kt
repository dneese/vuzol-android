package com.roman.zemzeme.services

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-conversation "auto-delete" timer (Telegram-style disappearing messages).
 * Value is the lifetime of a message in seconds; 0 means the timer is off.
 */
object MessageTTLStore {
    private const val PREFS_NAME = "message_ttl"
    private const val KEY_PREFIX = "ttl_"

    private val _values = MutableStateFlow<Map<String, Long>>(emptyMap())
    val values: StateFlow<Map<String, Long>> = _values.asStateFlow()

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        val loaded = mutableMapOf<String, Long>()
        p.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX) && value is Long) {
                loaded[key.removePrefix(KEY_PREFIX)] = value
            }
        }
        _values.value = loaded
    }

    fun ttl(conversationKey: String): Long = _values.value[conversationKey] ?: 0L

    fun setTtl(context: Context, conversationKey: String, seconds: Long) {
        val updated = _values.value.toMutableMap()
        if (seconds <= 0L) updated.remove(conversationKey) else updated[conversationKey] = seconds
        _values.value = updated
        val editor = (prefs ?: context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)).edit()
        editor.remove(KEY_PREFIX + conversationKey)
        if (seconds > 0L) editor.putLong(KEY_PREFIX + conversationKey, seconds)
        editor.apply()
    }

    fun clear(context: Context, conversationKey: String) = setTtl(context, conversationKey, 0L)
}
