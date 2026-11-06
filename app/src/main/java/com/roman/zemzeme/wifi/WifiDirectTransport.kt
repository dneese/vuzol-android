package com.roman.zemzeme.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.MacAddress
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
 * phone creates the group and opens a socket, every other phone connects to
 * it. The host relays every frame to the other phones, which turns the star
 * topology into a one hop mesh: everybody in range hears everybody.
 *
 * Frames are encrypted with a key derived from the room code and an optional
 * password, so the host can relay traffic without reading it. Frames of a
 * different room are dropped because the room id does not match.
 *
 * Android 15 (API 35) removed the old WiFi P2P API, so the room requires
 * Android 15 or newer.
 */
class WifiDirectTransport(
    private val context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "WifiDirectTransport"
        private const val PORT = 45678
        private const val FRAME_TEXT = 1
        private const val MAX_FRAME = 64 * 1024
        private const val NETWORK_NAME_PREFIX = "Vuzol-"

        /** WiFi Direct rooms need the rewritten API that arrived in Android 15. */
        val isSupported: Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM
    }

    data class RoomMessage(
        val id: String,
        val from: String,
        val nickname: String,
        val text: String,
        val timestamp: Long
    )

    enum class Role { IDLE, HOST, CLIENT }

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private val clientSockets = CopyOnWriteArrayList<Socket>()
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var discoveryJob: Job? = null
    private var currentRoom: String? = null
    private var roomKey: ByteArray? = null
    private var roomId: ByteArray? = null
    private var passphrase: String? = null
    private var hostMode = false
    private var connecting = false

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

    private val channelListener = object : WifiP2pManager.ChannelListener {
        override fun onChannelDisconnected() {
            setStatus("channel lost, reconnecting")
            openChannel()
        }
    }

    private val p2pListener = object : WifiP2pManager.WifiP2pListener {
        override fun onP2pStateChanged(state: Int) {
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                setStatus("WiFi Direct ready")
                if (hostMode) createGroup()
            } else {
                setStatus("WiFi Direct is switched off")
            }
        }

        override fun onPeerListChanged(peers: WifiP2pDeviceList) {
            val devices = peers.deviceList.toList()
            peerCount = devices.size
            notifyPeers()
            if (!hostMode && !connecting && devices.isNotEmpty()) {
                connectTo(devices.first())
            }
        }

        override fun onGroupCreated(groupInfo: WifiP2pInfo, group: WifiP2pGroup) {
            peerCount = group.clientList.size
            notifyPeers()
            if (group.isGroupOwner) {
                role = Role.HOST
                connecting = false
                startServer()
                setStatus("hosting the room · $peerCount peers")
            } else {
                role = Role.CLIENT
                connecting = false
                val host = groupInfo.groupOwnerAddress?.hostAddress
                connectSocket(host)
                setStatus("connected · $peerCount peers")
            }
        }

        override fun onPeerClientJoined(groupInfo: WifiP2pInfo, group: WifiP2pGroup) {
            peerCount = group.clientList.size
            notifyPeers()
            setStatus("hosting the room · $peerCount peers")
        }

        override fun onPeerClientDisconnected(groupInfo: WifiP2pInfo, group: WifiP2pGroup) {
            peerCount = group.clientList.size
            notifyPeers()
        }

        override fun onGroupRemoved() {
            setStatus("room closed")
            peerCount = 0
            notifyPeers()
        }
    }

    private var channel: WifiP2pManager.Channel? = null

    init {
        if (isSupported) {
            openChannel()
            try {
                manager?.registerWifiP2pListener(context.mainExecutor, p2pListener)
            } catch (e: Exception) {
                Log.w(TAG, "registerWifiP2pListener failed: ${e.message}")
            }
        }
    }

    private fun openChannel() {
        val mgr = manager ?: return
        try {
            channel = mgr.initialize(context, context.mainLooper, channelListener)
        } catch (e: Exception) {
            Log.w(TAG, "initialize failed: ${e.message}")
        }
    }

    /**
     * Starts a room. The host creates the WiFi group, everybody else joins it.
     */
    fun startRoom(roomCode: String, password: String?, asHost: Boolean) {
        val code = roomCode.trim().lowercase()
        if (code.isEmpty() || !isSupported) return
        currentRoom = code
        hostMode = asHost
        roomKey = deriveRoomKey(code, password.orEmpty())
        roomId = MessageDigest.getInstance("SHA-256")
            .digest((code + "|room-id").toByteArray())
            .copyOf(16)
        passphrase = passphraseFor(code, password.orEmpty())
        setStatus(if (asHost) "starting the room" else "searching for room \"$code\"")
        startDiscoveryLoop()
        if (asHost) createGroup()
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
        connecting = false
        hostMode = false
        role = Role.IDLE
        currentRoom = null
        roomKey = null
        roomId = null
        passphrase = null
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
        if (role == Role.HOST) {
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
        return delivered || role == Role.HOST
    }

    private fun startDiscoveryLoop() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val ch = channel
                    if (ch != null) {
                        manager?.discoverPeers(
                            ch,
                            object : WifiP2pManager.ActionListener {
                                override fun onSuccess() = Unit
                                override fun onFailure(reason: Int) {
                                    Log.w(TAG, "discoverPeers failed: $reason")
                                }
                            }
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Discovery failed: ${e.message}")
                }
                delay(8_000)
            }
        }
    }

    private fun createGroup() {
        val ch = channel ?: return
        val config = WifiP2pConfig.Builder()
            .setNetworkName(networkName())
            .setPassphrase(passphrase ?: return)
            .build()
        try {
            manager?.createGroup(
                ch,
                config,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        setStatus("waiting for clients")
                    }

                    override fun onFailure(reason: Int) {
                        setStatus("could not start the room: $reason")
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "createGroup failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectTo(device: WifiP2pDevice) {
        val ch = channel ?: return
        val address = device.deviceAddress ?: return
        connecting = true
        val mac = try {
            MacAddress.fromString(address)
        } catch (e: Exception) {
            null
        } ?: return
        val config = WifiP2pConfig.Builder()
            .setDeviceAddress(mac)
            .setNetworkName(networkName())
            .setPassphrase(passphrase ?: return)
            .build()
        try {
            manager?.connect(
                ch,
                config,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        setStatus("joining the room")
                    }

                    override fun onFailure(reason: Int) {
                        connecting = false
                        setStatus("could not join the room: $reason")
                    }
                }
            )
        } catch (e: Exception) {
            connecting = false
            Log.w(TAG, "connect failed: ${e.message}")
        }
    }

    private fun networkName(): String = NETWORK_NAME_PREFIX + (currentRoom ?: "room")

    private fun startServer() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(PORT)
                serverSocket = server
                while (isActive) {
                    val socket = server.accept()
                    clientSockets.add(socket)
                    peerCount = clientSockets.size
                    notifyPeers()
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
                peerCount = clientSockets.size
                notifyPeers()
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
            while (currentCoroutineContext().isActive && !socket.isClosed) {
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
                if (role == Role.HOST) {
                    clientSockets.forEach { other ->
                        if (other != socket) writeFrame(other, type, id, ciphertext)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Read loop ended: ${e.message}")
        } finally {
            clientSockets.remove(socket)
            peerCount = clientSockets.size
            notifyPeers()
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

    private fun passphraseFor(roomCode: String, password: String): String {
        val material = (password + "|" + roomCode).ifBlank { roomCode }
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
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

    private fun notifyPeers() {
        val count = peerCount
        scope.launch(Dispatchers.Main) { onPeersChanged?.invoke(count) }
    }

    private fun setStatus(value: String) {
        status = value
        scope.launch(Dispatchers.Main) { onStatusChanged?.invoke(value) }
    }
}
