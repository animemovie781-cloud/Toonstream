package com.toonstream.app.presentation.player

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.toonstream.app.databinding.ActivityPlayerBinding
import com.toonstream.app.media.model.ExtractorLink
import com.toonstream.app.media.model.VideoType
import com.toonstream.app.media.player.Media3PlayerManager
import com.toonstream.app.media.player.PlayerViewModel
import com.toonstream.app.media.ui.SourceSelectionBottomSheet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity(), Media3PlayerManager.PlayerEventListener {

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()

    private var playerManager: Media3PlayerManager? = null

    private var pageUrl: String = ""
    private var mediaTitle: String = ""
    private var mediaPoster: String = ""
    private var currentSpeed: Float = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enterImmersiveFullscreen()

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pageUrl = intent.getStringExtra("STREAM_URL") ?: ""
        mediaTitle = intent.getStringExtra("MEDIA_TITLE") ?: "Video Player"
        mediaPoster = intent.getStringExtra("MEDIA_POSTER") ?: ""

        binding.playerTitle.text = mediaTitle

        playerManager = Media3PlayerManager(this, binding.playerView, this)

        setupListeners()
        setupWebView()
        observeViewModel()

        viewModel.loadSources(pageUrl, mediaTitle)
    }

    private fun enterImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupListeners() {
        binding.btnPlayerBack.setOnClickListener {
            finish()
        }

        binding.btnServers.setOnClickListener {
            showSourceSelection()
        }

        binding.btnPlayerRetry.setOnClickListener {
            showSourceSelection()
        }

        binding.btnSpeed.setOnClickListener {
            showSpeedDialog()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webPlayerView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        binding.webPlayerView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    binding.layoutLoading.visibility = View.VISIBLE
                    binding.playerProgressText.text = "Loading web video player..."
                } else {
                    binding.layoutLoading.visibility = View.GONE
                }
            }
        }

        binding.webPlayerView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val isLegitEmbed = url.contains("filesforever.link") ||
                        url.contains("rubystm.com") ||
                        url.contains("vidmoly") ||
                        url.contains("vidc.upns.pro") ||
                        url.contains("abyssplayer") ||
                        url.contains("vidara") ||
                        url.contains("voe.sx") ||
                        url.contains("streamwish") ||
                        url.contains("filemoon") ||
                        url.contains("vidhide") ||
                        url.contains("iqsmartgames.com") ||
                        url.contains("toonstream.us") ||
                        url.contains("google") ||
                        url.contains("gstatic")
                
                // Allow the main frame to navigate to legit embeds, but block popup-style redirects
                if (request.isForMainFrame) {
                    return if (isLegitEmbed) false else {
                         // Still let it load if it's the first load or a known redirect pattern
                         false 
                    }
                }
                
                // Block non-legit sub-frame navigations (usually ads)
                return !isLegitEmbed
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    binding.playerErrorLayout.visibility = View.VISIBLE
                    binding.playerErrorText.text = "Web video player error. Tap to switch source."
                }
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.progressMessage.collectLatest { msg ->
                binding.playerProgressText.text = msg
            }
        }

        lifecycleScope.launch {
            viewModel.errorMessage.collectLatest { error ->
                if (!error.isNullOrBlank()) {
                    binding.playerErrorLayout.visibility = View.VISIBLE
                    binding.playerErrorText.text = error
                } else {
                    binding.playerErrorLayout.visibility = View.GONE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentLink.collectLatest { link ->
                if (link != null) {
                    playResolvedLink(link)
                }
            }
        }
    }

    private fun playResolvedLink(link: ExtractorLink) {
        binding.playerErrorLayout.visibility = View.GONE

        lifecycleScope.launch {
            val savedPosition = viewModel.getSavedPosition(pageUrl)

            // Try ExoPlayer for anything that is not explicitly a WebEmbed
            if (link.extractorName != "WebEmbed" || link.url.contains(".m3u8") || link.url.contains(".mp4")) {
                binding.webPlayerView.visibility = View.GONE
                binding.playerView.visibility = View.VISIBLE
                binding.webPlayerView.onPause()
                binding.webPlayerView.loadUrl("about:blank")

                val subs = viewModel.playbackResult.value?.subtitles ?: emptyList()
                playerManager?.playSource(link, subs, savedPosition)
            } else {
                // Fallback to web embed
                Toast.makeText(this@PlayerActivity, "Native extraction failed. Using browser player.", Toast.LENGTH_SHORT).show()
                binding.playerView.visibility = View.GONE
                binding.webPlayerView.visibility = View.VISIBLE
                playerManager?.pause()

                val headers = mapOf("Referer" to (link.referer ?: "https://toonstream.us/"))
                binding.webPlayerView.loadUrl(link.url, headers)
            }
        }
    }

    private fun showSourceSelection() {
        val result = viewModel.playbackResult.value
        val sources = result?.sources ?: emptyList()

        if (sources.isEmpty()) {
            Toast.makeText(this, "No alternative sources available.", Toast.LENGTH_SHORT).show()
            return
        }

        val sheet = SourceSelectionBottomSheet(
            sources = sources,
            currentLink = viewModel.currentLink.value
        ) { selected ->
            viewModel.selectSource(selected)
        }
        sheet.show(supportFragmentManager, "SourceSelectionBottomSheet")
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x")
        val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

        AlertDialog.Builder(this)
            .setTitle("Playback Speed")
            .setItems(speeds) { _, which ->
                currentSpeed = speedValues[which]
                binding.btnSpeed.text = speeds[which].substringBefore(" ")
                playerManager?.setPlaybackSpeed(currentSpeed)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Media3 Callbacks
    override fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean) {
        if (binding.playerView.visibility == View.VISIBLE) {
            binding.layoutLoading.visibility = if (isBuffering) View.VISIBLE else View.GONE
            if (isBuffering) {
                binding.playerProgressText.text = "Buffering stream..."
            }
        }
    }

    override fun onPlayerError(errorMessage: String, canRetry: Boolean) {
        binding.playerErrorLayout.visibility = View.VISIBLE
        binding.playerErrorText.text = "$errorMessage\nTap below to switch source."
    }

    override fun onPositionDiscontinuity(positionMs: Long, durationMs: Long) {
        viewModel.updatePlaybackPosition(positionMs, durationMs, pageUrl, mediaTitle, mediaPoster)
    }

    override fun onPause() {
        super.onPause()
        playerManager?.let {
            viewModel.updatePlaybackPosition(
                it.getCurrentPosition(),
                it.getDuration(),
                pageUrl,
                mediaTitle,
                mediaPoster
            )
            it.pause()
        }
        binding.webPlayerView.onPause()
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveFullscreen()
        binding.webPlayerView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager?.release()
        playerManager = null
        binding.webPlayerView.destroy()
    }
}
