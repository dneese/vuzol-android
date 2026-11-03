package com.roman.zemzeme.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roman.zemzeme.R
import com.roman.zemzeme.core.ui.component.sheet.ZemzemeBottomSheet
import com.roman.zemzeme.model.DeliveryStatus
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.model.ZemzemeMessageType
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Technical details of a single message: id, sender, time, type, delivery
 * status, proof of work and edit state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailsSheet(
    isPresented: Boolean,
    message: ZemzemeMessage?,
    onDismiss: () -> Unit
) {
    if (!isPresented || message == null) return
    val clipboard = LocalClipboardManager.current

    ZemzemeBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.message_details_title),
                fontSize = 20.sp,
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            DetailsRow(
                label = stringResource(R.string.message_details_id),
                value = message.id,
                monospace = true
            )
            DetailsRow(
                label = stringResource(R.string.message_details_sender),
                value = message.sender
            )
            DetailsRow(
                label = stringResource(R.string.message_details_time),
                value = remember(message.timestamp) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(message.timestamp)
                }
            )
            DetailsRow(
                label = stringResource(R.string.message_details_type),
                value = message.type.name.lowercase()
            )
            DetailsRow(
                label = stringResource(R.string.message_details_status),
                value = message.deliveryStatus?.let { describeStatus(it) }
                    ?: stringResource(R.string.message_details_none)
            )
            DetailsRow(
                label = stringResource(R.string.message_details_pow),
                value = message.powDifficulty?.takeIf { it > 0 }?.let { "$it bits" }
                    ?: stringResource(R.string.message_details_none)
            )
            if (message.isEdited) {
                DetailsRow(
                    label = stringResource(R.string.message_details_edited),
                    value = "✓"
                )
            }
            if (!message.senderPubkey.isNullOrBlank()) {
                DetailsRow(
                    label = "pubkey",
                    value = message.senderPubkey,
                    monospace = true
                )
            }

            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(message.id))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.message_details_copy_id))
            }
        }
    }
}

private fun describeStatus(status: DeliveryStatus): String = when (status) {
    is DeliveryStatus.Sending -> "sending"
    is DeliveryStatus.Sent -> "sent"
    is DeliveryStatus.Delivered -> "delivered"
    is DeliveryStatus.Read -> "read"
    is DeliveryStatus.Failed -> "failed: ${status.reason}"
    is DeliveryStatus.PartiallyDelivered -> "${status.reached}/${status.total}"
}

@Composable
private fun DetailsRow(
    label: String,
    value: String,
    monospace: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = if (monospace) FontFamily.Monospace else NunitoFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}
