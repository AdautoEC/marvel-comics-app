package com.adauto.marvel.feature.comics

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.adauto.marvel.core.ui.util.asString

/**
 * Entry point route for the Comic List screen.
 * Collects UI state and paginated items, listens for one-off effects via buffered channel,
 * and delegates actions to the [ComicListViewModel].
 */
@Composable
fun ComicListRoute(
    onOpenDetail: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComicListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val comics = viewModel.comics.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ComicListEffect.OpenDetail -> onOpenDetail(effect.id)
                is ComicListEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message.asString(context))
                }
            }
        }
    }

    ComicListScreen(
        uiState = uiState,
        comics = comics,
        onAction = viewModel::onAction,
        modifier = modifier,
        snackbarHostState = snackbarHostState
    )
}
