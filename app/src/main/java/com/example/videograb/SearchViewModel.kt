package com.example.videograb

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchResult(val id: String, val title: String, val channel: String, val duration: String) {
    val url get() = "https://www.youtube.com/watch?v=$id"
    val thumb get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
}

class SearchViewModel : ViewModel() {
    var results by mutableStateOf<List<SearchResult>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun search(q: String) {
        if (q.isBlank() || loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                Engine.awaitReady()
                val found = withContext(Dispatchers.IO) {
                    val req = YoutubeDLRequest("ytsearch12:${q.trim()}").apply {
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
                results = found
                if (found.isEmpty()) error = "No results"
            } catch (e: Exception) {
                error = Engine.friendlyError(e.message)
            } finally {
                loading = false
            }
        }
    }
}
