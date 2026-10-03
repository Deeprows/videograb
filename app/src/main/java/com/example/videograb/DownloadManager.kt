package com.example.videograb

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class DlState { QUEUED, RUNNING, PAUSED, DONE, FAILED }

data class DlItem(
    val id: String,
    val url: String,
    val title: String,
    val quality: Int,            // 0 = best, -1 = MP3, otherwise max height
    val platform: Platform = Platform.OTHER,
    val thumb: String = "",
    val state: DlState = DlState.QUEUED,
    val progress: Float = 0f,
    val eta: Long = -1,
    val uri: String? = null,
    val size: Long = 0,
    val error: String? = null,
    val note: String? = null,
)

object DownloadManager {
    private const val MAX_PARALLEL = 2
    private val MEDIA_EXT = setOf(
        "mp4", "mkv", "webm", "mov", "m4v", "3gp", "avi", "ts",
        "mp3", "m4a", "opus", "ogg", "aac", "flac", "wav"
    )

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val runs = ConcurrentHashMap<String, String>()

    private val _items = MutableStateFlow<List<DlItem>>(emptyList())
    val items: StateFlow<List<DlItem>> = _items.asStateFlow()

    fun init(context: Context) {
        app = context.applicationContext
        try {
            val json = app.getSharedPreferences("downloads", Context.MODE_PRIVATE)
                .getString("all", null)
                ?: app.getSharedPreferences("downloads", Context.MODE_PRIVATE).getString("done", "[]")
                ?: "[]"
            val arr = JSONArray(json)
            _items.value = (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val saved = runCatching { DlState.valueOf(o.optString("state", "DONE")) }.getOrDefault(DlState.DONE)
                // anything that was mid-flight when the app died comes back paused
                val state = if (saved == DlState.RUNNING || saved == DlState.QUEUED) DlState.PAUSED else saved
                val uri = o.optString("uri").ifBlank { null }
                if (state == DlState.DONE && uri == null) return@mapNotNull null
                val url = o.optString("url")
                DlItem(
                    id = o.getString("id"), url = url,
                    title = o.getString("title"), quality = o.optInt("quality"),
                    platform = platformOf(url),
                    thumb = o.optString("thumb"),
                    state = state,
                    progress = if (state == DlState.DONE) 1f else o.optDouble("progress", 0.0).toFloat(),
                    uri = uri, size = o.optLong("size", 0L),
                    error = o.optString("error").ifBlank { null },
                )
            }
        } catch (_: Exception) { }
    }

    // ---------- public API ----------
    fun enqueue(url: String, title: String, quality: Int, thumb: String = "") {
        val u = url.trim()
        val item = DlItem(
            UUID.randomUUID().toString(), u, title.ifBlank { u }, quality,
            platform = platformOf(u), thumb = thumb
        )
        _items.update { listOf(item) + it }
        persist()
        pump()
    }

    fun pause(id: String) {
        val state = get(id)?.state ?: return
        if (state != DlState.RUNNING && state != DlState.QUEUED) return
        update(id) { it.copy(state = DlState.PAUSED, note = null) }
        if (state == DlState.RUNNING) kill(id)
        persist()
    }

    fun resume(id: String) {
        update(id) { it.copy(state = DlState.QUEUED, error = null) }
        pump()
    }

    fun remove(id: String, deleteFile: Boolean) {
        val item = get(id) ?: return
        _items.update { list -> list.filter { it.id != id } }
        if (item.state == DlState.RUNNING) kill(id)
        scope.launch {
            File(app.cacheDir, "dl/$id").deleteRecursively()
            if (deleteFile && item.uri != null) {
                try {
                    val uri = Uri.parse(item.uri)
                    if (uri.scheme == "file") File(uri.path!!).delete()
                    else app.contentResolver.delete(uri, null, null)
                } catch (_: Exception) { }
            }
            persist()
        }
    }

    // ---------- internals ----------
    private fun get(id: String) = _items.value.firstOrNull { it.id == id }

    private fun update(id: String, f: (DlItem) -> DlItem) {
        _items.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    private fun kill(id: String) {
        scope.launch { try { YoutubeDL.getInstance().destroyProcessById(id) } catch (_: Exception) { } }
    }

    private fun pump() {
        synchronized(lock) {
            var running = _items.value.count { it.state == DlState.RUNNING }
            val queued = _items.value.filter { it.state == DlState.QUEUED }.reversed()
            for (q in queued) {
                if (running >= MAX_PARALLEL) break
                update(q.id) { it.copy(state = DlState.RUNNING, error = null) }
                running++
                scope.launch { runItem(q.id) }
            }
        }
        if (_items.value.any { it.state == DlState.RUNNING || it.state == DlState.QUEUED }) {
            try {
                ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
            } catch (_: Exception) { }
        }
    }

    private suspend fun runItem(id: String) {
        val item = get(id) ?: return
        val token = UUID.randomUUID().toString()
        runs[id] = token
        val dir = File(app.cacheDir, "dl/$id").apply { mkdirs() }   // kept between pause/resume
        try {
            Engine.awaitReady()
            var cookies = Engine.cookieFile(item.platform)
            var repaired = false
            var triedLogin = false
            while (true) {
                try {
                    download(item, id, dir, cookies)
                    break
                } catch (e: Exception) {
                    val cur = get(id)
                    val stillMine = cur != null && cur.state == DlState.RUNNING && runs[id] == token
                    if (!stillMine) throw e
                    if (item.platform == Platform.YOUTUBE && !Engine.useAltClients && Engine.isBotCheck(e.message)) {
                        Engine.useAltClients = true   // retry once with the no-sign-in player clients
                        continue
                    }
                    if (!triedLogin && cookies == null && item.platform == Platform.YOUTUBE && Engine.isBotCheck(e.message)) {
                        triedLogin = true
                        val forced = Engine.cookieFile(item.platform, force = true)
                        if (forced != null) { cookies = forced; continue }
                    }
                    if (!repaired && Engine.looksBroken(e.message)) {
                        // The site probably changed. Refresh yt-dlp once and try again.
                        repaired = true
                        update(id) { it.copy(note = "Updating download engine\u2026") }
                        Engine.update(nightly = true)
                        update(id) { it.copy(note = null) }
                        continue
                    }
                    throw e
                }
            }

            val files = dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in MEDIA_EXT }
                ?.sortedBy { it.name }
                .orEmpty()
            if (files.isEmpty()) error("No video was found at that link")

            val sizes = files.map { it.length() }
            val saved = files.map { saveToMediaStore(it) }
            dir.deleteRecursively()

            update(id) {
                it.copy(
                    state = DlState.DONE, progress = 1f, uri = saved[0].toString(),
                    title = files[0].nameWithoutExtension, size = sizes[0],
                    note = null, error = null, eta = -1
                )
            }
            // posts with several videos: every extra file becomes its own library entry
            if (files.size > 1) {
                val extras = (1 until files.size).map { i ->
                    DlItem(
                        UUID.randomUUID().toString(), item.url, files[i].nameWithoutExtension, item.quality,
                        platform = item.platform, thumb = item.thumb, state = DlState.DONE, progress = 1f,
                        uri = saved[i].toString(), size = sizes[i]
                    )
                }
                _items.update { extras + it }
            }
            persist()
        } catch (e: Exception) {
            val cur = get(id)
            if (runs[id] == token && cur != null && cur.state == DlState.RUNNING) {
                update(id) { it.copy(state = DlState.FAILED, error = Engine.friendlyError(e.message), note = null) }
            }
            persist()
        } finally {
            pump()
        }
    }

    private fun download(item: DlItem, id: String, dir: File, cookies: String?) {
        val req = YoutubeDLRequest(item.url).apply {
            addOption("-o", "${dir.absolutePath}/%(title).80s.%(ext)s")
            addOption("--windows-filenames")
            addOption("--no-mtime")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("-N", "4")
            addOption("--retries", "5")
            addOption("--fragment-retries", "5")
            addOption("--socket-timeout", "20")
            if (cookies != null) addOption("--cookies", cookies)
            if (item.platform == Platform.YOUTUBE && Engine.useAltClients) Engine.addYoutubeClients(this)
            when {
                item.quality < 0 -> {
                    addOption("-f", "ba/b")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                }
                else -> {
                    // Prefer the highest resolution, then H.264/AAC so files play everywhere.
                    val cap = if (item.quality == 0) "" else "[height<=${item.quality}]"
                    addOption("-f", "bv*$cap+ba/b$cap/bv*+ba/b")
                    addOption("-S", "res,vcodec:h264,acodec:aac")
                    addOption("--merge-output-format", "mp4")
                }
            }
        }
        YoutubeDL.getInstance().execute(req, id) { p, eta, _ ->
            if (p >= 0f) {
                val next = (p / 100f).coerceIn(0f, 0.99f)
                // video + audio download as two passes; never let the bar move backwards
                update(id) { it.copy(progress = maxOf(it.progress, next), eta = eta) }
            }
        }
    }

    private fun persist() {
        val arr = JSONArray()
        _items.value.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("url", it.url).put("title", it.title)
                    .put("quality", it.quality).put("thumb", it.thumb).put("state", it.state.name)
                    .put("progress", it.progress.toDouble()).put("uri", it.uri ?: "")
                    .put("size", it.size).put("error", it.error ?: "")
            )
        }
        app.getSharedPreferences("downloads", Context.MODE_PRIVATE)
            .edit().putString("all", arr.toString()).apply()
    }

    private fun saveToMediaStore(file: File): Uri {
        val ext = file.extension.lowercase()
        val isAudio = ext in setOf("mp3", "m4a", "opus", "aac", "ogg", "flac", "wav")
        val mime = when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            else -> "video/mp4"
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = if (isAudio)
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/VideoGrab"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = app.contentResolver.insert(collection, values) ?: error("Could not save to your gallery")
            try {
                app.contentResolver.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
                app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                try { app.contentResolver.delete(uri, null, null) } catch (_: Exception) { }
                throw e
            }
            return uri
        }
        // Android 7-9: app-specific external folder (no permission needed)
        val base = app.getExternalFilesDir(if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES)
            ?: app.filesDir
        val dest = File(File(base, "VideoGrab").apply { mkdirs() }, file.name)
        file.copyTo(dest, overwrite = true)
        return Uri.fromFile(dest)
    }
}
