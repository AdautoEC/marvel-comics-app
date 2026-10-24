package com.adauto.marvel.feature.movies

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
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
import com.adauto.marvel.core.ui.R
import com.adauto.marvel.core.ui.component.AttributionFooter
import com.adauto.marvel.core.ui.component.ErrorState
import com.adauto.marvel.core.ui.component.MovieCard
import com.adauto.marvel.core.ui.component.OfflineBanner
import com.adauto.marvel.core.ui.component.ShimmerMovieCard
import com.adauto.marvel.core.ui.util.titleRes
import com.adauto.marvel.feature.movies.component.GuestSyncBanner
import com.adauto.marvel.feature.movies.component.UserSessionPill
import com.adauto.marvel.feature.movies.preview.MovieListPreviewState
import com.adauto.marvel.feature.movies.preview.MovieListUiStatePreviewParameterProvider

/**
 * Cinema Hub Movies List Screen rendering a 2-column grid of movies with UDF architecture,
 * Category filter tabs (Populares, Mais Votados, Favoritos, Quero Assistir), pull-to-refresh,
 * offline banner, and shimmer loading placeholders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieListScreen(
    uiState: MovieListUiState,
    movies: LazyPagingItems<MovieSummary>,
    onAction: (MovieListAction) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                MovieListTopBar(
                    uiState = uiState,
                    onAction = onAction
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
                            onClick = { onAction(MovieListAction.CategorySelected(category)) },
                            text = {
                                Text(
                                    text = stringResource(category.titleRes),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) MarvelColors.MarvelRed else MarvelColors.TextSecondary
                                )
                            }
                        )
                    }
                }

                // Offline Notice
                if (uiState.isOffline) {
                    OfflineBanner()
                }

                // Guest Sync Banner for offline saved libraries
                if (uiState.selectedCategory in setOf(MovieCategory.FAVORITES, MovieCategory.WATCHLIST)) {
                    GuestSyncBanner(
                        visible = uiState.sessionState is SessionState.Guest,
                        onConnectClicked = { onAction(MovieListAction.ConnectTmdbClicked) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MarvelColors.DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isRefreshing = uiState.isRefreshing || (movies.loadState.refresh is LoadState.Loading && movies.itemCount > 0)
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    onAction(MovieListAction.RefreshRequested)
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
                                .testTag("MovieListShimmerGrid")
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
                                    onAction(MovieListAction.RefreshRequested)
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
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
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
                                .testTag("MovieListGrid")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovieListTopBar(
    uiState: MovieListUiState,
    onAction: (MovieListAction) -> Unit
) {
    if (uiState.isSearchActive) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        TopAppBar(
            navigationIcon = {
                IconButton(
                    onClick = { onAction(MovieListAction.ToggleSearchClicked) },
                    modifier = Modifier.testTag("CloseSearchButton")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Fechar pesquisa",
                        tint = MarvelColors.TextPrimary
                    )
                }
            },
            title = {
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = { onAction(MovieListAction.SearchQueryChanged(it)) },
                    placeholder = {
                        Text(
                            text = stringResource(
                                R.string.search_placeholder,
                                stringResource(uiState.selectedCategory.titleRes)
                            ),
                            color = MarvelColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MarvelColors.MarvelRed,
                        focusedTextColor = MarvelColors.TextPrimary,
                        unfocusedTextColor = MarvelColors.TextPrimary
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("SearchTextField")
                )
            },
            actions = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onAction(MovieListAction.SearchQueryChanged("")) },
                        modifier = Modifier.testTag("ClearSearchButton")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpar pesquisa",
                            tint = MarvelColors.TextSecondary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MarvelColors.DarkBackground
            )
        )
    } else {
        TopAppBar(
            title = {
                Text(
                    text = "CINEMA HUB",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MarvelColors.MarvelRed
                )
            },
            actions = {
                IconButton(
                    onClick = { onAction(MovieListAction.ToggleSearchClicked) },
                    modifier = Modifier.testTag("SearchIconButton")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Pesquisar filmes",
                        tint = MarvelColors.TextPrimary
                    )
                }
                UserSessionPill(
                    sessionState = uiState.sessionState,
                    onConnectClicked = { onAction(MovieListAction.ConnectTmdbClicked) },
                    onLogoutClicked = { onAction(MovieListAction.LogoutClicked) },
                    modifier = Modifier.padding(end = 12.dp)
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MarvelColors.DarkBackground,
                titleContentColor = MarvelColors.MarvelRed
            )
        )
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
