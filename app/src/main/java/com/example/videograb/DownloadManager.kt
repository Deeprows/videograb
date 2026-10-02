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
    val state: DlState = DlState.QUEUED,
    val progress: Float = 0f,
    val eta: Long = -1,
    val uri: String? = null,
    val error: String? = null,
)

object DownloadManager {
    private const val MAX_PARALLEL = 2

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
                .getString("done", "[]") ?: "[]"
            val arr = JSONArray(json)
            _items.value = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DlItem(
                    id = o.getString("id"), url = o.optString("url"),
                    title = o.getString("title"), quality = o.optInt("quality"),
                    state = DlState.DONE, progress = 1f, uri = o.getString("uri")
                )
            }
        } catch (_: Exception) { }
    }

    // ---------- public API ----------
    fun enqueue(url: String, title: String, quality: Int) {
        val item = DlItem(UUID.randomUUID().toString(), url.trim(), title.ifBlank { url.trim() }, quality)
        _items.update { listOf(item) + it }
        pump()
    }

    fun pause(id: String) {
        val state = get(id)?.state ?: return
        if (state != DlState.RUNNING && state != DlState.QUEUED) return
        update(id) { it.copy(state = DlState.PAUSED) }
        if (state == DlState.RUNNING) kill(id)
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
            persistDone()
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

    private fun runItem(id: String) {
        val item = get(id) ?: return
        val token = UUID.randomUUID().toString()
        runs[id] = token
        val dir = File(app.cacheDir, "dl/$id").apply { mkdirs() }   // kept between pause/resume
        try {
            val req = YoutubeDLRequest(item.url).apply {
                addOption("-o", "${dir.absolutePath}/%(title).80s.%(ext)s")
                addOption("--no-mtime")
                addOption("--no-playlist")
                addOption("-N", "4")
                when {
                    item.quality < 0 -> {
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                    }
                    item.quality == 0 -> {
                        addOption("-f", "bv*+ba/b")
                        addOption("--merge-output-format", "mp4")
                    }
                    else -> {
                        val h = item.quality
                        addOption("-f", "bv*[height<=$h]+ba/b[height<=$h]/b")
                        addOption("--merge-output-format", "mp4")
                    }
                }
            }
            YoutubeDL.getInstance().execute(req, id) { p, eta, _ ->
                if (p >= 0f) update(id) { it.copy(progress = (p / 100f).coerceIn(0f, 1f), eta = eta) }
            }
            val out = dir.listFiles()
                ?.filter { it.isFile && it.extension !in setOf("part", "ytdl", "temp") }
                ?.maxByOrNull { it.length() }
                ?: error("No output file found")
            val uri = saveToMediaStore(out)
            dir.deleteRecursively()
            update(id) {
                it.copy(state = DlState.DONE, progress = 1f, uri = uri.toString(), title = out.nameWithoutExtension)
            }
            persistDone()
        } catch (e: Exception) {
            val cur = get(id)
            if (runs[id] == token && cur != null && cur.state == DlState.RUNNING) {
                update(id) { it.copy(state = DlState.FAILED, error = e.message?.take(200) ?: "Download failed") }
            }
        } finally {
            pump()
        }
    }

    private fun persistDone() {
        val arr = JSONArray()
        _items.value.filter { it.state == DlState.DONE && it.uri != null }.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("url", it.url).put("title", it.title)
                    .put("quality", it.quality).put("uri", it.uri)
            )
        }
        app.getSharedPreferences("downloads", Context.MODE_PRIVATE)
            .edit().putString("done", arr.toString()).apply()
    }

    private fun saveToMediaStore(file: File): Uri {
        val ext = file.extension.lowercase()
        val isAudio = ext in setOf("mp3", "m4a", "opus", "aac", "ogg")
        val mime = when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "aac" -> "audio/aac"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
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
            }
            val uri = app.contentResolver.insert(collection, values) ?: error("MediaStore insert failed")
            app.contentResolver.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
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
