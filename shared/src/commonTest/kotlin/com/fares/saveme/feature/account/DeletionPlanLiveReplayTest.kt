package com.fares.saveme.feature.account

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.feature.account.CapturedDeletionPlanResponses as C
import com.fares.saveme.feature.account.CapturedFormerMemberResponses as F
import com.fares.saveme.feature.account.data.AccountApi
import com.fares.saveme.feature.account.domain.deletion.DefaultDeletionPlanService
import com.fares.saveme.feature.account.domain.deletion.FakeMemberships
import com.fares.saveme.feature.account.domain.deletion.ME
import com.fares.saveme.feature.account.domain.deletion.PlanOutcome
import com.fares.saveme.feature.account.domain.deletion.PlanState
import com.fares.saveme.feature.account.domain.deletion.SuccessorChoice
import com.fares.saveme.feature.account.domain.deletion.SuccessorChoices
import com.fares.saveme.feature.account.domain.deletion.SuccessorReason
import com.fares.saveme.feature.account.domain.deletion.member
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.model.DeletionOutcome
import com.fares.saveme.feature.account.domain.usecase.CancelDeletionUseCase
import com.fares.saveme.feature.account.domain.usecase.RequestDeletionUseCase
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountIntent
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountViewModel
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteStep
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.auth.domain.usecase.SignInUseCase
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.signInSuccessBody
import com.fares.saveme.feature.billing.Captured
import com.fares.saveme.feature.budgets.FakeBudgetWallets
import com.fares.saveme.feature.budgets.walletView
import com.fares.saveme.feature.wallets.domain.MemberRecord
import com.fares.saveme.feature.wallets.domain.model.WalletList
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The answers captured from the live local backend (CapturedDeletionPlanResponses, qa-report.md section 3) replayed through the
 * real `ApiClient` -> `AccountApi` -> [DefaultDeletionPlanService], the deletion use cases and the Delete account store (T074).
 */
class DeletionPlanLiveReplayTest {

    private val date = Instant.parse("2026-11-05T00:23:26.934042Z")

    /** Answers the next captured answer in the queue for every call but sign-in; [requests] are the calls the person's phone made. */
    private class Rig(scope: CoroutineScope, vararg answers: Captured) {
        private val queue = ArrayDeque(answers.toList())
        val env = AccountTestEnv { request ->
            if (request.url.encodedPath.endsWith("auth/login")) {
                jsonResponse(200, signInSuccessBody())
            } else {
                val c = if (queue.size > 1) queue.removeFirst() else queue.first()
                jsonResponse(c.status, c.body, *c.headers.toTypedArray())
            }
        }
        val sessions = SessionManager(env.auth.repository, env.auth.entitlementsApi, env.events, scope)
        val api = AccountApi(env.auth.client)
        val requests get() = env.requests.filterNot { it.url.encodedPath.endsWith("auth/login") }
    }

    private suspend fun rig(scope: CoroutineScope, vararg answers: Captured): Rig {
        val rig = Rig(scope, *answers)
        SignInUseCase(rig.env.auth.clientInfo, DeviceIdentityStore(rig.env.auth.keyValues), rig.env.auth.repository, rig.sessions)("sara@example.com", "pw", "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70")
        return rig
    }

    /** The phone's own member records of the QA wallets, as the live members screens listed them. */
    private fun members(): Map<String, List<MemberRecord>> = mapOf(
        F.WALLET_ALPHA to listOf(member(F.WALLET_ALPHA, ME, "OWNQA", "owner"), member(F.WALLET_ALPHA, F.PARTNER, "PARQA", "viewer", joined = "2026-10-06T00:21:05Z")),
        F.WALLET_BETA to listOf(
            member(F.WALLET_BETA, ME, "OWNQA", "owner"),
            member(F.WALLET_BETA, F.EDITOR, "EDIQA", "editor", joined = "2026-10-06T00:23:02Z"),
            member(F.WALLET_BETA, F.CHOSEN_VIEWER, "VIEQA", "viewer", joined = "2026-10-06T00:23:03Z"),
        ),
        F.WALLET_DELTA to listOf(
            member(F.WALLET_DELTA, ME, "OWNQA", "owner"),
            member(F.WALLET_DELTA, F.CHOSEN_VIEWER, "VIEQA", "viewer", joined = "2026-10-06T00:23:17Z"),
            member(F.WALLET_DELTA, F.PARTNER, "PARQA", "viewer", joined = "2026-10-06T00:23:18Z"),
        ),
    )

    private fun planService(rig: Rig) = DefaultDeletionPlanService(
        rig.api,
        FakeBudgetWallets().also { fake ->
            fake.list.value = WalletList(
                listOf(F.WALLET_ALPHA to "QA Alpha", F.WALLET_BETA to "QA Beta", F.WALLET_DELTA to "QA Delta")
                    .map { (id, name) -> walletView(id, "EGP").let { v -> v.copy(server = v.server.copy(name = name)) } },
                emptyList(), emptyList(), null,
            )
        },
        FakeMemberships(members()), rig.sessions,
    )

    // --- the plan ---

    @Test
    fun theLiveActivePlanNamesEachRuleAndOnlyAskForAChoiceWhereTheServerExpectsOne() = runTest {
        val rig = rig(backgroundScope, C.planActiveThreeWallets)
        val plan = assertIs<PlanOutcome.Loaded>(planService(rig).read()).plan
        assertEquals(PlanState.Active, plan.state)
        assertNull(plan.scheduledFor)
        val (alpha, beta, delta) = plan.wallets
        assertEquals(SuccessorReason.OnlyMember, alpha.reason)
        assertFalse(alpha.choiceExpected)
        assertFalse(alpha.choiceOffered, "one other member: no choice, that member always becomes owner")
        assertEquals("QA Alpha", alpha.walletName)
        assertEquals(SuccessorReason.OldestEditor, beta.reason)
        assertTrue(beta.choiceOffered)
        assertEquals(F.EDITOR, beta.candidates.first().userId, "the server's suggestion is listed first")
        assertEquals(listOf(F.EDITOR, F.CHOSEN_VIEWER), beta.candidates.map { it.userId })
        assertEquals(SuccessorReason.OldestViewer, delta.reason)
        assertTrue(delta.choiceOffered)
        assertEquals(F.CHOSEN_VIEWER, delta.suggested?.userId)
        assertEquals(1, rig.requests.size)
        assertEquals(HttpMethod.Get, rig.requests.single().method)
    }

    @Test
    fun theLivePendingPlansShowTheStoredChoiceThenTheReplacementAndNeverMoveTheDate() = runTest {
        val chosen = assertIs<PlanOutcome.Loaded>(planService(rig(backgroundScope, C.planPendingChosen)).read()).plan
        assertEquals(PlanState.PendingDeletion, chosen.state)
        assertEquals(date, chosen.scheduledFor)
        assertEquals(Instant.parse("2026-10-06T00:23:26.934042Z"), chosen.requestedAt)
        assertTrue(chosen.hasStoredChoice)
        assertEquals(listOf(SuccessorReason.OnlyMember, SuccessorReason.Chosen, SuccessorReason.Chosen), chosen.wallets.map { it.reason })
        assertTrue(chosen.wallets[1].choiceExpected, "choice_expected stays true after a choice was sent")
        val replaced = assertIs<PlanOutcome.Loaded>(planService(rig(backgroundScope, C.planPendingReplaced)).read()).plan
        assertEquals(date, replaced.scheduledFor)
        assertEquals(listOf(SuccessorReason.OnlyMember, SuccessorReason.Chosen, SuccessorReason.OldestViewer), replaced.wallets.map { it.reason })
        val none = assertIs<PlanOutcome.Loaded>(planService(rig(backgroundScope, C.planPendingNoChoices)).read()).plan
        assertFalse(none.hasStoredChoice)
    }

    @Test
    fun theLiveCancelledPlanIsActiveAgainWithEveryChoiceDropped() = runTest {
        val rig = rig(backgroundScope, C.planAfterCancel)
        val plan = assertIs<PlanOutcome.Loaded>(planService(rig).read()).plan
        assertEquals(PlanState.Active, plan.state)
        assertFalse(plan.hasStoredChoice)
        assertNull(plan.scheduledFor)
    }

    @Test
    fun anAccountWithNoSharedWalletHasAnEmptyPlanAndAnUnauthenticatedReadIsAFailure() = runTest {
        val empty = assertIs<PlanOutcome.Loaded>(planService(rig(backgroundScope, C.planPendingNoWallets)).read()).plan
        assertTrue(empty.wallets.isEmpty())
        assertEquals(PlanState.PendingDeletion, empty.state)
        assertEquals(ApiErrorCode.UNAUTHENTICATED, assertIs<PlanOutcome.Failed>(planService(rig(backgroundScope, C.planUnauthenticated)).read()).code)
    }

    @Test
    fun theLivePlanUpdatesTheAccountSnapshotsStateAndDate() = runTest {
        val rig = rig(backgroundScope, C.planPendingChosen)
        planService(rig).read()
        val account = rig.sessions.state.value.let { assertIs<com.fares.saveme.feature.auth.domain.AuthState.SignedIn>(it).account }
        assertEquals(AccountState.PendingDeletion, account.state)
        assertEquals(date, account.deletionScheduledFor)
    }

    // --- the request ---

    @Test
    fun theLiveScheduledAnswersCarryTheServersDateAndOwnersAndNeverMoveTheDate() = runTest {
        val firstRig = rig(backgroundScope, C.requestFirstScheduled)
        val first = assertIs<DeletionOutcome.Scheduled>(RequestDeletionUseCase(firstRig.api, firstRig.sessions)("pw"))
        assertEquals(date, first.date)
        assertEquals(listOf(SuccessorReason.OnlyMember, SuccessorReason.OldestEditor, SuccessorReason.OldestViewer), first.owners.map { it.reason })
        for (repeat in listOf(C.requestRepeatPasswordOnly, C.requestWithChoices, C.requestReplaceChoice, C.requestNoSharedWallets)) {
            val r = rig(backgroundScope, repeat)
            val outcome = assertIs<DeletionOutcome.Scheduled>(RequestDeletionUseCase(r.api, r.sessions)("pw"))
            if (repeat !== C.requestNoSharedWallets) assertEquals(date, outcome.date, "repeating while pending keeps the scheduled instant")
        }
        val withChoices = rig(backgroundScope, C.requestWithChoices)
        val owners = assertIs<DeletionOutcome.Scheduled>(RequestDeletionUseCase(withChoices.api, withChoices.sessions)("pw")).owners
        assertEquals(listOf(SuccessorReason.OnlyMember, SuccessorReason.Chosen, SuccessorReason.Chosen), owners.map { it.reason })
        assertTrue(owners.map { it.ownerUserId }.contains(F.CHOSEN_VIEWER))
    }

    @Test
    fun theLiveRequestBodyHasOnlyThePasswordAndTheChoicesWhenGiven() = runTest {
        val r = rig(backgroundScope, C.requestWithChoices)
        RequestDeletionUseCase(r.api, r.sessions)("pw", listOf(SuccessorChoice(F.WALLET_BETA, F.CHOSEN_VIEWER)))
        val body = r.requests.single().jsonBody()
        assertEquals(setOf("password", "successors"), body.keys)
        assertEquals(1, (body["successors"] as JsonArray).size)
        val plain = rig(backgroundScope, C.requestFirstScheduled)
        RequestDeletionUseCase(plain.api, plain.sessions)("pw")
        assertEquals(setOf("password"), plain.requests.single().jsonBody().keys)
        assertEquals(JsonPrimitive("pw"), plain.requests.single().jsonBody()["password"])
    }

    @Test
    fun theLiveRefusalsMapToSpec002AndSuccessorOutcomes() = runTest {
        suspend fun outcome(c: Captured): DeletionOutcome = rig(backgroundScope, c).let { RequestDeletionUseCase(it.api, it.sessions)("pw") }
        assertEquals(DeletionOutcome.WrongPassword, outcome(C.wrongPassword))
        assertIs<DeletionOutcome.Validation>(outcome(C.missingPassword))
        for (changed in listOf(C.successorsBeforePassword, C.successorNotMember, C.walletNotOwned, C.duplicateWallet)) {
            assertEquals(DeletionOutcome.SuccessorsChanged, outcome(changed), "a /successors pointer re-reads the plan and schedules nothing")
        }
    }

    @Test
    fun theLiveCancelAnswers204AlsoWhenNothingIsPendingAndTheAccountIsActiveAfterwards() = runTest {
        for (cancel in listOf(C.cancelled, C.cancelWhenActive)) {
            val r = rig(backgroundScope, cancel)
            assertEquals(ActionOutcome.Done, CancelDeletionUseCase(r.api, r.sessions)())
            val account = assertIs<com.fares.saveme.feature.auth.domain.AuthState.SignedIn>(r.sessions.state.value).account
            assertEquals(AccountState.Active, account.state)
        }
    }

    @Test
    fun aSignInDuringPurgingAnswersExactlyAsForAnUnknownAddress() {
        val purging = C.signInWhilePurging
        val unknown = C.signInUnknownForComparison
        assertEquals(401, purging.status)
        assertEquals(unknown.status, purging.status)
        fun shape(c: Captured) = c.body.replace(Regex("\"request_id\":\"[^\"]+\""), "")
        assertEquals(shape(unknown), shape(purging), "no way to tell a purging account from one that never existed (spec 022)")
        assertTrue(purging.body.contains("INVALID_CREDENTIALS"))
        assertNotEquals(purging.body, unknown.body, "request ids differ")
    }

    // --- the store ---

    @Test
    fun theDeleteStoreWalksTheLivePlanToTheServersScheduleAndSendsOneChoicePerOfferedWallet() = runMviTest {
        val r = rig(backgroundScope, C.planActiveThreeWallets, C.requestWithChoices)
        val service = planService(r)
        val request = RequestDeletionUseCase(r.api, r.sessions)
        val vm = DeleteAccountViewModel(readPlan = service::read, requestDeletion = { pw, s -> request(pw, s) }, now = { Instant.parse("2026-10-06T00:23:00Z") })
        vm.onIntent(DeleteAccountIntent.Continue)
        vm.state.first { it.step == DeleteStep.NewOwners }
        vm.onIntent(DeleteAccountIntent.PickOwner(F.WALLET_BETA, F.CHOSEN_VIEWER))
        vm.onIntent(DeleteAccountIntent.OwnersNext)
        assertEquals(DeleteStep.Review, vm.state.value.step)
        vm.onIntent(DeleteAccountIntent.ReviewConfirm)
        assertEquals(DeleteStep.Confirm, vm.state.value.step)
        vm.onIntent(DeleteAccountIntent.PasswordChanged("pw"))
        vm.onIntent(DeleteAccountIntent.Confirm)
        vm.state.first { it.step is DeleteStep.Scheduled }
        val scheduled = assertIs<DeleteStep.Scheduled>(vm.state.value.step)
        assertEquals(date, scheduled.date)
        assertEquals(listOf(SuccessorReason.OnlyMember, SuccessorReason.Chosen, SuccessorReason.Chosen), scheduled.owners.map { it.reason })
        val sent = (r.requests.last().jsonBody()["successors"] as JsonArray).map { it.toString() }
        assertEquals(2, sent.size, "Alpha has one other member and offered no choice: not sent")
        assertTrue(sent[0].contains(F.WALLET_BETA) && sent[0].contains(F.CHOSEN_VIEWER))
        assertTrue(sent[1].contains(F.WALLET_DELTA), "Delta's suggestion is sent as confirmed")
        assertEquals("", vm.state.value.password)
        assertEquals(
            listOf(SuccessorChoice(F.WALLET_BETA, F.CHOSEN_VIEWER), SuccessorChoice(F.WALLET_DELTA, F.CHOSEN_VIEWER)),
            SuccessorChoices.build(assertIs<PlanOutcome.Loaded>(planService(rig(backgroundScope, C.planActiveThreeWallets)).read()).plan, mapOf(F.WALLET_BETA to F.CHOSEN_VIEWER)),
        )
    }

    @Test
    fun aLiveRefusedSuccessorSendsThePersonBackToTheNewOwnersWithAFreshPlanAndSchedulesNothing() = runMviTest {
        val r = rig(backgroundScope, C.planActiveThreeWallets, C.successorNotMember, C.planActiveThreeWallets)
        val service = planService(r)
        val request = RequestDeletionUseCase(r.api, r.sessions)
        val vm = DeleteAccountViewModel(readPlan = service::read, requestDeletion = { pw, s -> request(pw, s) }, now = { Instant.parse("2026-10-06T00:23:00Z") })
        vm.onIntent(DeleteAccountIntent.Continue)
        vm.state.first { it.step == DeleteStep.NewOwners }
        vm.onIntent(DeleteAccountIntent.PickOwner(F.WALLET_BETA, F.CHOSEN_VIEWER))
        vm.onIntent(DeleteAccountIntent.OwnersNext)
        vm.onIntent(DeleteAccountIntent.ReviewConfirm)
        vm.onIntent(DeleteAccountIntent.PasswordChanged("pw"))
        vm.onIntent(DeleteAccountIntent.Confirm)
        vm.state.first { it.successorsChanged && it.step == DeleteStep.NewOwners && !it.submitting && r.requests.size == 3 }
        assertEquals(DeleteStep.NewOwners, vm.state.value.step)
        assertEquals(3, r.requests.size, "plan, refused request, plan again")
        assertEquals(emptyMap(), vm.state.value.picks, "the picks reset to the new suggestions")
        assertEquals(AccountState.Active, assertIs<com.fares.saveme.feature.auth.domain.AuthState.SignedIn>(r.sessions.state.value).account.state)
    }
}
