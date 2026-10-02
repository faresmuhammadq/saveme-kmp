package com.fares.saveme.core.sync

import com.fares.saveme.core.database.SyncScopeEntity
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FakeSyncApi.Companion.failure
import com.fares.saveme.core.sync.FakeSyncApi.Companion.page
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.data.OwnMembershipFeedApplier
import com.fares.saveme.feature.wallets.data.WalletFeedApplier
import com.fares.saveme.feature.wallets.data.WalletMemberFeedApplier
import com.fares.saveme.feature.wallets.domain.DefaultWalletScopes
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The first run after the update reads every scope from the start under the sweep rules (spec 009 FR-007, FR-029, US4-S4). */
class FirstRunAfterUpdateTest {

    @Test
    fun aMigratedPhoneKeepsShowingItsDataWhileTheFirstRunReadsEveryScopeFromTheStart() = runTest {
        val runnerScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        env.signedIn()
        // A v5 phone after the migration: data, a legacy "Updated …" stamp, a waiting change, and no feed positions.
        env.seed(walletJson("a", version = 2))
        env.seed(walletJson("b", version = 2))
        env.seed(walletJson("c", version = 2))
        env.engine.enqueue("wallet", "c", SyncScope(ScopeKind.Wallet, "c"), 2, "{}")
        // The person has not settled it (a refusal waits for them), so the upload leaves it alone.
        env.pendingDao.upsert(env.pendingDao.get("wallet", "c")!!.copy(state = "Refused"))
        env.scopeDao.upsert(SyncScopeEntity("wallets", "2026-09-30T08:00:00Z"))

        val api = FakeSyncApi()
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        api.user = { cursor ->
            assertNull(cursor, "every scope is read from the start")
            started.complete(Unit)
            gate.await()
            ApiResult.Success(page(emptyList(), sync = "u-pos"))
        }
        api.wallet = { id, cursor ->
            assertNull(cursor, "every scope is read from the start")
            when (id) {
                "a" -> ApiResult.Success(page(listOf(FakeSyncApi.row("wallet", "a", version = 2, extra = walletFields("a", 21_000))), sync = "a-pos"))
                "b" -> failure(ApiErrorCode.NOT_FOUND, 404)
                else -> ApiResult.Success(page(emptyList(), sync = "c-pos")) // the server never returned wallet c's record
            }
        }
        val lock = SyncLock()
        val scopes = DefaultWalletScopes(env.repository) { "u1" }
        val puller = FeedPuller(
            api, env.scopeDao,
            { listOf(OwnMembershipFeedApplier(env.repository), WalletFeedApplier(env.repository), WalletMemberFeedApplier(env.repository)) },
            { scopes }, lock, { "u1" }, env.clock,
        )
        val runner = DefaultSyncRunner(env.engine, puller, { scopes }, { "u1" }, runnerScope, env.clock)

        launch { runner.refresh(ReadTarget.Everything, force = true) }
        started.await()
        // Mid-run: nothing has been removed or blanked, and "Updated …" is still the legacy time.
        val during = env.service.observeList().first()
        assertEquals(listOf("a", "b", "c"), during.active.map { it.id })
        assertEquals("2026-09-30T08:00:00Z", during.updatedAt.toString())

        gate.complete(Unit)
        advanceUntilIdle()

        val after = env.service.observeList().first()
        assertEquals(listOf("a", "c"), after.active.map { it.id }, "b answered 404: the person no longer has it")
        assertEquals(21_000L, env.walletDao.rows.value.getValue("a").balanceMinor)
        assertNotNull(env.walletDao.rows.value["c"], "a wallet with a waiting change is never removed by the sweep")
        assertNotNull(env.engine.get("wallet", "c"))
        listOf("user:u1", "wallet:a", "wallet:c").forEach {
            assertNotNull(env.scopeDao.rows.value[it]?.feedCursor, "$it has a position now")
            assertNotNull(env.scopeDao.rows.value[it]?.feedReadAt, "$it was read to its end")
        }
        assertTrue(after.updatedAt.toString() != "2026-09-30T08:00:00Z", "the feed read time replaces the legacy one")
    }

    private fun walletFields(id: String, balance: Long): Map<String, kotlinx.serialization.json.JsonElement> =
        (AppJson.parseToJsonElement(walletJson(id, balance = balance, version = 2)) as JsonObject).filterKeys { it != "id" && it != "version" }
}
