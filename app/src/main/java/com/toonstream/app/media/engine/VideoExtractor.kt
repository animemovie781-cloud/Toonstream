package com.toonstream.app.media.engine

import com.toonstream.app.media.model.ExtractorLink
import com.toonstream.app.media.model.SubtitleTrack

interface VideoExtractor {
    val name: String
    val supportedDomains: List<String>
    val supportsSubtitles: Boolean get() = false

    fun canHandle(url: String): Boolean {
        val clean = url.lowercase()
        return supportedDomains.any { clean.contains(it.lowercase()) }
    }

    suspend fun extract(
        url: String,
        referer: String? = null,
        permittedHeaders: Map<String, String> = emptyMap()
    ): List<ExtractorLink>

    suspend fun extractSubtitles(url: String): List<SubtitleTrack> = emptyList()
}
