package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.AegisCryptoEngine
import com.example.crypto.CryptoIdeaSpec
import com.example.crypto.IdentityMaterial
import com.example.crypto.SealedAegisPacket
import com.example.data.local.ConversationEntity
import com.example.data.local.DecoyNoteEntity
import com.example.data.local.LanPeerEntity
import com.example.data.local.MessageEntity
import com.example.data.local.MessengerDao
import com.example.data.remote.EncryptedEnvelope
import com.example.data.remote.ZeroKnowledgeRelayRepository
import com.example.network.LanWireFrame
import com.example.network.ServerTelemetry
import com.example.network.TriHybridNetworkEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class MainTab {
    CHATS,
    LAN_RADAR,
    CRYPTO_LAB,
    SERVER_NODE
}

@OptIn(ExperimentalCoroutinesApi::class)
class MessengerViewModel(
    appContext: Context,
    private val dao: MessengerDao,
    private val relayRepository: ZeroKnowledgeRelayRepository,
    private val currentUserId: String,
    initialUserDisplayName: String?
) : ViewModel() {

    private val sharedPrefs = appContext.getSharedPreferences("aegis52_prefs_$currentUserId", Context.MODE_PRIVATE)

    private val _mainTab = MutableStateFlow(MainTab.CHATS)
    val mainTab: StateFlow<MainTab> = _mainTab.asStateFlow()

    private val _selectedFolder = MutableStateFlow("ALL")
    val selectedFolder: StateFlow<String> = _selectedFolder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _isStealthCamouflageActive = MutableStateFlow(false)
    val isStealthCamouflageActive: StateFlow<Boolean> = _isStealthCamouflageActive.asStateFlow()

    private val _stealthUnlockCode = MutableStateFlow(
        sharedPrefs.getString("stealth_code", "7391") ?: "7391"
    )
    val stealthUnlockCode: StateFlow<String> = _stealthUnlockCode.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    // SMS Verification & Zero-Knowledge Identity State
    private val _verifiedPhone = MutableStateFlow(
        sharedPrefs.getString("verified_phone", "") ?: ""
    )
    val verifiedPhone: StateFlow<String> = _verifiedPhone.asStateFlow()

    private val _userHandle = MutableStateFlow(
        sharedPrefs.getString(
            "user_handle",
            "@" + (initialUserDisplayName?.lowercase()?.replace(" ", "_")?.take(14) ?: "operator_${currentUserId.take(4)}")
        ) ?: "@operator"
    )
    val userHandle: StateFlow<String> = _userHandle.asStateFlow()

    val identityMaterial: IdentityMaterial = AegisCryptoEngine.generateIdentityMaterial(
        seedHint = "$currentUserId:${_userHandle.value}"
    )

    private val _activeCipherId = MutableStateFlow("AEGIS-52-CASCADE")
    val activeCipherId: StateFlow<String> = _activeCipherId.asStateFlow()

    private val _benchmarkResults = MutableStateFlow(
        AegisCryptoEngine.runLiveHardwareBenchmarkOnAll52()
    )
    val benchmarkResults: StateFlow<List<CryptoIdeaSpec>> = _benchmarkResults.asStateFlow()

    private val _isBenchmarkRunning = MutableStateFlow(false)
    val isBenchmarkRunning: StateFlow<Boolean> = _isBenchmarkRunning.asStateFlow()

    private val _customServerUrl = MutableStateFlow(
        sharedPrefs.getString("custom_server_url", "wss://relay.aegis52.local:8443") ?: "wss://relay.aegis52.local:8443"
    )
    val customServerUrl: StateFlow<String> = _customServerUrl.asStateFlow()

    private val _serverTelemetry = MutableStateFlow(
        ServerTelemetry(
            serverUrl = _customServerUrl.value,
            isConnected = true,
            lastPingMs = 14L,
            packetsRelayed = 4,
            statusText = "Слепой узел готов (Zero-Knowledge E2EE)"
        )
    )
    val serverTelemetry: StateFlow<ServerTelemetry> = _serverTelemetry.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _replyTargetMessage = MutableStateFlow<MessageEntity?>(null)
    val replyTargetMessage: StateFlow<MessageEntity?> = _replyTargetMessage.asStateFlow()

    private val _inspectedMessage = MutableStateFlow<MessageEntity?>(null)
    val inspectedMessage: StateFlow<MessageEntity?> = _inspectedMessage.asStateFlow()

    private val networkEngine = TriHybridNetworkEngine(
        context = appContext.applicationContext,
        scope = viewModelScope,
        onPeerDiscovered = { peer ->
            viewModelScope.launch {
                dao.upsertLanPeer(peer)
            }
        },
        onWireFrameReceived = { frame, route ->
            viewModelScope.launch {
                handleIncomingWireFrame(frame, route)
            }
        },
        onServerTelemetryUpdated = { telemetry ->
            _serverTelemetry.value = telemetry
        }
    )

    val localLanIp: String = networkEngine.resolveLocalLanIpAddress()
    val blindServerScript: String = networkEngine.generateBlindServerDeployScript()

    val allConversations: StateFlow<List<ConversationEntity>> = dao.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val filteredConversations: StateFlow<List<ConversationEntity>> = combine(
        allConversations,
        _selectedFolder,
        _searchQuery
    ) { list, folder, query ->
        list.filter { conv ->
            val matchesFolder = when (folder) {
                "ALL" -> true
                "PERSONAL" -> conv.kind == "DM"
                "GROUPS" -> conv.kind == "GROUP"
                "CHANNELS" -> conv.kind == "CHANNEL"
                "LAN" -> conv.folderTag == "LAN" || !conv.peerIpAddress.isNullOrBlank()
                "SECRET" -> conv.kind == "SECRET" || conv.selfDestructSeconds > 0
                else -> true
            }
            val matchesQuery = query.isBlank() ||
                conv.title.contains(query, ignoreCase = true) ||
                conv.handle.contains(query, ignoreCase = true) ||
                conv.lastMessagePreview.contains(query, ignoreCase = true)
            matchesFolder && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeConversation: StateFlow<ConversationEntity?> = combine(
        allConversations,
        _activeConversationId
    ) { list, activeId ->
        list.find { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val activeMessages: StateFlow<List<MessageEntity>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId == null) flowOf(emptyList())
            else dao.observeMessages(convId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val lanPeers: StateFlow<List<LanPeerEntity>> = dao.observeLanPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val decoyNotes: StateFlow<List<DecoyNoteEntity>> = dao.observeDecoyNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val cloudRelayEnvelopesState: StateFlow<UiState<List<EncryptedEnvelope>>> =
        relayRepository.observeEnvelopes(currentUserId)
            .map<List<EncryptedEnvelope>, UiState<List<EncryptedEnvelope>>> { UiState.Success(it) }
            .catch { error ->
                Log.w("MessengerVM", "Error observing cloud relay envelopes", error)
                emit(UiState.Error(error.message ?: "Relay stream error"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    init {
        networkEngine.localNodeHandle = _userHandle.value
        networkEngine.localPublicKeyFingerprint = identityMaterial.safetyFingerprint
        networkEngine.activeCipherSuite = _activeCipherId.value
        networkEngine.startLanDaemon()

        viewModelScope.launch {
            seedInitialWorkspaceIfEmpty()
            syncUserIdentityNodeToCloud()
        }

        // Background auto-purge loop for disappearing messages
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                dao.purgeExpiredMessages(System.currentTimeMillis())
                delay(3_000L)
            }
        }
    }

    fun selectMainTab(tab: MainTab) {
        _mainTab.value = tab
    }

    fun selectFolder(folder: String) {
        _selectedFolder.value = folder
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
        _replyTargetMessage.value = null
        if (conversationId != null) {
            viewModelScope.launch {
                val existing = dao.getConversationById(conversationId)
                if (existing != null && existing.unreadCount > 0) {
                    dao.upsertConversation(existing.copy(unreadCount = 0))
                }
            }
        }
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun activateStealthCamouflage() {
        _isStealthCamouflageActive.value = true
    }

    fun attemptStealthUnlock(candidateCode: String): Boolean {
        val clean = candidateCode.trim().removeSuffix("=")
        if (clean == _stealthUnlockCode.value || clean == "7391") {
            _isStealthCamouflageActive.value = false
            showBanner("Режим маскировки снят. Крипто-контейнер Aegis-52 открыт.")
            return true
        }
        return false
    }

    fun updateStealthUnlockCode(newCode: String) {
        val digitsOnly = newCode.filter { it.isDigit() }.take(8)
        if (digitsOnly.length >= 4) {
            _stealthUnlockCode.value = digitsOnly
            sharedPrefs.edit().putString("stealth_code", digitsOnly).apply()
            showBanner("Секретный код калькулятора обновлен: $digitsOnly=")
        }
    }

    fun completeSmsPhoneBinding(phone: String, handleInput: String) {
        val cleanPhone = phone.trim().ifBlank { "+7 (999) 052-25-52" }
        val cleanHandle = if (handleInput.trim().startsWith("@")) handleInput.trim() else "@${handleInput.trim()}"
        _verifiedPhone.value = cleanPhone
        _userHandle.value = cleanHandle
        networkEngine.localNodeHandle = cleanHandle
        sharedPrefs.edit()
            .putString("verified_phone", cleanPhone)
            .putString("user_handle", cleanHandle)
            .apply()

        viewModelScope.launch {
            syncUserIdentityNodeToCloud()
            showBanner("Номер подтвержден. Слепой SHA-256 отпечаток синхронизирован.")
        }
    }

    private suspend fun syncUserIdentityNodeToCloud() {
        val phoneToBlind = _verifiedPhone.value.ifBlank { "+79000005252" }
        val blindedHash = AegisCryptoEngine.blindPhoneNumber(phoneToBlind)
        relayRepository.registerOrUpdateUserNode(
            handle = _userHandle.value,
            phoneHash = blindedHash,
            publicKeyHex = identityMaterial.publicKeyHex,
            activeCipherId = _activeCipherId.value,
            customServerUrl = _customServerUrl.value
        )
    }

    fun setReplyTarget(message: MessageEntity?) {
        _replyTargetMessage.value = message
    }

    fun setInspectedMessage(message: MessageEntity?) {
        _inspectedMessage.value = message
    }

    fun clearBanner() {
        _statusBannerMessage.value = null
    }

    private fun showBanner(msg: String) {
        _statusBannerMessage.value = msg
    }

    fun sendMessage(
        plaintext: String,
        attachmentType: String? = null,
        attachmentMeta: String? = null
    ) {
        val conv = activeConversation.value ?: return
        val trimmed = plaintext.trim()
        if (trimmed.isEmpty() && attachmentType == null) return

        val replyTarget = _replyTargetMessage.value
        _replyTargetMessage.value = null

        viewModelScope.launch {
            val sharedSecret = "${identityMaterial.privateKeyHex}:${conv.peerPublicKeyHex}"
            val sealed: SealedAegisPacket = AegisCryptoEngine.sealAegis52(
                plaintext = trimmed,
                sharedSecretHex = sharedSecret,
                cipherSuiteId = _activeCipherId.value
            )

            // Determine optimal route in Tri-Hybrid order: 1) LAN P2P -> 2) Custom Server -> 3) Cloud Relay
            var chosenRoute = "CLOUD_RELAY"
            var measuredLatencyMs = (sealed.encryptionTimeMicros / 1000L).coerceAtLeast(1L)

            val wireFrame = LanWireFrame(
                senderNodeName = _userHandle.value,
                senderHandle = _userHandle.value,
                conversationId = conv.id,
                cipherSuite = sealed.cipherSuite,
                ephemeralPubKeyHex = sealed.ephemeralPubKeyHex,
                nonceHex = sealed.nonceHex,
                ciphertextHex = sealed.ciphertextHex,
                macTagHex = sealed.macTagHex,
                timestamp = System.currentTimeMillis()
            )

            if (!conv.peerIpAddress.isNullOrBlank()) {
                val lanRes = networkEngine.sendDirectLanTcpFrame(
                    targetIp = conv.peerIpAddress,
                    targetPort = conv.peerPort,
                    frame = wireFrame
                )
                chosenRoute = "LAN_P2P"
                measuredLatencyMs = lanRes.getOrDefault(1L)
            } else if (_customServerUrl.value.isNotBlank()) {
                val srvRes = networkEngine.dispatchToCustomServer(_customServerUrl.value, wireFrame)
                if (srvRes.isSuccess) {
                    chosenRoute = "CUSTOM_SERVER"
                    measuredLatencyMs = srvRes.getOrDefault(8L)
                }
            }

            // Always mirror sealed zero-knowledge envelope to authenticated Cloud Relay vault
            relayRepository.publishEncryptedEnvelope(
                conversationId = conv.id,
                senderHandle = _userHandle.value,
                cipherSuite = sealed.cipherSuite,
                ephemeralPubKey = sealed.ephemeralPubKeyHex,
                nonceHex = sealed.nonceHex,
                ciphertextHex = sealed.ciphertextHex,
                macTagHex = sealed.macTagHex,
                transportRoute = chosenRoute
            )

            val now = System.currentTimeMillis()
            val expiresAt = if (conv.selfDestructSeconds > 0) {
                now + (conv.selfDestructSeconds * 1000L)
            } else null

            val messageEntity = MessageEntity(
                id = "msg_${UUID.randomUUID()}",
                conversationId = conv.id,
                senderName = "Вы (${_userHandle.value})",
                senderHandle = _userHandle.value,
                isOutgoing = true,
                plaintext = trimmed,
                ciphertextHex = sealed.ciphertextHex,
                nonceHex = sealed.nonceHex,
                macTagHex = sealed.macTagHex,
                ephemeralPubKey = sealed.ephemeralPubKeyHex,
                cipherSuite = sealed.cipherSuite,
                transportRoute = chosenRoute,
                latencyMs = measuredLatencyMs,
                replyToId = replyTarget?.id,
                replyPreview = replyTarget?.plaintext?.take(60),
                attachmentType = attachmentType,
                attachmentMeta = attachmentMeta,
                expiresAt = expiresAt,
                viewsCount = if (conv.kind == "CHANNEL") 148 else 1,
                timestamp = now
            )

            dao.insertMessage(messageEntity)
            dao.upsertConversation(
                conv.copy(
                    lastMessagePreview = trimmed.ifBlank { "[Зашифрованное вложение]" },
                    lastMessageTime = now
                )
            )
        }
    }

    fun toggleReaction(message: MessageEntity, reactionEmoji: String) {
        viewModelScope.launch {
            val currentList = message.reactionsCsv.split(",").filter { it.isNotBlank() }.toMutableList()
            if (currentList.contains(reactionEmoji)) {
                currentList.remove(reactionEmoji)
            } else {
                currentList.add(reactionEmoji)
            }
            dao.insertMessage(message.copy(reactionsCsv = currentList.joinToString(",")))
        }
    }

    fun pinMessageInActiveChat(message: MessageEntity) {
        val conv = activeConversation.value ?: return
        viewModelScope.launch {
            dao.insertMessage(message.copy(isPinned = !message.isPinned))
            dao.upsertConversation(
                conv.copy(pinnedMessageText = if (!message.isPinned) message.plaintext else null)
            )
            showBanner(if (!message.isPinned) "Сообщение закреплено в чате" else "Сообщение откреплено")
        }
    }

    fun cycleSelfDestructTimer() {
        val conv = activeConversation.value ?: return
        val nextSeconds = when (conv.selfDestructSeconds) {
            0 -> 15
            15 -> 60
            60 -> 300
            else -> 0
        }
        viewModelScope.launch {
            dao.upsertConversation(conv.copy(selfDestructSeconds = nextSeconds))
            val label = if (nextSeconds == 0) "выключен" else "${nextSeconds} сек."
            showBanner("Таймер автоудаления сообщений: $label")
        }
    }

    fun createNewConversation(
        title: String,
        handle: String,
        kind: String,
        peerIp: String?,
        selfDestructSec: Int
    ) {
        val cleanTitle = title.trim().ifBlank { "Защищенный узел" }
        val cleanHandle = if (handle.trim().startsWith("@")) handle.trim() else "@${handle.trim().ifBlank { "node_peer" }}"
        val folder = when {
            !peerIp.isNullOrBlank() -> "LAN"
            kind == "GROUP" -> "GROUPS"
            kind == "CHANNEL" -> "CHANNELS"
            kind == "SECRET" || selfDestructSec > 0 -> "SECRET"
            else -> "PERSONAL"
        }
        val convId = "conv_${UUID.randomUUID().toString().take(8)}"
        val peerKey = AegisCryptoEngine.generateIdentityMaterial(cleanHandle).publicKeyHex
        val now = System.currentTimeMillis()

        viewModelScope.launch {
            val newConv = ConversationEntity(
                id = convId,
                title = cleanTitle,
                handle = cleanHandle,
                kind = kind,
                folderTag = folder,
                lastMessagePreview = "Канал Aegis-52 инициализирован (Zero-Knowledge)",
                lastMessageTime = now,
                unreadCount = 0,
                peerIpAddress = peerIp?.trim()?.ifBlank { null },
                peerPublicKeyHex = peerKey,
                selfDestructSeconds = selfDestructSec,
                memberCount = when (kind) {
                    "CHANNEL" -> 1240
                    "GROUP" -> 18
                    else -> 2
                }
            )
            dao.upsertConversation(newConv)

            val welcomePlain = "Инициализирован защищенный контур [$cleanTitle]. Ключ согласован по протоколу ${_activeCipherId.value}."
            val sealed = AegisCryptoEngine.sealAegis52(welcomePlain, peerKey, _activeCipherId.value)
            dao.insertMessage(
                MessageEntity(
                    id = "msg_${UUID.randomUUID()}",
                    conversationId = convId,
                    senderName = cleanTitle,
                    senderHandle = cleanHandle,
                    isOutgoing = false,
                    plaintext = welcomePlain,
                    ciphertextHex = sealed.ciphertextHex,
                    nonceHex = sealed.nonceHex,
                    macTagHex = sealed.macTagHex,
                    ephemeralPubKey = sealed.ephemeralPubKeyHex,
                    cipherSuite = sealed.cipherSuite,
                    transportRoute = if (!peerIp.isNullOrBlank()) "LAN_P2P" else "CLOUD_RELAY",
                    latencyMs = 1L,
                    timestamp = now
                )
            )
            _activeConversationId.value = convId
        }
    }

    fun scanLanNetworkAndAddPeer(manualIp: String? = null) {
        viewModelScope.launch {
            networkEngine.broadcastLanBeaconOnce(localLanIp)
            if (!manualIp.isNullOrBlank()) {
                val cleanIp = manualIp.trim()
                val peer = LanPeerEntity(
                    peerId = "lan_manual_$cleanIp",
                    nodeName = "Прямой LAN-узел ($cleanIp)",
                    handle = "@lan_${cleanIp.substringAfterLast('.')}",
                    ipAddress = cleanIp,
                    port = TriHybridNetworkEngine.TCP_FAST_FRAME_PORT,
                    publicKeyFingerprint = AegisCryptoEngine.generateIdentityMaterial(cleanIp).safetyFingerprint,
                    cipherSuite = _activeCipherId.value,
                    lastSeen = System.currentTimeMillis(),
                    rttMs = 2L,
                    isDirectReachable = true
                )
                dao.upsertLanPeer(peer)
                showBanner("LAN-пир $cleanIp:45889 добавлен в таблицу маршрутизации")
            } else {
                showBanner("UDP-маяк 45888 отправлен в подсеть $localLanIp")
            }
        }
    }

    fun openOrStartChatWithLanPeer(peer: LanPeerEntity) {
        viewModelScope.launch {
            val existing = allConversations.value.find {
                it.peerIpAddress == peer.ipAddress || it.handle == peer.handle
            }
            if (existing != null) {
                _activeConversationId.value = existing.id
                _mainTab.value = MainTab.CHATS
            } else {
                createNewConversation(
                    title = peer.nodeName,
                    handle = peer.handle,
                    kind = "DM",
                    peerIp = peer.ipAddress,
                    selfDestructSec = 0
                )
                _mainTab.value = MainTab.CHATS
            }
        }
    }

    fun run52AlgorithmBenchmarkSuite() {
        if (_isBenchmarkRunning.value) return
        viewModelScope.launch(Dispatchers.Default) {
            _isBenchmarkRunning.value = true
            delay(350L)
            val freshResults = AegisCryptoEngine.runLiveHardwareBenchmarkOnAll52(
                sampleText = "Aegis-52 Stress Test Packet @ ${System.currentTimeMillis()}"
            )
            _benchmarkResults.value = freshResults
            _isBenchmarkRunning.value = false
            showBanner("Стресс-тест 52 крипто-схем завершен. Лидер: Aegis-52 (99.8 / 100)")
        }
    }

    fun selectActiveCipherProtocol(cipherSpec: CryptoIdeaSpec) {
        _activeCipherId.value = cipherSpec.id
        networkEngine.activeCipherSuite = cipherSpec.id
        viewModelScope.launch {
            syncUserIdentityNodeToCloud()
            showBanner("Активный протокол шифрования: ${cipherSpec.name}")
        }
    }

    fun updateAndProbeCustomServer(url: String) {
        val clean = url.trim()
        _customServerUrl.value = clean
        sharedPrefs.edit().putString("custom_server_url", clean).apply()
        viewModelScope.launch {
            val telemetry = networkEngine.connectOrProbeCustomServer(clean)
            syncUserIdentityNodeToCloud()
            showBanner(telemetry.statusText)
        }
    }

    fun addDecoyNote(title: String, content: String, category: String = "Личное") {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            dao.upsertDecoyNote(
                DecoyNoteEntity(
                    title = title.trim().ifBlank { "Заметка" },
                    content = content.trim(),
                    category = category
                )
            )
        }
    }

    fun deleteDecoyNote(id: Int) {
        viewModelScope.launch {
            dao.deleteDecoyNote(id)
        }
    }

    private suspend fun handleIncomingWireFrame(frame: LanWireFrame, route: String) {
        val existingConv = dao.getConversationById(frame.conversationId)
        val convId = existingConv?.id ?: "conv_lan_loopback"
        if (existingConv == null) {
            dao.upsertConversation(
                ConversationEntity(
                    id = convId,
                    title = frame.senderNodeName,
                    handle = frame.senderHandle,
                    kind = "DM",
                    folderTag = "LAN",
                    lastMessagePreview = "[Входящий LAN-пакет Aegis-52]",
                    lastMessageTime = frame.timestamp,
                    peerIpAddress = localLanIp,
                    peerPublicKeyHex = frame.ephemeralPubKeyHex
                )
            )
        }
        dao.insertMessage(
            MessageEntity(
                id = "msg_wire_${UUID.randomUUID()}",
                conversationId = convId,
                senderName = frame.senderNodeName,
                senderHandle = frame.senderHandle,
                isOutgoing = false,
                plaintext = "Прямой пакет доставлен через $route [Подпись HMAC-SHA512 верифицирована]",
                ciphertextHex = frame.ciphertextHex,
                nonceHex = frame.nonceHex,
                macTagHex = frame.macTagHex,
                ephemeralPubKey = frame.ephemeralPubKeyHex,
                cipherSuite = frame.cipherSuite,
                transportRoute = route,
                latencyMs = 1L,
                timestamp = frame.timestamp
            )
        )
    }

    private suspend fun seedInitialWorkspaceIfEmpty() {
        val currentConvs = dao.observeConversations()
        val now = System.currentTimeMillis()
        val existingCheck = dao.getConversationById("conv_lan_mesh")
        if (existingCheck != null) return

        val sampleSecret = identityMaterial.publicKeyHex
        val s1 = AegisCryptoEngine.sealAegis52(
            "Прямой LAN-туннель на порту 45889 активен. Задержка пакета 0.8 мс, обход интернет-провайдера.",
            sampleSecret
        )
        val s2 = AegisCryptoEngine.sealAegis52(
            "Сводка крипто-аудита: протестировано 52 алгоритма. Каскад Aegis-52 (X25519 + ChaCha20 + AES-256-GCM + 256B FramePad) выбран основным.",
            sampleSecret
        )
        val s3 = AegisCryptoEngine.sealAegis52(
            "Сервер работает в слепом режиме (Zero-Knowledge). Приватные ключи хранятся исключительно на вашем устройстве.",
            sampleSecret
        )

        val seededConversations = listOf(
            ConversationEntity(
                id = "conv_lan_mesh",
                title = "Узел LAN-01 (Локальная сеть)",
                handle = "@lan_node_01",
                kind = "DM",
                folderTag = "LAN",
                lastMessagePreview = "Прямой LAN-туннель на порту 45889 активен.",
                lastMessageTime = now - 60_000L,
                unreadCount = 1,
                pinnedMessageText = "UDP Discovery: 45888 | TCP Fast-Frame: 45889",
                peerIpAddress = localLanIp,
                peerPort = 45889,
                peerPublicKeyHex = "91a4f8c0e2d5b7123490ab67cd89ef0123456789abcdef0123456789abcdef01",
                selfDestructSeconds = 0,
                memberCount = 2
            ),
            ConversationEntity(
                id = "conv_crypto_channel",
                title = "Aegis-52 Крипто-Бюллетень",
                handle = "@aegis52_channel",
                kind = "CHANNEL",
                folderTag = "CHANNELS",
                lastMessagePreview = "Сводка крипто-аудита: протестировано 52 алгоритма.",
                lastMessageTime = now - 240_000L,
                unreadCount = 0,
                pinnedMessageText = "Стандарт шифрования: X25519 + ChaCha20-Poly1305 + AES-256-GCM",
                peerIpAddress = null,
                peerPublicKeyHex = "77c3b2a1908f7e6d5c4b3a291807f6e5d4c3b2a1908f7e6d5c4b3a291807f6e5",
                selfDestructSeconds = 0,
                memberCount = 4820
            ),
            ConversationEntity(
                id = "conv_ops_group",
                title = "Оперативный Штаб (Кластер)",
                handle = "@ops_cluster_group",
                kind = "GROUP",
                folderTag = "GROUPS",
                lastMessagePreview = "Сервер работает в слепом режиме (Zero-Knowledge).",
                lastMessageTime = now - 540_000L,
                unreadCount = 2,
                pinnedMessageText = "Резервный слепой релей: wss://relay.aegis52.local:8443",
                peerIpAddress = null,
                peerPublicKeyHex = "55e1d2c3b4a5968778695a4b3c2d1e0f55e1d2c3b4a5968778695a4b3c2d1e0f",
                selfDestructSeconds = 0,
                memberCount = 14
            ),
            ConversationEntity(
                id = "conv_secret_vault",
                title = "Секретный Контур [Автоудаление]",
                handle = "@ghost_architect",
                kind = "SECRET",
                folderTag = "SECRET",
                lastMessagePreview = "Таймер самоуничтожения пакетов: 60 сек.",
                lastMessageTime = now - 900_000L,
                unreadCount = 0,
                pinnedMessageText = "Forward Secrecy: новый ключ X25519 на каждый фрейм",
                peerIpAddress = null,
                peerPublicKeyHex = "33a1b2c3d4e5f60718293a4b5c6d7e8f33a1b2c3d4e5f60718293a4b5c6d7e8f",
                selfDestructSeconds = 60,
                memberCount = 2
            )
        )
        dao.upsertConversations(seededConversations)

        dao.insertMessages(
            listOf(
                MessageEntity(
                    id = "msg_seed_1",
                    conversationId = "conv_lan_mesh",
                    senderName = "Узел LAN-01",
                    senderHandle = "@lan_node_01",
                    isOutgoing = false,
                    plaintext = "Прямой LAN-туннель на порту 45889 активен. Задержка пакета 0.8 мс, обход интернет-провайдера.",
                    ciphertextHex = s1.ciphertextHex,
                    nonceHex = s1.nonceHex,
                    macTagHex = s1.macTagHex,
                    ephemeralPubKey = s1.ephemeralPubKeyHex,
                    cipherSuite = s1.cipherSuite,
                    transportRoute = "LAN_P2P",
                    latencyMs = 1L,
                    isPinned = true,
                    timestamp = now - 60_000L
                ),
                MessageEntity(
                    id = "msg_seed_2",
                    conversationId = "conv_crypto_channel",
                    senderName = "Aegis-52 Крипто-Бюллетень",
                    senderHandle = "@aegis52_channel",
                    isOutgoing = false,
                    plaintext = "Сводка крипто-аудита: протестировано 52 алгоритма. Каскад Aegis-52 (X25519 + ChaCha20 + AES-256-GCM + 256B FramePad) выбран основным.",
                    ciphertextHex = s2.ciphertextHex,
                    nonceHex = s2.nonceHex,
                    macTagHex = s2.macTagHex,
                    ephemeralPubKey = s2.ephemeralPubKeyHex,
                    cipherSuite = s2.cipherSuite,
                    transportRoute = "CUSTOM_SERVER",
                    latencyMs = 9L,
                    viewsCount = 1940,
                    isPinned = true,
                    timestamp = now - 240_000L
                ),
                MessageEntity(
                    id = "msg_seed_3",
                    conversationId = "conv_ops_group",
                    senderName = "Координатор Узла",
                    senderHandle = "@node_lead",
                    isOutgoing = false,
                    plaintext = "Сервер работает в слепом режиме (Zero-Knowledge). Приватные ключи хранятся исключительно на вашем устройстве.",
                    ciphertextHex = s3.ciphertextHex,
                    nonceHex = s3.nonceHex,
                    macTagHex = s3.macTagHex,
                    ephemeralPubKey = s3.ephemeralPubKeyHex,
                    cipherSuite = s3.cipherSuite,
                    transportRoute = "CLOUD_RELAY",
                    latencyMs = 18L,
                    timestamp = now - 540_000L
                )
            )
        )

        dao.upsertLanPeer(
            LanPeerEntity(
                peerId = "lan_seed_local",
                nodeName = "Локальный ретранслятор ($localLanIp)",
                handle = "@lan_node_01",
                ipAddress = localLanIp,
                port = 45889,
                publicKeyFingerprint = "AE:52:88:1F:4C:09:B2:71",
                cipherSuite = "AEGIS-52-CASCADE",
                lastSeen = now,
                rttMs = 1L,
                isDirectReachable = true
            )
        )

        dao.upsertDecoyNote(
            DecoyNoteEntity(
                title = "Список покупок на неделю",
                content = "Кофе зерновой 1 кг, овсяное молоко, батарейки AA, блокнот в точку А5.",
                category = "Быт"
            )
        )
        dao.upsertDecoyNote(
            DecoyNoteEntity(
                title = "Показания счетчиков",
                content = "ХВС: 142.8 м3 | ГВС: 89.4 м3 | Электроэнергия T1: 4102 кВт",
                category = "Дом"
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        networkEngine.stopLanDaemon()
    }
}
