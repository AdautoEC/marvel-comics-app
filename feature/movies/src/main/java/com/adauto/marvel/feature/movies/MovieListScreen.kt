package com.adauto.marvel.feature.movies

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.adauto.marvel.core.designsystem.preview.DevicePreviews
import com.adauto.marvel.core.designsystem.preview.ThemePreviews
import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.designsystem.theme.MarvelTheme
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.ui.component.AttributionFooter
import com.adauto.marvel.core.ui.component.OfflineBanner
import com.adauto.marvel.core.ui.util.asString
import com.adauto.marvel.feature.movies.component.CategoryFilterTabs
import com.adauto.marvel.feature.movies.component.GuestSyncBanner
import com.adauto.marvel.feature.movies.component.MovieListEmptyState
import com.adauto.marvel.feature.movies.component.MovieListErrorState
import com.adauto.marvel.feature.movies.component.MovieListGrid
import com.adauto.marvel.feature.movies.component.MovieListShimmerGrid
import com.adauto.marvel.feature.movies.component.MovieListTopBar
import com.adauto.marvel.feature.movies.preview.MovieListPreviewState
import com.adauto.marvel.feature.movies.preview.MovieListUiStatePreviewParameterProvider
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
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message.asString(context))
            onAction(MovieListAction.UserMessageDismissed)
        }
    }

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
        containerColor = MarvelColors.DarkBackground
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
    val isRefreshing = uiState.isRefreshing ||
        (movies.loadState.refresh is LoadState.Loading && movies.itemCount > 0)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
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
    MarvelTheme {
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
