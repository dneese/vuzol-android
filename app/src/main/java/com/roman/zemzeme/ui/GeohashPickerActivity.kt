package com.roman.zemzeme.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.roman.zemzeme.BuildConfig
import androidx.core.view.updateLayoutParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import com.roman.zemzeme.geohash.Geohash
import com.roman.zemzeme.geohash.LocationChannelManager

class GeohashPickerActivity : OrientationAwareActivity() {

    companion object {
        const val EXTRA_INITIAL_GEOHASH = "initial_geohash"
        const val EXTRA_RESULT_GEOHASH = "result_geohash"
        private const val COUNTRIES_ASSET = "countries.json"
        private const val NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
    }

    private var webView: WebView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onDestroy() {
        scope.cancel()
        webView = null
        super.onDestroy()
    }

    private fun countriesJson(): String {
        return try {
            assets.open(COUNTRIES_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Throwable) {
            "[]"
        }
    }

    /**
     * Resolve a place name to coordinates with OpenStreetMap (Nominatim).
     * The result is passed back to the map as a JSON array.
     */
    private fun searchPlaces(query: String, countryCode: String) {
        scope.launch {
            val trimmed = query.trim()
            if (trimmed.isEmpty() && countryCode.isEmpty()) return@launch
            val searchTerm = if (trimmed.isNotEmpty()) trimmed else countryCode
            val countryParam = if (countryCode.isNotEmpty()) {
                "&countrycodes=" + URLEncoder.encode(countryCode.lowercase(), "UTF-8")
            } else {
                ""
            }
            val url = "$NOMINATIM_URL?q=" +
                    URLEncoder.encode(searchTerm, "UTF-8") +
                    countryParam +
                    "&format=jsonv2&limit=12&addressdetails=1"
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Vuzol/${BuildConfig.VERSION_NAME} (Android)")
                        .header("Accept", "application/json")
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) "" else response.body?.string().orEmpty()
                    }
                }.getOrDefault("")
            }
            if (raw.isBlank()) return@launch
            val places = try {
                val array = org.json.JSONArray(raw)
                val out = org.json.JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val lat = item.optString("lat").toDoubleOrNull() ?: continue
                    val lon = item.optString("lon").toDoubleOrNull() ?: continue
                    val label = item.optString("display_name").ifBlank { item.optString("name") }
                    val entry = org.json.JSONObject()
                    entry.put("label", label)
                    entry.put("lat", lat)
                    entry.put("lon", lon)
                    entry.put("precision", 5)
                    out.put(entry)
                }
                out.toString()
            } catch (_: Throwable) {
                "[]"
            }
            val view = webView ?: return@launch
            view.evaluateJavascript("window.showPlaces && window.showPlaces($places)", null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialGeohash = intent.getStringExtra(EXTRA_INITIAL_GEOHASH)?.trim()?.lowercase()
        var geohashToFocus: String? = null
        var (initLat, initLon) = 0.0 to 0.0

        if (!initialGeohash.isNullOrEmpty()) {
            geohashToFocus = initialGeohash
            try {
                val (lat, lon) = Geohash.decodeToCenter(initialGeohash)
                initLat = lat
                initLon = lon
            } catch (_: Throwable) {}
        } else {
            // If no initial geohash, try to use the user's coarsest location
            val locationManager = LocationChannelManager.getInstance(applicationContext)
            val channels = locationManager.availableChannels.value
            if (!channels.isNullOrEmpty()) {
                val coarsestChannel = channels.minByOrNull { it.geohash.length }
                if (coarsestChannel != null) {
                    geohashToFocus = coarsestChannel.geohash
                    try {
                        val (lat, lon) = Geohash.decodeToCenter(coarsestChannel.geohash)
                        initLat = lat
                        initLon = lon
                    } catch (_: Throwable) {}
                }
            }
        }

        setContent {
            MaterialTheme {
                var currentGeohash by remember { mutableStateOf(geohashToFocus ?: "") }

                Scaffold { padding ->
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                settings.allowFileAccessFromFileURLs = true
                                settings.userAgentString = buildString {
                                    append("Vuzol/")
                                    append(BuildConfig.VERSION_NAME)
                                    append(" (Android ")
                                    append(Build.VERSION.SDK_INT)
                                    append(")")
                                }
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.let { this@GeohashPickerActivity.webView = it }
                                        if (!geohashToFocus.isNullOrEmpty()) {
                                            evaluateJavascript(
                                                "window.focusGeohash('${geohashToFocus}')",
                                                null
                                            )
                                        } else {
                                            evaluateJavascript(
                                                "window.setCenter(${initLat}, ${initLon})",
                                                null
                                            )
                                        }

                                        // Apply theme to map tiles
                                        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                                        val theme = if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
                                        evaluateJavascript("window.setMapTheme('" + theme + "')", null)
                                        evaluateJavascript(
                                            "window.setCountries && window.setCountries(${countriesJson()})",
                                            null
                                        )
                                    }
                                }
                                addJavascriptInterface(object {
                                    @JavascriptInterface
                                    fun onGeohashChanged(geohash: String) {
                                        runOnUiThread {
                                            currentGeohash = geohash
                                        }
                                    }

                                    @JavascriptInterface
                                    fun selectGeohash(geohash: String) {
                                        runOnUiThread {
                                            val gh = geohash.trim().ifBlank { currentGeohash }
                                            val result = Intent().apply { putExtra(EXTRA_RESULT_GEOHASH, gh) }
                                            setResult(Activity.RESULT_OK, result)
                                            finish()
                                        }
                                    }

                                    @JavascriptInterface
                                    fun searchPlaces(query: String?, countryCode: String?) {
                                        this@GeohashPickerActivity.searchPlaces(
                                            query.orEmpty(),
                                            countryCode.orEmpty()
                                        )
                                    }

                                    @JavascriptInterface
                                    fun getCountriesJson(): String = countriesJson()
                                }, "Android")

                                loadUrl("file:///android_asset/geohash_picker.html")
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        update = { webView ->
                            this@GeohashPickerActivity.webView = webView
                            // ensure it fills parent
                            webView.updateLayoutParams<ViewGroup.LayoutParams> {
                                width = ViewGroup.LayoutParams.MATCH_PARENT
                                height = ViewGroup.LayoutParams.MATCH_PARENT
                            }
                        },
                        onRelease = { webView ->
                            // Best-effort cleanup to avoid leaks and timers
                            try { webView.evaluateJavascript("window.cleanup && window.cleanup()", null) } catch (_: Throwable) {}
                            try { webView.stopLoading() } catch (_: Throwable) {}
                            try { webView.clearHistory() } catch (_: Throwable) {}
                            try { webView.clearCache(true) } catch (_: Throwable) {}
                            try { webView.loadUrl("about:blank") } catch (_: Throwable) {}
                            try { webView.removeAllViews() } catch (_: Throwable) {}
                            try { webView.destroy() } catch (_: Throwable) {}
                            this@GeohashPickerActivity.webView = null
                        }
                    )
                }
            }
        }

    }
}
