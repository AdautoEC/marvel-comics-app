package com.adauto.cinemahub.core.testing.repository

import com.adauto.cinemahub.core.data.repository.AuthRepository
import com.adauto.cinemahub.core.model.AccountProfile
import com.adauto.cinemahub.core.model.Outcome
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.testing.data.MovieTestData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository(
    initialSessionState: SessionState = SessionState.Guest,
) : AuthRepository {

    private val _sessionState = MutableStateFlow(initialSessionState)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    var requestTokenResult: Outcome<String> = Outcome.Success("fake_request_token_123")
    var authorizationUrlResult: Outcome<String> = Outcome.Success(
        "https://www.themoviedb.org/authenticate/fake_request_token_123?redirect_to=cinemahub%3A%2F%2Ftmdb-callback"
    )
    var completeAuthResult: Outcome<AccountProfile> = Outcome.Success(MovieTestData.createAccountProfile())
    var logoutCallCount: Int = 0
        private set

    fun setSessionState(state: SessionState) {
        _sessionState.value = state
    }

    override suspend fun buildAuthorizationUrl(): Outcome<String> = authorizationUrlResult

    override suspend fun createRequestToken(): Outcome<String> = requestTokenResult

    override suspend fun completeAuthentication(requestToken: String): Outcome<AccountProfile> {
        val result = completeAuthResult
        if (result is Outcome.Success) {
            val profile = result.value
            _sessionState.value = SessionState.Authenticated(
                accountId = profile.id,
                username = profile.username,
                sessionId = "fake_session_id",
            )
        }
        return result
    }

    override suspend fun logout() {
        logoutCallCount++
        _sessionState.value = SessionState.Guest
    }
}
