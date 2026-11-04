package com.roman.zemzeme.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global chat display preferences: message layout, text size, clock format and
 * a simple keyword filter for incoming messages.
 */
object ChatDisplaySettings {
    const val LAYOUT_BUBBLES = 0
    const val LAYOUT_COMPACT = 1

    private const val PREFS_NAME = "chat_display_settings"
    private const val KEY_LAYOUT = "layout"
    private const val KEY_TEXT_SIZE = "text_size"
    private const val KEY_USE_24H = "use_24h"
    private const val KEY_BLOCKED_WORDS = "blocked_words"
    private const val KEY_BLUR_IMAGES = "blur_images"

    private val _layoutFlow = MutableStateFlow(LAYOUT_BUBBLES)
    val layoutFlow: StateFlow<Int> = _layoutFlow.asStateFlow()

    private val _textSizeFlow = MutableStateFlow(15)
    val textSizeFlow: StateFlow<Int> = _textSizeFlow.asStateFlow()

    private val _use24hFlow = MutableStateFlow(true)
    val use24hFlow: StateFlow<Boolean> = _use24hFlow.asStateFlow()

    private val _blockedWordsFlow = MutableStateFlow("")
    val blockedWordsFlow: StateFlow<String> = _blockedWordsFlow.asStateFlow()

    private val _blurImagesFlow = MutableStateFlow(false)
    val blurImagesFlow: StateFlow<Boolean> = _blurImagesFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _layoutFlow.value = prefs.getInt(KEY_LAYOUT, LAYOUT_BUBBLES)
        _textSizeFlow.value = prefs.getInt(KEY_TEXT_SIZE, 15).coerceIn(12, 22)
        _use24hFlow.value = prefs.getBoolean(KEY_USE_24H, true)
        _blockedWordsFlow.value = prefs.getString(KEY_BLOCKED_WORDS, "") ?: ""
        _blurImagesFlow.value = prefs.getBoolean(KEY_BLUR_IMAGES, false)
    }

    fun setLayout(context: Context, layout: Int) {
        prefs(context).edit().putInt(KEY_LAYOUT, layout).apply()
        _layoutFlow.value = layout
    }

    fun setTextSize(context: Context, size: Int) {
        val clamped = size.coerceIn(12, 22)
        prefs(context).edit().putInt(KEY_TEXT_SIZE, clamped).apply()
        _textSizeFlow.value = clamped
    }

    fun setUse24h(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_24H, enabled).apply()
        _use24hFlow.value = enabled
    }

    fun setBlockedWords(context: Context, words: String) {
        prefs(context).edit().putString(KEY_BLOCKED_WORDS, words.trim()).apply()
        _blockedWordsFlow.value = words.trim()
    }

    fun setBlurImages(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BLUR_IMAGES, enabled).apply()
        _blurImagesFlow.value = enabled
    }

    /** Returns true when the message content should be hidden by the keyword filter. */
    fun isBlocked(content: String): Boolean {
        val raw = _blockedWordsFlow.value
        if (raw.isBlank()) return false
        val lower = content.lowercase()
        return raw.split(',', '\n')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .any { lower.contains(it) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
