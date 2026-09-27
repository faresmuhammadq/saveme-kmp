package com.fares.saveme.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProblemDetailsTest {

    @Test
    fun decodesFullProblemAndIgnoresUnknownMembers() {
        val body = """
            {"type":"https://api.example/problems/validation-failed","title":"Validation failed",
             "status":422,"detail":"بيانات غير صحيحة","code":"VALIDATION_FAILED",
             "request_id":"0192f7a0-9999-7000-8000-00000000abcd",
             "errors":[{"pointer":"/email","code":"REQUIRED","extra":1}],
             "retry_after_seconds":30,"minimum_version":"0.2.0","something_new":true}
        """.trimIndent()

        val problem = AppJson.decodeFromString(ProblemDetails.serializer(), body)

        assertEquals(422, problem.status)
        assertEquals("VALIDATION_FAILED", problem.code)
        assertEquals("0192f7a0-9999-7000-8000-00000000abcd", problem.requestId)
        assertEquals("/email", problem.errors!!.single().pointer)
        assertEquals("REQUIRED", problem.errors!!.single().code)
        assertEquals(30L, problem.retryAfterSeconds)
        assertEquals("0.2.0", problem.minimumVersion)
    }

    @Test
    fun mapsEveryHandledCodeToItsEnumEntry() {
        val handled = listOf(
            "VALIDATION_FAILED", "MALFORMED_JSON", "CLIENT_VERSION_REQUIRED", "CLIENT_VERSION_INVALID",
            "CLIENT_VERSION_UNSUPPORTED", "UNAUTHENTICATED", "FORBIDDEN", "NOT_FOUND", "NOT_ACCEPTABLE",
            "UNSUPPORTED_MEDIA_TYPE", "RATE_LIMITED", "INTERNAL_ERROR", "SERVICE_UNAVAILABLE",
            "INVALID_CREDENTIALS", "ACCOUNT_LOCKED", "EMAIL_NOT_VERIFIED", "VERIFICATION_TOKEN_INVALID",
            "RESET_TOKEN_INVALID",
        )
        handled.forEach { assertEquals(it, ApiErrorCode.from(it).name) }
    }

    @Test
    fun unseenCodeMapsToUnknown() {
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.from("SOMETHING_ADDED_LATER"))
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.from("UNKNOWN"))
    }

    @Test
    fun retryClassFollowsTheCatalogue() {
        assertEquals(RetryClass.Automatic, ApiErrorCode.RATE_LIMITED.retryClass)
        assertEquals(RetryClass.Never, ApiErrorCode.INVALID_CREDENTIALS.retryClass)
        assertEquals(RetryClass.AfterChange, ApiErrorCode.VALIDATION_FAILED.retryClass)
    }

    @Test
    fun missingOptionalMembersDecodeAsNull() {
        val problem = AppJson.decodeFromString(
            ProblemDetails.serializer(),
            """{"type":"t","title":"x","status":500,"detail":"d","code":"INTERNAL_ERROR","request_id":"r"}""",
        )
        assertNull(problem.errors)
        assertNull(problem.retryAfterSeconds)
    }
}
