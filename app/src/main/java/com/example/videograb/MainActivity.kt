package com.example.videograb

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

enum class Dest(val label: String, val icon: String) {
    HOME("Home", "🏠"), BROWSER("Browser", "🌐"), DOWNLOADS("Downloads", "⬇️")
}

class MainActivity : ComponentActivity() {
    private val sharedLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
        setContent { MaterialTheme { AppRoot(sharedLink) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            Regex("https?://\\S+").find(text)?.value?.let { sharedLink.value = it }
        }
    }
}

@Composable
fun AppRoot(sharedLink: MutableState<String?>) {
    val ctx = LocalContext.current
    val search: SearchViewModel = viewModel()
    val items by DownloadManager.items.collectAsState()
    val browser = remember { Browser(ctx) }

    var tab by remember { mutableStateOf(Dest.HOME) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var playing by remember { mutableStateOf<DlItem?>(null) }
    var browserTarget by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(sharedLink.value) {
        sharedLink.value?.let { pending = Pending(it, ""); sharedLink.value = null }
    }
    BackHandler(enabled = tab != Dest.HOME && playing == null) { tab = Dest.HOME }

    val current = playing
    if (current != null) {
        PlayerScreen(current) { playing = null }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Dest.values().forEach { d ->
                        NavigationBarItem(
                            selected = tab == d,
                            onClick = { tab = d },
                            icon = { Text(d.icon) },
                            label = { Text(d.label) }
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    Dest.HOME -> HomeScreen(
                        search = search,
                        onOpenSite = { browserTarget = it; tab = Dest.BROWSER },
                        onDownload = { url, title -> pending = Pending(url, title) }
                    )
                    Dest.BROWSER -> BrowserScreen(
                        b = browser,
                        target = browserTarget,
                        onTargetConsumed = { browserTarget = null },
                        onExit = { tab = Dest.HOME },
                        onDownload = { url, title -> pending = Pending(url, title) }
                    )
                    Dest.DOWNLOADS -> DownloadsScreen(items) { playing = it }
                }
            }
        }
    }

    pending?.let { p ->
        QualityDialog(p, onDismiss = { pending = null }) { q ->
            DownloadManager.enqueue(p.url, p.title, q)
            pending = null
            tab = Dest.DOWNLOADS
        }
    }
}
