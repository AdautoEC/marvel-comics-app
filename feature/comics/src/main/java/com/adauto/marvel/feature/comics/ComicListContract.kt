package com.adauto.marvel.feature.comics

import androidx.compose.runtime.Immutable
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.UiText
import com.adauto.marvel.core.ui.component.DEFAULT_ATTRIBUTION_TEXT

@Immutable
data class ComicListUiState(
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedCategory: MovieCategory = MovieCategory.POPULAR,
    val attribution: String? = DEFAULT_ATTRIBUTION_TEXT
)

sealed interface ComicListAction {
    data object RefreshRequested : ComicListAction
    data class CategorySelected(val category: MovieCategory) : ComicListAction
    data class ComicClicked(val id: Int) : ComicListAction
    data class FavoriteToggled(val movie: MovieSummary) : ComicListAction
    data class WatchedToggled(val movie: MovieSummary) : ComicListAction
}

sealed interface ComicListEffect {
    data class OpenDetail(val id: Int) : ComicListEffect
    data class ShowMessage(val message: UiText) : ComicListEffect
}
