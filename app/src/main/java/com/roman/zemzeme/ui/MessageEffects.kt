package com.roman.zemzeme.ui

/**
 * Telegram-style message effects: sending one of these emoji on its own
 * triggers a small animation in the chat.
 */
object MessageEffects {
    private val effects = setOf("❤️", "🔥", "👍", "😍", "🎉", "😂", "💯", "🚀", "👏", "🙏")

    fun effectFor(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return if (effects.contains(trimmed)) trimmed else null
    }
}
