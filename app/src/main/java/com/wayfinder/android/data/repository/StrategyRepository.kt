package com.wayfinder.android.data.repository

import com.wayfinder.android.core.Result
import com.wayfinder.android.data.remote.ExplanationDTO
import com.wayfinder.android.data.remote.OutcomeSubmissionRequest
import com.wayfinder.android.data.remote.OutcomeSubmissionResponse
import com.wayfinder.android.data.remote.OutcomesDTO
import com.wayfinder.android.data.remote.StrategyDTO
import com.wayfinder.android.data.remote.StrategyHistoryDTO
import com.wayfinder.android.data.remote.WayfinderApi
import com.wayfinder.android.data.remote.toWayfinderError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around the strategy endpoints.
 *
 * Endpoint mapping:
 *  - getActiveStrategy(id) → GET /api/strategy/adopt
 *  - getExplanation(id)     → GET /api/strategy/{id}/explanation
 *  - getOutcomes(id)        → GET /api/strategy/{id}/outcomes
 *  - getHistory(cursor?)    → GET /api/strategy/history
 *
 * This repository performs NO local intelligence. OutcomeType, evaluation
 * status, confidence labels, and predictions are all server-authoritative;
 * the client only fetches, displays, and forwards user input.
 */
class StrategyRepository(private val api: WayfinderApi) {

    /** GET /api/strategy/adopt — fetches the currently active strategy. */
    suspend fun getActiveStrategy(): Result<StrategyDTO> = withContext(Dispatchers.IO) {
        try {
            Result.Success(api.adoptStrategy())
        } catch (e: Exception) {
            Result.Error(e.toWayfinderError())
        }
    }

    /** Legacy alias for [getActiveStrategy]; retained for callers using the old name. */
    suspend fun adopt(): Result<StrategyDTO> = getActiveStrategy()

    suspend fun getExplanation(strategyId: String): Result<ExplanationDTO> =
        withContext(Dispatchers.IO) {
            try {
                Result.Success(api.getExplanation(strategyId))
            } catch (e: Exception) {
                Result.Error(e.toWayfinderError())
            }
        }

    suspend fun getOutcomes(strategyId: String): Result<OutcomesDTO> =
        withContext(Dispatchers.IO) {
            try {
                Result.Success(api.getOutcomes(strategyId))
            } catch (e: Exception) {
                Result.Error(e.toWayfinderError())
            }
        }

    /**
     * Submits an observed outcome. The [type] field MUST originate from a
     * server-provided expected outcome — the client never invents types.
     */
    suspend fun submitOutcome(
        strategyId: String,
        type: String,
        label: String?,
        value: String?
    ): Result<OutcomeSubmissionResponse> = withContext(Dispatchers.IO) {
        try {
            val request = OutcomeSubmissionRequest(
                type = type,
                label = label,
                value = value
            )
            Result.Success(api.submitStrategyOutcome(strategyId, request))
        } catch (e: Exception) {
            Result.Error(e.toWayfinderError())
        }
    }

    suspend fun getHistory(cursor: String? = null): Result<StrategyHistoryDTO> =
        withContext(Dispatchers.IO) {
            try {
                Result.Success(api.getStrategyHistory(cursor))
            } catch (e: Exception) {
                Result.Error(e.toWayfinderError())
            }
        }
}
