package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.data.local.WalletMemberEntity
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the puller asks of wallets (spec 009 FR-002, FR-011, research R-03). */
class DefaultWalletScopesTest {

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = WalletTestEnv(scope) { jsonResponse(200, walletBody()) }

    private fun scopes(env: WalletTestEnv) = DefaultWalletScopes(env.repository, accountId = { "u1" })

    private fun own(id: String, walletId: String) = WalletMemberEntity(id, walletId, "u1", "Me", "owner", 1, "2026-09-01T00:00:00Z")

    @Test
    fun followedAreTheServerAcceptedWalletsPlusLiveOwnMembershipsNeverPlaceholders() = runTest {
        val env = env(backgroundScope)
        env.seed(walletJson("held", version = 2))
        env.repository.insertPlaceholder("fresh", WalletDraft("cash", "New", "EGP", 1, null, null), "u1")
        env.memberDao.insertAll(listOf(own("m1", "gained"), own("m2", "held")))
        env.memberDao.insertAll(listOf(WalletMemberEntity("m3", "other", "u2", null, "owner", 1, "2026-09-01T00:00:00Z")))
        assertEquals(setOf("held", "gained"), scopes(env).followed().toSet())
    }

    @Test
    fun aWalletIsAcceptedWhenItHoldsAServerVersionOrAnOwnMembershipLives() = runTest {
        val env = env(backgroundScope)
        env.seed(walletJson("held", version = 2))
        env.repository.insertPlaceholder("fresh", WalletDraft("cash", "New", "EGP", 1, null, null), "u1")
        env.memberDao.insertAll(listOf(own("m1", "gained")))
        val scopes = scopes(env)
        assertTrue(scopes.isAccepted("held"))
        assertTrue(scopes.isAccepted("gained"))
        assertFalse(scopes.isAccepted("fresh"))
        assertFalse(scopes.isAccepted("unknown"))
    }

    @Test
    fun unconfirmedListsHeldWalletsWhoseMembershipWasNotSeen() = runTest {
        val env = env(backgroundScope)
        env.seed(walletJson("a", version = 2))
        env.seed(walletJson("b", version = 2))
        env.repository.insertPlaceholder("fresh", WalletDraft("cash", "New", "EGP", 1, null, null), "u1")
        assertEquals(listOf("b"), scopes(env).unconfirmed(setOf("a")))
    }

    @Test
    fun accessLostRemovesAWalletAndClearsItsPositionButNeverAPlaceholder() = runTest {
        val env = env(backgroundScope)
        env.seed(walletJson("held", version = 2))
        env.repository.insertPlaceholder("fresh", WalletDraft("cash", "New", "EGP", 1, null, null), "u1")
        env.scopeDao.storeFeedPage("wallet:held", "pos", null)
        env.scopeDao.storeFeedPage("wallet:fresh", "pos", null)
        val scopes = scopes(env)

        scopes.accessLost("held")
        scopes.accessLost("fresh")

        assertNull(env.walletDao.rows.value["held"])
        assertNull(env.scopeDao.feedCursor("wallet:held"))
        assertNotNull(env.walletDao.rows.value["fresh"])
        assertEquals("pos", env.scopeDao.feedCursor("wallet:fresh"))
        assertEquals(listOf("held"), env.removed)
    }

    @Test
    fun accessLostKeepsAWalletWhoseRecordHasAWaitingChange() = runTest {
        val env = env(backgroundScope)
        env.seed(walletJson("held", version = 2))
        env.engine.enqueue("wallet", "held", com.fares.saveme.core.sync.SyncScope(com.fares.saveme.core.sync.ScopeKind.Wallet, "held"), 2, "{}")
        scopes(env).accessLost("held")
        assertNotNull(env.walletDao.rows.value["held"])
        assertEquals(emptyList(), env.removed)
    }

    @Test
    fun discardingAChangeOfAWalletTheServerNoLongerSharesReadsItAgainFromTheStart() = runTest {
        val env = env(backgroundScope)
        env.seed(walletJson("w1", version = 2))
        env.scopeDao.storeFeedPage("wallet:w1", "pos", null)
        env.engine.enqueue("wallet", "w1", com.fares.saveme.core.sync.SyncScope(com.fares.saveme.core.sync.ScopeKind.Wallet, "w1"), 2, "{}")
        env.engine.discard("wallet", "w1")
        assertNull(env.scopeDao.feedCursor("wallet:w1"), "no live membership: the next read decides")
        assertNotNull(env.walletDao.rows.value["w1"])

        env.scopeDao.storeFeedPage("wallet:w1", "pos2", null)
        env.memberDao.insertAll(listOf(own("m1", "w1")))
        env.engine.enqueue("wallet", "w1", com.fares.saveme.core.sync.SyncScope(com.fares.saveme.core.sync.ScopeKind.Wallet, "w1"), 2, "{}")
        env.engine.discard("wallet", "w1")
        assertEquals("pos2", env.scopeDao.feedCursor("wallet:w1"), "a wallet the person still belongs to keeps its position")
    }
}
