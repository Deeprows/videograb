package com.example.videograb

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun BrowserScreen(
    b: Browser,
    target: String?,
    onTargetConsumed: () -> Unit,
    onExit: () -> Unit,
    onDownload: (url: String, title: String) -> Unit,
) {
    var input by remember(b.currentUrl) { mutableStateOf(b.currentUrl) }

    LaunchedEffect(target) {
        if (target != null) {
            b.open(target)
            onTargetConsumed()
        }
    }
    BackHandler {
        when {
            b.customView != null -> b.exitFullscreen()
            !b.showStart && b.web.canGoBack() -> b.web.goBack()
            !b.showStart -> b.goStart()
            else -> onExit()
        }
    }

    fun load() { if (input.isNotBlank()) b.open(input) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = { b.web.goBack() }, enabled = b.canGoBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
                IconButton(onClick = { b.web.goForward() }, enabled = b.canGoForward, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Forward")
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = { Text("Search or type URL") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { load() })
                )
                IconButton(
                    onClick = { if (b.isLoading) b.web.stopLoading() else if (b.showStart) Unit else b.web.reload() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(if (b.isLoading && !b.showStart) Icons.Rounded.Close else Icons.Rounded.Refresh, "Reload")
                }
                IconButton(onClick = { b.goStart() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Home, "Start page")
                }
            }
            if (!b.showStart && b.isLoading && b.progress in 1..99) {
                LinearProgressIndicator(progress = { b.progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (b.showStart) {
                    BrowserStart { b.open(it) }
                } else {
                    AndroidView(
                        factory = {
                            (b.web.parent as? ViewGroup)?.removeView(b.web)
                            b.web
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    val err = b.error
                    if (err != null) {
                        Column(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                        ) {
                            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
                            Text("Can't open this page", style = MaterialTheme.typography.titleLarge)
                            Text(
                                err, textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.size(4.dp))
                            GradientButton("Try again", { b.error = null; b.web.reload() })
                        }
                    }
                    if (err == null && looksLikeVideo(b.currentUrl)) {
                        val shape = RoundedCornerShape(50)
                        Row(
                            Modifier.align(Alignment.BottomEnd).padding(18.dp)
                                .shadow(10.dp, shape)
                                .clip(shape)
                                .background(BrandBrush)
                                .clickable { onDownload(b.currentUrl, b.pageTitle) }
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

        // fullscreen video overlay
        b.customView?.let { v ->
            AndroidView(
                factory = {
                    (v.parent as? ViewGroup)?.removeView(v)
                    v
                },
                modifier = Modifier.fillMaxSize().background(Color.Black)
            )
        }
    }
}

@Composable
private fun BrowserStart(onOpen: (String) -> Unit) {
    val sites = Platform.values().filter { it != Platform.OTHER }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
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
        Spacer(Modifier.size(4.dp))
        SiteGrid(columns = 3, count = sites.size) { i ->
            val p = sites[i]
            SiteTile(p.label, { onOpen(p.home) }) { PlatformBadge(p, 56.dp) }
        }
    }
}
