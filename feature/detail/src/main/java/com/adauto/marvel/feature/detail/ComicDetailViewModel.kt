package com.adauto.marvel.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adauto.marvel.core.data.repository.ComicsRepository
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
class ComicDetailViewModel @Inject constructor(
    private val comicsRepository: ComicsRepository,
    private val moviesRepository: MoviesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val movieId: Int = checkNotNull(
        savedStateHandle.get<Int>("comicId") ?: savedStateHandle.get<String>("comicId")?.toIntOrNull()
    ) { "comicId is required" }

    private val _uiState = MutableStateFlow(ComicDetailUiState(isLoading = true))
    val uiState: StateFlow<ComicDetailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ComicDetailEffect>(Channel.BUFFERED)
    val effects: Flow<ComicDetailEffect> = _effects.receiveAsFlow()

    init {
        loadDetail()
    }

    fun onAction(action: ComicDetailAction) {
        when (action) {
            is ComicDetailAction.Retry -> loadDetail()
            is ComicDetailAction.BackClicked -> {
                viewModelScope.launch {
                    _effects.send(ComicDetailEffect.NavigateBack)
                }
            }
            is ComicDetailAction.FavoriteToggled -> {
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
            is ComicDetailAction.WatchedToggled -> {
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
                    moviesRepository.toggleWatched(summary)
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
                    // Try fallback comic detail if any
                    when (val comicOutcome = comicsRepository.getComicDetail(movieId)) {
                        is Outcome.Success -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    comic = comicOutcome.value,
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
