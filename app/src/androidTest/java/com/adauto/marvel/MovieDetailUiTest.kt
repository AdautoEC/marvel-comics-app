package com.adauto.marvel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adauto.marvel.core.model.MovieDetail
import com.adauto.marvel.feature.detail.MovieDetailAction
import com.adauto.marvel.feature.detail.MovieDetailScreen
import com.adauto.marvel.feature.detail.MovieDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovieDetailUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMovie = MovieDetail(
        id = 550,
        title = "Clube da Luta",
        overview = "Um homem deprimido que sofre de insônia conhece um vendedor estranho de sabonetes e funda um clube de luta clandestino que se transforma em algo muito mais grandioso.",
        posterUrl = null,
        backdropUrl = null,
        releaseDate = "1999-10-15",
        voteAverage = 8.8,
        runtime = 139,
        genres = listOf("Drama", "Suspense"),
        isFavorite = false,
        isWatched = false
    )

    @Test
    fun movieDetail_rendersTitleRatingAndOverview() {
        composeTestRule.setContent {
            MovieDetailScreen(
                uiState = MovieDetailUiState(
                    movie = sampleMovie,
                    isLoading = false
                ),
                onAction = {}
            )
        }

        // Validate key elements are displayed
        composeTestRule.onNodeWithText("Clube da Luta").assertIsDisplayed()
        composeTestRule.onNodeWithText("1999").assertIsDisplayed()
        composeTestRule.onNodeWithText("8.8").assertIsDisplayed()
        composeTestRule.onNodeWithText("139 min").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sinopse").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HeroPoster").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BackButton").assertIsDisplayed()
    }

    @Test
    fun favoriteButton_clicking_dispatchesFavoriteToggledAction() {
        var actionDispatched: MovieDetailAction? = null

        composeTestRule.setContent {
            MovieDetailScreen(
                uiState = MovieDetailUiState(
                    movie = sampleMovie.copy(isFavorite = false),
                    isLoading = false
                ),
                onAction = { actionDispatched = it }
            )
        }

        composeTestRule.onNodeWithTag("FavoriteButton").assertIsDisplayed()
        composeTestRule.onNodeWithText("Favoritar").assertIsDisplayed()

        composeTestRule.onNodeWithTag("FavoriteButton").performClick()
        assertEquals(MovieDetailAction.FavoriteToggled, actionDispatched)
    }

    @Test
    fun watchlistButton_clicking_dispatchesWatchlistToggledAction() {
        var actionDispatched: MovieDetailAction? = null

        composeTestRule.setContent {
            MovieDetailScreen(
                uiState = MovieDetailUiState(
                    movie = sampleMovie.copy(isWatched = false),
                    isLoading = false
                ),
                onAction = { actionDispatched = it }
            )
        }

        composeTestRule.onNodeWithTag("WatchlistButton").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quero Assistir").assertIsDisplayed()

        composeTestRule.onNodeWithTag("WatchlistButton").performClick()
        assertEquals(MovieDetailAction.WatchlistToggled, actionDispatched)
    }

    @Test
    fun backButton_clicking_dispatchesBackClickedAction() {
        var backClicked = false

        composeTestRule.setContent {
            MovieDetailScreen(
                uiState = MovieDetailUiState(
                    movie = sampleMovie,
                    isLoading = false
                ),
                onAction = { action ->
                    if (action is MovieDetailAction.BackClicked) {
                        backClicked = true
                    }
                }
            )
        }

        composeTestRule.onNodeWithTag("BackButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BackButton").performClick()

        assertTrue(backClicked)
    }

    @Test
    fun movieDetail_scrolling_revealsOverviewContent() {
        composeTestRule.setContent {
            MovieDetailScreen(
                uiState = MovieDetailUiState(
                    movie = sampleMovie,
                    isLoading = false
                ),
                onAction = {}
            )
        }

        // Swipe up to scroll down through parallax content
        composeTestRule.onNodeWithTag("MovieDetailContent").performTouchInput {
            swipeUp()
        }

        // Verify synopsis remains visible after scroll
        composeTestRule.onNodeWithText("Sinopse").assertIsDisplayed()
    }
}
