package com.adauto.marvel.core.network.security

import com.adauto.marvel.core.model.MarvelKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarvelKeyProviderImpl @Inject constructor(
    private val remoteConfigSource: RemoteConfigKeySource,
    private val cipher: KeystoreCipher,
    private val storage: EncryptedKeyStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : MarvelKeyProvider {

    private val mutex = Mutex()
    private var inMemoryKeys: MarvelKeys? = null

    private suspend fun loadCachedKeysOrNull(): MarvelKeys? {
        inMemoryKeys?.let { return it }
        val storedBlob = storage.loadEncryptedBlob() ?: return null
        return try {
            val decryptedJson = cipher.decrypt(storedBlob).decodeToString()
            val keys = json.decodeFromString<MarvelKeys>(decryptedJson)
            inMemoryKeys = keys
            keys
        } catch (_: GeneralSecurityException) {
            storage.clear()
            cipher.deleteKey()
            null
        } catch (_: Exception) {
            storage.clear()
            cipher.deleteKey()
            null
        }
    }

    override suspend fun current(): MarvelKeys? = mutex.withLock {
        loadCachedKeysOrNull()?.let { return it }

        val remoteKeys = withTimeoutOrNull(10_000L) {
            remoteConfigSource.fetchKeys()
        } ?: return null

        saveKeysInternal(remoteKeys)
        inMemoryKeys = remoteKeys
        return remoteKeys
    }

    override suspend fun forceRefresh(): Boolean = mutex.withLock {
        val newKeys = remoteConfigSource.fetchKeys() ?: return false
        val currentVer = inMemoryKeys?.keyVersion ?: loadCachedKeysOrNull()?.keyVersion ?: 0
        saveKeysInternal(newKeys)
        inMemoryKeys = newKeys
        return newKeys.keyVersion != currentVer
    }

    override suspend fun refreshIfNewer() {
        try {
            val remoteKeys = remoteConfigSource.fetchKeys() ?: return
            mutex.withLock {
                val currentVer = inMemoryKeys?.keyVersion ?: loadCachedKeysOrNull()?.keyVersion ?: 0
                if (remoteKeys.keyVersion > currentVer) {
                    saveKeysInternal(remoteKeys)
                    inMemoryKeys = remoteKeys
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Background check failure is non-fatal
        }
    }

    private suspend fun saveKeysInternal(keys: MarvelKeys) {
        val serialized = json.encodeToString(MarvelKeys.serializer(), keys).encodeToByteArray()
        val encryptedBlob = cipher.encrypt(serialized)
        storage.saveEncryptedBlob(encryptedBlob)
    }
}
