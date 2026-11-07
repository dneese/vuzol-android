package com.roman.zemzeme.wifi

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

/**
 * A message of an offline room. Text messages carry the text, file messages
 * carry the path of the received file.
 */
data class RoomMessage(
    val id: String,
    val from: String,
    val nickname: String,
    val text: String,
    val timestamp: Long,
    val filePath: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0L
)

/**
 * The wire format of the offline rooms.
 *
 * Every frame is length prefixed and encrypted. The layout is the same for
 * the WiFi Direct room and for the room inside the local WiFi network:
 *
 * [4 bytes frame size][4 bytes type][16 bytes room id][4 bytes cipher size][cipher]
 */
internal object RoomProtocol {

    const val FRAME_HANDSHAKE = 1
    const val FRAME_TEXT = 2
    const val FRAME_FILE_META = 3
    const val FRAME_FILE_CHUNK = 4
    const val FRAME_CALL = 5

    const val MAX_FRAME = 1024 * 1024
    const val FILE_CHUNK_SIZE = 256 * 1024

    data class Frame(val type: Int, val roomId: ByteArray, val ciphertext: ByteArray)

    data class FileMeta(
        val transferId: String,
        val fileName: String,
        val mime: String,
        val size: Long,
        val from: String,
        val nickname: String,
        val timestamp: Long
    )

    data class FileChunk(val transferId: String, val index: Int, val data: ByteArray)

    fun writeFrame(socket: Socket, type: Int, roomId: ByteArray, ciphertext: ByteArray): Boolean = try {
        // One frame at a time per socket, otherwise two threads can mix the bytes
        synchronized(socket) {
            val payload = ByteArrayOutputStream()
            val data = DataOutputStream(payload)
            data.writeInt(type)
            data.write(roomId)
            data.writeInt(ciphertext.size)
            data.write(ciphertext)
            data.flush()
            val frame = payload.toByteArray()
            val output = DataOutputStream(socket.getOutputStream())
            output.writeInt(frame.size)
            output.write(frame)
            output.flush()
        }
        true
    } catch (_: Exception) {
        false
    }

    fun readFrame(input: DataInputStream): Frame? {
        return try {
            val length = input.readInt()
            if (length <= 0 || length > MAX_FRAME) {
                null
            } else {
                val buffer = ByteArray(length)
                input.readFully(buffer)
                val data = DataInputStream(ByteArrayInputStream(buffer))
                val type = data.readInt()
                val roomId = ByteArray(16)
                data.readFully(roomId)
                val size = data.readInt()
                if (size <= 0 || size > MAX_FRAME) {
                    null
                } else {
                    val ciphertext = ByteArray(size)
                    data.readFully(ciphertext)
                    Frame(type, roomId, ciphertext)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun encodeHello(peerId: String, nickname: String): ByteArray =
        JSONObject()
            .put("id", peerId)
            .put("name", nickname)
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun decodeHello(payload: ByteArray): Pair<String, String>? = try {
        val json = JSONObject(String(payload, Charsets.UTF_8))
        val id = json.optString("id")
        if (id.isEmpty()) null else id to json.optString("name", "anon")
    } catch (_: Exception) {
        null
    }

    fun encodeText(
        id: String,
        from: String,
        nickname: String,
        text: String,
        timestamp: Long
    ): ByteArray =
        JSONObject()
            .put("id", id)
            .put("from", from)
            .put("name", nickname)
            .put("text", text)
            .put("ts", timestamp)
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun decodeText(payload: ByteArray): RoomMessage? = try {
        val json = JSONObject(String(payload, Charsets.UTF_8))
        RoomMessage(
            id = json.optString("id"),
            from = json.optString("from"),
            nickname = json.optString("name", "anon"),
            text = json.optString("text"),
            timestamp = json.optLong("ts", System.currentTimeMillis())
        )
    } catch (_: Exception) {
        null
    }

    fun encodeFileMeta(meta: FileMeta): ByteArray =
        JSONObject()
            .put("fid", meta.transferId)
            .put("fname", meta.fileName)
            .put("mime", meta.mime)
            .put("size", meta.size)
            .put("from", meta.from)
            .put("nick", meta.nickname)
            .put("ts", meta.timestamp)
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun decodeFileMeta(payload: ByteArray): FileMeta? = try {
        val json = JSONObject(String(payload, Charsets.UTF_8))
        FileMeta(
            transferId = json.optString("fid"),
            fileName = json.optString("fname", "file"),
            mime = json.optString("mime", "application/octet-stream"),
            size = json.optLong("size"),
            from = json.optString("from"),
            nickname = json.optString("nick", "anon"),
            timestamp = json.optLong("ts", System.currentTimeMillis())
        )
    } catch (_: Exception) {
        null
    }

    fun encodeFileChunk(transferId: String, index: Int, data: ByteArray): ByteArray {
        val payload = ByteArrayOutputStream()
        val stream = DataOutputStream(payload)
        val id = transferId.toByteArray(Charsets.UTF_8)
        stream.writeInt(id.size)
        stream.write(id)
        stream.writeInt(index)
        stream.write(data)
        stream.flush()
        return payload.toByteArray()
    }

    fun decodeFileChunk(payload: ByteArray): FileChunk? = try {
        val stream = DataInputStream(ByteArrayInputStream(payload))
        val idLength = stream.readInt()
        if (idLength <= 0 || idLength > 64) {
            null
        } else {
            val id = ByteArray(idLength)
            stream.readFully(id)
            val index = stream.readInt()
            val data = ByteArray(stream.available())
            stream.readFully(data)
            FileChunk(String(id, Charsets.UTF_8), index, data)
        }
    } catch (_: Exception) {
        null
    }
}
