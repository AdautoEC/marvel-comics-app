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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.model.ComicSummary
import com.adauto.marvel.core.model.MarvelApiException
import com.adauto.marvel.core.ui.component.AttributionFooter
import com.adauto.marvel.core.ui.component.ComicCard
import com.adauto.marvel.core.ui.component.ErrorState
import com.adauto.marvel.core.ui.component.OfflineBanner
import com.adauto.marvel.core.ui.component.ShimmerComicCard

/**
 * Comic List Screen rendering a 2-column grid of Marvel comics with UDF architecture,
 * pull-to-refresh, offline banner, shimmer loading placeholders, pagination append states,
 * and Marvel attribution footer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicListScreen(
    uiState: ComicListUiState,
    comics: LazyPagingItems<ComicSummary>,
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
                            text = "MARVEL COMICS",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            ),
                            color = MarvelColors.MarvelRed
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MarvelColors.DarkBackground,
                        titleContentColor = MarvelColors.MarvelRed
                    )
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
            val isRefreshing = uiState.isRefreshing || (comics.loadState.refresh is LoadState.Loading && comics.itemCount > 0)
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    onAction(ComicListAction.RefreshRequested)
                    comics.refresh()
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    comics.loadState.refresh is LoadState.Loading && comics.itemCount == 0 -> {
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
                                ShimmerComicCard()
                            }
                        }
                    }
                    comics.loadState.refresh is LoadState.Error && comics.itemCount == 0 -> {
                        val error = (comics.loadState.refresh as LoadState.Error).error
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ErrorState(
                                message = formatPagingError(error, "Não foi possível carregar os quadrinhos."),
                                onRetry = {
                                    onAction(ComicListAction.RefreshRequested)
                                    comics.retry()
                                },
                                modifier = Modifier.testTag("RefreshErrorState")
                            )
                        }
                    }
                    comics.itemCount == 0 && comics.loadState.refresh is LoadState.NotLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum quadrinho encontrado.",
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
                                count = comics.itemCount,
                                key = comics.itemKey { it.id }
                            ) { index ->
                                val comic = comics[index]
                                if (comic != null) {
                                    ComicCard(
                                        comic = comic,
                                        onClick = { onAction(ComicListAction.ComicClicked(comic.id)) },
                                        modifier = Modifier.testTag("ComicCard_${comic.id}")
                                    )
                                }
                            }

                            if (comics.loadState.append is LoadState.Loading) {
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

                            if (comics.loadState.append is LoadState.Error) {
                                val appendError = (comics.loadState.append as LoadState.Error).error
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ErrorState(
                                        message = formatPagingError(appendError, "Erro ao carregar mais quadrinhos."),
                                        onRetry = { comics.retry() },
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

private fun formatPagingError(error: Throwable, fallback: String): String {
    return when (error) {
        is MarvelApiException -> when (error.code) {
            401, 403 -> "Chave da API da Marvel inválida."
            429 -> "Limite de requisições diário da Marvel atingido. Tente novamente mais tarde."
            in 500..599 -> "Erro no servidor Marvel (${error.code})."
            else -> error.localizedMessage ?: fallback
        }
        else -> error.localizedMessage ?: fallback
    }
}
