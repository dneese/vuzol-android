package com.roman.zemzeme.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.draw.blur
import com.roman.zemzeme.ui.ChatDisplaySettings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontFamily
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import com.roman.zemzeme.mesh.BluetoothMeshService
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.model.ZemzemeMessageType
import androidx.compose.material3.ColorScheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * Decodes a downscaled preview (max 480 px) so the chat stays light even
 * with many photos. The full resolution image is loaded by the fullscreen
 * viewer when the user taps the preview.
 */
private fun decodePreview(path: String, maxDim: Int = 480): android.graphics.Bitmap? {
    return try {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        android.graphics.BitmapFactory.decodeFile(
            path,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        )
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageMessageItem(
    message: ZemzemeMessage,
    messages: List<ZemzemeMessage>,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    colorScheme: ColorScheme,
    timeFormatter: SimpleDateFormat,
    onNicknameClick: ((String) -> Unit)?,
    onMessageLongPress: ((ZemzemeMessage) -> Unit)?,
    onCancelTransfer: ((ZemzemeMessage) -> Unit)?,
    onImageClick: ((String, List<String>, Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val path = message.content.trim()
    Column(modifier = modifier.fillMaxWidth()) {
        val headerText = com.roman.zemzeme.ui.formatMessageHeaderAnnotatedString(
            message = message,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter
        )
        val haptic = LocalHapticFeedback.current
        var headerLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = headerText,
            fontFamily = NunitoFontFamily,
            color = colorScheme.onSurface,
            modifier = Modifier.pointerInput(message.id) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMessageLongPress?.invoke(message)
                    },
                    onTap = { pos ->
                    val layout = headerLayout ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(pos)
                    val ann = headerText.getStringAnnotations("nickname_click", offset, offset)
                    if (ann.isNotEmpty() && onNicknameClick != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNicknameClick.invoke(ann.first().item)
                    }
                })
            },
            onTextLayout = { headerLayout = it }
        )

        val context = LocalContext.current
        // Only a small preview is decoded for the chat; the full resolution
        // image is loaded by the fullscreen viewer when the user taps it.
        val bmp = remember(path) { decodePreview(path) }

        // Privacy: images received from other people can stay hidden until tapped
        val blurImages by ChatDisplaySettings.blurImagesFlow.collectAsState()
        var revealed by remember(message.id) { mutableStateOf(false) }
        val hidden = blurImages && message.sender != currentUserNickname && !revealed
        val blurModifier = if (hidden) Modifier.blur(16.dp) else Modifier

        // Collect all image paths from messages for swipe navigation
        val imagePaths = remember(messages) {
            messages.filter { it.type == ZemzemeMessageType.Image }
                .map { it.content.trim() }
        }

        if (bmp != null) {
            val img = bmp.asImageBitmap()
            val aspect = (bmp.width.toFloat() / bmp.height.toFloat()).takeIf { it.isFinite() && it > 0 } ?: 1f
            val progressFraction: Float? = when (val st = message.deliveryStatus) {
                is com.roman.zemzeme.model.DeliveryStatus.PartiallyDelivered -> if (st.total > 0) st.reached.toFloat() / st.total.toFloat() else 0f
                else -> null
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box {
                    if (progressFraction != null && progressFraction < 1f && message.sender == currentUserNickname) {
                        // Cyberpunk block-reveal while sending
                        BlockRevealImage(
                            bitmap = img,
                            progress = progressFraction,
                            blocksX = 24,
                            blocksY = 16,
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .aspectRatio(aspect)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = {
                                        val currentIndex = imagePaths.indexOf(path)
                                        onImageClick?.invoke(path, imagePaths, currentIndex)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onMessageLongPress?.invoke(message)
                                    }
                                )
                        )
                    } else {
                        // Fully revealed image
                        Image(
                            bitmap = img,
                            contentDescription = stringResource(com.roman.zemzeme.R.string.cd_image),
                            modifier = blurModifier
                                .widthIn(max = 300.dp)
                                .aspectRatio(aspect)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (hidden) {
                                            revealed = true
                                        } else {
                                            val currentIndex = imagePaths.indexOf(path)
                                            onImageClick?.invoke(path, imagePaths, currentIndex)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onMessageLongPress?.invoke(message)
                                    }
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (hidden) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .background(colorScheme.onSurface.copy(alpha = 0.45f))
                                .clickable { revealed = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(com.roman.zemzeme.R.string.chat_image_tap_to_reveal),
                                    color = colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    // Cancel button overlay during sending
                    val showCancel = message.sender == currentUserNickname && (message.deliveryStatus is com.roman.zemzeme.model.DeliveryStatus.PartiallyDelivered)
                    if (showCancel) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(22.dp)
                                .background(Color.Gray.copy(alpha = 0.6f), CircleShape)
                                .clickable { onCancelTransfer?.invoke(message) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(com.roman.zemzeme.R.string.cd_cancel), tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        } else {
            Text(text = stringResource(com.roman.zemzeme.R.string.image_unavailable), fontFamily = NunitoFontFamily, color = Color.Gray)
        }
    }
}
