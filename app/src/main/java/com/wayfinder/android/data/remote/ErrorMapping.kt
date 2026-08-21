package com.wayfinder.android.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.wayfinder.android.core.ErrorCode
import com.wayfinder.android.core.WayfinderError
import retrofit2.HttpException
import java.io.IOException

/**
 * Error mapping utilities.
 *
 * The server returns errors in the canonical contract:
 *   { error: { code, message, requestId } }
 *
 * This file converts OkHttp/Retrofit failures into [WayfinderError] instances
 * so feature code can branch on [ErrorCode] (e.g., navigate back to login on
 * AUTH_REFRESH_INVALID).
 */

private val errorMoshi: Moshi by lazy {
    Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
}

private val errorEnvelopeAdapter by lazy {
    errorMoshi.adapter(MobileApiErrorEnvelope::class.java)
}

/**
 * Converts a thrown exception into a [WayfinderError]. Decodes the server's
 * structured error envelope when present; falls back to a generic mapping
 * for network and unknown failures.
 */
fun Throwable.toWayfinderError(): WayfinderError = when (this) {
    is HttpException -> {
        val raw = runCatching { this.response()?.errorBody()?.string() }.getOrNull()
        val parsed = raw?.let { runCatching { errorEnvelopeAdapter.fromJson(it) }.getOrNull() }
        val code = parsed?.error?.code?.let(::mapErrorCode) ?: ErrorCode.UNKNOWN
        WayfinderError(
            code = code,
            message = parsed?.error?.message?.takeIf { it.isNotBlank() }
                ?: this.message()
                ?: "Request failed",
            requestId = parsed?.error?.requestId
        )
    }
    is IOException -> WayfinderError(
        code = ErrorCode.NETWORK_ERROR,
        message = "Network error. Check your connection.",
        requestId = null
    )
    else -> WayfinderError(
        code = ErrorCode.UNKNOWN,
        message = this.message?.takeIf { it.isNotBlank() } ?: "Unknown error",
        requestId = null
    )
}

/**
 * Maps the server's string error code to the client's [ErrorCode] enum.
 * Unknown codes resolve to [ErrorCode.UNKNOWN] so the client is forward-
 * compatible with new server codes.
 */
fun mapErrorCode(code: String): ErrorCode = when (code) {
    "AUTH_REQUIRED" -> ErrorCode.AUTH_REQUIRED
    "AUTH_EXPIRED" -> ErrorCode.AUTH_EXPIRED
    "AUTH_REFRESH_INVALID" -> ErrorCode.AUTH_REFRESH_INVALID
    "FORBIDDEN" -> ErrorCode.FORBIDDEN
    "NOT_FOUND" -> ErrorCode.NOT_FOUND
    "VALIDATION_ERROR" -> ErrorCode.VALIDATION_ERROR
    "CONFLICT" -> ErrorCode.CONFLICT
    "RATE_LIMITED" -> ErrorCode.RATE_LIMITED
    "SERVER_ERROR" -> ErrorCode.SERVER_ERROR
    "SERVICE_UNAVAILABLE" -> ErrorCode.SERVICE_UNAVAILABLE
    else -> ErrorCode.UNKNOWN
}
