package com.roman.zemzeme.services

import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.model.DeliveryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide in-memory state store that survives Activity recreation.
 * The foreground Mesh service updates this store; UI subscribes/hydrates from it.
 */
object AppStateStore {
    // Timestamp of last account refresh — messages older than this are ignored
    @Volatile
    private var resetTimestamp: Long = 0L

    // Global de-dup set by message id to avoid duplicate keys in Compose lists
    private val seenMessageIds = mutableSetOf<String>()
    // Connected peer IDs (mesh ephemeral IDs)
    private val _peers = MutableStateFlow<List<String>>(emptyList())
    val peers: StateFlow<List<String>> = _peers.asStateFlow()

    // Public mesh timeline messages (non-channel)
    private val _publicMessages = MutableStateFlow<List<ZemzemeMessage>>(emptyList())
    val publicMessages: StateFlow<List<ZemzemeMessage>> = _publicMessages.asStateFlow()

    // Private messages by peerID
    private val _privateMessages = MutableStateFlow<Map<String, List<ZemzemeMessage>>>(emptyMap())
    val privateMessages: StateFlow<Map<String, List<ZemzemeMessage>>> = _privateMessages.asStateFlow()

    // Channel messages by channel name
    private val _channelMessages = MutableStateFlow<Map<String, List<ZemzemeMessage>>>(emptyMap())
    val channelMessages: StateFlow<Map<String, List<ZemzemeMessage>>> = _channelMessages.asStateFlow()

    fun setPeers(ids: List<String>) {
        _peers.value = ids
    }

    fun addPublicMessage(msg: ZemzemeMessage) {
        synchronized(this) {
            if (msg.timestamp.time < resetTimestamp) return
            if (seenMessageIds.contains(msg.id)) return
            seenMessageIds.add(msg.id)
            _publicMessages.value = insertChronologically(_publicMessages.value, msg)
            ChatHistoryStore.save(ChatHistoryStore.CONVERSATION_MESH, msg)
        }
    }

    fun addPrivateMessage(peerID: String, msg: ZemzemeMessage) {
        synchronized(this) {
            if (msg.timestamp.time < resetTimestamp) return
            if (seenMessageIds.contains(msg.id)) return
            seenMessageIds.add(msg.id)
            val map = _privateMessages.value.toMutableMap()
            map[peerID] = insertChronologically(map[peerID] ?: emptyList(), msg)
            _privateMessages.value = map
            ChatHistoryStore.save(ChatHistoryStore.conversationForPeer(peerID), msg)
        }
    }

    private fun statusPriority(status: DeliveryStatus?): Int = when (status) {
        null -> 0
        is DeliveryStatus.Sending -> 1
        is DeliveryStatus.Sent -> 2
        is DeliveryStatus.PartiallyDelivered -> 3
        is DeliveryStatus.Delivered -> 4
        is DeliveryStatus.Read -> 5
        is DeliveryStatus.Failed -> 0
    }

    fun updatePrivateMessageStatus(messageID: String, status: DeliveryStatus) {
        synchronized(this) {
            val map = _privateMessages.value.toMutableMap()
            var changed = false
            map.keys.toList().forEach { peer ->
                val list = map[peer]?.toMutableList() ?: mutableListOf()
                val idx = list.indexOfFirst { it.id == messageID }
                if (idx >= 0) {
                    val current = list[idx].deliveryStatus
                    // Do not downgrade (e.g., Read -> Delivered)
                    if (statusPriority(status) >= statusPriority(current)) {
                        list[idx] = list[idx].copy(deliveryStatus = status)
                        map[peer] = list
                        changed = true
                    }
                }
            }
            if (changed) {
                _privateMessages.value = map
            }
        }
    }

    fun addChannelMessage(channel: String, msg: ZemzemeMessage) {
        synchronized(this) {
            if (msg.timestamp.time < resetTimestamp) return
            if (seenMessageIds.contains(msg.id)) return
            seenMessageIds.add(msg.id)
            val map = _channelMessages.value.toMutableMap()
            map[channel] = insertChronologically(map[channel] ?: emptyList(), msg)
            _channelMessages.value = map
            ChatHistoryStore.save(ChatHistoryStore.conversationForChannel(channel), msg)
        }
    }

    /**
     * Restores persisted history into the in-memory store. Called once at startup.
     * Existing (live) messages win, so late relay traffic is never overwritten.
     */
    fun hydrate(history: Map<String, List<ZemzemeMessage>>) {
        if (history.isEmpty()) return
        synchronized(this) {
            val channels = _channelMessages.value.toMutableMap()
            val privateChats = _privateMessages.value.toMutableMap()
            val public = _publicMessages.value.toMutableList()
            history.forEach { (conv, messages) ->
                when {
                    conv == ChatHistoryStore.CONVERSATION_MESH -> {
                        messages.forEach { msg ->
                            if (seenMessageIds.add(msg.id)) public.add(msg)
                        }
                        public.sortBy { it.timestamp.time }
                    }
                    conv.startsWith("ch:") -> {
                        val key = conv.removePrefix("ch:")
                        val list = (channels[key] ?: emptyList()).toMutableList()
                        messages.forEach { msg ->
                            if (seenMessageIds.add(msg.id)) list.add(msg)
                        }
                        list.sortBy { it.timestamp.time }
                        channels[key] = list
                    }
                    conv.startsWith("dm:") -> {
                        val key = conv.removePrefix("dm:")
                        val list = (privateChats[key] ?: emptyList()).toMutableList()
                        messages.forEach { msg ->
                            if (seenMessageIds.add(msg.id)) list.add(msg)
                        }
                        list.sortBy { it.timestamp.time }
                        privateChats[key] = list
                    }
                }
            }
            _publicMessages.value = public
            _channelMessages.value = channels
            _privateMessages.value = privateChats
        }
    }

    private fun insertChronologically(
        existing: List<ZemzemeMessage>,
        msg: ZemzemeMessage
    ): List<ZemzemeMessage> {
        val last = existing.lastOrNull()
        if (last == null || msg.timestamp.time >= last.timestamp.time) {
            return existing + msg
        }
        val out = existing.toMutableList()
        val index = out.indexOfFirst { it.timestamp.time > msg.timestamp.time }
        if (index < 0) out.add(msg) else out.add(index, msg)
        return out
    }

    // Clear all in-memory state and set reset timestamp to reject old messages from mesh
    fun clear() {
        synchronized(this) {
            resetTimestamp = System.currentTimeMillis()
            seenMessageIds.clear()
            _peers.value = emptyList()
            _publicMessages.value = emptyList()
            _privateMessages.value = emptyMap()
            _channelMessages.value = emptyMap()
            ChatHistoryStore.clearAll()
        }
    }
}
