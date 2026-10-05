package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.dataBody
import com.fares.saveme.feature.sharing.domain.model.MemberActionResult
import com.fares.saveme.feature.sharing.domain.model.WithdrawResult
import com.fares.saveme.feature.sharing.invitationJson
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.memberJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Role changes, removals, withdrawals and the plan note (spec 013 FR-007, FR-017, FR-018, FR-020, FR-021, FR-028). */
class SharingServiceMembersTest {

    private fun member(role: String, version: Long = 2, deleted: Boolean = false, id: String = "m-u2", user: String = "u2") =
        memberJson(id, user, "Member 2", role).replace("\"version\":1", "\"version\":$version")
            .let { if (deleted) it.replace("\"deleted_at\":null", "\"deleted_at\":\"2026-10-02T11:00:00Z\"") else it }

    /** Member `u2` is a viewer on the phone. */
    private suspend fun SharingTestEnv.viewerNow() {
        memberships.store(com.fares.saveme.feature.wallets.domain.MemberRecord("m-u2", "w1", "u2", "Member 2", "viewer", 1, "2026-09-02T00:00:00Z"))
    }

    @Test
    fun aRaiseChecksThePlanAndALoweringNeverDoes() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, dataBody(member("viewer", 3))) }
        env.signedIn()
        env.seedOwnerWallet(members = 2)
        env.viewerNow()
        env.entitlements.set(sharing = false, max = 1)
        assertEquals(MemberActionResult.NotEntitled("sharing.enabled"), env.service.changeRole("w1", "u2", WalletRole.Editor))
        assertEquals(0, env.requests.size)
        env.memberships.store(com.fares.saveme.feature.wallets.domain.MemberRecord("m-u2", "w1", "u2", "Member 2", "editor", 1, "2026-09-02T00:00:00Z"))
        assertEquals(MemberActionResult.Done, env.service.changeRole("w1", "u2", WalletRole.Viewer))
        assertEquals(1, env.requests.size)

        val full = SharingTestEnv(backgroundScope) { jsonResponse(200, dataBody(member("editor", 3))) }
        full.signedIn()
        full.seedOwnerWallet(members = 3)
        full.viewerNow()
        full.entitlements.set(sharing = true, max = 2)
        assertEquals(MemberActionResult.NotEntitled("sharing.max_members_per_wallet"), full.service.changeRole("w1", "u2", WalletRole.Editor))
    }

    @Test
    fun aRoleChangeSendsTheStoredMemberVersionAndStoresTheAnswer() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, dataBody(member("viewer", 3))) }
        env.signedIn()
        env.seedOwnerWallet(members = 2)
        assertEquals(MemberActionResult.Done, env.service.changeRole("w1", "u2", WalletRole.Viewer))
        val request = env.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/members/u2"))
        assertEquals("1", request.jsonBody()["version"].toString())
        assertEquals("viewer", env.service.observeMembers("w1").first().single { it.userId == "u2" }.roleWire)
    }

    @Test
    fun removingStoresATombstoneAndDropsTheRow() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, dataBody(member("editor", 3, deleted = true))) }
        env.signedIn()
        env.seedOwnerWallet(members = 2)
        assertEquals(MemberActionResult.Done, env.service.remove("w1", "u2"))
        assertEquals(listOf("u1"), env.service.observeMembers("w1").first().map { it.userId })
    }

    @Test
    fun everyAnswerBecomesAMemberActionResult() = runTest {
        suspend fun answer(status: Int, code: String, extra: String = "", action: suspend SharingTestEnv.() -> MemberActionResult): Pair<MemberActionResult, SharingTestEnv> {
            val env = SharingTestEnv(backgroundScope) { jsonResponse(status, problemBody(status, code, extra)) }
            env.signedIn()
            env.seedOwnerWallet(members = 2)
            return env.action() to env
        }
        val change: suspend SharingTestEnv.() -> MemberActionResult = { service.changeRole("w1", "u2", WalletRole.Viewer) }
        val (changed, withCurrent) = answer(409, "VERSION_CONFLICT", ""","current":${member("viewer", 5)}""", change)
        assertEquals(MemberActionResult.Changed, changed)
        assertEquals(5L, withCurrent.memberships.observe("w1").first().single { it.userId == "u2" }.version, "the server's copy is stored")
        assertTrue(withCurrent.runner.refreshes.any { it.first == ReadTarget.Wallet("w1") && it.second })
        assertEquals(MemberActionResult.OnlyOwner, answer(403, "FORBIDDEN", action = change).first)
        assertEquals(MemberActionResult.NotEntitled("sharing.enabled"), answer(403, "FEATURE_NOT_ENTITLED", ""","feature":"sharing.enabled"""", change).first)
        assertEquals(MemberActionResult.NotVerified, answer(403, "EMAIL_NOT_VERIFIED", action = change).first)
        assertEquals(MemberActionResult.Gone, answer(404, "NOT_FOUND", action = change).first)
        assertEquals(MemberActionResult.OwnerRole, answer(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/role","code":"IMMUTABLE_FIELD"}]""", change).first)
        assertEquals(MemberActionResult.Failed("req-42"), answer(500, "INTERNAL_ERROR", action = change).first)
        assertEquals(MemberActionResult.Offline, SharingTestEnv(backgroundScope).also { it.seedOwnerWallet(members = 2) }.service.changeRole("w1", "u2", WalletRole.Viewer))
    }

    @Test
    fun aFeatureEntitledRefusalAskedForTheEntitlementsAgainEvenWhenTheLocalCheckPassed() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(403, problemBody(403, "FEATURE_NOT_ENTITLED", ""","feature":"sharing.max_members_per_wallet"""")) }
        env.signedIn()
        env.seedOwnerWallet(members = 2)
        env.viewerNow()
        val result = env.service.changeRole("w1", "u2", WalletRole.Editor)
        assertEquals(MemberActionResult.NotEntitled("sharing.max_members_per_wallet"), result)
    }

    @Test
    fun theOwnRowAndTheOwnerRowAreRefusedLocallyAndNothingIsSent() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, dataBody(member("viewer", 3))) }
        env.signedIn()
        env.seedOwnerWallet(members = 2)
        assertEquals(MemberActionResult.OwnerRole, env.service.changeRole("w1", "u1", WalletRole.Viewer))
        assertEquals(MemberActionResult.OwnerRole, env.service.remove("w1", "u1"))
        assertEquals(MemberActionResult.Gone, env.service.remove("w1", "u9"))
        assertEquals(0, env.requests.size)
    }

    @Test
    fun aNonOwnerCannotManageMembers() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet(members = 2, role = "editor")
        assertEquals(MemberActionResult.OnlyOwner, env.service.remove("w1", "u2"))
    }

    @Test
    fun aSecondActionForTheSameWalletWhileOneRunsIsBusy() = runTest {
        val gate = CompletableDeferred<Unit>()
        val env = SharingTestEnv(backgroundScope) {
            gate.await()
            jsonResponse(200, dataBody(member("viewer", 3)))
        }
        env.signedIn()
        env.seedOwnerWallet(members = 3)
        val first = backgroundScope.launch { env.service.changeRole("w1", "u2", WalletRole.Viewer) }
        testScheduler.advanceUntilIdle()
        // The request leaves on the client's own threads: wait for it in real time.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withTimeout(10_000) { while (env.requests.isEmpty()) kotlinx.coroutines.delay(5) }
        }
        assertEquals(MemberActionResult.Busy, env.service.remove("w1", "u3"))
        gate.complete(Unit)
        first.join()
        assertEquals(1, env.requests.size)
    }

    @Test
    fun withdrawingASentInvitationQueuesTheDeleteWithItsVersionAndHidesIt() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet()
        env.seed(invitationJson(version = 2))
        env.shareLinks.offer("i1", "https://x/invite?token=abc")
        assertEquals(WithdrawResult.Withdrawn, env.service.withdraw("i1"))
        val change = assertNotNull(env.engine.get("invitation", "i1"))
        assertTrue(change.isDelete)
        assertEquals(2, change.baseVersion)
        assertNull(env.shareLinks.get("i1"))
        assertTrue(env.service.observeInvitations("w1").first().isEmpty(), "a waiting withdrawal hides the row")
    }

    @Test
    fun withdrawingANeverSentInvitationRemovesItWithNothingSent() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet()
        env.repository.insertPlaceholder("p1", "w1", "u1", "viewer", "a@b.co")
        env.engine.enqueue("invitation", "p1", com.fares.saveme.core.sync.SyncScope(com.fares.saveme.core.sync.ScopeKind.Wallet, "w1"), 0, "{}")
        assertEquals(WithdrawResult.Withdrawn, env.service.withdraw("p1"))
        assertNull(env.repository.get("p1"))
        assertNull(env.engine.get("invitation", "p1"))
        assertEquals(0, env.requests.size)
        assertEquals(WithdrawResult.Gone, env.service.withdraw("nope"))
    }

    @Test
    fun aNonOwnerCannotWithdraw() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet(role = "editor")
        env.seed()
        assertEquals(WithdrawResult.NotOwner, env.service.withdraw("i1"))
    }

    @Test
    fun thePlanNoteIsThePlansMaxWhenPeopleExceedItElseNull() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet(members = 3)
        env.entitlements.set(sharing = true, max = 2)
        assertEquals(2, env.service.observePlanNote("w1").first())
        env.entitlements.set(sharing = false, max = 5)
        assertEquals(1, env.service.observePlanNote("w1").first(), "sharing not included counts as one person")
        env.entitlements.set(sharing = true, max = 5)
        assertNull(env.service.observePlanNote("w1").first())
        env.entitlements.set(sharing = true, max = null)
        assertNull(env.service.observePlanNote("w1").first())
    }

    @Test
    fun aMemberSeesNoPlanNote() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet(members = 3, role = "editor")
        env.entitlements.set(sharing = true, max = 1)
        assertNull(env.service.observePlanNote("w1").first())
        assertIs<com.fares.saveme.feature.billing.domain.QuotaLimit.Limited>(env.entitlements.quota(com.fares.saveme.feature.billing.domain.QuotaFeature.MembersPerWallet))
    }
}
