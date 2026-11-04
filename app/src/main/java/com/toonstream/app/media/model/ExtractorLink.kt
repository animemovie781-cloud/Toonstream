package com.toonstream.app.media.model

enum class VideoType {
    HLS,
    MP4,
    DASH,
    UNKNOWN
}

enum class VideoQuality(val label: String, val height: Int) {
    Q_2160P("2160p (4K)", 2160),
    Q_1080P("1080p (FHD)", 1080),
    Q_720P("720p (HD)", 720),
    Q_480P("480p (SD)", 480),
    Q_360P("360p", 360),
    Q_UNKNOWN("Auto / Unknown", 0);

    companion object {
        fun fromHeight(height: Int): VideoQuality = when {
            height >= 2000 -> Q_2160P
            height >= 1000 -> Q_1080P
            height >= 700 -> Q_720P
            height >= 450 -> Q_480P
            height >= 300 -> Q_360P
            else -> Q_UNKNOWN
        }

        fun fromLabel(label: String?): VideoQuality {
            if (label.isNullOrBlank()) return Q_UNKNOWN
            val clean = label.lowercase()
            return when {
                clean.contains("4k") || clean.contains("2160") -> Q_2160P
                clean.contains("fhd") || clean.contains("1080") -> Q_1080P
                clean.contains("hd") || clean.contains("720") -> Q_720P
                clean.contains("sd") || clean.contains("480") -> Q_480P
                clean.contains("360") -> Q_360P
                else -> Q_UNKNOWN
            }
        }
    }
}

data class ExtractorLink(
    val id: String,
    val sourceName: String,
    val url: String,
    val quality: VideoQuality = VideoQuality.Q_UNKNOWN,
    val type: VideoType = VideoType.HLS,
    val mimeType: String = if (type == VideoType.HLS) "application/x-mpegURL" else "video/mp4",
    val referer: String? = null,
    val permittedHeaders: Map<String, String> = emptyMap(),
    val language: String = "Default",
    val isWorking: Boolean = true,
    val extractorName: String = "Generic"
)
