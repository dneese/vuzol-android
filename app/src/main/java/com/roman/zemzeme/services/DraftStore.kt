package com.roman.zemzeme.services

import android.content.Context
import android.content.SharedPreferences

/** Unsent message drafts, kept per conversation. */
object DraftStore {
    private const val PREFS = "vuzol_drafts"
    private const val PREFIX = "draft_"

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun get(conv: String): String = prefs?.getString(PREFIX + conv, "").orEmpty()

    fun set(conv: String, text: String) {
        val p = prefs ?: return
        if (text.isBlank()) p.edit().remove(PREFIX + conv).apply()
        else p.edit().putString(PREFIX + conv, text).apply()
    }

    fun clear(conv: String) {
        prefs?.edit()?.remove(PREFIX + conv)?.apply()
    }

    fun clearAll() {
        prefs?.edit()?.clear()?.apply()
    }
}
