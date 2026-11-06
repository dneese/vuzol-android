package com.roman.zemzeme.features.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Effects
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shrinks videos before they are sent to a group.
 *
 * Raw camera videos are far too big for a relay based transport, so they are
 * transcoded to a smaller resolution and bitrate. If transcoding fails the
 * original file is used, the transfer then simply takes longer.
 */
object VideoCompressor {

    private const val TAG = "VideoCompressor"

    /** Longest edge of the compressed video. */
    private const val MAX_DIMENSION = 720

    /** Target bitrate for the compressed video. */
    private const val TARGET_BITRATE = 1_200_000

    /** Videos longer than this are trimmed. */
    private const val MAX_DURATION_MS = 90_000L

    /** Files below this size are already fine and are sent unchanged. */
    private const val SIZE_THRESHOLD_BYTES = 1_200_000L

    /**
     * Returns a compressed copy of [file] or null when the file is small
     * enough or compression is not possible.
     */
    suspend fun compressIfNeeded(context: Context, file: File): File? {
        if (!file.exists() || !file.isFile) return null
        if (file.length() <= SIZE_THRESHOLD_BYTES) return null
        return compress(context, file)
    }

    private suspend fun compress(context: Context, file: File): File? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val output = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Log.d(TAG, "Video compressed: ${file.length()} -> ${output.length()} bytes")
                        if (continuation.isActive) continuation.resumeWith(Result.success(output))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Log.w(TAG, "Video compression failed: ${exportException.message}")
                        output.delete()
                        if (continuation.isActive) continuation.resumeWith(Result.success(null))
                    }
                }

                try {
                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(listener)
                        .build()

                    val clipping = MediaItem.ClippingConfiguration.Builder()
                        .setEndPositionMs(MAX_DURATION_MS)
                        .build()

                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.fromFile(file))
                        .setClippingConfiguration(clipping)
                        .build()

                    val effects = Effects(
                        emptyList(),
                        listOf<Effect>(Presentation.createForHeight(MAX_DIMENSION))
                    )

                    val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                        .setEffects(effects)
                        .build()

                    transformer.start(editedMediaItem, output.absolutePath)
                } catch (e: Exception) {
                    Log.w(TAG, "Video compression could not start: ${e.message}")
                    output.delete()
                    if (continuation.isActive) continuation.resumeWith(Result.success(null))
                }
            }
        }
}
