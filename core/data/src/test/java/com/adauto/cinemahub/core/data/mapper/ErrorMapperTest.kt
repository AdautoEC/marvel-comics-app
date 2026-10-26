package com.adauto.cinemahub.core.data.mapper

import com.adauto.cinemahub.core.model.DomainError
import com.adauto.cinemahub.core.network.security.KeysUnavailableException
import com.google.common.truth.Truth.assertThat
import com.skydoves.sandwich.ApiResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class ErrorMapperTest {

    private fun createHttpErrorResponse(code: Int): Response<Unit> {
        val body = "{}".toResponseBody("application/json".toMediaType())
        return Response.error(code, body)
    }

    @Test
    fun toDomainError_whenKeysUnavailableException_returnsKeysUnavailable() {
        val failure = ApiResponse.Failure.Exception(KeysUnavailableException("Keys unavailable"))

        val domainError = failure.toDomainError()

        assertThat(domainError).isEqualTo(DomainError.KeysUnavailable)
    }

    @Test
    fun toDomainError_whenSocketTimeout_returnsTimeout() {
        val failure = ApiResponse.Failure.Exception(SocketTimeoutException("Timeout"))

        val domainError = failure.toDomainError()

        assertThat(domainError).isEqualTo(DomainError.Timeout)
    }

    @Test
    fun toDomainError_whenIOException_returnsNoConnection() {
        val failure = ApiResponse.Failure.Exception(IOException("Network error"))

        val domainError = failure.toDomainError()

        assertThat(domainError).isEqualTo(DomainError.NoConnection)
    }

    @Test
    fun toDomainError_whenHttp401_returnsInvalidKey() {
        val failure = ApiResponse.Failure.Error(createHttpErrorResponse(401))

        val domainError = failure.toDomainError()

        assertThat(domainError).isEqualTo(DomainError.InvalidKey)
    }

    @Test
    fun toDomainError_whenHttp404_returnsNotFound() {
        val failure = ApiResponse.Failure.Error(createHttpErrorResponse(404))

        val domainError = failure.toDomainError()

        assertThat(domainError).isEqualTo(DomainError.NotFound)
    }

    @Test
    fun toDomainError_whenHttp500_returnsServer() {
        val failure = ApiResponse.Failure.Error(createHttpErrorResponse(500))

        val domainError = failure.toDomainError()

        assertThat(domainError).isEqualTo(DomainError.Server(500))
    }
}
