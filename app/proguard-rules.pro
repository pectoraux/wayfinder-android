# ----------------------------------------------------------------------------
# Wayfinder Android — ProGuard / R8 rules
#
# Minification is DISABLED for the P4.1-B release (see app/build.gradle.kts:
#   isMinifyEnabled = false
# ).
#
# This file is intentionally empty. When minification is re-enabled in a
# future release, add rules here for:
#   - Moshi generated adapters (com.squareup.moshi.*)
#   - Retrofit reflective access (retrofit2.*, okhttp3.*)
#   - Kotlin metadata (kotlin.Metadata)
#   - EncryptedSharedPreferences / AndroidX Security internals
# ----------------------------------------------------------------------------
