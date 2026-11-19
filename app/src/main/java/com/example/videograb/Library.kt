package com.example.videograb

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

data class LibItem(val url: String, val title: String, val thumb: String, val channel: String)

/** Watch history and saved ("favourites") videos, kept on the phone. */
object LibraryStore {
    val history = mutableStateListOf<LibItem>()
    val saved = mutableStateListOf<LibItem>()
    private var ctx: Context? = null

    private fun sp() = ctx?.getSharedPreferences("library", Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        read("history", history)
        read("saved", saved)
    }

    private fun read(key: String, into: MutableList<LibItem>) {
        try {
            val arr = JSONArray(sp()?.getString(key, "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url")
                if (url.isNotBlank()) into += LibItem(url, o.optString("title"), o.optString("thumb"), o.optString("channel"))
            }
        } catch (_: Exception) { }
    }

    private fun write(key: String, list: List<LibItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("url", it.url).put("title", it.title).put("thumb", it.thumb).put("channel", it.channel))
        }
        sp()?.edit()?.putString(key, arr.toString())?.apply()
    }

    fun addHistory(item: LibItem) {
        history.removeAll { it.url == item.url }
        history.add(0, item)
        while (history.size > 80) history.removeAt(history.size - 1)
        write("history", history)
    }

    fun clearHistory() {
        history.clear()
        write("history", history)
    }

    fun isSaved(url: String) = saved.any { it.url == url }

    fun toggleSaved(item: LibItem) {
        if (!saved.removeAll { it.url == item.url }) saved.add(0, item)
        write("saved", saved)
    }
}

@Composable
fun LibraryScreen(onWatch: (url: String, title: String, thumb: String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val list = if (tab == 0) LibraryStore.history else LibraryStore.saved

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Library", style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("History", "Saved").forEachIndexed { i, label ->
                    val selected = tab == i
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (selected) BrandBrush else SolidColor(MaterialTheme.colorScheme.surfaceVariant))
                            .clickable { tab = i }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            label, style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (tab == 0 && list.isNotEmpty()) {
                    TextButton(onClick = { LibraryStore.clearHistory() }) { Text("Clear") }
                }
            }
        }
        if (list.isEmpty()) {
            item {
                Text(
                    if (tab == 0) "Videos you watch will show up here."
                    else "Tap the heart on any video to save it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(list.toList()) { v ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .clickable { onWatch(v.url, v.title, v.thumb) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Thumb(v.thumb, platformOf(v.url), Modifier.width(140.dp).aspectRatio(16f / 9f), 12.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    if (v.channel.isNotBlank()) {
                        Text(
                            v.channel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
