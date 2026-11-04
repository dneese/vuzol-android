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
    private const val KEY_SOUND_PACK = "sound_pack"

    /** Default system notification sound. */
    const val PACK_SYSTEM = 0
    /** Short friendly double beep. */
    const val PACK_BEEP = 1
    /** Short rising pop. */
    const val PACK_POP = 2
    /** Soft two note chime. */
    const val PACK_CHIME = 3
    const val PACK_COUNT = 4

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(true)
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val _soundPack = MutableStateFlow(PACK_SYSTEM)
    val soundPack: StateFlow<Int> = _soundPack.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val application = context.applicationContext
        appContext = application
        val shared = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = shared
        _soundEnabled.value = shared.getBoolean(KEY_SOUND, true)
        _vibrationEnabled.value = shared.getBoolean(KEY_VIBRATION, true)
        _soundPack.value = shared.getInt(KEY_SOUND_PACK, PACK_SYSTEM).coerceIn(0, PACK_COUNT - 1)
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

    fun setSoundPack(pack: Int) {
        val clamped = pack.coerceIn(0, PACK_COUNT - 1)
        _soundPack.value = clamped
        prefs?.edit()?.putInt(KEY_SOUND_PACK, clamped)?.apply()
        refreshChannels()
    }

    private fun refreshChannels() {
        val context = appContext ?: return
        try { NotificationManager.syncChannels(context) } catch (_: Throwable) {}
    }
}
