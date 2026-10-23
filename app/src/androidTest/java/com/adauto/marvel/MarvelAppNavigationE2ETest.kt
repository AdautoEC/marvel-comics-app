package com.adauto.marvel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adauto.marvel.core.model.MovieDetail
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.feature.comics.ComicListAction
import com.adauto.marvel.feature.comics.ComicListUiState
import com.adauto.marvel.feature.comics.MovieListScreen
import com.adauto.marvel.feature.detail.ComicDetailAction
import com.adauto.marvel.feature.detail.ComicDetailScreen
import com.adauto.marvel.feature.detail.ComicDetailUiState
import com.adauto.marvel.navigation.ComicDetailRoute
import com.adauto.marvel.navigation.ComicListRoute
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarvelAppNavigationE2ETest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMovieSummary = MovieSummary(
        id = 550,
        title = "Clube da Luta",
        posterUrl = null,
        releaseDate = "1999-10-15",
        voteAverage = 8.8
    )

    private val sampleMovieDetail = MovieDetail(
        id = 550,
        title = "Clube da Luta",
        overview = "Um homem deprimido e insone conhece Tyler Durden.",
        posterUrl = null,
        backdropUrl = null,
        releaseDate = "1999-10-15",
        voteAverage = 8.8,
        runtime = 139,
        genres = listOf("Drama"),
        isFavorite = false,
        isWatched = false
    )

    @Composable
    private fun TestAppNavigation() {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = ComicListRoute
        ) {
            composable<ComicListRoute> {
                val pagingFlow = remember { flowOf(PagingData.from(listOf(sampleMovieSummary))) }
                val pagingItems = pagingFlow.collectAsLazyPagingItems()

                MovieListScreen(
                    uiState = ComicListUiState(),
                    movies = pagingItems,
                    onAction = { action ->
                        if (action is ComicListAction.ComicClicked) {
                            navController.navigate(ComicDetailRoute(action.id))
                        }
                    }
                )
            }

            composable<ComicDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ComicDetailRoute>()
                ComicDetailScreen(
                    uiState = ComicDetailUiState(
                        movie = if (route.comicId == 550) sampleMovieDetail else null,
                        isLoading = false
                    ),
                    onAction = { action ->
                        if (action is ComicDetailAction.BackClicked) {
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
    }

    @Test
    fun e2e_navigationFromListToDetailAndBack_preservesStack() {
        composeTestRule.setContent {
            TestAppNavigation()
        }

        // 1. Verify on List Screen
        composeTestRule.onNodeWithText("CINEMA HUB").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ComicListGrid").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clube da Luta").assertIsDisplayed()

        // 2. Click on the movie card to navigate to detail
        composeTestRule.onNodeWithTag("ComicCard_550").performClick()

        // 3. Verify on Detail Screen
        composeTestRule.onNodeWithTag("HeroPoster").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BackButton").assertIsDisplayed()
        composeTestRule.onNodeWithText("139 min").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sinopse").assertIsDisplayed()

        // 4. Click Back button to return to List
        composeTestRule.onNodeWithTag("BackButton").performClick()

        // 5. Verify back on List Screen
        composeTestRule.onNodeWithText("CINEMA HUB").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ComicListGrid").assertIsDisplayed()
    }
}
