package com.example.videograb

import android.net.Uri
import androidx.compose.ui.graphics.Color

enum class Platform(
    val label: String,
    val color: Color,
    val glyph: String,
    val home: String,
    val hosts: List<String>,
    val cookieSites: List<Pair<String, String>>, // page url to cookie domain
) {
    YOUTUBE(
        "YouTube", Color(0xFFFF0033), "", "https://m.youtube.com",
        listOf("youtube.com", "youtu.be", "youtube-nocookie.com"),
        listOf("https://www.youtube.com" to "youtube.com")
    ),
    TIKTOK(
        "TikTok", Color(0xFF16161D), "\u266A", "https://www.tiktok.com",
        listOf("tiktok.com"),
        listOf("https://www.tiktok.com" to "tiktok.com")
    ),
    FACEBOOK(
        "Facebook", Color(0xFF1877F2), "f", "https://m.facebook.com",
        listOf("facebook.com", "fb.watch", "fb.com"),
        listOf("https://www.facebook.com" to "facebook.com")
    ),
    TWITTER(
        "X", Color(0xFF0F0F10), "X", "https://x.com",
        listOf("twitter.com", "x.com", "t.co"),
        listOf("https://x.com" to "x.com", "https://twitter.com" to "twitter.com")
    ),
    DAILYMOTION(
        "Dailymotion", Color(0xFF0066DC), "d", "https://www.dailymotion.com",
        listOf("dailymotion.com", "dai.ly"),
        listOf("https://www.dailymotion.com" to "dailymotion.com")
    ),
    OTHER("Web", Color(0xFF5B5F7A), "\u2197", "", emptyList(), emptyList());
}

fun platformOf(url: String): Platform {
    val host = Uri.parse(url).host?.lowercase() ?: return Platform.OTHER
    return Platform.values().firstOrNull { p -> p.hosts.any { host == it || host.endsWith(".$it") } }
        ?: Platform.OTHER
}

private val URL_RE = Regex("""https?://[^\s<>"']+""")
private val BARE_RE = Regex(
    """(?:www\.|m\.|vm\.|vt\.)?(?:youtube\.com|youtu\.be|tiktok\.com|facebook\.com|fb\.watch|x\.com|twitter\.com|dailymotion\.com|dai\.ly)/[^\s<>"']+"""
)

/** Pulls the first link out of any text (share sheets add captions around the URL). */
fun extractUrl(text: String): String? {
    val raw = URL_RE.find(text)?.value
        ?: BARE_RE.find(text)?.value?.let { "https://$it" }
        ?: return null
    return raw.trimEnd('.', ',', ';', ')', ']', '!', '\u201D')
}

private val VIDEO_PATTERNS = listOf(
    Regex("youtube\\.com/(watch\\?|shorts/|live/|embed/)|youtu\\.be/"),
    Regex("(facebook|fb)\\.com/.*(video|watch|reel|share/[vr]/)|fb\\.watch"),
    Regex("tiktok\\.com/.*video/|(vm|vt)\\.tiktok\\.com|tiktok\\.com/t/"),
    Regex("dailymotion\\.com/video/|dai\\.ly/"),
    Regex("(x|twitter)\\.com/[^/]+/status/\\d+"),
)

fun looksLikeVideo(url: String) = VIDEO_PATTERNS.any { it.containsMatchIn(url) }
