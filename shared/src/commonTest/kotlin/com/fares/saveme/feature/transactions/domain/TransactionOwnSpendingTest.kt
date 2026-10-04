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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The server's attribution rules on the phone, waiting changes overlaid (spec 012 research R-05, ADR-0019). */
class TransactionOwnSpendingTest {

    private val from = LocalDate(2026, 10, 1)
    private val to = LocalDate(2026, 10, 31)
    private val user = SyncScope(ScopeKind.User, "u1")

    private class Rig(scope: kotlinx.coroutines.CoroutineScope) {
        val dao = FakeTransactionDao()
        val pending = FakePendingChangeDao()
        val scopes = FakeSyncScopeDao()
        val clock = FakeClock()
        val engine = SyncEngine(pending, scopes, listOf(FakeHandler("transaction")), FakeSyncScheduler(), currentAccountId = { "u1" }, clock = clock)
        val repository = TransactionRepository(dao, scopes, accountId = { "u1" }, hasQueuedChange = { engine.get("transaction", it) != null }, clock = clock)
        val wallets = MutableStateFlow(setOf("w1", "w2"))
        val authors = mutableMapOf<String, String?>()
        val spending = TransactionOwnSpending({ engine }, repository, accountId = { "u1" }, memberWalletIds = { wallets }, ruleAuthorOf = { authors[it] })
    }

    private fun tx(
        id: String,
        wallet: String = "w1",
        kind: String = "expense",
        date: String = "2026-10-05",
        amount: Long = 1_000,
        currency: String = "EGP",
        category: String? = "c1",
        by: String = "u1",
        version: Long = 1,
        deletedAt: String? = null,
        pendingConfirmation: Boolean = false,
        ruleId: String? = null,
        account: String = "u1",
    ) = TransactionEntity(
        id, account, wallet, null, kind, date, amount, currency, category, null, null, null, false, by, ruleId, null, version, 7,
        "2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z", deletedAt, "2026-10-01T00:00:00Z", pendingConfirmation = pendingConfirmation,
    )

    private fun payload(
        date: String = "2026-10-05", amount: Long = 1_000, category: String? = "c1", kind: String = "expense", currency: String = "EGP",
    ) = AppJson.encodeToString(TransactionPayload.serializer(), TransactionPayload(kind, date, amount, currency, category))

    private suspend fun Rig.snapshot() = spending.observe(from, to).first()

    @Test
    fun onlyTheSignedInPersonsLiveExpensesInTheirWalletsCount() = runTest {
        val rig = Rig(backgroundScope)
        rig.dao.upsertAll(
            listOf(
                tx("own"), tx("income", kind = "income"), tx("transfer", kind = "transfer"),
                tx("deleted", deletedAt = "2026-10-06T00:00:00Z"), tx("partner", by = "u2"),
                tx("elsewhere", wallet = "w9"), tx("someoneElsesAccount", account = "u9"), tx("viewer", wallet = "w2"),
            ),
        )
        val snapshot = rig.snapshot()
        assertEquals(setOf("own", "viewer"), snapshot.expenses.map { it.id }.toSet())
        assertEquals(setOf("own", "viewer"), snapshot.syncedExpenses.map { it.id }.toSet())
        assertTrue(snapshot.expenses.none { it.waiting })
        assertEquals(emptyList(), snapshot.waitingTouches)
    }

    @Test
    fun aWalletThePersonLeavesStopsCounting() = runTest {
        val rig = Rig(backgroundScope)
        rig.dao.upsertAll(listOf(tx("a", wallet = "w1"), tx("b", wallet = "w2")))
        assertEquals(2, rig.snapshot().expenses.size)
        rig.wallets.value = setOf("w1")
        assertEquals(listOf("a"), rig.snapshot().expenses.map { it.id })
    }

    @Test
    fun dateAndRangeUseTheEffectiveCalendarDate() = runTest {
        val rig = Rig(backgroundScope)
        rig.dao.upsertAll(listOf(tx("first", date = "2026-10-01"), tx("last", date = "2026-10-31"), tx("before", date = "2026-09-30"), tx("after", date = "2026-11-01")))
        assertEquals(setOf("first", "last"), rig.snapshot().expenses.map { it.id }.toSet())
    }

    @Test
    fun aWaitingCreateIsAddedAndAWaitingPlaceholderIsNotSynced() = runTest {
        val rig = Rig(backgroundScope)
        rig.dao.upsert(tx("new", version = 0, amount = 250))
        rig.engine.enqueue("transaction", "new", SyncScope(ScopeKind.Wallet, "w1"), 0, payload(amount = 250))
        val snapshot = rig.snapshot()
        assertEquals(listOf(250L), snapshot.expenses.map { it.amountMinor })
        assertTrue(snapshot.expenses.single().waiting)
        assertEquals(emptyList(), snapshot.syncedExpenses, "the server does not have it yet")
        assertEquals(LocalDate(2026, 10, 5), snapshot.waitingTouches.first().date)
    }

    @Test
    fun aWaitingEditReplacesTheStoredValuesAndTouchesBothSides() = runTest {
        val rig = Rig(backgroundScope)
        rig.dao.upsert(tx("e", amount = 1_000, category = "c1", date = "2026-10-05"))
        rig.engine.enqueue("transaction", "e", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(amount = 1_500, category = "c2", date = "2026-10-07"))
        val snapshot = rig.snapshot()
        val shown = snapshot.expenses.single()
        assertEquals(1_500, shown.amountMinor)
        assertEquals("c2", shown.categoryId)
        assertEquals(LocalDate(2026, 10, 7), shown.date)
        assertTrue(shown.waiting)
        assertEquals(1_000, snapshot.syncedExpenses.single().amountMinor, "the synced figure ignores the queue")
        assertEquals(
            setOf(SpendingKey(LocalDate(2026, 10, 5), "EGP", "c1"), SpendingKey(LocalDate(2026, 10, 7), "EGP", "c2")),
            snapshot.waitingTouches.toSet(),
        )
    }

    @Test
    fun anEditMovingTheDateOutOfTheRangeRemovesItFromTheShownListButNotFromTheSyncedOne() = runTest {
        val rig = Rig(backgroundScope)
        rig.dao.upsert(tx("e", date = "2026-10-05"))
        rig.engine.enqueue("transaction", "e", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(date = "2026-11-02"))
        val snapshot = rig.snapshot()
        assertEquals(emptyList(), snapshot.expenses)
        assertEquals(listOf("e"), snapshot.syncedExpenses.map { it.id })
        assertEquals(2, snapshot.waitingTouches.size)
    }

    @Test
    fun anEditMovingADateIntoTheRangeBringsItInWithoutBeingInTheStoredQuery() = runTest {
        val rig = Rig(backgroundScope)
        rig.dao.upsert(tx("e", date = "2026-09-20"))
        rig.engine.enqueue("transaction", "e", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(date = "2026-10-02"))
        val snapshot = rig.snapshot()
        assertEquals(listOf("e"), snapshot.expenses.map { it.id })
        assertEquals(emptyList(), snapshot.syncedExpenses)
    }

    @Test
    fun anEditChangingTheKindAwayFromExpenseRemovesItAndAnIncomeBecomingAnExpenseAddsIt() = runTest {
        val rig = Rig(backgroundScope)
        rig.dao.upsertAll(listOf(tx("was-expense"), tx("was-income", kind = "income")))
        rig.engine.enqueue("transaction", "was-expense", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(kind = "income"))
        rig.engine.enqueue("transaction", "was-income", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(kind = "expense", amount = 70))
        val snapshot = rig.snapshot()
        assertEquals(listOf("was-income"), snapshot.expenses.map { it.id })
        assertEquals(70, snapshot.expenses.single().amountMinor)
        assertEquals(listOf("was-expense"), snapshot.syncedExpenses.map { it.id })
    }

    @Test
    fun aWaitingDeletionRemovesItFromTheShownListButTheStoredRowStaysSynced() = runTest {
        val rig = Rig(backgroundScope)
        rig.dao.upsert(tx("gone"))
        rig.engine.enqueueDelete("transaction", "gone", SyncScope(ScopeKind.Wallet, "w1"), 1)
        val snapshot = rig.snapshot()
        assertEquals(emptyList(), snapshot.expenses)
        assertEquals(listOf("gone"), snapshot.syncedExpenses.map { it.id })
        assertEquals(listOf(SpendingKey(LocalDate(2026, 10, 5), "EGP", "c1")), snapshot.waitingTouches)
    }

    @Test
    fun refusedAndClashingChangesOverlayToo() = runTest {
        val rig = Rig(backgroundScope)
        rig.dao.upsertAll(listOf(tx("refused", amount = 100), tx("clash", amount = 100)))
        rig.engine.enqueue("transaction", "refused", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(amount = 111))
        rig.engine.enqueue("transaction", "clash", SyncScope(ScopeKind.Wallet, "w1"), 1, payload(amount = 222))
        rig.pending.upsert(rig.pending.get("transaction", "refused")!!.copy(state = "Refused"))
        rig.pending.upsert(rig.pending.get("transaction", "clash")!!.copy(state = "Clash"))
        assertEquals(mapOf("refused" to 111L, "clash" to 222L), rig.snapshot().expenses.associate { it.id to it.amountMinor })
    }

    @Test
    fun aConfirmationWaitingOnThePhoneCountsForItsRulesAuthor() = runTest {
        val rig = Rig(backgroundScope)
        rig.dao.upsertAll(
            listOf(
                tx("mine", version = 0, pendingConfirmation = true, ruleId = "r-mine"),
                tx("partners", version = 0, pendingConfirmation = true, ruleId = "r-partner"),
                tx("unknownRule", version = 0, pendingConfirmation = true, ruleId = "r-gone"),
                tx("noRule", version = 0, pendingConfirmation = true, ruleId = null),
            ),
        )
        rig.authors["r-mine"] = "u1"
        rig.authors["r-partner"] = "u2"
        val snapshot = rig.snapshot()
        assertEquals(setOf("mine", "unknownRule", "noRule"), snapshot.expenses.map { it.id }.toSet(), "a partner's rule's occurrence is theirs")
        assertTrue(snapshot.expenses.all { it.waiting })
        assertEquals(emptyList(), snapshot.syncedExpenses, "the synced figure ignores phone-only confirmations")
        assertEquals(1, snapshot.waitingTouches.size, "the same date, currency and category is one touch")
    }

    @Test
    fun anIncomeConfirmationWaitingOnThePhoneCountsForNothing() = runTest {
        val rig = Rig(backgroundScope)
        rig.dao.upsert(tx("pay", kind = "income", version = 0, pendingConfirmation = true, ruleId = "r"))
        val snapshot = rig.snapshot()
        assertEquals(emptyList(), snapshot.expenses)
        assertEquals(emptyList(), snapshot.waitingTouches)
    }

    @Test
    fun nothingIsReadWhileSignedOut() = runTest {
        val rig = Rig(backgroundScope)
        rig.dao.upsert(tx("a"))
        val signedOut = TransactionOwnSpending({ rig.engine }, rig.repository, accountId = { null }, memberWalletIds = { rig.wallets }, ruleAuthorOf = { null })
        assertEquals(SpendingSnapshot(emptyList(), emptyList(), emptyList()), signedOut.observe(from, to).first())
    }

    @Test
    fun theDefaultHookShowsNothing() = runTest {
        assertEquals(SpendingSnapshot(emptyList(), emptyList(), emptyList()), NoOwnSpending.observe(from, to).first())
        assertEquals(null, NoRecurringLinks.ruleAuthorOf("r"), "default-off: the rule is unknown")
    }
}
