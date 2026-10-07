package com.fares.saveme.feature.billing.domain.play

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.billing.bodyText
import com.fares.saveme.feature.billing.data.play.PlayPurchaseState
import com.fares.saveme.feature.billing.data.play.PlayResult
import com.fares.saveme.feature.billing.play.GooglePlayFixtures
import com.fares.saveme.feature.billing.play.PlayHandler
import com.fares.saveme.feature.billing.play.PlayTestEnv
import com.fares.saveme.feature.billing.play.playPurchase
import com.fares.saveme.feature.billing.waitUntil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Spec 018 US1: the pipe from Google Play to the plan, with a fake port and mock HTTP. Time is the test scheduler's. */
class PlayPurchaseVerifierTest {

    private fun TestScope.env() = PlayTestEnv(backgroundScope, testScheduler)

    private fun PlayTestEnv.keys() = verifyRequests.map { it.headers["Idempotency-Key"] }

    private fun PlayTestEnv.sentTokens() = verifyRequests.map { Json.parseToJsonElement(it.bodyText()).jsonObject["purchase_token"]!!.jsonPrimitive.content }

    /** Lets answers from the mock engine's own threads reach the test scheduler and virtual timers fire, a few rounds. */
    private suspend fun TestScope.drain(rounds: Int = 12) {
        // advanceUntilIdle skips background work (the backoff timers live in backgroundScope), so time is advanced explicitly.
        repeat(rounds) {
            testScheduler.advanceTimeBy(10.minutes)
            runCurrent()
            withContext(Dispatchers.Default) { delay(20) }
        }
        runCurrent()
    }

    private val account get() = com.fares.saveme.feature.billing.USER_ID

    @Test
    fun aPurchasedUnacknowledgedPurchaseBecomesThePlanWithOneKeyedSend() = runTest {
        val t = env()
        t.signIn()
        t.port.own(playPurchase("t-1"))

        t.verifier.pass(PassTrigger.ColdStart)
        runCurrent()

        assertEquals(1, t.verifyRequests.size)
        assertEquals(PlayKeys.verify(account, com.fares.saveme.feature.billing.data.play.PurchaseToken("t-1"), PlayPurchaseState.Purchased), t.keys().single())
        assertEquals("Plus · Monthly", (t.sessions.state.value as AuthState.SignedIn).account.plan)
        assertEquals("google_play", t.env.subscriptions.stored.value?.provider)
        assertEquals(1, t.entitlements.refreshes)
        assertEquals(listOf("connect", "purchases"), t.port.calls, "the app only reads from Google Play: no acknowledge, no consume (FR-002, SC-003)")
    }

    @Test
    fun onlyUnacknowledgedPurchasesAreSent() = runTest {
        val t = env()
        t.signIn()
        t.port.own(playPurchase("done", acknowledged = true), playPurchase("todo", minutesAgo = 5))

        t.verifier.pass(PassTrigger.Foreground)

        assertEquals(listOf("todo"), t.sentTokens())
    }

    @Test
    fun nothingIsGrantedWhenTheServerIsDown() = runTest {
        val t = env()
        t.signIn()
        t.verify = { jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE")) }
        t.port.own(playPurchase("t-1"))
        val before = (t.sessions.state.value as AuthState.SignedIn).account.plan

        t.verifier.pass(PassTrigger.ColdStart)

        assertEquals(before, (t.sessions.state.value as AuthState.SignedIn).account.plan)
        assertNull(t.env.subscriptions.stored.value)
        assertEquals(0, t.entitlements.refreshes)
    }

    @Test
    fun theSameKeyIsUsedAcrossARetryAndAcrossAProcessDeath() = runTest {
        val t = env()
        t.signIn()
        var answers = 0
        t.verify = { if (answers++ == 0) jsonResponse(503, GooglePlayFixtures.unavailable()) else jsonResponse(200, GooglePlayFixtures.active()) }
        t.port.own(playPurchase("t-1"))

        t.verifier.pass(PassTrigger.ColdStart)
        drain()

        assertEquals(2, t.verifyRequests.size, "the retry came after the server's wait")
        assertEquals(1, t.keys().toSet().size, "a retry reuses the key (SC-004)")

        // A killed process: a new verifier with empty notes lists the same purchase and derives the same key.
        t.port.own(playPurchase("t-1"))
        val reborn = PlayPurchaseVerifier(t.port, t.env.api, t.sessions, t.applier, PlayBillingNotes(), {}, PlayPassLock(), backgroundScope, t.clock)
        reborn.pass(PassTrigger.ColdStart)
        runCurrent()
        assertEquals(1, t.keys().toSet().size)
        assertEquals(3, t.verifyRequests.size)
    }

    @Test
    fun aPendingPurchaseIsSentOncePerRunThenAgainWhenPurchasedWithANewKey() = runTest {
        val t = env()
        t.signIn()
        t.verify = { jsonResponse(202, GooglePlayFixtures.pending()) }
        t.port.own(playPurchase("t-1", state = PlayPurchaseState.Pending))

        t.verifier.pass(PassTrigger.ColdStart)
        t.verifier.pass(PassTrigger.Foreground)
        runCurrent()
        assertEquals(1, t.verifyRequests.size, "pending is sent once while pending (FR-013)")
        assertEquals(setOf("plus_monthly"), t.notes.notes.value.pendingProducts)
        assertEquals("Free", (t.sessions.state.value as AuthState.SignedIn).account.plan, "pending is never granted")

        t.verify = { jsonResponse(200, GooglePlayFixtures.active()) }
        t.port.own(playPurchase("t-1", state = PlayPurchaseState.Purchased))
        t.verifier.pass(PassTrigger.Update)
        runCurrent()

        assertEquals(2, t.verifyRequests.size)
        assertNotEquals(t.keys()[0], t.keys()[1])
        assertEquals("Plus · Monthly", (t.sessions.state.value as AuthState.SignedIn).account.plan)
        assertTrue(t.notes.notes.value.pendingProducts.isEmpty())
    }

    @Test
    fun aPurchasedPurchaseTheServerAnswers202IsRecheckedNoSoonerThanAnHourWithAnHourKey() = runTest {
        val t = env()
        t.signIn()
        t.verify = { jsonResponse(202, GooglePlayFixtures.pending()) }
        t.port.own(playPurchase("t-1"))

        t.verifier.pass(PassTrigger.ColdStart)
        t.verifier.pass(PassTrigger.Foreground)
        assertEquals(1, t.verifyRequests.size)
        assertEquals(setOf("plus_monthly"), t.notes.notes.value.pendingProducts, "the pending note holds while Google Play still lists it (FR-033)")

        t.clock.skip(59.minutes)
        t.verifier.pass(PassTrigger.Foreground)
        assertEquals(1, t.verifyRequests.size, "not yet an hour")

        t.clock.skip(2.minutes)
        t.verifier.pass(PassTrigger.Foreground)
        assertEquals(2, t.verifyRequests.size)
        assertNotEquals(t.keys()[0], t.keys()[1], "the recheck key includes the hour")

        t.clock.skip(1.hours)
        t.verifier.pass(PassTrigger.Foreground)
        assertEquals(3, t.verifyRequests.size)
        assertNotEquals(t.keys()[1], t.keys()[2])
    }

    @Test
    fun aServiceFailureIsRetriedWithTheSameKeyAtMostSixTimesPerRun() = runTest {
        val t = env()
        t.signIn()
        t.verify = { jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE")) }
        t.port.own(playPurchase("t-1"))

        t.verifier.pass(PassTrigger.ColdStart)
        drain(rounds = 30)

        assertEquals(6, t.verifyRequests.size)
        assertEquals(1, t.keys().toSet().size)
        drain(rounds = 5)
        assertEquals(6, t.verifyRequests.size, "after the sixth send it waits for the next trigger")

        t.verifier.pass(PassTrigger.Foreground)
        assertEquals(7, t.verifyRequests.size, "a new trigger starts again")
    }

    @Test
    fun offlineAndRateLimitedAreRetriedAfterTheirWait() = runTest {
        val t = env()
        t.signIn()
        var calls = 0
        t.verify = {
            when (calls++) {
                0 -> throw IOException("no route")
                1 -> jsonResponse(429, GooglePlayFixtures.rateLimited())
                else -> jsonResponse(200, GooglePlayFixtures.active())
            }
        }
        t.port.own(playPurchase("t-1"))

        t.verifier.pass(PassTrigger.ColdStart)
        drain(rounds = 30)

        assertEquals(3, t.verifyRequests.size)
        assertEquals(1, t.keys().toSet().size)
        assertEquals("Plus · Monthly", (t.sessions.state.value as AuthState.SignedIn).account.plan)
    }

    @Test
    fun anInProgressAnswerIsRetriedWithTheSameKey() = runTest {
        val t = env()
        t.signIn()
        var calls = 0
        t.verify = { if (calls++ == 0) jsonResponse(409, GooglePlayFixtures.inProgress(), "Retry-After" to "3") else jsonResponse(200, GooglePlayFixtures.active()) }
        t.port.own(playPurchase("t-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        drain(rounds = 20)
        assertEquals(2, t.verifyRequests.size)
        assertEquals(1, t.keys().toSet().size)
    }

    @Test
    fun anotherAccountsPurchaseIsExplainedOnceAndNotResentByTriggers() = runTest {
        val t = env()
        t.signIn()
        t.verify = { jsonResponse(409, GooglePlayFixtures.alreadyLinked()) }
        t.port.own(playPurchase("t-1"))

        t.verifier.pass(PassTrigger.ColdStart)
        t.verifier.pass(PassTrigger.Foreground)
        t.verifier.pass(PassTrigger.PlanScreen)
        runCurrent()

        assertEquals(1, t.verifyRequests.size)
        assertTrue(t.notes.notes.value.anotherAccount)
        assertEquals("Free", (t.sessions.state.value as AuthState.SignedIn).account.plan)
    }

    @Test
    fun anUnverifiablePurchaseIsNotResentInTheRun() = runTest {
        val t = env()
        t.signIn()
        t.verify = { jsonResponse(422, GooglePlayFixtures.notVerified()) }
        t.port.own(playPurchase("t-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        t.verifier.pass(PassTrigger.Foreground)
        assertEquals(1, t.verifyRequests.size)
        assertFalse(t.notes.notes.value.anotherAccount)
    }

    @Test
    fun anUnknownProductReloadsTheCatalogueOnceAndIsNotResent() = runTest {
        val t = env()
        t.signIn()
        t.verify = { jsonResponse(422, GooglePlayFixtures.unknownProduct()) }
        t.port.own(playPurchase("t-1"), playPurchase("t-2", productId = "plus_other", minutesAgo = 1))
        t.verifier.pass(PassTrigger.ColdStart)
        runCurrent()
        t.verifier.pass(PassTrigger.Foreground)
        runCurrent()
        assertEquals(2, t.verifyRequests.size)
        assertEquals(1, t.catalogueReloads)
    }

    @Test
    fun aReusedKeyIsRefusedForTheRunAndNotRetried() = runTest {
        val t = env()
        t.signIn()
        t.verify = { jsonResponse(409, GooglePlayFixtures.keyReused()) }
        t.port.own(playPurchase("t-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        drain(rounds = 5)
        t.verifier.pass(PassTrigger.Foreground)
        assertEquals(1, t.verifyRequests.size)
    }

    @Test
    fun anEndedSessionOrAnUpdateRequiredAnswerStopsThePass() = runTest {
        listOf(jsonResponse401() to 401, jsonResponse426() to 426).forEach { (answer, _) ->
            val t = env()
            t.signIn()
            t.verify = answer
            t.port.own(playPurchase("t-1"), playPurchase("t-2", minutesAgo = 2))
            t.verifier.pass(PassTrigger.ColdStart)
            runCurrent()
            assertEquals(1, t.verifyRequests.size, "the second purchase is not sent")
        }
    }

    private fun jsonResponse401(): PlayHandler = { jsonResponse(401, GooglePlayFixtures.unauthenticated()) }
    private fun jsonResponse426(): PlayHandler = { jsonResponse(426, GooglePlayFixtures.upgradeRequired()) }

    @Test
    fun aTriggerDuringAPassWaitsForItAndRunsOnceMoreWhenSomethingChanged() = runTest {
        val t = env()
        t.signIn()
        val gate = CompletableDeferred<Unit>()
        t.verify = { gate.await(); jsonResponse(200, GooglePlayFixtures.active()) }
        t.port.own(playPurchase("t-1"))

        val first = launch { t.verifier.pass(PassTrigger.ColdStart) }
        waitUntil { t.verifyRequests.size == 1 }
        // The first answer is in flight. A foreground trigger arrives, and Google Play now lists a second purchase.
        t.port.own(playPurchase("t-1", acknowledged = true), playPurchase("t-2", minutesAgo = 3))
        val second = launch { t.verifier.pass(PassTrigger.Foreground) }
        runCurrent()
        assertEquals(1, t.verifyRequests.size, "one pass at a time (FR-011)")
        gate.complete(Unit)
        first.join()
        second.join()

        assertEquals(listOf("t-1", "t-2"), t.sentTokens(), "the waiting trigger ran once more and found the new purchase")
    }

    @Test
    fun anAnswerForAPersonWhoSignedOutMeanwhileIsDiscarded() = runTest {
        val t = env()
        t.signIn()
        val gate = CompletableDeferred<Unit>()
        t.verify = { gate.await(); jsonResponse(200, GooglePlayFixtures.active()) }
        t.port.own(playPurchase("t-1"))
        val pass = launch { t.verifier.pass(PassTrigger.ColdStart) }
        waitUntil { t.verifyRequests.size == 1 }

        t.sessions.signOutLocally(SignOutReason.SessionEnded)
        gate.complete(Unit)
        pass.join()

        assertNull(t.env.subscriptions.stored.value, "nothing of the answer was applied")
        assertEquals(0, t.entitlements.refreshes)
    }

    @Test
    fun anUnacknowledgedAnswerChangesNothingOnThePhone() = runTest {
        val t = env()
        t.signIn()
        t.verify = { jsonResponse(200, GooglePlayFixtures.ackPending()) }
        t.port.own(playPurchase("t-1", productId = "plus_yearly"))
        t.verifier.pass(PassTrigger.ColdStart)
        runCurrent()
        assertEquals("Plus · Yearly", (t.sessions.state.value as AuthState.SignedIn).account.plan)
        assertTrue(t.notes.notes.value.pendingProducts.isEmpty())
        assertTrue(t.notes.notes.value.refused.isEmpty())
        assertEquals(1, t.verifyRequests.size)
    }

    @Test
    fun holdPausedAndTestComeFromTheAnswerForThisRun() = runTest {
        val t = env()
        t.signIn()
        t.verify = { jsonResponse(200, GooglePlayFixtures.heldOrPaused("on_hold")) }
        t.port.own(playPurchase("t-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        assertEquals(setOf("plus_monthly"), t.notes.notes.value.heldProducts)

        t.verify = { jsonResponse(200, GooglePlayFixtures.heldOrPaused("paused")) }
        t.port.own(playPurchase("t-2", minutesAgo = 4))
        t.verifier.pass(PassTrigger.Foreground)
        assertTrue(t.notes.notes.value.heldProducts.isEmpty(), "a later answer for the product replaces the hold")
        assertEquals(setOf("plus_monthly"), t.notes.notes.value.pausedProducts)

        t.verify = { jsonResponse(200, GooglePlayFixtures.active(test = true)) }
        t.port.own(playPurchase("t-3", minutesAgo = 6))
        t.verifier.pass(PassTrigger.Foreground)
        runCurrent()
        assertEquals(setOf("plus_monthly"), t.notes.notes.value.testProducts)
    }

    @Test
    fun aChangedPlanAnnouncesItselfOnlyWhileThePlanScreenShows() = runTest {
        val t = env()
        t.signIn()
        t.port.own(playPurchase("t-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        runCurrent()
        assertNull(t.notes.consumeActivated(), "no Plan screen, no announcement")

        val shown = env()
        shown.signIn()
        shown.notes.planScreenOpened()
        shown.port.own(playPurchase("t-1"))
        shown.verifier.pass(PassTrigger.PlanScreen)
        runCurrent()
        assertEquals("Plus · Monthly", shown.notes.consumeActivated()?.planName)
    }

    @Test
    fun theSecondSendOfTheSamePlanDoesNotAnnounceAgain() = runTest {
        val t = env()
        t.signIn()
        t.notes.planScreenOpened()
        t.port.own(playPurchase("t-1"))
        t.verifier.pass(PassTrigger.PlanScreen)
        t.notes.consumeActivated()
        t.verifier.pass(PassTrigger.PlanScreen)
        runCurrent()
        assertNull(t.notes.consumeActivated())
    }

    @Test
    fun nothingIsSentWhenGooglePlayIsUnavailableOrCannotListOrNobodyIsSignedIn() = runTest {
        val t = env()
        t.port.own(playPurchase("t-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        assertEquals(0, t.verifyRequests.size, "signed out: no trigger sends anything")
        assertTrue(t.port.calls.isEmpty())

        t.signIn()
        t.port.connection = com.fares.saveme.feature.billing.data.play.PlayConnection.Unavailable(com.fares.saveme.feature.billing.data.play.PlayUnavailable.NotWired)
        t.verifier.pass(PassTrigger.ColdStart)
        assertEquals(0, t.verifyRequests.size)

        t.port.connection = com.fares.saveme.feature.billing.data.play.PlayConnection.Ready
        t.port.purchasesResult = PlayResult.Failed(com.fares.saveme.feature.billing.data.play.PlayError.Network)
        t.verifier.pass(PassTrigger.ColdStart)
        assertEquals(0, t.verifyRequests.size)
    }

    @Test
    fun signOutCancelsTheBackoffTimersAndClearsEveryNote() = runTest {
        val t = env()
        t.signIn()
        t.verify = { jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE")) }
        t.port.own(playPurchase("t-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        assertEquals(1, t.verifyRequests.size)

        val prices = PlayPriceLoader(t.port, t.clock)
        PlayBillingCleaner(t.notes, prices) { t.verifier.cancelTimers() }.clear()
        drain(rounds = 6)

        assertEquals(1, t.verifyRequests.size, "the timer was cancelled")
        assertEquals(PlayNotes(), t.notes.notes.value)
    }
}
