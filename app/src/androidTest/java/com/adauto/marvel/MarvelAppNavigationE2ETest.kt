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
import com.adauto.marvel.feature.detail.MovieDetailAction
import com.adauto.marvel.feature.detail.MovieDetailScreen
import com.adauto.marvel.feature.detail.MovieDetailUiState
import com.adauto.marvel.feature.movies.MovieListAction
import com.adauto.marvel.feature.movies.MovieListScreen
import com.adauto.marvel.feature.movies.MovieListUiState
import com.adauto.marvel.navigation.MovieDetailRoute
import com.adauto.marvel.navigation.MovieListRoute
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
            startDestination = MovieListRoute
        ) {
            composable<MovieListRoute> {
                val movies = remember { flowOf(PagingData.from(listOf(sampleMovieSummary))) }.collectAsLazyPagingItems()

                MovieListScreen(
                    uiState = MovieListUiState(),
                    movies = movies,
                    onAction = { action ->
                        if (action is MovieListAction.MovieClicked) {
                            navController.navigate(MovieDetailRoute(action.id))
                        }
                    }
                )
            }

            composable<MovieDetailRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<MovieDetailRoute>()
                MovieDetailScreen(
                    uiState = MovieDetailUiState(
                        movie = if (route.movieId == 550) sampleMovieDetail else null,
                        isLoading = false
                    ),
                    onAction = { action ->
                        if (action is MovieDetailAction.BackClicked) {
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
        composeTestRule.onNodeWithTag("MovieListGrid").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clube da Luta").assertIsDisplayed()

        // 2. Click on the movie card to navigate to detail
        composeTestRule.onNodeWithTag("MovieCard_550").performClick()

        // 3. Verify on Detail Screen
        composeTestRule.onNodeWithTag("HeroPoster").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BackButton").assertIsDisplayed()
        composeTestRule.onNodeWithText("139 min").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sinopse").assertIsDisplayed()

        // 4. Click Back button to return to List
        composeTestRule.onNodeWithTag("BackButton").performClick()

        // 5. Verify back on List Screen
        composeTestRule.onNodeWithText("CINEMA HUB").assertIsDisplayed()
        composeTestRule.onNodeWithTag("MovieListGrid").assertIsDisplayed()
    }
}
