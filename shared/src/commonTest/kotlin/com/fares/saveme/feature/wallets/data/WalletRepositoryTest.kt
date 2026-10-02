package com.fares.saveme.feature.wallets.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.memberJson
import com.fares.saveme.feature.wallets.pageOf
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WalletRepositoryTest {
    private val draft = WalletDraft("cash", "Pocket", "EGP", 25_000, null, null)

    @Test
    fun aPlaceholderIsOwnerWithBalanceEqualToOpening() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        env.repository.insertPlaceholder("p1", draft, "u1")
        val row = env.walletDao.rows.value.getValue("p1")
        assertEquals("owner", row.myRole)
        assertEquals(0L, row.version)
        assertEquals(25_000L, row.balanceMinor)
        assertEquals(false, row.currencyLocked)
    }

    @Test
    fun refreshAllMergesOnlyWhenEveryPageSucceeds() = runTest {
        val env = WalletTestEnv(backgroundScope) { request ->
            if (request.url.parameters["cursor"] == null) jsonResponse(200, pageOf(listOf(walletJson("a", name = "New A")), nextCursor = "c"))
            else jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE"))
        }
        env.signedIn()
        env.seed(walletJson("a", name = "Old A"))
        env.seed(walletJson("b"))
        assertIs<ApiResult.Failure>(env.repository.refreshAll())
        assertEquals("Old A", env.walletDao.rows.value.getValue("a").name)
        assertNotNull(env.walletDao.rows.value["b"])
        assertNull(env.scopeDao.rows.value["wallets"])
    }

    @Test
    fun anUnlistedSyncedWalletIsRemovedButQueuedAndPlaceholderRowsStay() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, pageOf(listOf(walletJson("a")))) }
        env.signedIn()
        env.seed(walletJson("a"))
        env.seed(walletJson("gone", version = 2))
        env.seed(walletJson("queued", version = 2))
        env.repository.insertPlaceholder("fresh", draft, "u1")
        env.memberDao.insertAll(listOf(com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m1", "gone", "u1", null, "owner", 1, "2026-09-01T00:00:00Z")))
        env.engine.enqueue("wallet", "queued", SyncScope(ScopeKind.Wallet, "queued"), 2, "{}")
        assertIs<ApiResult.Success<Unit>>(env.repository.refreshAll())
        assertNull(env.walletDao.rows.value["gone"])
        assertEquals(emptyList(), env.memberDao.rows.value)
        assertNotNull(env.walletDao.rows.value["queued"])
        assertNotNull(env.walletDao.rows.value["fresh"])
        assertEquals(listOf("gone"), env.removed)
        assertNotNull(env.scopeDao.rows.value["wallets"])
    }

    @Test
    fun refreshMembersReplacesTheSet() = runTest {
        var round = 0
        val env = WalletTestEnv(backgroundScope) {
            if (round++ == 0) jsonResponse(200, pageOf(listOf(memberJson("m1", "u1", "Sara"), memberJson("m2", "u2", "Omar", "editor"))))
            else jsonResponse(200, pageOf(listOf(memberJson("m1", "u1", "Sara"))))
        }
        env.signedIn()
        env.seed()
        env.repository.refreshMembers("w1")
        assertEquals(2, env.repository.observeMembers("w1").first().size)
        env.repository.refreshMembers("w1")
        assertEquals(listOf("m1"), env.repository.observeMembers("w1").first().map { it.id })
    }

    @Test
    fun a404OnGetRemovesTheWalletUnlessAChangeWaits() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
        env.signedIn()
        env.seed(walletJson("a"))
        env.seed(walletJson("b"))
        env.engine.enqueue("wallet", "b", SyncScope(ScopeKind.Wallet, "b"), 1, "{}")
        env.repository.refreshOne("a")
        env.repository.refreshOne("b")
        assertNull(env.walletDao.rows.value["a"])
        assertNotNull(env.walletDao.rows.value["b"])
    }

    @Test
    fun clearEmptiesBothTables() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        env.seed()
        env.memberDao.insertAll(listOf(com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m1", "w1", "u1", null, "owner", 1, "2026-09-01T00:00:00Z")))
        env.repository.clear()
        assertEquals(emptyMap(), env.walletDao.rows.value)
        assertEquals(emptyList(), env.memberDao.rows.value)
    }
}
