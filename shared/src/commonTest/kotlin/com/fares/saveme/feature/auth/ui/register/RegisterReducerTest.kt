package com.fares.saveme.feature.auth.ui.register

import com.fares.saveme.feature.auth.domain.model.RegisterOutcome
import com.fares.saveme.feature.auth.ui.FormBanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RegisterReducerTest {

    @Test
    fun editingTheEmailClearsTheAlreadyRegisteredNotice() {
        val state = reduce(RegisterState(email = "a@x.io", alreadyRegistered = true), RegisterChange.EmailEdited("b@x.io"))
        assertFalse(state.alreadyRegistered)
    }

    @Test
    fun rateLimitShowsMinutes() {
        val state = reduce(RegisterState(submitting = true), RegisterChange.Finished(RegisterOutcome.RateLimited(601)))
        assertEquals(FormBanner.RateLimited(11), state.banner)
        assertFalse(state.submitting)
    }

    @Test
    fun startingClearsOldMessages() {
        val state = reduce(RegisterState(banner = FormBanner.Offline, alreadyRegistered = true), RegisterChange.Started)
        assertEquals(null, state.banner)
        assertFalse(state.alreadyRegistered)
    }
}
