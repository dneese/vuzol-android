package com.roman.zemzeme.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Parcelable
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * WiFi Direct for Android 12 and older.
 *
 * The old platform API was removed from the SDK in Android 15, so the calls
 * that changed are made through reflection. Everything that stayed the same
 * (discovery, group creation) is called directly.
 */
internal class WifiP2pLinkLegacy(
    private val context: Context,
    private val scope: CoroutineScope
) : WifiP2pLink {

    companion object {
        private const val TAG = "WifiP2pLegacy"

        // Action and extra names of the old broadcast API
        private const val ACTION_STATE_CHANGED = "android.net.wifi.p2p.STATE_CHANGED"
        private const val ACTION_PEERS_CHANGED = "android.net.wifi.p2p.PEERS_CHANGED"
        private const val ACTION_CONNECTION_CHANGED = "android.net.wifi.p2p.CONNECTION_CHANGED"
        private const val ACTION_GROUP_CHANGED = "android.net.wifi.p2p.GROUP_CHANGED"
        private const val EXTRA_STATE = "WifiP2pState"
        private const val EXTRA_DEVICE_LIST = "wifiP2pDeviceList"
        private const val EXTRA_INFO = "WifiP2pInfo"
        private const val STATE_ENABLED = 1
    }

    override var onStatus: ((String) -> Unit)? = null
    override var onPeerCount: ((Int) -> Unit)? = null
    override var onRole: ((WifiP2pLinkRole) -> Unit)? = null
    override var onHostAddress: ((String) -> Unit)? = null

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private var channel: WifiP2pManager.Channel? = null
    private var discoveryJob: Job? = null
    private var roomName = ""
    private var passphrase = ""
    private var hostMode = false
    private var connecting = false
    private var peerCount = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(EXTRA_STATE, 0)
                    if (state == STATE_ENABLED) {
                        status("WiFi Direct ready")
                        if (hostMode) createGroup()
                    } else {
                        status("WiFi Direct is switched off")
                    }
                }
                ACTION_PEERS_CHANGED -> {
                    @Suppress("DEPRECATION")
                    val raw: Parcelable? = intent.getParcelableExtra(EXTRA_DEVICE_LIST)
                    // Android 13 and older deliver a plain list of devices
                    val devices: List<WifiP2pDevice> = when (raw) {
                        is WifiP2pDeviceList -> raw.deviceList.toList()
                        is ArrayList<*> -> raw.filterIsInstance<WifiP2pDevice>()
                        else -> emptyList()
                    }
                    peerCount = devices.size
                    onPeerCount?.invoke(peerCount)
                    if (!hostMode && !connecting && devices.isNotEmpty()) {
                        connectTo(devices.first())
                    }
                }
                ACTION_CONNECTION_CHANGED -> {
                    @Suppress("DEPRECATION")
                    val info = intent.getParcelableExtra(EXTRA_INFO) as? WifiP2pInfo
                    if (info == null || !info.groupFormed) return
                    connecting = false
                    if (info.isGroupOwner) {
                        onRole?.invoke(WifiP2pLinkRole.HOST)
                        status("hosting the room · $peerCount peers")
                    } else {
                        onRole?.invoke(WifiP2pLinkRole.CLIENT)
                        val address = info.groupOwnerAddress?.hostAddress
                        if (!address.isNullOrBlank()) onHostAddress?.invoke(address)
                        status("connected · $peerCount peers")
                    }
                }
                ACTION_GROUP_CHANGED -> {
                    @Suppress("DEPRECATION")
                    val group: Parcelable? = intent.getParcelableExtra("WifiP2pGroup")
                    if (group == null) {
                        onRole?.invoke(WifiP2pLinkRole.IDLE)
                        status("room closed")
                    }
                }
            }
        }
    }

    init {
        try {
            context.registerReceiver(receiver, IntentFilter().apply {
                addAction(ACTION_STATE_CHANGED)
                addAction(ACTION_PEERS_CHANGED)
                addAction(ACTION_CONNECTION_CHANGED)
                addAction(ACTION_GROUP_CHANGED)
            })
        } catch (e: Exception) {
            Log.w(TAG, "Could not register receiver: ${e.message}")
        }
        openChannel()
    }

    private fun openChannel() {
        val mgr = manager ?: return
        try {
            // The receiver based initialize() only exists before Android 15
            val method = mgr.javaClass.getMethod(
                "initialize",
                Context::class.java,
                android.os.Looper::class.java,
                BroadcastReceiver::class.java
            )
            channel = method.invoke(mgr, context, context.mainLooper, receiver) as WifiP2pManager.Channel
        } catch (e: Exception) {
            Log.w(TAG, "initialize failed: ${e.message}")
        }
    }

    override fun start(roomName: String, passphrase: String, asHost: Boolean) {
        this.roomName = roomName
        this.passphrase = passphrase
        this.hostMode = asHost
        status(if (asHost) "starting the room" else "searching for the room")
        startDiscoveryLoop()
        if (asHost) createGroup()
    }

    override fun stop() {
        discoveryJob?.cancel()
        discoveryJob = null
        connecting = false
        hostMode = false
        peerCount = 0
        onRole?.invoke(WifiP2pLinkRole.IDLE)
        try {
            manager?.removeGroup(channel, null)
        } catch (_: Exception) {
        }
        status("stopped")
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
            .setNetworkName(roomName)
            .setPassphrase(passphrase)
            .build()
        try {
            manager?.createGroup(
                ch,
                config,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = status("waiting for clients")
                    override fun onFailure(reason: Int) = status("could not start the room: $reason")
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "createGroup failed: ${e.message}")
        }
    }

    private fun connectTo(device: WifiP2pDevice) {
        val mgr = manager ?: return
        val ch = channel ?: return
        val config = WifiP2pConfig.Builder()
            .setNetworkName(roomName)
            .setPassphrase(passphrase)
            .build()
        connecting = true
        try {
            // The device parameter was removed in Android 15
            val method = mgr.javaClass.getMethod(
                "connect",
                WifiP2pManager.Channel::class.java,
                WifiP2pDevice::class.java,
                WifiP2pConfig::class.java
            )
            method.invoke(mgr, ch, device, config)
        } catch (e: Exception) {
            connecting = false
            Log.w(TAG, "connect failed: ${e.message}")
            status("could not join the room")
        }
    }

    private fun status(value: String) {
        scope.launch(Dispatchers.Main) { onStatus?.invoke(value) }
    }
}
