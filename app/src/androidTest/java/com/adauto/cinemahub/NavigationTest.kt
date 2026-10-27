package com.adauto.cinemahub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.adauto.cinemahub.navigation.CinemaHubNavHost
import com.adauto.cinemahub.navigation.MovieDetailRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolated Navigation Graph Contract Test.
 *
 * Exercises the real [CinemaHubNavHost] route registration, type-safe [MovieDetailRoute]
 * serialization/deserialization (`movieId` argument extraction via `SavedStateHandle`/`toRoute`),
 * and back-stack preservation between [MovieListScreen] and [MovieDetailScreen].
 */
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
        genres = persistentListOf("Drama"),
        isFavorite = false,
        isInWatchlist = false
    )

    @Composable
    private fun TestAppNavigation(onExtractedMovieId: (Int?) -> Unit = {}) {
        val navController = rememberNavController()

        CinemaHubNavHost(
            navController = navController,
            movieListContent = { onOpenDetail ->
                val movies = remember {
                    flowOf(PagingData.from(listOf(sampleMovieSummary)))
                }.collectAsLazyPagingItems()

                MovieListScreen(
                    uiState = MovieListUiState(),
                    movies = movies,
                    onAction = { action ->
                        if (action is MovieListAction.MovieClicked) {
                            onOpenDetail(action.id)
                        }
                    }
                )
            },
            movieDetailContent = { backStackEntry, onBackClick ->
                val route = backStackEntry.toRoute<MovieDetailRoute>()
                val savedStateMovieId = backStackEntry.savedStateHandle.get<Int>("movieId")
                onExtractedMovieId(savedStateMovieId)
                MovieDetailScreen(
                    uiState = MovieDetailUiState(
                        movie = if (route.movieId == 550 && savedStateMovieId == 550) sampleMovieDetail else null,
                        isLoading = false
                    ),
                    onAction = { action ->
                        if (action is MovieDetailAction.BackClicked) {
                            onBackClick()
                        }
                    }
                )
            }
        )
    }

    @Test
    fun navigationFromListToDetailAndBack_extractsMovieIdAndPreservesStack() {
        var extractedMovieId: Int? = null

        composeTestRule.setContent {
            TestAppNavigation(onExtractedMovieId = { extractedMovieId = it })
        }

        // 1. Initial Screen: MovieListScreen with item
        composeTestRule.onNodeWithText("Clube da Luta").assertIsDisplayed()

        // 2. Click movie card to navigate to detail via CinemaHubNavHost
        composeTestRule.onNodeWithText("Clube da Luta").performClick()

        // 3. Verify MovieDetailRoute argument movieId=550 was serialized into SavedStateHandle
        assertEquals(550, extractedMovieId)

        // 4. MovieDetailScreen should be visible with overview and back button
        composeTestRule.onNodeWithTag("MovieDetailContent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Um homem deprimido e insone conhece Tyler Durden.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BackButton").assertIsDisplayed()

        // 5. Click back button
        composeTestRule.onNodeWithTag("BackButton").performClick()

        // 6. Back to MovieListScreen
        composeTestRule.onNodeWithText("Clube da Luta").assertIsDisplayed()
    }
}
