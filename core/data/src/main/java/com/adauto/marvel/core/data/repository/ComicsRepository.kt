package com.adauto.marvel.core.data.repository

import androidx.paging.PagingData
import com.adauto.marvel.core.model.ComicDetail
import com.adauto.marvel.core.model.ComicSummary
import com.adauto.marvel.core.model.Outcome
import kotlinx.coroutines.flow.Flow

interface ComicsRepository {
    fun comics(): Flow<PagingData<ComicSummary>>
    suspend fun getComicDetail(id: Int): Outcome<ComicDetail>
}
