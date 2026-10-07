package com.fares.saveme.feature.billing.play

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.billing.BillingTestEnv
import com.fares.saveme.feature.billing.Captured
import com.fares.saveme.feature.billing.EntitlementSetFixtures
import com.fares.saveme.feature.billing.bodyText
import com.fares.saveme.feature.billing.data.play.PlayPurchaseState
import com.fares.saveme.feature.billing.data.play.PurchaseToken
import com.fares.saveme.feature.billing.domain.CurrentPlan
import com.fares.saveme.feature.billing.domain.EntitlementSet
import com.fares.saveme.feature.billing.domain.model.BillingPeriod
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import com.fares.saveme.feature.billing.domain.model.SubscriptionStatus
import com.fares.saveme.feature.billing.domain.model.toDomain
import com.fares.saveme.feature.billing.domain.play.PassTrigger
import com.fares.saveme.feature.billing.domain.play.PlayKeys
import com.fares.saveme.feature.billing.domain.play.RestoreMessagePart
import com.fares.saveme.feature.billing.domain.play.RestoreOutcome
import com.fares.saveme.feature.billing.domain.play.RestoreResult
import com.fares.saveme.feature.billing.domain.play.RestoreTrigger
import com.fares.saveme.feature.billing.ui.plan.PaymentProblemUi
import com.fares.saveme.feature.billing.ui.plan.PlanIntent
import com.fares.saveme.feature.billing.ui.plan.PlanState
import com.fares.saveme.feature.billing.ui.plan.PlanStatusUi
import com.fares.saveme.feature.billing.ui.plan.PlanViewModel
import com.fares.saveme.feature.billing.ui.plan.RestoreRefusal
import com.fares.saveme.feature.billing.ui.plan.RestoreUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The answers captured from the live local backend (CapturedPlayResponses, specs/018-google-play/qa-report.md, T050) replayed
 * through the real `ApiClient` -> `BillingApi` -> `PlayPurchaseVerifier` / `PlayRestoreService` -> `BillingAnswerApplier` -> the
 * Plan screen's state. The port is the fake: no Google Play is involved anywhere.
 */
class GooglePlayLiveReplayTest {

    private val monthlyId = "01a113ec-b118-715d-8416-8ab0492d9816"
    private val yearlyId = "01a113ec-b122-7384-8a25-20a76b5f5c49"
    private val freeId = "01a113ec-b10e-7248-aa4b-0d1580b50e8e"

    private fun TestScope.env() = PlayTestEnv(backgroundScope, testScheduler)

    private fun PlayTestEnv.answerVerify(answer: Captured) {
        verify = { jsonResponse(answer.status, answer.body, *answer.headers.toTypedArray()) }
    }

    private fun PlayTestEnv.answerRestore(answer: Captured) {
        restore = { jsonResponse(answer.status, answer.body, *answer.headers.toTypedArray()) }
    }

    private val PlayTestEnv.account get() = (sessions.state.value as AuthState.SignedIn).account

    /** The Plan screen's state as the screen's flows would build it from what the answers left on the phone. */
    private fun PlayTestEnv.planState(): PlanState {
        val account = account
        val catalogue = listOf(
            CataloguePlan(freeId, "Free", BillingPeriod.Lifetime, true, EntitlementSetFixtures.free, null),
            CataloguePlan(monthlyId, "Plus · Monthly", BillingPeriod.Monthly, false, EntitlementSetFixtures.plus, "plus_monthly"),
            CataloguePlan(yearlyId, "Plus · Yearly", BillingPeriod.Yearly, false, EntitlementSetFixtures.plus, "plus_yearly"),
        )
        return PlanState(
            plan = CurrentPlan(account.planId, account.plan, EntitlementSet(account.features)),
            subscription = env.subscriptions.stored.value?.toDomain(),
            settled = true,
            plans = catalogue,
            timeZoneId = "Africa/Cairo",
            playSupported = true,
            notes = notes.notes.value,
        )
    }

    private fun PlayTestEnv.sentBodies() = verifyRequests.map { Json.parseToJsonElement(it.bodyText()).jsonObject }

    private fun PlayTestEnv.restoreTokens(index: Int): List<String> =
        Json.parseToJsonElement(restoreRequests[index].bodyText()).jsonObject["purchases"]!!.jsonArray
            .map { it.jsonObject["purchase_token"]!!.jsonPrimitive.content }

    // --- keys: the app's gp1 keys against the server's own replay behaviour ------------------------------------

    @Test
    fun theAppsKeysAreExactlyTheKeysTheServerAcceptedAndReplayed() {
        val active = PlayKeys.verify(CapturedPlayInputs.USER_A, PurchaseToken(CapturedPlayInputs.TOKEN_ACTIVE), PlayPurchaseState.Purchased)
        assertEquals(CapturedPlayInputs.KEY_ACTIVE, active)
        assertEquals(68, active.length)
        assertTrue(Regex("^gp1:[0-9a-f]{64}$").matches(active))
        // The same key sent again was answered from the server's store (Idempotency-Replayed), body for body.
        assertEquals("true", CapturedPlayResponses.verifyReplay.headers.single { it.first == "Idempotency-Replayed" }.second)
        assertEquals(CapturedPlayResponses.verifyActive.body.stripOrder(), CapturedPlayResponses.verifyReplay.body.stripOrder())

        val pending = PlayKeys.verify(CapturedPlayInputs.USER_C, PurchaseToken(CapturedPlayInputs.TOKEN_PENDING_THEN_PURCHASED), PlayPurchaseState.Pending)
        val purchased = PlayKeys.verify(CapturedPlayInputs.USER_C, PurchaseToken(CapturedPlayInputs.TOKEN_PENDING_THEN_PURCHASED), PlayPurchaseState.Purchased)
        assertEquals(CapturedPlayInputs.KEY_PENDING, pending)
        assertEquals(CapturedPlayInputs.KEY_PURCHASED_AFTER_PENDING, purchased)
        assertNotEquals(pending, purchased, "a changed Google Play state is a changed key: the server would replay the 202 for the old one")
        assertEquals(
            CapturedPlayInputs.KEY_RECHECK_HOUR_OF_C,
            PlayKeys.verify(CapturedPlayInputs.USER_C, PurchaseToken(CapturedPlayInputs.TOKEN_PENDING_THEN_PURCHASED), PlayPurchaseState.Purchased, CapturedPlayInputs.RECHECK_HOUR_OF_C),
        )
        assertEquals(
            CapturedPlayInputs.KEY_SAME_TOKEN_RECHECK_HOUR,
            PlayKeys.verify(CapturedPlayInputs.USER_A, PurchaseToken(CapturedPlayInputs.TOKEN_ACTIVE), PlayPurchaseState.Purchased, CapturedPlayInputs.RECHECK_HOUR_OF_A),
        )
    }

    /** `Idempotency-Replayed` answers keep the first answer even when Google Play's state moved on: that is why the state is in the key. */
    @Test
    fun aReplayedKeyKeepsTheFirstAnswerEvenAfterGooglePlayChanged() = runTest {
        val replay = CapturedPlayResponses.verifyReplayAfterGoogleChanged
        assertEquals(200, replay.status)
        assertTrue(replay.headers.any { it.first == "Idempotency-Replayed" })
        assertTrue(replay.body.contains(""""state":"active"""") || replay.body.contains("\"state\": \"active\""), "answers the original purchased state, not on_hold")
    }

    // --- US1: verify ----------------------------------------------------------------------------------------

    @Test
    fun theLiveActiveAnswerBecomesPlusAndTheTestPurchaseIsNoted() = runTest {
        val t = env()
        t.signIn()
        t.answerVerify(CapturedPlayResponses.verifyActive)
        t.port.own(playPurchase(CapturedPlayInputs.TOKEN_ACTIVE))

        t.verifier.pass(PassTrigger.ColdStart)
        runCurrent()

        val request = t.verifyRequests.single()
        assertEquals(PlayKeys.verify(t.userId, PurchaseToken(CapturedPlayInputs.TOKEN_ACTIVE), PlayPurchaseState.Purchased), request.headers["Idempotency-Key"])
        assertEquals(setOf("purchase_token", "product_id"), t.sentBodies().single().keys)
        assertEquals("Plus · Monthly", t.account.plan)
        assertEquals("google_play", t.env.subscriptions.stored.value?.provider)
        assertEquals(1, t.entitlements.refreshes, "the C3 re-read follows the applied answer")
        assertEquals(setOf("plus_monthly"), t.notes.notes.value.testProducts, "the fake script marks the purchase a test one")
        val state = t.planState()
        assertTrue(state.showManage)
        assertTrue(state.showTestLabel)
        assertTrue(state.problems.isEmpty())
        assertEquals(PlanStatusUi.Renews(Instant.parse("2026-11-06T01:36:37.888549Z")), state.current?.status)
    }

    @Test
    fun aSameKeyReplayAndASameTokenNewKeyShowTheSamePlan() = runTest {
        listOf(CapturedPlayResponses.verifyReplay, CapturedPlayResponses.verifySameTokenNewKey).forEach { answer ->
            val t = env()
            t.signIn()
            t.answerVerify(answer)
            t.port.own(playPurchase(CapturedPlayInputs.TOKEN_ACTIVE))
            t.verifier.pass(PassTrigger.Foreground)
            runCurrent()
            assertEquals("Plus · Monthly", t.account.plan)
            assertEquals(1, t.verifyRequests.size)
        }
    }

    @Test
    fun theLivePendingAnswerGrantsNothingAndThePurchasedOneLaterGrantsPlus() = runTest {
        val t = env()
        t.signIn()
        t.answerVerify(CapturedPlayResponses.verifyPending)
        t.port.own(playPurchase("pend-1", state = PlayPurchaseState.Pending))

        t.verifier.pass(PassTrigger.ColdStart)
        t.verifier.pass(PassTrigger.Foreground)
        runCurrent()

        assertEquals(1, t.verifyRequests.size, "pending is sent once per run")
        assertEquals("Free", t.account.plan, "nothing is granted for a pending purchase")
        assertEquals(setOf("plus_monthly"), t.notes.notes.value.pendingProducts)
        assertTrue(t.planState().showPending)
        assertNull(t.env.subscriptions.stored.value?.status, "the live 202 carries the unchanged free subscription")

        t.answerVerify(CapturedPlayResponses.verifyPurchasedAfterPending)
        t.port.own(playPurchase("pend-1", state = PlayPurchaseState.Purchased))
        t.verifier.pass(PassTrigger.Update)
        runCurrent()

        assertEquals(2, t.verifyRequests.size)
        assertNotEquals(t.verifyRequests[0].headers["Idempotency-Key"], t.verifyRequests[1].headers["Idempotency-Key"])
        assertEquals("Plus · Monthly", t.account.plan)
        assertTrue(t.notes.notes.value.pendingProducts.isEmpty())
        assertFalse(t.planState().showPending)
    }

    @Test
    fun theLiveRecheckAnswerForAHourKeyIsAnOrdinaryGrant() = runTest {
        val t = env()
        t.signIn()
        t.answerVerify(CapturedPlayResponses.verifyRecheckHourKey)
        t.port.own(playPurchase("pend-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        runCurrent()
        assertEquals("Plus · Monthly", t.account.plan)
    }

    // --- US4: the states the server reports, and what the Plan screen makes of them -------------------------------

    @Test
    fun graceShowsTheWarningWithTheGraceEnd() = runTest {
        val t = env()
        t.signIn()
        t.answerVerify(CapturedPlayResponses.verifyGrace)
        t.port.own(playPurchase("e-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        runCurrent()
        val state = t.planState()
        assertEquals(SubscriptionStatus.Grace, state.subscription?.status)
        assertEquals(PaymentProblemUi.Grace(Instant.parse("2026-10-10T01:36:52.523762Z"), "Plus · Monthly"), state.problems.single())
        assertIs<PlanStatusUi.Grace>(state.current?.status)
        assertEquals("Plus · Monthly", t.account.plan, "access holds during grace")
    }

    @Test
    fun aCanceledSubscriptionKeepsPlusUntilItsEndWithoutRenewal() = runTest {
        val t = env()
        t.signIn()
        t.answerVerify(CapturedPlayResponses.verifyCanceled)
        t.port.own(playPurchase("e-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        runCurrent()
        val state = t.planState()
        assertEquals("Plus · Monthly", t.account.plan)
        assertEquals(PlanStatusUi.Ends(Instant.parse("2026-10-10T01:36:53.542522Z")), state.current?.status)
        assertTrue(state.problems.isEmpty())
        assertTrue(state.showManage)
    }

    @Test
    fun anOnHoldAnswerDropsToFreeAndShowsTheHoldNoteWithFixPaymentForThisRunOnly() = runTest {
        val t = env()
        t.signIn()
        t.answerVerify(CapturedPlayResponses.verifyOnHold)
        t.port.own(playPurchase("e-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        runCurrent()
        val state = t.planState()
        assertEquals("Free", t.account.plan, "the server reports hold as no plan")
        assertNull(state.subscription?.status)
        assertEquals(PaymentProblemUi.Hold("Plus · Monthly", "plus_monthly"), state.problems.single())
        assertFalse(state.showManage, "no governing Google Play subscription is readable (backend gap 1)")
    }

    @Test
    fun aPausedAnswerDropsToFreeAndOffersManageInGooglePlay() = runTest {
        val t = env()
        t.signIn()
        t.answerVerify(CapturedPlayResponses.verifyPaused)
        t.port.own(playPurchase("e-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        runCurrent()
        assertEquals("Free", t.account.plan)
        assertEquals(PaymentProblemUi.Paused("plus_monthly"), t.planState().problems.single())
    }

    @Test
    fun anExpiredOrRevokedAnswerShowsTheFreePlanAndNoNoteBlamingAnyone() = runTest {
        listOf(CapturedPlayResponses.verifyExpired, CapturedPlayResponses.verifyRevoked).forEach { answer ->
            val t = env()
            t.signIn()
            t.answerVerify(answer)
            t.port.own(playPurchase("e-1"))
            t.verifier.pass(PassTrigger.ColdStart)
            runCurrent()
            val state = t.planState()
            assertEquals("Free", t.account.plan)
            assertTrue(state.problems.isEmpty())
            assertFalse(state.showPending)
            assertFalse(state.showAnotherAccount)
            assertFalse(state.showManage)
            assertEquals(1, t.entitlements.refreshes)
        }
    }

    @Test
    fun aManualSubscriptionThatGovernsKeepsTheYearlyPlanAndNoGooglePlayLinks() = runTest {
        val t = env()
        t.signIn()
        t.answerVerify(CapturedPlayResponses.verifyUnderManual)
        t.port.own(playPurchase("f-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        runCurrent()
        val state = t.planState()
        assertEquals("Plus · Yearly", t.account.plan, "the answer's entitlements are the governing subscription's")
        assertEquals("manual", t.env.subscriptions.stored.value?.provider)
        assertFalse(state.showManage)
        assertFalse(state.showTestLabel)
        assertTrue(state.problems.isEmpty())
    }

    // --- US3 and the refusals of verify -----------------------------------------------------------------------------

    @Test
    fun bothLiveAlreadyLinkedAnswersExplainOnceAndAreNotResent() = runTest {
        listOf(CapturedPlayResponses.refusedAlreadyLinkedFirstLink, CapturedPlayResponses.refusedAlreadyLinkedByReference).forEach { answer ->
            val t = env()
            t.signIn()
            t.answerVerify(answer)
            t.port.own(playPurchase("b-1"))
            t.verifier.pass(PassTrigger.ColdStart)
            t.verifier.pass(PassTrigger.Foreground)
            runCurrent()
            assertEquals(1, t.verifyRequests.size)
            assertTrue(t.planState().showAnotherAccount)
            assertEquals("Free", t.account.plan)
            assertFalse(answer.body.contains("@"), "the answer names nobody")
        }
    }

    @Test
    fun theLiveNotVerifiedAnswerIsNotResentAndNothingIsGranted() = runTest {
        val t = env()
        t.signIn()
        t.answerVerify(CapturedPlayResponses.refusedNotVerified)
        t.port.own(playPurchase("u-1"))
        t.verifier.pass(PassTrigger.ColdStart)
        t.verifier.pass(PassTrigger.Foreground)
        runCurrent()
        assertEquals(1, t.verifyRequests.size)
        assertEquals("Free", t.account.plan)
        assertFalse(t.planState().showAnotherAccount)
        assertEquals(0, t.entitlements.refreshes)
    }

    @Test
    fun theLiveUnknownProductAnswerReloadsTheCatalogueOnceAndIsNotResent() = runTest {
        val t = env()
        t.signIn()
        t.answerVerify(CapturedPlayResponses.refusedUnknownProduct)
        t.port.own(playPurchase("p-1", productId = "no_such_product"))
        t.verifier.pass(PassTrigger.ColdStart)
        runCurrent()
        t.verifier.pass(PassTrigger.Foreground)
        runCurrent()
        assertEquals(1, t.verifyRequests.size)
        assertEquals(1, t.catalogueReloads)
    }

    @Test
    fun theLiveKeyRefusalsAreNeverRetriedInTheRunButAMissingKeyIsRetriedByTheNextTrigger() = runTest {
        val reused = env().apply { signIn() }
        reused.answerVerify(CapturedPlayResponses.refusedKeyReused)
        reused.port.own(playPurchase("k-1"))
        reused.verifier.pass(PassTrigger.ColdStart)
        reused.verifier.pass(PassTrigger.Foreground)
        assertEquals(1, reused.verifyRequests.size)

        // 400 IDEMPOTENCY_KEY_REQUIRED / _INVALID cannot happen from the app (it always sends a valid key); handled as an unexpected answer.
        listOf(CapturedPlayResponses.refusedKeyRequired, CapturedPlayResponses.refusedKeyInvalid).forEach { answer ->
            val t = env().apply { signIn() }
            t.answerVerify(answer)
            t.port.own(playPurchase("k-2"))
            t.verifier.pass(PassTrigger.ColdStart)
            t.verifier.pass(PassTrigger.Foreground)
            assertEquals(2, t.verifyRequests.size, "an unexpected refusal waits for the next trigger, no timer")
            assertEquals("Free", t.account.plan)
        }
    }

    @Test
    fun anUnauthenticatedLiveAnswerStopsThePass() = runTest {
        val t = env()
        t.signIn()
        t.answerVerify(CapturedPlayResponses.refusedUnauthenticated)
        t.port.own(playPurchase("a-1"), playPurchase("a-2", minutesAgo = 1))
        t.verifier.pass(PassTrigger.ColdStart)
        runCurrent()
        assertEquals(1, t.verifyRequests.size, "the second purchase is not sent after a 401")
    }

    /** The live 429 and 503 wait about an hour; the app waits as long as the server says and reuses the key. */
    @Test
    fun theServersOwnWaitIsHonouredForTheLiveRateLimitAndTheLiveUnavailableAnswers() = runTest {
        listOf(CapturedPlayResponses.refusedRateLimited to 3563L, CapturedPlayResponses.refusedUnavailable to 3600L).forEach { (answer, seconds) ->
            val t = env()
            t.signIn()
            t.answerVerify(answer)
            t.port.own(playPurchase("r-1"))
            t.verifier.pass(PassTrigger.ColdStart)
            val retry = t.notes.notes.value.retry.values.single()
            assertEquals(1, retry.sends)
            assertEquals(t.clock.now() + seconds.seconds, retry.nextAt, "Retry-After: $seconds")
            assertEquals(seconds.toString(), answer.headers.single { it.first == "Retry-After" }.second)
            t.verifier.cancelTimers()
        }
    }

    // --- US2: restore ----------------------------------------------------------------------------------------------

    private fun PlayTestEnv.ownFour() = port.own(
        playPurchase("r-0", minutesAgo = 0), playPurchase("r-1", minutesAgo = 1), playPurchase("r-2", minutesAgo = 2), playPurchase("r-3", minutesAgo = 3),
    )

    @Test
    fun theLiveMixedRestoreAppliesThePlanAndCombinesItsOutcomes() = runTest {
        val t = env()
        t.signIn()
        t.answerRestore(CapturedPlayResponses.restoreMixed)
        t.ownFour()

        val result = assertIs<RestoreResult.Done>(t.restorer.restore(RestoreTrigger.Tap))

        assertEquals(listOf("r-0", "r-1", "r-2", "r-3"), t.restoreTokens(0), "most recent first, indexes line up with the answer's")
        assertEquals(4, result.summary.sent)
        assertEquals(1, result.summary.count(RestoreOutcome.Linked))
        assertEquals(1, result.summary.count(RestoreOutcome.LinkedElsewhere))
        assertEquals(1, result.summary.count(RestoreOutcome.NotVerified))
        assertEquals(1, result.summary.count(RestoreOutcome.Pending))
        assertEquals(listOf(RestoreMessagePart.Restored, RestoreMessagePart.AnotherAccount, RestoreMessagePart.Pending), result.summary.messageParts())
        assertEquals("Plus · Monthly", t.account.plan)
        assertEquals("google_play", t.env.subscriptions.stored.value?.provider)
        assertTrue(t.planState().showAnotherAccount)
        val notes = t.notes.notes.value
        assertEquals(2, notes.refused.size, "the elsewhere and the unverifiable purchases are not resent by triggers")
        assertEquals(1, notes.pendingSent.size)
        assertEquals(1, t.entitlements.refreshes)
    }

    @Test
    fun aReplayedRestoreAndTheAlreadyYoursAnswerSayRestored() = runTest {
        listOf(CapturedPlayResponses.restoreMixedReplay to 4, CapturedPlayResponses.restoreAlreadyYours to 1).forEach { (answer, items) ->
            val t = env()
            t.signIn()
            t.answerRestore(answer)
            t.port.own(*Array(items) { playPurchase("r-$it", minutesAgo = it) })
            val result = assertIs<RestoreResult.Done>(t.restorer.restore(RestoreTrigger.Tap))
            assertTrue(RestoreMessagePart.Restored in result.summary.messageParts())
            assertEquals("Plus · Monthly", t.account.plan)
        }
        val alreadyYours = env().apply { signIn(); answerRestore(CapturedPlayResponses.restoreAlreadyYours); port.own(playPurchase("r-0")) }
        assertEquals(1, assertIs<RestoreResult.Done>(alreadyYours.restorer.restore(RestoreTrigger.Tap)).summary.count(RestoreOutcome.AlreadyYours))
    }

    @Test
    fun anEmptyRestoreSendsAnEmptyListAndSaysThereIsNothing() = runTest {
        val t = env()
        t.signIn()
        t.answerRestore(CapturedPlayResponses.restoreEmpty)
        val result = assertIs<RestoreResult.Done>(t.restorer.restore(RestoreTrigger.Tap))
        assertEquals(0, t.restoreTokens(0).size)
        assertEquals(listOf(RestoreMessagePart.Nothing), result.summary.messageParts())
        assertEquals("Free", t.account.plan)
    }

    @Test
    fun twentyFiveUnverifiableItemsAreOneRequestAndSayNothingToRestore() = runTest {
        val t = env()
        t.signIn()
        t.answerRestore(CapturedPlayResponses.restore25Unknown)
        t.port.own(*Array(25) { playPurchase("m-$it", minutesAgo = it) })
        val result = assertIs<RestoreResult.Done>(t.restorer.restore(RestoreTrigger.Tap))
        assertEquals(1, t.restoreRequests.size)
        assertEquals(25, t.restoreTokens(0).size)
        assertEquals(25, result.summary.count(RestoreOutcome.NotVerified))
        assertEquals(listOf(RestoreMessagePart.Nothing), result.summary.messageParts())
    }

    @Test
    fun aSignInRestoreWithPurchasesIsSilentUnlessSomethingBelongsElsewhere() = runTest {
        val quiet = env().apply { signIn(); answerRestore(CapturedPlayResponses.restoreAlreadyYours); port.own(playPurchase("r-0")) }
        assertIs<RestoreResult.Done>(quiet.restorer.restore(RestoreTrigger.SignIn))
        assertFalse(quiet.planState().showAnotherAccount)

        val elsewhere = env().apply { signIn(); answerRestore(CapturedPlayResponses.restoreMixed); ownFour() }
        assertIs<RestoreResult.Done>(elsewhere.restorer.restore(RestoreTrigger.SignIn))
        assertTrue(elsewhere.planState().showAnotherAccount)
    }

    @Test
    fun everyLiveRestoreRefusalMapsToItsOwnResultAndTheTapMessage() = runTest {
        val expected = listOf<Pair<Captured, (RestoreResult) -> Boolean>>(
            CapturedPlayResponses.restoreRefusedRateLimited to { r -> r is RestoreResult.RateLimited && r.retryAfterSeconds == 3586L },
            CapturedPlayResponses.restoreRefusedUnavailable to { r -> r is RestoreResult.ServiceUnavailable },
            CapturedPlayResponses.restoreEmptyRefusedUnavailable to { r -> r is RestoreResult.ServiceUnavailable },
            CapturedPlayResponses.restoreRefusedKeyRequired to { r -> r is RestoreResult.Failed && r.requestId != null },
            CapturedPlayResponses.restoreRefusedKeyReused to { r -> r is RestoreResult.Failed && r.requestId != null },
            CapturedPlayResponses.restoreRefusedUnknownProduct to { r -> r is RestoreResult.Failed && r.requestId != null },
            CapturedPlayResponses.restoreRefusedDuplicateTokens to { r -> r is RestoreResult.Failed && r.requestId != null },
            CapturedPlayResponses.restoreRefusedTwentySix to { r -> r is RestoreResult.Failed && r.requestId != null },
        )
        expected.forEach { (answer, matches) ->
            val t = env()
            t.signIn()
            t.answerRestore(answer)
            t.port.own(playPurchase("r-0"))
            val result = t.restorer.restore(RestoreTrigger.Tap)
            assertTrue(matches(result), "${answer.status} ${answer.body.take(120)} -> $result")
            assertEquals("Free", t.account.plan, "a refusal applies nothing")
            assertEquals(0, t.entitlements.refreshes)
        }
    }

    @Test
    fun theLiveRestoreRateLimitShowsItsWaitThroughThePlanStoreAndTryAgainReusesTheKey() = runMviTest {
        val t = PlayTestEnv(backgroundScope, testScheduler)
        t.signIn()
        t.answerRestore(CapturedPlayResponses.restoreRefusedRateLimited)
        t.port.own(playPurchase("r-0"))
        val vm = PlanViewModel(
            plan = MutableStateFlow<CurrentPlan?>(null), subscription = MutableStateFlow(null), catalogue = MutableStateFlow(null),
            timeZoneId = MutableStateFlow("Africa/Cairo"), loadStored = {}, refresh = { RefreshOutcome.Done }, loadCatalogue = { RefreshOutcome.Done },
            playSupported = true,
            restorePurchases = { retry -> t.restorer.restore(RestoreTrigger.Tap, retry) },
        )
        vm.onIntent(PlanIntent.RestorePurchases)
        waitForRestore(vm)
        assertEquals(RestoreUi.Refused(RestoreRefusal.RateLimited, retryAfterSeconds = 3586), vm.state.value.restore)
        vm.onIntent(PlanIntent.RetryRestore)
        waitForRestore(vm)
        assertEquals(2, t.restoreRequests.size)
        assertEquals(t.restoreRequests[0].headers["Idempotency-Key"], t.restoreRequests[1].headers["Idempotency-Key"], "Try again reuses the attempt's key")
    }

    private suspend fun waitForRestore(vm: PlanViewModel) {
        com.fares.saveme.feature.billing.waitUntil { vm.state.value.restore !is RestoreUi.Running }
    }

    // --- the account reference and the state reads the app makes after an answer ---------------------------------------

    @Test
    fun theLiveAccountReferenceIsSixtyFourLowercaseHexAndStablePerAccount() = runTest {
        val first = decodeReference(CapturedPlayResponses.accountReference)
        val again = decodeReference(CapturedPlayResponses.accountReferenceAgain)
        val other = decodeReference(CapturedPlayResponses.accountReferenceOtherAccount)
        assertTrue(first.isWellFormed && other.isWellFormed)
        assertEquals(first.obfuscatedAccountId, again.obfuscatedAccountId)
        assertNotEquals(first.obfuscatedAccountId, other.obfuscatedAccountId)
        assertEquals(CapturedPlayInputs.REFERENCE_A, first.obfuscatedAccountId)
        assertEquals(401, CapturedPlayResponses.accountReferenceNoBearer.status)
    }

    private suspend fun decodeReference(answer: Captured) =
        assertIs<ApiResult.Success<com.fares.saveme.feature.billing.data.dto.PlayAccountReferenceDto>>(
            BillingTestEnv { jsonResponse(answer.status, answer.body) }.also { it.signedIn() }.api.playAccountReference(),
        ).value

    @Test
    fun theSubscriptionAndEntitlementsReadsAfterEachStateDecodeAndMatchTheAnswers() = runTest {
        data class Read(val subscription: Captured, val entitlements: Captured?, val planName: String, val status: String?, val provider: String?)
        listOf(
            Read(CapturedPlayResponses.subscriptionAfterPurchase, CapturedPlayResponses.entitlementsAfterPurchase, "Plus · Monthly", "active", "google_play"),
            Read(CapturedPlayResponses.subscriptionGrace, CapturedPlayResponses.entitlementsGrace, "Plus · Monthly", "grace", "google_play"),
            Read(CapturedPlayResponses.subscriptionCanceled, null, "Plus · Monthly", "active", "google_play"),
            Read(CapturedPlayResponses.subscriptionOnHold, null, "Free", null, null),
            Read(CapturedPlayResponses.subscriptionPaused, null, "Free", null, null),
            Read(CapturedPlayResponses.subscriptionExpired, null, "Free", null, null),
            Read(CapturedPlayResponses.subscriptionRevoked, CapturedPlayResponses.entitlementsRevoked, "Free", null, null),
            Read(CapturedPlayResponses.subscriptionAfterPending, null, "Free", null, null),
            Read(CapturedPlayResponses.subscriptionAfterPurchasedLater, null, "Plus · Monthly", "active", "google_play"),
            Read(CapturedPlayResponses.subscriptionManualGoverns, CapturedPlayResponses.entitlementsManualGoverns, "Plus · Yearly", "active", "manual"),
        ).forEach { read ->
            val env = BillingTestEnv { request ->
                val answer = if (request.url.encodedPath.endsWith("/me/subscription")) read.subscription else read.entitlements ?: read.subscription
                jsonResponse(answer.status, answer.body)
            }
            env.signedIn()
            val subscription = assertIs<ApiResult.Success<com.fares.saveme.feature.billing.data.dto.MySubscriptionDto>>(env.subscriptions.fetch()).value
            assertEquals(read.planName, subscription.plan.name)
            assertEquals(read.status, subscription.status)
            assertEquals(read.provider, subscription.provider)
            if (read.entitlements != null) {
                val entitlements = assertIs<ApiResult.Success<com.fares.saveme.feature.auth.data.dto.EntitlementSummaryDto>>(env.auth.entitlementsApi.fetch()).value
                assertEquals(read.planName, entitlements.plan.name)
            }
        }
    }

    /** JSON objects compare by members, not by order: the replayed body has the same members in another order. */
    private fun String.stripOrder(): Any = Json.parseToJsonElement(this)
}
