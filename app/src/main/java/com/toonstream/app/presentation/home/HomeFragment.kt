package com.toonstream.app.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.toonstream.app.R
import com.toonstream.app.databinding.FragmentHomeBinding
import com.toonstream.app.domain.model.ContentItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var freshDropAdapter: AnimeAdapter
    private lateinit var animeSeriesAdapter: AnimeAdapter
    private lateinit var cartoonsAdapter: AnimeAdapter
    private lateinit var animeMoviesAdapter: AnimeAdapter
    private lateinit var animatedMoviesAdapter: AnimeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupListeners()
        observeViewModel()
    }

    private fun setupAdapters() {
        val onItemClick: (ContentItem) -> Unit = { item ->
            val bundle = Bundle().apply {
                putString("content_url", item.id)
                putString("content_title", item.title)
                putString("content_poster", item.posterUrl)
                putString("content_type", item.type)
            }
            findNavController().navigate(R.id.navigation_details, bundle)
        }

        freshDropAdapter = AnimeAdapter(onItemClick)
        animeSeriesAdapter = AnimeAdapter(onItemClick)
        cartoonsAdapter = AnimeAdapter(onItemClick)
        animeMoviesAdapter = AnimeAdapter(onItemClick)
        animatedMoviesAdapter = AnimeAdapter(onItemClick)

        setupHorizontalRecycler(binding.recyclerFreshDrop, freshDropAdapter)
        setupHorizontalRecycler(binding.recyclerAnimeSeries, animeSeriesAdapter)
        setupHorizontalRecycler(binding.recyclerCartoons, cartoonsAdapter)
        setupHorizontalRecycler(binding.recyclerAnimeMovies, animeMoviesAdapter)
        setupHorizontalRecycler(binding.recyclerAnimatedMovies, animatedMoviesAdapter)
    }

    private fun setupHorizontalRecycler(recycler: RecyclerView, adapter: AnimeAdapter) {
        recycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        recycler.adapter = adapter
        recycler.setHasFixedSize(true)
    }

    private fun setupListeners() {
        binding.swipeRefresh.setColorSchemeColors(0xFFE91E63.toInt())
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadContent()
        }

        binding.btnRetry.setOnClickListener {
            viewModel.loadContent()
        }

        binding.btnSearch.setOnClickListener {
            findNavController().navigate(R.id.navigation_search)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.swipeRefresh.isRefreshing = isLoading && binding.heroBannerCard.visibility == View.VISIBLE
                if (isLoading && binding.heroBannerCard.visibility != View.VISIBLE) {
                    binding.loadingProgress.visibility = View.VISIBLE
                } else {
                    binding.loadingProgress.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errorMessage.collectLatest { error ->
                if (!error.isNullOrBlank() && freshDropAdapter.itemCount == 0 && animeSeriesAdapter.itemCount == 0) {
                    binding.errorContainer.visibility = View.VISIBLE
                    binding.errorText.text = error
                    binding.nestedScroll.visibility = View.GONE
                } else {
                    binding.errorContainer.visibility = View.GONE
                    binding.nestedScroll.visibility = View.VISIBLE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.homeSections.collectLatest { sections ->
                // Hero Banner setup
                val heroItem = sections.featured.firstOrNull() ?: sections.freshDrop.firstOrNull()
                if (heroItem != null) {
                    binding.heroBannerCard.visibility = View.VISIBLE
                    binding.heroTitle.text = heroItem.title
                    binding.heroBadge.text = heroItem.type.uppercase()
                    binding.heroImage.load(heroItem.posterUrl) {
                        crossfade(true)
                    }
                    binding.heroBannerCard.setOnClickListener {
                        val bundle = Bundle().apply {
                            putString("content_url", heroItem.id)
                            putString("content_title", heroItem.title)
                            putString("content_poster", heroItem.posterUrl)
                            putString("content_type", heroItem.type)
                        }
                        findNavController().navigate(R.id.navigation_details, bundle)
                    }
                }

                // Section visibility & data
                bindSection(binding.layoutFreshDrop, freshDropAdapter, sections.freshDrop)
                bindSection(binding.layoutAnimeSeries, animeSeriesAdapter, sections.animeSeries)
                bindSection(binding.layoutCartoons, cartoonsAdapter, sections.cartoons)
                bindSection(binding.layoutAnimeMovies, animeMoviesAdapter, sections.animeMovies)
                bindSection(binding.layoutAnimatedMovies, animatedMoviesAdapter, sections.animatedMovies)
            }
        }
    }

    private fun bindSection(layout: View, adapter: AnimeAdapter, items: List<ContentItem>) {
        if (items.isNotEmpty()) {
            layout.visibility = View.VISIBLE
            adapter.submitList(items)
        } else {
            layout.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
