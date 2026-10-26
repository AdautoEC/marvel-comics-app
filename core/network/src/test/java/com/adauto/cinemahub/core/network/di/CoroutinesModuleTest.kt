package com.adauto.cinemahub.core.network.di

import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoroutinesModuleTest {

    @Test
    fun provideApplicationScope_returnsActiveScopeWithJob() {
        val scope = CoroutinesModule.provideApplicationScope()

        assertTrue(scope.isActive)
        val job = scope.coroutineContext[Job]
        assertNotNull(job)
        assertTrue(job?.isActive == true)
    }
}
