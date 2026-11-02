package com.roman.zemzeme.services

import android.content.Context
import android.content.SharedPreferences

/** Muted conversations (currently geohash channels) - no notifications, no sound. */
object MuteStore {
    private const val PREFS = "vuzol_muted"
    private const val PREFIX = "muted_"

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun isMuted(key: String): Boolean = prefs?.getBoolean(PREFIX + key, false) ?: false

    fun setMuted(key: String, muted: Boolean) {
        prefs?.edit()?.putBoolean(PREFIX + key, muted)?.apply()
    }

    fun clearAll() {
        prefs?.edit()?.clear()?.apply()
    }
}
