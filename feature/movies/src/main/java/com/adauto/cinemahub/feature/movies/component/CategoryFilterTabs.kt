package com.adauto.cinemahub.feature.movies.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubColors
import com.adauto.cinemahub.core.model.MovieCategory
import com.adauto.cinemahub.core.ui.util.titleRes
import com.adauto.cinemahub.feature.movies.MovieListAction
import com.adauto.cinemahub.feature.movies.MovieListUiState

@Composable
fun CategoryFilterTabs(
    uiState: MovieListUiState,
    onAction: (MovieListAction) -> Unit,
    modifier: Modifier = Modifier
) {
    ScrollableTabRow(
        selectedTabIndex = MovieCategory.entries.indexOf(uiState.selectedCategory).coerceAtLeast(0),
        containerColor = CinemaHubColors.DarkBackground,
        contentColor = CinemaHubColors.CinemaHubRed,
        edgePadding = 16.dp,
        divider = {},
        modifier = modifier
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
                        color = if (selected) CinemaHubColors.CinemaHubRed else CinemaHubColors.TextSecondary
                    )
                }
            )
        }
    }
}
