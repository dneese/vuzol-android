package com.roman.zemzeme.wifi

/**
 * Small abstraction over the two WiFi Direct APIs.
 *
 * Android 15 (API 35) removed the old WiFi P2P API and introduced a new
 * listener based one. The old API still works on Android 12 and older, but it
 * cannot be compiled against the current SDK any more, so the legacy path
 * talks to the platform through reflection.
 */
internal enum class WifiP2pLinkRole { IDLE, HOST, CLIENT }

internal interface WifiP2pLink {

    var onStatus: ((String) -> Unit)?
    var onPeerCount: ((Int) -> Unit)?
    var onRole: ((WifiP2pLinkRole) -> Unit)?
    var onHostAddress: ((String) -> Unit)?

    /** Starts discovery and joins or creates the room. */
    fun start(roomName: String, passphrase: String, asHost: Boolean)

    fun stop()

    companion object {
        /** Creates the implementation that fits the running Android version. */
        fun create(
            context: android.content.Context,
            scope: kotlinx.coroutines.CoroutineScope
        ): WifiP2pLink? {
            val modern = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM
            return if (modern) {
                try {
                    WifiP2pLinkModern(context, scope)
                } catch (e: Exception) {
                    android.util.Log.w("WifiP2pLink", "Modern WiFi Direct unavailable: ${e.message}")
                    null
                }
            } else {
                try {
                    WifiP2pLinkLegacy(context, scope)
                } catch (e: Exception) {
                    android.util.Log.w("WifiP2pLink", "Legacy WiFi Direct unavailable: ${e.message}")
                    null
                }
            }
        }
    }
}
