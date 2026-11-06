package com.roman.zemzeme.ui

/**
 * Invite helpers for private (custom) groups.
 *
 * A group is identified by a short code. The invite can be shared as:
 *  - a plain code (typed manually),
 *  - a web link `https://dneese.github.io/vuzol-android/?code=XXXXXX`
 *    that works in any browser and leads to the app,
 *  - a deep link `vuzol://join/group?code=XXXXXX` that opens the app directly,
 *  - a QR code that encodes the web link.
 */
object GroupInvite {
    const val SCHEME = "vuzol"
    const val HOST = "join"

    /** Public landing page that explains the invite and offers the app download. */
    const val WEB_BASE = "https://dneese.github.io/vuzol-android/"

    /** Deep link that opens the app and joins the group. */
    fun linkFor(code: String, password: String? = null): String =
        if (password.isNullOrBlank()) "$SCHEME://$HOST/group?code=$code"
        else "$SCHEME://$HOST/group?code=$code&p=${encodePassword(password)}"

    /** Web link that works in any browser and offers to open the app. */
    fun webLinkFor(code: String, password: String? = null): String =
        if (password.isNullOrBlank()) "$WEB_BASE?code=$code"
        else "$WEB_BASE?code=$code&p=${encodePassword(password)}"

    /** Text that is shared with the invitee. */
    fun shareText(groupName: String, code: String, password: String? = null): String {
        val base = "Join the Vuzol group \"$groupName\"\nInvite code: $code"
        val withPassword = if (password.isNullOrBlank()) "" else "\nPassword: $password"
        return "$base$withPassword\n${webLinkFor(code, password)}"
    }

    private fun encodePassword(password: String): String =
        java.net.URLEncoder.encode(password, Charsets.UTF_8.name())

    /**
     * Extracts the group password from an invite link.
     */
    fun passwordFrom(text: String?): String? {
        val raw = text?.trim() ?: return null
        if (raw.isEmpty()) return null
        val match = Regex("[?&]p=([^&\\s]+)").find(raw) ?: return null
        return try {
            java.net.URLDecoder.decode(match.groupValues[1], Charsets.UTF_8.name())
        } catch (_: Exception) {
            match.groupValues[1]
        }
    }

    /**
     * Extracts a group code from a link, a QR payload or plain text.
     * Returns null when the text does not contain a valid code.
     */
    fun codeFrom(text: String?): String? {
        val raw = text?.trim() ?: return null
        if (raw.isEmpty()) return null

        // Web link: https://dneese.github.io/vuzol-android/?code=XXXXXX
        // Deep link: vuzol://join/group?code=XXXXXX
        val linkMatch = Regex("[?&]code=([A-Za-z0-9]+)").find(raw)
        if (linkMatch != null) {
            val code = linkMatch.groupValues[1]
            if (isValidCode(code)) return code.lowercase()
        }

        // JSON payload: {"type":"vuzol_group","code":"XXXXXX"}
        val jsonMatch = Regex("\"code\"\\s*:\\s*\"([A-Za-z0-9]+)\"").find(raw)
        if (jsonMatch != null) {
            val code = jsonMatch.groupValues[1]
            if (isValidCode(code)) return code.lowercase()
        }

        // Plain code
        val plain = raw.trim()
        if (isValidCode(plain)) return plain.lowercase()

        return null
    }

    fun isValidCode(code: String): Boolean =
        code.length in 4..12 && code.all { it.isLetterOrDigit() }
}
