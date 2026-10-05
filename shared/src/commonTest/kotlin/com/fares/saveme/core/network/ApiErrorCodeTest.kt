package com.fares.saveme.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The codes the sync feeds branch on are known and never retried on their own (spec 009 FR-032, P-02.6). */
class ApiErrorCodeTest {

    @Test
    fun theTwoCursorCodesDecodeFromTheirWireValue() {
        assertEquals(ApiErrorCode.INVALID_CURSOR, ApiErrorCode.from("INVALID_CURSOR"))
        assertEquals(ApiErrorCode.SYNC_CURSOR_EXPIRED, ApiErrorCode.from("SYNC_CURSOR_EXPIRED"))
    }

    @Test
    fun theCursorCodesAreRetryableOnlyAfterAChange() {
        assertEquals(RetryClass.AfterChange, ApiErrorCode.INVALID_CURSOR.retryClass)
        assertEquals(RetryClass.AfterChange, ApiErrorCode.SYNC_CURSOR_EXPIRED.retryClass)
        assertNotEquals(RetryClass.Automatic, ApiErrorCode.INVALID_CURSOR.retryClass)
        assertNotEquals(RetryClass.Automatic, ApiErrorCode.SYNC_CURSOR_EXPIRED.retryClass)
    }

    @Test
    fun theRedeemAndIdempotencyCodesDecodeWithTheirRetryClasses() {
        mapOf(
            "REDEEM_CODE_EMAIL_MISMATCH" to RetryClass.AfterChange,
            "REDEEM_CODE_USED" to RetryClass.Never,
            "REDEEM_CODE_EXPIRED" to RetryClass.Never,
            "IDEMPOTENCY_REQUEST_IN_PROGRESS" to RetryClass.Automatic,
            "IDEMPOTENCY_KEY_REUSED" to RetryClass.Never,
        ).forEach { (wire, retry) ->
            val code = ApiErrorCode.from(wire)
            assertNotEquals(ApiErrorCode.UNKNOWN, code, wire)
            assertEquals(wire, code.name)
            assertEquals(retry, code.retryClass, wire)
        }
    }
}
