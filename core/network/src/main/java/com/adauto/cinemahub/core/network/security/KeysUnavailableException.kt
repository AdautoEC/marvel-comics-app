package com.adauto.cinemahub.core.network.security

import java.io.IOException

class KeysUnavailableException(
    message: String = "TMDB API credentials unavailable"
) : IOException(message)
