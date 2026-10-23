package com.adauto.marvel.feature.comics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.adauto.marvel.core.data.repository.AuthRepository
import com.adauto.marvel.core.data.repository.ComicsRepository
import com.adauto.marvel.core.data.repository.MoviesRepository
import com.adauto.marvel.core.model.ComicSummary
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.model.UiText
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

private const val TMDB_AUTH_BASE_URL = "https://www.themoviedb.org/authenticate"
private const val TMDB_REDIRECT_URI = "marvelapp://tmdb-callback"

@HiltViewModel
class ComicListViewModel @Inject constructor(
    private val comicsRepository: ComicsRepository,
    private val moviesRepository: MoviesRepository,
    private val authRepository: AuthRepository
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
                MovieCategory.WATCHLIST -> moviesRepository.observeWatched().map { PagingData.from(it) }
                else -> moviesRepository.movies(category)
            }
        }
        .cachedIn(viewModelScope)

    private val _effects = Channel<ComicListEffect>(Channel.BUFFERED)
    val effects: Flow<ComicListEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                _uiState.update { it.copy(sessionState = state) }
            }
        }
    }

    fun onAction(action: ComicListAction) {
        when (action) {
            is ComicListAction.RefreshRequested -> {
                if (_uiState.value.sessionState is SessionState.Authenticated) {
                    viewModelScope.launch {
                        moviesRepository.syncCloudWithLocal()
                    }
                }
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
            is ComicListAction.WatchlistToggled -> {
                viewModelScope.launch {
                    moviesRepository.toggleWatchlist(action.movie)
                }
            }
            is ComicListAction.WatchedToggled -> {
                viewModelScope.launch {
                    moviesRepository.toggleWatched(action.movie)
                }
            }
            is ComicListAction.ConnectTmdbClicked -> {
                viewModelScope.launch {
                    when (val result = authRepository.createRequestToken()) {
                        is Outcome.Success -> {
                            val url = "$TMDB_AUTH_BASE_URL/${result.value}?redirect_to=$TMDB_REDIRECT_URI"
                            _effects.send(ComicListEffect.LaunchTmdbAuth(url))
                        }
                        is Outcome.Failure -> {
                            _effects.send(
                                ComicListEffect.ShowMessage(
                                    UiText.Dynamic("Não foi possível iniciar a conexão com o TMDB.")
                                )
                            )
                        }
                    }
                }
            }
            is ComicListAction.LogoutClicked -> {
                viewModelScope.launch {
                    authRepository.logout()
                }
            }
        }
    }
}
