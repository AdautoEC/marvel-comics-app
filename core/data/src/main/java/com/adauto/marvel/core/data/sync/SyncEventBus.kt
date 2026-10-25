package com.adauto.marvel.core.data.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SyncFailureEvent(
    val movieId: Int,
    val target: String,
    val error: String?
)

@Singleton
class SyncEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<SyncFailureEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<SyncFailureEvent> = _events.asSharedFlow()

    suspend fun emitFailure(event: SyncFailureEvent) {
        _events.emit(event)
    }
}
