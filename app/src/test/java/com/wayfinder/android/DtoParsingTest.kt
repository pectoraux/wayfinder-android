package com.wayfinder.android

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.wayfinder.android.data.remote.MobileApiError
import com.wayfinder.android.data.remote.MobileApiErrorEnvelope
import com.wayfinder.android.data.remote.MobileLoginRequest
import com.wayfinder.android.data.remote.MobileLoginResponse
import com.wayfinder.android.data.remote.OutcomesDTO
import com.wayfinder.android.data.remote.StrategyDTO
import com.wayfinder.android.data.remote.WayfinderApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Verifies that DTOs parse the server's JSON contract correctly using a
 * MockWebServer-backed Retrofit instance.
 *
 * The Android client is a thin consumer; getting parsing right is the bulk
 * of its responsibility, so these tests are deliberately thorough about
 * field-name alignment with the server contract.
 *
 * Thin-client invariant: the client never derives OutcomeType, EvaluationStatus,
 * ConfidenceLevel, or predictions. These tests assert that opaque server strings
 * are preserved verbatim — never parsed, never interpreted.
 */
class DtoParsingTest {

    private lateinit var server: MockWebServer
    private lateinit var api: WayfinderApi
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WayfinderApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // region MobileLoginResponse

    @Test
    fun `login response parses accessToken, refreshToken, and user`() = runBlocking {
        val body = """
            {
              "accessToken": "jwt-access-123",
              "refreshToken": "opaque-refresh-456",
              "user": {
                "id": "u_1",
                "email": "demo-user@wayfinder.app",
                "displayName": "Demo User",
                "createdAt": "2024-01-01T00:00:00.000Z"
              }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val resp: MobileLoginResponse = api.login(
            MobileLoginRequest("demo-user@wayfinder.app", "wayfinder")
        )

        assertEquals("jwt-access-123", resp.accessToken)
        assertEquals("opaque-refresh-456", resp.refreshToken)
        assertEquals("u_1", resp.user?.id)
        assertEquals("demo-user@wayfinder.app", resp.user?.email)
        assertEquals("Demo User", resp.user?.displayName)
        assertEquals("2024-01-01T00:00:00.000Z", resp.user?.createdAt)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/auth/credentials", recorded.path)
    }

    @Test
    fun `login response tolerates a null user`() = runBlocking {
        val body = """
            {
              "accessToken": "a",
              "refreshToken": "r"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val resp = api.login(MobileLoginRequest("x@y.z", "pw"))
        assertEquals("a", resp.accessToken)
        assertEquals("r", resp.refreshToken)
        assertNull(resp.user)
    }

    @Test
    fun `login response tolerates missing user fields`() = runBlocking {
        val body = """
            {
              "accessToken": "a",
              "refreshToken": "r",
              "user": { "id": "u_2" }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val resp = api.login(MobileLoginRequest("x@y.z", "pw"))
        assertEquals("u_2", resp.user?.id)
        assertNull(resp.user?.email)
        assertNull(resp.user?.displayName)
        assertNull(resp.user?.createdAt)
    }

    // endregion

    // region MobileApiError

    @Test
    fun `MobileApiError parses code, message, and requestId directly`() {
        val body = """
            {
              "code": "AUTH_REFRESH_INVALID",
              "message": "Refresh token is no longer valid.",
              "requestId": "req_abc123"
            }
        """.trimIndent()
        val adapter = moshi.adapter(MobileApiError::class.java)
        val parsed = adapter.fromJson(body)!!

        assertEquals("AUTH_REFRESH_INVALID", parsed.code)
        assertEquals("Refresh token is no longer valid.", parsed.message)
        assertEquals("req_abc123", parsed.requestId)
    }

    @Test
    fun `MobileApiError tolerates a missing requestId`() {
        val body = """
            {
              "code": "VALIDATION_ERROR",
              "message": "email must be a valid email address"
            }
        """.trimIndent()
        val adapter = moshi.adapter(MobileApiError::class.java)
        val parsed = adapter.fromJson(body)!!

        assertEquals("VALIDATION_ERROR", parsed.code)
        assertEquals("email must be a valid email address", parsed.message)
        assertNull(parsed.requestId)
    }

    @Test
    fun `MobileApiError envelope parses the nested error object`() {
        val body = """
            {
              "error": {
                "code": "RATE_LIMITED",
                "message": "Too many requests.",
                "requestId": "req_xyz"
              }
            }
        """.trimIndent()
        val adapter = moshi.adapter(MobileApiErrorEnvelope::class.java)
        val parsed = adapter.fromJson(body)!!

        assertNotNull(parsed.error)
        assertEquals("RATE_LIMITED", parsed.error.code)
        assertEquals("Too many requests.", parsed.error.message)
        assertEquals("req_xyz", parsed.error.requestId)
    }

    @Test
    fun `MobileApiError tolerates unknown future fields`() {
        val body = """
            {
              "code": "SERVER_ERROR",
              "message": "boom",
              "requestId": "r1",
              "retryAfterMs": 5000,
              "docUrl": "https://example.com/errors/SERVER_ERROR"
            }
        """.trimIndent()
        val adapter = moshi.adapter(MobileApiError::class.java)
        val parsed = adapter.fromJson(body)!!

        assertEquals("SERVER_ERROR", parsed.code)
        assertEquals("boom", parsed.message)
        assertEquals("r1", parsed.requestId)
    }

    // endregion

    // region StrategyDTO

    @Test
    fun `strategy parses best trajectory, blockers, and actions`() = runBlocking {
        val body = """
            {
              "id": "strat_42",
              "title": "Pivot to self-serve",
              "summary": "Reduce sales-led friction.",
              "bestTrajectory": {
                "id": "traj_1",
                "label": "Self-serve growth",
                "description": "Trial funnel with in-product onboarding.",
                "confidenceLabel": "high"
              },
              "blockers": [
                { "id": "b1", "label": "Onboarding gap", "severity": "high" }
              ],
              "actions": [
                { "id": "a1", "title": "Ship trial flow", "status": "in_progress" }
              ],
              "createdAt": "2024-09-01T12:00:00.000Z"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val strategy: StrategyDTO = api.adoptStrategy()

        assertEquals("strat_42", strategy.id)
        assertEquals("Pivot to self-serve", strategy.title)
        assertEquals("Reduce sales-led friction.", strategy.summary)
        assertEquals("traj_1", strategy.bestTrajectory?.id)
        assertEquals("Self-serve growth", strategy.bestTrajectory?.label)
        assertEquals("Trial funnel with in-product onboarding.", strategy.bestTrajectory?.description)
        // confidenceLabel is server-authoritative and preserved verbatim — never parsed.
        assertEquals("high", strategy.bestTrajectory?.confidenceLabel)
        assertEquals(1, strategy.blockers.size)
        assertEquals("Onboarding gap", strategy.blockers.first().label)
        assertEquals("high", strategy.blockers.first().severity)
        assertEquals(1, strategy.actions.size)
        assertEquals("Ship trial flow", strategy.actions.first().title)
        assertEquals("in_progress", strategy.actions.first().status)
    }

    @Test
    fun `strategy tolerates absent optional fields`() = runBlocking {
        val body = """
            {
              "id": "s1",
              "title": "Strategy"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val strategy: StrategyDTO = api.adoptStrategy()
        assertEquals("s1", strategy.id)
        assertEquals("Strategy", strategy.title)
        assertNull(strategy.summary)
        assertNull(strategy.bestTrajectory)
        assertTrue(strategy.blockers.isEmpty())
        assertTrue(strategy.actions.isEmpty())
        assertNull(strategy.createdAt)
    }

    @Test
    fun `strategy tolerates unknown future fields`() = runBlocking {
        val body = """
            {
              "id": "s1",
              "title": "Strategy",
              "futureField": "ignored-by-client",
              "confidenceScore": 0.87
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val strategy: StrategyDTO = api.adoptStrategy()
        assertEquals("s1", strategy.id)
        assertEquals("Strategy", strategy.title)
        // Unknown fields are silently dropped — the client is forward-compatible.
        assertNull(strategy.bestTrajectory)
    }

    @Test
    fun `strategy history parses items list and nextCursor`() = runBlocking {
        val body = """
            {
              "items": [
                { "id": "s1", "title": "First" },
                { "id": "s2", "title": "Second" }
              ],
              "nextCursor": "cursor_xyz"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val history = api.getStrategyHistory()
        assertEquals(2, history.items.size)
        assertEquals("First", history.items[0].title)
        assertEquals("Second", history.items[1].title)
        assertEquals("cursor_xyz", history.nextCursor)
    }

    // endregion

    // region OutcomesDTO

    @Test
    fun `outcomes parse expected, observed, and server-authoritative evaluation`() = runBlocking {
        val body = """
            {
              "strategyId": "strat_42",
              "expected": [
                { "id": "e1", "type": "ADOPTION", "label": "Trial signups", "value": "120" }
              ],
              "observed": [
                {
                  "id": "o1",
                  "type": "ADOPTION",
                  "label": "Trial signups",
                  "value": "98",
                  "observedAt": "2024-09-02T10:00:00.000Z"
                }
              ],
              "evaluation": {
                "status": "PARTIALLY_MET",
                "notes": "Below target; onboarding friction suspected."
              }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val outcomes: OutcomesDTO = api.getOutcomes("strat_42")

        assertEquals("strat_42", outcomes.strategyId)
        assertEquals(1, outcomes.expected.size)
        assertEquals("e1", outcomes.expected.first().id)
        // OutcomeType ("ADOPTION") is server-authoritative — preserved as opaque string.
        assertEquals("ADOPTION", outcomes.expected.first().type)
        assertEquals("Trial signups", outcomes.expected.first().label)
        assertEquals("120", outcomes.expected.first().value)
        assertEquals(1, outcomes.observed.size)
        assertEquals("98", outcomes.observed.first().value)
        assertEquals("2024-09-02T10:00:00.000Z", outcomes.observed.first().observedAt)
        // EvaluationStatus ("PARTIALLY_MET") is server-authoritative — preserved as opaque string.
        assertEquals("PARTIALLY_MET", outcomes.evaluation?.status)
        assertEquals(
            "Below target; onboarding friction suspected.",
            outcomes.evaluation?.notes
        )
    }

    @Test
    fun `outcomes render server-authoritative summary verbatim`() = runBlocking {
        val body = """
            {
              "strategyId": "strat_42",
              "expected": [],
              "observed": [],
              "summary": {
                "achieved": 3,
                "partial": 1,
                "missed": 0,
                "pending": 2
              }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val outcomes: OutcomesDTO = api.getOutcomes("strat_42")
        // The summary counts are computed by the server; the client never
        // recomputes them. They must round-trip exactly.
        assertEquals(3, outcomes.summary?.achieved)
        assertEquals(1, outcomes.summary?.partial)
        assertEquals(0, outcomes.summary?.missed)
        assertEquals(2, outcomes.summary?.pending)
    }

    @Test
    fun `outcomes tolerate a missing evaluation and summary`() = runBlocking {
        val body = """
            {
              "strategyId": "strat_42",
              "expected": [],
              "observed": []
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val outcomes: OutcomesDTO = api.getOutcomes("strat_42")
        assertEquals("strat_42", outcomes.strategyId)
        assertTrue(outcomes.expected.isEmpty())
        assertTrue(outcomes.observed.isEmpty())
        assertNull(outcomes.evaluation)
        assertNull(outcomes.summary)
    }

    // endregion
}
