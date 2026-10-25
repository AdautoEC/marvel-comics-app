package com.adauto.marvel.core.data.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.adauto.marvel.core.data.sync.SyncFailureEvent
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
class SyncFailureStorageTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var storage: SyncFailureStorage

    @Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("sync_failures_test.preferences_pb") }
        )
        storage = DataStoreSyncFailureStorage(dataStore)
    }

    @Test
    fun unconsumedFailures_initiallyEmpty() = runTest(testDispatcher) {
        storage.unconsumedFailures.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun recordFailure_persistsAndAppendsFailures() = runTest(testDispatcher) {
        val failure1 = SyncFailureEvent(movieId = 101, target = "FAVORITE", error = "404 Not Found")
        val failure2 = SyncFailureEvent(movieId = 202, target = "WATCHED", error = "500 Server Error")

        storage.recordFailure(failure1, timestamp = 1_000L)
        storage.recordFailure(failure2, timestamp = 2_000L)

        storage.unconsumedFailures.test {
            val items = awaitItem()
            assertThat(items).hasSize(2)
            assertThat(items[0]).isEqualTo(failure1)
            assertThat(items[1]).isEqualTo(failure2)
        }
    }

    @Test
    fun clearFailures_removesAllPersistedFailures() = runTest(testDispatcher) {
        val failure = SyncFailureEvent(movieId = 101, target = "FAVORITE", error = "404 Not Found")
        storage.recordFailure(failure, timestamp = 1_000L)

        storage.clearFailures()

        storage.unconsumedFailures.test {
            assertThat(awaitItem()).isEmpty()
        }
    }
}
