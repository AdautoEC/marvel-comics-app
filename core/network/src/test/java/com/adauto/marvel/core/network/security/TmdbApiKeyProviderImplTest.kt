package com.adauto.marvel.core.network.security

import com.adauto.marvel.core.network.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigFetchThrottledException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbApiKeyProviderImplTest {

    private val remoteConfig: FirebaseRemoteConfig = mockk()
    private val encryptedStorage: EncryptedKeyStorage = mockk()
    private val cipher: KeystoreCipher = mockk()

    private var currentTimeMs = 1_000_000L
    private val timeProvider: () -> Long = { currentTimeMs }

    private lateinit var testScope: TestScope
    private lateinit var provider: TmdbApiKeyProviderImpl

    @Before
    fun setUp() {
        currentTimeMs = 1_000_000L
    }

    private fun <T> mockSuccessfulTask(result: T): Task<T> {
        val task = mockk<Task<T>>()
        every { task.addOnSuccessListener(any()) } answers {
            val listener = firstArg<com.google.android.gms.tasks.OnSuccessListener<T>>()
            listener.onSuccess(result)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
        return task
    }

    private fun <T> mockFailedTask(exception: Exception): Task<T> {
        val task = mockk<Task<T>>()
        every { task.addOnSuccessListener(any()) } returns task
        every { task.addOnFailureListener(any()) } answers {
            val listener = firstArg<com.google.android.gms.tasks.OnFailureListener>()
            listener.onFailure(exception)
            task
        }
        return task
    }

    private fun <T> mockHangingTask(): Task<T> {
        val task = mockk<Task<T>>()
        every { task.addOnSuccessListener(any()) } returns task
        every { task.addOnFailureListener(any()) } returns task
        return task
    }

    @Test
    fun getCredentials_whenCachedValid_returnsFromMemoryWithoutStorageOrNetwork() = runTest {
        provider = TmdbApiKeyProviderImpl(
            externalScope = this,
            remoteConfig = remoteConfig,
            encryptedStorage = encryptedStorage,
            cipher = cipher,
            timeProvider = timeProvider
        )

        val encryptedBlob = byteArrayOf(10, 20, 30)
        coEvery { encryptedStorage.loadEncryptedBlob() } returns encryptedBlob
        every { cipher.decrypt(encryptedBlob) } returns """{"apiKey":"cached_key","readAccessToken":"cached_token"}""".toByteArray()

        val firstCall = provider.getCredentials()
        assertEquals(TmdbCredentials(apiKey = "cached_key", readAccessToken = "cached_token"), firstCall)

        currentTimeMs += 10_000L

        val secondCall = provider.getCredentials()
        assertEquals(firstCall, secondCall)

        coVerify(exactly = 1) { encryptedStorage.loadEncryptedBlob() }
        coVerify(exactly = 0) { remoteConfig.fetchAndActivate() }
        coVerify(exactly = 0) { remoteConfig.fetch(any()) }
    }

    @Test
    fun getCredentials_whenCacheExpired_returnsStaleAndLaunchesBackgroundRefresh() = runTest {
        provider = TmdbApiKeyProviderImpl(
            externalScope = this,
            remoteConfig = remoteConfig,
            encryptedStorage = encryptedStorage,
            cipher = cipher,
            timeProvider = timeProvider
        )

        val initialBlob = byteArrayOf(1, 2, 3)
        coEvery { encryptedStorage.loadEncryptedBlob() } returns initialBlob
        every { cipher.decrypt(initialBlob) } returns """{"apiKey":"stale_key","readAccessToken":"stale_token"}""".toByteArray()

        val initialCreds = provider.getCredentials()
        assertEquals("stale_key", initialCreds?.apiKey)

        currentTimeMs += 25 * 60 * 60 * 1000L

        every { remoteConfig.fetchAndActivate() } returns mockSuccessfulTask(true)
        every { remoteConfig.getString("tmdb_api_key") } returns "refreshed_key"
        every { remoteConfig.getString("tmdb_read_access_token") } returns "refreshed_token"
        every { cipher.encrypt(any()) } returns byteArrayOf(99)
        coEvery { encryptedStorage.saveEncryptedBlob(byteArrayOf(99)) } returns Unit

        val staleCreds = provider.getCredentials()
        assertEquals("stale_key", staleCreds?.apiKey)

        advanceUntilIdle()

        coVerify(exactly = 1) { remoteConfig.fetchAndActivate() }
        coVerify(exactly = 1) { encryptedStorage.saveEncryptedBlob(byteArrayOf(99)) }

        val freshCreds = provider.getCredentials()
        assertEquals("refreshed_key", freshCreds?.apiKey)
        assertEquals("refreshed_token", freshCreds?.readAccessToken)
    }

    @Test
    fun getCredentials_whenRemoteConfigTimesOut_fallsBackGracefully() = runTest {
        provider = TmdbApiKeyProviderImpl(
            externalScope = this,
            remoteConfig = remoteConfig,
            encryptedStorage = encryptedStorage,
            cipher = cipher,
            timeProvider = timeProvider
        )

        coEvery { encryptedStorage.loadEncryptedBlob() } returns null
        every { remoteConfig.fetchAndActivate() } returns mockHangingTask()

        val result = provider.getCredentials()

        val expectedApiKey = BuildConfig.TMDB_API_KEY.takeIf { it.isNotBlank() }
        val expectedToken = BuildConfig.TMDB_READ_ACCESS_TOKEN.takeIf { it.isNotBlank() }
        if (expectedApiKey != null || expectedToken != null) {
            assertEquals(expectedApiKey, result?.apiKey)
            assertEquals(expectedToken, result?.readAccessToken)
        } else {
            assertNull(result)
        }
    }

    @Test
    fun refreshKeyForced_whenThrottled_returnsCurrentKeyWithoutCrashing() = runTest {
        provider = TmdbApiKeyProviderImpl(
            externalScope = this,
            remoteConfig = remoteConfig,
            encryptedStorage = encryptedStorage,
            cipher = cipher,
            timeProvider = timeProvider
        )

        val encryptedBlob = byteArrayOf(1, 2)
        coEvery { encryptedStorage.loadEncryptedBlob() } returns encryptedBlob
        every { cipher.decrypt(encryptedBlob) } returns """{"apiKey":"current_key","readAccessToken":"current_token"}""".toByteArray()

        val initialCreds = provider.getCredentials()
        assertEquals("current_key", initialCreds?.apiKey)

        every { remoteConfig.fetch(0) } returns mockFailedTask(
            FirebaseRemoteConfigFetchThrottledException("Throttled", 60_000L)
        )

        val result = provider.refreshKeyForced()

        assertEquals("current_key", result?.apiKey)
        assertEquals("current_token", result?.readAccessToken)
    }

    @Test
    fun refreshKeyForced_respectsCooldown() = runTest {
        provider = TmdbApiKeyProviderImpl(
            externalScope = this,
            remoteConfig = remoteConfig,
            encryptedStorage = encryptedStorage,
            cipher = cipher,
            timeProvider = timeProvider
        )

        val encryptedBlob = byteArrayOf(5, 6)
        coEvery { encryptedStorage.loadEncryptedBlob() } returns encryptedBlob
        every { cipher.decrypt(encryptedBlob) } returns """{"apiKey":"initial_key","readAccessToken":"initial_token"}""".toByteArray()
        provider.getCredentials()

        every { remoteConfig.fetch(0) } returns mockSuccessfulTask(null)
        every { remoteConfig.activate() } returns mockSuccessfulTask(true)
        every { remoteConfig.getString("tmdb_api_key") } returns "forced_key_1"
        every { remoteConfig.getString("tmdb_read_access_token") } returns "forced_token_1"
        every { cipher.encrypt(any()) } returns byteArrayOf(77)
        coEvery { encryptedStorage.saveEncryptedBlob(byteArrayOf(77)) } returns Unit

        val result1 = provider.refreshKeyForced()
        assertEquals("forced_key_1", result1?.apiKey)
        coVerify(exactly = 1) { remoteConfig.fetch(0) }

        currentTimeMs += 5 * 60 * 1000L

        val result2 = provider.refreshKeyForced()
        assertEquals("forced_key_1", result2?.apiKey)
        coVerify(exactly = 1) { remoteConfig.fetch(0) }

        currentTimeMs += 11 * 60 * 1000L

        every { remoteConfig.getString("tmdb_api_key") } returns "forced_key_2"
        val result3 = provider.refreshKeyForced()
        assertEquals("forced_key_2", result3?.apiKey)
        coVerify(exactly = 2) { remoteConfig.fetch(0) }
    }

    @Test
    fun getApiKey_and_getReadAccessToken_delegateToCredentials() = runTest {
        provider = TmdbApiKeyProviderImpl(
            externalScope = this,
            remoteConfig = remoteConfig,
            encryptedStorage = encryptedStorage,
            cipher = cipher,
            timeProvider = timeProvider
        )

        val encryptedBlob = byteArrayOf(1)
        coEvery { encryptedStorage.loadEncryptedBlob() } returns encryptedBlob
        every { cipher.decrypt(encryptedBlob) } returns """{"apiKey":"delegated_key","readAccessToken":"delegated_token"}""".toByteArray()

        assertEquals("delegated_key", provider.getApiKey())
        assertEquals("delegated_token", provider.getReadAccessToken())
    }

    @Test
    fun getCredentials_whenStorageHasPlainTextApiKey_parsesAsApiKey() = runTest {
        provider = TmdbApiKeyProviderImpl(
            externalScope = this,
            remoteConfig = remoteConfig,
            encryptedStorage = encryptedStorage,
            cipher = cipher,
            timeProvider = timeProvider
        )

        val encryptedBlob = byteArrayOf(2)
        coEvery { encryptedStorage.loadEncryptedBlob() } returns encryptedBlob
        every { cipher.decrypt(encryptedBlob) } returns "legacy_plain_key".toByteArray()

        val creds = provider.getCredentials()
        assertEquals("legacy_plain_key", creds?.apiKey)
        assertNull(creds?.readAccessToken)
    }

    @Test
    fun getCredentials_whenCacheExpiredAndMultipleCalls_stampedeGuardLaunchesOnlyOneRefresh() = runTest {
        provider = TmdbApiKeyProviderImpl(
            externalScope = this,
            remoteConfig = remoteConfig,
            encryptedStorage = encryptedStorage,
            cipher = cipher,
            timeProvider = timeProvider
        )

        val initialBlob = byteArrayOf(1, 2, 3)
        coEvery { encryptedStorage.loadEncryptedBlob() } returns initialBlob
        every { cipher.decrypt(initialBlob) } returns """{"apiKey":"stale_key","readAccessToken":"stale_token"}""".toByteArray()

        provider.getCredentials()

        currentTimeMs += 25 * 60 * 60 * 1000L

        every { remoteConfig.fetchAndActivate() } returns mockSuccessfulTask(true)
        every { remoteConfig.getString("tmdb_api_key") } returns "refreshed_key"
        every { remoteConfig.getString("tmdb_read_access_token") } returns "refreshed_token"
        every { cipher.encrypt(any()) } returns byteArrayOf(99)
        coEvery { encryptedStorage.saveEncryptedBlob(byteArrayOf(99)) } returns Unit

        val call1 = provider.getCredentials()
        val call2 = provider.getCredentials()
        val call3 = provider.getCredentials()

        assertEquals("stale_key", call1?.apiKey)
        assertEquals("stale_key", call2?.apiKey)
        assertEquals("stale_key", call3?.apiKey)

        advanceUntilIdle()

        coVerify(exactly = 1) { remoteConfig.fetchAndActivate() }
    }

    @Test
    fun getCredentials_whenDebugFallbackUsed_cachesInMemoryForSubsequentCalls() = runTest {
        provider = TmdbApiKeyProviderImpl(
            externalScope = this,
            remoteConfig = remoteConfig,
            encryptedStorage = encryptedStorage,
            cipher = cipher,
            timeProvider = timeProvider
        )

        coEvery { encryptedStorage.loadEncryptedBlob() } returns null
        every { remoteConfig.fetchAndActivate() } returns mockFailedTask(RuntimeException("Network down"))

        val firstCall = provider.getCredentials()
        if (firstCall != null) {
            val secondCall = provider.getCredentials()
            assertEquals(firstCall, secondCall)

            coVerify(exactly = 1) { encryptedStorage.loadEncryptedBlob() }
            coVerify(exactly = 1) { remoteConfig.fetchAndActivate() }
        }
    }

    @Test
    fun refreshKeyForced_whenCalledConcurrently_mutexSerializesAndSecondHitsCooldown() = runTest {
        provider = TmdbApiKeyProviderImpl(
            externalScope = this,
            remoteConfig = remoteConfig,
            encryptedStorage = encryptedStorage,
            cipher = cipher,
            timeProvider = timeProvider
        )

        coEvery { encryptedStorage.loadEncryptedBlob() } returns null
        every { remoteConfig.fetch(0) } returns mockSuccessfulTask(null)
        every { remoteConfig.activate() } returns mockSuccessfulTask(true)
        every { remoteConfig.getString("tmdb_api_key") } returns "mutex_key"
        every { remoteConfig.getString("tmdb_read_access_token") } returns "mutex_token"
        every { cipher.encrypt(any()) } returns byteArrayOf(88)
        coEvery { encryptedStorage.saveEncryptedBlob(byteArrayOf(88)) } returns Unit

        val deferred1 = async { provider.refreshKeyForced() }
        val deferred2 = async { provider.refreshKeyForced() }

        val res1 = deferred1.await()
        val res2 = deferred2.await()

        assertEquals("mutex_key", res1?.apiKey)
        assertEquals("mutex_key", res2?.apiKey)
        coVerify(exactly = 1) { remoteConfig.fetch(0) }
    }
}
