package com.adauto.cinemahub.feature.movies

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

@RunWith(RobolectricTestRunner::class)
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
}
