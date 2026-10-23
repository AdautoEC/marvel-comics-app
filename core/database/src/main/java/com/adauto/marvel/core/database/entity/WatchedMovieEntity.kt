package com.adauto.marvel.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watched_movies")
data class WatchedMovieEntity(
    @PrimaryKey val movieId: Int,
    val title: String,
    val posterPath: String?,
    val voteAverage: Double,
    val releaseDate: String?,
    val watchedAt: Long
)
