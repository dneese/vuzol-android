package com.roman.zemzeme.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roman.zemzeme.R
import com.roman.zemzeme.core.ui.component.sheet.ZemzemeBottomSheet
import java.io.File

/**
 * Telegram-style chat appearance picker: accent color and wallpaper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAppearanceSheet(
    isPresented: Boolean,
    appearance: ChatAppearanceStore.Appearance,
    onAccentChange: (Int) -> Unit,
    onWallpaperChange: (Int, String?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isPresented) return
    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val saved = copyWallpaperToFiles(context, uri)
        if (saved != null) {
            onWallpaperChange(ChatAppearanceStore.WALLPAPER_PHOTO, saved)
        }
    }

    ZemzemeBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.chat_appearance_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(R.string.chat_accent_color),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            ChatAppearanceStore.accentPalette.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    row.forEach { color ->
                        val index = ChatAppearanceStore.accentPalette.indexOf(color)
                        val selected = appearance.accentIndex == index
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface
                                    else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onAccentChange(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.chat_wallpaper),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WallpaperOption(
                    label = stringResource(R.string.wallpaper_solid),
                    selected = appearance.wallpaperMode == ChatAppearanceStore.WALLPAPER_SOLID,
                    accent = appearance.accent,
                    gradient = false,
                    onClick = { onWallpaperChange(ChatAppearanceStore.WALLPAPER_SOLID, null) },
                    modifier = Modifier.weight(1f)
                )
                WallpaperOption(
                    label = stringResource(R.string.wallpaper_gradient),
                    selected = appearance.wallpaperMode == ChatAppearanceStore.WALLPAPER_GRADIENT,
                    accent = appearance.accent,
                    gradient = true,
                    onClick = { onWallpaperChange(ChatAppearanceStore.WALLPAPER_GRADIENT, null) },
                    modifier = Modifier.weight(1f)
                )
                WallpaperOption(
                    label = stringResource(R.string.wallpaper_photo),
                    selected = appearance.wallpaperMode == ChatAppearanceStore.WALLPAPER_PHOTO,
                    accent = appearance.accent,
                    gradient = false,
                    onClick = {
                        if (appearance.wallpaperMode == ChatAppearanceStore.WALLPAPER_PHOTO) {
                            photoPicker.launch("image/*")
                        } else {
                            photoPicker.launch("image/*")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = stringResource(R.string.wallpaper_patterns),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            val patternLabels = listOf(
                stringResource(R.string.wallpaper_pattern_dots),
                stringResource(R.string.wallpaper_pattern_geometric),
                stringResource(R.string.wallpaper_pattern_waves),
                stringResource(R.string.wallpaper_pattern_hexagons),
                stringResource(R.string.wallpaper_pattern_circuit),
                stringResource(R.string.wallpaper_pattern_topography)
            )

            patternLabels.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { label ->
                        val pattern = patternLabels.indexOf(label)
                        PatternOption(
                            label = label,
                            pattern = pattern,
                            selected = appearance.wallpaperMode ==
                                ChatAppearanceStore.WALLPAPER_PATTERN_BASE + pattern,
                            accent = appearance.accent,
                            onClick = {
                                onWallpaperChange(
                                    ChatAppearanceStore.WALLPAPER_PATTERN_BASE + pattern,
                                    null
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    photoPicker.launch("image/*")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.wallpaper_choose_photo))
            }

            OutlinedButton(
                onClick = {
                    onReset()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.appearance_reset))
            }
        }
    }
}

@Composable
private fun WallpaperOption(
    label: String,
    selected: Boolean,
    accent: Color,
    gradient: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) androidx.compose.foundation.BorderStroke(
            2.dp,
            accent
        ) else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .then(
                        if (gradient) {
                            Modifier.background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(accent, accent.copy(alpha = 0.15f))
                                )
                            )
                        } else {
                            Modifier.background(accent.copy(alpha = 0.35f))
                        }
                    )
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PatternOption(
    label: String,
    pattern: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) androidx.compose.foundation.BorderStroke(
            2.dp,
            accent
        ) else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f))
            ) {
                WallpaperPatterns.draw(this, pattern, accent, 0.55f)
            }
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

private fun copyWallpaperToFiles(context: Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val target = File(dir, "wallpaper_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        target.absolutePath
    } catch (_: Exception) {
        null
    }
}
