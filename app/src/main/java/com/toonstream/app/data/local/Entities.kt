package com.toonstream.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String, // e.g. /series/attack-on-titan
    val title: String,
    val posterUrl: String,
    val type: String,
    val rating: String?,
    val releaseYear: String?,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val id: String, // Content or Episode URL
    val seriesOrMovieId: String,
    val title: String,
    val episodeTitle: String?,
    val posterUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
)
