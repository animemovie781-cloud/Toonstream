package com.toonstream.app.data.repository

import com.toonstream.app.data.remote.WebsiteApi
import com.toonstream.app.domain.model.ContentItem
import com.toonstream.app.domain.model.EpisodeItem
import com.toonstream.app.domain.model.HomeSections

class ContentRepositoryImpl(
    private val api: WebsiteApi
) {
    private val repo = ToonStreamRepository(api)

    suspend fun getHomeSections(): Result<HomeSections> {
        return repo.getHome()
    }

    suspend fun getFeaturedContent(): List<ContentItem> {
        val result = repo.getHome()
        return result.getOrNull()?.featured ?: emptyList()
    }

    suspend fun getLatestEpisodes(): List<ContentItem> {
        val result = repo.getHome()
        return result.getOrNull()?.freshDrop ?: emptyList()
    }
}
