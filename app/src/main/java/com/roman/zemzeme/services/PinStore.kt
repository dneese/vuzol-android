package com.roman.zemzeme.services

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pinned message per conversation (local to this device). */
object PinStore {
    private const val PREFS = "vuzol_pins"
    private const val PREFIX = "pin_"

    @Volatile private var prefs: SharedPreferences? = null

    private val _pins = MutableStateFlow<Map<String, String>>(emptyMap())
    val pins: StateFlow<Map<String, String>> = _pins.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val loaded = mutableMapOf<String, String>()
        p.all.forEach { (key, value) ->
            if (key.startsWith(PREFIX) && value is String) {
                loaded[key.removePrefix(PREFIX)] = value
            }
        }
        _pins.value = loaded
    }

    fun pinned(conv: String): String? = _pins.value[conv]

    fun set(conv: String, messageId: String) {
        _pins.value = _pins.value.toMutableMap().apply { put(conv, messageId) }
        prefs?.edit()?.putString(PREFIX + conv, messageId)?.apply()
    }

    fun clear(conv: String) {
        _pins.value = _pins.value.toMutableMap().apply { remove(conv) }
        prefs?.edit()?.remove(PREFIX + conv)?.apply()
    }

    fun clearAll() {
        _pins.value = emptyMap()
        prefs?.edit()?.clear()?.apply()
    }
}
