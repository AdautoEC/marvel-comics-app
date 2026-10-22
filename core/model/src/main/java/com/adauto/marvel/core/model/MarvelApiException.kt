package com.adauto.marvel.core.model

import java.io.IOException

/**
 * Exception representing an HTTP error returned by the Marvel API.
 * Extends [IOException] so it is compatible with Paging and Retrofit error propagation.
 *
 * @property code HTTP status code (e.g. 401, 429, 500)
 * @property apiMessage Description or status message from the API or HTTP layer
 */
class MarvelApiException(
    val code: Int,
    message: String = ""
) : IOException(
    if (message.isNotBlank()) "API error $code: $message" else "API error $code"
) {
    val apiMessage: String = message
}
