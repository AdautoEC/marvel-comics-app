package com.adauto.marvel.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adauto.marvel.core.data.repository.MoviesRepository
import com.adauto.marvel.core.model.DomainError
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.core.model.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val moviesRepository: MoviesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val movieId: Int = checkNotNull(
        savedStateHandle.get<Int>("movieId")
            ?: savedStateHandle.get<String>("movieId")?.toIntOrNull()
            ?: savedStateHandle.get<Int>("comicId")
            ?: savedStateHandle.get<String>("comicId")?.toIntOrNull()
    ) { "movieId is required" }

    private val _uiState = MutableStateFlow(MovieDetailUiState(isLoading = true))
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<MovieDetailEffect>(Channel.BUFFERED)
    val effects: Flow<MovieDetailEffect> = _effects.receiveAsFlow()

    init {
        loadDetail()
    }

    fun onAction(action: MovieDetailAction) {
        when (action) {
            is MovieDetailAction.Retry -> loadDetail()
            is MovieDetailAction.BackClicked -> {
                viewModelScope.launch {
                    _effects.send(MovieDetailEffect.NavigateBack)
                }
            }
            is MovieDetailAction.FavoriteToggled -> {
                val current = _uiState.value.movie ?: return
                val summary = MovieSummary(
                    id = current.id,
                    title = current.title,
                    posterUrl = current.posterUrl,
                    voteAverage = current.voteAverage,
                    releaseDate = current.releaseDate,
                    isFavorite = current.isFavorite
                )
                viewModelScope.launch {
                    moviesRepository.toggleFavorite(summary)
                    _uiState.update { it.copy(movie = current.copy(isFavorite = !current.isFavorite)) }
                }
            }
            is MovieDetailAction.WatchlistToggled, is MovieDetailAction.WatchedToggled -> {
                val current = _uiState.value.movie ?: return
                val summary = MovieSummary(
                    id = current.id,
                    title = current.title,
                    posterUrl = current.posterUrl,
                    voteAverage = current.voteAverage,
                    releaseDate = current.releaseDate,
                    isWatched = current.isWatched
                )
                viewModelScope.launch {
                    moviesRepository.toggleWatchlist(summary)
                    _uiState.update { it.copy(movie = current.copy(isWatched = !current.isWatched)) }
                }
            }
        }
    }

    private fun loadDetail() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val movieOutcome = moviesRepository.getMovieDetail(movieId)) {
                is Outcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            movie = movieOutcome.value,
                            errorMessage = null
                        )
                    }
                }
                is Outcome.Failure -> {
                    val errorText = mapDomainErrorToUiText(movieOutcome.error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = errorText
                        )
                    }
                }
            }
        }
    }

    private fun mapDomainErrorToUiText(error: DomainError): UiText {
        return when (error) {
            is DomainError.NoConnection -> UiText.Dynamic("Sem conexão com a internet.")
            is DomainError.Timeout -> UiText.Dynamic("A requisição demorou muito para responder.")
            is DomainError.NotFound -> UiText.Dynamic("Filme não encontrado.")
            is DomainError.RateLimited -> UiText.Dynamic("Limite de requisições atingido.")
            is DomainError.InvalidKey -> UiText.Dynamic("Chave de autenticação inválida.")
            is DomainError.KeysUnavailable -> UiText.Dynamic("Credenciais não disponíveis.")
            is DomainError.Server -> UiText.Dynamic("Erro no servidor (${error.code}).")
            is DomainError.Unknown -> UiText.Dynamic("Ocorreu um erro inesperado.")
        }
    }
}
