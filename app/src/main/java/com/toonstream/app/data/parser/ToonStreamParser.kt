package com.toonstream.app.data.parser

import com.toonstream.app.domain.model.*
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object ToonStreamParser {

    private const val BASE_URL = "https://toonstream.us"

    private fun normalizeUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$BASE_URL$url"
            else -> "$BASE_URL/$url"
        }
    }

    private fun parseArticle(article: Element, defaultType: String = "Anime"): ContentItem? {
        val html = article.html()
        if (html.contains("\${item")) return null

        val linkElem = article.selectFirst("a.lnk-blk") ?: article.selectFirst("a")
        val href = linkElem?.attr("href") ?: return null

        val titleElem = article.selectFirst("h2.entry-title")
            ?: article.selectFirst(".entry-title")
            ?: article.selectFirst("h3")
            ?: article.selectFirst("h5")
        val title = titleElem?.text()?.trim()
            ?: article.selectFirst("img")?.attr("alt")?.trim()
            ?: return null

        if (title.isBlank()) return null

        val imgElem = article.selectFirst("img")
        val rawImg = imgElem?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: imgElem?.attr("src")?.takeIf { it.isNotBlank() }
            ?: ""
        val posterUrl = normalizeUrl(rawImg)

        val seasonLabel = article.selectFirst(".post-ql")?.text()?.trim()
        val epOrYearLabel = article.selectFirst(".year")?.text()?.trim()
        val rating = article.selectFirst(".vote")?.ownText()?.trim()
            ?: article.selectFirst(".vote")?.text()?.replace("TMDB", "")?.trim()

        val type = when {
            href.contains("/movies/") -> "Movie"
            href.contains("/series/") -> "Series"
            href.contains("/episode/") -> "Episode"
            else -> defaultType
        }

        return ContentItem(
            id = href,
            title = title,
            posterUrl = posterUrl,
            type = type,
            rating = rating,
            releaseYear = epOrYearLabel?.takeIf { it.startsWith("20") || it.startsWith("19") },
            seasonLabel = seasonLabel,
            episodeLabel = epOrYearLabel,
            description = null
        )
    }

    fun parseHome(html: String): HomeSections {
        val doc = Jsoup.parse(html)
        val sections = doc.select("section")

        var freshDrop = mutableListOf<ContentItem>()
        var animeSeries = mutableListOf<ContentItem>()
        var cartoons = mutableListOf<ContentItem>()
        var animeMovies = mutableListOf<ContentItem>()
        var animatedMovies = mutableListOf<ContentItem>()
        var randomMovies = mutableListOf<ContentItem>()
        var randomSeries = mutableListOf<ContentItem>()

        for (section in sections) {
            val heading = section.selectFirst("h1, h2, h3, h4")?.text()?.trim() ?: continue
            val articles = section.select("article")
            val items = articles.mapNotNull { parseArticle(it) }

            when {
                heading.contains("Fresh Drop", ignoreCase = true) -> {
                    freshDrop.addAll(items)
                }
                heading.contains("Anime Series", ignoreCase = true) -> {
                    animeSeries.addAll(items)
                }
                heading.contains("Animated Series", ignoreCase = true) -> {
                    cartoons.addAll(items)
                }
                heading.contains("Anime Movies", ignoreCase = true) -> {
                    animeMovies.addAll(items)
                }
                heading.contains("Animated Movies", ignoreCase = true) -> {
                    animatedMovies.addAll(items)
                }
                heading.contains("Random Movies", ignoreCase = true) -> {
                    randomMovies.addAll(items)
                }
                heading.contains("Random series", ignoreCase = true) -> {
                    randomSeries.addAll(items)
                }
            }
        }

        // If specific sections were empty, fallback to extracting all articles
        if (freshDrop.isEmpty() && animeSeries.isEmpty() && animeMovies.isEmpty()) {
            val allArticles = doc.select("article").mapNotNull { parseArticle(it) }
            val (movies, seriesList) = allArticles.partition { it.type == "Movie" }
            animeMovies = movies.toMutableList()
            animeSeries = seriesList.toMutableList()
        }

        // Featured banner from first few items or fresh drop
        val featured = (freshDrop + animeSeries + animeMovies).take(5)

        return HomeSections(
            featured = featured,
            freshDrop = freshDrop,
            animeSeries = if (animeSeries.isNotEmpty()) animeSeries else randomSeries,
            cartoons = cartoons,
            animeMovies = animeMovies,
            animatedMovies = if (animatedMovies.isNotEmpty()) animatedMovies else randomMovies
        )
    }

    fun parseSearch(html: String): List<ContentItem> {
        val doc = Jsoup.parse(html)
        val articles = doc.select("article")
        return articles.mapNotNull { parseArticle(it) }
    }

    fun parseSearchSuggestions(jsonString: String): List<ContentItem> {
        val items = mutableListOf<ContentItem>()
        try {
            val root = JSONObject(jsonString)
            val dataArray = root.optJSONArray("data") ?: return items
            for (i in 0 until dataArray.length()) {
                val obj = dataArray.getJSONObject(i)
                val title = obj.optString("title")
                val type = obj.optString("type")
                val url = obj.optString("url")
                if (title.isNotBlank() && url.isNotBlank()) {
                    items.add(
                        ContentItem(
                            id = url,
                            title = title,
                            posterUrl = "", // suggestions only have title and URL
                            type = if (type.equals("movie", ignoreCase = true)) "Movie" else "Series"
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return items
    }

    fun parseCategory(html: String, categoryName: String): List<ContentItem> {
        val doc = Jsoup.parse(html)
        val articles = doc.select("article")
        return articles.mapNotNull { parseArticle(it, categoryName) }
    }

    fun parseDetails(html: String, pageUrl: String): ContentDetails {
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.selectFirst(".entry-title")?.text()?.trim()
            ?: doc.title().replace("Series - ToonStream", "")
                .replace("- Movie - ToonStream", "")
                .replace("- ToonStream", "")
                .trim()

        val posterElem = doc.selectFirst(".poster img")
            ?: doc.selectFirst(".post-thumbnail img")
            ?: doc.selectFirst("meta[property=og:image]")
        val rawPoster = posterElem?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: posterElem?.attr("src")?.takeIf { it.isNotBlank() }
            ?: posterElem?.attr("content")?.takeIf { it.isNotBlank() }
            ?: ""
        val posterUrl = normalizeUrl(rawPoster)

        val backdropElem = doc.selectFirst(".backdrop img") ?: doc.selectFirst(".h-cover-img")
        val backdropUrl = backdropElem?.attr("src")?.let { normalizeUrl(it) } ?: posterUrl

        val descElem = doc.selectFirst(".description")
            ?: doc.selectFirst(".entry-content")
            ?: doc.selectFirst(".synopsis")
        val description = descElem?.text()?.trim() ?: "No description available."

        val ratingElem = doc.selectFirst(".vote")
        val rating = ratingElem?.text()?.replace("TMDB", "")?.trim()

        val genres = doc.select("a[href*='/category/']").map { it.text().trim() }
            .filter { it.isNotBlank() && !it.equals("Anime", ignoreCase = true) && !it.equals("Movies", ignoreCase = true) }
            .distinct()

        val yearElem = doc.selectFirst(".year") ?: doc.selectFirst(".release-date")
        val releaseYear = yearElem?.text()?.trim()

        val seasons = parseSeasons(doc)
        val episodes = parseEpisodes(doc)
        val servers = parseServers(doc)

        val isMovie = pageUrl.contains("/movies/") || (!pageUrl.contains("/series/") && seasons.isEmpty() && episodes.isEmpty())

        return ContentDetails(
            id = pageUrl,
            title = title,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            type = if (isMovie) "Movie" else "Series",
            description = description,
            rating = rating,
            releaseYear = releaseYear,
            genres = genres,
            seasons = seasons,
            episodes = episodes,
            servers = servers
        )
    }

    private fun parseSeasons(doc: Document): List<SeasonItem> {
        val seasonButtons = doc.select("a.season-btn")
        val seasons = mutableListOf<SeasonItem>()
        for (btn in seasonButtons) {
            val seasonNum = btn.attr("data-season").toIntOrNull() ?: continue
            val dataUrl = btn.attr("data-url")
            val title = btn.text().trim().takeIf { it.isNotBlank() } ?: "Season $seasonNum"
            seasons.add(
                SeasonItem(
                    seasonNumber = seasonNum,
                    title = title,
                    url = normalizeUrl(dataUrl)
                )
            )
        }
        return seasons.sortedBy { it.seasonNumber }
    }

    fun parseSeasonEpisodes(html: String, seasonNum: Int): List<EpisodeItem> {
        val doc = Jsoup.parse(html)
        return parseEpisodes(doc, seasonNum)
    }

    private fun parseEpisodes(doc: Document, fallbackSeason: Int = 1): List<EpisodeItem> {
        val episodeArticles = doc.select("article.episodes")
        val episodes = mutableListOf<EpisodeItem>()

        for (article in episodeArticles) {
            val link = article.selectFirst("a.lnk-blk")?.attr("href")
                ?: article.selectFirst("a")?.attr("href")
                ?: continue

            val titleElem = article.selectFirst("h5.entry-title1")
                ?: article.selectFirst(".entry-title1")
                ?: article.selectFirst("h5")
                ?: article.selectFirst(".entry-title")
            val title = titleElem?.text()?.trim() ?: "Episode"

            val imgElem = article.selectFirst("img")
            val thumbUrl = imgElem?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: imgElem?.attr("src")?.takeIf { it.isNotBlank() }

            // Extract episode and season number from title (e.g. S 1 | E 5 or attack-on-titan-1x5)
            var seasonNumber = fallbackSeason
            var epNumber = episodes.size + 1

            val matchSxEy = Regex("""S\s*(\d+)\s*\|\s*E\s*(\d+)""", RegexOption.IGNORE_CASE).find(title)
            if (matchSxEy != null) {
                seasonNumber = matchSxEy.groupValues[1].toIntOrNull() ?: fallbackSeason
                epNumber = matchSxEy.groupValues[2].toIntOrNull() ?: epNumber
            } else {
                val matchSlug = Regex("""-(\d+)x(\d+)""").find(link)
                if (matchSlug != null) {
                    seasonNumber = matchSlug.groupValues[1].toIntOrNull() ?: fallbackSeason
                    epNumber = matchSlug.groupValues[2].toIntOrNull() ?: epNumber
                }
            }

            episodes.add(
                EpisodeItem(
                    id = normalizeUrl(link),
                    title = title,
                    episodeNumber = epNumber,
                    seasonNumber = seasonNumber,
                    thumbnailUrl = thumbUrl?.let { normalizeUrl(it) },
                    seriesUrl = null
                )
            )
        }

        return episodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
    }

    fun parseServers(doc: Document): List<ServerOption> {
        val servers = mutableListOf<ServerOption>()
        val playerSec = doc.selectFirst("section.player") ?: doc.selectFirst("#aa-options") ?: doc

        // Map button labels: #options-0 -> Server 1 FHD, language
        val btnMap = mutableMapOf<String, Pair<String, String>>()
        val currentLang = playerSec.selectFirst(".btn.rtg.active")?.text()?.trim() ?: "Multi-Audio"

        for (btn in playerSec.select("a.btn[href^='#options-']")) {
            val optId = btn.attr("href").removePrefix("#")
            val serverQuality = btn.selectFirst(".server")?.text()?.trim() ?: ""
            val serverText = btn.text().replace(serverQuality, "").trim()
            val fullName = if (serverQuality.isNotBlank()) "$serverText ($serverQuality)" else serverText
            btnMap[optId] = Pair(fullName, currentLang)
        }

        val videoOptions = playerSec.select("div[id^=options-]")
        for (opt in videoOptions) {
            val optId = opt.id()
            val iframe = opt.selectFirst("iframe") ?: continue
            val src = iframe.attr("src").takeIf { it.isNotBlank() }
                ?: iframe.attr("data-src").takeIf { it.isNotBlank() }
                ?: continue

            val labelInfo = btnMap[optId]
            val serverName = labelInfo?.first?.takeIf { it.isNotBlank() }
                ?: "Server ${optId.replace("options-", "").toIntOrNull()?.plus(1) ?: optId}"
            val lang = labelInfo?.second ?: currentLang

            servers.add(
                ServerOption(
                    id = optId,
                    name = serverName,
                    language = lang,
                    embedUrl = src
                )
            )
        }

        // If no options-div was found, find any iframe in the player section
        if (servers.isEmpty()) {
            val iframes = playerSec.select("iframe")
            for ((idx, iframe) in iframes.withIndex()) {
                val src = iframe.attr("src").takeIf { it.isNotBlank() }
                    ?: iframe.attr("data-src").takeIf { it.isNotBlank() }
                    ?: continue
                servers.add(
                    ServerOption(
                        id = "server-$idx",
                        name = "Server ${idx + 1}",
                        language = "Default",
                        embedUrl = src
                    )
                )
            }
        }

        return servers
    }
}
