package com.fares.saveme.feature.reports.ui.reports

import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.categories.domain.model.ReportLabels
import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.domain.model.RangeChoice
import com.fares.saveme.feature.reports.domain.model.RangeProblem
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.ReportState
import com.fares.saveme.feature.reports.sampleReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportsViewModelTest {

    private val labels = ReportLabels({ it }, CategoryDisplays({ id -> CategoryDisplay(id, "Name $id", "cart", "#2E7D32", "expense", CategorySource.Own, false) }))

    private class Calls {
        val refreshes = mutableListOf<Boolean>()
        val ranges = mutableListOf<RangeChoice>()
        val trendLengths = mutableListOf<Int>()
        val currencies = mutableListOf<String>()
        val switches = mutableListOf<Boolean>()
        var customProblem: RangeProblem? = null
        var outcome = RefreshOutcome.Done
    }

    private fun vm(
        calls: Calls = Calls(),
        report: MutableStateFlow<ReportState> = MutableStateFlow(ReportState.Ready(sampleReport())),
        walletId: String? = null,
    ) = ReportsViewModel(
        walletId = walletId,
        report = report,
        labels = flowOf(labels),
        refresh = { force -> calls.refreshes += force; calls.outcome },
        setRange = { calls.ranges += it },
        setTrendLength = { calls.trendLengths += it },
        setCurrency = { calls.currencies += it },
        setIncludeScheduled = { calls.switches += it },
        validateCustom = { _, _ -> calls.customProblem },
        timeZoneId = flowOf("Africa/Cairo"),
    )

    @Test
    fun theScreenShowsLoadingUntilTheFirstReportThenTheSummary() = runMviTest {
        val report = MutableStateFlow<ReportState>(ReportState.Loading)
        val vm = vm(report = report)
        assertEquals(ReportsStatus.Loading, vm.state.value.status)
        report.value = ReportState.Ready(sampleReport())
        assertEquals(ReportsStatus.Ready, vm.state.value.status)
        assertEquals(550_000, vm.state.value.summary!!.figures.expenses)
        assertEquals("Africa/Cairo", vm.state.value.timeZoneId)
    }

    @Test
    fun openingReadsOnceWithinTheFloorAndAPullForces() = runMviTest {
        val calls = Calls()
        val vm = vm(calls)
        vm.onIntent(ReportsIntent.Opened)
        vm.onIntent(ReportsIntent.PullToRefresh)
        assertEquals(listOf(false, true), calls.refreshes)
        assertEquals(false, vm.state.value.refreshing)
    }

    @Test
    fun anOfflineOrFailedReadIsReportedAndDoneClearsIt() = runMviTest {
        val calls = Calls()
        val vm = vm(calls)
        calls.outcome = RefreshOutcome.Offline
        vm.onIntent(ReportsIntent.Opened)
        assertTrue(vm.state.value.offline)
        calls.outcome = RefreshOutcome.Failed
        vm.onIntent(ReportsIntent.PullToRefresh)
        assertTrue(vm.state.value.refreshFailed && !vm.state.value.offline)
        calls.outcome = RefreshOutcome.Done
        vm.onIntent(ReportsIntent.PullToRefresh)
        assertTrue(!vm.state.value.refreshFailed && !vm.state.value.offline)
    }

    @Test
    fun seeAllOpensTheCategoriesWithTheScopesArguments() = runMviTest {
        val vm = vm()
        vm.onIntent(ReportsIntent.SeeAllClicked)
        vm.onIntent(ReportsIntent.CategoryClicked(CategoryGroupKind.Income))
        assertEquals(
            ReportsEffect.OpenCategories(CategoriesArgs(null, date("2026-10-01"), date("2026-10-31"), "EGP", false, CategoryGroupKind.Expenses)),
            vm.effects.first(),
        )
        assertEquals(
            ReportsEffect.OpenCategories(CategoriesArgs(null, date("2026-10-01"), date("2026-10-31"), "EGP", false, CategoryGroupKind.Income)),
            vm.effects.first(),
        )
    }

    @Test
    fun aWalletReportsArgumentsCarryTheWalletIdAndTheSwitch() = runMviTest {
        val report = MutableStateFlow<ReportState>(ReportState.Ready(sampleReport(scope = ReportScope.Wallet("w7"), title = "Cash", includeScheduled = true)))
        val vm = vm(report = report, walletId = "w7")
        vm.onIntent(ReportsIntent.CategoryClicked(CategoryGroupKind.Income))
        assertEquals(
            ReportsEffect.OpenCategories(CategoriesArgs("w7", date("2026-10-01"), date("2026-10-31"), "EGP", true, CategoryGroupKind.Income)),
            vm.effects.first(),
        )
    }

    @Test
    fun noWalletsOffersTheWalletsScreenAndABackCloses() = runMviTest {
        val vm = vm(report = MutableStateFlow(ReportState.NoWallets))
        assertEquals(ReportsStatus.NoWallets, vm.state.value.status)
        vm.onIntent(ReportsIntent.AddWalletClicked)
        assertEquals(ReportsEffect.OpenWallets, vm.effects.first())
        vm.onIntent(ReportsIntent.BackClicked)
        assertEquals(ReportsEffect.Close, vm.effects.first())
    }

    @Test
    fun aPurgedWalletClosesTheScreen() = runMviTest {
        val report = MutableStateFlow<ReportState>(ReportState.Ready(sampleReport(scope = ReportScope.Wallet("w7"))))
        val vm = vm(report = report, walletId = "w7")
        report.value = ReportState.WalletGone
        assertEquals(ReportsEffect.Close, vm.effects.first())
    }

    @Test
    fun rangeIntentsGoToTheDomainAndNextOnlyWhenAllowed() = runMviTest {
        val calls = Calls()
        val report = MutableStateFlow<ReportState>(ReportState.Ready(sampleReport(canGoNext = false)))
        val vm = vm(calls, report)
        vm.onIntent(ReportsIntent.PreviousClicked)
        vm.onIntent(ReportsIntent.NextClicked)
        vm.onIntent(ReportsIntent.CurrentPeriodClicked)
        report.value = ReportState.Ready(sampleReport(canGoNext = true))
        vm.onIntent(ReportsIntent.NextClicked)
        assertEquals(listOf(RangeChoice.Previous, RangeChoice.CurrentPeriod, RangeChoice.Next), calls.ranges)
    }

    @Test
    fun theCustomPickerAcceptsAValidRangeAndKeepsAProblemOpen() = runMviTest {
        val calls = Calls()
        val vm = vm(calls)
        vm.onIntent(ReportsIntent.CustomRangeClicked)
        assertTrue(vm.state.value.customRangeOpen)
        calls.customProblem = RangeProblem.TooLong
        vm.onIntent(ReportsIntent.CustomRangePicked(date("2026-01-01"), date("2027-01-02")))
        assertTrue(vm.state.value.customRangeOpen && vm.state.value.customProblem)
        assertEquals(emptyList(), calls.ranges)
        calls.customProblem = null
        vm.onIntent(ReportsIntent.CustomRangePicked(date("2026-01-01"), date("2027-01-01")))
        assertEquals(false, vm.state.value.customRangeOpen)
        assertEquals(false, vm.state.value.customProblem)
        assertEquals(listOf<RangeChoice>(RangeChoice.Custom(date("2026-01-01"), date("2027-01-01"))), calls.ranges)
        vm.onIntent(ReportsIntent.CustomRangeClicked)
        vm.onIntent(ReportsIntent.CustomRangeDismissed)
        assertEquals(false, vm.state.value.customRangeOpen)
    }

    @Test
    fun theCurrencyChoiceExistsOnlyForTheCrossWalletReport() = runMviTest {
        val calls = Calls()
        val vm = vm(calls)
        vm.onIntent(ReportsIntent.CurrencyClicked)
        assertTrue(vm.state.value.currencyPickerOpen)
        vm.onIntent(ReportsIntent.CurrencyPicked("USD"))
        assertEquals(false, vm.state.value.currencyPickerOpen)
        vm.onIntent(ReportsIntent.SeeCurrencyClicked("KWD"))
        assertEquals(listOf("USD", "KWD"), calls.currencies)

        val walletCalls = Calls()
        val walletVm = vm(walletCalls, MutableStateFlow(ReportState.Ready(sampleReport(scope = ReportScope.Wallet("w7")))), walletId = "w7")
        walletVm.onIntent(ReportsIntent.CurrencyClicked)
        walletVm.onIntent(ReportsIntent.CurrencyPicked("USD"))
        walletVm.onIntent(ReportsIntent.SeeCurrencyClicked("KWD"))
        assertEquals(emptyList(), walletCalls.currencies)
        assertEquals(false, walletVm.state.value.currencyPickerOpen)
    }

    @Test
    fun theTrendLengthAndTheScheduledSwitchGoToTheDomain() = runMviTest {
        val calls = Calls()
        val vm = vm(calls)
        vm.onIntent(ReportsIntent.TrendLengthPicked(12))
        vm.onIntent(ReportsIntent.IncludeScheduledToggled(true))
        assertEquals(listOf(12), calls.trendLengths)
        assertEquals(listOf(true), calls.switches)
    }

    @Test
    fun anUnknownPeriodHasNothingToOpenCategoriesWith() = runMviTest {
        val report = MutableStateFlow<ReportState>(ReportState.Ready(sampleReport(range = null, summary = null)))
        val vm = vm(report = report)
        vm.onIntent(ReportsIntent.SeeAllClicked)
        vm.onIntent(ReportsIntent.BackClicked)
        assertEquals(ReportsEffect.Close, vm.effects.first(), "the See all intent opened nothing")
    }
}
