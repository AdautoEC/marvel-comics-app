package com.adauto.cinemahub.feature.movies

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.adauto.cinemahub.core.designsystem.preview.DevicePreviews
import com.adauto.cinemahub.core.designsystem.preview.ThemePreviews
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubColors
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubTheme
import com.adauto.cinemahub.core.model.MovieCategory
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.ui.component.AttributionFooter
import com.adauto.cinemahub.core.ui.component.OfflineBanner
import com.adauto.cinemahub.feature.movies.component.CategoryFilterTabs
import com.adauto.cinemahub.feature.movies.component.GuestSyncBanner
import com.adauto.cinemahub.feature.movies.component.MovieListEmptyState
import com.adauto.cinemahub.feature.movies.component.MovieListErrorState
import com.adauto.cinemahub.feature.movies.component.MovieListGrid
import com.adauto.cinemahub.feature.movies.component.MovieListShimmerGrid
import com.adauto.cinemahub.feature.movies.component.MovieListTopBar
import com.adauto.cinemahub.feature.movies.preview.MovieListPreviewState
import com.adauto.cinemahub.feature.movies.preview.MovieListUiStatePreviewParameterProvider
import kotlinx.coroutines.flow.flowOf

/**
 * Cinema Hub Movies List Screen rendering a 2-column grid of movies with UDF architecture,
 * Category filter tabs (Populares, Mais Votados, Favoritos, Quero Assistir), pull-to-refresh,
 * offline banner, and shimmer loading placeholders.
 */
@Composable
fun MovieListScreen(
    uiState: MovieListUiState,
    movies: LazyPagingItems<MovieSummary>,
    onAction: (MovieListAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    MovieListContent(
        uiState = uiState,
        movies = movies,
        onAction = onAction,
        modifier = modifier,
        snackbarHostState = snackbarHostState
    )
}

@Composable
internal fun MovieListContent(
    uiState: MovieListUiState,
    movies: LazyPagingItems<MovieSummary>,
    onAction: (MovieListAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val gridState = rememberLazyGridState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MovieListTopHeader(
                uiState = uiState,
                onAction = onAction,
                modifier = Modifier.fillMaxWidth()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = CinemaHubColors.DarkBackground
    ) { paddingValues ->
        MovieListContentBody(
            uiState = uiState,
            movies = movies,
            gridState = gridState,
            onAction = onAction,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovieListContentBody(
    uiState: MovieListUiState,
    movies: LazyPagingItems<MovieSummary>,
    gridState: LazyGridState,
    onAction: (MovieListAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPullToRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(movies.loadState.refresh) {
        if (movies.loadState.refresh !is LoadState.Loading) {
            isPullToRefreshing = false
        }
    }
    val isRefreshing = uiState.isRefreshing || isPullToRefreshing

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isPullToRefreshing = true
            onAction(MovieListAction.RefreshRequested)
            movies.refresh()
        },
        modifier = modifier
    ) {
        when {
            movies.loadState.refresh is LoadState.Loading && movies.itemCount == 0 -> {
                MovieListShimmerGrid(modifier = Modifier.fillMaxSize())
            }
            movies.loadState.refresh is LoadState.Error && movies.itemCount == 0 -> {
                val error = (movies.loadState.refresh as LoadState.Error).error
                MovieListErrorState(
                    error = error,
                    onRetry = {
                        onAction(MovieListAction.RefreshRequested)
                        movies.retry()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            movies.itemCount == 0 && movies.loadState.refresh is LoadState.NotLoading -> {
                MovieListEmptyState(uiState = uiState, modifier = Modifier.fillMaxSize())
            }
            else -> {
                MovieListGrid(
                    movies = movies,
                    gridState = gridState,
                    onAction = onAction,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun MovieListTopHeader(
    uiState: MovieListUiState,
    onAction: (MovieListAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        MovieListTopBar(uiState = uiState, onAction = onAction)
        CategoryFilterTabs(uiState = uiState, onAction = onAction)
        OfflineBanner(visible = uiState.isOffline)
        if (uiState.selectedCategory in setOf(MovieCategory.FAVORITES, MovieCategory.WATCHLIST)) {
            GuestSyncBanner(
                visible = uiState.sessionState is SessionState.Guest,
                onConnectClicked = { onAction(MovieListAction.ConnectTmdbClicked) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@ThemePreviews
@DevicePreviews
@Composable
private fun MovieListScreenPreview(
    @PreviewParameter(MovieListUiStatePreviewParameterProvider::class) previewState: MovieListPreviewState
) {
    CinemaHubTheme {
        MovieListContentPreview(previewState = previewState)
    }
}

@Composable
internal fun MovieListContentPreview(
    previewState: MovieListPreviewState
) {
    val pagingData = remember(previewState) {
        val loadStates = when {
            previewState.isLoading -> LoadStates(
                refresh = LoadState.Loading,
                prepend = LoadState.NotLoading(endOfPaginationReached = false),
                append = LoadState.NotLoading(endOfPaginationReached = false)
            )
            previewState.errorMessage != null -> LoadStates(
                refresh = LoadState.Error(RuntimeException(previewState.errorMessage)),
                prepend = LoadState.NotLoading(endOfPaginationReached = false),
                append = LoadState.NotLoading(endOfPaginationReached = false)
            )
            else -> LoadStates(
                refresh = LoadState.NotLoading(endOfPaginationReached = true),
                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                append = LoadState.NotLoading(endOfPaginationReached = true)
            )
        }
        PagingData.from(
            data = previewState.movies,
            sourceLoadStates = loadStates
        )
    }
    val movies = flowOf(pagingData).collectAsLazyPagingItems()

    MovieListContent(
        uiState = previewState.uiState,
        movies = movies,
        onAction = {}
    )
}
