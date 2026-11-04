package com.toonstream.app.media.quality

import com.toonstream.app.media.model.ExtractorLink
import com.toonstream.app.media.model.VideoQuality
import com.toonstream.app.media.model.VideoType

object SourcePriorityManager {

    data class Preferences(
        val preferredQuality: VideoQuality = VideoQuality.Q_1080P,
        val preferredLanguage: String = "Default",
        val preferHls: Boolean = true
    )

    fun sortSources(
        sources: List<ExtractorLink>,
        preferences: Preferences = Preferences()
    ): List<ExtractorLink> {
        return sources
            .distinctBy { it.url.trim() }
            .sortedWith(
                compareByDescending<ExtractorLink> { it.isWorking }
                    .thenByDescending { it.extractorName != "WebEmbed" } // NATIVE FIRST
                    .thenByDescending { link ->
                        // Exact match with preferred quality gets highest bonus
                        if (link.quality == preferences.preferredQuality) 1000
                        else link.quality.height
                    }
                    .thenByDescending { link ->
                        if (preferences.preferHls && link.type == VideoType.HLS) 10 else 0
                    }
                    .thenByDescending { link ->
                        if (link.language.equals(preferences.preferredLanguage, ignoreCase = true)) 20 else 0
                    }
            )
    }

    fun findBestSource(
        sources: List<ExtractorLink>,
        preferences: Preferences = Preferences()
    ): ExtractorLink? {
        val sorted = sortSources(sources, preferences)
        return sorted.firstOrNull { it.isWorking } ?: sorted.firstOrNull()
    }
}
