package com.roman.zemzeme.nostr

import android.app.Application
import android.util.Log
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.ui.ChatState
import com.roman.zemzeme.ui.MessageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * GeohashMessageHandler
 * - Processes kind=20000 Nostr events for geohash channels
 * - Updates repository for participants + nicknames
 * - Emits messages to MessageManager
 */
class GeohashMessageHandler(
    private val application: Application,
    private val state: ChatState,
    private val messageManager: MessageManager,
    private val repo: GeohashRepository,
    private val scope: CoroutineScope,
    private val dataManager: com.roman.zemzeme.ui.DataManager,
    private val notificationManager: com.roman.zemzeme.ui.NotificationManager? = null,
    private val mediaTransfer: com.roman.zemzeme.features.media.GeohashMediaTransfer? = null,
    private val history: GeohashHistory? = null
) {
    companion object {
        private const val TAG = "GeohashMessageHandler"

        private fun decodeBase64Url(input: String): ByteArray? = try {
            val padded = input.replace("-", "+").replace("_", "/")
                .let { it + "=".repeat((4 - it.length % 4) % 4) }
            android.util.Base64.decode(padded, android.util.Base64.DEFAULT)
        } catch (_: Exception) { null }
    }

    // Simple event deduplication
    private val processedIds = ArrayDeque<String>()
    private val seen = HashSet<String>()
    private val max = 2000

    @Synchronized
    private fun dedupe(id: String): Boolean {
        if (seen.contains(id)) return true
        seen.add(id)
        processedIds.addLast(id)
        if (processedIds.size > max) {
            val old = processedIds.removeFirst()
            seen.remove(old)
        }
        return false
    }

    /**
     * Notify about a new message in a geohash channel. The same message can
     * arrive over several transports, so the message id keeps it to one sound.
     */
    private fun notifyGeohashMessage(
        geohash: String,
        senderNickname: String,
        content: String,
        messageId: String
    ) {
        val manager = notificationManager ?: return
        try {
            val preview = com.roman.zemzeme.ui.NotificationTextUtils.buildPrivateMessagePreview(
                com.roman.zemzeme.model.ZemzemeMessage(
                    id = messageId,
                    sender = senderNickname,
                    content = content,
                    timestamp = Date(),
                    isRelay = false
                )
            )
            val myNickname = state.getNicknameValue()
            val isMention = !myNickname.isNullOrBlank() &&
                content.contains("@$myNickname", ignoreCase = true)
            manager.showGeohashNotification(
                geohash = geohash,
                senderNickname = senderNickname,
                messageContent = preview,
                isMention = isMention,
                messageId = messageId
            )
        } catch (_: Throwable) {}
    }

    fun onEvent(event: NostrEvent, subscribedGeohash: String) {
        scope.launch(Dispatchers.Default) {
            try {
                if (event.kind != NostrKind.EPHEMERAL_EVENT &&
                    event.kind != NostrKind.GEOHASH_PRESENCE &&
                    event.kind != NostrKind.GEOHASH_REACTION &&
                    event.kind != NostrKind.GEOHASH_TYPING &&
                    event.kind != NostrKind.GEOHASH_EDIT &&
                    event.kind != NostrKind.GEOHASH_READ &&
                    event.kind != NostrKind.GEOHASH_HISTORY
                ) return@launch
                val tagGeo = event.tags.firstOrNull { it.size >= 2 && it[0] == "g" }?.getOrNull(1)
                if (tagGeo == null || !tagGeo.equals(subscribedGeohash, true)) return@launch
                if (dedupe(event.id)) return@launch

                // Groups with a password carry encrypted payloads
                var content = event.content
                if (com.roman.zemzeme.groups.GroupSpaceCrypto.isEncryptedPayload(content)) {
                    val decrypted = com.roman.zemzeme.groups.GroupSpaceCrypto
                        .decryptIfNeeded(application, subscribedGeohash, content)
                    if (decrypted == null) {
                        // The group is locked for us, show a hint instead of the payload
                        if (!event.content.startsWith("enc1:")) return@launch
                        val lockedMsg = ZemzemeMessage(
                            id = event.id,
                            sender = repo.displayNameForNostrPubkeyUI(event.pubkey),
                            content = "🔒",
                            timestamp = Date(event.createdAt * 1000L),
                            isRelay = false,
                            originalSender = repo.displayNameForNostrPubkey(event.pubkey),
                            senderPeerID = "nostr:${event.pubkey.take(8)}",
                            senderPubkey = event.pubkey,
                            channel = "#$subscribedGeohash"
                        )
                        withContext(Dispatchers.Main) {
                            messageManager.addChannelMessage("geo:$subscribedGeohash", lockedMsg)
                        }
                        return@launch
                    }
                    content = decrypted
                }

                // Chunked media and acknowledgements are handled before the
                // proof of work check, they are machine payloads and mining
                // every chunk would make transfers unusably slow.
                if (content.startsWith(com.roman.zemzeme.features.media.MediaChunkProtocol.CHUNK_PREFIX) ||
                    content.startsWith(com.roman.zemzeme.features.media.MediaChunkProtocol.ACK_PREFIX)
                ) {
                    val senderName = repo.displayNameForNostrPubkeyUI(event.pubkey)
                    mediaTransfer?.acceptIncoming(
                        content = content,
                        geohash = subscribedGeohash,
                        senderName = senderName,
                        senderId = event.pubkey,
                        nickname = event.tags.firstOrNull { it.size >= 2 && it[0] == "n" }?.getOrNull(1)
                    )
                    return@launch
                }

                // PoW validation (if enabled) - apply to chat messages primarily
                if (event.kind == NostrKind.EPHEMERAL_EVENT) {
                    val pow = PoWPreferenceManager.getCurrentSettings()
                    if (pow.enabled && pow.difficulty > 0) {
                        if (!NostrProofOfWork.validateDifficulty(event, pow.difficulty)) return@launch
                    }
                }

                // Blocked users check (use injected DataManager which has loaded state)
                if (dataManager.isGeohashUserBlocked(event.pubkey)) return@launch

                // Ignore our own geohash events entirely so local identity never appears in people lists.
                val isOwnEvent = try {
                    NostrIdentityBridge.deriveIdentity(subscribedGeohash, application)
                        .publicKeyHex
                        .equals(event.pubkey, ignoreCase = true)
                } catch (_: Exception) {
                    false
                }
                if (isOwnEvent) return@launch
                
                // Update repository (participants, nickname, teleport)
                // Update repository on a background-safe path; repository will post updates to LiveData
                
                // Update participant count (last seen) on BOTH Presence (20001) and Chat (20000) events
                if (event.kind == NostrKind.GEOHASH_PRESENCE || event.kind == NostrKind.EPHEMERAL_EVENT) {
                    repo.updateParticipant(subscribedGeohash, event.pubkey, Date(event.createdAt * 1000L))
                }
                
                event.tags.find { it.size >= 2 && it[0] == "n" }?.let { repo.cacheNickname(event.pubkey, it[1]) }
                // Ask relays for the profile so we can show a real avatar
                try { NostrProfileService.get(application).request(event.pubkey) } catch (_: Exception) { }
                event.tags.find { it.size >= 2 && it[0] == "t" && it[1] == "teleport" }?.let { repo.markTeleported(event.pubkey) }
                // Register a geohash DM alias for this participant so MessageRouter can route DMs via Nostr
                try {
                    com.roman.zemzeme.nostr.GeohashAliasRegistry.put("nostr_${event.pubkey.take(16)}", event.pubkey)
                } catch (_: Exception) { }

                // Stop here for presence events - they don't produce chat messages
                if (event.kind == NostrKind.GEOHASH_PRESENCE) return@launch

                // Channel history snapshot: lets newcomers see the last day
                if (event.kind == NostrKind.GEOHASH_HISTORY) {
                    val entries = history?.parse(content) ?: return@launch
                    if (entries.isEmpty()) return@launch
                    history.merge(subscribedGeohash, entries)
                    entries.forEach { entry ->
                        if (dedupe(entry.id)) return@forEach
                        val restored = ZemzemeMessage(
                            id = entry.id,
                            sender = entry.sender,
                            content = entry.content,
                            timestamp = Date(entry.timestamp),
                            isRelay = false,
                            originalSender = entry.sender,
                            senderPeerID = "nostr:${event.pubkey.take(8)}",
                            senderPubkey = event.pubkey,
                            channel = "#$subscribedGeohash"
                        )
                        withContext(Dispatchers.Main) {
                            messageManager.addChannelMessage("geo:$subscribedGeohash", restored)
                        }
                        com.roman.zemzeme.services.ChatHistoryStore.save(
                            com.roman.zemzeme.services.ChatHistoryStore.conversationForChannel("geo:$subscribedGeohash"),
                            restored
                        )
                    }
                    return@launch
                }

                val isTeleportPresence = event.tags.any { it.size >= 2 && it[0] == "t" && it[1] == "teleport" } &&
                                         event.content.trim().isEmpty()
                if (isTeleportPresence) return@launch

                // Reactions and typing hints are transparent to the message list
                if (event.kind == NostrKind.GEOHASH_REACTION) {
                    val targetId = event.tags
                        .firstOrNull { it.size >= 2 && it[0] == "e" }
                        ?.getOrNull(1)
                    val emoji = event.content.trim()
                    if (!targetId.isNullOrBlank() && emoji.isNotBlank()) {
                        com.roman.zemzeme.ui.ReactionsStore.apply(
                            targetId, emoji, repo.displayNameForNostrPubkeyUI(event.pubkey)
                        )
                    }
                    return@launch
                }
                if (event.kind == NostrKind.GEOHASH_EDIT) {
                    val targetId = event.tags
                        .firstOrNull { it.size >= 2 && it[0] == "e" }
                        ?.getOrNull(1)
                    val newText = event.content
                    if (!targetId.isNullOrBlank() && newText.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            messageManager.updateMessageContent("geo:$subscribedGeohash", targetId, newText)
                        }
                        com.roman.zemzeme.services.ChatHistoryStore.updateContent(targetId, newText)
                    }
                    return@launch
                }
                if (event.kind == NostrKind.GEOHASH_READ) {
                    val targetId = event.tags
                        .firstOrNull { it.size >= 2 && it[0] == "e" }
                        ?.getOrNull(1)
                    if (!targetId.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            messageManager.markChannelMessageRead(
                                "geo:$subscribedGeohash",
                                targetId,
                                repo.displayNameForNostrPubkeyUI(event.pubkey)
                            )
                        }
                    }
                    return@launch
                }
                if (event.kind == NostrKind.GEOHASH_TYPING) {
                    com.roman.zemzeme.ui.TypingStore.mark(
                        subscribedGeohash, repo.displayNameForNostrPubkeyUI(event.pubkey)
                    )
                    return@launch
                }

                val senderName = repo.displayNameForNostrPubkeyUI(event.pubkey)

                // Media transfer embedded as bitchat1:<base64url> in event content
                if (content.startsWith("bitchat1:")) {
                    val b64 = content.removePrefix("bitchat1:")
                    val fileBytes = decodeBase64Url(b64)
                    if (fileBytes != null) {
                        val filePacket = com.roman.zemzeme.model.ZemzemeFilePacket.decode(fileBytes)
                        if (filePacket != null) {
                            val savedPath = com.roman.zemzeme.features.file.FileUtils.saveIncomingFile(application, filePacket)
                            val msgType = com.roman.zemzeme.features.file.FileUtils.messageTypeForMime(filePacket.mimeType)
                            val mediaMsg = ZemzemeMessage(
                                id = event.id,
                                sender = senderName,
                                content = savedPath,
                                type = msgType,
                                timestamp = Date(event.createdAt * 1000L),
                                isRelay = false,
                                originalSender = repo.displayNameForNostrPubkey(event.pubkey),
                                senderPeerID = "nostr:${event.pubkey.take(8)}",
                                senderPubkey = event.pubkey,
                                channel = "#$subscribedGeohash"
                            )
                            withContext(Dispatchers.Main) {
                                messageManager.addChannelMessage("geo:$subscribedGeohash", mediaMsg)
                                notifyGeohashMessage(subscribedGeohash, senderName, mediaMsg.content, event.id)
                            }
                            history?.record(subscribedGeohash, mediaMsg)
                            return@launch
                        }
                    }
                    Log.w(TAG, "Failed to decode geohash media event ${event.id.take(8)}")
                    return@launch
                }

                val hasNonce = try { NostrProofOfWork.hasNonce(event) } catch (_: Exception) { false }
                val msg = ZemzemeMessage(
                    id = event.id,
                    sender = senderName,
                    content = content,
                    timestamp = Date(event.createdAt * 1000L),
                    isRelay = false,
                    originalSender = repo.displayNameForNostrPubkey(event.pubkey),
                    senderPeerID = "nostr:${event.pubkey.take(8)}",
                    senderPubkey = event.pubkey,
                    mentions = null,
                    channel = "#$subscribedGeohash",
                    powDifficulty = try {
                        if (hasNonce) NostrProofOfWork.calculateDifficulty(event.id).takeIf { it > 0 } else null
                    } catch (_: Exception) { null }
                )
                withContext(Dispatchers.Main) {
                    messageManager.addChannelMessage("geo:$subscribedGeohash", msg)
                    notifyGeohashMessage(subscribedGeohash, senderName, msg.content, event.id)
                }
                history?.record(subscribedGeohash, msg)
            } catch (e: Exception) {
                Log.e(TAG, "onEvent error: ${e.message}")
            }
        }
    }
}
