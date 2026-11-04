package com.toonstream.app.media.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.toonstream.app.media.model.ExtractorLink
import com.toonstream.app.media.model.SubtitleTrack
import com.toonstream.app.media.model.VideoType
import com.toonstream.app.media.subtitle.SubtitleCollector

class Media3PlayerManager(
    private val context: Context,
    private val playerView: PlayerView,
    private val listener: PlayerEventListener
) {

    interface PlayerEventListener {
        fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean)
        fun onPlayerError(errorMessage: String, canRetry: Boolean)
        fun onPositionDiscontinuity(positionMs: Long, durationMs: Long)
    }

    private var player: ExoPlayer? = null
    private var currentLink: ExtractorLink? = null
    private var currentSubtitles: List<SubtitleTrack> = emptyList()

    init {
        initializeExoPlayer()
    }

    private fun initializeExoPlayer() {
        player?.release()

        val exo = ExoPlayer.Builder(context).build()
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                val isPlaying = exo.isPlaying
                listener.onPlaybackStateChanged(isPlaying, isBuffering)

                if (playbackState == Player.STATE_READY) {
                    listener.onPositionDiscontinuity(exo.currentPosition, exo.duration)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener.onPlaybackStateChanged(isPlaying, false)
            }

            override fun onPlayerError(error: PlaybackException) {
                val msg = error.localizedMessage ?: "Playback error occurred (${error.errorCodeName})"
                listener.onPlayerError(msg, true)
            }
        })

        player = exo
        playerView.player = exo
    }

    @OptIn(UnstableApi::class)
    fun playSource(
        link: ExtractorLink,
        subtitles: List<SubtitleTrack> = emptyList(),
        startPositionMs: Long = 0L
    ) {
        currentLink = link
        currentSubtitles = subtitles

        val exo = player ?: return

        // 1. Build custom HttpDataSource with permitted headers and Referer
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setUserAgent(
                link.permittedHeaders["User-Agent"]
                    ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )

        val headers = link.permittedHeaders.toMutableMap()
        link.referer?.let { headers["Referer"] = it }
        if (headers.isNotEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(headers)
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        // 2. Build Subtitle Configurations
        val subtitleConfigs = subtitles.map { SubtitleCollector.toMedia3SubtitleConfig(it) }

        // 3. Build MediaItem
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(link.url)
            .setSubtitleConfigurations(subtitleConfigs)

        if (link.type == VideoType.HLS || link.url.contains(".m3u8")) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        } else if (link.type == VideoType.MP4 || link.url.contains(".mp4")) {
            mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
        }

        val mediaItem = mediaItemBuilder.build()
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        exo.setMediaSource(mediaSource)
        if (startPositionMs > 0) {
            exo.seekTo(startPositionMs)
        }
        exo.prepare()
        exo.playWhenReady = true
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L
    fun getDuration(): Long = player?.duration ?: 0L
    fun isPlaying(): Boolean = player?.isPlaying ?: false

    fun pause() {
        player?.pause()
    }

    fun play() {
        player?.play()
    }

    fun release() {
        player?.release()
        player = null
    }
}
