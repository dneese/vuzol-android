package com.roman.zemzeme.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.roman.zemzeme.R
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.model.ZemzemeMessageType
import com.roman.zemzeme.services.ChatHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saved messages shown as a real chat instead of a flat list, so bookmarks
 * read like a conversation with yourself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMessagesSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    myNickname: String? = null
) {
    if (!isPresented) return

    var saved by remember { mutableStateOf<List<ZemzemeMessage>>(emptyList()) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(isPresented) {
        saved = ChatHistoryStore.load(ChatHistoryStore.CONVERSATION_SAVED)
            .sortedByDescending { it.timestamp.time }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Chat header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.saved_messages_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }

                if (saved.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.saved_messages_empty),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(saved.size) {
                        if (saved.isNotEmpty()) listState.animateScrollToItem(0)
                    }
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(items = saved, key = { it.id }) { message ->
                            SavedMessageBubble(
                                message = message,
                                myNickname = myNickname,
                                time = timeFormatter.format(Date(message.timestamp.time)),
                                onDelete = {
                                    ChatHistoryStore.delete(message.id)
                                    saved = saved.filterNot { it.id == message.id }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedMessageBubble(
    message: ZemzemeMessage,
    myNickname: String?,
    time: String,
    onDelete: () -> Unit
) {
    val isMine = myNickname != null && message.sender.equals(myNickname, ignoreCase = true)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = 6.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isMine) {
                    Text(
                        text = message.sender,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                when (message.type) {
                    ZemzemeMessageType.Image -> SavedImagePreview(message.content)
                    ZemzemeMessageType.Video -> SavedMediaRow(
                        icon = { Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = stringResource(R.string.saved_video_label)
                    )
                    ZemzemeMessageType.Audio -> SavedMediaRow(
                        icon = { Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = stringResource(R.string.saved_voice_label)
                    )
                    ZemzemeMessageType.File -> SavedMediaRow(
                        icon = { Icon(Icons.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = message.content.substringAfterLast('/')
                    )
                    ZemzemeMessageType.Media -> SavedMediaRow(
                        icon = { Icon(Icons.Filled.Photo, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = stringResource(R.string.saved_media_label)
                    )
                    ZemzemeMessageType.Message -> Text(
                        text = message.content,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = time,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete_title),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedImagePreview(path: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            try {
                com.roman.zemzeme.ui.media.loadThumbnail(path, "image/jpeg")
            } catch (_: Exception) {
                null
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .heightIn(max = 220.dp)
                .widthIn(max = 260.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
        )
    } else {
        SavedMediaRow(
            icon = { Icon(Icons.Filled.Photo, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = stringResource(R.string.saved_photo_label)
        )
    }
}

@Composable
private fun SavedMediaRow(
    icon: @Composable () -> Unit,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon()
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
