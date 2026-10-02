package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.CapturedTransactionResponses as Captured
import com.fares.saveme.feature.transactions.domain.model.AddedBy
import com.fares.saveme.feature.transactions.domain.model.Counterpart
import com.fares.saveme.feature.transactions.domain.model.CreateResult
import com.fares.saveme.feature.transactions.domain.model.Direction
import com.fares.saveme.feature.transactions.domain.model.RefusalKind
import com.fares.saveme.feature.transactions.domain.model.SaveResult
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionSyncState
import com.fares.saveme.feature.wallets.pageOf
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The client's requests, decoding and sync handling over responses captured from the real local
 * backend (spec 008 host QA, qa-report.md §5), driven through the real sync engine, handler,
 * repository, postings and service (T084). Where QA recorded the request it sent, the app's own
 * request body is compared with it member for member.
 */
class TransactionCapturedResponsesTest {

    /** Answers by (method, path suffix); the first match wins, so more specific keys go first. */
    private class Script {
        val answers = mutableListOf<Triple<HttpMethod?, String, Pair<Int, String>>>()
        fun on(method: HttpMethod?, pathPart: String, status: Int, body: String) {
            answers.add(0, Triple(method, pathPart, status to body))
        }
        fun answer(request: HttpRequestData): Pair<Int, String> {
            val url = request.url.toString()
            return answers.firstOrNull { (m, part, _) -> (m == null || m == request.method) && part in url }?.third
                ?: error("no scripted answer for ${request.method.value} $url")
        }
    }

    private fun env(scope: CoroutineScope, script: Script, ids: List<String> = emptyList()): TransactionTestEnv {
        // A wallet read the reconciler asks for, unless a test scripts its own (later entries win).
        script.answers.add(Triple(HttpMethod.Get, "/wallets?", 200 to pageOf(listOf(cashJson(), bankJson()))))
        return TransactionTestEnv(scope, newIds = ids) { request ->
            val (status, body) = script.answer(request)
            jsonResponse(status, body)
        }
    }

    private fun cashJson(balance: Long = 100_000) = walletJson(id = Captured.CASH, name = "QA Cash", opening = 100_000, balance = balance)
    private fun bankJson(role: String = "owner") = walletJson(id = Captured.BANK, name = "QA Bank", opening = 0, balance = 0, role = role)

    private fun dataOf(body: String): String =
        AppJson.parseToJsonElement(body).jsonObject.getValue("data").toString()

    /** What QA sent for a captured answer: the app must send the same members and values. */
    private fun qaRequest(json: String): JsonObject = AppJson.parseToJsonElement(json).jsonObject

    private suspend fun TransactionTestEnv.seedCashAndBank(cashBalance: Long = 100_000, bankRole: String = "owner") {
        seedWallet(cashJson(cashBalance))
        seedWallet(bankJson(bankRole))
    }

    private suspend fun TransactionTestEnv.view(id: String, from: String = Captured.CASH) =
        service.observeTransaction(id, from).first()

    private fun draft(
        amount: Long,
        category: String? = Captured.GROCERIES,
        note: String? = null,
        memo: String? = null,
        person: String? = null,
        cleared: Boolean = false,
        kind: String = "expense",
        counter: String? = null,
        date: LocalDate = LocalDate(2026, 10, 1),
    ) = TransactionDraft(Captured.CASH, kind, date, amount, "EGP", category, counter, note, memo, person, cleared)

    @Test
    fun theRealListPagesAreAllReadByCursorAndEveryRowIsStoredWithNullRecurringLinks() = runTest {
        val script = Script()
        script.on(HttpMethod.Get, "/transactions?limit=200", 200, Captured.LIST_PAGE1_200)
        script.on(HttpMethod.Get, "cursor=eyJrIjoiMDgyMTg1ZDE3NDZiY2UxYyIsInAiOnsidmFsdWUiOiIyMDI2LTEwLTAxIiwiaWQiOiIwMWEwZmQ1Yi00MTYyLTcwOWUtYmUyOS0yYzhlMDc4NDk0N2QifX0", 200, Captured.LIST_PAGE2_200)
        script.on(HttpMethod.Get, "cursor=eyJrIjoiMDgyMTg1ZDE3NDZiY2UxYyIsInAiOnsidmFsdWUiOiIyMDI2LTEwLTAxIiwiaWQiOiIwMWEwZmQ1Yi0yMTBkLTdhNmYtOWE2NS03MWE3MTgyY2JmMmEifX0", 200, Captured.LIST_PAGE3_200)
        val env = env(backgroundScope, script)
        env.signedIn()
        env.seedCashAndBank()

        val read = assertIs<ApiResult.Success<WalletRead>>(env.repository.refreshWallet(Captured.CASH))
        assertTrue(read.value.changed)
        val gets = env.requests.filter { it.method == HttpMethod.Get }
        assertEquals(3, gets.size)
        gets.forEach {
            assertEquals("200", it.url.parameters["limit"])
            // No filter, sort or search reaches the server: search and filters run on the phone (FR-027, FR-028).
            assertTrue(it.url.parameters.names().all { name -> name == "limit" || name == "cursor" })
        }
        assertNull(gets[0].url.parameters["cursor"])
        val rows = env.transactionDao.getAll()
        assertEquals(5, rows.size)
        assertTrue(rows.all { it.recurringRuleId == null && it.occurrenceId == null })
        // The longest real values (100 emoji, 1,000-character details) decode and are kept whole.
        assertEquals(1_000, rows.single { it.id == "01a0fd5b-3d64-74e0-a702-b9a197334eb6" }.memo!!.length)
        val list = env.service.observeList(Captured.CASH).first()
        assertEquals(5, list.totalCount)
        // The server's blank note came back as null and the amount at the largest value decodes as a Long.
        assertTrue(rows.any { it.amountMinor == 9_007_199_254_740_991 })
    }

    @Test
    fun aTransferIsOneRowListedInBothWalletsOutOfTheSourceAndIntoTheDestination() = runTest {
        val script = Script()
        script.on(HttpMethod.Get, "wallets/${Captured.CASH}/transactions", 200, Captured.LIST_CASH_200)
        script.on(HttpMethod.Get, "wallets/${Captured.BANK}/transactions", 200, Captured.LIST_BANK_200)
        val env = env(backgroundScope, script)
        env.signedIn()
        env.seedCashAndBank()
        env.repository.refreshWallet(Captured.CASH)
        env.repository.refreshWallet(Captured.BANK)

        assertEquals(1, env.transactionDao.getAll().count { it.id == Captured.TRANSFER })
        val fromCash = env.view(Captured.TRANSFER, Captured.CASH)!!
        assertEquals(Direction.Out, fromCash.direction)
        assertEquals(Counterpart.Named("QA Bank"), fromCash.counterpart)
        assertNull(fromCash.shown.categoryId)
        val bank = env.service.observeList(Captured.BANK).first()
        val inBank = bank.days.single().items.single()
        assertEquals(Captured.TRANSFER, inBank.id)
        assertEquals(Direction.In, inBank.direction)
        assertEquals(Counterpart.Named("QA Cash"), inBank.counterpart)
        assertEquals(50_000, bank.days.single().netMinor)
        // An owner of both wallets may edit and delete it from either side (FR-013).
        assertTrue(inBank.canEdit && inBank.canDelete)
    }

    @Test
    fun aCreateSendsWhatQaSentAndTheBalanceNeverJumpsWhileItSyncsAndSettles() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "transactions/${Captured.EXPENSE}", 201, Captured.PUT_CREATED_201)
        script.on(HttpMethod.Get, "wallets?", 200, pageOf(listOf(dataOf(Captured.WALLET_AFTER_CREATE_200), dataOf(Captured.WALLET_BANK_AFTER_TRANSFER_200).replace("50000", "0"))))
        val env = env(backgroundScope, script, ids = listOf(Captured.EXPENSE))
        env.signedIn()
        env.seedCashAndBank()

        val created = assertIs<CreateResult.Created>(env.service.create(draft(4_550, note = "Weekly shop")))
        assertEquals(Captured.EXPENSE, created.id)
        // The phone's first transaction locks the wallet's currency before it syncs (US1-S10).
        val waiting = env.wallets.observeWallet(Captured.CASH).first()!!
        assertEquals(95_450, waiting.displayedBalanceMinor)
        assertTrue(waiting.hasPendingPostings)

        env.engine.drain()
        val put = env.requests.single { it.method == HttpMethod.Put }
        assertTrue(put.url.encodedPath.endsWith("wallets/${Captured.CASH}/transactions/${Captured.EXPENSE}"))
        assertEquals(qaRequest("""{"kind":"expense","date":"2026-10-01","amount_minor":4550,"currency":"EGP","category_id":"${Captured.GROCERIES}","counter_wallet_id":null,"description":"Weekly shop","memo":null,"counterparty":null,"cleared":false,"version":0}"""), put.jsonBody())
        assertTrue(env.pendingDao.rows.value.isEmpty())
        val row = env.transactionDao.get(Captured.EXPENSE)!!
        assertEquals(1, row.version)
        assertEquals(Captured.USER_A, row.createdBy)
        // Synced, but the wallet still shows the server's old balance: the carry keeps the change counted.
        assertEquals(-4_550, env.carryFor(Captured.CASH))
        val synced = env.wallets.observeWallet(Captured.CASH).first()!!
        assertEquals(95_450, synced.displayedBalanceMinor)
        // The stored wallet still says "not locked" until it is read again; the carry keeps it locked meanwhile.
        assertFalse(synced.server.currencyLocked)
        assertTrue(synced.hasPendingPostings)

        assertTrue(env.reconciler.settle())
        assertEquals(0, env.carryFor(Captured.CASH))
        val settled = env.wallets.observeWallet(Captured.CASH).first()!!
        assertEquals(95_450, settled.displayedBalanceMinor)
        assertTrue(settled.server.currencyLocked)
        assertFalse(settled.hasPendingPostings)
    }

    @Test
    fun anIdenticalReplayOfTheCreateIsAnsweredUnchangedAndStoredOnce() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "transactions/${Captured.EXPENSE}", 200, Captured.PUT_REPLAY_200)
        val env = env(backgroundScope, script, ids = listOf(Captured.EXPENSE))
        env.signedIn()
        env.seedCashAndBank()
        env.service.create(draft(4_550, note = "Weekly shop"))
        env.engine.drain()
        assertTrue(env.pendingDao.rows.value.isEmpty())
        assertEquals(1, env.transactionDao.get(Captured.EXPENSE)!!.version)
        assertEquals(1, env.transactionDao.getAll().size)
    }

    @Test
    fun aStaleSaveBecomesAClashWithTheOtherDevicesValuesAndKeepMineRebasesOnTheirVersion() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "transactions/${Captured.EXPENSE}", 409, Captured.PUT_VERSION_CONFLICT_409)
        val env = env(backgroundScope, script)
        env.signedIn()
        env.seedCashAndBank()
        env.seed(dataOf(Captured.PUT_CREATED_201))
        assertEquals(SaveResult.Saved, env.service.save(Captured.EXPENSE, draft(7_000, note = "Stale"), baseVersion = 1))
        env.engine.drain()

        val clash = assertIs<TransactionSyncState.Clash>(env.view(Captured.EXPENSE)!!.syncState)
        assertFalse(clash.deletedElsewhere)
        assertEquals(6_000, clash.theirs!!.amountMinor)
        assertEquals("Market", clash.theirs!!.counterparty)
        assertEquals(Captured.OWN_CATEGORY, clash.theirs!!.categoryId)
        assertEquals(2, clash.theirs!!.version)

        env.service.keepMine(Captured.EXPENSE)
        assertEquals(2, env.pendingDao.get("transaction", Captured.EXPENSE)!!.baseVersion)

        // Clashing again, "Use the other device's" stores the server copy and drops the change.
        env.engine.drain()
        env.service.useTheirs(Captured.EXPENSE)
        assertTrue(env.pendingDao.rows.value.isEmpty())
        val shown = env.view(Captured.EXPENSE)!!
        assertEquals(6_000, shown.shown.amountMinor)
        assertEquals(TransactionSyncState.None, shown.syncState)
    }

    @Test
    fun aSaveAfterADeletionElsewhereOffersRestoreWhichSendsThePatchThenThePutQaSent() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "transactions/${Captured.EXPENSE}", 409, Captured.PUT_AFTER_DELETE_CONFLICT_409)
        val env = env(backgroundScope, script)
        env.signedIn()
        env.seedCashAndBank()
        env.seed(dataOf(Captured.PUT_SAVED_200))
        val edit = draft(8_000, category = Captured.OWN_CATEGORY, note = "Phone edit")
        assertEquals(SaveResult.Saved, env.service.save(Captured.EXPENSE, edit, baseVersion = 2))
        env.engine.drain()
        val clash = assertIs<TransactionSyncState.Clash>(env.view(Captured.EXPENSE)!!.syncState)
        assertTrue(clash.deletedElsewhere)

        script.on(HttpMethod.Patch, "transactions/${Captured.EXPENSE}", 200, Captured.PATCH_RESTORED_200)
        script.on(HttpMethod.Put, "transactions/${Captured.EXPENSE}", 200, Captured.PUT_AFTER_RESTORE_200)
        env.service.keepMine(Captured.EXPENSE)
        env.engine.drain()

        val sent = env.requests.drop(1)
        assertEquals(listOf(HttpMethod.Patch, HttpMethod.Put), sent.map { it.method })
        assertEquals(qaRequest("""{"deleted_at":null,"version":3}"""), sent[0].jsonBody())
        assertEquals(
            qaRequest("""{"kind":"expense","date":"2026-10-01","amount_minor":8000,"currency":"EGP","category_id":"${Captured.OWN_CATEGORY}","counter_wallet_id":null,"description":"Phone edit","memo":null,"counterparty":null,"cleared":false,"version":4}"""),
            sent[1].jsonBody(),
        )
        assertTrue(env.pendingDao.rows.value.isEmpty())
        val row = env.transactionDao.get(Captured.EXPENSE)!!
        assertEquals(5, row.version)
        assertEquals(8_000, row.amountMinor)
        assertNull(row.deletedAt)
    }

    @Test
    fun aTransactionRemovedByARefreshWhileThePersonTypesIsSavedAndTheServerAnswersWithTheDeletionClash() = runTest {
        val script = Script()
        // A complete read of the wallet that no longer lists the transaction (deleted elsewhere).
        // (The real list of Bank: its only row is a transfer out of Cash, so it is also a complete read of Cash without the expense.)
        script.on(HttpMethod.Get, "wallets/${Captured.CASH}/transactions", 200, Captured.LIST_BANK_200)
        val env = env(backgroundScope, script)
        env.signedIn()
        env.seedCashAndBank()
        env.seed(dataOf(Captured.PUT_SAVED_200))
        val opened = env.view(Captured.EXPENSE)!!.server!!
        env.repository.refreshWallet(Captured.CASH)
        assertNull(env.view(Captured.EXPENSE))

        script.on(HttpMethod.Put, "transactions/${Captured.EXPENSE}", 409, Captured.PUT_AFTER_DELETE_CONFLICT_409)
        val edit = draft(8_000, category = Captured.OWN_CATEGORY, note = "Phone edit")
        assertEquals(SaveResult.Saved, env.service.save(Captured.EXPENSE, edit, baseVersion = 2, lastSeen = opened))
        // Listed again with the person's values while the change waits; a read meanwhile keeps it.
        assertEquals(8_000, env.view(Captured.EXPENSE)!!.shown.amountMinor)
        env.repository.refreshWallet(Captured.CASH)
        assertNotNull(env.view(Captured.EXPENSE))

        env.engine.drain()
        val clash = assertIs<TransactionSyncState.Clash>(env.view(Captured.EXPENSE)!!.syncState)
        assertTrue(clash.deletedElsewhere)
        assertEquals(2, env.requests.single { it.method == HttpMethod.Put }.jsonBody()["version"].toString().toLong())

        // "Accept the deletion": the tombstone removes it from the phone and nothing is left to send.
        env.service.useTheirs(Captured.EXPENSE)
        assertTrue(env.pendingDao.rows.value.isEmpty())
        assertNull(env.transactionDao.get(Captured.EXPENSE))
    }

    @Test
    fun aDeleteSendsItsVersionInABodyAndTheTombstoneHidesTheRowUntilTheWalletReadSettlesIt() = runTest {
        val script = Script()
        script.on(HttpMethod.Delete, "transactions/${Captured.EXPENSE}", 200, Captured.DELETE_TOMBSTONE_200)
        val env = env(backgroundScope, script)
        env.signedIn()
        env.seedCashAndBank(cashBalance = 94_000)
        env.seed(dataOf(Captured.PUT_SAVED_200))
        assertEquals(SaveResult.Saved, env.service.delete(Captured.EXPENSE))
        assertEquals(100_000, env.wallets.observeWallet(Captured.CASH).first()!!.displayedBalanceMinor)
        env.engine.drain()

        val delete = env.requests.single { it.method == HttpMethod.Delete }
        assertEquals(qaRequest("""{"version":2}"""), delete.jsonBody())
        assertTrue(env.pendingDao.rows.value.isEmpty())
        assertNotNull(env.transactionDao.get(Captured.EXPENSE)!!.deletedAt)
        assertNull(env.view(Captured.EXPENSE))
        assertEquals(100_000, env.wallets.observeWallet(Captured.CASH).first()!!.displayedBalanceMinor)

        assertTrue(env.reconciler.settle())
        assertNull(env.transactionDao.get(Captured.EXPENSE))
        assertEquals(100_000, env.wallets.observeWallet(Captured.CASH).first()!!.displayedBalanceMinor)
    }

    @Test
    fun aStaleDeleteBecomesADeleteClashWithTheLiveCopy() = runTest {
        val script = Script()
        script.on(HttpMethod.Delete, "transactions/${Captured.EXPENSE}", 409, Captured.DELETE_VERSION_CONFLICT_409)
        val env = env(backgroundScope, script)
        env.signedIn()
        env.seedCashAndBank()
        env.seed(dataOf(Captured.PUT_CREATED_201))
        env.service.delete(Captured.EXPENSE)
        env.engine.drain()
        val view = assertNotNull(env.view(Captured.EXPENSE))
        val clash = assertIs<TransactionSyncState.Clash>(view.syncState)
        assertTrue(clash.mineIsDeletion)
        assertFalse(clash.deletedElsewhere)
        assertEquals(2, clash.theirs!!.version)
        assertEquals(1, env.requests.single().jsonBody()["version"].toString().toLong())
    }

    private class RefusalCase(val name: String, val status: Int, val body: String, val kind: String, val expected: RefusalKind, val field: TransactionField?, val code: String?)

    @Test
    fun everyRealRefusalReadsAsItsReasonAndLandsOnItsField() = runTest {
        val cases = listOf(
            RefusalCase("currency changed", 422, Captured.PUT_CURRENCY_MISMATCH_CURRENCY_422, "expense", RefusalKind.CurrencyChanged, TransactionField.Currency, "CURRENCY_MISMATCH"),
            RefusalCase("currencies differ", 422, Captured.PUT_CURRENCY_MISMATCH_COUNTER_422, "transfer", RefusalKind.CurrenciesDiffer, TransactionField.Destination, "CURRENCY_MISMATCH"),
            RefusalCase("category unusable", 422, Captured.PUT_UNKNOWN_CATEGORY_422, "expense", RefusalKind.CategoryUnusable, TransactionField.Category, "INVALID_ENUM_VALUE"),
            RefusalCase("destination gone", 422, Captured.PUT_UNKNOWN_COUNTER_422, "transfer", RefusalKind.DestinationUnavailable, TransactionField.Destination, "INVALID_ENUM_VALUE"),
            RefusalCase("category required", 422, Captured.PUT_REQUIRED_CATEGORY_422, "expense", RefusalKind.Invalid, TransactionField.Category, "REQUIRED"),
            RefusalCase("amount", 422, Captured.PUT_AMOUNT_ZERO_422, "expense", RefusalKind.Invalid, TransactionField.Amount, "OUT_OF_RANGE"),
            RefusalCase("date", 422, Captured.PUT_DATE_OUT_OF_RANGE_422, "expense", RefusalKind.Invalid, TransactionField.Date, "OUT_OF_RANGE"),
            RefusalCase("note", 422, Captured.PUT_TEXT_TOO_LONG_422, "expense", RefusalKind.Invalid, TransactionField.Note, "OUT_OF_RANGE"),
            RefusalCase("kind", 422, Captured.PUT_IMMUTABLE_KIND_422, "expense", RefusalKind.Invalid, TransactionField.Kind, "IMMUTABLE_FIELD"),
            RefusalCase("destination changed", 422, Captured.PUT_IMMUTABLE_COUNTER_422, "transfer", RefusalKind.Invalid, TransactionField.Destination, "IMMUTABLE_FIELD"),
            RefusalCase("unknown member", 422, Captured.PUT_UNKNOWN_FIELD_422, "expense", RefusalKind.Invalid, null, null),
            RefusalCase("invalid id", 422, Captured.PUT_INVALID_ID_422, "expense", RefusalKind.Other, null, null),
            RefusalCase("not verified", 403, Captured.PUT_EMAIL_NOT_VERIFIED_403, "expense", RefusalKind.NotVerified, null, null),
            RefusalCase("viewer", 403, Captured.PUT_FORBIDDEN_VIEWER_403, "expense", RefusalKind.NoRightHere, null, null),
            RefusalCase("other wallet", 403, Captured.PUT_TRANSFER_FORBIDDEN_403, "transfer", RefusalKind.NoRightInOtherWallet, null, null),
            RefusalCase("not a member", 404, Captured.PUT_NOT_FOUND_404, "expense", RefusalKind.NoLongerAvailable, null, null),
            RefusalCase("never created", 404, Captured.PUT_UNKNOWN_ID_VERSION_404, "expense", RefusalKind.NoLongerAvailable, null, null),
        )
        for (case in cases) {
            val script = Script()
            script.on(HttpMethod.Put, "/transactions/", case.status, case.body)
            val env = env(backgroundScope, script)
            env.signedIn()
            env.seedCashAndBank()
            val transfer = case.kind == "transfer"
            val created = assertIs<CreateResult.Created>(
                env.service.create(
                    draft(1_00, category = if (transfer) null else Captured.GROCERIES, kind = case.kind, counter = if (transfer) Captured.BANK else null),
                ),
                case.name,
            )
            env.engine.drain()
            assertEquals(ChangeState.Refused.name, env.pendingDao.get("transaction", created.id)!!.state, case.name)
            val refused = assertIs<TransactionSyncState.Refused>(env.view(created.id)!!.syncState, case.name)
            assertEquals(case.expected, refused.reason.kind, case.name)
            assertNotNull(refused.reason.requestId, case.name)
            if (case.field != null) assertEquals(mapOf(case.field to case.code), refused.fields, case.name) else assertEquals(emptyMap(), refused.fields, case.name)
            // Discarding a refused create leaves nothing on the phone (FR-025).
            env.service.discard(created.id)
            assertNull(env.transactionDao.get(created.id), case.name)
        }
    }

    @Test
    fun threeOfflineTransactionsAreSentOldestFirstAndARefusalOfOneDoesNotStopTheOthers() = runTest {
        val refusedId = "01a0fd5b-0000-7000-8000-000000000002"
        val script = Script()
        script.on(HttpMethod.Put, "transactions/${Captured.EXPENSE}", 201, Captured.PUT_CREATED_201)
        script.on(HttpMethod.Put, "transactions/$refusedId", 422, Captured.PUT_UNKNOWN_CATEGORY_422)
        script.on(HttpMethod.Put, "transactions/${Captured.PARTNER_EXPENSE}", 201, Captured.PUT_PARTNER_CATEGORY_201)
        val env = env(backgroundScope, script, ids = listOf(Captured.EXPENSE, refusedId, Captured.PARTNER_EXPENSE))
        env.signedIn()
        env.seedCashAndBank()
        // Offline: nothing is drained while the three are recorded.
        env.service.create(draft(4_550, note = "Weekly shop"))
        env.service.create(draft(100, category = Captured.PARTNER_CATEGORY))
        env.service.create(draft(1_500, category = Captured.PARTNER_CATEGORY, note = "Partner lunch", date = LocalDate(2026, 10, 2)))
        assertEquals(93_850, env.wallets.observeWallet(Captured.CASH).first()!!.displayedBalanceMinor)

        env.engine.drain()
        val order = env.requests.filter { it.method == HttpMethod.Put }.map { it.url.encodedPath.substringAfterLast('/') }
        assertEquals(listOf(Captured.EXPENSE, refusedId, Captured.PARTNER_EXPENSE), order.distinct())
        assertNull(env.pendingDao.get("transaction", Captured.EXPENSE))
        assertNull(env.pendingDao.get("transaction", Captured.PARTNER_EXPENSE))
        assertEquals(RefusalKind.CategoryUnusable, assertIs<TransactionSyncState.Refused>(env.view(refusedId)!!.syncState).reason.kind)
        // The refused one still counts on the balance until the person edits or discards it.
        assertEquals(93_850, env.wallets.observeWallet(Captured.CASH).first()!!.displayedBalanceMinor)
    }

    @Test
    fun aCurrencyChangedRefusalOfANeverSentCreateIsSavedAgainInTheWalletsPresentCurrency() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "/transactions/", 422, Captured.PUT_CURRENCY_MISMATCH_CURRENCY_422)
        val env = env(backgroundScope, script, ids = listOf(Captured.EXPENSE))
        env.signedIn()
        env.seedCashAndBank()
        env.service.create(draft(4_550))
        env.engine.drain()
        val refused = assertIs<TransactionSyncState.Refused>(env.view(Captured.EXPENSE)!!.syncState)
        assertEquals(RefusalKind.CurrencyChanged, refused.reason.kind)

        // Another device changed the wallet to USD before this phone's first transaction reached the server.
        env.seedWallet(walletJson(id = Captured.CASH, name = "QA Cash", currency = "USD", opening = 100_000))
        assertEquals("USD", env.view(Captured.EXPENSE)!!.walletCurrency)
        script.on(HttpMethod.Put, "/transactions/", 201, Captured.PUT_CREATED_201)
        assertEquals(SaveResult.Saved, env.service.save(Captured.EXPENSE, draft(1_25).copy(currency = "EGP"), baseVersion = 0))
        env.engine.drain()
        val resent = env.requests.last { it.method == HttpMethod.Put }.jsonBody()
        assertEquals("\"USD\"", resent["currency"].toString())
        assertEquals("0", resent["version"].toString())
    }

    @Test
    fun aRestoreOfATransferWhoseDestinationWasDeletedIsRefusedAsDestinationUnavailable() = runTest {
        val tombstone = """{"type":"x","title":"Version conflict","status":409,"code":"VERSION_CONFLICT","request_id":"r","current":${dataOf(Captured.PUT_TRANSFER_CREATED_201).replace("\"deleted_at\":null", "\"deleted_at\":\"2026-10-02T16:05:43.340145Z\"").replace("\"version\":1", "\"version\":2")}}"""
        val script = Script()
        script.on(HttpMethod.Put, "/transactions/", 409, tombstone)
        val env = env(backgroundScope, script)
        env.signedIn()
        env.seedCashAndBank()
        env.seed(dataOf(Captured.PUT_TRANSFER_CREATED_201))
        env.service.save(Captured.TRANSFER, draft(60_000, category = null, kind = "transfer", counter = Captured.BANK), baseVersion = 1)
        env.engine.drain()
        assertTrue(assertIs<TransactionSyncState.Clash>(env.view(Captured.TRANSFER)!!.syncState).deletedElsewhere)

        script.on(HttpMethod.Patch, "/transactions/", 422, Captured.PATCH_RESTORE_COUNTER_GONE_422)
        env.service.keepMine(Captured.TRANSFER)
        env.engine.drain()
        val refused = assertIs<TransactionSyncState.Refused>(env.view(Captured.TRANSFER)!!.syncState)
        assertEquals(RefusalKind.DestinationUnavailable, refused.reason.kind)
        assertEquals(HttpMethod.Patch, env.requests.last().method)
    }

    @Test
    fun aPartnersTransferFromAWalletNotOnThePhoneReadsFromAnotherWalletAndIsDeletedThroughThisOne() = runTest {
        val script = Script()
        script.on(HttpMethod.Get, "wallets/${Captured.CASH}/transactions", 200, Captured.LIST_CASH_WITH_PARTNER_TRANSFER_200)
        script.on(HttpMethod.Delete, "transactions/${Captured.PARTNER_TRANSFER}", 200, Captured.DELETE_SOURCE_GONE_200)
        val env = env(backgroundScope, script)
        env.signedIn()
        env.seedWallet(walletJson(id = Captured.CASH, name = "QA Cash"))
        env.seedMember(Captured.USER_B, "Omar", walletId = Captured.CASH)
        env.repository.refreshWallet(Captured.CASH)

        val view = env.view(Captured.PARTNER_TRANSFER)!!
        assertEquals(Counterpart.Unknown, view.counterpart)
        assertEquals(Direction.In, view.direction)
        assertEquals(AddedBy.Member("Omar"), view.addedBy)
        assertFalse(view.canEdit)
        assertTrue(view.canDelete)

        assertEquals(SaveResult.Saved, env.service.delete(Captured.PARTNER_TRANSFER))
        env.engine.drain()
        val delete = env.requests.single { it.method == HttpMethod.Delete }
        // The source is not on the phone, so the path is the destination (ADR-0015 rule 5).
        assertTrue(delete.url.encodedPath.endsWith("wallets/${Captured.CASH}/transactions/${Captured.PARTNER_TRANSFER}"))
        assertTrue(env.pendingDao.rows.value.isEmpty())
    }

    @Test
    fun aPartnersCategoryAndTheReadOnlyLinksAreKeptOnEditAndNeverSent() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "transactions/${Captured.PARTNER_EXPENSE}", 200, Captured.PUT_EDIT_PARTNER_CATEGORY_200)
        val env = env(backgroundScope, script)
        env.signedIn()
        env.seedCashAndBank()
        env.seed(dataOf(Captured.PUT_PARTNER_CATEGORY_201))
        val opened = env.view(Captured.PARTNER_EXPENSE)!!
        assertEquals(Captured.PARTNER_CATEGORY, opened.shown.categoryId)
        assertEquals(AddedBy.AnotherMember, opened.addedBy)
        val edit = opened.shown.copy(amountMinor = 1_600)
        env.service.save(Captured.PARTNER_EXPENSE, edit, opened.baseVersion)
        env.engine.drain()
        val body = env.requests.single().jsonBody()
        assertEquals(
            qaRequest("""{"kind":"expense","date":"2026-10-02","amount_minor":1600,"currency":"EGP","category_id":"${Captured.PARTNER_CATEGORY}","counter_wallet_id":null,"description":"Partner lunch","memo":null,"counterparty":null,"cleared":false,"version":1}"""),
            body,
        )
        assertEquals(2, env.transactionDao.get(Captured.PARTNER_EXPENSE)!!.version)
    }
}
