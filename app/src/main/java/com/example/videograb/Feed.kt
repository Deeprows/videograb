package com.example.videograb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fast, engine-free YouTube search used for the Home feed and the search bar.
 * Talks to YouTube's public web endpoint directly, so Trending/Music/etc. load instantly
 * without waiting for yt-dlp. SearchViewModel falls back to yt-dlp if this ever fails.
 */
object Feed {
    private const val ENDPOINT = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("query", query)
            put("context", JSONObject().put("client", JSONObject().apply {
                put("clientName", "WEB")
                put("clientVersion", "2.20250101.00.00")
                put("hl", "en")
                put("gl", "US")
            }))
        }.toString()

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("Origin", "https://www.youtube.com")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val out = ArrayList<SearchResult>()
            val seen = HashSet<String>()
            collect(JSONObject(text), out, seen)
            out.take(20)
        } finally {
            conn.disconnect()
        }
    }

    private fun collect(node: Any?, out: MutableList<SearchResult>, seen: MutableSet<String>) {
        when (node) {
            is JSONObject -> {
                val vr = node.optJSONObject("videoRenderer")
                if (vr != null) parse(vr)?.let { if (seen.add(it.id)) out += it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k == "videoRenderer") continue
                    collect(node.opt(k), out, seen)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out, seen)
        }
    }

    private fun text(o: JSONObject?): String {
        if (o == null) return ""
        o.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = o.optJSONArray("runs") ?: return ""
        return buildString { for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text") ?: "") }
    }

    private fun parse(v: JSONObject): SearchResult? {
        val id = v.optString("videoId")
        if (id.isBlank()) return null
        val title = text(v.optJSONObject("title")).ifBlank { return null }
        val channel = text(v.optJSONObject("ownerText")).ifBlank { text(v.optJSONObject("longBylineText")) }
        val duration = text(v.optJSONObject("lengthText"))   // empty for live streams
        val views = text(v.optJSONObject("shortViewCountText")).ifBlank { text(v.optJSONObject("viewCountText")) }
        val age = text(v.optJSONObject("publishedTimeText"))
        return SearchResult(id, title, channel, duration, views, age)
    }
}
