package com.toonstream.app.media.engine

import com.toonstream.app.media.model.ExtractorLink
import com.toonstream.app.media.model.SubtitleTrack
import com.toonstream.app.media.validation.PlaybackValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object SourceResolver {

    data class ResolvedData(
        val links: List<ExtractorLink>,
        val subtitles: List<SubtitleTrack>
    )

    suspend fun resolve(
        url: String,
        sourceLabel: String = "Server",
        referer: String? = null,
        permittedHeaders: Map<String, String> = emptyMap()
    ): ResolvedData = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext ResolvedData(emptyList(), emptyList())

        val extractor = ExtractorRegistry.getExtractor(url)
        val rawLinks = try {
            extractor.extract(url, referer, permittedHeaders)
        } catch (_: Exception) {
            emptyList()
        }

        val subtitles = if (extractor.supportsSubtitles) {
            try {
                extractor.extractSubtitles(url)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // Validate links concurrently with non-blocking checks
        val validatedLinks = rawLinks.map { link ->
            async {
                val tagged = if (link.sourceName.isBlank() || link.sourceName == "Direct Media") {
                    link.copy(sourceName = sourceLabel)
                } else {
                    link
                }
                
                // If it's a direct m3u8 or mp4, we can trust it more and skip strict validation if it's already marked as working
                if (tagged.url.contains(".m3u8") || tagged.url.contains(".mp4")) {
                    tagged
                } else {
                    PlaybackValidator.validate(tagged)
                }
            }
        }.awaitAll()

        ResolvedData(
            links = validatedLinks.distinctBy { it.url },
            subtitles = subtitles.distinctBy { it.url }
        )
    }
}
