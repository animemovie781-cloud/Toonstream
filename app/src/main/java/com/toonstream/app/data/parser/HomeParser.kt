package com.toonstream.app.data.parser

import com.toonstream.app.domain.model.ContentItem

object HomeParser {
    fun parseFeatured(html: String): List<ContentItem> {
        return ToonStreamParser.parseHome(html).featured
    }

    fun parseLatestEpisodes(html: String): List<ContentItem> {
        return ToonStreamParser.parseHome(html).freshDrop
    }
}
