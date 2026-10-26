package com.adauto.cinemahub.core.data.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorTest {

    @Test
    fun requestPull_doesNotSuspendAndDropsOldestWhenBufferFull() = runTest {
        val coordinator = SyncCoordinator()

        // Rapid sequential triggers should not suspend or block
        coordinator.requestPull()
        coordinator.requestPull()
        coordinator.requestPull()

        var received = 0
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.pullRequests.collect { received++ }
        }

        coordinator.requestPull()
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(1, received)
    }
}
