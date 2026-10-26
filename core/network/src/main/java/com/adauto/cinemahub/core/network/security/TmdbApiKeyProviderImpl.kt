package com.adauto.cinemahub.core.network.security

import android.util.Log
import com.adauto.cinemahub.core.network.BuildConfig
import com.adauto.cinemahub.core.network.di.ApplicationScope
import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigFetchThrottledException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class TmdbApiKeyProviderImpl(
    private val externalScope: CoroutineScope,
    private val remoteConfig: FirebaseRemoteConfig,
    private val encryptedStorage: EncryptedKeyStorage,
    private val cipher: KeystoreCipher,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : TmdbApiKeyProvider {

    @Inject
    constructor(
        @ApplicationScope externalScope: CoroutineScope,
        remoteConfig: FirebaseRemoteConfig,
        encryptedStorage: EncryptedKeyStorage,
        cipher: KeystoreCipher
    ) : this(
        externalScope = externalScope,
        remoteConfig = remoteConfig,
        encryptedStorage = encryptedStorage,
        cipher = cipher,
        timeProvider = { System.currentTimeMillis() }
    )

    @Serializable
    internal data class StoredCredentials(
        val apiKey: String? = null,
        val readAccessToken: String? = null,
        val fetchedAt: Long = 0L
    )

    private val cachedCredentials = AtomicReference<Pair<TmdbCredentials, Long>?>()
    private val lastForcedFetchMs = AtomicLong(-1L)
    private val isRefreshing = AtomicBoolean(false)
    private val forcedRefreshMutex = Mutex()
    private val coldStartMutex = Mutex()

    companion object {
        const val KEY_TMDB_API_KEY = "tmdb_api_key"
        const val KEY_TMDB_READ_ACCESS_TOKEN = "tmdb_read_access_token"
        private const val TTL_24H = 24 * 60 * 60 * 1000L
        private const val COOLDOWN_15M = 15 * 60 * 1000L
        private const val REMOTE_CONFIG_TIMEOUT_MS = 5_000L
        private const val TAG = "TmdbApiKeyProvider"
    }

    override suspend fun getApiKey(): String? = getCredentials()?.apiKey

    override suspend fun getReadAccessToken(): String? = getCredentials()?.readAccessToken

    override suspend fun getCredentials(): TmdbCredentials? {
        cachedCredentials.get()?.let { return checkValidOrRefresh(it) }

        return coldStartMutex.withLock {
            cachedCredentials.get()?.let { return@withLock checkValidOrRefresh(it) }

            loadFromStorage()
                ?: fetchAndStoreCredentials()
                ?: loadFallbackCredentials()
        }
    }

    private fun checkValidOrRefresh(cached: Pair<TmdbCredentials, Long>): TmdbCredentials {
        val (creds, cachedAt) = cached
        val now = timeProvider()
        if (now - cachedAt < TTL_24H) {
            return creds
        }
        if (isRefreshing.compareAndSet(false, true)) {
            externalScope.launch {
                try {
                    fetchAndStoreCredentials()
                } finally {
                    isRefreshing.set(false)
                }
            }
        }
        return creds
    }

    private suspend fun loadFromStorage(): TmdbCredentials? {
        val cachedEncrypted = encryptedStorage.loadEncryptedBlob() ?: return null
        val decrypted = try {
            cipher.decrypt(cachedEncrypted).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
        val stored = decrypted?.takeIf { it.isNotBlank() }?.let { parseStoredCredentials(it, timeProvider) }
        val creds = stored?.let { TmdbCredentials(it.apiKey, it.readAccessToken) }?.takeIf { it.isNotEmpty }

        if (creds != null) {
            val now = timeProvider()
            cachedCredentials.set(creds to stored.fetchedAt)
            if (now - stored.fetchedAt >= TTL_24H && isRefreshing.compareAndSet(false, true)) {
                externalScope.launch {
                    try {
                        fetchAndStoreCredentials()
                    } finally {
                        isRefreshing.set(false)
                    }
                }
            }
        }
        return creds
    }

    private fun loadFallbackCredentials(): TmdbCredentials? {
        if (BuildConfig.DEBUG) {
            val fallback = TmdbCredentials(
                apiKey = BuildConfig.TMDB_API_KEY.takeIf { it.isNotBlank() },
                readAccessToken = BuildConfig.TMDB_READ_ACCESS_TOKEN.takeIf { it.isNotBlank() }
            )
            if (fallback.isNotEmpty) {
                cachedCredentials.set(fallback to timeProvider())
                return fallback
            }
        }
        return null
    }

    override suspend fun refreshKeyForced(): TmdbCredentials? = forcedRefreshMutex.withLock {
        val now = timeProvider()
        val lastFetch = lastForcedFetchMs.get()
        if (lastFetch >= 0L && (now - lastFetch) < COOLDOWN_15M) {
            return@withLock cachedCredentials.get()?.first ?: getCredentials()
        }
        lastForcedFetchMs.set(now)

        try {
            withTimeout(REMOTE_CONFIG_TIMEOUT_MS) {
                remoteConfig.fetch(0).awaitTask()
                remoteConfig.activate().awaitTask()
                val creds = extractRemoteCredentials()
                if (creds.isNotEmpty) {
                    persistAndCache(creds)
                    creds
                } else {
                    cachedCredentials.get()?.first ?: getCredentials()
                }
            }
        } catch (e: FirebaseRemoteConfigFetchThrottledException) {
            Log.w(TAG, "Remote Config fetch throttled: ${e.message}")
            cachedCredentials.get()?.first ?: getCredentials()
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Remote Config fetch timed out: ${e.message}")
            cachedCredentials.get()?.first ?: getCredentials()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Remote Config forced refresh failed: ${e.message}")
            cachedCredentials.get()?.first ?: getCredentials()
        }
    }

    private suspend fun fetchAndStoreCredentials(): TmdbCredentials? {
        return try {
            withTimeout(REMOTE_CONFIG_TIMEOUT_MS) {
                remoteConfig.fetchAndActivate().awaitTask()
                val creds = extractRemoteCredentials()
                if (creds.isNotEmpty) {
                    persistAndCache(creds)
                    creds
                } else {
                    null
                }
            }
        } catch (e: FirebaseRemoteConfigFetchThrottledException) {
            Log.w(TAG, "Remote Config fetch throttled: ${e.message}")
            null
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Remote Config fetch timed out: ${e.message}")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Remote Config fetch failed: ${e.message}")
            null
        }
    }

    private fun extractRemoteCredentials(): TmdbCredentials {
        val apiKey = remoteConfig.getString(KEY_TMDB_API_KEY).takeIf { it.isNotBlank() }
        val readAccessToken = remoteConfig.getString(KEY_TMDB_READ_ACCESS_TOKEN).takeIf { it.isNotBlank() }
        return TmdbCredentials(apiKey = apiKey, readAccessToken = readAccessToken)
    }

    private suspend fun persistAndCache(creds: TmdbCredentials) {
        val now = timeProvider()
        val stored = StoredCredentials(
            apiKey = creds.apiKey,
            readAccessToken = creds.readAccessToken,
            fetchedAt = now
        )
        val serialized = Json.encodeToString(stored)
        val encrypted = try {
            cipher.encrypt(serialized.toByteArray(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
        if (encrypted != null) {
            encryptedStorage.saveEncryptedBlob(encrypted)
        }
        cachedCredentials.set(creds to now)
    }
}

private fun parseStoredCredentials(
    decrypted: String,
    timeProvider: () -> Long
): TmdbApiKeyProviderImpl.StoredCredentials? {
    return try {
        Json.decodeFromString<TmdbApiKeyProviderImpl.StoredCredentials>(decrypted)
    } catch (_: Exception) {
        val legacy = TmdbCredentials.parse(decrypted)
        if (legacy.isNotEmpty) {
            TmdbApiKeyProviderImpl.StoredCredentials(legacy.apiKey, legacy.readAccessToken, timeProvider())
        } else {
            null
        }
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exception ->
        if (cont.isActive) cont.resumeWithException(exception)
    }
}
