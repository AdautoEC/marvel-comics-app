package com.adauto.cinemahub.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.adauto.cinemahub.feature.detail.MovieDetailRoute as MovieDetailScreen
import com.adauto.cinemahub.feature.movies.MovieListRoute as MovieListScreen

@Composable
fun CinemaHubNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = MovieListRoute,
        modifier = modifier
    ) {
        composable<MovieListRoute> {
            MovieListScreen(
                onOpenDetail = { movieId ->
                    navController.navigate(MovieDetailRoute(movieId))
                }
            )
        }
        composable<MovieDetailRoute> {
            MovieDetailScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
