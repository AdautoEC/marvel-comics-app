package com.adauto.cinemahub.core.network.security.session

import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.network.security.KeystoreCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManagerImpl(
    private val cipher: KeystoreCipher,
    private val storage: SessionStorage,
    private val scope: CoroutineScope,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SessionManager {

    @Inject
    constructor(
        cipher: KeystoreCipher,
        storage: SessionStorage
    ) : this(
        cipher = cipher,
        storage = storage,
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    )

    private val mutex = Mutex()
    private val initDeferred = CompletableDeferred<Unit>()
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Guest)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    init {
        scope.launch {
            try {
                mutex.withLock {
                    loadStoredSession()
                }
            } finally {
                initDeferred.complete(Unit)
            }
        }
    }

    override suspend fun awaitInitialSession(): SessionState {
        initDeferred.await()
        return sessionState.value
    }

    private suspend fun loadStoredSession() {
        try {
            val blob = storage.loadEncryptedSession() ?: return
            val decryptedJson = cipher.decrypt(blob).decodeToString()
            val auth = json.decodeFromString<SessionState.Authenticated>(decryptedJson)
            _sessionState.value = auth
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            _sessionState.value = SessionState.Guest
            runCatching { storage.clear() }
        }
    }

    override suspend fun saveSession(
        accountId: Int,
        username: String,
        sessionId: String,
        avatarUrl: String?
    ) = mutex.withLock {
        val auth = SessionState.Authenticated(
            accountId = accountId,
            username = username,
            sessionId = sessionId,
            avatarUrl = avatarUrl
        )
        val serialized = json.encodeToString(SessionState.Authenticated.serializer(), auth)
        val encryptedBlob = cipher.encrypt(serialized.encodeToByteArray())
        storage.saveEncryptedSession(encryptedBlob)
        _sessionState.value = auth
    }

    override suspend fun clearSession() = mutex.withLock {
        storage.clear()
        _sessionState.value = SessionState.Guest
    }
}
