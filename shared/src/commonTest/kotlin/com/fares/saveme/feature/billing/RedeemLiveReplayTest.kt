package com.fares.saveme.feature.billing

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.billing.domain.AttemptKey
import com.fares.saveme.feature.billing.domain.RedeemOutcome
import com.fares.saveme.feature.billing.domain.RedeemService
import com.fares.saveme.feature.billing.ui.redeem.RedeemIntent
import com.fares.saveme.feature.billing.ui.redeem.RedeemMessage
import com.fares.saveme.feature.billing.ui.redeem.RedeemViewModel
import com.fares.saveme.core.presentation.runMviTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The answers captured from the live local backend (CapturedRedeemResponses, qa-report.md) replayed through the real
 * `ApiClient` -> `BillingApi` -> `RedeemService` -> session and subscription stores (T049), and through the redeem store.
 */
class RedeemLiveReplayTest {

    private val attempt = AttemptKey("0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70")
    private val now = Instant.parse("2026-10-05T22:31:00Z")

    private fun env(scope: CoroutineScope, answer: Captured): RedeemTestEnv =
        RedeemTestEnv(scope) { jsonResponse(answer.status, answer.body, *answer.headers.toTypedArray()) }

    private fun serviceOf(t: RedeemTestEnv, scope: CoroutineScope) =
        RedeemService(t.env.api, t.sessions, t.env.subscriptions, t.entitlements, scope, object : kotlin.time.Clock {
            override fun now() = now
        })

    private suspend fun outcomeOf(scope: CoroutineScope, answer: Captured): Pair<RedeemOutcome, RedeemTestEnv> {
        val t = env(scope, answer)
        t.signIn()
        return serviceOf(t, scope).redeem(RAW_CODE, attempt) to t
    }

    private fun RedeemTestEnv.account() = (sessions.state.value as AuthState.SignedIn).account

    @Test
    fun theFirstRedeemNamesTheRedeemedPlanAndAppliesItEverywhere() = runTest {
        val (outcome, t) = outcomeOf(backgroundScope, CapturedRedeemResponses.successFirstRedeem)
        advanceUntilIdle()
        val confirmation = assertIs<RedeemOutcome.Redeemed>(outcome).confirmation
        assertEquals("Plus · Monthly", confirmation.governingPlanName)
        assertTrue(confirmation.governingIsRedeemed)
        assertFalse(confirmation.playRenews)
        assertEquals(Instant.parse("2026-11-05T22:30:24.494422Z"), confirmation.governingEnd)
        assertNull(confirmation.redeemedStartsAt, "the period started a moment before now")
        assertEquals("Plus · Monthly", t.account().plan)
        assertEquals("instapay", t.env.subscriptions.stored.value?.provider)
        assertEquals(1, t.entitlements.refreshes)
        val request = t.redeemRequests.single()
        assertEquals(attempt.value, request.headers["Idempotency-Key"])
        assertEquals("""{"code":"$GROUPED_CODE"}""", request.bodyText())
    }

    @Test
    fun aSameKeyReplayAndARepeatByTheSameAccountShowTheSameConfirmation() = runTest {
        val first = assertIs<RedeemOutcome.Redeemed>(outcomeOf(backgroundScope, CapturedRedeemResponses.successFirstRedeem).first)
        val replay = assertIs<RedeemOutcome.Redeemed>(outcomeOf(backgroundScope, CapturedRedeemResponses.successSameKeyReplay).first)
        val repeat = assertIs<RedeemOutcome.Redeemed>(outcomeOf(backgroundScope, CapturedRedeemResponses.successRepeatNewKey).first)
        assertEquals(first.confirmation, replay.confirmation)
        assertEquals(first.confirmation, repeat.confirmation)
    }

    @Test
    fun anEarlyRenewalSaysWhenTheRedeemedPeriodStarts() = runTest {
        val (outcome, t) = outcomeOf(backgroundScope, CapturedRedeemResponses.successEarlyRenewal)
        advanceUntilIdle()
        val confirmation = assertIs<RedeemOutcome.Redeemed>(outcome).confirmation
        assertEquals("Plus · Yearly", confirmation.governingPlanName)
        assertTrue(confirmation.governingIsRedeemed)
        assertEquals(Instant.parse("2026-11-05T22:30:24.494422Z"), confirmation.redeemedStartsAt)
        assertEquals("Plus · Yearly", t.account().plan)
    }

    @Test
    fun anOperatorGrantedLifetimePlanKeepsGoverningAndHasNoEnd() = runTest {
        val (outcome, t) = outcomeOf(backgroundScope, CapturedRedeemResponses.successManualLifetimeGoverns)
        advanceUntilIdle()
        val confirmation = assertIs<RedeemOutcome.Redeemed>(outcome).confirmation
        assertFalse(confirmation.governingIsRedeemed)
        assertNull(confirmation.governingEnd)
        assertFalse(confirmation.playRenews)
        assertEquals("Plus · Yearly", confirmation.governingPlanName)
        assertEquals("Plus · Monthly", confirmation.redeemedPlanName)
        assertEquals(Instant.parse("2026-12-05T22:32:11.396307Z"), confirmation.redeemedEnd)
        assertEquals("manual", t.env.subscriptions.stored.value?.provider)
        assertNull(t.env.subscriptions.stored.value?.currentPeriodEnd)
    }

    @Test
    fun aRenewingPlaySubscriptionKeepsGoverningAndGetsItsNote() = runTest {
        val (outcome, t) = outcomeOf(backgroundScope, CapturedRedeemResponses.successPlayRenewsGoverns)
        advanceUntilIdle()
        val confirmation = assertIs<RedeemOutcome.Redeemed>(outcome).confirmation
        assertFalse(confirmation.governingIsRedeemed)
        assertTrue(confirmation.playRenews)
        assertEquals("Plus · Yearly", confirmation.governingPlanName)
        assertEquals("google_play", t.env.subscriptions.stored.value?.provider)
        assertNotNull(t.env.subscriptions.stored.value?.currentPeriodEnd)
    }

    @Test
    fun everyLiveRefusalMapsToItsOwnOutcomeAndNothingIsApplied() = runTest {
        val table = listOf(
            CapturedRedeemResponses.refusedOtherAddress to RedeemOutcome.OtherAddress,
            CapturedRedeemResponses.refusedOtherAddressWhileUnverified to RedeemOutcome.OtherAddress,
            CapturedRedeemResponses.refusedNotVerified to RedeemOutcome.NotVerified,
            CapturedRedeemResponses.refusedNotFound to RedeemOutcome.NotValid,
            CapturedRedeemResponses.refusedRevokedCode to RedeemOutcome.NotValid,
            CapturedRedeemResponses.refusedUsed to RedeemOutcome.Used,
            CapturedRedeemResponses.refusedExpired to RedeemOutcome.Expired,
            CapturedRedeemResponses.refusedMalformed to RedeemOutcome.InvalidFormat,
            CapturedRedeemResponses.refusedMissingCode to RedeemOutcome.InvalidFormat,
            CapturedRedeemResponses.refusedRateLimited to RedeemOutcome.RateLimited(3466),
        )
        table.forEach { (answer, expected) ->
            val (outcome, t) = outcomeOf(backgroundScope, answer)
            assertEquals(expected, outcome, answer.body)
            assertEquals(1, t.redeemRequests.size, "never retried")
            assertEquals(0, t.entitlements.refreshes)
        }
    }

    @Test
    fun theLiveKeyRefusalsCarryTheirRequestIds() = runTest {
        val reused = outcomeOf(backgroundScope, CapturedRedeemResponses.refusedKeyReused).first
        assertEquals(RedeemOutcome.KeyReused("01a10e30-cf75-71ab-912b-64aa8abe52de"), reused)
        val required = outcomeOf(backgroundScope, CapturedRedeemResponses.refusedKeyRequired).first
        assertEquals(RedeemOutcome.Failed("01a10e30-ceee-737d-99f6-bf2557268450"), required)
    }

    @Test
    fun theMismatchAnswerNamesNoAddress() {
        listOf(CapturedRedeemResponses.refusedOtherAddress, CapturedRedeemResponses.refusedOtherAddressWhileUnverified).forEach {
            assertFalse(it.body.contains("@"), it.body)
        }
    }

    @Test
    fun theLiveRateLimitDisablesRedeemFromTheServersWaitThroughTheStore() = runMviTest {
        val t = RedeemTestEnv(this.backgroundScope) {
            val answer = CapturedRedeemResponses.refusedRateLimited
            jsonResponse(answer.status, answer.body, *answer.headers.toTypedArray())
        }
        t.signIn()
        val vm = RedeemViewModel(
            redeem = { code, key -> t.service.redeem(code, key) },
            isEmailVerified = { true },
            signedInEmail = { "qa@example.com" },
            signedIn = MutableStateFlow(true),
            timeZoneId = MutableStateFlow("Africa/Cairo"),
            now = { now },
        )
        vm.onIntent(RedeemIntent.CodeChanged(RAW_CODE))
        vm.onIntent(RedeemIntent.Redeem)
        vm.state.first { it.message != null } // the mock engine answers on its own thread
        // 3466 s rounds up to 58 minutes (live Retry-After and retry_after_seconds agree).
        assertEquals(RedeemMessage.RateLimited(58), vm.state.value.message)
        assertFalse(vm.state.value.canRedeem)
        assertEquals(1, t.redeemRequests.size)
        advanceTimeBy(3_467_000) // lets the wait timer finish before the test ends
        assertTrue(vm.state.value.canRedeem)
    }

    @Test
    fun theLiveOtherAddressAnswerShowsOnlyTheSignedInAddressThroughTheStore() = runMviTest {
        val t = RedeemTestEnv(this.backgroundScope) {
            val answer = CapturedRedeemResponses.refusedOtherAddress
            jsonResponse(answer.status, answer.body)
        }
        t.signIn()
        val vm = RedeemViewModel(
            redeem = { code, key -> t.service.redeem(code, key) },
            isEmailVerified = { true },
            signedInEmail = { "qa@example.com" },
            signedIn = MutableStateFlow(true),
            timeZoneId = MutableStateFlow("Africa/Cairo"),
            now = { now },
        )
        vm.onIntent(RedeemIntent.CodeChanged(RAW_CODE))
        vm.onIntent(RedeemIntent.Redeem)
        vm.state.first { it.message != null } // the mock engine answers on its own thread
        assertEquals(RedeemMessage.OtherAddress("qa@example.com"), vm.state.value.message)
    }
}
