package com.wayfinder.android.feature.outcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wayfinder.android.core.ErrorCode
import com.wayfinder.android.core.Result
import com.wayfinder.android.data.remote.OutcomeDTO
import com.wayfinder.android.data.remote.OutcomesDTO
import com.wayfinder.android.data.repository.StrategyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the outcome screen.
 *
 * Renders server-authoritative expected/observed/evaluation verbatim. The
 * client only forwards user-entered observations and re-fetches the
 * authoritative state afterwards.
 */
data class OutcomeUiState(
    val isLoading: Boolean = false,
    val outcomes: OutcomesDTO? = null,
    val error: String? = null,
    val submittingForType: String? = null,
    val lastSubmittedType: String? = null
)

class OutcomeViewModel(
    private val repo: StrategyRepository,
    private val strategyId: String
) : ViewModel() {

    private val _state = MutableStateFlow(OutcomeUiState(isLoading = true))
    val state: StateFlow<OutcomeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val r = repo.getOutcomes(strategyId)) {
                is Result.Success -> _state.value = OutcomeUiState(
                    isLoading = false,
                    outcomes = r.data
                )
                is Result.Error -> _state.value = OutcomeUiState(
                    isLoading = false,
                    error = r.error.code.toUserMessage()
                )
            }
        }
    }

    /**
     * Submits an observation for an [expected] outcome. The outcome [type]
     * is server-authoritative — it always comes from the matching expected
     * outcome, never invented by the client.
     */
    fun submitObservation(expected: OutcomeDTO, observedValue: String) {
        val type = expected.type ?: return
        if (observedValue.isBlank()) return
        _state.value = _state.value.copy(submittingForType = type, error = null)
        viewModelScope.launch {
            when (val r = repo.submitOutcome(
                strategyId = strategyId,
                type = type,
                label = expected.label,
                value = observedValue.trim()
            )) {
                is Result.Success -> {
                    _state.value = _state.value.copy(
                        submittingForType = null,
                        lastSubmittedType = type
                    )
                    load() // re-fetch authoritative observed + evaluation
                }
                is Result.Error -> {
                    _state.value = _state.value.copy(
                        submittingForType = null,
                        error = r.error.code.toUserMessage()
                    )
                }
            }
        }
    }

    fun consumeLastSubmitted() {
        _state.value = _state.value.copy(lastSubmittedType = null)
    }
}
