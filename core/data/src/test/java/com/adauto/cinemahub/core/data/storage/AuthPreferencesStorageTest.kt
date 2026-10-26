package com.adauto.cinemahub.core.data.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AuthPreferencesStorageTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var storage: AuthPreferencesStorage

    @Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("auth_preferences_test.preferences_pb") }
        )
        storage = DataStoreAuthPreferencesStorage(dataStore)
    }

    @Test
    fun getLastAccountId_whenEmpty_returnsNull() = runTest(testDispatcher) {
        val accountId = storage.getLastAccountId()
        assertThat(accountId).isNull()
    }

    @Test
    fun saveLastAccountId_persistsAndRetrievesAccountId() = runTest(testDispatcher) {
        storage.saveLastAccountId("42")
        val accountId = storage.getLastAccountId()
        assertThat(accountId).isEqualTo("42")
    }

    @Test
    fun clearLastAccountId_removesAccountId() = runTest(testDispatcher) {
        storage.saveLastAccountId("42")
        assertThat(storage.getLastAccountId()).isEqualTo("42")

        storage.clearLastAccountId()
        assertThat(storage.getLastAccountId()).isNull()
    }

    @Test
    fun getPendingRequestToken_whenEmpty_returnsNull() = runTest(testDispatcher) {
        val tokenPair = storage.getPendingRequestToken()
        assertThat(tokenPair).isNull()
    }

    @Test
    fun savePendingRequestToken_persistsAndRetrievesTokenAndTimestamp() = runTest(testDispatcher) {
        storage.savePendingRequestToken("req_token_123", 1000L)
        val tokenPair = storage.getPendingRequestToken()
        assertThat(tokenPair).isEqualTo("req_token_123" to 1000L)
    }

    @Test
    fun clearPendingRequestToken_removesToken() = runTest(testDispatcher) {
        storage.savePendingRequestToken("req_token_123", 1000L)
        assertThat(storage.getPendingRequestToken()).isNotNull()

        storage.clearPendingRequestToken()
        assertThat(storage.getPendingRequestToken()).isNull()
    }
}
