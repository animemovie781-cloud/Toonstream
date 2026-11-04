package com.toonstream.app.media.extractor

import com.toonstream.app.media.model.ExtractorLink
import com.toonstream.app.media.model.SubtitleTrack
import com.toonstream.app.media.model.VideoQuality
import com.toonstream.app.media.model.VideoType
import com.toonstream.app.media.subtitle.SubtitleCollector
import org.jsoup.Jsoup
import java.util.Base64

/**
 * Handles any direct HLS manifest (.m3u8) or MP4 (.mp4) stream URL
 */
class DirectMediaExtractor : BaseExtractor() {
    override val name: String = "Direct Media"
    override val supportedDomains: List<String> = listOf("m3u8", "mp4", "googlevideo", "fastcdn")

    override fun canHandle(url: String): Boolean {
        val clean = url.substringBefore("?").lowercase()
        return clean.endsWith(".m3u8") || clean.endsWith(".mp4")
    }

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val clean = url.substringBefore("?").lowercase()
        return if (clean.endsWith(".m3u8")) {
            expandMasterM3u8(url, name, referer, permittedHeaders)
        } else {
            listOf(
                ExtractorLink(
                    id = url,
                    sourceName = "Direct MP4",
                    url = url,
                    quality = VideoQuality.Q_1080P,
                    type = VideoType.MP4,
                    referer = referer,
                    permittedHeaders = permittedHeaders,
                    extractorName = name
                )
            )
        }
    }
}

/**
 * Extracts HTML5 video sources and subtitle tracks from standard embed pages
 */
class GenericHtml5Extractor : BaseExtractor() {
    override val name: String = "HTML5 Video"
    override val supportedDomains: List<String> = emptyList()
    override val supportsSubtitles: Boolean = true

    override fun canHandle(url: String): Boolean = true // fallback extractor

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val html = fetchHtml(url, permittedHeaders, referer) ?: return emptyList()
        val doc = Jsoup.parse(html, url)
        val links = mutableListOf<ExtractorLink>()

        // 1. Direct <video src="..."> or <source src="...">
        val videoSources = doc.select("video source[src], video[src]")
        for (srcElem in videoSources) {
            val src = srcElem.attr("abs:src").takeIf { it.isNotBlank() }
                ?: srcElem.attr("src").takeIf { it.isNotBlank() }
                ?: continue

            val type = if (src.contains(".m3u8")) VideoType.HLS else VideoType.MP4
            val qualityLabel = srcElem.attr("size").takeIf { it.isNotBlank() }
                ?: srcElem.attr("label")

            if (type == VideoType.HLS) {
                links.addAll(expandMasterM3u8(src, name, url, permittedHeaders))
            } else {
                links.add(
                    ExtractorLink(
                        id = src,
                        sourceName = "$name [${qualityLabel.ifBlank { "MP4" }}]",
                        url = src,
                        quality = VideoQuality.fromLabel(qualityLabel),
                        type = VideoType.MP4,
                        referer = url,
                        permittedHeaders = permittedHeaders,
                        extractorName = name
                    )
                )
            }
        }

        // 2. Look for regex matches of m3u8 or mp4 in inline script tags
        val m3u8Match = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)
        if (m3u8Match != null) {
            val directM3u8 = m3u8Match.groupValues[1]
            links.addAll(expandMasterM3u8(directM3u8, name, url, permittedHeaders))
        }

        return links.distinctBy { it.url }
    }

    override suspend fun extractSubtitles(url: String): List<SubtitleTrack> {
        val html = fetchHtml(url) ?: return emptyList()
        val doc = Jsoup.parse(html, url)
        return SubtitleCollector.extractFromHtml(doc, url)
    }
}

/**
 * Vidmoly Extractor
 */
class VidmolyExtractor : BaseExtractor() {
    override val name: String = "Vidmoly"
    override val supportedDomains: List<String> = listOf("vidmoly.net", "vidmoly.to", "vidmoly.me")
    override val supportsSubtitles: Boolean = true

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val headers = permittedHeaders.toMutableMap()
        headers["Referer"] = "https://vidmoly.net/"
        val html = fetchHtml(url, headers, referer) ?: return emptyList()

        val links = mutableListOf<ExtractorLink>()
        // Match: file:"https://...master.m3u8" or sources: [{file:"..."}]
        val fileRegex = Regex("""(?:file|sources):\s*["']([^"']+\.m3u8[^"']*)["']""")
        val fileMatch = fileRegex.find(html)
            ?: Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)

        if (fileMatch != null) {
            val streamUrl = fileMatch.groupValues[1]
            links.addAll(expandMasterM3u8(streamUrl, name, url, headers))
        }

        return links
    }
}

/**
 * RubyStm Extractor
 */
class RubyStmExtractor : BaseExtractor() {
    override val name: String = "RubyStm"
    override val supportedDomains: List<String> = listOf("rubystm.com")
    override val supportsSubtitles: Boolean = true

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val headers = permittedHeaders.toMutableMap()
        headers["Referer"] = referer ?: "https://toonstream.us/"
        val html = fetchHtml(url, headers, referer) ?: return emptyList()

        val links = mutableListOf<ExtractorLink>()
        val m3u8Match = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)
        if (m3u8Match != null) {
            val streamUrl = m3u8Match.groupValues[1]
            links.addAll(expandMasterM3u8(streamUrl, name, url, headers))
        }

        return links
    }
}

/**
 * FilesForever Extractor
 */
class FilesForeverExtractor : BaseExtractor() {
    override val name: String = "FilesForever"
    override val supportedDomains: List<String> = listOf("filesforever.link", "ddn.iqsmartgames.com")

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val headers = permittedHeaders.toMutableMap()
        headers["Referer"] = "https://toonstream.us/"
        val html = fetchHtml(url, headers, "https://toonstream.us/") ?: return emptyList()

        val links = mutableListOf<ExtractorLink>()
        val streamRegex = Regex("""["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""")
        for (match in streamRegex.findAll(html)) {
            val streamUrl = match.groupValues[1]
            if (streamUrl.contains(".m3u8")) {
                links.addAll(expandMasterM3u8(streamUrl, name, url, headers))
            } else if (streamUrl.contains(".mp4")) {
                links.add(
                    ExtractorLink(
                        id = streamUrl,
                        sourceName = "$name (MP4)",
                        url = streamUrl,
                        quality = VideoQuality.Q_720P,
                        type = VideoType.MP4,
                        referer = url,
                        permittedHeaders = headers,
                        extractorName = name
                    )
                )
            }
        }
        return links.distinctBy { it.url }
    }
}

/**
 * Voe Extractor
 */
class VoeExtractor : BaseExtractor() {
    override val name: String = "Voe"
    override val supportedDomains: List<String> = listOf("voe.sx", "voe-network.net", "repackers.xyz")

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val html = fetchHtml(url, permittedHeaders, referer) ?: return emptyList()
        val links = mutableListOf<ExtractorLink>()

        // 1. Direct 'hls': 'https://...'
        val hlsMatch = Regex("""'hls':\s*'([^']+)'""").find(html)
            ?: Regex("""'mp4':\s*'([^']+)'""").find(html)

        if (hlsMatch != null) {
            val directUrl = hlsMatch.groupValues[1]
            if (directUrl.contains(".m3u8")) {
                links.addAll(expandMasterM3u8(directUrl, name, url, permittedHeaders))
            } else {
                links.add(
                    ExtractorLink(
                        id = directUrl,
                        sourceName = name,
                        url = directUrl,
                        quality = VideoQuality.Q_1080P,
                        type = VideoType.MP4,
                        referer = url,
                        extractorName = name
                    )
                )
            }
        }
        return links
    }
}
