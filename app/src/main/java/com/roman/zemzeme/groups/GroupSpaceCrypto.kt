package com.roman.zemzeme.groups

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.subtle.XChaCha20Poly1305
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Encryption for private groups.
 *
 * The content key is derived from the group password and the group code with
 * HKDF-SHA256, so the same password always opens the same group while a
 * leaked key never opens another group.
 */
object GroupSpaceCrypto {

    private const val PREFIX = "enc1:"
    private const val INFO = "vuzol-group-v1"

    /** True when the group is protected by a password. */
    fun isEncrypted(context: Context, geohash: String): Boolean =
        GroupSpaceStore.hasPassword(context, geohash)

    /**
     * Encrypts the payload when the group has a password, otherwise returns it
     * unchanged.
     */
    fun encryptIfNeeded(context: Context, geohash: String, payload: String): String {
        val password = GroupSpaceStore.password(context, geohash) ?: return payload
        return encrypt(geohash, password, payload)
    }

    /**
     * Decrypts a payload when it is encrypted and the group password is known.
     * Returns null when the group is locked or the payload is broken.
     */
    fun decryptIfNeeded(context: Context, geohash: String, payload: String): String? {
        if (!payload.startsWith(PREFIX)) return payload
        val password = GroupSpaceStore.password(context, geohash) ?: return null
        return decrypt(geohash, password, payload)
    }

    fun isEncryptedPayload(payload: String): Boolean = payload.startsWith(PREFIX)

    fun encrypt(geohash: String, password: String, payload: String): String {
        return try {
            val key = deriveKey(geohash, password)
            val aead = XChaCha20Poly1305(key)
            val sealed = aead.encrypt(payload.toByteArray(Charsets.UTF_8), aad(geohash))
            PREFIX + Base64.encodeToString(sealed, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (e: Exception) {
            payload
        }
    }

    fun decrypt(geohash: String, password: String, payload: String): String? {
        return try {
            val encoded = payload.removePrefix(PREFIX)
            val sealed = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val key = deriveKey(geohash, password)
            val aead = XChaCha20Poly1305(key)
            val plain = aead.decrypt(sealed, aad(geohash))
            String(plain, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** HKDF-SHA256 over the password, salted with the group code. */
    fun deriveKey(geohash: String, password: String): ByteArray {
        val salt = MessageDigest.getInstance("SHA-256").digest(geohash.lowercase().toByteArray())
        val prk = hmac(salt, password.toByteArray(Charsets.UTF_8))
        return hkdfExpand(prk, INFO.toByteArray(Charsets.UTF_8), 32)
    }

    private fun aad(geohash: String): ByteArray =
        "vuzol-space|${geohash.lowercase()}".toByteArray(Charsets.UTF_8)

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
}
