package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.domain.model.CreateResult
import com.fares.saveme.feature.wallets.domain.model.SaveResult
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletsServiceArchiveDeleteTest {
    private fun env(scope: kotlinx.coroutines.CoroutineScope) = WalletTestEnv(scope) { jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE")) }

    @Test
    fun archiveQueuesArchiveTrueOnTheRowsVersion() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(walletJson(version = 3))
        assertEquals(SaveResult.Saved, env.service.archive("w1"))
        val change = env.pendingDao.rows.value.getValue("wallet" to "w1")
        assertEquals(3L, change.baseVersion)
        val draft = AppJson.decodeFromString(WalletDraft.serializer(), change.payloadJson)
        assertTrue(draft.archive)
        assertEquals("Pocket", draft.name)
        assertTrue(env.service.observeWallet("w1").first()!!.archived)
    }

    @Test
    fun deletingANeverSyncedWalletSendsNothing() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        val id = (env.service.create(WalletDraft("cash", "Temp", "EGP", 0, null, null)) as CreateResult.Created).id
        assertEquals(SaveResult.Saved, env.service.delete(id))
        assertNull(env.walletDao.rows.value[id])
        assertEquals(emptyMap(), env.pendingDao.rows.value)
        assertEquals(0, env.requests.size)
    }

    @Test
    fun deletingASyncedWalletHidesItWhileItWaits() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(walletJson(version = 3))
        assertEquals(SaveResult.Saved, env.service.delete("w1"))
        val change = env.pendingDao.rows.value.getValue("wallet" to "w1")
        assertTrue(change.isDelete)
        assertEquals(3L, change.baseVersion)
        assertNull(env.service.observeWallet("w1").first())
        assertEquals(emptyList(), env.service.observeList().first().active)
        assertNotNull(env.walletDao.rows.value["w1"])
    }

    @Test
    fun aDeleteClashOrRefusalMakesTheWalletVisibleAgainWithItsMark() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(walletJson(version = 3))
        env.service.delete("w1")
        val row = env.pendingDao.rows.value.getValue("wallet" to "w1")
        env.pendingDao.rows.value = env.pendingDao.rows.value + (("wallet" to "w1") to row.copy(
            state = ChangeState.Clash.name, clashJson = walletJson(version = 5, name = "Web"),
        ))
        val view = assertNotNull(env.service.observeWallet("w1").first())
        assertTrue(view.pendingDelete)
        assertIs<SyncState.Clash>(view.syncState)
        assertEquals(1, env.service.observeList().first().active.size)
        env.pendingDao.rows.value = env.pendingDao.rows.value + (("wallet" to "w1") to row.copy(
            state = ChangeState.Refused.name, refusalJson = """{"code":"FORBIDDEN"}""",
        ))
        assertIs<SyncState.Refused>(env.service.observeWallet("w1").first()!!.syncState)
    }

    @Test
    fun theServersTombstoneRemovesRowsAndCallsParticipants() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(walletJson(version = 3))
        env.service.delete("w1")
        env.engine.drain()
        // The mock answers a live copy; a real tombstone is applied the same way the engine does.
        env.syncHandler.applyServerCopy(AppJson.parseToJsonElement(walletJson(version = 4, deletedAt = "2026-10-02T00:00:00Z")))
        assertNull(env.walletDao.rows.value["w1"])
        assertEquals(listOf("w1"), env.removed)
    }

    @Test
    fun keepingTheWalletAfterADeleteClashBringsItBack() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(walletJson(version = 3))
        env.service.delete("w1")
        val row = env.pendingDao.rows.value.getValue("wallet" to "w1")
        env.pendingDao.rows.value = env.pendingDao.rows.value + (("wallet" to "w1") to row.copy(
            state = ChangeState.Clash.name, clashJson = walletJson(version = 5, name = "Web"),
        ))
        env.service.useTheirs("w1")
        assertEquals("Web", env.service.observeWallet("w1").first()!!.shown.name)
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }
}
