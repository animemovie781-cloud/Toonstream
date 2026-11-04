package com.toonstream.app.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toonstream.app.data.repository.ToonStreamRepository
import com.toonstream.app.domain.model.ContentItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: ToonStreamRepository = ToonStreamRepository()
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<ContentItem>>(emptyList())
    val searchResults: StateFlow<List<ContentItem>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _statusText = MutableStateFlow<String?>("Popular searches: Solo Leveling, One Piece, Naruto, Dragon Ball...")
    val statusText: StateFlow<String?> = _statusText

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _searchResults.value = emptyList()
            _isLoading.value = false
            _statusText.value = "Type at least 2 characters to search ToonStream."
            return
        }

        searchJob = viewModelScope.launch {
            delay(350) // debounce typing
            performSearch(trimmed)
        }
    }

    fun performSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = "Searching for \"$trimmed\"..."

            val result = repository.search(trimmed)
            result.fold(
                onSuccess = { items ->
                    _searchResults.value = items
                    _statusText.value = if (items.isNotEmpty()) {
                        "Found ${items.size} results for \"$trimmed\""
                    } else {
                        "No titles found matching \"$trimmed\"."
                    }
                },
                onFailure = { error ->
                    _searchResults.value = emptyList()
                    _statusText.value = "Search error: ${error.localizedMessage}"
                }
            )
            _isLoading.value = false
        }
    }
}
