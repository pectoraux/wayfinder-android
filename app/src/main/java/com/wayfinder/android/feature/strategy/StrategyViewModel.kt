package com.wayfinder.android.feature.strategy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wayfinder.android.core.ErrorCode
import com.wayfinder.android.core.Result
import com.wayfinder.android.data.remote.ExplanationDTO
import com.wayfinder.android.data.remote.StrategyDTO
import com.wayfinder.android.data.repository.StrategyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the strategy screen.
 *
 * [StrategyUiState.strategy] and [explanation] are rendered verbatim from the
 * server; the client does not transform, prioritize, or evaluate any of their
 * fields. The explanation is loaded opportunistically after the strategy is
 * adopted — if it fails the strategy is still shown.
 */
data class StrategyUiState(
    val isLoading: Boolean = false,
    val strategy: StrategyDTO? = null,
    val explanation: ExplanationDTO? = null,
    val error: String? = null,
    val loggedOut: Boolean = false
)

class StrategyViewModel(
    private val repo: StrategyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StrategyUiState(isLoading = true))
    val state: StateFlow<StrategyUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val r = repo.getActiveStrategy()) {
                is Result.Success -> {
                    _state.value = StrategyUiState(
                        isLoading = false,
                        strategy = r.data
                    )
                    // Best-effort explanation load — does not block strategy display.
                    loadExplanation(r.data.id)
                }
                is Result.Error -> {
                    val error = r.error
                    if (error.code == ErrorCode.AUTH_REFRESH_INVALID ||
                        error.code == ErrorCode.AUTH_REQUIRED
                    ) {
                        _state.value = StrategyUiState(
                            isLoading = false,
                            error = error.code.toUserMessage(),
                            loggedOut = true
                        )
                    } else {
                        _state.value = StrategyUiState(
                            isLoading = false,
                            error = error.code.toUserMessage()
                        )
                    }
                }
            }
        }
    }

    /**
     * Loads the explanation for [strategyId] and merges it into the current
     * state. Failures are swallowed so the strategy remains visible; the
     * explanation section is simply omitted from the UI.
     */
    private fun loadExplanation(strategyId: String) {
        viewModelScope.launch {
            when (val r = repo.getExplanation(strategyId)) {
                is Result.Success -> {
                    _state.value = _state.value.copy(explanation = r.data)
                }
                is Result.Error -> {
                    // Non-fatal: explanation is supplementary. Leave it null.
                }
            }
        }
    }

    fun consumeLoggedOut() {
        _state.value = _state.value.copy(loggedOut = false)
    }
}
