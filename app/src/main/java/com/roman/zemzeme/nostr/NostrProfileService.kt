package com.roman.zemzeme.nostr

import android.app.Application
import android.content.Context
import android.util.Log
import java.util.Collections

/**
 * Requests NIP-01 metadata (kind 0) for public keys we meet in channels.
 */
class NostrProfileService private constructor(private val application: Application) {
    companion object {
        private const val TAG = "NostrProfileService"
        private const val MAX_REQUESTS = 150

        @Volatile private var INSTANCE: NostrProfileService? = null

        fun get(context: Context): NostrProfileService = INSTANCE ?: synchronized(this) {
            INSTANCE ?: NostrProfileService(context.applicationContext).also { INSTANCE = it }
        }
    }

    private val requested = Collections.synchronizedSet(mutableSetOf<String>())
    private val relayManager get() = NostrRelayManager.getInstance(application)

    fun request(pubkey: String) {
        val key = pubkey.trim().lowercase()
        if (key.length < 32) return
        if (requested.size >= MAX_REQUESTS) return
        if (!requested.add(key)) return
        try {
            val filter = NostrFilter(
                kinds = listOf(NostrKind.METADATA),
                authors = listOf(key),
                limit = 1
            )
            relayManager.subscribe(filter, "profile-$key") { event ->
                if (event.kind != NostrKind.METADATA) return@subscribe
                if (!event.pubkey.equals(key, ignoreCase = true)) return@subscribe
                val profile = NostrProfileStore.parse(event.content) ?: return@subscribe
                NostrProfileStore.apply(key, profile)
            }
        } catch (e: Exception) {
            Log.w(TAG, "profile request failed: ${e.message}")
        }
    }
}
