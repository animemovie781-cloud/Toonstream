package com.toonstream.app.presentation.movies

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.toonstream.app.R
import com.toonstream.app.data.repository.ToonStreamRepository
import com.toonstream.app.databinding.FragmentCategoryListBinding
import com.toonstream.app.domain.model.ContentItem
import com.toonstream.app.presentation.home.AnimeAdapter
import kotlinx.coroutines.launch

class MoviesFragment : Fragment() {

    private var _binding: FragmentCategoryListBinding? = null
    private val binding get() = _binding!!

    private val repository = ToonStreamRepository()
    private lateinit var adapter: AnimeAdapter
    private var currentCategory = "movies"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbarTitle.text = "Movies"

        val onItemClick: (ContentItem) -> Unit = { item ->
            val bundle = Bundle().apply {
                putString("content_url", item.id)
                putString("content_title", item.title)
                putString("content_poster", item.posterUrl)
                putString("content_type", "Movie")
            }
            findNavController().navigate(R.id.navigation_details, bundle)
        }

        adapter = AnimeAdapter(onItemClick)
        binding.recyclerGrid.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerGrid.adapter = adapter

        binding.swipeRefresh.setColorSchemeColors(0xFFE91E63.toInt())
        binding.swipeRefresh.setOnRefreshListener { loadMovies(currentCategory) }

        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chip_anime -> currentCategory = "anime"
                R.id.chip_cartoons -> currentCategory = "cartoon"
                R.id.chip_hindi -> currentCategory = "hindi"
                else -> currentCategory = "movies"
            }
            loadMovies(currentCategory)
        }

        loadMovies(currentCategory)
    }

    private fun loadMovies(category: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.loadingProgress.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = adapter.itemCount > 0

            val result = if (category == "movies") {
                // If all movies, fetch home and combine anime + animated movies or fetch category
                repository.getCategory("cartoon")
            } else {
                repository.getCategory(category)
            }

            result.fold(
                onSuccess = { items ->
                    adapter.submitList(items)
                },
                onFailure = {
                    // Try home movies if category failed
                    val home = repository.getHome()
                    val fallback = home.getOrNull()?.let { it.animeMovies + it.animatedMovies } ?: emptyList()
                    adapter.submitList(fallback)
                }
            )

            binding.loadingProgress.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
