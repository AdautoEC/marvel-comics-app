package com.adauto.cinemahub

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
import com.adauto.cinemahub.core.model.MovieDetail
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.feature.detail.MovieDetailAction
import com.adauto.cinemahub.feature.detail.MovieDetailScreen
import com.adauto.cinemahub.feature.detail.MovieDetailUiState
import com.adauto.cinemahub.feature.movies.MovieListAction
import com.adauto.cinemahub.feature.movies.MovieListScreen
import com.adauto.cinemahub.feature.movies.MovieListUiState
import com.adauto.cinemahub.navigation.MovieDetailRoute
import com.adauto.cinemahub.navigation.MovieListRoute
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

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
        isInWatchlist = false
    )

    @Composable
    private fun TestAppNavigation() {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = MovieListRoute
        ) {
            composable<MovieListRoute> {
                val movies = remember {
                    flowOf(PagingData.from(listOf(sampleMovieSummary)))
                }.collectAsLazyPagingItems()

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
    fun navigationFromListToDetailAndBack_preservesStack() {
        composeTestRule.setContent {
            TestAppNavigation()
        }

        // 1. Initial Screen: MovieListScreen with item
        composeTestRule.onNodeWithText("Clube da Luta").assertIsDisplayed()

        // 2. Click movie card to navigate to detail
        composeTestRule.onNodeWithText("Clube da Luta").performClick()

        // 3. MovieDetailScreen should be visible with overview and back button
        composeTestRule.onNodeWithTag("MovieDetailContent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Um homem deprimido e insone conhece Tyler Durden.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BackButton").assertIsDisplayed()

        // 4. Click back button
        composeTestRule.onNodeWithTag("BackButton").performClick()

        // 5. Back to MovieListScreen
        composeTestRule.onNodeWithText("Clube da Luta").assertIsDisplayed()
    }
}
