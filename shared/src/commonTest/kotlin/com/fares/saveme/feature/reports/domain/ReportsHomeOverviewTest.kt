package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.wallets.domain.model.RecentAuthor
import com.fares.saveme.feature.wallets.domain.model.RecentDirection
import com.fares.saveme.feature.wallets.domain.model.RecentKind
import com.fares.saveme.feature.wallets.domain.model.RecentTransaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportsHomeOverviewTest {
    private fun tx(id: String, date: String, wallet: String) = RecentTransaction(
        id, RecentKind.Expense, RecentDirection.Out, "Groceries", null, null, RecentAuthor.You, LocalDate.parse(date), 100, "EGP", walletId = wallet,
    )

    @Test
    fun theNewestThreeAcrossWalletsAndATransferIsShownOnce() {
        val merged = ReportsHomeOverview.mergeRecent(
            listOf(
                listOf(tx("t1", "2026-10-01", "w1"), tx("shared", "2026-10-05", "w1"), tx("t3", "2026-09-20", "w1")),
                listOf(tx("shared", "2026-10-05", "w2"), tx("t2", "2026-10-03", "w2"), tx("t4", "2026-08-01", "w2")),
            ),
        )
        assertEquals(listOf("shared", "t2", "t1"), merged.map { it.id })
    }
}
