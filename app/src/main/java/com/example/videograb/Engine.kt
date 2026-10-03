package com.example.videograb

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

sealed interface EngineState {
    object Starting : EngineState
    object Ready : EngineState
    data class Failed(val message: String) : EngineState
}

data class QualityOption(val label: String, val quality: Int, val bytes: Long)

data class MediaInfo(
    val url: String,
    val platform: Platform,
    val title: String,
    val uploader: String,
    val thumb: String,
    val durationSec: Long,
    val options: List<QualityOption>,
    val count: Int,
)

object Prefs {
    private fun sp() = Engine.app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Reuse logins from the in-app browser for private / login-only videos. */
    var useLogin: Boolean
        get() = sp().getBoolean("useLogin", true)
        set(v) { sp().edit().putBoolean("useLogin", v).apply() }

    /** YouTube is opt-in: sending account cookies to yt-dlp can get accounts flagged. */
    var youtubeLogin: Boolean
        get() = sp().getBoolean("youtubeLogin", false)
        set(v) { sp().edit().putBoolean("youtubeLogin", v).apply() }

    var nightly: Boolean
        get() = sp().getBoolean("nightly", false)
        set(v) { sp().edit().putBoolean("nightly", v).apply() }

    var lastUpdate: Long
        get() = sp().getLong("lastUpdate", 0L)
        set(v) { sp().edit().putLong("lastUpdate", v).apply() }
}

object Engine {
    lateinit var app: Context
        private set

    private const val INFO_ID = "videograb-info"
    private const val UPDATE_EVERY_MS = 12L * 60 * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateLock = Mutex()

    val state = MutableStateFlow<EngineState>(EngineState.Starting)
    val updating = MutableStateFlow(false)

    /** Starts the yt-dlp runtime off the main thread so the app opens instantly. */
    fun start(context: Context) {
        app = context.applicationContext
        scope.launch {
            try {
                YoutubeDL.getInstance().init(app)
            } catch (e: Exception) {
                Log.e("VideoGrab", "yt-dlp init failed", e)
                state.value = EngineState.Failed(e.message ?: "Could not start the download engine")
                return@launch
            }
            try {
                FFmpeg.getInstance().init(app)
            } catch (e: Exception) {
                Log.e("VideoGrab", "ffmpeg init failed", e)
            }
            // Sites change constantly; the bundled yt-dlp is old by the time it ships.
            // Flag the update BEFORE announcing Ready, otherwise the first feed request runs
            // while yt-dlp's files are being replaced and fails (the "works only after refresh" bug).
            val due = System.currentTimeMillis() - Prefs.lastUpdate > UPDATE_EVERY_MS
            if (due) updating.value = true
            state.value = EngineState.Ready
            if (due) {
                try { update(Prefs.nightly) } catch (_: Exception) { } finally { updating.value = false }
            }
        }
    }

    /** Waits until yt-dlp has been unpacked (does not wait for updates). */
    suspend fun awaitInit() {
        val s = state.first { it !is EngineState.Starting }
        if (s is EngineState.Failed) throw IllegalStateException("Download engine failed to start: ${s.message}")
    }

    /** Waits for the engine AND for any update in progress, so yt-dlp is never run mid-update. */
    suspend fun awaitReady() {
        awaitInit()
        withTimeoutOrNull(90_000) { updating.first { !it } }
    }

    /**
     * YouTube bot-check workaround: ask for player clients that don't need a sign-in / PO token.
     * Switched on automatically the first time YouTube answers "confirm you're not a bot".
     */
    @Volatile var useAltClients = false

    fun addYoutubeClients(req: YoutubeDLRequest) {
        req.addOption("--extractor-args", "youtube:player_client=android_vr,tv,web_embedded")
    }

    suspend fun update(nightly: Boolean): String = updateLock.withLock {
        awaitInit()
        updating.value = true
        try {
            withContext(Dispatchers.IO) {
                val channel = if (nightly) YoutubeDL.UpdateChannel.NIGHTLY else YoutubeDL.UpdateChannel.STABLE
                val r = YoutubeDL.getInstance().updateYoutubeDL(app, channel)
                Prefs.lastUpdate = System.currentTimeMillis()
                useAltClients = false   // fresh yt-dlp: try the normal clients again first
                if (r.toString().contains("ALREADY", ignoreCase = true)) "Engine is already up to date"
                else "Engine updated"
            }
        } catch (e: Exception) {
            "Update failed: ${e.message?.take(120)}"
        } finally {
            updating.value = false
        }
    }

    fun cancelInfo() {
        scope.launch { try { YoutubeDL.getInstance().destroyProcessById(INFO_ID) } catch (_: Exception) { } }
    }

    // ------------------------------------------------------------ cookies
    /** Exports the in-app browser's cookies for [platform] in the Netscape format yt-dlp reads. */
    suspend fun cookieFile(platform: Platform, force: Boolean = false): String? {
        if (!force) {
            if (!Prefs.useLogin) return null
            if (platform == Platform.YOUTUBE && !Prefs.youtubeLogin) return null
        }
        if (platform.cookieSites.isEmpty()) return null
        val lines = withContext(Dispatchers.Main) {
            try {
                val cm = CookieManager.getInstance()
                val out = mutableListOf<String>()
                for ((url, domain) in platform.cookieSites) {
                    val raw = cm.getCookie(url) ?: continue
                    for (pair in raw.split(";")) {
                        val kv = pair.trim()
                        val i = kv.indexOf('=')
                        if (i <= 0) continue
                        out += ".$domain\tTRUE\t/\tTRUE\t2147483647\t${kv.substring(0, i)}\t${kv.substring(i + 1)}"
                    }
                }
                out
            } catch (_: Exception) {
                emptyList<String>()
            }
        }
        if (lines.isEmpty()) return null
        // forced use (bot-check recovery) only makes sense when the user is really signed in
        if (force && platform == Platform.YOUTUBE &&
            lines.none { it.contains("\tSAPISID\t") || it.contains("\t__Secure-3PSID\t") }) return null
        return withContext(Dispatchers.IO) {
            val f = File(app.filesDir, "cookies_${platform.name}.txt")
            f.writeText("# Netscape HTTP Cookie File\n" + lines.joinToString("\n") + "\n")
            f.absolutePath
        }
    }

    // ------------------------------------------------------------ link info
    suspend fun fetchInfo(url: String, titleHint: String, thumbHint: String): MediaInfo {
        awaitReady()
        val platform = platformOf(url)
        val cookies = cookieFile(platform)
        val alt = platform == Platform.YOUTUBE && useAltClients
        val first: Exception = try {
            return fetchInfoWith(url, platform, cookies, alt, titleHint, thumbHint)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
        if (platform != Platform.YOUTUBE || !isBotCheck(first.message)) throw first

        // Stage 2: other YouTube player clients (no sign-in needed for most videos).
        if (!alt) {
            try {
                val info = fetchInfoWith(url, platform, cookies, true, titleHint, thumbHint)
                useAltClients = true
                return info
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { }
        }
        // Stage 3: the user's YouTube login from the Browser tab, if they have one.
        if (cookies == null) {
            val forced = cookieFile(platform, force = true)
            if (forced != null) return fetchInfoWith(url, platform, forced, true, titleHint, thumbHint)
        }
        throw first
    }

    fun isBotCheck(raw: String?): Boolean {
        val m = (raw ?: "").lowercase()
        return "sign in to confirm" in m || "not a bot" in m
    }

    private suspend fun fetchInfoWith(
        url: String, platform: Platform, cookies: String?, altClients: Boolean,
        titleHint: String, thumbHint: String
    ): MediaInfo {
        val out = withContext(Dispatchers.IO) {
            val req = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--socket-timeout", "20")
                addOption("--retries", "3")
                if (cookies != null) addOption("--cookies", cookies)
                if (altClients) addYoutubeClients(this)
            }
            YoutubeDL.getInstance().execute(req, INFO_ID) { _, _, _ -> }.out
        }
        val start = out.indexOf('{')
        if (start < 0) error("Could not read this link")
        return parseInfo(url, platform, out.substring(start), titleHint, thumbHint)
    }

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

    private fun parseInfo(url: String, platform: Platform, json: String, titleHint: String, thumbHint: String): MediaInfo {
        val root = JSONObject(json)
        var entry = root
        var count = 1
        val arr = root.optJSONArray("entries")
        if (arr != null) {
            var first: JSONObject? = null
            var n = 0
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (first == null) first = o
                n++
            }
            if (first != null) { entry = first; count = n }
        }

        val title = entry.str("title").ifBlank { root.str("title") }.ifBlank { titleHint }
        val uploader = listOf("uploader", "channel", "creator", "uploader_id")
            .map { entry.str(it) }.firstOrNull { it.isNotBlank() && it != "NA" } ?: ""
        var thumb = entry.str("thumbnail")
        if (thumb.isBlank()) {
            val ts = entry.optJSONArray("thumbnails")
            if (ts != null && ts.length() > 0) thumb = ts.optJSONObject(ts.length() - 1)?.str("url") ?: ""
        }
        if (thumb.isBlank()) thumb = thumbHint
        var duration = entry.optDouble("duration", 0.0)
        if (duration.isNaN()) duration = 0.0

        return MediaInfo(url, platform, title, uploader, thumb, duration.toLong(), buildOptions(entry, duration), count)
    }

    private fun estimate(f: JSONObject, durationSec: Double): Long {
        val exact = f.optLong("filesize", 0L)
        if (exact > 0) return exact
        val approx = f.optLong("filesize_approx", 0L)
        if (approx > 0) return approx
        val tbr = f.optDouble("tbr", 0.0)
        return if (!tbr.isNaN() && tbr > 0 && durationSec > 0) (tbr * durationSec * 125).toLong() else 0L
    }

    private fun heightLabel(h: Int) = when {
        h >= 2160 -> "4K (${h}p)"
        h >= 1440 -> "2K (${h}p)"
        h >= 1080 -> "Full HD (${h}p)"
        h >= 720 -> "HD (${h}p)"
        else -> "${h}p"
    }

    private fun buildOptions(entry: JSONObject, duration: Double): List<QualityOption> {
        val videos = mutableListOf<JSONObject>()
        val audios = mutableListOf<JSONObject>()
        val fm = entry.optJSONArray("formats")
        if (fm != null) {
            for (i in 0 until fm.length()) {
                val f = fm.optJSONObject(i) ?: continue
                val vc = f.str("vcodec")
                if (vc == "none") {
                    if (f.str("acodec") != "none") audios += f
                } else if (f.optInt("height", 0) > 0) {
                    videos += f
                }
            }
        }
        val bestAudio = audios.maxByOrNull { it.optDouble("tbr", 0.0) }
        val audioBytes = if (bestAudio != null) estimate(bestAudio, duration) else 0L

        val heights = videos.map { it.optInt("height", 0) }.filter { it >= 144 }
            .distinct().sortedDescending().take(6)
        val perHeight = heights.map { h ->
            val best = videos.filter { it.optInt("height", 0) == h }.maxByOrNull { it.optDouble("tbr", 0.0) }!!
            var bytes = estimate(best, duration)
            if (bytes > 0 && best.str("acodec") == "none") bytes += audioBytes
            QualityOption(heightLabel(h), h, bytes)
        }

        val list = mutableListOf<QualityOption>()
        list += QualityOption("Best available", 0, perHeight.firstOrNull()?.bytes ?: 0L)
        list += perHeight
        list += QualityOption("MP3 audio", -1, audioBytes)
        return list
    }

    // ------------------------------------------------------------ errors
    fun friendlyError(raw: String?): String {
        val m = (raw ?: "").lowercase()
        return when {
            "sign in to confirm" in m || "not a bot" in m ->
                "YouTube is asking you to sign in before it will share this video. Tap \u201CSign in to YouTube\u201D, log in, then come back and try again."
            "login required" in m || "log in" in m || "private video" in m || "this video is private" in m ||
                "cookies" in m || "rate-limit reached" in m || "sign in" in m ->
                "This video needs a login. Sign in to the site in the Browser tab, then try again."
            "unsupported url" in m -> "That link doesn\u2019t point to a video page."
            "not available in your country" in m || "geo" in m && "restrict" in m ->
                "This video is blocked in your region."
            "429" in m -> "Too many requests. Wait a minute and try again."
            "no space left" in m -> "Your phone is out of storage space."
            "unable to resolve" in m || "network is unreachable" in m || "timed out" in m ||
                "connection reset" in m || "name or service not known" in m ->
                "No internet connection. Check your network and retry."
            "unavailable" in m || "removed" in m || "does not exist" in m || "no video could be found" in m ||
                "no video formats" in m ->
                "This video is unavailable or has been removed."
            else -> {
                val line = (raw ?: "").lineSequence().map { it.trim() }.lastOrNull { it.isNotBlank() }
                    ?.removePrefix("ERROR:")?.trim()
                line?.take(160)?.ifBlank { null } ?: "Something went wrong. Please retry."
            }
        }
    }

    /** Errors that usually mean the bundled yt-dlp is out of date for that site. */
    fun looksBroken(raw: String?): Boolean {
        val m = (raw ?: "").lowercase()
        return listOf(
            "unable to extract", "failed to extract", "http error 403", "nsig", "sig function",
            "player response", "requested format is not available", "unable to download video data",
            "extractorerror", "unable to download webpage"
        ).any { it in m }
    }
}
