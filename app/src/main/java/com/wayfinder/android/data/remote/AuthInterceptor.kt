package com.wayfinder.android.data.remote

import com.wayfinder.android.data.local.TokenStorage
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Adds the Bearer access token to outgoing requests and performs a single
 * refresh-and-retry on HTTP 401.
 *
 * Behavior:
 *  - Requests to /api/auth/credentials and /api/auth/refresh pass through
 *    unchanged (no token attached; the body carries credentials/refresh token).
 *  - All other requests get `Authorization: Bearer <accessToken>` if a token
 *    is present.
 *  - On HTTP 401 the interceptor attempts a single-flight token refresh via
 *    [TokenRefresher]. If refresh succeeds the original request is retried
 *    once with the new token. If refresh fails, local tokens are cleared.
 *
 * NOTE: This interceptor only handles token mechanics. It never inspects or
 * computes strategy-related fields.
 */
class AuthInterceptor(
    private val tokenStorage: TokenStorage,
    private val tokenRefresher: TokenRefresher
) : Interceptor {

    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Auth endpoints manage credentials themselves; do not attach a Bearer token.
        val path = original.url.encodedPath
        if (path.endsWith("/api/auth/credentials") || path.endsWith("/api/auth/refresh")) {
            return chain.proceed(original)
        }

        val initialToken = tokenStorage.getAccessToken()
        val firstResponse = chain.proceed(original.withBearer(initialToken))

        if (firstResponse.code != 401) return firstResponse

        // 401 — close the body so the connection can be reused.
        firstResponse.close()

        // Single-flight refresh: avoid N parallel refresh storms when many
        // requests fail at once.
        val tokenAfterFailure = tokenStorage.getAccessToken()
        val alreadyRefreshedByAnotherThread =
            tokenAfterFailure != null && tokenAfterFailure != initialToken

        val refreshed: Boolean = if (alreadyRefreshedByAnotherThread) {
            true
        } else {
            synchronized(refreshLock) {
                // Re-check once more after acquiring the lock — another thread may
                // have refreshed while we were waiting.
                val t = tokenStorage.getAccessToken()
                if (t != null && t != initialToken) {
                    true
                } else {
                    tokenRefresher.refresh()
                }
            }
        }

        if (!refreshed) {
            // Refresh failed (e.g., AUTH_REFRESH_INVALID). Clear local state so the
            // user is routed back to login. Replay the original request without a
            // token so callers see the server's structured 401 error.
            tokenStorage.clear()
            return chain.proceed(original.withBearer(null))
        }

        val newToken = tokenStorage.getAccessToken()
            ?: return chain.proceed(original.withBearer(null))

        return chain.proceed(original.withBearer(newToken))
    }

    private fun Request.withBearer(token: String?): Request {
        if (token == null) return this
        return newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }
}

/**
 * Performs a synchronous token refresh. Implementations bridge to the
 * suspend refresh endpoint (e.g., via [kotlinx.coroutines.runBlocking]) since
 * OkHttp interceptors execute on background threads.
 */
fun interface TokenRefresher {
    /** @return true if a fresh access token was persisted. */
    fun refresh(): Boolean
}
