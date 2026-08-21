package com.wayfinder.android.core

import android.app.Application
import com.wayfinder.android.data.local.TokenStorage
import com.wayfinder.android.data.remote.ApiModule
import com.wayfinder.android.data.remote.WayfinderApi

/**
 * Application entry point.
 *
 * Performs the small amount of initialization the thin client needs:
 *  - builds the EncryptedSharedPreferences-backed [TokenStorage]
 *  - builds the [WayfinderApi] Retrofit service wired with the [AuthInterceptor]
 *
 * Exposes singletons that the feature screens consume via their ViewModels.
 */
class WayfinderApp : Application() {

    lateinit var tokenStorage: TokenStorage
        private set

    lateinit var api: WayfinderApi
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        tokenStorage = ApiModule.createTokenStorage(this)
        api = ApiModule.createApiWith(tokenStorage)
    }

    companion object {
        @Volatile
        private var instance: WayfinderApp? = null

        /**
         * Returns the singleton [WayfinderApp]. Throws if called before
         * [Application.onCreate] completes — which should never happen for
         * application-scoped access from Compose / ViewModels.
         */
        fun get(): WayfinderApp =
            instance ?: error("WayfinderApp not initialized")
    }
}
