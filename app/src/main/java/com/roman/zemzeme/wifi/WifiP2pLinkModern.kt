package com.roman.zemzeme.wifi

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * WiFi Direct for Android 15 and newer, using the rewritten listener API.
 *
 * This class is only loaded on API 35+, older phones use
 * [WifiP2pLinkLegacy] instead.
 */
internal class WifiP2pLinkModern(
    private val context: Context,
    private val scope: CoroutineScope
) : WifiP2pLink {

    companion object {
        private const val TAG = "WifiP2pModern"
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

    private val channelListener = object : WifiP2pManager.ChannelListener {
        override fun onChannelDisconnected() {
            status("channel lost, reconnecting")
            openChannel()
        }
    }

    private val p2pListener = object : WifiP2pManager.WifiP2pListener {
        override fun onP2pStateChanged(state: Int) {
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                status("WiFi Direct ready")
                if (hostMode) createGroup()
            } else {
                status("WiFi Direct is switched off")
            }
        }

        override fun onPeerListChanged(peers: WifiP2pDeviceList) {
            val devices = peers.deviceList.toList()
            peerCount = devices.size
            onPeerCount?.invoke(peerCount)
            if (!hostMode && !connecting && devices.isNotEmpty()) {
                connectTo(devices.first())
            }
        }

        override fun onGroupCreated(groupInfo: WifiP2pInfo, group: WifiP2pGroup) {
            peerCount = group.clientList.size
            onPeerCount?.invoke(peerCount)
            connecting = false
            if (group.isGroupOwner) {
                onRole?.invoke(WifiP2pLinkRole.HOST)
                status("hosting the room · $peerCount peers")
            } else {
                onRole?.invoke(WifiP2pLinkRole.CLIENT)
                val address = groupInfo.groupOwnerAddress?.hostAddress
                if (!address.isNullOrBlank()) onHostAddress?.invoke(address)
                status("connected · $peerCount peers")
            }
        }

        override fun onPeerClientJoined(groupInfo: WifiP2pInfo, group: WifiP2pGroup) {
            peerCount = group.clientList.size
            onPeerCount?.invoke(peerCount)
        }

        override fun onPeerClientDisconnected(groupInfo: WifiP2pInfo, group: WifiP2pGroup) {
            peerCount = group.clientList.size
            onPeerCount?.invoke(peerCount)
        }

        override fun onGroupRemoved() {
            onRole?.invoke(WifiP2pLinkRole.IDLE)
            status("room closed")
        }
    }

    init {
        openChannel()
        try {
            manager?.registerWifiP2pListener(context.mainExecutor, p2pListener)
        } catch (e: Exception) {
            Log.w(TAG, "registerWifiP2pListener failed: ${e.message}")
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
        val address = device.deviceAddress ?: return
        val mac = try {
            MacAddress.fromString(address)
        } catch (e: Exception) {
            null
        } ?: return
        connecting = true
        val config = WifiP2pConfig.Builder()
            .setDeviceAddress(mac)
            .setNetworkName(roomName)
            .setPassphrase(passphrase)
            .build()
        try {
            manager?.connect(
                ch,
                config,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = status("joining the room")
                    override fun onFailure(reason: Int) {
                        connecting = false
                        status("could not join the room: $reason")
                    }
                }
            )
        } catch (e: Exception) {
            connecting = false
            Log.w(TAG, "connect failed: ${e.message}")
        }
    }

    private fun status(value: String) {
        scope.launch(Dispatchers.Main) { onStatus?.invoke(value) }
    }
}
