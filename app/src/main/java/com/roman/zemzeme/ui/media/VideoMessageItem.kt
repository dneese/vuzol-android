package com.roman.zemzeme.ui.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.roman.zemzeme.R
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Video message: a thumbnail with a play button. Tapping opens the built-in
 * player, so the video can be watched right in the chat.
 */
@Composable
fun VideoMessageItem(
    message: ZemzemeMessage,
    timeText: String,
    modifier: Modifier = Modifier
) {
    val path = message.content.trim()
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    var thumbnail by remember(path) { mutableStateOf<Bitmap?>(null) }
    var durationMs by remember(path) { mutableStateOf(0L) }
    var showPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        val result = withContext(Dispatchers.IO) { loadVideoPreview(path) }
        thumbnail = result.first
        durationMs = result.second
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF101014))
                .clickable { showPlayer = true },
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumbnail
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.video_play),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
            if (durationMs > 0) {
                Text(
                    text = formatDuration(durationMs),
                    fontSize = 11.sp,
                    fontFamily = NunitoFontFamily,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Text(
            text = timeText,
            fontSize = 11.sp,
            fontFamily = NunitoFontFamily,
            color = colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 3.dp)
        )
    }

    if (showPlayer) {
        VideoPlayerDialog(
            path = path,
            onClose = { showPlayer = false },
            onSave = {
                val ok = com.roman.zemzeme.features.file.FileUtils.saveToDownloads(
                    context = context,
                    fileName = File(path).name,
                    mimeType = "video/mp4",
                    content = runCatching { File(path).readBytes() }.getOrDefault(ByteArray(0))
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

/** Fullscreen-ish player with the standard media controls. */
@Composable
fun VideoPlayerDialog(
    path: String,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = Color.Black) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(path)
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setOnPreparedListener { player ->
                                    player.isLooping = false
                                    start()
                                }
                                setOnErrorListener { _, _, _ -> true }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = File(path).name,
                        fontSize = 13.sp,
                        fontFamily = NunitoFontFamily,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(R.string.video_save),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onSave() }
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onClose() }
                    )
                }
            }
        }
    }
}

/** Loads the first frame and the duration of a video. */
private fun loadVideoPreview(path: String): Pair<Bitmap?, Long> {
    return try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            frame to duration
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    } catch (_: Exception) {
        null to 0L
    }
}

/** Formats a duration in milliseconds as mm:ss (or h:mm:ss). */
private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
