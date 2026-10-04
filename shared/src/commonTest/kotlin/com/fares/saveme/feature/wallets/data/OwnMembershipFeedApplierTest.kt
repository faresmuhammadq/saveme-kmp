package com.fares.saveme.feature.wallets.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.memberJson
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The person's own memberships on their feed decide which wallets are followed and which leave (backend ADR-0017, spec 009 FR-010, FR-011). */
class OwnMembershipFeedApplierTest {

    private val user = SyncScope(ScopeKind.User, "u1")

    private fun json(text: String): JsonElement = AppJson.parseToJsonElement(text)

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = WalletTestEnv(scope) { jsonResponse(200, walletBody()) }

    private fun live(id: String = "m1", walletId: String = "w1", role: String = "owner", version: Long = 1) =
        memberJson(id, "u1", "Sara", role, walletId = walletId).replace("\"version\":1", "\"version\":$version")

    private fun ended(id: String = "m1", walletId: String = "w1", version: Long = 2) =
        live(id, walletId, version = version).replace("\"deleted_at\":null", "\"deleted_at\":\"2026-10-02T00:00:00Z\"")

    @Test
    fun aLiveOwnMembershipIsStoredWithItsRoleAndAnOlderVersionIsIgnored() = runTest {
        val env = env(backgroundScope)
        val applier = OwnMembershipFeedApplier(env.repository)
        applier.apply(user, listOf(json(live(role = "editor", version = 2))))
        assertEquals("editor", env.memberDao.rows.value.single().role)
        applier.apply(user, listOf(json(live(role = "viewer", version = 1))))
        assertEquals("editor", env.memberDao.rows.value.single().role)
        applier.apply(user, listOf(json(live(role = "viewer", version = 3))))
        assertEquals("viewer", env.memberDao.rows.value.single().role, "a role change is reflected")
    }

    @Test
    fun anEndedMembershipRemovesTheWalletWithItsMembersParticipantsAndPosition() = runTest {
        val env = env(backgroundScope)
        env.seed(walletJson("w1", version = 2))
        env.memberDao.insertAll(listOf(com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m2", "w1", "u2", null, "editor", 1, "2026-09-01T00:00:00Z")))
        env.scopeDao.storeFeedPage("wallet:w1", "pos", null)
        env.scopeDao.markFeedRead("wallet:w1", "2026-10-01T00:00:00.000Z")

        OwnMembershipFeedApplier(env.repository).apply(user, listOf(json(live()), json(ended())))

        assertNull(env.walletDao.rows.value["w1"])
        assertEquals(emptyList(), env.memberDao.rows.value)
        assertEquals(listOf("w1"), env.removed, "references, transactions and their queued changes go through the participants")
        assertNull(env.scopeDao.feedCursor("wallet:w1"))
        assertNull(env.scopeDao.rows.value["wallet:w1"]!!.feedReadAt)
    }

    @Test
    fun aWalletMadeOnThisPhoneAndNotYetAcceptedIsNeverRemovedThisWay() = runTest {
        val env = env(backgroundScope)
        env.repository.insertPlaceholder("w1", WalletDraft("cash", "New", "EGP", 100, null, null), "u1")
        env.engine.enqueue("wallet", "w1", SyncScope(ScopeKind.Wallet, "w1"), 0, "{}")
        OwnMembershipFeedApplier(env.repository).apply(user, listOf(json(ended())))
        assertNotNull(env.walletDao.rows.value["w1"])
        assertEquals(emptyList(), env.removed)
        assertNotNull(env.engine.get("wallet", "w1"))
    }

    @Test
    fun aWalletWhoseOwnRecordHasAWaitingEditStaysAndAcceptingTheDeletionRemovesItWithItsData() = runTest {
        val env = env(backgroundScope)
        env.seed(walletJson("w1", version = 2))
        env.engine.enqueue("wallet", "w1", SyncScope(ScopeKind.Wallet, "w1"), 2, AppJson.encodeToString(WalletDraft.serializer(), WalletDraft("cash", "Mine", "EGP", 25_000, null, null)))
        env.scopeDao.storeFeedPage("wallet:w1", "pos", null)

        OwnMembershipFeedApplier(env.repository).apply(user, listOf(json(ended())))

        assertNotNull(env.walletDao.rows.value["w1"], "it stays until the person settles it")
        assertEquals(emptyList(), env.removed)
        assertNull(env.scopeDao.feedCursor("wallet:w1"), "but it is no longer read from where it was")
        assertEquals(emptyList(), env.memberDao.rows.value, "the ended own membership row is gone")

        // The edit clashes with the deletion; "Accept the deletion" applies the tombstone and removes the wallet and its data.
        env.dao().upsert(env.engine.get("wallet", "w1").let { env.pendingDao.rows.value["wallet" to "w1"]!!.copy(state = ChangeState.Clash.name, clashJson = walletJson("w1", version = 3, deletedAt = "2026-10-02T00:00:00Z")) })
        env.engine.useTheirs("wallet", "w1")
        assertNull(env.walletDao.rows.value["w1"])
        assertEquals(listOf("w1"), env.removed)
    }

    @Test
    fun aMembershipThatIsNotThePersonsOwnIsIgnored() = runTest {
        val env = env(backgroundScope)
        OwnMembershipFeedApplier(env.repository).apply(user, listOf(json(memberJson("m9", "u9", "Omar", "editor"))))
        assertEquals(emptyList(), env.memberDao.rows.value)
    }

    @Test
    fun aMissingOwnMembershipInACompleteReadRemovesNothing() = runTest {
        val env = env(backgroundScope)
        env.seed(walletJson("w1", version = 2))
        OwnMembershipFeedApplier(env.repository).sweep(user, emptySet())
        assertNotNull(env.walletDao.rows.value["w1"], "only the wallet's own feed, answering 404, removes it")
    }

    private fun WalletTestEnv.dao() = pendingDao
}
