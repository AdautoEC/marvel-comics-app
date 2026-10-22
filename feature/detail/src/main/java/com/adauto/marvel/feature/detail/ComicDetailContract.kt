package com.adauto.marvel.feature.detail

import androidx.compose.runtime.Immutable
import com.adauto.marvel.core.model.ComicDetail
import com.adauto.marvel.core.model.MovieDetail
import com.adauto.marvel.core.model.UiText

@Immutable
data class ComicDetailUiState(
    val isLoading: Boolean = false,
    val comic: ComicDetail? = null,
    val movie: MovieDetail? = null,
    val errorMessage: UiText? = null
)

sealed interface ComicDetailAction {
    data object Retry : ComicDetailAction
    data object BackClicked : ComicDetailAction
    data object FavoriteToggled : ComicDetailAction
    data object WatchedToggled : ComicDetailAction
}

sealed interface ComicDetailEffect {
    data object NavigateBack : ComicDetailEffect
}
