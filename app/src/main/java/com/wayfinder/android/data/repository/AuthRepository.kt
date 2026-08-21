package com.wayfinder.android.data.repository

import com.wayfinder.android.core.Result
import com.wayfinder.android.data.local.TokenStorage
import com.wayfinder.android.data.remote.MobileLoginRequest
import com.wayfinder.android.data.remote.MobileLoginResponse
import com.wayfinder.android.data.remote.MobileLogoutRequest
import com.wayfinder.android.data.remote.MobileRefreshRequest
import com.wayfinder.android.data.remote.MobileRefreshResponse
import com.wayfinder.android.data.remote.WayfinderApi
import com.wayfinder.android.data.remote.toWayfinderError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Auth responsibilities for the thin client.
 *
 *  - login(email, password)   → POST /api/auth/credentials, persists tokens
 *  - refresh()                 → POST /api/auth/refresh, persists the new tokens
 *  - logout()                  → POST /api/auth/logout (best-effort), clears tokens
 *  - isAuthenticated()         → true if an access token is present locally
 *
 * NOTE: In normal operation token refresh is handled transparently by the
 * [com.wayfinder.android.data.remote.AuthInterceptor] on a 401. The explicit
 * [refresh] method is exposed for callers that want to proactively rotate the
 * access token (e.g., on app foreground) without waiting for a 401.
 */
class AuthRepository(
    private val api: WayfinderApi,
    private val tokenStorage: TokenStorage
) {

    suspend fun login(email: String, password: String): Result<MobileLoginResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.login(MobileLoginRequest(email.trim(), password))
                tokenStorage.saveTokens(response.accessToken, response.refreshToken)
                Result.Success(response)
            } catch (e: Exception) {
                Result.Error(e.toWayfinderError())
            }
        }

    suspend fun refresh(): Result<MobileRefreshResponse> = withContext(Dispatchers.IO) {
        val refreshToken = tokenStorage.getRefreshToken()
            ?: return@withContext Result.Error(
                com.wayfinder.android.core.WayfinderError(
                    code = com.wayfinder.android.core.ErrorCode.AUTH_REQUIRED,
                    message = "No refresh token available.",
                    requestId = null
                )
            )
        try {
            val response = api.refresh(MobileRefreshRequest(refreshToken))
            tokenStorage.saveTokens(response.accessToken, response.refreshToken)
            Result.Success(response)
        } catch (e: Exception) {
            // Refresh failed (e.g., AUTH_REFRESH_INVALID). Clear local state so the
            // user is routed back to login on the next authenticated call.
            tokenStorage.clear()
            Result.Error(e.toWayfinderError())
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        val refreshToken = tokenStorage.getRefreshToken()
        try {
            api.logout(MobileLogoutRequest(refreshToken))
        } catch (_: Exception) {
            // Best-effort: even if the server call fails we drop local state so
            // the user is returned to the login screen.
        } finally {
            tokenStorage.clear()
        }
        Result.Success(Unit)
    }

    fun isAuthenticated(): Boolean =
        !tokenStorage.getAccessToken().isNullOrBlank()
}
