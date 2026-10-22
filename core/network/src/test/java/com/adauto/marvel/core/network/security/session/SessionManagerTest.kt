package com.adauto.marvel.core.network.security.session

import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.network.security.KeystoreCipher
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val cipher: KeystoreCipher = mockk()
    private val storage: SessionStorage = mockk()

    @Before
    fun setUp() {
        coEvery { storage.loadEncryptedSession() } returns null
        coEvery { storage.saveEncryptedSession(any()) } returns Unit
        coEvery { storage.clear() } returns Unit
    }

    @Test
    fun initial_state_is_Guest_and_transitions_to_Authenticated_on_save() = runTest {
        val sessionManager = SessionManagerImpl(cipher, storage, testScope)
        assertThat(sessionManager.sessionState.value).isEqualTo(SessionState.Guest)

        coEvery { cipher.encrypt(any()) } returns byteArrayOf(1, 2, 3)

        sessionManager.saveSession(
            accountId = 42,
            username = "testuser",
            sessionId = "sess_abc",
            avatarUrl = null
        )

        assertThat(sessionManager.sessionState.value).isInstanceOf(SessionState.Authenticated::class.java)
        val auth = sessionManager.sessionState.value as SessionState.Authenticated
        assertThat(auth.accountId).isEqualTo(42)
        assertThat(auth.username).isEqualTo("testuser")
        assertThat(auth.sessionId).isEqualTo("sess_abc")
        assertThat(auth.avatarUrl).isNull()
    }

    @Test
    fun saveSession_encrypts_and_saves_payload_to_storage() = runTest {
        val sessionManager = SessionManagerImpl(cipher, storage, testScope)
        coEvery { cipher.encrypt(any()) } returns byteArrayOf(4, 5, 6)

        sessionManager.saveSession(
            accountId = 100,
            username = "marvel_fan",
            sessionId = "sess_xyz",
            avatarUrl = "https://image.tmdb.org/t/p/w500/avatar.jpg"
        )

        coVerify(exactly = 1) { storage.saveEncryptedSession(byteArrayOf(4, 5, 6)) }
        val auth = sessionManager.sessionState.value as SessionState.Authenticated
        assertThat(auth.avatarUrl).isEqualTo("https://image.tmdb.org/t/p/w500/avatar.jpg")
    }

    @Test
    fun clearSession_resets_state_to_Guest_and_clears_storage() = runTest {
        val sessionManager = SessionManagerImpl(cipher, storage, testScope)
        coEvery { cipher.encrypt(any()) } returns byteArrayOf(1, 2, 3)

        sessionManager.saveSession(42, "testuser", "sess_abc", null)
        assertThat(sessionManager.sessionState.value).isInstanceOf(SessionState.Authenticated::class.java)

        sessionManager.clearSession()

        assertThat(sessionManager.sessionState.value).isEqualTo(SessionState.Guest)
        coVerify(exactly = 1) { storage.clear() }
    }

    @Test
    fun restores_authenticated_session_from_storage_on_init() = runTest {
        val encryptedBytes = byteArrayOf(9, 8, 7)
        val decryptedJson = """{"accountId":42,"username":"restored_user","sessionId":"sess_restored","avatarUrl":null}"""

        coEvery { storage.loadEncryptedSession() } returns encryptedBytes
        coEvery { cipher.decrypt(encryptedBytes) } returns decryptedJson.toByteArray()

        val sessionManager = SessionManagerImpl(cipher, storage, testScope)
        testScope.advanceUntilIdle()

        assertThat(sessionManager.sessionState.value).isEqualTo(
            SessionState.Authenticated(
                accountId = 42,
                username = "restored_user",
                sessionId = "sess_restored",
                avatarUrl = null
            )
        )
    }

    @Test
    fun corrupted_encrypted_payload_cleanly_defaults_to_Guest_and_clears_storage() = runTest {
        val corruptedBytes = byteArrayOf(99, 99)
        coEvery { storage.loadEncryptedSession() } returns corruptedBytes
        coEvery { cipher.decrypt(corruptedBytes) } throws GeneralSecurityException("Corrupted payload")

        val sessionManager = SessionManagerImpl(cipher, storage, testScope)
        testScope.advanceUntilIdle()

        assertThat(sessionManager.sessionState.value).isEqualTo(SessionState.Guest)
        coVerify(exactly = 1) { storage.clear() }
    }

    @Test
    fun corrupted_json_cleanly_defaults_to_Guest_and_clears_storage() = runTest {
        val encryptedBytes = byteArrayOf(88, 88)
        coEvery { storage.loadEncryptedSession() } returns encryptedBytes
        coEvery { cipher.decrypt(encryptedBytes) } returns "invalid_json".toByteArray()

        val sessionManager = SessionManagerImpl(cipher, storage, testScope)
        testScope.advanceUntilIdle()

        assertThat(sessionManager.sessionState.value).isEqualTo(SessionState.Guest)
        coVerify(exactly = 1) { storage.clear() }
    }

    @Test
    fun empty_storage_cleanly_defaults_to_Guest() = runTest {
        coEvery { storage.loadEncryptedSession() } returns null

        val sessionManager = SessionManagerImpl(cipher, storage, testScope)
        testScope.advanceUntilIdle()

        assertThat(sessionManager.sessionState.value).isEqualTo(SessionState.Guest)
        coVerify(exactly = 0) { storage.clear() }
    }
}
