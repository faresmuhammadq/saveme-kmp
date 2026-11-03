package com.fares.saveme.feature.reports.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppDatePickerDialog
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.CalmDivider
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.DateField
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.ExcludedWalletsNotice
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.NoticeAction
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SelectionSheet
import com.fares.saveme.designsystem.component.SettingsSwitchRow
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.TrendPointUi
import com.fares.saveme.designsystem.component.TrendRowUi
import com.fares.saveme.designsystem.component.TrendTable
import com.fares.saveme.designsystem.component.TrendTableLabels
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.ReportsChevronBack
import com.fares.saveme.designsystem.icon.ReportsExpandMore
import com.fares.saveme.designsystem.money.MoneyFormatter
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.designsystem.tokens.CategoryColors
import com.fares.saveme.feature.reports.domain.model.ReportChoices
import com.fares.saveme.feature.reports.ui.CategoryRowUi
import com.fares.saveme.feature.reports.ui.rangeText
import com.fares.saveme.feature.reports.ui.shareText
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_done
import saveme.shared.generated.resources.category_unavailable
import saveme.shared.generated.resources.month_short
import saveme.shared.generated.resources.calm_rep_balance
import saveme.shared.generated.resources.calm_rep_less
import saveme.shared.generated.resources.calm_rep_more
import saveme.shared.generated.resources.calm_rep_net_period
import saveme.shared.generated.resources.calm_rep_spending_trend
import saveme.shared.generated.resources.calm_rep_spent
import saveme.shared.generated.resources.calm_rep_adjustments
import saveme.shared.generated.resources.calm_rep_transfers
import saveme.shared.generated.resources.calm_rep_where
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.reports_add_wallet
import saveme.shared.generated.resources.reports_all_wallets
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
import saveme.shared.generated.resources.reports_title
import saveme.shared.generated.resources.reports_too_large
import saveme.shared.generated.resources.reports_trend_periods
import saveme.shared.generated.resources.reports_trend_summary
import saveme.shared.generated.resources.reports_waiting
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    val walletReport = !state.isCrossWallet || state.status == ReportsStatus.WalletGone
    CalmScreen(
        title = state.title ?: stringResource(Res.string.reports_title),
        onBack = if (walletReport) ({ onIntent(ReportsIntent.BackClicked) }) else null,
        backDescription = stringResource(Res.string.cd_back),
        scrollable = false,
        spacing = 12.dp,
        actions = { if (state.status == ReportsStatus.Ready) ScopePill(state, onIntent) },
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
    if (state.currencyPickerOpen && state.isCrossWallet) {
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
private fun LoadingBody() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().testTag("reports.screen")) {
        Skeleton(Modifier.fillMaxWidth().height(48.dp), AppTheme.shapes.control)
        Skeleton(Modifier.fillMaxWidth().height(200.dp), RoundedCornerShape(24.dp))
        Skeleton(Modifier.fillMaxWidth().height(160.dp), RoundedCornerShape(24.dp))
    }
}

@Composable
private fun ReportBody(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = AppTheme.spacing.l).testTag("reports.screen"),
    ) {
        RangeBar(state, onIntent)
        if (state.customRangeOpen) CustomRangeCard(state, onIntent)
        if (state.isCrossWallet && state.excluded.isNotEmpty()) Excluded(state, onIntent)
        Notes(state)
        SummarySection(state)
        CategoriesSection(state, onIntent)
        TrendSection(state, onIntent)
        CalmCard(padding = PaddingValues(vertical = 4.dp)) {
            SettingsSwitchRow(
                title = stringResource(Res.string.reports_include_scheduled),
                checked = state.includeScheduled,
                onCheckedChange = { onIntent(ReportsIntent.IncludeScheduledToggled(it)) },
                modifier = Modifier.testTag("reports.scheduled.switch"),
            )
        }
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

/** "All wallets · EGP": opens the currency picker on the cross-wallet report; a plain currency tag on a wallet's report. */
@Composable
private fun ScopePill(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    val cross = state.isCrossWallet
    val label = if (cross) stringResource(Res.string.reports_all_wallets) + " · " + state.currency else state.currency
    val description = stringResource(Res.string.reports_currency) + ", " + label
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = AppTheme.sizes.touchTarget)
            .then(
                if (cross) {
                    Modifier
                        .testTag("reports.currency")
                        .semantics { contentDescription = description }
                        .clickable(role = Role.Button) { onIntent(ReportsIntent.CurrencyClicked) }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .height(40.dp)
                .background(AppTheme.colors.surface, AppTheme.shapes.pill)
                .border(1.dp, AppTheme.colors.outlineVariant, AppTheme.shapes.pill)
                .padding(horizontal = 12.dp),
        ) {
            Text(label, style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = AppTheme.colors.ink, maxLines = 1)
            if (cross) Icon(AppIcons.ReportsExpandMore, contentDescription = null, tint = AppTheme.colors.ink, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RangeBar(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            CalmCircleButton(
                AppIcons.ReportsChevronBack, stringResource(Res.string.reports_previous), { onIntent(ReportsIntent.PreviousClicked) },
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
            if (state.canGoNext) {
                CalmCircleButton(
                    AppIcons.ChevronForward, stringResource(Res.string.reports_next), { onIntent(ReportsIntent.NextClicked) },
                    modifier = Modifier.testTag("reports.range.next"),
                )
            }
            CalmCircleButton(
                AppIcons.Calendar, stringResource(Res.string.reports_custom), { onIntent(ReportsIntent.CustomRangeClicked) },
                modifier = Modifier.testTag("reports.range.custom"),
            )
        }
        if (state.canGoNext || state.isCustomRange || state.range == null) {
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
    CalmCard(modifier = Modifier.testTag("reports.custom")) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
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
    val summary = state.summary
    when {
        state.tooLarge -> CalmCard {
            Text(stringResource(Res.string.reports_too_large), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("reports.too_large"))
        }
        summary == null -> CalmCard {
            Text(stringResource(Res.string.reports_online_only), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("reports.summary.unknown"))
        }
        else -> {
            val figures = summary.figures
            val currency = figures.currency
            val planned = summary.planned
            val netColor = when {
                figures.net > 0 -> AppTheme.money.positive
                figures.net < 0 -> AppTheme.money.over
                else -> AppTheme.colors.ink
            }
            val incomeText = formatMoney(figures.income, currency, showPlus = true)
            val expensesText = formatMoney(-figures.expenses, currency)
            CalmCard(padding = PaddingValues(18.dp)) {
                Text(
                    stringResource(Res.string.calm_rep_net_period),
                    style = AppTheme.type.label,
                    color = AppTheme.colors.ink2,
                    modifier = Modifier.semantics { heading() },
                )
                BasicText(
                    text = formatMoney(figures.net, currency, showPlus = true),
                    style = AppTheme.type.numeralHero.copy(fontSize = 38.sp, color = netColor, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 38.sp, stepSize = 1.sp),
                    modifier = Modifier.testTag("reports.summary.net"),
                )
                IncomeExpenseBar(figures.income, figures.expenses, stringResource(Res.string.reports_income) + " " + incomeText + ", " + stringResource(Res.string.reports_expenses) + " " + expensesText)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Figure(stringResource(Res.string.reports_income), incomeText, "reports.summary.income", emphasis = true, stacked = true)
                        if (planned != null) ScheduledLine(planned.income, currency, "reports.planned.income")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Figure(stringResource(Res.string.reports_expenses), expensesText, "reports.summary.expenses", emphasis = true, stacked = true, end = true)
                        if (planned != null) ScheduledLine(planned.expenses, currency, "reports.planned")
                    }
                }
                CalmDivider(Modifier.padding(top = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Figure(stringResource(Res.string.calm_rep_balance), balanceText(figures.balanceStart, currency, summary.liabilityWallet), "reports.summary.balance_start")
                    Text("  →  ", style = AppTheme.type.caption, color = AppTheme.colors.ink2)
                    Figure(null, balanceText(figures.balanceEnd, currency, summary.liabilityWallet), "reports.summary.balance_end")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Figure(stringResource(Res.string.calm_rep_transfers), formatMoney(figures.transfersIn, currency, showPlus = true), "reports.summary.transfers_in")
                    Text("  /  ", style = AppTheme.type.caption, color = AppTheme.colors.ink2)
                    Figure(null, formatMoney(-figures.transfersOut, currency), "reports.summary.transfers_out")
                }
                if (figures.adjustmentsIn != 0L || figures.adjustmentsOut != 0L) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Figure(stringResource(Res.string.calm_rep_adjustments), formatMoney(figures.adjustmentsIn, currency, showPlus = true), "reports.summary.adjustments_in")
                        Text("  /  ", style = AppTheme.type.caption, color = AppTheme.colors.ink2)
                        Figure(null, formatMoney(-figures.adjustmentsOut, currency), "reports.summary.adjustments_out")
                    }
                }
                if (planned != null && planned.transfersIn != 0L) ScheduledLine(planned.transfersIn, currency, "reports.planned.transfers_in")
                if (planned != null && planned.transfersOut != 0L) ScheduledLine(planned.transfersOut, currency, "reports.planned.transfers_out")
            }
        }
    }
}

@Composable
private fun IncomeExpenseBar(incomeMinor: Long, expensesMinor: Long, description: String) {
    val income = incomeMinor.coerceAtLeast(0L)
    val expenses = expensesMinor.coerceAtLeast(0L)
    if (income + expenses == 0L) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(10.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (income > 0) {
            Box(
                Modifier
                    .weight(income.toFloat())
                    .fillMaxHeight()
                    .background(
                        AppTheme.money.positive,
                        RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = if (expenses > 0) 0.dp else 6.dp, bottomEnd = if (expenses > 0) 0.dp else 6.dp),
                    ),
            )
        }
        if (expenses > 0) {
            Box(
                Modifier
                    .weight(expenses.toFloat())
                    .fillMaxHeight()
                    .background(
                        AppTheme.colors.primary,
                        RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp, topStart = if (income > 0) 0.dp else 6.dp, bottomStart = if (income > 0) 0.dp else 6.dp),
                    ),
            )
        }
    }
}

/** "Label value" on one line (or the label over the value when [stacked]), announced as one node that carries the Maestro tag. */
@Composable
private fun Figure(
    label: String?,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    stacked: Boolean = false,
    end: Boolean = false,
) {
    val size = if (emphasis) 13.sp else 12.5.sp
    val labelText: @Composable () -> Unit = {
        if (label != null) {
            Text(
                if (stacked) label else "$label ",
                style = AppTheme.type.caption.copy(fontSize = size, fontWeight = FontWeight.SemiBold),
                color = AppTheme.colors.ink2,
            )
        }
    }
    val valueText: @Composable () -> Unit = {
        Text(
            value,
            style = AppTheme.type.caption.copy(
                fontSize = if (stacked) 15.sp else size,
                fontWeight = if (emphasis) FontWeight.Bold else FontWeight.SemiBold,
                textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
            ),
            color = if (emphasis) AppTheme.colors.ink else AppTheme.colors.ink2,
        )
    }
    val node = modifier.semantics(mergeDescendants = true) {}.testTag(tag)
    if (stacked) {
        Column(horizontalAlignment = if (end) Alignment.End else Alignment.Start, modifier = node) { labelText(); valueText() }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = node) { labelText(); valueText() }
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
    CalmCard(padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.calm_rep_where),
                style = AppTheme.type.itemTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (state.range != null && !state.tooLarge) {
                AppTextButton(stringResource(Res.string.reports_see_all), { onIntent(ReportsIntent.SeeAllClicked) }, modifier = Modifier.testTag("reports.categories.see_all"))
            }
        }
        when {
            state.tooLarge -> Text(stringResource(Res.string.reports_too_large), style = AppTheme.type.body, color = AppTheme.colors.ink2)
            state.range == null -> Text(stringResource(Res.string.reports_online_only), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("reports.categories.unknown"))
            state.topExpenses.isEmpty() -> Text(stringResource(Res.string.reports_no_spending), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("reports.categories.empty"))
            else -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                CalmDonut(
                    slices = state.topExpenses.map { it.fraction.coerceIn(0f, 1f) to CategoryColors.of(it.colorHex).swatch },
                    modifier = Modifier.size(120.dp).clearAndSetSemantics {},
                ) {
                    state.summary?.let { summary ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 22.dp)) {
                            BasicText(
                                text = MoneyFormatter.number(summary.figures.expenses, MoneyFormatter.digitsFor(summary.figures.currency)),
                                style = AppTheme.type.numeralL.copy(color = AppTheme.colors.ink, textAlign = TextAlign.Center, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 15.sp, stepSize = 1.sp),
                            )
                            Text(stringResource(Res.string.calm_rep_spent), style = AppTheme.type.caption.copy(fontSize = 11.sp), color = AppTheme.colors.ink2)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    state.topExpenses.forEachIndexed { index, row -> LegendRow(row, Modifier.testTag("reports.category.$index")) }
                }
            }
        }
    }
}

@Composable
internal fun LegendRow(row: CategoryRowUi, modifier: Modifier = Modifier) {
    val name = row.name.ifEmpty { stringResource(Res.string.category_unavailable) }
    val share = shareText(row.share)
    val amount = formatMoney(row.valueMinor, row.currency)
    val description = listOf(name, share, amount).filter { it.isNotEmpty() }.joinToString(", ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .padding(vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(Modifier.size(10.dp).background(CategoryColors.of(row.colorHex).swatch, RoundedCornerShape(3.dp)))
        Text(
            name,
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = AppTheme.colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(share, style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = AppTheme.colors.ink)
    }
}

@Composable
internal fun CategoryRow(row: CategoryRowUi, modifier: Modifier = Modifier) {
    com.fares.saveme.designsystem.component.ShareBarRow(
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
    CalmCard(padding = PaddingValues(18.dp)) {
        var lengthMenu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.calm_rep_spending_trend),
                style = AppTheme.type.itemTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = AppTheme.sizes.touchTarget)
                        .testTag("reports.trend.length")
                        .clickable(role = Role.Button) { lengthMenu = true },
                ) {
                    Text(
                        stringResource(Res.string.reports_trend_periods, state.trendLength),
                        style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                        color = AppTheme.colors.ink2,
                    )
                    Icon(AppIcons.ReportsExpandMore, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = lengthMenu, onDismissRequest = { lengthMenu = false }) {
                    ReportChoices.TREND_LENGTHS.forEach { length ->
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.reports_trend_periods, length), style = AppTheme.type.label) },
                            onClick = { lengthMenu = false; onIntent(ReportsIntent.TrendLengthPicked(length)) },
                            modifier = Modifier.testTag("reports.trend.length.$length"),
                        )
                    }
                }
            }
        }
        when (val trend = state.trend) {
            TrendUi.Unknown -> Text(
                if (state.tooLarge) stringResource(Res.string.reports_too_large) else stringResource(Res.string.reports_online_only),
                style = AppTheme.type.body,
                color = AppTheme.colors.ink2,
                modifier = Modifier.testTag("reports.trend.unknown"),
            )
            is TrendUi.Points -> {
                val showScheduled = state.includeScheduled
                val months = stringArrayResource(Res.array.month_short)
                CalmTrendBars(
                    points = trend.items.map {
                        TrendPointUi(
                            periodLabel = months.getOrElse(it.end.month.ordinal) { "" },
                            incomeMinor = it.incomeMinor,
                            expensesMinor = it.expensesMinor,
                            plannedIncomeMinor = it.plannedIncomeMinor.takeIf { showScheduled },
                            plannedExpensesMinor = it.plannedExpensesMinor.takeIf { showScheduled },
                        )
                    },
                    summary = stringResource(Res.string.reports_trend_summary, trend.items.size),
                    currency = state.currency,
                    incomeLabel = stringResource(Res.string.reports_income),
                    expensesLabel = stringResource(Res.string.reports_expenses),
                    modifier = Modifier.padding(top = 8.dp).testTag("reports.trend.chart"),
                )
                val last = trend.items.last().expensesMinor
                val previous = trend.items.getOrNull(trend.items.size - 2)?.expensesMinor
                if (previous != null && previous > 0 && last != previous) {
                    val change = abs(last - previous) * 100 / previous
                    val less = last < previous
                    Text(
                        stringResource(
                            if (less) Res.string.calm_rep_less else Res.string.calm_rep_more,
                            "$change%", months.getOrElse(trend.items[trend.items.size - 2].end.month.ordinal) { "" },
                        ),
                        style = AppTheme.type.label,
                        color = if (less) AppTheme.money.positive else AppTheme.money.over,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                TrendTable(
                    modifier = Modifier.padding(top = 12.dp),
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
