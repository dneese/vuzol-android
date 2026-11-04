package com.roman.zemzeme.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.content.Intent
import android.net.Uri
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.nostr.NostrProfileStore
import com.roman.zemzeme.model.DeliveryStatus
import com.roman.zemzeme.mesh.BluetoothMeshService
import java.text.SimpleDateFormat
import java.util.*
import com.roman.zemzeme.ui.media.VoiceNotePlayer
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import com.roman.zemzeme.ui.media.FileMessageItem
import com.roman.zemzeme.model.ZemzemeMessageType
import com.roman.zemzeme.R
import androidx.compose.ui.res.stringResource
import com.roman.zemzeme.ui.theme.ZemzemeShapes
import com.roman.zemzeme.ui.theme.ZemzemeElevation
import com.roman.zemzeme.ui.theme.extendedColors


// VoiceNotePlayer moved to com.roman.zemzeme.ui.media.VoiceNotePlayer

/**
 * Message display components for ChatScreen
 * Extracted from ChatScreen.kt for better organization
 */

@Composable
fun MessagesList(
    messages: List<ZemzemeMessage>,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    modifier: Modifier = Modifier,
    forceScrollToBottom: Boolean = false,
    onScrolledUpChanged: ((Boolean) -> Unit)? = null,
    onNicknameClick: ((String) -> Unit)? = null,
    onMessageLongPress: ((ZemzemeMessage) -> Unit)? = null,
    onCancelTransfer: ((ZemzemeMessage) -> Unit)? = null,
    onImageClick: ((String, List<String>, Int) -> Unit)? = null,
    onReply: ((ZemzemeMessage) -> Unit)? = null,
    onReact: ((ZemzemeMessage, String) -> Unit)? = null,
    onProfileClick: ((ZemzemeMessage) -> Unit)? = null,
    onDelete: ((ZemzemeMessage) -> Unit)? = null,
    selectionMode: Boolean = false,
    selectedMessageIds: Set<String> = emptySet(),
    onToggleSelect: ((ZemzemeMessage) -> Unit)? = null,
    onForward: ((ZemzemeMessage) -> Unit)? = null,
    onShare: ((ZemzemeMessage) -> Unit)? = null,
    onSave: ((ZemzemeMessage) -> Unit)? = null,
    onSelect: ((ZemzemeMessage) -> Unit)? = null,
    onEdit: ((ZemzemeMessage) -> Unit)? = null,
    onDetails: ((ZemzemeMessage) -> Unit)? = null,
    onPin: ((ZemzemeMessage) -> Unit)? = null,
    onBlock: ((ZemzemeMessage) -> Unit)? = null,
    lastReadAt: Long = 0L
) {
    val listState = rememberLazyListState()

    // Track if this is the first time messages are being loaded
    var hasScrolledToInitialPosition by remember { mutableStateOf(false) }
    var followIncomingMessages by remember { mutableStateOf(true) }

    // Smart scroll: auto-scroll to bottom for initial load, then follow unless user scrolls away
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val isFirstLoad = !hasScrolledToInitialPosition
            if (isFirstLoad || followIncomingMessages) {
                listState.scrollToItem(0)
                if (isFirstLoad) {
                    hasScrolledToInitialPosition = true
                }
            }
        }
    }

    // Track whether user has scrolled away from the latest messages
    val isAtLatest by remember {
        derivedStateOf {
            val firstVisibleIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1
            firstVisibleIndex <= 2
        }
    }
    LaunchedEffect(isAtLatest) {
        followIncomingMessages = isAtLatest
        onScrolledUpChanged?.invoke(!isAtLatest)
    }

    // Force scroll to bottom when requested (e.g., when user sends a message)
    LaunchedEffect(forceScrollToBottom) {
        if (messages.isNotEmpty()) {
            // With reverseLayout=true and reversed data, latest is at index 0
            followIncomingMessages = true
            listState.scrollToItem(0)
        }
    }

    val chatRows = remember(messages, lastReadAt) { buildChatRows(messages, lastReadAt) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.fillMaxSize(),
        reverseLayout = true
    ) {
        items(
            items = chatRows,
            key = { row ->
                when {
                    row.isUnreadDivider -> "unread-divider"
                    row.message != null -> row.message.id
                    else -> "day-${row.dayStart}"
                }
            }
        ) { row ->
            val rowMessage = row.message
            if (row.isUnreadDivider) {
                UnreadDivider()
            } else if (rowMessage == null) {
                ChatDaySeparator(dayStart = row.dayStart)
            } else {
                MessageItem(
                    message = rowMessage,
                    messages = messages,
                    currentUserNickname = currentUserNickname,
                    meshService = meshService,
                    modifier = Modifier
                        .animateItem()
                        .padding(top = if (row.showHeader) 6.dp else 0.dp),
                    showHeader = row.showHeader,
                    showAvatar = row.isLastInGroup,
                    showTail = row.isLastInGroup,
                    onNicknameClick = onNicknameClick,
                    onMessageLongPress = onMessageLongPress,
                    onCancelTransfer = onCancelTransfer,
                    onImageClick = onImageClick,
                    onReply = onReply,
                    onReact = onReact,
                    onProfileClick = onProfileClick,
                    onDelete = onDelete,
                    selectionMode = selectionMode,
                    isSelected = selectedMessageIds.contains(rowMessage.id),
                    onToggleSelect = onToggleSelect,
                    onForward = onForward,
                    onShare = onShare,
                    onSave = onSave,
                    onSelect = onSelect,
                    onEdit = onEdit,
                    onDetails = onDetails,
                    onPin = onPin,
                    onBlock = onBlock
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: ZemzemeMessage,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    messages: List<ZemzemeMessage> = emptyList(),
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    showAvatar: Boolean = true,
    showTail: Boolean = true,
    onNicknameClick: ((String) -> Unit)? = null,
    onMessageLongPress: ((ZemzemeMessage) -> Unit)? = null,
    onCancelTransfer: ((ZemzemeMessage) -> Unit)? = null,
    onImageClick: ((String, List<String>, Int) -> Unit)? = null,
    onReply: ((ZemzemeMessage) -> Unit)? = null,
    onReact: ((ZemzemeMessage, String) -> Unit)? = null,
    onProfileClick: ((ZemzemeMessage) -> Unit)? = null,
    onDelete: ((ZemzemeMessage) -> Unit)? = null,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: ((ZemzemeMessage) -> Unit)? = null,
    onForward: ((ZemzemeMessage) -> Unit)? = null,
    onShare: ((ZemzemeMessage) -> Unit)? = null,
    onSave: ((ZemzemeMessage) -> Unit)? = null,
    onSelect: ((ZemzemeMessage) -> Unit)? = null,
    onEdit: ((ZemzemeMessage) -> Unit)? = null,
    onDetails: ((ZemzemeMessage) -> Unit)? = null,
    onPin: ((ZemzemeMessage) -> Unit)? = null,
    onBlock: ((ZemzemeMessage) -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val use24h by ChatDisplaySettings.use24hFlow.collectAsState()
    val compactLayout by ChatDisplaySettings.layoutFlow.collectAsState()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val timeFormatter = remember(use24h) {
        SimpleDateFormat(if (use24h) "HH:mm" else "h:mm a", Locale.getDefault())
    }
    val haptic = LocalHapticFeedback.current

    // Swipe-to-reply (drag horizontally, release past the threshold).
    val maxSwipePx = with(LocalDensity.current) { 72.dp.toPx() }
    var swipeOffset by remember { mutableStateOf(0f) }
    val animatedSwipe by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "swipeToReply"
    )

    // Determine if this message was sent by self
    val isSelf = message.senderPeerID == meshService.myPeerID ||
                 message.sender == currentUserNickname ||
                 message.sender.startsWith("$currentUserNickname#")

    // System messages remain centered without bubbles
    if (message.sender == "system") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "* ${message.content} *",
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = Color.Gray.copy(alpha = 0.7f),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        return
    }

    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f
    val baseColor = if (isSelf) Color(0xFFFF9500) else getPeerColor(message, isDark)
    val resolvedSender = if (message.sender.startsWith("p2p:")) {
        message.senderPeerID?.let { pid ->
            com.roman.zemzeme.p2p.P2PAliasRegistry.getDisplayName(pid)
        } ?: "User"
    } else message.sender
    val (baseName, suffix) = splitSuffix(resolvedSender)

    // Modern message layout with avatar
    Box(modifier = modifier.fillMaxWidth()) {
        // Swipe action hint revealed behind the bubble
        if (animatedSwipe != 0f) {
            val deleteAction = animatedSwipe < 0f && isSelf && onDelete != null
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = if (isSelf) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (deleteAction) Icons.Filled.Delete
                    else Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = if (deleteAction) colorScheme.error else LocalChatAccent.current,
                    modifier = Modifier
                        .padding(horizontal = 22.dp)
                        .size(22.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .offset { IntOffset(animatedSwipe.roundToInt(), 0) }
            .pointerInput(onReply) {
                if (onReply == null) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(swipeOffset) >= maxSwipePx * 0.6f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val deleteOnLeft = swipeOffset < 0f && isSelf && onDelete != null
                            if (deleteOnLeft) {
                                onDelete.invoke(message)
                            } else {
                                onReply.invoke(message)
                            }
                        }
                        swipeOffset = 0f
                    },
                    onDragCancel = { swipeOffset = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        swipeOffset = (swipeOffset + amount).coerceIn(-maxSwipePx, maxSwipePx)
                    }
                )
            }
            .pointerInput(message.id, selectionMode) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMessageLongPress?.invoke(message)
                    },
                    onDoubleTap = {
                        if (!selectionMode && onReply != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReply.invoke(message)
                        }
                    },
                    onTap = {
                        if (selectionMode) {
                            onToggleSelect?.invoke(message)
                        }
                    }
                )
            },
        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
    ) {
        if (compactLayout == ChatDisplaySettings.LAYOUT_COMPACT && message.type == ZemzemeMessageType.Message) {
            CompactMessageContent(
                message = message,
                messages = messages,
                currentUserNickname = currentUserNickname,
                meshService = meshService,
                colorScheme = colorScheme,
                timeFormatter = timeFormatter,
                isSelf = isSelf,
                author = truncateNickname(baseName) + suffix,
                authorColor = baseColor,
                onNicknameClick = onNicknameClick,
                onProfileClick = onProfileClick
            )
        } else {
        // Message content column with sender name and bubble
        Row(
            horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            // For received messages, show avatar on the left, aligned with bubble
            if (!isSelf) {
                if (showAvatar) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(CircleShape)
                            .clickable(enabled = onProfileClick != null) {
                                onProfileClick?.invoke(message)
                            }
                    ) {
                        ProfileAvatar(
                            pubkey = message.senderPubkey,
                            letter = baseName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            color = baseColor
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(44.dp))
                }
            }

            if (selectionMode && isSelf) {
                SelectionCheck(isSelected = isSelected)
            }

            // Message content column - wraps content width
            Column(
                horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
            ) {
                // Sender name (only for the first message of a group)
                if (!isSelf && showHeader) {
                    Text(
                        text = truncateNickname(baseName) + suffix,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = baseColor,
                        modifier = Modifier
                            .padding(bottom = 4.dp, start = 4.dp)
                            .clickable(enabled = onNicknameClick != null || onProfileClick != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (onProfileClick != null) {
                                    onProfileClick.invoke(message)
                                } else {
                                    onNicknameClick?.invoke(message.originalSender ?: message.sender)
                                }
                            }
                    )
                }

                // Message bubble - wraps content
                MessageBubble(
                    message = message,
                    messages = messages,
                    currentUserNickname = currentUserNickname,
                    meshService = meshService,
                    colorScheme = colorScheme,
                    timeFormatter = timeFormatter,
                    isSelf = isSelf,
                    showTail = showTail,
                    onNicknameClick = onNicknameClick,
                    onMessageLongPress = onMessageLongPress,
                    onCancelTransfer = onCancelTransfer,
                    onImageClick = onImageClick
                )

                val allReactions by ReactionsStore.reactions.collectAsState()
                val messageReactions = allReactions[message.id].orEmpty()
                if (messageReactions.isNotEmpty()) {
                    ReactionChips(
                        reactions = messageReactions,
                        myName = currentUserNickname,
                        onToggle = { emoji -> onReact?.invoke(message, emoji) }
                    )
                }
            }

            if (selectionMode && !isSelf) {
                SelectionCheck(isSelected = isSelected)
            }
        }
        }

        // Big touch target that opens the message menu without a long press
        if (!selectionMode && compactLayout != ChatDisplaySettings.LAYOUT_COMPACT) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .padding(start = 2.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMessageLongPress?.invoke(message)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = stringResource(R.string.action_more_actions),
                    tint = colorScheme.onSurface.copy(alpha = 0.32f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        }

    }
}

/** Round checkbox shown while multiple messages are selected. */
@Composable
private fun SelectionCheck(isSelected: Boolean) {
    val accent = LocalChatAccent.current
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) accent else colorScheme.onSurface.copy(alpha = 0.12f)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun ReactionChips(
    reactions: Map<String, Set<String>>,
    myName: String,
    onToggle: (String) -> Unit
) {
    Row(
        modifier = Modifier.padding(top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        reactions.forEach { (emoji, senders) ->
            val mine = senders.contains(myName)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                    .clickable { onToggle(emoji) }
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = emoji, fontSize = 12.sp)
                if (senders.size > 1) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = senders.size.toString(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarCircle(
    letter: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 36.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun ProfileAvatar(
    pubkey: String?,
    letter: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 36.dp
) {
    val profiles by NostrProfileStore.profiles.collectAsState()
    val picture = pubkey?.let { profiles[it.lowercase()]?.picture }
    val bitmap by produceState<ImageBitmap?>(null, picture) {
        value = picture?.let { AvatarLoader.load(it) }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        AvatarCircle(
            letter = letter,
            color = color,
            modifier = modifier,
            size = size
        )
    }
}

/** Mixes a base color with an accent color. */
private fun blendColors(base: androidx.compose.ui.graphics.Color, accent: androidx.compose.ui.graphics.Color, ratio: Float): androidx.compose.ui.graphics.Color {
    return androidx.compose.ui.graphics.Color(
        red = base.red + (accent.red - base.red) * ratio,
        green = base.green + (accent.green - base.green) * ratio,
        blue = base.blue + (accent.blue - base.blue) * ratio,
        alpha = base.alpha
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactMessageContent(
    message: ZemzemeMessage,
    messages: List<ZemzemeMessage>,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    colorScheme: ColorScheme,
    timeFormatter: SimpleDateFormat,
    isSelf: Boolean,
    author: String,
    authorColor: Color,
    onNicknameClick: ((String) -> Unit)?,
    onProfileClick: ((ZemzemeMessage) -> Unit)?
) {
    val chatTextSize by ChatDisplaySettings.textSizeFlow.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = timeFormatter.format(message.timestamp),
            fontSize = 10.sp,
            color = colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.padding(top = 3.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = author,
            fontSize = (chatTextSize - 2).sp,
            fontWeight = FontWeight.SemiBold,
            color = authorColor,
            modifier = Modifier
                .padding(top = 2.dp)
                .clickable(enabled = onNicknameClick != null || onProfileClick != null) {
                    if (onProfileClick != null) {
                        onProfileClick.invoke(message)
                    } else {
                        onNicknameClick?.invoke(message.originalSender ?: message.sender)
                    }
                }
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.weight(1f)) {
            MessageBubbleContent(
                message = message,
                messages = messages,
                currentUserNickname = currentUserNickname,
                meshService = meshService,
                colorScheme = colorScheme,
                timeFormatter = timeFormatter,
                isSelf = isSelf,
                onNicknameClick = onNicknameClick,
                onMessageLongPress = null,
                onCancelTransfer = null,
                onImageClick = null,
                maxTextWidth = Dp.Unspecified
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ZemzemeMessage,
    messages: List<ZemzemeMessage>,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    colorScheme: ColorScheme,
    timeFormatter: SimpleDateFormat,
    isSelf: Boolean,
    showTail: Boolean = true,
    onNicknameClick: ((String) -> Unit)?,
    onMessageLongPress: ((ZemzemeMessage) -> Unit)?,
    onCancelTransfer: ((ZemzemeMessage) -> Unit)?,
    onImageClick: ((String, List<String>, Int) -> Unit)?
) {
    val extendedColors = MaterialTheme.extendedColors
    val chatAccent = LocalChatAccent.current

    // Bubble background color from theme (own bubbles follow the chat accent)
    val bubbleColor = if (isSelf) {
        blendColors(extendedColors.sentBubble, chatAccent, 0.45f)
    } else {
        extendedColors.receivedBubble
    }

    // Cyberpunk bubble shape with "tail" effect
    // Sent messages: tail on bottom-right (20dp, 20dp, 6dp, 20dp)
    // Received messages: tail on bottom-left (20dp, 20dp, 20dp, 6dp)
    val bubbleShape = if (isSelf) {
        RoundedCornerShape(
            topStart = ZemzemeShapes.MessageBubbleRadius,
            topEnd = ZemzemeShapes.MessageBubbleRadius,
            bottomEnd = if (showTail) ZemzemeShapes.MessageBubbleRadiusSmall
            else ZemzemeShapes.MessageBubbleRadius,
            bottomStart = ZemzemeShapes.MessageBubbleRadius
        )
    } else {
        RoundedCornerShape(
            topStart = ZemzemeShapes.MessageBubbleRadius,
            topEnd = ZemzemeShapes.MessageBubbleRadius,
            bottomEnd = ZemzemeShapes.MessageBubbleRadius,
            bottomStart = if (showTail) ZemzemeShapes.MessageBubbleRadiusSmall
            else ZemzemeShapes.MessageBubbleRadius
        )
    }

    val sentBrush = if (isSelf) {
        androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(
                blendColors(extendedColors.sentBubble, chatAccent, 0.55f),
                blendColors(extendedColors.sentBubble, chatAccent, 0.22f)
            )
        )
    } else {
        null
    }

    Surface(
        modifier = Modifier
            .wrapContentWidth()
            .then(
                if (sentBrush != null) Modifier.background(sentBrush, bubbleShape)
                else Modifier
            ),
        shape = bubbleShape,
        color = if (sentBrush != null) Color.Transparent else bubbleColor,
        tonalElevation = ZemzemeElevation.Low,
        shadowElevation = ZemzemeElevation.Low
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Message content
            MessageBubbleContent(
                message = message,
                messages = messages,
                currentUserNickname = currentUserNickname,
                meshService = meshService,
                colorScheme = colorScheme,
                timeFormatter = timeFormatter,
                isSelf = isSelf,
                onNicknameClick = onNicknameClick,
                onMessageLongPress = onMessageLongPress,
                onCancelTransfer = onCancelTransfer,
                onImageClick = onImageClick
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Timestamp row at the bottom
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeFormatter.format(message.timestamp),
                    fontSize = 11.sp,
                    color = Color.Gray.copy(alpha = 0.7f)
                )

                // Edited marker
                if (message.isEdited) {
                    Text(
                        text = " · " + stringResource(com.roman.zemzeme.R.string.message_edited),
                        fontSize = 10.sp,
                        color = Color.Gray.copy(alpha = 0.6f)
                    )
                }

                // PoW badge
                message.powDifficulty?.let { bits ->
                    if (bits > 0) {
                        Text(
                            text = " ⛨${bits}b",
                            fontSize = 10.sp,
                            color = Color.Gray.copy(alpha = 0.6f)
                        )
                    }
                }

                // Delivery status for private messages
                if (isSelf && message.deliveryStatus != null) {
                    message.deliveryStatus?.let { status ->
                        Spacer(modifier = Modifier.width(4.dp))
                        DeliveryStatusIcon(status = status)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleContent(
    message: ZemzemeMessage,
    messages: List<ZemzemeMessage>,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    colorScheme: ColorScheme,
    timeFormatter: SimpleDateFormat,
    isSelf: Boolean,
    onNicknameClick: ((String) -> Unit)?,
    onMessageLongPress: ((ZemzemeMessage) -> Unit)?,
    onCancelTransfer: ((ZemzemeMessage) -> Unit)?,
    onImageClick: ((String, List<String>, Int) -> Unit)?,
    maxTextWidth: Dp = 280.dp
) {
    val chatTextSize by ChatDisplaySettings.textSizeFlow.collectAsState()

    // Handle special message types
    when (message.type) {
        ZemzemeMessageType.Image -> {
            com.roman.zemzeme.ui.media.ImageMessageItem(
                message = message,
                messages = messages,
                currentUserNickname = currentUserNickname,
                meshService = meshService,
                colorScheme = colorScheme,
                timeFormatter = timeFormatter,
                onNicknameClick = onNicknameClick,
                onMessageLongPress = onMessageLongPress,
                onCancelTransfer = onCancelTransfer,
                onImageClick = onImageClick,
                modifier = Modifier.fillMaxWidth()
            )
            return
        }
        ZemzemeMessageType.Audio -> {
            com.roman.zemzeme.ui.media.AudioMessageItem(
                message = message,
                currentUserNickname = currentUserNickname,
                meshService = meshService,
                colorScheme = colorScheme,
                timeFormatter = timeFormatter,
                onNicknameClick = onNicknameClick,
                onMessageLongPress = onMessageLongPress,
                onCancelTransfer = onCancelTransfer,
                modifier = Modifier.fillMaxWidth()
            )
            return
        }
        ZemzemeMessageType.File -> {
            RenderFileMessage(
                message = message,
                currentUserNickname = currentUserNickname,
                onCancelTransfer = onCancelTransfer
            )
            return
        }
        else -> {
            // Text message - continue below
        }
    }

    // Check if this message should be animated during PoW mining
    val shouldAnimate = shouldAnimateMessage(message.id)

    if (shouldAnimate) {
        MessageWithMatrixAnimation(
            message = message,
            messages = messages,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onImageClick = onImageClick,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        // Normal text message. Replies prefix the content with a "> quote" block,
        // which is rendered separately above the message body.
        val (replyQuote, replyBody) = MessageReply.split(message.content)
        val contentMessage =
            if (replyQuote != null && replyBody.isNotEmpty()) message.copy(content = replyBody) else message
        val annotatedText = formatMessageContentOnly(
            message = contentMessage,
            currentUserNickname = currentUserNickname,
            colorScheme = colorScheme,
            isSelf = isSelf
        )

        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        val linkUrl = remember(contentMessage.content) {
            LinkPreviewStore.firstUrl(contentMessage.content)
        }
        LaunchedEffect(linkUrl) {
            linkUrl?.let { LinkPreviewStore.ensure(it) }
        }
        val previews by LinkPreviewStore.previews.collectAsState()
        val linkPreview = linkUrl?.let { previews[it] }

        Column(modifier = Modifier.widthIn(max = maxTextWidth)) {
        if (replyQuote != null) {
            ReplyQuoteBlock(text = replyQuote)
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = annotatedText,
            modifier = Modifier
                .wrapContentWidth()
                .pointerInput(contentMessage) {
                    detectTapGestures(
                        onTap = { position ->
                            val layout = textLayoutResult ?: return@detectTapGestures
                            val offset = layout.getOffsetForPosition(position)

                            // Geohash teleport
                            val geohashAnnotations = annotatedText.getStringAnnotations(
                                tag = "geohash_click",
                                start = offset,
                                end = offset
                            )
                            if (geohashAnnotations.isNotEmpty()) {
                                val geohash = geohashAnnotations.first().item
                                try {
                                    val locationManager =
                                        com.roman.zemzeme.geohash.LocationChannelManager.getInstance(
                                            context
                                        )
                                    val level = when (geohash.length) {
                                        in 0..2 -> com.roman.zemzeme.geohash.GeohashChannelLevel.REGION
                                        in 3..4 -> com.roman.zemzeme.geohash.GeohashChannelLevel.PROVINCE
                                        5 -> com.roman.zemzeme.geohash.GeohashChannelLevel.CITY
                                        6 -> com.roman.zemzeme.geohash.GeohashChannelLevel.NEIGHBORHOOD
                                        else -> com.roman.zemzeme.geohash.GeohashChannelLevel.BLOCK
                                    }
                                    val channel = com.roman.zemzeme.geohash.GeohashChannel(
                                        level,
                                        geohash.lowercase()
                                    )
                                    locationManager.setTeleported(true)
                                    locationManager.select(
                                        com.roman.zemzeme.geohash.ChannelID.Location(
                                            channel
                                        )
                                    )
                                } catch (_: Exception) {
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                return@detectTapGestures
                            }

                            // URL open
                            val urlAnnotations = annotatedText.getStringAnnotations(
                                tag = "url_click",
                                start = offset,
                                end = offset
                            )
                            if (urlAnnotations.isNotEmpty()) {
                                val raw = urlAnnotations.first().item
                                val resolved =
                                    if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith(
                                            "https://",
                                            ignoreCase = true
                                        )
                                    ) raw else "https://$raw"
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resolved))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                return@detectTapGestures
                            }
                        }
                    )
                },
            fontSize = chatTextSize.sp,
            lineHeight = (chatTextSize + 5).sp,
            color = colorScheme.onSurface,
            onTextLayout = { result -> textLayoutResult = result }
        )
        if (linkPreview != null) {
            LinkPreviewCard(preview = linkPreview)
        }
        }
    }
}

@Composable
private fun LinkPreviewCard(preview: LinkPreview) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, preview.imageUrl) {
        value = preview.imageUrl?.let { AvatarLoader.load(it) }
    }
    Column(
        modifier = Modifier
            .padding(top = 6.dp)
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(preview.url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            }
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
        }
        Column(modifier = Modifier.padding(10.dp)) {
            preview.title?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            preview.description?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            preview.siteName?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ReplyQuoteBlock(text: String) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colorScheme.primary.copy(alpha = 0.10f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .background(colorScheme.primary)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = colorScheme.primary.copy(alpha = 0.9f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun UnreadDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Text(
            text = stringResource(R.string.chat_new_messages),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun ChatDaySeparator(dayStart: Long) {
    val zone = remember { java.time.ZoneId.systemDefault() }
    val date = remember(dayStart) {
        java.time.Instant.ofEpochMilli(dayStart).atZone(zone).toLocalDate()
    }
    val today = remember { java.time.LocalDate.now(zone) }
    val label = when (date) {
        today -> stringResource(R.string.chat_today)
        today.minusDays(1) -> stringResource(R.string.chat_yesterday)
        else -> remember(date) {
            java.time.format.DateTimeFormatter
                .ofPattern("d MMMM", Locale.getDefault())
                .format(date)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

private data class ChatRow(
    val message: ZemzemeMessage?,
    val dayStart: Long = 0L,
    val showHeader: Boolean = true,
    val isLastInGroup: Boolean = true,
    val isUnreadDivider: Boolean = false
)

private fun buildChatRows(messages: List<ZemzemeMessage>, lastReadAt: Long = 0L): List<ChatRow> {
    val zone = java.time.ZoneId.systemDefault()
    fun dayOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    val rows = mutableListOf<ChatRow>()
    var previous: ZemzemeMessage? = null
    for (message in messages) {
        val prev = previous
        val newDay = prev == null || dayOf(prev.timestamp.time) != dayOf(message.timestamp.time)
        if (newDay) {
            rows.add(ChatRow(message = null, dayStart = message.timestamp.time))
        }
        val sameSender = prev != null &&
            prev.sender == message.sender &&
            prev.senderPeerID == message.senderPeerID
        val closeInTime = prev != null &&
            (message.timestamp.time - prev.timestamp.time) in 0..300_000
        rows.add(
            ChatRow(
                message = message,
                showHeader = newDay || !sameSender || !closeInTime
            )
        )
        previous = message
    }

    // A bubble only keeps its "tail" (and the avatar) on the last message of a group.
    val grouped = rows.mapIndexed { index, row ->
        val current = row.message ?: return@mapIndexed row
        val next = rows.getOrNull(index + 1)?.message
        val continues = next != null &&
            next.sender == current.sender &&
            next.senderPeerID == current.senderPeerID &&
            dayOf(next.timestamp.time) == dayOf(current.timestamp.time) &&
            (next.timestamp.time - current.timestamp.time) in 0..300_000
        row.copy(isLastInGroup = !continues)
    }

    // Insert a "new messages" divider above the first unread message.
    val withDivider = if (lastReadAt > 0L) {
        val unreadIndex = grouped.indexOfFirst { row ->
            val msg = row.message
            msg != null && msg.timestamp.time > lastReadAt && msg.sender != "system"
        }
        if (unreadIndex >= 0) {
            val out = grouped.toMutableList()
            out.add(unreadIndex, ChatRow(message = null, isUnreadDivider = true))
            out
        } else grouped
    } else grouped

    return withDivider.asReversed()
}

@Composable
private fun RenderFileMessage(
    message: ZemzemeMessage,
    currentUserNickname: String,
    onCancelTransfer: ((ZemzemeMessage) -> Unit)?
) {
    val path = message.content.trim()
    val (overrideProgress, _) = when (val st = message.deliveryStatus) {
        is com.roman.zemzeme.model.DeliveryStatus.PartiallyDelivered -> {
            if (st.total > 0 && st.reached < st.total) {
                (st.reached.toFloat() / st.total.toFloat()) to Color(0xFF1E88E5)
            } else null to null
        }
        else -> null to null
    }

    val packet = try {
        val file = java.io.File(path)
        if (file.exists()) {
            com.roman.zemzeme.model.ZemzemeFilePacket(
                fileName = file.name,
                fileSize = file.length(),
                mimeType = com.roman.zemzeme.features.file.FileUtils.getMimeTypeFromExtension(file.name),
                content = file.readBytes()
            )
        } else null
    } catch (e: Exception) {
        null
    }

    Box {
        if (packet != null) {
            if (overrideProgress != null) {
                com.roman.zemzeme.ui.media.FileSendingAnimation(
                    fileName = packet.fileName,
                    progress = overrideProgress,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                FileMessageItem(
                    packet = packet,
                    onFileClick = {}
                )
            }

            val showCancel = message.sender == currentUserNickname &&
                    (message.deliveryStatus is DeliveryStatus.PartiallyDelivered)
            if (showCancel) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .background(Color.Gray.copy(alpha = 0.6f), CircleShape)
                        .clickable { onCancelTransfer?.invoke(message) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_cancel),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.file_unavailable),
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
    private fun MessageTextWithClickableNicknames(
        message: ZemzemeMessage,
        messages: List<ZemzemeMessage>,
        currentUserNickname: String,
        meshService: BluetoothMeshService,
        colorScheme: ColorScheme,
        timeFormatter: SimpleDateFormat,
        onNicknameClick: ((String) -> Unit)?,
        onMessageLongPress: ((ZemzemeMessage) -> Unit)?,
        onCancelTransfer: ((ZemzemeMessage) -> Unit)?,
        onImageClick: ((String, List<String>, Int) -> Unit)?,
        modifier: Modifier = Modifier
    ) {
    // Image special rendering
    if (message.type == ZemzemeMessageType.Image) {
        com.roman.zemzeme.ui.media.ImageMessageItem(
            message = message,
            messages = messages,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onCancelTransfer = onCancelTransfer,
            onImageClick = onImageClick,
            modifier = modifier
        )
        return
    }

    // Voice note special rendering
    if (message.type == ZemzemeMessageType.Audio) {
        com.roman.zemzeme.ui.media.AudioMessageItem(
            message = message,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onCancelTransfer = onCancelTransfer,
            modifier = modifier
        )
        return
    }

    // File special rendering
    if (message.type == ZemzemeMessageType.File) {
        val path = message.content.trim()
        // Derive sending progress if applicable
        val (overrideProgress, _) = when (val st = message.deliveryStatus) {
            is com.roman.zemzeme.model.DeliveryStatus.PartiallyDelivered -> {
                if (st.total > 0 && st.reached < st.total) {
                    (st.reached.toFloat() / st.total.toFloat()) to Color(0xFF1E88E5) // blue while sending
                } else null to null
            }
            else -> null to null
        }
        Column(modifier = modifier.fillMaxWidth()) {
            // Header: nickname + timestamp line above the file, identical styling to text messages
            val headerText = formatMessageHeaderAnnotatedString(
                message = message,
                currentUserNickname = currentUserNickname,
                meshService = meshService,
                colorScheme = colorScheme,
                timeFormatter = timeFormatter
            )
            val haptic = LocalHapticFeedback.current
            var headerLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = headerText,
                fontFamily = NunitoFontFamily,
                color = colorScheme.onSurface,
                modifier = Modifier.pointerInput(message.id) {
                    detectTapGestures(onTap = { pos ->
                        val layout = headerLayout ?: return@detectTapGestures
                        val offset = layout.getOffsetForPosition(pos)
                        val ann = headerText.getStringAnnotations("nickname_click", offset, offset)
                        if (ann.isNotEmpty() && onNicknameClick != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNicknameClick.invoke(ann.first().item)
                        }
                    }, onLongPress = { onMessageLongPress?.invoke(message) })
                },
                onTextLayout = { headerLayout = it }
            )

            // Try to load the file packet from the path
            val packet = try {
                val file = java.io.File(path)
                if (file.exists()) {
                    // Create a temporary ZemzemeFilePacket for display
                    // In a real implementation, this would be stored with the packet metadata
                    com.roman.zemzeme.model.ZemzemeFilePacket(
                        fileName = file.name,
                        fileSize = file.length(),
                        mimeType = com.roman.zemzeme.features.file.FileUtils.getMimeTypeFromExtension(file.name),
                        content = file.readBytes()
                    )
                } else null
            } catch (e: Exception) {
                null
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box {
                    if (packet != null) {
                        if (overrideProgress != null) {
                            // Show sending animation while in-flight
                            com.roman.zemzeme.ui.media.FileSendingAnimation(
                                fileName = packet.fileName,
                                progress = overrideProgress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Static file display with open/save dialog
                            FileMessageItem(
                                packet = packet,
                                onFileClick = {
                                    // handled inside FileMessageItem via dialog
                                }
                            )
                        }

                        // Cancel button overlay during sending
                        val showCancel = message.sender == currentUserNickname && (message.deliveryStatus is DeliveryStatus.PartiallyDelivered)
                        if (showCancel) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .background(Color.Gray.copy(alpha = 0.6f), CircleShape)
                                    .clickable { onCancelTransfer?.invoke(message) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.cd_cancel), tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    } else {
                        Text(text = stringResource(R.string.file_unavailable), fontFamily = NunitoFontFamily, color = Color.Gray)
                    }
                }
            }
        }
        return
    }

    // Check if this message should be animated during PoW mining
    val shouldAnimate = shouldAnimateMessage(message.id)
    
    // If animation is needed, use the matrix animation component for content only
    if (shouldAnimate) {
        // Display message with matrix animation for content
        MessageWithMatrixAnimation(
            message = message,
            messages = messages,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onImageClick = onImageClick,
            modifier = modifier
        )
    } else {
        // Normal message display
        val annotatedText = formatMessageAsAnnotatedString(
            message = message,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter
        )
        
        // Check if this message was sent by self to avoid click interactions on own nickname
        val isSelf = message.senderPeerID == meshService.myPeerID || 
                     message.sender == currentUserNickname ||
                     message.sender.startsWith("$currentUserNickname#")
        
        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = annotatedText,
            modifier = modifier.pointerInput(message) {
                detectTapGestures(
                    onTap = { position ->
                        val layout = textLayoutResult ?: return@detectTapGestures
                        val offset = layout.getOffsetForPosition(position)
                        // Nickname click only when not self
                        if (!isSelf && onNicknameClick != null) {
                            val nicknameAnnotations = annotatedText.getStringAnnotations(
                                tag = "nickname_click",
                                start = offset,
                                end = offset
                            )
                            if (nicknameAnnotations.isNotEmpty()) {
                                val nickname = nicknameAnnotations.first().item
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNicknameClick.invoke(nickname)
                                return@detectTapGestures
                            }
                        }
                        // Geohash teleport (all messages)
                        val geohashAnnotations = annotatedText.getStringAnnotations(
                            tag = "geohash_click",
                            start = offset,
                            end = offset
                        )
                        if (geohashAnnotations.isNotEmpty()) {
                            val geohash = geohashAnnotations.first().item
                            try {
                                val locationManager = com.roman.zemzeme.geohash.LocationChannelManager.getInstance(
                                    context
                                )
                                val level = when (geohash.length) {
                                    in 0..2 -> com.roman.zemzeme.geohash.GeohashChannelLevel.REGION
                                    in 3..4 -> com.roman.zemzeme.geohash.GeohashChannelLevel.PROVINCE
                                    5 -> com.roman.zemzeme.geohash.GeohashChannelLevel.CITY
                                    6 -> com.roman.zemzeme.geohash.GeohashChannelLevel.NEIGHBORHOOD
                                    else -> com.roman.zemzeme.geohash.GeohashChannelLevel.BLOCK
                                }
                                val channel = com.roman.zemzeme.geohash.GeohashChannel(level, geohash.lowercase())
                                locationManager.setTeleported(true)
                                locationManager.select(com.roman.zemzeme.geohash.ChannelID.Location(channel))
                            } catch (_: Exception) { }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            return@detectTapGestures
                        }
                        // URL open (all messages)
                        val urlAnnotations = annotatedText.getStringAnnotations(
                            tag = "url_click",
                            start = offset,
                            end = offset
                        )
                        if (urlAnnotations.isNotEmpty()) {
                            val raw = urlAnnotations.first().item
                            val resolved = if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) raw else "https://$raw"
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resolved))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) { }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            return@detectTapGestures
                        }
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMessageLongPress?.invoke(message)
                    }
                )
            },
            fontFamily = NunitoFontFamily,
            softWrap = true,
            overflow = TextOverflow.Visible,
            style = androidx.compose.ui.text.TextStyle(
                color = colorScheme.onSurface
            ),
            onTextLayout = { result -> textLayoutResult = result }
        )
    }
}

@Composable
fun DeliveryStatusIcon(status: DeliveryStatus) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = LocalChatAccent.current

    when (status) {
        is DeliveryStatus.Sending -> {
            Text(
                text = "\u22EF",
                fontSize = 11.sp,
                color = colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        is DeliveryStatus.Sent -> {
            Text(
                text = "\u2713",
                fontSize = 11.sp,
                color = colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        is DeliveryStatus.Delivered -> {
            Text(
                text = "\u2713\u2713",
                fontSize = 10.sp,
                color = colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        is DeliveryStatus.Read -> {
            Text(
                text = "\u2713\u2713",
                fontSize = 10.sp,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        is DeliveryStatus.Failed -> {
            Text(
                text = "!",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Red.copy(alpha = 0.9f)
            )
        }
        is DeliveryStatus.PartiallyDelivered -> {
            Text(
                text = "\u25D4",
                fontSize = 11.sp,
                color = colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
