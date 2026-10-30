package com.adauto.cinemahub.feature.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.adauto.cinemahub.core.data.repository.AuthRepository
import com.adauto.cinemahub.core.data.repository.MoviesRepository
import com.adauto.cinemahub.core.data.sync.SyncEventBus
import com.adauto.cinemahub.core.data.usecase.GetMoviesUseCase
import com.adauto.cinemahub.core.data.usecase.ToggleFavoriteUseCase
import com.adauto.cinemahub.core.data.usecase.ToggleWatchlistUseCase
import com.adauto.cinemahub.core.data.util.NetworkMonitor
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.model.Outcome
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.model.UiText
import com.adauto.cinemahub.core.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MILLIS = 300L

@HiltViewModel
@Suppress("LongParameterList","MaxLineLength")
class MovieListViewModel @Inject constructor(
    private val moviesRepository: MoviesRepository,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val toggleWatchlistUseCase: ToggleWatchlistUseCase,
    private val getMoviesUseCase: GetMoviesUseCase,
    syncEventBus: SyncEventBus? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieListUiState())
    val uiState: StateFlow<MovieListUiState> = _uiState.asStateFlow()

    private val categoryFlow = _uiState.map { it.selectedCategory }.distinctUntilChanged()
    private val queryFlow = _uiState.map { it.searchQuery.trim() }.distinctUntilChanged()

    @OptIn(FlowPreview::class)
    private val debouncedQuery = queryFlow.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val moviesFlow: Flow<PagingData<MovieSummary>> = combine(
        categoryFlow,
        debouncedQuery
    ) { category, query ->
        category to query
    }.flatMapLatest { (category, query) ->
        getMoviesUseCase(category, query)
    }

    val movies: Flow<PagingData<MovieSummary>> = moviesFlow.cachedIn(viewModelScope)

    private val _effects = Channel<MovieListEffect>(Channel.BUFFERED)
    val effects: Flow<MovieListEffect> = _effects.receiveAsFlow()

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { state ->
                _uiState.update { it.copy(sessionState = state) }
            }
        }
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _uiState.update { it.copy(isOffline = !isOnline) }
            }
        }
        syncEventBus?.let { bus ->
            viewModelScope.launch {
                bus.unconsumedFailures.collect { failures ->
                    if (failures.isNotEmpty()) {
                        _effects.send(MovieListEffect.ShowMessage(UiText.Resource(R.string.sync_failure_message)))
                        bus.consumeFailures()
                    }
                }
            }
        }
    }

    fun onAction(action: MovieListAction) {
        when (action) {
            is MovieListAction.RefreshRequested -> {
                if (refreshJob?.isActive == true) return
                refreshJob = viewModelScope.launch {
                    _uiState.update { it.copy(isRefreshing = true) }
                    try {
                        if (_uiState.value.sessionState is SessionState.Authenticated) {
                            moviesRepository.syncCloudWithLocal()
                        }
                    } finally {
                        _uiState.update { it.copy(isRefreshing = false) }
                    }
                }
            }
            is MovieListAction.CategorySelected -> {
                _uiState.update {
                    it.copy(
                        selectedCategory = action.category,
                        searchQuery = "",
                        isSearchActive = false
                    )
                }
            }
            is MovieListAction.SearchQueryChanged -> {
                _uiState.update { it.copy(searchQuery = action.query) }
            }
            is MovieListAction.ToggleSearchClicked -> {
                val nextActive = !_uiState.value.isSearchActive
                _uiState.update {
                    it.copy(
                        isSearchActive = nextActive,
                        searchQuery = if (!nextActive) "" else it.searchQuery
                    )
                }
            }
            is MovieListAction.MovieClicked -> {
                viewModelScope.launch {
                    _effects.send(MovieListEffect.OpenDetail(action.id))
                }
            }
            is MovieListAction.FavoriteToggled -> {
                viewModelScope.launch {
                    toggleFavoriteUseCase(action.movie)
                }
            }
            is MovieListAction.WatchlistToggled -> {
                viewModelScope.launch {
                    toggleWatchlistUseCase(action.movie)
                }
            }
            is MovieListAction.ConnectTmdbClicked -> {
                viewModelScope.launch {
                    when (val result = authRepository.buildAuthorizationUrl()) {
                        is Outcome.Success -> {
                            if (result.value.isNotBlank()) {
                                _effects.send(MovieListEffect.LaunchTmdbAuth(result.value))
                            }
                        }
                        is Outcome.Failure -> {
                            val errorMessage = UiText.Resource(R.string.error_tmdb_connect_failed)
                            _effects.send(MovieListEffect.ShowMessage(errorMessage))
                        }
                    }
                }
            }
            is MovieListAction.LogoutClicked -> {
                viewModelScope.launch {
                    authRepository.logout()
                }
            }
        }
    }
}
