package com.example.videograb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    search: SearchViewModel,
    clipLink: String?,
    engineState: EngineState,
    activeDownloads: Int,
    onDismissClip: () -> Unit,
    onOpenSite: (String) -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenDownloads: () -> Unit,
    onLink: (url: String, title: String, thumb: String) -> Unit,
    onSettings: () -> Unit,
) {
    var q by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val link = extractUrl(q)

    // Fill the feed as soon as the home page opens (waits for the engine by itself).
    LaunchedEffect(Unit) { if (search.heading.isBlank()) search.loadCategory(0) }

    fun go() {
        focus.clearFocus()
        val u = extractUrl(q)
        if (u != null) {
            onLink(u, "", "")
            q = ""
        } else {
            search.search(q)
        }
    }

    val sites = Platform.values().filter { it != Platform.OTHER }
    val tileCount = sites.size + 3   // sites + Browser + Downloads + Paste

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- top bar
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(BrandBrush),
                    contentAlignment = Alignment.Center
                ) { Icon(DownloadIcon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(10.dp))
                Text(
                    "VideoGrab",
                    style = MaterialTheme.typography.titleLarge.copy(brush = BrandBrush),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenDownloads) {
                    if (activeDownloads > 0) {
                        BadgedBox(badge = { Badge { Text("$activeDownloads") } }) {
                            Icon(DownloadIcon, contentDescription = "Downloads")
                        }
                    } else {
                        Icon(DownloadIcon, contentDescription = "Downloads")
                    }
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
        }

        // ---- search / paste bar
        item {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                TextField(
                    value = q,
                    onValueChange = { q = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    placeholder = { Text("Search or paste video link") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    trailingIcon = {
                        if (q.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                                Box(
                                    Modifier.padding(end = 6.dp).size(38.dp).clip(CircleShape).background(BrandBrush)
                                        .clickable { go() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (link != null) DownloadIcon else Icons.Rounded.Search,
                                        if (link != null) "Get video" else "Search",
                                        tint = Color.White, modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else {
                            TextButton(onClick = {
                                val t = clipboard.getText()?.text
                                if (!t.isNullOrBlank()) {
                                    q = t
                                    if (extractUrl(t) != null) go()
                                }
                            }) { Text("Paste") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() })
                )
            }
        }

        // ---- engine status
        when (engineState) {
            is EngineState.Starting -> item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Preparing the download engine\u2026 the first launch takes a moment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
                }
            }
            is EngineState.Failed -> item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        "The download engine could not start: ${engineState.message}",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            else -> {}
        }

        // ---- copied link banner
        if (clipLink != null) {
            item {
                val p = platformOf(clipLink)
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlatformBadge(p, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${p.label} link copied",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                clipLink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        TextButton(onClick = { onLink(clipLink, "", ""); onDismissClip() }) { Text("Get") }
                        IconButton(onClick = onDismissClip) {
                            Icon(Icons.Rounded.Close, "Dismiss", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }

        // ---- shortcut grid (sites + browser + downloads + paste)
        item {
            SiteGrid(columns = 4, count = tileCount) { i ->
                when {
                    i < sites.size -> {
                        val p = sites[i]
                        SiteTile(p.label, { onOpenSite(p.home) }) { PlatformBadge(p, 52.dp) }
                    }
                    i == sites.size -> SiteTile("Browser", onOpenBrowser) { ActionBadge(GlobeIcon) }
                    i == sites.size + 1 -> SiteTile("Downloads", onOpenDownloads) {
                        if (activeDownloads > 0) {
                            BadgedBox(badge = { Badge { Text("$activeDownloads") } }) { ActionBadge(DownloadIcon) }
                        } else {
                            ActionBadge(DownloadIcon)
                        }
                    }
                    else -> SiteTile("Paste link", {
                        val t = clipboard.getText()?.text
                        val u = t?.let { extractUrl(it) }
                        if (u != null) onLink(u, "", "") else if (!t.isNullOrBlank()) q = t
                    }) { ActionBadge(Icons.Rounded.Add) }
                }
            }
        }

        // ---- category chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FeedCategories.size) { i ->
                    val selected = search.category == i
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (selected) BrandBrush else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant))
                            .clickable { q = ""; search.loadCategory(i) }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            FeedCategories[i].first,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // ---- feed heading
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    search.heading.ifBlank { "Trending" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { search.refresh() }, enabled = !search.loading) {
                    Icon(Icons.Rounded.Refresh, "Refresh")
                }
            }
        }

        // ---- feed
        if (search.loading && search.results.isEmpty()) {
            items(2) { FeedSkeleton() }
        } else {
            search.error?.let { e ->
                item { Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
            items(search.results) { r ->
                VideoCard(r) { onLink(r.url, r.title, r.thumb) }
            }
        }
        if (search.loading && search.results.isNotEmpty()) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50))) }
        }

        item {
            Text(
                "Only download content you own or have permission to save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VideoCard(r: SearchResult, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box {
            Thumb(r.thumb, Platform.YOUTUBE, Modifier.fillMaxWidth().aspectRatio(16f / 9f), 18.dp)
            if (r.duration.isNotBlank()) {
                Tag(r.duration, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                if (r.channel.isNotBlank()) {
                    Text(
                        r.channel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(BrandBrush),
                contentAlignment = Alignment.Center
            ) { Icon(DownloadIcon, "Download", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
    }
}

@Composable
private fun FeedSkeleton() {
    val c = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(c))
        Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(50)).background(c))
        Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(50)).background(c))
    }
}
