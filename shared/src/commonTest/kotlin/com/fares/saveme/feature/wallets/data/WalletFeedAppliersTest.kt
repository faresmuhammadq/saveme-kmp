package com.fares.saveme.feature.wallets.data

import com.fares.saveme.core.network.AppJson
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

/** A wallet, its members and the person's role from a wallet's feed (spec 009 FR-004, FR-005, FR-007, FR-013). */
class WalletFeedAppliersTest {

    private val wallet = SyncScope(ScopeKind.Wallet, "w1")

    private fun json(text: String): JsonElement = AppJson.parseToJsonElement(text)

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = WalletTestEnv(scope) { jsonResponse(200, walletBody()) }

    @Test
    fun theWalletRecordIsAppliedWhenItsVersionIsTheSameOrNewerAndAnEqualVersionBringsTheNewBalanceLockAndRole() = runTest {
        val env = env(backgroundScope)
        val applier = WalletFeedApplier(env.repository)
        applier.apply(wallet, listOf(json(walletJson(version = 3, balance = 25_000, locked = false, role = "owner"))))

        // The wallet record is re-sent with the same version when only its balance moved.
        applier.apply(wallet, listOf(json(walletJson(version = 3, balance = 21_000, locked = true, role = "editor"))))
        val row = env.walletDao.rows.value.getValue("w1")
        assertEquals(21_000L, row.balanceMinor)
        assertEquals(true, row.currencyLocked)
        assertEquals("editor", row.myRole)

        applier.apply(wallet, listOf(json(walletJson(version = 2, name = "Stale", balance = 1))))
        assertEquals("Pocket", env.walletDao.rows.value.getValue("w1").name)
        assertEquals(21_000L, env.walletDao.rows.value.getValue("w1").balanceMinor)
    }

    @Test
    fun aTombstoneRemovesTheWalletThroughRemoveWithItsParticipants() = runTest {
        val env = env(backgroundScope)
        env.seed(walletJson(version = 2))
        env.memberDao.insertAll(listOf(com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m1", "w1", "u1", null, "owner", 1, "2026-09-01T00:00:00Z")))
        WalletFeedApplier(env.repository).apply(wallet, listOf(json(walletJson(version = 3, deletedAt = "2026-10-02T00:00:00Z"))))
        assertNull(env.walletDao.rows.value["w1"])
        assertEquals(emptyList(), env.memberDao.rows.value)
        assertEquals(listOf("w1"), env.removed)
    }

    @Test
    fun aTombstoneLeavesAWalletWhoseRecordHasAWaitingChange() = runTest {
        val env = env(backgroundScope)
        env.seed(walletJson(version = 2))
        env.engine.enqueue("wallet", "w1", wallet, 2, AppJson.encodeToString(WalletDraft.serializer(), WalletDraft("cash", "Mine", "EGP", 25_000, null, null)))
        WalletFeedApplier(env.repository).apply(wallet, listOf(json(walletJson(version = 3, deletedAt = "2026-10-02T00:00:00Z"))))
        assertNotNull(env.walletDao.rows.value["w1"], "it stays until the person settles the clash with today's choices")
        assertEquals(emptyList(), env.removed)
        assertNotNull(env.engine.get("wallet", "w1"))
    }

    @Test
    fun aFeedCopyOfAPlaceholderWalletIsAcceptedOnceItsCreateWasAccepted() = runTest {
        val env = env(backgroundScope)
        env.repository.insertPlaceholder("w1", WalletDraft("cash", "Pocket", "EGP", 25_000, null, null), "u1")
        WalletFeedApplier(env.repository).apply(wallet, listOf(json(walletJson(version = 1))))
        assertEquals(1L, env.walletDao.rows.value.getValue("w1").version)
    }

    @Test
    fun walletMembersAreUpsertedByVersionAndADeletionRemovesOnlyThatMember() = runTest {
        val env = env(backgroundScope)
        val applier = WalletMemberFeedApplier(env.repository)
        applier.apply(wallet, listOf(json(memberJson("m1", "u1", "Sara")), json(memberJson("m2", "u2", "Omar", "editor"))))
        assertEquals(setOf("m1", "m2"), env.memberDao.rows.value.map { it.id }.toSet())

        applier.apply(wallet, listOf(json(memberJson("m2", "u2", "Stale name", "viewer").replace("\"version\":1", "\"version\":0"))))
        assertEquals("Omar", env.memberDao.rows.value.single { it.id == "m2" }.displayName)

        applier.apply(wallet, listOf(json(memberJson("m2", "u2", "Omar").replace("\"deleted_at\":null", "\"deleted_at\":\"2026-10-02T00:00:00Z\"").replace("\"version\":1", "\"version\":2"))))
        assertEquals(listOf("m1"), env.memberDao.rows.value.map { it.id })
    }

    @Test
    fun aMemberSweepIsPerWallet() = runTest {
        val env = env(backgroundScope)
        env.memberDao.insertAll(
            listOf(
                com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m1", "w1", "u1", null, "owner", 1, "2026-09-01T00:00:00Z"),
                com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m2", "w1", "u2", null, "editor", 1, "2026-09-01T00:00:00Z"),
                com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m9", "w2", "u2", null, "editor", 1, "2026-09-01T00:00:00Z"),
            ),
        )
        WalletMemberFeedApplier(env.repository).sweep(wallet, setOf("m1"))
        assertEquals(setOf("m1", "m9"), env.memberDao.rows.value.map { it.id }.toSet())
    }

    @Test
    fun aWalletRecordNeverReturnedByAReadFromTheStartRemovesTheWalletUnlessAChangeWaitsOrItIsAPlaceholder() = runTest {
        val env = env(backgroundScope)
        env.seed(walletJson("w1", version = 2))
        env.seed(walletJson("w2", version = 2))
        env.engine.enqueue("wallet", "w2", SyncScope(ScopeKind.Wallet, "w2"), 2, "{}")
        env.repository.insertPlaceholder("w3", WalletDraft("cash", "New", "EGP", 1, null, null), "u1")
        val applier = WalletFeedApplier(env.repository)
        applier.sweep(SyncScope(ScopeKind.Wallet, "w1"), emptySet())
        applier.sweep(SyncScope(ScopeKind.Wallet, "w2"), emptySet())
        applier.sweep(SyncScope(ScopeKind.Wallet, "w3"), emptySet())
        assertNull(env.walletDao.rows.value["w1"])
        assertNotNull(env.walletDao.rows.value["w2"])
        assertNotNull(env.walletDao.rows.value["w3"])
        applier.sweep(SyncScope(ScopeKind.Wallet, "w2"), setOf("w2"))
    }
}
