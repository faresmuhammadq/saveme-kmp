package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.domain.model.CreateResult
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletField
import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem
import com.fares.saveme.feature.wallets.signedInAccount
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletsServiceCreateTest {
    private val draft = WalletDraft("cash", "  Pocket  ", "EGP", 25_000, "cash", null)

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = WalletTestEnv(scope) { jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE")) }

    @Test
    fun anUnverifiedPersonCreatesNothing() = runTest {
        val env = env(backgroundScope)
        env.authState.value = AuthState.SignedIn(signedInAccount.copy(emailVerified = false))
        assertEquals(CreateResult.NotVerified, env.service.create(draft))
        assertEquals(emptyMap(), env.walletDao.rows.value)
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun theGateCountsOwnedWalletsIncludingArchivedAndNotThoseBeingDeleted() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(walletJson("a"))
        env.seed(walletJson("b", archivedAt = "2026-09-02T00:00:00Z"))
        env.seed(walletJson("c", role = "editor"))
        env.seed(walletJson("d"))
        env.engine.enqueueDelete("wallet", "d", com.fares.saveme.core.sync.SyncScope(ScopeKind.Wallet, "d"), 1)
        env.entitlements.walletLimit = 2
        assertEquals(CreateResult.NotEntitled, env.service.create(draft))
        env.entitlements.walletLimit = 3
        assertIs<CreateResult.Created>(env.service.create(draft))
    }

    @Test
    fun unlimitedPlansAlwaysAllowAnother() = runTest {
        val env = env(backgroundScope)
        env.entitlements.walletLimit = null
        repeat(5) { env.seed(walletJson("w$it")) }
        assertTrue(env.service.canCreateAnother())
    }

    @Test
    fun aValidCreateSavesAPlaceholderAndQueuesVersionZero() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        val result = assertIs<CreateResult.Created>(env.service.create(draft))
        assertEquals("w-new-1", result.id)
        val row = assertNotNull(env.walletDao.rows.value[result.id])
        assertEquals("Pocket", row.name)
        assertEquals(0L, row.version)
        val change = assertNotNull(env.pendingDao.rows.value["wallet" to result.id])
        assertEquals(0L, change.baseVersion)
        assertEquals("wallet", change.scopeKind)
        assertEquals(result.id, change.scopeId)
        val payload = AppJson.decodeFromString(WalletDraft.serializer(), change.payloadJson)
        assertEquals("Pocket", payload.name)
        assertEquals(25_000L, payload.openingBalanceMinor)
        assertEquals(false, payload.archive)
        assertTrue(env.scheduler.enqueued.isNotEmpty())
    }

    @Test
    fun newWalletsCarryAClientMadeUuidV7() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        val service = DefaultWalletsService(
            env.repository, env.engine, env.authState, env.entitlements, ZeroPendingPostings,
            defaultCurrency = kotlinx.coroutines.flow.flowOf("EGP"), scope = backgroundScope,
        )
        val id = assertIs<CreateResult.Created>(service.create(draft)).id
        assertEquals(36, id.length)
        assertEquals('7', id[14], "UUID version nibble of $id")
        assertNotNull(env.walletDao.rows.value[id])
    }

    @Test
    fun blankAndTooLongNamesAreInvalid() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        assertEquals(
            CreateResult.Invalid(mapOf(WalletField.Name to WalletFieldProblem.NameBlank)),
            env.service.create(draft.copy(name = "   ")),
        )
        assertEquals(
            CreateResult.Invalid(mapOf(WalletField.Name to WalletFieldProblem.NameTooLong)),
            env.service.create(draft.copy(name = "x".repeat(101))),
        )
        assertIs<CreateResult.Created>(env.service.create(draft.copy(name = "x".repeat(100))))
        assertNull(env.pendingDao.rows.value.keys.firstOrNull { it.second.endsWith("2") })
    }
}
