package com.adauto.cinemahub.feature.movies

import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.adauto.cinemahub.core.ui.util.CustomTabLauncher
import com.adauto.cinemahub.core.ui.util.asString

/**
 * Entry point route for the Cinema Hub Movies List screen.
 * Collects UI state, listens for one-off navigation effects via buffered channel,
 * and delegates actions to the [MovieListViewModel].
 */
@Composable
fun MovieListRoute(
    onOpenDetail: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MovieListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val movies = viewModel.movies.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenDetail by rememberUpdatedState(onOpenDetail)

    LaunchedEffect(viewModel.effects, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is MovieListEffect.OpenDetail -> currentOnOpenDetail(effect.id)
                    is MovieListEffect.LaunchTmdbAuth -> {
                        CustomTabLauncher.launchPartialCustomTab(context, Uri.parse(effect.authUrl))
                    }
                    is MovieListEffect.ShowMessage -> {
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
