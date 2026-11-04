package com.roman.zemzeme.ui

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roman.zemzeme.R
import com.roman.zemzeme.core.ui.component.sheet.ZemzemeBottomSheet
import com.roman.zemzeme.ui.theme.NunitoFontFamily

/**
 * Invite sheet for a private group: short code, deep link and a QR code the
 * invitee can scan straight away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInviteSheet(
    isPresented: Boolean,
    groupName: String,
    groupCode: String,
    onDismiss: () -> Unit
) {
    if (!isPresented) return

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val colorScheme = MaterialTheme.colorScheme
    val deepLink = GroupInvite.linkFor(groupCode)
    val webLink = GroupInvite.webLinkFor(groupCode)
    val shareText = GroupInvite.shareText(groupName, groupCode)

    ZemzemeBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.group_invite_title),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = groupName,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = NunitoFontFamily,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(18.dp))

            // QR code with the invite link
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(208.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    QRCodeImage(
                        data = webLink,
                        size = 196.dp,
                        dotColor = androidx.compose.ui.graphics.Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Short code
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.group_invite_code_label),
                            fontSize = 11.sp,
                            fontFamily = NunitoFontFamily,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = groupCode,
                            fontSize = 24.sp,
                            fontFamily = NunitoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            letterSpacing = 4.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.group_invite_copy_code),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { clipboard.setText(AnnotatedString(groupCode)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Web link: works in any browser and leads to the app
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.group_invite_link_label),
                            fontSize = 11.sp,
                            fontFamily = NunitoFontFamily,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = webLink,
                            fontSize = 12.sp,
                            fontFamily = NunitoFontFamily,
                            color = colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.group_invite_copy_link),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { clipboard.setText(AnnotatedString(webLink)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLink))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val downloadIntent = Intent(
                                Intent.ACTION_VIEW,
                                android.net.Uri.parse(GroupInvite.WEB_BASE)
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try { context.startActivity(downloadIntent) } catch (_: Exception) { }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.group_invite_open_app),
                        fontFamily = NunitoFontFamily,
                        fontSize = 13.sp
                    )
                }
                Button(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        try {
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        } catch (_: Exception) { }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.group_invite_share),
                        fontFamily = NunitoFontFamily,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.group_invite_hint),
                fontSize = 12.sp,
                fontFamily = NunitoFontFamily,
                color = colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
