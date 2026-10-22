package com.adauto.marvel.core.data.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.adauto.marvel.core.data.mapper.toEntity
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.entity.ComicEntity
import com.adauto.marvel.core.database.entity.RemoteKeyEntity
import com.adauto.marvel.core.network.api.MarvelApi
import com.skydoves.sandwich.ApiResponse
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalPagingApi::class)
class ComicsRemoteMediator(
    private val api: MarvelApi,
    private val database: MarvelDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val cacheTtlHours: Long = 6L
) : RemoteMediator<Int, ComicEntity>() {

    override suspend fun initialize(): InitializeAction {
        val lastFetched = database.comicDao().lastFetchedAt() ?: return InitializeAction.LAUNCH_INITIAL_REFRESH
        val cacheAge = clock.millis() - lastFetched
        return if (cacheAge < TimeUnit.HOURS.toMillis(cacheTtlHours)) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }

    override suspend fun load(loadType: LoadType, state: PagingState<Int, ComicEntity>): MediatorResult {
        val offset = when (loadType) {
            LoadType.REFRESH -> 0
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val next = database.remoteKeyDao().lastNextOffset()
                next ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }

        val response = api.getComics(offset = offset, limit = state.config.pageSize)
        return when (response) {
            is ApiResponse.Success -> {
                val container = response.data.data
                val isEnd = offset + container.count >= container.total
                val now = clock.millis()

                val entities = container.results.mapIndexed { index, dto ->
                    dto.toEntity(position = offset + index, fetchedAt = now)
                }

                database.withTransaction {
                    if (loadType == LoadType.REFRESH) {
                        database.comicDao().clearAll()
                        database.remoteKeyDao().clearAll()
                    }
                    database.comicDao().upsert(entities)
                    val nextOffset = if (isEnd) null else offset + container.count
                    database.remoteKeyDao().upsert(entities.map { RemoteKeyEntity(it.id, nextOffset) })
                }
                MediatorResult.Success(endOfPaginationReached = isEnd)
            }
            is ApiResponse.Failure.Error -> {
                MediatorResult.Error(IOException("API error"))
            }
            is ApiResponse.Failure.Exception -> {
                MediatorResult.Error(response.throwable)
            }
        }
    }
}
