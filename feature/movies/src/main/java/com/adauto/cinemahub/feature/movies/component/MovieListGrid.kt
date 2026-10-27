package com.adauto.cinemahub.feature.movies.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubTheme
import com.adauto.cinemahub.core.model.MovieCategory
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.model.TmdbApiException
import com.adauto.cinemahub.core.model.UiText
import com.adauto.cinemahub.core.ui.R
import com.adauto.cinemahub.core.ui.component.AttributionFooter
import com.adauto.cinemahub.core.ui.component.ErrorState
import com.adauto.cinemahub.core.ui.component.MovieCard
import com.adauto.cinemahub.core.ui.component.ShimmerMovieCard
import com.adauto.cinemahub.core.ui.component.rememberShimmerProgress
import com.adauto.cinemahub.core.ui.util.titleRes
import com.adauto.cinemahub.feature.movies.MovieListAction
import com.adauto.cinemahub.feature.movies.MovieListUiState

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_TOO_MANY_REQUESTS = 429

@Composable
fun MovieListGrid(
    movies: LazyPagingItems<MovieSummary>,
    gridState: LazyGridState,
    onAction: (MovieListAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val shimmerProgress = rememberShimmerProgress()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(CinemaHubTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(CinemaHubTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(CinemaHubTheme.spacing.medium),
        modifier = modifier.testTag("MovieListGrid")
    ) {
        items(
            count = movies.itemCount,
            key = movies.itemKey { it.id },
            contentType = movies.itemContentType { "MovieCard" }
        ) { index ->
            val movie = movies[index]
            if (movie != null) {
                MovieCard(
                    movie = movie,
                    onClick = { onAction(MovieListAction.MovieClicked(movie.id)) },
                    onFavoriteToggle = { onAction(MovieListAction.FavoriteToggled(movie)) },
                    modifier = Modifier.testTag("MovieCard_${movie.id}")
                )
            } else {
                ShimmerMovieCard(
                    progress = shimmerProgress,
                    modifier = Modifier.testTag("MovieCardPlaceholder_$index")
                )
            }
        }

        appendLoadingOrError(loadState = movies.loadState.append, onRetry = { movies.retry() })

        item(span = { GridItemSpan(maxLineSpan) }) {
            AttributionFooter(
                modifier = Modifier.testTag("AttributionFooter")
            )
        }
    }
}

private fun LazyGridScope.appendLoadingOrError(
    loadState: LoadState,
    onRetry: () -> Unit
) {
    if (loadState is LoadState.Loading) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CinemaHubTheme.spacing.medium),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("AppendLoadingIndicator")
                )
            }
        }
    }

    if (loadState is LoadState.Error) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ErrorState(
                message = formatPagingError(loadState.error, UiText.Resource(R.string.error_load_more_fallback)),
                onRetry = onRetry,
                modifier = Modifier.testTag("AppendErrorState")
            )
        }
    }
}

@Composable
fun MovieListShimmerGrid(modifier: Modifier = Modifier) {
    val shimmerProgress = rememberShimmerProgress()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(CinemaHubTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(CinemaHubTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(CinemaHubTheme.spacing.medium),
        modifier = modifier.testTag("MovieListShimmerGrid")
    ) {
        items(6) {
            ShimmerMovieCard(progress = shimmerProgress)
        }
    }
}

@Composable
fun MovieListErrorState(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(CinemaHubTheme.spacing.medium),
        contentAlignment = Alignment.Center
    ) {
        ErrorState(
            message = formatPagingError(error, UiText.Resource(R.string.error_load_movies_fallback)),
            onRetry = onRetry,
            modifier = Modifier.testTag("RefreshErrorState")
        )
    }
}

@Composable
fun MovieListEmptyState(
    uiState: MovieListUiState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(CinemaHubTheme.spacing.medium),
        contentAlignment = Alignment.Center
    ) {
        val emptyMessage = when {
            uiState.searchQuery.isNotBlank() ->
                stringResource(
                    R.string.search_empty_message,
                    uiState.searchQuery,
                    stringResource(uiState.selectedCategory.titleRes)
                )
            uiState.selectedCategory == MovieCategory.FAVORITES ->
                stringResource(R.string.empty_favorites_message)
            uiState.selectedCategory == MovieCategory.WATCHLIST ->
                stringResource(R.string.empty_watchlist_message)
            else -> stringResource(R.string.empty_movies_message)
        }
        Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("EmptyStateText")
        )
    }
}

internal fun formatPagingError(throwable: Throwable, fallback: UiText): UiText {
    return when (throwable) {
        is TmdbApiException -> {
            when (throwable.code) {
                HTTP_UNAUTHORIZED -> UiText.Resource(R.string.error_session_expired)
                HTTP_TOO_MANY_REQUESTS -> UiText.Resource(R.string.error_rate_limited)
                else -> throwable.message?.takeIf { it.isNotBlank() }?.let { UiText.Dynamic(it) } ?: fallback
            }
        }
        else -> throwable.localizedMessage?.takeIf { it.isNotBlank() }?.let { UiText.Dynamic(it) } ?: fallback
    }
}
