package com.roman.zemzeme

import android.app.Application
import com.roman.zemzeme.iconswitch.IconSwitchTaskDescription
import com.roman.zemzeme.nostr.RelayDirectory
import com.roman.zemzeme.ui.theme.ThemePreferenceManager
import com.roman.zemzeme.net.ArtiTorManager

/**
 * Main application class for vuzol Android
 */
class VuzolApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Tor first so any early network goes over Tor
        try {
            val torProvider = ArtiTorManager.getInstance()
            torProvider.init(this)
        } catch (_: Exception){}

        // Initialize relay directory (loads assets/nostr_relays.csv)
        RelayDirectory.initialize(this)

        // Initialize LocationNotesManager dependencies early so sheet subscriptions can start immediately
        try { com.roman.zemzeme.nostr.LocationNotesInitializer.initialize(this) } catch (_: Exception) { }

        // Initialize favorites persistence early so MessageRouter/NostrTransport can use it on startup
        try {
            com.roman.zemzeme.favorites.FavoritesPersistenceService.initialize(this)
        } catch (_: Exception) { }

        // Warm up Nostr identity to ensure npub is available for favorite notifications
        try {
            com.roman.zemzeme.nostr.NostrIdentityBridge.getCurrentNostrIdentity(this)
        } catch (_: Exception) { }

        // Initialize local chat history and restore it into the in-memory store
        try {
            com.roman.zemzeme.services.ChatHistoryStore.init(this)
            Thread({
                try {
                    val history = com.roman.zemzeme.services.ChatHistoryStore.loadAll()
                    com.roman.zemzeme.services.AppStateStore.hydrate(history)
                    com.roman.zemzeme.ui.ReactionsStore.load(
                        com.roman.zemzeme.services.ChatHistoryStore.loadReactions()
                    )
                } catch (_: Exception) { }
            }, "vuzol-history-load").apply { isDaemon = true }.start()
        } catch (_: Exception) { }

        // Notification sound/vibration preferences, drafts, read state and mutes
        try { com.roman.zemzeme.services.DraftStore.init(this) } catch (_: Exception) { }
        try { com.roman.zemzeme.services.ReadStateStore.init(this) } catch (_: Exception) { }
        try { com.roman.zemzeme.services.MuteStore.init(this) } catch (_: Exception) { }
        try { com.roman.zemzeme.services.PinStore.init(this) } catch (_: Exception) { }

        // Initialize theme preference
        ThemePreferenceManager.init(this)

        // Notification sound/vibration preferences (and channel setup)
        try { com.roman.zemzeme.ui.NotificationPreferences.init(this) } catch (_: Exception) { }

        // Initialize debug preference manager (persists debug toggles)
        try { com.roman.zemzeme.ui.debug.DebugPreferenceManager.init(this) } catch (_: Exception) { }

        // Initialize Geohash Registries for persistence
        try {
            com.roman.zemzeme.nostr.GeohashAliasRegistry.initialize(this)
            com.roman.zemzeme.nostr.GeohashConversationRegistry.initialize(this)
        } catch (_: Exception) { }

        // Initialize P2P Alias Registry for P2P peer display name persistence
        try {
            com.roman.zemzeme.p2p.P2PAliasRegistry.initialize(this)
            com.roman.zemzeme.p2p.P2PFavoritesRegistry.initialize(this)
        } catch (_: Exception) { }

        // Initialize mesh service preferences
        try { com.roman.zemzeme.service.MeshServicePreferences.init(this) } catch (_: Exception) { }

        // Proactively start the foreground service to keep mesh alive
        try { com.roman.zemzeme.service.MeshForegroundService.start(this) } catch (_: Exception) { }

        // Update recent-apps card with current dynamic icon/name
        registerActivityLifecycleCallbacks(IconSwitchTaskDescription())

        // TorManager already initialized above
    }
}
