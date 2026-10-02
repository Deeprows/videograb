package com.example.videograb

import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Site(val name: String, val url: String, val color: Color)
data class Pending(val url: String, val title: String)

private val SITES = listOf(
    Site("YouTube", "https://m.youtube.com", Color(0xFFE53935)),
    Site("Facebook", "https://m.facebook.com", Color(0xFF1877F2)),
    Site("TikTok", "https://www.tiktok.com", Color(0xFF212121)),
    Site("Dailymotion", "https://www.dailymotion.com", Color(0xFF0066DC)),
    Site("X", "https://x.com", Color(0xFF000000)),
)

// ------------------------------------------------------------ Home
@Composable
fun HomeScreen(
    search: SearchViewModel,
    onOpenSite: (String) -> Unit,
    onDownload: (String, String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }

    val go = {
        val t = q.trim()
        if (t.startsWith("http")) onDownload(t, "") else search.search(t)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("VideoGrab", style = MaterialTheme.typography.headlineMedium) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    label = { Text("Search or paste a video link") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() })
                )
                Button(onClick = { go() }) { Text("Go") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SITES.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { s -> SiteTile(s, Modifier.weight(1f)) { onOpenSite(s.url) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (search.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        search.error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
        items(search.results) { r ->
            Row(
                Modifier.fillMaxWidth().clickable { onDownload(r.url, r.title) },
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AsyncImage(
                    model = r.thumb, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(128.dp).height(72.dp).clip(RoundedCornerShape(8.dp))
                )
                Column(Modifier.weight(1f)) {
                    Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(r.channel, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            TextButton(
                enabled = !updating,
                onClick = {
                    scope.launch {
                        updating = true
                        val msg = try {
                            val r = withContext(Dispatchers.IO) {
                                YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE)
                            }
                            "Engine update: $r"
                        } catch (e: Exception) {
                            "Update failed: ${e.message}"
                        }
                        updating = false
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    }
                }
            ) { Text(if (updating) "Updating…" else "Update download engine") }
        }
        item {
            Text(
                "Only download content you own or have permission to save.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SiteTile(s: Site, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(s.color).clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(s.name.take(1), color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text(s.name, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

// ------------------------------------------------------------ Browser
@Composable
fun BrowserScreen(
    b: Browser,
    target: String?,
    onTargetConsumed: () -> Unit,
    onExit: () -> Unit,
    onDownload: (String, String) -> Unit,
) {
    var input by remember(b.currentUrl) { mutableStateOf(b.currentUrl) }

    LaunchedEffect(target) {
        if (target != null) {
            b.web.loadUrl(target)
            onTargetConsumed()
        }
    }
    BackHandler { if (b.web.canGoBack()) b.web.goBack() else onExit() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text("Search or enter address") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (input.isNotBlank()) b.web.loadUrl(normalizeInput(input)) })
            )
            Button(onClick = { if (input.isNotBlank()) b.web.loadUrl(normalizeInput(input)) }) { Text("Go") }
        }
        if (b.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = {
                    (b.web.parent as? ViewGroup)?.removeView(b.web)
                    b.web
                },
                modifier = Modifier.fillMaxSize()
            )
            if (looksLikeVideo(b.currentUrl)) {
                ExtendedFloatingActionButton(
                    onClick = { onDownload(b.currentUrl, b.web.title ?: "") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) { Text("⬇ Download") }
            }
        }
    }
}

// ------------------------------------------------------------ Downloads
@Composable
fun DownloadsScreen(all: List<DlItem>, onPlay: (DlItem) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val active = all.filter { it.state != DlState.DONE }
    val done = all.filter { it.state == DlState.DONE }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Downloading (${active.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Downloaded (${done.size})") })
        }
        val list = if (tab == 0) active else done
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (tab == 0) "No active downloads" else "Nothing downloaded yet")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.id }) { item ->
                    if (item.state == DlState.DONE) DoneRow(item, onPlay) else ActiveRow(item)
                }
            }
        }
    }
}

@Composable
private fun ActiveRow(it: DlItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(it.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxWidth())
            val pct = (it.progress * 100).toInt()
            Text(
                when (it.state) {
                    DlState.RUNNING -> "$pct%" + if (it.eta >= 0) "  •  ${it.eta}s left" else ""
                    DlState.QUEUED -> "Waiting…"
                    DlState.PAUSED -> "Paused at $pct%"
                    DlState.FAILED -> "Failed: ${it.error ?: "unknown error"}"
                    DlState.DONE -> ""
                },
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (it.state) {
                    DlState.RUNNING, DlState.QUEUED ->
                        OutlinedButton(onClick = { DownloadManager.pause(it.id) }) { Text("Pause") }
                    DlState.PAUSED ->
                        OutlinedButton(onClick = { DownloadManager.resume(it.id) }) { Text("Resume") }
                    DlState.FAILED ->
                        OutlinedButton(onClick = { DownloadManager.resume(it.id) }) { Text("Retry") }
                    else -> {}
                }
                TextButton(onClick = { DownloadManager.remove(it.id, false) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun DoneRow(it: DlItem, onPlay: (DlItem) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onPlay(it) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(it.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { onPlay(it) }) { Text("Play") }
            TextButton(onClick = { DownloadManager.remove(it.id, true) }) { Text("Delete") }
        }
    }
}

// ------------------------------------------------------------ Player
@Composable
fun PlayerScreen(item: DlItem, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val exo = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(item.uri)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { exo.release() } }
    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("← Back", color = Color.White) }
            Text(item.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        AndroidView(
            factory = { c -> PlayerView(c).apply { player = exo } },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

// ------------------------------------------------------------ Quality dialog
@Composable
fun QualityDialog(p: Pending, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download") },
        text = {
            Column {
                Text(p.title.ifBlank { p.url }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                listOf("Best available" to 0, "1080p" to 1080, "720p" to 720, "480p" to 480, "360p" to 360, "MP3 audio" to -1)
                    .forEach { (label, q) ->
                        TextButton(onClick = { onPick(q) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
