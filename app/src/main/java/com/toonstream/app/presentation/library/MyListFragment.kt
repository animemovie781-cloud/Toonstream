package com.toonstream.app.presentation.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.tabs.TabLayout
import com.toonstream.app.R
import com.toonstream.app.data.local.AppDatabase
import com.toonstream.app.databinding.FragmentMyListBinding
import com.toonstream.app.domain.model.ContentItem
import com.toonstream.app.presentation.home.AnimeAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MyListFragment : Fragment() {

    private var _binding: FragmentMyListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AnimeAdapter
    private var currentTabIndex = 0 // 0 = Favorites, 1 = History

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val onItemClick: (ContentItem) -> Unit = { item ->
            val bundle = Bundle().apply {
                putString("content_url", item.id)
                putString("content_title", item.title)
                putString("content_poster", item.posterUrl)
                putString("content_type", item.type)
            }
            findNavController().navigate(R.id.navigation_details, bundle)
        }

        adapter = AnimeAdapter(onItemClick)
        binding.recyclerMyList.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerMyList.adapter = adapter

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTabIndex = tab?.position ?: 0
                observeData()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        observeData()
    }

    private fun observeData() {
        val db = AppDatabase.getInstance(requireContext())
        if (currentTabIndex == 0) {
            viewLifecycleOwner.lifecycleScope.launch {
                db.favoritesDao().getAllFavorites().collectLatest { favorites ->
                    val items = favorites.map {
                        ContentItem(
                            id = it.id,
                            title = it.title,
                            posterUrl = it.posterUrl,
                            type = it.type,
                            rating = it.rating,
                            releaseYear = it.releaseYear
                        )
                    }
                    adapter.submitList(items)
                    binding.textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.textEmpty.text = "No favorites saved yet.\nTap the heart icon on any title to save it here!"
                }
            }
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                db.watchHistoryDao().getWatchHistory().collectLatest { history ->
                    val items = history.map {
                        ContentItem(
                            id = it.id,
                            title = it.title,
                            posterUrl = it.posterUrl,
                            episodeLabel = it.episodeTitle
                        )
                    }
                    adapter.submitList(items)
                    binding.textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.textEmpty.text = "No watch history yet.\nVideos you watch will appear here!"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
