package com.adauto.cinemahub.core.data.fake

import com.adauto.cinemahub.core.data.storage.AuthPreferencesStorage
import com.adauto.cinemahub.core.data.storage.SyncFailureStorage
import com.adauto.cinemahub.core.data.sync.SyncFailureEvent
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.network.security.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSessionManager(
    initialState: SessionState = SessionState.Guest
) : SessionManager {
    val sessionStateFlow = MutableStateFlow(initialState)
    override val sessionState: StateFlow<SessionState> = sessionStateFlow.asStateFlow()

    fun setSessionState(state: SessionState) {
        sessionStateFlow.value = state
    }

    override suspend fun awaitInitialSession(): SessionState = sessionStateFlow.value

    override suspend fun saveSession(
        accountId: Int,
        username: String,
        sessionId: String,
        avatarUrl: String?
    ) {
        sessionStateFlow.value = SessionState.Authenticated(
            accountId = accountId,
            username = username,
            sessionId = sessionId,
            avatarUrl = avatarUrl
        )
    }

    override suspend fun clearSession() {
        sessionStateFlow.value = SessionState.Guest
    }
}

class FakeAuthPreferencesStorage : AuthPreferencesStorage {
    var lastAccountId: String? = null
    var pendingRequestToken: Pair<String, Long>? = null

    override suspend fun getLastAccountId(): String? = lastAccountId

    override suspend fun saveLastAccountId(accountId: String) {
        lastAccountId = accountId
    }

    override suspend fun clearLastAccountId() {
        lastAccountId = null
    }

    override suspend fun savePendingRequestToken(token: String, createdAtMs: Long) {
        pendingRequestToken = token to createdAtMs
    }

    override suspend fun getPendingRequestToken(): Pair<String, Long>? = pendingRequestToken

    override suspend fun clearPendingRequestToken() {
        pendingRequestToken = null
    }
}

class FakeSyncFailureStorage : SyncFailureStorage {
    private val _unconsumedFailures = MutableStateFlow<List<SyncFailureEvent>>(emptyList())
    override val unconsumedFailures: Flow<List<SyncFailureEvent>> = _unconsumedFailures.asStateFlow()

    val recordedTimestamps = mutableListOf<Long>()

    override suspend fun recordFailure(event: SyncFailureEvent, timestamp: Long) {
        _unconsumedFailures.value = _unconsumedFailures.value + event
        recordedTimestamps.add(timestamp)
    }

    override suspend fun clearFailures() {
        _unconsumedFailures.value = emptyList()
    }
}
