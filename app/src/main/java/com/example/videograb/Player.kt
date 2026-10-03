package com.example.videograb

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun clock(ms: Long): String = if (ms <= 0L) "0:00" else fmtDuration(ms / 1000).ifEmpty { "0:00" }

private val Speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun speedLabel(s: Float) = (if (s % 1f == 0f) s.toInt().toString() else s.toString()) + "x"

// ====================================================================== full-screen player
@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val c = Playback.controller
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (c == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
            }
            BackHandler(onBack = onClose)
        } else {
            PlayerContent(c, onClose)
        }
    }
}

@Composable
private fun PlayerContent(c: MediaController, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    val view = LocalView.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val inPip = Playback.inPip

    var controls by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    var fill by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudKey by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onClose)

    // position / duration ticker
    LaunchedEffect(c) {
        while (true) {
            if (!dragging) pos = c.currentPosition
            val d = c.duration
            dur = if (d == C.TIME_UNSET || d < 0) 0L else d
            delay(250)
        }
    }
    // hide the controls a few seconds after the last touch while playing
    LaunchedEffect(controls, tick, Playback.playWhenReady) {
        if (controls && Playback.playWhenReady) {
            delay(3500)
            controls = false
        }
    }
    LaunchedEffect(hudKey) {
        if (hud != null) {
            delay(800)
            hud = null
        }
    }
    // immersive mode while the player is on screen
    DisposableEffect(Unit) {
        val w = activity?.window
        val ctl = if (w != null) WindowCompat.getInsetsController(w, view) else null
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (w != null) {
                val lp = w.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }

    // ---- video surface
    AndroidView(
        factory = { PlayerView(it).apply { useController = false; keepScreenOn = true; player = c } },
        update = {
            it.player = c
            it.resizeMode = if (fill && !inPip) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        modifier = Modifier.fillMaxSize()
    )

    // ---- audio files: cover art instead of a black screen
    if (!Playback.isVideo && !inPip) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Thumb(Playback.thumb, Platform.OTHER, Modifier.size(240.dp), 28.dp)
        }
    }

    if (inPip) return

    // ---- gestures: tap = controls, double tap = seek 10s, vertical drag = brightness / volume
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controls = !controls; tick++ },
                    onDoubleTap = { o ->
                        if (o.x < size.width / 2f) {
                            Playback.seekBy(-10_000L); hud = "\u221210s"
                        } else {
                            Playback.seekBy(10_000L); hud = "+10s"
                        }
                        hudKey++
                    }
                )
            }
            .pointerInput(Unit) {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                var left = true
                var vol = 0f
                var bright = 0.5f
                detectVerticalDragGestures(
                    onDragStart = { o ->
                        left = o.x < size.width / 2f
                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                        val cur = activity?.window?.attributes?.screenBrightness ?: -1f
                        bright = if (cur < 0f) 0.5f else cur
                    },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        val delta = -dy / size.height * 1.3f
                        if (left) {
                            bright = (bright + delta).coerceIn(0.02f, 1f)
                            val w = activity?.window
                            if (w != null) {
                                val lp = w.attributes
                                lp.screenBrightness = bright
                                w.attributes = lp
                            }
                            hud = "Brightness ${(bright * 100).roundToInt()}%"
                        } else {
                            vol = (vol + delta).coerceIn(0f, 1f)
                            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, (vol * max).roundToInt(), 0)
                            hud = "Volume ${(vol * 100).roundToInt()}%"
                        }
                        hudKey++
                    }
                )
            }
    )

    // ---- feedback bubble (seek / brightness / volume)
    hud?.let {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Surface(
                shape = RoundedCornerShape(50), color = Color(0xB3000000),
                modifier = Modifier.safeDrawingPadding().padding(top = 72.dp)
            ) {
                Text(
                    it, color = Color.White, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    // ---- error
    Playback.error?.let { msg ->
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(msg, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { Playback.retry() }) { Text("Try again") }
        }
    }

    // ---- controls
    AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
            )
            Box(
                Modifier.fillMaxWidth().height(170.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            )

            // top bar
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).safeDrawingPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Minimise player", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        Playback.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (Playback.count > 1) {
                        Text(
                            "${Playback.index + 1} of ${Playback.count}",
                            color = Color(0xB3FFFFFF), style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (Build.VERSION.SDK_INT >= 26 && Playback.isVideo) {
                    IconButton(onClick = { (activity as? MainActivity)?.enterPip() }) {
                        Icon(PipIcon, "Picture in picture", tint = Color.White)
                    }
                }
            }

            // centre transport
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (Playback.count > 1) {
                    CtrlButton(SkipPreviousIcon, "Previous", 30) { c.seekToPreviousMediaItem(); tick++ }
                }
                CtrlButton(RewindIcon, "Back 10 seconds", 30) { Playback.seekBy(-10_000L); tick++ }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(BrandBrush)
                        .clickable { Playback.togglePlay(); tick++ },
                    contentAlignment = Alignment.Center
                ) {
                    if (Playback.buffering && Playback.playWhenReady) {
                        CircularProgressIndicator(Modifier.size(34.dp), color = Color.White, strokeWidth = 3.dp)
                    } else {
                        val icon: ImageVector = when {
                            Playback.ended -> Icons.Rounded.Refresh
                            Playback.playWhenReady -> PauseIcon
                            else -> Icons.Rounded.PlayArrow
                        }
                        Icon(icon, "Play or pause", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                }
                CtrlButton(ForwardIcon, "Forward 10 seconds", 30) { Playback.seekBy(10_000L); tick++ }
                if (Playback.count > 1) {
                    CtrlButton(SkipNextIcon, "Next", 30) { c.seekToNextMediaItem(); tick++ }
                }
            }

            // bottom: seek bar + options
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).safeDrawingPadding().padding(horizontal = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(clock(if (dragging) (dragFrac * dur).toLong() else pos), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = if (dragging) dragFrac else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                        onValueChange = { dragging = true; dragFrac = it; tick++ },
                        onValueChangeFinished = {
                            if (dur > 0) c.seekTo((dragFrac * dur).toLong())
                            pos = (dragFrac * dur).toLong()
                            dragging = false
                        },
                        enabled = dur > 0,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0x55FFFFFF),
                            disabledThumbColor = Color(0x88FFFFFF),
                            disabledActiveTrackColor = Color(0x55FFFFFF),
                            disabledInactiveTrackColor = Color(0x33FFFFFF),
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(clock(dur), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Box {
                        OptionChip(speedLabel(Playback.speed)) { speedMenu = true; tick++ }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            Speeds.forEach { s ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            speedLabel(s),
                                            fontWeight = if (s == Playback.speed) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = { Playback.changeSpeed(s); speedMenu = false }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { Playback.toggleRepeat(); tick++ }) {
                        Icon(
                            if (Playback.repeatOne) RepeatOneIcon else RepeatIcon, "Repeat",
                            tint = if (Playback.repeatOne) Color.White else Color(0x99FFFFFF)
                        )
                    }
                    if (Playback.isVideo) {
                        OptionChip(if (fill) "Fill" else "Fit") { fill = !fill; tick++ }
                        Spacer(Modifier.width(2.dp))
                        IconButton(onClick = {
                            activity?.requestedOrientation =
                                if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            tick++
                        }) {
                            Icon(
                                if (landscape) FullscreenExitIcon else FullscreenIcon,
                                if (landscape) "Exit full screen" else "Full screen", tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CtrlButton(icon: ImageVector, desc: String, dim: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size((dim + 18).dp)) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(dim.dp))
    }
}

@Composable
private fun OptionChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

// ====================================================================== mini player
/** Sits above the bottom navigation while something is playing and the full player is closed. */
@Composable
fun MiniPlayer() {
    val c = Playback.controller
    if (c != null && Playback.hasMedia && !Playback.showPlayer) MiniPlayerBar(c)
}

@Composable
private fun MiniPlayerBar(c: MediaController) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(c) {
        while (true) {
            val d = c.duration
            progress = if (d > 0 && d != C.TIME_UNSET) (c.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { Playback.showPlayer = true }
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Thumb(Playback.thumb, Platform.OTHER, Modifier.size(44.dp), 10.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        Playback.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (Playback.playWhenReady) "Playing" else "Paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = { Playback.togglePlay() }) {
                    Icon(
                        if (Playback.playWhenReady) PauseIcon else Icons.Rounded.PlayArrow,
                        "Play or pause", tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = { Playback.stop() }) {
                    Icon(Icons.Rounded.Close, "Stop", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }
    }
}
