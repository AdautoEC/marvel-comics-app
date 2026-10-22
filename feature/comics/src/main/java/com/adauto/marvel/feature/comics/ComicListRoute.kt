package com.adauto.marvel.feature.comics

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.adauto.marvel.core.ui.util.asString

/**
 * Entry point route for the Cinema Hub Movies List screen.
 * Collects UI state and paginated movies, listens for one-off effects via buffered channel,
 * and delegates actions to the [ComicListViewModel].
 */
@Composable
fun ComicListRoute(
    onOpenDetail: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComicListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val movies = viewModel.movies.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel.effects, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is ComicListEffect.OpenDetail -> onOpenDetail(effect.id)
                    is ComicListEffect.ShowMessage -> {
                        snackbarHostState.showSnackbar(effect.message.asString(context))
                    }
                }
            }
        }
    }

    MovieListScreen(
        uiState = uiState,
        movies = movies,
        onAction = viewModel::onAction,
        modifier = modifier,
        snackbarHostState = snackbarHostState
    )
}
