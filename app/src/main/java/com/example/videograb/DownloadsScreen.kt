package com.example.videograb

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun DownloadsScreen(all: List<DlItem>, onPlay: (DlItem) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val active = all.filter { it.state != DlState.DONE }
    val done = all.filter { it.state == DlState.DONE }
    val list = if (tab == 0) active else done

    Column(Modifier.fillMaxSize()) {
        Text(
            "Downloads", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp)
        )
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SegmentPill("In progress (${active.size})", tab == 0) { tab = 0 }
            SegmentPill("Saved (${done.size})", tab == 1) { tab = 1 }
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(DownloadIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                    }
                    Text(
                        if (tab == 0) "Nothing downloading" else "No saved videos yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Paste a link on Home to get started.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list, key = { it.id }) { d ->
                    if (d.state == DlState.DONE) DoneRow(d, onPlay) else ActiveRow(d)
                }
            }
        }
    }
}

@Composable
private fun SegmentPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun statusText(d: DlItem): String {
    val pct = (d.progress * 100).toInt()
    return when (d.state) {
        DlState.RUNNING -> d.note ?: ("$pct%" + if (d.eta >= 0) "  \u2022  ${fmtEta(d.eta)} left" else "")
        DlState.QUEUED -> "Waiting\u2026"
        DlState.PAUSED -> "Paused at $pct%"
        DlState.FAILED -> d.error ?: "Failed"
        DlState.DONE -> ""
    }
}

@Composable
private fun ActiveRow(d: DlItem) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                Thumb(d.thumb, d.platform, Modifier.width(104.dp).aspectRatio(16f / 9f), 12.dp)
                PlatformBadge(d.platform, 22.dp, Modifier.align(Alignment.TopStart).padding(5.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(d.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                if (d.state == DlState.RUNNING || d.state == DlState.PAUSED || d.state == DlState.QUEUED) {
                    LinearProgressIndicator(
                        progress = { d.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                Text(
                    statusText(d), style = MaterialTheme.typography.bodySmall,
                    color = if (d.state == DlState.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (d.state) {
                        DlState.RUNNING, DlState.QUEUED ->
                            IconButton(onClick = { DownloadManager.pause(d.id) }) { Icon(PauseIcon, "Pause") }
                        DlState.PAUSED ->
                            IconButton(onClick = { DownloadManager.resume(d.id) }) { Icon(Icons.Rounded.PlayArrow, "Resume") }
                        DlState.FAILED ->
                            IconButton(onClick = { DownloadManager.resume(d.id) }) { Icon(Icons.Rounded.Refresh, "Retry") }
                        else -> {}
                    }
                    IconButton(onClick = { DownloadManager.remove(d.id, false) }) { Icon(Icons.Rounded.Close, "Cancel") }
                }
            }
        }
    }
}

@Composable
private fun DoneRow(d: DlItem, onPlay: (DlItem) -> Unit) {
    val ctx = LocalContext.current
    Card(
        Modifier.fillMaxWidth().clickable { onPlay(d) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                Thumb(d.thumb, d.platform, Modifier.width(104.dp).aspectRatio(16f / 9f), 12.dp)
                Box(
                    Modifier.align(Alignment.Center).size(32.dp).clip(CircleShape).background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(d.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                val meta = listOf(d.platform.label, qualityLabel(d.quality), fmtSize(d.size)).filter { it.isNotBlank() }
                Text(
                    meta.joinToString("  \u2022  "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (d.uri?.startsWith("content:") == true) {
                        IconButton(onClick = { shareFile(ctx, d) }) { Icon(Icons.Rounded.Share, "Share") }
                    }
                    IconButton(onClick = { DownloadManager.remove(d.id, true) }) { Icon(Icons.Rounded.Delete, "Delete") }
                }
            }
        }
    }
}

private fun shareFile(ctx: android.content.Context, d: DlItem) {
    try {
        val uri = Uri.parse(d.uri)
        val mime = ctx.contentResolver.getType(uri) ?: "video/*"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Couldn\u2019t share this file", Toast.LENGTH_SHORT).show()
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
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                item.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)
            )
        }
        AndroidView(
            factory = { c -> PlayerView(c).apply { player = exo } },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}
