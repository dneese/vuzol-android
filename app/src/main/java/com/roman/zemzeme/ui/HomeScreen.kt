package com.roman.zemzeme.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roman.zemzeme.geohash.ChannelID
import com.roman.zemzeme.geohash.GeohashChannelLevel
import com.roman.zemzeme.geohash.LocationChannelManager
import com.roman.zemzeme.model.ZemzemeMessage
import androidx.compose.ui.res.stringResource
import com.roman.zemzeme.BuildConfig
import com.roman.zemzeme.R
import com.roman.zemzeme.nostr.NostrRelayManager
import com.roman.zemzeme.p2p.P2PConfig
import com.roman.zemzeme.p2p.TopicConnectionState
import com.roman.zemzeme.ui.connectioninfo.ConnectionDisplayData
import com.roman.zemzeme.ui.connectioninfo.ConnectionIndicatorFactory
import com.roman.zemzeme.ui.theme.extendedColors
import java.text.SimpleDateFormat
import java.util.Locale

// ── State holders for long-press flow ──

private data class ActionTarget(
    val type: String,   // "group" | "dm"
    val key: String,
    val displayName: String
)

private enum class PendingAction { DELETE, CLEAR, RENAME }

// ── Bottom navigation tabs ──

private enum class HomeTab { Chats, People, Settings }

private data class PinnedChat(
    val key: String,
    val name: String,
    val color: Color,
    val icon: ImageVector,
    val unread: Int,
    val onClick: () -> Unit
)

// ── HomeScreen ──

@Composable
fun HomeScreen(
    chatViewModel: ChatViewModel,
    onGroupSelected: () -> Unit,
    onSettingsClick: () -> Unit,
    onRefreshAccount: () -> Unit,
    onCityChosen: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    // Observed state
    val messages by chatViewModel.messages.collectAsStateWithLifecycle()
    val customGroups by chatViewModel.customGroups.collectAsStateWithLifecycle()
    val geographicGroups by chatViewModel.geographicGroups.collectAsStateWithLifecycle()
    val groupNicknames by chatViewModel.groupNicknames.collectAsStateWithLifecycle()
    val channelMessages by chatViewModel.channelMessages.collectAsStateWithLifecycle()
    val unreadChannelMessages by chatViewModel.unreadChannelMessages.collectAsStateWithLifecycle()
    val unreadMeshCount by chatViewModel.unreadMeshCount.collectAsStateWithLifecycle()
    val nickname by chatViewModel.nickname.collectAsStateWithLifecycle()
    val myPeerID = chatViewModel.myPeerID
    val contacts by chatViewModel.contacts.collectAsStateWithLifecycle()
    val privateChats by chatViewModel.privateChats.collectAsStateWithLifecycle()
    val unreadPrivateMessages by chatViewModel.unreadPrivateMessages.collectAsStateWithLifecycle()
    val peerNicknames by chatViewModel.peerNicknames.collectAsStateWithLifecycle()
    val p2pTopicStates by chatViewModel.p2pTopicStates.collectAsStateWithLifecycle()
    val geohashParticipantCounts by chatViewModel.geohashParticipantCounts.collectAsStateWithLifecycle()
    val geohashPeople by chatViewModel.geohashPeople.collectAsStateWithLifecycle()
    val connectedPeers by chatViewModel.connectedPeers.collectAsStateWithLifecycle()
    val isConnected by chatViewModel.isConnected.collectAsStateWithLifecycle()

    // Transport state for computing per-group connection status
    val transportConfig = remember { P2PConfig(context) }
    val transportToggles by remember(transportConfig) {
        P2PConfig.transportTogglesFlow
    }.collectAsStateWithLifecycle()
    val nostrRelayManager = remember { NostrRelayManager.getInstance(context) }
    val nostrConnected by nostrRelayManager.isConnected.collectAsStateWithLifecycle()
    val nostrRelays by nostrRelayManager.relays.collectAsStateWithLifecycle()
    val nostrIsUp = transportToggles.nostrEnabled &&
        (nostrConnected || nostrRelays.any { it.isConnected })

    val locationChannelManager = remember {
        try { LocationChannelManager.getInstance(context) } catch (_: Exception) { null }
    }

    // Trigger location resolution so availableChannels populates
    LaunchedEffect(locationChannelManager) {
        locationChannelManager?.enableLocationChannels()
    }

    val locationNames by locationChannelManager?.locationNames?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(emptyMap()) }
    val availableChannels by locationChannelManager?.availableChannels?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(emptyList()) }

    // Bottom navigation + search + pinned conversations
    var selectedTab by remember { mutableStateOf(HomeTab.Chats) }
    var searchQuery by remember { mutableStateOf("") }
    val pinnedKeys by ConversationPinStore.pinned.collectAsState()

    // FAB
    var fabExpanded by remember { mutableStateOf(false) }
    BackHandler(enabled = fabExpanded) { fabExpanded = false }

    // Create / Join / Scan dialogs
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }

    // Long-press action sheet → confirmation flow
    var actionTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var pendingActionTarget by remember { mutableStateOf<ActionTarget?>(null) }

    // Refresh account confirmation dialog
    var showRefreshDialog by remember { mutableStateOf(false) }

    // Saved messages
    var showSavedMessages by remember { mutableStateOf(false) }

    // Mesh intro dialog (first-time only)
    val meshIntroPrefs = remember { context.getSharedPreferences("mesh_intro", android.content.Context.MODE_PRIVATE) }
    var showMeshIntroDialog by remember { mutableStateOf(false) }

    // GeohashPicker launcher
    val geohashPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(GeohashPickerActivity.EXTRA_RESULT_GEOHASH)
                ?.let { onCityChosen(it) }
        }
    }

    // Geographic group
    val geoChannel = availableChannels.firstOrNull { it.level == GeohashChannelLevel.CITY }
        ?: availableChannels.firstOrNull { it.level == GeohashChannelLevel.NEIGHBORHOOD }
    val geoName = locationNames[GeohashChannelLevel.CITY]
        ?: locationNames[GeohashChannelLevel.NEIGHBORHOOD]

    // Geographic groups sorted by last-message time
    val geoGroupItems = remember(geographicGroups, channelMessages) {
        geographicGroups.map { geohash ->
            val lastTs = channelMessages["geo:$geohash"]?.lastOrNull()?.timestamp?.time ?: 0L
            Triple("group", geohash, lastTs)
        }.sortedByDescending { it.third }
    }

    // "My Groups" = custom groups only, sorted by last-message time
    val myGroupItems = remember(customGroups, channelMessages) {
        customGroups.map { geohash ->
            val lastTs = channelMessages["geo:$geohash"]?.lastOrNull()?.timestamp?.time ?: 0L
            Triple("group", geohash, lastTs)
        }.sortedByDescending { it.third }
    }

    // "Contacts" = persisted peers, sorted by last-message time (contacts without messages shown last)
    val contactItems = remember(contacts, privateChats) {
        contacts.map { peerID ->
            val msgs = privateChats[peerID]
            val lastMsg = msgs?.lastOrNull()
            Triple(peerID, lastMsg, lastMsg?.timestamp?.time ?: 0L)
        }.sortedByDescending { it.third }
    }

    // Search filtering for the chat list
    val searchTerm = searchQuery.trim().lowercase()
    val filteredGeoGroupItems = remember(geoGroupItems, groupNicknames, searchTerm) {
        if (searchTerm.isEmpty()) geoGroupItems else geoGroupItems.filter {
            (groupNicknames[it.second] ?: it.second).lowercase().contains(searchTerm)
        }
    }
    val filteredMyGroupItems = remember(myGroupItems, groupNicknames, searchTerm) {
        if (searchTerm.isEmpty()) myGroupItems else myGroupItems.filter {
            (groupNicknames[it.second] ?: it.second).lowercase().contains(searchTerm)
        }
    }
    val filteredContactItems = remember(contactItems, peerNicknames, searchTerm) {
        if (searchTerm.isEmpty()) contactItems else contactItems.filter { (peerID, _, _) ->
            val display = peerNicknames[peerID]
                ?: com.roman.zemzeme.p2p.P2PAliasRegistry.getDisplayName(peerID)
                ?: "User"
            display.lowercase().contains(searchTerm)
        }
    }

    // Pinned conversations (shown in a compact row above the list)
    val pinnedChats = remember(
        pinnedKeys, geoGroupItems, myGroupItems, contactItems,
        groupNicknames, peerNicknames, unreadChannelMessages, unreadPrivateMessages
    ) {
        pinnedKeys.mapNotNull { key ->
            when {
                key.startsWith("dm:") -> {
                    val peerID = key.removePrefix("dm:")
                    val entry = contactItems.firstOrNull { it.first == peerID } ?: return@mapNotNull null
                    val name = peerNicknames[peerID]
                        ?: com.roman.zemzeme.p2p.P2PAliasRegistry.getDisplayName(peerID)
                        ?: "User"
                    PinnedChat(
                        key = key,
                        name = name,
                        color = extended.contactGreen,
                        icon = Icons.Outlined.Person,
                        unread = if (peerID in unreadPrivateMessages) 1 else 0
                    ) {
                        chatViewModel.navigateToContact(peerID)
                        onGroupSelected()
                    }
                }
                key.startsWith("geo:") -> {
                    val geohash = key.removePrefix("geo:")
                    val entry = (geoGroupItems + myGroupItems).firstOrNull { it.second == geohash }
                        ?: return@mapNotNull null
                    val name = groupNicknames[geohash] ?: geohash
                    PinnedChat(
                        key = key,
                        name = name,
                        color = extended.solarOrange,
                        icon = Icons.Outlined.Map,
                        unread = unreadChannelMessages[key] ?: 0
                    ) {
                        chatViewModel.navigateToGeohashGroup(geohash)
                        onGroupSelected()
                    }
                }
                else -> null
            }
        }
    }

    // Helper: compute connection data for a geohash group
    fun geohashConnectionData(geohash: String): ConnectionDisplayData.ChannelConnection {
        val topicState = p2pTopicStates[geohash]
        val p2pPeers = topicState?.peers?.size ?: 0
        val nostrPeers = geohashParticipantCounts[geohash] ?: 0
        val totalPeers = maxOf(p2pPeers, nostrPeers)
        val p2pConnected = transportToggles.p2pEnabled && p2pPeers > 0

        val stage = when {
            p2pConnected || (nostrIsUp && totalPeers > 0) ->
                ConnectionDisplayData.ChannelStage.CONNECTED
            transportToggles.p2pEnabled && (topicState?.providerCount ?: 0) > 0 ->
                ConnectionDisplayData.ChannelStage.CONNECTING
            // Relays are connected: the channel is usable, we are only waiting
            // for other people to show up.
            nostrIsUp ->
                ConnectionDisplayData.ChannelStage.NOSTR_IDLE
            transportToggles.p2pEnabled ->
                ConnectionDisplayData.ChannelStage.SEARCHING
            transportToggles.nostrEnabled ->
                ConnectionDisplayData.ChannelStage.SEARCHING
            else ->
                ConnectionDisplayData.ChannelStage.ERROR
        }

        return ConnectionDisplayData.ChannelConnection(
            stage = stage,
            peerCount = totalPeers,
            error = topicState?.error
        )
    }

    // ── Scaffold ──

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.background)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: title + nickname subtitle (matching LocationChannelsButton layout)
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            Text(
                                "Vuzol",
                                fontSize = 22.sp,
                                fontFamily = NunitoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                "@${nickname.ifEmpty { myPeerID.take(8) }}",
                                fontSize = 12.sp,
                                color = colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Right: refresh + settings
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            IconButton(
                                onClick = { showRefreshDialog = true },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteForever,
                                    contentDescription = stringResource(R.string.cd_refresh_account),
                                    tint = colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.cd_settings),
                                    tint = colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(
                    color = colorScheme.outline.copy(alpha = 0.3f)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.search_chats),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = NunitoFontFamily)
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == HomeTab.Chats,
                    onClick = { selectedTab = HomeTab.Chats },
                    icon = {
                        Box {
                            Icon(Icons.Outlined.Chat, contentDescription = null)
                            val totalUnread = unreadChannelMessages.values.sum() +
                                unreadPrivateMessages.size + unreadMeshCount
                            if (totalUnread > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(18.dp)
                                        .background(colorScheme.error, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (totalUnread > 99) "99+" else totalUnread.toString(),
                                        fontSize = 9.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.nav_chats)) }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.People,
                    onClick = { selectedTab = HomeTab.People },
                    icon = { Icon(Icons.Outlined.People, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_people)) }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.Settings,
                    onClick = { selectedTab = HomeTab.Settings },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(fabExpanded, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FabMenuItem(stringResource(R.string.fab_scan_qr), Icons.Outlined.QrCodeScanner, colorScheme.tertiaryContainer) {
                            fabExpanded = false; showQrScanner = true
                        }
                        val canAdd = customGroups.size + geographicGroups.size < ChatViewModel.MAX_P2P_GROUPS
                        FabMenuItem(stringResource(R.string.fab_choose_city), Icons.Outlined.LocationCity,
                            if (canAdd) colorScheme.tertiaryContainer else colorScheme.surfaceVariant
                        ) {
                            if (canAdd) {
                                fabExpanded = false
                                geohashPickerLauncher.launch(Intent(context, GeohashPickerActivity::class.java))
                            }
                        }
                        FabMenuItem(stringResource(R.string.fab_join_group), Icons.Outlined.GroupAdd,
                            if (canAdd) colorScheme.secondaryContainer else colorScheme.surfaceVariant
                        ) {
                            if (canAdd) { fabExpanded = false; showJoinDialog = true }
                        }
                        FabMenuItem(stringResource(R.string.fab_create_group), Icons.Outlined.Group,
                            if (canAdd) colorScheme.primaryContainer else colorScheme.surfaceVariant
                        ) {
                            if (canAdd) { fabExpanded = false; showCreateDialog = true }
                        }
                    }
                }
                FloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                    Icon(
                        if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = null
                    )
                }
            }
        }
    ) { padding ->
      when (selectedTab) {
        HomeTab.Chats -> LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // ── PINNED ──
            if (pinnedChats.isNotEmpty()) {
                item(key = "pinned_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(pinnedChats, key = { it.key }) { pinned ->
                            Surface(
                                onClick = pinned.onClick,
                                shape = RoundedCornerShape(18.dp),
                                color = colorScheme.surfaceVariant,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        pinned.icon,
                                        contentDescription = null,
                                        tint = pinned.color,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = pinned.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = NunitoFontFamily,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (pinned.unread > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(0xFFFF9500), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── SAVED MESSAGES ──
            item(key = "saved_messages") {
                Surface(
                    onClick = { showSavedMessages = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Bookmark,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            stringResource(R.string.saved_messages_title),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = NunitoFontFamily,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // ── NEARBY ──
            item(key = "nearby_header") { SectionHeader(stringResource(R.string.section_nearby)) }

            item(key = "mesh") {
                val meshPeerCount = connectedPeers.size
                val meshConnectionData = ConnectionDisplayData.ChannelConnection(
                    stage = when {
                        meshPeerCount > 0 -> ConnectionDisplayData.ChannelStage.CONNECTED
                        isConnected -> ConnectionDisplayData.ChannelStage.SEARCHING
                        else -> ConnectionDisplayData.ChannelStage.SEARCHING
                    },
                    peerCount = meshPeerCount
                )
                GroupRow(
                    name = "Bluetooth",
                    lastMessage = messages.lastOrNull(),
                    avatarColor = extended.electricCyan,
                    icon = Icons.Outlined.Hub,
                    myPeerID = myPeerID,
                    myNickname = nickname,
                    unreadCount = unreadMeshCount,
                    connectionData = meshConnectionData,
                    onClick = {
                        if (!meshIntroPrefs.getBoolean("dismissed", false)) {
                            showMeshIntroDialog = true
                        } else {
                            chatViewModel.navigateToMesh(); onGroupSelected()
                        }
                    },
                    onLongPress = { actionTarget = ActionTarget("mesh", "mesh", "Bluetooth") }
                )
            }

            item(key = "geo") {
                val ready = geoChannel != null
                val pulse = rememberInfiniteTransition(label = "geo_pulse")
                val alpha by pulse.animateFloat(
                    1f, 0.3f,
                    infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "geo_alpha"
                )
                val geoDisplayName = if (ready) (geoName ?: geoChannel!!.displayName) else "Locating..."
                val geoLastMsg = if (ready) channelMessages["geo:${geoChannel!!.geohash}"]?.lastOrNull() else null
                GroupRow(
                    name = geoDisplayName,
                    lastMessage = geoLastMsg,
                    avatarColor = extended.electricCyan,
                    icon = Icons.Outlined.LocationOn,
                    myPeerID = myPeerID,
                    myNickname = nickname,
                    unreadCount = if (ready) (unreadChannelMessages["geo:${geoChannel!!.geohash}"] ?: 0) else 0,
                    connectionData = if (ready) geohashConnectionData(geoChannel!!.geohash) else null,
                    rowAlpha = if (ready) 1f else alpha,
                    onClick = {
                        if (ready) {
                            chatViewModel.navigateToLocationChannel(ChannelID.Location(geoChannel!!))
                            onGroupSelected()
                        }
                    },
                    onLongPress = if (ready) {{ actionTarget = ActionTarget("location", geoChannel!!.geohash, geoDisplayName) }} else null
                )
            }

            // ── GEOGRAPHIC GROUPS ──
            if (filteredGeoGroupItems.isNotEmpty()) {
                item(key = "geogroups_header") { SectionHeader(stringResource(R.string.section_geographic_groups)) }

                items(filteredGeoGroupItems, key = { "geo_${it.second}" }) { (_, key, _) ->
                    val nick = groupNicknames[key]
                    val display = nick ?: key
                    val groupLastMsg = channelMessages["geo:$key"]?.lastOrNull()
                    GroupRow(
                        name = display,
                        lastMessage = groupLastMsg,
                        avatarColor = extended.solarOrange,
                        icon = Icons.Outlined.Map,
                        myPeerID = myPeerID,
                        myNickname = nickname,
                        unreadCount = unreadChannelMessages["geo:$key"] ?: 0,
                        connectionData = geohashConnectionData(key),
                        onClick = { chatViewModel.navigateToGeohashGroup(key); onGroupSelected() },
                        onLongPress = { actionTarget = ActionTarget("geographic", key, display) }
                    )
                }
            }

            // ── MY GROUPS ──
            if (filteredMyGroupItems.isNotEmpty()) {
                item(key = "mygroups_header") { SectionHeader(stringResource(R.string.section_my_groups)) }

                items(filteredMyGroupItems, key = { "group_${it.second}" }) { (_, key, _) ->
                    val nick = groupNicknames[key]
                    val display = nick ?: key
                    val groupLastMsg = channelMessages["geo:$key"]?.lastOrNull()
                    GroupRow(
                        name = display,
                        lastMessage = groupLastMsg,
                        avatarColor = extended.neonPurple,
                        myPeerID = myPeerID,
                        myNickname = nickname,
                        unreadCount = unreadChannelMessages["geo:$key"] ?: 0,
                        connectionData = geohashConnectionData(key),
                        onClick = { chatViewModel.navigateToGeohashGroup(key); onGroupSelected() },
                        onLongPress = { actionTarget = ActionTarget("group", key, display) }
                    )
                }
            }

            // ── CONTACTS ──
            if (filteredContactItems.isNotEmpty()) {
                item(key = "contacts_header") { SectionHeader(stringResource(R.string.section_contacts)) }

                items(filteredContactItems, key = { "dm_${it.first}" }) { (peerID, lastMsg, _) ->
                    val displayName = peerNicknames[peerID]
                        ?: com.roman.zemzeme.p2p.P2PAliasRegistry.getDisplayName(peerID)
                        ?: "User"
                    val unread = if (peerID in unreadPrivateMessages) 1 else 0
                    GroupRow(
                        name = displayName,
                        lastMessage = lastMsg,
                        avatarColor = extended.contactGreen,
                        myPeerID = myPeerID,
                        myNickname = nickname,
                        unreadCount = unread,
                        onClick = {
                            chatViewModel.navigateToContact(peerID)
                            onGroupSelected()
                        },
                        onLongPress = { actionTarget = ActionTarget("dm", peerID, displayName) }
                    )
                }
            }

        }
        HomeTab.People -> PeopleTabContent(
            nearby = geohashPeople,
            contacts = filteredContactItems,
            peerNicknames = peerNicknames,
            unreadPrivateMessages = unreadPrivateMessages,
            myPeerID = myPeerID,
            nickname = nickname,
            onOpenChat = { peerID ->
                chatViewModel.navigateToContact(peerID)
                onGroupSelected()
            },
            onLongPress = { peerID, displayName ->
                actionTarget = ActionTarget("dm", peerID, displayName)
            }
        )
        HomeTab.Settings -> SettingsTabContent(
            onAboutClick = onSettingsClick,
            onResetIdentity = { showRefreshDialog = true }
        )
      }
    }

    // ── Action sheet (centered dialog) ──

    actionTarget?.let { target ->
        val pinKey = when (target.type) {
            "dm" -> "dm:${target.key}"
            "mesh" -> "mesh"
            else -> "geo:${target.key}"
        }
        GroupActionSheet(
            groupName = target.displayName,
            isPinned = pinKey in pinnedKeys,
            onPin = {
                ConversationPinStore.toggle(context, pinKey)
                actionTarget = null
            },
            showDelete = target.type != "mesh" && target.type != "location",
            deleteLabel = if (target.type == "dm") stringResource(R.string.dialog_delete_chat) else stringResource(R.string.dialog_delete_group),
            showRename = target.type == "group",
            onRename = {
                pendingActionTarget = target
                actionTarget = null
                pendingAction = PendingAction.RENAME
            },
            onDelete = {
                pendingActionTarget = target
                actionTarget = null
                pendingAction = PendingAction.DELETE
            },
            onClear = {
                pendingActionTarget = target
                actionTarget = null
                pendingAction = PendingAction.CLEAR
            },
            onDismiss = { actionTarget = null }
        )
    }

    // ── Confirmation dialogs ──

    if (pendingAction == PendingAction.DELETE && pendingActionTarget != null) {
        val target = pendingActionTarget!!
        ConfirmationDialog(
            title = if (target.type == "dm") stringResource(R.string.dialog_delete_chat) else stringResource(R.string.dialog_delete_group),
            body = stringResource(R.string.dialog_delete_confirm_body, target.displayName),
            confirmLabel = stringResource(R.string.action_delete),
            confirmColor = MaterialTheme.colorScheme.error,
            onConfirm = {
                when (target.type) {
                    "group" -> chatViewModel.removeGroup(target.key)
                    "geographic" -> chatViewModel.removeGeographicGroup(target.key)
                    "dm" -> chatViewModel.removeContact(target.key)
                }
                pendingAction = null
                pendingActionTarget = null
            },
            onDismiss = { pendingAction = null; pendingActionTarget = null }
        )
    }

    if (pendingAction == PendingAction.CLEAR && pendingActionTarget != null) {
        val target = pendingActionTarget!!
        ConfirmationDialog(
            title = stringResource(R.string.dialog_clear_chat),
            body = stringResource(R.string.dialog_clear_confirm_body, target.displayName),
            confirmLabel = stringResource(R.string.action_clear),
            confirmColor = MaterialTheme.colorScheme.error,
            onConfirm = {
                when (target.type) {
                    "mesh" -> chatViewModel.clearMeshHistory()
                    "location" -> chatViewModel.clearGeohashHistory(target.key)
                    "group" -> chatViewModel.clearGeohashHistory(target.key)
                    "geographic" -> chatViewModel.clearGeohashHistory(target.key)
                    "dm" -> chatViewModel.clearPrivateChatHistory(target.key)
                }
                pendingAction = null
                pendingActionTarget = null
            },
            onDismiss = { pendingAction = null; pendingActionTarget = null }
        )
    }

    if (pendingAction == PendingAction.RENAME && pendingActionTarget != null) {
        val target = pendingActionTarget!!
        RenameGroupDialog(
            currentName = target.displayName,
            onConfirm = { newName ->
                chatViewModel.renameGroup(target.key, newName)
                pendingAction = null
                pendingActionTarget = null
            },
            onDismiss = { pendingAction = null; pendingActionTarget = null }
        )
    }

    // ── Create / Join dialogs ──

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { chatViewModel.createGroup(it); showCreateDialog = false }
        )
    }
    if (showJoinDialog) {
        JoinGroupDialog(
            onDismiss = { showJoinDialog = false },
            onConfirm = { g, n -> chatViewModel.joinGroup(g, n); showJoinDialog = false }
        )
    }

    if (showQrScanner) {
        QrScannerSheet(
            isPresented = showQrScanner,
            onDismiss = { showQrScanner = false },
            viewModel = chatViewModel
        )
    }

    if (showRefreshDialog) {
        AlertDialog(
            onDismissRequest = { showRefreshDialog = false },
            title = { Text(stringResource(R.string.refresh_account_title)) },
            text = { Text(stringResource(R.string.refresh_account_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRefreshDialog = false
                    onRefreshAccount()
                }) {
                    Text(stringResource(R.string.refresh_account_confirm), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRefreshDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Mesh intro dialog
    if (showMeshIntroDialog) {
        var dontShowAgain by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                showMeshIntroDialog = false
                if (dontShowAgain) meshIntroPrefs.edit().putBoolean("dismissed", true).apply()
                chatViewModel.navigateToMesh(); onGroupSelected()
            },
            title = {
                Text(
                    stringResource(R.string.mesh_intro_title),
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.mesh_intro_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dontShowAgain = !dontShowAgain }
                    ) {
                        Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.mesh_intro_dont_show),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showMeshIntroDialog = false
                    if (dontShowAgain) meshIntroPrefs.edit().putBoolean("dismissed", true).apply()
                    chatViewModel.navigateToMesh(); onGroupSelected()
                }) {
                    Text(stringResource(R.string.mesh_intro_close))
                }
            }
        )
    }

    // Saved messages sheet
    SavedMessagesSheet(
        isPresented = showSavedMessages,
        onDismiss = { showSavedMessages = false }
    )

    // About / Settings sheet
    val showAppInfo by chatViewModel.showAppInfo.collectAsStateWithLifecycle()
    var showDebugSheet by remember { mutableStateOf(false) }
    AboutSheet(
        isPresented = showAppInfo,
        onDismiss = { chatViewModel.hideAppInfo() },
        onShowDebug = if (BuildConfig.DEBUG) ({ showDebugSheet = true }) else null,
        nickname = nickname,
        onNicknameChange = { chatViewModel.setNickname(it) }
    )
    if (BuildConfig.DEBUG && showDebugSheet) {
        com.roman.zemzeme.ui.debug.DebugSettingsSheet(
            isPresented = showDebugSheet,
            onDismiss = { showDebugSheet = false },
            meshService = chatViewModel.meshService
        )
    }
}

// ══════════════════════════════════════════════
// Composables
// ══════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = NunitoFontFamily,
            color = MaterialTheme.extendedColors.textTertiary,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)
        ),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun FabMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 2.dp
        ) {
            Text(
                label,
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = NunitoFontFamily)
            )
        }
        SmallFloatingActionButton(onClick = onClick, containerColor = containerColor) {
            Icon(icon, contentDescription = label)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupRow(
    name: String,
    lastMessage: ZemzemeMessage?,
    avatarColor: Color,
    icon: ImageVector? = null,
    myPeerID: String = "",
    myNickname: String = "",
    unreadCount: Int = 0,
    connectionData: ConnectionDisplayData.ChannelConnection? = null,
    rowAlpha: Float = 1f,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val extended = MaterialTheme.extendedColors
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongPress?.invoke() }
            ),
        color = Color.Transparent
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Avatar with unread badge on corner
            Box(modifier = Modifier.size(48.dp)) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(avatarColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = avatarColor,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            name.firstOrNull()?.uppercase() ?: "#",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = NunitoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = avatarColor
                            )
                        )
                    }
                }
                if (unreadCount > 0) {
                    val displayCount = if (unreadCount > 99) "99" else unreadCount.toString()
                    val badgeSize = 20.dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .defaultMinSize(minWidth = badgeSize, minHeight = badgeSize)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayCount,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NunitoFontFamily,
                                lineHeight = 10.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            // Name + connection status + last message
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = NunitoFontFamily, fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (connectionData != null) {
                    ConnectionIndicatorFactory.ChannelIndicator(data = connectionData)
                }
                if (lastMessage != null) {
                    val isMe = (myPeerID.isNotEmpty() && lastMessage.senderPeerID == myPeerID) ||
                        (myNickname.isNotEmpty() && lastMessage.sender == myNickname)
                    val prefix = if (isMe) "You" else {
                        val sender = lastMessage.sender
                        if (sender.startsWith("p2p:")) {
                            lastMessage.senderPeerID?.let { pid ->
                                com.roman.zemzeme.p2p.P2PAliasRegistry.getDisplayName(pid)
                            } ?: "User"
                        } else sender
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$prefix: ${lastMessage.content}",
                            style = MaterialTheme.typography.bodySmall.copy(color = extended.textSecondary),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            timeFormat.format(lastMessage.timestamp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = NunitoFontFamily, color = extended.textTertiary
                            )
                        )
                    }
                }
            }
        }
    }
}

// ── Action sheet dialog (centered, full-width card) ──

@Composable
private fun GroupActionSheet(
    groupName: String,
    showDelete: Boolean = true,
    deleteLabel: String = "Delete group",
    showRename: Boolean = false,
    isPinned: Boolean = false,
    onPin: () -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    groupName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                HorizontalDivider(color = extended.borderSubtle)

                // Pin chat button
                Surface(
                    onClick = onPin,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceVariant
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = if (isPinned) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            stringResource(if (isPinned) R.string.action_unpin else R.string.action_pin),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = NunitoFontFamily,
                                fontWeight = FontWeight.Medium,
                                color = if (isPinned) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // Rename group button
                if (showRename) {
                    Surface(
                        onClick = onRename,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.surfaceVariant
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                stringResource(R.string.dialog_title_rename_group),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = NunitoFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                // Clear chat button
                Surface(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceVariant
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            Icons.Outlined.LayersClear,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            stringResource(R.string.dialog_clear_chat),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = NunitoFontFamily,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // Delete group button
                if (showDelete) {
                    Surface(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.errorContainer.copy(alpha = 0.4f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                deleteLabel,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = NunitoFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.error
                                )
                            )
                        }
                    }
                }

                // Cancel
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.cancel),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = NunitoFontFamily,
                            color = extended.textSecondary
                        )
                    )
                }
            }
        }
    }
}

// ── Confirmation dialog ──

@Composable
private fun ConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirmColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = NunitoFontFamily))
        },
        text = {
            Text(body, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = confirmColor, fontFamily = NunitoFontFamily)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), fontFamily = NunitoFontFamily)
            }
        }
    )
}

// ── Create / Join dialogs ──

@Composable
private fun CreateGroupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var nickname by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_create_group), style = MaterialTheme.typography.titleMedium.copy(fontFamily = NunitoFontFamily)) },
        text = {
            OutlinedTextField(
                value = nickname, onValueChange = { if (it.length <= 15) nickname = it },
                label = { Text(stringResource(R.string.label_group_name)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (nickname.isNotBlank()) onConfirm(nickname.trim()) }, enabled = nickname.isNotBlank()) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun JoinGroupDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var geohash by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_join_group), style = MaterialTheme.typography.titleMedium.copy(fontFamily = NunitoFontFamily)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = geohash,
                    onValueChange = { input ->
                        val filtered = input.lowercase().filter { it in "0123456789bcdefghjkmnpqrstuvwxyz" }
                        geohash = filtered.take(6)
                    },
                    label = { Text(stringResource(R.string.label_group_id)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nickname, onValueChange = { if (it.length <= 15) nickname = it },
                    label = { Text(stringResource(R.string.label_nickname_optional)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (geohash.isNotBlank()) onConfirm(geohash.trim(), nickname.trim().ifEmpty { geohash.trim() }) },
                enabled = geohash.isNotBlank()
            ) { Text(stringResource(R.string.join)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun RenameGroupDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var newName by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_rename_group), style = MaterialTheme.typography.titleMedium.copy(fontFamily = NunitoFontFamily)) },
        text = {
            OutlinedTextField(
                value = newName, onValueChange = { if (it.length <= 15) newName = it },
                label = { Text(stringResource(R.string.label_group_name)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (newName.isNotBlank()) onConfirm(newName.trim()) }, enabled = newName.isNotBlank()) {
                Text(stringResource(R.string.action_rename))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ══════════════════════════════════════════════
// People tab
// ══════════════════════════════════════════════

@Composable
private fun PeopleTabContent(
    nearby: List<GeoPerson>,
    contacts: List<Triple<String, ZemzemeMessage?, Long>>,
    peerNicknames: Map<String, String>,
    unreadPrivateMessages: Set<String>,
    myPeerID: String,
    nickname: String,
    onOpenChat: (String) -> Unit,
    onLongPress: (String, String) -> Unit
) {
    val extended = MaterialTheme.extendedColors
    val colorScheme = MaterialTheme.colorScheme

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (nearby.isNotEmpty()) {
            item(key = "nearby_people_header") {
                SectionHeader(stringResource(R.string.section_people_nearby))
            }
            items(nearby, key = { "nearby_${it.id}" }) { person ->
                NearbyPersonRow(
                    person = person,
                    onClick = { onOpenChat("nostr_${person.id.take(16)}") }
                )
            }
        }

        item(key = "people_header") {
            SectionHeader(stringResource(R.string.section_contacts))
        }

        if (contacts.isEmpty()) {
            item(key = "people_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.People,
                        contentDescription = null,
                        tint = colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        stringResource(R.string.section_people_nearby),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = NunitoFontFamily,
                            color = colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }

        items(contacts, key = { "people_${it.first}" }) { (peerID, lastMsg, _) ->
            val displayName = peerNicknames[peerID]
                ?: com.roman.zemzeme.p2p.P2PAliasRegistry.getDisplayName(peerID)
                ?: "User"
            GroupRow(
                name = displayName,
                lastMessage = lastMsg,
                avatarColor = extended.contactGreen,
                myPeerID = myPeerID,
                myNickname = nickname,
                unreadCount = if (peerID in unreadPrivateMessages) 1 else 0,
                onClick = { onOpenChat(peerID) },
                onLongPress = { onLongPress(peerID, displayName) }
            )
        }
    }
}

// ══════════════════════════════════════════════
// Settings tab
// ══════════════════════════════════════════════

@Composable
private fun NearbyPersonRow(
    person: GeoPerson,
    onClick: () -> Unit
) {
    val extended = MaterialTheme.extendedColors
    val colorScheme = MaterialTheme.colorScheme
    val (baseName, _) = splitSuffix(person.displayName)
    val lastSeen = person.lastSeen.time
    val now = System.currentTimeMillis()
    val online = now - lastSeen < 120_000L
    val minutes = (now - lastSeen) / 60_000L

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProfileAvatar(
                pubkey = person.id,
                letter = baseName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = extended.electricCyan,
                size = 46.dp
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    baseName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        online -> stringResource(R.string.presence_online)
                        minutes < 60 -> stringResource(R.string.presence_minutes_ago, minutes)
                        minutes < 60 * 24 -> stringResource(R.string.presence_hours_ago, minutes / 60)
                        else -> stringResource(R.string.presence_days_ago, minutes / (60 * 24))
                    },
                    fontSize = 12.sp,
                    color = if (online) extended.contactGreen else colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun SettingsTabContent(
    onAboutClick: () -> Unit,
    onResetIdentity: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val themePref by com.roman.zemzeme.ui.theme.ThemePreferenceManager.themeFlow.collectAsState()
    val themePack by com.roman.zemzeme.ui.theme.ThemePackManager.packFlow.collectAsState()
    val layoutMode by ChatDisplaySettings.layoutFlow.collectAsState()
    val textSize by ChatDisplaySettings.textSizeFlow.collectAsState()
    val use24h by ChatDisplaySettings.use24hFlow.collectAsState()
    val blockedWords by ChatDisplaySettings.blockedWordsFlow.collectAsState()
    val blurImages by ChatDisplaySettings.blurImagesFlow.collectAsState()
    val awayState by AwayStatus.state.collectAsState()
    val awayAutoReply = awayState.autoReply
    var showBlockedWordsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item(key = "appearance_header") {
            SectionHeader(stringResource(R.string.settings_section_appearance))
        }

        item(key = "theme_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsChip(
                    label = stringResource(R.string.about_system),
                    selected = themePref.isSystem,
                    onClick = {
                        com.roman.zemzeme.ui.theme.ThemePreferenceManager.set(
                            context,
                            com.roman.zemzeme.ui.theme.ThemePreference.System
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                SettingsChip(
                    label = stringResource(R.string.about_light),
                    selected = themePref.isLight,
                    onClick = {
                        com.roman.zemzeme.ui.theme.ThemePreferenceManager.set(
                            context,
                            com.roman.zemzeme.ui.theme.ThemePreference.Light
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                SettingsChip(
                    label = stringResource(R.string.about_dark),
                    selected = themePref.isDark,
                    onClick = {
                        com.roman.zemzeme.ui.theme.ThemePreferenceManager.set(
                            context,
                            com.roman.zemzeme.ui.theme.ThemePreference.Dark
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                SettingsChip(
                    label = stringResource(R.string.theme_amoled),
                    selected = themePref.isAmoled,
                    onClick = {
                        com.roman.zemzeme.ui.theme.ThemePreferenceManager.set(
                            context,
                            com.roman.zemzeme.ui.theme.ThemePreference.Amoled
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "theme_pack_header") {
            SectionHeader(stringResource(R.string.settings_theme_pack))
        }

        items(
            listOf(
                com.roman.zemzeme.ui.theme.ThemePack.Bitchat,
                com.roman.zemzeme.ui.theme.ThemePack.Matrix,
                com.roman.zemzeme.ui.theme.ThemePack.Amber,
                com.roman.zemzeme.ui.theme.ThemePack.Cyber,
                com.roman.zemzeme.ui.theme.ThemePack.Hacker,
                com.roman.zemzeme.ui.theme.ThemePack.Ghost
            ),
            key = { "theme_pack_" + it.id }
        ) { pack ->
            ThemePackChip(
                pack = pack,
                selected = themePack == pack,
                onClick = {
                    com.roman.zemzeme.ui.theme.ThemePackManager.set(context, pack)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            )
        }

        item(key = "chat_section_header") {
            SectionHeader(stringResource(R.string.settings_section_chat))
        }

        item(key = "layout_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsChip(
                    label = stringResource(R.string.chat_layout_bubbles),
                    selected = layoutMode == ChatDisplaySettings.LAYOUT_BUBBLES,
                    onClick = {
                        ChatDisplaySettings.setLayout(
                            context,
                            ChatDisplaySettings.LAYOUT_BUBBLES
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                SettingsChip(
                    label = stringResource(R.string.chat_layout_compact),
                    selected = layoutMode == ChatDisplaySettings.LAYOUT_COMPACT,
                    onClick = {
                        ChatDisplaySettings.setLayout(
                            context,
                            ChatDisplaySettings.LAYOUT_COMPACT
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "text_size_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(13, 15, 18, 21).forEach { size ->
                    SettingsChip(
                        label = "$size",
                        selected = textSize == size,
                        onClick = { ChatDisplaySettings.setTextSize(context, size) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "clock_row") {
            SettingsSwitchRow(
                icon = Icons.Outlined.Schedule,
                label = stringResource(R.string.settings_time_format),
                checked = use24h,
                onCheckedChange = { ChatDisplaySettings.setUse24h(context, it) }
            )
        }

        item(key = "blocked_words_row") {
            SettingsRow(
                icon = Icons.Outlined.Block,
                label = stringResource(R.string.settings_blocked_words),
                onClick = { showBlockedWordsDialog = true }
            )
        }

        item(key = "blur_images_row") {
            SettingsSwitchRow(
                icon = Icons.Outlined.VisibilityOff,
                label = stringResource(R.string.settings_blur_images),
                checked = blurImages,
                onCheckedChange = { ChatDisplaySettings.setBlurImages(context, it) }
            )
        }

        item(key = "away_auto_reply_row") {
            SettingsSwitchRow(
                icon = Icons.Outlined.DoNotDisturbOn,
                label = stringResource(R.string.away_auto_reply_title),
                checked = awayAutoReply,
                onCheckedChange = { AwayStatus.setAutoReply(context, it) }
            )
        }

        item(key = "about_row") {
            SettingsRow(
                icon = Icons.Outlined.Settings,
                label = stringResource(R.string.action_about_app),
                onClick = onAboutClick
            )
        }

        item(key = "reset_row") {
            SettingsRow(
                icon = Icons.Outlined.DeleteForever,
                label = stringResource(R.string.action_refresh_account_action),
                onClick = onResetIdentity,
                tint = colorScheme.error
            )
        }
    }

    if (showBlockedWordsDialog) {
        var blockedInput by remember { mutableStateOf(blockedWords) }
        AlertDialog(
            onDismissRequest = { showBlockedWordsDialog = false },
            title = { Text(stringResource(R.string.settings_blocked_words)) },
            text = {
                OutlinedTextField(
                    value = blockedInput,
                    onValueChange = { blockedInput = it },
                    placeholder = { Text(stringResource(R.string.blocked_words_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ChatDisplaySettings.setBlockedWords(context, blockedInput)
                        showBlockedWordsDialog = false
                    }
                ) {
                    Text(stringResource(R.string.perm_info_dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockedWordsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ThemePackChip(
    pack: com.roman.zemzeme.ui.theme.ThemePack,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = com.roman.zemzeme.ui.theme.isAppInDarkTheme()
    val packColors = com.roman.zemzeme.ui.theme.ThemePacks.colors(pack, dark = isDark)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(packColors.primary)
            )
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(packColors.secondary)
            )
            Text(
                text = pack.label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = tint
                )
            )
        }
    }
}
