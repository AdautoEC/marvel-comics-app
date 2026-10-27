package com.adauto.cinemahub.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist_movies")
data class WatchlistMovieEntity(
    @PrimaryKey val movieId: Int,
    val title: String,
    val posterPath: String?,
    val voteAverage: Double,
    val releaseDate: String?,
    val addedAt: Long
)
