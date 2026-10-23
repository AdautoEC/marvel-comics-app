package com.adauto.marvel.feature.movies

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.adauto.marvel.core.model.MovieSummary
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MovieListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMovies = listOf(
        MovieSummary(id = 101, title = "Clube da Luta", posterUrl = null),
        MovieSummary(id = 102, title = "Inception", posterUrl = null)
    )

    @Test
    fun stateRestoration_preservesScreenStateAcrossRecreation() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            val pagingFlow = remember { flowOf(PagingData.from(sampleMovies)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = pagingItems,
                onAction = {}
            )
        }

        // Emulate process recreation
        restorationTester.emulateSavedInstanceStateRestore()

        // Content remains restored and visible
        composeTestRule.onNodeWithText("Clube da Luta").assertIsDisplayed()
    }

    @Test
    fun movieListScreen_displaysOfflineBannerWhenOffline() {
        composeTestRule.setContent {
            val pagingFlow = remember { flowOf(PagingData.from(sampleMovies)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(isOffline = true),
                movies = pagingItems,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("OfflineBanner").assertIsDisplayed()
    }

    @Test
    fun movieListScreen_displaysAttributionFooter() {
        composeTestRule.setContent {
            val pagingFlow = remember { flowOf(PagingData.from(sampleMovies)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(attribution = "Este produto usa a API TMDB, mas não é endossado ou certificado pelo TMDB."),
                movies = pagingItems,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithText("Este produto usa a API TMDB, mas não é endossado ou certificado pelo TMDB.").assertIsDisplayed()
    }

    @Test
    fun movieListScreen_clickingMovie_triggersMovieClickedAction() {
        var clickedId: Int? = null

        composeTestRule.setContent {
            val pagingFlow = remember { flowOf(PagingData.from(sampleMovies)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()
            MovieListScreen(
                uiState = MovieListUiState(),
                movies = pagingItems,
                onAction = { action ->
                    if (action is MovieListAction.MovieClicked) {
                        clickedId = action.id
                    }
                }
            )
        }

        composeTestRule.onNodeWithText("Clube da Luta").performClick()
        assertEquals(101, clickedId)
    }
}
