package com.adauto.marvel.feature.movies.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.ui.R
import com.adauto.marvel.core.ui.util.titleRes
import com.adauto.marvel.feature.movies.MovieListAction
import com.adauto.marvel.feature.movies.MovieListUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieListTopBar(
    uiState: MovieListUiState,
    onAction: (MovieListAction) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isSearchActive) {
        SearchTopBar(uiState = uiState, onAction = onAction, modifier = modifier)
    } else {
        StandardTopBar(uiState = uiState, onAction = onAction, modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    uiState: MovieListUiState,
    onAction: (MovieListAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(
                onClick = { onAction(MovieListAction.ToggleSearchClicked) },
                modifier = Modifier.testTag("CloseSearchButton")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_search_close),
                    tint = MarvelColors.TextPrimary
                )
            }
        },
        title = {
            SearchTextField(
                uiState = uiState,
                focusRequester = focusRequester,
                onAction = onAction
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
                        contentDescription = stringResource(R.string.cd_search_clear),
                        tint = MarvelColors.TextSecondary
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MarvelColors.DarkBackground
        )
    )
}

@Composable
private fun searchTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = MarvelColors.MarvelRed,
    focusedTextColor = MarvelColors.TextPrimary,
    unfocusedTextColor = MarvelColors.TextPrimary
)

@Composable
private fun SearchTextField(
    uiState: MovieListUiState,
    focusRequester: FocusRequester,
    onAction: (MovieListAction) -> Unit,
    modifier: Modifier = Modifier
) {
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
        colors = searchTextFieldColors(),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .testTag("SearchTextField")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StandardTopBar(
    uiState: MovieListUiState,
    onAction: (MovieListAction) -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.app_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black
                ),
                color = MarvelColors.MarvelRed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            IconButton(
                onClick = { onAction(MovieListAction.ToggleSearchClicked) },
                modifier = Modifier.testTag("SearchIconButton")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.cd_search_open),
                    tint = MarvelColors.TextPrimary
                )
            }
            UserSessionPill(
                sessionState = uiState.sessionState,
                onConnectClicked = { onAction(MovieListAction.ConnectTmdbClicked) },
                onLogoutClicked = { onAction(MovieListAction.LogoutClicked) }
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MarvelColors.DarkBackground
        )
    )
}
