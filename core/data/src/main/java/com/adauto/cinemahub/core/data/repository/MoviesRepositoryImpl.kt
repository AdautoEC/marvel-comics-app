package com.adauto.cinemahub.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.adauto.cinemahub.core.data.mapper.toDomainError
import com.adauto.cinemahub.core.data.mapper.toMovieDetail
import com.adauto.cinemahub.core.data.mapper.toMovieSummary
import com.adauto.cinemahub.core.data.mediator.MoviesRemoteMediator
import com.adauto.cinemahub.core.data.sync.SyncCoordinator
import com.adauto.cinemahub.core.data.sync.SyncEventBus
import com.adauto.cinemahub.core.data.worker.SyncOutboxWorker
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.entity.FavoriteMovieEntity
import com.adauto.cinemahub.core.database.entity.SyncOutboxEntity
import com.adauto.cinemahub.core.database.entity.WatchlistMovieEntity
import com.adauto.cinemahub.core.model.DomainError
import com.adauto.cinemahub.core.model.MovieCategory
import com.adauto.cinemahub.core.model.MovieDetail
import com.adauto.cinemahub.core.model.MovieSummary
import com.adauto.cinemahub.core.model.Outcome
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.network.api.TmdbApi
import com.adauto.cinemahub.core.network.di.ApplicationScope
import com.adauto.cinemahub.core.network.model.TmdbMovieDto
import com.adauto.cinemahub.core.network.security.session.SessionManager
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.statusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403

@Singleton
@Suppress("LongParameterList", "TooManyFunctions")
class MoviesRepositoryImpl @Inject constructor(
    private val api: TmdbApi,
    private val database: CinemaHubDatabase,
    private val sessionManager: SessionManager,
    private val workManager: WorkManager,
    private val syncCoordinator: SyncCoordinator,
    private val syncEventBus: SyncEventBus? = null,
    @ApplicationScope private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: Clock = Clock.systemUTC()
) : MoviesRepository {

    init {
        externalScope.launch {
            syncCloudWithLocal()
        }
        externalScope.launch {
            syncCoordinator.pullRequests.collect {
                syncCloudWithLocal()
            }
        }
    }

    @OptIn(ExperimentalPagingApi::class)
    override fun movies(category: MovieCategory, query: String): Flow<PagingData<MovieSummary>> {
        val categoryKey = category.name
        val config = PagingConfig(
            pageSize = 20,
            prefetchDistance = 4,
            enablePlaceholders = true,
            initialLoadSize = 40
        )
        val trimmedQuery = query.trim()
        val pager = if (trimmedQuery.isBlank()) {
            Pager(
                config = config,
                remoteMediator = MoviesRemoteMediator(
                    api = api,
                    database = database,
                    category = categoryKey,
                    clock = clock
                ),
                pagingSourceFactory = { database.movieDao().pagingSource(categoryKey) }
            )
        } else {
            Pager(
                config = config,
                pagingSourceFactory = { database.movieDao().searchPagingSource(categoryKey, trimmedQuery) }
            )
        }

        return pager.flow.map { pagingData ->
            pagingData.map { item ->
                item.movie.toMovieSummary(isFavorite = item.isFavorite, isInWatchlist = item.isInWatchlist)
            }
        }
    }

    override suspend fun getMovieDetail(id: Int): Outcome<MovieDetail> {
        val isFav = database.favoriteDao().isFavorite(id).first()
        val isWatch = database.watchlistDao().isInWatchlist(id).first()

        val cachedEntity = database.movieDao().getMovieById(id)

        val apiResponse = api.getMovieDetail(movieId = id)
        return when (apiResponse) {
            is ApiResponse.Success -> {
                Outcome.Success(apiResponse.data.toMovieDetail(isFavorite = isFav, isInWatchlist = isWatch))
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
                            isInWatchlist = isWatch
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
                            isInWatchlist = isWatch
                        )
                    )
                } else {
                    Outcome.Failure(DomainError.Unknown(apiResponse.throwable))
                }
            }
        }
    }

    override suspend fun toggleFavorite(movie: MovieSummary) {
        val isAuthenticated = sessionManager.awaitInitialSession() is SessionState.Authenticated

        if (!isAuthenticated) {
            mutateLocalFavorite(movie)
            return
        }

        database.withTransaction {
            val newFavState = mutateLocalFavorite(movie)
            val existing = database.syncOutboxDao().find(movie.id, SyncOutboxWorker.TARGET_FAVORITE)
            val nextVersion = (existing?.version ?: 0) + 1
            val creationTime = existing?.createdAt?.takeIf { it > 0 } ?: clock.millis()
            database.syncOutboxDao().upsert(
                SyncOutboxEntity(
                    movieId = movie.id,
                    target = SyncOutboxWorker.TARGET_FAVORITE,
                    desired = newFavState,
                    attempts = 0,
                    version = nextVersion,
                    createdAt = creationTime
                )
            )
        }

        enqueueSyncOutboxWorker()
    }

    private suspend fun mutateLocalFavorite(movie: MovieSummary): Boolean {
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
        return newFavState
    }

    override suspend fun toggleWatchlist(movie: MovieSummary) {
        val isAuthenticated = sessionManager.awaitInitialSession() is SessionState.Authenticated

        if (!isAuthenticated) {
            mutateLocalWatchlist(movie)
            return
        }

        database.withTransaction {
            val newWatchState = mutateLocalWatchlist(movie)
            val existing = database.syncOutboxDao().find(movie.id, SyncOutboxWorker.TARGET_WATCHLIST)
            val nextVersion = (existing?.version ?: 0) + 1
            val creationTime = existing?.createdAt?.takeIf { it > 0 } ?: clock.millis()
            database.syncOutboxDao().upsert(
                SyncOutboxEntity(
                    movieId = movie.id,
                    target = SyncOutboxWorker.TARGET_WATCHLIST,
                    desired = newWatchState,
                    attempts = 0,
                    version = nextVersion,
                    createdAt = creationTime
                )
            )
        }

        enqueueSyncOutboxWorker()
    }

    private suspend fun mutateLocalWatchlist(movie: MovieSummary): Boolean {
        val isWatch = database.watchlistDao().isInWatchlist(movie.id).first()
        val newWatchState = !isWatch
        if (isWatch) {
            database.watchlistDao().delete(movie.id)
        } else {
            database.watchlistDao().upsert(
                WatchlistMovieEntity(
                    movieId = movie.id,
                    title = movie.title,
                    posterPath = movie.posterUrl?.substringAfter("w500"),
                    voteAverage = movie.voteAverage,
                    releaseDate = movie.releaseDate,
                    addedAt = clock.millis()
                )
            )
        }
        return newWatchState
    }

    private fun enqueueSyncOutboxWorker() {
        val request = OneTimeWorkRequestBuilder<SyncOutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(SyncOutboxWorker.WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override suspend fun syncCloudWithLocal(): Outcome<Unit> {
        val state = sessionManager.awaitInitialSession()
        if (state !is SessionState.Authenticated) {
            return Outcome.Success(Unit)
        }

        val accountId = state.accountId
        val sessionId = state.sessionId

        return syncCoordinator.mutex.withLock {
            val sessionAfterLock = sessionManager.sessionState.value
            if (sessionAfterLock !is SessionState.Authenticated || sessionAfterLock.sessionId != sessionId) {
                return@withLock Outcome.Success(Unit)
            }

            val remoteFavs = when (val favsResult = fetchAllRemoteFavorites(accountId, sessionId)) {
                is Outcome.Success -> favsResult.value
                is Outcome.Failure -> return@withLock favsResult
            }

            val remoteWatchlist = when (val watchResult = fetchAllRemoteWatchlist(accountId, sessionId)) {
                is Outcome.Success -> watchResult.value
                is Outcome.Failure -> return@withLock watchResult
            }

            val sessionBeforeCommit = sessionManager.sessionState.value
            if (sessionBeforeCommit !is SessionState.Authenticated || sessionBeforeCommit.sessionId != sessionId) {
                return@withLock Outcome.Success(Unit)
            }

            database.withTransaction {
                reconcileFavorites(remoteFavs)
                reconcileWatchlist(remoteWatchlist)
            }

            Outcome.Success(Unit)
        }
    }

    private suspend fun fetchAllRemoteFavorites(
        accountId: Int,
        sessionId: String
    ): Outcome<List<TmdbMovieDto>> {
        val results = mutableListOf<TmdbMovieDto>()
        var page = 1
        var totalPages = 1
        while (page <= totalPages) {
            val response = api.getFavoriteMovies(accountId = accountId, sessionId = sessionId, page = page)
            when (response) {
                is ApiResponse.Success -> {
                    totalPages = response.data.totalPages.coerceAtLeast(1)
                    results.addAll(response.data.results)
                    page++
                }
                is ApiResponse.Failure -> {
                    val error = if (isUnauthorized(response)) {
                        sessionManager.clearSession()
                        syncEventBus?.consumeFailures()
                        DomainError.InvalidKey
                    } else {
                        response.toDomainError()
                    }
                    return Outcome.Failure(error)
                }
            }
        }
        return Outcome.Success(results)
    }

    private suspend fun fetchAllRemoteWatchlist(
        accountId: Int,
        sessionId: String
    ): Outcome<List<TmdbMovieDto>> {
        val results = mutableListOf<TmdbMovieDto>()
        var page = 1
        var totalPages = 1
        while (page <= totalPages) {
            val response = api.getWatchlistMovies(accountId = accountId, sessionId = sessionId, page = page)
            when (response) {
                is ApiResponse.Success -> {
                    totalPages = response.data.totalPages.coerceAtLeast(1)
                    results.addAll(response.data.results)
                    page++
                }
                is ApiResponse.Failure -> {
                    val error = if (isUnauthorized(response)) {
                        sessionManager.clearSession()
                        syncEventBus?.consumeFailures()
                        DomainError.InvalidKey
                    } else {
                        response.toDomainError()
                    }
                    return Outcome.Failure(error)
                }
            }
        }
        return Outcome.Success(results)
    }

    private fun isUnauthorized(response: ApiResponse.Failure<*>): Boolean {
        return response is ApiResponse.Failure.Error &&
            (response.statusCode.code == HTTP_UNAUTHORIZED || response.statusCode.code == HTTP_FORBIDDEN)
    }

    private suspend fun reconcileFavorites(remoteFavs: List<TmdbMovieDto>) {
        val pendingFavs = database.syncOutboxDao().getPendingMovieIds(SyncOutboxWorker.TARGET_FAVORITE).toSet()
        val localFavList = database.favoriteDao().getAllFavorites().first()
        val localFavMap = localFavList.associateBy { it.movieId }
        val remoteFavMap = remoteFavs.associateBy { it.id }

        for (remoteMovie in remoteFavs) {
            if (remoteMovie.id !in pendingFavs && !localFavMap.containsKey(remoteMovie.id)) {
                database.favoriteDao().upsert(
                    FavoriteMovieEntity(
                        movieId = remoteMovie.id,
                        title = remoteMovie.title,
                        posterPath = remoteMovie.posterPath,
                        voteAverage = remoteMovie.voteAverage,
                        releaseDate = remoteMovie.releaseDate,
                        addedAt = clock.millis()
                    )
                )
            }
        }

        for (localFav in localFavList) {
            if (localFav.movieId !in pendingFavs && !remoteFavMap.containsKey(localFav.movieId)) {
                database.favoriteDao().delete(localFav.movieId)
            }
        }
    }

    private suspend fun reconcileWatchlist(remoteWatchlist: List<TmdbMovieDto>) {
        val pendingWatch = database.syncOutboxDao().getPendingMovieIds(SyncOutboxWorker.TARGET_WATCHLIST).toSet()
        val localWatchList = database.watchlistDao().getAllWatchlist().first()
        val localWatchMap = localWatchList.associateBy { it.movieId }
        val remoteWatchMap = remoteWatchlist.associateBy { it.id }

        for (remoteMovie in remoteWatchlist) {
            if (remoteMovie.id !in pendingWatch && !localWatchMap.containsKey(remoteMovie.id)) {
                database.watchlistDao().upsert(
                    WatchlistMovieEntity(
                        movieId = remoteMovie.id,
                        title = remoteMovie.title,
                        posterPath = remoteMovie.posterPath,
                        voteAverage = remoteMovie.voteAverage,
                        releaseDate = remoteMovie.releaseDate,
                        addedAt = clock.millis()
                    )
                )
            }
        }

        for (localWatch in localWatchList) {
            if (localWatch.movieId !in pendingWatch && !remoteWatchMap.containsKey(localWatch.movieId)) {
                database.watchlistDao().delete(localWatch.movieId)
            }
        }
    }

    override fun observeFavorites(): Flow<List<MovieSummary>> {
        return combine(
            database.favoriteDao().getAllFavorites(),
            database.watchlistDao().getAllWatchlist()
        ) { favList, watchList ->
            val watchIds = watchList.asSequence().map { it.movieId }.toSet()
            favList.map { it.toMovieSummary(isInWatchlist = it.movieId in watchIds) }
        }
    }

    override fun observeWatchlist(): Flow<List<MovieSummary>> {
        return combine(
            database.watchlistDao().getAllWatchlist(),
            database.favoriteDao().getAllFavorites()
        ) { watchList, favList ->
            val favIds = favList.asSequence().map { it.movieId }.toSet()
            watchList.map { it.toMovieSummary(isFavorite = it.movieId in favIds) }
        }
    }

    override fun isFavorite(id: Int): Flow<Boolean> = database.favoriteDao().isFavorite(id)

    override fun isInWatchlist(id: Int): Flow<Boolean> = database.watchlistDao().isInWatchlist(id)
}
