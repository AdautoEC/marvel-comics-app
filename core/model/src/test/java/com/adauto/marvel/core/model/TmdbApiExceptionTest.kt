package com.adauto.marvel.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbApiExceptionTest {

    @Test
    fun exception_preservesStatusCodeAndMessage() {
        val exception = TmdbApiException(code = 429, message = "Too Many Requests")
        assertEquals(429, exception.code)
        assertEquals("Too Many Requests", exception.message)
    }

    @Test
    fun exception_providesDefaultMessageWhenNoneGiven() {
        val exception = TmdbApiException(code = 500)
        assertEquals(500, exception.code)
        assertEquals("TMDB API Error 500", exception.message)
    }
}
