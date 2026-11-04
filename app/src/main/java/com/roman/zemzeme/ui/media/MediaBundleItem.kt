package com.roman.zemzeme.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roman.zemzeme.model.MediaBundle
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.model.ZemzemeMessageType
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat

/** One extracted file of a media bundle. */
data class BundleEntry(
    val name: String,
    val mimeType: String,
    val path: String
)

/**
 * Renders a message that carries several photos / videos / files as one grid.
 */
@Composable
fun MediaBundleItem(
    message: ZemzemeMessage,
    timeFormatter: SimpleDateFormat,
    onImageClick: ((String, List<String>, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val path = message.content.trim()
    val colorScheme = MaterialTheme.colorScheme

    var entries by remember(path) { mutableStateOf<List<BundleEntry>>(emptyList()) }
    var loading by remember(path) { mutableStateOf(true) }

    LaunchedEffect(path) {
        val extracted = withContext(Dispatchers.IO) { extractBundle(context, path) }
        entries = extracted
        loading = false
    }

    Column(modifier = modifier.fillMaxWidth()) {
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.primary
                    )
                }
            }

            entries.isEmpty() -> {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = NunitoFontFamily,
                    color = colorScheme.onSurfaceVariant
                )
            }

            else -> {
                val imagePaths = entries.filter { it.mimeType.startsWith("image/") }.map { it.path }
                val visible = entries.take(4)
                val columns = if (visible.size == 1) 1 else 2

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    visible.chunked(columns).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            rowItems.forEach { entry ->
                                BundleCell(
                                    entry = entry,
                                    modifier = Modifier.weight(1f),
                                    showPlus = entry === visible.last() && entries.size > 4,
                                    extraCount = entries.size - 4,
                                    onClick = {
                                        val index = imagePaths.indexOf(entry.path)
                                        if (entry.mimeType.startsWith("image/") && index >= 0) {
                                            onImageClick?.invoke(entry.path, imagePaths, index)
                                        }
                                    }
                                )
                            }
                            // keep the row balanced when a single item is in the last row
                            if (rowItems.size < columns) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Text(
                    text = timeFormatter.format(message.timestamp),
                    fontSize = 11.sp,
                    fontFamily = NunitoFontFamily,
                    color = colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun BundleCell(
    entry: BundleEntry,
    modifier: Modifier = Modifier,
    showPlus: Boolean = false,
    extraCount: Int = 0,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isImage = entry.mimeType.startsWith("image/")
    val isVideo = entry.mimeType.startsWith("video/")

    var bitmap by remember(entry.path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entry.path) {
        bitmap = withContext(Dispatchers.IO) { loadThumbnail(entry.path, entry.mimeType) }
    }

    Box(
        modifier = modifier
            .aspectRatio(if (isImage || isVideo) 1f else 1.3f)
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
        }

        if (showPlus && extraCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$extraCount",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NunitoFontFamily
                )
            }
        }
    }
}

/** Extracts the bundle file into individual files so they can be displayed. */
private fun extractBundle(context: android.content.Context, path: String): List<BundleEntry> {
    return try {
        val source = File(path)
        if (!source.exists()) return emptyList()
        val items = MediaBundle.decode(source.readBytes()) ?: return emptyList()
        val dir = File(context.cacheDir, "media/bundles/extracted").apply { mkdirs() }
        items.mapIndexed { index, item ->
            val safeName = item.name.substringAfterLast('/').ifBlank { "file_$index" }
            val target = File(dir, "${index}_$safeName")
            try {
                target.writeBytes(item.content)
            } catch (_: Exception) { }
            BundleEntry(item.name, item.mimeType, target.absolutePath)
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/** Loads a small preview for an image or the first frame of a video. */
private fun loadThumbnail(path: String, mimeType: String): Bitmap? {
    return try {
        if (mimeType.startsWith("video/")) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                try { retriever.release() } catch (_: Exception) { }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }
    } catch (_: Exception) {
        null
    }
}
