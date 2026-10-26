package com.adauto.cinemahub.core.model

import java.io.IOException

class TmdbApiException(
    val code: Int,
    message: String? = "TMDB API Error $code"
) : IOException(message)
