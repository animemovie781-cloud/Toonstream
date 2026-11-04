package com.toonstream.app.domain.model

data class ContentItem(
    val id: String, // e.g. /series/attack-on-titan or /movies/... or /episode/...
    val title: String,
    val posterUrl: String,
    val backdropUrl: String? = null,
    val type: String = "Anime", // Series, Movie, Episode, Cartoon
    val rating: String? = null,
    val releaseYear: String? = null,
    val seasonLabel: String? = null,
    val episodeLabel: String? = null,
    val description: String? = null
)

data class SeasonItem(
    val seasonNumber: Int,
    val title: String,
    val url: String
)

data class EpisodeItem(
    val id: String, // e.g. /episode/attack-on-titan-1x1/
    val title: String, // e.g. S 1 | E 1
    val episodeNumber: Int,
    val seasonNumber: Int,
    val thumbnailUrl: String? = null,
    val seriesUrl: String? = null
)

data class ServerOption(
    val id: String,
    val name: String, // e.g. Server 1 FHD
    val language: String = "Default",
    val embedUrl: String,
    val directStreamUrl: String? = null
)

data class ContentDetails(
    val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String? = null,
    val type: String, // series or movie
    val description: String,
    val rating: String? = null,
    val releaseYear: String? = null,
    val genres: List<String> = emptyList(),
    val seasons: List<SeasonItem> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val servers: List<ServerOption> = emptyList()
)

data class HomeSections(
    val featured: List<ContentItem> = emptyList(),
    val freshDrop: List<ContentItem> = emptyList(), // Latest episodes
    val animeSeries: List<ContentItem> = emptyList(),
    val cartoons: List<ContentItem> = emptyList(), // Animated Series
    val animeMovies: List<ContentItem> = emptyList(),
    val animatedMovies: List<ContentItem> = emptyList()
)

data class StreamSource(
    val url: String,
    val quality: String,
    val isHls: Boolean = true,
    val headers: Map<String, String> = emptyMap()
)

data class Subtitle(
    val url: String,
    val language: String,
    val format: String = "vtt"
)

data class PlaybackResult(
    val sources: List<StreamSource>,
    val subtitles: List<Subtitle> = emptyList()
)
