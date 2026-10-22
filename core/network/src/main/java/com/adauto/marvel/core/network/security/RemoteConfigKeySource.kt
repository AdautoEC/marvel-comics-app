package com.adauto.marvel.core.network.security

import com.adauto.marvel.core.model.MarvelKeys
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface RemoteConfigKeySource {
    suspend fun fetchKeys(): MarvelKeys?
}

@Singleton
class FirebaseRemoteConfigKeySource @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : RemoteConfigKeySource {

    companion object {
        const val KEY_MARVEL_KEYS = "marvel_keys"
    }

    override suspend fun fetchKeys(): MarvelKeys? {
        return try {
            suspendCancellableCoroutine<Boolean> { continuation ->
                remoteConfig.fetchAndActivate()
                    .addOnSuccessListener {
                        if (continuation.isActive) {
                            continuation.resume(it)
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) {
                            continuation.resumeWithException(it)
                        }
                    }
            }
            val rawJson = remoteConfig.getString(KEY_MARVEL_KEYS)
            if (rawJson.isBlank()) {
                null
            } else {
                json.decodeFromString<MarvelKeys>(rawJson)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
