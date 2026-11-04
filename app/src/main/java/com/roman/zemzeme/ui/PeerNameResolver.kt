package com.roman.zemzeme.ui

import com.roman.zemzeme.nostr.NostrProfile
import com.roman.zemzeme.nostr.NostrProfileStore
import com.roman.zemzeme.nostr.GeohashAliasRegistry

/**
 * Turns raw transport ids ("nostr:f59cbbc0", "p2p:12D3KooW...", noise keys)
 * into something a human can read.
 *
 * Nostr peers are stored with a shortened public key, so the name is looked up
 * in the NIP-01 profile cache by prefix. When nothing is known the user sees a
 * short "Nostr·f59c" label instead of a wall of hex characters.
 */
object PeerNameResolver {

    /** Ids that are just transport noise and must never be shown as a name. */
    private fun looksLikeRawId(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        val lower = value.lowercase()
        if (lower.startsWith("nostr:") || lower.startsWith("nostr_") || lower.startsWith("p2p:")) return true
        if (lower.length >= 12 && lower.all { it.isDigit() || it in 'a'..'f' }) return true
        return false
    }

    /** Hex part of a Nostr peer id, without the "nostr:" / "nostr_" prefix. */
    private fun nostrHexPrefix(peerId: String): String? {
        val lower = peerId.lowercase()
        return when {
            lower.startsWith("nostr:") -> lower.removePrefix("nostr:")
            lower.startsWith("nostr_") -> lower.removePrefix("nostr_")
            else -> null
        }
    }

    private fun profileName(profile: NostrProfile?): String? {
        if (profile == null) return null
        return profile.displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: profile.name?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Resolves a readable name for a peer id. */
    fun resolve(
        peerId: String?,
        knownNickname: String? = null,
        lastMessageSender: String? = null,
        profiles: Map<String, NostrProfile>
    ): String? {
        if (!looksLikeRawId(knownNickname)) return knownNickname

        if (peerId.isNullOrBlank()) {
            return if (!looksLikeRawId(lastMessageSender)) lastMessageSender else null
        }

        val hexPrefix = nostrHexPrefix(peerId)
        if (hexPrefix != null) {
            // Exact match first (the full public key may be known already)
            profileName(profiles[hexPrefix])?.let { return it }
            // The alias registry maps "nostr_<pub16>" to the full public key
            GeohashAliasRegistry.get(peerId)?.let { fullKey ->
                profileName(profiles[fullKey.lowercase()])?.let { return it }
            }
            // Otherwise match by prefix: 8 or 16 hex characters are enough
            if (hexPrefix.length >= 6) {
                val match = profiles.entries.firstOrNull { entry ->
                    entry.key.lowercase().startsWith(hexPrefix)
                }
                profileName(match?.value)?.let { return it }
            }
            if (!looksLikeRawId(lastMessageSender)) return lastMessageSender
            val tail = hexPrefix.takeLast(4)
            return if (tail.isNotEmpty()) "Nostr·$tail" else null
        }

        if (peerId.startsWith("p2p:")) {
            com.roman.zemzeme.p2p.P2PAliasRegistry.getDisplayName(peerId)
                ?.takeIf { !looksLikeRawId(it) }
                ?.let { return it }
            if (!looksLikeRawId(lastMessageSender)) return lastMessageSender
            val tail = peerId.removePrefix("p2p:").takeLast(4)
            return if (tail.isNotEmpty()) "P2P·$tail" else null
        }

        if (!looksLikeRawId(lastMessageSender)) return lastMessageSender
        return peerId.take(12)
    }

    /** Convenience overload that reads the global profile cache. */
    fun resolve(peerId: String?, knownNickname: String? = null, lastMessageSender: String? = null): String? =
        resolve(
            peerId = peerId,
            knownNickname = knownNickname,
            lastMessageSender = lastMessageSender,
            profiles = NostrProfileStore.profiles.value
        )
}
