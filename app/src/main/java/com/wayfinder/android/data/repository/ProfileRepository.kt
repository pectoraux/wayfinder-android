package com.wayfinder.android.data.repository

import com.wayfinder.android.core.Result
import com.wayfinder.android.data.remote.ProfileDTO
import com.wayfinder.android.data.remote.ProfileUpdateRequest
import com.wayfinder.android.data.remote.WayfinderApi
import com.wayfinder.android.data.remote.toWayfinderError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Access to the authenticated user's profile.
 *
 *  - getProfile()           → GET /api/profile
 *  - updateProfile(updates) → POST /api/profile
 *
 * The client performs no validation of profile field values beyond trimming
 * and non-blank checks; the server is authoritative for normalization and
 * constraints.
 */
class ProfileRepository(private val api: WayfinderApi) {

    suspend fun getProfile(): Result<ProfileDTO> = withContext(Dispatchers.IO) {
        try {
            Result.Success(api.getProfile())
        } catch (e: Exception) {
            Result.Error(e.toWayfinderError())
        }
    }

    /**
     * Submits profile updates. Only non-null fields in [updates] are sent; the
     * server returns the full updated profile.
     */
    suspend fun updateProfile(updates: ProfileUpdateRequest): Result<ProfileDTO> =
        withContext(Dispatchers.IO) {
            try {
                val sanitized = ProfileUpdateRequest(
                    displayName = updates.displayName?.trim()?.takeIf { it.isNotBlank() },
                    email = updates.email?.trim()?.takeIf { it.isNotBlank() }
                )
                Result.Success(api.updateProfile(sanitized))
            } catch (e: Exception) {
                Result.Error(e.toWayfinderError())
            }
        }
}
