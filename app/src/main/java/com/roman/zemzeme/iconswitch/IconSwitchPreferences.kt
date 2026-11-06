package com.roman.zemzeme.iconswitch

import android.content.Context
import android.content.SharedPreferences

internal class IconSwitchPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("icon_switch_prefs", Context.MODE_PRIVATE)

    var currentAliasIndex: Int
        get() = prefs.getInt(KEY_CURRENT_ALIAS, DEFAULT_ALIAS)
        set(value) = prefs.edit().putInt(KEY_CURRENT_ALIAS, value).apply()

    /** Alias that can be disabled later, when the app does not run through it. */
    var pendingDisableIndex: Int
        get() = prefs.getInt(KEY_PENDING_DISABLE, 0)
        set(value) = prefs.edit().putInt(KEY_PENDING_DISABLE, value).apply()

    companion object {
        private const val KEY_CURRENT_ALIAS = "current_alias_index"
        private const val KEY_PENDING_DISABLE = "pending_disable_index"
        const val DEFAULT_ALIAS = 1
    }
}
