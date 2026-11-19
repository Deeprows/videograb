package com.example.videograb

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

private const val DEFAULT_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

@androidx.annotation.OptIn(UnstableApi::class)
private fun buildSource(ctx: Context, info: MediaInfo, o: QualityOption): MediaSource {
    val headers = HashMap<String, String>()
    if (info.platform != Platform.YOUTUBE && info.platform.home.isNotBlank()) headers["Referer"] = info.platform.home
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(o.previewUa.ifBlank { DEFAULT_UA })
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)
        .setDefaultRequestProperties(headers)
    val factory = DefaultDataSource.Factory(ctx, http)
    fun one(u: String): MediaSource {
        val item = MediaItem.fromUri(u)
        return if (u.contains(".m3u8")) HlsMediaSource.Factory(factory).createMediaSource(item)
        else ProgressiveMediaSource.Factory(factory).createMediaSource(item)
    }
    return if (o.previewAudioUrl.isBlank()) one(o.previewUrl)
    else MergingMediaSource(one(o.previewUrl), one(o.previewAudioUrl))
}

/** Video frame on top of the quality list: thumbnail + play button, plays inline in the sheet. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SheetPreview(info: MediaInfo, option: QualityOption) {
    val ctx = LocalContext.current
    var started by remember(info) { mutableStateOf(false) }
    var failed by remember(info, option) { mutableStateOf(false) }
    val usable = option.previewUrl.isNotBlank()

    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp)).background(Color.Black)
    ) {
        if (started && usable) {
            val exo = remember(info) { ExoPlayer.Builder(ctx).build() }
            LaunchedEffect(Unit) { Playback.pause() }
            DisposableEffect(exo) {
                val l = object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { failed = true }
                }
                exo.addListener(l)
                onDispose { exo.removeListener(l); exo.release() }
            }
            // (re)load whenever the chosen quality changes, keeping the current position
            LaunchedEffect(exo, option.previewUrl, option.previewAudioUrl) {
                failed = false
                val pos = exo.currentPosition
                exo.setMediaSource(buildSource(ctx, info, option))
                exo.prepare()
                if (pos > 0) exo.seekTo(pos)
                exo.playWhenReady = true
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    PlayerView(c).apply {
                        player = exo
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            )
            if (failed) {
                Text(
                    "This quality can\u2019t be previewed. You can still download it.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            }
        } else {
            Thumb(info.thumb, info.platform, Modifier.fillMaxSize(), 18.dp)
            PlatformBadge(info.platform, 30.dp, Modifier.align(Alignment.TopStart).padding(10.dp))
            if (info.durationSec > 0) {
                Tag(fmtDuration(info.durationSec), Modifier.align(Alignment.BottomEnd).padding(10.dp))
            }
            if (usable) {
                Box(
                    Modifier.align(Alignment.Center).size(60.dp).clip(CircleShape)
                        .background(Color(0x99000000)).clickable { started = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Play preview", tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
        }
    }
}
