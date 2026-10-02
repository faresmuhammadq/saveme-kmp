package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.TransactionQuery
import com.fares.saveme.feature.transactions.viewOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Search and filters match the server's rules (FR-027, FR-028, transactions.yaml). */
class TransactionFilterTest {
    private val views = listOf(
        viewOf("plumber", date = "2026-10-05", category = "c1", description = "Plumber visit", memo = "bathroom", counterparty = "Ahmed"),
        viewOf("percent", date = "2026-09-20", category = "c2", description = "50% off_sale"),
        viewOf("salary", date = "2026-10-01", kind = "income", category = "ci", description = "Salary"),
        viewOf("transfer", date = "2026-10-10", kind = "transfer", category = null, counterWalletId = "w2"),
    )

    private fun ids(query: TransactionQuery) = TransactionFilter.apply(views, query).map { it.id }

    @Test
    fun aBlankSearchShowsEverythingAndTheSearchIsTrimmedAndCaseInsensitive() {
        assertEquals(4, ids(TransactionQuery()).size)
        assertEquals(4, ids(TransactionQuery(search = "   ")).size)
        assertEquals(listOf("plumber"), ids(TransactionQuery(search = "  PLUMB ")))
    }

    @Test
    fun itMatchesTheNoteTheDetailsAndThePersonOfTheShownValues() {
        assertEquals(listOf("plumber"), ids(TransactionQuery(search = "bathroom")))
        assertEquals(listOf("plumber"), ids(TransactionQuery(search = "ahmed")))
        assertEquals(listOf("salary"), ids(TransactionQuery(search = "salary")))
    }

    @Test
    fun percentAndUnderscoreMatchThemselves() {
        assertEquals(listOf("percent"), ids(TransactionQuery(search = "%")))
        assertEquals(listOf("percent"), ids(TransactionQuery(search = "off_s")))
        assertEquals(emptyList(), ids(TransactionQuery(search = "off.s")))
    }

    @Test
    fun theDateRangeIsInclusive() {
        assertEquals(setOf("plumber", "salary"), ids(TransactionQuery(from = LocalDate(2026, 10, 1), to = LocalDate(2026, 10, 5))).toSet())
        assertEquals(setOf("percent"), ids(TransactionQuery(to = LocalDate(2026, 9, 30))).toSet())
    }

    @Test
    fun valuesOfOneFilterAreOrAndFiltersAreAnd() {
        assertEquals(setOf("plumber", "percent"), ids(TransactionQuery(categoryIds = setOf("c1", "c2"))).toSet())
        assertEquals(setOf("salary", "transfer"), ids(TransactionQuery(kinds = setOf(TransactionKind.Income, TransactionKind.Transfer))).toSet())
        assertEquals(listOf("plumber"), ids(TransactionQuery(categoryIds = setOf("c1", "c2"), search = "plumb", from = LocalDate(2026, 10, 1))))
    }

    @Test
    fun theMonthPresetsFollowTheGivenToday() {
        assertEquals(LocalDate(2026, 10, 1) to LocalDate(2026, 10, 31), DatePresets.thisMonth(LocalDate(2026, 10, 17)))
        assertEquals(LocalDate(2026, 9, 1) to LocalDate(2026, 9, 30), DatePresets.lastMonth(LocalDate(2026, 10, 17)))
        assertEquals(LocalDate(2025, 12, 1) to LocalDate(2025, 12, 31), DatePresets.lastMonth(LocalDate(2026, 1, 3)))
        assertEquals(LocalDate(2028, 2, 1) to LocalDate(2028, 2, 29), DatePresets.thisMonth(LocalDate(2028, 2, 10)))
    }
}
