package com.adauto.marvel.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * Entry point route for the Movie Detail screen.
 * Collects [MovieDetailUiState] from [MovieDetailViewModel], listens for navigation effects,
 * and delegates user actions.
 */
@Composable
fun MovieDetailRoute(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MovieDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit = onBack
) {
    val effectiveOnBack = if (onBackClick !== onBack) onBackClick else onBack
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel.effects, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is MovieDetailEffect.NavigateBack -> effectiveOnBack()
                }
            }
        }
    }

    MovieDetailScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier
    )
}
