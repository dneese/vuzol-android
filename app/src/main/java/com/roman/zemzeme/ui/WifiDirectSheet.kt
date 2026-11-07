package com.roman.zemzeme.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.roman.zemzeme.R
import com.roman.zemzeme.wifi.RoomMessage
import com.roman.zemzeme.wifi.RoomProtocol
import com.roman.zemzeme.wifi.WifiDirectTransport
import com.roman.zemzeme.wifi.WifiRoomManager
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline room. Two ways to meet:
 *
 * WiFi Direct works without router and internet, one phone creates the room
 * and relays the messages, so everybody in range hears everybody.
 *
 * The local network mode works with any WiFi router, the phones of the room
 * find each other by themselves and talk over TCP.
 *
 * The room is remembered, so the code and the password are typed only once.
 * The room stays in the chat list until the user leaves it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun WifiDirectSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    myNickname: String?
) {
    if (!isPresented) return

    val context = LocalContext.current
    val manager = WifiRoomManager
    val savedRoom by manager.savedRoom.collectAsStateWithLifecycle()
    val messages by manager.messages.collectAsStateWithLifecycle()
    val status by manager.status.collectAsStateWithLifecycle()
    val peers by manager.peers.collectAsStateWithLifecycle()
    val activeMode by manager.activeMode.collectAsStateWithLifecycle()

    var lanMode by remember { mutableStateOf(WifiRoomManager.savedRoom.value?.mode == WifiRoomManager.Mode.LAN) }
    var roomCode by remember { mutableStateOf(WifiRoomManager.savedCode()) }
    var password by remember { mutableStateOf(WifiRoomManager.savedRoom.value?.password ?: "") }
    var started by remember { mutableStateOf(activeMode != null) }
    var input by remember { mutableStateOf("") }
    var incomingCall by remember { mutableStateOf<RoomProtocol.CallSignal?>(null) }
    var activeCall by remember { mutableStateOf<RoomProtocol.CallSignal?>(null) }
    var callSeconds by remember { mutableStateOf(0) }
    var callTargets by remember { mutableStateOf<List<com.roman.zemzeme.wifi.LanTransport.PeerInfo>>(emptyList()) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val supported = WifiDirectTransport.isSupported

    val nearbyPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }
    val permissionState = rememberPermissionState(nearbyPermission)
    val permissionGranted = permissionState.status.isGranted

    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val micGranted = micPermission.status.isGranted

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            manager.sendFile(uri, myNickname ?: "me")
        }
    }

    LaunchedEffect(isPresented) {
        if (isPresented) {
            val room = manager.savedRoom.value
            if (room != null) {
                roomCode = room.code
                password = room.password
                lanMode = room.mode == WifiRoomManager.Mode.LAN
            }
            if (activeMode == null && room != null) {
                manager.rejoin(context, myNickname ?: "me")
            }
        }
    }

    LaunchedEffect(Unit) {
        manager.callSignals.collect { signal ->
            when (signal.action) {
                "ring" -> incomingCall = signal
                "accept" -> activeCall = signal
                "decline", "end" -> {
                    incomingCall = null
                    activeCall = null
                    callSeconds = 0
                }
                else -> Unit
            }
        }
    }

    // The room can be started from the chat list, so the screen follows it
    LaunchedEffect(activeMode) {
        if (activeMode != null) started = true
    }

    LaunchedEffect(permissionGranted, lanMode) {
        if (!lanMode && !permissionGranted) permissionState.launchPermissionRequest()
    }

    LaunchedEffect(activeCall) {
        if (activeCall == null) {
            callSeconds = 0
            return@LaunchedEffect
        }
        while (activeCall != null) {
            delay(1000)
            callSeconds++
        }
    }

    DisposableEffect(isPresented) {
        onDispose {
            // The room keeps running in the background, only the screen closes
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        Icons.Filled.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = stringResource(
                                if (lanMode) R.string.wifi_direct_mode_lan else R.string.wifi_direct_title
                            ),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (peers > 0) "$status · $peers" else status,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !lanMode,
                        onClick = {
                            if (lanMode) {
                                lanMode = false
                                started = false
                            }
                        },
                        label = { Text(stringResource(R.string.wifi_direct_mode_p2p)) }
                    )
                    FilterChip(
                        selected = lanMode,
                        onClick = {
                            if (!lanMode) {
                                lanMode = true
                                started = false
                            }
                        },
                        label = { Text(stringResource(R.string.wifi_direct_mode_lan)) }
                    )
                }

                if (!started) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (lanMode) R.string.wifi_direct_lan_intro else R.string.wifi_direct_intro
                            ),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!lanMode && !supported) {
                            Text(
                                text = stringResource(R.string.wifi_direct_needs_android9),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        OutlinedTextField(
                            value = roomCode,
                            onValueChange = { roomCode = it },
                            label = { Text(stringResource(R.string.wifi_direct_room_code)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.wifi_direct_room_password)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (lanMode) {
                            Button(
                                onClick = {
                                    manager.join(
                                        context,
                                        roomCode,
                                        password.ifBlank { null },
                                        WifiRoomManager.Mode.LAN,
                                        myNickname ?: "me"
                                    )
                                    started = true
                                },
                                enabled = roomCode.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.wifi_direct_lan_connect))
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        manager.join(
                                            context,
                                            roomCode,
                                            password.ifBlank { null },
                                            WifiRoomManager.Mode.DIRECT,
                                            myNickname ?: "me"
                                        )
                                        started = true
                                    },
                                    enabled = roomCode.isNotBlank() && permissionGranted && supported,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.wifi_direct_host))
                                }
                                OutlinedButton(
                                    onClick = {
                                        manager.join(
                                            context,
                                            roomCode,
                                            password.ifBlank { null },
                                            WifiRoomManager.Mode.DIRECT,
                                            myNickname ?: "me"
                                        )
                                        started = true
                                    },
                                    enabled = roomCode.isNotBlank() && permissionGranted && supported,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.wifi_direct_join))
                                }
                            }
                            if (!permissionGranted) {
                                Text(
                                    text = stringResource(R.string.wifi_direct_permission_needed),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (savedRoom != null) {
                            TextButton(
                                onClick = {
                                    manager.leave(context)
                                    started = false
                                    roomCode = ""
                                    password = ""
                                }
                            ) {
                                Text(stringResource(R.string.wifi_direct_leave_room))
                            }
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(messages.size) {
                        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages) { message ->
                            val mine = message.from == manager.localPeerId
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                            ) {
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = 280.dp)
                                        .background(
                                            if (mine) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable(enabled = message.filePath != null) {
                                            message.filePath?.let { openRoomFile(context, it) }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    if (!mine) {
                                        Text(
                                            text = message.nickname,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = message.text,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (message.fileName != null) {
                                        Text(
                                            text = stringResource(R.string.wifi_direct_lan_open_file),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = timeFormatter.format(Date(message.timestamp)),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    activeCall?.let { call ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = call.nickname,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = String.format(
                                        Locale.getDefault(),
                                        "%02d:%02d",
                                        callSeconds / 60,
                                        callSeconds % 60
                                    ),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(onClick = {
                                activeCall = null
                                callSeconds = 0
                                manager.endCall()
                            }) {
                                Icon(
                                    Icons.Filled.CallEnd,
                                    contentDescription = stringResource(R.string.wifi_direct_lan_call_end),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        if (lanMode) {
                            IconButton(
                                onClick = {
                                    filePicker.launch(arrayOf("*/*"))
                                },
                                enabled = peers > 0
                            ) {
                                Icon(
                                    Icons.Filled.AttachFile,
                                    contentDescription = stringResource(R.string.wifi_direct_lan_attach),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (!micGranted) {
                                        micPermission.launchPermissionRequest()
                                        return@IconButton
                                    }
                                    val targets = manager.peersForCall()
                                    if (targets.isEmpty()) return@IconButton
                                    if (targets.size == 1) {
                                        manager.startCall(targets.first())
                                    } else {
                                        callTargets = targets
                                    }
                                },
                                enabled = peers > 0 && activeCall == null
                            ) {
                                Icon(
                                    Icons.Filled.Call,
                                    contentDescription = stringResource(R.string.wifi_direct_lan_call),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text(stringResource(R.string.wifi_direct_message_hint)) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            maxLines = 4
                        )
                        IconButton(
                            onClick = {
                                val text = input
                                input = ""
                                manager.sendText(text, myNickname ?: "me")
                            },
                            enabled = input.isNotBlank()
                        ) {
                            Icon(
                                Icons.Filled.Send,
                                contentDescription = stringResource(R.string.wifi_direct_send),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    incomingCall?.let { call ->
        AlertDialog(
            onDismissRequest = {
                incomingCall = null
                manager.declineCall(call.callId)
            },
            title = { Text(call.nickname) },
            text = { Text(stringResource(R.string.wifi_direct_lan_incoming_call)) },
            confirmButton = {
                TextButton(onClick = {
                    incomingCall = null
                    if (!micGranted) {
                        micPermission.launchPermissionRequest()
                    } else {
                        manager.acceptCall(call)
                        activeCall = call
                    }
                }) {
                    Text(stringResource(R.string.wifi_direct_lan_call_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    incomingCall = null
                    manager.declineCall(call.callId)
                }) {
                    Text(stringResource(R.string.wifi_direct_lan_call_decline))
                }
            }
        )
    }

    if (callTargets.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { callTargets = emptyList() },
            title = { Text(stringResource(R.string.wifi_direct_lan_peers_title)) },
            text = {
                Column {
                    callTargets.forEach { peer ->
                        TextButton(onClick = {
                            callTargets = emptyList()
                            manager.startCall(peer)
                        }) {
                            Text(peer.nickname, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { callTargets = emptyList() }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

/** Opens a file that was received in an offline room. */
private fun openRoomFile(context: Context, path: String) {
    try {
        val file = File(path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
