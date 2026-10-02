package com.example.videograb

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

enum class Dest(val label: String) {
    HOME("Home"), BROWSER("Browser"), DOWNLOADS("Downloads")
}

private fun Dest.icon(): ImageVector = when (this) {
    Dest.HOME -> Icons.Rounded.Home
    Dest.BROWSER -> GlobeIcon
    Dest.DOWNLOADS -> DownloadIcon
}

class MainActivity : ComponentActivity() {
    private val sharedLink = mutableStateOf<String?>(null)
    private val clipLink = mutableStateOf<String?>(null)
    private var lastClip: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
        setContent { VideoGrabTheme { AppRoot(sharedLink, clipLink) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    // Android only lets the focused app read the clipboard, so check when we gain focus.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        try {
            val cm = getSystemService(ClipboardManager::class.java)
            val clip = cm?.primaryClip
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString() else null
            val url = text?.let { extractUrl(it) } ?: return
            if (url != lastClip && platformOf(url) != Platform.OTHER && looksLikeVideo(url)) {
                lastClip = url
                clipLink.value = url
            }
        } catch (_: Exception) { }
    }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            extractUrl(text)?.let { sharedLink.value = it }
        }
    }
}

@Composable
fun AppRoot(sharedLink: MutableState<String?>, clipLink: MutableState<String?>) {
    val ctx = LocalContext.current
    val search: SearchViewModel = viewModel()
    val link: LinkViewModel = viewModel()
    val items by DownloadManager.items.collectAsState()
    val engine by Engine.state.collectAsState()
    val browser = remember { Browser(ctx) }
    LaunchedEffect(browser) { browser.onFileDownload = { url -> link.open(url) } }

    var tab by remember { mutableStateOf(Dest.HOME) }
    var playing by remember { mutableStateOf<DlItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var browserTarget by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(sharedLink.value) {
        sharedLink.value?.let {
            link.open(it)
            sharedLink.value = null
        }
    }
    BackHandler(enabled = tab != Dest.HOME && playing == null && !showSettings) { tab = Dest.HOME }

    val current = playing
    if (current != null) {
        PlayerScreen(current) { playing = null }
    } else if (showSettings) {
        SettingsScreen { showSettings = false }
    } else {
        val activeCount = items.count { it.state == DlState.RUNNING || it.state == DlState.QUEUED }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Dest.values().forEach { d ->
                        NavigationBarItem(
                            selected = tab == d,
                            onClick = { tab = d },
                            icon = {
                                if (d == Dest.DOWNLOADS && activeCount > 0) {
                                    BadgedBox(badge = { Badge { Text("$activeCount") } }) {
                                        Icon(d.icon(), contentDescription = d.label)
                                    }
                                } else {
                                    Icon(d.icon(), contentDescription = d.label)
                                }
                            },
                            label = { Text(d.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    Dest.HOME -> HomeScreen(
                        search = search,
                        clipLink = clipLink.value,
                        engineState = engine,
                        activeDownloads = activeCount,
                        onDismissClip = { clipLink.value = null },
                        onOpenSite = { browserTarget = it; tab = Dest.BROWSER },
                        onOpenBrowser = { tab = Dest.BROWSER },
                        onOpenDownloads = { tab = Dest.DOWNLOADS },
                        onLink = { url, title, thumb -> link.open(url, title, thumb) },
                        onSettings = { showSettings = true }
                    )
                    Dest.BROWSER -> BrowserScreen(
                        b = browser,
                        target = browserTarget,
                        onTargetConsumed = { browserTarget = null },
                        onExit = { tab = Dest.HOME },
                        onDownload = { url, title -> link.open(url, title) }
                    )
                    Dest.DOWNLOADS -> DownloadsScreen(items) { playing = it }
                }
            }
        }
    }

    LinkSheet(
        state = link.state,
        onDismiss = { link.dismiss() },
        onPick = { info, option ->
            DownloadManager.enqueue(info.url, info.title, option.quality, info.thumb)
            link.dismiss()
            clipLink.value = null
            Toast.makeText(ctx, "Added to downloads", Toast.LENGTH_SHORT).show()
            tab = Dest.DOWNLOADS
        },
        onRetry = { url, title -> link.open(url, title) },
        onAnyway = { url, title ->
            DownloadManager.enqueue(url, title, 0)
            link.dismiss()
            tab = Dest.DOWNLOADS
        }
    )
}
