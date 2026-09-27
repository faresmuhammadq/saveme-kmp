package com.fares.saveme.feature.auth.ui.signin

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.auth.domain.FieldError
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.auth.domain.model.AuthField
import com.fares.saveme.feature.auth.domain.model.SignInOutcome
import com.fares.saveme.feature.auth.ui.FieldProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SignInReducerTest {

    private val filled = SignInState(email = "sara@example.com", password = "pw", submitting = true, attemptSessionId = "a1")

    private fun finish(outcome: SignInOutcome) = reduce(filled, SignInChange.Finished(outcome))

    @Test
    fun initialBannerComesFromTheReason() {
        assertEquals(SignInBanner.SessionEnded, SignInState.initial(SignInArgs(reason = SignOutReason.SessionEnded)).banner)
        assertEquals("a@b.co", SignInState.initial(SignInArgs(prefillEmail = "a@b.co")).email)
    }

    @Test
    fun editingEmailStartsANewAttempt() {
        val edited = reduce(filled, SignInChange.EmailEdited("other@example.com"))
        assertNull(edited.attemptSessionId)
        assertEquals("a1", reduce(filled, SignInChange.PasswordEdited("pw2")).attemptSessionId)
    }

    @Test
    fun invalidCredentialsClearsPasswordAndKeepsEmail() {
        val state = finish(SignInOutcome.InvalidCredentials)
        assertEquals(SignInBanner.InvalidCredentials, state.banner)
        assertEquals("", state.password)
        assertEquals("sara@example.com", state.email)
        assertFalse(state.submitting)
        assertEquals("a1", state.attemptSessionId)
    }

    @Test
    fun lockRoundsWaitUpToWholeMinutes() {
        assertEquals(SignInBanner.Locked(15), finish(SignInOutcome.Locked(900)).banner)
        assertEquals(SignInBanner.Locked(2), finish(SignInOutcome.Locked(61)).banner)
        assertEquals(SignInBanner.Locked(null), finish(SignInOutcome.Locked(null)).banner)
    }

    @Test
    fun offlineKeepsTheTypedEmail() {
        val state = finish(SignInOutcome.Offline)
        assertEquals(SignInBanner.Offline, state.banner)
        assertEquals("sara@example.com", state.email)
    }

    @Test
    fun serverFieldErrorsLandOnTheirFields() {
        val state = finish(SignInOutcome.Validation(mapOf(AuthField.Email to "REQUIRED")))
        assertEquals(FieldProblem.Server("REQUIRED"), state.emailError)
        assertEquals(SignInBanner.Validation, state.banner)
    }

    @Test
    fun clientErrorsAndGenericError() {
        val invalid = reduce(SignInState(), SignInChange.ClientInvalid(FieldError.EmailInvalid, FieldError.Required))
        assertEquals(FieldProblem.Client(FieldError.EmailInvalid), invalid.emailError)
        assertEquals(SignInBanner.Error(ApiErrorCode.INTERNAL_ERROR, "r"), finish(SignInOutcome.Error(ApiErrorCode.INTERNAL_ERROR, "r")).banner)
    }
}
