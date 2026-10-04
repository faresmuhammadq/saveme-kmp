package com.fares.saveme.core.sync

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.FakeSyncApi.Companion.page
import com.fares.saveme.core.sync.FakeSyncApi.Companion.rejected
import com.fares.saveme.feature.account.FakeSyncScopeDao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** No payload, amount, note, display name or position value reaches a log; type, id, outcome, code, counts and request id do (spec 009 FR-030, P-09.2). */
class SyncLoggingFeedBatchTest {

    private val lines = mutableListOf<String>()
    private val writer = object : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) { lines += "$tag $message" }
    }

    init {
        Logger.setLogWriters(writer)
        Logger.setMinSeverity(Severity.Verbose)
    }

    @AfterTest
    fun reset() = Logger.setLogWriters(emptyList())

    private val secrets = listOf("SECRET-NOTE", "SECRET-NAME", "98765432101", "CURSOR-SECRET-abc", "NEXT-CURSOR-SECRET-xyz", "SECRET-PAYLOAD")

    @Test
    fun aBatchWithAppliedConflictAndRejectedItemsLogsIdsOutcomesAndCodesNeverThePayload() = runTest {
        val h = BatchHarness()
        listOf("a", "b", "c", "d").forEach { h.save(it, """{"note":"SECRET-PAYLOAD","amount":98765432101}""") }
        h.api.batchAnswer = { items ->
            ApiResult.Success(
                items.map {
                    when (it.id) {
                        "b" -> rejected(it, ApiErrorCode.VERSION_CONFLICT, 409, current = BatchHarness().record(7))
                        "c" -> rejected(it, ApiErrorCode.VALIDATION_FAILED, 422)
                        "d" -> rejected(it, ApiErrorCode.INTERNAL_ERROR, 500)
                        else -> FakeSyncApi.appliedResult(it)
                    }
                },
            )
        }
        h.engine.drain()

        assertTrue(lines.any { it.startsWith("sync") && "batch of 4 items" in it }, "one line per batch with the count")
        assertTrue(lines.any { "note/a" in it }, "applied item by type and id")
        assertTrue(lines.any { "clash on note/b" in it && "req-1" in it }, "conflict with the request id")
        assertTrue(lines.any { "refused note/c" in it && "VALIDATION_FAILED" in it && "req-1" in it })
        assertTrue(lines.any { "retrying note/d" in it && "INTERNAL_ERROR" in it })
        secrets.forEach { secret -> assertFalse(lines.any { secret in it }, "$secret leaked:\n" + lines.joinToString("\n")) }
    }

    @Test
    fun aRequestLevelRefusalAndAFallbackLogTheCodeAndRequestIdOnly() = runTest {
        val h = BatchHarness()
        h.handler.answer = { EngineHarness.ok(2) }
        h.save("a", """{"note":"SECRET-PAYLOAD"}""")
        h.api.batchAnswer = { FakeSyncApi.failure(ApiErrorCode.NOT_FOUND, 404) }
        h.engine.drain()
        assertTrue(lines.any { "batch refused as a whole" in it && "NOT_FOUND" in it && "req-1" in it })
        secrets.forEach { secret -> assertFalse(lines.any { secret in it }) }
    }

    @Test
    fun aFeedReadWithTransactionsAndAProfileLogsCountsAndSkippedKindsNotRecordsOrPositions() = runTest {
        val api = FakeSyncApi()
        val scopes = FakeSyncScopeDao()
        val transactions = RecordingApplier(ScopeKind.Wallet, "transaction")
        val profile = RecordingApplier(ScopeKind.User, "profile")
        val puller = FeedPuller(api, scopes, { listOf(transactions, profile) }, { null }, SyncLock(), { "u1" })

        api.wallet = { _, cursor ->
            if (cursor == null) ApiResult.Success(page(listOf(FakeSyncApi.row("transaction", "t1", extra = mapOf("amount_minor" to JsonPrimitive(98765432101), "description" to JsonPrimitive("SECRET-NOTE"))), FakeSyncApi.row("budget", "b1")), sync = "CURSOR-SECRET-abc", next = "NEXT-CURSOR-SECRET-xyz"))
            else ApiResult.Success(FakeSyncApi.emptyPage("CURSOR-SECRET-abc"))
        }
        api.user = { ApiResult.Success(page(listOf(FakeSyncApi.row("profile", "p1", extra = mapOf("display_name" to JsonPrimitive("SECRET-NAME")))), sync = "CURSOR-SECRET-abc")) }

        puller.read(SyncScope(ScopeKind.Wallet, "w1"))
        puller.read(SyncScope(ScopeKind.User, "u1"))

        assertTrue(lines.any { "read of wallet scope" in it && "2 pages" in it && "2 rows" in it && "skipped budget x1" in it }, lines.joinToString("\n"))
        assertTrue(lines.any { "read of user scope" in it && "1 pages" in it && "1 rows" in it })
        secrets.forEach { secret -> assertFalse(lines.any { secret in it }, "$secret leaked:\n" + lines.joinToString("\n")) }
    }

    @Test
    fun aRefusedPositionLogsTheRequestIdNotTheValue() = runTest {
        val api = FakeSyncApi()
        val scopes = FakeSyncScopeDao()
        scopes.storeFeedPage("user:u1", "CURSOR-SECRET-abc", null)
        api.user = { cursor -> if (cursor != null) FakeSyncApi.failure(ApiErrorCode.INVALID_CURSOR, 400) else ApiResult.Success(FakeSyncApi.emptyPage("NEXT-CURSOR-SECRET-xyz")) }
        FeedPuller(api, scopes, { emptyList() }, { null }, SyncLock(), { "u1" }).read(SyncScope(ScopeKind.User, "u1"))
        assertTrue(lines.any { "refused" in it && "request req-1" in it })
        secrets.forEach { secret -> assertFalse(lines.any { secret in it }, lines.joinToString("\n")) }
    }

    @Test
    fun aHeadsRefreshLogsCountsAndRequestIdsNeverAPositionHeadHorizonOrCursor() = runTest {
        val api = FakeSyncApi()
        val scopes = FakeSyncScopeDao()
        val clock = FakeClock()
        val lock = SyncLock()
        val wallets = FakeWalletScopes(followed = listOf("quiet", "moved", "old"))
        scopes.storeFeedPage("user:u1", "CURSOR-SECRET-abc", 98765432101)
        scopes.storeFeedPage("wallet:quiet", "CURSOR-SECRET-abc", 98765432102)
        scopes.storeFeedPage("wallet:moved", "CURSOR-SECRET-abc", 98765432103)
        scopes.storeFeedPage("wallet:old", "CURSOR-SECRET-abc", 98765432104)
        api.heads = {
            ApiResult.Success(
                FakeSyncApi.headsPage(
                    FakeSyncApi.head("quiet", 98765432102), FakeSyncApi.head("moved", 98765439999), FakeSyncApi.head("old", 98765439998, purgedChangeSeq = 98765439000),
                ),
            )
        }
        api.user = { ApiResult.Success(FakeSyncApi.emptyPage("NEXT-CURSOR-SECRET-xyz", 98765432101)) }
        api.wallet = { _, _ -> ApiResult.Success(FakeSyncApi.emptyPage("NEXT-CURSOR-SECRET-xyz", 98765439999)) }
        val engine = SyncEngine(FakePendingChangeDao(), scopes, emptyList(), FakeSyncScheduler(), currentAccountId = { "u1" }, clock = clock, syncApi = api, lock = lock)
        val puller = FeedPuller(api, scopes, { emptyList() }, { wallets }, lock, { "u1" }, clock)
        val runner = DefaultSyncRunner(engine, puller, { wallets }, { "u1" }, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)), clock)

        runner.refresh(ReadTarget.Everything, force = false)

        assertTrue(lines.any { it.startsWith("sync") && "heads: 3 wallets in 1 pages" in it }, lines.joinToString("\n"))
        assertTrue(lines.any { "wallets: 3 followed, 1 unchanged, 1 read, 1 from the start (purge horizon), 0 direct" in it }, lines.joinToString("\n"))
        listOf("98765432101", "98765432102", "98765439999", "98765439998", "98765439000", "CURSOR-SECRET-abc", "NEXT-CURSOR-SECRET-xyz")
            .forEach { secret -> assertFalse(lines.any { secret in it }, "$secret leaked:\n" + lines.joinToString("\n")) }
    }

    @Test
    fun aRefusedHeadsReadLogsTheCodeAndRequestId() = runTest {
        val api = FakeSyncApi()
        api.heads = { FakeSyncApi.failure(ApiErrorCode.INTERNAL_ERROR, 500) }
        val result = FeedPuller(api, FakeSyncScopeDao(), { emptyList() }, { null }, SyncLock(), { "u1" }).heads(ReadRun("u1"))
        assertTrue(result == HeadsRead.Failed)
        assertTrue(lines.any { "heads refused" in it && "INTERNAL_ERROR" in it && "req-1" in it }, lines.joinToString("\n"))
    }
}
