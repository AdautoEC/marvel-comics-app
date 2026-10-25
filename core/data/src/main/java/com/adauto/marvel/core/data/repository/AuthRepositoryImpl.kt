package com.adauto.marvel.core.data.repository

import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.adauto.marvel.core.data.mapper.toDomainError
import com.adauto.marvel.core.data.storage.AuthPreferencesStorage
import com.adauto.marvel.core.data.sync.SyncCoordinator
import com.adauto.marvel.core.data.worker.SyncOutboxWorker
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.database.entity.SyncOutboxEntity
import com.adauto.marvel.core.model.AccountProfile
import com.adauto.marvel.core.model.DomainError
import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.network.api.TmdbApi
import com.adauto.marvel.core.network.model.TmdbCreateSessionRequestDto
import com.adauto.marvel.core.network.model.TmdbDeleteSessionRequestDto
import com.adauto.marvel.core.network.model.TmdbFavoriteRequestDto
import com.adauto.marvel.core.network.model.TmdbWatchlistRequestDto
import com.adauto.marvel.core.network.security.session.SessionManager
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TMDB_AVATAR_BASE_URL = "https://image.tmdb.org/t/p/w200"
private const val TOKEN_TTL_MS = 10 * 60 * 1000L // 10 minutes
private const val LOGOUT_FLUSH_TIMEOUT_MS = 3_000L

@Singleton
@Suppress("LongParameterList")
class AuthRepositoryImpl(
    private val api: TmdbApi,
    private val sessionManager: SessionManager,
    private val database: MarvelDatabase,
    private val moviesRepository: MoviesRepository,
    private val authPreferences: AuthPreferencesStorage,
    private val workManager: WorkManager,
    private val syncCoordinator: SyncCoordinator,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : AuthRepository {

    @Inject
    constructor(
        api: TmdbApi,
        sessionManager: SessionManager,
        database: MarvelDatabase,
        moviesRepository: MoviesRepository,
        authPreferences: AuthPreferencesStorage,
        workManager: WorkManager,
        syncCoordinator: SyncCoordinator
    ) : this(
        api = api,
        sessionManager = sessionManager,
        database = database,
        moviesRepository = moviesRepository,
        authPreferences = authPreferences,
        workManager = workManager,
        syncCoordinator = syncCoordinator,
        timeProvider = { System.currentTimeMillis() }
    )

    override val sessionState: StateFlow<SessionState> = sessionManager.sessionState

    override suspend fun createRequestToken(): Outcome<String> {
        return when (val response = api.createRequestToken()) {
            is ApiResponse.Success -> {
                val token = response.data.request_token
                authPreferences.savePendingRequestToken(token, timeProvider())
                Outcome.Success(token)
            }
            is ApiResponse.Failure -> Outcome.Failure(response.toDomainError())
        }
    }

    override suspend fun completeAuthentication(requestToken: String): Outcome<AccountProfile> {
        val pending = authPreferences.getPendingRequestToken()
        val now = timeProvider()

        if (pending == null) {
            return Outcome.Failure(DomainError.InvalidKey)
        }

        val (token, createdAtMs) = pending
        if (now - createdAtMs > TOKEN_TTL_MS) {
            authPreferences.clearPendingRequestToken()
            return Outcome.Failure(DomainError.InvalidKey)
        }

        if (token != requestToken) {
            return Outcome.Failure(DomainError.InvalidKey)
        }

        authPreferences.clearPendingRequestToken()

        val sessionResponse = api.createSession(TmdbCreateSessionRequestDto(requestToken))
        val sessionId = when (sessionResponse) {
            is ApiResponse.Success -> sessionResponse.data.session_id
            is ApiResponse.Failure -> return Outcome.Failure(sessionResponse.toDomainError())
        }

        val accountResponse = api.getAccountDetails(sessionId)
        val account = when (accountResponse) {
            is ApiResponse.Success -> accountResponse.data
            is ApiResponse.Failure -> return Outcome.Failure(accountResponse.toDomainError())
        }

        val lastAccountId = authPreferences.getLastAccountId()
        val accountIdStr = account.id.toString()
        if (lastAccountId != null && lastAccountId != accountIdStr) {
            database.withTransaction {
                database.syncOutboxDao().clearAll()
                database.favoriteDao().clearAll()
                database.watchedDao().clearAll()
            }
        }

        stageGuestDataIntoOutbox()

        authPreferences.saveLastAccountId(accountIdStr)

        val avatarUrl = account.avatar?.tmdb?.avatar_path?.let { "$TMDB_AVATAR_BASE_URL$it" }
        sessionManager.saveSession(
            accountId = account.id,
            username = account.username,
            sessionId = sessionId,
            avatarUrl = avatarUrl
        )

        enqueueSyncOutboxWorker()

        moviesRepository.syncCloudWithLocal()

        return Outcome.Success(
            AccountProfile(
                id = account.id,
                username = account.username,
                name = account.name,
                includeAdult = account.includeAdult
            )
        )
    }

    private suspend fun stageGuestDataIntoOutbox() {
        val localFavorites = database.favoriteDao().getAllFavorites().first()
        val localWatched = database.watchedDao().getAllWatched().first()

        if (localFavorites.isNotEmpty() || localWatched.isNotEmpty()) {
            database.withTransaction {
                for (fav in localFavorites) {
                    database.syncOutboxDao().upsert(
                        SyncOutboxEntity(
                            movieId = fav.movieId,
                            target = SyncOutboxWorker.TARGET_FAVORITE,
                            desired = true,
                            attempts = 0,
                            version = 1
                        )
                    )
                }
                for (watch in localWatched) {
                    database.syncOutboxDao().upsert(
                        SyncOutboxEntity(
                            movieId = watch.movieId,
                            target = SyncOutboxWorker.TARGET_WATCHLIST,
                            desired = true,
                            attempts = 0,
                            version = 1
                        )
                    )
                }
            }
        }
    }

    private fun enqueueSyncOutboxWorker() {
        val request = OneTimeWorkRequestBuilder<SyncOutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(SyncOutboxWorker.WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override suspend fun logout() {
        val state = sessionManager.sessionState.value
        val authenticatedSession = state as? SessionState.Authenticated

        authPreferences.clearPendingRequestToken()
        workManager.cancelUniqueWork(SyncOutboxWorker.WORK_NAME)

        withTimeoutOrNull(LOGOUT_FLUSH_TIMEOUT_MS) {
            syncCoordinator.mutex.withLock {
                if (authenticatedSession != null) {
                    flushPendingOutbox(authenticatedSession)
                }
            }
        }

        if (authenticatedSession != null) {
            try {
                api.deleteSession(TmdbDeleteSessionRequestDto(authenticatedSession.sessionId))
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception) {
                // Best-effort remote revocation; local session will still be purged
            }
        }

        sessionManager.clearSession()
        database.withTransaction {
            database.syncOutboxDao().clearAll()
            database.favoriteDao().clearAll()
            database.watchedDao().clearAll()
        }
        authPreferences.clearLastAccountId()
    }

    private suspend fun flushPendingOutbox(session: SessionState.Authenticated) {
        val pending = database.syncOutboxDao().getAllPending()
        for (item in pending) {
            val response = try {
                when (item.target) {
                    SyncOutboxWorker.TARGET_FAVORITE -> api.markAsFavorite(
                        accountId = session.accountId,
                        sessionId = session.sessionId,
                        body = TmdbFavoriteRequestDto(
                            media_id = item.movieId,
                            favorite = item.desired
                        )
                    )
                    SyncOutboxWorker.TARGET_WATCHLIST -> api.addToWatchlist(
                        accountId = session.accountId,
                        sessionId = session.sessionId,
                        body = TmdbWatchlistRequestDto(
                            media_id = item.movieId,
                            watchlist = item.desired
                        )
                    )
                    else -> null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception) {
                null
            }
            if (response is ApiResponse.Success) {
                database.syncOutboxDao().deleteVersion(item.movieId, item.target, item.version)
            }
        }
    }
}
