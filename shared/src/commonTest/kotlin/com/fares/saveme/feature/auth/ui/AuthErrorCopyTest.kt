package com.fares.saveme.feature.auth.ui

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.RetryClass
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.error_account_locked
import saveme.shared.generated.resources.error_email_already_registered
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
    fun alreadyRegisteredIsRecognisedAndNeverRetried() {
        assertEquals(ApiErrorCode.EMAIL_ALREADY_REGISTERED, ApiErrorCode.from("EMAIL_ALREADY_REGISTERED"))
        assertEquals(RetryClass.Never, ApiErrorCode.EMAIL_ALREADY_REGISTERED.retryClass)
        assertEquals(Res.string.error_email_already_registered, ApiErrorCode.EMAIL_ALREADY_REGISTERED.message())
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
