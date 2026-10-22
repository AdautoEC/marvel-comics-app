package com.adauto.marvel.feature.comics

import androidx.compose.runtime.Immutable
import com.adauto.marvel.core.model.UiText
import com.adauto.marvel.core.ui.component.DEFAULT_ATTRIBUTION_TEXT

@Immutable
data class ComicListUiState(
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val attribution: String? = DEFAULT_ATTRIBUTION_TEXT
)

sealed interface ComicListAction {
    data object RefreshRequested : ComicListAction
    data class ComicClicked(val id: Int) : ComicListAction
}

sealed interface ComicListEffect {
    data class OpenDetail(val id: Int) : ComicListEffect
    data class ShowMessage(val message: UiText) : ComicListEffect
}
