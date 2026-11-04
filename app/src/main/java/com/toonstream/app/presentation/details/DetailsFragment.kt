package com.toonstream.app.presentation.details

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.chip.Chip
import com.toonstream.app.R
import com.toonstream.app.data.local.AppDatabase
import com.toonstream.app.data.local.FavoriteEntity
import com.toonstream.app.data.repository.ToonStreamRepository
import com.toonstream.app.databinding.FragmentDetailsBinding
import com.toonstream.app.domain.model.ContentDetails
import com.toonstream.app.domain.model.EpisodeItem
import com.toonstream.app.domain.model.SeasonItem
import com.toonstream.app.domain.model.ServerOption
import com.toonstream.app.presentation.player.PlayerActivity
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class DetailsFragment : Fragment() {

    private var _binding: FragmentDetailsBinding? = null
    private val binding get() = _binding!!

    private val repository = ToonStreamRepository()
    private lateinit var episodeAdapter: EpisodeAdapter

    private var contentUrl: String = ""
    private var contentTitle: String = ""
    private var contentPoster: String = ""
    private var contentType: String = "Anime"

    private var currentDetails: ContentDetails? = null
    private var selectedServer: ServerOption? = null
    private var isFavorite: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentUrl = arguments?.getString("content_url")
            ?: arguments?.getString("anime_id")
            ?: ""
        contentTitle = arguments?.getString("content_title").orEmpty()
        contentPoster = arguments?.getString("content_poster").orEmpty()
        contentType = arguments?.getString("content_type") ?: "Anime"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupInitialUi()
        setupListeners()
        loadDetails()
        checkFavoriteStatus()
    }

    private fun setupInitialUi() {
        if (contentTitle.isNotBlank()) binding.titleText.text = contentTitle
        if (contentPoster.isNotBlank()) {
            binding.posterImage.load(contentPoster) { crossfade(true) }
            binding.backdropImage.load(contentPoster) { crossfade(true) }
        }

        episodeAdapter = EpisodeAdapter { episode ->
            playVideo(
                streamUrl = episode.id,
                title = "$contentTitle - ${episode.title}",
                poster = episode.thumbnailUrl ?: contentPoster
            )
        }

        binding.recyclerEpisodes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerEpisodes.adapter = episodeAdapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnFavorite.setOnClickListener {
            toggleFavorite()
        }

        binding.watchButton.setOnClickListener {
            val details = currentDetails
            if (details == null) {
                Toast.makeText(requireContext(), "Loading details...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (details.type == "Movie" || details.episodes.isEmpty()) {
                val server = selectedServer ?: details.servers.firstOrNull()
                val targetUrl = server?.embedUrl ?: details.id
                playVideo(
                    streamUrl = targetUrl,
                    title = details.title,
                    poster = details.posterUrl
                )
            } else {
                // If series, play first episode
                val firstEp = details.episodes.firstOrNull()
                if (firstEp != null) {
                    playVideo(
                        streamUrl = firstEp.id,
                        title = "${details.title} - ${firstEp.title}",
                        poster = firstEp.thumbnailUrl ?: details.posterUrl
                    )
                } else {
                    Toast.makeText(requireContext(), "No episodes available yet.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadDetails() {
        if (contentUrl.isBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            binding.detailsProgress.visibility = View.VISIBLE
            val result = repository.getDetails(contentUrl)

            result.fold(
                onSuccess = { details ->
                    currentDetails = details
                    bindDetails(details)
                },
                onFailure = { error ->
                    Toast.makeText(
                        requireContext(),
                        "Failed to load details: ${error.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )

            binding.detailsProgress.visibility = View.GONE
        }
    }

    private fun bindDetails(details: ContentDetails) {
        binding.titleText.text = details.title
        binding.descriptionText.text = details.description

        if (details.posterUrl.isNotBlank()) {
            binding.posterImage.load(details.posterUrl) { crossfade(true) }
        }
        if (!details.backdropUrl.isNullOrBlank()) {
            binding.backdropImage.load(details.backdropUrl) { crossfade(true) }
        }

        // Rating
        if (!details.rating.isNullOrBlank()) {
            binding.ratingBadge.visibility = View.VISIBLE
            binding.ratingBadge.text = "★ ${details.rating}"
        } else {
            binding.ratingBadge.visibility = View.GONE
        }

        // Type badge
        binding.typeBadge.text = details.type.uppercase()

        // Release Year
        if (!details.releaseYear.isNullOrBlank()) {
            binding.yearText.visibility = View.VISIBLE
            binding.yearText.text = details.releaseYear
        } else {
            binding.yearText.visibility = View.GONE
        }

        // Genres
        if (details.genres.isNotEmpty()) {
            binding.genresText.visibility = View.VISIBLE
            binding.genresText.text = details.genres.joinToString(" • ")
        } else {
            binding.genresText.visibility = View.GONE
        }

        // Series vs Movie handling
        if (details.type == "Movie" || (details.seasons.isEmpty() && details.episodes.isEmpty())) {
            binding.layoutMovieServers.visibility = View.VISIBLE
            binding.layoutSeriesContent.visibility = View.GONE
            setupMovieServers(details.servers)
        } else {
            binding.layoutMovieServers.visibility = View.GONE
            binding.layoutSeriesContent.visibility = View.VISIBLE
            setupSeriesSeasons(details)
        }
    }

    private fun setupMovieServers(servers: List<ServerOption>) {
        binding.chipGroupServers.removeAllViews()
        if (servers.isEmpty()) {
            binding.layoutMovieServers.visibility = View.GONE
            return
        }

        selectedServer = servers.firstOrNull()

        for ((index, server) in servers.withIndex()) {
            val chip = Chip(requireContext()).apply {
                text = "${server.name} [${server.language}]"
                isCheckable = true
                isChecked = index == 0
                setChipBackgroundColorResource(android.R.color.transparent)
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener {
                    selectedServer = server
                }
            }
            binding.chipGroupServers.addView(chip)
        }
    }

    private fun setupSeriesSeasons(details: ContentDetails) {
        binding.chipGroupSeasons.removeAllViews()

        if (details.seasons.isEmpty()) {
            // Just display all parsed episodes
            episodeAdapter.submitList(details.episodes)
            return
        }

        // Build season chips
        for ((index, season) in details.seasons.withIndex()) {
            val chip = Chip(requireContext()).apply {
                text = season.title
                isCheckable = true
                isChecked = index == 0
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener {
                    loadSeasonEpisodes(season)
                }
            }
            binding.chipGroupSeasons.addView(chip)
        }

        // Load first season episodes
        val firstSeason = details.seasons.firstOrNull()
        if (firstSeason != null) {
            loadSeasonEpisodes(firstSeason)
        } else {
            episodeAdapter.submitList(details.episodes)
        }
    }

    private fun loadSeasonEpisodes(season: SeasonItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.detailsProgress.visibility = View.VISIBLE
            val result = repository.getSeasonEpisodes(season.url, season.seasonNumber)
            result.fold(
                onSuccess = { episodes ->
                    if (episodes.isNotEmpty()) {
                        episodeAdapter.submitList(episodes)
                    } else {
                        // Fallback to details episodes matching season
                        val filtered = currentDetails?.episodes?.filter { it.seasonNumber == season.seasonNumber }
                        episodeAdapter.submitList(filtered ?: emptyList())
                    }
                },
                onFailure = {
                    val filtered = currentDetails?.episodes?.filter { it.seasonNumber == season.seasonNumber }
                    episodeAdapter.submitList(filtered ?: emptyList())
                }
            )
            binding.detailsProgress.visibility = View.GONE
        }
    }

    private fun playVideo(streamUrl: String, title: String, poster: String) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("STREAM_URL", streamUrl)
            putExtra("MEDIA_TITLE", title)
            putExtra("MEDIA_POSTER", poster)
            putExtra("SERIES_ID", contentUrl)
        }
        startActivity(intent)
    }

    private fun checkFavoriteStatus() {
        val db = AppDatabase.getInstance(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val exists = db.favoritesDao().isFavorite(contentUrl).firstOrNull() ?: false
            isFavorite = exists
            updateFavoriteIcon()
        }
    }

    private fun toggleFavorite() {
        val db = AppDatabase.getInstance(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            if (isFavorite) {
                db.favoritesDao().removeFavorite(contentUrl)
                isFavorite = false
                Toast.makeText(requireContext(), "Removed from My List", Toast.LENGTH_SHORT).show()
            } else {
                val details = currentDetails
                val entity = FavoriteEntity(
                    id = contentUrl,
                    title = details?.title ?: contentTitle,
                    posterUrl = details?.posterUrl ?: contentPoster,
                    type = details?.type ?: contentType,
                    rating = details?.rating,
                    releaseYear = details?.releaseYear
                )
                db.favoritesDao().addFavorite(entity)
                isFavorite = true
                Toast.makeText(requireContext(), "Added to My List", Toast.LENGTH_SHORT).show()
            }
            updateFavoriteIcon()
        }
    }

    private fun updateFavoriteIcon() {
        if (isFavorite) {
            binding.btnFavorite.setColorFilter(0xFFFFD700.toInt())
        } else {
            binding.btnFavorite.setColorFilter(0xFFFFFFFF.toInt())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
