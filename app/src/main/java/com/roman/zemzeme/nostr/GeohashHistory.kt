package com.roman.zemzeme.nostr

import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.model.ZemzemeMessageType
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps a short rolling history of every geohash channel.
 *
 * Channel messages travel as ephemeral events, so relays do not keep them and
 * a newcomer used to see an empty chat. Every participant therefore publishes
 * a replaceable snapshot of the last day and newcomers merge those snapshots
 * into their channel.
 */
class GeohashHistory(
    private val maxEntries: Int = 120,
    private val windowMs: Long = 24L * 60L * 60L * 1000L
) {

    data class Entry(
        val id: String,
        val sender: String,
        val content: String,
        val timestamp: Long,
        val isMedia: Boolean
    )

    private val buffers = ConcurrentHashMap<String, MutableList<Entry>>()
    private val lock = Any()

    /** Records a message so it becomes part of the next published snapshot. */
    fun record(geohash: String, message: ZemzemeMessage) {
        val text = when (message.type) {
            ZemzemeMessageType.Message -> message.content
            ZemzemeMessageType.Image -> "[photo]"
            ZemzemeMessageType.Audio -> "[voice]"
            ZemzemeMessageType.Video -> "[video]"
            ZemzemeMessageType.File -> "[file]"
            ZemzemeMessageType.Media -> "[media]"
        }
        if (text.isBlank()) return
        val entry = Entry(
            id = message.id,
            sender = message.sender,
            content = text,
            timestamp = message.timestamp.time,
            isMedia = message.type != ZemzemeMessageType.Message
        )
        synchronized(lock) {
            val list = buffers.getOrPut(geohash.lowercase()) { mutableListOf() }
            if (list.none { it.id == entry.id }) list.add(entry)
            val cutoff = System.currentTimeMillis() - windowMs
            list.removeAll { it.timestamp < cutoff }
            while (list.size > maxEntries) list.removeAt(0)
        }
    }

    /** Serialises the recent history of a channel. */
    fun snapshot(geohash: String): String {
        val list = synchronized(lock) {
            buffers[geohash.lowercase()]?.toList() ?: emptyList()
        }
        if (list.isEmpty()) return ""
        val array = JSONArray()
        list.forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("s", entry.sender)
            obj.put("c", entry.content)
            obj.put("t", entry.timestamp)
            obj.put("m", entry.isMedia)
            array.put(obj)
        }
        return array.toString()
    }

    /** Parses a snapshot published by another participant. */
    fun parse(json: String): List<Entry> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val result = ArrayList<Entry>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val content = obj.optString("c")
                if (id.isBlank() || content.isBlank()) continue
                result.add(
                    Entry(
                        id = id,
                        sender = obj.optString("s", "anon"),
                        content = content,
                        timestamp = obj.optLong("t", System.currentTimeMillis()),
                        isMedia = obj.optBoolean("m", false)
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Records the entries of a received snapshot so it can be republished. */
    fun merge(geohash: String, entries: List<Entry>) {
        if (entries.isEmpty()) return
        synchronized(lock) {
            val list = buffers.getOrPut(geohash.lowercase()) { mutableListOf() }
            entries.forEach { entry ->
                if (list.none { it.id == entry.id }) list.add(entry)
            }
            val cutoff = System.currentTimeMillis() - windowMs
            list.removeAll { it.timestamp < cutoff }
            while (list.size > maxEntries) list.removeAt(0)
        }
    }
}
