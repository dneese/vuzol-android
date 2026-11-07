package com.roman.zemzeme.wifi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One finished or running call of an offline room. */
data class CallHistoryEntry(
    val id: String,
    val name: String,
    val outgoing: Boolean,
    val missed: Boolean,
    val timestamp: Long,
    val durationSeconds: Int = 0
)

/**
 * The calls of the offline rooms. The list is shown in the Calls tab, like
 * the call history of Telegram.
 */
object CallHistoryStore {

    private const val MAX_ENTRIES = 50

    private val _entries = MutableStateFlow<List<CallHistoryEntry>>(emptyList())
    val entries: StateFlow<List<CallHistoryEntry>> = _entries.asStateFlow()

    /** Remembers a call that starts now. */
    fun start(id: String, name: String, outgoing: Boolean) {
        val entry = CallHistoryEntry(
            id = id,
            name = name,
            outgoing = outgoing,
            missed = false,
            timestamp = System.currentTimeMillis()
        )
        _entries.value = (listOf(entry) + _entries.value.filterNot { it.id == id }).take(MAX_ENTRIES)
    }

    /** Writes the length of a finished call. */
    fun finish(id: String, durationSeconds: Int, missed: Boolean = false) {
        _entries.value = _entries.value.map { entry ->
            if (entry.id == id) entry.copy(durationSeconds = durationSeconds, missed = missed) else entry
        }
    }

    /** A call that nobody answered. */
    fun missed(id: String, name: String, outgoing: Boolean) {
        val entry = CallHistoryEntry(
            id = id,
            name = name,
            outgoing = outgoing,
            missed = true,
            timestamp = System.currentTimeMillis()
        )
        _entries.value = (listOf(entry) + _entries.value.filterNot { it.id == id }).take(MAX_ENTRIES)
    }
}
