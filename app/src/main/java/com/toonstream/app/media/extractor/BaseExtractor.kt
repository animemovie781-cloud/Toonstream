package com.toonstream.app.media.extractor

import com.toonstream.app.media.engine.VideoExtractor
import com.toonstream.app.media.model.ExtractorLink
import com.toonstream.app.media.model.VideoQuality
import com.toonstream.app.media.model.VideoType
import com.toonstream.app.media.quality.QualityDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

abstract class BaseExtractor : VideoExtractor {

    protected val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    protected suspend fun fetchHtml(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder().url(url)
            reqBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            referer?.let { reqBuilder.header("Referer", it) }
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves master m3u8 playlist and splits it into discrete quality sub-streams
     */
    protected suspend fun expandMasterM3u8(
        masterUrl: String,
        sourceName: String,
        referer: String? = null,
        permittedHeaders: Map<String, String> = emptyMap(),
        language: String = "Default"
    ): List<ExtractorLink> = withContext(Dispatchers.IO) {
        val links = mutableListOf<ExtractorLink>()

        // Always add the master stream as Auto quality
        links.add(
            ExtractorLink(
                id = "${masterUrl}_master",
                sourceName = "$sourceName (Auto)",
                url = masterUrl,
                quality = VideoQuality.Q_UNKNOWN,
                type = VideoType.HLS,
                referer = referer,
                permittedHeaders = permittedHeaders,
                language = language,
                extractorName = name
            )
        )

        val masterContent = fetchHtml(masterUrl, permittedHeaders, referer)
        if (!masterContent.isNullOrBlank() && masterContent.contains("#EXT-X-STREAM-INF:")) {
            val streams = QualityDetector.parseMasterM3u8(masterContent, masterUrl)
            for (stream in streams) {
                links.add(
                    ExtractorLink(
                        id = stream.streamUrl,
                        sourceName = "$sourceName (${stream.quality.label})",
                        url = stream.streamUrl,
                        quality = stream.quality,
                        type = VideoType.HLS,
                        referer = referer,
                        permittedHeaders = permittedHeaders,
                        language = language,
                        extractorName = name
                    )
                )
            }
        }

        links
    }
}
