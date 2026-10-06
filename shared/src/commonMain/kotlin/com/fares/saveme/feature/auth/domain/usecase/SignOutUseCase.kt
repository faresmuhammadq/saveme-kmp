package com.fares.saveme.feature.auth.domain.usecase

import com.fares.saveme.core.session.SignOutParticipant
import com.fares.saveme.feature.auth.data.AuthRepository
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.SignOutReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tells the server best-effort, then always clears this phone: offline, a 401 or a 500 must never
 * leave the person signed in locally (spec 001 FR-024). Participants (spec 017) run first, while the session is
 * still stored, inside one bound; one that fails or hangs never keeps the rest from running.
 */
class SignOutUseCase(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager,
    /** Null waits for the server without a timer (tests on virtual time). */
    private val serverTimeout: Duration? = 5.seconds,
    /** Resolved on use, so a feature's graph is never built while this use case is (spec 006 lesson). */
    private val participants: () -> List<SignOutParticipant> = { emptyList() },
    /** Null waits for the participants without a timer. */
    private val participantTimeout: Duration? = 2.seconds,
) {
    suspend operator fun invoke() {
        runParticipants()
        if (serverTimeout == null) repository.signOut() else withTimeoutOrNull(serverTimeout) { repository.signOut() }
        sessionManager.signOutLocally(SignOutReason.UserSignedOut)
    }

    private suspend fun runParticipants() {
        val all = participants()
        if (all.isEmpty()) return
        val work: suspend () -> Unit = {
            all.forEach { participant ->
                try {
                    participant.beforeSignOut()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // One failing participant must not skip the others or the sign-out.
                }
            }
        }
        if (participantTimeout == null) work() else withTimeoutOrNull(participantTimeout) { work() }
    }
}
