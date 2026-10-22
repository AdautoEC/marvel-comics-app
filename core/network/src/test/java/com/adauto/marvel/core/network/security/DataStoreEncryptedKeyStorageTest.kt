package com.adauto.marvel.core.network.security

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
class DataStoreEncryptedKeyStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var storage: EncryptedKeyStorage

    @Before
    fun setUp() {
        val testFile = File(tempFolder.root, "test_keys.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        storage = DataStoreEncryptedKeyStorage(dataStore)
    }

    @Test
    fun loadEncryptedBlob_returnsNullWhenEmpty() = runTest {
        val loaded = storage.loadEncryptedBlob()
        assertThat(loaded).isNull()
    }

    @Test
    fun saveEncryptedBlob_storesBytesAndCanBeLoaded() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 42, 99)
        storage.saveEncryptedBlob(bytes)

        val loaded = storage.loadEncryptedBlob()
        assertThat(loaded).isEqualTo(bytes)
    }

    @Test
    fun clear_removesStoredBlob() = runTest {
        val bytes = byteArrayOf(10, 20, 30)
        storage.saveEncryptedBlob(bytes)
        assertThat(storage.loadEncryptedBlob()).isEqualTo(bytes)

        storage.clear()
        assertThat(storage.loadEncryptedBlob()).isNull()
    }

    @Test
    fun saveEncryptedBlob_overwritesExistingBlob() = runTest {
        val initialBytes = byteArrayOf(1, 2, 3)
        val newBytes = byteArrayOf(4, 5, 6, 7)

        storage.saveEncryptedBlob(initialBytes)
        assertThat(storage.loadEncryptedBlob()).isEqualTo(initialBytes)

        storage.saveEncryptedBlob(newBytes)
        assertThat(storage.loadEncryptedBlob()).isEqualTo(newBytes)
    }

    @Test
    fun loadEncryptedBlob_returnsNullWhenCorruptedBase64() = runTest {
        dataStore.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[DataStoreEncryptedKeyStorage.KEY_BLOB] = "not_valid_base64!!!"
            }
        }
        assertThat(storage.loadEncryptedBlob()).isNull()
    }
}

