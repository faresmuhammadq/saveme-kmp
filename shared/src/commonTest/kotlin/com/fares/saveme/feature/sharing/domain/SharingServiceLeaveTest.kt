package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.dataBody
import com.fares.saveme.feature.sharing.domain.model.LeaveCheck
import com.fares.saveme.feature.sharing.domain.model.LeaveResult
import com.fares.saveme.feature.wallets.memberJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Leaving a wallet: upload first, count what still waits, and every answer of A8 (spec 013 FR-019). */
class SharingServiceLeaveTest {

    private fun tombstone() = dataBody(
        memberJson("m-u1", "u1", "Sara", "editor").replace("\"version\":1", "\"version\":4").replace("\"deleted_at\":null", "\"deleted_at\":\"2026-10-02T11:00:00Z\""),
    )

    @Test
    fun theCheckUploadsFirstThenCountsWhatStillWaitsInTheWalletScope() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet(role = "editor")
        val scope = SyncScope(ScopeKind.Wallet, "w1")
        env.engine.enqueue("invitation", "x1", scope, 0, "{}")
        env.engine.enqueue("invitation", "x2", SyncScope(ScopeKind.Wallet, "w2"), 0, "{}")
        assertEquals(LeaveCheck.Ready(1), env.service.leaveCheck("w1"))
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.Wallet("w1") to true), env.runner.refreshes)
    }

    @Test
    fun theOwnerAndOfflineAreTheirOwnChecks() = runTest {
        val owner = SharingTestEnv(backgroundScope)
        owner.seedOwnerWallet()
        assertEquals(LeaveCheck.Owner, owner.service.leaveCheck("w1"))
        val offline = SharingTestEnv(backgroundScope)
        offline.seedOwnerWallet(role = "editor")
        offline.runner.outcome = ReadOutcome.Offline
        assertEquals(LeaveCheck.Offline, offline.service.leaveCheck("w1"))
    }

    @Test
    fun leavingSendsTheOwnMembershipVersionAndClearsTheWalletFromThePhone() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, tombstone()) }
        env.signedIn()
        env.seedOwnerWallet(role = "editor")
        assertEquals(LeaveResult.Left("Home"), env.service.leave("w1"))
        assertTrue(env.requests.single().url.encodedPath.endsWith("/wallets/w1/leave"))
        assertNull(env.wallets.observeWallet("w1").first())
        assertTrue(env.removedWallets.contains("w1"), "the participants ran (wallet deletion hooks)")
        assertEquals("Home", env.left.notice.value)
    }

    @Test
    fun anAlreadyEndedMembershipIsDone() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
        env.signedIn()
        env.seedOwnerWallet(role = "editor")
        assertEquals(LeaveResult.Left("Home"), env.service.leave("w1"))
        assertNull(env.wallets.observeWallet("w1").first())
    }

    @Test
    fun theOtherAnswers() = runTest {
        val owner = SharingTestEnv(backgroundScope) { jsonResponse(403, problemBody(403, "FORBIDDEN")) }
        owner.signedIn()
        owner.seedOwnerWallet(role = "editor")
        assertEquals(LeaveResult.OwnerCannotLeave, owner.service.leave("w1"))

        val stale = SharingTestEnv(backgroundScope) {
            jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${memberJson("m-u1", "u1", "Sara", "viewer").replace("\"version\":1", "\"version\":7")}"""))
        }
        stale.signedIn()
        stale.seedOwnerWallet(role = "editor")
        assertEquals(LeaveResult.Changed, stale.service.leave("w1"))
        assertEquals(7L, stale.memberships.ownMembershipVersion("w1"))
        assertTrue(stale.wallets.observeWallet("w1").first() != null, "the wallet stays")

        val offline = SharingTestEnv(backgroundScope)
        offline.seedOwnerWallet(role = "editor")
        assertEquals(LeaveResult.Offline, offline.service.leave("w1"))

        val broken = SharingTestEnv(backgroundScope) { jsonResponse(500, problemBody(500, "INTERNAL_ERROR")) }
        broken.signedIn()
        broken.seedOwnerWallet(role = "editor")
        assertEquals(LeaveResult.Failed("req-42"), broken.service.leave("w1"))
    }
}
