package com.adauto.cinemahub.core.data.mapper

import com.adauto.cinemahub.core.model.DomainError
import com.adauto.cinemahub.core.network.security.KeysUnavailableException
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.statusCode
import java.io.IOException
import java.net.SocketTimeoutException

fun ApiResponse.Failure<*>.toDomainError(): DomainError = when (this) {
    is ApiResponse.Failure.Error -> when (val code = statusCode.code) {
        404 -> DomainError.NotFound
        401, 403 -> DomainError.InvalidKey
        429 -> DomainError.RateLimited
        in 500..599 -> DomainError.Server(code)
        else -> DomainError.Server(code)
    }
    is ApiResponse.Failure.Exception -> when (throwable) {
        is KeysUnavailableException -> DomainError.KeysUnavailable
        is SocketTimeoutException -> DomainError.Timeout
        is IOException -> DomainError.NoConnection
        else -> DomainError.Unknown(throwable)
    }
    else -> DomainError.Unknown()
}
