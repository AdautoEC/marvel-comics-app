package com.adauto.marvel.feature.movies.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.ui.util.titleRes
import com.adauto.marvel.feature.movies.MovieListAction
import com.adauto.marvel.feature.movies.MovieListUiState

@Composable
fun CategoryFilterTabs(
    uiState: MovieListUiState,
    onAction: (MovieListAction) -> Unit,
    modifier: Modifier = Modifier
) {
    ScrollableTabRow(
        selectedTabIndex = MovieCategory.entries.indexOf(uiState.selectedCategory).coerceAtLeast(0),
        containerColor = MarvelColors.DarkBackground,
        contentColor = MarvelColors.MarvelRed,
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
                        color = if (selected) MarvelColors.MarvelRed else MarvelColors.TextSecondary
                    )
                }
            )
        }
    }
}
