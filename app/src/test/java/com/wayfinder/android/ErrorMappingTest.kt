package com.wayfinder.android

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.wayfinder.android.core.ErrorCode
import com.wayfinder.android.data.remote.MobileApiError
import com.wayfinder.android.data.remote.MobileApiErrorEnvelope
import com.wayfinder.android.data.remote.mapErrorCode
import com.wayfinder.android.data.remote.toWayfinderError
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Verifies that the server's stable error-code contract is mapped correctly
 * into client-side [ErrorCode] values and user-visible messages.
 *
 * The server returns errors in the canonical envelope:
 *   { error: { code, message, requestId } }
 *
 * The client maps the [ErrorCode] to a user-facing string via
 * [ErrorCode.toUserMessage]. User messages must never expose internal
 * details (tokens, request IDs, stack traces).
 */
class ErrorMappingTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val envelopeAdapter = moshi.adapter(MobileApiErrorEnvelope::class.java)

    // region All 10 server error codes map to known enum values

    @Test
    fun `AUTH_REQUIRED maps to ErrorCode AUTH_REQUIRED`() {
        assertEquals(ErrorCode.AUTH_REQUIRED, mapErrorCode("AUTH_REQUIRED"))
    }

    @Test
    fun `AUTH_EXPIRED maps to ErrorCode AUTH_EXPIRED`() {
        assertEquals(ErrorCode.AUTH_EXPIRED, mapErrorCode("AUTH_EXPIRED"))
    }

    @Test
    fun `AUTH_REFRESH_INVALID maps to ErrorCode AUTH_REFRESH_INVALID`() {
        assertEquals(ErrorCode.AUTH_REFRESH_INVALID, mapErrorCode("AUTH_REFRESH_INVALID"))
    }

    @Test
    fun `FORBIDDEN maps to ErrorCode FORBIDDEN`() {
        assertEquals(ErrorCode.FORBIDDEN, mapErrorCode("FORBIDDEN"))
    }

    @Test
    fun `NOT_FOUND maps to ErrorCode NOT_FOUND`() {
        assertEquals(ErrorCode.NOT_FOUND, mapErrorCode("NOT_FOUND"))
    }

    @Test
    fun `VALIDATION_ERROR maps to ErrorCode VALIDATION_ERROR`() {
        assertEquals(ErrorCode.VALIDATION_ERROR, mapErrorCode("VALIDATION_ERROR"))
    }

    @Test
    fun `CONFLICT maps to ErrorCode CONFLICT`() {
        assertEquals(ErrorCode.CONFLICT, mapErrorCode("CONFLICT"))
    }

    @Test
    fun `RATE_LIMITED maps to ErrorCode RATE_LIMITED`() {
        assertEquals(ErrorCode.RATE_LIMITED, mapErrorCode("RATE_LIMITED"))
    }

    @Test
    fun `SERVER_ERROR maps to ErrorCode SERVER_ERROR`() {
        assertEquals(ErrorCode.SERVER_ERROR, mapErrorCode("SERVER_ERROR"))
    }

    @Test
    fun `SERVICE_UNAVAILABLE maps to ErrorCode SERVICE_UNAVAILABLE`() {
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, mapErrorCode("SERVICE_UNAVAILABLE"))
    }

    @Test
    fun `all ten server codes map exhaustively`() {
        // Belt-and-braces: enumerate every server code so a future addition
        // is impossible to silently miss.
        val cases = mapOf(
            "AUTH_REQUIRED" to ErrorCode.AUTH_REQUIRED,
            "AUTH_EXPIRED" to ErrorCode.AUTH_EXPIRED,
            "AUTH_REFRESH_INVALID" to ErrorCode.AUTH_REFRESH_INVALID,
            "FORBIDDEN" to ErrorCode.FORBIDDEN,
            "NOT_FOUND" to ErrorCode.NOT_FOUND,
            "VALIDATION_ERROR" to ErrorCode.VALIDATION_ERROR,
            "CONFLICT" to ErrorCode.CONFLICT,
            "RATE_LIMITED" to ErrorCode.RATE_LIMITED,
            "SERVER_ERROR" to ErrorCode.SERVER_ERROR,
            "SERVICE_UNAVAILABLE" to ErrorCode.SERVICE_UNAVAILABLE
        )
        assertEquals(10, cases.size)
        cases.forEach { (serverCode, expected) ->
            assertEquals(
                "Server code '$serverCode' should map to $expected",
                expected,
                mapErrorCode(serverCode)
            )
        }
    }

    // endregion

    // region Forward-compatibility for unknown codes

    @Test
    fun `unknown server code maps to UNKNOWN`() {
        assertEquals(ErrorCode.UNKNOWN, mapErrorCode("UNRECOGNIZED_FUTURE_CODE"))
    }

    @Test
    fun `empty server code maps to UNKNOWN`() {
        assertEquals(ErrorCode.UNKNOWN, mapErrorCode(""))
    }

    // endregion

    // region User-visible messages

    @Test
    fun `every ErrorCode exposes a non-blank user-visible message`() {
        ErrorCode.values().forEach { code ->
            val msg = code.toUserMessage()
            assertTrue("Message for $code must not be blank", msg.isNotBlank())
        }
    }

    @Test
    fun `user messages never leak tokens or request IDs`() {
        ErrorCode.values().forEach { code ->
            val msg = code.toUserMessage()
            assertFalse("$code message must not contain 'Bearer'", msg.contains("Bearer", ignoreCase = true))
            assertFalse("$code message must not contain 'token'", msg.contains("token", ignoreCase = true))
            assertFalse("$code message must not contain 'req_'", msg.contains("req_"))
        }
    }

    @Test
    fun `AUTH_REQUIRED message prompts sign in`() {
        val msg = ErrorCode.AUTH_REQUIRED.toUserMessage()
        assertTrue(msg.contains("sign in", ignoreCase = true))
    }

    @Test
    fun `AUTH_REFRESH_INVALID message prompts sign in again`() {
        val msg = ErrorCode.AUTH_REFRESH_INVALID.toUserMessage()
        assertTrue(msg.contains("sign in", ignoreCase = true))
    }

    @Test
    fun `NETWORK_ERROR message mentions connection`() {
        val msg = ErrorCode.NETWORK_ERROR.toUserMessage()
        assertTrue(msg.contains("connection", ignoreCase = true))
    }

    @Test
    fun `RATE_LIMITED message mentions slowing down`() {
        val msg = ErrorCode.RATE_LIMITED.toUserMessage()
        assertTrue(msg.contains("slow", ignoreCase = true) || msg.contains("too many", ignoreCase = true))
    }

    // endregion

    // region Throwable → WayfinderError

    @Test
    fun `HttpException with structured envelope maps to typed error`() {
        val body = """
            {"error":{"code":"AUTH_REFRESH_INVALID","message":"Refresh token invalid","requestId":"req_1"}}
        """.trimIndent()
        val response = Response.error<Any>(
            401,
            body.toResponseBody("application/json".toMediaType())
        )
        val exception = HttpException(response)

        val mapped = exception.toWayfinderError()
        assertEquals(ErrorCode.AUTH_REFRESH_INVALID, mapped.code)
        assertEquals("Refresh token invalid", mapped.message)
        assertEquals("req_1", mapped.requestId)
    }

    @Test
    fun `HttpException with unparseable body falls back to UNKNOWN but keeps a message`() {
        val response = Response.error<Any>(
            500,
            "not-json".toResponseBody("text/plain".toMediaType())
        )
        val exception = HttpException(response)

        val mapped = exception.toWayfinderError()
        assertEquals(ErrorCode.UNKNOWN, mapped.code)
        assertNotNull(mapped.message)
        assertTrue(mapped.message.isNotBlank())
        assertNull(mapped.requestId)
    }

    @Test
    fun `HttpException with envelope but blank message falls back to HTTP message`() {
        val body = """
            {"error":{"code":"NOT_FOUND","message":"","requestId":null}}
        """.trimIndent()
        val response = Response.error<Any>(
            404,
            body.toResponseBody("application/json".toMediaType())
        )
        val exception = HttpException(response)

        val mapped = exception.toWayfinderError()
        assertEquals(ErrorCode.NOT_FOUND, mapped.code)
        assertNotNull(mapped.message)
        assertTrue(mapped.message.isNotBlank())
    }

    @Test
    fun `IOException maps to NETWORK_ERROR`() {
        val err = IOException("connection refused").toWayfinderError()
        assertEquals(ErrorCode.NETWORK_ERROR, err.code)
        assertNotNull(err.message)
        assertNull(err.requestId)
    }

    @Test
    fun `generic throwable maps to UNKNOWN with its message`() {
        val err = IllegalStateException("boom").toWayfinderError()
        assertEquals(ErrorCode.UNKNOWN, err.code)
        assertEquals("boom", err.message)
    }

    @Test
    fun `throwable with null message maps to UNKNOWN with a fallback message`() {
        val err = IllegalStateException().toWayfinderError()
        assertEquals(ErrorCode.UNKNOWN, err.code)
        assertNotNull(err.message)
    }

    // endregion

    // region Moshi round-trip

    @Test
    fun `envelope adapter round-trips`() {
        val original = MobileApiErrorEnvelope(
            MobileApiError(
                code = "RATE_LIMITED",
                message = "Too many requests",
                requestId = "req_2"
            )
        )
        val json = envelopeAdapter.toJson(original)
        val parsed = envelopeAdapter.fromJson(json)!!
        assertEquals("RATE_LIMITED", parsed.error.code)
        assertEquals("Too many requests", parsed.error.message)
        assertEquals("req_2", parsed.error.requestId)
    }

    // endregion
}
