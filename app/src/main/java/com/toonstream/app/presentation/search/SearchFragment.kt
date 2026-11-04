package com.toonstream.app.presentation.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.toonstream.app.R
import com.toonstream.app.databinding.FragmentSearchBinding
import com.toonstream.app.domain.model.ContentItem
import com.toonstream.app.presentation.home.AnimeAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var adapter: AnimeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
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
        binding.recyclerSearchResults.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerSearchResults.adapter = adapter

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnClear.setOnClickListener {
            binding.editSearch.text?.clear()
        }

        binding.editSearch.addTextChangedListener { text ->
            val query = text?.toString().orEmpty()
            binding.btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            viewModel.onQueryChanged(query)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.searchProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statusText.collectLatest { status ->
                binding.textStatus.text = status.orEmpty()
                binding.textStatus.visibility = if (status.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchResults.collectLatest { results ->
                adapter.submitList(results)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
