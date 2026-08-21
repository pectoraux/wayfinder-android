package com.wayfinder.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wayfinder.android.core.ErrorCode
import com.wayfinder.android.core.Result
import com.wayfinder.android.data.remote.MobileLoginResponse
import com.wayfinder.android.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the login screen.
 *
 * Demo credentials are pre-filled to make smoke testing trivial:
 *   email:    demo-user@wayfinder.app
 *   password: wayfinder
 */
data class LoginUiState(
    val email: String = DEFAULT_EMAIL,
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isLoading

    companion object {
        const val DEFAULT_EMAIL = "demo-user@wayfinder.app"
    }
}

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        if (auth.isAuthenticated()) {
            _state.value = _state.value.copy(loggedIn = true)
        }
    }

    fun onEmailChange(value: String) {
        _state.value = _state.value.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun login() {
        val current = _state.value
        if (!current.canSubmit) {
            _state.value = current.copy(error = "Enter your email and password.")
            return
        }
        _state.value = current.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val result = auth.login(current.email, current.password)) {
                is Result.Success<MobileLoginResponse> -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        loggedIn = true,
                        password = "" // never retain the password in memory
                    )
                }
                is Result.Error -> {
                    val message = if (result.error.code == ErrorCode.AUTH_REQUIRED ||
                        result.error.code == ErrorCode.AUTH_REFRESH_INVALID
                    ) {
                        "Invalid email or password."
                    } else {
                        result.error.code.toUserMessage()
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = message
                    )
                }
            }
        }
    }
}
