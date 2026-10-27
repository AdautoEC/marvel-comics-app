package com.adauto.cinemahub.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.adauto.cinemahub.feature.detail.MovieDetailRoute as MovieDetailScreen
import com.adauto.cinemahub.feature.movies.MovieListRoute as MovieListScreen

@Composable
fun CinemaHubNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    movieListContent: @Composable (onOpenDetail: (Int) -> Unit) -> Unit = { onOpenDetail ->
        MovieListScreen(onOpenDetail = onOpenDetail)
    },
    movieDetailContent: @Composable (
        backStackEntry: NavBackStackEntry,
        onBackClick: () -> Unit
    ) -> Unit = { _, onBackClick ->
        MovieDetailScreen(onBackClick = onBackClick)
    }
) {
    NavHost(
        navController = navController,
        startDestination = MovieListRoute,
        modifier = modifier
    ) {
        composable<MovieListRoute> {
            movieListContent { movieId ->
                navController.navigate(MovieDetailRoute(movieId))
            }
        }
        composable<MovieDetailRoute> { backStackEntry ->
            movieDetailContent(backStackEntry) {
                navController.popBackStack()
            }
        }
    }
}
