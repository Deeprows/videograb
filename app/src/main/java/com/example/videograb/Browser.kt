package com.example.videograb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

fun normalizeInput(raw: String): String {
    val t = raw.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(" ") || !t.contains(".") -> "https://www.google.com/search?q=" + Uri.encode(t)
        else -> "https://$t"
    }
}

class Browser(private val context: Context) {
    var currentUrl by mutableStateOf("")          // "" = show the browser start page
    var pageTitle by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var progress by mutableIntStateOf(0)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var customView by mutableStateOf<View?>(null)  // fullscreen video

    /** Called when the page tries to download a media file directly. */
    var onFileDownload: ((String) -> Unit)? = null

    val showStart: Boolean get() = currentUrl.isBlank()

    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var clearHistoryPending = false
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    val web: WebView = WebView(context).also { w ->
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            // Look like Chrome, not an embedded WebView. Many sites (Google, YouTube, Facebook,
            // TikTok, X) show broken pages or block sign-in when they see "; wv" / "Version/4.0".
            userAgentString = userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)

        w.setDownloadListener { url, _, _, mime, _ ->
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                onFileDownload?.invoke(url)
            } else {
                Toast.makeText(context, "Only video and audio downloads are supported", Toast.LENGTH_SHORT).show()
            }
        }

        w.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                currentUrl = if (url == "about:blank") "" else url
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                isLoading = true
                if (url != "about:blank") error = null
            }
            override fun onPageFinished(view: WebView, url: String) {
                isLoading = false
                if (url == "about:blank" && clearHistoryPending) {
                    clearHistoryPending = false
                    view.clearHistory()
                }
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
                CookieManager.getInstance().flush()   // make sure logins are saved for downloads
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: ""
                // http(s) and a few internal schemes load inside the app; app deep links
                // (intent://, market://, fb://, tiktok://...) are ignored so pages don't break.
                return !(scheme.startsWith("http") || scheme == "about" || scheme == "data" || scheme == "blob")
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    this@Browser.error = error.description?.toString()?.takeIf { it.isNotBlank() }
                        ?: "The page could not be loaded."
                }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                this@Browser.error = "This site's secure connection could not be verified."
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                this@Browser.error = "The page ran out of memory. Tap Try again."
                return true
            }
        }

        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
            override fun onReceivedTitle(view: WebView, title: String?) { pageTitle = title ?: "" }

            // target="_blank" / window.open -> open in this same tab
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val temp = WebView(view.context)
                temp.webViewClient = object : WebViewClient() {
                    private var done = false
                    private fun forward(u: String?) {
                        if (done || u.isNullOrBlank() || u == "about:blank") return
                        done = true
                        main.post {
                            if (u.startsWith("http")) view.loadUrl(u)
                            temp.stopLoading()
                            temp.destroy()
                        }
                    }
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        forward(r.url.toString()); return true
                    }
                    override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) { forward(url) }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = temp
                resultMsg.sendToTarget()
                return true
            }

            // fullscreen video (YouTube, Dailymotion, ...)
            override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                customViewCallback?.onCustomViewHidden()
                customView = view
                customViewCallback = callback
            }
            override fun onHideCustomView() {
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }
        }
    }

    init {
        // pause page media when the app goes to the background
        (context as? ComponentActivity)?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) { web.onPause() }
            override fun onResume(owner: LifecycleOwner) { web.onResume() }
        })
    }

    fun open(raw: String) {
        if (raw.isBlank()) return
        val u = normalizeInput(raw)
        error = null
        currentUrl = u            // switches away from the start page immediately
        web.loadUrl(u)
    }

    fun goStart() {
        error = null
        currentUrl = ""
        pageTitle = ""
        web.stopLoading()
        clearHistoryPending = true
        web.loadUrl("about:blank")
    }

    fun exitFullscreen() {
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }
}
