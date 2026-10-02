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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
    onDismissClip: () -> Unit,
    onOpenSite: (String) -> Unit,
    onLink: (url: String, title: String, thumb: String) -> Unit,
    onSettings: () -> Unit,
) {
    var q by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val link = extractUrl(q)

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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- top bar
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BrandBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(DownloadIcon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "VideoGrab",
                    style = MaterialTheme.typography.titleLarge.copy(brush = BrandBrush),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
        }

        // ---- hero
        item {
            Column {
                Text("Save any video,", style = MaterialTheme.typography.headlineLarge)
                Text("in one tap.", style = MaterialTheme.typography.headlineLarge.copy(brush = BrandBrush))
                Spacer(Modifier.size(6.dp))
                Text(
                    "Paste a link from YouTube, TikTok, Facebook, X or Dailymotion.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

        // ---- input card
        item {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = q,
                        onValueChange = { q = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        placeholder = { Text("Paste a link or search YouTube") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            if (q.isNotEmpty()) {
                                IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, "Clear") }
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
                    GradientButton(
                        text = if (link != null) "Get video" else "Search YouTube",
                        onClick = { go() },
                        icon = if (link != null) DownloadIcon else Icons.Rounded.Search
                    )
                }
            }
        }

        // ---- supported sites
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Open a site and download from the browser", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Platform.values().filter { it != Platform.OTHER }) { p ->
                        Row(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onOpenSite(p.home) }
                                .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlatformBadge(p, 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(p.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // ---- search results
        if (search.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50))) }
        search.error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
        if (search.results.isNotEmpty()) {
            item { Text("YouTube results", style = MaterialTheme.typography.titleMedium) }
            items(search.results) { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { onLink(r.url, r.title, r.thumb) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box {
                        Thumb(r.thumb, Platform.YOUTUBE, Modifier.width(140.dp).aspectRatio(16f / 9f), 12.dp)
                        if (r.duration.isNotBlank()) {
                            Tag(r.duration, Modifier.align(Alignment.BottomEnd).padding(6.dp))
                        }
                    }
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
                }
            }
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
