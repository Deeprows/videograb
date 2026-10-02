package com.example.videograb

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchResult(val id: String, val title: String, val channel: String, val duration: String) {
    val url get() = "https://www.youtube.com/watch?v=$id"
    val thumb get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
}

/** Home-screen discover tabs (label to YouTube search query). */
val FeedCategories = listOf(
    "Trending" to "trending videos today",
    "Music" to "top music videos",
    "Movies" to "official movie trailers",
    "Gaming" to "gaming highlights",
    "Comedy" to "funny videos",
    "News" to "latest news",
    "Sports" to "sports highlights",
)

class SearchViewModel : ViewModel() {
    var results by mutableStateOf<List<SearchResult>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var category by mutableIntStateOf(0)       // -1 while showing a typed search
    var heading by mutableStateOf("")

    private var job: Job? = null
    private var gen = 0

    // Feed cache: switching tabs or returning Home doesn't hit YouTube again for 30 minutes.
    private val cache = HashMap<String, Pair<Long, List<SearchResult>>>()
    private val TTL = 30L * 60 * 1000

    fun loadCategory(i: Int, force: Boolean = false) {
        category = i
        val (label, query) = FeedCategories[i]
        val hit = cache[query]
        if (!force && hit != null && System.currentTimeMillis() - hit.first < TTL) {
            job?.cancel(); gen++
            loading = false; error = null; heading = label; results = hit.second
            return
        }
        run(query, label)
    }

    fun search(q: String) {
        val t = q.trim()
        if (t.isBlank()) return
        category = -1
        run(t, "Results for \u201C$t\u201D")
    }

    fun refresh() {
        if (category >= 0) loadCategory(category, force = true) else loadCategory(0, force = true)
    }

    private fun run(query: String, title: String) {
        val my = ++gen
        job?.cancel()
        loading = true
        error = null
        heading = title
        job = viewModelScope.launch {
            try {
                Engine.awaitReady()
                val cookies = Engine.cookieFile(Platform.YOUTUBE)
                val found = withContext(Dispatchers.IO) {
                    val req = YoutubeDLRequest("ytsearch15:$query").apply {
                        addOption("--flat-playlist")
                        addOption("--no-warnings")
                        addOption("--socket-timeout", "15")
                        if (cookies != null) addOption("--cookies", cookies)
                        addOption("--print", "%(id)s;;;%(title)s;;;%(uploader,channel)s;;;%(duration_string)s")
                    }
                    YoutubeDL.getInstance().execute(req).out.lines().mapNotNull { line ->
                        val p = line.split(";;;")
                        if (p.size >= 2 && p[0].isNotBlank())
                            SearchResult(
                                p[0].trim(), p[1],
                                p.getOrElse(2) { "" }.takeIf { it != "NA" } ?: "",
                                p.getOrElse(3) { "" }.takeIf { it != "NA" } ?: ""
                            )
                        else null
                    }
                }
                if (my != gen) return@launch
                results = found
                if (found.isNotEmpty()) cache[query] = System.currentTimeMillis() to found
                if (found.isEmpty()) error = "No results"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (my == gen) {
                    error = if (Engine.isBotCheck(e.message))
                        "YouTube is limiting requests from your network right now. Signing in to YouTube in the Browser tab usually fixes this. You can still paste links."
                    else Engine.friendlyError(e.message)
                }
            } finally {
                if (my == gen) loading = false
            }
        }
    }
}
