package com.roman.zemzeme.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ForwardToInbox
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roman.zemzeme.R
import com.roman.zemzeme.core.ui.component.sheet.ZemzemeBottomSheet
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.model.ZemzemeMessageType
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import androidx.compose.ui.res.stringResource

/**
 * Single place for every action that can be applied to a message.
 *
 * It replaces the floating popup and the duplicated action lists that used to
 * live in the peer sheet: long press a message (or tap the ⋮ button) and all
 * available operations are listed here in one clean, scrollable sheet.
 */
@Composable
fun MessageActionsSheet(
    message: ZemzemeMessage,
    authorName: String,
    authorColor: Color,
    timeText: String,
    isSelf: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onShare: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onDetails: (() -> Unit)? = null,
    onMute: (() -> Unit)? = null,
    onAutoDelete: (() -> Unit)? = null,
    onBlock: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = LocalChatAccent.current

    val replyLabel = stringResource(R.string.action_reply_title)
    val editLabel = stringResource(R.string.action_edit_title)
    val copyLabel = stringResource(R.string.action_copy_message_title)
    val shareLabel = stringResource(R.string.action_share_message)
    val forwardLabel = stringResource(R.string.action_forward_title)
    val saveLabel = stringResource(R.string.action_save_message_title)
    val pinLabel = stringResource(R.string.action_pin_title)
    val selectLabel = stringResource(R.string.action_select_multiple_title)
    val detailsLabel = stringResource(R.string.message_details_title)
    val muteLabel = stringResource(R.string.channel_mute)
    val autoDeleteLabel = stringResource(R.string.action_auto_delete_title)
    val deleteLabel = stringResource(R.string.action_delete_title)
    val blockLabel = stringResource(R.string.action_block_title_short)

    ZemzemeBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header: who wrote it and when
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileAvatar(
                    pubkey = message.senderPubkey,
                    letter = authorName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = authorColor,
                    size = 40.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = authorName,
                        fontSize = 16.sp,
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = authorColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timeText,
                        fontSize = 12.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Message preview
            val preview = message.content.trim()
            if (preview.isNotEmpty() && message.type == ZemzemeMessageType.Message) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = preview,
                        fontSize = 14.sp,
                        fontFamily = NunitoFontFamily,
                        color = colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Quick reactions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReactionsStore.SUPPORTED.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .clickable {
                                onReact(emoji)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 20.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SheetActionRow(
                    icon = Icons.AutoMirrored.Filled.Reply,
                    label = replyLabel,
                    tint = accent,
                    onClick = { onReply(); onDismiss() }
                )
                if (isSelf && onEdit != null) {
                    SheetActionRow(
                        icon = Icons.Outlined.Edit,
                        label = editLabel,
                        onClick = { onEdit(); onDismiss() }
                    )
                }
                SheetActionRow(
                    icon = Icons.Outlined.ContentCopy,
                    label = copyLabel,
                    onClick = { onCopy(); onDismiss() }
                )
                if (onShare != null) {
                    SheetActionRow(
                        icon = Icons.Filled.Share,
                        label = shareLabel,
                        onClick = { onShare(); onDismiss() }
                    )
                }
                if (onForward != null) {
                    SheetActionRow(
                        icon = Icons.Outlined.ForwardToInbox,
                        label = forwardLabel,
                        onClick = { onForward(); onDismiss() }
                    )
                }
                if (onSave != null) {
                    SheetActionRow(
                        icon = Icons.Filled.Bookmark,
                        label = saveLabel,
                        onClick = { onSave(); onDismiss() }
                    )
                }
                if (onPin != null) {
                    SheetActionRow(
                        icon = Icons.Outlined.PushPin,
                        label = pinLabel,
                        onClick = { onPin(); onDismiss() }
                    )
                }
                if (onSelect != null) {
                    SheetActionRow(
                        icon = Icons.Outlined.Checklist,
                        label = selectLabel,
                        onClick = { onSelect(); onDismiss() }
                    )
                }
                if (onDetails != null) {
                    SheetActionRow(
                        icon = Icons.Filled.Info,
                        label = detailsLabel,
                        onClick = { onDetails(); onDismiss() }
                    )
                }
                if (onMute != null) {
                    SheetActionRow(
                        icon = Icons.Outlined.NotificationsOff,
                        label = muteLabel,
                        onClick = { onMute(); onDismiss() }
                    )
                }
                if (onAutoDelete != null) {
                    SheetActionRow(
                        icon = Icons.Outlined.Schedule,
                        label = autoDeleteLabel,
                        onClick = { onAutoDelete(); onDismiss() }
                    )
                }
                if (isSelf && onDelete != null) {
                    SheetActionRow(
                        icon = Icons.Filled.Delete,
                        label = deleteLabel,
                        tint = colorScheme.error,
                        onClick = { onDelete(); onDismiss() }
                    )
                }
                if (!isSelf && onBlock != null) {
                    SheetActionRow(
                        icon = Icons.Outlined.Block,
                        label = blockLabel,
                        tint = colorScheme.error,
                        onClick = { onBlock(); onDismiss() }
                    )
                }
            }
        }
    }
}

/** One row of the actions sheet: icon, label and a comfortable touch target. */
@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val contentTint = tint ?: colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (tint != null) tint.copy(alpha = 0.12f)
                    else colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            fontSize = 15.sp,
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.Medium,
            color = contentTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(0.dp))
    }
}
