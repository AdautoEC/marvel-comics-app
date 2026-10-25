package com.adauto.marvel.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movie_remote_keys")
data class MovieRemoteKeysEntity(
    @PrimaryKey val label: String,
    val nextPage: Int?,
    val totalPages: Int?,
    val updatedAt: Long
)
