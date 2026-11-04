package com.toonstream.app.media.quality

import com.toonstream.app.media.model.VideoQuality

object QualityDetector {

    data class M3u8StreamInfo(
        val quality: VideoQuality,
        val bandwidth: Long,
        val resolution: String?,
        val streamUrl: String
    )

    fun detectFromLabel(label: String?): VideoQuality {
        return VideoQuality.fromLabel(label)
    }

    fun detectFromUrl(url: String): VideoQuality {
        val clean = url.lowercase()
        return when {
            clean.contains("2160") || clean.contains("4k") -> VideoQuality.Q_2160P
            clean.contains("1080") || clean.contains("fhd") -> VideoQuality.Q_1080P
            clean.contains("720") || clean.contains("hd") -> VideoQuality.Q_720P
            clean.contains("480") || clean.contains("sd") -> VideoQuality.Q_480P
            clean.contains("360") -> VideoQuality.Q_360P
            else -> VideoQuality.Q_UNKNOWN
        }
    }

    /**
     * Parses an HLS Master Playlist content and extracts discrete quality sub-streams
     */
    fun parseMasterM3u8(masterContent: String, baseUrl: String): List<M3u8StreamInfo> {
        val results = mutableListOf<M3u8StreamInfo>()
        val lines = masterContent.lines()
        var currentResolution: String? = null
        var currentBandwidth: Long = 0
        var currentQuality = VideoQuality.Q_UNKNOWN

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                // Parse RESOLUTION=1920x1080
                val resMatch = Regex("""RESOLUTION=(\d+)x(\d+)""").find(line)
                if (resMatch != null) {
                    val width = resMatch.groupValues[1].toIntOrNull() ?: 0
                    val height = resMatch.groupValues[2].toIntOrNull() ?: 0
                    currentResolution = "${width}x${height}"
                    currentQuality = VideoQuality.fromHeight(height)
                }

                // Parse BANDWIDTH=
                val bwMatch = Regex("""BANDWIDTH=(\d+)""").find(line)
                if (bwMatch != null) {
                    currentBandwidth = bwMatch.groupValues[1].toLongOrNull() ?: 0
                }
            } else if (line.isNotBlank() && !line.startsWith("#")) {
                // This is the stream URI
                val streamUrl = if (line.startsWith("http://") || line.startsWith("https://")) {
                    line
                } else if (line.startsWith("/")) {
                    val root = baseUrl.substringBefore("/", "").ifEmpty { baseUrl }
                    val scheme = if (baseUrl.startsWith("https")) "https://" else "http://"
                    val host = baseUrl.removePrefix("http://").removePrefix("https://").substringBefore("/")
                    "$scheme$host$line"
                } else {
                    val baseDir = baseUrl.substringBeforeLast("/")
                    "$baseDir/$line"
                }

                val detectedQuality = if (currentQuality != VideoQuality.Q_UNKNOWN) {
                    currentQuality
                } else {
                    detectFromUrl(streamUrl)
                }

                results.add(
                    M3u8StreamInfo(
                        quality = detectedQuality,
                        bandwidth = currentBandwidth,
                        resolution = currentResolution,
                        streamUrl = streamUrl
                    )
                )

                // Reset per-stream temp values
                currentResolution = null
                currentBandwidth = 0
                currentQuality = VideoQuality.Q_UNKNOWN
            }
        }

        return results.distinctBy { it.streamUrl }.sortedByDescending { it.quality.height }
    }
}
