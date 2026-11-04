package com.toonstream.app.media.subtitle

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.toonstream.app.media.model.SubtitleFormat
import com.toonstream.app.media.model.SubtitleTrack
import org.jsoup.nodes.Document

object SubtitleCollector {

    fun detectFormat(url: String): SubtitleFormat {
        val clean = url.substringBefore("?").lowercase()
        return when {
            clean.endsWith(".vtt") -> SubtitleFormat.VTT
            clean.endsWith(".webvtt") -> SubtitleFormat.WEBVTT
            clean.endsWith(".srt") -> SubtitleFormat.SRT
            else -> SubtitleFormat.VTT
        }
    }

    fun extractFromHtml(doc: Document, baseUrl: String): List<SubtitleTrack> {
        val tracks = mutableListOf<SubtitleTrack>()
        val trackElements = doc.select("track[src]")

        for ((index, track) in trackElements.withIndex()) {
            val src = track.attr("src")
            if (src.isBlank()) continue

            val fullUrl = if (src.startsWith("http://") || src.startsWith("https://")) {
                src
            } else if (src.startsWith("/")) {
                val host = baseUrl.substringBefore("/", "")
                "$host$src"
            } else {
                "${baseUrl.substringBeforeLast("/")}/$src"
            }

            val label = track.attr("label").takeIf { it.isNotBlank() }
                ?: track.attr("srclang").takeIf { it.isNotBlank() }
                ?: "Subtitle ${index + 1}"

            val langCode = track.attr("srclang").takeIf { it.isNotBlank() } ?: "en"
            val isDefault = track.hasAttr("default")
            val format = detectFormat(fullUrl)

            tracks.add(
                SubtitleTrack(
                    id = "sub_$index",
                    url = fullUrl,
                    language = label,
                    languageCode = langCode,
                    displayName = label,
                    isDefault = isDefault,
                    format = format
                )
            )
        }
        return tracks
    }

    fun toMedia3SubtitleConfig(track: SubtitleTrack): MediaItem.SubtitleConfiguration {
        val mimeType = track.format.mimeType
        val selectionFlags = if (track.isDefault) C.SELECTION_FLAG_DEFAULT else 0

        return MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
            .setMimeType(mimeType)
            .setLanguage(track.languageCode)
            .setLabel(track.displayName)
            .setSelectionFlags(selectionFlags)
            .build()
    }
}
