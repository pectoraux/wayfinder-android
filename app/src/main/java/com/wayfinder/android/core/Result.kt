package com.wayfinder.android.core

/**
 * Generic Result wrapper used across repositories.
 *
 * The Android client is a thin consumer of the Wayfinder server API and
 * MUST NOT compute any strategy-related intelligence. All decisions about
 * OutcomeType, EvaluationStatus, ConfidenceLevel, and predictions are
 * server-authoritative; this client only displays and forwards them.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val error: WayfinderError) : Result<Nothing>()
}

/**
 * A structured error mapped from the server's error contract:
 *   { error: { code, message, requestId } }
 *
 * The [code] field mirrors the server's stable error codes so callers can
 * branch on them (e.g., to navigate back to login on AUTH_REFRESH_INVALID).
 */
data class WayfinderError(
    val code: ErrorCode,
    val message: String,
    val requestId: String? = null
)

/**
 * Stable error codes recognized by the client. Mirrors the server contract.
 *
 * NETWORK_ERROR and UNKNOWN are client-only codes; the rest are 1:1 with
 * the server's `error.code` values.
 */
enum class ErrorCode {
    AUTH_REQUIRED,
    AUTH_EXPIRED,
    AUTH_REFRESH_INVALID,
    FORBIDDEN,
    NOT_FOUND,
    VALIDATION_ERROR,
    CONFLICT,
    RATE_LIMITED,
    SERVER_ERROR,
    SERVICE_UNAVAILABLE,
    NETWORK_ERROR,
    UNKNOWN;

    /**
     * Human-readable message shown to end users. Never exposes internal
     * details (tokens, request IDs, stack traces).
     */
    fun toUserMessage(): String = when (this) {
        AUTH_REQUIRED -> "Please sign in to continue."
        AUTH_EXPIRED -> "Your session expired. Please sign in again."
        AUTH_REFRESH_INVALID -> "Your session is no longer valid. Please sign in again."
        FORBIDDEN -> "You don't have permission to do that."
        NOT_FOUND -> "That resource was not found."
        VALIDATION_ERROR -> "Some details were invalid. Please check and try again."
        CONFLICT -> "There was a conflict with the current state."
        RATE_LIMITED -> "Too many requests. Please slow down."
        SERVER_ERROR -> "Something went wrong on our end. Please try again later."
        SERVICE_UNAVAILABLE -> "The service is temporarily unavailable."
        NETWORK_ERROR -> "Network error. Check your connection and try again."
        UNKNOWN -> "Something went wrong. Please try again."
    }
}
