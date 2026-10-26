package com.adauto.cinemahub.core.network.telemetry

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

private val SENSITIVE_QUERY_PARAMS = setOf("apikey", "hash", "ts", "api_key", "session_id")

/**
 * Strips sensitive authentication parameters (apikey, hash, ts, api_key, session_id) from the given URL
 * before logging or telemetry recording.
 */
fun sanitizeUrlForTelemetry(url: HttpUrl): String {
    val builder = url.newBuilder()
    for (name in url.queryParameterNames) {
        if (name in SENSITIVE_QUERY_PARAMS) {
            builder.setQueryParameter(name, "[REDACTED]")
        }
    }
    return builder.build().toString()
}

/**
 * An OkHttp interceptor that logs HTTP requests and responses while redacting
 * sensitive query parameters to prevent credential leakage in logs or telemetry.
 */
class RedactingHttpLoggingInterceptor(
    private val logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT
) : Interceptor {

    constructor(logFn: (String) -> Unit) : this(HttpLoggingInterceptor.Logger { logFn(it) })

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val sanitizedUrl = sanitizeUrlForTelemetry(request.url)
        logger.log("--> ${request.method} $sanitizedUrl")

        val startNs = System.nanoTime()
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            logger.log("<-- HTTP FAILED: $e")
            throw e
        }
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        logger.log("<-- ${response.code} ${response.message} $sanitizedUrl (${tookMs}ms)")
        return response
    }
}
