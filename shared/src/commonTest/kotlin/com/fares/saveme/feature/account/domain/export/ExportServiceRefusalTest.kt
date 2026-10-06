package com.fares.saveme.feature.account.domain.export

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Refusals are chosen by code and never retried (FR-017 to FR-023, P-02.4, P-02.6). */
class ExportServiceRefusalTest {

    private suspend fun outcome(status: Int, body: String, vararg headers: Pair<String, String>, calls: ((Int) -> Unit)? = null): RequestOutcome {
        val env = ExportTestEnv { jsonResponse(status, body, *headers) }
        env.signedIn()
        val result = DefaultExportService(env.api).request(ExportScope.Account, null)
        calls?.invoke(env.requests.size)
        return result
    }

    @Test
    fun conflictOpensTheCurrentExport() = runTest {
        val result = assertIs<RequestOutcome.InProgress>(
            outcome(409, problemBody(409, "EXPORT_IN_PROGRESS", ""","current":${exportJson(id = "run1", status = "running")}"""), calls = { assertEquals(1, it) }),
        )
        assertEquals("run1", result.current?.id)
        assertEquals(ExportStatus.Running, result.current?.status)
    }

    @Test
    fun conflictWithoutOrWithAnUnreadableCurrentHasNone() = runTest {
        assertNull(assertIs<RequestOutcome.InProgress>(outcome(409, problemBody(409, "EXPORT_IN_PROGRESS"))).current)
        assertNull(assertIs<RequestOutcome.InProgress>(outcome(409, problemBody(409, "EXPORT_IN_PROGRESS", ""","current":{"id":5}"""))).current)
    }

    @Test
    fun rateLimitCarriesTheWaitFromEitherPlaceOrNone() = runTest {
        assertEquals(RequestOutcome.RateLimited(3600), outcome(429, problemBody(429, "RATE_LIMITED", ""","retry_after_seconds":3600"""), "Retry-After" to "3600"))
        assertEquals(RequestOutcome.RateLimited(120), outcome(429, problemBody(429, "RATE_LIMITED"), "Retry-After" to "120"))
        assertEquals(RequestOutcome.RateLimited(null), outcome(429, problemBody(429, "RATE_LIMITED")))
    }

    @Test
    fun notFoundOnARequestIsTheWalletGone() = runTest {
        assertEquals(RequestOutcome.WalletGone, outcome(404, problemBody(404, "NOT_FOUND")))
    }

    @Test
    fun otherRefusalsAreFailuresWithTheirCodeAndRequestId() = runTest {
        mapOf(
            422 to "VALIDATION_FAILED", 400 to "MALFORMED_JSON", 406 to "NOT_ACCEPTABLE", 415 to "UNSUPPORTED_MEDIA_TYPE", 500 to "INTERNAL_ERROR",
        ).forEach { (status, code) ->
            val failed = assertIs<RequestOutcome.Failed>(outcome(status, problemBody(status, code), calls = { assertEquals(1, it, "$status is not retried") }))
            assertEquals(ApiErrorCode.from(code), failed.code)
            assertEquals("req-42", failed.requestId)
        }
    }

    @Test
    fun unauthenticatedAndUpdateRequiredAreLeftToTheAppWideEvents() = runTest {
        assertEquals(ApiErrorCode.UNAUTHENTICATED, assertIs<RequestOutcome.Failed>(outcome(401, problemBody(401, "UNAUTHENTICATED"))).code)
        assertIs<RequestOutcome.Failed>(outcome(426, problemBody(426, "CLIENT_VERSION_UNSUPPORTED", ""","minimum_version":"2.0.0""")))
    }
}
