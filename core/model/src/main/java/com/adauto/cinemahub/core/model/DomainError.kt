package com.adauto.cinemahub.core.model

sealed interface DomainError {
    data object NoConnection : DomainError
    data object Timeout : DomainError
    data object NotFound : DomainError
    data object RateLimited : DomainError
    data object InvalidKey : DomainError
    data object KeysUnavailable : DomainError
    data class Server(val code: Int) : DomainError
    data class Unknown(val cause: Throwable? = null) : DomainError
}
