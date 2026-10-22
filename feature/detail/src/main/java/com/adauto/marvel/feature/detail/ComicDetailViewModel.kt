package com.adauto.marvel.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adauto.marvel.core.data.repository.ComicsRepository
import com.adauto.marvel.core.model.DomainError
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
    private val repository: ComicsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val comicId: Int = checkNotNull(
        savedStateHandle.get<Int>("comicId") ?: savedStateHandle.get<String>("comicId")?.toIntOrNull()
    ) { "comicId is required" }

    private val _uiState = MutableStateFlow(ComicDetailUiState(isLoading = true))
    val uiState: StateFlow<ComicDetailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ComicDetailEffect>(Channel.BUFFERED)
    val effects: Flow<ComicDetailEffect> = _effects.receiveAsFlow()

    init {
        loadComicDetail()
    }

    fun onAction(action: ComicDetailAction) {
        when (action) {
            is ComicDetailAction.Retry -> loadComicDetail()
            is ComicDetailAction.BackClicked -> {
                viewModelScope.launch {
                    _effects.send(ComicDetailEffect.NavigateBack)
                }
            }
        }
    }

    private fun loadComicDetail() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val outcome = repository.getComicDetail(comicId)) {
                is Outcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            comic = outcome.value,
                            errorMessage = null
                        )
                    }
                }
                is Outcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = outcome.error.toUiText()
                        )
                    }
                }
            }
        }
    }
}

private fun DomainError.toUiText(): UiText = when (this) {
    is DomainError.NoConnection -> UiText.Dynamic("Sem conexão com a internet.")
    is DomainError.Timeout -> UiText.Dynamic("Tempo limite esgotado. Tente novamente.")
    is DomainError.NotFound -> UiText.Dynamic("Quadrinho não encontrado.")
    is DomainError.RateLimited -> UiText.Dynamic("Limite de requisições diário da Marvel atingido. Tente novamente mais tarde.")
    is DomainError.InvalidKey -> UiText.Dynamic("Chave da API da Marvel inválida.")
    is DomainError.KeysUnavailable -> UiText.Dynamic("Chaves da Marvel indisponíveis.")
    is DomainError.Server -> UiText.Dynamic("Erro no servidor Marvel ($code).")
    is DomainError.Unknown -> UiText.Dynamic(cause?.message ?: "Ocorreu um erro desconhecido.")
}
