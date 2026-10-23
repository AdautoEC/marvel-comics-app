package com.adauto.marvel.feature.movies

import androidx.compose.runtime.Immutable
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.model.UiText
import com.adauto.marvel.core.ui.component.DEFAULT_ATTRIBUTION_TEXT

@Immutable
data class MovieListUiState(
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedCategory: MovieCategory = MovieCategory.POPULAR,
    val sessionState: SessionState = SessionState.Guest,
    val attribution: String? = DEFAULT_ATTRIBUTION_TEXT
)

sealed interface MovieListAction {
    data object RefreshRequested : MovieListAction
    data class CategorySelected(val category: MovieCategory) : MovieListAction
    data class MovieClicked(val id: Int) : MovieListAction
    data class FavoriteToggled(val movie: MovieSummary) : MovieListAction
    data class WatchlistToggled(val movie: MovieSummary) : MovieListAction
    data class WatchedToggled(val movie: MovieSummary) : MovieListAction
    data object ConnectTmdbClicked : MovieListAction
    data object LogoutClicked : MovieListAction
}

sealed interface MovieListEffect {
    data class OpenDetail(val id: Int) : MovieListEffect
    data class ShowMessage(val message: UiText) : MovieListEffect
    data class LaunchTmdbAuth(val authUrl: String) : MovieListEffect
}
