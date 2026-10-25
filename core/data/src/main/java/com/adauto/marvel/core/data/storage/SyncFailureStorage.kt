package com.adauto.marvel.core.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.adauto.marvel.core.data.sync.SyncFailureEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PersistedSyncFailure(
    val movieId: Int,
    val target: String,
    val error: String?,
    val timestamp: Long
)

interface SyncFailureStorage {
    val unconsumedFailures: Flow<List<SyncFailureEvent>>
    suspend fun recordFailure(event: SyncFailureEvent, timestamp: Long)
    suspend fun clearFailures()
}

@Singleton
class DataStoreSyncFailureStorage @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SyncFailureStorage {

    companion object {
        val KEY_UNCONSUMED_FAILURES = stringPreferencesKey("unconsumed_sync_failures")
        private val json = Json { ignoreUnknownKeys = true }
    }

    override val unconsumedFailures: Flow<List<SyncFailureEvent>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val raw = preferences[KEY_UNCONSUMED_FAILURES] ?: return@map emptyList()
            try {
                val list = json.decodeFromString<List<PersistedSyncFailure>>(raw)
                list.map { SyncFailureEvent(movieId = it.movieId, target = it.target, error = it.error) }
            } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception) {
                emptyList()
            }
        }

    override suspend fun recordFailure(event: SyncFailureEvent, timestamp: Long) {
        dataStore.edit { preferences ->
            val raw = preferences[KEY_UNCONSUMED_FAILURES]
            val existing = if (raw != null) {
                try {
                    json.decodeFromString<List<PersistedSyncFailure>>(raw)
                } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            val updated = existing + PersistedSyncFailure(
                movieId = event.movieId,
                target = event.target,
                error = event.error,
                timestamp = timestamp
            )
            preferences[KEY_UNCONSUMED_FAILURES] = json.encodeToString(updated)
        }
    }

    override suspend fun clearFailures() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_UNCONSUMED_FAILURES)
        }
    }
}
