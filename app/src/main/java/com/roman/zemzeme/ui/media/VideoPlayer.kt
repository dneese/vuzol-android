package com.roman.zemzeme.ui.media

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.roman.zemzeme.R
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import kotlinx.coroutines.delay
import java.io.File

/**
 * Video player with custom controls: play/pause, seek bar, fullscreen and save.
 */
@Composable
fun VideoPlayerDialog(
    path: String,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var fullscreen by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        VideoPlayerContent(
            path = path,
            fullscreen = fullscreen,
            onToggleFullscreen = {
                fullscreen = !fullscreen
                activity?.requestedOrientation = if (fullscreen) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            },
            onClose = onClose,
            onSave = onSave
        )
    }
}

@Composable
private fun VideoPlayerContent(
    path: String,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    val videoRef = remember { VideoRef() }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        while (true) {
            delay(300)
            val view = videoRef.view ?: continue
            if (!scrubbing) {
                position = view.currentPosition.toFloat().coerceAtLeast(0f)
                val total = view.duration
                if (total > 0) duration = total.toFloat()
            }
            isPlaying = view.isPlaying
        }
    }

    Surface(color = Color.Black) {
        Column(
            modifier = if (fullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            },
            verticalArrangement = if (fullscreen) Arrangement.Center else Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(path)
                            setOnPreparedListener { player ->
                                player.isLooping = false
                                started = true
                                start()
                            }
                            setOnCompletionListener { isPlaying = false }
                            setOnErrorListener { _, _, _ -> true }
                        }.also { videoRef.view = it }
                    },
                    modifier = if (fullscreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    }
                )

                if (!started) {
                    Text(
                        text = stringResource(R.string.video_loading),
                        fontSize = 13.sp,
                        fontFamily = NunitoFontFamily,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111114))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatPlaybackTime(position.toLong()),
                        fontSize = 12.sp,
                        fontFamily = NunitoFontFamily,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = position.coerceIn(0f, if (duration > 0f) duration else 1f),
                        onValueChange = {
                            scrubbing = true
                            position = it
                        },
                        onValueChangeFinished = {
                            videoRef.view?.seekTo(position.toInt())
                            scrubbing = false
                        },
                        valueRange = 0f..if (duration > 0f) duration else 1f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatPlaybackTime(duration.toLong()),
                        fontSize = 12.sp,
                        fontFamily = NunitoFontFamily,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.video_pause else R.string.video_play
                        ),
                        tint = Color.White,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable {
                                val view = videoRef.view ?: return@clickable
                                if (view.isPlaying) {
                                    view.pause()
                                    isPlaying = false
                                } else {
                                    view.start()
                                    isPlaying = true
                                }
                            }
                    )

                    Text(
                        text = File(path).name,
                        fontSize = 13.sp,
                        fontFamily = NunitoFontFamily,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = stringResource(
                            if (fullscreen) R.string.video_exit_fullscreen else R.string.video_fullscreen
                        ),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { onToggleFullscreen() }
                    )
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(R.string.video_save),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { onSave() }
                    )
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { onClose() }
                    )
                }
            }
        }
    }
}

/** Plain holder for the platform video view (no snapshot writes during composition). */
private class VideoRef {
    var view: VideoView? = null
}

/** Finds the hosting activity from a composition context. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Formats a playback position as mm:ss (or h:mm:ss). */
private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
