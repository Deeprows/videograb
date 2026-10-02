package com.example.videograb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
    var progress by mutableIntStateOf(0)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    @SuppressLint("SetJavaScriptEnabled")
    val web: WebView = WebView(context).also { w ->
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                currentUrl = url
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { isLoading = true }
            override fun onPageFinished(view: WebView, url: String) {
                isLoading = false
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
                CookieManager.getInstance().flush()   // make sure logins are saved for downloads
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !(request.url.scheme ?: "").startsWith("http")
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
        }
    }
}
