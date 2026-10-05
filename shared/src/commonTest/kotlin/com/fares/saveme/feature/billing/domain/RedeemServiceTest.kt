package com.fares.saveme.feature.billing.domain

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.billing.GROUPED_CODE
import com.fares.saveme.feature.billing.RAW_CODE
import com.fares.saveme.feature.billing.RedeemTestEnv
import com.fares.saveme.feature.billing.bodyText
import com.fares.saveme.feature.billing.redeemedGovernsBody
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedeemServiceTest {

    private val attempt = AttemptKey("0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70")

    @Test
    fun successAppliesEntitlementsThenSavesGoverningThenRefreshesAndConfirms() = runTest {
        val t = RedeemTestEnv(backgroundScope)
        t.signIn()
        var seenByRefresh: Pair<String?, String?>? = null
        val entitlements = com.fares.saveme.feature.billing.RecordingEntitlements {
            seenByRefresh = (t.sessions.state.value as AuthState.SignedIn).account.plan to t.env.subscriptions.stored.value?.plan?.name
        }
        val service = RedeemService(t.env.api, t.sessions, t.env.subscriptions, entitlements, backgroundScope)

        val outcome = service.redeem(RAW_CODE, attempt)
        advanceUntilIdle()

        val redeemed = assertIs<RedeemOutcome.Redeemed>(outcome)
        assertEquals("Plus · Yearly", redeemed.confirmation.governingPlanName)
        assertTrue(redeemed.confirmation.governingIsRedeemed)
        assertEquals("Plus · Yearly" to "Plus · Yearly", seenByRefresh, "entitlements and subscription were applied before the re-read")
        assertEquals(1, entitlements.refreshes)
        assertEquals(true, (t.sessions.state.value as AuthState.SignedIn).account.features["sharing.enabled"] == com.fares.saveme.feature.auth.domain.model.FeatureValue.Flag(true))
    }

    @Test
    fun sendsTheGroupedCodeAndTheAttemptKeyOnce() = runTest {
        val t = RedeemTestEnv(backgroundScope)
        t.signIn()
        t.service.redeem(RAW_CODE, attempt)
        val request = t.redeemRequests.single()
        assertEquals(attempt.value, request.headers["Idempotency-Key"])
        assertEquals("""{"code":"$GROUPED_CODE"}""", request.bodyText())
    }

    @Test
    fun aFailedReReadLeavesTheAppliedAnswer() = runTest {
        val t = RedeemTestEnv(backgroundScope)
        t.signIn()
        t.entitlements.outcome = RefreshOutcome.Failed
        assertIs<RedeemOutcome.Redeemed>(t.service.redeem(RAW_CODE, attempt))
        advanceUntilIdle()
        assertEquals("Plus · Yearly", (t.sessions.state.value as AuthState.SignedIn).account.plan)
        assertNotNull(t.env.subscriptions.stored.value)
    }

    @Test
    fun signingOutBeforeTheAnswerAppliesNothing() = runTest {
        val gate = CompletableDeferred<Unit>()
        val t = RedeemTestEnv(backgroundScope) { gate.await(); jsonResponse(200, redeemedGovernsBody()) }
        t.signIn()
        val result = CompletableDeferred<RedeemOutcome>()
        launch { result.complete(t.service.redeem(RAW_CODE, attempt)) }
        runCurrent()
        t.sessions.signOutLocally(SignOutReason.SessionEnded)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(RedeemOutcome.SignedOut, result.await())
        assertNull(t.env.subscriptions.stored.value)
        assertEquals(0, t.entitlements.refreshes)
    }

    @Test
    fun leavingTheScreenAfterTheRequestStartedStillAppliesASuccess() = runTest {
        val gate = CompletableDeferred<Unit>()
        val t = RedeemTestEnv(backgroundScope) { gate.await(); jsonResponse(200, redeemedGovernsBody()) }
        t.signIn()
        val caller = launch { t.service.redeem(RAW_CODE, attempt) }
        runCurrent()
        caller.cancel()
        gate.complete(Unit)
        com.fares.saveme.feature.billing.waitUntil { t.env.subscriptions.stored.value != null }
        advanceUntilIdle()

        assertEquals("Plus · Yearly", (t.sessions.state.value as AuthState.SignedIn).account.plan)
        assertNotNull(t.env.subscriptions.stored.value)
    }

    @Test
    fun offlineIsAnOutcomeAndNothingIsRetried() = runTest {
        val t = RedeemTestEnv(backgroundScope) { throw IOException("no route") }
        t.signIn()
        assertEquals(RedeemOutcome.Offline, t.service.redeem(RAW_CODE, attempt))
        assertEquals(1, t.redeemRequests.size)
    }

    @Test
    fun anUnreadableSuccessBodyIsAnUnexpectedFailureNotACrash() = runTest {
        val t = RedeemTestEnv(backgroundScope) { jsonResponse(200, """{"data":{"nope":1},"meta":{}}""") }
        t.signIn()
        assertEquals(RedeemOutcome.Failed(null), t.service.redeem(RAW_CODE, attempt))
        assertNull(t.env.subscriptions.stored.value)
    }

    @Test
    fun anIncompleteCodeIsNeverSent() = runTest {
        val t = RedeemTestEnv(backgroundScope)
        t.signIn()
        assertFailsWith<IllegalArgumentException> { t.service.redeem("7KQ2", attempt) }
        assertEquals(0, t.redeemRequests.size)
    }

    @Test
    fun signedOutIsReportedWithoutARequest() = runTest {
        val t = RedeemTestEnv(backgroundScope)
        assertEquals(RedeemOutcome.SignedOut, t.service.redeem(RAW_CODE, attempt))
        assertEquals(0, t.redeemRequests.size)
    }
}
