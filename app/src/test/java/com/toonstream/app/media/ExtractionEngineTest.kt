package com.toonstream.app.media

import com.toonstream.app.media.engine.ExtractorRegistry
import com.toonstream.app.media.engine.VideoExtractor
import com.toonstream.app.media.extractor.DirectMediaExtractor
import com.toonstream.app.media.model.*
import com.toonstream.app.media.quality.QualityDetector
import com.toonstream.app.media.quality.SourcePriorityManager
import com.toonstream.app.media.subtitle.SubtitleCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExtractionEngineTest {

    @Test
    fun qualityDetector_recognizes_standard_labels() {
        assertEquals(VideoQuality.Q_2160P, QualityDetector.detectFromLabel("4K UHD"))
        assertEquals(VideoQuality.Q_1080P, QualityDetector.detectFromLabel("Server 1 (FHD)"))
        assertEquals(VideoQuality.Q_720P, QualityDetector.detectFromLabel("720p HD"))
        assertEquals(VideoQuality.Q_480P, QualityDetector.detectFromLabel("480p SD"))
        assertEquals(VideoQuality.Q_360P, QualityDetector.detectFromLabel("360p"))
        assertEquals(VideoQuality.Q_UNKNOWN, QualityDetector.detectFromLabel("auto"))
    }

    @Test
    fun qualityDetector_parses_master_m3u8_playlists() {
        val masterPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            1080p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
            720p/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=854x480
            480p/index.m3u8
        """.trimIndent()

        val streams = QualityDetector.parseMasterM3u8(masterPlaylist, "https://cdn.example.com/live/master.m3u8")
        assertEquals(3, streams.size)
        assertEquals(VideoQuality.Q_1080P, streams[0].quality)
        assertEquals("https://cdn.example.com/live/1080p/index.m3u8", streams[0].streamUrl)
        assertEquals(VideoQuality.Q_720P, streams[1].quality)
        assertEquals(VideoQuality.Q_480P, streams[2].quality)
    }

    @Test
    fun sourcePriorityManager_sorts_by_working_status_and_quality() {
        val broken1080p = ExtractorLink(
            id = "1", sourceName = "Source 1", url = "http://a.com/1080.mp4",
            quality = VideoQuality.Q_1080P, isWorking = false
        )
        val working720p = ExtractorLink(
            id = "2", sourceName = "Source 2", url = "http://b.com/720.m3u8",
            quality = VideoQuality.Q_720P, isWorking = true
        )
        val working1080p = ExtractorLink(
            id = "3", sourceName = "Source 3", url = "http://c.com/1080.m3u8",
            quality = VideoQuality.Q_1080P, isWorking = true
        )

        val sorted = SourcePriorityManager.sortSources(listOf(broken1080p, working720p, working1080p))
        assertEquals(working1080p.id, sorted[0].id)
        assertEquals(working720p.id, sorted[1].id)
        assertEquals(broken1080p.id, sorted[2].id)
    }

    @Test
    fun sourcePriorityManager_removes_duplicate_links() {
        val link1 = ExtractorLink(id = "1", sourceName = "S1", url = "https://cdn.com/stream.m3u8")
        val link2 = ExtractorLink(id = "2", sourceName = "S2", url = "https://cdn.com/stream.m3u8")

        val deduplicated = SourcePriorityManager.sortSources(listOf(link1, link2))
        assertEquals(1, deduplicated.size)
    }

    @Test
    fun subtitleCollector_detects_formats() {
        assertEquals(SubtitleFormat.VTT, SubtitleCollector.detectFormat("https://site.com/sub.vtt?token=123"))
        assertEquals(SubtitleFormat.WEBVTT, SubtitleCollector.detectFormat("https://site.com/sub.webvtt"))
        assertEquals(SubtitleFormat.SRT, SubtitleCollector.detectFormat("https://site.com/sub.srt"))
    }

    @Test
    fun extractorRegistry_routes_to_matching_extractor() {
        val directExtractor = DirectMediaExtractor()
        assertTrue(directExtractor.canHandle("https://stream.net/video.m3u8"))
        assertTrue(directExtractor.canHandle("https://stream.net/video.mp4?auth=1"))

        val extractor = ExtractorRegistry.getExtractor("https://vidmoly.net/embed-xyz.html")
        assertEquals("Vidmoly", extractor.name)
    }

    @Test
    fun directMediaExtractor_extracts_mp4() = runBlocking {
        val extractor = DirectMediaExtractor()
        val links = extractor.extract("https://files.com/sample.mp4")
        assertEquals(1, links.size)
        assertEquals(VideoType.MP4, links[0].type)
        assertEquals("https://files.com/sample.mp4", links[0].url)
    }
}
