package com.roman.zemzeme.services

import android.content.Context
import android.content.SharedPreferences

/**
 * Per-conversation "last read" timestamps, used to draw the "new messages"
 * divider and to keep unread badges honest across restarts.
 */
object ReadStateStore {
    private const val PREFS = "vuzol_read_state"
    private const val PREFIX = "last_read_"

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun lastRead(conv: String, fallback: Long = 0L): Long =
        prefs?.getLong(PREFIX + conv, fallback) ?: fallback

    fun markRead(conv: String, ts: Long = System.currentTimeMillis()) {
        prefs?.edit()?.putLong(PREFIX + conv, ts)?.apply()
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
