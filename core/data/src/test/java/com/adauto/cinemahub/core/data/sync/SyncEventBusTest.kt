package com.adauto.cinemahub.core.data.sync

import app.cash.turbine.test
import com.adauto.cinemahub.core.data.storage.SyncFailureStorage
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SyncEventBusTest {

    private val storage: SyncFailureStorage = mockk(relaxed = true)
    private val fixedClock = Clock.fixed(Instant.ofEpochMilli(123456789L), ZoneOffset.UTC)
    private val bus = SyncEventBus(storage, fixedClock)

    @Test
    fun emitFailure_recordsInStorageAndEmitsToSharedFlow() = runTest {
        val event = SyncFailureEvent(movieId = 10, target = "FAVORITE", error = "404")

        bus.events.test {
            bus.emitFailure(event)
            assertThat(awaitItem()).isEqualTo(event)
        }

        coVerify(exactly = 1) { storage.recordFailure(event, 123456789L) }
    }

    @Test
    fun consumeFailures_callsStorageClearFailures() = runTest {
        bus.consumeFailures()

        coVerify(exactly = 1) { storage.clearFailures() }
    }

    @Test
    fun unconsumedFailures_delegatesToStorage() = runTest {
        val events = listOf(SyncFailureEvent(movieId = 10, target = "FAVORITE", error = "404"))
        every { storage.unconsumedFailures } returns flowOf(events)

        bus.unconsumedFailures.test {
            assertThat(awaitItem()).isEqualTo(events)
            awaitComplete()
        }
    }
}
