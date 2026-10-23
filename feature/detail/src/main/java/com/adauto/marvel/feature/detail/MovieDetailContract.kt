package com.adauto.marvel.feature.detail

import androidx.compose.runtime.Immutable
import com.adauto.marvel.core.model.MovieDetail
import com.adauto.marvel.core.model.UiText

@Immutable
data class MovieDetailUiState(
    val isLoading: Boolean = false,
    val movie: MovieDetail? = null,
    val errorMessage: UiText? = null
)

sealed interface MovieDetailAction {
    data object Retry : MovieDetailAction
    data object BackClicked : MovieDetailAction
    data object FavoriteToggled : MovieDetailAction
    data object WatchlistToggled : MovieDetailAction
    data object WatchedToggled : MovieDetailAction
}

sealed interface MovieDetailEffect {
    data object NavigateBack : MovieDetailEffect
}
