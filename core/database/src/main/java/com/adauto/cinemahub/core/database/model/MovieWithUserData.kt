package com.adauto.cinemahub.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.adauto.cinemahub.core.database.entity.MovieEntity

data class MovieWithUserData(
    @Embedded val movie: MovieEntity,
    val isFavorite: Boolean,
    @ColumnInfo(name = "isWatched")
    val isInWatchlist: Boolean
)
