package com.adauto.marvel.feature.movies

import androidx.compose.runtime.Immutable
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.model.UiText

@Immutable
data class MovieListUiState(
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedCategory: MovieCategory = MovieCategory.POPULAR,
    val sessionState: SessionState = SessionState.Guest,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false
)

sealed interface MovieListAction {
    data object RefreshRequested : MovieListAction
    data class CategorySelected(val category: MovieCategory) : MovieListAction
    data class MovieClicked(val id: Int) : MovieListAction
    data class FavoriteToggled(val movie: MovieSummary) : MovieListAction
    data class WatchlistToggled(val movie: MovieSummary) : MovieListAction
    data object ConnectTmdbClicked : MovieListAction
    data object LogoutClicked : MovieListAction
    data class SearchQueryChanged(val query: String) : MovieListAction
    data object ToggleSearchClicked : MovieListAction
}

sealed interface MovieListEffect {
    data class OpenDetail(val id: Int) : MovieListEffect
    data class ShowMessage(val message: UiText) : MovieListEffect
    data class LaunchTmdbAuth(val authUrl: String) : MovieListEffect
}
