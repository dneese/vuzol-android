package com.roman.zemzeme.ui

import com.roman.zemzeme.services.ChatHistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Emoji reactions for chat messages.
 *
 * Keyed by message id, then emoji, then the set of senders. Kept in memory and
 * persisted to the local history database so reactions survive restarts.
 */
object ReactionsStore {
    val SUPPORTED = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

    private val _reactions =
        MutableStateFlow<Map<String, Map<String, Set<String>>>>(emptyMap())
    val reactions: StateFlow<Map<String, Map<String, Set<String>>>> =
        _reactions.asStateFlow()

    fun load(persisted: Map<String, Map<String, Set<String>>>) {
        if (persisted.isEmpty()) return
        synchronized(this) {
            val merged = persisted.mapValues { (_, emojis) ->
                emojis.mapValues { (_, senders) -> senders.toMutableSet() }.toMutableMap()
            }.toMutableMap()
            _reactions.value.forEach { (msgId, emojis) ->
                val target = merged.getOrPut(msgId) { mutableMapOf() }
                emojis.forEach { (emoji, senders) ->
                    target.getOrPut(emoji) { mutableSetOf() }.addAll(senders)
                }
            }
            _reactions.value = merged
        }
    }

    fun apply(messageId: String, emoji: String, sender: String, persist: Boolean = true) {
        if (emoji.isBlank() || sender.isBlank()) return
        synchronized(this) {
            val current = _reactions.value.toMutableMap()
            val perMessage = (current[messageId] ?: emptyMap()).toMutableMap()
            val senders = (perMessage[emoji] ?: emptySet()).toMutableSet()
            val added = senders.add(sender)
            perMessage[emoji] = senders
            current[messageId] = perMessage
            _reactions.value = current
            if (persist && added) ChatHistoryStore.saveReaction(messageId, emoji, sender)
        }
    }

    fun remove(messageId: String, emoji: String, sender: String, persist: Boolean = true) {
        synchronized(this) {
            val current = _reactions.value.toMutableMap()
            val perMessage = (current[messageId] ?: return).toMutableMap()
            val senders = (perMessage[emoji] ?: return).toMutableSet()
            if (!senders.remove(sender)) return
            if (senders.isEmpty()) perMessage.remove(emoji) else perMessage[emoji] = senders
            if (perMessage.isEmpty()) current.remove(messageId) else current[messageId] = perMessage
            _reactions.value = current
            if (persist) ChatHistoryStore.removeReaction(messageId, emoji, sender)
        }
    }

    fun forMessage(messageId: String): Map<String, Set<String>> =
        _reactions.value[messageId] ?: emptyMap()

    /** Returns true when the user had already reacted with that emoji (i.e. it was removed). */
    fun toggle(messageId: String, emoji: String, sender: String): Boolean {
        val existing = forMessage(messageId)[emoji]?.contains(sender) == true
        if (existing) remove(messageId, emoji, sender) else apply(messageId, emoji, sender)
        return existing
    }

    fun clear() {
        _reactions.value = emptyMap()
    }
}
