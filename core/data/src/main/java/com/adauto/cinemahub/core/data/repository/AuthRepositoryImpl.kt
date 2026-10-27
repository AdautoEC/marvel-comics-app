package com.adauto.cinemahub.core.data.repository

import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.adauto.cinemahub.core.data.mapper.toDomainError
import com.adauto.cinemahub.core.data.storage.AuthPreferencesStorage
import com.adauto.cinemahub.core.data.sync.SyncCoordinator
import com.adauto.cinemahub.core.data.sync.SyncEventBus
import com.adauto.cinemahub.core.data.worker.SyncOutboxWorker
import com.adauto.cinemahub.core.database.CinemaHubDatabase
import com.adauto.cinemahub.core.database.entity.SyncOutboxEntity
import com.adauto.cinemahub.core.model.AccountProfile
import com.adauto.cinemahub.core.model.DomainError
import com.adauto.cinemahub.core.model.Outcome
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.network.api.TmdbApi
import com.adauto.cinemahub.core.network.model.TmdbCreateSessionRequestDto
import com.adauto.cinemahub.core.network.model.TmdbDeleteSessionRequestDto
import com.adauto.cinemahub.core.network.model.TmdbFavoriteRequestDto
import com.adauto.cinemahub.core.network.model.TmdbWatchlistRequestDto
import com.adauto.cinemahub.core.network.security.session.SessionManager
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl

private const val TMDB_AUTH_HOST = "www.themoviedb.org"
private const val TMDB_REDIRECT_URI = "cinemahub://tmdb-callback"
private const val TMDB_AVATAR_BASE_URL = "https://image.tmdb.org/t/p/w200"
private const val TOKEN_TTL_MS = 10 * 60 * 1000L // 10 minutes
private const val LOGOUT_FLUSH_TIMEOUT_MS = 3_000L

@Singleton
@Suppress("LongParameterList")
class AuthRepositoryImpl(
    private val api: TmdbApi,
    private val sessionManager: SessionManager,
    private val database: CinemaHubDatabase,
    private val moviesRepository: MoviesRepository,
    private val authPreferences: AuthPreferencesStorage,
    private val workManager: WorkManager,
    private val syncCoordinator: SyncCoordinator,
    private val syncEventBus: SyncEventBus? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : AuthRepository {

    @Inject
    constructor(
        api: TmdbApi,
        sessionManager: SessionManager,
        database: CinemaHubDatabase,
        moviesRepository: MoviesRepository,
        authPreferences: AuthPreferencesStorage,
        workManager: WorkManager,
        syncCoordinator: SyncCoordinator,
        syncEventBus: SyncEventBus
    ) : this(
        api = api,
        sessionManager = sessionManager,
        database = database,
        moviesRepository = moviesRepository,
        authPreferences = authPreferences,
        workManager = workManager,
        syncCoordinator = syncCoordinator,
        syncEventBus = syncEventBus,
        timeProvider = { System.currentTimeMillis() }
    )

    override val sessionState: StateFlow<SessionState> = sessionManager.sessionState

    override suspend fun buildAuthorizationUrl(): Outcome<String> {
        return when (val tokenOutcome = createRequestToken()) {
            is Outcome.Success -> {
                val url = HttpUrl.Builder()
                    .scheme("https")
                    .host(TMDB_AUTH_HOST)
                    .addPathSegment("authenticate")
                    .addPathSegment(tokenOutcome.value)
                    .addQueryParameter("redirect_to", TMDB_REDIRECT_URI)
                    .build()
                    .toString()
                Outcome.Success(url)
            }
            is Outcome.Failure -> tokenOutcome
        }
    }

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

        val accountResponse = when (val initialAccount = api.getAccountDetails(sessionId)) {
            is ApiResponse.Success -> initialAccount
            is ApiResponse.Failure -> {
                val fallback = api.getAccountDetails(sessionId = null)
                if (fallback is ApiResponse.Success) fallback else initialAccount
            }
        }
        val account = when (accountResponse) {
            is ApiResponse.Success -> accountResponse.data
            is ApiResponse.Failure -> return Outcome.Failure(accountResponse.toDomainError())
        }

        return finalizeAuthenticatedSession(account = account, sessionId = sessionId)
    }

    private suspend fun finalizeAuthenticatedSession(
        account: com.adauto.cinemahub.core.network.model.TmdbAccountDto,
        sessionId: String
    ): Outcome<AccountProfile> {
        val lastAccountId = authPreferences.getLastAccountId()
        val accountIdStr = account.id.toString()
        if (lastAccountId != null && lastAccountId != accountIdStr) {
            database.withTransaction {
                database.syncOutboxDao().clearAll()
                database.favoriteDao().clearAll()
                database.watchlistDao().clearAll()
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
        val localWatchlist = database.watchlistDao().getAllWatchlist().first()

        if (localFavorites.isNotEmpty() || localWatchlist.isNotEmpty()) {
            database.withTransaction {
                for (fav in localFavorites) {
                    val existing = database.syncOutboxDao().find(fav.movieId, SyncOutboxWorker.TARGET_FAVORITE)
                    val nextVersion = (existing?.version ?: 0) + 1
                    val creationTime = existing?.createdAt?.takeIf { it > 0 } ?: fav.addedAt
                    database.syncOutboxDao().upsert(
                        SyncOutboxEntity(
                            movieId = fav.movieId,
                            target = SyncOutboxWorker.TARGET_FAVORITE,
                            desired = true,
                            attempts = 0,
                            version = nextVersion,
                            createdAt = creationTime
                        )
                    )
                }
                for (watch in localWatchlist) {
                    val existing = database.syncOutboxDao().find(watch.movieId, SyncOutboxWorker.TARGET_WATCHLIST)
                    val nextVersion = (existing?.version ?: 0) + 1
                    val creationTime = existing?.createdAt?.takeIf { it > 0 } ?: watch.addedAt
                    database.syncOutboxDao().upsert(
                        SyncOutboxEntity(
                            movieId = watch.movieId,
                            target = SyncOutboxWorker.TARGET_WATCHLIST,
                            desired = true,
                            attempts = 0,
                            version = nextVersion,
                            createdAt = creationTime
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
        syncEventBus?.consumeFailures()
        database.withTransaction {
            database.syncOutboxDao().clearAll()
            database.favoriteDao().clearAll()
            database.watchlistDao().clearAll()
        }
        authPreferences.clearLastAccountId()
    }

    private suspend fun flushPendingOutbox(session: SessionState.Authenticated) {
        val pending = database.syncOutboxDao().getAllPending()
        val sessionId = session.sessionId.takeIf { it.isNotBlank() }
        for (item in pending) {
            val response = try {
                when (item.target) {
                    SyncOutboxWorker.TARGET_FAVORITE -> api.markAsFavorite(
                        accountId = session.accountId,
                        sessionId = sessionId,
                        body = TmdbFavoriteRequestDto(
                            media_id = item.movieId,
                            favorite = item.desired
                        )
                    )
                    SyncOutboxWorker.TARGET_WATCHLIST -> api.addToWatchlist(
                        accountId = session.accountId,
                        sessionId = sessionId,
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
