package com.adauto.marvel.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeTest {

    @Test
    fun success_getOrNullReturnsValue() {
        val outcome: Outcome<String> = Outcome.Success("marvel")
        assertEquals("marvel", outcome.getOrNull())
    }

    @Test
    fun success_onSuccessExecutesAndOnFailureIgnored() {
        var successCalled = false
        var failureCalled = false

        val outcome: Outcome<Int> = Outcome.Success(42)
        outcome
            .onSuccess { value ->
                assertEquals(42, value)
                successCalled = true
            }
            .onFailure {
                failureCalled = true
            }

        assertTrue(successCalled)
        assertTrue(!failureCalled)
    }

    @Test
    fun failure_getOrNullReturnsNull() {
        val outcome: Outcome<String> = Outcome.Failure(DomainError.NoConnection)
        assertNull(outcome.getOrNull())
    }

    @Test
    fun failure_onFailureExecutesAndOnSuccessIgnored() {
        var successCalled = false
        var capturedError: DomainError? = null

        val outcome: Outcome<Int> = Outcome.Failure(DomainError.Server(500))
        outcome
            .onSuccess {
                successCalled = true
            }
            .onFailure { error ->
                capturedError = error
            }

        assertTrue(!successCalled)
        assertEquals(DomainError.Server(500), capturedError)
    }

    @Test
    fun domainError_allVariantsCanBeInstantiated() {
        val errors = listOf(
            DomainError.NoConnection,
            DomainError.Timeout,
            DomainError.NotFound,
            DomainError.RateLimited,
            DomainError.InvalidKey,
            DomainError.KeysUnavailable,
            DomainError.Server(503),
            DomainError.Unknown(IllegalStateException("test"))
        )
        assertEquals(8, errors.size)
    }
}
