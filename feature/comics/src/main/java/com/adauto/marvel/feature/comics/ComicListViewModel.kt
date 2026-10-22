package com.adauto.marvel.feature.comics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.adauto.marvel.core.data.repository.ComicsRepository
import com.adauto.marvel.core.model.ComicSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComicListViewModel @Inject constructor(
    private val repository: ComicsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComicListUiState())
    val uiState: StateFlow<ComicListUiState> = _uiState.asStateFlow()

    val comics: Flow<PagingData<ComicSummary>> = repository.comics().cachedIn(viewModelScope)

    private val _effects = Channel<ComicListEffect>(Channel.BUFFERED)
    val effects: Flow<ComicListEffect> = _effects.receiveAsFlow()

    fun onAction(action: ComicListAction) {
        when (action) {
            is ComicListAction.RefreshRequested -> {
                // Handled via PagingData refresh in UI layer
            }
            is ComicListAction.ComicClicked -> {
                viewModelScope.launch {
                    _effects.send(ComicListEffect.OpenDetail(action.id))
                }
            }
        }
    }
}
