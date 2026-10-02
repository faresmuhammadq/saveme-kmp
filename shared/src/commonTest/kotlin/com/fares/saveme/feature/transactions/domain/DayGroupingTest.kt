package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.domain.model.Direction
import com.fares.saveme.feature.transactions.viewOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DayGroupingTest {

    @Test
    fun newestDateFirstTiesByCreationThenIdAndEachDayCarriesItsNet() {
        val days = DayGrouping.group(
            listOf(
                viewOf("a", "2026-10-01", 1_000, "expense", createdAt = "2026-10-01T08:00:00Z"),
                viewOf("b", "2026-10-02", 5_000, "income", createdAt = "2026-10-02T08:00:00Z"),
                viewOf("c", "2026-10-02", 700, "expense", createdAt = "2026-10-02T09:00:00Z"),
                viewOf("d", "2026-10-02", 100, "expense", createdAt = "2026-10-02T09:00:00Z"),
            ),
            "w1",
        )
        assertEquals(listOf(LocalDate(2026, 10, 2), LocalDate(2026, 10, 1)), days.map { it.date })
        assertEquals(listOf("d", "c", "b"), days[0].items.map { it.id })
        assertEquals(5_000 - 700 - 100, days[0].netMinor)
        assertEquals(-1_000, days[1].netMinor)
    }

    @Test
    fun aTransferCountsByItsDirectionInTheViewedWallet() {
        val out = viewOf("o", kind = "transfer", walletId = "w1", counterWalletId = "w2", category = null, amount = 2_000)
        val inn = viewOf("i", kind = "transfer", walletId = "w2", counterWalletId = "w1", category = null, amount = 500, viewedFrom = "w1")
        val days = DayGrouping.group(listOf(out, inn), "w1")
        assertEquals(-1_500, days.single().netMinor)
        assertEquals(Direction.Out, out.direction)
        assertEquals(Direction.In, inn.direction)
    }

    @Test
    fun anUnknownKindContributesNothing() {
        val days = DayGrouping.group(listOf(viewOf("u", kind = "refund", amount = 9_999), viewOf("e", amount = 100)), "w1")
        assertEquals(-100, days.single().netMinor)
    }
}
