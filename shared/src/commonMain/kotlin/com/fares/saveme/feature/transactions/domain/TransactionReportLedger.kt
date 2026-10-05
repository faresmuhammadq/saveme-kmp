package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.transactions.data.BalanceEffect
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.data.TransactionSyncHandler
import com.fares.saveme.feature.transactions.data.local.DaySumRow
import com.fares.saveme.feature.transactions.data.local.ReportRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/**
 * The transactions behind a report (spec 014 research R-03), with the rules of [TransactionOwnSpending] and
 * [TransactionPostings]:
 *
 * 1. effective values are the waiting change over the stored row (a waiting deletion removes it); refused and clashing
 *    changes overlay too, as the transaction list shows them;
 * 2. deleted rows and kinds this app does not know contribute nothing;
 * 3. a phone-only recurring confirmation is an actual line;
 * 4. [LedgerSnapshot.before] and [LedgerSnapshot.daily] start from the server's rows (summed in SQL) and are corrected by
 *    every waiting change whose stored or shown date falls in them;
 * 5. the `synced*` members ignore the queue and phone-only rows.
 *
 * Only the ten columns a report needs are read, and only the range's rows: the earlier and later days of a trend come as one
 * sum per day. The queue is read together with the rows (the [TransactionPostings] pattern), so a send's answer never counts
 * twice.
 *
 * @param engine resolved on use: the engine is built from every sync handler (spec 006 lesson).
 */
class TransactionReportLedger(
    private val engine: () -> SyncEngine,
    private val repository: TransactionRepository,
    private val accountId: () -> String?,
) : ReportLedger {

    private class Windows(val from: LocalDate, val to: LocalDate, val extraFrom: LocalDate, val extraTo: LocalDate) {
        fun inRange(date: LocalDate) = date >= from && date <= to
        fun inExtra(date: LocalDate) = !inRange(date) && date >= extraFrom && date <= extraTo
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(walletIds: Set<String>, from: LocalDate, to: LocalDate, extraFrom: LocalDate, extraTo: LocalDate): Flow<LedgerSnapshot> {
        val account = accountId()
        if (account == null || walletIds.isEmpty()) return flowOf(LedgerSnapshot.Empty)
        val windows = Windows(from, to, minOf(from, extraFrom), maxOf(to, extraTo))
        return engine().observeAll(TransactionSyncHandler.RECORD_TYPE).flatMapLatest { changes ->
            combine(
                repository.observeForReport(account, walletIds, from.toString(), to.toString(), changes.map { it.recordId }),
                repository.observeBalancesBefore(account, walletIds, from.toString()),
                repository.observeDailySums(account, walletIds, from.toString(), to.toString(), windows.extraFrom.toString(), windows.extraTo.toString()),
            ) { rows, synced, daily -> build(walletIds, windows, changes, rows, synced, daily) }
        }
    }

    /** One transaction state as reports count it. */
    private class State(
        val id: String,
        val kind: String,
        val date: LocalDate,
        val amountMinor: Long,
        val currency: String,
        val walletId: String,
        val counterWalletId: String?,
        val categoryId: String?,
    ) {
        fun touches(walletIds: Set<String>) = walletId in walletIds || (counterWalletId != null && counterWalletId in walletIds)

        fun effectOn(wallet: String) = BalanceEffect.of(kind, amountMinor, walletId, counterWalletId, deleted = false, forWallet = wallet)

        fun line(waiting: Boolean) = LedgerLine(id, kind, date, amountMinor, currency, walletId, counterWalletId, categoryId, waiting)
    }

    private fun build(
        walletIds: Set<String>,
        windows: Windows,
        changes: List<PendingChange>,
        rows: List<ReportRow>,
        syncedSums: Map<String, Long>,
        syncedDaily: List<DaySumRow>,
    ): LedgerSnapshot {
        val changeById = changes.associateBy { it.recordId }
        val rowIds = rows.mapTo(HashSet()) { it.id }
        val lines = mutableListOf<LedgerLine>()
        val synced = mutableListOf<LedgerLine>()
        val touches = mutableListOf<LedgerTouch>()
        val before = HashMap<String, Long>(syncedSums)
        // Per day: income, expenses.
        val daily = HashMap<LocalDate, LongArray>()
        for (sum in syncedDaily) {
            val slot = daily.getOrPut(parseDate(sum.date)) { LongArray(2) }
            if (sum.kind == BalanceEffect.INCOME) slot[0] += sum.total else if (sum.kind == BalanceEffect.EXPENSE) slot[1] += sum.total
        }

        fun touch(state: State) {
            if (state.walletId in walletIds) touches += LedgerTouch(state.walletId, state.date)
            if (state.counterWalletId != null && state.counterWalletId in walletIds) touches += LedgerTouch(state.counterWalletId, state.date)
        }

        fun shiftBefore(state: State, sign: Long) {
            if (state.date >= windows.from) return
            for (wallet in walletIds) {
                val effect = state.effectOn(wallet)
                if (effect != 0L) before[wallet] = (before[wallet] ?: 0L) + sign * effect
            }
        }

        fun shiftDaily(state: State, sign: Long) {
            if (!windows.inExtra(state.date) || state.walletId !in walletIds) return
            val index = when (state.kind) {
                BalanceEffect.INCOME -> 0
                BalanceEffect.EXPENSE -> 1
                else -> return
            }
            daily.getOrPut(state.date) { LongArray(2) }[index] += sign * state.amountMinor
        }

        fun stored(row: ReportRow) = State(
            row.id, row.kind, parseDate(row.date), row.amountMinor, row.currency, row.walletId, row.counterWalletId, row.categoryId,
        )

        for (row in rows) {
            val state = stored(row)
            if (row.pendingConfirmation && row.version == 0L) {
                if (!state.touches(walletIds)) continue
                touch(state)
                if (windows.inRange(state.date)) lines += state.line(waiting = true)
                shiftBefore(state, +1)
                shiftDaily(state, +1)
                continue
            }
            val change = changeById[row.id]
            if (change == null) {
                if (state.touches(walletIds) && windows.inRange(state.date)) {
                    lines += state.line(waiting = false)
                    if (row.version > 0) synced += state.line(waiting = false)
                }
                continue
            }
            // The stored side: the server holds it, so a sum before the range or in the extra days includes it and must give it back.
            if (row.version > 0 && state.touches(walletIds)) {
                touch(state)
                if (windows.inRange(state.date)) synced += state.line(waiting = true)
                shiftBefore(state, -1)
                shiftDaily(state, -1)
            }
            val payload = if (change.isDelete) null else decodePayload(change.payloadJson)
            if (payload != null) {
                val shown = State(
                    row.id, payload.kind, parseDate(payload.date), payload.amountMinor, payload.currency, row.walletId,
                    payload.counterWalletId, payload.categoryId,
                )
                if (shown.touches(walletIds)) {
                    touch(shown)
                    if (windows.inRange(shown.date)) lines += shown.line(waiting = true)
                    shiftBefore(shown, +1)
                    shiftDaily(shown, +1)
                }
            }
        }

        // A waiting create whose placeholder row is missing still adds what it says.
        for (change in changes) {
            if (change.recordId in rowIds || change.isDelete) continue
            val payload = decodePayload(change.payloadJson) ?: continue
            val shown = State(
                change.recordId, payload.kind, parseDate(payload.date), payload.amountMinor, payload.currency, change.scope.id,
                payload.counterWalletId, payload.categoryId,
            )
            if (!shown.touches(walletIds)) continue
            touch(shown)
            if (windows.inRange(shown.date)) lines += shown.line(waiting = true)
            shiftBefore(shown, +1)
            shiftDaily(shown, +1)
        }
        return LedgerSnapshot(
            lines = lines.filter { it.kind in KNOWN_KINDS },
            before = before,
            syncedLines = synced.filter { it.kind in KNOWN_KINDS },
            syncedBefore = syncedSums,
            waitingTouches = touches.distinct(),
            daily = daily.entries.sortedBy { it.key }.map { (date, sums) -> DaySum(date, sums[0], sums[1]) }.filter { it.incomeMinor != 0L || it.expensesMinor != 0L },
            syncedDaily = syncedDaily.groupBy { parseDate(it.date) }.entries.sortedBy { it.key }.map { (date, sums) ->
                DaySum(date, sums.filter { it.kind == BalanceEffect.INCOME }.sumOf { it.total }, sums.filter { it.kind == BalanceEffect.EXPENSE }.sumOf { it.total })
            },
        )
    }

    private fun parseDate(value: String): LocalDate = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate(1970, 1, 1))

    private companion object {
        val KNOWN_KINDS = setOf(BalanceEffect.INCOME, BalanceEffect.EXPENSE, BalanceEffect.TRANSFER)
    }
}
