package com.adauto.marvel.navigation

import kotlinx.serialization.Serializable

@Serializable
data object ComicListRoute

@Serializable
data class ComicDetailRoute(val comicId: Int)
