package com.adauto.marvel.feature.comics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.adauto.marvel.core.designsystem.preview.DevicePreviews
import com.adauto.marvel.core.designsystem.preview.ThemePreviews
import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.designsystem.theme.MarvelTheme
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.model.TmdbApiException
import com.adauto.marvel.core.model.UiText
import com.adauto.marvel.core.ui.component.AttributionFooter
import com.adauto.marvel.core.ui.component.ErrorState
import com.adauto.marvel.core.ui.component.MovieCard
import com.adauto.marvel.core.ui.component.OfflineBanner
import com.adauto.marvel.core.ui.component.ShimmerMovieCard
import com.adauto.marvel.feature.comics.component.GuestSyncBanner
import com.adauto.marvel.feature.comics.component.UserSessionPill
import com.adauto.marvel.feature.comics.preview.ComicListPreviewState
import com.adauto.marvel.feature.comics.preview.ComicListUiStatePreviewParameterProvider

/**
 * Cinema Hub Movies List Screen rendering a 2-column grid of movies with UDF architecture,
 * Category filter tabs (Populares, Mais Votados, Favoritos, Assistidos), pull-to-refresh,
 * offline banner, and shimmer loading placeholders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieListScreen(
    uiState: ComicListUiState,
    movies: LazyPagingItems<MovieSummary>,
    onAction: (ComicListAction) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Text(
                            text = "CINEMA HUB",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            ),
                            color = MarvelColors.MarvelRed
                        )
                    },
                    actions = {
                        UserSessionPill(
                            sessionState = uiState.sessionState,
                            onConnectClicked = { onAction(ComicListAction.ConnectTmdbClicked) },
                            onLogoutClicked = { onAction(ComicListAction.LogoutClicked) },
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MarvelColors.DarkBackground,
                        titleContentColor = MarvelColors.MarvelRed
                    )
                )

                // Category Filter Tabs
                ScrollableTabRow(
                    selectedTabIndex = MovieCategory.entries.indexOf(uiState.selectedCategory).coerceAtLeast(0),
                    containerColor = MarvelColors.DarkBackground,
                    contentColor = MarvelColors.MarvelRed,
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    MovieCategory.entries.forEach { category ->
                        val selected = category == uiState.selectedCategory
                        Tab(
                            selected = selected,
                            onClick = { onAction(ComicListAction.CategorySelected(category)) },
                            text = {
                                Text(
                                    text = category.displayName,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) MarvelColors.MarvelRed else MarvelColors.TextSecondary
                                )
                            }
                        )
                    }
                }

                // Guest Sync Banner when in Favorites or Watchlist and user is Guest
                val showGuestSyncBanner = uiState.sessionState is SessionState.Guest &&
                    (uiState.selectedCategory == MovieCategory.FAVORITES || uiState.selectedCategory == MovieCategory.WATCHLIST)

                GuestSyncBanner(
                    onConnectClicked = { onAction(ComicListAction.ConnectTmdbClicked) },
                    visible = showGuestSyncBanner
                )

                OfflineBanner(
                    visible = uiState.isOffline,
                    modifier = Modifier.testTag("OfflineBanner")
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MarvelColors.DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isRefreshing = uiState.isRefreshing || (movies.loadState.refresh is LoadState.Loading && movies.itemCount > 0)
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    onAction(ComicListAction.RefreshRequested)
                    movies.refresh()
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    movies.loadState.refresh is LoadState.Loading && movies.itemCount == 0 -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = gridState,
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("ComicListShimmerGrid")
                        ) {
                            items(6) {
                                ShimmerMovieCard()
                            }
                        }
                    }
                    movies.loadState.refresh is LoadState.Error && movies.itemCount == 0 -> {
                        val error = (movies.loadState.refresh as LoadState.Error).error
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ErrorState(
                                message = formatPagingError(error, "Não foi possível carregar os filmes."),
                                onRetry = {
                                    onAction(ComicListAction.RefreshRequested)
                                    movies.retry()
                                },
                                modifier = Modifier.testTag("RefreshErrorState")
                            )
                        }
                    }
                    movies.itemCount == 0 && movies.loadState.refresh is LoadState.NotLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val emptyMessage = when (uiState.selectedCategory) {
                                MovieCategory.FAVORITES -> "Nenhum filme favoritado ainda.\nToque no coração para salvar!"
                                MovieCategory.WATCHLIST -> "Nenhum filme na lista de Quero Assistir."
                                else -> "Nenhum filme encontrado."
                            }
                            Text(
                                text = emptyMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MarvelColors.TextSecondary,
                                modifier = Modifier.testTag("EmptyStateText")
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = gridState,
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("ComicListGrid")
                        ) {
                            items(
                                count = movies.itemCount,
                                key = movies.itemKey { it.id }
                            ) { index ->
                                val movie = movies[index]
                                if (movie != null) {
                                    MovieCard(
                                        movie = movie,
                                        onClick = { onAction(ComicListAction.ComicClicked(movie.id)) },
                                        onFavoriteToggle = { onAction(ComicListAction.FavoriteToggled(movie)) },
                                        modifier = Modifier.testTag("ComicCard_${movie.id}")
                                    )
                                }
                            }

                            if (movies.loadState.append is LoadState.Loading) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = MarvelColors.MarvelRed,
                                            modifier = Modifier.testTag("AppendLoadingIndicator")
                                        )
                                    }
                                }
                            }

                            if (movies.loadState.append is LoadState.Error) {
                                val appendError = (movies.loadState.append as LoadState.Error).error
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ErrorState(
                                        message = formatPagingError(appendError, "Erro ao carregar mais filmes."),
                                        onRetry = { movies.retry() },
                                        modifier = Modifier.testTag("AppendErrorState")
                                    )
                                }
                            }

                            if (uiState.attribution != null) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    AttributionFooter(
                                        text = uiState.attribution,
                                        modifier = Modifier.testTag("AttributionFooter")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun formatPagingError(throwable: Throwable, fallback: String): UiText {
    return when (throwable) {
        is TmdbApiException -> {
            when (throwable.code) {
                429 -> UiText.Dynamic("Limite de requisições excedido. Tente mais tarde.")
                401 -> UiText.Dynamic("Chave de API inválida.")
                else -> UiText.Dynamic(throwable.message ?: fallback)
            }
        }
        else -> UiText.Dynamic(throwable.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback)
    }
}

@ThemePreviews
@DevicePreviews
@Composable
private fun ComicListScreenPreview(
    @PreviewParameter(ComicListUiStatePreviewParameterProvider::class) previewState: ComicListPreviewState
) {
    MarvelTheme {
        ComicListContentPreview(previewState = previewState)
    }
}

@Composable
internal fun ComicListContentPreview(
    previewState: ComicListPreviewState
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("PreviewContent")
    ) {
        Text(
            text = "Cinema Hub Preview (${previewState.uiState.selectedCategory.displayName})",
            color = MarvelColors.TextPrimary,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
