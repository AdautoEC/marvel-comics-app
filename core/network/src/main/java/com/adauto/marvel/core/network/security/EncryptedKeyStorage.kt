package com.adauto.marvel.core.network.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.Base64

interface EncryptedKeyStorage {
    suspend fun loadEncryptedBlob(): ByteArray?
    suspend fun saveEncryptedBlob(blob: ByteArray)
    suspend fun clear()
}

class DataStoreEncryptedKeyStorage(
    private val dataStore: DataStore<Preferences>
) : EncryptedKeyStorage {

    companion object {
        val KEY_BLOB = stringPreferencesKey("marvel_keys_blob")
    }

    override suspend fun loadEncryptedBlob(): ByteArray? {
        val base64String = dataStore.data.map { preferences ->
            preferences[KEY_BLOB]
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

    override suspend fun saveEncryptedBlob(blob: ByteArray) {
        val base64String = Base64.getEncoder().encodeToString(blob)
        dataStore.edit { preferences ->
            preferences[KEY_BLOB] = base64String
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_BLOB)
        }
    }
}
