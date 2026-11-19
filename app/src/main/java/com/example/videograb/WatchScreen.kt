package com.example.videograb

import android.content.Intent
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface WatchState {
    object Idle : WatchState
    data class Loading(val url: String, val title: String, val thumb: String) : WatchState
    data class Ready(val info: MediaInfo) : WatchState
    data class Error(val url: String, val title: String, val thumb: String, val message: String) : WatchState
}

class WatchViewModel : ViewModel() {
    var state by androidx.compose.runtime.mutableStateOf<WatchState>(WatchState.Idle)
        private set
    var related by androidx.compose.runtime.mutableStateOf<List<SearchResult>>(emptyList())
        private set

    private var job: Job? = null
    private var relatedJob: Job? = null

    fun open(rawUrl: String, title: String = "", thumb: String = "") {
        val url = rawUrl.trim()
        job?.cancel()
        relatedJob?.cancel()
        Engine.cancelInfo()
        related = emptyList()
        state = WatchState.Loading(url, title, thumb)
        job = viewModelScope.launch {
            try {
                val info = Engine.fetchInfo(url, title, thumb)
                state = WatchState.Ready(info)
                LibraryStore.addHistory(LibItem(url, info.title, info.thumb, info.uploader))
                relatedJob = launch {
                    try {
                        val q = "${info.uploader} ${info.title}".trim().take(60)
                        related = Feed.search(q).filter { it.url != url && !url.contains(it.id) }.take(12)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) { }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = WatchState.Error(url, title, thumb, Engine.friendlyError(e.message))
            }
        }
    }

    fun close() {
        job?.cancel()
        relatedJob?.cancel()
        Engine.cancelInfo()
        state = WatchState.Idle
        related = emptyList()
    }
}

@Composable
fun WatchScreen(
    vm: WatchViewModel,
    onClose: () -> Unit,
    onDownload: (MediaInfo) -> Unit,
    onQuickDownload: (url: String, title: String, thumb: String) -> Unit,
    onWatch: (url: String, title: String, thumb: String) -> Unit,
) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val st = vm.state
    var audioOnly by remember(st) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth()) {
            when (st) {
                is WatchState.Ready -> {
                    val opts = st.info.options
                    val audioOpt = opts.last()
                    val useAudio = audioOnly && audioOpt.previewUrl.isNotBlank()
                    SheetPreview(
                        st.info, if (useAudio) audioOpt else opts.first(),
                        autoStart = true, audioOnly = useAudio
                    )
                }
                is WatchState.Loading -> {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (st.thumb.isNotBlank()) Thumb(st.thumb, platformOf(st.url), Modifier.fillMaxSize(), 0.dp)
                        CircularProgressIndicator()
                    }
                }
                is WatchState.Error -> {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            st.message, color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                else -> {}
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                    .background(Color(0x66000000), CircleShape)
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
        }

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (st) {
                is WatchState.Ready -> {
                    val info = st.info
                    item {
                        Column {
                            Text(
                                info.title.ifBlank { "${info.platform.label} video" },
                                maxLines = 3, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            val sub = listOf(info.uploader, fmtDuration(info.durationSec))
                                .filter { it.isNotBlank() }.joinToString(" \u00B7 ")
                            if (sub.isNotBlank()) {
                                Text(
                                    sub, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item {
                        val saved = LibraryStore.saved.any { it.url == info.url }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ActionItem(
                                if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                if (saved) "Saved" else "Save",
                                if (saved) Color(0xFFFF4D8D) else MaterialTheme.colorScheme.onSurface
                            ) { LibraryStore.toggleSaved(LibItem(info.url, info.title, info.thumb, info.uploader)) }
                            ActionItem(
                                HeadphonesIcon,
                                if (audioOnly) "Play video" else "Play audio",
                                MaterialTheme.colorScheme.onSurface
                            ) { audioOnly = !audioOnly }
                            ActionItem(Icons.Rounded.Share, "Share", MaterialTheme.colorScheme.onSurface) {
                                val i = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, info.url)
                                }
                                ctx.startActivity(Intent.createChooser(i, "Share link"))
                            }
                            ActionItem(DownloadIcon, "Download", Color.White, highlighted = true) { onDownload(info) }
                        }
                    }
                    if (vm.related.isNotEmpty()) {
                        item { Text("Related videos", style = MaterialTheme.typography.titleMedium) }
                        items(vm.related) { r ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .clickable { onWatch(r.url, r.title, r.thumb) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    Thumb(r.thumb, Platform.YOUTUBE, Modifier.width(150.dp).aspectRatio(16f / 9f), 12.dp)
                                    if (r.duration.isNotBlank()) {
                                        Tag(r.duration, Modifier.align(Alignment.BottomEnd).padding(5.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    val sub = listOf(r.channel, r.views).filter { it.isNotBlank() }.joinToString(" \u00B7 ")
                                    if (sub.isNotBlank()) {
                                        Text(
                                            sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { onQuickDownload(r.url, r.title, r.thumb) }) {
                                    Icon(DownloadIcon, "Download", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                is WatchState.Loading -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (st.title.isNotBlank()) {
                            Text(st.title, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Loading video\u2026", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is WatchState.Error -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { vm.open(st.url, st.title, st.thumb) },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("Try again") }
                        OutlinedButton(
                            onClick = { onQuickDownload(st.url, st.title, st.thumb) },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("Open download options") }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape)
                .background(if (highlighted) BrandBrush else SolidColor(MaterialTheme.colorScheme.surfaceVariant)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
