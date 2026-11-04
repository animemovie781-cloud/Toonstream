package com.toonstream.app.media.model

data class PlaybackResult(
    val contentTitle: String,
    val episodeTitle: String? = null,
    val pageUrl: String,
    val sources: List<ExtractorLink> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val preferredSource: ExtractorLink? = null
)

sealed class SourceResult<out T> {
    data class Loading(val progressMessage: String = "Resolving sources...") : SourceResult<Nothing>()
    data class Success<out T>(val data: T) : SourceResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : SourceResult<Nothing>()
    data class Unsupported(val reason: String) : SourceResult<Nothing>()
}
