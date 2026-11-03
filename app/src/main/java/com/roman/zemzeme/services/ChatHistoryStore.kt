package com.roman.zemzeme.services

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.roman.zemzeme.model.DeliveryStatus
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.model.ZemzemeMessageType
import java.util.Date
import java.util.concurrent.Executors

/**
 * On-device chat history backed by plain SQLite (no extra dependencies).
 *
 * Conversations are keyed by a string:
 *  - "mesh"            public mesh timeline
 *  - "ch:<channel>"    channel / geohash conversation
 *  - "dm:<peerID>"     private chat
 *
 * All writes happen on a single background thread, so callers never block the UI.
 */
object ChatHistoryStore {
    private const val TAG = "ChatHistoryStore"
    private const val DB_NAME = "vuzol_history.db"
    private const val DB_VERSION = 3
    private const val MAX_MESSAGES_PER_CONVERSATION = 500
    private const val TABLE_MESSAGES = "messages"
    private const val TABLE_REACTIONS = "reactions"

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "vuzol-history").apply { isDaemon = true }
    }

    @Volatile private var helper: Helper? = null

    fun init(context: Context) {
        if (helper != null) return
        synchronized(this) {
            if (helper == null) {
                helper = Helper(context.applicationContext)
            }
        }
    }

    fun conversationForChannel(channel: String): String = "ch:$channel"

    fun conversationForPeer(peerID: String): String = "dm:$peerID"

    const val CONVERSATION_MESH = "mesh"
    const val CONVERSATION_SAVED = "saved"

    fun save(conv: String, message: ZemzemeMessage) {
        val h = helper ?: return
        executor.execute {
            try {
                val db = h.writableDatabase
                db.insertWithOnConflict(
                    TABLE_MESSAGES, null, toValues(conv, message),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                prune(db, conv)
            } catch (e: Exception) {
                Log.w(TAG, "save failed: ${e.message}")
            }
        }
    }

    fun saveAll(conv: String, messages: List<ZemzemeMessage>) {
        val h = helper ?: return
        if (messages.isEmpty()) return
        executor.execute {
            try {
                val db = h.writableDatabase
                db.beginTransaction()
                try {
                    for (message in messages) {
                        db.insertWithOnConflict(
                            TABLE_MESSAGES, null, toValues(conv, message),
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                prune(db, conv)
            } catch (e: Exception) {
                Log.w(TAG, "saveAll failed: ${e.message}")
            }
        }
    }

    fun delete(messageId: String) {
        val h = helper ?: return
        executor.execute {
            try {
                val db = h.writableDatabase
                db.delete(TABLE_MESSAGES, "id = ?", arrayOf(messageId))
                db.delete(TABLE_REACTIONS, "msg_id = ?", arrayOf(messageId))
            } catch (e: Exception) {
                Log.w(TAG, "delete failed: ${e.message}")
            }
        }
    }

    fun deleteConversation(conv: String) {
        val h = helper ?: return
        executor.execute {
            try {
                val db = h.writableDatabase
                db.delete(TABLE_MESSAGES, "conv = ?", arrayOf(conv))
            } catch (e: Exception) {
                Log.w(TAG, "deleteConversation failed: ${e.message}")
            }
        }
    }

    fun clearAll() {
        val h = helper ?: return
        executor.execute {
            try {
                val db = h.writableDatabase
                db.delete(TABLE_MESSAGES, null, null)
                db.delete(TABLE_REACTIONS, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "clearAll failed: ${e.message}")
            }
        }
    }

    /** Loads the most recent messages of every conversation, oldest first. */
    fun loadAll(): Map<String, List<ZemzemeMessage>> {
        val h = helper ?: return emptyMap()
        return try {
            val db = h.readableDatabase
            val result = LinkedHashMap<String, MutableList<ZemzemeMessage>>()
            db.rawQuery(
                "SELECT id, conv, sender, sender_peer, content, type, ts, is_private, delivery, pow, sender_pubkey, edited " +
                    "FROM messages ORDER BY ts ASC",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val msg = fromCursor(cursor) ?: continue
                    result.getOrPut(cursor.getString(1)) { mutableListOf() }.add(msg)
                }
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "loadAll failed: ${e.message}")
            emptyMap()
        }
    }

    fun load(conv: String, limit: Int = MAX_MESSAGES_PER_CONVERSATION): List<ZemzemeMessage> {
        val h = helper ?: return emptyList()
        return try {
            val db = h.readableDatabase
            val out = mutableListOf<ZemzemeMessage>()
            db.rawQuery(
                "SELECT id, conv, sender, sender_peer, content, type, ts, is_private, delivery, pow, sender_pubkey, edited " +
                    "FROM messages WHERE conv = ? ORDER BY ts ASC LIMIT ?",
                arrayOf(conv, limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    fromCursor(cursor)?.let { out.add(it) }
                }
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "load failed: ${e.message}")
            emptyList()
        }
    }

    /** Stores a copy of a message in the "saved messages" conversation. */
    fun saveBookmark(message: ZemzemeMessage) {
        save(CONVERSATION_SAVED, message.copy(id = "saved:${message.id}"))
    }

    fun removeBookmark(messageId: String) {
        delete("saved:$messageId")
    }

    /** Replaces the text of a stored message and marks it as edited. */
    fun updateContent(messageId: String, newContent: String) {
        val h = helper ?: return
        executor.execute {
            try {
                val db = h.writableDatabase
                db.update(
                    TABLE_MESSAGES,
                    ContentValues().apply {
                        put("content", newContent)
                        put("edited", 1)
                    },
                    "id = ?",
                    arrayOf(messageId)
                )
            } catch (e: Exception) {
                Log.w(TAG, "updateContent failed: ${e.message}")
            }
        }
    }

    // --- Reactions -----------------------------------------------------------

    fun saveReaction(messageId: String, emoji: String, sender: String) {
        val h = helper ?: return
        executor.execute {
            try {
                val values = ContentValues().apply {
                    put("msg_id", messageId)
                    put("emoji", emoji)
                    put("sender", sender)
                    put("ts", System.currentTimeMillis())
                }
                h.writableDatabase.insertWithOnConflict(
                    TABLE_REACTIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE
                )
            } catch (e: Exception) {
                Log.w(TAG, "saveReaction failed: ${e.message}")
            }
        }
    }

    fun removeReaction(messageId: String, emoji: String, sender: String) {
        val h = helper ?: return
        executor.execute {
            try {
                h.writableDatabase.delete(
                    TABLE_REACTIONS, "msg_id = ? AND emoji = ? AND sender = ?",
                    arrayOf(messageId, emoji, sender)
                )
            } catch (e: Exception) {
                Log.w(TAG, "removeReaction failed: ${e.message}")
            }
        }
    }

    /** messageId -> emoji -> senders */
    fun loadReactions(): Map<String, Map<String, Set<String>>> {
        val h = helper ?: return emptyMap()
        return try {
            val out = LinkedHashMap<String, MutableMap<String, MutableSet<String>>>()
            h.readableDatabase.rawQuery(
                "SELECT msg_id, emoji, sender FROM reactions", null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    out.getOrPut(cursor.getString(0)) { mutableMapOf() }
                        .getOrPut(cursor.getString(1)) { mutableSetOf() }
                        .add(cursor.getString(2))
                }
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "loadReactions failed: ${e.message}")
            emptyMap()
        }
    }

    // --- Internals -----------------------------------------------------------

    private fun toValues(conv: String, message: ZemzemeMessage): ContentValues =
        ContentValues().apply {
            put("id", message.id)
            put("conv", conv)
            put("sender", message.sender)
            put("sender_peer", message.senderPeerID)
            put("content", message.content)
            put("type", message.type.name)
            put("ts", message.timestamp.time)
            put("is_private", if (message.isPrivate) 1 else 0)
            put("delivery", encodeDelivery(message.deliveryStatus))
            put("pow", message.powDifficulty)
            put("sender_pubkey", message.senderPubkey)
            put("edited", if (message.isEdited) 1 else 0)
        }

    private fun fromCursor(cursor: android.database.Cursor): ZemzemeMessage? {
        return try {
            val type = runCatching {
                ZemzemeMessageType.valueOf(cursor.getString(5))
            }.getOrDefault(ZemzemeMessageType.Message)
            ZemzemeMessage(
                id = cursor.getString(0),
                sender = cursor.getString(2),
                content = cursor.getString(3),
                type = type,
                timestamp = Date(cursor.getLong(6)),
                isPrivate = cursor.getInt(7) != 0,
                senderPeerID = cursor.getString(4),
                deliveryStatus = decodeDelivery(cursor.getString(8)),
                powDifficulty = if (cursor.isNull(9)) null else cursor.getInt(9),
                senderPubkey = if (cursor.isNull(10)) null else cursor.getString(10),
                isEdited = cursor.getInt(11) != 0
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun encodeDelivery(status: DeliveryStatus?): String? = when (status) {
        null -> null
        is DeliveryStatus.Sending -> "sending"
        is DeliveryStatus.Sent -> "sent"
        is DeliveryStatus.Delivered -> "delivered"
        is DeliveryStatus.Read -> "read"
        is DeliveryStatus.Failed -> "failed"
        is DeliveryStatus.PartiallyDelivered ->
            "partial:${status.reached}:${status.total}"
    }

    private fun decodeDelivery(raw: String?): DeliveryStatus? = when {
        raw == null -> null
        raw == "sending" -> DeliveryStatus.Sent
        raw == "sent" -> DeliveryStatus.Sent
        // Delivered/Read carry peer data we do not persist; show a plain "sent" tick.
        raw == "delivered" -> DeliveryStatus.Sent
        raw == "read" -> DeliveryStatus.Sent
        raw == "failed" -> DeliveryStatus.Failed("failed")
        raw.startsWith("partial:") -> {
            val parts = raw.removePrefix("partial:").split(":")
            val reached = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val total = parts.getOrNull(1)?.toIntOrNull() ?: 0
            DeliveryStatus.PartiallyDelivered(reached, total)
        }
        else -> null
    }

    private fun prune(db: SQLiteDatabase, conv: String) {
        db.execSQL(
            "DELETE FROM messages WHERE conv = ? AND id NOT IN (" +
                "SELECT id FROM messages WHERE conv = ? ORDER BY ts DESC LIMIT " +
                "$MAX_MESSAGES_PER_CONVERSATION)",
            arrayOf(conv, conv)
        )
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE messages (" +
                    "id TEXT PRIMARY KEY, conv TEXT NOT NULL, sender TEXT NOT NULL, " +
                    "sender_peer TEXT, content TEXT NOT NULL, type TEXT NOT NULL, ts INTEGER NOT NULL, " +
                    "is_private INTEGER NOT NULL DEFAULT 0, delivery TEXT, pow INTEGER, " +
                    "sender_pubkey TEXT, edited INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("CREATE INDEX idx_messages_conv_ts ON messages(conv, ts)")
            db.execSQL(
                "CREATE TABLE reactions (" +
                    "msg_id TEXT NOT NULL, emoji TEXT NOT NULL, sender TEXT NOT NULL, " +
                    "ts INTEGER NOT NULL, PRIMARY KEY(msg_id, emoji, sender))"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                try { db.execSQL("ALTER TABLE messages ADD COLUMN sender_pubkey TEXT") } catch (_: Exception) { }
            }
            if (oldVersion < 3) {
                try { db.execSQL("ALTER TABLE messages ADD COLUMN edited INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) { }
            }
        }
    }
}
