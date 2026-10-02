package com.example.videograb

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSheet(
    state: LinkState,
    onDismiss: () -> Unit,
    onPick: (MediaInfo, QualityOption) -> Unit,
    onRetry: (url: String, title: String) -> Unit,
    onAnyway: (url: String, title: String) -> Unit,
    onSignIn: (url: String) -> Unit,
) {
    if (state is LinkState.Idle) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp)
        ) {
            when (state) {
                is LinkState.Loading -> LoadingBody(state)
                is LinkState.Ready -> ReadyBody(state.info, onPick)
                is LinkState.Error -> ErrorBody(state, onRetry, onAnyway, onSignIn)
                else -> {}
            }
        }
    }
}

@Composable
private fun LoadingBody(s: LinkState.Loading) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PlatformBadge(s.platform, 52.dp)
        CircularProgressIndicator()
        Text("Reading ${s.platform.label} link\u2026", style = MaterialTheme.typography.titleMedium)
        Text(
            s.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReadyBody(info: MediaInfo, onPick: (MediaInfo, QualityOption) -> Unit) {
    var sel by remember(info) { mutableIntStateOf(0) }

    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        Thumb(info.thumb, info.platform, Modifier.matchParentSize(), 18.dp)
        PlatformBadge(info.platform, 30.dp, Modifier.align(Alignment.TopStart).padding(10.dp))
        if (info.durationSec > 0) {
            Tag(fmtDuration(info.durationSec), Modifier.align(Alignment.BottomEnd).padding(10.dp))
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(
        info.title.ifBlank { "${info.platform.label} video" },
        maxLines = 2, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.titleMedium
    )
    if (info.uploader.isNotBlank()) {
        Text(
            info.uploader, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (info.count > 1) {
        Spacer(Modifier.height(6.dp))
        Text(
            "This post has ${info.count} videos. All of them will be saved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Spacer(Modifier.height(18.dp))
    Text("Quality", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        info.options.forEachIndexed { i, o ->
            val selected = i == sel
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier.fillMaxWidth().clip(shape)
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape
                    )
                    .clickable { sel = i }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected, onClick = { sel = i })
                Text(o.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                val size = fmtSize(o.bytes)
                if (size.isNotEmpty()) {
                    Text(
                        "~$size", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    GradientButton("Download", onClick = { onPick(info, info.options[sel]) }, icon = DownloadIcon)
}

@Composable
private fun ErrorBody(
    s: LinkState.Error,
    onRetry: (String, String) -> Unit,
    onAnyway: (String, String) -> Unit,
    onSignIn: (String) -> Unit,
) {
    val platform = platformOf(s.url)
    val needsSignIn = platform.cookieSites.isNotEmpty() &&
        (s.message.contains("sign in", ignoreCase = true) || s.message.contains("login", ignoreCase = true))
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.width(40.dp).height(40.dp))
        Text("Couldn\u2019t read this link", style = MaterialTheme.typography.titleMedium)
        Text(
            s.message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        if (needsSignIn) {
            GradientButton("Sign in to ${platform.label}", onClick = { onSignIn(s.url) })
            OutlinedButton(onClick = { onRetry(s.url, s.title) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Try again")
            }
        } else {
            GradientButton("Try again", onClick = { onRetry(s.url, s.title) })
        }
        OutlinedButton(onClick = { onAnyway(s.url, s.title) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Download anyway (best quality)")
        }
    }
}

// Box scope helper kept local so the thumbnail fills the 16:9 container.
private fun Modifier.matchParentSize(): Modifier = this.then(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
