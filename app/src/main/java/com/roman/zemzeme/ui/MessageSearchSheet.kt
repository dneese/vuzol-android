package com.roman.zemzeme.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roman.zemzeme.R
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.core.ui.component.sheet.ZemzemeBottomSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-conversation message search. Shows matches for the current channel/chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSearchSheet(
    isPresented: Boolean,
    messages: List<ZemzemeMessage>,
    onDismiss: () -> Unit
) {
    if (!isPresented) return

    var query by remember { mutableStateOf("") }
    val timeFormatter = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val results = remember(query, messages) {
        if (query.isBlank()) {
            emptyList()
        } else {
            messages.asReversed()
                .filter { it.sender != "system" && it.content.contains(query, ignoreCase = true) }
                .take(100)
        }
    }

    ZemzemeBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.search_messages_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                placeholder = {
                    Text(androidx.compose.ui.res.stringResource(R.string.search_messages_hint))
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (query.isNotBlank()) {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        R.string.search_messages_count, results.size
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                items(items = results, key = { it.id }) { message ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismiss() }
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = splitSuffix(message.sender).first,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = timeFormatter.format(Date(message.timestamp.time)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Text(
                            text = message.content.replace("\n", " "),
                            fontSize = 14.sp,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                }
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
            }
        }
    }
}
