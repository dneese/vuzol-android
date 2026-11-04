package com.roman.zemzeme.ui.media

import android.graphics.Bitmap
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roman.zemzeme.R
import com.roman.zemzeme.core.ui.component.sheet.ZemzemeBottomSheet
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.model.ZemzemeMessageType
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One photo or video in the gallery. */
data class GalleryEntry(
    val path: String,
    val mimeType: String
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

/**
 * Gallery of the photos and videos shared in the current conversation.
 *
 * Tapping a photo opens the fullscreen viewer (with zoom), tapping a video
 * opens the built-in player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGallerySheet(
    isPresented: Boolean,
    messages: List<ZemzemeMessage>,
    onDismiss: () -> Unit
) {
    if (!isPresented) return

    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    var entries by remember(messages) { mutableStateOf<List<GalleryEntry>>(emptyList()) }
    var viewerIndex by remember { mutableStateOf(-1) }
    var videoToPlay by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(messages) {
        entries = withContext(Dispatchers.IO) { collectGalleryEntries(context, messages) }
    }

    val imagePaths = entries.filter { !it.isVideo }.map { it.path }

    ZemzemeBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.media_gallery_title),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.media_gallery_empty),
                        fontSize = 14.sp,
                        fontFamily = NunitoFontFamily,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(entries) { entry ->
                        GalleryThumbnail(
                            entry = entry,
                            onClick = {
                                if (entry.isVideo) {
                                    videoToPlay = entry.path
                                } else {
                                    viewerIndex = imagePaths.indexOf(entry.path)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (viewerIndex >= 0 && imagePaths.isNotEmpty()) {
        FullScreenImageViewer(
            imagePaths = imagePaths,
            initialIndex = viewerIndex,
            onClose = { viewerIndex = -1 }
        )
    }

    val playing = videoToPlay
    if (playing != null) {
        VideoPlayerDialog(
            path = playing,
            onClose = { videoToPlay = null },
            onSave = {
                val ok = com.roman.zemzeme.features.file.FileUtils.saveToDownloads(
                    context = context,
                    fileName = File(playing).name,
                    mimeType = "video/mp4",
                    content = runCatching { File(playing).readBytes() }.getOrDefault(ByteArray(0))
                )
                android.widget.Toast.makeText(
                    context,
                    context.getString(
                        if (ok) R.string.toast_file_saved else R.string.toast_failed_to_save_file
                    ),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

/** Collects every photo and video of the conversation, including bundle contents. */
private fun collectGalleryEntries(
    context: android.content.Context,
    messages: List<ZemzemeMessage>
): List<GalleryEntry> {
    val result = mutableListOf<GalleryEntry>()
    messages.forEach { message ->
        when (message.type) {
            ZemzemeMessageType.Image -> {
                val path = message.content.trim()
                if (path.isNotBlank()) result.add(GalleryEntry(path, "image/*"))
            }
            ZemzemeMessageType.Video -> {
                val path = message.content.trim()
                if (path.isNotBlank()) result.add(GalleryEntry(path, "video/*"))
            }
            ZemzemeMessageType.Media -> {
                val path = message.content.trim()
                if (path.isNotBlank()) {
                    extractBundle(context, path)
                        .filter { it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") }
                        .forEach { result.add(GalleryEntry(it.path, it.mimeType)) }
                }
            }
            else -> Unit
        }
    }
    return result
}

@Composable
private fun GalleryThumbnail(entry: GalleryEntry, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    var bitmap by remember(entry.path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(entry.path) {
        bitmap = withContext(Dispatchers.IO) {
            loadThumbnail(entry.path, if (entry.isVideo) "video/mp4" else "image/jpeg")
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (entry.isVideo) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.video_play),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
