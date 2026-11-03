package com.roman.zemzeme.ui

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.roman.zemzeme.model.DeliveryStatus
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.nostr.NostrIdentityBridge
import com.roman.zemzeme.nostr.NostrProtocol
import com.roman.zemzeme.nostr.NostrRelayManager
import com.roman.zemzeme.p2p.P2PConfig
import com.roman.zemzeme.services.AppStateStore
import com.roman.zemzeme.services.ChatHistoryStore
import com.roman.zemzeme.services.ReadStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Handles the quick actions attached to chat notifications:
 *  - inline reply (text entered right in the notification)
 *  - mark the conversation as read
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationActionReceiver"
        const val ACTION_REPLY = "com.roman.zemzeme.action.NOTIFICATION_REPLY"
        const val ACTION_MARK_READ = "com.roman.zemzeme.action.NOTIFICATION_MARK_READ"
        const val EXTRA_GEOHASH = "geohash"
        const val EXTRA_REPLY_KEY = "reply_key"
        const val REPLY_KEY = "vuzol_reply_text"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geohash = intent.getStringExtra(EXTRA_GEOHASH) ?: return
        when (intent.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(REPLY_KEY)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                if (text.isEmpty()) return
                sendReply(context, geohash, text)
            }
            ACTION_MARK_READ -> {
                val conv = ChatHistoryStore.conversationForChannel("geo:$geohash")
                ReadStateStore.markRead(conv)
                notificationManager(context.applicationContext).clearNotificationsForGeohash(geohash)
            }
        }
    }

    private fun notificationManager(context: Context): NotificationManager =
        NotificationManager(
            context.applicationContext,
            androidx.core.app.NotificationManagerCompat.from(context.applicationContext),
            com.roman.zemzeme.util.NotificationIntervalManager()
        )

    private fun sendReply(context: Context, geohash: String, text: String) {
        val appContext = context.applicationContext
        val p2pConfig = P2PConfig(appContext)
        val nickname = runCatching {
            appContext.getSharedPreferences("bitchat_settings", Context.MODE_PRIVATE)
                .getString("nickname", null)
        }.getOrNull()

        scope.launch {
            try {
                val identity = NostrIdentityBridge.deriveIdentity(
                    forGeohash = geohash,
                    context = appContext
                )
                val event = NostrProtocol.createEphemeralGeohashEvent(
                    content = text,
                    geohash = geohash,
                    senderIdentity = identity,
                    nickname = nickname
                )
                NostrRelayManager.getInstance(appContext)
                    .sendEventToGeohash(event, geohash, includeDefaults = true, nRelays = 5)

                val localMessage = ZemzemeMessage(
                    id = "notif_reply_${System.currentTimeMillis()}",
                    sender = nickname ?: "me",
                    content = text,
                    timestamp = Date(),
                    senderPeerID = "geohash:$geohash",
                    channel = "#$geohash",
                    deliveryStatus = DeliveryStatus.Sent
                )
                AppStateStore.addChannelMessage("geo:$geohash", localMessage)
            } catch (e: Exception) {
                Log.w(TAG, "notification reply failed: ${e.message}")
            } finally {
                try {
                    val conv = ChatHistoryStore.conversationForChannel("geo:$geohash")
                    ReadStateStore.markRead(conv)
                    notificationManager(appContext).clearNotificationsForGeohash(geohash)
                } catch (_: Exception) { }
            }
        }
    }
}
