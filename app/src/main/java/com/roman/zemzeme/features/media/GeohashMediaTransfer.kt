package com.roman.zemzeme.features.media

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends and receives media inside geohash channels using the chunked
 * [MediaChunkProtocol].
 *
 * Sending a file means publishing many small chunks instead of one huge
 * blob. The receiver answers with an acknowledgement that lists the chunks it
 * is still missing, so exactly those chunks are resent. That makes transfers
 * survive flaky relays and makes large videos actually arrive.
 */
class GeohashMediaTransfer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val publish: suspend (geohash: String, content: String, nickname: String?) -> Unit,
    private val onAssembled: (geohash: String, assembled: MediaChunkAssembler.Assembled, senderName: String, senderId: String?) -> Unit
) {

    companion object {
        private const val TAG = "GeohashMediaTransfer"
        private const val MAX_PARALLEL_CHUNKS = 6
        private const val RETRY_INTERVAL_MS = 4_000L
        private const val MAX_ATTEMPTS = 4
        private const val TRANSFER_TIMEOUT_MS = 120_000L
        private const val ACK_THROTTLE_MS = 8_000L
    }

    private class PendingTransfer(
        val transferId: String,
        val file: File,
        val mimeType: String,
        val total: Int,
        val geohash: String,
        val nickname: String?,
        val createdAt: Long,
        var attempts: Int = 0,
        var lastSentAt: Long = 0L,
        var missing: IntArray? = null,
        var acked: Boolean = false
    )

    private val assembler = MediaChunkAssembler(context)
    private val pending = ConcurrentHashMap<String, PendingTransfer>()
    private val lastAckAt = ConcurrentHashMap<String, Long>()
    private val selfTransferIds = ConcurrentHashMap.newKeySet<String>()
    private var retryJob: Job? = null

    /**
     * Sends a file to a geohash channel. Returns the transfer id, which is also
     * used as the message id on both sides.
     */
    suspend fun send(
        file: File,
        mimeType: String,
        geohash: String,
        nickname: String?,
        transferId: String = MediaChunkProtocol.newTransferId()
    ): String {
        val total = ((file.length() + MediaChunkProtocol.CHUNK_SIZE - 1) / MediaChunkProtocol.CHUNK_SIZE)
            .toInt()
            .coerceAtLeast(1)

        val transfer = PendingTransfer(
            transferId = transferId,
            file = file,
            mimeType = mimeType,
            total = total,
            geohash = geohash,
            nickname = nickname,
            createdAt = System.currentTimeMillis()
        )
        pending[transferId] = transfer
        // Our own chunks come back through the relay, they must not be
        // assembled a second time on our side
        selfTransferIds.add(transferId)
        ensureRetryLoop()

        sendChunks(transfer, indices = null)
        return transferId
    }

    /**
     * Handles an incoming chunk or acknowledgement.
     */
    fun acceptIncoming(
        content: String,
        geohash: String,
        senderName: String,
        senderId: String?,
        nickname: String?
    ) {
        if (content.startsWith(MediaChunkProtocol.ACK_PREFIX)) {
            handleAck(content)
            return
        }
        if (!content.startsWith(MediaChunkProtocol.CHUNK_PREFIX)) return

        val transferId = MediaChunkProtocol.peekTransferId(content) ?: return
        // Ignore the echo of our own transfer
        if (selfTransferIds.contains(transferId)) return
        val assembled = assembler.accept(content)
        if (assembled != null) {
            Log.d(TAG, "Transfer ${transferId.take(8)} complete: ${assembled.fileName}")
            publishAck(geohash, transferId, IntArray(0), nickname, force = true)
            onAssembled(geohash, assembled, senderName, senderId)
        } else {
            publishAck(geohash, transferId, assembler.missingIndices(transferId), nickname, force = false)
        }
    }

    /** Handles an acknowledgement from a receiver. */
    fun handleAck(content: String) {
        val ack = MediaChunkProtocol.decodeAck(content) ?: return
        val transfer = pending[ack.transferId] ?: return
        if (ack.missing.isEmpty()) {
            transfer.acked = true
            pending.remove(ack.transferId)
            Log.d(TAG, "Transfer ${ack.transferId.take(8)} acknowledged")
        } else {
            transfer.missing = ack.missing
        }
    }

    fun pendingTransferCount(): Int = pending.size

    private suspend fun sendChunks(transfer: PendingTransfer, indices: IntArray?) {
        val gate = Semaphore(MAX_PARALLEL_CHUNKS)
        val targets = indices ?: IntArray(transfer.total) { it }
        withContext(Dispatchers.IO) {
            targets.forEach { index ->
                gate.withPermit {
                    try {
                        val data = readChunk(transfer.file, index)
                        val content = MediaChunkProtocol.encodeChunk(
                            transferId = transfer.transferId,
                            index = index,
                            total = transfer.total,
                            fileSize = transfer.file.length(),
                            fileName = transfer.file.name,
                            mimeType = transfer.mimeType,
                            data = data
                        )
                        publish(transfer.geohash, content, transfer.nickname)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send chunk ${index}: ${e.message}")
                    }
                }
            }
        }
        transfer.lastSentAt = System.currentTimeMillis()
    }

    private fun readChunk(file: File, index: Int): ByteArray {
        val offset = index.toLong() * MediaChunkProtocol.CHUNK_SIZE
        val length = minOf(MediaChunkProtocol.CHUNK_SIZE.toLong(), file.length() - offset)
        if (length <= 0) return ByteArray(0)
        val buffer = ByteArray(length.toInt())
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            raf.readFully(buffer)
        }
        return buffer
    }

    private fun publishAck(
        geohash: String,
        transferId: String,
        missing: IntArray,
        nickname: String?,
        force: Boolean
    ) {
        val now = System.currentTimeMillis()
        val last = lastAckAt[transferId] ?: 0L
        if (!force && now - last < ACK_THROTTLE_MS) return
        lastAckAt[transferId] = now
        scope.launch {
            try {
                val content = MediaChunkProtocol.encodeAck(transferId, missing)
                publish(geohash, content, nickname)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to publish ack: ${e.message}")
            }
        }
    }

    private fun ensureRetryLoop() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(RETRY_INTERVAL_MS)
                val now = System.currentTimeMillis()
                pending.entries.toList().forEach { (id, transfer) ->
                    if (transfer.acked) {
                        pending.remove(id)
                        return@forEach
                    }
                    if (now - transfer.createdAt > TRANSFER_TIMEOUT_MS) {
                        Log.w(TAG, "Transfer ${id.take(8)} timed out")
                        pending.remove(id)
                        return@forEach
                    }
                    if (transfer.attempts >= MAX_ATTEMPTS) {
                        Log.w(TAG, "Transfer ${id.take(8)} gave up after ${transfer.attempts} attempts")
                        pending.remove(id)
                        return@forEach
                    }
                    if (now - transfer.lastSentAt < RETRY_INTERVAL_MS) return@forEach
                    transfer.attempts++
                    val missing = transfer.missing
                    Log.d(TAG, "Retrying transfer ${id.take(8)} (attempt ${transfer.attempts}), missing=${missing?.size ?: transfer.total}")
                    sendChunks(transfer, missing)
                }
            }
        }
    }
}
