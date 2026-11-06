package com.roman.zemzeme.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroupInfo
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * WiFi Direct transport for offline conversations.
 *
 * Two phones create a direct WiFi link without any router or internet. One
 * phone becomes the group owner and opens a socket, every other phone
 * connects to it. The owner relays every frame to the other phones, which
 * turns the star topology into a one hop mesh: everybody in range hears
 * everybody.
 *
 * Frames are encrypted with a key derived from the room code and an optional
 * password, so the owner can relay traffic without reading it. Frames of a
 * different room are dropped because the room id does not match.
 */
class WifiDirectTransport(
    private val context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "WifiDirectTransport"
        private const val PORT = 45678
        private const val NETWORK_NAME = "Vuzol"
        private const val FRAME_TEXT = 1
        private const val MAX_FRAME = 64 * 1024
    }

    data class RoomMessage(
        val id: String,
        val from: String,
        val nickname: String,
        val text: String,
        val timestamp: Long
    )

    enum class Role { IDLE, OWNER, CLIENT }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED
                    )
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        startDiscoveryLoop()
                    } else {
                        setStatus("WiFi Direct is switched off")
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> refreshPeers()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val info: WifiP2pInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                    if (info == null) setStatus("waiting for peers")
                }
                WifiP2pManager.WIFI_P2P_GROUP_CHANGED_ACTION -> {
                    val group: WifiP2pGroupInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
                    onGroupChanged(group)
                }
            }
        }
    }

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private val channel: WifiP2pManager.Channel? = try {
        manager?.initialize(context, context.mainLooper, receiver)
    } catch (e: Exception) {
        Log.w(TAG, "initialize failed: ${e.message}")
        null
    }

    private val clientSockets = CopyOnWriteArrayList<Socket>()
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var discoveryJob: Job? = null
    private var currentRoom: String? = null
    private var roomKey: ByteArray? = null
    private var roomId: ByteArray? = null
    private var connectedToPeer = false

    @Volatile
    var role: Role = Role.IDLE
        private set

    @Volatile
    var status: String = "idle"
        private set

    @Volatile
    var peerCount: Int = 0
        private set

    var onMessage: ((RoomMessage) -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null
    var onPeersChanged: ((Int) -> Unit)? = null

    /** Stable identifier of this phone inside a room. */
    val localPeerId: String by lazy {
        val prefs = context.getSharedPreferences("wifi_direct_room", Context.MODE_PRIVATE)
        prefs.getString("peer_id", null) ?: UUID.randomUUID().toString().take(8).also {
            prefs.edit().putString("peer_id", it).apply()
        }
    }

    init {
        try {
            context.registerReceiver(receiver, IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_GROUP_CHANGED_ACTION)
            })
        } catch (e: Exception) {
            Log.w(TAG, "Could not register receiver: ${e.message}")
        }
    }

    /**
     * Starts a room. Everybody uses the same room code and password.
     */
    fun startRoom(roomCode: String, password: String?) {
        val code = roomCode.trim().lowercase()
        if (code.isEmpty()) return
        currentRoom = code
        roomKey = deriveRoomKey(code, password.orEmpty())
        roomId = MessageDigest.getInstance("SHA-256")
            .digest((code + "|room-id").toByteArray())
            .copyOf(16)
        setStatus("searching for room \"$code\"")
        startDiscoveryLoop()
    }

    fun stop() {
        discoveryJob?.cancel()
        discoveryJob = null
        serverJob?.cancel()
        serverJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        clientSockets.forEach { runCatching { it.close() } }
        clientSockets.clear()
        peerCount = 0
        connectedToPeer = false
        role = Role.IDLE
        currentRoom = null
        roomKey = null
        roomId = null
        try {
            manager?.removeGroup(channel, null)
        } catch (_: Exception) {
        }
        setStatus("stopped")
    }

    /** Sends a text message to every phone in the room. */
    fun sendText(text: String, fromPeerId: String, nickname: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val payload = org.json.JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("from", fromPeerId)
            put("name", nickname)
            put("text", trimmed)
            put("ts", System.currentTimeMillis())
        }.toString().toByteArray(Charsets.UTF_8)

        val id = roomId ?: return false
        val ciphertext = encryptPayload(id, payload) ?: return false

        var delivered = false
        clientSockets.forEach { socket -> delivered = writeFrame(socket, FRAME_TEXT, id, ciphertext) || delivered }
        if (role == Role.OWNER) {
            onMessage?.invoke(
                RoomMessage(
                    id = "local",
                    from = fromPeerId,
                    nickname = nickname,
                    text = trimmed,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        return delivered || role == Role.OWNER
    }

    private fun startDiscoveryLoop() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val mgr = manager
                    val ch = channel
                    if (mgr != null && ch != null) {
                        val config = WifiP2pConfig.Builder()
                            .setNetworkName(NETWORK_NAME)
                            .setGroupOwnerIntent(15)
                            .build()
                        mgr.discoverPeers(ch, config)
                    }
                    refreshPeers()
                } catch (e: Exception) {
                    Log.w(TAG, "Discovery failed: ${e.message}")
                }
                delay(6_000)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshPeers() {
        val mgr = manager ?: return
        val ch = channel ?: return
        try {
            mgr.requestPeers(ch, object : WifiP2pManager.PeerListListener {
                override fun onPeersAvailable(peerDeviceList: MutableList<WifiP2pDevice>) {
                    setPeers(peerDeviceList.size)
                    if (connectedToPeer) return
                    val peer = peerDeviceList.firstOrNull() ?: return
                    connectTo(peer)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "requestPeers failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectTo(device: WifiP2pDevice) {
        val mgr = manager ?: return
        val ch = channel ?: return
        try {
            val config = WifiP2pConfig.Builder()
                .setNetworkName(NETWORK_NAME)
                .setGroupOwnerIntent(0)
                .build()
            mgr.connect(ch, device, config)
        } catch (e: Exception) {
            Log.w(TAG, "connect failed: ${e.message}")
        }
    }

    private fun onGroupChanged(group: WifiP2pGroupInfo?) {
        if (group == null) return
        connectedToPeer = true
        setPeers(group.clientList?.size ?: 0)
        if (group.isGroupOwner) {
            role = Role.OWNER
            startServer()
            setStatus("hosting the room · $peerCount peers")
        } else {
            role = Role.CLIENT
            connectSocket(group.groupOwner?.ownIpAddress?.hostAddress)
            setStatus("connected · $peerCount peers")
        }
    }

    private fun startServer() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(PORT)
                serverSocket = server
                while (isActive) {
                    val socket = server.accept()
                    clientSockets.add(socket)
                    setPeers(clientSockets.size)
                    scope.launch(Dispatchers.IO) { readLoop(socket) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Server stopped: ${e.message}")
            }
        }
    }

    private fun connectSocket(host: String?) {
        if (host.isNullOrBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, PORT), 8_000)
                clientSockets.add(socket)
                setPeers(clientSockets.size)
                setStatus("connected · $peerCount peers")
                readLoop(socket)
            } catch (e: Exception) {
                Log.w(TAG, "Socket connect failed: ${e.message}")
                setStatus("waiting for the host")
            }
        }
    }

    private suspend fun readLoop(socket: Socket) {
        try {
            val input = DataInputStream(socket.getInputStream())
            while (isActive && !socket.isClosed) {
                val frameLength = input.readInt()
                if (frameLength <= 0 || frameLength > MAX_FRAME) break
                val frame = ByteArray(frameLength)
                input.readFully(frame)
                val buffer = java.io.ByteArrayInputStream(frame)
                val data = DataInputStream(buffer)
                val type = data.readInt()
                if (type <= 0 || type > 16) continue
                val id = ByteArray(16)
                data.readFully(id)
                val length = data.readInt()
                if (length <= 0 || length > MAX_FRAME) continue
                val ciphertext = ByteArray(length)
                data.readFully(ciphertext)

                // Frames of a different room are ignored
                val ownId = roomId ?: continue
                if (!id.contentEquals(ownId)) continue

                val payload = decryptPayload(id, ciphertext) ?: continue
                if (type == FRAME_TEXT) {
                    val json = org.json.JSONObject(String(payload, Charsets.UTF_8))
                    val message = RoomMessage(
                        id = json.optString("id"),
                        from = json.optString("from"),
                        nickname = json.optString("name", "anon"),
                        text = json.optString("text"),
                        timestamp = json.optLong("ts", System.currentTimeMillis())
                    )
                    withContext(Dispatchers.Main) { onMessage?.invoke(message) }
                }

                // Relay to every other phone: this is the mesh hop
                if (role == Role.OWNER) {
                    clientSockets.forEach { other ->
                        if (other != socket) writeFrame(other, type, id, ciphertext)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Read loop ended: ${e.message}")
        } finally {
            clientSockets.remove(socket)
            setPeers(clientSockets.size)
            runCatching { socket.close() }
        }
    }

    private fun writeFrame(socket: Socket, type: Int, id: ByteArray, ciphertext: ByteArray): Boolean = try {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.writeInt(type)
        data.write(id)
        data.writeInt(ciphertext.size)
        data.write(ciphertext)
        data.flush()
        val frame = out.toByteArray()
        val output = DataOutputStream(socket.getOutputStream())
        output.writeInt(frame.size)
        output.write(frame)
        output.flush()
        true
    } catch (e: Exception) {
        false
    }

    private fun encryptPayload(id: ByteArray, payload: ByteArray): ByteArray? = try {
        val key = roomKey ?: return null
        com.google.crypto.tink.subtle.XChaCha20Poly1305(key).encrypt(payload, id)
    } catch (_: Exception) {
        null
    }

    private fun decryptPayload(id: ByteArray, ciphertext: ByteArray): ByteArray? = try {
        val key = roomKey ?: return null
        com.google.crypto.tink.subtle.XChaCha20Poly1305(key).decrypt(ciphertext, id)
    } catch (_: Exception) {
        null
    }

    /** Derives the room key from the room code and the optional password. */
    fun deriveRoomKey(roomCode: String, password: String): ByteArray {
        val salt = MessageDigest.getInstance("SHA-256")
            .digest(roomCode.lowercase().toByteArray())
        val prk = hmac(salt, password.toByteArray(Charsets.UTF_8))
        return hkdfExpand(prk, "vuzol-wifi-room-v1".toByteArray(), 32)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val result = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val take = minOf(previous.size, length - offset)
            System.arraycopy(previous, 0, result, offset, take)
            offset += take
            counter++
        }
        return result
    }

    private fun setPeers(value: Int) {
        peerCount = value
        scope.launch(Dispatchers.Main) { onPeersChanged?.invoke(value) }
    }

    private fun setStatus(value: String) {
        status = value
        scope.launch(Dispatchers.Main) { onStatusChanged?.invoke(value) }
    }
}
