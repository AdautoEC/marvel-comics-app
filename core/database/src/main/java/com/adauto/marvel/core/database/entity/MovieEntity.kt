package com.adauto.marvel.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "movies",
    indices = [Index(value = ["category", "position"], unique = true)]
)
data class MovieEntity(
    @PrimaryKey val id: Int,
    val category: String,
    val position: Int,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val releaseDate: String?,
    val runtime: Int? = null,
    val genresJson: String? = null,
    val castJson: String? = null,
    val fetchedAt: Long
)
