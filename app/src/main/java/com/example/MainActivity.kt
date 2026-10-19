package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.MessengerDatabase
import com.example.data.remote.ZeroKnowledgeRelayRepository
import com.example.ui.AuthGateScreen
import com.example.ui.ChatsWorkspaceScreen
import com.example.ui.CryptoLab52Screen
import com.example.ui.LanRadarScreen
import com.example.ui.MainTab
import com.example.ui.MessengerViewModel
import com.example.ui.ServerControlNodeScreen
import com.example.ui.StealthCamouflageScreen
import com.example.ui.signOutAndClearCredentials
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRootNavigation()
        }
    }
}

@Composable
fun AppRootNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        MyApplicationTheme(darkTheme = true) {
            AuthGateScreen(onAuthSuccess = {})
        }
    } else {
        val viewModel: MessengerViewModel = viewModel(
            key = user.uid,
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY]) {
                        "APPLICATION_KEY missing from CreationExtras"
                    }
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val firestore = FirebaseFirestore.getInstance(databaseId)
                    val roomDb = MessengerDatabase.getInstance(app)
                    MessengerViewModel(
                        appContext = app,
                        dao = roomDb.dao(),
                        relayRepository = ZeroKnowledgeRelayRepository(firestore, auth),
                        currentUserId = user.uid,
                        initialUserDisplayName = user.displayName
                    )
                }
            }
        )
        val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
        MyApplicationTheme(darkTheme = isDark) {
            AuthenticatedMessengerShell(viewModel = viewModel)
        }
    }
}

@Composable
fun AuthenticatedMessengerShell(viewModel: MessengerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isStealthCamouflage by viewModel.isStealthCamouflageActive.collectAsStateWithLifecycle()
    val stealthCode by viewModel.stealthUnlockCode.collectAsStateWithLifecycle()
    val decoyNotes by viewModel.decoyNotes.collectAsStateWithLifecycle()

    if (isStealthCamouflage) {
        StealthCamouflageScreen(
            decoyNotes = decoyNotes,
            secretUnlockCode = stealthCode,
            onAttemptUnlock = { candidate -> viewModel.attemptStealthUnlock(candidate) },
            onAddNote = { title, body -> viewModel.addDecoyNote(title, body) },
            onDeleteNote = { id -> viewModel.deleteDecoyNote(id) }
        )
        return
    }

    val mainTab by viewModel.mainTab.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val conversations by viewModel.filteredConversations.collectAsStateWithLifecycle()
    val activeConversation by viewModel.activeConversation.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val replyTarget by viewModel.replyTargetMessage.collectAsStateWithLifecycle()
    val inspectedMessage by viewModel.inspectedMessage.collectAsStateWithLifecycle()
    val activeCipherId by viewModel.activeCipherId.collectAsStateWithLifecycle()
    val benchmarkResults by viewModel.benchmarkResults.collectAsStateWithLifecycle()
    val isBenchmarkRunning by viewModel.isBenchmarkRunning.collectAsStateWithLifecycle()
    val lanPeers by viewModel.lanPeers.collectAsStateWithLifecycle()
    val customServerUrl by viewModel.customServerUrl.collectAsStateWithLifecycle()
    val serverTelemetry by viewModel.serverTelemetry.collectAsStateWithLifecycle()
    val userHandle by viewModel.userHandle.collectAsStateWithLifecycle()
    val verifiedPhone by viewModel.verifiedPhone.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Aegis-52",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "AEGIS-52",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "LAN ${viewModel.localLanIp} • ZERO-KNOWLEDGE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // One-tap Stealth Camouflage Disguise button
                            OutlinedButton(
                                onClick = { viewModel.activateStealthCamouflage() },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("activate_stealth_camouflage_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = "Маскировка",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Маскировка", style = MaterialTheme.typography.labelSmall)
                            }

                            IconButton(
                                onClick = { viewModel.toggleTheme() },
                                modifier = Modifier.testTag("toggle_theme_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Contrast,
                                    contentDescription = "Сменить тему",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    signOutAndClearCredentials(
                                        context = context,
                                        onSignOutComplete = {},
                                        scope = scope
                                    )
                                },
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Выйти",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = statusBanner != null) {
                        if (statusBanner != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
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
                                        text = statusBanner ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "ОК",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .testTag("dismiss_banner_button")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = mainTab == MainTab.CHATS,
                    onClick = { viewModel.selectMainTab(MainTab.CHATS) },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Чаты") },
                    label = { Text("Чаты") },
                    modifier = Modifier.testTag("nav_tab_chats")
                )
                NavigationBarItem(
                    selected = mainTab == MainTab.LAN_RADAR,
                    onClick = { viewModel.selectMainTab(MainTab.LAN_RADAR) },
                    icon = { Icon(Icons.Default.Radar, contentDescription = "LAN Сеть") },
                    label = { Text("LAN P2P") },
                    modifier = Modifier.testTag("nav_tab_lan")
                )
                NavigationBarItem(
                    selected = mainTab == MainTab.CRYPTO_LAB,
                    onClick = { viewModel.selectMainTab(MainTab.CRYPTO_LAB) },
                    icon = { Icon(Icons.Default.Memory, contentDescription = "52 Шифра") },
                    label = { Text("52 Шифра") },
                    modifier = Modifier.testTag("nav_tab_crypto_lab")
                )
                NavigationBarItem(
                    selected = mainTab == MainTab.SERVER_NODE,
                    onClick = { viewModel.selectMainTab(MainTab.SERVER_NODE) },
                    icon = { Icon(Icons.Default.Dns, contentDescription = "Сервер") },
                    label = { Text("Сервер") },
                    modifier = Modifier.testTag("nav_tab_server")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (mainTab) {
                MainTab.CHATS -> {
                    ChatsWorkspaceScreen(
                        conversations = conversations,
                        activeConversation = activeConversation,
                        activeMessages = activeMessages,
                        selectedFolder = selectedFolder,
                        searchQuery = searchQuery,
                        replyTargetMessage = replyTarget,
                        inspectedMessage = inspectedMessage,
                        activeCipherId = activeCipherId,
                        localLanIp = viewModel.localLanIp,
                        onSelectFolder = { viewModel.selectFolder(it) },
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onOpenConversation = { viewModel.openConversation(it) },
                        onSendMessage = { text, type, meta -> viewModel.sendMessage(text, type, meta) },
                        onSetReplyTarget = { viewModel.setReplyTarget(it) },
                        onToggleReaction = { msg, emoji -> viewModel.toggleReaction(msg, emoji) },
                        onPinMessage = { viewModel.pinMessageInActiveChat(it) },
                        onCycleSelfDestruct = { viewModel.cycleSelfDestructTimer() },
                        onInspectMessage = { viewModel.setInspectedMessage(it) },
                        onCreateConversation = { title, handle, kind, ip, sec ->
                            viewModel.createNewConversation(title, handle, kind, ip, sec)
                        }
                    )
                }

                MainTab.LAN_RADAR -> {
                    BackHandler { viewModel.selectMainTab(MainTab.CHATS) }
                    LanRadarScreen(
                        localLanIp = viewModel.localLanIp,
                        lanPeers = lanPeers,
                        onScanNetwork = { manualIp -> viewModel.scanLanNetworkAndAddPeer(manualIp) },
                        onOpenPeerChat = { peer -> viewModel.openOrStartChatWithLanPeer(peer) }
                    )
                }

                MainTab.CRYPTO_LAB -> {
                    BackHandler { viewModel.selectMainTab(MainTab.CHATS) }
                    CryptoLab52Screen(
                        algorithms = benchmarkResults,
                        activeCipherId = activeCipherId,
                        isBenchmarkRunning = isBenchmarkRunning,
                        onRunBenchmark = { viewModel.run52AlgorithmBenchmarkSuite() },
                        onSelectCipher = { spec -> viewModel.selectActiveCipherProtocol(spec) }
                    )
                }

                MainTab.SERVER_NODE -> {
                    BackHandler { viewModel.selectMainTab(MainTab.CHATS) }
                    ServerControlNodeScreen(
                        customServerUrl = customServerUrl,
                        telemetry = serverTelemetry,
                        blindServerScript = viewModel.blindServerScript,
                        userHandle = userHandle,
                        verifiedPhone = verifiedPhone,
                        safetyFingerprint = viewModel.identityMaterial.safetyFingerprint,
                        recoveryPhrase = viewModel.identityMaterial.recoveryPhrase,
                        stealthCode = stealthCode,
                        onUpdateServerUrl = { url -> viewModel.updateAndProbeCustomServer(url) },
                        onUpdatePhoneAndHandle = { phone, handle -> viewModel.completeSmsPhoneBinding(phone, handle) },
                        onUpdateStealthCode = { code -> viewModel.updateStealthUnlockCode(code) }
                    )
                }
            }
        }
    }
}
