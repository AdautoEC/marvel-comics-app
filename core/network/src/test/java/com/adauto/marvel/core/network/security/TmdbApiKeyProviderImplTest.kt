package com.adauto.marvel.core.network.security

import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TmdbApiKeyProviderImplTest {

    private val remoteConfig: FirebaseRemoteConfig = mockk()
    private val encryptedStorage: EncryptedKeyStorage = mockk()
    private val cipher: KeystoreCipher = mockk()

    private lateinit var provider: TmdbApiKeyProviderImpl

    @Before
    fun setUp() {
        provider = TmdbApiKeyProviderImpl(remoteConfig, encryptedStorage, cipher)
    }

    @Test
    fun getApiKey_whenStorageHasKey_returnsDecryptedKeyWithoutCallingRemoteConfig() = runTest {
        val encryptedBlob = byteArrayOf(10, 20, 30)
        coEvery { encryptedStorage.loadEncryptedBlob() } returns encryptedBlob
        every { cipher.decrypt(encryptedBlob) } returns "my_secure_tmdb_key".toByteArray()

        val result = provider.getApiKey()

        assertEquals("my_secure_tmdb_key", result)
        coVerify(exactly = 0) { remoteConfig.fetchAndActivate() }
    }

    @Test
    fun getApiKey_whenStorageEmptyAndRemoteConfigSucceeds_encryptsAndReturnsKey() = runTest {
        coEvery { encryptedStorage.loadEncryptedBlob() } returns null
        val task = mockk<Task<Boolean>>()
        every { remoteConfig.fetchAndActivate() } returns task
        every { task.addOnSuccessListener(any()) } answers {
            val listener = firstArg<com.google.android.gms.tasks.OnSuccessListener<Boolean>>()
            listener.onSuccess(true)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
        every { remoteConfig.getString("tmdb_api_key") } returns "remote_tmdb_key_123"

        every { cipher.encrypt("remote_tmdb_key_123".toByteArray()) } returns byteArrayOf(99, 88, 77)
        coEvery { encryptedStorage.saveEncryptedBlob(byteArrayOf(99, 88, 77)) } returns Unit

        val result = provider.getApiKey()

        assertEquals("remote_tmdb_key_123", result)
        coVerify(exactly = 1) { encryptedStorage.saveEncryptedBlob(byteArrayOf(99, 88, 77)) }
    }

    @Test
    fun getApiKey_whenStorageEmptyAndRemoteConfigFails_returnsNullWithoutHardcodedFallback() = runTest {
        coEvery { encryptedStorage.loadEncryptedBlob() } returns null
        val task = mockk<Task<Boolean>>()
        every { remoteConfig.fetchAndActivate() } returns task
        every { task.addOnSuccessListener(any()) } returns task
        every { task.addOnFailureListener(any()) } answers {
            val listener = firstArg<com.google.android.gms.tasks.OnFailureListener>()
            listener.onFailure(RuntimeException("Remote Config fetch failed"))
            task
        }

        val result = provider.getApiKey()

        assertNull(result)
        coVerify(exactly = 0) { encryptedStorage.saveEncryptedBlob(any()) }
    }
}
