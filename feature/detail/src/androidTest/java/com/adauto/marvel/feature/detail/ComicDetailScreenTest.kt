package com.adauto.marvel.feature.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adauto.marvel.core.model.MovieDetail
import com.adauto.marvel.core.model.UiText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComicDetailScreenTest {

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
        isWatched = false
    )

    @Test
    fun comicDetailScreen_displaysLoadingSkeleton_whenLoading() {
        composeTestRule.setContent {
            ComicDetailScreen(
                uiState = ComicDetailUiState(isLoading = true),
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("ComicDetailLoadingShimmer").assertIsDisplayed()
    }

    @Test
    fun comicDetailScreen_displaysPopulatedContent_andHandlesBackButton() {
        var backClicked = false

        composeTestRule.setContent {
            ComicDetailScreen(
                uiState = ComicDetailUiState(movie = sampleMovie),
                onAction = { action ->
                    if (action is ComicDetailAction.BackClicked) {
                        backClicked = true
                    }
                }
            )
        }

        composeTestRule.onNodeWithTag("ComicDetailContent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()
        composeTestRule.onNodeWithText("Leonardo DiCaprio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Joseph Gordon-Levitt").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Voltar").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun comicDetailScreen_displaysErrorState_andTriggersRetry() {
        var retryTriggered = false

        composeTestRule.setContent {
            ComicDetailScreen(
                uiState = ComicDetailUiState(
                    errorMessage = UiText.Dynamic("Falha ao carregar detalhes")
                ),
                onAction = { action ->
                    if (action is ComicDetailAction.Retry) {
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
            ComicDetailScreen(
                uiState = ComicDetailUiState(movie = sampleMovie),
                onAction = {}
            )
        }

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()
    }
}
