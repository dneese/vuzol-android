package com.roman.zemzeme.nostr

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** NIP-01 profile metadata for a Nostr public key. */
data class NostrProfile(
    val name: String? = null,
    val displayName: String? = null,
    val picture: String? = null,
    val about: String? = null,
    val nip05: String? = null
)

/**
 * Cache of NIP-01 metadata (kind 0) so people in a channel can show a real
 * avatar and profile name instead of a coloured letter.
 */
object NostrProfileStore {
    private const val PREFS = "vuzol_nostr_profiles"
    private const val KEY = "profiles"

    private val _profiles = MutableStateFlow<Map<String, NostrProfile>>(emptyMap())
    val profiles: StateFlow<Map<String, NostrProfile>> = _profiles.asStateFlow()

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val json = p.getString(KEY, null) ?: return
        try {
            val type = object : TypeToken<Map<String, NostrProfile>>() {}.type
            val loaded: Map<String, NostrProfile> = Gson().fromJson(json, type) ?: emptyMap()
            _profiles.value = loaded
        } catch (_: Exception) {
        }
    }

    fun profile(pubkey: String?): NostrProfile? =
        pubkey?.let { _profiles.value[it.lowercase()] }

    fun apply(pubkey: String, profile: NostrProfile) {
        val key = pubkey.lowercase()
        _profiles.value = _profiles.value.toMutableMap().apply { put(key, profile) }
        persist()
    }

    private fun persist() {
        try {
            prefs?.edit()?.putString(KEY, Gson().toJson(_profiles.value))?.apply()
        } catch (_: Exception) {
        }
    }

    fun parse(content: String): NostrProfile? {
        if (content.isBlank()) return null
        return try {
            val obj = JSONObject(content)
            NostrProfile(
                name = obj.optString("name").takeIf { it.isNotBlank() },
                displayName = obj.optString("display_name").takeIf { it.isNotBlank() },
                picture = obj.optString("picture").takeIf { it.isNotBlank() },
                about = obj.optString("about").takeIf { it.isNotBlank() },
                nip05 = obj.optString("nip05").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            null
        }
    }
}
