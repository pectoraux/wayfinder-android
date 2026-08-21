package com.wayfinder.android.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the Wayfinder access/refresh tokens.
 *
 * Production implementation uses [EncryptedSharedPreferences] backed by the
 * Android Keystore. Tokens are never written to plain SharedPreferences and
 * never logged.
 *
 * An [InMemoryTokenStorage] is provided for JVM unit tests where the
 * Android Keystore is unavailable.
 */
interface TokenStorage {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun saveTokens(accessToken: String, refreshToken: String)
    fun clear()
}

class EncryptedTokenStorage(context: Context) : TokenStorage {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getAccessToken(): String? = prefs.getString(KEY_ACCESS, null)
    override fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)

    override fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "wayfinder_tokens"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
    }
}

/**
 * Pure-JVM implementation used by unit tests. Not thread-safe for concurrent
 * writes; tests run sequentially.
 */
class InMemoryTokenStorage : TokenStorage {

    @Volatile private var access: String? = null
    @Volatile private var refresh: String? = null

    override fun getAccessToken(): String? = access
    override fun getRefreshToken(): String? = refresh

    override fun saveTokens(accessToken: String, refreshToken: String) {
        access = accessToken
        refresh = refreshToken
    }

    override fun clear() {
        access = null
        refresh = null
    }
}
