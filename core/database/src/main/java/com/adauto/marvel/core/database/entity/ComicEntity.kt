package com.adauto.marvel.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "comics",
    indices = [Index(value = ["position"], unique = true)]
)
data class ComicEntity(
    @PrimaryKey val id: Int,
    val position: Int,
    val title: String,
    val issueNumber: Double,
    val description: String?,
    val thumbnailPath: String?,
    val thumbnailExt: String?,
    val pageCount: Int,
    val priceCents: Int?,
    val onsaleDate: String?,
    val creatorsJson: String?,
    val charactersJson: String?,
    val fetchedAt: Long
)
