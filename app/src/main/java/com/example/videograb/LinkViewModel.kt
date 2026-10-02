package com.example.videograb

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface LinkState {
    object Idle : LinkState
    data class Loading(val url: String, val platform: Platform) : LinkState
    data class Ready(val info: MediaInfo) : LinkState
    data class Error(val url: String, val title: String, val message: String) : LinkState
}

class LinkViewModel : ViewModel() {
    var state by mutableStateOf<LinkState>(LinkState.Idle)
        private set

    private var job: Job? = null

    fun open(rawUrl: String, titleHint: String = "", thumbHint: String = "") {
        val url = rawUrl.trim()
        job?.cancel()
        Engine.cancelInfo()
        state = LinkState.Loading(url, platformOf(url))
        job = viewModelScope.launch {
            val result: LinkState = try {
                LinkState.Ready(Engine.fetchInfo(url, titleHint, thumbHint))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LinkState.Error(url, titleHint, Engine.friendlyError(e.message))
            }
            state = result
        }
    }

    fun dismiss() {
        job?.cancel()
        Engine.cancelInfo()
        state = LinkState.Idle
    }
}
