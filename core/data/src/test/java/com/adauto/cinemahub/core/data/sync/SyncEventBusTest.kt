package com.adauto.cinemahub.core.data.sync

import app.cash.turbine.test
import com.adauto.cinemahub.core.testing.storage.FakeSyncFailureStorage
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SyncEventBusTest {

    private val storage = FakeSyncFailureStorage()
    private val fixedClock = Clock.fixed(Instant.ofEpochMilli(123456789L), ZoneOffset.UTC)
    private val bus = SyncEventBus(storage, fixedClock)

    @Test
    fun emitFailure_recordsInStorageAndEmitsToSharedFlow() = runTest {
        val event = SyncFailureEvent(movieId = 10, target = "FAVORITE", error = "404")

        bus.events.test {
            bus.emitFailure(event)
            assertThat(awaitItem()).isEqualTo(event)
        }

        assertThat(storage.recordedTimestamps).containsExactly(123456789L)
        bus.unconsumedFailures.test {
            assertThat(awaitItem()).containsExactly(event)
        }
    }

    @Test
    fun consumeFailures_clearsFailuresInStorage() = runTest {
        val event = SyncFailureEvent(movieId = 10, target = "FAVORITE", error = "404")
        bus.emitFailure(event)

        bus.consumeFailures()

        bus.unconsumedFailures.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun unconsumedFailures_delegatesToStorage() = runTest {
        val event = SyncFailureEvent(movieId = 10, target = "FAVORITE", error = "404")
        storage.recordFailure(event, 1000L)

        bus.unconsumedFailures.test {
            assertThat(awaitItem()).containsExactly(event)
        }
    }
}
