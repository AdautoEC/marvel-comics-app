package com.adauto.marvel.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.adauto.marvel.core.database.entity.ComicEntity

@Dao
interface ComicDao {
    @Upsert
    suspend fun upsert(comics: List<ComicEntity>)

    @Query("SELECT * FROM comics ORDER BY position ASC")
    fun pagingSource(): PagingSource<Int, ComicEntity>

    @Query("SELECT * FROM comics WHERE id = :id LIMIT 1")
    suspend fun getComicById(id: Int): ComicEntity?

    @Query("SELECT MAX(fetchedAt) FROM comics")
    suspend fun lastFetchedAt(): Long?

    @Query("DELETE FROM comics")
    suspend fun clearAll()
}
