package com.example.videograb

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

fun normalizeInput(raw: String): String {
    val t = raw.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(" ") || !t.contains(".") -> "https://www.google.com/search?q=" + Uri.encode(t)
        else -> "https://$t"
    }
}

fun hostOf(url: String): String =
    try { Uri.parse(url).host?.removePrefix("www.")?.removePrefix("m.") ?: url } catch (_: Exception) { url }

private fun friendlyNetError(desc: String?): String {
    val d = desc ?: ""
    return when {
        "ERR_INTERNET_DISCONNECTED" in d || "ERR_NAME_NOT_RESOLVED" in d || "ERR_NETWORK_CHANGED" in d ->
            "No internet connection. Check your network and try again."
        "ERR_CONNECTION_TIMED_OUT" in d || "ERR_TIMED_OUT" in d -> "The site took too long to respond."
        "ERR_TOO_MANY_REDIRECTS" in d ->
            "This site keeps redirecting. Clear cookies from the menu, or try Desktop site."
        d.isBlank() -> "The page could not be loaded."
        else -> d.removePrefix("net::")
    }
}

/** One browser tab: its own WebView plus the state the UI needs. */
class BrowserTab(
    val id: Int,
    private val owner: Browser,
    startDesktop: Boolean = false,
    val parentId: Int = -1,
) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var favicon by mutableStateOf<Bitmap?>(null)
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var blank by mutableStateOf(false)
    var desktop by mutableStateOf(startDesktop)
    var showStart by mutableStateOf(true)
    var customView by mutableStateOf<View?>(null)
    internal var customCallback: WebChromeClient.CustomViewCallback? = null
    internal var fallbackTried = false

    var web: WebView by mutableStateOf(owner.makeWebView(this))
        private set

    fun open(raw: String) {
        if (raw.isBlank()) return
        val u = normalizeInput(raw)
        showStart = false
        error = null
        blank = false
        fallbackTried = false
        url = u
        web.loadUrl(u)
    }

    fun reload() {
        error = null
        blank = false
        if (web.url.isNullOrBlank() && url.isNotBlank()) web.loadUrl(url) else web.reload()
    }

    fun setDesktop(on: Boolean) {
        desktop = on
        web.settings.userAgentString = if (on) owner.desktopUa else owner.mobileUa
        web.settings.loadWithOverviewMode = on
        reload()
    }

    fun goStart() {
        web.stopLoading()
        showStart = true
    }

    /** The page process died (low memory): swap in a fresh WebView and reload. */
    fun rebuild() {
        val old = web
        val u = url
        web = owner.makeWebView(this)
        try {
            (old.parent as? ViewGroup)?.removeView(old)
            old.destroy()
        } catch (_: Exception) { }
        error = null
        if (u.isNotBlank() && !showStart) web.loadUrl(u)
    }

    fun exitFullscreen() {
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
    }

    internal fun dispose() {
        try {
            web.stopLoading()
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        } catch (_: Exception) { }
    }
}

class Browser(private val context: Context) {
    val tabs = mutableStateListOf<BrowserTab>()
    var currentId by mutableIntStateOf(0)
    var pendingLink by mutableStateOf<String?>(null)     // link the user long-pressed

    /** Called when a page hands us a direct video/audio file. */
    var onFileDownload: ((String) -> Unit)? = null

    private var nextId = 1
    private val main = Handler(Looper.getMainLooper())

    // ---- user agents: look like current Chrome so sites serve their normal pages
    private val defaultUa: String = WebSettings.getDefaultUserAgent(context)
    private val chromeVersion: String =
        Regex("Chrome/([\\d.]+)").find(defaultUa)?.groupValues?.get(1) ?: "124.0.0.0"
    val mobileUa: String = defaultUa.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
    val desktopUa: String =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"

    val engineMajor: Int = Regex("Chrome/(\\d+)").find(defaultUa)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val engineOutdated: Boolean get() = engineMajor in 1..119
    val enginePackage: String = try {
        if (Build.VERSION.SDK_INT >= 26) WebView.getCurrentWebViewPackage()?.packageName ?: "" else ""
    } catch (_: Exception) { "" }
    val engineLabel: String = try {
        if (Build.VERSION.SDK_INT >= 26)
            WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } ?: "Chrome $chromeVersion"
        else "Chrome $chromeVersion"
    } catch (_: Exception) { "Chrome $chromeVersion" }

    val current: BrowserTab get() = tabs.firstOrNull { it.id == currentId } ?: tabs.first()

    // ------------------------------------------------------------ tabs
    fun newTab(
        url: String? = null,
        startDesktop: Boolean = false,
        parentId: Int = -1,
        select: Boolean = true,
    ): BrowserTab {
        val t = BrowserTab(nextId++, this, startDesktop, parentId)
        tabs.add(t)
        if (select) currentId = t.id
        if (url != null) t.open(url)
        return t
    }

    fun select(id: Int) { if (tabs.any { it.id == id }) currentId = id }

    fun closeTab(id: Int) {
        val i = tabs.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = tabs[i]
        if (tabs.size == 1) {
            val fresh = BrowserTab(nextId++, this)
            tabs.add(fresh)
            currentId = fresh.id
            tabs.remove(t)
        } else {
            tabs.removeAt(i)
            if (currentId == id) {
                currentId = (tabs.firstOrNull { it.id == t.parentId } ?: tabs[(i - 1).coerceAtLeast(0)]).id
            }
        }
        main.postDelayed({ t.dispose() }, 500)   // let Compose detach the view first
    }

    /** Opens [url] in the current tab if it is empty, otherwise in a new tab. */
    fun openUrl(url: String) {
        val t = current
        if (t.showStart || tabs.size >= MAX_TABS) t.open(url) else newTab(url, startDesktop = false)
    }

    /** Back button. Returns false when the browser has nothing left to go back to. */
    fun handleBack(): Boolean {
        val t = current
        return when {
            t.customView != null -> { t.exitFullscreen(); true }
            !t.showStart && t.web.canGoBack() -> { t.web.goBack(); true }
            tabs.size > 1 && t.parentId >= 0 -> { closeTab(t.id); true }
            !t.showStart -> { t.goStart(); true }
            else -> false
        }
    }

    fun clearSiteData() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
        android.webkit.WebStorage.getInstance().deleteAllData()
        tabs.forEach { it.web.clearCache(true) }
        Toast.makeText(context, "Cookies and cache cleared", Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------ WebView factory
    @SuppressLint("SetJavaScriptEnabled")
    internal fun makeWebView(tab: BrowserTab): WebView {
        val w = WebView(context)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            useWideViewPort = true
            loadWithOverviewMode = tab.desktop
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = true   // we decide per popup below
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            allowContentAccess = false
            userAgentString = if (tab.desktop) desktopUa else mobileUa
        }
        // Don't tell sites which app embeds the WebView (Google sign-in rejects it).
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(w.settings, emptySet())
            }
        } catch (_: Exception) { }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)

        w.setDownloadListener { dlUrl, _, _, mime, _ ->
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                onFileDownload?.invoke(dlUrl)
            } else {
                Toast.makeText(context, "Only video and audio downloads are supported", Toast.LENGTH_SHORT).show()
            }
        }

        // long-press a link -> menu (open in new tab / copy / download video)
        w.setOnLongClickListener {
            val r = w.hitTestResult
            val extra = r.extra
            if (extra != null && extra.startsWith("http") &&
                (r.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || r.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)
            ) {
                pendingLink = extra
                true
            } else false
        }

        w.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                if (url != "about:blank") tab.url = url
                tab.canGoBack = view.canGoBack()
                tab.canGoForward = view.canGoForward()
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                tab.loading = true
                tab.blank = false
                if (url != "about:blank") tab.error = null
            }
            override fun onPageFinished(view: WebView, url: String) {
                tab.loading = false
                tab.canGoBack = view.canGoBack()
                tab.canGoForward = view.canGoForward()
                CookieManager.getInstance().flush()   // keep logins for downloads
                scheduleBlankCheck(tab, view, tab.url)
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                return when (u.scheme ?: "") {
                    "http", "https", "about", "data", "blob", "javascript" -> false
                    "intent" -> {
                        try {
                            val i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                            val fb = i.getStringExtra("browser_fallback_url")
                            if (fb != null && fb.startsWith("http")) view.loadUrl(fb)
                        } catch (_: Exception) { }
                        true
                    }
                    "mailto", "tel", "sms" -> {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) { }
                        true
                    }
                    else -> true   // app deep links (fb://, twitter://, market://) are ignored so pages keep working
                }
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                val d = error.description?.toString()
                if (d != null && ("ERR_ABORTED" in d || "ERR_UNKNOWN_URL_SCHEME" in d)) return
                tab.error = friendlyNetError(d)
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                tab.error = "This site's secure connection could not be verified."
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                main.post { tab.rebuild() }
                return true
            }
        }

        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { tab.progress = newProgress }
            override fun onReceivedTitle(view: WebView, title: String?) { tab.title = title ?: "" }
            override fun onReceivedIcon(view: WebView, icon: Bitmap?) { tab.favicon = icon }

            // Real child windows: sign-in popups (Google/Apple on X, Facebook login...) need window.opener
            // to keep working, so they open as their own tab and close themselves when done.
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (!isUserGesture || tabs.size >= MAX_TABS) return false   // block popups nobody tapped
                val t = newTab(startDesktop = tab.desktop, parentId = tab.id, select = true)
                t.showStart = false
                (resultMsg.obj as WebView.WebViewTransport).webView = t.web
                resultMsg.sendToTarget()
                return true
            }
            override fun onCloseWindow(window: WebView) {
                tabs.firstOrNull { it.web === window }?.let { closeTab(it.id) }
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                tab.customCallback?.onCustomViewHidden()
                tab.customView = view
                tab.customCallback = callback
            }
            override fun onHideCustomView() {
                tab.customView = null
                tab.customCallback?.onCustomViewHidden()
                tab.customCallback = null
            }
        }
        return w
    }

    /**
     * Some sites render nothing in a WebView. If a finished page is empty, retry once as a desktop
     * site automatically; if it is still empty, tell the user.
     */
    private fun scheduleBlankCheck(tab: BrowserTab, w: WebView, forUrl: String) {
        w.postDelayed({
            if (tab.web !== w || tab.showStart || tab.error != null || tab.loading || tab.url != forUrl) return@postDelayed
            w.evaluateJavascript(
                "(function(){var b=document.body;return (b?b.innerText.trim().length:0)+','+" +
                    "document.querySelectorAll('img,video,canvas,svg,iframe').length;})()"
            ) { r ->
                val n = Regex("\\d+").findAll(r ?: "").map { it.value.toIntOrNull() ?: 0 }.toList()
                if (n.size >= 2 && n[0] < 15 && n[1] == 0 && tab.web === w && tab.url == forUrl && !tab.loading) {
                    if (!tab.fallbackTried && !tab.desktop) {
                        tab.fallbackTried = true
                        tab.setDesktop(true)
                    } else {
                        tab.blank = true
                    }
                }
            }
        }, 2500)
    }

    init {
        newTab()
        // pause page media/JS when the app goes to the background
        (context as? ComponentActivity)?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) { tabs.forEach { it.web.onPause() } }
            override fun onResume(owner: LifecycleOwner) { tabs.forEach { it.web.onResume() } }
        })
    }

    companion object { const val MAX_TABS = 12 }
}
