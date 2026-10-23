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
import com.adauto.marvel.feature.comics.ComicListAction
import com.adauto.marvel.feature.comics.ComicListUiState
import com.adauto.marvel.feature.comics.MovieListScreen
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
            title = "Filme Marvel #$id",
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
                uiState = ComicListUiState(),
                movies = pagingItems,
                onAction = {}
            )
        }

        // Initial items are visible
        composeTestRule.onNodeWithText("Filme Marvel #1").assertIsDisplayed()

        // Scroll down in grid to the 18th movie
        composeTestRule.onNodeWithTag("ComicListGrid")
            .performScrollToIndex(18)

        // Item 18 is now scrolled into view and visible
        composeTestRule.onNodeWithText("Filme Marvel #18").assertIsDisplayed()
    }

    @Test
    fun categoryTabs_switchingToFavoritesAndWatchlist_displaysGuestSyncBanner() {
        var dispatchedAction: ComicListAction? = null

        composeTestRule.setContent {
            var selectedCategory by remember { mutableStateOf(MovieCategory.POPULAR) }
            val pagingFlow = remember { flowOf(PagingData.from(sampleMovies)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()

            MovieListScreen(
                uiState = ComicListUiState(
                    sessionState = SessionState.Guest,
                    selectedCategory = selectedCategory
                ),
                movies = pagingItems,
                onAction = { action ->
                    dispatchedAction = action
                    if (action is ComicListAction.CategorySelected) {
                        selectedCategory = action.category
                    }
                }
            )
        }

        // Initially in POPULAR: GuestSyncBanner should not exist/be visible
        composeTestRule.onNodeWithTag("GuestSyncBanner").assertDoesNotExist()

        // Click Favoritos tab
        composeTestRule.onNodeWithText("Favoritos").performClick()
        assertEquals(ComicListAction.CategorySelected(MovieCategory.FAVORITES), dispatchedAction)

        // Banner is displayed
        composeTestRule.onNodeWithTag("GuestSyncBanner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Seus filmes estão salvos apenas neste aparelho").assertIsDisplayed()

        // Click Quero Assistir tab
        composeTestRule.onNodeWithText("Quero Assistir").performClick()
        assertEquals(ComicListAction.CategorySelected(MovieCategory.WATCHLIST), dispatchedAction)

        // Banner remains displayed for Watchlist in Guest mode
        composeTestRule.onNodeWithTag("GuestSyncBanner").assertIsDisplayed()

        // Return to Populares tab
        composeTestRule.onNodeWithText("Populares").performClick()
        composeTestRule.onNodeWithTag("GuestSyncBanner").assertDoesNotExist()
    }

    @Test
    fun userSessionPill_whenGuest_clickingConnect_triggersAuthFlow() {
        var connectClicked = false

        composeTestRule.setContent {
            val pagingFlow = remember { flowOf(PagingData.from(sampleMovies)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()

            MovieListScreen(
                uiState = ComicListUiState(sessionState = SessionState.Guest),
                movies = pagingItems,
                onAction = { action ->
                    if (action is ComicListAction.ConnectTmdbClicked) {
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
                uiState = ComicListUiState(
                    sessionState = SessionState.Guest,
                    selectedCategory = MovieCategory.FAVORITES
                ),
                movies = pagingItems,
                onAction = { action ->
                    if (action is ComicListAction.ConnectTmdbClicked) {
                        connectClicked = true
                    }
                }
            )
        }

        composeTestRule.onNodeWithTag("GuestSyncBanner").assertIsDisplayed()

        // Find "Conectar" button in banner and click it
        val connectNodes = composeTestRule.onAllNodesWithText("Conectar")
        connectNodes.assertCountEquals(2) // 1 in Pill, 1 in Banner
        connectNodes[1].performClick()

        assertTrue(connectClicked)
    }
}
