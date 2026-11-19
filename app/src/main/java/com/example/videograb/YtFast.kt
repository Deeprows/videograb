package com.example.videograb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Native YouTube reader. One small HTTPS request returns title, thumbnail, duration and direct
 * stream links, so the watch page starts playing without waiting for yt-dlp.
 * Two YouTube clients are raced (the second one only starts if the first is slow) and results are
 * cached, so repeat taps are instant. If YouTube refuses, the caller falls back to yt-dlp.
 */
object YtFast {
    private const val UA_VR =
        "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    private const val UA_IOS = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
    private val ID = Regex("""(?:[?&]v=|youtu\.be/|/shorts/|/embed/|/live/|/v/)([A-Za-z0-9_-]{11})""")
    private const val CACHE_MS = 20L * 60 * 1000

    private class Client(
        val name: String, val id: String, val version: String, val ua: String,
        val startDelayMs: Long, val extra: JSONObject.() -> Unit,
    )

    private val CLIENTS = listOf(
        Client("ANDROID_VR", "28", "1.60.19", UA_VR, 0L) {
            put("deviceMake", "Oculus"); put("deviceModel", "Quest 3")
            put("androidSdkVersion", 32); put("osName", "Android"); put("osVersion", "12L")
        },
        Client("IOS", "5", "19.45.4", UA_IOS, 1200L) {
            put("deviceMake", "Apple"); put("deviceModel", "iPhone16,2")
            put("osName", "iPhone"); put("osVersion", "18.1.0.22B83")
        },
    )

    private val cache = ConcurrentHashMap<String, Pair<Long, MediaInfo>>()

    fun videoId(url: String): String? = ID.find(url)?.groupValues?.get(1)

    private class F(
        val url: String, val h: Int, val mime: String, val bytes: Long, val br: Long,
        val muxed: Boolean, val audio: Boolean,
    )

    /** Warm the cache for the first few feed videos so tapping one opens instantly. */
    suspend fun prefetch(results: List<SearchResult>) = coroutineScope {
        for (r in results.take(4)) {
            launch {
                try { info(r.id, r.url, r.title, r.thumb) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { }
            }
        }
    }

    suspend fun info(id: String, url: String, titleHint: String, thumbHint: String): MediaInfo {
        cache[id]?.let { (t, i) ->
            if (System.currentTimeMillis() - t < CACHE_MS) return i.copy(url = url)
        }
        val found = coroutineScope {
            val winner = CompletableDeferred<MediaInfo>()
            val failed = AtomicInteger(0)
            for (c in CLIENTS) {
                launch(Dispatchers.IO) {
                    try {
                        if (c.startDelayMs > 0) delay(c.startDelayMs)
                        winner.complete(fetchOne(c, id, url, titleHint, thumbHint))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (failed.incrementAndGet() == CLIENTS.size) winner.completeExceptionally(e)
                    }
                }
            }
            try { winner.await() } finally { coroutineContext.cancelChildren() }
        }
        cache[id] = System.currentTimeMillis() to found
        return found
    }

    private suspend fun fetchOne(c: Client, id: String, url: String, titleHint: String, thumbHint: String): MediaInfo =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("videoId", id)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                put("context", JSONObject().put("client", JSONObject().apply {
                    put("clientName", c.name)
                    put("clientVersion", c.version)
                    put("userAgent", c.ua)
                    put("hl", "en")
                    put("timeZone", "UTC")
                    put("utcOffsetMinutes", 0)
                    c.extra(this)
                }))
            }.toString()

            val conn = (URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", c.ua)
                setRequestProperty("X-YouTube-Client-Name", c.id)
                setRequestProperty("X-YouTube-Client-Version", c.version)
            }
            val text = try {
                conn.outputStream.use { it.write(body.toByteArray()) }
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            parse(JSONObject(text), id, url, titleHint, thumbHint, c.ua)
        }

    private fun parse(root: JSONObject, id: String, url: String, titleHint: String, thumbHint: String, ua: String): MediaInfo {
        val status = root.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
        if (status != "OK") error("YouTube says: $status")
        val vd = root.optJSONObject("videoDetails") ?: error("no video details")
        val sd = root.optJSONObject("streamingData") ?: error("no streams")
        val hls = sd.optString("hlsManifestUrl")

        val all = ArrayList<F>()
        fun read(key: String, muxed: Boolean) {
            val arr = sd.optJSONArray(key) ?: return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val mime = o.optString("mimeType")
                all += F(
                    o.optString("url"), o.optInt("height", 0), mime,
                    o.optString("contentLength", "0").toLongOrNull() ?: 0L,
                    o.optLong("bitrate", 0L), muxed, mime.startsWith("audio/")
                )
            }
        }
        read("formats", true)
        read("adaptiveFormats", false)

        val duration = vd.optString("lengthSeconds", "0").toLongOrNull() ?: 0L
        val bestAudio = all.filter { it.audio && it.url.isNotBlank() }
            .sortedWith(compareByDescending<F> { it.mime.contains("mp4") }.thenByDescending { it.br }).firstOrNull()
        val anyAudio = all.filter { it.audio }.maxByOrNull { it.br }
        val videos = all.filter { !it.audio && it.h > 0 }
        val heights = videos.map { it.h }.filter { it >= 144 }.distinct().sortedDescending().take(6)
        if (heights.isEmpty()) error("no video streams")

        fun size(f: F?): Long = if (f == null) 0L else if (f.bytes > 0) f.bytes else f.br * duration / 8

        val perHeight = heights.map { h ->
            val pick = videos.filter { it.h == h }
                .sortedWith(compareBy<F> { if (it.url.isNotBlank()) 0 else 1 }
                    .thenBy { if (it.muxed) 0 else 1 }
                    .thenBy { if (it.mime.contains("avc1")) 0 else 1 }
                    .thenByDescending { it.br })
                .first()
            var bytes = size(pick)
            if (!pick.muxed) bytes += size(anyAudio)
            val direct = pick.url.isNotBlank()
            QualityOption(
                Engine.heightLabel(h), h, bytes,
                previewUrl = if (direct) pick.url else hls,
                previewAudioUrl = if (direct && !pick.muxed) bestAudio?.url.orEmpty() else "",
                previewUa = ua
            )
        }

        // adaptive HLS (when YouTube offers it) is the most reliable thing to play first
        val best = if (hls.isNotBlank()) QualityOption("Best available", 0, perHeight.first().bytes, hls, "", ua)
        else {
            val p = perHeight.firstOrNull { it.quality <= 720 && it.previewUrl.isNotBlank() }
                ?: perHeight.lastOrNull { it.previewUrl.isNotBlank() }
            QualityOption("Best available", 0, perHeight.first().bytes, p?.previewUrl.orEmpty(), p?.previewAudioUrl.orEmpty(), ua)
        }
        if (best.previewUrl.isBlank()) error("no direct streams")

        val options = mutableListOf<QualityOption>()
        options += best
        options += perHeight
        options += QualityOption("MP3 audio", -1, size(anyAudio), bestAudio?.url.orEmpty(), "", ua)

        val thumbs = vd.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        var thumb = ""
        if (thumbs != null && thumbs.length() > 0) thumb = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
        if (thumb.isBlank()) thumb = thumbHint.ifBlank { "https://i.ytimg.com/vi/$id/hqdefault.jpg" }

        return MediaInfo(
            url, Platform.YOUTUBE,
            vd.optString("title").ifBlank { titleHint },
            vd.optString("author"), thumb, duration, options, 1
        )
    }
}
