package com.wayfinder.android

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.wayfinder.android.data.local.InMemoryTokenStorage
import com.wayfinder.android.data.remote.ApiModule
import com.wayfinder.android.data.remote.AuthInterceptor
import com.wayfinder.android.data.remote.MobileApiErrorEnvelope
import com.wayfinder.android.data.remote.MobileRefreshResponse
import com.wayfinder.android.data.remote.TokenRefresher
import com.wayfinder.android.data.remote.WayfinderApi
import kotlinx.coroutines.runBlocking
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the storage/retrieval lifecycle of tokens against the
 * [InMemoryTokenStorage] implementation, and that the production HTTP
 * logging configuration never leaks token values into logs.
 *
 * The production [com.wayfinder.android.data.local.EncryptedTokenStorage]
 * delegates to EncryptedSharedPreferences (Android Keystore) and is exercised
 * by instrumentation tests; here we validate the contract that the auth
 * interceptor and repositories depend on.
 */
class TokenLifecycleTest {

    // region save / retrieve / clear

    @Test
    fun `fresh storage has no tokens`() {
        val storage = InMemoryTokenStorage()
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
    }

    @Test
    fun `saveTokens persists both tokens and they are retrievable`() {
        val storage = InMemoryTokenStorage()
        storage.saveTokens("access-1", "refresh-1")

        assertEquals("access-1", storage.getAccessToken())
        assertEquals("refresh-1", storage.getRefreshToken())
    }

    @Test
    fun `saveTokens overwrites previous tokens atomically`() {
        val storage = InMemoryTokenStorage()
        storage.saveTokens("access-1", "refresh-1")
        storage.saveTokens("access-2", "refresh-2")

        assertEquals("access-2", storage.getAccessToken())
        assertEquals("refresh-2", storage.getRefreshToken())
        // The old tokens must not linger — both replaced together.
        assertNotEquals("access-1", storage.getAccessToken())
        assertNotEquals("refresh-1", storage.getRefreshToken())
    }

    @Test
    fun `clear removes both tokens`() {
        val storage = InMemoryTokenStorage()
        storage.saveTokens("access-1", "refresh-1")
        storage.clear()

        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
    }

    @Test
    fun `clear on empty storage is a no-op and idempotent`() {
        val storage = InMemoryTokenStorage()
        storage.clear()
        assertNull(storage.getAccessToken())
        storage.clear()
        assertNull(storage.getAccessToken())
    }

    @Test
    fun `refresh replaces only what the server returned`() {
        val storage = InMemoryTokenStorage()
        storage.saveTokens("access-1", "refresh-1")

        // Simulate a server refresh — both tokens rotate.
        storage.saveTokens("access-2", "refresh-2")

        assertEquals("access-2", storage.getAccessToken())
        assertEquals("refresh-2", storage.getRefreshToken())
        assertNotNull(storage.getRefreshToken())
    }

    @Test
    fun `empty string tokens are treated as present by storage`() {
        // The auth repository treats blank access tokens as unauthenticated,
        // but storage itself does not interpret content — it stores what it's given.
        val storage = InMemoryTokenStorage()
        storage.saveTokens("", "")
        assertEquals("", storage.getAccessToken())
        assertEquals("", storage.getRefreshToken())
        assertNotNull(storage.getAccessToken())
    }

    // endregion

    // region Tokens are never logged

    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    /**
     * The production [ApiModule] builds its OkHttp logging interceptor at
     * [HttpLoggingInterceptor.Level.BASIC], which logs only the request method,
     * URL, and response status line — never headers (where the Bearer token
     * lives) and never the body.
     *
     * This test reconstructs that exact logging configuration, makes a real
     * HTTP call whose request carries a Bearer access token, captures every
     * line the logger writes, and asserts that the token value never appears.
     */
    @Test
    fun `http logging at BASIC level never emits the access token`() = runBlocking {
        val accessToken = "SECRET-jwt-access-DO-NOT-LEAK-1234567890"
        val refreshToken = "SECRET-opaque-refresh-DO-NOT-LEAK-0987654321"

        val storage = InMemoryTokenStorage().apply {
            saveTokens(accessToken, refreshToken)
        }

        val captured = mutableListOf<String>()
        val capturingLogger = HttpLoggingInterceptor.Logger { msg ->
            captured.add(msg)
        }
        val loggingInterceptor = HttpLoggingInterceptor(capturingLogger).apply {
            // Mirrors ApiModule's production setting.
            level = HttpLoggingInterceptor.Level.BASIC
        }

        // A trivially-failing refresh implementation so the AuthInterceptor
        // doesn't recurse into a real refresh call.
        val noopRefresher = TokenRefresher { false }
        val authInterceptor = AuthInterceptor(storage, noopRefresher)

        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val api = retrofit2.Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create(moshi))
            .build()
            .create(WayfinderApi::class.java)

        // Server returns a 404 with a structured envelope — any non-401 response
        // is fine; we only care about what the logger wrote.
        val errorBody = moshi.adapter(MobileApiErrorEnvelope::class.java).toJson(
            MobileApiErrorEnvelope(
                com.wayfinder.android.data.remote.MobileApiError(
                    code = "NOT_FOUND",
                    message = "Not found",
                    requestId = "req_test"
                )
            )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody(errorBody)
        )

        // Issue a call that goes through the auth interceptor (i.e., not a
        // login or refresh call) so a Bearer token is attached.
        runCatching { api.getProfile() }

        // We must have actually logged something — otherwise the test is vacuous.
        assertTrue("Logger should have captured at least one line", captured.isNotEmpty())

        // The access token and refresh token must never appear in any logged line.
        captured.forEach { line ->
            assertFalse(
                "Access token must not appear in logs. Offending line: $line",
                line.contains(accessToken)
            )
            assertFalse(
                "Refresh token must not appear in logs. Offending line: $line",
                line.contains(refreshToken)
            )
            assertFalse(
                "Authorization header value must not appear in logs. Offending line: $line",
                line.contains("Bearer", ignoreCase = true)
            )
        }

        // Sanity: the request itself DID carry the token — proves the
        // interceptor attached it and the logger still didn't leak it.
        val recorded = server.takeRequest()
        val authHeader = recorded.getHeader("Authorization")
        assertNotNull("Bearer token should have been attached to the request", authHeader)
        assertEquals("Bearer $accessToken", authHeader)
    }

    /**
     * The reverse sanity check: if someone accidentally bumps the logging
     * level to HEADERS, the access token WOULD leak. We verify our
     * assertion logic is sound by showing HEADERS-level logging captures
     * the Bearer header. This protects the test above from a future
     * refactor that silently breaks it.
     */
    @Test
    fun `headers-level logging WOULD leak the token - sanity check for the BASIC test`() = runBlocking {
        val accessToken = "SECRET-jwt-access-DO-NOT-LEAK-1234567890"
        val storage = InMemoryTokenStorage().apply { saveTokens(accessToken, "r") }

        val captured = mutableListOf<String>()
        val loggingInterceptor = HttpLoggingInterceptor { captured.add(it) }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(storage, TokenRefresher { false }))
            .addInterceptor(loggingInterceptor)
            .build()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val api = retrofit2.Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create(moshi))
            .build()
            .create(WayfinderApi::class.java)

        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody(
                    moshi.adapter(MobileApiErrorEnvelope::class.java).toJson(
                        MobileApiErrorEnvelope(
                            com.wayfinder.android.data.remote.MobileApiError(
                                code = "NOT_FOUND",
                                message = "Not found",
                                requestId = null
                            )
                        )
                    )
                )
        )

        runCatching { api.getProfile() }

        val leaked = captured.any { it.contains(accessToken) }
        assertTrue(
            "Sanity check: HEADERS-level logging should have leaked the token. " +
                "If this fails, the BASIC-level test's assertion is vacuous.",
            leaked
        )
    }

    /**
     * A successful token refresh via [AuthInterceptor]'s 401 path must
     * persist the rotated tokens into storage — and the refresh response
     * body (which contains the new tokens) must never be logged.
     */
    @Test
    fun `token refresh on 401 persists rotated tokens and never logs them`() = runBlocking {
        val initialAccess = "ACCESS-INITIAL"
        val initialRefresh = "REFRESH-INITIAL"
        val rotatedAccess = "ACCESS-ROTATED-SECRET"
        val rotatedRefresh = "REFRESH-ROTATED-SECRET"

        val storage = InMemoryTokenStorage().apply {
            saveTokens(initialAccess, initialRefresh)
        }

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val refreshAdapter = moshi.adapter(MobileRefreshResponse::class.java)

        // A real refresher that hits the mock server's /api/auth/refresh
        // and persists the rotated tokens.
        val plainClient = okhttp3.OkHttpClient.Builder().build()
        val refresher = com.wayfinder.android.data.remote.OkHttpTokenRefresher(
            server.url("/").toString(),
            plainClient,
            storage,
            moshi
        )

        val captured = mutableListOf<String>()
        val loggingInterceptor = HttpLoggingInterceptor { captured.add(it) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val authedClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(storage, refresher))
            .addInterceptor(loggingInterceptor)
            .build()

        val api = retrofit2.Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(authedClient)
            .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create(moshi))
            .build()
            .create(WayfinderApi::class.java)

        // First call: 401 → triggers refresh.
        server.enqueue(MockResponse().setResponseCode(401).setBody(
            moshi.adapter(MobileApiErrorEnvelope::class.java).toJson(
                MobileApiErrorEnvelope(
                    com.wayfinder.android.data.remote.MobileApiError(
                        code = "AUTH_EXPIRED",
                        message = "Access token expired",
                        requestId = "r1"
                    )
                )
            )
        ))
        // Refresh response.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                refreshAdapter.toJson(
                    MobileRefreshResponse(
                        accessToken = rotatedAccess,
                        refreshToken = rotatedRefresh
                    )
                )
            )
        )
        // Retry of the original request with the rotated token.
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            moshi.adapter(com.wayfinder.android.data.remote.ProfileDTO::class.java).toJson(
                com.wayfinder.android.data.remote.ProfileDTO(
                    com.wayfinder.android.data.remote.MobileUser(id = "u_1", email = "x@y.z")
                )
            )
        ))

        val profile = api.getProfile()
        assertEquals("u_1", profile.user.id)

        // Rotated tokens must be in storage.
        assertEquals(rotatedAccess, storage.getAccessToken())
        assertEquals(rotatedRefresh, storage.getRefreshToken())

        // The rotated token values must never appear in the captured logs.
        captured.forEach { line ->
            assertFalse(
                "Rotated access token must not appear in logs. Offending line: $line",
                line.contains(rotatedAccess)
            )
            assertFalse(
                "Rotated refresh token must not appear in logs. Offending line: $line",
                line.contains(rotatedRefresh)
            )
        }
    }

    // endregion
}
