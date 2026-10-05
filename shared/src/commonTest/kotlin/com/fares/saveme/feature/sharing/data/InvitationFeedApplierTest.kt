package com.fares.saveme.feature.sharing.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.invitationJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A wallet's pending invitations from its feed: version guard, tombstones and sweep (spec 009, spec 013 FR-029). */
class InvitationFeedApplierTest {

    private val wallet = SyncScope(ScopeKind.Wallet, "w1")

    private fun json(text: String): JsonElement = AppJson.parseToJsonElement(text)

    @Test
    fun itConsumesInvitationRowsOfAWalletsFeed() = runTest {
        val applier = InvitationFeedApplier(SharingTestEnv(backgroundScope).repository)
        assertEquals(ScopeKind.Wallet, applier.scopeKind)
        assertEquals("invitation", applier.rowType)
    }

    @Test
    fun anOlderVersionNeverReplacesANewerOneAndAnEqualOneIsApplied() = runTest {
        val env = SharingTestEnv(backgroundScope)
        val applier = InvitationFeedApplier(env.repository)
        applier.apply(wallet, listOf(json(invitationJson("a", version = 3, role = "editor"))))
        applier.apply(wallet, listOf(json(invitationJson("a", version = 2, role = "viewer"))))
        assertEquals("editor", env.invitationDao.rows.value.getValue("a").role)
        applier.apply(wallet, listOf(json(invitationJson("a", version = 3, role = "viewer"))))
        assertEquals("viewer", env.invitationDao.rows.value.getValue("a").role)
        assertEquals("u1", env.invitationDao.rows.value.getValue("a").accountId)
    }

    @Test
    fun anUnknownRoleAndUnknownMembersAreKept() = runTest {
        val env = SharingTestEnv(backgroundScope)
        InvitationFeedApplier(env.repository).apply(wallet, listOf(json(invitationJson("a", role = "co_owner"))))
        assertEquals("co_owner", env.invitationDao.rows.value.getValue("a").role)
    }

    @Test
    fun aTombstoneOfEachClosedStatusRemovesTheRowUnlessAChangeWaits() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.signedIn()
        val statuses = listOf("accepted", "declined", "revoked", "expired")
        statuses.forEach { env.seed(invitationJson("gone-$it", version = 1)) }
        env.seed(invitationJson("queued", version = 1))
        env.engine.enqueueDelete("invitation", "queued", wallet, 1)
        InvitationFeedApplier(env.repository).apply(
            wallet,
            statuses.map { json(invitationJson("gone-$it", status = it, version = 2, deletedAt = "2026-10-03T00:00:00Z", email = null, channel = "email")) } +
                json(invitationJson("queued", status = "revoked", version = 2, deletedAt = "2026-10-03T00:00:00Z")),
        )
        statuses.forEach { assertNull(env.invitationDao.rows.value["gone-$it"], it) }
        assertNotNull(env.invitationDao.rows.value["queued"], "it stays until the withdrawal settles")
        assertEquals(1L, env.invitationDao.rows.value.getValue("queued").version)
    }

    @Test
    fun aFeedCopyNeverTouchesTheWaitingChange() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seed(invitationJson("a", version = 1))
        env.engine.enqueueDelete("invitation", "a", wallet, 1)
        InvitationFeedApplier(env.repository).apply(wallet, listOf(json(invitationJson("a", version = 1))))
        assertNotNull(env.engine.get("invitation", "a"))
    }

    @Test
    fun theSweepRemovesSyncedRowsOfTheScopeNotSeenButNeverPlaceholdersOrRowsWithAChange() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.signedIn()
        env.seed(invitationJson("seen", version = 1))
        env.seed(invitationJson("gone", version = 1))
        env.seed(invitationJson("queued", version = 1))
        env.seed(invitationJson("other", walletId = "w2", version = 1))
        env.repository.insertPlaceholder("placeholder", "w1", "u1", "viewer", null)
        env.engine.enqueueDelete("invitation", "queued", wallet, 1)
        InvitationFeedApplier(env.repository).sweep(wallet, setOf("seen"))
        assertEquals(setOf("seen", "queued", "other", "placeholder"), env.invitationDao.rows.value.keys)
    }
}
