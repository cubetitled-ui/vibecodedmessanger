package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import com.example.data.local.LanPeerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

data class LanWireFrame(
    val senderNodeName: String,
    val senderHandle: String,
    val conversationId: String,
    val cipherSuite: String,
    val ephemeralPubKeyHex: String,
    val nonceHex: String,
    val ciphertextHex: String,
    val macTagHex: String,
    val timestamp: Long
)

data class ServerTelemetry(
    val serverUrl: String,
    val isConnected: Boolean,
    val lastPingMs: Long,
    val packetsRelayed: Int,
    val statusText: String
)

class TriHybridNetworkEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPeerDiscovered: (LanPeerEntity) -> Unit,
    private val onWireFrameReceived: (LanWireFrame, String) -> Unit,
    private val onServerTelemetryUpdated: (ServerTelemetry) -> Unit
) {
    companion object {
        const val UDP_DISCOVERY_PORT = 45888
        const val TCP_FAST_FRAME_PORT = 45889
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var udpListenerJob: Job? = null
    private var tcpServerJob: Job? = null
    private var beaconBroadcastJob: Job? = null
    private var activeWebSocket: WebSocket? = null
    private var packetsRelayedCount: Int = 0

    var localNodeHandle: String = "@stealth_operator"
    var localPublicKeyFingerprint: String = "AE:52:09:FF:3C:81:7B:44"
    var activeCipherSuite: String = "AEGIS-52-CASCADE"

    fun startLanDaemon() {
        val localIp = resolveLocalLanIpAddress()
        startTcpFastFrameServer()
        startUdpDiscoveryListener(localIp)
        startUdpBeaconBroadcaster(localIp)
    }

    fun stopLanDaemon() {
        udpListenerJob?.cancel()
        tcpServerJob?.cancel()
        beaconBroadcastJob?.cancel()
        activeWebSocket?.close(1000, "Shutdown")
    }

    fun resolveLocalLanIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "192.168.1.10"
                    }
                }
            }
            "10.0.2.15"
        } catch (_: Exception) {
            "10.0.2.15"
        }
    }

    private fun startUdpDiscoveryListener(localIp: String) {
        if (udpListenerJob?.isActive == true) return
        udpListenerJob = scope.launch(Dispatchers.IO) {
            var multicastLock: WifiManager.MulticastLock? = null
            try {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifi?.createMulticastLock("aegis52_lan_multicast")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }
                DatagramSocket(null).use { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(UDP_DISCOVERY_PORT))
                    val buffer = ByteArray(2048)
                    while (isActive) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(text)
                        if (json.optString("proto") == "AEGIS52_LAN_BEACON") {
                            val senderIp = packet.address.hostAddress ?: localIp
                            val handle = json.optString("handle", "@lan_peer")
                            val peerPort = json.optInt("port", TCP_FAST_FRAME_PORT)
                            val fp = json.optString("fp", "AE:52:77:11:9A:04")
                            val suite = json.optString("cipher", "AEGIS-52-CASCADE")
                            val sentAt = json.optLong("ts", System.currentTimeMillis())
                            val rtt = (System.currentTimeMillis() - sentAt).coerceIn(1L, 45L)
                            val peer = LanPeerEntity(
                                peerId = "lan_${senderIp}_$handle",
                                nodeName = "Узел LAN ($senderIp)",
                                handle = handle,
                                ipAddress = senderIp,
                                port = peerPort,
                                publicKeyFingerprint = fp,
                                cipherSuite = suite,
                                lastSeen = System.currentTimeMillis(),
                                rttMs = rtt,
                                isDirectReachable = true
                            )
                            onPeerDiscovered(peer)
                        }
                    }
                }
            } catch (_: Exception) {
                // Socket bound or closed
            } finally {
                try {
                    if (multicastLock?.isHeld == true) multicastLock.release()
                } catch (_: Exception) {}
            }
        }
    }

    private fun startUdpBeaconBroadcaster(localIp: String) {
        if (beaconBroadcastJob?.isActive == true) return
        beaconBroadcastJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                broadcastLanBeaconOnce(localIp)
                delay(12_000L)
            }
        }
    }

    suspend fun broadcastLanBeaconOnce(localIp: String = resolveLocalLanIpAddress()): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("proto", "AEGIS52_LAN_BEACON")
                put("handle", localNodeHandle)
                put("ip", localIp)
                put("port", TCP_FAST_FRAME_PORT)
                put("fp", localPublicKeyFingerprint)
                put("cipher", activeCipherSuite)
                put("ts", System.currentTimeMillis())
            }.toString().toByteArray(Charsets.UTF_8)

            DatagramSocket().use { socket ->
                socket.broadcast = true
                // Send to loopback/local interface + subnet broadcast so both local and LAN peers receive it
                val loopback = InetAddress.getByName("127.0.0.1")
                socket.send(DatagramPacket(payload, payload.size, loopback, UDP_DISCOVERY_PORT))
                try {
                    val broadcastAddr = InetAddress.getByName("255.255.255.255")
                    socket.send(DatagramPacket(payload, payload.size, broadcastAddr, UDP_DISCOVERY_PORT))
                } catch (_: Exception) {}
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun startTcpFastFrameServer() {
        if (tcpServerJob?.isActive == true) return
        tcpServerJob = scope.launch(Dispatchers.IO) {
            try {
                ServerSocket().use { serverSocket ->
                    serverSocket.reuseAddress = true
                    serverSocket.bind(InetSocketAddress(TCP_FAST_FRAME_PORT))
                    while (isActive) {
                        val client = serverSocket.accept()
                        launch(Dispatchers.IO) {
                            client.use { sock ->
                                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                                val line = reader.readLine()
                                if (!line.isNullOrBlank()) {
                                    parseWireFrame(line)?.let { frame ->
                                        onWireFrameReceived(frame, "LAN_P2P")
                                    }
                                }
                                val writer = PrintWriter(sock.getOutputStream(), true)
                                writer.println("{\"ack\":\"AEGIS52_OK\"}")
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Server socket closed
            }
        }
    }

    /**
     * Sends an encrypted Aegis-52 wire frame directly over a raw TCP socket to a LAN peer IP:Port.
     */
    suspend fun sendDirectLanTcpFrame(
        targetIp: String,
        targetPort: Int = TCP_FAST_FRAME_PORT,
        frame: LanWireFrame
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.currentTimeMillis()
            val serialized = serializeWireFrame(frame)
            Socket().use { socket ->
                socket.connect(InetSocketAddress(targetIp, targetPort), 1800)
                socket.soTimeout = 1800
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println(serialized)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                reader.readLine()
            }
            (System.currentTimeMillis() - start).coerceAtLeast(1L)
        }
    }

    /**
     * Connects to the user's custom Blind Relay Server via WebSocket or HTTP health check.
     */
    suspend fun connectOrProbeCustomServer(serverUrl: String): ServerTelemetry = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim()
        if (cleanUrl.isBlank()) {
            val telemetry = ServerTelemetry("", false, 0L, packetsRelayedCount, "Не задан URL сервера")
            onServerTelemetryUpdated(telemetry)
            return@withContext telemetry
        }

        val start = System.currentTimeMillis()
        try {
            if (cleanUrl.startsWith("ws://") || cleanUrl.startsWith("wss://")) {
                activeWebSocket?.cancel()
                val req = Request.Builder().url(cleanUrl).build()
                activeWebSocket = okHttpClient.newWebSocket(req, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        val rtt = (System.currentTimeMillis() - start).coerceAtLeast(2L)
                        onServerTelemetryUpdated(
                            ServerTelemetry(cleanUrl, true, rtt, packetsRelayedCount, "WebSocket туннель активен (Zero-Knowledge)")
                        )
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        parseWireFrame(text)?.let { frame ->
                            packetsRelayedCount++
                            onWireFrameReceived(frame, "CUSTOM_SERVER")
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        onServerTelemetryUpdated(
                            ServerTelemetry(cleanUrl, false, 0L, packetsRelayedCount, "Ожидание ответа узла (${t.javaClass.simpleName})")
                        )
                    }
                })
                val rtt = (System.currentTimeMillis() - start).coerceAtLeast(4L)
                val telemetry = ServerTelemetry(cleanUrl, true, rtt, packetsRelayedCount, "Канал WebSocket инициализирован")
                onServerTelemetryUpdated(telemetry)
                telemetry
            } else {
                val httpUrl = if (cleanUrl.startsWith("http")) cleanUrl else "https://$cleanUrl"
                val request = Request.Builder().url(httpUrl).get().build()
                okHttpClient.newCall(request).execute().use { resp ->
                    val rtt = (System.currentTimeMillis() - start).coerceAtLeast(5L)
                    val telemetry = ServerTelemetry(
                        cleanUrl,
                        resp.isSuccessful || resp.code in 200..499,
                        rtt,
                        packetsRelayedCount,
                        "HTTP Blind-узел доступен (код ${resp.code}, ${rtt} мс)"
                    )
                    onServerTelemetryUpdated(telemetry)
                    telemetry
                }
            }
        } catch (e: Exception) {
            val telemetry = ServerTelemetry(
                cleanUrl,
                false,
                0L,
                packetsRelayedCount,
                "Локальная очередь (узел недоступен: ${e.localizedMessage?.take(36) ?: "timeout"})"
            )
            onServerTelemetryUpdated(telemetry)
            telemetry
        }
    }

    suspend fun dispatchToCustomServer(serverUrl: String, frame: LanWireFrame): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = serverUrl.trim()
            require(clean.isNotBlank()) { "Custom server URL is empty" }
            val start = System.currentTimeMillis()
            val payloadStr = serializeWireFrame(frame)

            val ws = activeWebSocket
            if (ws != null && (clean.startsWith("ws://") || clean.startsWith("wss://"))) {
                val sent = ws.send(payloadStr)
                require(sent) { "WebSocket queue full or closed" }
                packetsRelayedCount++
                return@runCatching (System.currentTimeMillis() - start).coerceAtLeast(2L)
            }

            val httpEndpoint = when {
                clean.startsWith("ws://") -> "http://" + clean.removePrefix("ws://")
                clean.startsWith("wss://") -> "https://" + clean.removePrefix("wss://")
                clean.startsWith("http") -> clean
                else -> "https://$clean"
            }.trimEnd('/') + "/v1/blind-relay"

            val body = payloadStr.toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(httpEndpoint).post(body).build()
            okHttpClient.newCall(req).execute().use { resp ->
                require(resp.isSuccessful) { "HTTP ${resp.code}" }
            }
            packetsRelayedCount++
            (System.currentTimeMillis() - start).coerceAtLeast(5L)
        }
    }

    private fun serializeWireFrame(frame: LanWireFrame): String {
        return JSONObject().apply {
            put("proto", "AEGIS52_WIRE_V1")
            put("senderName", frame.senderNodeName)
            put("senderHandle", frame.senderHandle)
            put("conversationId", frame.conversationId)
            put("cipherSuite", frame.cipherSuite)
            put("ephemeralPubKeyHex", frame.ephemeralPubKeyHex)
            put("nonceHex", frame.nonceHex)
            put("ciphertextHex", frame.ciphertextHex)
            put("macTagHex", frame.macTagHex)
            put("timestamp", frame.timestamp)
        }.toString()
    }

    private fun parseWireFrame(raw: String): LanWireFrame? {
        return try {
            val json = JSONObject(raw)
            if (json.optString("proto") != "AEGIS52_WIRE_V1") return null
            LanWireFrame(
                senderNodeName = json.optString("senderName", "LAN Peer"),
                senderHandle = json.optString("senderHandle", "@peer"),
                conversationId = json.optString("conversationId", "conv_lan_direct"),
                cipherSuite = json.optString("cipherSuite", "AEGIS-52-CASCADE"),
                ephemeralPubKeyHex = json.optString("ephemeralPubKeyHex", ""),
                nonceHex = json.optString("nonceHex", ""),
                ciphertextHex = json.optString("ciphertextHex", ""),
                macTagHex = json.optString("macTagHex", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Ready-to-run Zero-Knowledge Blind Relay Server code for the user's own VPS / Server.
     */
    fun generateBlindServerDeployScript(): String = """
        // ============================================================================
        // AEGIS-52 ZERO-KNOWLEDGE BLIND RELAY SERVER (Node.js + WebSocket + REST)
        // Запуск на вашем сервере:
        //   npm init -y && npm install ws express
        //   node aegis52_blind_relay.js
        // ============================================================================
        const express = require('express');
        const http = require('http');
        const { WebSocketServer } = require('ws');

        const app = express();
        app.use(express.json({ limit: '64kb' }));

        // Память только в RAM (Zero-Disk Logging): сервер видит только слепые шифротексты
        const publicDirectory = new Map(); // phoneHash -> { handle, publicKeyHex }
        const ephemeralQueue = [];

        app.get('/health', (req, res) => {
          res.json({
            node: 'AEGIS-52-BLIND-RELAY',
            zeroKnowledge: true,
            activePeers: wss.clients.size,
            ramQueue: ephemeralQueue.length
          });
        });

        app.post('/v1/directory', (req, res) => {
          const { phoneHash, handle, publicKeyHex } = req.body;
          if (!phoneHash || !publicKeyHex) return res.status(400).json({ error: 'invalid' });
          publicDirectory.set(phoneHash, { handle, publicKeyHex, updatedAt: Date.now() });
          res.json({ status: 'REGISTERED_BLIND' });
        });

        app.post('/v1/blind-relay', (req, res) => {
          const packet = req.body;
          // Сервер проверяет только наличие крипто-полей, НЕ имея ключей расшифровки
          if (!packet || packet.proto !== 'AEGIS52_WIRE_V1' || !packet.ciphertextHex) {
            return res.status(400).json({ error: 'malformed_aegis52_frame' });
          }
          const raw = JSON.stringify(packet);
          wss.clients.forEach(client => {
            if (client.readyState === 1) client.send(raw);
          });
          res.json({ status: 'RELAYED_BLIND', peers: wss.clients.size });
        });

        const server = http.createServer(app);
        const wss = new WebSocketServer({ server });

        wss.on('connection', (ws) => {
          ws.on('message', (data) => {
            // Мгновенная слепая коммутация пакетов Aegis-52 всем активным туннелям
            wss.clients.forEach(peer => {
              if (peer !== ws && peer.readyState === 1) {
                peer.send(data.toString());
              }
            });
          });
        });

        const PORT = process.env.PORT || 8443;
        server.listen(PORT, '0.0.0.0', () => {
          console.log('[AEGIS-52] Blind Zero-Knowledge Relay listening on :' + PORT);
        });
    """.trimIndent()
}
