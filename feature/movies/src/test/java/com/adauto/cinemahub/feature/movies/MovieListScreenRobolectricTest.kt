package com.adauto.cinemahub.feature.movies

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.adauto.cinemahub.core.model.MovieCategory
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.testing.data.MovieTestData
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class MovieListScreenRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMovies = listOf(
        MovieTestData.createMovieSummary(id = 101, title = "Clube da Luta"),
        MovieTestData.createMovieSummary(id = 102, title = "Inception")
    )

    @Test
    fun movieListScreen_displaysMovieCards_andHandlesClicks() {
        var clickedId: Int? = null

        composeTestRule.setContent {
            val movies = flowOf(PagingData.from(sampleMovies)).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = movies,
                onAction = { action ->
                    if (action is MovieListAction.MovieClicked) {
                        clickedId = action.id
                    }
                }
            )
        }

        composeTestRule.onNodeWithText("Clube da Luta").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()

        composeTestRule.onNodeWithText("Clube da Luta").performClick()
        assertEquals(101, clickedId)
    }

    @Test
    fun movieCard_favoriteButtonClick_dispatchesFavoriteToggledAction() {
        var toggledMovie: MovieSummary? = null

        composeTestRule.setContent {
            val movies = flowOf(PagingData.from(sampleMovies)).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = movies,
                onAction = { action ->
                    if (action is MovieListAction.FavoriteToggled) {
                        toggledMovie = action.movie
                    }
                }
            )
        }

        composeTestRule.onAllNodesWithContentDescription("Favoritar")[0].performClick()
        assertEquals(101, toggledMovie?.id)
    }

    @Test
    fun movieListScreen_displaysOfflineBannerWhenOffline() {
        composeTestRule.setContent {
            val movies = flowOf(PagingData.empty<MovieSummary>()).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(isOffline = true),
                movies = movies,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("OfflineBanner").assertIsDisplayed()
    }

    @Test
    fun movieListScreen_displaysAttributionFooter() {
        composeTestRule.setContent {
            val movies = flowOf(PagingData.from(sampleMovies)).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = movies,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithText(
            "Este produto usa a API TMDB, mas não é endossado ou certificado pelo TMDB."
        ).assertIsDisplayed()
    }

    @Test
    fun categoryTabs_clickingCategory_dispatchesCategorySelectedAction() {
        var selectedCategory: MovieCategory? = null

        composeTestRule.setContent {
            val movies = flowOf(PagingData.from(sampleMovies)).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(selectedCategory = MovieCategory.POPULAR),
                movies = movies,
                onAction = { action ->
                    if (action is MovieListAction.CategorySelected) {
                        selectedCategory = action.category
                    }
                }
            )
        }

        composeTestRule.onNodeWithText("Mais Votados").performClick()
        assertEquals(MovieCategory.TOP_RATED, selectedCategory)
    }

    @Test
    fun movieListScreen_whenFavoritesEmpty_displaysEmptyFavoritesMessage() {
        val notLoadingStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true)
        )
        composeTestRule.setContent {
            val movies = flowOf(
                PagingData.from(emptyList<MovieSummary>(), sourceLoadStates = notLoadingStates)
            ).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(selectedCategory = MovieCategory.FAVORITES),
                movies = movies,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("EmptyStateText").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Nenhum filme favoritado ainda.\nToque no coração para salvar!")
            .assertIsDisplayed()
    }

    @Test
    fun movieListScreen_whenWatchlistEmpty_displaysEmptyWatchlistMessage() {
        val notLoadingStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true)
        )
        composeTestRule.setContent {
            val movies = flowOf(
                PagingData.from(emptyList<MovieSummary>(), sourceLoadStates = notLoadingStates)
            ).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(selectedCategory = MovieCategory.WATCHLIST),
                movies = movies,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("EmptyStateText").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Nenhum filme na lista de Quero Assistir.")
            .assertIsDisplayed()
    }

    @Test
    fun movieListScreen_whenSearchEmpty_displaysEmptySearchMessage() {
        val notLoadingStates = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true)
        )
        composeTestRule.setContent {
            val movies = flowOf(
                PagingData.from(emptyList<MovieSummary>(), sourceLoadStates = notLoadingStates)
            ).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(searchQuery = "Matrix"),
                movies = movies,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("EmptyStateText").assertIsDisplayed()
    }

    @Test
    fun movieListScreen_displaysRefreshErrorState() {
        composeTestRule.setContent {
            val loadStates = LoadStates(
                refresh = LoadState.Error(IOException("Network error")),
                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                append = LoadState.NotLoading(endOfPaginationReached = true)
            )
            val movies = flowOf(
                PagingData.empty<MovieSummary>(sourceLoadStates = loadStates)
            ).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = movies,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithText("Tentar novamente").assertIsDisplayed()
    }

    @Test
    fun movieListScreen_clickableItemsHaveAccessibilitySemantics() {
        composeTestRule.setContent {
            val movies = flowOf(PagingData.from(sampleMovies)).collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = movies,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("MovieCard_101").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        )

        composeTestRule.onNodeWithTag("UserSessionPill_ConnectBtn").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        )
    }
}
