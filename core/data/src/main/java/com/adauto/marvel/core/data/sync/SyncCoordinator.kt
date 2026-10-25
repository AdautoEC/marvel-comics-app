package com.adauto.marvel.core.data.sync

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncCoordinator @Inject constructor() {
    val mutex = Mutex()
    private val _pullRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val pullRequests: SharedFlow<Unit> = _pullRequests.asSharedFlow()

    fun requestPull() {
        _pullRequests.tryEmit(Unit)
    }
}
