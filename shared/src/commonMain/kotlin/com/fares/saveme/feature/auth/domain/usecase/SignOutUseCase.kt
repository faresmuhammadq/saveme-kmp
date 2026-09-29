package com.fares.saveme.feature.auth.domain.usecase

import com.fares.saveme.feature.auth.data.AuthRepository
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.SignOutReason
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tells the server best-effort, then always clears this phone: offline, a 401 or a 500 must never
 * leave the person signed in locally (spec 001 FR-024).
 */
class SignOutUseCase(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager,
    /** Null waits for the server without a timer (tests on virtual time). */
    private val serverTimeout: Duration? = 5.seconds,
) {
    suspend operator fun invoke() {
        if (serverTimeout == null) repository.signOut() else withTimeoutOrNull(serverTimeout) { repository.signOut() }
        sessionManager.signOutLocally(SignOutReason.UserSignedOut)
    }
}
