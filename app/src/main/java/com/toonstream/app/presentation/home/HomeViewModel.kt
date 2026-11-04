package com.toonstream.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toonstream.app.data.repository.ToonStreamRepository
import com.toonstream.app.domain.model.ContentItem
import com.toonstream.app.domain.model.HomeSections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: ToonStreamRepository = ToonStreamRepository()
) : ViewModel() {

    private val _homeSections = MutableStateFlow(HomeSections())
    val homeSections: StateFlow<HomeSections> = _homeSections

    private val _featuredContent = MutableStateFlow<List<ContentItem>>(emptyList())
    val featuredContent: StateFlow<List<ContentItem>> = _featuredContent

    private val _freshDrop = MutableStateFlow<List<ContentItem>>(emptyList())
    val freshDrop: StateFlow<List<ContentItem>> = _freshDrop

    private val _animeSeries = MutableStateFlow<List<ContentItem>>(emptyList())
    val animeSeries: StateFlow<List<ContentItem>> = _animeSeries

    private val _cartoons = MutableStateFlow<List<ContentItem>>(emptyList())
    val cartoons: StateFlow<List<ContentItem>> = _cartoons

    private val _animeMovies = MutableStateFlow<List<ContentItem>>(emptyList())
    val animeMovies: StateFlow<List<ContentItem>> = _animeMovies

    private val _animatedMovies = MutableStateFlow<List<ContentItem>>(emptyList())
    val animatedMovies: StateFlow<List<ContentItem>> = _animatedMovies

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        loadContent()
    }

    fun loadContent() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = repository.getHome()
            result.fold(
                onSuccess = { sections ->
                    _homeSections.value = sections
                    _featuredContent.value = sections.featured
                    _freshDrop.value = sections.freshDrop
                    _animeSeries.value = sections.animeSeries
                    _cartoons.value = sections.cartoons
                    _animeMovies.value = sections.animeMovies
                    _animatedMovies.value = sections.animatedMovies
                },
                onFailure = { error ->
                    _errorMessage.value = error.localizedMessage ?: "Failed to load content from ToonStream."
                }
            )
            _isLoading.value = false
        }
    }
}
