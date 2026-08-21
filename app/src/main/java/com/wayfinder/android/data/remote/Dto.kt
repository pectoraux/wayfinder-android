package com.wayfinder.android.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Objects for the Wayfinder thin client.
 *
 * IMPORTANT: The Android app is a thin client. The server is authoritative
 * for all strategy intelligence: OutcomeType, EvaluationStatus,
 * ConfidenceLevel, predictions, etc. The client merely renders server-provided
 * values as opaque strings and forwards user input.
 *
 * DTO field names mirror the server's JSON contract. Optional fields are
 * nullable so the client is resilient to additive server changes.
 */

// region Auth

@JsonClass(generateAdapter = true)
data class MobileLoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class MobileLoginResponse(
    @Json(name = "accessToken") val accessToken: String,
    @Json(name = "refreshToken") val refreshToken: String,
    @Json(name = "user") val user: MobileUser? = null
)

@JsonClass(generateAdapter = true)
data class MobileRefreshRequest(
    @Json(name = "refreshToken") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class MobileRefreshResponse(
    @Json(name = "accessToken") val accessToken: String,
    @Json(name = "refreshToken") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class MobileLogoutRequest(
    @Json(name = "refreshToken") val refreshToken: String? = null
)

@JsonClass(generateAdapter = true)
data class MobileUser(
    val id: String,
    val email: String? = null,
    @Json(name = "displayName") val displayName: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ProfileDTO(
    val user: MobileUser
)

/**
 * Body for POST /api/profile.
 *
 * All fields are optional — the client only sends the fields the user edited.
 * The client performs NO validation of these values beyond non-blank checks;
 * the server is authoritative for any normalization or constraints.
 */
@JsonClass(generateAdapter = true)
data class ProfileUpdateRequest(
    @Json(name = "displayName") val displayName: String? = null,
    @Json(name = "email") val email: String? = null
)

// endregion

// region Errors

@JsonClass(generateAdapter = true)
data class MobileApiErrorEnvelope(
    @Json(name = "error") val error: MobileApiError
)

@JsonClass(generateAdapter = true)
data class MobileApiError(
    val code: String,
    val message: String,
    @Json(name = "requestId") val requestId: String? = null
)

// endregion

// region Strategy

@JsonClass(generateAdapter = true)
data class StrategyDTO(
    val id: String,
    val title: String? = null,
    val summary: String? = null,
    @Json(name = "bestTrajectory") val bestTrajectory: TrajectoryDTO? = null,
    @Json(name = "blockers") val blockers: List<BlockerDTO> = emptyList(),
    @Json(name = "actions") val actions: List<ActionDTO> = emptyList(),
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class TrajectoryDTO(
    val id: String? = null,
    val label: String? = null,
    val description: String? = null,
    /**
     * Server-authoritative confidence label (e.g., "high", "medium", "low").
     * The client treats this as an opaque string — never parsed locally.
     */
    @Json(name = "confidenceLabel") val confidenceLabel: String? = null
)

@JsonClass(generateAdapter = true)
data class BlockerDTO(
    val id: String? = null,
    val label: String? = null,
    val description: String? = null,
    val severity: String? = null
)

@JsonClass(generateAdapter = true)
data class ActionDTO(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val status: String? = null
)

@JsonClass(generateAdapter = true)
data class ActionsDTO(
    val items: List<ActionDTO> = emptyList()
)

@JsonClass(generateAdapter = true)
data class StrategyHistoryDTO(
    val items: List<StrategyDTO> = emptyList(),
    @Json(name = "nextCursor") val nextCursor: String? = null
)

// endregion

// region Explanation + Outcomes

@JsonClass(generateAdapter = true)
data class ExplanationDTO(
    @Json(name = "strategyId") val strategyId: String,
    val summary: String? = null,
    @Json(name = "keyFactors") val keyFactors: List<String> = emptyList(),
    @Json(name = "assumptions") val assumptions: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OutcomesDTO(
    @Json(name = "strategyId") val strategyId: String,
    @Json(name = "expected") val expected: List<OutcomeDTO> = emptyList(),
    @Json(name = "observed") val observed: List<OutcomeDTO> = emptyList(),
    /**
     * Server-authoritative evaluation. The client renders [EvaluationDTO.status]
     * as an opaque string — never derives it.
     */
    val evaluation: EvaluationDTO? = null,
    /**
     * Optional server-authoritative tally of outcome categories
     * (achieved / partial / missed / pending). Every value is computed by the
     * server; the client only renders them. Fields are nullable so the client
     * degrades gracefully if the server omits any.
     */
    val summary: OutcomeSummaryDTO? = null
)

/**
 * Server-provided summary counts for an outcome set.
 *
 * IMPORTANT: The client MUST NOT compute these counts. They reflect the
 * server's authoritative evaluation of each outcome (achieved / partial /
 * missed / pending). The client renders them verbatim; if a field is null it
 * is simply omitted from the UI.
 */
@JsonClass(generateAdapter = true)
data class OutcomeSummaryDTO(
    @Json(name = "achieved") val achieved: Int? = null,
    @Json(name = "partial") val partial: Int? = null,
    @Json(name = "missed") val missed: Int? = null,
    @Json(name = "pending") val pending: Int? = null
)

@JsonClass(generateAdapter = true)
data class OutcomeDTO(
    val id: String? = null,
    /**
     * Server-authoritative OutcomeType. The client treats this as opaque and
     * only forwards it back when submitting a corresponding observation.
     */
    val type: String? = null,
    val label: String? = null,
    val value: String? = null,
    @Json(name = "observedAt") val observedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class EvaluationDTO(
    /**
     * Server-authoritative EvaluationStatus. Rendered as an opaque string.
     */
    val status: String? = null,
    val notes: String? = null
)

/**
 * Body for POST /api/strategy/{id}/outcome and POST /api/actions/{id}/outcome.
 *
 * The [type] field is the server-authoritative OutcomeType — the client only
 * submits values that originated from the server (e.g., the type of an
 * expected outcome). The client NEVER invents an OutcomeType.
 */
@JsonClass(generateAdapter = true)
data class OutcomeSubmissionRequest(
    @Json(name = "type") val type: String,
    @Json(name = "label") val label: String? = null,
    @Json(name = "value") val value: String? = null
)

@JsonClass(generateAdapter = true)
data class OutcomeSubmissionResponse(
    val id: String? = null,
    @Json(name = "strategyId") val strategyId: String? = null,
    val status: String? = null
)

// endregion
