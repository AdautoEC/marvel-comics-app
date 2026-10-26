package com.adauto.marvel.feature.movies

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.adauto.marvel.core.ui.util.CustomTabLauncher

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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel.effects, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is MovieListEffect.OpenDetail -> onOpenDetail(effect.id)
                    is MovieListEffect.LaunchTmdbAuth -> {
                        CustomTabLauncher.launchPartialCustomTab(context, Uri.parse(effect.authUrl))
                    }
                    is MovieListEffect.ShowMessage -> Unit
                }
            }
        }
    }

    MovieListScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier
    )
}
