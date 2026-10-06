package com.fares.saveme.designsystem.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.fares.saveme.designsystem.component.ExcludedWalletsNotice
import com.fares.saveme.designsystem.component.FigureTone
import com.fares.saveme.designsystem.component.NoticeAction
import com.fares.saveme.designsystem.component.ReportFigureRow
import com.fares.saveme.designsystem.component.ShareBarRow
import com.fares.saveme.designsystem.component.SummaryCard
import com.fares.saveme.designsystem.component.SummaryDivider
import com.fares.saveme.designsystem.component.TrendBarChart
import com.fares.saveme.designsystem.component.TrendChartLabels
import com.fares.saveme.designsystem.component.TrendPointUi
import com.fares.saveme.designsystem.component.TrendRowUi
import com.fares.saveme.designsystem.component.TrendTable
import com.fares.saveme.designsystem.component.TrendTableLabels
import com.fares.saveme.designsystem.component.formatMoney
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.catalog_cat_rent
import saveme.shared.generated.resources.catalog_section_reports
import saveme.shared.generated.resources.catalog_tx_groceries
import saveme.shared.generated.resources.catalog_tx_transport
import saveme.shared.generated.resources.reports_balance_end
import saveme.shared.generated.resources.reports_expenses
import saveme.shared.generated.resources.reports_income
import saveme.shared.generated.resources.reports_net
import saveme.shared.generated.resources.reports_not_included
import saveme.shared.generated.resources.reports_scheduled_amount
import saveme.shared.generated.resources.reports_scheduled_legend
import saveme.shared.generated.resources.reports_see_currency
import saveme.shared.generated.resources.reports_share_under_one
import saveme.shared.generated.resources.reports_summary
import saveme.shared.generated.resources.reports_trend_summary

/** The report components (spec 014, research R-10): share bars, the trend chart and its table, figure rows, the summary card and the excluded-wallets notice. */
@Composable
fun ReportsSection() = CatalogSection(Res.string.catalog_section_reports) {
    SummaryCard(stringResource(Res.string.reports_summary)) {
        ReportFigureRow(stringResource(Res.string.reports_income), formatMoney(1_000_000, "EGP", showPlus = true), FigureTone.Positive)
        ReportFigureRow(stringResource(Res.string.reports_expenses), formatMoney(-550_000, "EGP"))
        SummaryDivider()
        ReportFigureRow(stringResource(Res.string.reports_net), formatMoney(450_000, "EGP"), emphasis = true)
        ReportFigureRow(stringResource(Res.string.reports_balance_end), formatMoney(-120_000, "EGP"), FigureTone.Muted)
    }
    Column {
        ShareBarRow(stringResource(Res.string.catalog_tx_groceries), "cart", "#2E7D32", formatMoney(350_000, "EGP"), "64%", 0.64f)
        ShareBarRow(stringResource(Res.string.catalog_tx_transport), "bus", "#1F7F8C", formatMoney(120_000, "EGP"), "22%", 0.22f)
        ShareBarRow(
            stringResource(Res.string.catalog_cat_rent), "sofa", "#A9503A", formatMoney(1_000, "EGP"), stringResource(Res.string.reports_share_under_one), 0.002f,
            scheduledText = stringResource(Res.string.reports_scheduled_amount, formatMoney(500_000, "EGP")),
        )
    }
    val points = listOf(
        TrendPointUi("1 Aug", 0, 0),
        TrendPointUi("1 Sep", 1_000_000, 1_200_000),
        TrendPointUi("1 Oct", 1_000_000, 350_000, plannedExpensesMinor = 500_000),
    )
    TrendBarChart(
        points, "EGP",
        TrendChartLabels(
            income = stringResource(Res.string.reports_income),
            expenses = stringResource(Res.string.reports_expenses),
            scheduled = stringResource(Res.string.reports_scheduled_legend),
            summary = stringResource(Res.string.reports_trend_summary, points.size),
        ),
    )
    TrendTable(
        rows = listOf(
            TrendRowUi("1 Sep – 30 Sep", 1_000_000, 1_200_000, -200_000),
            TrendRowUi("1 Oct – 31 Oct", 1_000_000, 350_000, 650_000, plannedExpensesMinor = 500_000),
        ),
        currency = "EGP",
        labels = TrendTableLabels(
            income = stringResource(Res.string.reports_income),
            expenses = stringResource(Res.string.reports_expenses),
            net = stringResource(Res.string.reports_net),
            scheduled = stringResource(Res.string.reports_scheduled_legend),
        ),
    )
    ExcludedWalletsNotice(
        text = stringResource(Res.string.reports_not_included, "Savings (USD)"),
        actions = listOf(NoticeAction(stringResource(Res.string.reports_see_currency, "USD"), "catalog.see.USD")),
        onAction = {},
    )
}
