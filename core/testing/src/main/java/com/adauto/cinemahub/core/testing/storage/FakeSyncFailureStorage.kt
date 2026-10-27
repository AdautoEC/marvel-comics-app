package com.adauto.cinemahub.core.testing.storage

import com.adauto.cinemahub.core.data.storage.SyncFailureStorage
import com.adauto.cinemahub.core.data.sync.SyncFailureEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSyncFailureStorage : SyncFailureStorage {
    private val _unconsumedFailures = MutableStateFlow<List<SyncFailureEvent>>(emptyList())
    override val unconsumedFailures: Flow<List<SyncFailureEvent>> = _unconsumedFailures.asStateFlow()

    val recordedTimestamps = mutableListOf<Long>()

    override suspend fun recordFailure(event: SyncFailureEvent, timestamp: Long) {
        _unconsumedFailures.value = _unconsumedFailures.value + event
        recordedTimestamps.add(timestamp)
    }

    override suspend fun clearFailures() {
        _unconsumedFailures.value = emptyList()
    }
}
