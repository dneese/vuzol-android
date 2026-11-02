package com.roman.zemzeme.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.SharedPreferences

/**
 * User preferences for notification sound and vibration.
 *
 * Android controls sound and vibration per notification channel, and the
 * settings of an existing channel cannot be changed. So a change here rebuilds
 * the app's channels (see [NotificationManager.syncChannels]).
 */
object NotificationPreferences {

    private const val PREFS_NAME = "notification_preferences"
    private const val KEY_SOUND = "sound_enabled"
    private const val KEY_VIBRATION = "vibration_enabled"

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(true)
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val application = context.applicationContext
        appContext = application
        val shared = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = shared
        _soundEnabled.value = shared.getBoolean(KEY_SOUND, true)
        _vibrationEnabled.value = shared.getBoolean(KEY_VIBRATION, true)
    }

    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_SOUND, enabled)?.apply()
        refreshChannels()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _vibrationEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_VIBRATION, enabled)?.apply()
        refreshChannels()
    }

    private fun refreshChannels() {
        val context = appContext ?: return
        try { NotificationManager.syncChannels(context) } catch (_: Throwable) {}
    }
}
