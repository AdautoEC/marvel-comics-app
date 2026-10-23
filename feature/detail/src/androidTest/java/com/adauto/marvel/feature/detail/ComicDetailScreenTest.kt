package com.adauto.marvel.feature.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adauto.marvel.core.model.ComicDetail
import com.adauto.marvel.core.model.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComicDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleComic = ComicDetail(
        id = 101,
        title = "The Amazing Spider-Man #1",
        issueNumber = "#1",
        description = "Peter Parker takes on the world in this historic issue.",
        pageCount = 36,
        coverUrl = null,
        detailUrl = "https://marvel.com/comics/101",
        onsaleDate = "2024-01-15T00:00:00-0500",
        price = 3.99,
        creators = listOf("Stan Lee", "Steve Ditko"),
        characters = listOf("Spider-Man", "J. Jonah Jameson")
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
                uiState = ComicDetailUiState(comic = sampleComic),
                onAction = { action ->
                    if (action is ComicDetailAction.BackClicked) {
                        backClicked = true
                    }
                }
            )
        }

        composeTestRule.onNodeWithTag("ComicDetailContent").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Amazing Spider-Man #1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stan Lee").assertIsDisplayed()
        composeTestRule.onNodeWithText("Steve Ditko").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Voltar").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun comicDetailScreen_displaysErrorState_andTriggersRetry() {
        var retryTriggered = false

        composeTestRule.setContent {
            ComicDetailScreen(
                uiState = ComicDetailUiState(
                    errorMessage = UiText.DynamicString("Falha ao carregar detalhes")
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
                uiState = ComicDetailUiState(comic = sampleComic),
                onAction = {}
            )
        }

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("The Amazing Spider-Man #1").assertIsDisplayed()
    }
}
