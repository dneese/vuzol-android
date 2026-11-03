package com.roman.zemzeme.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-chat appearance: accent color and wallpaper.
 * Settings are stored per conversation key in SharedPreferences.
 */
object ChatAppearanceStore {
    val accentPalette: List<Color> = listOf(
        Color(0xFF2AABEE),
        Color(0xFF3EAE5A),
        Color(0xFF8E5CE6),
        Color(0xFFE85E8A),
        Color(0xFFF0922B),
        Color(0xFFE5484D),
        Color(0xFF00AFC7),
        Color(0xFF6E7A8A)
    )

    const val WALLPAPER_SOLID = 0
    const val WALLPAPER_GRADIENT = 1
    const val WALLPAPER_PHOTO = 2

    /** Pattern wallpapers use mode = WALLPAPER_PATTERN_BASE + pattern index. */
    const val WALLPAPER_PATTERN_BASE = 10

    data class Appearance(
        val accentIndex: Int = 0,
        val wallpaperMode: Int = WALLPAPER_SOLID,
        val wallpaperPath: String? = null
    ) {
        val accent: Color
            get() = accentPalette.getOrElse(accentIndex) { accentPalette.first() }
    }

    private const val PREFS_NAME = "chat_appearance"
    private const val KEY_ACCENT = "accent_"
    private const val KEY_WALLPAPER = "wallpaper_"
    private const val KEY_WALLPAPER_PATH = "wallpaper_path_"

    private val cache = ConcurrentHashMap<String, Appearance>()
    private var appContext: Context? = null

    private val _appearances = MutableStateFlow<Map<String, Appearance>>(emptyMap())
    val appearances: StateFlow<Map<String, Appearance>> = _appearances.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        reload()
    }

    private fun reload() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loaded = mutableMapOf<String, Appearance>()
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith(KEY_ACCENT)) return@forEach
            val conversation = key.removePrefix(KEY_ACCENT)
            loaded[conversation] = Appearance(
                accentIndex = (value as? Int) ?: 0,
                wallpaperMode = prefs.getInt(KEY_WALLPAPER + conversation, WALLPAPER_SOLID),
                wallpaperPath = prefs.getString(KEY_WALLPAPER_PATH + conversation, null)
            )
        }
        cache.clear()
        cache.putAll(loaded)
        _appearances.value = loaded
    }

    fun get(conversationKey: String): Appearance =
        cache[conversationKey] ?: Appearance()

    fun setAccent(context: Context, conversationKey: String, accentIndex: Int) {
        save(context, conversationKey, get(conversationKey).copy(accentIndex = accentIndex))
    }

    fun setWallpaper(context: Context, conversationKey: String, mode: Int, path: String? = null) {
        save(context, conversationKey, get(conversationKey).copy(wallpaperMode = mode, wallpaperPath = path))
    }

    fun reset(context: Context, conversationKey: String) {
        val ctx = context.applicationContext
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_ACCENT + conversationKey)
            .remove(KEY_WALLPAPER + conversationKey)
            .remove(KEY_WALLPAPER_PATH + conversationKey)
            .apply()
        cache.remove(conversationKey)
        _appearances.value = _appearances.value - conversationKey
    }

    private fun save(context: Context, conversationKey: String, appearance: Appearance) {
        val ctx = context.applicationContext
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_ACCENT + conversationKey, appearance.accentIndex)
            .putInt(KEY_WALLPAPER + conversationKey, appearance.wallpaperMode)
            .putString(KEY_WALLPAPER_PATH + conversationKey, appearance.wallpaperPath)
            .apply()
        cache[conversationKey] = appearance
        _appearances.value = _appearances.value + (conversationKey to appearance)
    }
}

/** Accent color of the active chat, provided by the chat screen. */
val LocalChatAccent = staticCompositionLocalOf { ChatAppearanceStore.accentPalette.first() }
