package com.adauto.marvel.core.database.model

import androidx.room.Embedded
import com.adauto.marvel.core.database.entity.MovieEntity

data class MovieWithUserData(
    @Embedded val movie: MovieEntity,
    val isFavorite: Boolean,
    val isWatched: Boolean
)
