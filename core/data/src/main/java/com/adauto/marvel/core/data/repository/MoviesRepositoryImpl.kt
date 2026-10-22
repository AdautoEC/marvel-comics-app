package com.adauto.marvel.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.adauto.marvel.core.data.mapper.toMovieDetail
import com.adauto.marvel.core.data.mapper.toMovieSummary
import com.adauto.marvel.core.data.mediator.MoviesRemoteMediator
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.entity.FavoriteMovieEntity
import com.adauto.marvel.core.database.entity.WatchedMovieEntity
import com.adauto.marvel.core.model.DomainError
import com.adauto.marvel.core.model.MovieCategory
import com.adauto.marvel.core.model.MovieDetail
import com.adauto.marvel.core.model.MovieSummary
import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.network.api.TmdbApi
import com.adauto.marvel.core.network.model.TmdbFavoriteRequestDto
import com.adauto.marvel.core.network.model.TmdbWatchlistRequestDto
import com.adauto.marvel.core.network.security.session.SessionManager
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.statusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoviesRepositoryImpl @Inject constructor(
    private val api: TmdbApi,
    private val database: MarvelDatabase,
    private val sessionManager: SessionManager,
    private val clock: Clock = Clock.systemUTC()
) : MoviesRepository {

    @OptIn(ExperimentalPagingApi::class)
    override fun movies(category: MovieCategory): Flow<PagingData<MovieSummary>> {
        val categoryKey = category.name
        val pager = Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 4,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
            remoteMediator = MoviesRemoteMediator(
                api = api,
                database = database,
                category = categoryKey,
                clock = clock
            ),
            pagingSourceFactory = { database.movieDao().pagingSource(categoryKey) }
        )

        return pager.flow.map { pagingData ->
            pagingData.map { entity ->
                val isFav = database.favoriteDao().isFavorite(entity.id).first()
                val isWatch = database.watchedDao().isWatched(entity.id).first()
                entity.toMovieSummary(isFavorite = isFav, isWatched = isWatch)
            }
        }
    }

    override suspend fun getMovieDetail(id: Int): Outcome<MovieDetail> {
        val isFav = database.favoriteDao().isFavorite(id).first()
        val isWatch = database.watchedDao().isWatched(id).first()

        val cachedEntity = database.movieDao().getMovieById(id)

        val apiResponse = api.getMovieDetail(movieId = id)
        return when (apiResponse) {
            is ApiResponse.Success -> {
                Outcome.Success(apiResponse.data.toMovieDetail(isFavorite = isFav, isWatched = isWatch))
            }
            is ApiResponse.Failure.Error -> {
                if (cachedEntity != null) {
                    Outcome.Success(
                        MovieDetail(
                            id = cachedEntity.id,
                            title = cachedEntity.title,
                            overview = cachedEntity.overview,
                            posterUrl = cachedEntity.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                            backdropUrl = cachedEntity.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" },
                            voteAverage = cachedEntity.voteAverage,
                            runtime = cachedEntity.runtime,
                            releaseDate = cachedEntity.releaseDate,
                            isFavorite = isFav,
                            isWatched = isWatch
                        )
                    )
                } else {
                    Outcome.Failure(DomainError.Server(apiResponse.statusCode.code))
                }
            }
            is ApiResponse.Failure.Exception -> {
                if (cachedEntity != null) {
                    Outcome.Success(
                        MovieDetail(
                            id = cachedEntity.id,
                            title = cachedEntity.title,
                            overview = cachedEntity.overview,
                            posterUrl = cachedEntity.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                            backdropUrl = cachedEntity.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" },
                            voteAverage = cachedEntity.voteAverage,
                            runtime = cachedEntity.runtime,
                            releaseDate = cachedEntity.releaseDate,
                            isFavorite = isFav,
                            isWatched = isWatch
                        )
                    )
                } else {
                    Outcome.Failure(DomainError.Unknown(apiResponse.throwable))
                }
            }
        }
    }

    override suspend fun toggleFavorite(movie: MovieSummary) {
        val isFav = database.favoriteDao().isFavorite(movie.id).first()
        val newFavState = !isFav
        if (isFav) {
            database.favoriteDao().delete(movie.id)
        } else {
            database.favoriteDao().upsert(
                FavoriteMovieEntity(
                    movieId = movie.id,
                    title = movie.title,
                    posterPath = movie.posterUrl?.substringAfter("w500"),
                    voteAverage = movie.voteAverage,
                    releaseDate = movie.releaseDate,
                    addedAt = clock.millis()
                )
            )
        }

        val state = sessionManager.sessionState.value
        if (state is SessionState.Authenticated) {
            runCatching {
                api.markAsFavorite(
                    accountId = state.accountId,
                    sessionId = state.sessionId,
                    body = TmdbFavoriteRequestDto(media_id = movie.id, favorite = newFavState)
                )
            }
        }
    }

    override suspend fun toggleWatchlist(movie: MovieSummary) {
        val isWatch = database.watchedDao().isWatched(movie.id).first()
        val newWatchState = !isWatch
        if (isWatch) {
            database.watchedDao().delete(movie.id)
        } else {
            database.watchedDao().upsert(
                WatchedMovieEntity(
                    movieId = movie.id,
                    title = movie.title,
                    posterPath = movie.posterUrl?.substringAfter("w500"),
                    voteAverage = movie.voteAverage,
                    releaseDate = movie.releaseDate,
                    watchedAt = clock.millis()
                )
            )
        }

        val state = sessionManager.sessionState.value
        if (state is SessionState.Authenticated) {
            runCatching {
                api.addToWatchlist(
                    accountId = state.accountId,
                    sessionId = state.sessionId,
                    body = TmdbWatchlistRequestDto(media_id = movie.id, watchlist = newWatchState)
                )
            }
        }
    }

    override suspend fun toggleWatched(movie: MovieSummary) {
        toggleWatchlist(movie)
    }

    override suspend fun syncCloudWithLocal(): Outcome<Unit> {
        val state = sessionManager.sessionState.value
        if (state !is SessionState.Authenticated) {
            return Outcome.Success(Unit)
        }

        val accountId = state.accountId
        val sessionId = state.sessionId

        // 1. Push local favorites & watchlist to TMDB
        runCatching {
            val localFavorites = database.favoriteDao().getAllFavorites().first()
            for (fav in localFavorites) {
                api.markAsFavorite(
                    accountId = accountId,
                    sessionId = sessionId,
                    body = TmdbFavoriteRequestDto(media_id = fav.movieId, favorite = true)
                )
            }

            val localWatched = database.watchedDao().getAllWatched().first()
            for (watch in localWatched) {
                api.addToWatchlist(
                    accountId = accountId,
                    sessionId = sessionId,
                    body = TmdbWatchlistRequestDto(media_id = watch.movieId, watchlist = true)
                )
            }
        }

        // 2. Pull remote favorites & watchlist and upsert into Room
        runCatching {
            val favResponse = api.getFavoriteMovies(accountId = accountId, sessionId = sessionId, page = 1)
            if (favResponse is ApiResponse.Success) {
                for (movieDto in favResponse.data.results) {
                    database.favoriteDao().upsert(
                        FavoriteMovieEntity(
                            movieId = movieDto.id,
                            title = movieDto.title,
                            posterPath = movieDto.posterPath,
                            voteAverage = movieDto.voteAverage,
                            releaseDate = movieDto.releaseDate,
                            addedAt = clock.millis()
                        )
                    )
                }
            } else if (favResponse is ApiResponse.Failure.Error && favResponse.statusCode.code == 401) {
                sessionManager.clearSession()
                return Outcome.Failure(DomainError.InvalidKey)
            }

            val watchResponse = api.getWatchlistMovies(accountId = accountId, sessionId = sessionId, page = 1)
            if (watchResponse is ApiResponse.Success) {
                for (movieDto in watchResponse.data.results) {
                    database.watchedDao().upsert(
                        WatchedMovieEntity(
                            movieId = movieDto.id,
                            title = movieDto.title,
                            posterPath = movieDto.posterPath,
                            voteAverage = movieDto.voteAverage,
                            releaseDate = movieDto.releaseDate,
                            watchedAt = clock.millis()
                        )
                    )
                }
            } else if (watchResponse is ApiResponse.Failure.Error && watchResponse.statusCode.code == 401) {
                sessionManager.clearSession()
                return Outcome.Failure(DomainError.InvalidKey)
            }
        }

        return Outcome.Success(Unit)
    }

    override fun observeFavorites(): Flow<List<MovieSummary>> {
        return database.favoriteDao().getAllFavorites().map { list ->
            list.map { it.toMovieSummary() }
        }
    }

    override fun observeWatched(): Flow<List<MovieSummary>> {
        return database.watchedDao().getAllWatched().map { list ->
            list.map { it.toMovieSummary() }
        }
    }

    override fun isFavorite(id: Int): Flow<Boolean> = database.favoriteDao().isFavorite(id)

    override fun isWatched(id: Int): Flow<Boolean> = database.watchedDao().isWatched(id)
}
