package com.roman.zemzeme.groups

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the password of private groups.
 *
 * A group with a password is encrypted end to end: the key is derived from
 * the password and the group code, so only members who know the password can
 * read what is written inside the group.
 */
object GroupSpaceStore {

    private const val TAG = "GroupSpaceStore"
    private const val PREFS_NAME = "group_space_secrets"
    private const val KEY_PREFIX = "space_"

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        prefs?.let { return it }
        synchronized(this) {
            prefs?.let { return it }
            val created = try {
                val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open encrypted prefs, falling back to plain prefs: ${e.message}")
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
            prefs = created
            return created
        }
    }

    fun setPassword(context: Context, geohash: String, password: String) {
        if (geohash.isBlank() || password.isBlank()) return
        prefs(context).edit().putString(KEY_PREFIX + geohash.lowercase(), password).apply()
    }

    fun password(context: Context, geohash: String): String? {
        if (geohash.isBlank()) return null
        return prefs(context).getString(KEY_PREFIX + geohash.lowercase(), null)
    }

    fun hasPassword(context: Context, geohash: String): Boolean =
        !password(context, geohash).isNullOrBlank()

    fun removePassword(context: Context, geohash: String) {
        prefs(context).edit().remove(KEY_PREFIX + geohash.lowercase()).apply()
    }
}
