package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.AegisCryptoEngine
import com.example.crypto.CryptoIdeaSpec
import com.example.data.local.LanPeerEntity
import com.example.network.ServerTelemetry
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun CryptoLab52Screen(
    algorithms: List<CryptoIdeaSpec>,
    activeCipherId: String,
    isBenchmarkRunning: Boolean,
    onRunBenchmark: () -> Unit,
    onSelectCipher: (CryptoIdeaSpec) -> Unit
) {
    var selectedGeneration by remember { mutableStateOf("ВСЕ (52)") }
    var sandboxInput by remember { mutableStateOf("Секретный пакет LAN P2P -> Aegis-52 Cascade") }
    val sampleSealed = remember(sandboxInput, activeCipherId) {
        AegisCryptoEngine.sealAegis52(
            plaintext = sandboxInput.ifBlank { "AEGIS-52" },
            sharedSecretHex = "8f40c5adb68f25624ae5b214ea767a6ec94d829d3d7b5e1ad1ba6f3e2138285f",
            cipherSuiteId = activeCipherId
        )
    }

    val generations = listOf(
        "ВСЕ (52)",
        "Gen 5: Гипер-Каскады",
        "Gen 4: Стелс и LAN",
        "Gen 3: Постквантовые",
        "Gen 2: AEAD & Ratchet",
        "Gen 1: Базовые блочные"
    )

    val filteredAlgorithms = remember(algorithms, selectedGeneration) {
        if (selectedGeneration == "ВСЕ (52)") algorithms
        else algorithms.filter { it.generation == selectedGeneration }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            // Winner Banner Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "ПОБЕДИТЕЛЬ ИЗ 52 ИДЕЙ: ПРОТОКОЛ AEGIS-52",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Text(
                            text = "RANK 99.8 / 100",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "X25519 Ephemeral Ratchet + ChaCha20-Poly1305 + AES-256-GCM + HKDF-SHA512 + 256B FramePad",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Двухслойный каскад независимых примитивов (потоковый ARX + аппаратный AES-GCM) с выравниванием пакетов до 256 байт. Даже при компрометации одного стандарта второй слой и эфемерный Ratchet сохраняют 100% секретность.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onRunBenchmark,
                        enabled = !isBenchmarkRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_52_benchmark_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isBenchmarkRunning) "Тестирование 52 алгоритмов на CPU..."
                            else "Запустить живой стресс-тест 52 крипто-схем"
                        )
                    }

                    if (isBenchmarkRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        // Live Cipher Sandbox
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ЖИВОЙ ИНСПЕКТОР КАСКАДНОГО ШИФРОВАНИЯ (${activeCipherId})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sandboxInput,
                        onValueChange = { sandboxInput = it },
                        label = { Text("Введите текст для мгновенной упаковки в фрейм Aegis-52") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("crypto_sandbox_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "EPHEMERAL KEY: ${sampleSealed.ephemeralPubKeyHex}  |  NONCE: ${sampleSealed.nonceHex}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "CIPHERTEXT (${sampleSealed.paddedBytesSize}B PADDED): ${sampleSealed.ciphertextHex.take(72)}...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "HMAC-SHA512 TAG: ${sampleSealed.macTagHex.take(48)}...  |  TIME: ${sampleSealed.encryptionTimeMicros} мкс",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // Generation Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                generations.forEach { gen ->
                    FilterChip(
                        selected = selectedGeneration == gen,
                        onClick = { selectedGeneration = gen },
                        label = { Text(gen, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        // List of 52 evaluated algorithms
        items(filteredAlgorithms, key = { it.id }) { spec ->
            val isSelected = spec.id == activeCipherId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectCipher(spec) }
                    .testTag("cipher_card_${spec.index}"),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (spec.isWinner || isSelected) 1.5.dp else 1.dp,
                    color = if (spec.isWinner) MaterialTheme.colorScheme.tertiary
                    else if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = "#${spec.index}",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column {
                                Text(
                                    text = spec.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${spec.generation} • Ключ ${spec.keySizeBits} бит",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            color = if (spec.isWinner) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${spec.overallSecurityRank}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (spec.isWinner) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = spec.architecture,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Скорость: ${spec.measuredThroughputMBps} МБ/с",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Задержка: ${spec.measuredLatencyMicros} мкс",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Энтропия: ${spec.measuredEntropyBits} бит/Б",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun LanRadarScreen(
    localLanIp: String,
    lanPeers: List<LanPeerEntity>,
    onScanNetwork: (String?) -> Unit,
    onOpenPeerChat: (LanPeerEntity) -> Unit
) {
    var manualIpInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "ПРЯМОЙ LAN P2P ТРАНСПОРТ (БЕЗ ИНТЕРНЕТА)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Локальный адрес узла: $localLanIp • UDP Beacon: 45888 • TCP Fast-Frame: 45889",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Устройства в одной Wi-Fi / LAN сети обнаруживают друг друга автоматически по зашифрованным UDP-маякам и передают пакеты напрямую сокетами минуя серверы.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualIpInput,
                            onValueChange = { manualIpInput = it },
                            label = { Text("IP абонента в LAN (напр. 192.168.1.42)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lan_manual_ip_input")
                        )
                        Button(
                            onClick = {
                                onScanNetwork(manualIpInput.ifBlank { null })
                                manualIpInput = ""
                            },
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("lan_scan_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Маяк")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "ОБНАРУЖЕННЫЕ АБОНЕНТЫ В ЛОКАЛЬНОЙ СЕТИ (${lanPeers.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(lanPeers, key = { it.peerId }) { peer ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPeerChat(peer) }
                    .testTag("lan_peer_card_${peer.peerId}"),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = peer.nodeName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                            ) {
                                Text(
                                    text = "${peer.rttMs} ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${peer.handle} • ${peer.ipAddress}:${peer.port}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "FP: ${peer.publicKeyFingerprint} • ${peer.cipherSuite}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { onOpenPeerChat(peer) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("connect_lan_peer_button_${peer.peerId}")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LAN Чат")
                    }
                }
            }
        }
    }
}

@Composable
fun ServerControlNodeScreen(
    customServerUrl: String,
    telemetry: ServerTelemetry,
    blindServerScript: String,
    userHandle: String,
    verifiedPhone: String,
    safetyFingerprint: String,
    recoveryPhrase: String,
    stealthCode: String,
    onUpdateServerUrl: (String) -> Unit,
    onUpdatePhoneAndHandle: (String, String) -> Unit,
    onUpdateStealthCode: (String) -> Unit
) {
    val context = LocalContext.current
    var serverInput by remember(customServerUrl) { mutableStateOf(customServerUrl) }
    var handleInput by remember(userHandle) { mutableStateOf(userHandle) }
    var phoneInput by remember(verifiedPhone) { mutableStateOf(verifiedPhone.ifBlank { "+7 (999) 052-25-52" }) }
    var stealthCodeInput by remember(stealthCode) { mutableStateOf(stealthCode) }
    var showServerCode by remember { mutableStateOf(true) }
    var copiedBanner by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "ВАШ КАСТОМНЫЙ СЛЕПОЙ СЕРВЕР (BLIND RELAY)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Укажите WebSocket/HTTPS адрес вашего сервера. Сервер работает как слепой коммутатор: пересылает зашифрованные пакеты Aegis-52 в RAM без возможности расшифровки.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = serverInput,
                        onValueChange = { serverInput = it },
                        label = { Text("URL вашего сервера (ws://, wss:// или https://)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_server_url_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = telemetry.statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Пинг: ${telemetry.lastPingMs} мс • Пакетов через узел: ${telemetry.packetsRelayed}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { onUpdateServerUrl(serverInput) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("probe_custom_server_button")
                        ) {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Подключить")
                        }
                    }
                }
            }
        }

        // Ready-to-deploy Node.js Blind Relay code for the user's server
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "ГОТОВЫЙ КОД ДЛЯ ВАШЕГО СЕРВЕРА (NODE.JS)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("aegis52_server.js", blindServerScript))
                                copiedBanner = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("copy_server_script_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (copiedBanner) "Скопировано!" else "Копировать код")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (showServerCode) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = blindServerScript,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Zero-Knowledge Keys, SMS Binding & Stealth PIN configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "КРИПТО-ПРОФИЛЬ, SMS-ХЭШ И КОД МАСКИРОВКИ",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Safety Fingerprint: $safetyFingerprint",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Резервная фраза (только на устройстве): $recoveryPhrase",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = handleInput,
                            onValueChange = { handleInput = it },
                            label = { Text("Ваш @никнейм") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("Телефон (SMS)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = stealthCodeInput,
                            onValueChange = { stealthCodeInput = it.filter { c -> c.isDigit() }.take(8) },
                            label = { Text("Секретный код калькулятора (напр. 7391)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onUpdatePhoneAndHandle(phoneInput, handleInput)
                                onUpdateStealthCode(stealthCodeInput)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("save_node_profile_button")
                        ) {
                            Text("Сохранить")
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
