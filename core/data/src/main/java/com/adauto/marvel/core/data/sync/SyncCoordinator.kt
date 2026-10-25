package com.adauto.marvel.core.data.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncCoordinator @Inject constructor() {
    val mutex = Mutex()
    private val _pullRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pullRequests: SharedFlow<Unit> = _pullRequests.asSharedFlow()

    suspend fun requestPull() {
        _pullRequests.emit(Unit)
    }
}
