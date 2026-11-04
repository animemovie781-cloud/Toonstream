package com.toonstream.app.media.extractor

import com.toonstream.app.media.engine.ExtractorRegistry
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
        return extractRecursive(url, referer, permittedHeaders, 0)
    }

    private suspend fun extractRecursive(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>,
        depth: Int
    ): List<ExtractorLink> {
        if (depth > 2) return emptyList()

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
            ?: Regex("""sources:\s*\[\{file:\s*["']([^"']+)["']""").find(html)
            ?: Regex("""src:\s*["']([^"']+\.m3u8[^"']*)["']""").find(html)
        if (m3u8Match != null) {
            val directM3u8 = m3u8Match.groupValues[1]
            links.addAll(expandMasterM3u8(directM3u8, name, url, permittedHeaders))
        }

        // 3. If no links found, look for nested iframes that might be the real player
        if (links.isEmpty()) {
            val iframes = doc.select("iframe[src]")
            for (iframe in iframes) {
                var iframeSrc = iframe.attr("abs:src")
                if (iframeSrc.isBlank()) iframeSrc = iframe.attr("data-src")
                if (iframeSrc.isBlank()) iframeSrc = iframe.attr("data-l")
                
                if (iframeSrc.isNotBlank() && !iframeSrc.contains("ads") && !iframeSrc.contains("facebook")) {
                    if (!iframeSrc.startsWith("http")) {
                        iframeSrc = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else "https://toonstream.us$iframeSrc"
                    }
                    
                    // Try to resolve using registry first, then fallback to recursive HTML5
                    val extractor = ExtractorRegistry.getExtractor(iframeSrc)
                    if (extractor !is GenericHtml5Extractor) {
                        links.addAll(extractor.extract(iframeSrc, url, permittedHeaders))
                    } else {
                        links.addAll(extractRecursive(iframeSrc, url, permittedHeaders, depth + 1))
                    }
                }
            }
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
        val rawHtml = fetchHtml(url, headers, referer) ?: return emptyList()
        val html = cleanJsString(rawHtml)

        val links = mutableListOf<ExtractorLink>()
        // Match: file:"https://...master.m3u8" or sources: [{file:"..."}]
        val fileRegex = Regex("""(?:file|sources|src):\s*["']([^"']+\.m3u8[^"']*)["']""")
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
        val rawHtml = fetchHtml(url, headers, referer) ?: return emptyList()
        val html = cleanJsString(rawHtml)

        val links = mutableListOf<ExtractorLink>()
        val m3u8Match = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)
            ?: Regex("""(?:file|sources|src):\s*["']([^"']+\.m3u8[^"']*)["']""").find(html)
            
        if (m3u8Match != null) {
            val streamUrl = m3u8Match.groupValues[1]
            links.addAll(expandMasterM3u8(streamUrl, name, url, headers))
        }

        return links
    }
}

/**
 * FilesForever / DDN Extractor
 */
class FilesForeverExtractor : BaseExtractor() {
    override val name: String = "FilesForever"
    override val supportedDomains: List<String> = listOf("filesforever.link", "ddn.iqsmartgames.com", "iqsmartgames.com")

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val headers = permittedHeaders.toMutableMap()
        headers["Referer"] = referer ?: "https://toonstream.us/"
        val html = fetchHtml(url, headers, "https://toonstream.us/") ?: return emptyList()

        val links = mutableListOf<ExtractorLink>()
        
        // 1. Check for sources array in JS
        val sourcesRegex = Regex("""sources:\s*\[([^\]]+)\]""")
        val sourcesMatch = sourcesRegex.find(html)
        if (sourcesMatch != null) {
            val sourcesPart = sourcesMatch.groupValues[1]
            val fileRegex = Regex("""file:\s*["']([^"']+)["']""")
            for (match in fileRegex.findAll(sourcesPart)) {
                val streamUrl = match.groupValues[1]
                if (streamUrl.contains(".m3u8")) {
                    links.addAll(expandMasterM3u8(streamUrl, name, url, headers))
                } else {
                    links.add(createMp4Link(streamUrl, url, headers))
                }
            }
        }

        // 2. Fallback to general regex
        if (links.isEmpty()) {
            val streamRegex = Regex("""["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""")
            for (match in streamRegex.findAll(html)) {
                val streamUrl = match.groupValues[1]
                if (streamUrl.contains(".m3u8")) {
                    links.addAll(expandMasterM3u8(streamUrl, name, url, headers))
                } else if (streamUrl.contains(".mp4")) {
                    links.add(createMp4Link(streamUrl, url, headers))
                }
            }
        }
        
        // 3. Check for packed script or base64 (common in ddn)
        if (links.isEmpty() && html.contains("eval(function(p,a,c,k,e,d)")) {
            // Simplified: look for m3u8 inside the packed string
            val m3u8Match = Regex("""(https?://[^\s"'<>]+?\.m3u8[^\s"'<>]*?)""").find(html)
            if (m3u8Match != null) {
                links.addAll(expandMasterM3u8(m3u8Match.groupValues[1], name, url, headers))
            }
        }

        return links.distinctBy { it.url }
    }

    private fun createMp4Link(url: String, referer: String, headers: Map<String, String>) = ExtractorLink(
        id = url,
        sourceName = "$name (MP4)",
        url = url,
        quality = VideoQuality.Q_720P,
        type = VideoType.MP4,
        referer = referer,
        permittedHeaders = headers,
        extractorName = name
    )
}

/**
 * StreamWish Extractor
 */
class StreamWishExtractor : BaseExtractor() {
    override val name: String = "StreamWish"
    override val supportedDomains: List<String> = listOf("streamwish.to", "strwish.com", "wishembed.com", "streamwish.com")

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val rawHtml = fetchHtml(url, permittedHeaders, referer) ?: return emptyList()
        val html = cleanJsString(rawHtml)
        val m3u8Match = Regex("""sources:\s*\[\{file:\s*["']([^"']+)["']""").find(html)
            ?: Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)
            ?: Regex("""(?:file|sources|src):\s*["']([^"']+\.m3u8[^"']*)["']""").find(html)

        return if (m3u8Match != null) {
            expandMasterM3u8(m3u8Match.groupValues[1], name, url, permittedHeaders)
        } else emptyList()
    }
}

/**
 * FileMoon Extractor
 */
class FileMoonExtractor : BaseExtractor() {
    override val name: String = "FileMoon"
    override val supportedDomains: List<String> = listOf("filemoon.sx", "filemoon.to", "filemoon.com")

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val rawHtml = fetchHtml(url, permittedHeaders, referer) ?: return emptyList()
        val html = cleanJsString(rawHtml)
        val m3u8Match = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)
            ?: Regex("""(?:file|sources|src):\s*["']([^"']+\.m3u8[^"']*)["']""").find(html)
        
        return if (m3u8Match != null) {
            expandMasterM3u8(m3u8Match.groupValues[1], name, url, permittedHeaders)
        } else emptyList()
    }
}

/**
 * Vidhide Extractor
 */
class VidhideExtractor : BaseExtractor() {
    override val name: String = "Vidhide"
    override val supportedDomains: List<String> = listOf("vidhide.com", "vidhide.to", "vidhidepro.com", "vidhidepre.com")

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val rawHtml = fetchHtml(url, permittedHeaders, referer) ?: return emptyList()
        val html = cleanJsString(rawHtml)
        // Vidhide often uses packed scripts or direct sources array
        val m3u8Match = Regex("""sources:\s*\[\{file:\s*["']([^"']+)["']""").find(html)
            ?: Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)
            ?: Regex("""(?:file|sources|src):\s*["']([^"']+\.m3u8[^"']*)["']""").find(html)

        return if (m3u8Match != null) {
            expandMasterM3u8(m3u8Match.groupValues[1], name, url, permittedHeaders)
        } else emptyList()
    }
}

/**
 * Abyss / Vidara / Vidsrc Extractor
 */
class AbyssExtractor : BaseExtractor() {
    override val name: String = "Abyss"
    override val supportedDomains: List<String> = listOf("abyssplayer.com", "vidara.to", "vidsrc.me", "vidsrc.to", "vidsrc.net")

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val rawHtml = fetchHtml(url, permittedHeaders, referer) ?: return emptyList()
        val html = cleanJsString(rawHtml)
        val links = mutableListOf<ExtractorLink>()
        
        // 1. sources: [{file: "..."}]
        val fileRegex = Regex("""(?:file|sources|src):\s*["']([^"']+)["']""")
        val fileMatch = fileRegex.find(html)
        if (fileMatch != null) {
            val streamUrl = fileMatch.groupValues[1]
            if (streamUrl.contains(".m3u8")) {
                links.addAll(expandMasterM3u8(streamUrl, name, url, permittedHeaders))
            } else if (streamUrl.contains(".mp4")) {
                links.add(
                    ExtractorLink(
                        id = streamUrl,
                        sourceName = name,
                        url = streamUrl,
                        quality = VideoQuality.Q_1080P,
                        type = VideoType.MP4,
                        referer = url,
                        extractorName = name
                    )
                )
            }
        }
        
        // 2. Fallback to general m3u8 search
        if (links.isEmpty()) {
            val m3u8Match = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)
            if (m3u8Match != null) {
                links.addAll(expandMasterM3u8(m3u8Match.groupValues[1], name, url, permittedHeaders))
            }
        }
        
        return links
    }
}

/**
 * Voe Extractor
 */
class VoeExtractor : BaseExtractor() {
    override val name: String = "Voe"
    override val supportedDomains: List<String> = listOf("voe.sx", "voe-network.net", "repackers.xyz", "voe.un")

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val rawHtml = fetchHtml(url, permittedHeaders, referer) ?: return emptyList()
        val html = cleanJsString(rawHtml)
        val links = mutableListOf<ExtractorLink>()

        val hlsMatch = Regex("""'hls':\s*'([^']+)'""").find(html)
            ?: Regex("""'mp4':\s*'([^']+)'""").find(html)
            ?: Regex("""window\.location\.href\s*=\s*['"](https?://[^'"]+)['"]""").find(html)
            ?: Regex("""sources:\s*\[\{file:\s*["']([^"']+)["']""").find(html)

        if (hlsMatch != null) {
            val directUrl = hlsMatch.groupValues[1]
            if (directUrl.contains(".m3u8")) {
                links.addAll(expandMasterM3u8(directUrl, name, url, permittedHeaders))
            } else if (directUrl.contains(".mp4")) {
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

/**
 * DoodStream Extractor
 */
class DoodExtractor : BaseExtractor() {
    override val name: String = "DoodStream"
    override val supportedDomains: List<String> = listOf("dood.to", "dood.so", "dood.wf", "dood.la", "dood.li", "doodstream.com", "ds2play.com")

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val html = fetchHtml(url, permittedHeaders, referer) ?: return emptyList()
        val links = mutableListOf<ExtractorLink>()
        
        // Dood is complex, usually needs a secondary fetch for the token
        // But some pages have direct m3u8 in script
        val m3u8Match = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)
        if (m3u8Match != null) {
            links.addAll(expandMasterM3u8(m3u8Match.groupValues[1], name, url, permittedHeaders))
        }

        return links
    }
}

/**
 * StreamTape Extractor
 */
class StreamTapeExtractor : BaseExtractor() {
    override val name: String = "StreamTape"
    override val supportedDomains: List<String> = listOf("streamtape.com", "streamtape.to", "streamtape.net", "stape.me")

    override suspend fun extract(
        url: String,
        referer: String?,
        permittedHeaders: Map<String, String>
    ): List<ExtractorLink> {
        val html = fetchHtml(url, permittedHeaders, referer) ?: return emptyList()
        val links = mutableListOf<ExtractorLink>()
        
        val mp4Match = Regex("""id=["']robotlink["']>([^<]+)""").find(html)
            ?: Regex("""["'](https?://streamtape\.com/get_video\?id=[^"']+)["']""").find(html)
            
        if (mp4Match != null) {
            var streamUrl = mp4Match.groupValues[1]
            if (!streamUrl.startsWith("http")) streamUrl = "https:$streamUrl"
            links.add(
                ExtractorLink(
                    id = streamUrl,
                    sourceName = name,
                    url = streamUrl,
                    quality = VideoQuality.Q_1080P,
                    type = VideoType.MP4,
                    referer = url,
                    extractorName = name
                )
            )
        }
        return links
    }
}
