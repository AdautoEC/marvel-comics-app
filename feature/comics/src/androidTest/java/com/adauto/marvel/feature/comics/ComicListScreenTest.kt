package com.adauto.marvel.feature.comics

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.adauto.marvel.core.model.ComicSummary
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComicListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleComics = listOf(
        ComicSummary(id = 101, title = "The Amazing Spider-Man #1", issueNumber = "#1", coverUrl = null),
        ComicSummary(id = 102, title = "Iron Man #1", issueNumber = "#1", coverUrl = null)
    )

    @Test
    fun stateRestoration_preservesScreenStateAcrossRecreation() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            val pagingFlow = remember { flowOf(PagingData.from(sampleComics)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()
            ComicListScreen(
                uiState = ComicListUiState(),
                comics = pagingItems,
                onAction = {}
            )
        }

        // Emulate process recreation
        restorationTester.emulateSavedInstanceStateRestore()

        // Content remains restored and visible
        composeTestRule.onNodeWithText("The Amazing Spider-Man #1").assertIsDisplayed()
    }

    @Test
    fun comicListScreen_displaysOfflineBannerWhenOffline() {
        composeTestRule.setContent {
            val pagingFlow = remember { flowOf(PagingData.from(sampleComics)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()
            ComicListScreen(
                uiState = ComicListUiState(isOffline = true),
                comics = pagingItems,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithTag("OfflineBanner").assertIsDisplayed()
    }

    @Test
    fun comicListScreen_displaysAttributionFooter() {
        composeTestRule.setContent {
            val pagingFlow = remember { flowOf(PagingData.from(sampleComics)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()
            ComicListScreen(
                uiState = ComicListUiState(attribution = "Data provided by Marvel. © 2026 MARVEL"),
                comics = pagingItems,
                onAction = {}
            )
        }

        composeTestRule.onNodeWithText("Data provided by Marvel. © 2026 MARVEL").assertIsDisplayed()
    }

    @Test
    fun comicListScreen_clickingComic_triggersComicClickedAction() {
        var clickedId: Int? = null

        composeTestRule.setContent {
            val pagingFlow = remember { flowOf(PagingData.from(sampleComics)) }
            val pagingItems = pagingFlow.collectAsLazyPagingItems()
            ComicListScreen(
                uiState = ComicListUiState(),
                comics = pagingItems,
                onAction = { action ->
                    if (action is ComicListAction.ComicClicked) {
                        clickedId = action.id
                    }
                }
            )
        }

        composeTestRule.onNodeWithText("The Amazing Spider-Man #1").performClick()
        assertEquals(101, clickedId)
    }
}
