package com.roman.zemzeme.features.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Shrinks photos before they are sent over relays/mesh.
 * Large photos are the main cause of failed media delivery, so anything above
 * a few hundred kilobytes is re-encoded down to [MAX_DIM] pixels at JPEG [QUALITY].
 */
object ImageCompressor {
    private const val MAX_DIM = 1600
    private const val QUALITY = 88
    private const val SMALL_ENOUGH_BYTES = 400_000L

    fun compressIfImage(context: Context, path: String, mimeType: String): File {
        val source = File(path)
        if (!source.exists()) return source
        if (!mimeType.startsWith("image/")) return source
        if (mimeType.contains("gif", ignoreCase = true)) return source
        if (source.length() <= SMALL_ENOUGH_BYTES) return source

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return source

            var sample = 1
            while (bounds.outWidth / sample > MAX_DIM || bounds.outHeight / sample > MAX_DIM) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return source

            val outFile = File(context.cacheDir, "vuzol_send_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, stream)
            }
            bitmap.recycle()

            if (outFile.length() in 1 until source.length()) outFile else source
        } catch (_: Exception) {
            source
        }
    }
}
