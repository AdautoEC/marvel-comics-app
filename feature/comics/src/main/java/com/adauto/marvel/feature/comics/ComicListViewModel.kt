package com.adauto.marvel.feature.comics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.adauto.marvel.core.data.repository.ComicsRepository
import com.adauto.marvel.core.data.repository.MoviesRepository
import com.adauto.marvel.core.model.ComicSummary
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.MovieSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComicListViewModel @Inject constructor(
    private val comicsRepository: ComicsRepository,
    private val moviesRepository: MoviesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComicListUiState())
    val uiState: StateFlow<ComicListUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow(MovieCategory.POPULAR)

    val comics: Flow<PagingData<ComicSummary>> = comicsRepository.comics().cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val movies: Flow<PagingData<MovieSummary>> = _selectedCategory
        .flatMapLatest { category ->
            when (category) {
                MovieCategory.FAVORITES -> moviesRepository.observeFavorites().map { PagingData.from(it) }
                MovieCategory.WATCHED -> moviesRepository.observeWatched().map { PagingData.from(it) }
                else -> moviesRepository.movies(category)
            }
        }
        .cachedIn(viewModelScope)

    private val _effects = Channel<ComicListEffect>(Channel.BUFFERED)
    val effects: Flow<ComicListEffect> = _effects.receiveAsFlow()

    fun onAction(action: ComicListAction) {
        when (action) {
            is ComicListAction.RefreshRequested -> {
                // Paging 3 refresh handled in UI
            }
            is ComicListAction.CategorySelected -> {
                _selectedCategory.value = action.category
                _uiState.update { it.copy(selectedCategory = action.category) }
            }
            is ComicListAction.ComicClicked -> {
                viewModelScope.launch {
                    _effects.send(ComicListEffect.OpenDetail(action.id))
                }
            }
            is ComicListAction.FavoriteToggled -> {
                viewModelScope.launch {
                    moviesRepository.toggleFavorite(action.movie)
                }
            }
            is ComicListAction.WatchedToggled -> {
                viewModelScope.launch {
                    moviesRepository.toggleWatched(action.movie)
                }
            }
        }
    }
}
