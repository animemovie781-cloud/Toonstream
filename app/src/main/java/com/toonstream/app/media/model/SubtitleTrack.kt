package com.toonstream.app.media.model

enum class SubtitleFormat(val mimeType: String) {
    VTT("text/vtt"),
    WEBVTT("text/vtt"),
    SRT("application/x-subrip"),
    UNKNOWN("text/plain")
}

data class SubtitleTrack(
    val id: String,
    val url: String,
    val language: String,
    val languageCode: String = "en",
    val displayName: String = language,
    val isDefault: Boolean = false,
    val format: SubtitleFormat = SubtitleFormat.VTT
)
