package com.fares.saveme.core.sync

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.CapturedSyncResponses.Answer
import com.fares.saveme.feature.account.FakeSyncScopeDao
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.TransactionSyncHandler
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.data.WalletMemberFeedApplier
import com.fares.saveme.feature.wallets.memberJson
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import com.fares.saveme.feature.auth.jsonResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec 011 through the real wire client ([HttpSyncApi]), the real puller, runner and engine, answered with the shapes
 * of sync.yaml v1.10 (the OpenAPI examples and bodies built to the same schema): the edge cases the live QA could not
 * stage cheaply. The live backend's own recorded answers ([CapturedHeadsResponses], contract QA T030) are driven
 * through the same stack by [SyncHeadsCapturedTest] and [SyncRestorePairCapturedTest], which complement these.
 */
class SyncHeadsWireTest {

    private fun TestScope.foregroundScope() = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

    private val w1 = "0192f79e-0000-7000-8000-000000000001"
    private val w2 = "0192f79e-0000-7000-8000-000000000002"

    private fun feedPage(cursor: String, position: Long, next: String? = null, rows: String = "") =
        """{"data":[$rows],"meta":{"next_cursor":${next?.let { "\"$it\"" } ?: "null"},"has_more":${next != null},"limit":200,"sync_cursor":"$cursor","sync_position":$position}}"""

    private class Rig(scope: CoroutineScope, val server: CapturedSyncServer = CapturedSyncServer()) {
        val scopes = FakeSyncScopeDao()
        val wallets = FakeWalletScopes()
        val clock = FakeClock()
        val lock = SyncLock()
        val engine = SyncEngine(FakePendingChangeDao(), scopes, emptyList(), FakeSyncScheduler(), currentAccountId = { "u1" }, clock = clock, syncApi = server.api, lock = lock)
        val tx = RecordingApplier(ScopeKind.Wallet, "transaction")
        val puller = FeedPuller(server.api, scopes, { listOf(tx) }, { wallets }, lock, { "u1" }, clock)
        val runner = DefaultSyncRunner(engine, puller, { wallets }, { "u1" }, scope, clock)
    }

    private suspend fun Rig.start() {
        server.signedIn()
        server.user = { Answer(200, feedPageStatic("u-new", 60)) }
        server.heads = { Answer(200, OpenApiSyncExamples.HEADS_TWO_WALLETS) }
        scopes.storeFeedPage("user:u1", "u-0", 59)
    }

    private fun feedPageStatic(cursor: String, position: Long) =
        """{"data":[],"meta":{"next_cursor":null,"has_more":false,"limit":200,"sync_cursor":"$cursor","sync_position":$position}}"""

    @Test
    fun aQuietRefreshOverTheWireIsTwoRequests() = runTest {
        val rig = Rig(foregroundScope())
        rig.start()
        rig.wallets.followed = listOf(w2)
        rig.scopes.storeFeedPage("wallet:$w2", "c2", 133) // the head of the second wallet in the OpenAPI example is 133

        rig.runner.refresh(ReadTarget.Everything, force = false)

        assertEquals(listOf("sync/user/changes?cursor", "sync/wallets/heads"), rig.server.requests)
    }

    @Test
    fun aWalletWhoseHeadMovedIsTheOnlyOneRead() = runTest {
        val rig = Rig(foregroundScope())
        rig.start()
        rig.wallets.followed = listOf(w1, w2)
        rig.scopes.storeFeedPage("wallet:$w1", "c1", 700)
        rig.scopes.storeFeedPage("wallet:$w2", "c2", 133)
        rig.server.wallet = { id, cursor ->
            assertEquals(w1, id)
            assertEquals("c1", cursor)
            Answer(200, feedPage("c1b", 812))
        }

        rig.runner.refresh(ReadTarget.Everything, force = false)

        assertEquals(listOf("sync/user/changes?cursor", "sync/wallets/heads", "sync/wallets/$w1/changes?cursor"), rig.server.requests)
        assertEquals(812L, rig.scopes.feedPlace("wallet:$w1")!!.position)
    }

    @Test
    fun aWalletBehindItsPurgeHorizonIsReadFromTheStartPastTheHorizonAndSwept() = runTest {
        val rig = Rig(foregroundScope())
        rig.start()
        rig.wallets.followed = listOf(w1)
        rig.scopes.storeFeedPage("wallet:$w1", "expired", 100) // the example's horizon for the first wallet is 140
        val pages = mutableListOf<String?>()
        rig.server.wallet = { _, cursor ->
            pages += cursor
            when (cursor) {
                null -> Answer(200, feedPage("p1", 150, next = "p1", rows = """{"type":"transaction","record":{"id":"t1","version":1}}"""))
                "p1" -> Answer(200, feedPage("p2", 160, next = "p2", rows = """{"type":"transaction","record":{"id":"t2","version":1}}"""))
                else -> Answer(200, feedPage("p3", 812, rows = """{"type":"transaction","record":{"id":"t3","version":1}}"""))
            }
        }

        rig.runner.refresh(ReadTarget.Everything, force = false)

        assertEquals(listOf<String?>(null, "p1", "p2"), pages, "the expired cursor was never sent")
        assertEquals(setOf("t1", "t2", "t3"), rig.tx.swept.single().second)
        assertEquals(FeedPlaceOf("p3", 812), rig.scopes.feedPlace("wallet:$w1")?.let { FeedPlaceOf(it.cursor, it.position) })
    }

    private data class FeedPlaceOf(val cursor: String?, val position: Long?)

    @Test
    fun aRenamedMemberRowWithTheSameVersionReplacesTheNameThroughTheWire() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        val server = CapturedSyncServer()
        server.signedIn()
        env.memberDao.insertAll(listOf(com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m2", "w1", "u2", "Omar", "editor", 1, "2026-09-01T00:00:00Z")))
        server.wallet = { _, _ -> Answer(200, feedPage("c1", 20, rows = """{"type":"wallet_member","record":${memberJson("m2", "u2", "Omar Hassan", "editor")}}""")) }
        val scopes = FakeSyncScopeDao()
        val puller = FeedPuller(server.api, scopes, { listOf(WalletMemberFeedApplier(env.repository)) }, { null }, SyncLock(), { "u1" }, FakeClock())

        puller.read(SyncScope(ScopeKind.Wallet, "w1"))

        assertEquals("Omar Hassan", env.memberDao.rows.value.single().displayName)
        assertEquals(1L, env.memberDao.rows.value.single().version)
    }

    @Test
    fun aRestorePairLeavesAsOneRequestAndBothAppliedSettlesTheChange() = runTest {
        val server = CapturedSyncServer()
        val env = TransactionTestEnv(backgroundScope)
        env.signedIn()
        server.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 5))
        val pending = FakePendingChangeDao()
        val engine = SyncEngine(
            pending, env.scopeDao, listOf(env.walletHandler, env.syncHandler), FakeSyncScheduler(),
            currentAccountId = { "u1" }, clock = env.clock, syncApi = server.api,
        )
        engine.enqueue(
            TransactionSyncHandler.RECORD_TYPE, "t1", SyncScope(ScopeKind.Wallet, "w1"), 6,
            AppJson.encodeToString(TransactionPayload.serializer(), TransactionPayload("expense", "2026-10-02", 6_000, "EGP", categoryId = "c1")),
        )
        pending.upsert(pending.rows.value.getValue("transaction" to "t1").let { it.copy(restore = true) })
        server.batch = { _, _ ->
            Answer(
                200,
                """{"data":[
                    {"type":"transaction","id":"t1","outcome":"applied","status":200,"record":${transactionJson(version = 7, amount = 4_550)},"problem":null},
                    {"type":"transaction","id":"t1","outcome":"applied","status":200,"record":${transactionJson(version = 8, amount = 6_000)},"problem":null}],"meta":{}}""",
            )
        }

        engine.drain()

        assertEquals(1, server.uploads.size)
        val items = (server.uploads.single() as JsonArray).map { it.jsonObject }
        assertEquals(listOf("restore", "upsert"), items.map { it.getValue("op").jsonPrimitive.content })
        assertEquals(listOf("w1", "w1"), items.map { it.getValue("wallet_id").jsonPrimitive.content })
        assertEquals("6", items[0].getValue("data").jsonObject.getValue("version").toString())
        assertEquals("7", items[1].getValue("data").jsonObject.getValue("version").toString())
        assertTrue(pending.rows.value.isEmpty(), "settled as one change")
        assertEquals(8L, env.transactionDao.rows.value.getValue("t1").version)
        assertEquals(6_000L, env.transactionDao.rows.value.getValue("t1").amountMinor)
    }
}
