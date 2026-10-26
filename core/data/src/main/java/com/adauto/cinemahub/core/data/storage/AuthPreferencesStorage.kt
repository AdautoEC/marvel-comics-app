package com.adauto.cinemahub.core.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface AuthPreferencesStorage {
    suspend fun getLastAccountId(): String?
    suspend fun saveLastAccountId(accountId: String)
    suspend fun clearLastAccountId()

    suspend fun savePendingRequestToken(token: String, createdAtMs: Long)
    suspend fun getPendingRequestToken(): Pair<String, Long>?
    suspend fun clearPendingRequestToken()
}

@Singleton
class DataStoreAuthPreferencesStorage @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : AuthPreferencesStorage {

    companion object {
        val KEY_LAST_ACCOUNT_ID = stringPreferencesKey("last_account_id")
        val KEY_PENDING_REQUEST_TOKEN = stringPreferencesKey("pending_request_token")
        val KEY_PENDING_REQUEST_TOKEN_CREATED_AT = longPreferencesKey("pending_request_token_created_at")
    }

    override suspend fun getLastAccountId(): String? {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[KEY_LAST_ACCOUNT_ID]
            }.firstOrNull()
    }

    override suspend fun saveLastAccountId(accountId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_ACCOUNT_ID] = accountId
        }
    }

    override suspend fun clearLastAccountId() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_LAST_ACCOUNT_ID)
        }
    }

    override suspend fun savePendingRequestToken(token: String, createdAtMs: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_PENDING_REQUEST_TOKEN] = token
            preferences[KEY_PENDING_REQUEST_TOKEN_CREATED_AT] = createdAtMs
        }
    }

    override suspend fun getPendingRequestToken(): Pair<String, Long>? {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val token = preferences[KEY_PENDING_REQUEST_TOKEN]
                val createdAt = preferences[KEY_PENDING_REQUEST_TOKEN_CREATED_AT]
                if (token != null && createdAt != null) {
                    token to createdAt
                } else {
                    null
                }
            }.firstOrNull()
    }

    override suspend fun clearPendingRequestToken() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_PENDING_REQUEST_TOKEN)
            preferences.remove(KEY_PENDING_REQUEST_TOKEN_CREATED_AT)
        }
    }
}
