package com.adauto.marvel.core.data.repository

import com.adauto.marvel.core.data.mapper.toDomainError
import com.adauto.marvel.core.database.MarvelDatabase
import com.adauto.marvel.core.model.DomainError
import com.adauto.marvel.core.model.Outcome
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.network.api.TmdbApi
import com.adauto.marvel.core.network.model.TmdbAccountDto
import com.adauto.marvel.core.network.model.TmdbCreateSessionRequestDto
import com.adauto.marvel.core.network.security.session.SessionManager
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TMDB_AVATAR_BASE_URL = "https://image.tmdb.org/t/p/w200"
private const val TOKEN_TTL_MS = 10 * 60 * 1000L // 10 minutes

@Singleton
class AuthRepositoryImpl(
    private val api: TmdbApi,
    private val sessionManager: SessionManager,
    private val database: MarvelDatabase,
    private val moviesRepository: MoviesRepository,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : AuthRepository {

    @Inject
    constructor(
        api: TmdbApi,
        sessionManager: SessionManager,
        database: MarvelDatabase,
        moviesRepository: MoviesRepository
    ) : this(
        api = api,
        sessionManager = sessionManager,
        database = database,
        moviesRepository = moviesRepository,
        timeProvider = { System.currentTimeMillis() }
    )

    @Volatile
    private var pendingRequestToken: String? = null

    @Volatile
    private var pendingTokenCreatedAtMs: Long = 0L

    override val sessionState: StateFlow<SessionState> = sessionManager.sessionState

    override suspend fun createRequestToken(): Outcome<String> {
        return when (val response = api.createRequestToken()) {
            is ApiResponse.Success -> {
                val token = response.data.request_token
                pendingRequestToken = token
                pendingTokenCreatedAtMs = timeProvider()
                Outcome.Success(token)
            }
            is ApiResponse.Failure -> Outcome.Failure(response.toDomainError())
        }
    }

    override suspend fun completeAuthentication(requestToken: String): Outcome<TmdbAccountDto> {
        val pending = pendingRequestToken
        val createdAt = pendingTokenCreatedAtMs
        val now = timeProvider()

        if (pending.isNullOrBlank() || pending != requestToken || (now - createdAt) > TOKEN_TTL_MS) {
            pendingRequestToken = null
            return Outcome.Failure(DomainError.InvalidKey)
        }

        // Clear pending token to prevent replay attacks
        pendingRequestToken = null

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

        val avatarUrl = account.avatar?.tmdb?.avatar_path?.let { "$TMDB_AVATAR_BASE_URL$it" }
        sessionManager.saveSession(
            accountId = account.id,
            username = account.username,
            sessionId = sessionId,
            avatarUrl = avatarUrl
        )

        moviesRepository.syncCloudWithLocal()

        return Outcome.Success(account)
    }

    override suspend fun logout() {
        pendingRequestToken = null
        sessionManager.clearSession()
        database.favoriteDao().clearAll()
        database.watchedDao().clearAll()
    }
}
