package com.adauto.marvel.core.network.security

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class TmdbApiKeyProviderImpl @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    private val encryptedStorage: EncryptedKeyStorage,
    private val cipher: KeystoreCipher
) : TmdbApiKeyProvider {

    companion object {
        const val KEY_TMDB_API_KEY = "tmdb_api_key"
    }

    override suspend fun getApiKey(): String? {
        val cachedEncrypted = encryptedStorage.loadEncryptedBlob()
        if (cachedEncrypted != null) {
            val decrypted = try {
                cipher.decrypt(cachedEncrypted).toString(Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
            if (!decrypted.isNullOrBlank()) {
                return decrypted
            }
        }

        val remoteKey = try {
            suspendCancellableCoroutine<Boolean> { cont ->
                remoteConfig.fetchAndActivate()
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
            }
            remoteConfig.getString(KEY_TMDB_API_KEY).takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        if (!remoteKey.isNullOrBlank()) {
            val encrypted = try {
                cipher.encrypt(remoteKey.toByteArray(Charsets.UTF_8))
            } catch (_: Exception) {
                null
            }
            if (encrypted != null) {
                encryptedStorage.saveEncryptedBlob(encrypted)
            }
            return remoteKey
        }

        return null
    }

    override suspend fun getReadAccessToken(): String? = null

    override suspend fun getCredentials(): TmdbCredentials? =
        getApiKey()?.let { TmdbCredentials(apiKey = it) }

    override suspend fun refreshKeyForced(): TmdbCredentials? = getCredentials()
}
