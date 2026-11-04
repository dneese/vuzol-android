package com.roman.zemzeme.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ghost mode hides the user from others: no mesh announcements, no Nostr
 * presence heartbeats and no typing indicators are sent while it is active.
 * Messages can still be sent and received.
 */
object GhostMode {
    private const val PREFS_NAME = "ghost_mode"
    private const val KEY_ENABLED = "enabled"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        val application = context.applicationContext
        appContext = application
        val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    /** True when the app must stay invisible to other peers. */
    fun isActive(): Boolean = _enabled.value
}
