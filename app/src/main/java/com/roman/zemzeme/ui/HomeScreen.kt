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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.roman.zemzeme.wifi.CallHistoryStore
import com.roman.zemzeme.ui.connectioninfo.ConnectionDisplayData
import com.roman.zemzeme.ui.connectioninfo.ConnectionIndicatorFactory
import com.roman.zemzeme.ui.theme.extendedColors
import kotlinx.coroutines.launch
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

private enum class HomeTab { Chats, People, Calls, Settings }

/** The tabs of the chat list, like the folders of Telegram. */
private enum class ChatFilter { ALL, UNREAD, GROUPS, PEOPLE }

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
    // The offline room is remembered, so it stays in the chat list
    val wifiRoom by com.roman.zemzeme.wifi.WifiRoomManager.savedRoom.collectAsStateWithLifecycle()
    val wifiRoomMessages by com.roman.zemzeme.wifi.WifiRoomManager.messages.collectAsStateWithLifecycle()
    val wifiRoomActive by com.roman.zemzeme.wifi.WifiRoomManager.activeMode.collectAsStateWithLifecycle()
    val wifiRoomPeers by com.roman.zemzeme.wifi.WifiRoomManager.peers.collectAsStateWithLifecycle()
    val activeChannels by chatViewModel.activeChannels.collectAsStateWithLifecycle()
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
    var chatFilter by remember { mutableStateOf(ChatFilter.ALL) }
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

    // Profile editor (avatar, name, bio)
    var showProfileEditor by remember { mutableStateOf(false) }

    // Group invite sheet
    var inviteTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var pendingActionTarget by remember { mutableStateOf<ActionTarget?>(null) }

    // Refresh account confirmation dialog
    var showRefreshDialog by remember { mutableStateOf(false) }

    // Saved messages
    var showSavedMessages by remember { mutableStateOf(false) }
    var showWifiDirect by remember { mutableStateOf(false) }

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

    // "My Groups" = joined groups and the channels where the user wrote,
    // sorted by last-message time
    val myGroupItems = remember(customGroups, activeChannels, geographicGroups, channelMessages) {
        val keys = (customGroups + activeChannels) - geographicGroups
        keys.map { geohash ->
            val lastTs = channelMessages["geo:$geohash"]?.lastOrNull()?.timestamp?.time ?: 0L
            Triple("group", geohash, lastTs)
        }.sortedByDescending { it.third }
    }

    // "People" = saved contacts and every private chat, so no conversation
    // disappears from the list; sorted by last-message time
    val contactItems = remember(contacts, privateChats) {
        val keys = contacts + privateChats.keys
        keys.map { peerID ->
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
            val display = PeerNameResolver.resolve(
                peerId = peerID,
                knownNickname = peerNicknames[peerID]
            ) ?: "User"
            display.lowercase().contains(searchTerm)
        }
    }

    // The folders of Telegram: all, unread, groups and people
    val unreadOnly = chatFilter == ChatFilter.UNREAD
    val showGroups = chatFilter != ChatFilter.PEOPLE
    val showContacts = chatFilter != ChatFilter.GROUPS
    val showSpecial = chatFilter == ChatFilter.ALL
    val visibleGeoGroupItems = remember(filteredGeoGroupItems, unreadChannelMessages, unreadOnly) {
        if (!unreadOnly) filteredGeoGroupItems
        else filteredGeoGroupItems.filter { (unreadChannelMessages["geo:${it.second}"] ?: 0) > 0 }
    }
    val visibleMyGroupItems = remember(filteredMyGroupItems, unreadChannelMessages, unreadOnly) {
        if (!unreadOnly) filteredMyGroupItems
        else filteredMyGroupItems.filter { (unreadChannelMessages["geo:${it.second}"] ?: 0) > 0 }
    }
    val visibleContactItems = remember(filteredContactItems, unreadPrivateMessages, unreadOnly) {
        if (!unreadOnly) filteredContactItems
        else filteredContactItems.filter { (peerID, _, _) -> peerID in unreadPrivateMessages }
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
                    val name = PeerNameResolver.resolve(
                        peerId = peerID,
                        knownNickname = peerNicknames[peerID]
                    ) ?: "User"
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

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colorScheme.surface,
                drawerContentColor = colorScheme.onSurface
            ) {
                MainDrawerContent(
                    nickname = nickname,
                    myPeerID = myPeerID,
                    onProfile = {
                        scope.launch { drawerState.close() }
                        showProfileEditor = true
                    },
                    onChats = {
                        scope.launch { drawerState.close() }
                        selectedTab = HomeTab.Chats
                    },
                    onPeople = {
                        scope.launch { drawerState.close() }
                        selectedTab = HomeTab.People
                    },
                    onCalls = {
                        scope.launch { drawerState.close() }
                        selectedTab = HomeTab.Calls
                    },
                    onNewGroup = {
                        scope.launch { drawerState.close() }
                        showCreateDialog = true
                    },
                    onSavedMessages = {
                        scope.launch { drawerState.close() }
                        showSavedMessages = true
                    },
                    onWifiRoom = {
                        scope.launch { drawerState.close() }
                        showWifiDirect = true
                    },
                    onSettings = {
                        scope.launch { drawerState.close() }
                        onSettingsClick()
                    }
                )
            }
        }
    ) {
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
                            // Left: the menu of Telegram + avatar + title
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Menu,
                                    contentDescription = stringResource(R.string.cd_open_menu),
                                    tint = colorScheme.onSurface.copy(alpha = 0.8f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable { showProfileEditor = true },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OwnProfileAvatar(nickname = nickname, size = 38.dp)
                                Column {
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = chatFilter == ChatFilter.ALL,
                            onClick = { chatFilter = ChatFilter.ALL },
                            label = { Text(stringResource(R.string.chat_filter_all)) }
                        )
                        FilterChip(
                            selected = chatFilter == ChatFilter.UNREAD,
                            onClick = { chatFilter = ChatFilter.UNREAD },
                            label = { Text(stringResource(R.string.chat_filter_unread)) }
                        )
                        FilterChip(
                            selected = chatFilter == ChatFilter.GROUPS,
                            onClick = { chatFilter = ChatFilter.GROUPS },
                            label = { Text(stringResource(R.string.chat_filter_groups)) }
                        )
                        FilterChip(
                            selected = chatFilter == ChatFilter.PEOPLE,
                            onClick = { chatFilter = ChatFilter.PEOPLE },
                            label = { Text(stringResource(R.string.chat_filter_people)) }
                        )
                    }
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
                        selected = selectedTab == HomeTab.Calls,
                        onClick = { selectedTab = HomeTab.Calls },
                        icon = { Icon(Icons.Outlined.Call, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_calls)) }
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
                            FabMenuItem(stringResource(R.string.wifi_direct_open), Icons.Outlined.Wifi,
                                colorScheme.tertiaryContainer
                            ) {
                                fabExpanded = false; showWifiDirect = true
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
                if (pinnedChats.isNotEmpty() && showSpecial) {
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
                if (showSpecial || showContacts) {
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
                }

                // ── NEARBY ──
                if (showSpecial) {
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
                }

                // ── OFFLINE ROOM (WiFi Direct or local network) ──
                val room = wifiRoom
                if (room != null && showGroups) {
                    item(key = "wifi_room") {
                        val roomName = stringResource(R.string.wifi_direct_open) + " · " + room.code
                        val roomLastMsg = wifiRoomMessages.lastOrNull()
                        GroupRow(
                            name = roomName,
                            lastMessage = null,
                            previewText = roomLastMsg?.let { msg ->
                                if (msg.text.isNotBlank()) msg.text else msg.fileName ?: ""
                            },
                            previewTimeMs = roomLastMsg?.timestamp,
                            avatarColor = extended.electricCyan,
                            icon = Icons.Outlined.Wifi,
                            myPeerID = myPeerID,
                            myNickname = nickname,
                            unreadCount = 0,
                            connectionData = com.roman.zemzeme.ui.connectioninfo.ConnectionDisplayData.ChannelConnection(
                                stage = if (wifiRoomActive != null) {
                                    com.roman.zemzeme.ui.connectioninfo.ConnectionDisplayData.ChannelStage.CONNECTED
                                } else {
                                    com.roman.zemzeme.ui.connectioninfo.ConnectionDisplayData.ChannelStage.SEARCHING
                                },
                                peerCount = wifiRoomPeers
                            ),
                            onClick = { showWifiDirect = true },
                            onLongPress = { actionTarget = ActionTarget("wifi", room.code, roomName) }
                        )
                    }
                }

                // ── GEOGRAPHIC GROUPS ──
                if (showGroups && visibleGeoGroupItems.isNotEmpty()) {
                    item(key = "geogroups_header") { SectionHeader(stringResource(R.string.section_geographic_groups)) }

                    items(visibleGeoGroupItems, key = { "geo_${it.second}" }) { (_, key, _) ->
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
                if (showGroups && visibleMyGroupItems.isNotEmpty()) {
                    item(key = "mygroups_header") { SectionHeader(stringResource(R.string.section_my_groups)) }

                    items(visibleMyGroupItems, key = { "group_${it.second}" }) { (_, key, _) ->
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
                if (showContacts && visibleContactItems.isNotEmpty()) {
                    item(key = "contacts_header") { SectionHeader(stringResource(R.string.section_contacts)) }

                    items(visibleContactItems, key = { "dm_${it.first}" }) { (peerID, lastMsg, _) ->
                        val displayName = PeerNameResolver.resolve(
                            peerId = peerID,
                            knownNickname = peerNicknames[peerID],
                            lastMessageSender = lastMsg?.sender
                        ) ?: "User"
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
            HomeTab.Calls -> CallsTabContent()
            HomeTab.Settings -> SettingsTabContent(
                onAboutClick = onSettingsClick,
                onResetIdentity = { showRefreshDialog = true },
                nickname = nickname,
                onNicknameChange = { chatViewModel.setNickname(it) },
                onOpenProfile = { showProfileEditor = true }
            )
          }
    }
    }

    inviteTarget?.let { target ->
        GroupInviteSheet(
            isPresented = true,
            groupName = target.displayName,
            groupCode = target.key,
            onDismiss = { inviteTarget = null }
        )
    }

    ProfileEditorSheet(
        isPresented = showProfileEditor,
        nickname = nickname,
        onNicknameChange = { chatViewModel.setNickname(it) },
        onDismiss = { showProfileEditor = false }
    )

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
            deleteLabel = when (target.type) {
                "dm" -> stringResource(R.string.dialog_delete_chat)
                "wifi" -> stringResource(R.string.wifi_direct_leave_room)
                else -> stringResource(R.string.dialog_delete_group)
            },
            showRename = target.type == "group",
            onInvite = if (target.type == "group") {{
                inviteTarget = target
                actionTarget = null
            }} else null,
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
            title = when (target.type) {
                "dm" -> stringResource(R.string.dialog_delete_chat)
                "wifi" -> stringResource(R.string.wifi_direct_leave_room)
                else -> stringResource(R.string.dialog_delete_group)
            },
            body = stringResource(R.string.dialog_delete_confirm_body, target.displayName),
            confirmLabel = stringResource(R.string.action_delete),
            confirmColor = MaterialTheme.colorScheme.error,
            onConfirm = {
                when (target.type) {
                    "group" -> chatViewModel.removeGroup(target.key)
                    "geographic" -> chatViewModel.removeGeographicGroup(target.key)
                    "dm" -> chatViewModel.removeContact(target.key)
                    "wifi" -> com.roman.zemzeme.wifi.WifiRoomManager.leave(context)
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
            onConfirm = { name, password ->
                chatViewModel.createGroup(name, password)
                showCreateDialog = false
            }
        )
    }
    if (showJoinDialog) {
        JoinGroupDialog(
            onDismiss = { showJoinDialog = false },
            onConfirm = { g, n, password ->
                chatViewModel.joinGroup(g, n, password)
                showJoinDialog = false
            }
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
        onDismiss = { showSavedMessages = false },
        myNickname = nickname
    )

    // Offline room over WiFi Direct
    WifiDirectSheet(
        isPresented = showWifiDirect,
        onDismiss = { showWifiDirect = false },
        myNickname = nickname
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
    previewText: String? = null,
    previewTimeMs: Long? = null,
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
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // The round avatar of Telegram
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Text(
                        name.firstOrNull()?.uppercase() ?: "#",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = NunitoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            // Name with the time on the right, then the last message
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = NunitoFontFamily, fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val rowTime = lastMessage?.timestamp ?: previewTimeMs
                    if (rowTime != null) {
                        Text(
                            timeFormat.format(rowTime),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = NunitoFontFamily, color = extended.textTertiary
                            )
                        )
                    }
                }
                if (connectionData != null) {
                    ConnectionIndicatorFactory.ChannelIndicator(data = connectionData)
                }
                if (lastMessage == null && previewText != null) {
                    Text(
                        previewText,
                        style = MaterialTheme.typography.bodySmall.copy(color = extended.textSecondary),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else if (lastMessage != null) {
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isMe) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = extended.textTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            "$prefix: ${lastMessage.content}",
                            style = MaterialTheme.typography.bodySmall.copy(color = extended.textSecondary),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (unreadCount > 0) {
                            val displayCount = if (unreadCount > 99) "99+" else unreadCount.toString()
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayCount,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = NunitoFontFamily,
                                        lineHeight = 12.sp
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NunitoFontFamily,
                                lineHeight = 12.sp
                            ),
                            color = Color.White
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
    onInvite: (() -> Unit)? = null,
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

                // Invite friends button
                if (onInvite != null) {
                    Surface(
                        onClick = onInvite,
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
                                Icons.Outlined.PersonAdd,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                stringResource(R.string.action_invite),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = NunitoFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurfaceVariant
                                )
                            )
                        }
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
private fun CreateGroupDialog(onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_create_group), style = MaterialTheme.typography.titleMedium.copy(fontFamily = NunitoFontFamily)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nickname, onValueChange = { if (it.length <= 15) nickname = it },
                    label = { Text(stringResource(R.string.label_group_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { if (it.length <= 64) password = it },
                    label = { Text(stringResource(R.string.label_group_password)) },
                    placeholder = { Text(stringResource(R.string.label_group_password_hint)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (nickname.isNotBlank()) onConfirm(nickname.trim(), password.trim().ifBlank { null }) },
                enabled = nickname.isNotBlank()
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun JoinGroupDialog(onDismiss: () -> Unit, onConfirm: (String, String, String?) -> Unit) {
    var geohash by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_join_group), style = MaterialTheme.typography.titleMedium.copy(fontFamily = NunitoFontFamily)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = geohash,
                    onValueChange = { input ->
                        // Accept a pasted invite link or a plain code
                        val parsed = GroupInvite.codeFrom(input)
                        if (parsed != null && input.contains("://")) {
                            geohash = parsed
                            // Invite links can carry the group password
                            GroupInvite.passwordFrom(input)?.let { password = it }
                        } else {
                            val filtered = input.lowercase().filter { it in "0123456789bcdefghjkmnpqrstuvwxyz" }
                            geohash = filtered.take(6)
                        }
                    },
                    label = { Text(stringResource(R.string.label_group_id)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nickname, onValueChange = { if (it.length <= 15) nickname = it },
                    label = { Text(stringResource(R.string.label_nickname_optional)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { if (it.length <= 64) password = it },
                    label = { Text(stringResource(R.string.label_group_password_join)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (geohash.isNotBlank()) {
                        onConfirm(
                            geohash.trim(),
                            nickname.trim().ifEmpty { geohash.trim() },
                            password.trim().ifBlank { null }
                        )
                    }
                },
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
// The menu of Telegram
// ══════════════════════════════════════════════

@Composable
private fun MainDrawerContent(
    nickname: String,
    myPeerID: String,
    onProfile: () -> Unit,
    onChats: () -> Unit,
    onPeople: () -> Unit,
    onCalls: () -> Unit,
    onNewGroup: () -> Unit,
    onSavedMessages: () -> Unit,
    onWifiRoom: () -> Unit,
    onSettings: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onProfile)
                .padding(horizontal = 4.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OwnProfileAvatar(nickname = nickname, size = 48.dp)
            Column {
                Text(
                    nickname.ifEmpty { "Vuzol" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "@${nickname.ifEmpty { myPeerID.take(8) }}",
                    fontSize = 13.sp,
                    color = extended.textSecondary
                )
            }
        }

        HorizontalDivider(color = extended.borderSubtle)

        DrawerItem(Icons.Outlined.Chat, stringResource(R.string.nav_chats), onChats)
        DrawerItem(Icons.Outlined.People, stringResource(R.string.nav_people), onPeople)
        DrawerItem(Icons.Outlined.Call, stringResource(R.string.nav_calls), onCalls)
        DrawerItem(Icons.Outlined.GroupAdd, stringResource(R.string.fab_create_group), onNewGroup)
        DrawerItem(Icons.Outlined.Bookmark, stringResource(R.string.saved_messages_title), onSavedMessages)
        DrawerItem(Icons.Outlined.Wifi, stringResource(R.string.wifi_direct_open), onWifiRoom)

        HorizontalDivider(color = extended.borderSubtle)

        DrawerItem(Icons.Outlined.Settings, stringResource(R.string.nav_settings), onSettings)

        Spacer(modifier = Modifier.weight(1f))

        Text(
            "Vuzol ${BuildConfig.VERSION_NAME}",
            fontSize = 12.sp,
            color = extended.textTertiary,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ══════════════════════════════════════════════
// Calls tab
// ══════════════════════════════════════════════

@Composable
private fun CallsTabContent() {
    val entries by CallHistoryStore.entries.collectAsStateWithLifecycle()
    val extended = MaterialTheme.extendedColors
    val colorScheme = MaterialTheme.colorScheme
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yy", Locale.getDefault()) }

    if (entries.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.Call,
                contentDescription = null,
                tint = extended.textTertiary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                stringResource(R.string.calls_empty),
                fontSize = 14.sp,
                color = extended.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(entries, key = { it.id }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (entry.missed) colorScheme.error.copy(alpha = 0.15f)
                            else colorScheme.primary.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            entry.missed -> Icons.Outlined.CallMissed
                            entry.outgoing -> Icons.Outlined.CallMade
                            else -> Icons.Outlined.CallReceived
                        },
                        contentDescription = null,
                        tint = if (entry.missed) colorScheme.error else colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.missed) colorScheme.error else colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val kind = when {
                        entry.missed -> stringResource(R.string.calls_missed)
                        entry.outgoing -> stringResource(R.string.calls_outgoing)
                        else -> stringResource(R.string.calls_incoming)
                    }
                    Text(
                        text = if (entry.durationSeconds > 0) "$kind · ${entry.durationSeconds}s" else kind,
                        fontSize = 12.sp,
                        color = extended.textSecondary
                    )
                }
                Text(
                    text = "${dateFormat.format(entry.timestamp)} ${timeFormat.format(entry.timestamp)}",
                    fontSize = 11.sp,
                    color = extended.textTertiary
                )
            }
        }
    }
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
            val displayName = PeerNameResolver.resolve(
                peerId = peerID,
                knownNickname = peerNicknames[peerID],
                lastMessageSender = lastMsg?.sender
            ) ?: "User"
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
    onResetIdentity: () -> Unit,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onOpenProfile: () -> Unit
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
    val ghostMode by GhostMode.enabled.collectAsState()
    val soundPack by com.roman.zemzeme.ui.NotificationPreferences.soundPack.collectAsState()
    var showBlockedWordsDialog by remember { mutableStateOf(false) }
    val ownProfile by OwnProfile.state.collectAsState()
    val ownAvatarBitmap = remember(ownProfile.avatarPath) {
        ownProfile.avatarPath?.let { path ->
            runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item(key = "profile_header") {
            SectionHeader(stringResource(R.string.profile_section))
        }

        item(key = "profile_card") {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onOpenProfile() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (ownAvatarBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = ownAvatarBitmap,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                fontSize = 22.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ownProfile.displayName.ifBlank { nickname },
                            fontSize = 17.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = ownProfile.about.ifBlank { stringResource(R.string.profile_change_avatar) },
                            fontSize = 13.sp,
                            color = colorScheme.onSurface.copy(alpha = 0.65f),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

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

        item(key = "notifications_header") {
            SectionHeader(stringResource(R.string.settings_section_notifications))
        }

        item(key = "sound_pack_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    com.roman.zemzeme.ui.NotificationPreferences.PACK_SYSTEM to R.string.notif_sound_pack_system,
                    com.roman.zemzeme.ui.NotificationPreferences.PACK_BEEP to R.string.notif_sound_pack_beep,
                    com.roman.zemzeme.ui.NotificationPreferences.PACK_POP to R.string.notif_sound_pack_pop,
                    com.roman.zemzeme.ui.NotificationPreferences.PACK_CHIME to R.string.notif_sound_pack_chime
                ).forEach { (pack, labelRes) ->
                    SettingsChip(
                        label = stringResource(labelRes),
                        selected = soundPack == pack,
                        onClick = {
                            com.roman.zemzeme.ui.NotificationPreferences.setSoundPack(pack)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "privacy_header") {
            SectionHeader(stringResource(R.string.settings_section_privacy))
        }

        item(key = "away_auto_reply_row") {
            SettingsSwitchRow(
                icon = Icons.Outlined.DoNotDisturbOn,
                label = stringResource(R.string.away_auto_reply_title),
                checked = awayAutoReply,
                onCheckedChange = { AwayStatus.setAutoReply(context, it) }
            )
        }

        item(key = "ghost_mode_row") {
            SettingsSwitchRow(
                icon = Icons.Outlined.Visibility,
                label = stringResource(R.string.settings_ghost_mode),
                checked = ghostMode,
                onCheckedChange = { GhostMode.setEnabled(context, it) }
            )
        }

        item(key = "share_app_row") {
            SettingsRow(
                icon = Icons.Outlined.Share,
                label = stringResource(R.string.settings_share_app),
                onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_app_message))
                    }
                    try {
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    } catch (_: Exception) { }
                }
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
