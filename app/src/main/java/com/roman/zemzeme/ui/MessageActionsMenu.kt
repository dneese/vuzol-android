package com.roman.zemzeme.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roman.zemzeme.R
import com.roman.zemzeme.model.ZemzemeMessage

/**
 * Compact message menu.
 *
 * It is shown right above the input bar as a small card with a grid of
 * actions, so the chat stays visible. Long press on any part of a message
 * opens it.
 */
@Composable
fun MessageActionsMenu(
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
    val interactionSource = remember { MutableInteractionSource() }

    val actions = buildList {
        add(MenuAction(Icons.AutoMirrored.Filled.Reply, stringResource(R.string.menu_reply), accent) {
            onReply(); onDismiss()
        })
        if (isSelf && onEdit != null) {
            add(MenuAction(Icons.Outlined.Edit, stringResource(R.string.menu_edit), accent) {
                onEdit(); onDismiss()
            })
        }
        add(MenuAction(Icons.Outlined.ContentCopy, stringResource(R.string.menu_copy), accent) {
            onCopy(); onDismiss()
        })
        if (onShare != null) {
            add(MenuAction(Icons.Filled.Share, stringResource(R.string.menu_share), accent) {
                onShare(); onDismiss()
            })
        }
        if (onForward != null) {
            add(MenuAction(Icons.Outlined.ForwardToInbox, stringResource(R.string.menu_forward), accent) {
                onForward(); onDismiss()
            })
        }
        if (onSave != null) {
            add(MenuAction(Icons.Filled.Bookmark, stringResource(R.string.menu_save), accent) {
                onSave(); onDismiss()
            })
        }
        if (onPin != null) {
            add(MenuAction(Icons.Outlined.PushPin, stringResource(R.string.menu_pin), accent) {
                onPin(); onDismiss()
            })
        }
        if (onSelect != null) {
            add(MenuAction(Icons.Outlined.Checklist, stringResource(R.string.menu_select), accent) {
                onSelect(); onDismiss()
            })
        }
        if (onDetails != null) {
            add(MenuAction(Icons.Filled.Info, stringResource(R.string.menu_details), accent) {
                onDetails(); onDismiss()
            })
        }
        if (onMute != null) {
            add(MenuAction(Icons.Outlined.NotificationsOff, stringResource(R.string.menu_mute), accent) {
                onMute(); onDismiss()
            })
        }
        if (onAutoDelete != null) {
            add(MenuAction(Icons.Outlined.Schedule, stringResource(R.string.menu_auto), accent) {
                onAutoDelete(); onDismiss()
            })
        }
        if (isSelf && onDelete != null) {
            add(MenuAction(Icons.Filled.Delete, stringResource(R.string.menu_delete), colorScheme.error) {
                onDelete(); onDismiss()
            })
        }
        if (!isSelf && onBlock != null) {
            add(MenuAction(Icons.Outlined.Block, stringResource(R.string.menu_block), colorScheme.error) {
                onBlock(); onDismiss()
            })
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onDismiss() }
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 92.dp)
                .widthIn(max = 380.dp),
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                // Who wrote the message — one thin line, so the menu stays small
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileAvatar(
                        pubkey = message.senderPubkey,
                        letter = authorName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = authorColor,
                        size = 24.dp
                    )
                    Text(
                        text = authorName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = authorColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = timeText,
                        fontSize = 11.sp,
                        color = colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick reactions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReactionsStore.SUPPORTED.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colorScheme.surfaceVariant.copy(alpha = 0.55f))
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

                Spacer(modifier = Modifier.height(8.dp))

                // The actions themselves: four small buttons per row
                actions.chunked(4).forEach { rowActions ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowActions.forEach { action ->
                            MenuActionButton(
                                action = action,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(4 - rowActions.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** One action of the compact menu. */
private data class MenuAction(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val onClick: () -> Unit
)

@Composable
private fun MenuActionButton(action: MenuAction, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = action.onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = action.tint,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = action.label,
            fontSize = 10.sp,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
