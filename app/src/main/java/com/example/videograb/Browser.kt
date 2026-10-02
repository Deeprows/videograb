package com.example.videograb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private val VIDEO_PATTERNS = listOf(
    Regex("youtube\\.com/(watch|shorts)|youtu\\.be/"),
    Regex("facebook\\.com/.*(videos|watch|reel)|fb\\.watch"),
    Regex("tiktok\\.com/.*/video/|vm\\.tiktok\\.com|tiktok\\.com/t/"),
    Regex("dailymotion\\.com/video/|dai\\.ly/"),
    Regex("(x|twitter)\\.com/.*/status/"),
)

fun looksLikeVideo(url: String) = VIDEO_PATTERNS.any { it.containsMatchIn(url) }

fun normalizeInput(raw: String): String {
    val t = raw.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(" ") || !t.contains(".") -> "https://www.google.com/search?q=" + Uri.encode(t)
        else -> "https://$t"
    }
}

class Browser(context: Context) {
    var currentUrl by mutableStateOf("")
    var isLoading by mutableStateOf(false)

    @SuppressLint("SetJavaScriptEnabled")
    val web: WebView = WebView(context).also { w ->
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                currentUrl = url
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { isLoading = true }
            override fun onPageFinished(view: WebView, url: String) { isLoading = false }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !(request.url.scheme ?: "").startsWith("http")
        }
    }
}
