package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FakeClock
import com.fares.saveme.core.sync.FakeHandler
import com.fares.saveme.core.sync.FakePendingChangeDao
import com.fares.saveme.core.sync.FakeSyncScheduler
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.FakeSyncScopeDao
import com.fares.saveme.feature.transactions.FakeTransactionDao
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spec 014 research R-03: effective lines in a range and the balance effect before it, waiting changes overlaid. */
class TransactionReportLedgerTest {

    private val from = LocalDate(2026, 10, 1)
    private val to = LocalDate(2026, 10, 31)
    private val ids = setOf("w1", "w2")

    private class Rig {
        val dao = FakeTransactionDao()
        val pending = FakePendingChangeDao()
        val scopes = FakeSyncScopeDao()
        val clock = FakeClock()
        val engine = SyncEngine(pending, scopes, listOf(FakeHandler("transaction")), FakeSyncScheduler(), currentAccountId = { "u1" }, clock = clock)
        val repository = TransactionRepository(dao, scopes, accountId = { "u1" }, hasQueuedChange = { engine.get("transaction", it) != null }, clock = clock)
        val ledger = TransactionReportLedger({ engine }, repository, accountId = { "u1" })
    }

    private fun tx(
        id: String,
        wallet: String = "w1",
        kind: String = "expense",
        date: String = "2026-10-05",
        amount: Long = 1_000,
        counter: String? = null,
        category: String? = "c1",
        version: Long = 1,
        deletedAt: String? = null,
        pendingConfirmation: Boolean = false,
        account: String = "u1",
    ) = TransactionEntity(
        id, account, wallet, counter, kind, date, amount, "EGP", category, null, null, null, false, "u1", null, null, version, 7,
        "2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z", deletedAt, "2026-10-01T00:00:00Z", pendingConfirmation = pendingConfirmation,
    )

    private fun payload(
        kind: String = "expense", date: String = "2026-10-05", amount: Long = 1_000, category: String? = "c1", counter: String? = null,
    ) = AppJson.encodeToString(TransactionPayload.serializer(), TransactionPayload(kind, date, amount, "EGP", category, counter))

    private suspend fun Rig.snapshot(wallets: Set<String> = ids) = ledger.observe(wallets, from, to).first()

    @Test
    fun linesTouchingTheWalletsInTheRangeAreReturnedWithSourceOrDestination() = runTest {
        val rig = Rig()
        rig.dao.upsertAll(
            listOf(
                tx("inRange"), tx("income", kind = "income", wallet = "w2"), tx("outWallet", wallet = "w9"),
                tx("toMe", kind = "transfer", wallet = "w9", counter = "w1", category = null),
                tx("before", date = "2026-09-30"), tx("after", date = "2026-11-01"), tx("deleted", deletedAt = "2026-10-06T00:00:00Z"),
                tx("someoneElse", account = "u9"),
            ),
        )
        val snapshot = rig.snapshot()
        assertEquals(setOf("inRange", "income", "toMe"), snapshot.lines.map { it.id }.toSet())
        assertEquals(setOf("inRange", "income", "toMe"), snapshot.syncedLines.map { it.id }.toSet())
        assertTrue(snapshot.lines.none { it.waiting })
        assertEquals(emptyList(), snapshot.waitingTouches)
    }

    @Test
    fun theBalanceBeforeTheRangeSumsTheSignedEffectPerWalletAndLeavesDeletedAndUnknownKindsOut() = runTest {
        val rig = Rig()
        rig.dao.upsertAll(
            listOf(
                tx("i", kind = "income", date = "2026-09-01", amount = 5_000),
                tx("e", date = "2026-09-02", amount = 1_200),
                tx("t", kind = "transfer", date = "2026-09-03", amount = 700, wallet = "w1", counter = "w2", category = null),
                tx("x", kind = "refund", date = "2026-09-04", amount = 99),
                tx("d", kind = "income", date = "2026-09-05", amount = 123, deletedAt = "2026-09-06T00:00:00Z"),
                tx("onTheDay", date = "2026-10-01", amount = 50),
            ),
        )
        val snapshot = rig.snapshot()
        assertEquals(mapOf("w1" to 5_000 - 1_200 - 700L, "w2" to 700L), snapshot.before)
        assertEquals(snapshot.before, snapshot.syncedBefore)
    }

    @Test
    fun aWaitingCreateIsALineAndAPlaceholderIsNotSynced() = runTest {
        val rig = Rig()
        rig.dao.upsert(tx("new", version = 0, amount = 250))
        rig.engine.enqueue("transaction", "new", SyncScope(ScopeKind.Wallet, "w1"), 0, payload(amount = 250))
        val snapshot = rig.snapshot()
        assertEquals(listOf(250L), snapshot.lines.map { it.amountMinor })
        assertTrue(snapshot.lines.single().waiting)
        assertEquals(emptyList(), snapshot.syncedLines)
        assertEquals(listOf(LedgerTouch("w1", LocalDate(2026, 10, 5))), snapshot.waitingTouches)
    }

    @Test
    fun aWaitingCreateWithoutAPlaceholderStillAddsWhatItSays() = runTest {
        val rig = Rig()
        rig.engine.enqueue("transaction", "orphan", SyncScope(ScopeKind.Wallet, "w2"), 0, payload(kind = "income", amount = 90))
        val snapshot = rig.snapshot()
        assertEquals(listOf("orphan"), snapshot.lines.map { it.id })
        assertEquals("w2", snapshot.lines.single().walletId)
    }

    @Test
    fun aWaitingEditReplacesTheStoredValuesAndTouchesBothSides() = runTest {
        val rig = Rig()
        rig.dao.upsert(tx("e", amount = 1_000, category = "c1", date = "2026-10-05"))
        rig.engine.enqueue("transaction", "e", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(amount = 1_500, category = "c2", date = "2026-10-07"))
        val snapshot = rig.snapshot()
        val shown = snapshot.lines.single()
        assertEquals(1_500, shown.amountMinor)
        assertEquals("c2", shown.categoryId)
        assertEquals(LocalDate(2026, 10, 7), shown.date)
        assertTrue(shown.waiting)
        assertEquals(1_000, snapshot.syncedLines.single().amountMinor, "the synced lines ignore the queue")
        assertEquals(setOf(LedgerTouch("w1", LocalDate(2026, 10, 5)), LedgerTouch("w1", LocalDate(2026, 10, 7))), snapshot.waitingTouches.toSet())
    }

    @Test
    fun anEditMovingTheDateOutOfTheRangeOrBeforeItShiftsTheBalanceBefore() = runTest {
        val rig = Rig()
        rig.dao.upsert(tx("e", date = "2026-10-05", amount = 1_000))
        rig.engine.enqueue("transaction", "e", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(date = "2026-09-20", amount = 1_000))
        val moved = rig.snapshot()
        assertEquals(emptyList(), moved.lines)
        assertEquals(mapOf("w1" to -1_000L), moved.before, "the expense now happened before the range")
        assertEquals(emptyMap(), moved.syncedBefore)
        assertEquals(listOf("e"), moved.syncedLines.map { it.id })
    }

    @Test
    fun anEditMovingAStoredRowFromBeforeTheRangeIntoItGivesTheBalanceBack() = runTest {
        val rig = Rig()
        rig.dao.upsert(tx("e", date = "2026-09-20", amount = 400))
        rig.engine.enqueue("transaction", "e", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(date = "2026-10-02", amount = 400))
        val snapshot = rig.snapshot()
        assertEquals(listOf("e"), snapshot.lines.map { it.id })
        assertEquals(0L, snapshot.before["w1"] ?: 0L)
        assertEquals(mapOf("w1" to -400L), snapshot.syncedBefore)
    }

    @Test
    fun aWaitingDeletionRemovesTheLineAndTheBalanceEffect() = runTest {
        val rig = Rig()
        rig.dao.upsertAll(listOf(tx("inRange"), tx("old", date = "2026-09-10", amount = 300)))
        rig.engine.enqueueDelete("transaction", "inRange", SyncScope(ScopeKind.Wallet, "w1"), 1)
        rig.engine.enqueueDelete("transaction", "old", SyncScope(ScopeKind.Wallet, "w1"), 1)
        val snapshot = rig.snapshot()
        assertEquals(emptyList(), snapshot.lines)
        assertEquals(0L, snapshot.before["w1"] ?: 0L)
        assertEquals(listOf("inRange"), snapshot.syncedLines.map { it.id })
        assertEquals(mapOf("w1" to -300L), snapshot.syncedBefore)
        assertEquals(2, snapshot.waitingTouches.size)
    }

    @Test
    fun refusedAndClashingChangesOverlayToo() = runTest {
        val rig = Rig()
        rig.dao.upsertAll(listOf(tx("refused", amount = 100), tx("clash", amount = 100)))
        rig.engine.enqueue("transaction", "refused", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(amount = 111))
        rig.engine.enqueue("transaction", "clash", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(amount = 222))
        rig.pending.upsert(rig.pending.get("transaction", "refused")!!.copy(state = "Refused"))
        rig.pending.upsert(rig.pending.get("transaction", "clash")!!.copy(state = "Clash"))
        assertEquals(mapOf("refused" to 111L, "clash" to 222L), rig.snapshot().lines.associate { it.id to it.amountMinor })
    }

    @Test
    fun aConfirmationWaitingOnThePhoneIsAnActualLineAndCountsBeforeTheRangeToo() = runTest {
        val rig = Rig()
        rig.dao.upsertAll(
            listOf(
                tx("confirmed", version = 0, pendingConfirmation = true, amount = 600),
                tx("older", version = 0, pendingConfirmation = true, date = "2026-09-25", amount = 80),
            ),
        )
        val snapshot = rig.snapshot()
        assertEquals(listOf("confirmed"), snapshot.lines.map { it.id })
        assertTrue(snapshot.lines.single().waiting)
        assertEquals(mapOf("w1" to -80L), snapshot.before)
        assertEquals(emptyList(), snapshot.syncedLines)
        assertEquals(emptyMap(), snapshot.syncedBefore)
    }

    @Test
    fun aTransferTouchesBothWalletsOfAWaitingChange() = runTest {
        val rig = Rig()
        rig.dao.upsert(tx("t", kind = "transfer", wallet = "w1", counter = "w2", category = null, version = 0))
        rig.engine.enqueue("transaction", "t", SyncScope(ScopeKind.Wallet, "w1"), 0, payload(kind = "transfer", counter = "w2", category = null))
        val snapshot = rig.snapshot()
        assertEquals(setOf("w1", "w2"), snapshot.waitingTouches.map { it.walletId }.toSet())
        assertEquals("w2", snapshot.lines.single().counterWalletId)
    }

    @Test
    fun aChangeInAnotherWalletDoesNotTouchTheAskedOnes() = runTest {
        val rig = Rig()
        rig.dao.upsert(tx("e", wallet = "w9"))
        rig.engine.enqueue("transaction", "e", SyncScope(ScopeKind.Wallet, "w9"), 1, payload(amount = 5))
        val snapshot = rig.snapshot()
        assertEquals(emptyList(), snapshot.lines)
        assertEquals(emptyList(), snapshot.waitingTouches)
    }

    @Test
    fun nothingIsReadWhileSignedOutOrForNoWallets() = runTest {
        val rig = Rig()
        rig.dao.upsert(tx("a"))
        val signedOut = TransactionReportLedger({ rig.engine }, rig.repository, accountId = { null })
        assertEquals(LedgerSnapshot.Empty, signedOut.observe(ids, from, to).first())
        assertEquals(LedgerSnapshot.Empty, rig.ledger.observe(emptySet(), from, to).first())
        assertEquals(LedgerSnapshot.Empty, NoReportLedger.observe(ids, from, to).first())
    }

    private suspend fun Rig.withExtra(extraFrom: String, extraTo: String = "2026-10-31") =
        ledger.observe(ids, from, to, LocalDate.parse(extraFrom), LocalDate.parse(extraTo)).first()

    @Test
    fun theDaysAroundTheRangeComeAsSumsPerDayNotRows() = runTest {
        val rig = Rig()
        rig.dao.upsertAll(
            listOf(
                tx("i1", kind = "income", date = "2026-09-05", amount = 1_000), tx("i2", kind = "income", date = "2026-09-05", amount = 500, wallet = "w2"),
                tx("e1", date = "2026-09-05", amount = 300), tx("e2", date = "2026-08-20", amount = 40),
                tx("inRange", date = "2026-10-05"), tx("t", kind = "transfer", date = "2026-09-06", amount = 9, wallet = "w1", counter = "w2", category = null),
                tx("other", date = "2026-09-05", wallet = "w9", amount = 77), tx("tooOld", date = "2026-07-31", amount = 88),
                tx("after", date = "2026-11-04", amount = 66, kind = "income"), tx("deleted", date = "2026-09-07", deletedAt = "2026-09-08T00:00:00Z"),
            ),
        )
        val snapshot = rig.withExtra("2026-08-01", "2026-11-30")
        assertEquals(listOf("inRange"), snapshot.lines.map { it.id })
        assertEquals(
            listOf(
                com.fares.saveme.feature.transactions.domain.DaySum(LocalDate(2026, 8, 20), 0, 40),
                com.fares.saveme.feature.transactions.domain.DaySum(LocalDate(2026, 9, 5), 1_500, 300),
                com.fares.saveme.feature.transactions.domain.DaySum(LocalDate(2026, 11, 4), 66, 0),
            ),
            snapshot.daily,
        )
        assertEquals(snapshot.daily, snapshot.syncedDaily)
        assertEquals(emptyList(), rig.snapshot().daily, "with no extra window there are no sums")
    }

    @Test
    fun aWaitingChangeCorrectsTheDailySumsAtItsStoredAndItsShownDate() = runTest {
        val rig = Rig()
        rig.dao.upsertAll(listOf(tx("edited", date = "2026-09-05", amount = 300), tx("deleted", date = "2026-09-06", amount = 70), tx("new", date = "2026-09-07", version = 0, amount = 25)))
        rig.engine.enqueue("transaction", "edited", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(date = "2026-09-09", amount = 350))
        rig.engine.enqueueDelete("transaction", "deleted", SyncScope(ScopeKind.Wallet, "w1"), 1)
        rig.engine.enqueue("transaction", "new", SyncScope(ScopeKind.Wallet, "w1"), 0, payload(date = "2026-09-07", amount = 25))
        rig.dao.upsert(tx("phoneOnly", version = 0, pendingConfirmation = true, date = "2026-09-10", amount = 11))
        val snapshot = rig.withExtra("2026-08-01")
        assertEquals(
            mapOf(LocalDate(2026, 9, 7) to 25L, LocalDate(2026, 9, 9) to 350L, LocalDate(2026, 9, 10) to 11L),
            snapshot.daily.associate { it.date to it.expensesMinor },
        )
        assertEquals(mapOf(LocalDate(2026, 9, 5) to 300L, LocalDate(2026, 9, 6) to 70L), snapshot.syncedDaily.associate { it.date to it.expensesMinor })
    }
}
