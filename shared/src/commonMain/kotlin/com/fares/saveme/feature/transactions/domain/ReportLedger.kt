package com.fares.saveme.feature.transactions.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/**
 * One income, expense or transfer as reports see it: the effective values (a waiting change applied). Amounts are never
 * printed (P-09.2).
 */
data class LedgerLine(
    val id: String,
    val kind: String,
    val date: LocalDate,
    val amountMinor: Long,
    val currency: String,
    val walletId: String,
    val counterWalletId: String?,
    val categoryId: String?,
    /** True when a change waiting to sync, or a confirmation waiting on this phone, shapes this line. */
    val waiting: Boolean,
) {
    override fun toString() = "LedgerLine($id)"
}

/** The wallet and date of one side of a waiting change: what its owner would want to know changed (FR-002). */
data class LedgerTouch(val walletId: String, val date: LocalDate) {
    override fun toString() = "LedgerTouch"
}

/** One day's effective income and expense sum over the asked wallets (the trend's older and later days, spec 014 R-03). */
data class DaySum(val date: LocalDate, val incomeMinor: Long, val expensesMinor: Long) {
    override fun toString() = "DaySum"
}

/**
 * [lines] are the effective lines dated in the asked range that touch the asked wallets (as source or destination);
 * [before] is, per wallet, the signed effect of every effective live line dated before the range's first day. [daily] are
 * the effective income and expense sums per day for the days of the extra window that lie outside the range (a trend over
 * earlier periods needs sums, not rows). [syncedLines], [syncedBefore] and [syncedDaily] are the server's copies only (no
 * overlay, no phone-only confirmations): the development build's parity probe compares those. [waitingTouches] lists both
 * sides of every waiting change.
 */
data class LedgerSnapshot(
    val lines: List<LedgerLine>,
    val before: Map<String, Long>,
    val syncedLines: List<LedgerLine>,
    val syncedBefore: Map<String, Long>,
    val waitingTouches: List<LedgerTouch>,
    val daily: List<DaySum> = emptyList(),
    val syncedDaily: List<DaySum> = emptyList(),
) {
    override fun toString() = "LedgerSnapshot"

    companion object {
        val Empty = LedgerSnapshot(emptyList(), emptyMap(), emptyList(), emptyMap(), emptyList())
    }
}

/**
 * What reports need from transactions without reading its tables (spec 014 research R-03, P-10.4): the effective lines of
 * some wallets in a range plus the balance effect before it, waiting changes overlaid. Transactions binds
 * [TransactionReportLedger]; until then [NoReportLedger] shows nothing.
 */
interface ReportLedger {
    /**
     * @param from the range's first day, [to] its last; lines and [LedgerSnapshot.before] are for it
     * @param extraFrom the first day [LedgerSnapshot.daily] covers, and [extraTo] the last (the days inside [from]..[to] are lines)
     */
    fun observe(walletIds: Set<String>, from: LocalDate, to: LocalDate, extraFrom: LocalDate = from, extraTo: LocalDate = to): Flow<LedgerSnapshot>
}

object NoReportLedger : ReportLedger {
    override fun observe(walletIds: Set<String>, from: LocalDate, to: LocalDate, extraFrom: LocalDate, extraTo: LocalDate): Flow<LedgerSnapshot> =
        flowOf(LedgerSnapshot.Empty)
}
