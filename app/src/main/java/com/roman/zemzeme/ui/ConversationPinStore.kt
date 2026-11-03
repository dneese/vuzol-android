package com.roman.zemzeme.ui

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pinned conversations shown at the top of the chat list.
 */
object ConversationPinStore {
    private const val PREFS_NAME = "conversation_pins"
    private const val KEY_PINS = "pins"

    private val _pinned = MutableStateFlow<Set<String>>(emptySet())
    val pinned: StateFlow<Set<String>> = _pinned.asStateFlow()

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        _pinned.value = p.getStringSet(KEY_PINS, emptySet())?.toSet() ?: emptySet()
    }

    fun isPinned(key: String): Boolean = _pinned.value.contains(key)

    /** Toggles the pin state and returns the new state. */
    fun toggle(context: Context, key: String): Boolean {
        val current = _pinned.value
        val updated = if (current.contains(key)) current - key else current + key
        _pinned.value = updated
        prefs?.edit()?.putStringSet(KEY_PINS, updated)?.apply()
            ?: context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_PINS, updated).apply()
        return updated.contains(key)
    }

    fun clear(context: Context, key: String) {
        val updated = _pinned.value - key
        _pinned.value = updated
        prefs?.edit()?.putStringSet(KEY_PINS, updated)?.apply()
            ?: context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_PINS, updated).apply()
    }
}
