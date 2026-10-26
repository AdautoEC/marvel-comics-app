package com.adauto.marvel.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "sync_outbox",
    primaryKeys = ["movieId", "target"]
)
data class SyncOutboxEntity(
    val movieId: Int,
    val target: String,
    val desired: Boolean,
    @ColumnInfo(defaultValue = "0")
    val attempts: Int = 0,
    val lastError: String? = null,
    @ColumnInfo(defaultValue = "1")
    val version: Int = 1,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = 0L
) {
    companion object {
        const val TARGET_FAVORITE = "FAVORITE"
        const val TARGET_WATCHLIST = "WATCHLIST"
    }
}
