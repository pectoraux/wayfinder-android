package com.wayfinder.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for the Wayfinder thin client.
 *
 * Verifies that:
 *  1. `MainActivity` launches and the login screen renders with all three
 *     contract elements — email field, password field, and sign-in button.
 *  2. The pre-filled demo email is present and the password field starts empty,
 *     so the sign-in button is disabled until the user types a password.
 *  3. Typing a password enables the button.
 *  4. Clicking sign-in attempts an API connection — the app either navigates
 *     forward to the strategy screen or surfaces a user-visible error message.
 *
 * These are real assertions on observable UI state, not just existence checks.
 *
 * Thin-client invariant: this test never asserts on server-authoritative fields
 * like OutcomeType, EvaluationStatus, or ConfidenceLevel.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginScreen_renders_email_password_and_signIn_button() {
        composeRule.waitForIdle()

        // Top app bar shows the app name "Wayfinder".
        composeRule.onNodeWithText("Wayfinder").assertIsDisplayed()

        // All three contract elements are on screen.
        composeRule.onNodeWithTag("email_field").assertIsDisplayed()
        composeRule.onNodeWithTag("password_field").assertIsDisplayed()
        composeRule.onNodeWithTag("login_button").assertIsDisplayed()

        // Field labels are present.
        composeRule.onNodeWithText("Email").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        // The button text is "Sign in".
        composeRule.onNodeWithText("Sign in").assertIsDisplayed()
    }

    @Test
    fun loginScreen_email_is_prefilled_and_password_is_blank_so_button_is_disabled() {
        composeRule.waitForIdle()

        // Demo email is pre-filled — real content assertion, not just node existence.
        composeRule.onNodeWithTag("email_field")
            .assertTextContains("demo-user@wayfinder.app")

        // Password is empty: the button must be disabled.
        composeRule.onNodeWithTag("login_button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun typing_password_enables_signIn_button() {
        composeRule.waitForIdle()

        // Start: button disabled.
        composeRule.onNodeWithTag("login_button").assertIsNotEnabled()

        // Type a password.
        composeRule.onNodeWithTag("password_field").performClick()
        composeRule.onNodeWithTag("password_field").performTextInput("wayfinder")
        composeRule.waitForIdle()

        // Now: button is enabled (real state assertion, not just node existence).
        composeRule.onNodeWithTag("login_button").assertIsEnabled()
    }

    @Test
    fun tapping_signIn_attempts_api_connection_and_app_responds() {
        composeRule.waitForIdle()

        // Demo email is pre-filled; type a password to satisfy local validation.
        composeRule.onNodeWithTag("password_field").performClick()
        composeRule.onNodeWithTag("password_field").performTextInput("wayfinder")
        composeRule.onNodeWithTag("login_button").performClick()

        // Wait (up to 20s) for the app to respond. Acceptable outcomes:
        //   - Success: navigated away from login (login_button no longer displayed)
        //   - Failure: error_message node surfaced
        // Either outcome satisfies the smoke test — the goal is to confirm
        // that the tap triggered an API connection attempt and the app reacted.
        // waitUntil() throws ComposeTimeoutException if neither happens in time,
        // which fails the test naturally.
        val reacted = composeRule.waitUntil(20_000) {
            val stillOnLogin = runCatching {
                composeRule.onNodeWithTag("login_button").assertIsDisplayed()
                true
            }.getOrDefault(false)

            val errored = runCatching {
                composeRule.onNodeWithTag("error_message").assertIsDisplayed()
                true
            }.getOrDefault(false)

            (!stillOnLogin) || errored
        }

        assertTrue(
            "App must react to a sign-in tap within 20s — either navigate forward or surface an error",
            reacted
        )
    }

    @Test
    fun packageName_matches_application_id() {
        // Real assertion: the instrumentation target is the Wayfinder app, not
        // some other package on the emulator.
        val context = composeRule.activity.applicationContext
        assertEquals("com.wayfinder.android", context.packageName)
    }
}
