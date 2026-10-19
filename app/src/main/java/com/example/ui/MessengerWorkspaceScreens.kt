package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FolderOption(val key: String, val label: String)

private val folderOptions = listOf(
    FolderOption("ALL", "Все"),
    FolderOption("PERSONAL", "Личные"),
    FolderOption("GROUPS", "Группы"),
    FolderOption("CHANNELS", "Каналы"),
    FolderOption("LAN", "LAN-сеть"),
    FolderOption("SECRET", "Секретные")
)

@Composable
fun ChatsWorkspaceScreen(
    conversations: List<ConversationEntity>,
    activeConversation: ConversationEntity?,
    activeMessages: List<MessageEntity>,
    selectedFolder: String,
    searchQuery: String,
    replyTargetMessage: MessageEntity?,
    inspectedMessage: MessageEntity?,
    activeCipherId: String,
    localLanIp: String,
    onSelectFolder: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenConversation: (String?) -> Unit,
    onSendMessage: (String, String?, String?) -> Unit,
    onSetReplyTarget: (MessageEntity?) -> Unit,
    onToggleReaction: (MessageEntity, String) -> Unit,
    onPinMessage: (MessageEntity) -> Unit,
    onCycleSelfDestruct: () -> Unit,
    onInspectMessage: (MessageEntity?) -> Unit,
    onCreateConversation: (String, String, String, String?, Int) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 720.dp

        if (isExpandedScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                ) {
                    ConversationListPane(
                        conversations = conversations,
                        activeConversationId = activeConversation?.id,
                        selectedFolder = selectedFolder,
                        searchQuery = searchQuery,
                        localLanIp = localLanIp,
                        onSelectFolder = onSelectFolder,
                        onSearchQueryChange = onSearchQueryChange,
                        onSelectConversation = { onOpenConversation(it) },
                        onOpenCreateDialog = { showCreateDialog = true }
                    )
                }
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outline
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (activeConversation != null) {
                        ConversationDetailPane(
                            conversation = activeConversation,
                            messages = activeMessages,
                            replyTarget = replyTargetMessage,
                            activeCipherId = activeCipherId,
                            showBackButton = false,
                            onBack = { onOpenConversation(null) },
                            onSendMessage = onSendMessage,
                            onSetReplyTarget = onSetReplyTarget,
                            onToggleReaction = onToggleReaction,
                            onPinMessage = onPinMessage,
                            onCycleSelfDestruct = onCycleSelfDestruct,
                            onInspectMessage = onInspectMessage
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Выберите чат, группу, канал или LAN-узел",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            if (activeConversation != null) {
                BackHandler {
                    onOpenConversation(null)
                }
                ConversationDetailPane(
                    conversation = activeConversation,
                    messages = activeMessages,
                    replyTarget = replyTargetMessage,
                    activeCipherId = activeCipherId,
                    showBackButton = true,
                    onBack = { onOpenConversation(null) },
                    onSendMessage = onSendMessage,
                    onSetReplyTarget = onSetReplyTarget,
                    onToggleReaction = onToggleReaction,
                    onPinMessage = onPinMessage,
                    onCycleSelfDestruct = onCycleSelfDestruct,
                    onInspectMessage = onInspectMessage
                )
            } else {
                ConversationListPane(
                    conversations = conversations,
                    activeConversationId = null,
                    selectedFolder = selectedFolder,
                    searchQuery = searchQuery,
                    localLanIp = localLanIp,
                    onSelectFolder = onSelectFolder,
                    onSearchQueryChange = onSearchQueryChange,
                    onSelectConversation = { onOpenConversation(it) },
                    onOpenCreateDialog = { showCreateDialog = true }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateConversationModal(
            defaultLanIp = localLanIp,
            onDismiss = { showCreateDialog = false },
            onCreate = { title, handle, kind, peerIp, destructSec ->
                onCreateConversation(title, handle, kind, peerIp, destructSec)
                showCreateDialog = false
            }
        )
    }

    if (inspectedMessage != null) {
        PacketInspectorDialog(
            message = inspectedMessage,
            onDismiss = { onInspectMessage(null) }
        )
    }
}

@Composable
private fun ConversationListPane(
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    selectedFolder: String,
    searchQuery: String,
    localLanIp: String,
    onSelectFolder: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectConversation: (String) -> Unit,
    onOpenCreateDialog: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Поиск по чатам, каналам, LAN-узлам...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("chat_search_input")
            )

            // Telegram-style Folder Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                folderOptions.forEach { folder ->
                    FilterChip(
                        selected = selectedFolder == folder.key,
                        onClick = { onSelectFolder(folder.key) },
                        label = {
                            Text(
                                text = folder.label,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        modifier = Modifier.testTag("folder_chip_${folder.key}")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "В этой папке пока нет диалогов",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Нажмите +, чтобы создать личный чат, группу, канал или прямое LAN-соединение.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(conversations, key = { it.id }) { conv ->
                        val isSelected = conv.id == activeConversationId
                        ConversationRowItem(
                            conversation = conv,
                            isSelected = isSelected,
                            onClick = { onSelectConversation(conv.id) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }
                    item { Spacer(modifier = Modifier.height(84.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenCreateDialog,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .testTag("new_conversation_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Создать чат, группу или канал")
        }
    }
}

@Composable
private fun ConversationRowItem(
    conversation: ConversationEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val kindIcon = when (conversation.kind) {
        "CHANNEL" -> Icons.Default.Campaign
        "GROUP" -> Icons.Default.Group
        "SECRET" -> Icons.Default.Lock
        else -> if (!conversation.peerIpAddress.isNullOrBlank()) Icons.Default.Lan else Icons.Default.Person
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.background
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("conversation_item_${conversation.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = kindIcon,
                    contentDescription = conversation.kind,
                    tint = if (!conversation.peerIpAddress.isNullOrBlank()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!conversation.peerIpAddress.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text(
                                text = "LAN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else if (conversation.kind == "CHANNEL") {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = "КАНАЛ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Text(
                    text = timeFormatter.format(Date(conversation.lastMessageTime)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.lastMessagePreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationDetailPane(
    conversation: ConversationEntity,
    messages: List<MessageEntity>,
    replyTarget: MessageEntity?,
    activeCipherId: String,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onSendMessage: (String, String?, String?) -> Unit,
    onSetReplyTarget: (MessageEntity?) -> Unit,
    onToggleReaction: (MessageEntity, String) -> Unit,
    onPinMessage: (MessageEntity) -> Unit,
    onCycleSelfDestruct: () -> Unit,
    onInspectMessage: (MessageEntity?) -> Unit
) {
    var messageDraft by remember { mutableStateOf("") }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Conversation Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (showBackButton) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("chat_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад"
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conversation.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subInfo = buildString {
                                append(conversation.handle)
                                if (!conversation.peerIpAddress.isNullOrBlank()) {
                                    append(" • LAN ${conversation.peerIpAddress}:${conversation.peerPort}")
                                } else if (conversation.kind == "CHANNEL") {
                                    append(" • ${conversation.memberCount} подписчиков")
                                } else if (conversation.kind == "GROUP") {
                                    append(" • ${conversation.memberCount} участников")
                                }
                                append(" • $activeCipherId")
                            }
                            Text(
                                text = subInfo,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = onCycleSelfDestruct,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("self_destruct_timer_button")
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (conversation.selfDestructSeconds == 0) "OFF" else "${conversation.selfDestructSeconds}s",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Pinned Message Bar
                if (!conversation.pinnedMessageText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Закреплено: ${conversation.pinnedMessageText}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Messages List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val alignment = if (msg.isOutgoing) Alignment.End else Alignment.Start
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = alignment
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .clickable { onInspectMessage(msg) }
                            .testTag("message_bubble_${msg.id}"),
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (msg.isOutgoing) 12.dp else 2.dp,
                            bottomEnd = if (msg.isOutgoing) 2.dp else 12.dp
                        ),
                        color = if (msg.isOutgoing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (msg.transportRoute == "LAN_P2P") MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg.senderName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${msg.transportRoute} • ${msg.latencyMs} ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (msg.transportRoute == "LAN_P2P") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!msg.replyPreview.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Text(
                                        text = "↪ ${msg.replyPreview}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (!msg.attachmentType.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(
                                        text = "📎 [${msg.attachmentType}] ${msg.attachmentMeta ?: "Зашифрованный контейнер Aegis-52"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = msg.plaintext,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Cryptographic Wire Preview Footer
                            Text(
                                text = "HEX: ${msg.ciphertextHex.take(28)}... • MAC: ${msg.macTagHex.take(12)}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Action Row: Reply, Pin, Inspect, Reactions, Timestamp
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("⚡", "🛡", "✓").forEach { emoji ->
                                        val isReacted = msg.reactionsCsv.split(",").contains(emoji)
                                        Surface(
                                            color = if (isReacted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            modifier = Modifier.clickable { onToggleReaction(msg, emoji) }
                                        ) {
                                            Text(
                                                text = emoji,
                                                fontSize = 11.sp,
                                                color = if (isReacted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Ответить",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clickable { onSetReplyTarget(msg) }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )

                                    Text(
                                        text = if (msg.isPinned) "Открепить" else "Закрепить",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clickable { onPinMessage(msg) }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = buildString {
                                        if (conversation.kind == "CHANNEL") {
                                            append("👁 ${msg.viewsCount} • ")
                                        }
                                        append(timeFormatter.format(Date(msg.timestamp)))
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Reply Banner if active
        AnimatedVisibility(visible = replyTarget != null) {
            if (replyTarget != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ответ на: ${replyTarget.plaintext.take(50)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onSetReplyTarget(null) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Reply", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Composer Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        onSendMessage(
                            "Зашифрованный контейнер файла (256B Quantum Block Padding)",
                            "FILE",
                            "aegis_keys_backup.bin • 4.2 KB"
                        )
                    },
                    modifier = Modifier.testTag("attach_encrypted_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Прикрепить зашифрованный файл",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        onSendMessage(
                            "Голосовая заметка [Opus 24kbps -> Aegis-52 Stream]",
                            "VOICE",
                            "Аудио 00:07 • Скрытие спектра"
                        )
                    },
                    modifier = Modifier.testTag("send_voice_note_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Отправить голосовую заметку",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = messageDraft,
                    onValueChange = { messageDraft = it },
                    placeholder = {
                        Text(
                            if (!conversation.peerIpAddress.isNullOrBlank()) "Сообщение в LAN (${conversation.peerIpAddress})..."
                            else "Зашифрованное сообщение Aegis-52..."
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("message_input_field")
                )

                Button(
                    onClick = {
                        if (messageDraft.isNotBlank()) {
                            onSendMessage(messageDraft, null, null)
                            messageDraft = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Отправить"
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateConversationModal(
    defaultLanIp: String,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String?, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("@") }
    var kind by remember { mutableStateOf("DM") } // DM, GROUP, CHANNEL, SECRET
    var peerLanIp by remember { mutableStateOf("") }
    var selfDestructSec by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Новый защищенный контур",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "DM" to "Личный",
                        "GROUP" to "Группа",
                        "CHANNEL" to "Канал",
                        "SECRET" to "Секрет"
                    ).forEach { (k, label) ->
                        FilterChip(
                            selected = kind == k,
                            onClick = {
                                kind = k
                                if (k == "SECRET" && selfDestructSec == 0) selfDestructSec = 60
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название чата / группы / канала") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_conv_title_input")
                )

                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text("@никнейм или телефон абонента") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_conv_handle_input")
                )

                OutlinedTextField(
                    value = peerLanIp,
                    onValueChange = { peerLanIp = it },
                    label = { Text("Опционально: LAN IP для прямой связи (напр. $defaultLanIp)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_conv_lan_ip_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        title.ifBlank { "Узел Aegis-52" },
                        handle,
                        kind,
                        peerLanIp.ifBlank { null },
                        selfDestructSec
                    )
                },
                modifier = Modifier.testTag("confirm_create_conversation_button")
            ) {
                Text("Создать контур")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun PacketInspectorDialog(
    message: MessageEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Крипто-Инспектор Пакета Aegis-52",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "То, что видят сервер, провайдер и создатель мессенджера (Zero-Knowledge Blind Frame):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "PROTOCOL: ${message.cipherSuite}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "ROUTE: ${message.transportRoute} (${message.latencyMs} ms)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "EPHEMERAL PUBKEY: ${message.ephemeralPubKey}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "NONCE (96-BIT): ${message.nonceHex}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "CIPHERTEXT (256B PADDED): ${message.ciphertextHex}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "HMAC-SHA512 TAG: ${message.macTagHex}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
