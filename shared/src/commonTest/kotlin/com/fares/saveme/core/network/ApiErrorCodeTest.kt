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
}
