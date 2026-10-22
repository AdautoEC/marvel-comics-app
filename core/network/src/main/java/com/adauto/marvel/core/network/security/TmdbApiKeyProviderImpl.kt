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
    private val encryptedStorage: EncryptedKeyStorage
) : TmdbApiKeyProvider {

    companion object {
        const val KEY_TMDB_API_KEY = "tmdb_api_key"
        const val FALLBACK_API_KEY = "a2ed008f1b8419141aaef581625e9c67"
    }

    override suspend fun getApiKey(): String? {
        val cached = encryptedStorage.loadEncryptedBlob()?.toString(Charsets.UTF_8)
        if (!cached.isNullOrBlank()) {
            return cached
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

        val resolved = remoteKey ?: FALLBACK_API_KEY
        encryptedStorage.saveEncryptedBlob(resolved.toByteArray(Charsets.UTF_8))
        return resolved
    }
}
