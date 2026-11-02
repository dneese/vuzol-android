package com.roman.zemzeme.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live "X is typing" state for geohash channels.
 * Entries expire after a few seconds; the UI prunes them on a ticker.
 */
object TypingStore {
    const val TTL_MS = 7_000L

    // geohash -> sender -> lastSeenMs
    private val _typing = MutableStateFlow<Map<String, Map<String, Long>>>(emptyMap())
    val typing: StateFlow<Map<String, Map<String, Long>>> = _typing.asStateFlow()

    fun mark(geohash: String, sender: String) {
        if (geohash.isBlank() || sender.isBlank()) return
        synchronized(this) {
            val now = System.currentTimeMillis()
            val current = _typing.value.toMutableMap()
            val people = (current[geohash] ?: emptyMap()).toMutableMap()
            people[sender] = now
            current[geohash] = people
            _typing.value = current
        }
    }

    fun peopleTyping(geohash: String, now: Long = System.currentTimeMillis()): List<String> =
        (_typing.value[geohash] ?: emptyMap())
            .filterValues { now - it < TTL_MS }
            .keys
            .sorted()

    fun prune(now: Long = System.currentTimeMillis()) {
        synchronized(this) {
            val current = _typing.value
            var changed = false
            val next = current.mapValues { (_, people) ->
                val alive = people.filterValues { now - it < TTL_MS }
                if (alive.size != people.size) changed = true
                alive
            }.filterValues { it.isNotEmpty() }
            if (changed) _typing.value = next
        }
    }

    fun clear() {
        _typing.value = emptyMap()
    }
}
