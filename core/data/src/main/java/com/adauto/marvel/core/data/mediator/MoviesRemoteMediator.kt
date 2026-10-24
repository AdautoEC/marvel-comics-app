package com.adauto.marvel.core.data.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.adauto.marvel.core.data.mapper.toMovieEntity
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.entity.MovieEntity
import com.adauto.marvel.core.database.model.MovieWithUserData
import com.adauto.marvel.core.model.TmdbApiException
import com.adauto.marvel.core.network.api.TmdbApi
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.statusCode
import java.time.Clock
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalPagingApi::class)
class MoviesRemoteMediator(
    private val api: TmdbApi,
    private val database: MarvelDatabase,
    private val category: String,
    private val clock: Clock = Clock.systemUTC(),
    private val cacheTtlHours: Long = 6L
) : RemoteMediator<Int, MovieWithUserData>() {

    override suspend fun initialize(): InitializeAction {
        val lastFetched = database.movieDao().lastFetchedAt(category) ?: return InitializeAction.LAUNCH_INITIAL_REFRESH
        val cacheAge = clock.millis() - lastFetched
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
                val lastItem = state.lastItemOrNull()
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
                (lastItem.movie.position / state.config.pageSize) + 2
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

                val startPosition = (page - 1) * state.config.pageSize
                val entities = data.results.mapIndexed { index, dto ->
                    dto.toMovieEntity(category = category, position = startPosition + index, fetchedAt = now)
                }

                database.withTransaction {
                    if (loadType == LoadType.REFRESH) {
                        database.movieDao().clearCategory(category)
                    }
                    database.movieDao().upsertAll(entities)
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
