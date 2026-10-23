package com.adauto.marvel.core.network.security.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.Base64

interface SessionStorage {
    suspend fun loadEncryptedSession(): ByteArray?
    suspend fun saveEncryptedSession(blob: ByteArray)
    suspend fun clear()
}

class DataStoreSessionStorage(
    private val dataStore: DataStore<Preferences>
) : SessionStorage {

    companion object {
        val KEY_SESSION_BLOB = stringPreferencesKey("tmdb_session_blob")
    }

    override suspend fun loadEncryptedSession(): ByteArray? {
        val base64String = dataStore.data.map { preferences ->
            preferences[KEY_SESSION_BLOB]
        }.firstOrNull()

        return if (base64String.isNullOrEmpty()) {
            null
        } else {
            try {
                Base64.getDecoder().decode(base64String)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    override suspend fun saveEncryptedSession(blob: ByteArray) {
        val base64String = Base64.getEncoder().encodeToString(blob)
        dataStore.edit { preferences ->
            preferences[KEY_SESSION_BLOB] = base64String
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_SESSION_BLOB)
        }
    }
}
