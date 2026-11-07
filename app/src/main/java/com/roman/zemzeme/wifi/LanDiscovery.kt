package com.roman.zemzeme.wifi

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the other phones of a room inside the local WiFi network.
 *
 * Every phone sends a small encrypted hello over UDP every few seconds, to
 * the broadcast address of the network, to the limited broadcast address and
 * to a multicast group. The hello is encrypted with the room key, so phones
 * of a different room cannot be seen and the room code never leaves the
 * phones.
 */
internal class LanDiscovery(
    private val context: Context,
    private val scope: CoroutineScope,
    private val key: ByteArray,
    private val peerId: String,
    private val nickname: String,
    private val tcpPort: Int
) {

    companion object {
        private const val TAG = "LanDiscovery"

        /** UDP port of the hello packets. */
        const val DISCOVERY_PORT = 45679

        private const val MULTICAST_GROUP = "239.50.40.0"
        private const val HEARTBEAT_MS = 3000L
        private const val PEER_TIMEOUT_MS = 25000L
        private val AAD = "vuzol-lan-hello".toByteArray(Charsets.UTF_8)
    }

    data class Peer(
        val id: String,
        val address: String,
        val port: Int,
        val nickname: String,
        val lastSeen: Long
    )

    /** Called for every hello of another phone, also when the peer is known. */
    var onPeer: ((Peer) -> Unit)? = null

    private var multicastSocket: MulticastSocket? = null
    private var sendSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var receiveJob: Job? = null
    private var sendJob: Job? = null
    private val peers = ConcurrentHashMap<String, Peer>()

    fun start() {
        if (receiveJob?.isActive == true) return
        acquireMulticastLock()

        try {
            val socket = MulticastSocket()
            socket.reuseAddress = true
            socket.soTimeout = 2000
            try {
                socket.joinGroup(InetAddress.getByName(MULTICAST_GROUP))
            } catch (e: Exception) {
                Log.w(TAG, "Could not join the multicast group: ${e.message}")
            }
            multicastSocket = socket
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the discovery socket: ${e.message}")
            return
        }

        try {
            sendSocket = DatagramSocket()
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the sender socket: ${e.message}")
        }

        receiveJob = scope.launch(Dispatchers.IO) {
            val socket = multicastSocket ?: return@launch
            val buffer = ByteArray(4096)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (e: Exception) {
                    break
                }
                handleHello(packet)
            }
        }

        sendJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                announce()
                dropSilentPeers()
                delay(HEARTBEAT_MS)
            }
        }
    }

    fun stop() {
        receiveJob?.cancel()
        receiveJob = null
        sendJob?.cancel()
        sendJob = null
        runCatching { multicastSocket?.close() }
        multicastSocket = null
        runCatching { sendSocket?.close() }
        sendSocket = null
        runCatching { multicastLock?.release() }
        multicastLock = null
        peers.clear()
    }

    private fun handleHello(packet: DatagramPacket) {
        val hello = decryptHello(packet.data, packet.length) ?: return
        val id = hello.optString("id")
        if (id.isEmpty() || id == peerId) return
        val address = packet.address?.hostAddress ?: return
        val peer = Peer(
            id = id,
            address = address,
            port = hello.optInt("port", tcpPort),
            nickname = hello.optString("name", "anon"),
            lastSeen = System.currentTimeMillis()
        )
        peers[id] = peer
        onPeer?.invoke(peer)
    }

    private fun announce() {
        val socket = sendSocket ?: return
        val hello = JSONObject()
            .put("v", 1)
            .put("id", peerId)
            .put("name", nickname)
            .put("port", tcpPort)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val ciphertext = RoomCrypto.encrypt(key, AAD, hello) ?: return

        val payload = ByteArrayOutputStream()
        val data = DataOutputStream(payload)
        data.writeInt(ciphertext.size)
        data.write(ciphertext)
        data.flush()
        val bytes = payload.toByteArray()

        targets().forEach { address ->
            try {
                socket.send(DatagramPacket(bytes, bytes.size, address, DISCOVERY_PORT))
            } catch (_: Exception) {
            }
        }
    }

    private fun decryptHello(data: ByteArray, length: Int): JSONObject? = try {
        val stream = DataInputStream(java.io.ByteArrayInputStream(data, 0, length))
        val size = stream.readInt()
        if (size <= 0 || size > 2048) {
            null
        } else {
            val ciphertext = ByteArray(size)
            stream.readFully(ciphertext)
            val plaintext = RoomCrypto.decrypt(key, AAD, ciphertext)
            if (plaintext == null) null else JSONObject(String(plaintext, Charsets.UTF_8))
        }
    } catch (_: Exception) {
        null
    }

    /** Broadcast addresses of the phone plus the multicast group. */
    private fun targets(): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        try {
            result.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Exception) {
        }
        try {
            result.add(InetAddress.getByName(MULTICAST_GROUP))
        } catch (_: Exception) {
        }
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.interfaceAddresses
                for (address in addresses) {
                    val broadcast = address.broadcast
                    if (broadcast is Inet4Address) result.add(broadcast)
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun dropSilentPeers() {
        val now = System.currentTimeMillis()
        val iterator = peers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastSeen > PEER_TIMEOUT_MS) {
                iterator.remove()
            }
        }
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            val lock = wifiManager.createMulticastLock("vuzol-lan-room")
            lock.setReferenceCounted(false)
            lock.acquire()
            multicastLock = lock
        } catch (e: Exception) {
            Log.w(TAG, "Multicast lock failed: ${e.message}")
        }
    }
}
