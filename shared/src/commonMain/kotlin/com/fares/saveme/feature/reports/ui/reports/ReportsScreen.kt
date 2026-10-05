package com.fares.saveme.feature.reports.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppDatePickerDialog
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppSelectField
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.DateField
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.ExcludedWalletsNotice
import com.fares.saveme.designsystem.component.FigureTone
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.NoticeAction
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.ReportFigureRow
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SegmentedControl
import com.fares.saveme.designsystem.component.SelectionSheet
import com.fares.saveme.designsystem.component.SettingsSwitchRow
import com.fares.saveme.designsystem.component.ShareBarRow
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.SummaryCard
import com.fares.saveme.designsystem.component.SummaryDivider
import com.fares.saveme.designsystem.component.TrendBarChart
import com.fares.saveme.designsystem.component.TrendChartLabels
import com.fares.saveme.designsystem.component.TrendPointUi
import com.fares.saveme.designsystem.component.TrendRowUi
import com.fares.saveme.designsystem.component.TrendTable
import com.fares.saveme.designsystem.component.TrendTableLabels
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.reports.domain.model.ReportChoices
import com.fares.saveme.feature.reports.ui.CategoryRowUi
import com.fares.saveme.feature.reports.ui.ReportsBackBar
import com.fares.saveme.feature.reports.ui.rangeText
import com.fares.saveme.feature.reports.ui.shareText
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_done
import saveme.shared.generated.resources.category_unavailable
import saveme.shared.generated.resources.reports_add_wallet
import saveme.shared.generated.resources.reports_balance_end
import saveme.shared.generated.resources.reports_balance_start
import saveme.shared.generated.resources.reports_by_category
import saveme.shared.generated.resources.reports_current
import saveme.shared.generated.resources.reports_currency
import saveme.shared.generated.resources.reports_custom
import saveme.shared.generated.resources.reports_custom_from
import saveme.shared.generated.resources.reports_custom_problem
import saveme.shared.generated.resources.reports_custom_to
import saveme.shared.generated.resources.reports_expenses
import saveme.shared.generated.resources.reports_failed_updated
import saveme.shared.generated.resources.reports_include_scheduled
import saveme.shared.generated.resources.reports_income
import saveme.shared.generated.resources.reports_net
import saveme.shared.generated.resources.reports_next
import saveme.shared.generated.resources.reports_no_spending
import saveme.shared.generated.resources.reports_no_wallets
import saveme.shared.generated.resources.reports_not_included
import saveme.shared.generated.resources.reports_offline_updated
import saveme.shared.generated.resources.reports_online_only
import saveme.shared.generated.resources.reports_owed
import saveme.shared.generated.resources.reports_previous
import saveme.shared.generated.resources.reports_scheduled_amount
import saveme.shared.generated.resources.reports_scheduled_known_until
import saveme.shared.generated.resources.reports_scheduled_legend
import saveme.shared.generated.resources.reports_see_all
import saveme.shared.generated.resources.reports_see_currency
import saveme.shared.generated.resources.reports_summary
import saveme.shared.generated.resources.reports_title
import saveme.shared.generated.resources.reports_too_large
import saveme.shared.generated.resources.reports_transfers_in
import saveme.shared.generated.resources.reports_transfers_out
import saveme.shared.generated.resources.reports_trend
import saveme.shared.generated.resources.reports_trend_periods
import saveme.shared.generated.resources.reports_trend_summary
import saveme.shared.generated.resources.reports_waiting
import saveme.shared.generated.resources.reports_all_wallets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    AppScreen(
        scrollable = false,
        topBar = {
            ReportsBackBar(
                title = state.title ?: stringResource(Res.string.reports_title),
                onBack = { onIntent(ReportsIntent.BackClicked) },
            )
        },
    ) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { onIntent(ReportsIntent.PullToRefresh) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when (state.status) {
                ReportsStatus.Loading, ReportsStatus.WalletGone -> LoadingBody()
                ReportsStatus.NoWallets -> EmptyState(
                    title = stringResource(Res.string.reports_no_wallets),
                    body = "",
                    actionLabel = stringResource(Res.string.reports_add_wallet),
                    onAction = { onIntent(ReportsIntent.AddWalletClicked) },
                    actionModifier = Modifier.testTag("reports.empty.add_wallet"),
                    modifier = Modifier.testTag("reports.screen"),
                )
                ReportsStatus.Ready -> ReportBody(state, onIntent)
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), modifier = Modifier.fillMaxSize().testTag("reports.screen")) {
        Skeleton(Modifier.fillMaxWidth().heightIn(min = androidx.compose.ui.unit.Dp(48f)), AppTheme.shapes.control)
        Skeleton(Modifier.fillMaxWidth().heightIn(min = androidx.compose.ui.unit.Dp(200f)), AppTheme.shapes.card)
        Skeleton(Modifier.fillMaxWidth().heightIn(min = androidx.compose.ui.unit.Dp(160f)), AppTheme.shapes.card)
    }
}


@Composable
private fun ReportBody(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.m),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = AppTheme.spacing.l).testTag("reports.screen"),
    ) {
        ScopeHeader(state, onIntent)
        RangeBar(state, onIntent)
        if (state.customRangeOpen) CustomRangeCard(state, onIntent)
        if (state.isCrossWallet && state.excluded.isNotEmpty()) Excluded(state, onIntent)
        Notes(state)
        SummarySection(state)
        CategoriesSection(state, onIntent)
        TrendSection(state, onIntent)
        SettingsSwitchRow(
            title = stringResource(Res.string.reports_include_scheduled),
            checked = state.includeScheduled,
            onCheckedChange = { onIntent(ReportsIntent.IncludeScheduledToggled(it)) },
            modifier = Modifier.testTag("reports.scheduled.switch"),
        )
        state.knownUntil?.takeIf { state.includeScheduled }?.let { until ->
            Text(
                stringResource(Res.string.reports_scheduled_known_until, formatDate(until)),
                style = AppTheme.type.caption,
                color = AppTheme.colors.caption,
                modifier = Modifier.testTag("reports.scheduled.known_until"),
            )
        }
    }
}

@Composable
private fun ScopeHeader(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        Text(
            state.title ?: stringResource(Res.string.reports_all_wallets),
            style = AppTheme.type.headline,
            color = AppTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (state.isCrossWallet) {
            AppSelectField(
                label = stringResource(Res.string.reports_currency),
                value = state.currency,
                onClick = { onIntent(ReportsIntent.CurrencyClicked) },
                modifier = Modifier.testTag("reports.currency"),
            )
        } else {
            Text(state.currency, style = AppTheme.type.label, color = AppTheme.colors.ink2)
        }
    }
    if (state.currencyPickerOpen) {
        SelectionSheet(
            title = stringResource(Res.string.reports_currency),
            options = state.currencies,
            label = { it },
            selected = state.currency,
            onSelect = { onIntent(ReportsIntent.CurrencyPicked(it)) },
            onDismiss = { onIntent(ReportsIntent.CurrencyPickerDismissed) },
            searchable = false,
        )
    }
}

@Composable
private fun RangeBar(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
            AppIconButton(
                AppIcons.ArrowBack, stringResource(Res.string.reports_previous), { onIntent(ReportsIntent.PreviousClicked) },
                modifier = Modifier.testTag("reports.range.previous"),
                enabled = state.range != null,
            )
            Text(
                text = state.range?.let { rangeText(it.from, it.to, state.thisYear) } ?: "—",
                style = AppTheme.type.itemTitle,
                color = AppTheme.colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).testTag("reports.range.label"),
            )
            AppIconButton(
                AppIcons.ChevronForward, stringResource(Res.string.reports_next), { onIntent(ReportsIntent.NextClicked) },
                modifier = Modifier.testTag("reports.range.next"),
                enabled = state.canGoNext,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs), modifier = Modifier.fillMaxWidth()) {
            AppTextButton(
                stringResource(Res.string.reports_custom), { onIntent(ReportsIntent.CustomRangeClicked) },
                modifier = Modifier.testTag("reports.range.custom"),
            )
            AppTextButton(
                stringResource(Res.string.reports_current), { onIntent(ReportsIntent.CurrentPeriodClicked) },
                modifier = Modifier.testTag("reports.range.current"),
            )
        }
    }
}

@Composable
private fun CustomRangeCard(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    val fallback = LocalDate(state.thisYear, 1, 1)
    var from by remember { mutableStateOf(state.range?.from ?: fallback) }
    var to by remember { mutableStateOf(state.range?.to ?: fallback) }
    var picking by remember { mutableStateOf<Int?>(null) }
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.spacing.xxs)
            .testTag("reports.custom"),
    ) {
        DateField(stringResource(Res.string.reports_custom_from), formatDate(from), onClick = { picking = 0 }, modifier = Modifier.testTag("reports.custom.from"))
        DateField(stringResource(Res.string.reports_custom_to), formatDate(to), onClick = { picking = 1 }, modifier = Modifier.testTag("reports.custom.to"))
        if (state.customProblem) {
            Text(
                stringResource(Res.string.reports_custom_problem),
                style = AppTheme.type.caption,
                color = AppTheme.colors.error,
                modifier = Modifier.testTag("reports.custom.problem"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
            SecondaryButton(
                stringResource(Res.string.action_cancel), { onIntent(ReportsIntent.CustomRangeDismissed) },
                modifier = Modifier.weight(1f).testTag("reports.custom.cancel"),
            )
            PrimaryButton(
                stringResource(Res.string.action_done), { onIntent(ReportsIntent.CustomRangePicked(from, to)) },
                modifier = Modifier.weight(1f).testTag("reports.custom.apply"),
            )
        }
    }
    when (picking) {
        0 -> AppDatePickerDialog(from, onPick = { from = it; picking = null }, onDismiss = { picking = null })
        1 -> AppDatePickerDialog(to, onPick = { to = it; picking = null }, onDismiss = { picking = null })
    }
}

@Composable
private fun Excluded(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    val names = state.excluded.joinToString(", ") { "${it.name} (${it.currency})" }
    ExcludedWalletsNotice(
        text = stringResource(Res.string.reports_not_included, names),
        actions = state.otherCurrencies.map { NoticeAction(stringResource(Res.string.reports_see_currency, it), "reports.excluded.see.$it") },
        onAction = { index -> onIntent(ReportsIntent.SeeCurrencyClicked(state.otherCurrencies[index])) },
        modifier = Modifier.testTag("reports.excluded"),
    )
}

@Composable
private fun Notes(state: ReportsState) {
    val updated = state.updatedAt?.let { formatDateTime(it, state.timeZoneId) }
    if (updated != null && (state.offline || state.refreshFailed)) {
        InlineAlert(
            tone = if (state.refreshFailed && !state.offline) AlertTone.Warning else AlertTone.Info,
            text = if (state.offline) stringResource(Res.string.reports_offline_updated, updated) else stringResource(Res.string.reports_failed_updated, updated),
            modifier = Modifier.testTag("reports.freshness"),
        )
    }
    if (state.includesWaiting) {
        InlineAlert(AlertTone.Info, stringResource(Res.string.reports_waiting), Modifier.testTag("reports.waiting"))
    }
}

@Composable
private fun SummarySection(state: ReportsState) {
    SummaryCard(stringResource(Res.string.reports_summary)) {
        val summary = state.summary
        when {
            state.tooLarge -> Text(stringResource(Res.string.reports_too_large), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("reports.too_large"))
            summary == null -> Text(stringResource(Res.string.reports_online_only), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("reports.summary.unknown"))
            else -> {
                val figures = summary.figures
                val currency = figures.currency
                val planned = summary.planned
                ReportFigureRow(
                    stringResource(Res.string.reports_income), formatMoney(figures.income, currency, showPlus = true), FigureTone.Positive,
                    Modifier.testTag("reports.summary.income"),
                )
                if (planned != null) ScheduledLine(planned.income, currency, "reports.planned.income")
                ReportFigureRow(
                    stringResource(Res.string.reports_expenses), formatMoney(-figures.expenses, currency), FigureTone.Neutral,
                    Modifier.testTag("reports.summary.expenses"),
                )
                if (planned != null) ScheduledLine(planned.expenses, currency, "reports.planned")
                ReportFigureRow(
                    stringResource(Res.string.reports_net), formatMoney(figures.net, currency), FigureTone.Neutral,
                    Modifier.testTag("reports.summary.net"), emphasis = true,
                )
                SummaryDivider(Modifier.padding(vertical = AppTheme.spacing.xxs))
                ReportFigureRow(
                    stringResource(Res.string.reports_transfers_in), formatMoney(figures.transfersIn, currency, showPlus = true), FigureTone.Neutral,
                    Modifier.testTag("reports.summary.transfers_in"),
                )
                if (planned != null && planned.transfersIn != 0L) ScheduledLine(planned.transfersIn, currency, "reports.planned.transfers_in")
                ReportFigureRow(
                    stringResource(Res.string.reports_transfers_out), formatMoney(-figures.transfersOut, currency), FigureTone.Neutral,
                    Modifier.testTag("reports.summary.transfers_out"),
                )
                if (planned != null && planned.transfersOut != 0L) ScheduledLine(planned.transfersOut, currency, "reports.planned.transfers_out")
                SummaryDivider(Modifier.padding(vertical = AppTheme.spacing.xxs))
                ReportFigureRow(
                    stringResource(Res.string.reports_balance_start), balanceText(figures.balanceStart, currency, summary.liabilityWallet), FigureTone.Neutral,
                    Modifier.testTag("reports.summary.balance_start"),
                )
                ReportFigureRow(
                    stringResource(Res.string.reports_balance_end), balanceText(figures.balanceEnd, currency, summary.liabilityWallet), FigureTone.Neutral,
                    Modifier.testTag("reports.summary.balance_end"),
                )
            }
        }
    }
}

/** A liability wallet's negative balance is an amount owed (FR-017); every other balance keeps its sign (U+2212 when negative). */
@Composable
private fun balanceText(amountMinor: Long, currency: String, liabilityWallet: Boolean): String =
    if (liabilityWallet && amountMinor < 0) stringResource(Res.string.reports_owed, formatMoney(-amountMinor, currency)) else formatMoney(amountMinor, currency)

@Composable
private fun ScheduledLine(plannedMinor: Long, currency: String, tag: String) {
    Text(
        stringResource(Res.string.reports_scheduled_amount, formatMoney(plannedMinor, currency)),
        style = AppTheme.type.caption,
        color = AppTheme.colors.caption,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun CategoriesSection(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
        modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(AppTheme.spacing.m),
    ) {
        Text(stringResource(Res.string.reports_by_category), style = AppTheme.type.title, color = AppTheme.colors.ink, modifier = Modifier.semantics { heading() })
        when {
            state.tooLarge -> Text(stringResource(Res.string.reports_too_large), style = AppTheme.type.body, color = AppTheme.colors.ink2)
            state.range == null -> Text(stringResource(Res.string.reports_online_only), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("reports.categories.unknown"))
            state.topExpenses.isEmpty() -> Text(stringResource(Res.string.reports_no_spending), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("reports.categories.empty"))
            else -> state.topExpenses.forEachIndexed { index, row -> CategoryRow(row, Modifier.testTag("reports.category.$index")) }
        }
        if (state.range != null && !state.tooLarge) {
            AppTextButton(stringResource(Res.string.reports_see_all), { onIntent(ReportsIntent.SeeAllClicked) }, modifier = Modifier.testTag("reports.categories.see_all"))
        }
    }
}

@Composable
internal fun CategoryRow(row: CategoryRowUi, modifier: Modifier = Modifier) {
    ShareBarRow(
        label = row.name.ifEmpty { stringResource(Res.string.category_unavailable) },
        iconKey = row.iconKey,
        colorHex = row.colorHex,
        amountText = formatMoney(row.valueMinor, row.currency),
        shareText = shareText(row.share),
        fraction = row.fraction,
        modifier = modifier,
        scheduledText = row.plannedMinor?.takeIf { it != 0L }?.let { stringResource(Res.string.reports_scheduled_amount, formatMoney(it, row.currency)) },
    )
}

@Composable
private fun TrendSection(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(AppTheme.spacing.m),
    ) {
        Text(stringResource(Res.string.reports_trend), style = AppTheme.type.title, color = AppTheme.colors.ink, modifier = Modifier.semantics { heading() })
        SegmentedControl(
            options = ReportChoices.TREND_LENGTHS,
            label = { stringResource(Res.string.reports_trend_periods, it) },
            selected = state.trendLength,
            onSelect = { onIntent(ReportsIntent.TrendLengthPicked(it)) },
            modifier = Modifier.testTag("reports.trend.length"),
            optionModifier = { Modifier.testTag("reports.trend.length.$it") },
        )
        when (val trend = state.trend) {
            TrendUi.Unknown -> Text(
                if (state.tooLarge) stringResource(Res.string.reports_too_large) else stringResource(Res.string.reports_online_only),
                style = AppTheme.type.body,
                color = AppTheme.colors.ink2,
                modifier = Modifier.testTag("reports.trend.unknown"),
            )
            is TrendUi.Points -> {
                val labels = TrendChartLabels(
                    income = stringResource(Res.string.reports_income),
                    expenses = stringResource(Res.string.reports_expenses),
                    scheduled = stringResource(Res.string.reports_scheduled_legend),
                    summary = stringResource(Res.string.reports_trend_summary, trend.items.size),
                )
                val showScheduled = state.includeScheduled
                TrendBarChart(
                    points = trend.items.map {
                        TrendPointUi(
                            periodLabel = com.fares.saveme.designsystem.format.formatDayMonth(it.start),
                            incomeMinor = it.incomeMinor,
                            expensesMinor = it.expensesMinor,
                            plannedIncomeMinor = it.plannedIncomeMinor.takeIf { showScheduled },
                            plannedExpensesMinor = it.plannedExpensesMinor.takeIf { showScheduled },
                        )
                    },
                    currency = state.currency,
                    labels = labels,
                    modifier = Modifier.testTag("reports.trend.chart"),
                )
                TrendTable(
                    rows = trend.items.map {
                        TrendRowUi(
                            periodLabel = rangeText(it.start, it.end, state.thisYear),
                            incomeMinor = it.incomeMinor,
                            expensesMinor = it.expensesMinor,
                            netMinor = it.netMinor,
                            plannedIncomeMinor = it.plannedIncomeMinor.takeIf { showScheduled },
                            plannedExpensesMinor = it.plannedExpensesMinor.takeIf { showScheduled },
                        )
                    },
                    currency = state.currency,
                    labels = TrendTableLabels(
                        income = stringResource(Res.string.reports_income),
                        expenses = stringResource(Res.string.reports_expenses),
                        net = stringResource(Res.string.reports_net),
                        scheduled = stringResource(Res.string.reports_scheduled_legend),
                    ),
                    rowModifier = { Modifier.testTag("reports.trend.row.$it") },
                )
                if (trend.hasUnknown) Text(stringResource(Res.string.reports_online_only), style = AppTheme.type.caption, color = AppTheme.colors.caption, modifier = Modifier.testTag("reports.trend.unknown"))
            }
        }
    }
}

@Preview
@Composable
private fun ReportsScreenPreview() {
    AppTheme {
        ReportsScreen(
            ReportsState(
                status = ReportsStatus.Ready,
                currency = "EGP",
                currencies = listOf("EGP"),
                thisYear = 2026,
                range = RangeUi(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), custom = false),
            ),
            {},
        )
    }
}
