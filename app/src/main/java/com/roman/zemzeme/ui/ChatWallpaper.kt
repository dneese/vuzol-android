package com.roman.zemzeme.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Chat background: solid theme background, soft accent gradient or a photo
 * wallpaper with a readability scrim.
 */
@Composable
fun ChatWallpaper(
    accent: Color,
    mode: Int,
    photoPath: String?,
    modifier: Modifier = Modifier
) {
    val isDark = com.roman.zemzeme.ui.theme.isAppInDarkTheme()
    val bitmap = remember(photoPath) { photoPath?.let { decodeSampledBitmap(it) } }

    Box(modifier = modifier) {
        when (mode) {
            ChatAppearanceStore.WALLPAPER_GRADIENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to accent.copy(alpha = if (isDark) 0.30f else 0.18f),
                                0.40f to accent.copy(alpha = 0.07f),
                                1f to Color.Transparent
                            )
                        )
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 52.dp.toPx()
                    val radius = 1.5.dp.toPx()
                    var y = step
                    while (y < size.height) {
                        var x = step / 2
                        while (x < size.width) {
                            drawCircle(
                                color = accent.copy(alpha = 0.10f),
                                radius = radius,
                                center = Offset(x, y)
                            )
                            x += step
                        }
                        y += step
                    }
                }
            }

            ChatAppearanceStore.WALLPAPER_PHOTO -> {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (isDark) 0.32f else 0.5f),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isDark) Color.Black.copy(alpha = 0.35f)
                                else Color.White.copy(alpha = 0.30f)
                            )
                    )
                }
            }

            else -> Unit
        }
    }
}

private fun decodeSampledBitmap(path: String): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val maxDimension = 1080
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, options)
    } catch (_: Exception) {
        null
    }
}
