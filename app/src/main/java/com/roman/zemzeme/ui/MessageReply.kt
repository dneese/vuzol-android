package com.roman.zemzeme.ui

import com.roman.zemzeme.model.ZemzemeMessage

/**
 * Reply quoting for chat messages.
 *
 * A reply is carried inside the message text as a leading block of lines that
 * start with "> ". Every transport (BLE mesh, P2P, Nostr) carries message text
 * unchanged, so replies work everywhere without touching the wire format, and
 * clients that do not understand the quoting still show a readable message.
 */
object MessageReply {

    const val QUOTE_PREFIX = "> "
    private const val MAX_QUOTE_LENGTH = 160
    private const val ELLIPSIS = "…"

    /**
     * Builds the text that is actually sent: the quoted line followed by the
     * new message text.
     */
    fun encode(original: ZemzemeMessage, body: String): String {
        val sender = original.sender.ifBlank { "?" }
        val flat = original.content.replace("\r", " ").replace("\n", " ").trim()
        val snippet = if (flat.length > MAX_QUOTE_LENGTH) {
            flat.take(MAX_QUOTE_LENGTH).trimEnd() + ELLIPSIS
        } else {
            flat
        }
        return "$QUOTE_PREFIX$sender: $snippet\n$body"
    }

    /**
     * Splits message text into the quoted part (without "> ") and the rest.
     * Returns null as the quote when the message is not a reply.
     */
    fun split(content: String): Pair<String?, String> {
        if (!content.startsWith(QUOTE_PREFIX)) return null to content
        val lines = content.lines()
        val quoteLines = lines.takeWhile { it.startsWith(QUOTE_PREFIX) }
        if (quoteLines.isEmpty()) return null to content
        val quote = quoteLines
            .joinToString("\n") { it.removePrefix(QUOTE_PREFIX) }
            .trim()
        val body = lines.drop(quoteLines.size).joinToString("\n")
        return quote.ifEmpty { null } to body
    }

    /** Text used in the reply bar above the input. */
    fun preview(message: ZemzemeMessage): String {
        val flat = message.content
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
        return if (flat.length > MAX_QUOTE_LENGTH) {
            flat.take(MAX_QUOTE_LENGTH).trimEnd() + ELLIPSIS
        } else {
            flat
        }
    }
}
