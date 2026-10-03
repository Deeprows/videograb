package com.example.videograb

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * UI-side handle on the PlaybackService. All state the player screens draw lives here
 * as Compose state, so the full player, the mini player and PiP always agree.
 */
object Playback {
    var controller by mutableStateOf<MediaController?>(null)
        private set

    var showPlayer by mutableStateOf(false)
    var inPip by mutableStateOf(false)

    var hasMedia by mutableStateOf(false)
    var playWhenReady by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var isVideo by mutableStateOf(true)
    var speed by mutableFloatStateOf(1f)
    var repeatOne by mutableStateOf(false)
    var title by mutableStateOf("")
    var subtitle by mutableStateOf("")
    var thumb by mutableStateOf("")
    var index by mutableIntStateOf(0)
    var count by mutableIntStateOf(0)
    var videoW by mutableIntStateOf(0)
    var videoH by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)

    private var future: ListenableFuture<MediaController>? = null
    private val waiting = mutableListOf<(MediaController) -> Unit>()
    private var queue: List<DlItem> = emptyList()

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            Playback.playWhenReady = playWhenReady
            if (!playWhenReady) savePosition()
        }

        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            ended = state == Player.STATE_ENDED
            if (state == Player.STATE_ENDED) controller?.currentMediaItem?.mediaId?.let { forgetPosition(it) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) savePosition()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            error = null
            controller?.let { syncItem(it) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            isVideo = tracks.isTypeSelected(C.TRACK_TYPE_VIDEO) || tracks.groups.isEmpty()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            videoW = videoSize.width
            videoH = videoSize.height
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            speed = playbackParameters.speed
        }

        override fun onPlayerError(e: PlaybackException) {
            error = "This file couldn\u2019t be played (${e.errorCodeName})"
        }
    }

    // ------------------------------------------------------------ connection
    private fun connect(app: Context, onReady: (MediaController) -> Unit) {
        val c = controller
        if (c != null && c.isConnected) { onReady(c); return }
        waiting += onReady
        if (future != null) return
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    reset()
                }
            })
            .buildAsync()
        future = f
        f.addListener({
            try {
                val mc = f.get()
                mc.addListener(listener)
                controller = mc
                syncAll(mc)
                val todo = waiting.toList()
                waiting.clear()
                todo.forEach { it(mc) }
            } catch (_: Exception) {
                future = null
                waiting.clear()
                showPlayer = false
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun reset() {
        controller = null
        future = null
        hasMedia = false
        showPlayer = false
        playWhenReady = false
        inPip = false
    }

    private fun syncAll(c: MediaController) {
        playWhenReady = c.playWhenReady
        buffering = c.playbackState == Player.STATE_BUFFERING
        speed = c.playbackParameters.speed
        repeatOne = c.repeatMode == Player.REPEAT_MODE_ONE
        syncItem(c)
    }

    private fun syncItem(c: MediaController) {
        val item = c.currentMediaItem
        hasMedia = item != null
        title = item?.mediaMetadata?.title?.toString() ?: ""
        subtitle = item?.mediaMetadata?.artist?.toString() ?: ""
        thumb = item?.mediaMetadata?.artworkUri?.toString() ?: ""
        index = c.currentMediaItemIndex
        count = c.mediaItemCount
    }

    // ------------------------------------------------------------ commands
    /** Plays [items] starting at [startId]; the rest of the list becomes the queue (next / previous). */
    fun play(ctx: Context, items: List<DlItem>, startId: String) {
        val list = items.filter { it.state == DlState.DONE && it.uri != null }
        if (list.isEmpty()) return
        val start = list.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        showPlayer = true
        val app = ctx.applicationContext
        connect(app) { c ->
            // already playing this very item: just bring the player back
            if (c.currentMediaItem?.mediaId == list[start].id && c.mediaItemCount > 0) {
                if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
                c.play()
                syncAll(c)
                return@connect
            }
            queue = list
            error = null
            c.setMediaItems(list.map { toMediaItem(it) }, start, resumePosition(app, list[start].id))
            c.prepare()
            c.play()
            syncAll(c)
        }
    }

    private fun toMediaItem(d: DlItem): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(d.title)
            .setArtist(d.platform.label)
            .apply { if (d.thumb.isNotBlank()) setArtworkUri(Uri.parse(d.thumb)) }
            .build()
        return MediaItem.Builder()
            .setMediaId(d.id)
            .setUri(Uri.parse(d.uri))
            .setMediaMetadata(meta)
            .build()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
        if (c.playWhenReady) c.pause() else c.play()
    }

    fun pause() { controller?.pause() }

    fun seekBy(ms: Long) {
        val c = controller ?: return
        val d = c.duration
        val target = (c.currentPosition + ms).coerceAtLeast(0L)
        c.seekTo(if (d != C.TIME_UNSET) target.coerceAtMost(d) else target)
    }

    fun changeSpeed(s: Float) { controller?.setPlaybackSpeed(s) }

    fun toggleRepeat() {
        val c = controller ?: return
        repeatOne = !repeatOne
        c.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun retry() {
        val c = controller ?: return
        error = null
        c.prepare()
        c.play()
    }

    /** Fully stops playback, clears the queue and removes the notification. */
    fun stop() {
        savePosition()
        val c = controller
        showPlayer = false
        inPip = false
        if (c != null) {
            c.stop()
            c.clearMediaItems()
            c.release()
        }
        future = null
        controller = null
        hasMedia = false
        playWhenReady = false
    }

    /** Downloads that were deleted must not stay in the queue. */
    fun dropMissing(items: List<DlItem>) {
        val c = controller ?: return
        val alive = items.filter { it.state == DlState.DONE }.map { it.id }.toSet()
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId !in alive) c.removeMediaItem(i)
        }
        if (c.mediaItemCount == 0) stop() else syncItem(c)
    }

    // ------------------------------------------------------------ resume positions
    private fun prefs() = Engine.app.getSharedPreferences("positions", Context.MODE_PRIVATE)

    private fun resumePosition(ctx: Context, id: String): Long {
        val p = ctx.getSharedPreferences("positions", Context.MODE_PRIVATE).getLong(id, 0L)
        return if (p > 5_000L) p - 1_000L else C.TIME_UNSET
    }

    private fun forgetPosition(id: String) { prefs().edit().remove(id).apply() }

    private fun savePosition() {
        val c = controller ?: return
        val id = c.currentMediaItem?.mediaId ?: return
        val d = c.duration
        val p = c.currentPosition
        if (d == C.TIME_UNSET || d <= 0) return
        if (p > d - 5_000L || p < 5_000L) forgetPosition(id)
        else prefs().edit().putLong(id, p).apply()
    }
}
