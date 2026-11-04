package com.toonstream.app.media.engine

import com.toonstream.app.media.extractor.*

object ExtractorRegistry {

    private val extractors = mutableListOf<VideoExtractor>()
    private val fallbackExtractor = GenericHtml5Extractor()

    init {
        // Register standard and authorized adapters
        register(DirectMediaExtractor())
        register(VidmolyExtractor())
        register(RubyStmExtractor())
        register(FilesForeverExtractor())
        register(VoeExtractor())
    }

    fun register(extractor: VideoExtractor) {
        if (!extractors.contains(extractor)) {
            extractors.add(0, extractor) // New ones have priority
        }
    }

    fun getExtractor(url: String): VideoExtractor {
        return extractors.firstOrNull { it.canHandle(url) } ?: fallbackExtractor
    }

    fun getAllExtractors(): List<VideoExtractor> = extractors.toList()
}
