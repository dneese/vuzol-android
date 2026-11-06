package com.roman.zemzeme.features.media

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/**
 * Wire format for chunked media transfer inside geohash channels.
 *
 * A media file is split into fixed size chunks and every chunk travels as its
 * own event / topic message. Relays therefore never have to accept one huge
 * payload, which is what used to make videos silently disappear.
 *
 * chunk: "bitchat1c:" + base64url( binary )
 * binary: "VZC1" | transferId(16) | index(4) | total(4) | fileSize(8) |
 *         nameLen(2) | name | mimeLen(2) | mime | data
 *
 * ack:   "bitchat1a:" + base64url( transferId(16) | count(2) | index(4) * count )
 *        The indices are the chunks the receiver is still missing, so the
 *        sender can resend exactly what was lost.
 */
object MediaChunkProtocol {

    const val CHUNK_PREFIX = "bitchat1c:"
    const val ACK_PREFIX = "bitchat1a:"

    /** Raw bytes per chunk. Base64 grows this by 4/3 so an event stays small. */
    const val CHUNK_SIZE = 24 * 1024

    /** Maximum number of chunk indices carried by one acknowledgement. */
    const val MAX_ACK_INDICES = 96

    private val MAGIC = byteArrayOf(0x56, 0x5A, 0x43, 0x31) // "VZC1"
    private val random = SecureRandom()

    data class Chunk(
        val transferId: String,
        val index: Int,
        val total: Int,
        val fileSize: Long,
        val fileName: String,
        val mimeType: String,
        val data: ByteArray
    )

    data class Ack(val transferId: String, val missing: IntArray)

    fun newTransferId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytesToTransferId(bytes)
    }

    fun encodeChunk(
        transferId: String,
        index: Int,
        total: Int,
        fileSize: Long,
        fileName: String,
        mimeType: String,
        data: ByteArray
    ): String {
        val nameBytes = fileName.toByteArray(Charsets.UTF_8)
        val mimeBytes = mimeType.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(
            MAGIC.size + 16 + 4 + 4 + 8 + 2 + nameBytes.size + 2 + mimeBytes.size + data.size
        )
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.put(MAGIC)
        buffer.put(transferIdToBytes(transferId))
        buffer.putInt(index)
        buffer.putInt(total)
        buffer.putLong(fileSize)
        buffer.putShort(nameBytes.size.toShort())
        buffer.put(nameBytes)
        buffer.putShort(mimeBytes.size.toShort())
        buffer.put(mimeBytes)
        buffer.put(data)
        return CHUNK_PREFIX + base64UrlEncode(buffer.array())
    }

    fun decodeChunk(content: String): Chunk? {
        if (!content.startsWith(CHUNK_PREFIX)) return null
        val bytes = base64UrlDecode(content.substring(CHUNK_PREFIX.length)) ?: return null
        return try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(MAGIC.size)
            buffer.get(magic)
            if (!magic.contentEquals(MAGIC)) return null
            val transferBytes = ByteArray(16)
            buffer.get(transferBytes)
            val index = buffer.int
            val total = buffer.int
            val fileSize = buffer.long
            if (buffer.remaining() < 2) return null
            val nameLen = buffer.short.toInt() and 0xFFFF
            if (nameLen > buffer.remaining()) return null
            val nameBytes = ByteArray(nameLen)
            buffer.get(nameBytes)
            if (buffer.remaining() < 2) return null
            val mimeLen = buffer.short.toInt() and 0xFFFF
            if (mimeLen > buffer.remaining()) return null
            val mimeBytes = ByteArray(mimeLen)
            buffer.get(mimeBytes)
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            if (index < 0 || total <= 0 || index >= total) return null
            Chunk(
                transferId = bytesToTransferId(transferBytes),
                index = index,
                total = total,
                fileSize = fileSize,
                fileName = String(nameBytes, Charsets.UTF_8),
                mimeType = String(mimeBytes, Charsets.UTF_8),
                data = data
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Reads only the transfer id, so a missing chunk can be reported quickly. */
    fun peekTransferId(content: String): String? {
        if (!content.startsWith(CHUNK_PREFIX)) return null
        val bytes = base64UrlDecode(content.substring(CHUNK_PREFIX.length)) ?: return null
        return try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(MAGIC.size)
            buffer.get(magic)
            if (!magic.contentEquals(MAGIC)) return null
            val transferBytes = ByteArray(16)
            buffer.get(transferBytes)
            bytesToTransferId(transferBytes)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeAck(transferId: String, missing: IntArray): String {
        val capped = if (missing.size > MAX_ACK_INDICES) missing.copyOf(MAX_ACK_INDICES) else missing
        val buffer = ByteBuffer.allocate(16 + 2 + capped.size * 4)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.put(transferIdToBytes(transferId))
        buffer.putShort(capped.size.toShort())
        capped.forEach { buffer.putInt(it) }
        return ACK_PREFIX + base64UrlEncode(buffer.array())
    }

    fun decodeAck(content: String): Ack? {
        if (!content.startsWith(ACK_PREFIX)) return null
        val bytes = base64UrlDecode(content.substring(ACK_PREFIX.length)) ?: return null
        return try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            if (buffer.remaining() < 18) return null
            val transferBytes = ByteArray(16)
            buffer.get(transferBytes)
            val count = buffer.short.toInt() and 0xFFFF
            if (count > buffer.remaining() / 4) return null
            val missing = IntArray(count)
            for (i in 0 until count) missing[i] = buffer.int
            Ack(bytesToTransferId(transferBytes), missing)
        } catch (_: Exception) {
            null
        }
    }

    private fun transferIdToBytes(transferId: String): ByteArray {
        val clean = transferId.take(32)
        val out = ByteArray(16)
        for (i in out.indices) {
            val hi = Character.digit(clean.getOrNull(i * 2) ?: '0', 16)
            val lo = Character.digit(clean.getOrNull(i * 2 + 1) ?: '0', 16)
            out[i] = (((hi shl 4) or lo) and 0xFF).toByte()
        }
        return out
    }

    private fun bytesToTransferId(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun base64UrlEncode(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun base64UrlDecode(data: String): ByteArray? = try {
        Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    } catch (_: Exception) {
        null
    }
}
