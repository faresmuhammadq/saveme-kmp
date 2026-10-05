package com.fares.saveme.feature.wallets.domain

import app.cash.turbine.test
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.wallets.data.dto.WalletDto
import com.fares.saveme.feature.wallets.memberJson
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What sharing may do with the members through wallets' domain API (spec 013 R-01, R-12, FR-022). */
class WalletMembershipsTest {

    private fun record(id: String = "m2", user: String = "u2", role: String = "editor", version: Long = 2, deleted: Boolean = false, wallet: String = "w1") =
        MemberRecord(id, wallet, user, "Karim", role, version, "2026-09-02T00:00:00Z", deleted)

    @Test
    fun storeAppliesEqualAndNewerVersionsAndIgnoresOlderOnes() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet(members = 2)
        env.memberships.store(record(role = "viewer", version = 5))
        env.memberships.store(record(role = "editor", version = 4))
        assertEquals("viewer", env.memberDao.rows.value.single { it.id == "m2" }.role)
        env.memberships.store(record(role = "editor", version = 5))
        assertEquals("editor", env.memberDao.rows.value.single { it.id == "m2" }.role, "an equal version is applied (spec 011 R-08)")
        env.memberships.store(record(role = "viewer", version = 6))
        assertEquals("viewer", env.memberDao.rows.value.single { it.id == "m2" }.role)
    }

    @Test
    fun aTombstoneDeletesTheRow() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet(members = 2)
        env.memberships.store(record(version = 3, deleted = true))
        assertNull(env.memberDao.rows.value.firstOrNull { it.id == "m2" })
    }

    @Test
    fun thePersonsOwnLiveRowSetsTheWalletsRoleWhenTheWalletExists() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet(role = "editor")
        assertEquals("editor", env.walletDao.rows.value.getValue("w1").myRole)
        env.memberships.store(record("m-u1", "u1", role = "viewer", version = 5))
        assertEquals("viewer", env.walletDao.rows.value.getValue("w1").myRole)
        // Another person's row never changes the wallet's role.
        env.memberships.store(record("m2", "u2", role = "owner", version = 5))
        assertEquals("viewer", env.walletDao.rows.value.getValue("w1").myRole)
    }

    @Test
    fun aMissingWalletIsLeftAlone() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.memberships.store(record("m-u1", "u1", role = "editor", version = 1, wallet = "w9"))
        assertEquals(emptyList(), env.walletDao.rows.value.keys.toList())
        assertEquals("editor", env.memberDao.rows.value.single().role)
    }

    @Test
    fun ownRoleAndVersionComeFromTheWalletAndTheOwnRow() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet(members = 2)
        assertEquals("owner", env.memberships.ownRole("w1")?.wire)
        assertEquals(1L, env.memberships.ownMembershipVersion("w1"))
        assertNull(env.memberships.ownRole("nope"))
        assertNull(env.memberships.ownMembershipVersion("nope"))
        // Before the wallet record arrives the own member row alone gives the role.
        env.memberships.store(record("m-u1b", "u1", role = "viewer", version = 1, wallet = "w2"))
        assertEquals("viewer", env.memberships.ownRole("w2")?.wire)
    }

    @Test
    fun observeListsTheOwnerFirstThenInJoiningOrder() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet(members = 3, role = "editor")
        env.memberships.store(record("m-owner", "u9", role = "owner", version = 1).copy(createdAt = "2026-09-09T00:00:00Z"))
        env.memberships.observe("w1").test {
            assertEquals(listOf("u9", "u1", "u2", "u3"), awaitItem().map { it.userId })
        }
    }

    @Test
    fun leftWalletRunsAccessLostAndTheParticipantsAreCalled() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet(members = 2, role = "editor")
        env.scopeDao.storeFeedPage("wallet:w1", "pos", 5)
        env.memberships.leftWallet("w1")
        assertNull(env.walletDao.rows.value["w1"])
        assertEquals(emptyList(), env.memberDao.rows.value)
        assertEquals(listOf("w1"), env.removedWallets)
        assertNull(env.scopeDao.feedCursor("wallet:w1"))
        assertEquals("Home", env.left.notice.value)
        env.left.observe("w1").test { assertTrue(awaitItem()) }
    }

    @Test
    fun theJoiningStateIsTrueOnlyWhileTheOwnRowExistsAndTheWalletDoesNot() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        env.wallets.observeJoining("w1").test {
            assertFalse(awaitItem())
            env.memberships.store(record("m-u1", "u1", role = "editor", version = 1))
            assertTrue(awaitItem())
            env.walletRepository.storeServerCopy(AppJson.decodeFromString(WalletDto.serializer(), walletJson("w1", role = "editor")))
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // Another person's row alone is not "joining".
        env.memberships.store(record("m-x", "u2", wallet = "w7"))
        assertFalse(env.wallets.observeJoining("w7").first())
    }

    @Test
    fun theDefaultSharingGlanceSaysNothingIsPendingAndNoPlanNote() = runTest {
        NoSharingGlance.observePendingCount("w1").test { assertEquals(0, awaitItem()); awaitComplete() }
        NoSharingGlance.observePlanLimited("w1").test { assertFalse(awaitItem()); awaitComplete() }
        assertNotNull(NoSharingGlance)
    }
}
