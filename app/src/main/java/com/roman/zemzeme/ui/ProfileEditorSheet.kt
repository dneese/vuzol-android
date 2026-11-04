package com.roman.zemzeme.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roman.zemzeme.R
import com.roman.zemzeme.core.ui.component.sheet.ZemzemeBottomSheet
import com.roman.zemzeme.nostr.NostrEvent
import com.roman.zemzeme.nostr.NostrIdentityBridge
import com.roman.zemzeme.nostr.NostrProfile
import com.roman.zemzeme.nostr.NostrProfileStore
import com.roman.zemzeme.nostr.NostrRelayManager
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Profile editor: avatar, nickname, display name and bio.
 *
 * The avatar is stored on the device, the name and bio can be published to
 * Nostr as a NIP-01 metadata event.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorSheet(
    isPresented: Boolean,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isPresented) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val profile by OwnProfile.state.collectAsState()

    var displayName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var about by remember(profile.about) { mutableStateOf(profile.about) }
    var publishing by remember { mutableStateOf(false) }
    var published by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val avatarBitmap = remember(profile.avatarPath) {
        profile.avatarPath?.let { path ->
            runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val path = com.roman.zemzeme.features.media.ImageUtils
                .downscaleAndSaveToAppFiles(context, uri)
            if (!path.isNullOrBlank()) OwnProfile.setAvatar(context, path)
        }
    }

    ZemzemeBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Avatar
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceVariant)
                    .clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap,
                        contentDescription = stringResource(R.string.profile_avatar),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        fontSize = 38.sp,
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = stringResource(R.string.profile_change_avatar),
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.profile_change_avatar),
                fontSize = 12.sp,
                fontFamily = NunitoFontFamily,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                label = { Text(stringResource(R.string.profile_nickname)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.profile_display_name)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = about,
                onValueChange = { about = it },
                label = { Text(stringResource(R.string.profile_about)) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        publishing = true
                        failed = false
                        published = false
                        OwnProfile.setDisplayName(context, displayName)
                        OwnProfile.setAbout(context, about)
                        scope.launch {
                            val ok = publishProfileToNostr(context, displayName, about)
                            publishing = false
                            published = ok
                            failed = !ok
                        }
                    },
                    enabled = !publishing,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (publishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = if (published) Icons.Outlined.Check else Icons.Outlined.Public,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = if (published) {
                                stringResource(R.string.profile_published)
                            } else {
                                stringResource(R.string.profile_publish_nostr)
                            },
                            fontFamily = NunitoFontFamily
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cancel),
                        fontFamily = NunitoFontFamily
                    )
                }
            }

            if (failed) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.profile_publish_failed),
                    fontSize = 12.sp,
                    fontFamily = NunitoFontFamily,
                    color = colorScheme.error
                )
            }
        }
    }
}

/** Publishes the local name and bio as a NIP-01 metadata event. */
private suspend fun publishProfileToNostr(context: android.content.Context, name: String, about: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val identity = NostrIdentityBridge.getCurrentNostrIdentity(context) ?: return@withContext false
            val meta = JSONObject().apply {
                put("name", name.ifBlank { "anon" })
                put("display_name", name)
                put("about", about)
            }
            val event = NostrEvent(
                pubkey = identity.publicKeyHex,
                createdAt = (System.currentTimeMillis() / 1000).toInt(),
                kind = 0,
                tags = emptyList(),
                content = meta.toString()
            )
            val signed = identity.signEvent(event)
            NostrRelayManager.getInstance(context).sendEvent(signed)
            NostrProfileStore.apply(
                identity.publicKeyHex,
                NostrProfile(name = name, displayName = name, about = about)
            )
            true
        } catch (_: Exception) {
            false
        }
    }
