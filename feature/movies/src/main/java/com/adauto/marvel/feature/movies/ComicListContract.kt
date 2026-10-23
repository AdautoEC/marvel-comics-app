package com.adauto.marvel.feature.movies

import androidx.compose.runtime.Immutable
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.model.UiText
import com.adauto.marvel.core.ui.component.DEFAULT_ATTRIBUTION_TEXT

@Immutable
data class ComicListUiState(
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedCategory: MovieCategory = MovieCategory.POPULAR,
    val sessionState: SessionState = SessionState.Guest,
    val attribution: String? = DEFAULT_ATTRIBUTION_TEXT
)

sealed interface ComicListAction {
    data object RefreshRequested : ComicListAction
    data class CategorySelected(val category: MovieCategory) : ComicListAction
    data class ComicClicked(val id: Int) : ComicListAction
    data class FavoriteToggled(val movie: MovieSummary) : ComicListAction
    data class WatchlistToggled(val movie: MovieSummary) : ComicListAction
    data class WatchedToggled(val movie: MovieSummary) : ComicListAction
    data object ConnectTmdbClicked : ComicListAction
    data object LogoutClicked : ComicListAction
}

sealed interface ComicListEffect {
    data class OpenDetail(val id: Int) : ComicListEffect
    data class ShowMessage(val message: UiText) : ComicListEffect
    data class LaunchTmdbAuth(val authUrl: String) : ComicListEffect
}
