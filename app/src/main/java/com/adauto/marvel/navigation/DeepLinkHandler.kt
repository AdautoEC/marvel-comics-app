package com.adauto.marvel.navigation

import android.content.Intent
import android.net.Uri

object DeepLinkHandler {

    const val SCHEME = "marvelapp"
    const val HOST_TMDB_CALLBACK = "tmdb-callback"
    const val PARAM_REQUEST_TOKEN = "request_token"
    const val PARAM_APPROVED = "approved"

    fun extractApprovedRequestToken(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        return extractApprovedRequestToken(uri)
    }

    fun extractApprovedRequestToken(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme != SCHEME || uri.host != HOST_TMDB_CALLBACK) return null
        val approved = uri.getQueryParameter(PARAM_APPROVED)
        if (approved != "true") return null
        return uri.getQueryParameter(PARAM_REQUEST_TOKEN)?.takeIf { it.isNotBlank() }
    }
}
