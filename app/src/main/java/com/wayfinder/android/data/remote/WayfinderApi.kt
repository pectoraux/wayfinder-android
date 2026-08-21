package com.wayfinder.android.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit definition of the Wayfinder server API.
 *
 * Base URL: https://my-project-wheat-omega-90.vercel.app
 *
 * The [AuthInterceptor] transparently attaches the Bearer access token to all
 * authenticated calls and performs a single refresh-and-retry on 401.
 */
interface WayfinderApi {

    // region Auth

    @POST("api/auth/credentials")
    suspend fun login(@Body request: MobileLoginRequest): MobileLoginResponse

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: MobileRefreshRequest): MobileRefreshResponse

    @POST("api/auth/logout")
    suspend fun logout(@Body request: MobileLogoutRequest = MobileLogoutRequest())

    // endregion

    // region Profile

    @GET("api/profile")
    suspend fun getProfile(): ProfileDTO

    @POST("api/profile")
    suspend fun updateProfile(@Body request: ProfileUpdateRequest): ProfileDTO

    // endregion

    // region Strategy

    @GET("api/strategy/adopt")
    suspend fun adoptStrategy(): StrategyDTO

    @GET("api/strategy/{id}/explanation")
    suspend fun getExplanation(@Path("id") id: String): ExplanationDTO

    @GET("api/strategy/{id}/outcomes")
    suspend fun getOutcomes(@Path("id") id: String): OutcomesDTO

    @POST("api/strategy/{id}/outcome")
    suspend fun submitStrategyOutcome(
        @Path("id") id: String,
        @Body request: OutcomeSubmissionRequest
    ): OutcomeSubmissionResponse

    @GET("api/strategy/history")
    suspend fun getStrategyHistory(
        @Query("cursor") cursor: String? = null
    ): StrategyHistoryDTO

    // endregion

    // region Actions

    @GET("api/actions")
    suspend fun getActions(): ActionsDTO

    @POST("api/actions/{id}/outcome")
    suspend fun submitActionOutcome(
        @Path("id") id: String,
        @Body request: OutcomeSubmissionRequest
    ): OutcomeSubmissionResponse

    // endregion
}
