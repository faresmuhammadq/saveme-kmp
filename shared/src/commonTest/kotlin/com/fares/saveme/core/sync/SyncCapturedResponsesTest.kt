package com.fares.saveme.core.sync

import app.cash.turbine.test
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.Envelope
import com.fares.saveme.core.sync.CapturedSyncResponses.BATCH_1
import com.fares.saveme.core.sync.CapturedSyncResponses.BATCH_1_REPLAY
import com.fares.saveme.core.sync.CapturedSyncResponses.BATCH_1_REQUEST
import com.fares.saveme.core.sync.CapturedSyncResponses.BATCH_2
import com.fares.saveme.core.sync.CapturedSyncResponses.BATCH_2_REQUEST
import com.fares.saveme.core.sync.CapturedSyncResponses.BATCH_3
import com.fares.saveme.core.sync.CapturedSyncResponses.BATCH_3_REQUEST
import com.fares.saveme.core.sync.CapturedSyncResponses.CATEGORY_1
import com.fares.saveme.core.sync.CapturedSyncResponses.EXPENSE_1
import com.fares.saveme.core.sync.CapturedSyncResponses.EXPENSE_2
import com.fares.saveme.core.sync.CapturedSyncResponses.EXPENSE_IN_UNKNOWN_WALLET
import com.fares.saveme.core.sync.CapturedSyncResponses.PROFILE_A
import com.fares.saveme.core.sync.CapturedSyncResponses.TRANSFER
import com.fares.saveme.core.sync.CapturedSyncResponses.WALLET_1
import com.fares.saveme.core.sync.CapturedSyncResponses.WALLET_2
import com.fares.saveme.core.sync.CapturedSyncServer.Companion.itemsOf
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.FakeSyncScopeDao
import com.fares.saveme.feature.account.data.ProfileSyncHandler
import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.data.CategorySyncHandler
import com.fares.saveme.feature.categories.data.dto.CategoryPayload
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.TransactionSyncHandler
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.wallets.data.WalletSyncHandler
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The live backend's batch answers (spec 009 contract QA, [CapturedSyncResponses]) through the real wire client,
 * the real engine and every feature's handler on fakes for Room (T069): the items the app builds are the ones the
 * server accepted, and every real answer settles exactly as the same single write's would (FR-017–FR-024, FR-033).
 */
class SyncCapturedResponsesTest {

    private class Backlog(scope: CoroutineScope) {
        val server = CapturedSyncServer()
        val tx = TransactionTestEnv(scope)
        val categories = CategoryTestEnv(scope) { jsonResponse(200, "{}") }
        val account = AccountTestEnv { jsonResponse(200, "{}") }
        val pending = FakePendingChangeDao()
        val engine = SyncEngine(
            pending, tx.scopeDao, listOf(tx.walletHandler, tx.syncHandler, categories.syncHandler, account.profileHandler),
            FakeSyncScheduler(), currentAccountId = { "u1" }, clock = tx.clock, syncApi = server.api,
        )
        val person = SyncScope(ScopeKind.User, "u1")

        fun row(type: String, id: String) = pending.rows.value[type to id]

        suspend fun wallet(id: String, type: String, name: String, opening: Long, base: Long = 0) = engine.enqueue(
            WalletSyncHandler.RECORD_TYPE, id, SyncScope(ScopeKind.Wallet, id), base,
            AppJson.encodeToString(WalletDraft.serializer(), WalletDraft(type, name, "EGP", opening, icon = null, color = null)),
        )

        suspend fun transaction(id: String, wallet: String, payload: TransactionPayload, base: Long = 0) = engine.enqueue(
            TransactionSyncHandler.RECORD_TYPE, id, SyncScope(ScopeKind.Wallet, wallet), base, AppJson.encodeToString(TransactionPayload.serializer(), payload),
        )

        /** What the person did offline before the first upload: a wallet, a category, an expense using both, a profile edit. */
        suspend fun firstBacklog() {
            wallet(WALLET_1, "cash", "QA Cash", 100_000)
            engine.enqueue(
                CategorySyncHandler.RECORD_TYPE, CATEGORY_1, person, 0,
                AppJson.encodeToString(CategoryPayload.serializer(), CategoryPayload("expense", "QA Pharmacy")),
            )
            transaction(EXPENSE_1, WALLET_1, TransactionPayload("expense", "2026-10-02", 12_500, "EGP", categoryId = CATEGORY_1, description = "Seif"))
            engine.enqueue(
                ProfileSyncHandler.RECORD_TYPE, PROFILE_A, person, 1,
                AppJson.encodeToString(ProfileDraft.serializer(), ProfileDraft("QA Nine", "en", "Africa/Cairo", "EGP", 1)),
            )
        }

        /** The wallet's balance as the screens compute it from the copy: the server's balance plus the carry of accepted changes. */
        suspend fun shownBalance(walletId: String) = tx.walletDao.rows.value.getValue(walletId).balanceMinor + tx.carryFor(walletId)
    }

    @Test
    fun aBacklogLeavesAsTheItemsTheLiveServerAcceptedAndEveryAnswerSettles() = runTest {
        val b = Backlog(backgroundScope)
        b.server.signedIn()
        b.server.batch = { _, _ -> BATCH_1 }
        b.firstBacklog()

        val report = b.engine.drain()

        assertEquals(1, b.server.uploads.size, "four changes, one request")
        assertEquals(itemsOf(BATCH_1_REQUEST), b.server.uploads.single(), "the wallet before the expense that needs it, the category too, all byte-equal")
        assertTrue(b.pending.rows.value.isEmpty(), "everything accepted: nothing waits")
        assertTrue(report.appliedAny)
        assertEquals(1L, b.tx.walletDao.rows.value.getValue(WALLET_1).version)
        assertEquals(1L, b.categories.categoryDao.rows.value.getValue(CATEGORY_1).version)
        assertEquals(2L, b.account.profileDao.row.value?.version)
        assertEquals("QA Nine", b.account.profileDao.row.value?.displayName)
        assertEquals(1L, b.tx.transactionDao.rows.value.getValue(EXPENSE_1).version)
        // The wallet's answer predates the expense; the expense's carry keeps the shown balance right until the wallet is read.
        assertEquals(87_500L, b.shownBalance(WALLET_1))
        assertNotNull(b.tx.scopeDao.rows.value["wallet:$WALLET_1"]?.lastPushedAt)
        assertEquals(setOf(SyncScope(ScopeKind.Wallet, WALLET_1), b.person), report.written)
    }

    @Test
    fun aLostAnswerIsSentAgainUnchangedAndTheReplayIsAcceptedOnce() = runTest {
        val b = Backlog(backgroundScope)
        b.server.signedIn()
        b.server.batch = { n, _ -> if (n == 0) throw IOException("the answer never arrived") else BATCH_1_REPLAY }
        b.firstBacklog()

        val first = b.engine.drain()
        assertTrue(first.offline)
        assertEquals(4, b.pending.rows.value.size)
        assertTrue(b.pending.rows.value.values.all { it.state == "Waiting" }, "nothing lost")

        b.engine.drain()
        assertEquals(b.server.uploads[0], b.server.uploads[1], "the same items again: safe to repeat (FR-023)")
        assertTrue(b.pending.rows.value.isEmpty())
        assertEquals(1L, b.tx.walletDao.rows.value.getValue(WALLET_1).version)
        assertEquals(1, b.tx.transactionDao.rows.value.size, "stored once")
        assertEquals(87_500L, b.shownBalance(WALLET_1), "the replay counts the expense once")
    }

    @Test
    fun aRefusedNewWalletHoldsItsTransferWhileEveryOtherItemSettlesOnItsOwnAndThenItGoes() = runTest {
        val b = Backlog(backgroundScope)
        b.server.signedIn()
        b.server.batch = { n, _ ->
            when (n) {
                0 -> BATCH_1
                1 -> BATCH_2
                else -> BATCH_3
            }
        }
        b.firstBacklog()
        b.engine.drain()

        // Offline again: a second wallet the server will refuse, a transfer into it, an expense of 0, an expense in a wallet
        // the person never had, a stale edit of wallet 1, and the deletion of the first expense.
        val unknownWallet = itemsOf(BATCH_2_REQUEST).let { (it as JsonArray)[3].jsonObject.getValue("wallet_id").jsonPrimitive.content }
        b.wallet(WALLET_2, "bank", "QA Bank", 0)
        b.transaction(TRANSFER, WALLET_1, TransactionPayload("transfer", "2026-10-02", 5_000, "EGP", counterWalletId = WALLET_2, description = "Move"))
        b.transaction(EXPENSE_2, WALLET_1, TransactionPayload("expense", "2026-10-02", 0, "EGP", categoryId = CATEGORY_1))
        b.transaction(EXPENSE_IN_UNKNOWN_WALLET, unknownWallet, TransactionPayload("expense", "2026-10-02", 100, "EGP", categoryId = CATEGORY_1))
        b.wallet(WALLET_1, "cash", "QA Cash renamed", 100_000, base = 1)
        b.engine.enqueueDelete(TransactionSyncHandler.RECORD_TYPE, EXPENSE_1, SyncScope(ScopeKind.Wallet, WALLET_1), 1)

        b.engine.drain()

        val sent = b.server.uploads[1] as JsonArray
        assertEquals(listOf(WALLET_2, TRANSFER, EXPENSE_2, EXPENSE_IN_UNKNOWN_WALLET, WALLET_1, EXPENSE_1), sent.map { it.jsonObject.getValue("id").jsonPrimitive.content })
        val walletTwo = assertNotNull(b.row("wallet", WALLET_2))
        assertEquals("Refused", walletTwo.state)
        assertTrue(walletTwo.refusalJson!!.contains("INVALID_ENUM_VALUE"))
        val transfer = assertNotNull(b.row("transaction", TRANSFER))
        assertEquals("Waiting", transfer.state, "its wallet was refused: the transfer's own refusal is ignored")
        assertEquals(0, transfer.attempts)
        assertNull(transfer.refusalJson)
        val zero = assertNotNull(b.row("transaction", EXPENSE_2))
        assertEquals("Refused", zero.state)
        val zeroRefusal = AppJson.decodeFromString(Refusal.serializer(), zero.refusalJson!!)
        assertEquals("VALIDATION_FAILED", zeroRefusal.code)
        assertEquals(listOf("OUT_OF_RANGE"), zeroRefusal.fields.values.toList(), "the pointer, rooted at the item's data, maps to the amount field as for the single write")
        assertEquals("Refused", b.row("transaction", EXPENSE_IN_UNKNOWN_WALLET)?.state)
        assertTrue(b.row("transaction", EXPENSE_IN_UNKNOWN_WALLET)!!.refusalJson!!.contains("NOT_FOUND"))
        val stale = assertNotNull(b.row("wallet", WALLET_1))
        assertEquals("Clash", stale.state)
        assertTrue(stale.clashJson!!.contains("\"version\":1"), "the server's current copy is kept for the choice")
        assertNull(b.row("transaction", EXPENSE_1), "the deletion was accepted")
        assertEquals(100_000L, b.shownBalance(WALLET_1), "the accepted deletion undoes the expense; the refused and held ones count nothing yet")

        // The person fixes the wallet and the amount; the held transfer goes with them, in the order they were made.
        b.wallet(WALLET_2, "checking", "QA Bank", 0)
        b.transaction(EXPENSE_2, WALLET_1, TransactionPayload("expense", "2026-10-02", 700, "EGP", categoryId = CATEGORY_1, description = "Snack"))
        b.engine.drain()

        assertEquals(itemsOf(BATCH_3_REQUEST), b.server.uploads[2], "byte-equal to what the live server accepted")
        assertNull(b.row("wallet", WALLET_2))
        assertNull(b.row("transaction", TRANSFER))
        assertNull(b.row("transaction", EXPENSE_2))
        assertEquals(WALLET_2, b.tx.transactionDao.rows.value.getValue(TRANSFER).counterWalletId)
    }

    @Test
    fun theLiveServersRequestLevelShapeRefusalsSendTheSameChangesPerRecordInTheSameRun() = runTest {
        for (refusal in listOf(
            CapturedSyncResponses.BATCH_EMPTY, CapturedSyncResponses.BATCH_51, CapturedSyncResponses.BATCH_MALFORMED,
            CapturedSyncResponses.BATCH_NO_ITEMS, CapturedSyncResponses.BATCH_TEXT_PLAIN, CapturedSyncResponses.BATCH_GET,
        )) {
            val server = CapturedSyncServer()
            server.signedIn()
            server.batch = { _, _ -> refusal }
            val handler = BatchHandler(answer = { EngineHarness.record(2).let { ApiResult.Success(it) } })
            val dao = FakePendingChangeDao()
            val engine = SyncEngine(dao, FakeSyncScopeDao(), listOf(handler), FakeSyncScheduler(), currentAccountId = { "u1" }, syncApi = server.api)
            listOf("a", "b").forEach { engine.enqueue("note", it, SyncScope(ScopeKind.User, "u1"), 1, "{}") }

            engine.drain()

            assertEquals(listOf("a", "b"), handler.sent.map { it.recordId }, "status ${refusal.status}: per record, in order")
            assertTrue(dao.rows.value.isEmpty(), "status ${refusal.status}")
        }
    }

    @Test
    fun theLiveServersSessionAndVersionRefusalsSettleEveryItemAsTheSingleWriteWould() = runTest {
        for ((refusal, expected) in listOf(CapturedSyncResponses.BATCH_UNAUTHENTICATED to "Waiting", CapturedSyncResponses.BATCH_OLD_CLIENT to "Refused")) {
            val server = CapturedSyncServer()
            server.signedIn()
            server.batch = { _, _ -> refusal }
            val handler = BatchHandler()
            val dao = FakePendingChangeDao()
            val engine = SyncEngine(dao, FakeSyncScopeDao(), listOf(handler), FakeSyncScheduler(), currentAccountId = { "u1" }, syncApi = server.api)
            listOf("a", "b").forEach { engine.enqueue("note", it, SyncScope(ScopeKind.User, "u1"), 1, "{}") }

            engine.drain()

            assertTrue(handler.sent.isEmpty(), "no per-record fallback for ${refusal.status}")
            listOf("a", "b").forEach { id ->
                val row = assertNotNull(dao.rows.value["note" to id])
                assertEquals(expected, row.state, "status ${refusal.status}")
                if (expected == "Waiting") assertEquals(0, row.attempts)
            }
            if (refusal.status == 426) assertTrue(dao.rows.value.values.all { it.refusalJson!!.contains("CLIENT_VERSION_UNSUPPORTED") })
        }
    }

    @Test
    fun anItemRefusedForAnUnverifiedAddressPromptsVerification() = runTest {
        val server = CapturedSyncServer()
        server.batch = { _, _ -> CapturedSyncResponses.BATCH_UNVERIFIED }
        val id = AppJson.parseToJsonElement(CapturedSyncResponses.BATCH_UNVERIFIED.body).jsonObject.getValue("data").let { (it as JsonArray)[0] }
            .jsonObject.getValue("id").jsonPrimitive.content
        server.auth.events.events.test {
            server.signedIn()
            val dao = FakePendingChangeDao()
            val engine = SyncEngine(
                dao, FakeSyncScopeDao(), listOf(BatchHandler("wallet")), FakeSyncScheduler(), currentAccountId = { "u1" }, syncApi = server.api,
            )
            engine.enqueue("wallet", id, SyncScope(ScopeKind.Wallet, id), 0, "{}")
            engine.drain()
            assertEquals(ApiEvent.EmailNotVerified, awaitItem())
            assertEquals("Refused", dao.rows.value["wallet" to id]!!.state)
        }
    }

    @Test
    fun theLiveServersItemAnswersLeaveTheSameRowsThroughTheBatchAndThroughSingleWrites() = runTest {
        fun decode(answer: CapturedSyncResponses.Answer) =
            AppJson.decodeFromString(Envelope.serializer(ListSerializer(BatchItemResultDto.serializer())), answer.body).data
        // Every item answer recorded live, one per record: applied (201 and 200), conflict, rejected 422 / 404 / 409 / 403.
        val answers = (decode(BATCH_2) + decode(BATCH_1) + decode(CapturedSyncResponses.BATCH_CATEGORIES) + decode(CapturedSyncResponses.BATCH_UNVERIFIED))
            .distinctBy { it.type to it.id }
        val types = answers.map { it.type }.distinct().toTypedArray()

        suspend fun BatchHarness.queue() = answers.forEach { save(it.id, base = 1, type = it.type) }

        val batched = BatchHarness(*types)
        batched.queue()
        batched.api.batchAnswer = { items -> ApiResult.Success(items.map { item -> answers.first { it.type == item.type && it.id == item.id } }) }
        batched.engine.drain()

        val single = BatchHarness(*types)
        single.handlers.forEach { handler ->
            handler.answer = { change ->
                val result = answers.first { it.type == change.recordType && it.id == change.recordId }
                if (result.outcome == "applied") ApiResult.Success(result.record!!)
                else ApiResult.Failure(result.problem!!, ApiErrorCode.from(result.problem.code), result.problem.retryAfterSeconds)
            }
            answers.forEach { handler.perRecordOnly += it.id }
        }
        single.queue()
        single.engine.drain()

        fun BatchHarness.rows() = dao.rows.value.values.sortedBy { it.createdOrder }
            .map { listOf(it.recordType, it.recordId, it.state, it.baseVersion, it.attempts, it.clashJson, it.refusalJson) }
        assertEquals(single.rows(), batched.rows())
        assertEquals(single.handlers.flatMap { it.applied }.toSet(), batched.handlers.flatMap { it.applied }.toSet())
        assertEquals(
            setOf("Clash", "Refused"), batched.rows().map { it[2] as String }.toSet(),
            "the applied ones are cleared, the conflict is a clash, the rejections are refusals",
        )
    }
}
