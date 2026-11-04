package com.roman.zemzeme.ui
// [Goose] TODO: Replace inline file attachment stub with FilePickerButton abstraction that dispatches via FileShareDispatcher


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roman.zemzeme.R
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.withStyle
import com.roman.zemzeme.ui.theme.BASE_FONT_SIZE
import com.roman.zemzeme.features.voice.normalizeAmplitudeSample
import com.roman.zemzeme.features.voice.AudioWaveformExtractor
import com.roman.zemzeme.ui.media.RealtimeScrollingWaveform
import com.roman.zemzeme.ui.media.ImagePickerButton
import com.roman.zemzeme.ui.media.FilePickerButton

/**
 * Input components for ChatScreen
 * Extracted from ChatScreen.kt for better organization
 */

/**
 * VisualTransformation that styles slash commands with background and color
 * while preserving cursor positioning and click handling
 */
class SlashCommandVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val slashCommandRegex = Regex("(/\\w+)(?=\\s|$)")
        val annotatedString = buildAnnotatedString {
            var lastIndex = 0

            slashCommandRegex.findAll(text.text).forEach { match ->
                // Add text before the match
                if (match.range.first > lastIndex) {
                    append(text.text.substring(lastIndex, match.range.first))
                }

                // Add the styled slash command
                withStyle(
                    style = SpanStyle(
                        color = Color(0xFF00FF7F), // Bright green
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.Medium,
                        background = Color(0xFF2D2D2D) // Dark gray background
                    )
                ) {
                    append(match.value)
                }

                lastIndex = match.range.last + 1
            }

            // Add remaining text
            if (lastIndex < text.text.length) {
                append(text.text.substring(lastIndex))
            }
        }

        return TransformedText(
            text = annotatedString,
            offsetMapping = OffsetMapping.Identity
        )
    }
}

/**
 * VisualTransformation that styles mentions with background and color
 * while preserving cursor positioning and click handling
 */
class MentionVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val mentionRegex = Regex("@([a-zA-Z0-9_]+)")
        val annotatedString = buildAnnotatedString {
            var lastIndex = 0
            
            mentionRegex.findAll(text.text).forEach { match ->
                // Add text before the match
                if (match.range.first > lastIndex) {
                    append(text.text.substring(lastIndex, match.range.first))
                }
                
                // Add the styled mention
                withStyle(
                    style = SpanStyle(
                        color = Color(0xFFFF9500), // Orange
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(match.value)
                }
                
                lastIndex = match.range.last + 1
            }
            
            // Add remaining text
            if (lastIndex < text.text.length) {
                append(text.text.substring(lastIndex))
            }
        }
        
        return TransformedText(
            text = annotatedString,
            offsetMapping = OffsetMapping.Identity
        )
    }
}

/**
 * VisualTransformation that combines multiple visual transformations
 */
class CombinedVisualTransformation(private val transformations: List<VisualTransformation>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        var resultText = text
        
        // Apply each transformation in order
        transformations.forEach { transformation ->
            resultText = transformation.filter(resultText).text
        }
        
        return TransformedText(
            text = resultText,
            offsetMapping = OffsetMapping.Identity
        )
    }
}





@Composable
fun MessageInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onSendVoiceNote: (String?, String?, String) -> Unit,
    onSendImageNote: (String?, String?, String) -> Unit,
    onSendFileNote: (String?, String?, String) -> Unit,
    onSendMediaBundle: (String?, String?, List<String>) -> Unit = { _, _, _ -> },
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    showMediaButtons: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = LocalChatAccent.current
    val isFocused = remember { mutableStateOf(false) }
    val hasText = value.text.isNotBlank()
    val focusRequester = remember { FocusRequester() }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var amplitude by remember { mutableStateOf(0) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }

    val latestSelectedPeer = rememberUpdatedState(selectedPrivatePeer)
    val latestChannel = rememberUpdatedState(currentChannel)
    val latestOnSendVoiceNote = rememberUpdatedState(onSendVoiceNote)

    Column(modifier = modifier) {
        if (showEmojiPicker) {
            EmojiPickerPanel(
                onPick = { emoji ->
                    val newText = value.text + emoji
                    onValueChange(value.copy(text = newText, selection = TextRange(newText.length)))
                }
            )
        }

        Row(
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Attachments
            if (showMediaButtons && enabled) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .clickable { showAttachmentSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.cd_attach_file),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Rounded composer
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Always keep the text field mounted to retain focus and avoid IME collapse
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            enabled = enabled,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = if (enabled) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.3f),
                                fontFamily = NunitoFontFamily
                            ),
                            cursorBrush = SolidColor(LocalChatAccent.current),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (hasText && enabled) onSend()
                            }),
                            visualTransformation = CombinedVisualTransformation(
                                listOf(SlashCommandVisualTransformation(), MentionVisualTransformation())
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    isFocused.value = focusState.isFocused
                                }
                        )

                        // Show placeholder when there's no text and not recording
                        if (value.text.isEmpty() && !isRecording) {
                            Text(
                                text = stringResource(R.string.type_a_message_placeholder),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = NunitoFontFamily
                                ),
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Overlay the real-time scrolling waveform while recording
                        if (isRecording) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RealtimeScrollingWaveform(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp),
                                    amplitudeNorm = normalizeAmplitudeSample(amplitude)
                                )
                                Spacer(Modifier.width(12.dp))
                                val secs = (elapsedMs / 1000).toInt()
                                val mm = secs / 60
                                val ss = secs % 60
                                val maxSecs = 10
                                val maxMm = maxSecs / 60
                                val maxSs = maxSecs % 60
                                Text(
                                    text = String.format("%02d:%02d / %02d:%02d", mm, ss, maxMm, maxSs),
                                    fontFamily = NunitoFontFamily,
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Emoji picker toggle
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { showEmojiPicker = !showEmojiPicker },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEmotions,
                            contentDescription = stringResource(id = R.string.emoji_picker),
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Send / record
            if (hasText) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(enabled = enabled) { if (hasText && enabled) onSend() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = stringResource(id = R.string.send_message),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else if (showMediaButtons && enabled) {
                VoiceRecordButton(
                    backgroundColor = accent,
                    onStart = {
                        isRecording = true
                        elapsedMs = 0L
                    },
                    onAmplitude = { amp, ms ->
                        amplitude = amp
                        elapsedMs = ms
                    },
                    onFinish = { path ->
                        isRecording = false
                        AudioWaveformExtractor.extractAsync(path, sampleCount = 120) { arr ->
                            if (arr != null) {
                                try { com.roman.zemzeme.features.voice.VoiceWaveformCache.put(path, arr) } catch (_: Exception) {}
                            }
                        }
                        latestOnSendVoiceNote.value(
                            latestSelectedPeer.value,
                            latestChannel.value,
                            path
                        )
                    }
                )
            }
        }
    }

    // Attachments — the pickers live here (not inside the sheet) so that the
    // result is never lost when the sheet is recomposed or closed.
    val attachmentContext = androidx.compose.ui.platform.LocalContext.current
    var pendingAttachments by remember { mutableStateOf<List<PendingAttachment>>(emptyList()) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris: List<android.net.Uri> ->
        val picked = uris.mapNotNull { uri ->
            val mime = try {
                attachmentContext.contentResolver.getType(uri).orEmpty()
            } catch (_: Exception) { "" }
            val path = if (mime.startsWith("image/")) {
                com.roman.zemzeme.features.media.ImageUtils
                    .downscaleAndSaveToAppFiles(attachmentContext, uri)
            } else {
                com.roman.zemzeme.features.file.FileUtils
                    .copyFileForSending(attachmentContext, uri)
            }
            if (path.isNullOrBlank()) null else {
                val file = java.io.File(path)
                PendingAttachment(path, file.name, file.length(), isImage = mime.startsWith("image/"))
            }
        }
        if (picked.isNotEmpty()) pendingAttachments = pendingAttachments + picked
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<android.net.Uri> ->
        val picked = uris.mapNotNull { uri ->
            try {
                attachmentContext.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            val path = com.roman.zemzeme.features.file.FileUtils
                .copyFileForSending(attachmentContext, uri)
            if (path.isNullOrBlank()) null else {
                val file = java.io.File(path)
                val mime = try {
                    attachmentContext.contentResolver.getType(uri).orEmpty()
                } catch (_: Exception) { "" }
                PendingAttachment(path, file.name, file.length(), isImage = mime.startsWith("image/"))
            }
        }
        if (picked.isNotEmpty()) pendingAttachments = pendingAttachments + picked
    }

    if (showAttachmentSheet) {
        AttachmentSheet(
            pending = pendingAttachments,
            onDismiss = { showAttachmentSheet = false },
            onPickPhoto = {
                photoPicker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts
                            .PickVisualMedia.ImageAndVideo
                    )
                )
            },
            onPickFile = { filePicker.launch(arrayOf("*/*")) },
            onCancelPending = { pendingAttachments = emptyList() },
            onConfirm = { attachments ->
                pendingAttachments = emptyList()
                showAttachmentSheet = false
                if (attachments.size == 1) {
                    val attachment = attachments.first()
                    if (attachment.isImage) {
                        onSendImageNote(latestSelectedPeer.value, latestChannel.value, attachment.path)
                    } else {
                        onSendFileNote(latestSelectedPeer.value, latestChannel.value, attachment.path)
                    }
                } else if (attachments.size > 1) {
                    onSendMediaBundle(
                        latestSelectedPeer.value,
                        latestChannel.value,
                        attachments.map { it.path }
                    )
                }
            }
        )
    }
}

/** A file that was picked and is waiting for confirmation. */
private data class PendingAttachment(
    val path: String,
    val name: String,
    val size: Long,
    val isImage: Boolean
)

/**
 * Bottom sheet with the attachment options (photo / file).
 *
 * The picked file is shown before sending, so the user always knows what and
 * how much is about to leave the device.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    pending: List<PendingAttachment>,
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onCancelPending: () -> Unit,
    onConfirm: (List<PendingAttachment>) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val maxBytes = com.roman.zemzeme.util.AppConstants.Media.MAX_FILE_SIZE_BYTES

    com.roman.zemzeme.core.ui.component.sheet.ZemzemeBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.attachments_title),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = NunitoFontFamily,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = stringResource(
                    R.string.attachment_size_limit,
                    com.roman.zemzeme.features.file.FileUtils.formatFileSize(maxBytes)
                ),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = NunitoFontFamily,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            AttachmentRow(
                icon = Icons.Filled.Photo,
                label = stringResource(R.string.action_send_media),
                onClick = onPickPhoto
            )
            AttachmentRow(
                icon = Icons.Filled.Attachment,
                label = stringResource(R.string.action_send_file),
                onClick = onPickFile
            )

            if (pending.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.attachment_selected_count, pending.size),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = NunitoFontFamily,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        pending.take(5).forEach { attachment ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (attachment.isImage) Icons.Filled.Photo else Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = attachment.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = NunitoFontFamily,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = com.roman.zemzeme.features.file.FileUtils.formatFileSize(attachment.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = NunitoFontFamily,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (pending.size > 5) {
                            Text(
                                text = stringResource(R.string.attachment_more_files, pending.size - 5),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = NunitoFontFamily,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onCancelPending) {
                                Text(
                                    text = stringResource(R.string.attachment_cancel),
                                    fontFamily = NunitoFontFamily,
                                    color = colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.size(6.dp))
                            Button(
                                onClick = { onConfirm(pending) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.attachment_send),
                                    fontFamily = NunitoFontFamily
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            fontSize = 15.sp,
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface
        )
    }
}

@Composable
private fun EmojiPickerPanel(onPick: (String) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        color = colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            EMOJI_PALETTE.chunked(8).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPick(emoji) }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

private val EMOJI_PALETTE = listOf(
        "😀","😄","😁","😆","😅","😂","🙂","🙃","😉","😊","😇","🥰","😍","😘","😜","🤪","🤔","🤨","😐","😴","😢","😭","😤","😡","🥺","😱","🤯","😎","🤓","🥳","😏","🙄",
        "👍","👎","👌","✌️","🤝","🙏","💪","👏","🙌","👀","❤️","🧡","💚","💙","💜","🖤","💔","❤️‍🔥","✨","🔥","🎉","🎊","⭐","🌟","💯","✅","❌","⚠️","❓","❗","💡","🚀"
    )

@Composable
fun CommandSuggestionsBox(
    suggestions: List<CommandSuggestion>,
    onSuggestionClick: (CommandSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(vertical = 8.dp)
    ) {
        suggestions.forEach { suggestion: CommandSuggestion ->
            CommandSuggestionItem(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
        }
    }
}

@Composable
fun CommandSuggestionItem(
    suggestion: CommandSuggestion,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .background(Color.Gray.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Show all aliases together
        val allCommands = if (suggestion.aliases.isNotEmpty()) {
            listOf(suggestion.command) + suggestion.aliases
        } else {
            listOf(suggestion.command)
        }

        Text(
            text = allCommands.joinToString(", "),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.Medium
            ),
            color = colorScheme.primary,
            fontSize = (BASE_FONT_SIZE - 4).sp
        )

        // Show syntax if any
        suggestion.syntax?.let { syntax ->
            Text(
                text = syntax,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = NunitoFontFamily
                ),
                color = colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = (BASE_FONT_SIZE - 5).sp
            )
        }

        // Show description
        Text(
            text = suggestion.description,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = NunitoFontFamily
            ),
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = (BASE_FONT_SIZE - 5).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MentionSuggestionsBox(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Column(
        modifier = modifier
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(vertical = 8.dp)
    ) {
        suggestions.forEach { suggestion: String ->
            MentionSuggestionItem(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
        }
    }
}

@Composable
fun MentionSuggestionItem(
    suggestion: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .background(Color.Gray.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.mention_suggestion_at, suggestion),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFFFF9500), // Orange like mentions
            fontSize = (BASE_FONT_SIZE - 4).sp
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = stringResource(R.string.mention),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = NunitoFontFamily
            ),
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = (BASE_FONT_SIZE - 5).sp
        )
    }
}
