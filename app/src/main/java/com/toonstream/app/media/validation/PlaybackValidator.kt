package com.toonstream.app.media.validation

import com.toonstream.app.media.model.ExtractorLink
import com.toonstream.app.media.model.VideoType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object PlaybackValidator {

    private val validatorClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun validate(link: ExtractorLink): ExtractorLink = withContext(Dispatchers.IO) {
        if (link.url.isBlank() || (!link.url.startsWith("http://") && !link.url.startsWith("https://"))) {
            return@withContext link.copy(isWorking = false)
        }

        try {
            val requestBuilder = Request.Builder()
                .url(link.url)
                // Use Range request so video servers don't stream full file
                .header("Range", "bytes=0-1024")

            link.referer?.let { requestBuilder.header("Referer", it) }
            link.permittedHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

            if (!link.permittedHeaders.containsKey("User-Agent")) {
                requestBuilder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
            }

            validatorClient.newCall(requestBuilder.build()).execute().use { response ->
                val code = response.code
                val contentType = response.header("Content-Type", "")?.lowercase().orEmpty()

                val isHttpValid = code in 200..299 || code == 206
                val isMediaCompatible = when (link.type) {
                    VideoType.HLS -> contentType.contains("mpegurl") || contentType.contains("application/x-mpegurl") ||
                            contentType.contains("application/vnd.apple.mpegurl") || contentType.contains("video/") ||
                            link.url.contains(".m3u8") || contentType.contains("text/plain") || contentType.contains("octet-stream")
                    VideoType.MP4 -> contentType.contains("video/") || contentType.contains("octet-stream") || link.url.contains(".mp4")
                    else -> true
                }

                link.copy(isWorking = isHttpValid && isMediaCompatible)
            }
        } catch (_: Exception) {
            // If validation check timed out or failed, keep isWorking true if it's a known valid pattern
            // so we don't prematurely discard playable streams due to strict CDN firewalls
            val isLikelyValid = link.url.contains(".m3u8") || link.url.contains(".mp4")
            link.copy(isWorking = isLikelyValid)
        }
    }
}
