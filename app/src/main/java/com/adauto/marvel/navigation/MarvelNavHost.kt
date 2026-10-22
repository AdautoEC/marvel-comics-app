package com.adauto.marvel.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.adauto.marvel.feature.comics.ComicListRoute as ComicListScreen
import com.adauto.marvel.feature.detail.ComicDetailRoute as ComicDetailScreen

@Composable
fun MarvelNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = ComicListRoute,
        modifier = modifier
    ) {
        composable<ComicListRoute> {
            ComicListScreen(
                onOpenDetail = { comicId ->
                    navController.navigate(ComicDetailRoute(comicId))
                }
            )
        }
        composable<ComicDetailRoute> { backStackEntry ->
            val route: ComicDetailRoute = backStackEntry.toRoute()
            ComicDetailScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
