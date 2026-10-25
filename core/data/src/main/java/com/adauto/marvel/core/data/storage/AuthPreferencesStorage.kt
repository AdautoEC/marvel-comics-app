package com.adauto.marvel.core.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface AuthPreferencesStorage {
    suspend fun getLastAccountId(): String?
    suspend fun saveLastAccountId(accountId: String)
    suspend fun clearLastAccountId()
}

@Singleton
class DataStoreAuthPreferencesStorage @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : AuthPreferencesStorage {

    companion object {
        val KEY_LAST_ACCOUNT_ID = stringPreferencesKey("last_account_id")
    }

    override suspend fun getLastAccountId(): String? {
        return dataStore.data.map { preferences ->
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
}
