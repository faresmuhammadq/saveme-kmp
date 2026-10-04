package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.transactions.data.BalanceEffect
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.data.TransactionSyncHandler
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * The person's own expenses with the server's attribution rules (ADR-0019, spec 012 research R-05):
 *
 * 1. effective values are the waiting change over the stored row (a waiting deletion removes it); refused and clashing
 *    changes overlay too, as the transaction list shows them;
 * 2. only live expenses (the SQL already leaves out rows kept for a balance carry);
 * 3. the author is `created_by`; a phone-only recurring confirmation counts for its rule's author, else the person;
 * 4. the author is the signed-in person and the wallet is one the person belongs to (any role);
 * 5. [SpendingSnapshot.syncedExpenses] are the server's copies only (version above zero, no overlay, no confirmations),
 *    [SpendingSnapshot.waitingTouches] the stored and the effective side of every waiting change.
 *
 * One Room snapshot of the rows is read together with the queue (the [TransactionPostings] pattern), so the queue and
 * the rows never disagree while a send's answer is being stored.
 *
 * @param engine resolved on use: the engine is built from every sync handler (spec 006 lesson).
 * @param memberWalletIds the ids of the wallets the person belongs to, as C4 lists them.
 * @param ruleAuthorOf the author of a recurring rule on this phone ([RecurringLinks.ruleAuthorOf]).
 */
class TransactionOwnSpending(
    private val engine: () -> SyncEngine,
    private val repository: TransactionRepository,
    private val accountId: () -> String?,
    private val memberWalletIds: () -> Flow<Set<String>>,
    private val ruleAuthorOf: suspend (String) -> String?,
) : OwnSpending {

    private class Raw(val changes: List<PendingChange>, val rows: List<TransactionEntity>)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(from: LocalDate, to: LocalDate): Flow<SpendingSnapshot> {
        val account = accountId() ?: return flowOf(SpendingSnapshot(emptyList(), emptyList(), emptyList()))
        val raw = engine().observeAll(TransactionSyncHandler.RECORD_TYPE).flatMapLatest { changes ->
            repository.observeForSpending(account, from.toString(), to.toString(), changes.map { it.recordId }).map { rows -> Raw(changes, rows) }
        }
        return combine(raw, memberWalletIds()) { snapshot, wallets -> build(account, from, to, snapshot, wallets) }
    }

    private suspend fun build(account: String, from: LocalDate, to: LocalDate, raw: Raw, wallets: Set<String>): SpendingSnapshot {
        val changeById = raw.changes.associateBy { it.recordId }
        val rowIds = raw.rows.mapTo(HashSet()) { it.id }
        val expenses = mutableListOf<OwnExpense>()
        val synced = mutableListOf<OwnExpense>()
        val touches = mutableListOf<SpendingKey>()

        fun inRange(date: LocalDate) = date >= from && date <= to

        for (row in raw.rows) {
            if (row.pendingConfirmation && row.version == 0L) {
                val author = row.recurringRuleId?.let { ruleAuthorOf(it) } ?: account
                val date = parseDate(row.date)
                if (author == account && row.walletId in wallets && row.kind == EXPENSE) {
                    touches += SpendingKey(date, row.currency, row.categoryId)
                    if (inRange(date)) expenses += OwnExpense(row.id, date, row.amountMinor, row.currency, row.categoryId, waiting = true)
                }
                continue
            }
            val change = changeById[row.id]
            val storedOwn = row.kind == EXPENSE && row.createdBy == account && row.walletId in wallets
            val storedDate = parseDate(row.date)
            if (storedOwn && row.version > 0 && inRange(storedDate)) {
                synced += OwnExpense(row.id, storedDate, row.amountMinor, row.currency, row.categoryId, waiting = change != null)
            }
            if (change == null) {
                if (storedOwn && inRange(storedDate)) expenses += OwnExpense(row.id, storedDate, row.amountMinor, row.currency, row.categoryId, waiting = false)
                continue
            }
            if (storedOwn) touches += SpendingKey(storedDate, row.currency, row.categoryId)
            val payload = if (change.isDelete) null else decodePayload(change.payloadJson)
            if (payload != null && payload.kind == EXPENSE && row.createdBy == account && row.walletId in wallets) {
                val date = parseDate(payload.date)
                touches += SpendingKey(date, payload.currency, payload.categoryId)
                if (inRange(date)) expenses += OwnExpense(row.id, date, payload.amountMinor, payload.currency, payload.categoryId, waiting = true)
            }
        }

        // A waiting create whose placeholder row is missing still adds what it says; it is the person's own.
        for (change in raw.changes) {
            if (change.recordId in rowIds || change.isDelete) continue
            val payload = decodePayload(change.payloadJson) ?: continue
            if (payload.kind != EXPENSE || change.scope.id !in wallets) continue
            val date = parseDate(payload.date)
            touches += SpendingKey(date, payload.currency, payload.categoryId)
            if (inRange(date)) expenses += OwnExpense(change.recordId, date, payload.amountMinor, payload.currency, payload.categoryId, waiting = true)
        }
        return SpendingSnapshot(expenses, synced, touches.distinct())
    }

    private fun parseDate(value: String): LocalDate = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate(1970, 1, 1))

    private companion object {
        const val EXPENSE = BalanceEffect.EXPENSE
    }
}
