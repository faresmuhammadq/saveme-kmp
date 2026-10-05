package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.domain.model.BudgetPeriodSpan
import com.fares.saveme.feature.reports.domain.model.RangeSelection
import com.fares.saveme.feature.reports.domain.model.ReportChoices
import com.fares.saveme.feature.reports.domain.model.ReportScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** FR-012, FR-029, data-model §4: the session's choices and learned periods live in memory, per account. */
class ReportHoldersTest {

    @Test
    fun choicesDefaultToTheCurrentPeriodSixPeriodsTheProfileCurrencyAndTheSwitchOff() = runTest {
        val store = ReportChoicesStore()
        val choices = store.observe("u1", ReportScope.AllWallets).first()
        assertEquals(RangeSelection.CurrentPeriod, choices.range)
        assertEquals(6, choices.trendLength)
        assertEquals(null, choices.currency)
        assertEquals(false, choices.includeScheduled)
    }

    @Test
    fun choicesAreKeptPerAccountAndScope() = runTest {
        val store = ReportChoicesStore()
        store.update("u1", ReportScope.AllWallets) { it.copy(trendLength = 12, currency = "USD") }
        store.update("u1", ReportScope.Wallet("w1")) { it.copy(includeScheduled = true) }
        assertEquals(12, store.get("u1", ReportScope.AllWallets).trendLength)
        assertEquals(6, store.get("u1", ReportScope.Wallet("w1")).trendLength)
        assertEquals(true, store.get("u1", ReportScope.Wallet("w1")).includeScheduled)
        assertEquals(ReportChoices(), store.get("u2", ReportScope.AllWallets))
    }

    @Test
    fun anotherAccountSigningInForgetsTheLastOnesChoices() {
        val store = ReportChoicesStore()
        store.update("u1", ReportScope.AllWallets) { it.copy(trendLength = 3) }
        store.dropOtherAccounts("u2")
        store.update("u2", ReportScope.AllWallets) { it.copy(trendLength = 12) }
        assertEquals(ReportChoices(), store.get("u1", ReportScope.AllWallets))
        assertEquals(12, store.get("u2", ReportScope.AllWallets).trendLength)
    }

    @Test
    fun theCleanerEmptiesChoicesAndLearnedPeriods() = runTest {
        val choices = ReportChoicesStore()
        val learned = LearnedPeriods()
        val profile = PeriodProfile(25, null)
        choices.update("u1", ReportScope.AllWallets) { it.copy(trendLength = 3) }
        learned.store("u1", profile, listOf(BudgetPeriodSpan(date("2026-08-25"), date("2026-09-24"))))
        ReportsCleaner(choices, learned).clear()
        assertEquals(ReportChoices(), choices.get("u1", ReportScope.AllWallets))
        assertEquals(emptyList(), learned.get("u1", profile))
    }

    @Test
    fun learnedPeriodsBelongToAnAccountAndAProfile() {
        val learned = LearnedPeriods()
        val profile = PeriodProfile(25, date("2026-09-25"))
        val span = BudgetPeriodSpan(date("2026-08-10"), date("2026-09-24"))
        learned.store("u1", profile, listOf(span))
        assertEquals(listOf(span), learned.get("u1", profile))
        assertEquals(emptyList(), learned.get("u2", profile))
        assertEquals(emptyList(), learned.get("u1", PeriodProfile(26, date("2026-09-25"))), "a changed start day drops what was learned")
        assertEquals(emptyList(), learned.get("u1", PeriodProfile(25, date("2026-10-25"))), "a changed 'applies from' date drops it too")
    }

    @Test
    fun aNewAnswerReplacesOverlappingPeriodsAndKeepsTheRest() {
        val learned = LearnedPeriods()
        val profile = PeriodProfile(1, null)
        learned.store("u1", profile, listOf(BudgetPeriodSpan(date("2026-06-01"), date("2026-06-30")), BudgetPeriodSpan(date("2026-07-01"), date("2026-07-31"))))
        learned.store("u1", profile, listOf(BudgetPeriodSpan(date("2026-07-01"), date("2026-07-31")), BudgetPeriodSpan(date("2026-08-01"), date("2026-08-31"))))
        assertEquals(
            listOf("2026-06-01", "2026-07-01", "2026-08-01"),
            learned.get("u1", profile).map { it.start.toString() },
        )
    }
}
