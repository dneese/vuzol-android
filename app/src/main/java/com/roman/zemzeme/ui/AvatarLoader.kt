package com.roman.zemzeme.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.roman.zemzeme.net.OkHttpProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Small in-memory cache for remote avatars. */
object AvatarLoader {
    private val cache = object : LruCache<String, Bitmap>(32) {}

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            cache.get(url)?.let { return@withContext it.asImageBitmap() }
            val request = Request.Builder().url(url).build()
            OkHttpProvider.httpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext null
                val scaled = scaleTo(decoded, 160)
                cache.put(url, scaled)
                scaled.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleTo(bitmap: Bitmap, maxSize: Int): Bitmap = try {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) {
            bitmap
        } else {
            val ratio = maxSize.toFloat() / maxOf(width, height)
            Bitmap.createScaledBitmap(
                bitmap,
                (width * ratio).toInt().coerceAtLeast(1),
                (height * ratio).toInt().coerceAtLeast(1),
                true
            )
        }
    } catch (_: Exception) {
        bitmap
    }
}
