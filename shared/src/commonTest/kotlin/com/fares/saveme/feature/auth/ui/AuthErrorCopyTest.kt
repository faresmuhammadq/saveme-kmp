package com.fares.saveme.feature.auth.ui

import com.fares.saveme.core.network.ApiErrorCode
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.error_account_locked
import saveme.shared.generated.resources.error_generic_reference
import saveme.shared.generated.resources.error_invalid_credentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthErrorCopyTest {

    @Test
    fun everyCodeHasItsOwnCopy() {
        ApiErrorCode.entries.forEach { code -> code.message() } // total mapping: compiles only if exhaustive
        assertEquals(Res.string.error_invalid_credentials, ApiErrorCode.INVALID_CREDENTIALS.message())
        assertEquals(Res.string.error_generic_reference, ApiErrorCode.UNKNOWN.message())
    }

    @Test
    fun waitTimesRoundUpToMinutes() {
        assertEquals(15, minutesToWait(900))
        assertEquals(1, minutesToWait(5))
        assertNull(minutesToWait(null))
    }

    @Test
    fun lockedCopyIsPlural() {
        assertEquals("error_account_locked", Res.plurals.error_account_locked.key)
    }
}
