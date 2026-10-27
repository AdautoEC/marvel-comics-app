package com.adauto.cinemahub.feature.detail

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.adauto.cinemahub.core.model.UiText
import com.adauto.cinemahub.core.testing.data.MovieTestData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MovieDetailScreenRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMovie = MovieTestData.createMovieDetail(
        id = 101,
        title = "Inception",
        overview = "Peter Parker takes on the world in this historic issue.",
        releaseDate = "2024-01-15",
        voteAverage = 8.8,
        runtime = 148,
        genres = persistentListOf("Ação", "Ficção Científica"),
        cast = persistentListOf("Leonardo DiCaprio", "Joseph Gordon-Levitt"),
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
        composeTestRule.onNodeWithText("Leonardo DiCaprio").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Joseph Gordon-Levitt").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Voltar").performClick()
        assertTrue(backClicked)
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
                    movie = sampleMovie.copy(isInWatchlist = false),
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
    fun movieDetailScreen_accessibilityChecks_labelsAndTouchTargets() {
        composeTestRule.setContent {
            MovieDetailScreen(
                uiState = MovieDetailUiState(movie = sampleMovie),
                onAction = {}
            )
        }

        // Floating back button must have contentDescription and >= 48.dp touch target
        composeTestRule.onNodeWithTag("BackButton")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithContentDescription("Voltar").assertIsDisplayed()

        // Action buttons must have accessible labels and >= 48.dp touch height
        composeTestRule.onNodeWithTag("FavoriteButton")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithTag("WatchlistButton")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)

        // All clickable nodes must expose text, contentDescription, or onClickLabel
        val clickableNodes = composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(clickableNodes.isNotEmpty())
        clickableNodes.forEach { node ->
            val hasText = node.config
                .getOrElse(SemanticsProperties.Text) { emptyList() }
                .isNotEmpty()
            val hasContentDesc = node.config
                .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .isNotEmpty()
            val hasClickLabel = !node.config
                .getOrNull(SemanticsActions.OnClick)
                ?.label
                .isNullOrBlank()
            assertTrue(
                "Clickable node ${node.id} lacks accessible label",
                hasText || hasContentDesc || hasClickLabel
            )
        }
    }
}
