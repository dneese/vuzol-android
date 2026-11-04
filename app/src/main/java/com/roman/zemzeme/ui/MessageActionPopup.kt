package com.roman.zemzeme.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import com.roman.zemzeme.R
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.model.ZemzemeMessageType
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import androidx.compose.ui.res.stringResource

/**
 * Positions the quick action popup next to the touched message: below it when
 * there is room, otherwise above, always clamped inside the window.
 */
class MessagePopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val margin = 8
        val spaceBelow = windowSize.height - anchorBounds.bottom
        val fitsBelow = spaceBelow >= popupContentSize.height + margin
        val fitsAbove = anchorBounds.top >= popupContentSize.height + margin
        val y = when {
            fitsBelow -> anchorBounds.bottom + margin
            fitsAbove -> anchorBounds.top - popupContentSize.height - margin
            else -> (windowSize.height - popupContentSize.height).coerceAtLeast(margin)
        }
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(margin, maxX)
        return IntOffset(x, y)
    }
}

/** One entry of the quick action grid. */
private data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color? = null,
    val onClick: () -> Unit
)

/**
 * Floating quick-action menu shown when a message is long-pressed.
 * It offers the most common operations (reply, reactions, copy, forward,
 * save, edit, delete, details) without leaving the conversation.
 */
@Composable
fun MessageActionPopup(
    message: ZemzemeMessage,
    authorName: String,
    authorColor: Color,
    timeText: String,
    isSelf: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onForward: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onDetails: (() -> Unit)? = null,
    onBlock: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = LocalChatAccent.current

    val replyLabel = stringResource(R.string.action_reply_title)
    val copyLabel = stringResource(R.string.action_copy_message_title)
    val forwardLabel = stringResource(R.string.action_forward_title)
    val saveLabel = stringResource(R.string.action_save_message_title)
    val pinLabel = stringResource(R.string.action_pin_title)
    val editLabel = stringResource(R.string.action_edit_title)
    val selectLabel = stringResource(R.string.action_select_multiple_title)
    val detailsLabel = stringResource(R.string.message_details_title)
    val blockLabel = stringResource(R.string.action_block_title_short)
    val deleteLabel = stringResource(R.string.action_delete_title)
    val moreLabel = stringResource(R.string.action_more_actions)

    val actions = buildList {
        add(
            QuickAction(
                label = replyLabel,
                icon = Icons.AutoMirrored.Filled.Reply,
                tint = accent,
                onClick = onReply
            )
        )
        add(
            QuickAction(
                label = copyLabel,
                icon = Icons.Outlined.ContentCopy,
                onClick = onCopy
            )
        )
        onForward?.let {
            add(
                QuickAction(
                    label = forwardLabel,
                    icon = Icons.AutoMirrored.Filled.Forward,
                    onClick = it
                )
            )
        }
        onSave?.let {
            add(
                QuickAction(
                    label = saveLabel,
                    icon = Icons.Filled.Bookmark,
                    onClick = it
                )
            )
        }
        onPin?.let {
            add(
                QuickAction(
                    label = pinLabel,
                    icon = Icons.Outlined.PushPin,
                    onClick = it
                )
            )
        }
        onEdit?.let {
            add(
                QuickAction(
                    label = editLabel,
                    icon = Icons.Outlined.Edit,
                    onClick = it
                )
            )
        }
        onSelect?.let {
            add(
                QuickAction(
                    label = selectLabel,
                    icon = Icons.Outlined.Checklist,
                    onClick = it
                )
            )
        }
        onDetails?.let {
            add(
                QuickAction(
                    label = detailsLabel,
                    icon = Icons.Filled.Info,
                    onClick = it
                )
            )
        }
        if (!isSelf) {
            onBlock?.let {
                add(
                    QuickAction(
                        label = blockLabel,
                        icon = Icons.Outlined.Block,
                        tint = colorScheme.error,
                        onClick = it
                    )
                )
            }
        }
        onDelete?.let {
            add(
                QuickAction(
                    label = deleteLabel,
                    icon = Icons.Filled.Delete,
                    tint = colorScheme.error,
                    onClick = it
                )
            )
        }
        onMore?.let {
            add(
                QuickAction(
                    label = moreLabel,
                    icon = Icons.Outlined.MoreHoriz,
                    onClick = it
                )
            )
        }
    }

    // Every action closes the popup once it has been triggered
    val dismissActions = actions.map { action ->
        action.copy(onClick = {
            action.onClick()
            onDismiss()
        })
    }

    Surface(
        modifier = Modifier.width(268.dp),
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: author, time and a short preview of the message
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileAvatar(
                    pubkey = message.senderPubkey,
                    letter = authorName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = authorColor,
                    size = 30.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = authorName,
                        fontSize = 13.sp,
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = authorColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timeText,
                        fontSize = 10.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }

            val preview = message.content.trim()
            if (preview.isNotEmpty() && message.type == ZemzemeMessageType.Message) {
                Text(
                    text = preview,
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            // Quick reactions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ReactionsStore.SUPPORTED.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable {
                                onReact(emoji)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 17.sp)
                    }
                }
            }

            // Action grid
            dismissActions.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    row.forEach { action ->
                        QuickActionButton(
                            action = action,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    action: QuickAction,
    modifier: Modifier = Modifier
) {
    val tint = action.tint ?: MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { action.onClick() }
            .padding(vertical = 7.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = action.label,
            fontSize = 9.sp,
            fontFamily = NunitoFontFamily,
            color = tint.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
