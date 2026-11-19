package com.example.videograb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Native YouTube reader. One small HTTPS request returns title, thumbnail, duration and direct
 * stream links, so the quality sheet opens (and the preview plays) without starting yt-dlp.
 * If YouTube refuses (age gate, login, etc.) the caller falls back to yt-dlp.
 */
object YtFast {
    private const val VER = "1.60.19"
    private const val UA =
        "com.google.android.apps.youtube.vr.oculus/$VER (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    private val ID = Regex("""(?:[?&]v=|youtu\.be/|/shorts/|/embed/|/live/|/v/)([A-Za-z0-9_-]{11})""")

    fun videoId(url: String): String? = ID.find(url)?.groupValues?.get(1)

    private class F(
        val url: String, val h: Int, val mime: String, val bytes: Long, val br: Long,
        val muxed: Boolean, val audio: Boolean,
    )

    suspend fun info(id: String, url: String, titleHint: String, thumbHint: String): MediaInfo =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("videoId", id)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                put("context", JSONObject().put("client", JSONObject().apply {
                    put("clientName", "ANDROID_VR")
                    put("clientVersion", VER)
                    put("deviceMake", "Oculus")
                    put("deviceModel", "Quest 3")
                    put("androidSdkVersion", 32)
                    put("userAgent", UA)
                    put("osName", "Android")
                    put("osVersion", "12L")
                    put("hl", "en")
                    put("timeZone", "UTC")
                    put("utcOffsetMinutes", 0)
                }))
            }.toString()

            val conn = (URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 12_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", UA)
                setRequestProperty("X-YouTube-Client-Name", "28")
                setRequestProperty("X-YouTube-Client-Version", VER)
            }
            val text = try {
                conn.outputStream.use { it.write(body.toByteArray()) }
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            parse(JSONObject(text), id, url, titleHint, thumbHint)
        }

    private fun parse(root: JSONObject, id: String, url: String, titleHint: String, thumbHint: String): MediaInfo {
        val status = root.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
        if (status != "OK") error("YouTube says: $status")
        val vd = root.optJSONObject("videoDetails") ?: error("no video details")
        val sd = root.optJSONObject("streamingData") ?: error("no streams")

        val all = ArrayList<F>()
        fun read(key: String, muxed: Boolean) {
            val arr = sd.optJSONArray(key) ?: return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val u = o.optString("url")
                if (u.isBlank()) continue                 // ciphered stream: skip
                val mime = o.optString("mimeType")
                all += F(
                    u, o.optInt("height", 0), mime,
                    o.optString("contentLength", "0").toLongOrNull() ?: 0L,
                    o.optLong("bitrate", 0L), muxed, mime.startsWith("audio/")
                )
            }
        }
        read("formats", true)
        read("adaptiveFormats", false)

        val duration = vd.optString("lengthSeconds", "0").toLongOrNull() ?: 0L
        val audios = all.filter { it.audio }
        val bestAudio = audios.sortedWith(compareByDescending<F> { it.mime.contains("mp4") }.thenByDescending { it.br }).firstOrNull()
        val videos = all.filter { !it.audio && it.h > 0 }
        val heights = videos.map { it.h }.filter { it >= 144 }.distinct().sortedDescending().take(6)
        if (heights.isEmpty()) error("no playable video streams")

        val perHeight = heights.map { h ->
            val pick = videos.filter { it.h == h }
                .sortedWith(compareBy<F> { if (it.muxed) 0 else 1 }
                    .thenBy { if (it.mime.contains("avc1")) 0 else 1 }
                    .thenByDescending { it.br })
                .first()
            var bytes = if (pick.bytes > 0) pick.bytes else (pick.br * duration / 8)
            if (!pick.muxed) bytes += bestAudio?.let { if (it.bytes > 0) it.bytes else it.br * duration / 8 } ?: 0L
            QualityOption(
                Engine.heightLabel(h), h, bytes,
                previewUrl = pick.url,
                previewAudioUrl = if (pick.muxed) "" else bestAudio?.url.orEmpty(),
                previewUa = UA
            )
        }
        val bestPreview = perHeight.firstOrNull { it.quality <= 720 } ?: perHeight.last()
        val audioBytes = bestAudio?.let { if (it.bytes > 0) it.bytes else it.br * duration / 8 } ?: 0L

        val options = mutableListOf<QualityOption>()
        options += QualityOption(
            "Best available", 0, perHeight.first().bytes,
            bestPreview.previewUrl, bestPreview.previewAudioUrl, UA
        )
        options += perHeight
        options += QualityOption("MP3 audio", -1, audioBytes, bestAudio?.url.orEmpty(), "", UA)

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
