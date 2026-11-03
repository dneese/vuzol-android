package com.roman.zemzeme.ui

import com.roman.zemzeme.net.OkHttpProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request

/** Preview card data for a link inside a message. */
data class LinkPreview(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null
)

/**
 * Fetches small "unfurl" previews for links posted in chat, so a message with a
 * URL looks like a rich card instead of plain text.
 */
object LinkPreviewStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requested = mutableSetOf<String>()

    private val _previews = MutableStateFlow<Map<String, LinkPreview>>(emptyMap())
    val previews: StateFlow<Map<String, LinkPreview>> = _previews.asStateFlow()

    private val urlRegex = Regex("https?://\\S+")

    fun firstUrl(text: String): String? =
        urlRegex.find(text)?.value?.trimEnd('.', ',', ';', ')', ']', '"', '\'')

    fun ensure(url: String) {
        synchronized(requested) {
            if (!requested.add(url)) return
        }
        scope.launch {
            val preview = fetch(url) ?: return@launch
            _previews.value = _previews.value.toMutableMap().apply { put(url, preview) }
        }
    }

    private fun fetch(url: String): LinkPreview? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) Vuzol/1.2")
                .build()
            OkHttpProvider.httpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val contentType = response.header("Content-Type") ?: ""
                if (!contentType.contains("html", ignoreCase = true)) return null
                val bytes = response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var total = 0
                    while (total < buffer.size) {
                        val read = input.read(buffer, total, buffer.size - total)
                        if (read <= 0) break
                        total += read
                    }
                    buffer.copyOf(total)
                } ?: return null
                val html = String(bytes, Charsets.UTF_8)

                val title = tagContent(html, "title")
                    ?: metaContent(html, "og:title")
                val description = metaContent(html, "og:description")
                    ?: metaContent(html, "description")
                val image = metaContent(html, "og:image")
                val site = metaContent(html, "og:site_name")

                LinkPreview(
                    url = url,
                    title = title?.take(160)?.trim(),
                    description = description?.take(300)?.trim(),
                    imageUrl = image,
                    siteName = site?.take(60)?.trim()
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tagContent(html: String, tag: String): String? {
        val match = Regex("(?is)<$tag[^>]*>(.*?)</$tag>").find(html) ?: return null
        return clean(match.groupValues.getOrNull(1) ?: return null)
    }

    private fun metaContent(html: String, key: String): String? {
        val pattern = Regex(
            "(?is)<meta[^>]*(?:property|name)=[\"']$key[\"'][^>]*content=[\"'](.*?)[\"']"
        )
        val match = pattern.find(html) ?: return null
        return clean(match.groupValues.getOrNull(1) ?: return null)
    }

    private fun clean(raw: String): String? {
        val text = raw.replace(Regex("<[^>]*>"), " ")
            .replace("&", "&")
            .replace(""", "\"")
            .replace("'", "'")
            .replace("<", "<")
            .replace(">", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
        return text.ifEmpty { null }
    }
}
