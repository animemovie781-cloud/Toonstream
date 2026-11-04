package com.toonstream.app.data.repository

import com.toonstream.app.core.network.NetworkModule
import com.toonstream.app.data.parser.ToonStreamParser
import com.toonstream.app.data.remote.WebsiteApi
import com.toonstream.app.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ToonStreamRepository(
    private val api: WebsiteApi = NetworkModule.websiteApi
) {
    suspend fun getHome(): Result<HomeSections> = withContext(Dispatchers.IO) {
        try {
            val response = api.fetchHtml("https://toonstream.us/home")
            if (response.isSuccessful) {
                val html = response.body()?.string() ?: ""
                val sections = ToonStreamParser.parseHome(html)
                Result.success(sections)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun search(query: String): Result<List<ContentItem>> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val response = api.fetchHtml("https://toonstream.us/s?q=$encodedQuery")
            if (response.isSuccessful) {
                val html = response.body()?.string() ?: ""
                val results = ToonStreamParser.parseSearch(html)
                Result.success(results)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSuggestions(query: String): List<ContentItem> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val response = api.fetchHtml("https://toonstream.us/search/all?q=$encodedQuery")
            if (response.isSuccessful) {
                val json = response.body()?.string() ?: ""
                ToonStreamParser.parseSearchSuggestions(json)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCategory(category: String, page: Int = 1): Result<List<ContentItem>> = withContext(Dispatchers.IO) {
        try {
            val url = if (page <= 1) {
                "https://toonstream.us/category/$category/"
            } else {
                "https://toonstream.us/category/$category/page/$page/"
            }
            val response = api.fetchHtml(url)
            if (response.isSuccessful) {
                val html = response.body()?.string() ?: ""
                val items = ToonStreamParser.parseCategory(html, category.replaceFirstChar { it.uppercase() })
                Result.success(items)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDetails(urlOrPath: String): Result<ContentDetails> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = if (urlOrPath.startsWith("http")) urlOrPath else "https://toonstream.us$urlOrPath"
            val response = api.fetchHtml(fullUrl)
            if (response.isSuccessful) {
                val html = response.body()?.string() ?: ""
                val details = ToonStreamParser.parseDetails(html, fullUrl)
                Result.success(details)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeasonEpisodes(seasonUrl: String, seasonNum: Int): Result<List<EpisodeItem>> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = if (seasonUrl.startsWith("http")) seasonUrl else "https://toonstream.us$seasonUrl"
            val response = api.fetchHtml(fullUrl)
            if (response.isSuccessful) {
                val html = response.body()?.string() ?: ""
                val episodes = ToonStreamParser.parseSeasonEpisodes(html, seasonNum)
                Result.success(episodes)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
