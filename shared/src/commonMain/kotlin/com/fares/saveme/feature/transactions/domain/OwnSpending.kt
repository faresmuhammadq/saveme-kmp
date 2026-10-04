package com.fares.saveme.feature.transactions.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/**
 * One of the person's own expenses as the phone shows it (waiting changes applied). Amounts are never printed
 * (P-09.2).
 */
data class OwnExpense(
    val id: String,
    val date: LocalDate,
    val amountMinor: Long,
    val currency: String,
    val categoryId: String?,
    /** True when a change waiting to sync, or a confirmation waiting on this phone, shapes this expense. */
    val waiting: Boolean,
) {
    override fun toString() = "OwnExpense($id)"
}

/** Where a waiting change touches the person's own spending: the date, currency and category of one side of it. */
data class SpendingKey(val date: LocalDate, val currency: String, val categoryId: String?) {
    override fun toString() = "SpendingKey"
}

/**
 * [expenses] are the own expenses dated in the range with waiting changes applied; [syncedExpenses] the stored rows
 * only (no overlay, no phone-only confirmations); [waitingTouches] one key for the stored side and one for the
 * effective side of every waiting change that is, or was, an own expense.
 */
data class SpendingSnapshot(
    val expenses: List<OwnExpense>,
    val syncedExpenses: List<OwnExpense>,
    val waitingTouches: List<SpendingKey>,
) {
    override fun toString() = "SpendingSnapshot"
}

/**
 * What budgets need from transactions without reading its tables (spec 012 research R-05, P-10.4): the person's own
 * expenses with the server's attribution rules (ADR-0019) applied, the waiting changes overlaid. Transactions binds
 * [TransactionOwnSpending]; until then [NoOwnSpending] shows nothing.
 */
interface OwnSpending {
    fun observe(from: LocalDate, to: LocalDate): Flow<SpendingSnapshot>
}

object NoOwnSpending : OwnSpending {
    override fun observe(from: LocalDate, to: LocalDate): Flow<SpendingSnapshot> =
        flowOf(SpendingSnapshot(emptyList(), emptyList(), emptyList()))
}
