package com.toonstream.app.media.engine

import com.toonstream.app.data.parser.ToonStreamParser
import com.toonstream.app.data.repository.ToonStreamRepository
import com.toonstream.app.domain.model.ServerOption
import com.toonstream.app.media.model.*
import com.toonstream.app.media.quality.SourcePriorityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

object LoadLinksEngine {

    private val repository = ToonStreamRepository()

    // In-memory cache for resolved links with TTL (15 minutes)
    private data class CacheEntry(
        val timestamp: Long,
        val result: PlaybackResult
    )

    private val linkCache = mutableMapOf<String, CacheEntry>()
    private const val CACHE_TTL_MS = 15 * 60 * 1000L

    /**
     * Resolves all available video links and subtitles for a given episode or movie
     */
    fun loadLinksFlow(
        pageUrl: String,
        contentTitle: String,
        episodeTitle: String? = null,
        preParsedServers: List<ServerOption> = emptyList()
    ): Flow<SourceResult<PlaybackResult>> = flow {
        emit(SourceResult.Loading("Checking cache for playback links..."))

        // 1. Check cache
        val cached = linkCache[pageUrl]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
            emit(SourceResult.Success(cached.result))
            return@flow
        }

        emit(SourceResult.Loading("Fetching available video servers..."))

        // 2. Identify available server options
        val servers = if (preParsedServers.isNotEmpty()) {
            preParsedServers
        } else {
            val detailsResult = repository.getDetails(pageUrl)
            detailsResult.getOrNull()?.servers ?: emptyList()
        }

        if (servers.isEmpty() && !pageUrl.endsWith(".m3u8") && !pageUrl.endsWith(".mp4")) {
            emit(SourceResult.Error("No video servers found for this title."))
            return@flow
        }

        emit(SourceResult.Loading("Resolving stream links from ${servers.size.coerceAtLeast(1)} servers..."))

        val allLinks = mutableListOf<ExtractorLink>()
        val allSubtitles = mutableListOf<SubtitleTrack>()

        // If direct stream URL was passed directly
        if (pageUrl.endsWith(".m3u8") || pageUrl.endsWith(".mp4")) {
            val directData = SourceResolver.resolve(pageUrl, "Direct Stream")
            allLinks.addAll(directData.links)
            allSubtitles.addAll(directData.subtitles)
        }

        // Concurrently resolve all server embed options
        withContext(Dispatchers.IO) {
            val deferredList = servers.map { server ->
                async {
                    val resolved = SourceResolver.resolve(
                        url = server.embedUrl,
                        sourceLabel = "${server.name} [${server.language}]",
                        referer = "https://toonstream.us/"
                    )

                    val directStreamUrl = server.directStreamUrl
                    val directLinks = if (!directStreamUrl.isNullOrBlank()) {
                        SourceResolver.resolve(
                            url = directStreamUrl,
                            sourceLabel = "${server.name} Direct",
                            referer = "https://toonstream.us/"
                        ).links
                    } else {
                        emptyList()
                    }

                    // If extractor couldn't resolve raw M3U8, create a web embed link so playback never fails
                    val finalLinks = if (resolved.links.isEmpty() && directLinks.isEmpty()) {
                        listOf(
                            ExtractorLink(
                                id = server.embedUrl,
                                sourceName = "${server.name} [Embed Web]",
                                url = server.embedUrl,
                                quality = VideoQuality.fromLabel(server.name),
                                type = VideoType.UNKNOWN,
                                referer = "https://toonstream.us/",
                                language = server.language,
                                extractorName = "WebEmbed"
                            )
                        )
                    } else {
                        resolved.links + directLinks
                    }

                    Pair(finalLinks, resolved.subtitles)
                }
            }

            for (deferred in deferredList.awaitAll()) {
                allLinks.addAll(deferred.first)
                allSubtitles.addAll(deferred.second)
            }
        }

        if (allLinks.isEmpty()) {
            emit(SourceResult.Error("Unable to resolve any working video sources."))
            return@flow
        }

        // 3. Priority Sort and Deduplicate
        val sortedLinks = SourcePriorityManager.sortSources(allLinks)
        val preferred = SourcePriorityManager.findBestSource(sortedLinks)

        val result = PlaybackResult(
            contentTitle = contentTitle,
            episodeTitle = episodeTitle,
            pageUrl = pageUrl,
            sources = sortedLinks,
            subtitles = allSubtitles.distinctBy { it.url },
            preferredSource = preferred
        )

        // Save to cache
        linkCache[pageUrl] = CacheEntry(System.currentTimeMillis(), result)

        emit(SourceResult.Success(result))
    }.flowOn(Dispatchers.IO)

    fun clearCache() {
        linkCache.clear()
    }
}
