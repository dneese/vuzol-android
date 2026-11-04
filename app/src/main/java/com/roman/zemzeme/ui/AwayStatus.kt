package com.roman.zemzeme.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Away" status of the local user.
 *
 * While the user is away the app shows a banner above the chat input and can
 * automatically answer mentions with a short automatic reply.
 */
object AwayStatus {
    private const val PREFS_NAME = "away_status"
    private const val KEY_ACTIVE = "active"
    private const val KEY_MESSAGE = "message"
    private const val KEY_AUTO_REPLY = "auto_reply"

    data class State(
        val active: Boolean = false,
        val message: String = "",
        val autoReply: Boolean = true
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        val application = context.applicationContext
        appContext = application
        val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _state.value = State(
            active = prefs.getBoolean(KEY_ACTIVE, false),
            message = prefs.getString(KEY_MESSAGE, "") ?: "",
            autoReply = prefs.getBoolean(KEY_AUTO_REPLY, true)
        )
    }

    fun setAway(context: Context, message: String) {
        val text = message.trim()
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_MESSAGE, text)
            .apply()
        _state.value = _state.value.copy(active = true, message = text)
    }

    fun clear(context: Context) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).apply()
        _state.value = _state.value.copy(active = false, message = "")
    }

    fun setAutoReply(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_REPLY, enabled).apply()
        _state.value = _state.value.copy(autoReply = enabled)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
