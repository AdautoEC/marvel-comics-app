package com.adauto.cinemahub.feature.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adauto.cinemahub.core.model.MovieDetail
import com.adauto.cinemahub.core.model.UiText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovieDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMovie = MovieDetail(
        id = 101,
        title = "Inception",
        overview = "Peter Parker takes on the world in this historic issue.",
        posterUrl = null,
        backdropUrl = null,
        releaseDate = "2024-01-15",
        voteAverage = 8.8,
        runtime = 148,
        genres = listOf("Ação", "Ficção Científica"),
        cast = listOf("Leonardo DiCaprio", "Joseph Gordon-Levitt"),
        isFavorite = false,
        isInWatchlist = false
    )

    @Test
    fun movieDetailScreen_displaysLoadingSkeleton_whenLoading() {
        composeTestRule.setContent {
            MovieDetailScreen(
                uiState = MovieDetailUiState(isLoading = true),
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("MovieDetailLoadingShimmer").assertIsDisplayed()
    }

    @Test
    fun movieDetailScreen_displaysPopulatedContent_andHandlesBackButton() {
        var backClicked = false

        composeTestRule.setContent {
            MovieDetailScreen(
                uiState = MovieDetailUiState(movie = sampleMovie),
                onAction = { action ->
                    if (action is MovieDetailAction.BackClicked) {
                        backClicked = true
                    }
                }
            )
        }

        composeTestRule.onNodeWithTag("MovieDetailContent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()
        composeTestRule.onNodeWithText("Leonardo DiCaprio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Joseph Gordon-Levitt").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Voltar").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun movieDetailScreen_displaysErrorState_andTriggersRetry() {
        var retryTriggered = false

        composeTestRule.setContent {
            MovieDetailScreen(
                uiState = MovieDetailUiState(
                    errorMessage = UiText.Dynamic("Falha ao carregar detalhes")
                ),
                onAction = { action ->
                    if (action is MovieDetailAction.Retry) {
                        retryTriggered = true
                    }
                }
            )
        }

        composeTestRule.onNodeWithTag("DetailErrorState").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tentar novamente").performClick()
        assertTrue(retryTriggered)
    }

    @Test
    fun stateRestoration_preservesDetailScreenStateAcrossRecreation() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            MovieDetailScreen(
                uiState = MovieDetailUiState(movie = sampleMovie),
                onAction = {}
            )
        }

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()
    }
}
