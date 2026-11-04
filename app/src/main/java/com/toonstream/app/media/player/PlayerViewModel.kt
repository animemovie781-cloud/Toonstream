package com.toonstream.app.media.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toonstream.app.data.local.AppDatabase
import com.toonstream.app.data.local.WatchHistoryEntity
import com.toonstream.app.domain.model.ServerOption
import com.toonstream.app.media.engine.LoadLinksEngine
import com.toonstream.app.media.model.ExtractorLink
import com.toonstream.app.media.model.PlaybackResult
import com.toonstream.app.media.model.SourceResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _progressMessage = MutableStateFlow("Initializing engine...")
    val progressMessage: StateFlow<String> = _progressMessage

    private val _playbackResult = MutableStateFlow<PlaybackResult?>(null)
    val playbackResult: StateFlow<PlaybackResult?> = _playbackResult

    private val _currentLink = MutableStateFlow<ExtractorLink?>(null)
    val currentLink: StateFlow<ExtractorLink?> = _currentLink

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var currentPositionMs: Long = 0L

    fun loadSources(
        pageUrl: String,
        contentTitle: String,
        episodeTitle: String? = null,
        preParsedServers: List<ServerOption> = emptyList()
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            LoadLinksEngine.loadLinksFlow(pageUrl, contentTitle, episodeTitle, preParsedServers)
                .collectLatest { result ->
                    when (result) {
                        is SourceResult.Loading -> {
                            _isLoading.value = true
                            _progressMessage.value = result.progressMessage
                        }
                        is SourceResult.Success -> {
                            _isLoading.value = false
                            _playbackResult.value = result.data
                            val best = result.data.preferredSource ?: result.data.sources.firstOrNull()
                            _currentLink.value = best
                        }
                        is SourceResult.Error -> {
                            _isLoading.value = false
                            _errorMessage.value = result.message
                        }
                        is SourceResult.Unsupported -> {
                            _isLoading.value = false
                            _errorMessage.value = result.reason
                        }
                    }
                }
        }
    }

    fun selectSource(link: ExtractorLink) {
        _currentLink.value = link
    }

    fun updatePlaybackPosition(positionMs: Long, durationMs: Long, pageUrl: String, title: String, posterUrl: String) {
        currentPositionMs = positionMs
        viewModelScope.launch {
            if (positionMs > 5000) { // save after 5 seconds
                db.watchHistoryDao().recordProgress(
                    WatchHistoryEntity(
                        id = pageUrl,
                        seriesOrMovieId = pageUrl,
                        title = title,
                        episodeTitle = title,
                        posterUrl = posterUrl,
                        positionMs = positionMs,
                        durationMs = durationMs
                    )
                )
            }
        }
    }

    suspend fun getSavedPosition(pageUrl: String): Long {
        return db.watchHistoryDao().getEntry(pageUrl)?.positionMs ?: 0L
    }
}
