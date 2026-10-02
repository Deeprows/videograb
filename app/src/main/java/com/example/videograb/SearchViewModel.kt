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

    fun loadCategory(i: Int) {
        category = i
        run(FeedCategories[i].second, FeedCategories[i].first)
    }

    fun search(q: String) {
        val t = q.trim()
        if (t.isBlank()) return
        category = -1
        run(t, "Results for \u201C$t\u201D")
    }

    fun refresh() {
        if (category >= 0) loadCategory(category) else if (heading.isNotBlank()) loadCategory(0)
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
                val found = withContext(Dispatchers.IO) {
                    val req = YoutubeDLRequest("ytsearch15:$query").apply {
                        addOption("--flat-playlist")
                        addOption("--no-warnings")
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
                if (found.isEmpty()) error = "No results"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (my == gen) error = Engine.friendlyError(e.message)
            } finally {
                if (my == gen) loading = false
            }
        }
    }
}
