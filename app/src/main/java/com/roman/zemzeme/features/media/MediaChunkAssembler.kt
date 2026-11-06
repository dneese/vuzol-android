package com.roman.zemzeme.features.media

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Receives chunked media, stores the parts on disk and assembles the final
 * file once every chunk has arrived. Parts survive process death, so a slow
 * transfer can continue after the app was restarted.
 */
class MediaChunkAssembler(private val context: Context) {

    companion object {
        private const val TAG = "MediaChunkAssembler"
        private const val PARTS_DIR = "media_parts"
        private const val MAX_PART_AGE_MS = 6L * 60L * 60L * 1000L
    }

    data class Assembled(
        val transferId: String,
        val path: String,
        val fileName: String,
        val mimeType: String,
        val fileSize: Long
    )

    private class Pending(
        val header: MediaChunkProtocol.Chunk,
        val received: MutableSet<Int> = HashSet(),
        var lastActivity: Long = System.currentTimeMillis()
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    /**
     * Stores one chunk. Returns the assembled file when the transfer is
     * complete, otherwise null.
     */
    fun accept(content: String): Assembled? {
        val chunk = MediaChunkProtocol.decodeChunk(content) ?: return null
        val state = pending.getOrPut(chunk.transferId) { Pending(chunk) }
        synchronized(state) {
            if (state.header.total != chunk.total) {
                Log.w(TAG, "Ignoring chunk with mismatched total for ${chunk.transferId}")
                return null
            }
            if (!state.received.contains(chunk.index)) {
                val partFile = partFile(chunk.transferId, chunk.index)
                try {
                    partFile.parentFile?.mkdirs()
                    partFile.writeBytes(chunk.data)
                    state.received.add(chunk.index)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to store chunk ${chunk.index}: ${e.message}")
                    return null
                }
            }
            state.lastActivity = System.currentTimeMillis()
            if (state.received.size < state.header.total) return null
            pending.remove(chunk.transferId)
            return assemble(state)
        }
    }

    /** Chunk indices the receiver is still waiting for. */
    fun missingIndices(transferId: String): IntArray {
        val state = pending[transferId] ?: return IntArray(0)
        synchronized(state) {
            val missing = ArrayList<Int>()
            for (i in 0 until state.header.total) {
                if (!state.received.contains(i)) missing.add(i)
            }
            return missing.toIntArray()
        }
    }

    fun isPending(transferId: String): Boolean = pending.containsKey(transferId)

    /** Deletes leftovers of transfers that were never completed. */
    fun cleanupOldParts() {
        try {
            val root = File(context.cacheDir, PARTS_DIR)
            if (!root.exists()) return
            val now = System.currentTimeMillis()
            root.listFiles()?.forEach { dir ->
                if (!dir.isDirectory) return@forEach
                val newest = dir.listFiles()?.maxOfOrNull { it.lastModified() } ?: dir.lastModified()
                if (now - newest > MAX_PART_AGE_MS) {
                    dir.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "cleanupOldParts failed: ${e.message}")
        }
    }

    private fun partFile(transferId: String, index: Int): File =
        File(File(context.cacheDir, PARTS_DIR), "$transferId/$index.part")

    private fun assemble(state: Pending): Assembled? {
        val header = state.header
        return try {
            val isImage = header.mimeType.lowercase().startsWith("image/")
            val subdir = if (isImage) "images/incoming" else "files/incoming"
            val targetDir = File(context.cacheDir, subdir).apply { mkdirs() }
            val safeName = sanitizeName(header.fileName, header.mimeType)
            val target = uniqueFile(targetDir, safeName)

            RandomAccessFile(target, "rw").use { out ->
                out.setLength(0)
                for (i in 0 until header.total) {
                    val part = partFile(header.transferId, i)
                    if (!part.exists()) {
                        Log.w(TAG, "Missing part $i for ${header.transferId}")
                        return null
                    }
                    part.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                        }
                    }
                }
            }

            File(context.cacheDir, PARTS_DIR).let { root ->
                File(root, header.transferId).deleteRecursively()
            }

            Log.d(TAG, "Assembled ${header.fileName} (${target.length()} bytes) from ${header.total} chunks")
            Assembled(
                transferId = header.transferId,
                path = target.absolutePath,
                fileName = header.fileName,
                mimeType = header.mimeType,
                fileSize = target.length()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Assembly failed for ${header.transferId}: ${e.message}")
            null
        }
    }

    private fun sanitizeName(fileName: String, mimeType: String): String {
        val base = fileName.ifBlank { "file" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val hasExtension = base.contains('.')
        return if (hasExtension) base else base + extensionFor(mimeType)
    }

    private fun extensionFor(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> ".jpg"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "video/mp4" -> ".mp4"
        "video/quicktime" -> ".mov"
        "audio/mp4", "audio/m4a" -> ".m4a"
        "audio/mpeg" -> ".mp3"
        "audio/ogg" -> ".ogg"
        "application/pdf" -> ".pdf"
        "text/plain" -> ".txt"
        else -> ".bin"
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        val suffix = if (ext.isBlank()) "" else ".$ext"
        var index = 1
        while (candidate.exists() && index < 1000) {
            candidate = File(dir, "$base ($index)$suffix")
            index++
        }
        return candidate
    }
}
