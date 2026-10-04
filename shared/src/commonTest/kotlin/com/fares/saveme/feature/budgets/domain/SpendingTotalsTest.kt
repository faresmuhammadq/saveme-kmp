package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod
import com.fares.saveme.feature.budgets.expense
import com.fares.saveme.feature.budgets.touch
import com.fares.saveme.feature.transactions.domain.SpendingSnapshot
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The phone sums its own expenses with the server's rules (FR-012, ADR-0019). */
class SpendingTotalsTest {

    private val period = BudgetPeriod(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), 31, 2)

    private fun snapshot(
        expenses: List<com.fares.saveme.feature.transactions.domain.OwnExpense>,
        synced: List<com.fares.saveme.feature.transactions.domain.OwnExpense> = expenses,
        touches: List<com.fares.saveme.feature.transactions.domain.SpendingKey> = emptyList(),
    ) = SpendingSnapshot(expenses, synced, touches)

    @Test
    fun aCategoryBudgetCountsItsCategoryAndCurrencyInThePeriodOnly() {
        val snap = snapshot(
            listOf(
                expense("a", "2026-10-01", 10_000),
                expense("b", "2026-10-31", 5_000),
                expense("c", "2026-10-15", 7_000, category = "c2"),
                expense("d", "2026-09-30", 9_000),
                expense("e", "2026-11-01", 9_000),
            ),
        )
        val sums = SpendingTotals.withWaiting(snap, "c1", "EGP", period)
        assertEquals(15_000, sums.spentMinor)
        assertEquals(emptyList(), sums.otherCurrencies)
        assertFalse(sums.includesWaiting)
    }

    @Test
    fun anAllExpensesBudgetCountsEveryCategory() {
        val snap = snapshot(listOf(expense("a", "2026-10-02", 1_000), expense("b", "2026-10-03", 2_000, category = "c2"), expense("c", "2026-10-04", 4_000, category = null)))
        assertEquals(7_000, SpendingTotals.withWaiting(snap, null, "EGP", period).spentMinor)
    }

    @Test
    fun otherCurrenciesAreNamedSortedAndNeverSummed() {
        val snap = snapshot(
            listOf(
                expense("a", "2026-10-02", 1_000),
                expense("b", "2026-10-03", 50_000, currency = "USD"),
                expense("c", "2026-10-04", 90_000, currency = "KWD"),
                expense("d", "2026-10-05", 10_000, currency = "USD"),
                expense("e", "2026-10-06", 10_000, currency = "GBP", category = "c2"),
            ),
        )
        val sums = SpendingTotals.withWaiting(snap, "c1", "EGP", period)
        assertEquals(1_000, sums.spentMinor)
        assertEquals(listOf("KWD", "USD"), sums.otherCurrencies, "only matching category, sorted, no duplicates")
        assertEquals(listOf("GBP", "KWD", "USD"), SpendingTotals.withWaiting(snap, null, "EGP", period).otherCurrencies)
    }

    @Test
    fun aWaitingChangeMatchesOnItsOldOrNewValuesInTheBudgetsPeriodCurrencyAndCategory() {
        val withTouch = { t: com.fares.saveme.feature.transactions.domain.SpendingKey -> snapshot(emptyList(), touches = listOf(t)) }
        assertTrue(SpendingTotals.withWaiting(withTouch(touch("2026-10-10")), "c1", "EGP", period).includesWaiting)
        assertTrue(SpendingTotals.withWaiting(withTouch(touch("2026-10-10", category = "c2")), null, "EGP", period).includesWaiting, "all expenses matches any category")
        assertFalse(SpendingTotals.withWaiting(withTouch(touch("2026-10-10", category = "c2")), "c1", "EGP", period).includesWaiting)
        assertFalse(SpendingTotals.withWaiting(withTouch(touch("2026-10-10", currency = "USD")), "c1", "EGP", period).includesWaiting)
        assertFalse(SpendingTotals.withWaiting(withTouch(touch("2026-09-30")), "c1", "EGP", period).includesWaiting)
        assertFalse(SpendingTotals.withWaiting(withTouch(touch("2026-11-01")), "c1", "EGP", period).includesWaiting)
        assertTrue(SpendingTotals.withWaiting(withTouch(touch("2026-10-01")), "c1", "EGP", period).includesWaiting)
        assertTrue(SpendingTotals.withWaiting(withTouch(touch("2026-10-31")), "c1", "EGP", period).includesWaiting)
    }

    @Test
    fun syncedOnlyTotalsComeFromTheStoredRowsAndNeverMarkWaiting() {
        val snap = snapshot(
            expenses = listOf(expense("a", "2026-10-02", 1_000), expense("n", "2026-10-03", 200, waiting = true)),
            synced = listOf(expense("a", "2026-10-02", 1_000)),
            touches = listOf(touch("2026-10-03")),
        )
        val synced = SpendingTotals.syncedOnly(snap, "c1", "EGP", period)
        assertEquals(1_000, synced.spentMinor)
        assertFalse(synced.includesWaiting)
        val withWaiting = SpendingTotals.withWaiting(snap, "c1", "EGP", period)
        assertEquals(1_200, withWaiting.spentMinor)
        assertTrue(withWaiting.includesWaiting)
    }
}
