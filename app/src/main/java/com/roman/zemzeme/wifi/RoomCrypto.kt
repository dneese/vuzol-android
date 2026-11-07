package com.roman.zemzeme.wifi

import com.google.crypto.tink.subtle.XChaCha20Poly1305
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto of the offline rooms.
 *
 * The room code and the optional password give one symmetric key. Every frame
 * of a room is encrypted with it, so a phone that relays traffic between the
 * others does not read it. Frames of a room with a different code or password
 * cannot be decrypted and are dropped.
 */
internal object RoomCrypto {

    /** Id of a room, 16 bytes, used as the associated data of every frame. */
    fun roomId(roomCode: String): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest((roomCode.trim().lowercase() + "|room-id").toByteArray())
            .copyOf(16)

    /**
     * Derives the room key. The same code and password always give the same
     * key on every phone, no server is involved.
     */
    fun deriveKey(roomCode: String, password: String, info: String): ByteArray {
        val salt = MessageDigest.getInstance("SHA-256")
            .digest(roomCode.trim().lowercase().toByteArray())
        val prk = hmac(salt, password.toByteArray(Charsets.UTF_8))
        return hkdfExpand(prk, info.toByteArray(Charsets.UTF_8), 32)
    }

    fun encrypt(key: ByteArray, associatedData: ByteArray, plaintext: ByteArray): ByteArray? = try {
        XChaCha20Poly1305(key).encrypt(plaintext, associatedData)
    } catch (_: Exception) {
        null
    }

    fun decrypt(key: ByteArray, associatedData: ByteArray, ciphertext: ByteArray): ByteArray? = try {
        XChaCha20Poly1305(key).decrypt(ciphertext, associatedData)
    } catch (_: Exception) {
        null
    }

    /** Short hash of the room code, safe to show or to compare. */
    fun roomFingerprint(roomCode: String): String =
        roomId(roomCode).take(4).joinToString("") { "%02x".format(it) }

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
