package com.adauto.cinemahub.feature.detail

import androidx.compose.runtime.Immutable
import com.adauto.cinemahub.core.model.MovieDetail
import com.adauto.cinemahub.core.model.UiText

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
}

sealed interface MovieDetailEffect {
    data object NavigateBack : MovieDetailEffect
}
