package com.wayfinder.android.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.wayfinder.android.data.local.TokenStorage
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Wire layer for the Wayfinder API.
 *
 * Builds:
 *  - a shared [Moshi] instance with Kotlin reflection support
 *  - a plain [OkHttpClient] used by [OkHttpTokenRefresher] (no auth interceptor —
 *    avoids recursive refresh on the refresh call itself)
 *  - an authenticated [OkHttpClient] with the [AuthInterceptor] wired in
 *  - the [WayfinderApi] Retrofit service
 *
 * The base URL is fixed to the production Wayfinder server. The Android app
 * is a thin consumer and never substitutes alternative hosts.
 */
object ApiModule {

    const val BASE_URL = "https://my-project-wheat-omega-90.vercel.app/"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    fun moshi(): Moshi = moshi

    /**
     * Builds a [TokenStorage] backed by EncryptedSharedPreferences.
     */
    fun createTokenStorage(context: android.content.Context): TokenStorage =
        com.wayfinder.android.data.local.EncryptedTokenStorage(context.applicationContext)

    /**
     * Builds the [WayfinderApi] using the provided [tokenStorage]. Both the
     * authenticated client and the plain refresh client share the same storage
     * so refresh results are visible to subsequent requests.
     */
    fun createApiWith(tokenStorage: TokenStorage): WayfinderApi {
        val logging = okhttp3.logging.HttpLoggingInterceptor().apply {
            // BASIC only — never log headers or bodies, to avoid leaking tokens.
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
        }

        val plainClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val refresher = OkHttpTokenRefresher(BASE_URL, plainClient, tokenStorage, moshi)
        val authInterceptor = AuthInterceptor(tokenStorage, refresher)

        val authedClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        return retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(authedClient)
            .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create(moshi))
            .build()
            .create(WayfinderApi::class.java)
    }
}

/**
 * Implements [TokenRefresher] using a plain (auth-less) OkHttp client.
 *
 * The refresh endpoint accepts the refresh token in the request body, so no
 * Authorization header is needed. We deliberately avoid the Retrofit service
 * here so refresh can't recurse through the [AuthInterceptor].
 */
internal class OkHttpTokenRefresher(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val tokenStorage: TokenStorage,
    private val moshi: Moshi
) : TokenRefresher {

    private val requestAdapter = moshi.adapter(MobileRefreshRequest::class.java)
    private val responseAdapter = moshi.adapter(MobileRefreshResponse::class.java)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun refresh(): Boolean = runBlocking {
        val refreshToken = tokenStorage.getRefreshToken() ?: return@runBlocking false
        try {
            val body = requestAdapter.toJson(MobileRefreshRequest(refreshToken))
                .toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${baseUrl}api/auth/refresh")
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runBlocking false
                val raw = resp.body?.string() ?: return@runBlocking false
                val parsed = responseAdapter.fromJson(raw) ?: return@runBlocking false
                tokenStorage.saveTokens(parsed.accessToken, parsed.refreshToken)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
