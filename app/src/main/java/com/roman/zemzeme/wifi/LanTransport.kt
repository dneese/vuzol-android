package com.roman.zemzeme.wifi

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A room inside the local WiFi network.
 *
 * The phones of the room find each other with [LanDiscovery] and then talk
 * over TCP. The traffic is encrypted with the room key, so the WiFi router
 * and the other devices of the network see only noise.
 *
 * The phone with the smaller id connects to the other one, that way only one
 * connection exists between two phones and no message is delivered twice.
 */
internal class LanTransport(
    private val context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "LanTransport"

        /** TCP port of the room traffic. */
        const val TCP_PORT = 45680

        private const val KEY_INFO = "vuzol-lan-room-v1"
    }

    private data class PeerConnection(
        val id: String,
        val nickname: String,
        val socket: Socket
    )

    private data class IncomingFile(
        val meta: RoomProtocol.FileMeta,
        val file: File,
        val stream: OutputStream,
        var received: Long = 0L
    )

    /** A phone of the room that can be called. */
    data class PeerInfo(val id: String, val nickname: String, val address: String)

    var onMessage: ((RoomMessage) -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null
    var onPeersChanged: ((Int) -> Unit)? = null
    var onCallSignal: ((RoomProtocol.CallSignal) -> Unit)? = null

    private var key: ByteArray? = null
    private var roomId: ByteArray? = null
    private var localNickname: String = "anon"
    private var discovery: LanDiscovery? = null
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var callEngine: RoomCallEngine? = null
    private var activeCallId: String? = null
    private var callPeer: PeerInfo? = null
    private var callSeconds: Int = 0
    private var callTimerJob: Job? = null
    private val connections = CopyOnWriteArrayList<PeerConnection>()
    private val incomingFiles = ConcurrentHashMap<String, IncomingFile>()

    /** Stable identifier of this phone inside the room. */
    val localPeerId: String by lazy {
        val prefs = context.getSharedPreferences("lan_room", Context.MODE_PRIVATE)
        prefs.getString("peer_id", null) ?: UUID.randomUUID().toString().take(8).also {
            prefs.edit().putString("peer_id", it).apply()
        }
    }

    @Volatile
    var status: String = "idle"
        private set

    @Volatile
    var peerCount: Int = 0
        private set

    /**
     * Joins a room of the local WiFi network. The phone with the smaller id
     * opens the connection, the other one waits for it.
     */
    fun start(roomCode: String, password: String?, nickname: String) {
        stop()
        val code = roomCode.trim().lowercase()
        if (code.isEmpty()) return

        val roomKey = RoomCrypto.deriveKey(code, password.orEmpty(), KEY_INFO)
        val room = RoomCrypto.roomId(code)
        key = roomKey
        roomId = room
        localNickname = nickname

        setStatus("looking for the room \"$code\"")
        if (!startServer()) return

        val found = LanDiscovery(context, scope, roomKey, localPeerId, nickname, TCP_PORT)
        found.onPeer = { peer -> onPeerFound(peer) }
        discovery = found
        found.start()
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        connections.forEach { runCatching { it.socket.close() } }
        connections.clear()
        incomingFiles.values.forEach { runCatching { it.stream.close() } }
        incomingFiles.clear()
        discovery?.stop()
        discovery = null
        stopCall()
        key = null
        roomId = null
        peerCount = 0
        setStatus("stopped")
    }

    /** The phones of the room that can be called. */
    fun peers(): List<PeerInfo> = connections.map { connection ->
        PeerInfo(
            id = connection.id,
            nickname = connection.nickname,
            address = remoteAddress(connection.socket)
        )
    }

    /** Calls one phone of the room, the sound goes over UDP. */
    fun startCall(peer: PeerInfo): Boolean {
        if (callEngine != null) return false
        val roomKey = key ?: return false
        val room = roomId ?: return false
        val engine = RoomCallEngine(roomKey, room)
        val port = engine.start()
        if (port <= 0) {
            engine.stop()
            return false
        }
        engine.setDestination(peer.address, 0)
        callEngine = engine
        callPeer = peer
        val callId = UUID.randomUUID().toString().replace("-", "")
        activeCallId = callId
        sendCallSignal(RoomProtocol.CallSignal("ring", callId, localPeerId, localNickname, port))
        CallHistoryStore.start(callId, peer.nickname, outgoing = true)
        startCallTimer()
        return true
    }

    /** Answers a call of another phone. */
    fun acceptCall(signal: RoomProtocol.CallSignal): Boolean {
        if (callEngine != null) return false
        val roomKey = key ?: return false
        val room = roomId ?: return false
        val peer = peers().firstOrNull { it.id == signal.from } ?: return false
        val engine = RoomCallEngine(roomKey, room)
        val port = engine.start()
        if (port <= 0) {
            engine.stop()
            return false
        }
        engine.setDestination(peer.address, signal.audioPort)
        callEngine = engine
        callPeer = peer
        activeCallId = signal.callId
        sendCallSignal(RoomProtocol.CallSignal("accept", signal.callId, localPeerId, localNickname, port))
        CallHistoryStore.start(signal.callId, peer.nickname, outgoing = false)
        startCallTimer()
        return true
    }

    /** Rejects an incoming call. */
    fun declineCall(callId: String) {
        sendCallSignal(RoomProtocol.CallSignal("decline", callId, localPeerId, localNickname, 0))
        CallHistoryStore.missed(callId, callPeer?.nickname ?: "unknown", outgoing = false)
        stopCall()
    }

    /** Ends the running call. */
    fun endCall() {
        val callId = activeCallId ?: return
        sendCallSignal(RoomProtocol.CallSignal("end", callId, localPeerId, localNickname, 0))
        CallHistoryStore.finish(callId, callSeconds)
        stopCall()
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callSeconds = 0
        callTimerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                callSeconds++
            }
        }
    }

    private fun stopCall() {
        callTimerJob?.cancel()
        callTimerJob = null
        val callId = activeCallId
        if (callId != null && callEngine != null) {
            CallHistoryStore.finish(callId, callSeconds)
        }
        callEngine?.stop()
        callEngine = null
        activeCallId = null
        callPeer = null
    }

    private fun sendCallSignal(signal: RoomProtocol.CallSignal) {
        val roomKey = key ?: return
        val room = roomId ?: return
        val cipher = RoomCrypto.encrypt(roomKey, room, RoomProtocol.encodeCallSignal(signal)) ?: return
        sendFrame(RoomProtocol.FRAME_CALL, room, cipher)
    }

    private fun handleCallSignal(signal: RoomProtocol.CallSignal) {
        when (signal.action) {
            "ring" -> withContextSafe { onCallSignal?.invoke(signal) }
            "accept" -> {
                val engine = callEngine
                val peer = callPeer
                if (engine != null && peer != null) {
                    engine.setDestination(peer.address, signal.audioPort)
                }
                withContextSafe { onCallSignal?.invoke(signal) }
            }
            "decline", "end" -> {
                stopCall()
                withContextSafe { onCallSignal?.invoke(signal) }
            }
            else -> Unit
        }
    }

    private fun remoteAddress(socket: Socket): String =
        (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress ?: ""

    /** Sends a text message to every phone of the room. */
    fun sendText(text: String, nickname: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val roomKey = key ?: return false
        val room = roomId ?: return false
        val payload = RoomProtocol.encodeText(
            id = UUID.randomUUID().toString(),
            from = localPeerId,
            nickname = nickname,
            text = trimmed,
            timestamp = System.currentTimeMillis()
        )
        val ciphertext = RoomCrypto.encrypt(roomKey, room, payload) ?: return false
        val sent = sendFrame(RoomProtocol.FRAME_TEXT, room, ciphertext)
        withContextSafe {
            onMessage?.invoke(
                RoomMessage(
                    id = "local-${System.currentTimeMillis()}",
                    from = localPeerId,
                    nickname = nickname,
                    text = trimmed,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        return sent || connections.isNotEmpty()
    }

    /** Sends a file of the phone to every other phone of the room. */
    fun sendFile(uri: Uri, nickname: String): Boolean {
        val roomKey = key ?: return false
        val room = roomId ?: return false
        if (connections.isEmpty()) return false

        val resolver = context.contentResolver
        val fileName = displayName(uri) ?: "file"
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var size = 0L
        runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                size = descriptor.length
            }
        }
        val transferId = UUID.randomUUID().toString().replace("-", "")

        val meta = RoomProtocol.FileMeta(
            transferId = transferId,
            fileName = fileName,
            mime = mime,
            size = size,
            from = localPeerId,
            nickname = nickname,
            timestamp = System.currentTimeMillis()
        )
        val metaCipher = RoomCrypto.encrypt(roomKey, room, RoomProtocol.encodeFileMeta(meta)) ?: return false

        scope.launch(Dispatchers.IO) {
            try {
                val input = resolver.openInputStream(uri)
                if (input == null) return@launch
                input.use { stream ->
                    sendFrame(RoomProtocol.FRAME_FILE_META, room, metaCipher)
                    val buffer = ByteArray(RoomProtocol.FILE_CHUNK_SIZE)
                    var index = 0
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        val chunk = RoomProtocol.encodeFileChunk(transferId, index, buffer.copyOf(read))
                        val cipher = RoomCrypto.encrypt(roomKey, room, chunk) ?: break
                        sendFrame(RoomProtocol.FRAME_FILE_CHUNK, room, cipher)
                        index++
                    }
                }
                withContextSafe {
                    onMessage?.invoke(
                        RoomMessage(
                            id = "local-file-$transferId",
                            from = localPeerId,
                            nickname = nickname,
                            text = fileName,
                            timestamp = System.currentTimeMillis(),
                            fileName = fileName,
                            fileSize = size
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "File send failed: ${e.message}")
            }
        }
        return true
    }

    private fun startServer(): Boolean {
        val server = try {
            ServerSocket(TCP_PORT)
        } catch (e: Exception) {
            Log.w(TAG, "Could not bind the port $TCP_PORT: ${e.message}")
            setStatus("the port $TCP_PORT is busy")
            return false
        }
        serverSocket = server
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val socket = server.accept()
                    scope.launch(Dispatchers.IO) {
                        sendHandshake(socket)
                        readLoop(socket, null)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Server stopped: ${e.message}")
            }
        }
        return true
    }

    private fun onPeerFound(peer: LanDiscovery.Peer) {
        if (peer.id == localPeerId) return
        if (connections.any { it.id == peer.id }) return
        // Only the phone with the smaller id opens the connection
        if (localPeerId >= peer.id) return
        scope.launch(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(peer.address, peer.port), 5000)
                if (!sendHandshake(socket)) return@launch
                registerConnection(PeerConnection(peer.id, peer.nickname, socket))
                readLoop(socket, peer.id)
            } catch (e: Exception) {
                Log.w(TAG, "Connect to ${peer.address} failed: ${e.message}")
            }
        }
    }

    /** Sends the hello of this phone, it carries the id and the nickname. */
    private fun sendHandshake(socket: Socket): Boolean {
        val roomKey = key ?: return false
        val room = roomId ?: return false
        val hello = RoomCrypto.encrypt(
            roomKey,
            room,
            RoomProtocol.encodeHello(localPeerId, localNickname)
        ) ?: return false
        return RoomProtocol.writeFrame(socket, RoomProtocol.FRAME_HANDSHAKE, room, hello)
    }

    private suspend fun readLoop(socket: Socket, knownPeerId: String?) {
        var peerId = knownPeerId
        var nickname = "anon"
        try {
            val input = DataInputStream(socket.getInputStream())
            while (currentCoroutineContext().isActive && !socket.isClosed) {
                val frame = RoomProtocol.readFrame(input) ?: break
                val roomKey = key ?: break
                val room = roomId ?: break
                if (!frame.roomId.contentEquals(room)) continue
                val payload = RoomCrypto.decrypt(roomKey, room, frame.ciphertext) ?: continue
                when (frame.type) {
                    RoomProtocol.FRAME_HANDSHAKE -> {
                        val hello = RoomProtocol.decodeHello(payload) ?: continue
                        peerId = hello.first
                        nickname = hello.second
                        registerConnection(PeerConnection(peerId, nickname, socket))
                    }
                    RoomProtocol.FRAME_TEXT -> {
                        if (peerId == null) continue
                        val message = RoomProtocol.decodeText(payload) ?: continue
                        withContextSafe { onMessage?.invoke(message) }
                    }
                    RoomProtocol.FRAME_FILE_META -> {
                        val meta = RoomProtocol.decodeFileMeta(payload) ?: continue
                        startIncomingFile(meta)
                    }
                    RoomProtocol.FRAME_FILE_CHUNK -> {
                        val chunk = RoomProtocol.decodeFileChunk(payload) ?: continue
                        writeIncomingChunk(chunk)
                    }
                    RoomProtocol.FRAME_CALL -> {
                        val signal = RoomProtocol.decodeCallSignal(payload) ?: continue
                        handleCallSignal(signal)
                    }
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Read loop ended: ${e.message}")
        } finally {
            removeConnection(socket)
            runCatching { socket.close() }
        }
    }

    private fun registerConnection(connection: PeerConnection) {
        if (connections.any { it.id == connection.id }) return
        connections.add(connection)
        peerCount = connections.size
        withContextSafe { onPeersChanged?.invoke(peerCount) }
        setStatus("connected · ${connections.size} peers")
    }

    private fun removeConnection(socket: Socket) {
        val removed = connections.removeAll { it.socket === socket }
        if (removed) {
            peerCount = connections.size
            withContextSafe { onPeersChanged?.invoke(peerCount) }
            setStatus(if (connections.isEmpty()) "looking for the room" else "connected · ${connections.size} peers")
        }
    }

    private fun sendFrame(type: Int, room: ByteArray, ciphertext: ByteArray): Boolean {
        var sent = false
        connections.forEach { connection ->
            sent = RoomProtocol.writeFrame(connection.socket, type, room, ciphertext) || sent
        }
        return sent
    }

    private fun startIncomingFile(meta: RoomProtocol.FileMeta) {
        try {
            val directory = File(context.cacheDir, "lan_files")
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, "${meta.transferId}_${safeName(meta.fileName)}")
            val stream = FileOutputStream(file)
            incomingFiles[meta.transferId] = IncomingFile(meta, file, stream)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start the file: ${e.message}")
        }
    }

    private fun writeIncomingChunk(chunk: RoomProtocol.FileChunk) {
        val incoming = incomingFiles[chunk.transferId] ?: return
        try {
            incoming.stream.write(chunk.data)
            incoming.received += chunk.data.size
            val size = incoming.meta.size
            if (size > 0 && incoming.received >= size) {
                runCatching { incoming.stream.close() }
                incomingFiles.remove(chunk.transferId)
                withContextSafe {
                    onMessage?.invoke(
                        RoomMessage(
                            id = "file-${incoming.meta.transferId}",
                            from = incoming.meta.from,
                            nickname = incoming.meta.nickname,
                            text = incoming.meta.fileName,
                            timestamp = incoming.meta.timestamp,
                            filePath = incoming.file.absolutePath,
                            fileName = incoming.meta.fileName,
                            fileSize = size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write the file: ${e.message}")
            runCatching { incoming.stream.close() }
            incomingFiles.remove(chunk.transferId)
        }
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

    private fun withContextSafe(action: () -> Unit) {
        scope.launch(Dispatchers.Main) { action() }
    }

    private fun setStatus(value: String) {
        status = value
        scope.launch(Dispatchers.Main) { onStatusChanged?.invoke(value) }
    }
}
