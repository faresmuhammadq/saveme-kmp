package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.wallets.FakePostings
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.domain.model.CreateResult
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletsServiceListTest {
    @Test
    fun activeThenArchivedWithTotalsAndRefreshTime() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        env.signedIn()
        env.seed(walletJson("a"))
        env.seed(walletJson("b", archivedAt = "2026-09-02T00:00:00Z", opening = 5))
        env.scopeDao.markFeedRead("user:u1", "2026-10-02T10:00:00.000Z")
        val list = env.service.observeList().first()
        assertEquals(listOf("a"), list.active.map { it.id })
        assertEquals(listOf("b"), list.archived.map { it.id })
        assertEquals(listOf(25_000L), list.totals.map { it.totalMinor })
        assertNotNull(list.updatedAt)
    }

    @Test
    fun anUpgradedPhoneKeepsTheLegacyTimeUntilEveryFeedReadHasCompleted() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        env.signedIn()
        env.scopeDao.upsert(com.fares.saveme.core.database.SyncScopeEntity("wallets", "2026-09-30T08:00:00Z"))
        assertEquals("2026-09-30T08:00:00Z", env.service.observeList().first().updatedAt.toString().replace(".000", ""))
        env.scopeDao.markFeedRead("user:u1", "2026-10-02T10:00:00.000Z")
        assertEquals("2026-10-02T10:00:00Z", env.service.observeList().first().updatedAt.toString())
    }

    @Test
    fun aNeverRefreshedListHasNoUpdateTime() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        assertNull(env.service.observeList().first().updatedAt)
    }

    @Test
    fun aPlaceholderShowsItsOpeningBalanceAndAWaitingMark() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(503, com.fares.saveme.feature.auth.problemBody(503, "SERVICE_UNAVAILABLE")) }
        env.signedIn()
        env.service.create(WalletDraft("cash", "Cash", "EGP", 25_000, null, null)) as CreateResult.Created
        val view = env.service.observeList().first().active.single()
        assertEquals(25_000L, view.displayedBalanceMinor)
        assertTrue(view.waiting)
    }

    @Test
    fun unsentPostingsMoveTheBalanceAndKeepTheMark() = runTest {
        val postings = FakePostings()
        val env = WalletTestEnv(backgroundScope, postings) { jsonResponse(200, walletBody()) }
        env.signedIn()
        env.seed(walletJson(balance = 25_000))
        postings.delta.value = mapOf("w1" to -3_000L)
        postings.pending.value = setOf("w1")
        val view = env.service.observeWallet("w1").first()!!
        assertEquals(22_000L, view.displayedBalanceMinor)
        assertTrue(view.waiting)
    }

    @Test
    fun membersMarkYouAndKeepNullNames() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        env.signedIn()
        env.seed()
        // The members arrive with the wallet's feed.
        env.memberDao.insertAll(
            listOf(
                com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m1", "w1", "u1", null, "owner", 1, "2026-09-01T00:00:00Z"),
                com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m2", "w1", "u2", "Omar", "editor", 1, "2026-09-02T00:00:00Z"),
            ),
        )
        val members = env.service.observeMembers("w1").first()
        assertEquals(listOf(true, false), members.map { it.isYou })
        assertNull(members[0].displayName)
    }

    @Test
    fun anotherAccountsWalletsAreNotListed() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        env.signedIn()
        env.walletDao.upsert(com.fares.saveme.feature.wallets.data.local.WalletEntity(
            "x", "someone-else", "cash", "Hers", "EGP", 1, 1, null, null, null, false, "owner", 1, 1, "2026-09-01T00:00:00Z", "2026-09-01T00:00:00Z", "2026-09-01T00:00:00Z",
        ))
        assertEquals(emptyList(), env.service.observeList().first().active)
    }
}
