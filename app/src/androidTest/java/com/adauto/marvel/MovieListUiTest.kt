package com.adauto.marvel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.feature.movies.MovieListAction
import com.adauto.marvel.feature.movies.MovieListScreen
import com.adauto.marvel.feature.movies.MovieListUiState
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovieListUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMovies = (1..20).map { id ->
        MovieSummary(
            id = id,
            title = "Filme #$id",
            posterUrl = null,
            releaseDate = "2024-01-01",
            voteAverage = 8.5
        )
    }

    @Test
    fun movieGrid_scrollDown_displaysSubsequentMovies() {
        composeTestRule.setContent {
            val pagingFlow = remember { flowOf(PagingData.from(sampleMovies)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()

            MovieListScreen(
                uiState = MovieListUiState(),
                movies = pagingItems,
                onAction = {}
            )
        }

        // Initial items are visible
        composeTestRule.onNodeWithText("Filme #1").assertIsDisplayed()

        // Scroll down in grid to the 18th movie
        composeTestRule.onNodeWithTag("MovieListGrid")
            .performScrollToIndex(18)

        // Item 18 is now scrolled into view and visible
        composeTestRule.onNodeWithText("Filme #18").assertIsDisplayed()
    }

    @Test
    fun categoryTabs_switchingToFavoritesAndWatchlist_displaysGuestSyncBanner() {
        var dispatchedAction: MovieListAction? = null

        composeTestRule.setContent {
            var selectedCategory by remember { mutableStateOf(MovieCategory.POPULAR) }
            val pagingFlow = remember { flowOf(PagingData.from(sampleMovies)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()

            MovieListScreen(
                uiState = MovieListUiState(
                    sessionState = SessionState.Guest,
                    selectedCategory = selectedCategory
                ),
                movies = pagingItems,
                onAction = { action ->
                    dispatchedAction = action
                    if (action is MovieListAction.CategorySelected) {
                        selectedCategory = action.category
                    }
                }
            )
        }

        // Initially in POPULAR: GuestSyncBanner should not exist/be visible
        composeTestRule.onNodeWithTag("GuestSyncBanner").assertDoesNotExist()

        // Click Favoritos tab
        composeTestRule.onNodeWithText("Favoritos").performClick()
        composeTestRule.waitForIdle()
        assertEquals(MovieListAction.CategorySelected(MovieCategory.FAVORITES), dispatchedAction)

        // Banner is displayed
        composeTestRule.onNodeWithTag("GuestSyncBanner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Seus filmes estão salvos apenas neste aparelho", substring = true).assertIsDisplayed()

        // Click Quero Assistir tab
        composeTestRule.onNodeWithText("Quero Assistir").performClick()
        composeTestRule.waitForIdle()
        assertEquals(MovieListAction.CategorySelected(MovieCategory.WATCHLIST), dispatchedAction)

        // Banner remains displayed for Watchlist in Guest mode
        composeTestRule.onNodeWithTag("GuestSyncBanner").assertIsDisplayed()

        // Return to Populares tab
        composeTestRule.onNodeWithText("Populares").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("GuestSyncBanner").assertDoesNotExist()
    }

    @Test
    fun userSessionPill_whenGuest_clickingConnect_triggersAuthFlow() {
        var connectClicked = false

        composeTestRule.setContent {
            val pagingFlow = remember { flowOf(PagingData.from(sampleMovies)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()

            MovieListScreen(
                uiState = MovieListUiState(sessionState = SessionState.Guest),
                movies = pagingItems,
                onAction = { action ->
                    if (action is MovieListAction.ConnectTmdbClicked) {
                        connectClicked = true
                    }
                }
            )
        }

        // Assert Guest pill is displayed
        composeTestRule.onNodeWithTag("UserSessionPill_Guest").assertIsDisplayed()
        composeTestRule.onNodeWithText("Convidado").assertIsDisplayed()

        // Click Conectar button inside pill
        composeTestRule.onAllNodesWithText("Conectar")[0].performClick()
        assertTrue(connectClicked)
    }

    @Test
    fun guestSyncBanner_clickingConnect_triggersAuthFlow() {
        var connectClicked = false

        composeTestRule.setContent {
            val pagingFlow = remember { flowOf(PagingData.from(sampleMovies)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()

            MovieListScreen(
                uiState = MovieListUiState(
                    sessionState = SessionState.Guest,
                    selectedCategory = MovieCategory.FAVORITES
                ),
                movies = pagingItems,
                onAction = { action ->
                    if (action is MovieListAction.ConnectTmdbClicked) {
                        connectClicked = true
                    }
                }
            )
        }

        composeTestRule.onNodeWithTag("GuestSyncBanner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("GuestSyncBanner_ConnectBtn").performClick()

        assertTrue(connectClicked)
    }
}
