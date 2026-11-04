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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roman.zemzeme.R
import com.roman.zemzeme.core.ui.component.sheet.ZemzemeBottomSheet
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import androidx.compose.ui.res.stringResource

/**
 * Single overflow menu for a conversation.
 *
 * Everything that used to be scattered across the header (appearance, mute,
 * auto-delete, selection, search, people, clear) now lives here, so the chat
 * bar stays clean.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatOptionsSheet(
    onDismiss: () -> Unit,
    onAppearance: (() -> Unit)? = null,
    muted: Boolean? = null,
    onToggleMute: (() -> Unit)? = null,
    onAutoDelete: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onPeople: (() -> Unit)? = null,
    onInvite: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme

    ZemzemeBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.chat_options_title),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = NunitoFontFamily,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(6.dp))

            if (onSearch != null) {
                OptionsRow(
                    icon = Icons.Outlined.Search,
                    label = stringResource(R.string.search_messages_title),
                    onClick = { onSearch(); onDismiss() }
                )
            }
            if (onAppearance != null) {
                OptionsRow(
                    icon = Icons.Outlined.Palette,
                    label = stringResource(R.string.cd_chat_appearance),
                    onClick = { onAppearance(); onDismiss() }
                )
            }
            if (onPeople != null) {
                OptionsRow(
                    icon = Icons.Outlined.Groups,
                    label = stringResource(R.string.section_people_nearby),
                    onClick = { onPeople(); onDismiss() }
                )
            }
            if (onInvite != null) {
                OptionsRow(
                    icon = Icons.Outlined.PersonAdd,
                    label = stringResource(R.string.action_invite),
                    onClick = { onInvite(); onDismiss() }
                )
            }
            if (onToggleMute != null) {
                OptionsRow(
                    icon = Icons.Outlined.NotificationsOff,
                    label = if (muted == true) {
                        stringResource(R.string.channel_unmute)
                    } else {
                        stringResource(R.string.channel_mute)
                    },
                    onClick = { onToggleMute(); onDismiss() }
                )
            }
            if (onAutoDelete != null) {
                OptionsRow(
                    icon = Icons.Outlined.AutoDelete,
                    label = stringResource(R.string.action_auto_delete_title),
                    onClick = { onAutoDelete(); onDismiss() }
                )
            }
            if (onSelect != null) {
                OptionsRow(
                    icon = Icons.Outlined.Checklist,
                    label = stringResource(R.string.action_select_multiple_title),
                    onClick = { onSelect(); onDismiss() }
                )
            }
            if (onClear != null) {
                OptionsRow(
                    icon = Icons.Outlined.DeleteSweep,
                    label = stringResource(R.string.action_clear_chat),
                    tint = colorScheme.error,
                    onClick = { onClear(); onDismiss() }
                )
            }
        }
    }
}

@Composable
private fun OptionsRow(
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
            .padding(horizontal = 8.dp, vertical = 11.dp),
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
            color = contentTint
        )
    }
}
