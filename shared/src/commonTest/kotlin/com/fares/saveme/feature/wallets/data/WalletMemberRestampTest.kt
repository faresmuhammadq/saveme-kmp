package com.fares.saveme.feature.wallets.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.memberJson
import com.fares.saveme.feature.wallets.walletBody
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A member's new name arrives as a row with the SAME version (backend ADR-0021 re-stamps keep it) and replaces the copy
 * (spec 011 US2, FR-012, FR-013).
 */
class WalletMemberRestampTest {

    private val wallet = SyncScope(ScopeKind.Wallet, "w1")
    private val user = SyncScope(ScopeKind.User, "u1")

    private fun json(text: String): JsonElement = AppJson.parseToJsonElement(text)

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = WalletTestEnv(scope) { jsonResponse(200, walletBody()) }

    private fun member(name: String?, version: Long = 1, id: String = "m2", userId: String = "u2", role: String = "editor") =
        memberJson(id, userId, name, role).replace("\"version\":1", "\"version\":$version")

    @Test
    fun aWalletFeedRowWithTheStoredVersionAndANewNameReplacesTheName() = runTest {
        val env = env(backgroundScope)
        val applier = WalletMemberFeedApplier(env.repository)
        applier.apply(wallet, listOf(json(member("Sara", version = 3))))

        applier.apply(wallet, listOf(json(member("Sara M.", version = 3))))

        assertEquals("Sara M.", env.memberDao.rows.value.single().displayName)
        assertEquals(3L, env.memberDao.rows.value.single().version, "the version did not move")
    }

    @Test
    fun anOlderVersionIsStillIgnored() = runTest {
        val env = env(backgroundScope)
        val applier = WalletMemberFeedApplier(env.repository)
        applier.apply(wallet, listOf(json(member("Sara M.", version = 3))))
        applier.apply(wallet, listOf(json(member("Old name", version = 2))))
        assertEquals("Sara M.", env.memberDao.rows.value.single().displayName)
    }

    @Test
    fun aNameRemovedByTheirOwnerArrivesAsNull() = runTest {
        val env = env(backgroundScope)
        val applier = WalletMemberFeedApplier(env.repository)
        applier.apply(wallet, listOf(json(member("Sara", version = 1))))
        applier.apply(wallet, listOf(json(member(null, version = 1))))
        assertEquals(null, env.memberDao.rows.value.single().displayName)
    }

    @Test
    fun thePersonsOwnMembershipOnTheirFeedAppliesAnEqualVersionToo() = runTest {
        val env = env(backgroundScope)
        val applier = OwnMembershipFeedApplier(env.repository)
        applier.apply(user, listOf(json(member("Sara", version = 2, id = "m1", userId = "u1", role = "owner"))))
        applier.apply(user, listOf(json(member("Sara Mohamed", version = 2, id = "m1", userId = "u1", role = "owner"))))
        assertEquals("Sara Mohamed", env.memberDao.rows.value.single().displayName)
    }

    @Test
    fun theMembersListAndTheTransferChoiceSeeTheNewNameWithNoOtherAction() = runTest {
        val env = env(backgroundScope)
        val applier = WalletMemberFeedApplier(env.repository)
        applier.apply(wallet, listOf(json(member("Sara", version = 1))))
        assertEquals(listOf("Sara"), env.repository.observeMembers("w1").first().map { it.displayName })

        applier.apply(wallet, listOf(json(member("Sara M.", version = 1))))

        assertEquals(listOf("Sara M."), env.repository.observeMembers("w1").first().map { it.displayName })
    }
}
