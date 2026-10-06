package com.fares.saveme.feature.reports.ui.reports

import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.categories.domain.model.ReportLabels
import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.domain.model.CategoryItem
import com.fares.saveme.feature.reports.domain.model.ExcludedWallet
import com.fares.saveme.feature.reports.domain.model.PlannedSummary
import com.fares.saveme.feature.reports.domain.model.RangeKind
import com.fares.saveme.feature.reports.domain.model.ReportRange
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.ReportState
import com.fares.saveme.feature.reports.domain.model.Share
import com.fares.saveme.feature.reports.domain.model.TrendSlot
import com.fares.saveme.feature.reports.sampleReport
import com.fares.saveme.feature.reports.trendPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pure reducer of the Reports screen (P-16): domain state in, screen state out. */
class ReportsReducerTest {

    private val labels = ReportLabels(
        key = { it },
        displays = CategoryDisplays({ id ->
            when (id) {
                "p-g" -> CategoryDisplay(id, "Groceries", "cart", "#2E7D32", "expense", CategorySource.Default, false)
                "p-t" -> CategoryDisplay(id, "Transport", "bus", "#1F7F8C", "expense", CategorySource.Default, false)
                else -> CategoryDisplay(id, "", null, null, null, CategorySource.Unavailable, false)
            }
        }),
    )

    private fun stored(state: ReportState) = reduce(ReportsState(), ReportsChange.Stored(state, labels))

    @Test
    fun theScreenStartsLoadingAndNeverEmptyFlashes() {
        assertEquals(ReportsStatus.Loading, ReportsState().status)
        assertEquals(ReportsStatus.Loading, stored(ReportState.Loading).status)
        assertEquals(ReportsStatus.NoWallets, stored(ReportState.NoWallets).status)
        assertEquals(ReportsStatus.WalletGone, stored(ReportState.WalletGone).status)
    }

    @Test
    fun aReadyReportFillsTheSummaryRangeAndCategoryRowsWithSharesFromTheTotal() {
        val state = stored(ReportState.Ready(sampleReport()))
        assertEquals(ReportsStatus.Ready, state.status)
        assertEquals(1_000_000, state.summary!!.figures.income)
        assertEquals(450_000, state.summary!!.figures.net)
        assertEquals(RangeUi(date("2026-10-01"), date("2026-10-31"), custom = false), state.range)
        assertEquals(2026, state.thisYear)
        assertEquals("EGP", state.currency)
        assertEquals(listOf("Groceries", "Transport", ""), state.topExpenses.map { it.name })
        assertEquals(listOf(Share.Percent(64), Share.Percent(22), Share.Percent(15)), state.topExpenses.map { it.share })
        assertTrue(state.topExpenses.first().fraction > state.topExpenses.last().fraction)
        assertEquals(false, state.moreCategories)
    }

    @Test
    fun onlyTheTopFiveShowAndMoreCategoriesSaysSo() {
        val items = (1..7).map { CategoryItem("k$it", "c$it", null, (100 - it).toLong(), null) }
        val state = stored(ReportState.Ready(sampleReport(expenses = items)))
        assertEquals(5, state.topExpenses.size)
        assertEquals(listOf("c1", "c2", "c3", "c4", "c5"), state.topExpenses.map { it.categoryId })
        assertTrue(state.moreCategories)
    }

    @Test
    fun aReportWithNoSpendingHasNoRows() {
        val state = stored(ReportState.Ready(sampleReport(expenses = emptyList())))
        assertEquals(emptyList(), state.topExpenses)
        assertEquals(false, state.moreCategories)
    }

    @Test
    fun anUnnameableCategoryHasAnEmptyNameForTheScreenToSayUnavailable() {
        val state = stored(ReportState.Ready(sampleReport(expenses = listOf(CategoryItem("zzz", "zzz", null, 10, null)))))
        assertEquals("", state.topExpenses.single().name)
    }

    @Test
    fun aWalletReportCarriesTheWalletIdAndTitleAndNoOtherCurrencies() {
        val state = stored(ReportState.Ready(sampleReport(scope = ReportScope.Wallet("w7"), title = "Cash", liability = true)))
        assertEquals("w7", state.walletId)
        assertEquals("Cash", state.title)
        assertEquals(true, state.summary!!.liabilityWallet)
        assertEquals(false, state.isCrossWallet)
    }

    @Test
    fun excludedWalletsAndTheirCurrenciesAreListedSortedAndOnceEach() {
        val excluded = listOf(ExcludedWallet("a", "Trip", "KWD"), ExcludedWallet("b", "Abroad", "USD"), ExcludedWallet("c", "Savings", "USD"))
        val state = stored(ReportState.Ready(sampleReport(excluded = excluded)))
        assertEquals(listOf("Trip" to "KWD", "Abroad" to "USD", "Savings" to "USD"), state.excluded.map { it.name to it.currency })
        assertEquals(listOf("KWD", "USD"), state.otherCurrencies)
    }

    @Test
    fun anUnknownPeriodHasNoRangeNoSummaryAndAnUnknownTrend() {
        val state = stored(ReportState.Ready(sampleReport(range = null, summary = null, expenses = emptyList(), trend = List(6) { TrendSlot.Unknown })))
        assertNull(state.range)
        assertNull(state.summary)
        assertEquals(TrendUi.Unknown, state.trend)
    }

    @Test
    fun aTrendWithSomeUnknownSlotsKeepsTheKnownOnesAndNotesTheRest() {
        val state = stored(
            ReportState.Ready(sampleReport(trend = listOf(TrendSlot.Unknown, trendPoint("2026-09-01", "2026-09-30", 10, 4), trendPoint("2026-10-01", "2026-10-31", 20, 5)))),
        )
        val points = state.trend as TrendUi.Points
        assertEquals(2, points.items.size)
        assertTrue(points.hasUnknown)
        assertEquals(listOf(6L, 15L), points.items.map { it.netMinor })
    }

    @Test
    fun theTrendLengthAndTheSwitchComeFromTheChoices() {
        val choices = com.fares.saveme.feature.reports.domain.model.ReportChoices(trendLength = 12)
        val state = stored(
            ReportState.Ready(sampleReport(choices = choices, includeScheduled = true, planned = PlannedSummary(0, 500_000, 0, 0), knownUntil = date("2026-11-06"))),
        )
        assertEquals(12, state.trendLength)
        assertTrue(state.includeScheduled)
        assertEquals(500_000, state.summary!!.planned!!.expenses)
        assertEquals(date("2026-11-06"), state.knownUntil)
    }

    @Test
    fun aCustomRangeIsMarkedAsOne() {
        val range = ReportRange(date("2026-09-10"), date("2026-10-12"), RangeKind.Custom)
        assertTrue(stored(ReportState.Ready(sampleReport(range = range))).isCustomRange)
    }

    @Test
    fun waitingTooLargeAndFreshnessReachTheState() {
        val state = stored(ReportState.Ready(sampleReport(includesWaiting = true, tooLarge = true, updatedAt = kotlin.time.Instant.parse("2026-10-02T10:00:00Z"))))
        assertTrue(state.includesWaiting)
        assertTrue(state.tooLarge)
        assertEquals(kotlin.time.Instant.parse("2026-10-02T10:00:00Z"), state.updatedAt)
        val refreshed = reduce(state, ReportsChange.RefreshDone(offline = true, failed = false))
        assertTrue(refreshed.offline)
        assertEquals(false, refreshed.refreshing)
        assertTrue(reduce(refreshed, ReportsChange.RefreshDone(offline = false, failed = true)).refreshFailed)
        assertTrue(reduce(state, ReportsChange.Refreshing(true)).refreshing)
    }

    @Test
    fun theCustomPickerAndTheCurrencySheetOpenAndCloseAndAProblemClearsWithTheDismissal() {
        val open = reduce(ReportsState(), ReportsChange.CustomOpen(true))
        assertTrue(open.customRangeOpen)
        val problem = reduce(open, ReportsChange.CustomProblem(true))
        assertTrue(problem.customProblem)
        val closed = reduce(problem, ReportsChange.CustomOpen(false))
        assertEquals(false, closed.customRangeOpen)
        assertEquals(false, closed.customProblem)
        assertTrue(reduce(ReportsState(), ReportsChange.CurrencyPicker(true)).currencyPickerOpen)
    }
}
