package com.adauto.cinemahub.core.network.security.session

import androidx.datastore.core.DataStore
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSessionStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var storage: SessionStorage

    @Before
    fun setUp() {
        val testFile = File(tempFolder.root, "test_session.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        storage = DataStoreSessionStorage(dataStore)
    }

    @Test
    fun loadEncryptedSession_returnsNullWhenEmpty() = runTest {
        val loaded = storage.loadEncryptedSession()
        assertThat(loaded).isNull()
    }

    @Test
    fun saveEncryptedSession_storesBytesAndCanBeLoaded() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 42, 99)
        storage.saveEncryptedSession(bytes)

        val loaded = storage.loadEncryptedSession()
        assertThat(loaded).isEqualTo(bytes)
    }

    @Test
    fun clear_removesStoredSession() = runTest {
        val bytes = byteArrayOf(10, 20, 30)
        storage.saveEncryptedSession(bytes)
        assertThat(storage.loadEncryptedSession()).isEqualTo(bytes)

        storage.clear()
        assertThat(storage.loadEncryptedSession()).isNull()
    }

    @Test
    fun saveEncryptedSession_overwritesExistingSession() = runTest {
        val initialBytes = byteArrayOf(1, 2, 3)
        val newBytes = byteArrayOf(4, 5, 6, 7)

        storage.saveEncryptedSession(initialBytes)
        assertThat(storage.loadEncryptedSession()).isEqualTo(initialBytes)

        storage.saveEncryptedSession(newBytes)
        assertThat(storage.loadEncryptedSession()).isEqualTo(newBytes)
    }

    @Test
    fun loadEncryptedSession_returnsNullWhenCorruptedBase64() = runTest {
        dataStore.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[DataStoreSessionStorage.KEY_SESSION_BLOB] = "not_valid_base64!!!"
            }
        }
        assertThat(storage.loadEncryptedSession()).isNull()
    }
}
