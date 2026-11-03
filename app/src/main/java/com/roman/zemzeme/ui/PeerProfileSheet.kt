package com.roman.zemzeme.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roman.zemzeme.R
import com.roman.zemzeme.core.ui.component.sheet.ZemzemeBottomSheet
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.nostr.NostrProfileStore

/**
 * Telegram-style peer profile: avatar, name, short bio and quick actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerProfileSheet(
    isPresented: Boolean,
    message: ZemzemeMessage?,
    displayName: String,
    onMention: ((String) -> Unit)? = null,
    onBlock: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (!isPresented || message == null) return

    val profiles by NostrProfileStore.profiles.collectAsState()
    val profile = message.senderPubkey?.let { profiles[it.lowercase()] }
    val colorScheme = MaterialTheme.colorScheme

    ZemzemeBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ProfileAvatar(
                    pubkey = message.senderPubkey,
                    letter = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    profile?.nip05?.let {
                        Text(
                            text = it,
                            fontSize = 13.sp,
                            color = colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    message.senderPeerID?.let {
                        Text(
                            text = it,
                            fontSize = 11.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            profile?.about?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }

            if (onMention != null) {
                Button(
                    onClick = {
                        onMention(displayName)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.action_mention_title))
                }
            }

            if (onBlock != null) {
                Button(
                    onClick = {
                        onBlock()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error.copy(alpha = 0.12f),
                        contentColor = colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.action_block_title, displayName))
                }
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.secondary.copy(alpha = 0.12f),
                    contentColor = colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
            }
        }
    }
}
