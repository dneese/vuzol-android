package com.roman.zemzeme.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Profile of the local user: avatar, display name and bio.
 *
 * The avatar stays on the device, the name and bio can be published to Nostr
 * as a NIP-01 metadata event (kind 0).
 */
object OwnProfile {
    private const val PREFS_NAME = "own_profile"
    private const val KEY_AVATAR = "avatar_path"
    private const val KEY_NAME = "display_name"
    private const val KEY_ABOUT = "about"

    data class State(
        val avatarPath: String? = null,
        val displayName: String = "",
        val about: String = ""
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _state.value = State(
            avatarPath = prefs.getString(KEY_AVATAR, null),
            displayName = prefs.getString(KEY_NAME, "") ?: "",
            about = prefs.getString(KEY_ABOUT, "") ?: ""
        )
    }

    fun setAvatar(context: Context, path: String?) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AVATAR, path).apply()
        _state.value = _state.value.copy(avatarPath = path)
    }

    fun setDisplayName(context: Context, name: String) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NAME, name.trim()).apply()
        _state.value = _state.value.copy(displayName = name.trim())
    }

    fun setAbout(context: Context, about: String) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ABOUT, about.trim()).apply()
        _state.value = _state.value.copy(about = about.trim())
    }
}
