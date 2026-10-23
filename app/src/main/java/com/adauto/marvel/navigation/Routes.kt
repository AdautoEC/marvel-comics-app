package com.adauto.marvel.navigation

import kotlinx.serialization.Serializable

@Serializable
data object MovieListRoute

@Serializable
data class MovieDetailRoute(val movieId: Int)
