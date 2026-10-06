package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.line
import com.fares.saveme.feature.reports.scheduledLine
import com.fares.saveme.feature.transactions.domain.LedgerLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** FR-006: grouping by system key or id, the server's order, zero items left out, totals equal to the item sums. */
class CategoryBreakdownTest {

    private val from = date("2026-10-01")
    private val to = date("2026-10-31")
    private val platform = mapOf("p-groceries" to "groceries", "p-transport" to "transport", "p-rent" to "rent", "p-salary" to "salary")
    private val keyOf: (String) -> String = { platform[it] ?: it }

    private fun breakdown(lines: List<LedgerLine>, scheduled: List<com.fares.saveme.feature.recurring.domain.ScheduledLine>? = null, covered: Set<String> = setOf("w1", "w2")) =
        ReportCalculator.breakdown("EGP", covered, from, to, lines, scheduled, keyOf)

    @Test
    fun aPlatformCategoryIsGroupedBySystemKeyAcrossWalletsAndOthersByTheirId() {
        val result = breakdown(
            listOf(
                line("a", "expense", "2026-10-02", 200_000, wallet = "w1", category = "p-groceries"),
                line("b", "expense", "2026-10-03", 150_000, wallet = "w2", category = "p-groceries"),
                line("c", "expense", "2026-10-04", 120_000, category = "p-transport"),
                line("d", "expense", "2026-10-05", 80_000, category = "own-1"),
                line("e", "expense", "2026-10-05", 5_000, category = "own-2"),
                line("s", "income", "2026-10-01", 1_000_000, category = "p-salary"),
            ),
        )
        assertEquals(listOf("groceries", "transport", "own-1", "own-2"), result.expenses.items.map { it.label })
        assertEquals(listOf(350_000L, 120_000L, 80_000L, 5_000L), result.expenses.items.map { it.valueMinor })
        assertEquals(listOf<String?>("groceries", "transport", null, null), result.expenses.items.map { it.systemKey })
        assertEquals(555_000, result.expenses.totalMinor)
        assertEquals(listOf("salary"), result.income.items.map { it.label })
        assertEquals(1_000_000, result.income.totalMinor)
        assertNull(result.expenses.plannedMinor)
    }

    @Test
    fun twoCategoriesWithTheSameNameAreNeverMergedAndTransfersAreInNeitherGroup() {
        val result = breakdown(
            listOf(
                line("a", "expense", "2026-10-02", 10, category = "own-a"),
                line("b", "expense", "2026-10-03", 10, wallet = "w2", category = "own-b"),
                line("t", "transfer", "2026-10-04", 999, wallet = "w1", counter = "w9", category = null),
            ),
        )
        assertEquals(2, result.expenses.items.size)
        assertEquals(0, result.income.items.size)
        assertEquals(20, result.expenses.totalMinor)
    }

    @Test
    fun sortsBySumDescendingThenKeyAscendingInByteOrder() {
        val result = breakdown(
            listOf(
                line("1", "expense", "2026-10-02", 100, category = "groceries-key"),
                line("2", "expense", "2026-10-02", 100, category = "0192f7c1-0000-7000-8000-0000000000a7"),
                line("3", "expense", "2026-10-02", 100, category = "Zeta"),
                line("4", "expense", "2026-10-02", 300, category = "zzz"),
            ),
        )
        // Digits sort before capitals, capitals before lowercase: the backend's strcmp.
        assertEquals(listOf("zzz", "0192f7c1-0000-7000-8000-0000000000a7", "Zeta", "groceries-key"), result.expenses.items.map { it.label })
    }

    @Test
    fun anItemWithNothingRecordedIsDroppedButKeptWhileScheduledMoneyIsOnAndNotZero() {
        val off = breakdown(listOf(line("z", "expense", "2026-10-02", 0, category = "c-zero")))
        assertEquals(emptyList(), off.expenses.items)
        val on = breakdown(
            emptyList(),
            scheduled = listOf(scheduledLine("rent", "expense", "2026-10-28", 500_000, category = "p-rent"), scheduledLine("none", "expense", "2026-10-28", 0, category = "c-zero")),
        )
        assertEquals(listOf("rent"), on.expenses.items.map { it.label })
        assertEquals(0, on.expenses.items.single().valueMinor)
        assertEquals(500_000, on.expenses.items.single().plannedMinor)
        assertEquals(500_000, on.expenses.plannedMinor)
        assertEquals(0, on.income.plannedMinor)
    }

    @Test
    fun scheduledSumsAreOtherCurrencyFreeAndNeverAddedToTheValue() {
        val result = breakdown(
            listOf(line("a", "expense", "2026-10-02", 100, category = "p-rent")),
            scheduled = listOf(
                scheduledLine("r", "expense", "2026-10-28", 40, category = "p-rent"),
                scheduledLine("usd", "expense", "2026-10-28", 999, category = "p-rent", currency = "USD"),
                scheduledLine("transfer", "transfer", "2026-10-28", 888, counter = "w2"),
            ),
        )
        val item = result.expenses.items.single()
        assertEquals(100, item.valueMinor)
        assertEquals(40, item.plannedMinor)
        assertEquals(100, result.expenses.totalMinor)
    }

    @Test
    fun aWalletOutsideTheCoveredSetIsNotCounted() {
        val result = breakdown(listOf(line("a", "expense", "2026-10-02", 100, wallet = "w9", category = "c")), covered = setOf("w1"))
        assertEquals(emptyList(), result.expenses.items)
    }
}
