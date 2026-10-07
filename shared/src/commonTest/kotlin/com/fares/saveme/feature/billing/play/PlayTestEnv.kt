package com.fares.saveme.feature.billing.play

import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.billing.BillingRoutes
import com.fares.saveme.feature.billing.BillingTestEnv
import com.fares.saveme.feature.billing.RecordingEntitlements
import com.fares.saveme.feature.billing.USER_ID
import com.fares.saveme.feature.billing.domain.BillingAnswerApplier
import com.fares.saveme.feature.billing.domain.play.PlayBillingNotes
import com.fares.saveme.feature.billing.domain.play.PlayPassLock
import com.fares.saveme.feature.billing.domain.play.PlayPurchaseVerifier
import com.fares.saveme.feature.billing.domain.play.PlayRestoreService
import com.fares.saveme.feature.billing.endsWithPath
import com.fares.saveme.feature.billing.signIn
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** The test scheduler's virtual time as a [Clock], so backoff `delay`s and the pass logic agree; [skip] moves it on its own. */
class SchedulerClock(private val scheduler: TestCoroutineScheduler, private val start: Instant = Instant.parse("2026-10-02T10:00:00Z")) : Clock {
    private var extra: Duration = Duration.ZERO
    override fun now(): Instant = start + scheduler.currentTime.milliseconds + extra

    fun skip(by: Duration) {
        extra += by
    }
}

typealias PlayHandler = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData

/** A signed-in account whose Google Play endpoints answer whatever [verify] and [restore] say, over a fake port. */
class PlayTestEnv(
    scope: CoroutineScope,
    scheduler: TestCoroutineScheduler,
    val port: FakePlayBillingClient = FakePlayBillingClient(),
) {
    var verify: PlayHandler = { jsonResponse(200, GooglePlayFixtures.active()) }
    var restore: PlayHandler = { jsonResponse(200, GooglePlayFixtures.restored()) }
    private val routes = BillingRoutes()
    val env = BillingTestEnv { request ->
        when {
            request.endsWithPath("/billing/google/purchases") -> verify(this, request)
            request.endsWithPath("/billing/google/restore") -> restore(this, request)
            else -> with(routes) { answer(request) }
        }
    }
    val clock = SchedulerClock(scheduler)
    val sessions = env.sessionManager(scope)
    val entitlements = RecordingEntitlements()
    val notes = PlayBillingNotes()
    val lock = PlayPassLock()
    val applier = BillingAnswerApplier(sessions, env.subscriptions, entitlements, scope)
    var catalogueReloads = 0
    val verifier = PlayPurchaseVerifier(port, env.api, sessions, applier, notes, { catalogueReloads++ }, lock, scope, clock)
    val restorer = PlayRestoreService(port, env.api, sessions, applier, notes, lock, scope)

    val userId: String = USER_ID

    suspend fun signIn() = env.signIn(sessions)

    val verifyRequests: List<HttpRequestData> get() = snapshot().filter { it.endsWithPath("/billing/google/purchases") }
    val restoreRequests: List<HttpRequestData> get() = snapshot().filter { it.endsWithPath("/billing/google/restore") }

    /** The engine records requests from its own thread, so a copy can race with an append: retry until one copy is whole. */
    private fun snapshot(): List<HttpRequestData> {
        repeat(20) {
            try {
                return env.requests.toList()
            } catch (_: ConcurrentModificationException) {
                // appended while copying
            }
        }
        return emptyList()
    }
}
