package com.adauto.marvel.core.data.repository

import com.adauto.marvel.core.data.mapper.toDomainError
import com.adauto.marvel.core.database.MarvelDatabase
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

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: TmdbApi,
    private val sessionManager: SessionManager,
    private val database: MarvelDatabase,
    private val moviesRepository: MoviesRepository
) : AuthRepository {

    override val sessionState: StateFlow<SessionState> = sessionManager.sessionState

    override suspend fun createRequestToken(): Outcome<String> {
        return when (val response = api.createRequestToken()) {
            is ApiResponse.Success -> Outcome.Success(response.data.request_token)
            is ApiResponse.Failure -> Outcome.Failure(response.toDomainError())
        }
    }

    override suspend fun completeAuthentication(requestToken: String): Outcome<TmdbAccountDto> {
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
        sessionManager.clearSession()
        database.favoriteDao().clearAll()
        database.watchedDao().clearAll()
    }
}
