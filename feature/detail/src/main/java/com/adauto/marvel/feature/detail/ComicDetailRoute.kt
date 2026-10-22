package com.adauto.marvel.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Entry point route for the Comic Detail screen.
 * Collects [ComicDetailUiState] from [ComicDetailViewModel], listens for navigation effects,
 * and delegates user actions.
 */
@Composable
fun ComicDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComicDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ComicDetailEffect.NavigateBack -> onBack()
            }
        }
    }

    ComicDetailScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier
    )
}
