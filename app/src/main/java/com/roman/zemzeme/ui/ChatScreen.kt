package com.roman.zemzeme.ui
// [Goose] Bridge file share events to ViewModel via dispatcher is installed in ChatScreen composition

// [Goose] Installing FileShareDispatcher handler in ChatScreen to forward file sends to ViewModel


import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.roman.zemzeme.geohash.isMeshView
import com.roman.zemzeme.BuildConfig
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.onboarding.NetworkStatus
import com.roman.zemzeme.onboarding.NetworkStatusManager
import com.roman.zemzeme.ui.media.FullScreenImageViewer

/**
 * Main ChatScreen - REFACTORED to use component-based architecture
 * This is now a coordinator that orchestrates the following UI components:
 * - ChatHeader: App bar, navigation, peer counter
 * - MessageComponents: Message display and formatting
 * - InputComponents: Message input and command suggestions
 * - SidebarComponents: Navigation drawer with channels and people
 * - AboutSheet: App info and password prompts
 * - ChatUIUtils: Utility functions for formatting and colors
 */
@Composable
fun ChatScreen(viewModel: ChatViewModel, isBluetoothEnabled: Boolean = true, onBackToHome: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val selectedPrivatePeer by viewModel.selectedPrivateChatPeer.collectAsStateWithLifecycle()
    val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val joinedChannels by viewModel.joinedChannels.collectAsStateWithLifecycle()
    val hasUnreadChannels by viewModel.unreadChannelMessages.collectAsStateWithLifecycle()
    val hasUnreadPrivateMessages by viewModel.unreadPrivateMessages.collectAsStateWithLifecycle()
    val privateChats by viewModel.privateChats.collectAsStateWithLifecycle()
    val channelMessages by viewModel.channelMessages.collectAsStateWithLifecycle()
    val showCommandSuggestions by viewModel.showCommandSuggestions.collectAsStateWithLifecycle()
    val commandSuggestions by viewModel.commandSuggestions.collectAsStateWithLifecycle()
    val showMentionSuggestions by viewModel.showMentionSuggestions.collectAsStateWithLifecycle()
    val mentionSuggestions by viewModel.mentionSuggestions.collectAsStateWithLifecycle()
    val showAppInfo by viewModel.showAppInfo.collectAsStateWithLifecycle()
    val showMeshPeerListSheet by viewModel.showMeshPeerList.collectAsStateWithLifecycle()
    val privateChatSheetPeer by viewModel.privateChatSheetPeer.collectAsStateWithLifecycle()
    val showVerificationSheet by viewModel.showVerificationSheet.collectAsStateWithLifecycle()
    val showSecurityVerificationSheet by viewModel.showSecurityVerificationSheet.collectAsStateWithLifecycle()

    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var showLocationChannelsSheet by remember { mutableStateOf(false) }
    var showLocationNotesSheet by remember { mutableStateOf(false) }
    var showMessageSearch by remember { mutableStateOf(false) }
    var messageToForward by remember { mutableStateOf<ZemzemeMessage?>(null) }
    var profileMessage by remember { mutableStateOf<ZemzemeMessage?>(null) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ZemzemeMessage?>(null) }
    var editingText by remember { mutableStateOf("") }
    var messageEffect by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val pinnedMessages by com.roman.zemzeme.services.PinStore.pins.collectAsState()
    var showUserSheet by remember { mutableStateOf(false) }
    var selectedUserForSheet by remember { mutableStateOf("") }
    var selectedMessageForSheet by remember { mutableStateOf<ZemzemeMessage?>(null) }
    var showFullScreenImageViewer by remember { mutableStateOf(false) }
    var viewerImagePaths by remember { mutableStateOf(emptyList<String>()) }
    var initialViewerIndex by remember { mutableStateOf(0) }
    var forceScrollToBottom by remember { mutableStateOf(false) }
    var isScrolledUp by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<ZemzemeMessage?>(null) }
    var detailsMessage by remember { mutableStateOf<ZemzemeMessage?>(null) }


    // Show password dialog when needed
    LaunchedEffect(showPasswordPrompt) {
        showPasswordDialog = showPasswordPrompt
    }

    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val passwordPromptChannel by viewModel.passwordPromptChannel.collectAsStateWithLifecycle()

    // Get location channel info for timeline switching
    val selectedLocationChannel by viewModel.selectedLocationChannel.collectAsStateWithLifecycle()
    val conversationKey: String? = when {
        currentChannel != null -> "ch:$currentChannel"
        else -> (selectedLocationChannel as? com.roman.zemzeme.geohash.ChannelID.Location)
            ?.let { "geo:${it.channel.geohash}" }
    }

    // Shared message action handlers (quick popup + full action sheet)
    val handleMessageForward: (ZemzemeMessage) -> Unit = { message ->
        messageToForward = message
    }
    val handleMessageEdit: (ZemzemeMessage) -> Unit = { message ->
        editingMessage = message
        editingText = message.content
    }
    val handleMessageSelect: (ZemzemeMessage) -> Unit = { message ->
        selectionMode = true
        selectedMessageIds = setOf(message.id)
    }
    val handleMessageSave: (ZemzemeMessage) -> Unit = { message ->
        com.roman.zemzeme.services.ChatHistoryStore.saveBookmark(message)
        android.widget.Toast.makeText(
            context,
            context.getString(com.roman.zemzeme.R.string.saved_message_toast),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
    val handleMessagePin: (ZemzemeMessage) -> Unit = { message ->
        val key = conversationKey
        if (key != null) {
            if (com.roman.zemzeme.services.PinStore.pinned(key) == message.id) {
                com.roman.zemzeme.services.PinStore.clear(key)
            } else {
                com.roman.zemzeme.services.PinStore.set(key, message.id)
            }
        }
    }
    val handleMessageBlock: (ZemzemeMessage) -> Unit = { message ->
        val (baseName, _) = splitSuffix(message.sender)
        val selectedLocationChannel = viewModel.selectedLocationChannel.value
        if (selectedLocationChannel is com.roman.zemzeme.geohash.ChannelID.Location) {
            viewModel.blockUserInGeohash(baseName)
        } else {
            viewModel.sendMessage("/block $baseName")
        }
    }
    val initialLastRead = remember(conversationKey) {
        conversationKey?.let { com.roman.zemzeme.services.ReadStateStore.lastRead(it) } ?: 0L
    }
    LaunchedEffect(conversationKey) {
        if (conversationKey != null) {
            val draft = com.roman.zemzeme.services.DraftStore.get(conversationKey)
            if (draft.isNotEmpty() && messageText.text.isEmpty()) {
                messageText = TextFieldValue(draft, selection = TextRange(draft.length))
            }
        }
    }
    val transportToggles by com.roman.zemzeme.p2p.P2PConfig.transportTogglesFlow.collectAsStateWithLifecycle()
    val isBleSettingEnabled = transportToggles.bleEnabled
    val isP2PEnabled = transportToggles.p2pEnabled
    val isNostrEnabled = transportToggles.nostrEnabled
    val networkStatus by NetworkStatusManager.networkStatusFlow.collectAsStateWithLifecycle()
    val isAirplaneModeOn by NetworkStatusManager.airplaneModeFlow.collectAsStateWithLifecycle()

    // Determine what messages to show based on current context (unified timelines)
    // Legacy private chat timeline removed - private chats now exclusively use PrivateChatSheet
    val rawMessages = when {
        currentChannel != null -> channelMessages[currentChannel] ?: emptyList()
        else -> {
            val locationChannel = selectedLocationChannel
            if (locationChannel is com.roman.zemzeme.geohash.ChannelID.Location) {
                val geokey = "geo:${locationChannel.channel.geohash}"
                channelMessages[geokey] ?: emptyList()
            } else {
                messages // Mesh timeline
            }
        }
    }

    // Keyword filter: hide messages that contain blocked words
    val displayMessages = remember(rawMessages) {
        if (rawMessages.any { ChatDisplaySettings.isBlocked(it.content) }) {
            rawMessages.filterNot { ChatDisplaySettings.isBlocked(it.content) }
        } else {
            rawMessages
        }
    }

    LaunchedEffect(conversationKey, displayMessages.size) {
        if (conversationKey != null && displayMessages.isNotEmpty()) {
            delay(1_000)
            com.roman.zemzeme.services.ReadStateStore.markRead(conversationKey)
            // Tell the channel that the newest incoming message was read
            val lastIncoming = displayMessages.lastOrNull { message ->
                message.sender != nickname &&
                    !message.sender.startsWith("$nickname#")
            }
            if (lastIncoming != null) {
                viewModel.markMessageRead(lastIncoming.id)
            }
        }
    }

    // Away mode: automatic reply to mentions while the user is away
    val awayState by AwayStatus.state.collectAsState()
    val awayRepliedIds = remember { mutableSetOf<String>() }
    LaunchedEffect(displayMessages.size, awayState.active, awayState.autoReply) {
        val last = displayMessages.lastOrNull() ?: return@LaunchedEffect
        if (!awayState.active || !awayState.autoReply) return@LaunchedEffect
        if (last.sender == nickname || last.sender.startsWith("$nickname#")) return@LaunchedEffect
        if (last.sender == "system") return@LaunchedEffect
        if (!last.content.contains("@$nickname", ignoreCase = true)) return@LaunchedEffect
        if (!awayRepliedIds.add(last.id)) return@LaunchedEffect
        val replyText = if (awayState.message.isBlank()) {
            context.getString(com.roman.zemzeme.R.string.away_auto_reply_default)
        } else {
            context.getString(com.roman.zemzeme.R.string.away_auto_reply, awayState.message)
        }
        viewModel.sendMessage(replyText)
    }

    // Auto-delete (disappearing) messages timer for this conversation
    val ttlValues by com.roman.zemzeme.services.MessageTTLStore.values.collectAsState()
    val conversationTtl = conversationKey?.let { ttlValues[it] ?: 0L } ?: 0L
    val currentMessages by rememberUpdatedState(displayMessages)
    LaunchedEffect(conversationKey, conversationTtl) {
        if (conversationTtl > 0L) {
            while (true) {
                kotlinx.coroutines.delay(1_000)
                val cutoff = System.currentTimeMillis() - conversationTtl * 1000L
                currentMessages
                    .filter { it.timestamp.time < cutoff }
                    .forEach { viewModel.deleteMessageLocally(it) }
            }
        }
    }

    // Show media buttons in all channel contexts (mesh, named channels, and geohash location channels)
    val showMediaButtons = true

    // Appearance (accent color + wallpaper) of the current chat
    val appearanceKey = conversationKey ?: "default"
    val appearances by ChatAppearanceStore.appearances.collectAsState()
    val appearance = appearances[appearanceKey] ?: ChatAppearanceStore.Appearance()

    // Immersive header: peer avatar as a blurred backdrop in private chats
    val nostrProfiles by com.roman.zemzeme.nostr.NostrProfileStore.profiles.collectAsState()
    val peerPubkey = remember(conversationKey, displayMessages, selectedPrivatePeer) {
        selectedPrivatePeer?.let { peer ->
            displayMessages.lastOrNull {
                it.senderPeerID == peer || it.sender == peer || it.sender.startsWith(peer)
            }?.senderPubkey
        }
    }
    val peerDisplayName = remember(conversationKey, displayMessages, selectedPrivatePeer) {
        selectedPrivatePeer?.let { peer ->
            displayMessages.lastOrNull {
                it.senderPeerID == peer || it.sender == peer || it.sender.startsWith(peer)
            }?.sender
        }
    }
    val headerImageUrl = peerPubkey?.let { nostrProfiles[it.lowercase()]?.picture }
    var headerImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(headerImageUrl) {
        headerImage = headerImageUrl?.let { AvatarLoader.load(it) }
    }

    // Use WindowInsets to handle keyboard properly
    androidx.compose.runtime.CompositionLocalProvider(LocalChatAccent provides appearance.accent) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background) // Extend background to fill entire screen including status bar
    ) {
        // Chat wallpaper behind the messages
        ChatWallpaper(
            accent = appearance.accent,
            mode = appearance.wallpaperMode,
            photoPath = appearance.wallpaperPath,
            modifier = Modifier.matchParentSize()
        )
        val headerHeight = 82.dp

        // Main content area that responds to keyboard/window insets
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime) // This handles keyboard insets
                .windowInsetsPadding(WindowInsets.navigationBars) // Add bottom padding when keyboard is not expanded
        ) {
            // Header spacer - creates exact space for the floating header (status bar + compact header)
            Spacer(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(headerHeight)
            )

            // BLE disconnection banner — shown in #mesh view when BT or BLE setting is off
            if (shouldShowBleMeshBanner(isBluetoothEnabled, isBleSettingEnabled, currentChannel, selectedLocationChannel)) {
                BleMeshBanner(
                    isBluetoothEnabled = isBluetoothEnabled,
                    isBleSettingEnabled = isBleSettingEnabled,
                    onOpenAppSettings = { viewModel.showAppInfo() }
                )
            }

            // Network banner — shown in internet-requiring channels when no internet or no transport enabled
            if (shouldShowNetworkBanner(networkStatus, isP2PEnabled, isNostrEnabled, currentChannel, selectedLocationChannel)) {
                NetworkBanner(
                    networkStatus = networkStatus,
                    isP2PEnabled = isP2PEnabled,
                    isNostrEnabled = isNostrEnabled,
                    isAirplaneModeOn = isAirplaneModeOn,
                    onOpenAppSettings = { viewModel.showAppInfo() }
                )
            }

            // Pinned message banner
            val pinnedId = conversationKey?.let { pinnedMessages[it] }
            val pinnedMessage = pinnedId?.let { id -> displayMessages.firstOrNull { it.id == id } }
            if (pinnedMessage != null && conversationKey != null) {
                PinnedMessageBanner(
                    message = pinnedMessage,
                    onUnpin = { com.roman.zemzeme.services.PinStore.clear(conversationKey) }
                )
            }

            // Messages area - takes up available space, will compress when keyboard appears
            MessagesList(
                messages = displayMessages,
                currentUserNickname = nickname,
                meshService = viewModel.meshService,
                modifier = Modifier.weight(1f),
                forceScrollToBottom = forceScrollToBottom,
                onScrolledUpChanged = { isUp -> isScrolledUp = isUp },
                onNicknameClick = { fullSenderName ->
                    // Single click - mention user in text input
                    val currentText = messageText.text
                    
                    // Extract base nickname and hash suffix from full sender name
                    val (baseName, hashSuffix) = splitSuffix(fullSenderName)
                    
                    // Check if we're in a geohash channel to include hash suffix
                    val selectedLocationChannel = viewModel.selectedLocationChannel.value
                    val mentionText = if (selectedLocationChannel is com.roman.zemzeme.geohash.ChannelID.Location && hashSuffix.isNotEmpty()) {
                        // In geohash chat - include the hash suffix from the full display name
                        "@$baseName$hashSuffix"
                    } else {
                        // Regular chat - just the base nickname
                        "@$baseName"
                    }
                    
                    val newText = when {
                        currentText.isEmpty() -> "$mentionText "
                        currentText.endsWith(" ") -> "$currentText$mentionText "
                        else -> "$currentText $mentionText "
                    }
                    
                    messageText = TextFieldValue(
                        text = newText,
                        selection = TextRange(newText.length)
                    )
                },
                onMessageLongPress = { message ->
                    // Message long press - open user action sheet with message context
                    // Extract base nickname from message sender (contains all necessary info)
                    val (baseName, _) = splitSuffix(message.sender)
                    selectedUserForSheet = baseName
                    selectedMessageForSheet = message
                    showUserSheet = true
                },
                onCancelTransfer = { msg ->
                    viewModel.cancelMediaSend(msg.id)
                },
                onImageClick = { currentPath, allImagePaths, initialIndex ->
                    viewerImagePaths = allImagePaths
                    initialViewerIndex = initialIndex
                    showFullScreenImageViewer = true
                },
                onReply = { msg -> replyTo = msg },
                onReact = { msg, emoji -> viewModel.sendReaction(msg.id, emoji) },
                onProfileClick = { msg -> profileMessage = msg },
                selectionMode = selectionMode,
                selectedMessageIds = selectedMessageIds,
                onToggleSelect = { msg ->
                    selectedMessageIds = if (selectedMessageIds.contains(msg.id)) {
                        selectedMessageIds - msg.id
                    } else {
                        selectedMessageIds + msg.id
                    }
                },
                onDelete = { msg ->
                    viewModel.deleteMessageLocally(msg)
                    android.widget.Toast.makeText(
                        context,
                        context.getString(com.roman.zemzeme.R.string.message_deleted),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onForward = handleMessageForward,
                onSave = handleMessageSave,
                onSelect = handleMessageSelect,
                onEdit = handleMessageEdit,
                onDetails = { msg -> detailsMessage = msg },
                onPin = handleMessagePin,
                onBlock = handleMessageBlock,
                lastReadAt = initialLastRead
            )
            // Input area - stays at bottom
        // Bridge file share from lower-level input to ViewModel
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.roman.zemzeme.ui.events.FileShareDispatcher.setHandler { peer, channel, path ->
            viewModel.sendFileNote(peer, channel, path)
        }
    }

    // "X is typing..." for the current geohash channel
    val typingSnapshot by TypingStore.typing.collectAsState()
    val typingGeohash = (selectedLocationChannel as? com.roman.zemzeme.geohash.ChannelID.Location)
        ?.channel?.geohash
    var typingTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_500)
            TypingStore.prune()
            typingTick = System.currentTimeMillis()
        }
    }
    val typingNames = remember(typingSnapshot, typingGeohash, typingTick) {
        val now = System.currentTimeMillis()
        typingGeohash?.let { geo ->
            typingSnapshot[geo].orEmpty()
                .filterValues { now - it < TypingStore.TTL_MS }
                .keys
                .filter { it != nickname }
                .sorted()
        }.orEmpty()
    }
    if (typingNames.isNotEmpty()) {
        Text(
            text = if (typingNames.size == 1) {
                stringResource(com.roman.zemzeme.R.string.typing_one, typingNames.first())
            } else {
                stringResource(com.roman.zemzeme.R.string.typing_many, typingNames.size)
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
        )
    }

    if (awayState.active) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colorScheme.secondaryContainer.copy(alpha = 0.6f))
                .clickable { AwayStatus.clear(context) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.VisibilityOff,
                contentDescription = null,
                tint = colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (awayState.message.isBlank()) {
                    stringResource(com.roman.zemzeme.R.string.away_banner_default)
                } else {
                    stringResource(com.roman.zemzeme.R.string.away_banner, awayState.message)
                },
                color = colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(com.roman.zemzeme.R.string.away_banner_cancel),
                color = colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    ChatInputSection(
        messageText = messageText,
        onMessageTextChange = { newText: TextFieldValue ->
            messageText = newText
            viewModel.updateCommandSuggestions(newText.text)
            viewModel.updateMentionSuggestions(newText.text)
            conversationKey?.let { com.roman.zemzeme.services.DraftStore.set(it, newText.text) }
            if (newText.text.isNotBlank()) viewModel.notifyTyping()
        },
        onSend = {
            val trimmed = messageText.text.trim()
            if (trimmed.isNotEmpty()) {
                val original = replyTo
                val outgoing =
                    if (original != null) MessageReply.encode(original, trimmed) else trimmed
                viewModel.sendMessage(outgoing)
                val effect = com.roman.zemzeme.ui.MessageEffects.effectFor(trimmed)
                if (effect != null) messageEffect = effect
                conversationKey?.let { com.roman.zemzeme.services.DraftStore.clear(it) }
                messageText = TextFieldValue("")
                replyTo = null
                forceScrollToBottom = !forceScrollToBottom // Toggle to trigger scroll
            }
        },
        replyingTo = replyTo,
        onCancelReply = { replyTo = null },
        onSendVoiceNote = { peer, onionOrChannel, path ->
            viewModel.sendVoiceNote(peer, onionOrChannel, path)
        },
        onSendImageNote = { peer, onionOrChannel, path ->
            viewModel.sendImageNote(peer, onionOrChannel, path)
        },
        onSendFileNote = { peer, onionOrChannel, path ->
            viewModel.sendFileNote(peer, onionOrChannel, path)
        },
        
        showCommandSuggestions = showCommandSuggestions,
        commandSuggestions = commandSuggestions,
        showMentionSuggestions = showMentionSuggestions,
        mentionSuggestions = mentionSuggestions,
        onCommandSuggestionClick = { suggestion: CommandSuggestion ->
                    val commandText = viewModel.selectCommandSuggestion(suggestion)
                    messageText = TextFieldValue(
                        text = commandText,
                        selection = TextRange(commandText.length)
                    )
                },
                onMentionSuggestionClick = { mention: String ->
                    val mentionText = viewModel.selectMentionSuggestion(mention, messageText.text)
                    messageText = TextFieldValue(
                        text = mentionText,
                        selection = TextRange(mentionText.length)
                    )
                },
                selectedPrivatePeer = null,
                currentChannel = currentChannel,
                nickname = nickname,
                colorScheme = colorScheme,
                showMediaButtons = showMediaButtons,
                enabled = !shouldShowBleMeshBanner(isBluetoothEnabled, isBleSettingEnabled, currentChannel, selectedLocationChannel)
                    && !shouldShowNetworkBanner(networkStatus, isP2PEnabled, isNostrEnabled, currentChannel, selectedLocationChannel)
            )
        }

        // Selection action bar replaces the header while selecting messages
        if (selectionMode) {
            SelectionActionBar(
                count = selectedMessageIds.size,
                onClose = {
                    selectionMode = false
                    selectedMessageIds = emptySet()
                },
                onDelete = {
                    selectedMessageIds.forEach { id ->
                        displayMessages.firstOrNull { it.id == id }?.let {
                            viewModel.deleteMessageLocally(it)
                        }
                    }
                    selectedMessageIds = emptySet()
                    selectionMode = false
                },
                onSave = {
                    selectedMessageIds.forEach { id ->
                        displayMessages.firstOrNull { it.id == id }?.let {
                            com.roman.zemzeme.services.ChatHistoryStore.saveBookmark(it)
                        }
                    }
                    selectedMessageIds = emptySet()
                    selectionMode = false
                },
                onForward = {
                    val first = selectedMessageIds.firstOrNull()
                    val message = displayMessages.firstOrNull { it.id == first }
                    if (message != null) {
                        messageToForward = message
                    }
                    selectedMessageIds = emptySet()
                    selectionMode = false
                }
            )
        }

        // Floating header - positioned absolutely at top, ignores keyboard
        if (!selectionMode) ChatFloatingHeader(
            headerHeight = headerHeight,
            selectedPrivatePeer = selectedPrivatePeer,
            currentChannel = currentChannel,
            nickname = nickname,
            viewModel = viewModel,
            colorScheme = colorScheme,
            onSidebarToggle = { viewModel.showMeshPeerList() },
            onBackToHome = onBackToHome,
            onShowAppInfo = { viewModel.showAppInfo() },
            onPanicClear = { viewModel.panicClearAllData() },
            onLocationChannelsClick = { showLocationChannelsSheet = true },
            onLocationNotesClick = { showLocationNotesSheet = true },
            onSearchClick = { showMessageSearch = true },
            onAppearanceClick = { showAppearanceSheet = true },
            headerImage = headerImage,
            privatePeerPubkey = peerPubkey,
            privatePeerName = peerDisplayName
        )

        // Divider under header - positioned after status bar + header height
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .offset(y = headerHeight)
                .zIndex(1f),
            color = colorScheme.outline.copy(alpha = 0.3f)
        )

        // Message effect (emoji burst) shown after sending a reaction-style emoji
        messageEffect?.let { emoji ->
            LaunchedEffect(emoji) {
                kotlinx.coroutines.delay(1400)
                messageEffect = null
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = androidx.compose.animation.fadeIn() +
                    androidx.compose.animation.scaleIn(initialScale = 0.4f),
                exit = androidx.compose.animation.fadeOut() +
                    androidx.compose.animation.scaleOut(targetScale = 1.4f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp)
                    .zIndex(2f)
            ) {
                Text(
                    text = emoji,
                    fontSize = 72.sp
                )
            }
        }

        // Scroll-to-bottom floating button
        AnimatedVisibility(
            visible = isScrolledUp,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 64.dp)
                .zIndex(1.5f)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime)
        ) {
            Surface(
                shape = CircleShape,
                color = colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(2.dp, LocalChatAccent.current)
            ) {
                IconButton(onClick = { forceScrollToBottom = !forceScrollToBottom }) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = stringResource(com.roman.zemzeme.R.string.cd_scroll_to_bottom),
                        tint = LocalChatAccent.current
                    )
                }
            }
        }
    }
    }

    // Edit own message
    editingMessage?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text(androidx.compose.ui.res.stringResource(com.roman.zemzeme.R.string.action_edit_title)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.editMessage(message, editingText)
                    editingMessage = null
                }) {
                    Text(androidx.compose.ui.res.stringResource(com.roman.zemzeme.R.string.action_save))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { editingMessage = null }) {
                    Text(androidx.compose.ui.res.stringResource(com.roman.zemzeme.R.string.cancel))
                }
            }
        )
    }

    // Chat appearance (accent color + wallpaper)
    ChatAppearanceSheet(
        isPresented = showAppearanceSheet,
        appearance = appearances[appearanceKey] ?: ChatAppearanceStore.Appearance(),
        onAccentChange = { index ->
            ChatAppearanceStore.setAccent(context, appearanceKey, index)
        },
        onWallpaperChange = { mode, path ->
            ChatAppearanceStore.setWallpaper(context, appearanceKey, mode, path)
        },
        onReset = {
            ChatAppearanceStore.reset(context, appearanceKey)
        },
        onDismiss = { showAppearanceSheet = false }
    )

    // Full-screen image viewer - separate from other sheets to allow image browsing without navigation
    if (showFullScreenImageViewer) {
        FullScreenImageViewer(
            imagePaths = viewerImagePaths,
            initialIndex = initialViewerIndex,
            onClose = { showFullScreenImageViewer = false }
        )
    }

    // Dialogs and Sheets
    ChatDialogs(
        isBluetoothEnabled = isBluetoothEnabled,
        showPasswordDialog = showPasswordDialog,
        passwordPromptChannel = passwordPromptChannel,
        passwordInput = passwordInput,
        onPasswordChange = { passwordInput = it },
        onPasswordConfirm = {
            if (passwordInput.isNotEmpty()) {
                val success = viewModel.joinChannel(passwordPromptChannel!!, passwordInput)
                if (success) {
                    showPasswordDialog = false
                    passwordInput = ""
                }
            }
        },
        onPasswordDismiss = {
            showPasswordDialog = false
            passwordInput = ""
        },
        showAppInfo = showAppInfo,
        onAppInfoDismiss = { viewModel.hideAppInfo() },
        showLocationChannelsSheet = showLocationChannelsSheet,
        onLocationChannelsSheetDismiss = { showLocationChannelsSheet = false },
        showLocationNotesSheet = showLocationNotesSheet,
        onLocationNotesSheetDismiss = { showLocationNotesSheet = false },
        showMessageSearch = showMessageSearch,
        onMessageSearchDismiss = { showMessageSearch = false },
        displayMessages = displayMessages,
        messageToForward = messageToForward,
        profileMessage = profileMessage,
        onProfileDismiss = { profileMessage = null },
        onProfileMention = { name ->
            val currentText = messageText.text
            val mentionText = "@$name "
            messageText = TextFieldValue(
                text = currentText + mentionText,
                selection = TextRange(currentText.length + mentionText.length)
            )
        },
        onProfileBlock = { name ->
            val selectedLocationChannel = viewModel.selectedLocationChannel.value
            if (selectedLocationChannel is com.roman.zemzeme.geohash.ChannelID.Location) {
                viewModel.blockUserInGeohash(name)
            } else {
                viewModel.sendMessage("/block $name")
            }
        },
        onMessageForwardDismiss = { messageToForward = null },
        onMessageForwardRequest = handleMessageForward,
        onMessageEditRequest = handleMessageEdit,
        onMessageSelectRequest = handleMessageSelect,
        onMessageSaveRequest = handleMessageSave,
        onMessageForward = { message, channel ->
            viewModel.forwardMessage(message, channel)
        },
        showUserSheet = showUserSheet,
        onUserSheetDismiss = { 
            showUserSheet = false
            selectedMessageForSheet = null // Reset message when dismissing
        },
        selectedUserForSheet = selectedUserForSheet,
        selectedMessageForSheet = selectedMessageForSheet,
        onReply = { msg -> replyTo = msg },
        viewModel = viewModel,
        showVerificationSheet = showVerificationSheet,
        onVerificationSheetDismiss = viewModel::hideVerificationSheet,
        showSecurityVerificationSheet = showSecurityVerificationSheet,
        onSecurityVerificationSheetDismiss = viewModel::hideSecurityVerificationSheet,
        showMeshPeerListSheet = showMeshPeerListSheet,
        onMeshPeerListDismiss = viewModel::hideMeshPeerList,
        onBackToHome = onBackToHome,
    )

    // Technical details of a message
    MessageDetailsSheet(
        isPresented = detailsMessage != null,
        message = detailsMessage,
        onDismiss = { detailsMessage = null }
    )
}

private fun shouldShowBleMeshBanner(
    isBluetoothEnabled: Boolean,
    isBleSettingEnabled: Boolean,
    currentChannel: String?,
    selectedLocationChannel: com.roman.zemzeme.geohash.ChannelID?
): Boolean = (!isBluetoothEnabled || !isBleSettingEnabled) && currentChannel == null && selectedLocationChannel.isMeshView

@Composable
private fun BleMeshBanner(
    isBluetoothEnabled: Boolean,
    isBleSettingEnabled: Boolean,
    onOpenAppSettings: () -> Unit
) {
    val context = LocalContext.current
    val bannerColor = Color(0xFFFF3B30)

    val bannerText = if (!isBleSettingEnabled) {
        stringResource(com.roman.zemzeme.R.string.ble_banner_mesh_disabled)
    } else {
        stringResource(com.roman.zemzeme.R.string.ble_banner_off)
    }

    // Setting disabled → go to app settings (the BT dialog handles the rest)
    // Setting enabled but BT off → go to Android BT settings
    val onEnableClick: () -> Unit = if (!isBleSettingEnabled) {
        onOpenAppSettings
    } else {
        { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bannerColor.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.BluetoothDisabled,
                contentDescription = null,
                tint = bannerColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = bannerText,
                style = MaterialTheme.typography.bodySmall,
                color = bannerColor,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onEnableClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(com.roman.zemzeme.R.string.ble_banner_enable_btn),
                    style = MaterialTheme.typography.labelSmall,
                    color = bannerColor
                )
            }
        }
    }
}

private fun shouldShowNetworkBanner(
    networkStatus: NetworkStatus,
    isP2PEnabled: Boolean,
    isNostrEnabled: Boolean,
    currentChannel: String?,
    selectedLocationChannel: com.roman.zemzeme.geohash.ChannelID?
): Boolean {
    // Network banner only applies to internet-requiring views (geohash location or joined channel)
    val isInternetRequiringView = currentChannel != null ||
        selectedLocationChannel is com.roman.zemzeme.geohash.ChannelID.Location
    if (!isInternetRequiringView) return false

    // Both transports disabled by user
    if (!isP2PEnabled && !isNostrEnabled) return true

    // No network or no validated internet
    return networkStatus != NetworkStatus.CONNECTED
}

@Composable
private fun NetworkBanner(
    networkStatus: NetworkStatus,
    isP2PEnabled: Boolean,
    isNostrEnabled: Boolean,
    isAirplaneModeOn: Boolean,
    onOpenAppSettings: () -> Unit
) {
    val context = LocalContext.current

    val bannerText: String
    val bannerIcon: androidx.compose.ui.graphics.vector.ImageVector
    val onActionClick: () -> Unit
    val bannerColor: Color

    when {
        // User has both transports disabled — orange/yellow
        !isP2PEnabled && !isNostrEnabled -> {
            bannerColor = Color(0xFFFF9500)
            bannerText = stringResource(com.roman.zemzeme.R.string.network_banner_transports_disabled)
            bannerIcon = Icons.Filled.CloudOff
            onActionClick = onOpenAppSettings
        }
        // Airplane mode on — red, open airplane mode settings directly
        isAirplaneModeOn -> {
            bannerColor = Color(0xFFFF3B30)
            bannerText = stringResource(com.roman.zemzeme.R.string.network_banner_airplane_mode)
            bannerIcon = Icons.Filled.AirplanemodeActive
            onActionClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                }
            }
        }
        // No network at all — red
        networkStatus == NetworkStatus.DISCONNECTED -> {
            bannerColor = Color(0xFFFF3B30)
            bannerText = stringResource(com.roman.zemzeme.R.string.network_banner_disconnected)
            bannerIcon = Icons.Filled.WifiOff
            onActionClick = {
                try {
                    context.startActivity(Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                }
            }
        }
        // Connected but no validated internet — red
        else -> {
            bannerColor = Color(0xFFFF3B30)
            bannerText = stringResource(com.roman.zemzeme.R.string.network_banner_no_internet)
            bannerIcon = Icons.Filled.WifiOff
            onActionClick = {
                try {
                    context.startActivity(Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bannerColor.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = bannerIcon,
                contentDescription = null,
                tint = bannerColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = bannerText,
                style = MaterialTheme.typography.bodySmall,
                color = bannerColor,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (!isP2PEnabled && !isNostrEnabled) {
                        stringResource(com.roman.zemzeme.R.string.network_banner_settings_btn)
                    } else {
                        stringResource(com.roman.zemzeme.R.string.ble_banner_enable_btn)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = bannerColor
                )
            }
        }
    }
}

@Composable
private fun ReplyComposerBar(message: ZemzemeMessage, onCancel: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val (sender, _) = splitSuffix(message.sender)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(34.dp)
                .background(colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(com.roman.zemzeme.R.string.replying_to, sender),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = MessageReply.preview(message),
                fontSize = 13.sp,
                color = colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(com.roman.zemzeme.R.string.cancel_lower),
                tint = colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun PinnedMessageBanner(message: ZemzemeMessage, onUnpin: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .background(colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(com.roman.zemzeme.R.string.pinned_message),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary
            )
            Text(
                text = message.content.replace("\n", " "),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
        IconButton(onClick = onUnpin) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(android.R.string.cancel),
                tint = colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun ChatInputSection(
    messageText: TextFieldValue,
    onMessageTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onSendVoiceNote: (String?, String?, String) -> Unit,
    onSendImageNote: (String?, String?, String) -> Unit,
    onSendFileNote: (String?, String?, String) -> Unit,
    showCommandSuggestions: Boolean,
    commandSuggestions: List<CommandSuggestion>,
    showMentionSuggestions: Boolean,
    mentionSuggestions: List<String>,
    onCommandSuggestionClick: (CommandSuggestion) -> Unit,
    onMentionSuggestionClick: (String) -> Unit,
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    colorScheme: ColorScheme,
    showMediaButtons: Boolean,
    enabled: Boolean = true,
    replyingTo: ZemzemeMessage? = null,
    onCancelReply: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.background
    ) {
        Column {
            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))
            if (replyingTo != null) {
                ReplyComposerBar(message = replyingTo, onCancel = onCancelReply)
                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.2f))
            }
            // Command suggestions box
            if (showCommandSuggestions && commandSuggestions.isNotEmpty()) {
                CommandSuggestionsBox(
                    suggestions = commandSuggestions,
                    onSuggestionClick = onCommandSuggestionClick,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.2f))
            }
            // Mention suggestions box
            if (showMentionSuggestions && mentionSuggestions.isNotEmpty()) {
                MentionSuggestionsBox(
                    suggestions = mentionSuggestions,
                    onSuggestionClick = onMentionSuggestionClick,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.2f))
            }
            MessageInput(
                value = messageText,
                onValueChange = onMessageTextChange,
                onSend = onSend,
                onSendVoiceNote = onSendVoiceNote,
                onSendImageNote = onSendImageNote,
                onSendFileNote = onSendFileNote,
                selectedPrivatePeer = selectedPrivatePeer,
                currentChannel = currentChannel,
                nickname = nickname,
                showMediaButtons = showMediaButtons,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatFloatingHeader(
    headerHeight: Dp,
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    viewModel: ChatViewModel,
    colorScheme: ColorScheme,
    onSidebarToggle: () -> Unit,
    onBackToHome: () -> Unit,
    onShowAppInfo: () -> Unit,
    onPanicClear: () -> Unit,
    onLocationChannelsClick: () -> Unit,
    onLocationNotesClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onAppearanceClick: (() -> Unit)? = null,
    headerImage: androidx.compose.ui.graphics.ImageBitmap? = null,
    privatePeerPubkey: String? = null,
    privatePeerName: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val locationManager = remember { com.roman.zemzeme.geohash.LocationChannelManager.getInstance(context) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .windowInsetsPadding(WindowInsets.statusBars), // Extend into status bar area
        color = if (headerImage != null) Color.Transparent else colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (headerImage != null) {
                androidx.compose.foundation.Image(
                    bitmap = headerImage,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(28.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(colorScheme.background.copy(alpha = 0.62f))
                )
            }
            ChatHeaderContent(
                selectedPrivatePeer = selectedPrivatePeer,
                currentChannel = currentChannel,
                nickname = nickname,
                viewModel = viewModel,
                onBackClick = {
                    when {
                        selectedPrivatePeer != null -> viewModel.endPrivateChat()
                        currentChannel != null -> viewModel.switchToChannel(null)
                    }
                },
                onBackToHome = onBackToHome,
                onSidebarClick = onSidebarToggle,
                onTripleClick = onPanicClear,
                onShowAppInfo = onShowAppInfo,
                onLocationChannelsClick = onLocationChannelsClick,
                onLocationNotesClick = {
                    // Ensure location is loaded before showing sheet
                    locationManager.refreshChannels()
                    onLocationNotesClick()
                },
                onSearchClick = onSearchClick,
                onAppearanceClick = onAppearanceClick,
                privatePeerPubkey = privatePeerPubkey,
                privatePeerName = privatePeerName
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionActionBar(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onForward: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = LocalChatAccent.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(2f)
            .windowInsetsPadding(WindowInsets.statusBars),
        color = colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(com.roman.zemzeme.R.string.cancel),
                    tint = colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(com.roman.zemzeme.R.string.selection_count, count),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSave, enabled = count > 0) {
                Icon(
                    imageVector = Icons.Outlined.Bookmark,
                    contentDescription = stringResource(com.roman.zemzeme.R.string.action_save_message_title),
                    tint = if (count > 0) accent else colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            IconButton(onClick = onForward, enabled = count > 0) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Forward,
                    contentDescription = stringResource(com.roman.zemzeme.R.string.action_forward_title),
                    tint = if (count > 0) accent else colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            IconButton(onClick = onDelete, enabled = count > 0) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(com.roman.zemzeme.R.string.action_delete_title),
                    tint = if (count > 0) colorScheme.error else colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDialogs(
    isBluetoothEnabled: Boolean,
    showPasswordDialog: Boolean,
    passwordPromptChannel: String?,
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirm: () -> Unit,
    onPasswordDismiss: () -> Unit,
    showAppInfo: Boolean,
    onAppInfoDismiss: () -> Unit,
    showLocationChannelsSheet: Boolean,
    onLocationChannelsSheetDismiss: () -> Unit,
    showLocationNotesSheet: Boolean,
    onLocationNotesSheetDismiss: () -> Unit,
    showUserSheet: Boolean,
    onUserSheetDismiss: () -> Unit,
    selectedUserForSheet: String,
    selectedMessageForSheet: ZemzemeMessage?,
    onReply: (ZemzemeMessage) -> Unit,
    showMessageSearch: Boolean,
    onMessageSearchDismiss: () -> Unit,
    displayMessages: List<ZemzemeMessage>,
    messageToForward: ZemzemeMessage?,
    profileMessage: ZemzemeMessage?,
    onProfileDismiss: () -> Unit,
    onProfileMention: (String) -> Unit,
    onProfileBlock: (String) -> Unit,
    onMessageForwardDismiss: () -> Unit,
    onMessageForwardRequest: (ZemzemeMessage) -> Unit,
    onMessageEditRequest: (ZemzemeMessage) -> Unit,
    onMessageSaveRequest: (ZemzemeMessage) -> Unit,
    onMessageSelectRequest: (ZemzemeMessage) -> Unit,
    onMessageForward: (ZemzemeMessage, com.roman.zemzeme.geohash.GeohashChannel) -> Unit,
    viewModel: ChatViewModel,
    showVerificationSheet: Boolean,
    onVerificationSheetDismiss: () -> Unit,
    showSecurityVerificationSheet: Boolean,
    onSecurityVerificationSheetDismiss: () -> Unit,
    showMeshPeerListSheet: Boolean,
    onMeshPeerListDismiss: () -> Unit,
    onBackToHome: () -> Unit = {},
) {
    val privateChatSheetPeer by viewModel.privateChatSheetPeer.collectAsStateWithLifecycle()

    // Password dialog
    PasswordPromptDialog(
        show = showPasswordDialog,
        channelName = passwordPromptChannel,
        passwordInput = passwordInput,
        onPasswordChange = onPasswordChange,
        onConfirm = onPasswordConfirm,
        onDismiss = onPasswordDismiss
    )

    // About sheet
    var showDebugSheet by remember { mutableStateOf(false) }
    val debugUiEnabled by com.roman.zemzeme.ui.debug.DebugSettingsManager
        .getInstance().debugUiEnabled.collectAsState()
    val showDebugAccess = debugUiEnabled || BuildConfig.DEBUG
    val currentNickname by viewModel.nickname.collectAsStateWithLifecycle()
    AboutSheet(
        isPresented = showAppInfo,
        onDismiss = onAppInfoDismiss,
        isBluetoothEnabled = isBluetoothEnabled,
        onShowDebug = if (showDebugAccess) ({ showDebugSheet = true }) else null,
        nickname = currentNickname,
        onNicknameChange = { viewModel.setNickname(it) }
    )
    if (showDebugAccess && showDebugSheet) {
        com.roman.zemzeme.ui.debug.DebugSettingsSheet(
            isPresented = showDebugSheet,
            onDismiss = { showDebugSheet = false },
            meshService = viewModel.meshService
        )
    }
    
    // Peer profile
    PeerProfileSheet(
        isPresented = profileMessage != null,
        message = profileMessage,
        displayName = profileMessage?.let { splitSuffix(it.sender).first } ?: "",
        onMention = { name -> onProfileMention(name) },
        onBlock = {
            val name = profileMessage?.let { splitSuffix(it.sender).first } ?: ""
            onProfileBlock(name)
        },
        onDismiss = onProfileDismiss
    )

    // Forward message to another channel
    ForwardSheet(
        isPresented = messageToForward != null,
        onDismiss = onMessageForwardDismiss,
        onPick = { channel ->
            messageToForward?.let { onMessageForward(it, channel) }
        }
    )

    // Message search
    MessageSearchSheet(
        isPresented = showMessageSearch,
        messages = displayMessages,
        onDismiss = onMessageSearchDismiss
    )

    // Location channels sheet
    if (showLocationChannelsSheet) {
        LocationChannelsSheet(
            isPresented = showLocationChannelsSheet,
            onDismiss = onLocationChannelsSheetDismiss,
            viewModel = viewModel
        )
    }
    
    // Location notes sheet (extracted to separate presenter)
    if (showLocationNotesSheet) {
        LocationNotesSheetPresenter(
            viewModel = viewModel,
            onDismiss = onLocationNotesSheetDismiss
        )
    }
    
    // User action sheet
    if (showUserSheet) {
        ChatUserSheet(
            isPresented = showUserSheet,
            onDismiss = onUserSheetDismiss,
            targetNickname = selectedUserForSheet,
            selectedMessage = selectedMessageForSheet,
            onReply = onReply,
            onReact = { emoji -> selectedMessageForSheet?.let { viewModel.sendReaction(it.id, emoji) } },
            onForward = { message -> onMessageForwardRequest(message) },
            onEdit = { message -> onMessageEditRequest(message) },
            onSave = { message -> onMessageSaveRequest(message) },
            onSelectMultiple = { message -> onMessageSelectRequest(message) },
            viewModel = viewModel
        )
    }
    // MeshPeerList sheet (network view)
    if (showMeshPeerListSheet){
        MeshPeerListSheet(
            isPresented = showMeshPeerListSheet,
            viewModel = viewModel,
            onDismiss = onMeshPeerListDismiss,
            onShowVerification = {
                onMeshPeerListDismiss()
                viewModel.showVerificationSheet(fromSidebar = true)
            }
        )
    }

    if (showVerificationSheet) {
        VerificationSheet(
            isPresented = showVerificationSheet,
            onDismiss = onVerificationSheetDismiss,
            viewModel = viewModel
        )
    }

    if (showSecurityVerificationSheet) {
        SecurityVerificationSheet(
            isPresented = showSecurityVerificationSheet,
            onDismiss = onSecurityVerificationSheetDismiss,
            viewModel = viewModel
        )
    }

    if (privateChatSheetPeer != null) {
        // Full-screen loading overlay to hide mesh timeline while PrivateChatSheet animates in
        val cs = MaterialTheme.colorScheme
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cs.background)
                .zIndex(10f),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = cs.primary,
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp
            )
        }

        PrivateChatSheet(
            isPresented = true,
            peerID = privateChatSheetPeer!!,
            viewModel = viewModel,
            onDismiss = {
                val goHome = viewModel.consumeEnteredViaContact()
                viewModel.hidePrivateChatSheet()
                viewModel.endPrivateChat()
                if (goHome) {
                    onBackToHome()
                }
            }
        )
    }
}
