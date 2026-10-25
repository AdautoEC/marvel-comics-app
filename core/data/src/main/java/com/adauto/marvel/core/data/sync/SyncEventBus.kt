package com.adauto.marvel.core.data.sync

import com.adauto.marvel.core.data.storage.SyncFailureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

data class SyncFailureEvent(
    val movieId: Int,
    val target: String,
    val error: String?
)

@Singleton
class SyncEventBus @Inject constructor(
    private val storage: SyncFailureStorage,
    private val clock: Clock = Clock.systemUTC()
) {
    private val _events = MutableSharedFlow<SyncFailureEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<SyncFailureEvent> = _events.asSharedFlow()

    val unconsumedFailures: Flow<List<SyncFailureEvent>>
        get() = storage.unconsumedFailures

    suspend fun emitFailure(event: SyncFailureEvent) {
        storage.recordFailure(event, clock.millis())
        _events.emit(event)
    }

    suspend fun consumeFailures() {
        storage.clearFailures()
    }
}
