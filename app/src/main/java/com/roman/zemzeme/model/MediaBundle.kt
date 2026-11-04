package com.roman.zemzeme.model

import java.io.ByteArrayOutputStream

/**
 * A bundle of several media files sent as one message.
 *
 * Layout (big endian):
 * ```
 * uint16  count
 * repeat:
 *   uint16 nameLength   + name (UTF-8)
 *   uint16 mimeLength   + mime (UTF-8)
 *   uint32 contentSize  + content bytes
 * ```
 */
data class MediaBundleItem(
    val name: String,
    val mimeType: String,
    val content: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is MediaBundleItem && name == other.name &&
            mimeType == other.mimeType && content.contentEquals(other.content))

    override fun hashCode(): Int =
        (name.hashCode() * 31 + mimeType.hashCode()) * 31 + content.contentHashCode()
}

object MediaBundle {
    /** MIME type that marks a message as a media bundle. */
    const val MIME = "application/x-vuzol-bundle"

    fun encode(items: List<MediaBundleItem>): ByteArray {
        val out = ByteArrayOutputStream()
        val count = items.size.coerceAtMost(0xFFFF)
        out.write((count shr 8) and 0xFF)
        out.write(count and 0xFF)
        for (item in items) {
            val nameBytes = item.name.toByteArray(Charsets.UTF_8)
            out.write((nameBytes.size shr 8) and 0xFF)
            out.write(nameBytes.size and 0xFF)
            out.write(nameBytes)

            val mimeBytes = item.mimeType.toByteArray(Charsets.UTF_8)
            out.write((mimeBytes.size shr 8) and 0xFF)
            out.write(mimeBytes.size and 0xFF)
            out.write(mimeBytes)

            out.write((item.content.size shr 24) and 0xFF)
            out.write((item.content.size shr 16) and 0xFF)
            out.write((item.content.size shr 8) and 0xFF)
            out.write(item.content.size and 0xFF)
            out.write(item.content)
        }
        return out.toByteArray()
    }

    fun decode(data: ByteArray): List<MediaBundleItem>? {
        return try {
            var pos = 0
            fun readU8(): Int {
                val b = data[pos].toInt() and 0xFF
                pos++
                return b
            }
            fun readU16(): Int {
                val v = (readU8() shl 8) or readU8()
                return v
            }
            fun readU32(): Long {
                var v = 0L
                repeat(4) { v = (v shl 8) or readU8().toLong() }
                return v
            }

            val count = readU16()
            if (count <= 0) return emptyList()
            val items = ArrayList<MediaBundleItem>(count)
            repeat(count) {
                val nameLen = readU16()
                val name = String(data, pos, nameLen, Charsets.UTF_8)
                pos += nameLen
                val mimeLen = readU16()
                val mime = String(data, pos, mimeLen, Charsets.UTF_8)
                pos += mimeLen
                val size = readU32().toInt()
                if (size < 0 || pos + size > data.size) return null
                val content = data.copyOfRange(pos, pos + size)
                pos += size
                items.add(MediaBundleItem(name, mime, content))
            }
            items
        } catch (_: Exception) {
            null
        }
    }
}
