package com.adauto.cinemahub.core.data.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.adauto.cinemahub.core.data.mapper.toMovieEntity
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.entity.MovieEntity
import com.adauto.cinemahub.core.database.entity.MovieRemoteKeysEntity
import com.adauto.cinemahub.core.database.model.MovieWithUserData
import com.adauto.cinemahub.core.model.TmdbApiException
import com.adauto.cinemahub.core.network.api.TmdbApi
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.statusCode
import java.time.Clock
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalPagingApi::class)
class MoviesRemoteMediator(
    private val api: TmdbApi,
    private val database: CinemaHubDatabase,
    private val category: String,
    private val clock: Clock = Clock.systemUTC(),
    private val cacheTtlHours: Long = 6L
) : RemoteMediator<Int, MovieWithUserData>() {

    companion object {
        const val TMDB_PAGE_SIZE = 20
    }

    override suspend fun initialize(): InitializeAction {
        val remoteKey = database.movieRemoteKeysDao().getKey(category)
            ?: return InitializeAction.LAUNCH_INITIAL_REFRESH

        val cacheAge = clock.millis() - remoteKey.updatedAt
        return if (cacheAge < TimeUnit.HOURS.toMillis(cacheTtlHours)) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }

    override suspend fun load(loadType: LoadType, state: PagingState<Int, MovieWithUserData>): MediatorResult {
        val page = when (loadType) {
            LoadType.REFRESH -> 1
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val remoteKey = database.movieRemoteKeysDao().getKey(category)
                remoteKey?.nextPage ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }

        val response = when (category) {
            "TOP_RATED" -> api.getTopRatedMovies(page = page)
            else -> api.getPopularMovies(page = page)
        }

        return when (response) {
            is ApiResponse.Success -> {
                val data = response.data
                val now = clock.millis()
                val isEnd = page >= data.totalPages || data.results.isEmpty()
                val nextPage = if (isEnd) null else page + 1

                val startPosition = (page - 1) * TMDB_PAGE_SIZE
                val entities = data.results.mapIndexed { index, dto ->
                    dto.toMovieEntity(category = category, position = startPosition + index, fetchedAt = now)
                }

                database.withTransaction {
                    if (loadType == LoadType.REFRESH) {
                        database.movieDao().clearCategory(category)
                        database.movieRemoteKeysDao().deleteKey(category)
                    }
                    database.movieDao().upsertAll(entities)
                    database.movieRemoteKeysDao().insertOrReplace(
                        MovieRemoteKeysEntity(
                            label = category,
                            nextPage = nextPage,
                            totalPages = data.totalPages,
                            updatedAt = now
                        )
                    )
                }

                MediatorResult.Success(endOfPaginationReached = isEnd)
            }
            is ApiResponse.Failure.Error -> {
                val code = response.statusCode.code
                val message = response.errorMessage()
                MediatorResult.Error(TmdbApiException(code, "TMDB API Error $code: $message"))
            }
            is ApiResponse.Failure.Exception -> {
                MediatorResult.Error(response.throwable)
            }
        }
    }
}

private fun ApiResponse.Failure.Error.errorMessage(): String {
    val retrofitResponse = payload as? retrofit2.Response<*>
    val raw = retrofitResponse?.message()?.takeIf { it.isNotBlank() }
    return raw ?: statusCode.name
}
