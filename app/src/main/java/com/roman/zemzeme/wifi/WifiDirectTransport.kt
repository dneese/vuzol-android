package com.roman.zemzeme.wifi

import android.content.Context
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

        /** WiFi Direct rooms work from Android 9 upwards. */
        val isSupported: Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
    }

    data class RoomMessage(
        val id: String,
        val from: String,
        val nickname: String,
        val text: String,
        val timestamp: Long
    )

    enum class Role { IDLE, HOST, CLIENT }

    private val clientSockets = CopyOnWriteArrayList<Socket>()
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var currentRoom: String? = null
    private var roomKey: ByteArray? = null
    private var roomId: ByteArray? = null
    private var passphrase: String? = null
    private var link: WifiP2pLink? = null

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
        link = WifiP2pLink.create(context, scope)?.also { p2p ->
            p2p.onStatus = { value -> setStatus(value) }
            p2p.onPeerCount = { value -> setPeers(value) }
            p2p.onRole = { value -> onLinkRole(value) }
            p2p.onHostAddress = { address -> connectSocket(address) }
        }
    }

    /**
     * Starts a room. The host creates the WiFi group, everybody else joins it.
     */
    fun startRoom(roomCode: String, password: String?, asHost: Boolean) {
        val code = roomCode.trim().lowercase()
        if (code.isEmpty() || !isSupported) return
        currentRoom = code
        roomKey = deriveRoomKey(code, password.orEmpty())
        roomId = MessageDigest.getInstance("SHA-256")
            .digest((code + "|room-id").toByteArray())
            .copyOf(16)
        passphrase = passphraseFor(code, password.orEmpty())
        setStatus(if (asHost) "starting the room" else "searching for room \"$code\"")
        link?.start(NETWORK_NAME_PREFIX + code, passphrase ?: return, asHost)
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        clientSockets.forEach { runCatching { it.close() } }
        clientSockets.clear()
        peerCount = 0
        role = Role.IDLE
        currentRoom = null
        roomKey = null
        roomId = null
        passphrase = null
        link?.stop()
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

    private fun onLinkRole(linkRole: WifiP2pLinkRole) {
        when (linkRole) {
            WifiP2pLinkRole.HOST -> {
                role = Role.HOST
                startServer()
            }
            WifiP2pLinkRole.CLIENT -> role = Role.CLIENT
            WifiP2pLinkRole.IDLE -> role = Role.IDLE
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
                setStatus("connected · ${clientSockets.size} peers")
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

    private fun passphraseFor(roomCode: String, password: String): String {
        val material = if (password.isBlank()) roomCode else "$password|$roomCode"
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

    private fun setPeers(value: Int) {
        peerCount = value
        scope.launch(Dispatchers.Main) { onPeersChanged?.invoke(value) }
    }

    private fun setStatus(value: String) {
        status = value
        scope.launch(Dispatchers.Main) { onStatusChanged?.invoke(value) }
    }
}
