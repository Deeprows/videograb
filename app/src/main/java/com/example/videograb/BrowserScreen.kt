package com.example.videograb

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    b: Browser,
    target: String?,
    onTargetConsumed: () -> Unit,
    onExit: () -> Unit,
    onDownload: (url: String, title: String) -> Unit,
) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    val tab = b.current

    var menuOpen by remember { mutableStateOf(false) }
    var tabsOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        if (target != null) {
            b.openUrl(target)
            onTargetConsumed()
        }
    }
    BackHandler { if (!b.handleBack()) onExit() }

    // ---- address bar text: host when idle, full URL (selected) when editing
    val shownUrl = if (tab.showStart) "" else tab.url
    var focused by remember(tab.id) { mutableStateOf(false) }
    var tfv by remember(tab.id) { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(tab.id, shownUrl, focused) {
        if (!focused) tfv = TextFieldValue(if (shownUrl.isEmpty()) "" else hostOf(shownUrl))
    }
    fun submit() {
        val t = tfv.text
        focus.clearFocus()
        if (t.isNotBlank()) b.current.open(t)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ================= toolbar =================
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (!b.handleBack()) onExit() },
                    modifier = Modifier.size(40.dp)
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }

                Row(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 14.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val secure = shownUrl.startsWith("https://")
                    Icon(
                        when {
                            shownUrl.isEmpty() -> Icons.Rounded.Search
                            secure -> Icons.Rounded.Lock
                            else -> Icons.Rounded.Warning
                        },
                        contentDescription = null,
                        tint = if (shownUrl.isNotEmpty() && !secure) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (tfv.text.isEmpty()) {
                            Text(
                                "Search or type URL", maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = tfv,
                            onValueChange = { tfv = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { submit() }),
                            modifier = Modifier.fillMaxWidth().onFocusChanged { st ->
                                if (st.isFocused && !focused) {
                                    focused = true
                                    tfv = TextFieldValue(shownUrl, TextRange(0, shownUrl.length))
                                } else if (!st.isFocused && focused) {
                                    focused = false
                                }
                            }
                        )
                    }
                    if (!tab.showStart) {
                        IconButton(
                            onClick = { if (tab.loading) tab.web.stopLoading() else tab.reload() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                if (tab.loading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                                if (tab.loading) "Stop" else "Reload",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // tab counter
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { tabsOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(24.dp).border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${b.tabs.size}", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // overflow menu
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.MoreVert, "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("New tab") },
                            leadingIcon = { Icon(Icons.Rounded.Add, null) },
                            onClick = { menuOpen = false; b.newTab() }
                        )
                        DropdownMenuItem(
                            text = { Text("Forward") },
                            enabled = tab.canGoForward && !tab.showStart,
                            onClick = { menuOpen = false; tab.web.goForward() }
                        )
                        DropdownMenuItem(
                            text = { Text("Reload") },
                            enabled = !tab.showStart,
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = { menuOpen = false; tab.reload() }
                        )
                        DropdownMenuItem(
                            text = { Text("Start page") },
                            enabled = !tab.showStart,
                            onClick = { menuOpen = false; tab.goStart() }
                        )
                        DropdownMenuItem(
                            text = { Text("Desktop site") },
                            leadingIcon = { if (tab.desktop) Icon(Icons.Rounded.Check, null) },
                            onClick = { menuOpen = false; tab.setDesktop(!tab.desktop) }
                        )
                        DropdownMenuItem(
                            text = { Text("Share link") },
                            enabled = !tab.showStart,
                            onClick = {
                                menuOpen = false
                                val i = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, tab.url)
                                }
                                ctx.startActivity(Intent.createChooser(i, "Share link"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy link") },
                            enabled = !tab.showStart,
                            onClick = { menuOpen = false; clipboard.setText(AnnotatedString(tab.url)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Open in other browser") },
                            enabled = !tab.showStart,
                            onClick = { menuOpen = false; openExternally(ctx, tab.url) }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear cookies & cache") },
                            onClick = { menuOpen = false; confirmClear = true }
                        )
                    }
                }
            }

            // ================= progress =================
            if (!tab.showStart && tab.loading && tab.progress in 1..99) {
                LinearProgressIndicator(progress = { tab.progress / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }

            // ================= page =================
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab.showStart) {
                    BrowserStart(b) { b.current.open(it) }
                } else {
                    key(tab.id, tab.web) {
                        AndroidView(
                            factory = {
                                (tab.web.parent as? ViewGroup)?.removeView(tab.web)
                                tab.web
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    val err = tab.error
                    if (err != null) {
                        Column(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                        ) {
                            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
                            Text("Can\u2019t open this page", style = MaterialTheme.typography.titleLarge)
                            Text(
                                err, textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (b.engineOutdated) {
                                Text(
                                    "Your browser engine (WebView ${b.engineMajor}) is outdated. Updating it fixes many sites.",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.size(4.dp))
                            GradientButton("Try again", { tab.reload() })
                            OutlinedButton(
                                onClick = { openExternally(ctx, tab.url) },
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) { Text("Open in other browser") }
                        }
                    } else if (tab.blank) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shadowElevation = 6.dp
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "This page looks empty",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    if (b.engineOutdated) "Your browser engine (WebView ${b.engineMajor}) is outdated, which breaks sites like Facebook and X. Update it, or try another browser."
                                    else "The site may not support in-app browsers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Row {
                                    TextButton(onClick = { tab.reload() }) { Text("Reload") }
                                    TextButton(onClick = { openExternally(ctx, tab.url) }) { Text("Other browser") }
                                    if (b.engineOutdated) {
                                        TextButton(onClick = { openWebViewUpdate(ctx, b.enginePackage) }) { Text("Update") }
                                    }
                                }
                            }
                        }
                    }
                    if (err == null && looksLikeVideo(tab.url)) {
                        val shape = RoundedCornerShape(50)
                        Row(
                            Modifier.align(Alignment.BottomEnd).padding(18.dp)
                                .shadow(10.dp, shape)
                                .clip(shape)
                                .background(BrandBrush)
                                .clickable { onDownload(tab.url, tab.title) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(DownloadIcon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Download", color = Color.White, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }

        // ---- fullscreen video overlay
        tab.customView?.let { v ->
            AndroidView(
                factory = {
                    (v.parent as? ViewGroup)?.removeView(v)
                    v
                },
                modifier = Modifier.fillMaxSize().background(Color.Black)
            )
        }
    }

    // ================= tab switcher =================
    if (tabsOpen) {
        ModalBottomSheet(
            onDismissRequest = { tabsOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${b.tabs.size} ${if (b.tabs.size == 1) "tab" else "tabs"}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { b.newTab(); tabsOpen = false }) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New tab")
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(b.tabs.toList(), key = { it.id }) { t ->
                        val selected = t.id == b.currentId
                        val shape = RoundedCornerShape(16.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(shape)
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape
                                )
                                .clickable { b.select(t.id); tabsOpen = false }
                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val fav = t.favicon
                            val p = platformOf(t.url)
                            if (fav != null && !t.showStart) {
                                Image(fav.asImageBitmap(), null, Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)))
                            } else if (p != Platform.OTHER && !t.showStart) {
                                PlatformBadge(p, 28.dp)
                            } else {
                                ActionBadge(GlobeIcon, 28.dp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (t.showStart) "New tab" else t.title.ifBlank { hostOf(t.url) },
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    if (t.showStart) "Start page" else hostOf(t.url),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { b.closeTab(t.id) }) { Icon(Icons.Rounded.Close, "Close tab") }
                        }
                    }
                }
            }
        }
    }

    // ================= long-press link menu =================
    val link = b.pendingLink
    if (link != null) {
        AlertDialog(
            onDismissRequest = { b.pendingLink = null },
            title = { Text(hostOf(link), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    if (looksLikeVideo(link)) {
                        LinkAction("Download video") { b.pendingLink = null; onDownload(link, "") }
                    }
                    LinkAction("Open") { b.pendingLink = null; b.current.open(link) }
                    LinkAction("Open in new tab") { b.pendingLink = null; b.newTab(link) }
                    LinkAction("Copy link") { b.pendingLink = null; clipboard.setText(AnnotatedString(link)) }
                }
            },
            confirmButton = { TextButton(onClick = { b.pendingLink = null }) { Text("Cancel") } }
        )
    }

    // ================= clear data confirm =================
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear cookies & cache?") },
            text = {
                Text("This signs you out of every site in the browser, including logins used for private downloads. It can fix sites that won\u2019t load.")
            },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; b.clearSiteData(); b.current.let { if (!it.showStart) it.reload() } }) {
                    Text("Clear")
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LinkAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    )
}

@Composable
private fun BrowserStart(b: Browser, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val sites = Platform.values().filter { it != Platform.OTHER }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(BrandBrush),
            contentAlignment = Alignment.Center
        ) { Icon(GlobeIcon, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
        Text("Browse & grab", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Open a site, play a video, then tap Download. Log in to use private or age-restricted videos.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (b.engineOutdated) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Your browser engine is out of date",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "Android System WebView ${b.engineMajor} is too old for Facebook, X and Google sign-in, so they may show blank or broken pages. Update it and they will work.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = { openWebViewUpdate(ctx, b.enginePackage) }) { Text("Update WebView") }
                }
            }
        }

        SiteGrid(columns = 3, count = sites.size + 1) { i ->
            if (i < sites.size) {
                val p = sites[i]
                SiteTile(p.label, { onOpen(p.home) }) { PlatformBadge(p, 56.dp) }
            } else {
                SiteTile("Google", { onOpen("https://www.google.com") }) { ActionBadge(Icons.Rounded.Search, 56.dp) }
            }
        }

        Text(
            "Engine: ${b.engineLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openExternally(ctx: Context, url: String) {
    if (url.isBlank()) return
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
}

private fun openWebViewUpdate(ctx: Context, pkg: String) {
    val id = pkg.ifBlank { "com.google.android.webview" }
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
    } catch (_: Exception) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")))
        } catch (_: Exception) { }
    }
}
