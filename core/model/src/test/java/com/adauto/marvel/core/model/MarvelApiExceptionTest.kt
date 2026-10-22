package com.adauto.marvel.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException

class MarvelApiExceptionTest {

    @Test
    fun constructor_withCodeAndMessage_createsInformativeMessage() {
        val exception = MarvelApiException(code = 429, message = "Too Many Requests")

        assertThat(exception.code).isEqualTo(429)
        assertThat(exception.apiMessage).isEqualTo("Too Many Requests")
        assertThat(exception.message).isEqualTo("API error 429: Too Many Requests")
        assertThat(exception).isInstanceOf(IOException::class.java)
    }

    @Test
    fun constructor_withCodeOnly_createsCodeOnlyMessage() {
        val exception = MarvelApiException(code = 500)

        assertThat(exception.code).isEqualTo(500)
        assertThat(exception.apiMessage).isEmpty()
        assertThat(exception.message).isEqualTo("API error 500")
        assertThat(exception).isInstanceOf(IOException::class.java)
    }
}
