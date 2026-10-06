package com.fares.saveme.feature.account.domain.deletion

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.data.AccountApi
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.auth.domain.usecase.SignInUseCase
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.auth.signInSuccessBody
import com.fares.saveme.feature.budgets.FakeBudgetWallets
import com.fares.saveme.feature.budgets.walletView
import com.fares.saveme.feature.wallets.domain.MemberRecord
import com.fares.saveme.feature.wallets.domain.WalletMemberships
import com.fares.saveme.feature.wallets.domain.model.WalletList
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import io.ktor.http.HttpMethod
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The signed-in person in these tests (see `signInSuccessBody`). */
const val ME = "0192f7a0-0000-7000-8000-000000000001"

class FakeMemberships(private val byWallet: Map<String, List<MemberRecord>>) : WalletMemberships {
    override fun observe(walletId: String): Flow<List<MemberRecord>> = flowOf(byWallet[walletId].orEmpty())
    override suspend fun ownRole(walletId: String): WalletRole? = null
    override suspend fun ownMembershipVersion(walletId: String): Long? = null
    override suspend fun store(record: MemberRecord) = Unit
    override suspend fun leftWallet(walletId: String) = Unit
}

fun member(wallet: String, user: String, name: String?, role: String = "editor", joined: String = "2026-09-01T00:00:00Z", deleted: Boolean = false) =
    MemberRecord("m-$wallet-$user", wallet, user, name, role, 1, joined, deleted)

fun planJson(state: String = "active", scheduled: String? = null, vararg successors: String) =
    """{"data":{"state":"$state","deletion_requested_at":null,"deletion_scheduled_for":${scheduled?.let { "\"$it\"" } ?: "null"},"successors":[${successors.joinToString(",")}]},"meta":{}}"""

fun successorJson(wallet: String, user: String, reason: String = "oldest_editor", choice: Boolean = true) =
    """{"wallet_id":"$wallet","successor_user_id":"$user","reason":"$reason","choice_expected":$choice}"""

class DeletionPlanServiceTest {

    private class Setup(
        val service: DeletionPlanService,
        val sessions: SessionManager,
        val env: AccountTestEnv,
    )

    private suspend fun setup(
        scope: CoroutineScope,
        planBody: String,
        wallets: List<Pair<String, String>> = listOf("w1" to "Home", "w3" to "Trip"),
        members: Map<String, List<MemberRecord>> = emptyMap(),
        offline: Boolean = false,
    ): Setup {
        val env = AccountTestEnv { request ->
            when {
                request.url.encodedPath.endsWith("auth/login") -> jsonResponse(200, signInSuccessBody())
                offline -> throw IOException("offline")
                else -> jsonResponse(200, planBody)
            }
        }
        val sessions = SessionManager(env.auth.repository, env.auth.entitlementsApi, env.events, scope)
        SignInUseCase(env.auth.clientInfo, DeviceIdentityStore(env.auth.keyValues), env.auth.repository, sessions)("sara@example.com", "pw", "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70")
        val fakeWallets = FakeBudgetWallets().also { fake ->
            fake.list.value = WalletList(wallets.map { (id, name) -> walletView(id, "EGP").let { v -> v.copy(server = v.server.copy(name = name)) } }, emptyList(), emptyList(), null)
        }
        return Setup(DefaultDeletionPlanService(AccountApi(env.auth.client), fakeWallets, FakeMemberships(members), sessions), sessions, env)
    }

    @Test
    fun joinsNamesAndOrdersTheCandidatesSuggestedFirstThenByJoinDate() = runTest {
        val s = setup(
            backgroundScope,
            planJson("active", null, successorJson("w1", "u2", "only_member", false), successorJson("w3", "u4")),
            members = mapOf(
                "w1" to listOf(member("w1", ME, "Sara", "owner"), member("w1", "u2", "Omar", "viewer")),
                "w3" to listOf(
                    member("w3", ME, "Sara", "owner"),
                    member("w3", "u6", "Late", "viewer", joined = "2026-09-20T00:00:00Z"),
                    member("w3", "u4", "Mona", "editor", joined = "2026-09-10T00:00:00Z"),
                    member("w3", "u5", "Early", "editor", joined = "2026-09-02T00:00:00Z"),
                ),
            ),
        )
        val plan = assertIs<PlanOutcome.Loaded>(s.service.read()).plan
        assertEquals(PlanState.Active, plan.state)
        val (fixed, choice) = plan.wallets
        assertEquals("Home", fixed.walletName)
        assertFalse(fixed.choiceOffered)
        assertEquals(SuccessorReason.OnlyMember, fixed.reason)
        assertEquals("Trip", choice.walletName)
        assertTrue(choice.choiceOffered)
        assertEquals(listOf("u4", "u5", "u6"), choice.candidates.map { it.userId }, "suggested first, then earliest joined; the person is excluded")
        assertEquals("Mona", choice.suggested?.displayName)
        assertEquals(WalletRole.Editor, choice.candidates[0].role)
        assertTrue(plan.offersChoice)
    }

    @Test
    fun aWalletTheArchivedListHoldsIsNamedToo() = runTest {
        val s = setup(backgroundScope, planJson("active", null, successorJson("wa", "u2")), wallets = emptyList(), members = mapOf("wa" to listOf(member("wa", "u2", "Omar"))))
        val wallet = assertIs<PlanOutcome.Loaded>(s.service.read()).plan.wallets.single()
        assertNull(wallet.walletName)
        assertFalse(wallet.choiceOffered, "the phone does not hold the wallet: no choice is offered")
    }

    @Test
    fun aSuggestionTheCandidatesLackBecomesASyntheticUnknownOne() = runTest {
        val s = setup(
            backgroundScope, planJson("active", null, successorJson("w3", "ghost")),
            members = mapOf("w3" to listOf(member("w3", ME, "Sara", "owner"), member("w3", "u5", "Early"))),
        )
        val wallet = assertIs<PlanOutcome.Loaded>(s.service.read()).plan.wallets.single()
        assertEquals(listOf("ghost", "u5"), wallet.candidates.map { it.userId })
        assertTrue(wallet.candidates[0].unknown)
        assertNull(wallet.candidates[0].displayName)
        assertTrue(wallet.choiceOffered)
    }

    @Test
    fun noChoiceWhenThePhoneHoldsNoOtherMember() = runTest {
        val s = setup(backgroundScope, planJson("active", null, successorJson("w3", "ghost")), members = mapOf("w3" to listOf(member("w3", ME, "Sara", "owner"))))
        val wallet = assertIs<PlanOutcome.Loaded>(s.service.read()).plan.wallets.single()
        assertFalse(wallet.choiceOffered)
        assertTrue(wallet.choiceExpected)
    }

    @Test
    fun departedMembersAreNotCandidates() = runTest {
        val s = setup(
            backgroundScope, planJson("active", null, successorJson("w3", "u4")),
            members = mapOf("w3" to listOf(member("w3", "u4", "Mona"), member("w3", "u5", "Gone", deleted = true))),
        )
        assertEquals(listOf("u4"), assertIs<PlanOutcome.Loaded>(s.service.read()).plan.wallets.single().candidates.map { it.userId })
    }

    @Test
    fun unknownReasonAndStateKeepTheirText() = runTest {
        val s = setup(backgroundScope, planJson("on_hold", null, successorJson("w3", "u4", "coin_toss")), members = mapOf("w3" to listOf(member("w3", "u4", "Mona"))))
        val plan = assertIs<PlanOutcome.Loaded>(s.service.read()).plan
        assertEquals(PlanState.Unknown("on_hold"), plan.state)
        assertEquals(SuccessorReason.Unknown("coin_toss"), plan.wallets.single().reason)
    }

    @Test
    fun readingAPendingPlanUpdatesTheSnapshotAndAnActiveOneClearsIt() = runTest {
        val pending = setup(backgroundScope, planJson("pending_deletion", "2026-11-05T10:00:00Z"))
        pending.service.read()
        val account = assertIs<AuthState.SignedIn>(pending.sessions.state.value).account
        assertEquals(AccountState.PendingDeletion, account.state)
        assertEquals(Instant.parse("2026-11-05T10:00:00Z"), account.deletionScheduledFor)

        val unknown = setup(backgroundScope, planJson("on_hold", "2026-11-05T10:00:00Z"))
        unknown.service.read()
        assertEquals(AccountState.Active, assertIs<AuthState.SignedIn>(unknown.sessions.state.value).account.state, "an unknown state leaves the snapshot alone")

        val active = setup(backgroundScope, planJson("active"))
        active.sessions.updateDeletion(AccountState.PendingDeletion, Instant.parse("2026-11-05T10:00:00Z"))
        active.service.read()
        assertEquals(AccountState.Active, assertIs<AuthState.SignedIn>(active.sessions.state.value).account.state)
    }

    @Test
    fun offlineAndRefusalsAreOutcomesAndEachCallIsOneGet() = runTest {
        val offline = setup(backgroundScope, "", offline = true)
        assertEquals(PlanOutcome.Offline, offline.service.read())
        assertEquals(1, offline.env.requests.count { it.method == HttpMethod.Get })

        val env = AccountTestEnv { jsonResponse(500, problemBody(500, "INTERNAL_ERROR")) }
        env.signedIn()
        val failed = DefaultDeletionPlanService(AccountApi(env.auth.client), FakeBudgetWallets(), FakeMemberships(emptyMap()), SessionManager(env.auth.repository, env.auth.entitlementsApi, env.events, backgroundScope))
            .read()
        assertEquals(PlanOutcome.Failed(ApiErrorCode.INTERNAL_ERROR, "req-42"), failed)
    }
}
